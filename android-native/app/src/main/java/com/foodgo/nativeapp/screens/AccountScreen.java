package com.foodgo.nativeapp.screens;

import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Address;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Dialogs;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.Password;
import com.foodgo.nativeapp.util.Validate;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** app/account/page.tsx */
public class AccountScreen extends Screen implements AppState.Listener {
    private static final String[][] TABS = {
            {"profile", "Thông tin cá nhân"}, {"addresses", "Địa chỉ"}, {"favorites", "Món yêu thích"}, {"password", "Đổi mật khẩu"}};
    private static final int[] TAB_ICONS = {R.drawable.ic_person, R.drawable.ic_pin, R.drawable.ic_heart_outline, R.drawable.ic_key};

    private String tab = "profile";
    private LinearLayout nav, content;
    private View adminLink;
    private Runnable onState;

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    protected View build() {
        String t = param("tab");
        for (String[] x : TABS) if (x[0].equals(t)) tab = t;
        LinearLayout page = U.col(act);
        page.addView(U.h1(act, "Tài khoản"));
        nav = U.row(act);
        U.pad(nav, 12);
        LinearLayout navCard = U.card(U.col(act), 16);
        navCard.addView(U.hscroll(act, nav));
        U.add(page, navCard, 32);
        content = U.col(act);
        U.add(page, content, 24);
        state().addListener(this);
        renderNav();
        renderTab();
        return scrollPage(page);
    }

    @Override
    public void onDestroy() { state().removeListener(this); }

    @Override
    public void onAppState() {
        if (adminLink != null) adminLink.setVisibility(state().profile != null && state().profile.isAdmin() ? View.VISIBLE : View.GONE);
        if (onState != null) onState.run();
    }

    private View navButton(int icon, String label, boolean active, int color, Runnable r) {
        TextView t = U.iconText(act, icon, 16, label, 14, active ? U.ORANGE : color, active ? U.BOLD : U.NORMAL);
        U.pad(t, 12, 12);
        if (active) U.pressable(t, U.PEACH, 12); else U.pressable(t, U.WHITE, 12);
        t.setOnClickListener(v -> r.run());
        return t;
    }

    private void renderNav() {
        nav.removeAllViews();
        for (int i = 0; i < TABS.length; i++) {
            String id = TABS[i][0];
            U.add(nav, navButton(TAB_ICONS[i], TABS[i][1], tab.equals(id), U.MUTED, () -> {
                // router.replace(`${pathname}?tab=${id}`)
                tab = id;
                path = "/account?tab=" + id;
                uri = android.net.Uri.parse("https://foodgo.local" + path);
                renderNav();
                renderTab();
            }), i == 0 ? 0 : 4, U.WRAP, U.WRAP);
        }
        U.add(nav, navButton(R.drawable.ic_receipt, "Đơn hàng", false, U.MUTED, () -> navigate("/orders")), 4, U.WRAP, U.WRAP);
        U.add(nav, navButton(R.drawable.ic_store, "Kênh nhà hàng", false, U.MUTED, () -> navigate("/shop")), 4, U.WRAP, U.WRAP);
        adminLink = navButton(R.drawable.ic_dashboard, "Trang quản trị", false, U.MUTED, () -> navigate("/admin"));
        U.add(nav, adminLink, 4, U.WRAP, U.WRAP);
        TextView out = (TextView) navButton(R.drawable.ic_logout, "Đăng xuất", false, U.RED, () -> state().signOut());
        U.add(nav, out, 4, U.WRAP, U.WRAP);
        onAppState();
    }

    private void renderTab() {
        content.removeAllViews();
        onState = null;
        switch (tab) {
            case "addresses": addressesTab(); break;
            case "favorites": favoritesTab(); break;
            case "password": passwordTab(); break;
            default: profileTab();
        }
    }

    // ------------------------------------------------------------------ profile

