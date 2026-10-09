package com.foodgo.nativeapp.shop;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.admin.ImageInput;
import com.foodgo.nativeapp.admin.PlacePicker;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;
import com.foodgo.nativeapp.util.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** components/shop/register-form.tsx: self-service sign-up creates the restaurant (hidden until paid) and its first invoice. */
public class RegisterForm extends LinearLayout {
    private Double lat, lng;
    private String image = "", logo = "";

    public RegisterForm(Activity a, Runnable onRegistered) {
        super(a);
        setOrientation(VERTICAL);
        AppState s = AppState.get();
        addView(U.text(a, "KÊNH NHÀ HÀNG", 14, U.ORANGE, U.SEMI));
        U.add(this, U.h1(a, "Đăng ký nhà hàng của bạn"), 8);
        U.add(this, U.text(a, "Mở gian hàng trên FoodGo, tự quản lý thực đơn, mã giảm giá và doanh thu.", 14, U.MUTED), 8);
        List<View> perks = new ArrayList<>();
        int[] icons = {R.drawable.ic_utensils, R.drawable.ic_ticket, R.drawable.ic_receipt, R.drawable.ic_bar_chart};
        String[] texts = {"Thêm món, giá, ảnh", "Tạo mã giảm giá riêng", "Nhận & xử lý đơn", "Xem doanh thu"};
        for (int i = 0; i < 4; i++) {
            TextView t = U.iconText(a, icons[i], 16, texts[i], 13, U.INK, U.SEMI);
            t.getCompoundDrawablesRelative()[0].setTint(U.ORANGE);
            U.pad(t, 12);
            U.card(t, 12);
            perks.add(t);
        }
        U.add(this, U.grid(a, 2, perks, 12), 24);
        android.text.SpannableStringBuilder fee = new android.text.SpannableStringBuilder("Phí duy trì ");
        int st = fee.length();
        fee.append(Fmt.money(StoreHours.SUBSCRIPTION_FEE)).append("/tháng");
        fee.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), st, fee.length(), 0);
        fee.append(", thanh toán bằng chuyển khoản QR. Nhà hàng hiển thị với khách ngay khi nhận được tiền.");
        TextView feeNote = U.text(a, fee, 14, U.AMBER_DARK);
        U.pad(feeNote, 16, 12);
        U.bg(feeNote, U.AMBER_BG, 12);
        U.add(this, feeNote, 16);

        LinearLayout form = U.card(a);
        EditText name = U.maxLength(U.input(a, "VD: Bún bò Cô Ba"), 80);
        form.addView(U.field(a, "Tên nhà hàng *", name));
        EditText cuisine = U.maxLength(U.input(a, "VD: Bún bò • Bánh canh"), 120);
        U.add(form, U.field(a, "Món chính", cuisine), 16);
        EditText phone = U.input(a, "0912 345 678");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.setText(s.profile != null && s.profile.phone != null ? s.profile.phone : "");
        phone.addTextChangedListener(watch(v -> {
            String f = Validate.phoneInput(v);
            if (!f.equals(v)) { phone.setText(f); phone.setSelection(f.length()); }
        }));
        U.add(form, U.field(a, "Số điện thoại *", phone), 16);
        EditText address = U.input(a, "");
        PlacePicker[] picker = new PlacePicker[1];
        picker[0] = new PlacePicker(a, null, null, "", (la, ln, addr) -> {
            lat = la; lng = ln;
            if (addr != null) address.setText(addr);
        });
        U.add(form, U.field(a, "Vị trí nhà hàng *", picker[0]), 16);
        address.addTextChangedListener(watch(v -> picker[0].setAddress(v)));
        LinearLayout af = U.field(a, "Địa chỉ hiển thị cho khách *", address);
        U.add(af, U.text(a, "Tự điền khi ghim trên bản đồ; có thể sửa lại cho dễ đọc", 12, U.SUBTLE), 4);
        U.add(form, af, 16);
        EditText open = timeInput(a, "07:00");
        U.add(form, U.field(a, "Giờ mở cửa", open), 16);
        EditText close = timeInput(a, "21:00");
        U.add(form, U.field(a, "Giờ đóng cửa", close), 16);
        EditText delivery = U.input(a, "");
        delivery.setText("20-30 phút");
        U.add(form, U.field(a, "Thời gian giao dự kiến", delivery), 16);
        U.add(form, U.field(a, "Ảnh bìa", new ImageInput(a, "").onChange(v -> image = v)), 16);
        U.add(form, U.field(a, "Logo", new ImageInput(a, "").onChange(v -> logo = v)), 16);
        LinearLayout errBox = U.col(a);
        form.addView(errBox);
        PButton submit = new PButton(a, "Đăng ký & thanh toán " + Fmt.money(StoreHours.SUBSCRIPTION_FEE), PButton.PRIMARY);
        U.add(form, submit, 16, U.MATCH, U.dp(a, 48));
        U.add(this, form, 24);

        submit.onClick(() -> {
            errBox.removeAllViews();
            if (name.getText().toString().trim().isEmpty()) { name.setError("Vui lòng điền vào trường này"); name.requestFocus(); return; }
            if (phone.getText().toString().trim().isEmpty()) { phone.setError("Vui lòng điền vào trường này"); phone.requestFocus(); return; }
            if (address.getText().toString().trim().isEmpty()) { address.setError("Vui lòng điền vào trường này"); address.requestFocus(); return; }
            String ph = Validate.normalizePhone(phone.getText().toString());
            if (ph == null) { U.add(errBox, U.errorBox(a, Validate.PHONE_ERROR), 16); return; }
            if (lat == null || lng == null) { U.add(errBox, U.errorBox(a, "Vui lòng ghim vị trí nhà hàng trên bản đồ"), 16); return; }
            submit.setBusy(true);
            Db.rpc("fg_register_restaurant", Json.obj("p", Json.obj(
                    "name", name.getText().toString(), "cuisine", cuisine.getText().toString(), "phone", ph,
                    "address", address.getText().toString(), "delivery_time", delivery.getText().toString(),
                    "open_time", open.getText().toString(), "close_time", close.getText().toString(),
                    "image", image, "logo", logo, "lat", lat, "lng", lng)), (d, err) -> {
                submit.setBusy(false);
                if (err != null) { U.add(errBox, U.errorBox(a, err), 16); return; }
                s.toast("Đã tạo nhà hàng. Thanh toán phí tháng đầu để bắt đầu bán!");
                onRegistered.run();
            });
        });
    }

    private interface Fn { void on(String v); }

    private static TextWatcher watch(Fn f) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { f.on(s.toString()); }
        };
    }

    private static EditText timeInput(Activity a, String value) {
        EditText e = U.input(a, "--:--");
        e.setFocusable(false);
        e.setText(value);
        e.setOnClickListener(v -> {
            String cur = e.getText().toString();
            int h = 7, m = 0;
            if (cur.matches("\\d{2}:\\d{2}")) { h = Integer.parseInt(cur.substring(0, 2)); m = Integer.parseInt(cur.substring(3)); }
            TimePickerDialog d = new TimePickerDialog(a, (tp, hh, mm) -> e.setText(String.format(Locale.US, "%02d:%02d", hh, mm)), h, m, true);
            d.setButton(TimePickerDialog.BUTTON_NEUTRAL, "Xóa", (di, w) -> e.setText(""));
            d.show();
        });
        return e;
    }
}
