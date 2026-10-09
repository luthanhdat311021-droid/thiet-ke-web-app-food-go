package com.foodgo.nativeapp.screens;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.MainActivity;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;

/** CartDrawer in site-shell.tsx */
public class CartDrawer implements AppState.Listener {
    private final MainActivity act;
    private final LinearLayout root;
    private final LinearLayout list, totals;
    private final TextView restaurantLink;
    private final TextView clearAll;

    public CartDrawer(MainActivity act) {
        this.act = act;
        root = U.col(act);
        root.setBackgroundColor(U.WHITE);
        root.setElevation(U.dp(act, 16));

        LinearLayout head = U.row(act);
        U.pad(head, 24, 24, 24, 0);
        LinearLayout titles = U.col(act);
        restaurantLink = U.text(act, "", 12, U.ORANGE, U.BOLD);
        restaurantLink.setAllCaps(true);
        titles.addView(restaurantLink);
        U.add(titles, U.text(act, "Giỏ hàng của bạn", 24, U.INK, U.XBOLD), 4);
        U.addFlex(head, titles, 0);
        FrameLayout close = new FrameLayout(act);
        U.pressable(close, U.SOFT, 999);
        close.addView(U.icon(act, R.drawable.ic_close, 24, U.INK), new FrameLayout.LayoutParams(U.dp(act, 24), U.dp(act, 24), Gravity.CENTER));
        close.setContentDescription("Đóng giỏ hàng");
        close.setOnClickListener(v -> AppState.get().setCartOpen(false));
        head.addView(close, new LinearLayout.LayoutParams(U.dp(act, 40), U.dp(act, 40)));
        root.addView(head);

        ScrollView scroll = new ScrollView(act);
        LinearLayout inner = U.col(act);
        U.pad(inner, 24, 32, 24, 16);
        list = U.col(act);
        inner.addView(list);
        clearAll = U.text(act, "Xóa tất cả", 14, U.SUBTLE, U.SEMI);
        U.pad(clearAll, 0, 8, 0, 8);
        U.click(clearAll, () -> AppState.get().clearCart());
        U.add(inner, clearAll, 16, U.WRAP, U.WRAP);
        scroll.addView(inner);
        root.addView(scroll, new LinearLayout.LayoutParams(U.MATCH, 0, 1));

        root.addView(U.divider(act, U.LINE));
        totals = U.col(act);
        U.pad(totals, 24, 20, 24, 24);
        root.addView(totals);

        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { AppState.get().addListener(CartDrawer.this); onAppState(); }
            @Override public void onViewDetachedFromWindow(View v) { AppState.get().removeListener(CartDrawer.this); }
        });
        onAppState();
    }

    public View view() { return root; }

    @Override
    public void onAppState() {
        AppState s = AppState.get();
        list.removeAllViews();
        if (!s.cart.isEmpty()) {
            CartItem first = s.cart.get(0);
            restaurantLink.setVisibility(View.VISIBLE);
            restaurantLink.setText(first.restaurant_name);
            restaurantLink.setOnClickListener(v -> { s.setCartOpen(false); act.navigate("/restaurant/" + first.restaurant_id); });
        } else restaurantLink.setVisibility(View.GONE);

        for (int i = 0; i < s.cart.size(); i++) {
            CartItem x = s.cart.get(i);
            LinearLayout row = U.row(act);
            row.setGravity(Gravity.TOP);
            U.pad(row, 0, 0, 0, 20);
            if (x.image != null) row.addView(U.img(act, x.image, 80, 80, 12));
            LinearLayout info = U.col(act);
            info.addView(U.ellipsize(U.text(act, x.name, 16, U.INK, U.BOLD), 1));
            U.add(info, U.text(act, Fmt.money(x.price), 14, U.ORANGE), 4);
            LinearLayout q = U.row(act);
            q.addView(btn(R.drawable.ic_remove, U.SOFT, U.INK, "Giảm", () -> s.setQty(x.food_id, x.qty - 1)));
            TextView n = U.text(act, String.valueOf(x.qty), 14, U.INK, U.BOLD);
            n.setGravity(Gravity.CENTER);
            U.add(q, n, 12, U.dp(act, 20), U.WRAP);
            U.add(q, btn(R.drawable.ic_add, U.PEACH, U.ORANGE, "Tăng", () -> s.setQty(x.food_id, x.qty + 1)), 12, U.dp(act, 32), U.dp(act, 32));
            U.add(info, q, 12);
            U.addFlex(row, info, x.image != null ? 12 : 0);
            ImageView del = U.icon(act, R.drawable.ic_delete, 16, U.FAINT);
            U.pad(del, 8);
            del.setContentDescription("Xóa món");
            U.click(del, () -> s.setQty(x.food_id, 0));
            row.addView(del, new LinearLayout.LayoutParams(U.dp(act, 32), U.dp(act, 32)));
            U.add(list, row, i == 0 ? 0 : 20);
            list.addView(U.divider(act, U.LINE));
        }
        clearAll.setVisibility(s.cart.isEmpty() ? View.GONE : View.VISIBLE);
        if (s.cart.isEmpty()) {
            LinearLayout e = U.col(act);
            e.setGravity(Gravity.CENTER_HORIZONTAL);
            U.pad(e, 0, 80, 0, 80);
            e.addView(U.icon(act, R.drawable.ic_bag, 48, U.PEACH_ICON));
            U.add(e, U.text(act, "Giỏ hàng đang trống", 16, U.INK, U.BOLD), 16, U.WRAP, U.WRAP);
            PButton b = new PButton(act, "Xem nhà hàng", PButton.OUTLINE);
            b.onClick(() -> { s.setCartOpen(false); act.navigate("/menu"); });
            U.add(e, b, 20, U.WRAP, U.dp(act, 44));
            list.addView(e);
        }

        totals.removeAllViews();
        long sub = s.cartSubtotal();
        long fee = s.cart.isEmpty() ? 0 : Fmt.shippingFee(sub);
        totals.addView(line("Tạm tính", Fmt.money(sub), U.INK));
        U.add(totals, line("Phí giao hàng", fee > 0 ? Fmt.money(fee) : "Miễn phí", fee > 0 ? U.INK : U.GREEN_SOFT), 12);
        if (!s.cart.isEmpty() && fee > 0) U.add(totals, U.text(act, "Mua thêm " + Fmt.money(Fmt.FREE_SHIP_FROM - sub) + " để được miễn phí giao hàng", 12, U.SUBTLE), 8);
        LinearLayout total = U.row(act);
        U.addFlex(total, U.text(act, "Tổng cộng", 18, U.INK, U.XBOLD), 0);
        total.addView(U.text(act, Fmt.money(sub + fee), 18, U.ORANGE, U.XBOLD));
        U.add(totals, total, 20);
        PButton pay = new PButton(act, "Tiến hành thanh toán", PButton.PRIMARY);
        pay.setEnabled(!s.cart.isEmpty());
        pay.onClick(() -> { s.setCartOpen(false); act.navigate("/checkout"); });
        U.add(totals, pay, 20, U.MATCH, U.dp(act, 48));
    }

    private View line(String label, String value, int valueColor) {
        LinearLayout r = U.row(act);
        U.addFlex(r, U.text(act, label, 14, U.MUTED), 0);
        r.addView(U.text(act, value, 14, valueColor, U.BOLD));
        return r;
    }

    private View btn(int icon, int bg, int fg, String label, Runnable r) {
        FrameLayout b = new FrameLayout(act);
        U.pressable(b, bg, 8);
        b.addView(U.icon(act, icon, 12, fg), new FrameLayout.LayoutParams(U.dp(act, 12), U.dp(act, 12), Gravity.CENTER));
        b.setContentDescription(label);
        b.setOnClickListener(v -> r.run());
        b.setLayoutParams(new LinearLayout.LayoutParams(U.dp(act, 32), U.dp(act, 32)));
        return b;
    }
}