    private void profileTab() {
        AppState s = state();
        LinearLayout[] p = Cards.panel(act, "Thông tin cá nhân", null);
        LinearLayout head = U.row(act);
        FrameLayout avatar = new FrameLayout(act);
        head.addView(avatar, new LinearLayout.LayoutParams(U.dp(act, 80), U.dp(act, 80)));
        LinearLayout who = U.col(act);
        TextView nameView = U.text(act, "", 14, U.INK, U.BOLD);
        who.addView(nameView);
        who.addView(U.text(act, s.email() != null ? s.email() : "", 14, U.MUTED));
        U.addFlex(head, who, 16);
        p[1].addView(head);

        EditText fullName = U.input(act, "");
        fullName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        U.add(p[1], U.field(act, "Họ và tên", fullName), 20);
        EditText email = U.input(act, "");
        email.setText(s.email());
        email.setEnabled(false);
        U.add(p[1], U.field(act, "Email", email), 20);
        EditText phone = U.input(act, "0912 345 678");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.addTextChangedListener(filter(phone));
        U.add(p[1], U.field(act, "Số điện thoại", phone), 20);
        EditText birthday = dateInput();
        U.add(p[1], U.field(act, "Ngày sinh", birthday), 20);
        PButton save = new PButton(act, "Lưu thay đổi", PButton.PRIMARY);
        U.add(p[1], save, 20, U.WRAP, U.dp(act, 44));
        content.addView(p[0]);

        Runnable refreshHead = () -> {
            String n = fullName.getText().toString();
            nameView.setText(n.isEmpty() ? "Chưa đặt tên" : n);
            avatar.removeAllViews();
            String url = s.profile != null ? s.profile.avatar_url : null;
            if (url != null) avatar.addView(U.img(act, url, 80, 80, 40), new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            else {
                TextView i = U.text(act, Fmt.initials(!n.isEmpty() ? n : s.email()), 20, U.ORANGE, U.BOLD);
                i.setGravity(Gravity.CENTER);
                U.bg(i, U.PEACH3, 999);
                avatar.addView(i, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
            }
        };
        fullName.addTextChangedListener(after(v -> refreshHead.run()));
        String[] loadedFor = {null};
        onState = () -> {
            // setFullName(profile.full_name) … whenever the profile (re)loads
            if (s.profile == null) return;
            String key = s.profile.full_name + "|" + s.profile.phone + "|" + s.profile.birthday;
            if (key.equals(loadedFor[0])) return;
            loadedFor[0] = key;
            fullName.setText(s.profile.full_name != null ? s.profile.full_name : "");
            phone.setText(s.profile.phone != null ? s.profile.phone : "");
            birthday.setText(s.profile.birthday != null ? s.profile.birthday : "");
            refreshHead.run();
        };
        onState.run();
        refreshHead.run();
        save.onClick(() -> {
            String uid = s.userId();
            if (uid == null) return;
            if (fullName.getText().toString().trim().isEmpty()) { fullName.setError("Vui lòng điền vào trường này"); return; }
            String ph = phone.getText().toString();
            String clean = ph.trim().isEmpty() ? null : Validate.normalizePhone(ph);
            if (!ph.trim().isEmpty() && clean == null) { s.toastError(Validate.PHONE_ERROR); return; }
            save.setBusy(true);
            String b = birthday.getText().toString();
            Db.from("fg_profiles").update(Json.obj("full_name", fullName.getText().toString().trim(), "phone", clean, "birthday", b.isEmpty() ? null : b))
                    .eq("id", uid).run((d, err) -> {
                        save.setBusy(false);
                        if (err != null) { s.toastError(err); return; }
                        s.refreshProfile(() -> s.toast("Đã lưu thông tin"));
                    });
        });
    }

    private EditText dateInput() {
        EditText e = U.input(act, "dd/mm/yyyy");
        e.setFocusable(false);
        e.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            String cur = e.getText().toString();
            if (cur.matches("\\d{4}-\\d{2}-\\d{2}")) {
                c.set(Integer.parseInt(cur.substring(0, 4)), Integer.parseInt(cur.substring(5, 7)) - 1, Integer.parseInt(cur.substring(8, 10)));
            }
            DatePickerDialog d = new DatePickerDialog(act, (dp, y, m, day) -> e.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, day)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            d.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Xóa", (di, w) -> e.setText(""));
            d.show();
        });
        return e;
    }

    private interface Fn { void on(String v); }

