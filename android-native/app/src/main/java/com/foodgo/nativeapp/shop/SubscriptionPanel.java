package com.foodgo.nativeapp.shop;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.model.SubscriptionPayment;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;

import java.util.ArrayList;
import java.util.List;

/**
 * components/shop/subscription-panel.tsx: the owner's monthly fee — status, renew (1/3/6/12 months) by VietQR.
 * SePay sees the money and fg_sepay_confirm() extends paid_until; this panel polls every 5 s until then.
 */
public class SubscriptionPanel extends LinearLayout {
    private final Activity a;
    private final Restaurant restaurant;
    private final Runnable onPaid;
    private List<SubscriptionPayment> history;
    private SubscriptionPayment invoice;
    private int months = 1;
    private boolean busy;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (invoice == null || !"pending".equals(invoice.status) || !isAttachedToWindow()) return;
            long id = invoice.id;
            Db.from("fg_subscription_payments").select("*").eq("id", id).maybeSingle(SubscriptionPayment.class, (p, e) -> {
                if (invoice == null || invoice.id != id) return;
                if (p != null && "paid".equals(p.status)) {
                    invoice = null;
                    AppState.get().toast("Đã nhận phí duy trì. Nhà hàng đã được gia hạn!");
                    loadHistory();
                    onPaid.run();
                    render();
                } else Net.later(poll, 5000);
            });
        }
    };

    public SubscriptionPanel(Activity a, Restaurant restaurant, Runnable onPaid) {
        super(a);
        this.a = a;
        this.restaurant = restaurant;
        this.onPaid = onPaid;
        setOrientation(VERTICAL);
        U.card(this, 16);
        U.pad(this, 16);
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Net.cancel(poll); Net.later(poll, 5000); }
            @Override public void onViewDetachedFromWindow(View v) { Net.cancel(poll); }
        });
        render();
        loadHistory();
    }

    private void loadHistory() {
        Db.from("fg_subscription_payments").select("*").eq("restaurant_id", restaurant.id).order("created_at", false).limit(50)
                .list(SubscriptionPayment.class, (list, e) -> {
                    history = list;
                    // an unpaid invoice from registration (or an earlier visit) is shown right away
                    if (invoice == null) for (SubscriptionPayment p : list) if ("pending".equals(p.status) && "qr".equals(p.method)) { setInvoice(p); break; }
                    render();
                });
    }

    private void setInvoice(SubscriptionPayment p) {
        invoice = p;
        Net.cancel(poll);
        if (p != null) Net.later(poll, 5000);
    }

    private void render() {
        removeAllViews();
        StoreHours.Subscription sub = StoreHours.subscriptionOf(restaurant);
        LinearLayout head = U.col(a);
        head.addView(U.text(a, "Phí duy trì nhà hàng", 16, U.INK, U.XBOLD));
        U.add(head, U.text(a, Fmt.money(StoreHours.SUBSCRIPTION_FEE) + " / tháng • nhà hàng hiển thị cho khách khi còn hạn", 14, U.MUTED), 2);
        String pillText = sub.until == null ? "Chưa kích hoạt" : !sub.active ? "Đã hết hạn" : "Còn " + sub.daysLeft + " ngày • đến " + Fmt.date(sub.until);
        int[] pc = !sub.active ? new int[]{U.RED_BG, U.RED_TEXT} : sub.daysLeft <= 7 ? new int[]{U.AMBER_BG, U.AMBER} : new int[]{U.GREEN_BG, U.GREEN_DARK};
        U.add(head, U.pill(a, pillText, pc[0], pc[1]), 8, U.WRAP, U.WRAP);
        addView(head);
        if (!sub.active) {
            U.add(this, U.note(a, (sub.until != null ? "Nhà hàng đã hết hạn nên đang bị ẩn với khách." : "Nhà hàng chưa hiển thị với khách.")
                    + " Thanh toán phí để " + (sub.until != null ? "mở lại" : "bắt đầu nhận đơn") + ".", U.RED_BG, U.RED_DARK), 16);
        }
        if (invoice != null) {
            LinearLayout box = U.col(a);
            U.pad(box, 16);
            U.border(box, U.WHITE, 16, U.LINE, 1);
            String qr = Fmt.vietQrUrl(invoice.amount, invoice.code);
            if (qr != null) {
                ImageView img = new ImageView(a);
                img.setAdjustViewBounds(true);
                img.setContentDescription("Mã QR thanh toán " + Fmt.money(invoice.amount));
                U.rounded(img, 12);
                com.bumptech.glide.Glide.with(a.getApplicationContext()).load(qr).into(img);
                LayoutParams ip = new LayoutParams(U.dp(a, 224), U.WRAP);
                ip.gravity = Gravity.CENTER_HORIZONTAL;
                box.addView(img, ip);
            } else box.addView(U.note(a, "Chưa cấu hình tài khoản nhận tiền (NEXT_PUBLIC_VIETQR_*).", U.AMBER_BG, U.AMBER_DARK));
            U.add(box, U.text(a, "Gia hạn " + invoice.months + " tháng", 14, U.INK, U.BOLD), 16);
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Mở app ngân hàng, quét mã QR. Số tiền và nội dung đã điền sẵn — ");
            int s = b.length();
            b.append("giữ nguyên nội dung chuyển khoản");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
            b.append(".");
            U.add(box, U.text(a, b, 14, U.MUTED), 4);
            U.add(box, Cards.copyRow(a, "Số tiền", String.valueOf(invoice.amount), Fmt.money(invoice.amount)), 16);
            U.add(box, Cards.copyRow(a, "Nội dung", invoice.code, null), 8);
            if (!Config.VIETQR_ACCOUNT_NO.isEmpty()) U.add(box, Cards.copyRow(a, "Số tài khoản", Config.VIETQR_ACCOUNT_NO, null), 8);
            LinearLayout w = U.row(a);
            w.addView(U.progress(a, 16, U.AMBER));
            U.add(w, U.text(a, "Đang chờ tiền vào tài khoản...", 14, U.AMBER, U.SEMI), 8, U.WRAP, U.WRAP);
            U.add(box, w, 16);
            U.add(box, U.text(a, "Trang tự cập nhật khi nhận được tiền (thường dưới 1 phút).", 12, U.SUBTLE), 4);
            TextView other = U.text(a, "Chọn gói khác", 12, U.MUTED, U.BOLD);
            U.pad(other, 0, 4, 0, 4);
            U.click(other, () -> { setInvoice(null); render(); });
            U.add(box, other, 12, U.WRAP, U.WRAP);
            U.add(this, box, 20);
        } else {
            U.add(this, U.text(a, sub.active ? "Gia hạn thêm" : "Chọn gói", 14, U.INK, U.SEMI), 20);
            List<View> opts = new ArrayList<>();
            for (int m : StoreHours.SUBSCRIPTION_MONTHS) {
                LinearLayout o = U.col(a);
                U.pad(o, 12);
                if (months == m) U.border(o, U.PEACH2, 12, U.ORANGE, 2); else U.border(o, U.WHITE, 12, U.BORDER, 1);
                o.addView(U.text(a, m + " tháng", 15, U.INK, U.BOLD));
                o.addView(U.text(a, Fmt.money(StoreHours.SUBSCRIPTION_FEE * m), 12, U.MUTED));
                U.click(o, () -> { months = m; render(); });
                opts.add(o);
            }
            U.add(this, U.grid(a, 2, opts, 8), 8);
            PButton pay = new PButton(a, "Thanh toán " + Fmt.money(StoreHours.SUBSCRIPTION_FEE * months) + " bằng QR", PButton.PRIMARY, R.drawable.ic_qr);
            pay.setBusy(busy);
            pay.onClick(() -> {
                busy = true;
                pay.setBusy(true);
                Db.rpc("fg_create_subscription_payment", Json.obj("p_months", months), (d, err) -> {
                    busy = false;
                    if (err != null) { AppState.get().toastError(err); render(); return; }
                    setInvoice(Json.as(d, SubscriptionPayment.class));
                    loadHistory();
                    render();
                });
            });
            U.add(this, pay, 16, U.MATCH, U.dp(a, 48));
        }
        if (history != null) {
            List<SubscriptionPayment> paid = new ArrayList<>();
            for (SubscriptionPayment p : history) if ("paid".equals(p.status)) paid.add(p);
            if (!paid.isEmpty()) {
                U.add(this, U.divider(a, U.LINE), 24);
                U.add(this, U.text(a, "Lịch sử gia hạn", 14, U.INK, U.BOLD), 16);
                for (int i = 0; i < paid.size(); i++) {
                    SubscriptionPayment p = paid.get(i);
                    LinearLayout r = U.row(a);
                    U.pad(r, 0, 8);
                    r.addView(U.icon(a, R.drawable.ic_check_circle, 16, U.GREEN));
                    U.addFlex(r, U.text(a, p.months + " tháng • " + Fmt.dateTime(p.paid_at), 14, U.INK), 8);
                    r.addView("admin".equals(p.method) ? U.text(a, "Admin tặng", 14, U.SUBTLE) : U.text(a, Fmt.money(p.amount), 14, U.INK, U.BOLD));
                    if (i > 0) addView(U.divider(a, U.SOFT));
                    addView(r);
                }
            }
        }
    }
}
