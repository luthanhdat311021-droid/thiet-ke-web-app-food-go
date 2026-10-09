package com.foodgo.nativeapp.admin;

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
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.Profile;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** app/admin/page.tsx (RequireAuth admin) */
public class AdminActivity extends BaseActivity implements AppState.Listener {
    private static final String[][] TABS = {
            {"overview", "Tổng quan"}, {"subscriptions", "Phí duy trì"}, {"stats", "Thống kê đơn"}, {"orders", "Đơn hàng"},
            {"foods", "Món ăn"}, {"vouchers", "Mã giảm giá"}, {"restaurants", "Nhà hàng"}, {"reviews", "Đánh giá"},
            {"categories", "Danh mục"}, {"users", "Người dùng"}, {"audit", "Nhật ký bảo mật"}};
    private static final int[] ICONS = {R.drawable.ic_dashboard, R.drawable.ic_wallet, R.drawable.ic_bar_chart, R.drawable.ic_receipt,
            R.drawable.ic_utensils, R.drawable.ic_ticket, R.drawable.ic_store, R.drawable.ic_star, R.drawable.ic_grid, R.drawable.ic_users, R.drawable.ic_scroll};

    private FrameLayout root;
    private LinearLayout strip;
    private FrameLayout content;
    private String tab = "overview";
    private String state = "";

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

    /** RequireAuth admin */
    @Override
    public void onAppState() {
        AppState s = AppState.get();
        String next;
        if (s.authLoading) next = "loading";
        else if (!s.signedIn()) next = "out";
        else if (s.profile == null) next = "loading";
        else next = s.profile.isAdmin() ? "admin" : "denied";
        if (next.equals(state)) return;
        state = next;
        root.removeAllViews();
        switch (next) {
            case "loading": root.addView(U.spinner(this)); break;
            case "out":
                if (!s.signingOut) App.navigate("/login?next=" + Uri.encode("/admin"));
                finish();
                break;
            case "denied": root.addView(denied(s)); break;
            default: buildAdmin();
        }
    }