    private TextWatcher after(Fn f) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { f.on(s.toString()); }
        };
    }

    private TextWatcher filter(EditText e) {
        return after(v -> {
            String f = Validate.phoneInput(v);
            if (!f.equals(v)) { e.setText(f); e.setSelection(f.length()); }
        });
    }

    // ------------------------------------------------------------------ addresses

    private List<Address> addresses;
    private Address editing; // null = no form; id 0 = new
    private LinearLayout addrBody;

    private void addressesTab() {
        PButton add = new PButton(act, "Thêm", PButton.PRIMARY, R.drawable.ic_add);
        add.label().setTextSize(14);
        add.onClick(() -> { editing = new Address(); renderAddresses(); });
        LinearLayout[] p = Cards.panel(act, "Sổ địa chỉ", add);
        addrBody = p[1];
        content.addView(p[0]);
        View addBtn = add;
        addBtn.setTag("add");
        renderAddresses();
        loadAddresses();
    }

    private void loadAddresses() {
        Db.from("fg_addresses").select("*").order("is_default", false).order("created_at").list(Address.class, (l, e) -> { addresses = l; renderAddresses(); });
    }

    private void renderAddresses() {
        if (addrBody == null || !"addresses".equals(tab)) return;
        View addBtn = content.findViewWithTag("add");
        if (addBtn != null) addBtn.setVisibility(editing == null ? View.VISIBLE : View.GONE);
        addrBody.removeAllViews();
        AppState s = state();
        if (editing != null) {
            LinearLayout form = U.col(act);
            U.pad(form, 16);
            U.bg(form, U.BG, 16);
            Address ed = editing;
            EditText label = U.input(act, "Nhà riêng, Công ty...");
            label.setText(ed.label);
            form.addView(U.field(act, "Tên gợi nhớ", label));
            EditText rec = U.input(act, "");
            rec.setText(ed.recipient);
            U.add(form, U.field(act, "Người nhận", rec), 16);
            EditText ph = U.input(act, "0912 345 678");
            ph.setInputType(InputType.TYPE_CLASS_PHONE);
            ph.setText(ed.phone);
            ph.addTextChangedListener(filter(ph));
            U.add(form, U.field(act, "Số điện thoại", ph), 16);
            EditText addr = U.input(act, "");
            addr.setText(ed.address);
            U.add(form, U.field(act, "Địa chỉ", addr), 16);
            CheckBox def = U.check(act, "Đặt làm địa chỉ mặc định", ed.is_default);
            U.add(form, def, 12);
            LinearLayout btns = U.row(act);
            PButton save = new PButton(act, "Lưu", PButton.PRIMARY);
            PButton cancel = new PButton(act, "Hủy", PButton.OUTLINE);
            cancel.onClick(() -> { editing = null; renderAddresses(); });
            btns.addView(save, U.lp(U.WRAP, U.dp(act, 44)));
            U.add(btns, cancel, 12, U.WRAP, U.dp(act, 44));
            U.add(form, btns, 16);
            save.onClick(() -> {
                String uid = s.userId();
                if (uid == null) return;
                if (rec.getText().toString().isEmpty()) { rec.setError("Vui lòng điền vào trường này"); return; }
                if (ph.getText().toString().isEmpty()) { ph.setError("Vui lòng điền vào trường này"); return; }
                if (addr.getText().toString().isEmpty()) { addr.setError("Vui lòng điền vào trường này"); return; }
                String phone = Validate.normalizePhone(ph.getText().toString());
                if (phone == null) { s.toastError(Validate.PHONE_ERROR); return; }
                save.setBusy(true);
                String lb = label.getText().toString();
                JsonObject row = Json.obj("label", lb.isEmpty() ? "Nhà riêng" : lb, "recipient", rec.getText().toString(), "phone", phone,
                        "address", addr.getText().toString(), "is_default", def.isChecked() || addresses == null || addresses.isEmpty());
                Db q;
                if (ed.id != 0) q = Db.from("fg_addresses").update(row).eq("id", ed.id);
                else { row.addProperty("user_id", uid); q = Db.from("fg_addresses").insert(row); }
                q.run((d, err) -> {
                    save.setBusy(false);
                    if (err != null) { s.toastError(err); return; }
                    editing = null;
                    s.toast("Đã lưu địa chỉ");
                    loadAddresses();
                });
            });
            addrBody.addView(form);
        }
        if (addresses == null) { addrBody.addView(U.spinner(act)); return; }
        if (addresses.isEmpty() && editing == null) {
            TextView t = U.text(act, "Bạn chưa lưu địa chỉ nào", 14, U.SUBTLE);
            t.setGravity(Gravity.CENTER);
            U.pad(t, 0, 40);
            addrBody.addView(t);
        }
        for (Address a : addresses) {
            LinearLayout r = U.row(act);
            r.setGravity(Gravity.TOP);
            U.pad(r, 16);
            U.border(r, U.WHITE, 12, U.BORDER, 1);
            r.addView(U.icon(act, R.drawable.ic_pin, 20, U.ORANGE));
            LinearLayout info = U.col(act);
            LinearLayout l1 = new com.foodgo.nativeapp.ui.FlowRow(act, 8, 4);
            l1.addView(U.text(act, a.label, 14, U.INK, U.BOLD));
            if (a.is_default) l1.addView(U.badge(act, "Mặc định", U.PEACH, U.ORANGE));
            info.addView(l1);
            U.add(info, U.text(act, a.recipient + " • " + a.phone, 14, U.INK), 4);
            info.addView(U.text(act, a.address, 14, U.MUTED));
            if (!a.is_default) {
                TextView mk = U.text(act, "Đặt làm mặc định", 12, U.ORANGE, U.BOLD);
                U.pad(mk, 0, 4, 0, 4);
                U.click(mk, () -> Db.from("fg_addresses").update(Json.obj("is_default", true)).eq("id", a.id).run((d, e) -> loadAddresses()));
                U.add(info, mk, 4, U.WRAP, U.WRAP);
            }
            U.addFlex(r, info, 12);
            r.addView(iconBtn(R.drawable.ic_edit, "Sửa", () -> { editing = a; renderAddresses(); }));
            r.addView(iconBtn(R.drawable.ic_delete, "Xóa", () -> Dialogs.confirm(act, "Xóa địa chỉ \"" + a.label + "\"?", () ->
                    Db.from("fg_addresses").delete().eq("id", a.id).run((d, err) -> {
                        if (err != null) { s.toastError(err); return; }
                        loadAddresses();
                    }))));
            U.add(addrBody, r, addrBody.getChildCount() == 0 ? 0 : 12);
        }
    }

    private View iconBtn(int icon, String label, Runnable r) {
        ImageView i = U.icon(act, icon, 16, U.MUTED);
        U.pad(i, 10);
        i.setContentDescription(label);
        U.click(i, r);
        i.setLayoutParams(new LinearLayout.LayoutParams(U.dp(act, 36), U.dp(act, 36)));
        return i;
    }

    // ------------------------------------------------------------------ favorites

    private void favoritesTab() {
        LinearLayout box = U.col(act);
        content.addView(box);
        box.addView(U.spinner(act));
        List<Food>[] foods = new List[]{null};
        Runnable render = () -> {
            if (foods[0] == null) return;
            box.removeAllViews();
            List<Food> shown = new ArrayList<>();
            for (Food f : foods[0]) if (state().favoriteIds.contains(f.id)) shown.add(f);
            if (shown.isEmpty()) box.addView(U.empty(act, R.drawable.ic_heart_outline, "Chưa có món yêu thích", U.text(act, "Bấm biểu tượng trái tim trên món ăn để lưu lại.", 14, U.MUTED)));
            for (int i = 0; i < shown.size(); i++) U.add(box, new FoodCardView(act, shown.get(i), null), i == 0 ? 0 : 20);
        };
        Db.from("fg_favorites").select("created_at, foods:fg_foods(" + Store.FOOD_SELECT + ")").order("created_at", false).rows((rows, err) -> {
            List<Food> list = new ArrayList<>();
            for (JsonObject r : rows) {
                JsonElement f = r.get("foods");
                Food food = Json.as(f, Food.class);
                if (food != null) list.add(food);
            }
            foods[0] = list;
            render.run();
        });
        int[] count = {-1};
        onState = () -> {
            int n = state().favoriteIds.size();
            if (n != count[0]) { count[0] = n; render.run(); }
        };
    }

    // ------------------------------------------------------------------ password

    private void passwordTab() {
        LinearLayout[] p = Cards.panel(act, "Đổi mật khẩu", null);
        p[1].addView(U.text(act, "Nếu bạn đăng nhập bằng Google, bạn có thể đặt mật khẩu để đăng nhập thêm bằng email.", 14, U.MUTED));
        EditText pw = U.input(act, "");
        pw.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        U.add(p[1], U.field(act, "Mật khẩu mới", pw), 16);
        EditText confirm = U.input(act, "");
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        U.add(p[1], U.field(act, "Nhập lại mật khẩu", confirm), 16);
        LinearLayout errBox = U.col(act);
        p[1].addView(errBox);
        PButton save = new PButton(act, "Cập nhật mật khẩu", PButton.PRIMARY);
        U.add(p[1], save, 16, U.WRAP, U.dp(act, 44));
        content.addView(p[0]);
        java.util.function.Consumer<String> setError = msg -> {
            errBox.removeAllViews();
            if (msg != null && !msg.isEmpty()) U.add(errBox, U.errorBox(act, msg), 16);
        };
        save.onClick(() -> {
            setError.accept("");
            String a = pw.getText().toString(), b = confirm.getText().toString();
            if (a.isEmpty()) { pw.setError("Vui lòng điền vào trường này"); return; }
            if (b.isEmpty()) { confirm.setError("Vui lòng điền vào trường này"); return; }
            if (!a.equals(b)) { setError.accept("Mật khẩu nhập lại không khớp"); return; }
            save.setBusy(true);
            Password.validateNew(a, (problem, e0) -> {
                if (problem != null) { save.setBusy(false); setError.accept(problem); return; }
                Auth.updatePassword(a, (d, err) -> {
                    save.setBusy(false);
                    if (err != null) { setError.accept(err); return; }
                    pw.setText(""); confirm.setText("");
                    state().toast("Đã đổi mật khẩu");
                });
            });
        });
    }
}
