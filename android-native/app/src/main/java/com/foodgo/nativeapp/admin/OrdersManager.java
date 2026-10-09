package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Realtime;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.OrderItem;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;
import com.foodgo.nativeapp.util.Fmt;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** components/admin/orders-manager.tsx: order queue with status buttons (admin: all; owner: their restaurant). */
public class OrdersManager extends LinearLayout {
    private static final Map<String, String> NEXT_STATUS = new java.util.HashMap<>();
    static {
        NEXT_STATUS.put("pending", "confirmed");
        NEXT_STATUS.put("confirmed", "preparing");
        NEXT_STATUS.put("preparing", "picking_up");
        NEXT_STATUS.put("picking_up", "delivering");
        NEXT_STATUS.put("delivering", "delivered");
    }

    private final Activity a;
    private final Long restaurantId;
    private List<Order> orders;
    private String filter = "active";
    private String restaurant = "";
    private Long open;
    private final Select restaurantSelect;
    private final LinearLayout list;
    private Realtime.Channel channel;

    public OrdersManager(Activity a, Long restaurantId) {
        super(a);
        this.a = a;
        this.restaurantId = restaurantId;
        setOrientation(VERTICAL);
        addView(U.text(a, "Đơn hàng", 22, U.INK, U.XBOLD));
        LinearLayout tools = U.row(a);
        restaurantSelect = new Select(a).title("Lọc nhà hàng");
        restaurantSelect.setVisibility(GONE);
        restaurantSelect.onChange(v -> { restaurant = v; render(); });
        tools.addView(restaurantSelect, new LayoutParams(0, U.dp(a, 40), 1));
        Select status = new Select(a).title("Lọc trạng thái").option("active", "Đang xử lý").option("all", "Tất cả");
        for (Map.Entry<String, String> e : Fmt.STATUS_LABEL.entrySet()) status.option(e.getKey(), e.getValue());
        status.onChange(v -> { filter = v; render(); });
        U.add(tools, status, 8, U.WRAP, U.dp(a, 40));
        U.add(this, tools, 12);
        list = U.col(a);
        U.add(this, list, 20);
        render();
        load();
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                channel = Realtime.subscribe("orders-" + (restaurantId != null ? restaurantId : "all"), "*", "fg_orders",
                        restaurantId != null ? "restaurant_id=eq." + restaurantId : null, (type, rec, old) -> {
                            if ("INSERT".equals(type)) AppState.get().toast("Có đơn mới #" + Json.str(rec, "code"));
                            load();
                        });
            }
            @Override public void onViewDetachedFromWindow(View v) { Realtime.remove(channel); }
        });
    }

    private void load() {
        Db q = Db.from("fg_orders").select("*, order_items:fg_order_items(*)");
        if (restaurantId != null) q.eq("restaurant_id", restaurantId);
        q.order("created_at", false).limit(200).list(Order.class, (l, e) -> { orders = l; render(); });
    }

    /** cancelled after paying = the shop still owes a refund, so it stays in the "to do" list */
    private static boolean needsRefund(Order o) { return "cancelled".equals(o.status) && "paid".equals(o.payment_status); }

    private void update(Order o, JsonObject patch) {
        Db.from("fg_orders").update(patch).eq("id", o.id).run((d, err) -> {
            if (err != null) { AppState.get().toastError(err); return; }
            if (patch.has("status")) o.status = Json.str(patch, "status");
            if (patch.has("payment_status")) o.payment_status = Json.str(patch, "payment_status");
            render();
        });
    }

    private void render() {
        list.removeAllViews();
        if (orders == null) { list.addView(U.spinner(a)); return; }
        TreeSet<String> names = new TreeSet<>();
        for (Order o : orders) names.add(o.restaurant_name);
        if (names.size() > 1) {
            String cur = restaurant;
            restaurantSelect.clearOptions().option("", "Mọi nhà hàng");
            for (String n : names) restaurantSelect.option(n, n);
            restaurantSelect.set(cur);
            restaurantSelect.setVisibility(VISIBLE);
        } else restaurantSelect.setVisibility(GONE);
        List<String> done = Arrays.asList("delivered", "cancelled");
        int n = 0;
        for (Order o : orders) {
            boolean match = filter.equals("all") || (filter.equals("active") ? !done.contains(o.status) || needsRefund(o) : o.status.equals(filter));
            if (!match || (!restaurant.isEmpty() && !restaurant.equals(o.restaurant_name))) continue;
            U.add(list, card(o), n++ == 0 ? 0 : 12);
        }
        if (n == 0) {
            TextView t = U.text(a, "Không có đơn nào", 14, U.SUBTLE);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 0, 48);
            U.card(t, 16);
            list.addView(t);
        }
    }

    private View card(Order o) {
        LinearLayout c = U.card(U.pad(U.col(a), 16), 16);
        LinearLayout r1 = U.row(a);
        r1.setGravity(Gravity.TOP);
        LinearLayout left = U.col(a);
        left.addView(U.ellipsize(U.text(a, "#" + o.code, 15, U.INK, U.BOLD), 1));
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(o.restaurant_name);
        b.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 0, b.length(), 0);
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
        b.append(" • ").append(Fmt.dateTime(o.created_at));
        U.add(left, U.text(a, b, 12, U.SUBTLE), 2);
        U.addFlex(r1, left, 0);
        int[] st = Fmt.statusStyle(o.status);
        U.add(r1, U.pill(a, Fmt.statusLabel(o.status), st[0], st[1]), 12, U.WRAP, U.WRAP);
        c.addView(r1);

        LinearLayout who = U.row(a);
        who.addView(U.text(a, o.recipient, 14, U.INK, U.BOLD));
        who.addView(U.text(a, " • ", 14, U.MUTED));
        TextView tel = U.text(a, o.phone, 14, U.ORANGE);
        U.click(tel, () -> U2.dial(a, o.phone));
        who.addView(tel);
        U.add(c, who, 12);
        U.add(c, U.ellipsize(U.text(a, o.address, 12, U.SUBTLE), 2), 2);

        U.add(c, U.divider(a, U.SOFT), 12);
        LinearLayout r3 = U.row(a);
        int qty = 0;
        for (OrderItem i : o.items()) qty += i.qty;
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder(Fmt.money(o.total));
        t.setSpan(new android.text.style.ForegroundColorSpan(U.ORANGE), 0, t.length(), 0);
        t.setSpan(new android.text.style.AbsoluteSizeSpan(18, true), 0, t.length(), 0);
        t.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, t.length(), 0);
        t.append("  ").append(qty + " món");
        U.addFlex(r3, U.text(a, t, 12, U.SUBTLE), 0);
        String method = "qr".equals(o.payment_method) ? "QR" : "cod".equals(o.payment_method) ? "COD" : "MoMo";
        String payText = needsRefund(o) ? "Cần hoàn tiền" : "paid".equals(o.payment_status) ? "Đã TT" : "refunded".equals(o.payment_status) ? "Đã hoàn tiền"
                : "cod".equals(o.payment_method) ? "Thu khi giao" : "Chờ tiền vào";
        int[] pc = needsRefund(o) ? new int[]{U.RED_BG, U.RED_TEXT} : "paid".equals(o.payment_status) ? new int[]{U.GREEN_BG, U.GREEN}
                : "refunded".equals(o.payment_status) ? new int[]{U.GREY_BG, U.MUTED} : new int[]{U.AMBER_BG, U.AMBER};
        r3.addView(U.pill(a, method + " • " + payText, pc[0], pc[1]));
        U.add(c, r3, 12);

        boolean isOpen = open != null && open == o.id;
        TextView toggle = U.text(a, isOpen ? "Ẩn chi tiết ▴" : "Xem món & ghi chú ▾", 12, U.ORANGE, U.BOLD);
        U.pad(toggle, 0, 4, 0, 4);
        U.click(toggle, () -> { open = isOpen ? null : o.id; render(); });
        U.add(c, toggle, 8, U.WRAP, U.WRAP);
        if (isOpen) {
            LinearLayout d = U.col(a);
            U.pad(d, 12);
            U.bg(d, U.BG, 12);
            for (OrderItem i : o.items()) {
                android.text.SpannableStringBuilder line = new android.text.SpannableStringBuilder(i.qty + " × " + i.name + " ");
                int s = line.length();
                line.append("(").append(Fmt.money(i.price)).append(")");
                line.setSpan(new android.text.style.ForegroundColorSpan(U.SUBTLE), s, line.length(), 0);
                d.addView(U.text(a, line, 14, U.INK));
            }
            U.add(d, U.text(a, "Phí ship: " + Fmt.money(o.shipping_fee), 14, U.MUTED), 8);
            if (o.discount > 0) d.addView(U.text(a, "Mã " + o.voucher_code + ": -" + Fmt.money(o.discount), 14, U.GREEN_DARK));
            if (o.note != null && !o.note.isEmpty()) {
                android.text.SpannableStringBuilder nb = new android.text.SpannableStringBuilder("Ghi chú:");
                nb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, nb.length(), 0);
                nb.setSpan(new android.text.style.ForegroundColorSpan(U.INK), 0, nb.length(), 0);
                nb.append(" ").append(o.note);
                U.add(d, U.text(a, nb, 14, U.MUTED), 12);
            } else U.add(d, U.text(a, "Không có ghi chú", 14, U.SUBTLE), 12);
            U.add(c, d, 8);
        }

        // actions: main step full-width, secondary buttons below
        LinearLayout actions = U.col(a);
        String next = NEXT_STATUS.get(o.status);
        if (next != null) {
            // payment is settled by a status step (the DB does the same in its trigger):
            // online (QR/MoMo) → confirming means the money arrived; COD → delivering means cash collected
            boolean settles = !"paid".equals(o.payment_status) && ((next.equals("confirmed") && !"cod".equals(o.payment_method)) || (next.equals("delivered") && "cod".equals(o.payment_method)));
            String label = !settles ? Fmt.stepLabel(next) : "cod".equals(o.payment_method) ? "Đã giao & thu tiền" : "Đã nhận tiền & xác nhận";
            TextView b1 = button("→ " + label, U.ORANGE, U.WHITE, 14);
            U.click(b1, () -> update(o, settles ? Json.obj("status", next, "payment_status", "paid") : Json.obj("status", next)));
            actions.addView(b1, new LayoutParams(U.MATCH, U.dp(a, 40)));
        }
        if (!done.contains(o.status)) {
            TextView b2 = button("Hủy đơn", U.WHITE, U.RED, 12);
            U.border(b2, U.WHITE, 8, U.BORDER, 1);
            U.click(b2, () -> Dialogs.confirm(a, "Hủy đơn #" + o.code + "?" + ("paid".equals(o.payment_status) ? " Đơn đã thanh toán, bạn sẽ cần hoàn tiền cho khách." : ""),
                    () -> update(o, Json.obj("status", "cancelled"))));
            U.add(actions, b2, actions.getChildCount() == 0 ? 0 : 8, U.WRAP, U.dp(a, 40));
        }
        if (needsRefund(o)) {
            TextView b3 = button("Đã hoàn tiền cho khách", U.RED, U.WHITE, 14);
            U.click(b3, () -> Dialogs.confirm(a, "Xác nhận đã hoàn " + Fmt.money(o.total) + " cho khách (đơn #" + o.code + ")?",
                    () -> update(o, Json.obj("payment_status", "refunded"))));
            U.add(actions, b3, actions.getChildCount() == 0 ? 0 : 8, U.MATCH, U.dp(a, 40));
        }
        if (actions.getChildCount() > 0) U.add(c, actions, 12);
        return c;
    }

    private static final List<String> done = Arrays.asList("delivered", "cancelled");

    private TextView button(String text, int bg, int fg, float sp) {
        TextView t = U.text(a, text, sp, fg, U.BOLD);
        t.setGravity(Gravity.CENTER);
        U.pad(t, 12, 0);
        U.pressable(t, bg, 8);
        return t;
    }

    @SuppressWarnings("unused")
    private static HashSet<String> unused() { return new HashSet<>(new ArrayList<>()); }
}
