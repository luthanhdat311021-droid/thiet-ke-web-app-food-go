package com.foodgo.nativeapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Realtime;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.OrderItem;
import com.foodgo.nativeapp.pay.Momo;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** app/orders/[id]/page.tsx */
public class OrderDetailScreen extends Screen {
    private long oid;
    private Order order;
    private boolean loaded;
    private boolean scrolledToReview;
    private boolean momoBusy, busy;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout page;
    private OrderReviewPanel reviewPanel;
    private OrderTrackingMap trackingMap;
    private Realtime.Channel channel;

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    protected View build() {
        try { oid = Long.parseLong(uri.getPathSegments().get(1)); } catch (Exception e) { oid = 0; }
        root = new FrameLayout(act);
        root.setBackgroundColor(U.BG);
        root.addView(U.spinner(act));
        reviewPanel = new OrderReviewPanel(act);
        trackingMap = new OrderTrackingMap(act);

        // back from MoMo: /orders/<id>?partnerCode=...&resultCode=...&signature=...
        if (param("signature") != null && param("resultCode") != null) {
            JsonObject payload = new JsonObject();
            for (String k : Momo.RETURN_KEYS) payload.addProperty(k, param(k) != null ? param(k) : "");
            String message = param("message");
            Db.rpc("fg_momo_confirm", Json.obj("p", payload), (data, err) -> {
                String r = data != null && data.isJsonPrimitive() ? data.getAsString() : null;
                if (err != null) state().toastError(err);
                else if ("ok".equals(r) || "already_paid".equals(r)) state().toast("Thanh toán MoMo thành công");
                else if ("failed".equals(r)) state().toastError("Thanh toán MoMo chưa thành công" + (message != null && !message.isEmpty() ? ": " + message : ""));
                else state().toastError("Không xác minh được giao dịch MoMo");
                if (exists()) replace("/orders/" + oid);
            });
        }

        load();
        channel = Realtime.subscribe("order-" + oid, "UPDATE", "fg_orders", "id=eq." + oid, (t, r, o) -> load());
        return root;
    }

    @Override
    public void onDestroy() { Realtime.remove(channel); }

    private void load() {
        Db.from("fg_orders").select("*, order_items:fg_order_items(*), restaurant:fg_restaurants(lat, lng, address)").eq("id", oid)
                .maybeSingle(Order.class, (o, e) -> { order = o; loaded = true; render(); });
    }

