package com.foodgo.nativeapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Realtime;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.OrderItem;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** app/orders/page.tsx */
public class OrdersScreen extends Screen {
    private static final String[] TAB_LABELS = {"Tất cả", "Đang xử lý", "Đang giao", "Hoàn thành", "Đã hủy"};
    private static final List<List<String>> TAB_STATUSES = Arrays.asList(
            null,
            Arrays.asList("pending", "confirmed", "preparing"),
            Arrays.asList("picking_up", "delivering"),
            Arrays.asList("delivered"),
            Arrays.asList("cancelled"));

    private List<Order> orders;
    private Set<String> reviewed = new HashSet<>(); // "orderId:foodId"
    private int tab;
    private LinearLayout tabs, list;
    private Realtime.Channel channel;

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    protected View build() {
        LinearLayout page = U.col(act);
        page.addView(U.h1(act, "Đơn hàng của tôi"));
        tabs = U.row(act);
        tabs.setGravity(Gravity.BOTTOM);
        U.add(page, U.hscroll(act, tabs), 28);
        page.addView(U.divider(act, U.BORDER));
        list = U.col(act);
        U.add(page, list, 24);
        renderTabs();
        render();
        String uid = state().userId();
        if (uid != null) {
            load(uid);
            channel = Realtime.subscribe("orders-list-" + uid, "*", "fg_orders", "user_id=eq." + uid, (t, r, o) -> load(uid));
        }
        return scrollPage(page);
    }

    @Override
    public void onDestroy() { Realtime.remove(channel); }

    private void load(String uid) {
        Db.from("fg_orders").select("*, order_items:fg_order_items(*)").eq("user_id", uid).order("created_at", false)
                .list(Order.class, (l, e) -> { orders = l; render(); });
        Db.from("fg_reviews").select("order_id, food_id").eq("user_id", uid).rows((rows, e) -> {
            Set<String> s = new HashSet<>();
            for (JsonObject r : rows) s.add(Json.str(r, "order_id") + ":" + Json.str(r, "food_id"));
            reviewed = s;
            render();
        });
    }

    private void renderTabs() {
        tabs.removeAllViews();
        for (int i = 0; i < TAB_LABELS.length; i++) {
            int idx = i;
            boolean active = i == tab;
            LinearLayout t = U.col(act);
            TextView label = U.text(act, TAB_LABELS[i], 14, active ? U.ORANGE : U.MUTED, U.BOLD);
            U.pad(label, 16, 0, 16, 12);
            t.addView(label);
            View line = new View(act);
            line.setBackgroundColor(active ? U.ORANGE : 0);
            t.addView(line, new LinearLayout.LayoutParams(U.MATCH, U.dp(act, 2)));
            U.click(t, () -> { tab = idx; renderTabs(); render(); });
            tabs.addView(t);
        }
    }

    private void render() {
        list.removeAllViews();
        if (orders == null) { list.addView(U.spinner(act)); return; }
        List<String> statuses = TAB_STATUSES.get(tab);
        int shown = 0;
        for (Order o : orders) {
            if (statuses != null && !statuses.contains(o.status)) continue;
            U.add(list, card(o), shown++ == 0 ? 0 : 16);
        }
        if (shown == 0) {
            TextView link = U.text(act, "Đặt món ngay", 14, U.ORANGE, U.BOLD);
            U.click(link, () -> navigate("/search"));
            list.addView(U.empty(act, R.drawable.ic_receipt, "Chưa có đơn hàng nào", link));
        }
    }

    private View card(Order o) {
        List<OrderItem> items = o.items();
        boolean awaitingReview = false;
        if ("delivered".equals(o.status)) for (OrderItem i : items) if (i.food_id != null && !reviewed.contains(o.id + ":" + i.food_id)) { awaitingReview = true; break; }
        LinearLayout c = U.card(act);
        LinearLayout top = U.row(act);
        top.setGravity(Gravity.TOP);
        LinearLayout left = U.col(act);
        left.addView(U.ellipsize(U.text(act, o.restaurant_name, 15, U.INK, U.BOLD), 1));
        U.add(left, U.text(act, "Đơn #" + o.code + " • " + Fmt.dateTime(o.created_at), 12, U.SUBTLE), 4);
        U.addFlex(top, left, 0);
        LinearLayout right = U.col(act);
        right.setGravity(Gravity.END);
        int[] st = Fmt.statusStyle(o.status);
        right.addView(U.pill(act, Fmt.statusLabel(o.status), st[0], st[1]));
        if (awaitingReview) U.add(right, U.pill(act, "★ Chờ đánh giá", U.AMBER_BG, U.AMBER), 4, U.WRAP, U.WRAP);
        U.add(top, right, 12, U.WRAP, U.WRAP);
        c.addView(top);

        U.add(c, U.divider(act, U.LINE), 20);
        LinearLayout mid = U.row(act);
        U.pad(mid, 0, 16, 0, 16);
        if (!items.isEmpty() && items.get(0).image != null) mid.addView(U.img(act, items.get(0).image, 56, 56, 8));
        LinearLayout info = U.col(act);
        StringBuilder names = new StringBuilder();
        int qty = 0;
        for (OrderItem i : items) { if (names.length() > 0) names.append(", "); names.append(i.name).append(" x").append(i.qty); qty += i.qty; }
        info.addView(U.ellipsize(U.text(act, names.toString(), 14, U.INK, U.BOLD), 1));
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(qty + " phần • " + Fmt.paymentLabel(o.payment_method));
        String payNote = "paid".equals(o.payment_status) ? "Đã thanh toán" : !"cod".equals(o.payment_method) && !"cancelled".equals(o.status) ? "Chờ thanh toán" : null;
        if (payNote != null) {
            int s = b.length() + 2;
            b.append("  ").append(payNote);
            b.setSpan(new android.text.style.ForegroundColorSpan("paid".equals(o.payment_status) ? U.GREEN : U.AMBER), s, b.length(), 0);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
        }
        U.add(info, U.text(act, b, 14, U.MUTED), 4);
        U.addFlex(mid, info, items.isEmpty() || items.get(0).image == null ? 0 : 12);
        c.addView(mid);
        c.addView(U.divider(act, U.LINE));

        LinearLayout bottom = U.row(act);
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder("Tổng cộng ");
        int s = t.length();
        t.append(Fmt.money(o.total));
        t.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), s, t.length(), 0);
        U.addFlex(bottom, U.text(act, t, 15, U.INK, U.BOLD), 0);
        TextView action = awaitingReview ? U.text(act, "★ Đánh giá", 14, U.WHITE, U.BOLD) : U.text(act, "Xem chi tiết", 14, U.INK, U.SEMI);
        U.pad(action, 16, 8);
        if (awaitingReview) U.bg(action, U.ORANGE, 12); else U.border(action, U.WHITE, 12, U.BORDER, 1);
        bottom.addView(action);
        U.add(c, bottom, 16);
        boolean review = awaitingReview;
        U.click(c, () -> navigate("/orders/" + o.id + (review ? "#danh-gia" : "")));
        return c;
    }
}
