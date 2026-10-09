package com.foodgo.nativeapp.shop;

import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.BaseActivity;
import com.foodgo.nativeapp.MainActivity;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.admin.Catalog;
import com.foodgo.nativeapp.admin.EntityManager;
import com.foodgo.nativeapp.admin.OrdersManager;
import com.foodgo.nativeapp.admin.StatsView;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** app/shop/page.tsx: the restaurant owner's channel (RequireAuth). */
public class ShopActivity extends BaseActivity implements AppState.Listener {
    private static final String[][] TABS = {
            {"overview", "Tổng quan"}, {"orders", "Đơn hàng"}, {"foods", "Món ăn"}, {"vouchers", "Mã giảm giá"}, {"stats", "Doanh thu"}, {"info", "Thông tin quán"}};
    private static final int[] ICONS = {R.drawable.ic_dashboard, R.drawable.ic_receipt, R.drawable.ic_utensils, R.drawable.ic_ticket, R.drawable.ic_bar_chart, R.drawable.ic_store};

    private FrameLayout root;
    private String tab = "overview";
    private Restaurant restaurant;
    private boolean loaded;
    private String loadedFor;
    private LinearLayout strip;
    private FrameLayout content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String p = getIntent().getStringExtra(MainActivity.EXTRA_PATH);
        if (p != null) {
            String t = Uri.parse("https://foodgo.local" + p).getQueryParameter("tab");
            for (String[] x : TABS) if (x[0].equals(t)) tab = t;
        }
        root = new FrameLayout(this);
        root.setBackgroundColor(U.BG);
        root.setFitsSystemWindows(true);
        setContentView(root);
        AppState.get().addListener(this);
        onAppState();
    }

    @Override
    protected void onDestroy() {
        AppState.get().removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onAppState() {
        AppState s = AppState.get();
        if (s.authLoading) { show(U.spinner(this)); return; }
        if (!s.signedIn()) {
            if (!s.signingOut) App.navigate("/login?next=" + Uri.encode("/shop"));
            finish();
            return;
        }
        if (!s.userId().equals(loadedFor)) { loadedFor = s.userId(); show(U.spinner(this)); reload(); }
    }

    private void show(View v) { root.removeAllViews(); root.addView(v); }

    /** useMyRestaurant(): the signed-in user's own restaurant, or null */
    private void reload() {
        Db.from("fg_restaurants").select("*").eq("owner_id", AppState.get().userId()).maybeSingle(Restaurant.class, (r, e) -> {
            boolean first = !loaded;
            restaurant = r;
            loaded = true;
            if (r == null) showRegister();
            else if (first || content == null) buildDashboard();
            else { renderStrip(); showTab(); }
        });
    }

    private void showRegister() {
        LinearLayout col = U.col(this);
        LinearLayout header = U.row(this);
        header.setBackgroundColor(U.WHITE);
        U.pad(header, 20, 12);
        TextView f = U.text(this, "F", 18, U.WHITE, U.XBOLD);
        f.setGravity(Gravity.CENTER);
        U.bg(f, U.ORANGE, 12);
        header.addView(f, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 40)));
        android.text.SpannableString s = new android.text.SpannableString("FoodGo");
        s.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 4, 6, 0);
        U.addFlex(header, U.text(this, s, 20, U.INK, U.XBOLD), 8);
        header.addView(U.link(this, "← Về trang chủ", () -> { App.navigate("/"); finish(); }));
        col.addView(header);
        col.addView(U.divider(this, U.LINE));
        ScrollView sv = new ScrollView(this);
        LinearLayout pad = U.col(this);
        U.pad(pad, 20, 32, 20, 32);
        pad.addView(new RegisterForm(this, this::reload));
        sv.addView(pad);
        col.addView(sv, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        show(col);
        content = null;
    }

    private void buildDashboard() {
        LinearLayout col = U.col(this);
        strip = U.row(this);
        U.pad(strip, 12, 8);
        LinearLayout box = U.col(this);
        box.setBackgroundColor(U.WHITE);
        box.addView(U.hscroll(this, strip));
        box.addView(U.divider(this, U.LINE));
        col.addView(box);
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        show(col);
        renderStrip();
        showTab();
    }

    private TextView item(int icon, String label, boolean active, Runnable r) {
        TextView t = U.iconText(this, icon, 16, label, 14, active ? U.ORANGE : U.MUTED, active ? U.BOLD : U.NORMAL);
        U.pad(t, 12, 10);
        U.pressable(t, active ? U.PEACH : U.WHITE, 12);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void renderStrip() {
        strip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            String id = TABS[i][0];
            U.add(strip, item(ICONS[i], TABS[i][1], tab.equals(id), () -> openTab(id)), i == 0 ? 0 : 4, U.WRAP, U.WRAP);
        }
        U.add(strip, item(R.drawable.ic_open, "Xem trang quán", false, () -> { App.navigate("/restaurant/" + restaurant.id); finish(); }), 4, U.WRAP, U.WRAP);
        U.add(strip, item(R.drawable.ic_arrow_back, "Về trang chủ", false, () -> { App.navigate("/"); finish(); }), 4, U.WRAP, U.WRAP);
    }

    private void openTab(String id) { tab = id; renderStrip(); showTab(); }

    private void showTab() {
        content.removeAllViews();
        LinearLayout pad = U.col(this);
        U.pad(pad, 16, 16, 16, 24);
        StoreHours.Subscription sub = StoreHours.subscriptionOf(restaurant);
        if (!sub.active && !tab.equals("overview")) {
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Nhà hàng " + (sub.until != null ? "đã hết hạn" : "chưa kích hoạt") + " nên khách chưa thấy. ");
            int s = b.length();
            b.append("Thanh toán phí duy trì");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
            b.setSpan(new android.text.style.UnderlineSpan(), s, b.length(), 0);
            TextView warn = U.text(this, b, 14, U.RED_DARK);
            U.pad(warn, 16, 12);
            U.pressable(warn, U.RED_BG, 12);
            warn.setOnClickListener(v -> openTab("overview"));
            pad.addView(warn);
            ((LinearLayout.LayoutParams) warn.getLayoutParams()).bottomMargin = U.dp(this, 16);
        }
        View v;
        switch (tab) {
            case "orders": v = new OrdersManager(this, restaurant.id); break;
            case "foods": v = Catalog.foodsManager(this, restaurant.id); break;
            case "vouchers": {
                LinearLayout l = U.col(this);
                l.addView(U.text(this, "Mã do bạn tạo chỉ dùng được cho đơn của " + restaurant.name + ". Mã bị trừ vào doanh thu của quán.", 14, U.MUTED));
                U.add(l, Catalog.vouchersManager(this, restaurant.id), 16);
                v = l;
                break;
            }
            case "stats": v = new StatsView(this, restaurant.id, "Doanh thu"); break;
            case "info": v = info(); break;
            default: v = overview();
        }
        pad.addView(v);
        ScrollView sv = new ScrollView(this);
        sv.addView(pad);
        content.addView(sv);
    }

    private View info() {
        List<EntityManager.Column> cols = new ArrayList<>();
        cols.add(new EntityManager.Column("Ảnh", r -> Cards.thumb(this, Json.str(r, "image"))));
        cols.add(new EntityManager.Column("Tên nhà hàng", r -> Catalog.bold(this, Json.str(r, "name", ""))));
        cols.add(new EntityManager.Column("Địa chỉ", r -> Catalog.addressCell(this, r)));
        cols.add(new EntityManager.Column("Giờ mở cửa", r -> Catalog.plain(this, Catalog.hoursOf(r).hours != null ? Catalog.hoursOf(r).hours : "Cả ngày")));
        cols.add(new EntityManager.Column("Trạng thái", r -> Catalog.restaurantStatus(this, r)));
        return new EntityManager(this).table("fg_restaurants").title("Thông tin quán").match("id", restaurant.id).noCreate().noDelete()
                .onChanged(() -> { reload(); Store.invalidate(); })
                .fields(Catalog.restaurantFields(false)).columns(cols).start();
    }

    private View overview() {
        LinearLayout root = U.col(this);
        root.addView(U.h2(this, restaurant.name));
        if (restaurant.lat == null) {
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Nhà hàng chưa có vị trí. ");
            int s = b.length();
            b.append("Ghim vị trí trên bản đồ");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
            b.setSpan(new android.text.style.UnderlineSpan(), s, b.length(), 0);
            b.append(" để khách biết quán ở đâu.");
            TextView t = U.text(this, b, 14, U.AMBER_DARK);
            U.pad(t, 16, 12);
            U.pressable(t, U.AMBER_BG, 12);
            t.setOnClickListener(v -> openTab("info"));
            U.add(root, t, 12);
        }
        LinearLayout cards = U.col(this);
        U.add(root, cards, 20);
        Runnable[] render = new Runnable[1];
        long[] today = null;
        long[][] holder = {today};
        render[0] = () -> {
            cards.removeAllViews();
            long[] t = holder[0];
            StoreHours h = StoreHours.of(restaurant);
            List<View> k = new ArrayList<>();
            k.add(card("Doanh thu hôm nay", t != null ? Fmt.money(t[0]) : "…", t != null ? t[1] + " đơn đã giao" : null, false, null));
            k.add(card("Đơn chờ xác nhận", t != null ? String.valueOf(t[2]) : "…", null, t != null && t[2] > 0, () -> openTab("orders")));
            k.add(card("Đánh giá", restaurant.review_count > 0 ? Fmt.fixed1(restaurant.rating) + " ★" : "—", restaurant.review_count + " lượt", false, null));
            LinearLayout st = U.card(U.pad(U.col(this), 16), 16);
            st.addView(U.text(this, "Trạng thái", 12, U.MUTED));
            U.add(st, U.ellipsize(U.text(this, h.open ? "Đang mở cửa" : h.paused ? "Tạm đóng cửa" : "Ngoài giờ (mở " + h.reopens + ")", 15, U.INK, U.XBOLD), 1), 4);
            TextView btn = U.text(this, restaurant.is_open ? "Tạm đóng cửa" : "Mở cửa lại", 12, restaurant.is_open ? U.RED : U.WHITE, U.BOLD);
            btn.setGravity(Gravity.CENTER);
            U.pad(btn, 12, 0);
            if (restaurant.is_open) U.border(btn, U.WHITE, 8, U.BORDER, 1); else U.pressable(btn, U.ORANGE, 8);
            U.click(btn, () -> toggleOpen(btn));
            U.add(st, btn, 8, U.WRAP, U.dp(this, 36));
            k.add(st);
            cards.addView(U.grid(this, 2, k, 12));
        };
        render[0].run();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        long start = c.getTimeInMillis();
        String iso = Iso.format(start);
        Db.from("fg_orders").select("status, total, delivered_at, created_at").eq("restaurant_id", restaurant.id)
                .or("created_at.gte." + iso + ",delivered_at.gte." + iso).rows((list, e) -> {
                    long revenue = 0, delivered = 0, pending = 0;
                    for (JsonObject o : list) {
                        String status = Json.str(o, "status");
                        String da = Json.str(o, "delivered_at");
                        if ("delivered".equals(status) && da != null && Iso.parse(da) >= start) { revenue += (long) Json.num(o, "total", 0); delivered++; }
                        if ("pending".equals(status)) pending++;
                    }
                    holder[0] = new long[]{revenue, delivered, pending};
                    render[0].run();
                });
        Runnable clock = new Runnable() { @Override public void run() { if (cards.isAttachedToWindow()) { render[0].run(); Net.later(this, 60000); } } };
        Net.later(clock, 60000);
        U.add(root, new SubscriptionPanel(this, restaurant, () -> { reload(); Store.invalidate(); }), 16);
        return root;
    }

    private View card(String label, String value, String hint, boolean highlight, Runnable onClick) {
        LinearLayout c = U.col(this);
        U.pad(c, 16);
        if (onClick != null) { U.pressable(c, highlight ? U.ORANGE : U.WHITE, 16); c.setOnClickListener(v -> onClick.run()); }
        else U.bg(c, highlight ? U.ORANGE : U.WHITE, 16);
        c.setElevation(U.dp(this, 1));
        c.addView(U.text(this, label, 12, highlight ? 0xCCFFFFFF : U.MUTED));
        U.add(c, U.ellipsize(U.text(this, value, 20, highlight ? U.WHITE : U.INK, U.XBOLD), 1), 4);
        if (hint != null) U.add(c, U.ellipsize(U.text(this, hint, 11, highlight ? 0xB3FFFFFF : U.SUBTLE), 1), 2);
        return c;
    }

    private void toggleOpen(TextView btn) {
        boolean next = !restaurant.is_open;
        Runnable go = () -> {
            btn.setEnabled(false);
            Db.from("fg_restaurants").update(Json.obj("is_open", next)).eq("id", restaurant.id).run((d, err) -> {
                btn.setEnabled(true);
                if (err != null) { AppState.get().toastError(err); return; }
                AppState.get().toast(next ? "Đã mở cửa nhận đơn" : "Đã tạm đóng cửa");
                reload();
                Store.invalidate();
            });
        };
        if (!next) Dialogs.confirm(this, "Tạm đóng cửa? Khách sẽ không đặt được đơn mới cho tới khi bạn mở lại.", go);
        else go.run();
    }
}