    private View denied(AppState s) {
        LinearLayout c = U.col(this);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        U.pad(c, 20, 96, 20, 32);
        TextView h = U.h2(this, "Không có quyền truy cập");
        h.setGravity(Gravity.CENTER);
        c.addView(h);
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Tài khoản ");
        int st = b.length();
        b.append(s.profile.full_name != null && !s.profile.full_name.isEmpty() ? s.profile.full_name : s.email());
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, b.length(), 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(U.INK), st, b.length(), 0);
        b.append(" không phải quản trị viên.");
        TextView t = U.text(this, b, 14, U.MUTED);
        t.setGravity(Gravity.CENTER);
        U.add(c, t, 12);
        PButton home = new PButton(this, "Về trang chủ", PButton.PRIMARY);
        home.onClick(() -> { App.navigate("/"); finish(); });
        U.add(c, home, 32, U.MATCH, U.dp(this, 48));
        PButton other = new PButton(this, "Đăng nhập tài khoản khác", PButton.OUTLINE);
        other.textColor(U.MUTED);
        other.onClick(() -> { AppState.get().signOut(); finish(); });
        U.add(c, other, 12, U.MATCH, U.dp(this, 48));
        return c;
    }

    private void buildAdmin() {
        LinearLayout col = U.col(this);
        // mobile: horizontal tab strip under the status bar
        strip = U.row(this);
        U.pad(strip, 12, 8);
        LinearLayout stripBox = U.col(this);
        stripBox.setBackgroundColor(U.WHITE);
        stripBox.addView(U.hscroll(this, strip));
        stripBox.addView(U.divider(this, U.LINE));
        col.addView(stripBox);
        content = new FrameLayout(this);
        col.addView(content, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        root.addView(col);
        renderStrip();
        showTab();
    }

    private TextView stripItem(int icon, String label, boolean active, int color, Runnable r) {
        TextView t = U.iconText(this, icon, 16, label, 14, active ? U.ORANGE : color, active ? U.BOLD : U.NORMAL);
        U.pad(t, 12, 10);
        U.pressable(t, active ? U.PEACH : U.WHITE, 12);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void renderStrip() {
        strip.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            String id = TABS[i][0];
            U.add(strip, stripItem(ICONS[i], TABS[i][1], tab.equals(id), U.MUTED, () -> { tab = id; renderStrip(); showTab(); }), i == 0 ? 0 : 4, U.WRAP, U.WRAP);
        }
        U.add(strip, stripItem(R.drawable.ic_arrow_back, "Về ứng dụng", false, U.MUTED, () -> { App.navigate("/"); finish(); }), 4, U.WRAP, U.WRAP);
        U.add(strip, stripItem(R.drawable.ic_logout, "Đăng xuất", false, U.RED, () -> { AppState.get().signOut(); finish(); }), 4, U.WRAP, U.WRAP);
    }

    void openTab(String id) { tab = id; renderStrip(); showTab(); }

    private void showTab() {
        content.removeAllViews();
        View v;
        switch (tab) {
            case "stats": v = new StatsView(this, null, "Thống kê đơn hàng"); break;
            case "subscriptions": v = Subscriptions.admin(this); break;
            case "vouchers": v = Catalog.vouchersManager(this, null); break;
            case "orders": v = new OrdersManager(this, null); break;
            case "foods": v = Catalog.foodsManager(this, null); break;
            case "restaurants": v = restaurantsAdmin(); break;
            case "categories": v = categoriesAdmin(); break;
            case "users": v = usersAdmin(); break;
            case "reviews": v = reviewsAdmin(); break;
            case "audit": v = new AuditLog(this); break;
            default: v = overview();
        }
        ScrollView s = new ScrollView(this);
        LinearLayout pad = U.col(this);
        U.pad(pad, 16, 16, 16, 24);
        pad.addView(v);
        s.addView(pad);
        content.addView(s);
    }

    // ------------------------------------------------------------------ overview

    private static String dayKey(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.DAY_OF_YEAR);
    }

    private View overview() {
        LinearLayout root = U.col(this);
        root.addView(U.text(this, "Tổng quan", 22, U.INK, U.XBOLD));
        U.add(root, Subscriptions.summary(this, () -> openTab("subscriptions")), 20);
        U.add(root, U.text(this, "Đơn hàng toàn hệ thống", 16, U.INK, U.XBOLD), 24);
        LinearLayout body = U.col(this);
        body.addView(U.spinner(this));
        root.addView(body);
        long sinceMs = System.currentTimeMillis() - 30 * 86400000L;
        String since = Iso.format(sinceMs);
        int[] counts = {0, 0, 0};
        List<Order>[] orders = new List[]{null};
        int[] pending = {4};
        Runnable done = () -> {
            if (--pending[0] > 0) return;
            renderOverview(body, orders[0], counts, sinceMs);
        };
        // orders placed in the window plus older ones delivered in it (revenue counts by delivery day)
        Db.from("fg_orders").select("*").or("created_at.gte." + since + ",delivered_at.gte." + since).order("created_at", false)
                .list(Order.class, (l, e) -> { orders[0] = l; done.run(); });
        Db.from("fg_foods").select("id").runCount((n, e) -> { counts[0] = n; done.run(); });
        Db.from("fg_restaurants").select("id").runCount((n, e) -> { counts[1] = n; done.run(); });
        Db.from("fg_profiles").select("id").runCount((n, e) -> { counts[2] = n; done.run(); });
        U.add(root, restaurantSwitches(), 16);
        return root;
    }

    private void renderOverview(LinearLayout body, List<Order> orders, int[] counts, long sinceMs) {
        body.removeAllViews();
        String today = dayKey(System.currentTimeMillis());
        long todayRevenue = 0, monthRevenue = 0, inProgress = 0;
        int deliveredToday = 0, placedToday = 0, pendingCount = 0;
        long[] days = new long[7];
        String[] labels = new String[7];
        long now = System.currentTimeMillis();
        for (int i = 0; i < 7; i++) {
            long d = now - (6 - i) * 86400000L;
            // short labels so 7 columns fit a phone: "T2" … "CN", today = "Nay"
            labels[i] = i == 6 ? "Nay" : Fmt.weekdayShort(d);
        }
        for (Order o : orders) {
            // revenue = delivered orders only, dated by when they were delivered
            if ("delivered".equals(o.status)) {
                long dd = Iso.parse(o.delivered_at != null ? o.delivered_at : o.updated_at);
                if (dayKey(dd).equals(today)) { todayRevenue += o.total; deliveredToday++; }
                if (dd >= sinceMs) monthRevenue += o.total;
                for (int i = 0; i < 7; i++) if (dayKey(dd).equals(dayKey(now - (6 - i) * 86400000L))) days[i] += o.total;
            }
            if (!"cancelled".equals(o.status) && dayKey(Iso.parse(o.created_at)).equals(today)) placedToday++;
            if (!"delivered".equals(o.status) && !"cancelled".equals(o.status)) inProgress += o.total;
            if ("pending".equals(o.status)) pendingCount++;
        }
        List<View> k = new ArrayList<>();
        k.add(stat("Doanh số hôm nay", Fmt.money(todayRevenue), deliveredToday + " đơn đã giao", false));
        k.add(stat("Đơn đặt hôm nay", String.valueOf(placedToday), null, false));
        k.add(stat("Doanh số 30 ngày", Fmt.money(monthRevenue), "Tiền món các nhà hàng, đơn đã giao", false));
        k.add(stat("Đơn chờ xác nhận", String.valueOf(pendingCount), null, pendingCount > 0));
        U.add(body, U.grid(this, 2, k, 12), 12);
        LinearLayout chart = U.card(U.pad(U.col(this), 16), 16);
        chart.addView(U.text(this, "Doanh số đơn hàng 7 ngày gần nhất", 16, U.INK, U.XBOLD));
        U.add(chart, U.text(this, "Tính theo ngày giao xong", 12, U.SUBTLE), 2);
        U.add(chart, Subscriptions.bars(this, labels, days, 192, "Nay"), 20);
        U.add(body, chart, 16);
        U.add(body, stat("Đơn đang xử lý (chưa tính doanh thu)", Fmt.money(inProgress), null, false), 16);
        List<View> k2 = new ArrayList<>();
        k2.add(stat("Món ăn", String.valueOf(counts[0]), null, false));
        k2.add(stat("Người dùng", String.valueOf(counts[2]), null, false));
        U.add(body, U.grid(this, 2, k2, 12), 12);
    }

    private View stat(String label, String value, String hint, boolean highlight) {
        LinearLayout c = U.col(this);
        U.pad(c, 16);
        U.bg(c, highlight ? U.ORANGE : U.WHITE, 16);
        c.setElevation(U.dp(this, 1));
        c.addView(U.text(this, label, 12, highlight ? 0xCCFFFFFF : U.MUTED));
        U.add(c, U.ellipsize(U.text(this, value, 20, highlight ? U.WHITE : U.INK, U.XBOLD), 1), 4);
        if (hint != null) U.add(c, U.ellipsize(U.text(this, hint, 11, highlight ? 0xB3FFFFFF : U.SUBTLE), 1), 2);
        return c;
    }

    /** One-tap "tạm đóng cửa" per restaurant for busy moments; regular hours are set under "Nhà hàng". */
    private View restaurantSwitches() {
        LinearLayout box = U.col(this);
        Runnable[] tick = new Runnable[1];
        List<Restaurant>[] list = new List[]{null};
        Runnable render = () -> {
            box.removeAllViews();
            if (list[0] == null || list[0].isEmpty()) return;
            LinearLayout c = U.card(U.pad(U.col(this), 16), 16);
            LinearLayout head = U.row(this);
            U.addFlex(head, U.text(this, "Nhà hàng", 16, U.INK, U.XBOLD), 0);
            int open = 0;
            for (Restaurant r : list[0]) if (r.is_active && StoreHours.of(r).open) open++;
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(String.valueOf(open));
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            b.setSpan(new android.text.style.ForegroundColorSpan(U.INK), 0, b.length(), 0);
            b.append(" / ").append(String.valueOf(list[0].size())).append(" đang mở cửa");
            head.addView(U.text(this, b, 12, U.MUTED));
            c.addView(head);
            for (int i = 0; i < list[0].size(); i++) {
                Restaurant r = list[0].get(i);
                StoreHours h = StoreHours.of(r);
                String status = !r.is_active ? "Đang ẩn" : h.open ? "Đang mở cửa" : h.paused ? "Tạm đóng cửa" : "Ngoài giờ (mở " + h.reopens + ")";
                LinearLayout row = U.row(this);
                U.pad(row, 0, 10);
                View dot = new View(this);
                U.bg(dot, r.is_active && h.open ? U.GREEN : U.DOT_OFF, 999);
                row.addView(dot, new LinearLayout.LayoutParams(U.dp(this, 10), U.dp(this, 10)));
                LinearLayout info = U.col(this);
                info.addView(U.ellipsize(U.text(this, r.name, 14, U.INK, U.BOLD), 1));
                info.addView(U.text(this, status + (h.hours != null ? " • " + h.hours : ""), 12, U.MUTED));
                U.addFlex(row, info, 12);
                TextView btn = U.text(this, r.is_open ? "Tạm đóng" : "Mở lại", 12, r.is_open ? U.RED : U.WHITE, U.BOLD);
                btn.setGravity(Gravity.CENTER);
                U.pad(btn, 12, 0);
                if (r.is_open) U.border(btn, U.WHITE, 8, U.BORDER, 1); else U.pressable(btn, U.ORANGE, 8);
                if (!r.is_active) btn.setAlpha(0.4f);
                else U.click(btn, () -> {
                    boolean next = !r.is_open;
                    Runnable go = () -> {
                        btn.setEnabled(false);
                        Db.from("fg_restaurants").update(Json.obj("is_open", next)).eq("id", r.id).run((d, err) -> {
                            btn.setEnabled(true);
                            if (err != null) { AppState.get().toastError(err); return; }
                            r.is_open = next;
                            Store.invalidate();
                            AppState.get().toast(next ? r.name + " đã mở cửa nhận đơn" : r.name + " đã tạm đóng cửa");
                            tick[0].run();
                        });
                    };
                    if (!next) Dialogs.confirm(this, "Tạm đóng cửa " + r.name + "? Khách sẽ không đặt được đơn mới cho tới khi bạn mở lại.", go);
                    else go.run();
                });
                U.add(row, btn, 12, U.WRAP, U.dp(this, 36));
                if (i > 0) c.addView(U.divider(this, U.SOFT));
                c.addView(row);
            }
            box.addView(c);
        };
        tick[0] = render;
        Store.restaurants(true, (l, e) -> { list[0] = l; render.run(); });
        Runnable clock = new Runnable() { @Override public void run() { if (box.isAttachedToWindow()) { render.run(); Net.later(this, 60000); } } };
        Net.later(clock, 60000);
        return box;
    }

    // ------------------------------------------------------------------ restaurants / categories

    private View restaurantsAdmin() {
        List<EntityManager.Column> cols = new ArrayList<>();
        cols.add(new EntityManager.Column("Ảnh", r -> Cards.thumb(this, Json.str(r, "image"))));
        cols.add(new EntityManager.Column("Tên nhà hàng", r -> Catalog.bold(this, Json.str(r, "name", ""))));
        cols.add(new EntityManager.Column("Địa chỉ", r -> Catalog.addressCell(this, r)));
        cols.add(new EntityManager.Column("Giờ mở cửa", r -> Catalog.plain(this, Catalog.hoursOf(r).hours != null ? Catalog.hoursOf(r).hours : "Cả ngày")));
        cols.add(new EntityManager.Column("Phí duy trì", r -> {
            if (Json.str(r, "owner_id") == null) return U.text(this, "Admin quản lý", 12, U.SUBTLE);
            StoreHours.Subscription s = StoreHours.subscriptionOf(Json.str(r, "owner_id"), Json.str(r, "paid_until"));
            return s.active ? U.text(this, "Còn " + s.daysLeft + " ngày", 12, U.INK) : U.text(this, "Hết hạn", 12, U.WARN, U.BOLD);
        }));
        cols.add(new EntityManager.Column("Trạng thái", r -> Catalog.restaurantStatus(this, r)));
        return new EntityManager(this).table("fg_restaurants").title("Nhà hàng").orderBy("id")
                .deleteWarning("Toàn bộ món ăn của nhà hàng này cũng bị xóa (đơn hàng cũ vẫn được giữ). Muốn ngừng tạm thời thì nên tắt “Hiển thị nhà hàng”.")
                .onChanged(Store::invalidate).fields(Catalog.restaurantFields(true)).columns(cols).start();
    }

    private View categoriesAdmin() {
        return new EntityManager(this).table("fg_categories").title("Danh mục").orderBy("sort")
                .fields(Arrays.asList(
                        new EntityManager.Field("name", "Tên danh mục", "text").req(),
                        new EntityManager.Field("sort", "Thứ tự", "number").req(),
                        new EntityManager.Field("image", "Ảnh", "image")))
                .columns(Arrays.asList(
                        new EntityManager.Column("Ảnh", r -> Cards.thumb(this, Json.str(r, "image"))),
                        new EntityManager.Column("Tên", r -> Catalog.bold(this, Json.str(r, "name", ""))),
                        new EntityManager.Column("Thứ tự", r -> Catalog.plain(this, Json.str(r, "sort", "")))))
                .start();
    }

    // ------------------------------------------------------------------ reviews

    private View reviewsAdmin() {
        LinearLayout root = U.col(this);
        LinearLayout head = U.row(this);
        LinearLayout titles = U.col(this);
        titles.addView(U.text(this, "Đánh giá", 22, U.INK, U.XBOLD));
        TextView avgView = U.text(this, "", 14, U.MUTED);
        titles.addView(avgView);
        U.addFlex(head, titles, 0);
        Select stars = new Select(this).title("Lọc theo số sao").option("all", "Tất cả");
        for (int n = 5; n >= 1; n--) stars.option(String.valueOf(n), n + " sao");
        head.addView(stars, new LinearLayout.LayoutParams(U.WRAP, U.dp(this, 40)));
        root.addView(head);
        LinearLayout list = U.col(this);
        U.add(root, list, 20);
        list.addView(U.spinner(this));
        List<JsonObject>[] reviews = new List[]{null};
        Runnable[] render = new Runnable[1];
        render[0] = () -> {
            list.removeAllViews();
            List<JsonObject> all = reviews[0];
            if (all == null) { list.addView(U.spinner(this)); return; }
            if (!all.isEmpty()) {
                double sum = 0;
                for (JsonObject r : all) sum += Json.num(r, "rating", 0);
                android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Trung bình ");
                int s = b.length();
                b.append(Fmt.fixed1(sum / all.size())).append(" ★");
                b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
                b.setSpan(new android.text.style.ForegroundColorSpan(U.INK), s, b.length(), 0);
                b.append(" từ ").append(String.valueOf(all.size())).append(" đánh giá");
                avgView.setText(b);
            } else avgView.setText("");
            int n = 0;
            for (JsonObject r : all) {
                if (!"all".equals(stars.value()) && (int) Json.num(r, "rating", 0) != Integer.parseInt(stars.value())) continue;
                LinearLayout c = U.row(this);
                c.setGravity(Gravity.TOP);
                U.pad(c, 16);
                U.card(c, 16);
                JsonObject food = Json.obj(r, "food");
                if (food != null && Json.str(food, "image") != null) c.addView(U.img(this, Json.str(food, "image"), 48, 48, 8));
                LinearLayout info = U.col(this);
                info.addView(U.ellipsize(U.text(this, food != null ? Json.str(food, "name", "Món đã xóa") : "Món đã xóa", 14, U.INK, U.BOLD), 1));
                info.addView(U.text(this, Fmt.dateTime(Json.str(r, "updated_at")), 12, U.SUBTLE));
                LinearLayout sr = U.row(this);
                sr.addView(Cards.stars(this, (int) Json.num(r, "rating", 0), 14, null));
                U.add(sr, U.text(this, Json.str(r, "reviewer_name", ""), 12, U.MUTED), 8, U.WRAP, U.WRAP);
                U.add(info, sr, 4);
                String comment = Json.str(r, "comment");
                if (comment != null && !comment.isEmpty()) U.add(info, U.text(this, comment, 14, U.BODY), 6);
                U.addFlex(c, info, food != null && Json.str(food, "image") != null ? 12 : 0);
                android.widget.ImageView del = U.icon(this, R.drawable.ic_delete, 16, U.MUTED);
                U.pad(del, 10);
                del.setContentDescription("Xóa đánh giá");
                // removing a review also recalculates the dish/shop rating (DB trigger)
                U.click(del, () -> Dialogs.confirm(this, "Xóa đánh giá của " + Json.str(r, "reviewer_name") + "? Điểm của món sẽ được tính lại.", () ->
                        Db.from("fg_reviews").delete().eq("id", Json.str(r, "id")).run((d, err) -> {
                            if (err != null) { AppState.get().toastError(err); return; }
                            reviews[0].remove(r);
                            render[0].run();
                        })));
                c.addView(del, new LinearLayout.LayoutParams(U.dp(this, 36), U.dp(this, 36)));
                U.add(list, c, n++ == 0 ? 0 : 12);
            }
            if (n == 0) {
                TextView t = U.text(this, "Chưa có đánh giá nào", 14, U.SUBTLE);
                t.setGravity(Gravity.CENTER);
                U.pad(t, 0, 48);
                U.card(t, 16);
                list.addView(t);
            }
        };
        stars.onChange(v -> render[0].run());
        Db.from("fg_reviews").select("id, reviewer_name, rating, comment, updated_at, order_id, food:fg_foods(name, image)")
                .order("updated_at", false).limit(200).rows((l, e) -> { reviews[0] = new ArrayList<>(l); render[0].run(); });
        return root;
    }

    // ------------------------------------------------------------------ users

    private View usersAdmin() {
        LinearLayout root = U.col(this);
        root.addView(U.text(this, "Người dùng", 22, U.INK, U.XBOLD));
        LinearLayout list = U.col(this);
        U.add(root, list, 16);
        list.addView(U.spinner(this));
        Runnable[] load = new Runnable[1];
        load[0] = () -> Db.from("fg_profiles").select("*").order("created_at", false).list(Profile.class, (users, e) ->
                Db.from("fg_restaurants").select("name, owner_id").notNull("owner_id").rows((rows, e2) -> {
                    // user id → the restaurant they own
                    Map<String, String> owned = new HashMap<>();
                    for (JsonObject r : rows) owned.put(Json.str(r, "owner_id"), Json.str(r, "name"));
                    list.removeAllViews();
                    String me = AppState.get().userId();
                    for (int i = 0; i < users.size(); i++) {
                        Profile p = users.get(i);
                        LinearLayout c = U.row(this);
                        U.pad(c, 16);
                        U.card(c, 16);
                        TextView ini = U.text(this, Fmt.initials(p.full_name != null && !p.full_name.isEmpty() ? p.full_name : "?"), 14, U.ORANGE, U.BOLD);
                        ini.setGravity(Gravity.CENTER);
                        U.bg(ini, U.PEACH3, 999);
                        c.addView(ini, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 40)));
                        LinearLayout info = U.col(this);
                        info.addView(U.ellipsize(U.text(this, p.full_name != null && !p.full_name.isEmpty() ? p.full_name : "—", 14, U.INK, U.BOLD), 1));
                        info.addView(U.ellipsize(U.text(this, (p.phone != null && !p.phone.isEmpty() ? p.phone : "Chưa có SĐT") + " • " + Fmt.dateTime(p.created_at), 12, U.SUBTLE), 1));
                        View role = p.isAdmin() ? Cards.yesNo(this, true, "Admin", "")
                                : owned.containsKey(p.id) ? U.pill(this, "Chủ: " + owned.get(p.id), U.PEACH, U.ORANGE)
                                : Cards.yesNo(this, false, "", "Khách hàng");
                        U.add(info, role, 4, U.WRAP, U.WRAP);
                        U.addFlex(c, info, 12);
                        LinearLayout actions = U.col(this);
                        actions.setGravity(Gravity.END);
                        actions.addView(Subscriptions.grantButton(this, p, owned.get(p.id), load[0]));
                        if (!p.id.equals(me)) {
                            TextView b = U.text(this, p.isAdmin() ? "Gỡ admin" : "Cấp admin", 12, U.ORANGE, U.BOLD);
                            U.pad(b, 12, 8);
                            U.border(b, U.WHITE, 8, U.BORDER, 1);
                            String role2 = p.isAdmin() ? "customer" : "admin";
                            U.click(b, () -> Dialogs.confirm(this, "admin".equals(role2) ? "Cấp quyền admin cho " + p.full_name + "?" : "Gỡ quyền admin của " + p.full_name + "?", () ->
                                    Db.from("fg_profiles").update(Json.obj("role", role2)).eq("id", p.id).run((d, err) -> {
                                        if (err != null) { AppState.get().toastError(err); return; }
                                        load[0].run();
                                    })));
                            U.add(actions, b, 4, U.WRAP, U.WRAP);
                        }
                        U.add(c, actions, 8, U.WRAP, U.WRAP);
                        U.add(list, c, i == 0 ? 0 : 12);
                    }
                }));
        load[0].run();
        return root;
    }
}
