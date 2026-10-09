package com.foodgo.nativeapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.model.Category;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** app/restaurant/[id]/page.tsx */
public class RestaurantScreen extends Screen implements AppState.Listener {
    private long restaurantId;
    private Restaurant store;
    private boolean storeLoaded;
    private List<Food> foods;
    private List<Category> categories = new ArrayList<>();
    private FrameLayout root;
    private ScrollView scroll;
    private final List<Runnable> binders = new ArrayList<>();
    private FrameLayout cartBar;
    private TextView cartBarLeft, cartBarRight;
    private HorizontalScrollView pinnedNav;
    private View inlineNav;
    private final Map<String, View> sections = new LinkedHashMap<>();

    @Override
    protected View build() {
        try { restaurantId = Long.parseLong(uri.getPathSegments().get(1)); } catch (Exception e) { restaurantId = 0; }
        root = new FrameLayout(act);
        root.setBackgroundColor(U.BG);
        root.addView(U.spinner(act));
        if (restaurantId <= 0) { storeLoaded = true; foods = new ArrayList<>(); render(); return root; }
        Store.restaurant(restaurantId, (r, e) -> { store = r; storeLoaded = true; render(); });
        Db.from("fg_foods").select(Store.FOOD_SELECT).eq("restaurant_id", restaurantId)
                .order("is_popular", false).order("sold_count", false).list(Food.class, (l, e) -> { foods = l; render(); });
        Db.from("fg_categories").select("*").order("sort").list(Category.class, (l, e) -> { categories = l; render(); });
        state().addListener(this);
        return root;
    }

    @Override
    public void onDestroy() { state().removeListener(this); }

    /** "Nổi bật" first, then one section per category in the admin-defined order */
    private List<Object[]> groups() {
        List<Object[]> out = new ArrayList<>();
        List<Food> popular = new ArrayList<>();
        for (Food f : foods) if (f.is_popular) popular.add(f);
        if (!popular.isEmpty()) out.add(new Object[]{"Nổi bật", popular});
        for (Category c : categories) {
            List<Food> items = new ArrayList<>();
            for (Food f : foods) if (f.category_id != null && f.category_id == c.id) items.add(f);
            if (!items.isEmpty()) out.add(new Object[]{c.name, items});
        }
        List<Food> other = new ArrayList<>();
        for (Food f : foods) {
            boolean known = false;
            for (Category c : categories) if (f.category_id != null && f.category_id == c.id) { known = true; break; }
            if (!known) other.add(f);
        }
        if (!other.isEmpty()) out.add(new Object[]{"Khác", other});
        return out;
    }

