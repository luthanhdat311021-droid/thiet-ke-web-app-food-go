package com.foodgo.nativeapp.state;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Realtime;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Notification;
import com.foodgo.nativeapp.model.Profile;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.Toaster;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** components/app-provider.tsx: auth, cart, favourites, notifications and toasts shared by every screen. */
public final class AppState {
    public interface Listener { void onAppState(); }

    private static AppState I;

    public static AppState get() { return I; }

    public static void init(Context ctx) { I = new AppState(ctx); }

    private final SharedPreferences prefs;
    private final List<Listener> listeners = new ArrayList<>();

    public Auth.Session session;
    public Profile profile;
    public boolean authLoading = true;
    /** true while a deliberate sign-out is in progress */
    public boolean signingOut;

    public List<CartItem> cart = new ArrayList<>();
    private String cartKey;
    public boolean cartOpen;

    public final Set<Long> favoriteIds = new LinkedHashSet<>();
    public List<Notification> notifications = new ArrayList<>();
    private Realtime.Channel notificationsChannel;
    private String loadedUser;

    private static final String CART_KEY = "foodgo-cart";

    private AppState(Context ctx) {
        prefs = ctx.getSharedPreferences("foodgo", Context.MODE_PRIVATE);
        session = Auth.session();
        Auth.addListener(s -> {
            session = s;
            authLoading = false;
            userChanged();
            changed();
        });
        if (session == null) {
            authLoading = false;
            userChanged();
        } else {
            Auth.restore(() -> {
                session = Auth.session();
                authLoading = false;
                userChanged();
                changed();
            });
        }
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    public void changed() { for (Listener l : new ArrayList<>(listeners)) l.onAppState(); }

    public String userId() { return session != null ? session.userId() : null; }

    public boolean signedIn() { return session != null; }

    public String email() { return session != null ? session.email() : null; }

    // ------------------------------------------------------------------ toasts

    public void toast(String message) { Toaster.show(App.current(), message, false); }

    public void toastError(String message) { Toaster.show(App.current(), message, true); }

    // ------------------------------------------------------------------ cart (one per account + one for guests)

    private List<CartItem> readCart(String key) {
        return Json.list(Json.parse(prefs.getString(key, "[]")), CartItem.class);
    }

    private void saveCart() {
        if (cartKey != null) prefs.edit().putString(cartKey, Json.gson.toJson(cart)).apply();
    }

    private void setCart(List<CartItem> items) {
        cart = items;
        saveCart();
        changed();
    }

    public int cartCount() { int n = 0; for (CartItem x : cart) n += x.qty; return n; }

    public long cartSubtotal() { long s = 0; for (CartItem x : cart) s += x.qty * x.price; return s; }

    public CartItem inCart(long foodId) { for (CartItem x : cart) if (x.food_id == foodId) return x; return null; }

    /** One order = one restaurant (fg_place_order enforces it): switching restaurants starts a new cart. */
    public void addToCart(Food food, int qty) {
        if (!food.is_available) { toastError("Món này tạm hết"); return; }
        CartItem other = null;
        for (CartItem x : cart) if (x.restaurant_id != food.restaurant_id) { other = x; break; }
        String restaurantName = food.restaurants != null && food.restaurants.name != null ? food.restaurants.name : "Nhà hàng";
        Runnable add = () -> {
            List<CartItem> next = new ArrayList<>();
            boolean found = false;
            for (CartItem x : cart) {
                if (x.restaurant_id != food.restaurant_id) continue;
                CartItem c = x.copy();
                if (c.food_id == food.id) { c.qty = Math.min(50, c.qty + qty); found = true; }
                next.add(c);
            }
            if (!found) {
                CartItem item = new CartItem();
                item.food_id = food.id; item.name = food.name; item.price = food.price; item.image = food.image;
                item.restaurant_id = food.restaurant_id; item.restaurant_name = restaurantName; item.qty = qty;
                next.add(item);
            }
            setCart(next);
            toast("Đã thêm " + food.name + " vào giỏ");
            Recommend.track("cart", food.id, signedIn());
        };
        if (other != null) {
            Dialogs.confirm(App.current(), "Giỏ hàng đang có món của " + other.restaurant_name + ". Mỗi đơn chỉ đặt từ một nhà hàng.\n\nXóa giỏ hiện tại để thêm món của " + restaurantName + "?", add);
        } else add.run();
    }

    public void addToCart(Food food) { addToCart(food, 1); }

    /** Calls onReplaced only when the cart was replaced (after a confirm if it wasn't empty). */
    public void replaceCart(List<CartItem> items, Runnable onReplaced) {
        Runnable go = () -> { setCart(items); onReplaced.run(); };
        if (!cart.isEmpty()) Dialogs.confirm(App.current(), "Thay giỏ hàng hiện tại bằng các món trong đơn này?", go);
        else go.run();
    }

    public void setQty(long foodId, int qty) {
        List<CartItem> next = new ArrayList<>();
        for (CartItem x : cart) {
            CartItem c = x.copy();
            if (c.food_id == foodId) c.qty = Math.min(50, qty);
            if (c.qty > 0) next.add(c);
        }
        setCart(next);
    }

    public void clearCart() { setCart(new ArrayList<>()); }

    public void setCartOpen(boolean open) { cartOpen = open; changed(); }

    // ------------------------------------------------------------------ auth

    /** Runs whenever the signed-in account changes (sign in, sign out, another account). */
    private void userChanged() {
        String uid = userId();
        // cart: signing out shows the (empty) guest cart; the account's cart comes back when it signs in again
        String key = uid != null ? CART_KEY + ":" + uid : CART_KEY;
        if (!key.equals(cartKey)) {
            List<CartItem> items = readCart(key);
            if (uid != null) {
                // dishes added before signing in carry over to an empty account cart
                List<CartItem> guest = readCart(CART_KEY);
                if (items.isEmpty() && !guest.isEmpty()) items = guest;
                prefs.edit().remove(CART_KEY).apply();
            }
            cart = items;
            cartKey = key;
            saveCart();
        }

        if (uid != null && uid.equals(loadedUser)) return;
        loadedUser = uid;
        Realtime.remove(notificationsChannel);
        notificationsChannel = null;
        if (uid == null) {
            profile = null;
            favoriteIds.clear();
            notifications = new ArrayList<>();
            return;
        }
        loadProfile(uid, null);
        Db.from("fg_favorites").select("food_id").rows((rows, err) -> {
            if (!uid.equals(userId())) return;
            favoriteIds.clear();
            for (JsonObject r : rows) favoriteIds.add((long) Json.num(r, "food_id", 0));
            changed();
        });
        Db.from("fg_notifications").select("*").order("created_at", false).limit(20).list(Notification.class, (list, err) -> {
            if (!uid.equals(userId())) return;
            notifications = list;
            changed();
        });
        notificationsChannel = Realtime.subscribe("notifications-" + uid, "INSERT", "fg_notifications", "user_id=eq." + uid, (type, rec, old) -> {
            Notification n = Json.as(rec, Notification.class);
            if (n == null) return;
            List<Notification> next = new ArrayList<>();
            next.add(n);
            next.addAll(notifications);
            notifications = next.size() > 20 ? new ArrayList<>(next.subList(0, 20)) : next;
            toast(n.body != null ? n.body : n.title);
            changed();
        });
    }

    private void loadProfile(String uid, Runnable done) {
        Db.from("fg_profiles").select("*").eq("id", uid).maybeSingle(Profile.class, (p, err) -> {
            if (!uid.equals(userId())) return;
            if (p != null) {
                profile = p;
                changed();
                if (done != null) done.run();
                return;
            }
            // account created before FoodGo's signup trigger existed
            Auth.getUser((u, e) -> {
                JsonObject meta = u != null && u.has("user_metadata") && u.get("user_metadata").isJsonObject() ? u.getAsJsonObject("user_metadata") : new JsonObject();
                String email = Json.str(u, "email");
                String name = Json.str(meta, "full_name");
                if (name == null) name = Json.str(meta, "name");
                if (name == null && email != null) name = email.split("@")[0];
                Db.from("fg_profiles").insert(Json.obj("id", uid, "full_name", name, "avatar_url", Json.str(meta, "avatar_url")))
                        .select("*").maybeSingle(Profile.class, (created, e2) -> {
                            if (!uid.equals(userId())) return;
                            profile = created;
                            changed();
                            if (done != null) done.run();
                        });
            });
        });
    }

    public void refreshProfile(Runnable done) {
        String uid = userId();
        if (uid == null) { if (done != null) done.run(); return; }
        loadProfile(uid, done);
    }

    /**
     * A deliberate sign-out goes home; RequireAuth must not bounce to /login?next=<protected page> meanwhile,
     * or the next account to sign in lands on a page it may not be allowed to see (e.g. /admin).
     */
    public void signOut() {
        signingOut = true;
        App.navigate("/");
        Auth.signOut(() -> {
            toast("Đã đăng xuất");
            signingOut = false;
            changed();
        });
    }

    // ------------------------------------------------------------------ favorites

    public void toggleFavorite(long foodId) {
        String uid = userId();
        if (uid == null) { App.navigate("/login"); return; }
        boolean liked = favoriteIds.contains(foodId);
        if (liked) favoriteIds.remove(foodId); else favoriteIds.add(foodId);
        changed();
        Db q = liked
                ? Db.from("fg_favorites").delete().eq("user_id", uid).eq("food_id", foodId)
                : Db.from("fg_favorites").insert(Json.obj("user_id", uid, "food_id", foodId));
        q.run((d, err) -> {
            if (err == null) return;
            if (liked) favoriteIds.add(foodId); else favoriteIds.remove(foodId);
            changed();
            toastError(err);
        });
    }

    // ------------------------------------------------------------------ notifications

    public int unreadCount() { int n = 0; for (Notification x : notifications) if (!x.is_read) n++; return n; }

    public void markAllRead() {
        String uid = userId();
        if (uid == null || unreadCount() == 0) return;
        for (Notification n : notifications) n.is_read = true;
        changed();
        Db.from("fg_notifications").update(Json.obj("is_read", true)).eq("user_id", uid).eq("is_read", false).run((d, e) -> {});
    }

    /** Small helper for activities that need the state without casting. */
    public static boolean isAlive(Activity a) { return a != null && !a.isFinishing() && !a.isDestroyed(); }

    static JsonElement unused() { return null; }
}
