package com.foodgo.nativeapp.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.ui.FoodMap;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;
import com.foodgo.nativeapp.util.Geo;

import java.util.ArrayList;
import java.util.List;

/**
 * components/order-tracking-map.tsx: restaurant → customer map with the road route. There is no driver app yet,
 * so while the order is "delivering" the driver's position is estimated from the time since that status started.
 */
public class OrderTrackingMap extends FrameLayout {
    private Order order;
    private Geo.LatLng from, to;
    private Geo.Route route;
    private String fromKey = "", toKey = "", routeKey = "";
    private final FoodMap map;
    private final LinearLayout info;
    private final LinearLayout box;
    private final TextView loading;
    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); if (order != null && "delivering".equals(order.status)) Net.later(this, 3000); }
    };

    public OrderTrackingMap(Context c) {
        super(c);
        loading = U.text(c, "Đang tải bản đồ...", 14, U.SUBTLE);
        loading.setGravity(Gravity.CENTER);
        U.bg(loading, U.SKELETON, 16);
        box = U.col(c);
        U.card(box, 16);
        U.rounded(box, 16);
        map = new FoodMap(c, 288).padding(40);
        box.addView(map);
        info = U.col(c);
        U.pad(info, 20);
        box.addView(info);
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Net.cancel(tick); tick.run(); }
            @Override public void onViewDetachedFromWindow(View v) { Net.cancel(tick); }
        });
    }

    public void update(Order o) {
        order = o;
        String fk = o.restaurant != null ? o.restaurant.lat + "," + o.restaurant.lng + "," + o.restaurant.address : "";
        if (!fk.equals(fromKey)) {
            fromKey = fk;
            if (o.restaurant != null && o.restaurant.lat != null && o.restaurant.lng != null) from = new Geo.LatLng(o.restaurant.lat, o.restaurant.lng);
            else if (o.restaurant != null && o.restaurant.address != null) Geo.search(o.restaurant.address, (p, e) -> { from = p; afterPoints(); });
        }
        String tk = o.delivery_lat + "," + o.delivery_lng + "," + o.address;
        if (!tk.equals(toKey)) {
            toKey = tk;
            if (o.delivery_lat != null && o.delivery_lng != null) to = new Geo.LatLng(o.delivery_lat, o.delivery_lng);
            else Geo.search(o.address, (p, e) -> { to = p; afterPoints(); });
        }
        afterPoints();
        Net.cancel(tick);
        tick.run();
    }

    private void afterPoints() {
        if (from != null && to != null) {
            String k = from.lat + "," + from.lng + ";" + to.lat + "," + to.lng;
            if (!k.equals(routeKey)) {
                routeKey = k;
                Geo.route(from, to, (r, e) -> { route = r; render(); });
            }
        }
        render();
    }

    private void render() {
        if (order == null) return;
        Context c = getContext();
        if (loading.getParent() == null) {
            addView(loading, new LayoutParams(U.MATCH, U.dp(c, 256)));
            addView(box, new LayoutParams(U.MATCH, U.WRAP));
        }
        if ("cancelled".equals(order.status)) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        boolean waiting = from == null && to == null;
        loading.setVisibility(waiting ? VISIBLE : GONE);
        box.setVisibility(waiting ? GONE : VISIBLE);
        if (waiting) return;

        List<double[]> points = route != null && !route.points.isEmpty() ? route.points : new ArrayList<>();
        if (points.isEmpty() && from != null && to != null) { points.add(new double[]{from.lat, from.lng}); points.add(new double[]{to.lat, to.lng}); }
        double duration = route != null ? route.duration : 15 * 60;
        boolean moving = "delivering".equals(order.status);
        double progress = moving ? Math.min(1, (System.currentTimeMillis() - Iso.parse(order.updated_at)) / (duration * 1000)) : 0;
        long remainingMin = Math.max(1, (long) Math.ceil(duration * (1 - progress) / 60));

        double[] driver = null;
        if ("picking_up".equals(order.status) && from != null) driver = new double[]{from.lat, from.lng};
        if (moving && !points.isEmpty()) driver = Geo.pointAlong(points, progress);
        if ("delivered".equals(order.status) && to != null) driver = new double[]{to.lat, to.lng};

        List<FoodMap.MapMarker> markers = new ArrayList<>();
        if (from != null) markers.add(new FoodMap.MapMarker("restaurant", "restaurant", from.lat, from.lng, order.restaurant_name));
        if (to != null) markers.add(new FoodMap.MapMarker("home", "home", to.lat, to.lng, "Điểm giao hàng"));
        if (driver != null) markers.add(new FoodMap.MapMarker("driver", "driver", driver[0], driver[1], "Tài xế"));
        List<double[]> fit = new ArrayList<>();
        for (FoodMap.MapMarker m : markers) fit.add(new double[]{mLat(m), mLng(m)});
        map.render(markers, points, null, fit);

        String headline = moving ? (progress >= 1 ? "Tài xế sắp đến nơi" : "Tài xế sẽ đến trong khoảng " + remainingMin + " phút")
                : "picking_up".equals(order.status) ? "Tài xế đang lấy món tại " + order.restaurant_name
                : "delivered".equals(order.status) ? "Đơn hàng đã được giao"
                : order.restaurant_name + " đang chuẩn bị đơn của bạn";
        info.removeAllViews();
        LinearLayout head = U.row(c);
        head.setGravity(Gravity.TOP);
        LinearLayout t = U.col(c);
        t.addView(U.text(c, headline, 15, U.INK, U.BOLD));
        String sub = (route != null ? "Quãng đường " + Geo.formatKm(route.distance / 1000) : "Đang tính đường đi...") + (moving ? " • vị trí tài xế là ước tính" : "");
        U.add(t, U.text(c, sub, 12, U.MUTED), 4);
        U.addFlex(head, t, 0);
        if (to != null) {
            String directions = "https://www.google.com/maps/dir/?api=1&destination=" + to.lat + "," + to.lng
                    + (from != null ? "&origin=" + from.lat + "," + from.lng : "") + "&travelmode=two-wheeler";
            TextView g = U.iconText(c, R.drawable.ic_navigation, 14, "Google Maps", 12, U.ORANGE, U.BOLD);
            U.pad(g, 8, 8);
            U.click(g, () -> U2.openExternal(c, directions));
            U.add(head, g, 12, U.WRAP, U.WRAP);
        }
        info.addView(head);
        if (moving) {
            FrameLayout bar = new FrameLayout(c);
            U.bg(bar, U.SKELETON, 999);
            View fill = new View(c);
            U.bg(fill, U.ORANGE, 999);
            bar.addView(fill, new LayoutParams(0, U.MATCH));
            double p = progress;
            bar.post(() -> { fill.getLayoutParams().width = (int) Math.round(bar.getWidth() * p); fill.requestLayout(); });
            U.add(info, bar, 12, U.MATCH, U.dp(c, 8));
        }
        if ("picking_up".equals(order.status) || moving) {
            U.add(info, U.divider(c, U.LINE), 12);
            LinearLayout d = U.row(c);
            TextView scooter = U.text(c, "🛵", 18, U.INK);
            scooter.setGravity(Gravity.CENTER);
            U.bg(scooter, U.PEACH3, 999);
            d.addView(scooter, new LinearLayout.LayoutParams(U.dp(c, 44), U.dp(c, 44)));
            LinearLayout dt = U.col(c);
            dt.addView(U.text(c, "Tài xế FoodGo", 14, U.INK, U.BOLD));
            dt.addView(U.text(c, moving ? "Đang trên đường đến bạn" : "Đang chờ lấy món", 12, U.MUTED));
            U.addFlex(d, dt, 12);
            FrameLayout call = new FrameLayout(c);
            U.pressable(call, U.GREEN_BG, 999);
            call.addView(U.icon(c, R.drawable.ic_phone, 16, U.GREEN), new LayoutParams(U.dp(c, 16), U.dp(c, 16), Gravity.CENTER));
            call.setContentDescription("Gọi tài xế");
            call.setOnClickListener(v -> U2.dial(c, "19001234"));
            d.addView(call, new LinearLayout.LayoutParams(U.dp(c, 40), U.dp(c, 40)));
            U.add(info, d, 12);
        }
    }

    private static double mLat(FoodMap.MapMarker m) { return m.lat(); }

    private static double mLng(FoodMap.MapMarker m) { return m.lng(); }
}