    private void render() {
        if (!loaded) return;
        int y = scroll != null ? scroll.getScrollY() : 0;
        root.removeAllViews();
        if (order == null) {
            LinearLayout p = U.col(act);
            TextView link = U.text(act, "Về danh sách đơn", 14, U.ORANGE, U.BOLD);
            U.click(link, () -> navigate("/orders"));
            p.addView(U.empty(act, R.drawable.ic_receipt, "Không tìm thấy đơn hàng", link));
            root.addView(scrollPage(p));
            return;
        }
        Order o = order;
        boolean cancelled = "cancelled".equals(o.status);
        int stepIndex = Fmt.stepIndex(o.status);
        boolean awaitingQr = "qr".equals(o.payment_method) && "unpaid".equals(o.payment_status) && !cancelled;
        boolean awaitingMomo = "momo".equals(o.payment_method) && "unpaid".equals(o.payment_status) && !cancelled;

        page = U.col(act);
        TextView back = U.text(act, "← Đơn hàng của tôi", 14, U.ORANGE, U.BOLD);
        U.pad(back, 0, 12, 0, 12);
        U.click(back, () -> navigate("/orders"));
        page.addView(back, U.lp(U.WRAP, U.WRAP));

        if ("1".equals(param("new"))) {
            LinearLayout ok = U.row(act);
            U.pad(ok, 16);
            U.bg(ok, U.GREEN_BG, 16);
            ok.addView(U.icon(act, R.drawable.ic_check_circle, 24, U.GREEN_DARK));
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Đặt hàng thành công!");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            b.append(" ").append(awaitingQr ? "Quét mã QR bên dưới để thanh toán." : "Quán sẽ xác nhận đơn trong giây lát.");
            U.addFlex(ok, U.text(act, b, 14, U.GREEN_DARK), 12);
            U.add(page, ok, 20);
        }

        LinearLayout head = new com.foodgo.nativeapp.ui.FlowRow(act, 12, 8);
        head.addView(U.text(act, "Đơn #" + o.code, 28, U.INK, U.XBOLD));
        int[] st = Fmt.statusStyle(o.status);
        TextView pill = U.pill(act, Fmt.statusLabel(o.status), st[0], st[1]);
        pill.setTextSize(14);
        head.addView(pill);
        U.add(page, head, 20);
        LinearLayout sub = U.row(act);
        TextView rname = U.text(act, o.restaurant_name, 14, o.restaurant_id != null ? U.ORANGE : U.INK, U.BOLD);
        if (o.restaurant_id != null) { long rid = o.restaurant_id; U.click(rname, () -> navigate("/restaurant/" + rid)); }
        sub.addView(rname);
        sub.addView(U.text(act, " • " + Fmt.dateTime(o.created_at), 14, U.SUBTLE));
        U.add(page, sub, 4);

        if (awaitingMomo) {
            LinearLayout[] p = Cards.panel(act, "Thanh toán bằng ví MoMo", null);
            p[1].addView(Cards.momoIcon(act, 64, 48, 14));
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(Fmt.money(o.total));
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            b.append(" — đơn chưa được thanh toán.");
            U.add(p[1], U.text(act, b, 14, U.INK), 16);
            U.add(p[1], U.text(act, "Bấm nút để mở trang thanh toán MoMo. Trang này sẽ tự cập nhật khi thanh toán xong.", 14, U.MUTED), 4);
            PButton pay = new PButton(act, "Thanh toán MoMo", PButton.MOMO);
            pay.setBusy(momoBusy);
            pay.onClick(() -> {
                momoBusy = true;
                pay.setBusy(true);
                Momo.start(act, o.id, (v, e) -> {
                    if (e != null) { state().toastError(e); momoBusy = false; pay.setBusy(false); }
                });
            });
            U.add(p[1], pay, 16, U.MATCH, U.dp(act, 48));
            U.add(page, p[0], 32);
        }
        if (awaitingQr) {
            LinearLayout[] p = Cards.panel(act, "Thanh toán chuyển khoản", null);
            String qr = Fmt.vietQrUrl(o.total, o.code);
            if (qr != null) {
                android.widget.ImageView img = new android.widget.ImageView(act);
                img.setAdjustViewBounds(true);
                img.setContentDescription("Mã QR thanh toán " + Fmt.money(o.total));
                U.border(img, U.WHITE, 12, U.LINE, 1);
                U.rounded(img, 12);
                com.bumptech.glide.Glide.with(act.getApplicationContext()).load(qr).into(img);
                LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(U.dp(act, 240), U.WRAP);
                ip.gravity = Gravity.CENTER_HORIZONTAL;
                p[1].addView(img, ip);
            } else p[1].addView(U.note(act, "Chưa cấu hình tài khoản nhận tiền (NEXT_PUBLIC_VIETQR_* trong .env.local).", U.AMBER_BG, U.AMBER_DARK));
            U.add(p[1], U.text(act, "Mở app ngân hàng, quét mã QR. Số tiền và nội dung đã được điền sẵn.", 14, U.MUTED), 20);
            U.add(p[1], Cards.copyRow(act, "Số tiền", String.valueOf(o.total), Fmt.money(o.total)), 16);
            U.add(p[1], Cards.copyRow(act, "Nội dung", o.code, null), 12);
            if (!Config.VIETQR_ACCOUNT_NO.isEmpty()) U.add(p[1], Cards.copyRow(act, "Số tài khoản", Config.VIETQR_ACCOUNT_NO, null), 12);
            LinearLayout waiting = U.row(act);
            waiting.addView(U.progress(act, 16, U.AMBER));
            U.add(waiting, U.text(act, "Đang chờ thanh toán...", 14, U.AMBER, U.SEMI), 8, U.WRAP, U.WRAP);
            U.add(p[1], waiting, 16);
            U.add(p[1], U.text(act, "Trang sẽ tự cập nhật khi nhận được tiền.", 12, U.SUBTLE), 4);
            U.add(page, p[0], awaitingMomo ? 24 : 32);
        }

        reviewPanel.update(o);
        detach(reviewPanel);
        U.add(page, reviewPanel, 24);
        trackingMap.update(o);
        detach(trackingMap);
        U.add(page, trackingMap, 24);

        // status
        LinearLayout[] sp = Cards.panel(act, "Trạng thái đơn hàng", null);
        if (cancelled) {
            LinearLayout c = U.row(act);
            U.pad(c, 20);
            U.bg(c, U.GREY_BG, 16);
            c.addView(U.icon(act, R.drawable.ic_close, 24, U.MUTED));
            U.add(c, U.text(act, "Đơn hàng đã bị hủy", 15, U.MUTED, U.BOLD), 12, U.WRAP, U.WRAP);
            sp[1].addView(c);
        } else {
            LinearLayout box = U.col(act);
            U.pad(box, 20);
            U.bg(box, U.PEACH2, 16);
            LinearLayout r = U.row(act);
            FrameLayout bike = new FrameLayout(act);
            U.bg(bike, U.ORANGE, 999);
            bike.addView(U.icon(act, R.drawable.ic_bike, 24, U.WHITE), new FrameLayout.LayoutParams(U.dp(act, 24), U.dp(act, 24), Gravity.CENTER));
            r.addView(bike, new LinearLayout.LayoutParams(U.dp(act, 44), U.dp(act, 44)));
            LinearLayout t = U.col(act);
            t.addView(U.text(act, stepIndex >= 0 ? Fmt.ORDER_STEPS[stepIndex][1] : "", 15, U.INK, U.BOLD));
            t.addView(U.text(act, "Dự kiến giao trong 20 - 30 phút", 14, U.MUTED));
            U.addFlex(r, t, 12);
            box.addView(r);
            LinearLayout steps = U.col(act);
            for (int i = 0; i < Fmt.ORDER_STEPS.length; i++) {
                LinearLayout s = U.row(act);
                FrameLayout dot = new FrameLayout(act);
                U.bg(dot, i <= stepIndex ? U.ORANGE : U.WHITE, 999);
                if (i <= stepIndex) dot.addView(U.icon(act, R.drawable.ic_check, 16, U.WHITE), new FrameLayout.LayoutParams(U.dp(act, 16), U.dp(act, 16), Gravity.CENTER));
                else dot.addView(U.text(act, String.valueOf(i + 1), 12, U.FAINT), new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));
                s.addView(dot, new LinearLayout.LayoutParams(U.dp(act, 28), U.dp(act, 28)));
                TextView l = U.text(act, Fmt.ORDER_STEPS[i][1], 14, i == stepIndex ? U.ORANGE : i < stepIndex ? U.INK : U.FAINT, i == stepIndex ? U.BOLD : U.NORMAL);
                U.add(s, l, 12, U.WRAP, U.WRAP);
                U.add(steps, s, i == 0 ? 0 : 20);
            }
            U.add(box, steps, 28);
            sp[1].addView(box);
        }
        U.add(page, sp[0], 24);

