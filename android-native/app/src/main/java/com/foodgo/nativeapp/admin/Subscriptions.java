package com.foodgo.nativeapp.admin;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Profile;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.model.SubscriptionPayment;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.Overlay;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** components/admin/subscriptions.tsx */
public final class Subscriptions {
    private Subscriptions() {}

    static class Data {
        List<SubscriptionPayment> payments;
        List<Restaurant> shops = new ArrayList<>();
        long month, all;
        int monthCount, active, expiring, pending;
        long[] months = new long[6];
        String[] monthLabels = new String[6];
    }

    private static String monthKey(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c.get(Calendar.YEAR) + "-" + c.get(Calendar.MONTH);
    }

    static void load(java.util.function.Consumer<Data> cb) {
        Data d = new Data();
        int[] pending = {2};
        Runnable done = () -> {
            if (--pending[0] > 0) return;
            List<SubscriptionPayment> paid = new ArrayList<>();
            for (SubscriptionPayment p : d.payments) if ("paid".equals(p.status) && p.amount > 0) paid.add(p);
            String now = monthKey(System.currentTimeMillis());
            for (SubscriptionPayment p : paid) {
                d.all += p.amount;
                if (monthKey(Iso.parse(p.paid_at)).equals(now)) { d.month += p.amount; d.monthCount++; }
            }
            for (Restaurant r : d.shops) {
                StoreHours.Subscription s = StoreHours.subscriptionOf(r);
                if (s.active) d.active++;
                if (s.active && s.daysLeft <= 7) d.expiring++;
            }
            for (SubscriptionPayment p : d.payments) if ("pending".equals(p.status)) d.pending++;
            // last 6 calendar months, oldest first
            Calendar c = Calendar.getInstance();
            for (int i = 0; i < 6; i++) {
                Calendar m = Calendar.getInstance();
                m.clear();
                m.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1);
                m.add(Calendar.MONTH, -(5 - i));
                String key = monthKey(m.getTimeInMillis());
                d.monthLabels[i] = "T" + (m.get(Calendar.MONTH) + 1);
                for (SubscriptionPayment p : paid) if (monthKey(Iso.parse(p.paid_at)).equals(key)) d.months[i] += p.amount;
            }
            cb.accept(d);
        };
        Db.from("fg_subscription_payments").select("*, restaurant:fg_restaurants(name)").order("created_at", false).limit(500)
                .list(SubscriptionPayment.class, (l, e) -> { d.payments = l; done.run(); });
        Db.from("fg_restaurants").select("*").notNull("owner_id").order("paid_until", true, true)
                .list(Restaurant.class, (l, e) -> { d.shops = l; done.run(); });
    }

    /** Top of "Tổng quan": the platform's own income = restaurants' monthly fees (links to the tab). */
    public static View summary(Activity a, Runnable openTab) {
        FrameLayout holder = new FrameLayout(a);
        load(d -> {
            LinearLayout box = U.col(a);
            U.pad(box, 16);
            U.pressable(box, U.INK, 16);
            box.setOnClickListener(v -> openTab.run());
            box.addView(U.text(a, "Doanh thu phí duy trì tháng này", 12, 0xB3FFFFFF));
            U.add(box, U.text(a, Fmt.money(d.month), 24, U.WHITE, U.XBOLD), 4);
            box.addView(U.text(a, d.monthCount + " lượt gia hạn", 11, 0x99FFFFFF));
            List<View> cells = new ArrayList<>();
            cells.add(mini(a, "Tổng đã thu", Fmt.money(d.all)));
            cells.add(mini(a, "Nhà hàng đang hoạt động", String.valueOf(d.active)));
            U.add(box, U.grid(a, 2, cells, 12), 12);
            holder.addView(box);
        });
        return holder;
    }

    private static View mini(Activity a, String label, String value) {
        LinearLayout c = U.col(a);
        c.addView(U.text(a, label, 12, 0xB3FFFFFF));
        U.add(c, U.text(a, value, 18, U.WHITE, U.XBOLD), 4);
        return c;
    }

    static View kpi(Activity a, String label, String value, String hint) {
        LinearLayout c = U.card(U.pad(U.col(a), 16), 16);
        c.addView(U.text(a, label, 12, U.MUTED));
        U.add(c, U.ellipsize(U.text(a, value, 20, U.INK, U.XBOLD), 1), 4);
        if (hint != null) U.add(c, U.ellipsize(U.text(a, hint, 11, U.SUBTLE), 1), 2);
        return c;
    }

    /** Small column chart with value labels above (6 months / 7 days in "Tổng quan"). */
    public static View bars(Activity a, String[] labels, long[] values, int heightDp, String highlightLabel) {
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        LinearLayout row = U.row(a);
        row.setGravity(Gravity.BOTTOM);
        for (int i = 0; i < values.length; i++) {
            LinearLayout col = U.col(a);
            col.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            col.addView(U.text(a, values[i] > 0 ? Math.round(values[i] / 1000.0) + "k" : "", 10, U.SUBTLE));
            View bar = new View(a);
            U.bg(bar, values[i] > 0 ? U.ORANGE : U.LINE, 6);
            int h = (int) (U.dp(a, heightDp - 40) * Math.max(0.03, (double) values[i] / max));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(U.dp(a, 40), h);
            bp.topMargin = U.dp(a, 4);
            col.addView(bar, bp);
            boolean hl = labels[i].equals(highlightLabel);
            TextView l = U.text(a, labels[i], 11, hl ? U.ORANGE : U.MUTED, hl ? U.BOLD : U.NORMAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.WRAP, U.WRAP);
            lp.topMargin = U.dp(a, 4);
            col.addView(l, lp);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, U.dp(a, heightDp), 1);
            if (i > 0) cp.leftMargin = U.dp(a, 8);
            row.addView(col, cp);
        }
        return row;
    }

    public static View admin(Activity a) {
        LinearLayout root = U.col(a);
        root.addView(U.spinner(a));
        reload(a, root, new String[]{"all"});
        return root;
    }

    private static void reload(Activity a, LinearLayout root, String[] status) {
        load(d -> render(a, root, d, status));
    }

    private static void render(Activity a, LinearLayout root, Data d, String[] status) {
        root.removeAllViews();
        root.addView(U.text(a, "Phí duy trì nhà hàng", 22, U.INK, U.XBOLD));
        U.add(root, U.text(a, Fmt.money(StoreHours.SUBSCRIPTION_FEE) + " / tháng / nhà hàng • chuyển khoản QR, SePay tự xác nhận", 14, U.MUTED), 4);
        List<View> k = new ArrayList<>();
        k.add(kpi(a, "Thu tháng này", Fmt.money(d.month), d.monthCount + " lượt gia hạn"));
        k.add(kpi(a, "Tổng đã thu", Fmt.money(d.all), null));
        k.add(kpi(a, "Nhà hàng đang hoạt động", String.valueOf(d.active), d.expiring + " sắp hết hạn"));
        k.add(kpi(a, "Hóa đơn chờ thanh toán", String.valueOf(d.pending), null));
        U.add(root, U.grid(a, 2, k, 12), 20);

        LinearLayout chart = U.card(U.pad(U.col(a), 16), 16);
        chart.addView(U.text(a, "Doanh thu 6 tháng gần nhất", 16, U.INK, U.XBOLD));
        U.add(chart, bars(a, d.monthLabels, d.months, 176, null), 20);
        U.add(root, chart, 16);

        LinearLayout shops = U.card(U.pad(U.col(a), 16), 16);
        shops.addView(U.text(a, "Hạn dùng các nhà hàng", 16, U.INK, U.XBOLD));
        if (d.shops.isEmpty()) {
            TextView t = U.text(a, "Chưa có nhà hàng nào đăng ký", 14, U.SUBTLE);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 0, 32);
            shops.addView(t);
        }
        for (int i = 0; i < d.shops.size(); i++) {
            Restaurant r = d.shops.get(i);
            StoreHours.Subscription s = StoreHours.subscriptionOf(r);
            LinearLayout row = U.row(a);
            U.pad(row, 0, 10);
            U.addFlex(row, U.ellipsize(U.text(a, r.name, 14, U.INK, U.BOLD), 1), 0);
            String txt = s.until == null ? "Chưa thanh toán" : !s.active ? "Đã hết hạn" : "Còn " + s.daysLeft + " ngày (" + Fmt.date(s.until) + ")";
            U.add(row, U.text(a, txt, 12, !s.active ? U.WARN : s.daysLeft <= 7 ? U.AMBER : U.GREEN_DARK, U.BOLD), 12, U.WRAP, U.WRAP);
            if (i > 0) shops.addView(U.divider(a, U.SOFT));
            shops.addView(row);
        }
        U.add(root, shops, 16);

        LinearLayout hist = U.card(U.pad(U.col(a), 16), 16);
        LinearLayout hh = U.row(a);
        U.addFlex(hh, U.text(a, "Lịch sử thanh toán", 16, U.INK, U.XBOLD), 0);
        Select sel = new Select(a).title("Lọc trạng thái").option("all", "Tất cả").option("paid", "Đã thanh toán").option("pending", "Chờ thanh toán");
        sel.set(status[0]);
        sel.onChange(v -> { status[0] = v; render(a, root, d, status); });
        hh.addView(sel, new LinearLayout.LayoutParams(U.WRAP, U.dp(a, 40)));
        hist.addView(hh);
        int n = 0;
        for (SubscriptionPayment p : d.payments) {
            if (!status[0].equals("all") && !status[0].equals(p.status)) continue;
            LinearLayout r = U.col(a);
            U.pad(r, 0, 10);
            LinearLayout top = U.row(a);
            TextView code = U.text(a, p.code, 12, U.INK, U.BOLD);
            code.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            U.addFlex(top, code, 0);
            top.addView("admin".equals(p.method) ? U.text(a, "Admin cấp", 14, U.SUBTLE) : U.text(a, Fmt.money(p.amount), 14, U.INK, U.BOLD));
            r.addView(top);
            U.add(r, U.text(a, (p.restaurant != null ? p.restaurant.name : "—") + " • " + p.months + " tháng • " + Fmt.dateTime(p.paid_at != null ? p.paid_at : p.created_at), 12, U.MUTED), 2);
            View state;
            if ("paid".equals(p.status)) state = U.pill(a, "Đã thanh toán", U.GREEN_BG, U.GREEN);
            else if ("pending".equals(p.status)) {
                TextView b = U.text(a, "Chờ tiền • Xác nhận đã nhận", 12, U.ORANGE, U.BOLD);
                U.pad(b, 8, 6);
                U.border(b, U.WHITE, 8, U.BORDER, 1);
                // fallback when SePay didn't match the transfer (wrong content…): admin checks the bank app, then confirms
                U.click(b, () -> Dialogs.confirm(a, "Xác nhận đã nhận " + Fmt.money(p.amount) + " (nội dung " + p.code + ") của " + (p.restaurant != null ? p.restaurant.name : "") + "?", () -> {
                    b.setText("Đang xử lý...");
                    Db.rpc("fg_admin_confirm_subscription", Json.obj("p_payment_id", p.id), (x, err) -> {
                        if (err != null) { AppState.get().toastError(err); b.setText("Chờ tiền • Xác nhận đã nhận"); return; }
                        AppState.get().toast("Đã gia hạn cho nhà hàng");
                        Store.invalidate();
                        reload(a, root, status);
                    });
                }));
                state = b;
            } else state = U.text(a, "Đã hủy", 12, U.SUBTLE);
            U.add(r, state, 6, U.WRAP, U.WRAP);
            if (n++ > 0) hist.addView(U.divider(a, U.SOFT));
            hist.addView(r);
        }
        if (n == 0) {
            TextView t = U.text(a, "Chưa có giao dịch", 14, U.SUBTLE);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 0, 32);
            hist.addView(t);
        }
        U.add(root, hist, 16);
    }

    /** DEMO (GrantRestaurantButton): admin gives a user a restaurant (new or an unowned one) with free months. */
    public static View grantButton(Activity a, Profile user, String ownedName, Runnable onDone) {
        TextView b = U.iconText(a, R.drawable.ic_store, 14, ownedName != null ? "Gia hạn quán" : "Cấp nhà hàng", 12, U.INK, U.BOLD);
        U.pad(b, 4, 8);
        U.click(b, () -> openGrant(a, user, ownedName, onDone));
        return b;
    }

    private static void openGrant(Activity a, Profile user, String ownedName, Runnable onDone) {
        LinearLayout panel = U.col(a);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(U.WHITE);
        float r = U.dp(a, 24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        panel.setBackground(bg);
        U.pad(panel, 20);
        LinearLayout head = U.row(a);
        head.setGravity(Gravity.TOP);
        LinearLayout t = U.col(a);
        t.addView(U.text(a, ownedName != null ? "Gia hạn nhà hàng" : "Cấp nhà hàng", 18, U.INK, U.XBOLD));
        t.addView(U.text(a, "Cho " + (user.full_name != null && !user.full_name.isEmpty() ? user.full_name : "người dùng") + " • miễn phí (demo)", 14, U.MUTED));
        U.addFlex(head, t, 0);
        FrameLayout close = new FrameLayout(a);
        U.pressable(close, U.SOFT, 999);
        close.addView(U.icon(a, R.drawable.ic_close, 24, U.INK), new FrameLayout.LayoutParams(U.dp(a, 24), U.dp(a, 24), Gravity.CENTER));
        head.addView(close, new LinearLayout.LayoutParams(U.dp(a, 40), U.dp(a, 40)));
        panel.addView(head);

        Select target = new Select(a).title("Nhà hàng").option("new", "+ Tạo nhà hàng mới");
        EditText name = U.input(a, "VD: Bún bò Cô Ba");
        LinearLayout nameBox = U.field(a, "Tên nhà hàng", name);
        U.add(nameBox, U.text(a, "Chủ quán tự ghim vị trí và thêm món trong “Kênh nhà hàng”.", 12, U.SUBTLE), 4);
        if (ownedName != null) {
            TextView own = U.iconText(a, R.drawable.ic_check_circle, 16, "Đang sở hữu " + ownedName, 14, U.INK, U.NORMAL);
            own.getCompoundDrawablesRelative()[0].setTint(U.GREEN);
            U.pad(own, 12, 10);
            U.bg(own, U.SOFT, 12);
            U.add(panel, own, 16);
        } else {
            U.add(panel, U.field(a, "Nhà hàng", target), 16);
            U.add(panel, nameBox, 16);
            Db.from("fg_restaurants").select("*").isNull("owner_id").order("name").list(Restaurant.class, (list, e) -> {
                for (Restaurant x : list) target.option(String.valueOf(x.id), "Giao " + x.name + " (đang do admin quản lý)");
            });
            target.onChange(v -> nameBox.setVisibility("new".equals(v) ? View.VISIBLE : View.GONE));
        }
        Select months = new Select(a).title("Thời hạn");
        for (int m : StoreHours.SUBSCRIPTION_MONTHS) months.option(String.valueOf(m), m + " tháng");
        U.add(panel, U.field(a, "Thời hạn", months), 16);
        PButton submit = new PButton(a, ownedName != null ? "Gia hạn 1 tháng" : "Cấp nhà hàng", PButton.PRIMARY);
        if (ownedName != null) months.onChange(v -> submit.text("Gia hạn " + v + " tháng"));
        U.add(panel, submit, 20, U.MATCH, U.dp(a, 48));

        Overlay o = new Overlay(a, panel, Overlay.BOTTOM);
        U.click(close, o::dismiss);
        submit.onClick(() -> {
            boolean isNew = ownedName != null || "new".equals(target.value());
            if (ownedName == null && isNew && name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng điền vào trường này"); return; }
            int m = Integer.parseInt(months.value());
            submit.setBusy(true);
            Db.rpc("fg_admin_grant_restaurant", Json.obj("p_user", user.id,
                    "p_restaurant_id", ownedName == null && !"new".equals(target.value()) ? Long.parseLong(target.value()) : null,
                    "p_name", ownedName == null && "new".equals(target.value()) ? name.getText().toString() : null,
                    "p_months", m), (d, err) -> {
                submit.setBusy(false);
                if (err != null) { AppState.get().toastError(err); return; }
                AppState.get().toast(ownedName != null ? "Đã gia hạn " + m + " tháng cho " + ownedName : "Đã cấp nhà hàng cho " + user.full_name);
                Store.invalidate();
                o.dismiss();
                onDone.run();
            });
        });
        o.show();
    }
}