    @SuppressWarnings("unchecked")
    private void render() {
        if (!storeLoaded || foods == null) return;
        root.removeAllViews();
        binders.clear();
        sections.clear();
        if (store == null) {
            LinearLayout page = U.col(act);
            TextView link = U.text(act, "Xem các nhà hàng khác", 14, U.ORANGE, U.BOLD);
            U.click(link, () -> navigate("/menu"));
            page.addView(U.empty(act, R.drawable.ic_store, "Không tìm thấy nhà hàng", link));
            root.addView(scrollPage(page));
            return;
        }
        scroll = new ScrollView(act);
        LinearLayout page = U.col(act);
        page.setClipChildren(false);
        U.pad(page, 0, 16, 0, 112);
        TextView back = U.text(act, "← Tất cả nhà hàng", 14, U.ORANGE, U.BOLD);
        U.pad(back, 20, 12);
        U.click(back, () -> navigate("/menu"));
        page.addView(back, U.lp(U.WRAP, U.WRAP));
        page.addView(new StoreCardView(act, store));

        List<Object[]> groups = groups();
        LinearLayout content = U.col(act);
        U.pad(content, 20, 0);
        if (groups.size() > 1) {
            inlineNav = categoryNav(groups);
            U.add(content, inlineNav, 16);
        }
        if (foods.isEmpty()) U.add(content, U.empty(act, R.drawable.ic_store, "Thực đơn đang được cập nhật", null), 32);
        for (Object[] g : groups) {
            String name = (String) g[0];
            LinearLayout section = U.col(act);
            section.addView(U.text(act, name, 20, U.INK, U.XBOLD));
            List<Food> items = (List<Food>) g[1];
            for (int i = 0; i < items.size(); i++) U.add(section, foodRow(items.get(i)), i == 0 ? 16 : 16);
            U.add(content, section, 32);
            sections.put(name, section);
        }
        page.addView(content);
        scroll.addView(page);
        root.addView(scroll, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        // sticky copy of the category bar once the inline one scrolls away
        if (groups.size() > 1) {
            pinnedNav = (HorizontalScrollView) categoryNav(groups);
            pinnedNav.setBackgroundColor(0xF2FFFAF7);
            U.pad(pinnedNav, 20, 12);
            pinnedNav.setVisibility(View.GONE);
            root.addView(pinnedNav, new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.TOP));
            scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
                int navTop = ((View) inlineNav.getParent()).getTop() + inlineNav.getTop();
                pinnedNav.setVisibility(y > navTop ? View.VISIBLE : View.GONE);
            });
        }

        cartBar = new FrameLayout(act);
        LinearLayout bar = U.row(act);
        U.pad(bar, 20, 0);
        U.pressable(bar, U.ORANGE, 16);
        bar.setElevation(U.dp(act, 8));
        cartBarLeft = U.text(act, "", 15, U.WHITE, U.BOLD);
        cartBarRight = U.text(act, "", 15, U.WHITE, U.BOLD);
        U.addFlex(bar, cartBarLeft, 0);
        bar.addView(cartBarRight);
        bar.setOnClickListener(v -> state().setCartOpen(true));
        cartBar.addView(bar, new FrameLayout.LayoutParams(U.MATCH, U.dp(act, 56)));
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.BOTTOM);
        cp.setMargins(U.dp(act, 20), 0, U.dp(act, 20), U.dp(act, 16));
        root.addView(cartBar, cp);
        onAppState();
    }

    private View categoryNav(List<Object[]> groups) {
        LinearLayout row = U.row(act);
        for (Object[] g : groups) {
            String name = (String) g[0];
            TextView chip = U.text(act, name, 14, U.MUTED, U.SEMI);
            U.pad(chip, 16, 8);
            U.pressable(chip, U.WHITE, 999);
            chip.setElevation(U.dp(act, 1));
            chip.setOnClickListener(v -> {
                View s = sections.get(name);
                if (s != null && scroll != null) {
                    int y = ((View) s.getParent()).getTop() + s.getTop() - (pinnedNav != null ? pinnedNav.getHeight() + U.dp(act, 64) : 0);
                    scroll.smoothScrollTo(0, Math.max(0, y));
                }
            });
            U.add(row, chip, row.getChildCount() == 0 ? 0 : 8, U.WRAP, U.WRAP);
        }
        U.pad(row, 0, 12, 0, 12);
        return U.hscroll(act, row);
    }

    private View foodRow(Food f) {
        LinearLayout card = U.row(act);
        card.setGravity(Gravity.TOP);
        U.pad(card, 12);
        U.card(card, 16);
        if (!f.is_available) card.setAlpha(0.6f);
        if (f.image != null) {
            ImageView img = U.img(act, f.image, 112, 112, 12);
            img.setContentDescription("Xem chi tiết " + f.name);
            U.click(img, () -> FoodDetailSheet.open(act, f));
            card.addView(img);
        }
        LinearLayout info = U.col(act);
        LinearLayout head = U.row(act);
        head.setGravity(Gravity.TOP);
        TextView name = U.text(act, f.name, 16, U.INK, U.BOLD);
        U.click(name, () -> FoodDetailSheet.open(act, f));
        U.addFlex(head, name, 0);
        ImageView heart = U.icon(act, R.drawable.ic_heart_outline, 20, U.ORANGE);
        U.pad(heart, 4);
        U.click(heart, () -> state().toggleFavorite(f.id));
        U.add(head, heart, 8, U.dp(act, 28), U.dp(act, 28));
        info.addView(head);
        U.add(info, Cards.ratingBadge(act, f.rating, f.review_count, 12, () -> FoodDetailSheet.open(act, f)), 2, U.WRAP, U.WRAP);
        if (f.description != null && !f.description.isEmpty()) U.add(info, U.ellipsize(U.text(act, f.description, 12, U.SUBTLE), 2), 4);
        LinearLayout bottom = U.row(act);
        U.addFlex(bottom, Cards.price(act, f.price, f.old_price, 16, 12), 0);
        FrameLayout action = new FrameLayout(act);
        bottom.addView(action);
        U.add(info, bottom, 12);
        U.addFlex(card, info, f.image != null ? 16 : 0);

        binders.add(() -> {
            boolean liked = state().favoriteIds.contains(f.id);
            heart.setImageDrawable(U.drawable(act, liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, U.ORANGE));
            heart.setContentDescription(liked ? "Bỏ yêu thích" : "Yêu thích");
            action.removeAllViews();
            CartItem inCart = state().inCart(f.id);
            if (!f.is_available) {
                action.addView(U.text(act, "Tạm hết", 12, U.SUBTLE, U.BOLD));
            } else if (inCart != null) {
                LinearLayout q = U.row(act);
                q.addView(square(R.drawable.ic_remove, U.SOFT, U.INK, 32, 8, "Giảm", () -> state().setQty(f.id, inCart.qty - 1)));
                TextView n = U.text(act, String.valueOf(inCart.qty), 14, U.INK, U.BOLD);
                n.setGravity(Gravity.CENTER);
                U.add(q, n, 8, U.dp(act, 20), U.WRAP);
                U.add(q, square(R.drawable.ic_add, U.ORANGE, U.WHITE, 32, 8, "Tăng", () -> state().setQty(f.id, inCart.qty + 1)), 8, U.dp(act, 32), U.dp(act, 32));
                action.addView(q);
            } else {
                action.addView(square(R.drawable.ic_add, U.PEACH, U.ORANGE, 36, 12, "Thêm " + f.name, () -> state().addToCart(f)));
            }
        });
        return card;
    }

    private View square(int icon, int bg, int fg, int sizeDp, int radius, String label, Runnable r) {
        FrameLayout b = new FrameLayout(act);
        U.pressable(b, bg, radius);
        int iconDp = sizeDp >= 36 ? 24 : 12;
        b.addView(U.icon(act, icon, iconDp, fg), new FrameLayout.LayoutParams(U.dp(act, iconDp), U.dp(act, iconDp), Gravity.CENTER));
        b.setContentDescription(label);
        b.setOnClickListener(v -> r.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(U.dp(act, sizeDp), U.dp(act, sizeDp)));
        return b;
    }

    @Override
    public void onAppState() {
        for (Runnable r : binders) r.run();
        if (cartBar == null) return;
        AppState s = state();
        // the cart bar only counts this restaurant's dishes (the cart may hold another restaurant's)
        boolean here = !s.cart.isEmpty() && s.cart.get(0).restaurant_id == restaurantId;
        cartBar.setVisibility(s.cartCount() > 0 && here ? View.VISIBLE : View.GONE);
        cartBarLeft.setText(s.cartCount() + " món • Xem giỏ hàng");
        cartBarRight.setText(Fmt.money(s.cartSubtotal()));
    }
}