        // delivery
        LinearLayout[] dp = Cards.panel(act, "Giao đến", null);
        LinearLayout dr = U.row(act);
        dr.setGravity(Gravity.TOP);
        dr.addView(U.icon(act, R.drawable.ic_pin, 20, U.ORANGE));
        LinearLayout dt = U.col(act);
        android.text.SpannableStringBuilder who = new android.text.SpannableStringBuilder(o.recipient);
        who.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, who.length(), 0);
        int ws = who.length();
        who.append(" • ").append(o.phone);
        who.setSpan(new android.text.style.ForegroundColorSpan(U.MUTED), ws, who.length(), 0);
        dt.addView(U.text(act, who, 14, U.INK));
        U.add(dt, U.text(act, o.address, 14, U.MUTED), 4);
        if (o.note != null && !o.note.isEmpty()) {
            android.text.SpannableStringBuilder n = new android.text.SpannableStringBuilder("Ghi chú:");
            n.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, n.length(), 0);
            n.setSpan(new android.text.style.ForegroundColorSpan(U.INK), 0, n.length(), 0);
            n.append(" ").append(o.note);
            U.add(dt, U.text(act, n, 14, U.MUTED), 8);
        }
        U.addFlex(dr, dt, 12);
        dp[1].addView(dr);
        U.add(page, dp[0], 24);

        // items
        LinearLayout[] ip = Cards.panel(act, "Món đã đặt", null);
        for (OrderItem i : o.items()) {
            LinearLayout r = U.row(act);
            if (i.image != null) r.addView(U.img(act, i.image, 56, 56, 8));
            LinearLayout info = U.col(act);
            info.addView(U.ellipsize(U.text(act, i.name, 14, U.INK, U.BOLD), 1));
            info.addView(U.text(act, "x" + i.qty + " • " + Fmt.money(i.price), 14, U.SUBTLE));
            U.addFlex(r, info, i.image != null ? 12 : 0);
            r.addView(U.text(act, Fmt.money(i.price * i.qty), 14, U.INK, U.BOLD));
            U.add(ip[1], r, ip[1].getChildCount() == 0 ? 0 : 16);
        }
        U.add(ip[1], U.divider(act, U.LINE), 16);
        U.add(ip[1], line("Tạm tính", Fmt.money(o.subtotal), U.MUTED, U.MUTED, false), 16);
        U.add(ip[1], line("Phí giao hàng", o.shipping_fee > 0 ? Fmt.money(o.shipping_fee) : "Miễn phí", U.MUTED, o.shipping_fee > 0 ? U.MUTED : U.GREEN_SOFT, false), 12);
        if (o.discount > 0) U.add(ip[1], line("Giảm giá" + (o.voucher_code != null ? " (" + o.voucher_code + ")" : ""), "-" + Fmt.money(o.discount), U.GREEN_DARK, U.GREEN_DARK, false), 12);
        LinearLayout total = U.row(act);
        U.addFlex(total, U.text(act, "Tổng cộng", 18, U.INK, U.XBOLD), 0);
        total.addView(U.text(act, Fmt.money(o.total), 18, U.ORANGE, U.XBOLD));
        U.add(ip[1], total, 16);
        String payState = "paid".equals(o.payment_status) ? "Đã thanh toán" : "cod".equals(o.payment_method) ? "Trả khi nhận hàng" : "Chưa thanh toán";
        int payColor = "paid".equals(o.payment_status) ? U.GREEN : "cod".equals(o.payment_method) ? U.MUTED : U.AMBER;
        U.add(ip[1], line(Fmt.paymentLabel(o.payment_method), payState, U.MUTED, payColor, true), 12);
        U.add(page, ip[0], 24);

        // actions
        LinearLayout actions = U.col(act);
        // cancellable until the kitchen starts cooking (same rule as fg_cancel_order)
        if ("pending".equals(o.status) || "confirmed".equals(o.status)) {
            PButton cancel = new PButton(act, "Hủy đơn hàng", PButton.OUTLINE);
            cancel.textColor(U.RED);
            cancel.setBusy(busy);
            cancel.onClick(this::cancel);
            actions.addView(cancel, new LinearLayout.LayoutParams(U.MATCH, U.dp(act, 48)));
            TextView hint = U.text(act, "Chỉ hủy được khi quán chưa bắt đầu chuẩn bị món", 12, U.SUBTLE);
            hint.setGravity(Gravity.CENTER);
            U.add(actions, hint, 6);
        }
        if (cancelled && "paid".equals(o.payment_status)) U.add(actions, U.note(act, "Đơn đã thanh toán trước, quán sẽ hoàn tiền cho bạn.", U.AMBER_BG, U.AMBER_DARK), actions.getChildCount() == 0 ? 0 : 12);
        if (cancelled && "refunded".equals(o.payment_status)) U.add(actions, U.note(act, "Quán đã hoàn tiền cho đơn này.", U.GREEN_BG, U.GREEN_DARK), actions.getChildCount() == 0 ? 0 : 12);
        if ("delivered".equals(o.status) || cancelled) {
            PButton re = new PButton(act, "Đặt lại đơn này", PButton.PRIMARY, R.drawable.ic_replay);
            re.onClick(this::reorder);
            U.add(actions, re, actions.getChildCount() == 0 ? 0 : 12, U.MATCH, U.dp(act, 48));
        }
        PButton cont = new PButton(act, "Tiếp tục mua hàng", PButton.OUTLINE);
        cont.onClick(() -> navigate("/"));
        U.add(actions, cont, actions.getChildCount() == 0 ? 0 : 12, U.MATCH, U.dp(act, 48));
        U.add(page, actions, 24);

        scroll = scrollPage(page);
        root.addView(scroll);
        int keep = y;
        scroll.post(() -> {
            // from "Đánh giá" in the orders list: jump to the review panel once the order has loaded
            if (!scrolledToReview && "danh-gia".equals(uri.getFragment()) && reviewPanel.getVisibility() == View.VISIBLE) {
                scrolledToReview = true;
                scroll.smoothScrollTo(0, reviewPanel.getTop() - U.dp(act, 16));
            } else scroll.scrollTo(0, keep);
        });
    }

    private static void detach(View v) {
        if (v.getParent() != null) ((android.view.ViewGroup) v.getParent()).removeView(v);
    }

    private View line(String l, String v, int lc, int vc, boolean boldValue) {
        LinearLayout r = U.row(act);
        U.addFlex(r, U.text(act, l, 14, lc), 0);
        r.addView(U.text(act, v, 14, vc, boldValue ? U.BOLD : U.NORMAL));
        return r;
    }

    private void cancel() {
        Order o = order;
        String msg = "paid".equals(o.payment_status)
                ? "Bạn chắc chắn muốn hủy đơn? Đơn đã thanh toán, quán sẽ hoàn tiền cho bạn."
                : "Bạn chắc chắn muốn hủy đơn hàng này?";
        Dialogs.confirm(act, msg, () -> {
            busy = true;
            render();
            Db.rpc("fg_cancel_order", Json.obj("p_order_id", o.id), (d, err) -> {
                busy = false;
                if (err != null) { state().toastError(err); render(); return; }
                order.status = "cancelled";
                render();
            });
        });
    }

    private void reorder() {
        Order o = order;
        List<Long> ids = new ArrayList<>();
        for (OrderItem i : o.items()) if (i.food_id != null) ids.add(i.food_id);
        Db.from("fg_foods").select("*").in("id", ids).list(Food.class, (foods, err) -> {
            List<CartItem> items = new ArrayList<>();
            for (OrderItem item : o.items()) {
                for (Food f : foods) {
                    if (item.food_id != null && f.id == item.food_id && f.is_available) {
                        CartItem c = new CartItem();
                        c.food_id = f.id; c.name = f.name; c.price = f.price; c.image = f.image;
                        c.restaurant_id = f.restaurant_id; c.restaurant_name = o.restaurant_name; c.qty = item.qty;
                        items.add(c);
                    }
                }
            }
            if (items.isEmpty()) { state().toastError("Các món trong đơn hiện không còn bán"); return; }
            state().replaceCart(items, () -> state().setCartOpen(true));
        });
    }
}
