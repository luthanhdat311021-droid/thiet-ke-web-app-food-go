package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.OrderItem;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * components/admin/stats.tsx. Revenue counts on the day an order was delivered (same rule as "Tổng quan");
 * order counts use the day it was placed. Every number compares with the period before.
 */
public class StatsView extends LinearLayout {
    private static final long DAY = 86400000L;
    private final Activity a;
    private final Long restaurantId;
    private int range = 30;
    private int generation;
    private final LinearLayout tabs, body;

    public StatsView(Activity a, Long restaurantId, String title) {
        super(a);
        this.a = a;
        this.restaurantId = restaurantId;
        setOrientation(VERTICAL);
        addView(U.text(a, title, 22, U.INK, U.XBOLD));
        TextView sub = U.text(a, "", 12, U.SUBTLE);
        sub.setTag("sub");
        U.add(this, sub, 2);
        tabs = U.row(a);
        U.pad(tabs, 4);
        U.card(tabs, 12);
        U.add(this, tabs, 12, U.WRAP, U.WRAP);
        body = U.col(a);
        addView(body);
        renderTabs();
        load();
    }

    private void renderTabs() {
        tabs.removeAllViews();
        for (int r : new int[]{7, 30, 90}) {
            TextView t = U.text(a, r + " ngày", 14, r == range ? U.WHITE : U.MUTED, U.BOLD);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 16, 0);
            U.pressable(t, r == range ? U.ORANGE : U.WHITE, 8);
            U.click(t, () -> { range = r; renderTabs(); load(); });
            tabs.addView(t, new LayoutParams(U.WRAP, U.dp(a, 36)));
        }
        ((TextView) findViewWithTag("sub")).setText("So với " + range + " ngày liền trước");
    }

    private static long startOfDay(long t) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(t);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    // fetch two periods (current + the one before) so every number can show its trend
    private void load() {
        int gen = ++generation;
        body.removeAllViews();
        body.addView(U.spinner(a));
        String since = Iso.format(startOfDay(System.currentTimeMillis()) - (range * 2L - 1) * DAY);
        List<Order> all = new ArrayList<>();
        fetchPage(gen, since, 0, all);
    }

    private void fetchPage(int gen, String since, int from, List<Order> all) {
        Db q = Db.from("fg_orders").select("*, order_items:fg_order_items(*)").or("created_at.gte." + since + ",delivered_at.gte." + since);
        if (restaurantId != null) q.eq("restaurant_id", restaurantId);
        q.order("id").range(from, from + 999).list(Order.class, (page, err) -> {
            if (gen != generation) return;
            all.addAll(page);
            if (page.size() == 1000) { fetchPage(gen, since, from + 1000, all); return; }
            Db r = restaurantId != null
                    ? Db.from("fg_reviews").select("rating, updated_at, food:fg_foods!inner(restaurant_id)").eq("food.restaurant_id", restaurantId).gte("updated_at", since)
                    : Db.from("fg_reviews").select("rating, updated_at").gte("updated_at", since);
            r.rows((reviews, e2) -> { if (gen == generation) render(all, reviews); });
        });
    }

    private static class Period {
        List<Order> delivered = new ArrayList<>(), placed = new ArrayList<>();
        int cancelled, reviews, voucherOrders;
        long revenue, aov, discount;
        double cancelRate, rating;
    }

    private static long deliveredAt(Order o) { return Iso.parse(o.delivered_at != null ? o.delivered_at : o.updated_at); }

    private Period period(List<Order> orders, List<JsonObject> reviews, long from, long to) {
        Period p = new Period();
        for (Order o : orders) {
            if ("delivered".equals(o.status) && deliveredAt(o) >= from && deliveredAt(o) < to) p.delivered.add(o);
            long c = Iso.parse(o.created_at);
            if (c >= from && c < to) p.placed.add(o);
        }
        for (Order o : p.placed) if ("cancelled".equals(o.status)) p.cancelled++;
        for (Order o : p.delivered) { p.revenue += o.total; p.discount += o.discount; if (o.voucher_code != null) p.voucherOrders++; }
        p.aov = p.delivered.isEmpty() ? 0 : Math.round((double) p.revenue / p.delivered.size());
        p.cancelRate = p.placed.isEmpty() ? 0 : (double) p.cancelled / p.placed.size();
        double sum = 0;
        for (JsonObject r : reviews) {
            long t = Iso.parse(Json.str(r, "updated_at"));
            if (t >= from && t < to) { p.reviews++; sum += Json.num(r, "rating", 0); }
        }
        p.rating = p.reviews > 0 ? sum / p.reviews : 0;
        return p;
    }

    /** relative change; null when there's nothing to compare against */
    private static Double trend(double cur, double prev) { return prev != 0 ? (cur - prev) / prev : null; }

    private void render(List<Order> orders, List<JsonObject> reviews) {
        body.removeAllViews();
        long end = startOfDay(System.currentTimeMillis()) + DAY;
        long start = end - range * DAY;
        Period cur = period(orders, reviews, start, end);
        Period prev = period(orders, reviews, start - range * DAY, start);

        List<View> kpis = new ArrayList<>();
        kpis.add(kpi("Doanh thu", Fmt.money(cur.revenue), null, trend(cur.revenue, prev.revenue), false));
        kpis.add(kpi("Đơn hoàn thành", String.valueOf(cur.delivered.size()), null, trend(cur.delivered.size(), prev.delivered.size()), false));
        kpis.add(kpi("Giá trị TB / đơn", Fmt.money(cur.aov), null, trend(cur.aov, prev.aov), false));
        kpis.add(kpi("Tỉ lệ hủy đơn", Fmt.pct(cur.cancelRate), cur.cancelled + " / " + cur.placed.size() + " đơn đặt", trend(cur.cancelRate, prev.cancelRate), true));
        U.add(body, U.grid(a, 2, kpis, 12), 20);

        // daily bars (weekly for 90 days, so bars stay readable)
        int bucket = range == 90 ? 7 : 1;
        List<Point> series = new ArrayList<>();
        for (long t = start; t < end; t += bucket * DAY) {
            long to = Math.min(end, t + bucket * DAY);
            long sum = 0;
            int count = 0;
            for (Order o : cur.delivered) if (deliveredAt(o) >= t && deliveredAt(o) < to) { sum += o.total; count++; }
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(t);
            String label = bucket > 1 ? Fmt.dayMonth(t) : range == 7 ? Fmt.weekdayShort(t) : String.valueOf(c.get(Calendar.DAY_OF_MONTH));
            String title = bucket > 1 ? Fmt.dayMonth(t) + " – " + Fmt.dayMonth(to - DAY) : Fmt.weekdayLong(t);
            series.add(new Point(label, title, sum, count));
        }
        U.add(body, card("Doanh thu theo ngày", range == 90 ? "Gộp theo tuần • tính theo ngày giao xong" : "Tính theo ngày giao xong",
                new ColumnChart(a, series, Fmt::money, p -> p.orders + " đơn", false)), 16);

        // per restaurant
        Map<String, long[]> rmap = new LinkedHashMap<>();
        for (Order o : cur.delivered) { long[] v = rmap.computeIfAbsent(o.restaurant_name, k -> new long[2]); v[0]++; v[1] += o.total; }
        List<Map.Entry<String, long[]>> rs = new ArrayList<>(rmap.entrySet());
        rs.sort((x, y) -> Long.compare(y.getValue()[1], x.getValue()[1]));
        if (rs.size() > 1) {
            LinearLayout l = U.col(a);
            for (int i = 0; i < rs.size(); i++) {
                Map.Entry<String, long[]> e = rs.get(i);
                U.add(l, ranked(null, e.getKey(), Fmt.money(e.getValue()[1]) + " • " + e.getValue()[0] + " đơn • " + Fmt.pct(cur.revenue > 0 ? (double) e.getValue()[1] / cur.revenue : 0),
                        (double) e.getValue()[1] / rs.get(0).getValue()[1]), i == 0 ? 0 : 12);
            }
            U.add(body, card("Doanh thu theo nhà hàng", "Đơn đã giao", l), 16);
        }

        // top foods
        Map<String, Object[]> foods = new LinkedHashMap<>();
        for (Order o : cur.delivered) for (OrderItem i : o.items()) {
            String key = i.food_id != null ? "#" + i.food_id : i.name;
            Object[] f = foods.computeIfAbsent(key, k -> new Object[]{i.name, 0L, 0L});
            f[1] = (Long) f[1] + i.qty;
            f[2] = (Long) f[2] + (long) i.qty * i.price;
        }
        List<Object[]> top = new ArrayList<>(foods.values());
        top.sort((x, y) -> { int c = Long.compare((Long) y[1], (Long) x[1]); return c != 0 ? c : Long.compare((Long) y[2], (Long) x[2]); });
        if (top.size() > 8) top = top.subList(0, 8);
        LinearLayout tf = U.col(a);
        if (top.isEmpty()) tf.addView(emptyText());
        for (int i = 0; i < top.size(); i++) {
            Object[] f = top.get(i);
            U.add(tf, ranked((i + 1) + ".", (String) f[0], f[1] + " phần • " + Fmt.money((Long) f[2]), (double) (Long) f[1] / (Long) top.get(0)[1]), i == 0 ? 0 : 12);
        }
        U.add(body, card("Món bán chạy", "Theo số phần đã giao", tf), 16);

        // peak hours
        List<Point> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int n = 0;
            for (Order o : cur.placed) {
                if ("cancelled".equals(o.status)) continue;
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(Iso.parse(o.created_at));
                if (c.get(Calendar.HOUR_OF_DAY) == h) n++;
            }
            hours.add(new Point(h % 3 == 0 ? h + "h" : "", h + ":00 – " + h + ":59", n, 0));
        }
        U.add(body, card("Giờ cao điểm", "Số đơn đặt theo giờ trong ngày", new ColumnChart(a, hours, v -> v + " đơn", null, true)), 16);

        // payment methods
        LinearLayout pm = U.col(a);
        boolean any = false;
        String[] methods = {"momo", "qr", "cod"};
        for (int i = 0; i < methods.length; i++) {
            int count = 0;
            long rev = 0;
            for (Order o : cur.delivered) if (methods[i].equals(o.payment_method)) { count++; rev += o.total; }
            if (count > 0) any = true;
            double share = cur.delivered.isEmpty() ? 0 : (double) count / cur.delivered.size();
            U.add(pm, ranked(null, Fmt.paymentLabel(methods[i]), count + " đơn • " + Fmt.money(rev) + " • " + Fmt.pct(share), share), i == 0 ? 0 : 12);
        }
        if (!any) { pm.removeAllViews(); pm.addView(emptyText()); }
        U.add(body, card("Phương thức thanh toán", "Đơn đã giao", pm), 16);

        // promotions & reviews
        LinearLayout promo = U.col(a);
        List<View> cells = new ArrayList<>();
        cells.add(stat("Tiền đã giảm cho khách", Fmt.money(cur.discount)));
        cells.add(stat("Đơn dùng mã giảm giá", String.valueOf(cur.voucherOrders)));
        cells.add(stat("Điểm đánh giá TB", cur.reviews > 0 ? Fmt.fixed1(cur.rating) + " ★" : "—"));
        cells.add(stat("Lượt đánh giá mới", String.valueOf(cur.reviews)));
        promo.addView(U.grid(a, 2, cells, 16));
        Map<String, long[]> vmap = new LinkedHashMap<>();
        for (Order o : cur.delivered) if (o.voucher_code != null) { long[] v = vmap.computeIfAbsent(o.voucher_code, k -> new long[2]); v[0]++; v[1] += o.discount; }
        if (!vmap.isEmpty()) {
            List<Map.Entry<String, long[]>> vs = new ArrayList<>(vmap.entrySet());
            vs.sort((x, y) -> Long.compare(y.getValue()[0], x.getValue()[0]));
            LinearLayout table = U.col(a);
            table.addView(tableRow("Mã", "Lượt dùng", "Đã giảm", true));
            for (Map.Entry<String, long[]> e : vs) {
                table.addView(U.divider(a, U.SOFT));
                table.addView(tableRow(e.getKey(), String.valueOf(e.getValue()[0]), Fmt.money(e.getValue()[1]), false));
            }
            U.add(promo, table, 20);
        }
        U.add(body, card("Khuyến mãi & đánh giá", null, promo), 16);
    }

    private View tableRow(String a1, String b1, String c1, boolean head) {
        LinearLayout r = U.row(a);
        U.pad(r, 0, 8);
        TextView x = U.text(a, a1, head ? 12 : 14, head ? U.SUBTLE : U.INK, head ? U.SEMI : U.BOLD);
        if (!head) x.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        U.addFlex(r, x, 0);
        TextView y = U.text(a, b1, head ? 12 : 14, head ? U.SUBTLE : U.INK, head ? U.SEMI : U.NORMAL);
        y.setGravity(Gravity.END);
        r.addView(y, new LayoutParams(U.dp(a, 80), U.WRAP));
        TextView z = U.text(a, c1, head ? 12 : 14, head ? U.SUBTLE : U.INK, head ? U.SEMI : U.NORMAL);
        z.setGravity(Gravity.END);
        r.addView(z, new LayoutParams(U.dp(a, 100), U.WRAP));
        return r;
    }

    private View stat(String label, String value) {
        LinearLayout c = U.col(a);
        c.addView(U.text(a, label, 14, U.MUTED));
        U.add(c, U.text(a, value, 20, U.INK, U.XBOLD), 4);
        return c;
    }

    private TextView emptyText() {
        TextView t = U.text(a, "Chưa có dữ liệu trong khoảng này", 14, U.SUBTLE);
        t.setGravity(Gravity.CENTER);
        U.pad(t, 0, 32);
        return t;
    }

    private View ranked(String index, String name, String detail, double ratio) {
        LinearLayout box = U.col(a);
        LinearLayout r = U.row(a);
        if (index != null) r.addView(U.text(a, index, 14, U.SUBTLE), new LayoutParams(U.dp(a, 24), U.WRAP));
        U.addFlex(r, U.ellipsize(U.text(a, name, 14, U.INK, U.BOLD), 1), 0);
        U.add(r, U.text(a, detail, 12, U.MUTED), 12, U.WRAP, U.WRAP);
        box.addView(r);
        U.add(box, bar(ratio), 6, U.MATCH, U.dp(a, 8));
        return box;
    }

    private View bar(double ratio) {
        FrameLayout f = new FrameLayout(a);
        U.bg(f, U.SOFT, 999);
        View fill = new View(a);
        U.bg(fill, U.ORANGE, 999);
        f.addView(fill, new FrameLayout.LayoutParams(0, U.MATCH));
        f.post(() -> { fill.getLayoutParams().width = (int) (f.getWidth() * Math.max(0.02, Math.min(1, ratio))); fill.requestLayout(); });
        return f;
    }

    private View kpi(String label, String value, String hint, Double delta, boolean lowerIsBetter) {
        LinearLayout c = U.card(U.pad(U.col(a), 16), 16);
        c.addView(U.text(a, label, 12, U.MUTED));
        U.add(c, U.ellipsize(U.text(a, value, 20, U.INK, U.XBOLD), 1), 4);
        TextView d;
        if (delta == null || Math.abs(delta) < 0.0005) d = U.text(a, hint != null ? hint : "Chưa có dữ liệu để so sánh", 11, U.SUBTLE);
        else {
            boolean good = lowerIsBetter ? delta < 0 : delta > 0;
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(Fmt.pct(Math.abs(delta)));
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            b.setSpan(new android.text.style.ForegroundColorSpan(good ? U.GREEN_DARK : U.WARN), 0, b.length(), 0);
            if (hint != null) b.append(" • ").append(hint);
            d = U.iconText(a, delta > 0 ? R.drawable.ic_trend_up : R.drawable.ic_trend_down, 12, b, 11, U.SUBTLE, U.NORMAL);
            d.getCompoundDrawablesRelative()[0].setTint(good ? U.GREEN_DARK : U.WARN);
        }
        U.ellipsize(d, 1);
        U.add(c, d, 2);
        return c;
    }

    private View card(String title, String sub, View content) {
        LinearLayout c = U.card(U.pad(U.col(a), 16), 16);
        c.addView(U.text(a, title, 16, U.INK, U.XBOLD));
        if (sub != null) U.add(c, U.text(a, sub, 12, U.SUBTLE), 2);
        U.add(c, content, 20);
        return c;
    }

    static class Point {
        final String label, title;
        final long value;
        final int orders;
        Point(String label, String title, long value, int orders) { this.label = label; this.title = title; this.value = value; this.orders = orders; }
    }

    /** Single-series column chart; tap a column for its exact value. */
    static class ColumnChart extends LinearLayout {
        private int active = -1;

        ColumnChart(Activity a, List<Point> data, Function<Long, String> format, Function<Point, String> detail, boolean compact) {
            super(a);
            setOrientation(VERTICAL);
            long max = 1, total = 0;
            for (Point p : data) { max = Math.max(max, p.value); total += p.value; }
            TextView caption = U.text(a, "", 12, U.MUTED);
            addView(caption);
            LinearLayout bars = U.row(a);
            bars.setGravity(Gravity.BOTTOM);
            int h = U.dp(a, compact ? 144 : 192);
            LinearLayout labels = U.row(a);
            List<View> cols = new ArrayList<>();
            List<TextView> lbls = new ArrayList<>();
            long fmax = max, ftotal = total;
            Runnable[] refresh = new Runnable[1];
            for (int i = 0; i < data.size(); i++) {
                Point p = data.get(i);
                FrameLayout col = new FrameLayout(a);
                View fill = new View(a);
                int fh = p.value > 0 ? Math.max((int) (h * 0.03), (int) (h * (double) p.value / fmax)) : U.dp(a, 2);
                FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(U.MATCH, fh, Gravity.BOTTOM);
                fp.leftMargin = fp.rightMargin = U.dp(a, 1);
                col.addView(fill, fp);
                int idx = i;
                col.setOnClickListener(v -> { active = idx; refresh[0].run(); });
                bars.addView(col, new LayoutParams(0, h, 1));
                cols.add(fill);
                String l = data.size() > 16 && !compact ? (i % 5 == 0 || i == data.size() - 1 ? p.label : "") : p.label;
                TextView lt = U.text(a, l, 10, U.SUBTLE);
                lt.setGravity(Gravity.CENTER);
                lt.setSingleLine(true);
                labels.addView(lt, new LayoutParams(0, U.WRAP, 1));
                lbls.add(lt);
            }
            refresh[0] = () -> {
                for (int i = 0; i < data.size(); i++) {
                    Point p = data.get(i);
                    int color = p.value > 0 ? (active == i ? U.ORANGE_DEEP : U.ORANGE) : U.LINE;
                    U.bg(cols.get(i), color, 3);
                    lbls.get(i).setTextColor(active == i ? U.ORANGE : U.SUBTLE);
                    U.weight(lbls.get(i), active == i ? U.BOLD : U.NORMAL);
                }
                if (active >= 0) {
                    Point p = data.get(active);
                    android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(p.title + ":");
                    b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
                    b.setSpan(new android.text.style.ForegroundColorSpan(U.INK), 0, b.length(), 0);
                    b.append(" ").append(format.apply(p.value));
                    if (detail != null) b.append(" • ").append(detail.apply(p));
                    caption.setText(b);
                } else caption.setText(ftotal == 0 ? "Chưa có dữ liệu trong khoảng này" : "Rê chuột hoặc chạm vào cột để xem chi tiết");
            };
            refresh[0].run();
            U.add(this, bars, 8, U.MATCH, h);
            addView(U.divider(a, U.LINE));
            U.add(this, labels, 6);
            if (total > 0) {
                String top = format.apply(fmax);
                if (detail != null) top = (fmax >= 1e6 ? String.format(Locale.US, "%.1f", fmax / 1e6).replace(".0", "") + "tr" : fmax >= 1000 ? Math.round(fmax / 1000.0) + "k" : String.valueOf(fmax)) + "đ";
                TextView t = U.text(a, "Cao nhất: " + top, 11, U.SUBTLE);
                t.setGravity(Gravity.END);
                U.add(this, t, 8);
            }
        }
    }
}
