package com.foodgo.nativeapp.screens;

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
import android.widget.RadioButton;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Address;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.model.VoucherQuote;
import com.foodgo.nativeapp.pay.Momo;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.FoodMap;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.Geo;
import com.foodgo.nativeapp.util.StoreHours;
import com.foodgo.nativeapp.util.Validate;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** app/checkout/page.tsx */
public class CheckoutScreen extends Screen implements AppState.Listener, LocationState.Listener {
    private List<Address> addresses;
    /** -1 = 'here': deliver to a point picked on the map (prefilled with the detected location) */
    private long selected = -1;
    private boolean userPicked;
    private Geo.LatLng pin;
    private boolean resolving;
    private Restaurant store;
    private Long storeFor;
    private VoucherQuote voucher;
    private String voucherError = "";
    private boolean voucherBusy;
    private String payment = "qr";
    private boolean busy;
    private String error = "";
    private long lastSubtotal = -1;
    private boolean profileFilled;

    private FrameLayout root;
    private LinearLayout page;
    private LinearLayout hereOption, hereDetails, savedList, tripInfo, payOptions, summaryItems, summaryTotals, voucherBox, bottomBox;
    private TextView roughNote, addressLabel;
    private EditText recipient, phone, address, note, voucherInput;
    private CheckBox saveAddress, agree;
    private FoodMap map;
    private PButton applyBtn, orderBtn;
    private TextView summaryTitle;
    private final Runnable clock = new Runnable() { @Override public void run() { renderBottom(); Net.later(this, 60000); } };

    @Override
    public boolean requiresAuth() { return true; }

    @Override
    protected View build() {
        root = new FrameLayout(act);
        root.setBackgroundColor(U.BG);
        Db.from("fg_addresses").select("*").order("is_default", false).order("created_at", false).list(Address.class, (list, err) -> {
            addresses = list;
            // without a detected location, fall back to the default saved address
            if (!list.isEmpty() && LocationState.get().place == null) selected = list.get(0).id;
            renderAddresses();
        });
        state().addListener(this);
        LocationState.get().addListener(this);
        Net.later(clock, 60000);
        render();
        return root;
    }

    @Override
    public void onDestroy() {
        state().removeListener(this);
        LocationState.get().removeListener(this);
        Net.cancel(clock);
    }

    private void render() {
        root.removeAllViews();
        page = null;
        if (state().cart.isEmpty()) {
            LinearLayout p = U.col(act);
            TextView link = U.text(act, "Khám phá món ăn", 14, U.ORANGE, U.BOLD);
            U.click(link, () -> navigate("/search"));
            p.addView(U.empty(act, R.drawable.ic_bag, "Giỏ hàng đang trống", link));
            root.addView(scrollPage(p));
            return;
        }
        page = U.col(act);
        TextView back = U.text(act, "← Tiếp tục chọn món", 14, U.ORANGE, U.BOLD);
        U.pad(back, 0, 12, 0, 12);
        U.click(back, () -> navigate("/menu"));
        page.addView(back, U.lp(U.WRAP, U.WRAP));
        U.add(page, U.h1(act, "Thanh toán"), 8);

        // ---- address panel
        TextView manage = U.link(act, "Quản lý", () -> navigate("/account?tab=addresses"));
        LinearLayout[] ap = Cards.panel(act, "Địa chỉ giao hàng", manage);
        LinearLayout body = ap[1];
        hereOption = U.col(act);
        body.addView(hereOption);
        hereDetails = buildHereDetails();
        U.add(body, hereDetails, 12);
        savedList = U.col(act);
        body.addView(savedList);
        tripInfo = U.col(act);
        body.addView(tripInfo);
        U.add(page, ap[0], 32);

        // ---- payment panel
        LinearLayout[] pp = Cards.panel(act, "Phương thức thanh toán", null);
        payOptions = U.col(act);
        pp[1].addView(payOptions);
        U.add(page, pp[0], 20);

        // ---- note
        LinearLayout[] np = Cards.panel(act, "Ghi chú cho quán", null);
        note = U.maxLength(U.textarea(act, "Ví dụ: ít cay, không hành...", 3), 300);
        note.setContentDescription("Ghi chú cho quán");
        np[1].addView(note);
        U.add(page, np[0], 20);

        // ---- summary
        LinearLayout[] sp = Cards.panel(act, "Đơn hàng của bạn", null);
        summaryTitle = (TextView) ((LinearLayout) sp[0].getChildAt(0)).getChildAt(0);
        summaryItems = U.col(act);
        sp[1].addView(summaryItems);
        summaryTotals = U.col(act);
        U.add(sp[1], summaryTotals, 16);
        U.add(sp[1], U.divider(act, U.LINE), 16);
        voucherBox = U.col(act);
        voucherInput = U.input(act, "Nhập mã giảm giá");
        voucherInput.setTextSize(14);
        voucherInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        U.maxLength(voucherInput, 30);
        voucherInput.setContentDescription("Mã giảm giá");
        voucherInput.addTextChangedListener(watch(v -> {
            String up = v.toUpperCase();
            if (!up.equals(v)) { voucherInput.setText(up); voucherInput.setSelection(up.length()); return; }
            if (!voucherError.isEmpty()) { voucherError = ""; renderVoucher(); }
            if (applyBtn != null) applyBtn.setEnabled(!voucherBusy && !up.trim().isEmpty());
        }));
        voucherInput.setOnEditorActionListener((v, a, e) -> { applyVoucher(voucherInput.getText().toString()); return true; });
        U.add(sp[1], voucherBox, 16);
        agree = U.check(act, "Tôi đồng ý với điều khoản đặt hàng", true);
        U.add(sp[1], agree, 8);
        bottomBox = U.col(act);
        sp[1].addView(bottomBox);
        U.add(page, sp[0], 24);

        root.addView(scrollPage(page));
        AppState s = state();
        if (s.profile != null) fillFromProfile();
        onLocation();
        renderAddresses();
        renderPayments();
        renderSummary();
        loadStore();
    }

    private interface Changed { void on(String v); }

    private TextWatcher watch(Changed c) {
        return new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { c.on(s.toString()); }
        };
    }

    private LinearLayout buildHereDetails() {
        LinearLayout d = U.col(act);
        FrameLayout mapBox = new FrameLayout(act);
        U.border(mapBox, U.WHITE, 16, U.BORDER, 1);
        U.rounded(mapBox, 16);
        map = new FoodMap(act, 256);
        map.onPick(p -> { userPicked = true; movePin(p); });
        mapBox.addView(map, new FrameLayout.LayoutParams(U.MATCH, U.dp(act, 256)));
        LinearLayout btn = U.row(act);
        U.pad(btn, 12, 0);
        U.pressable(btn, U.WHITE, 12);
        btn.setElevation(U.dp(act, 4));
        btn.addView(U.icon(act, R.drawable.ic_locate, 16, U.ORANGE));
        U.add(btn, U.text(act, "Lấy vị trí của tôi", 14, U.ORANGE, U.BOLD), 8, U.WRAP, U.WRAP);
        btn.setOnClickListener(v -> useCurrentLocation());
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(U.WRAP, U.dp(act, 40), Gravity.TOP | Gravity.END);
        bp.setMargins(0, U.dp(act, 12), U.dp(act, 12), 0);
        mapBox.addView(btn, bp);
        d.addView(mapBox);
        roughNote = U.text(act, "", 12, U.SUBTLE);
        U.add(d, roughNote, 8);
        recipient = U.input(act, "");
        recipient.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        U.add(d, U.field(act, "Người nhận", recipient), 16);
        phone = U.input(act, "0912 345 678");
        phone.setInputType(InputType.TYPE_CLASS_PHONE);
        phone.addTextChangedListener(watch(v -> {
            String f = Validate.phoneInput(v);
            if (!f.equals(v)) { phone.setText(f); phone.setSelection(f.length()); }
        }));
        U.add(d, U.field(act, "Số điện thoại", phone), 16);
        address = U.input(act, "Số nhà, đường, phường, quận, thành phố");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS);
        LinearLayout af = U.field(act, "Địa chỉ", address);
        addressLabel = (TextView) af.getChildAt(0);
        address.addTextChangedListener(watch(v -> renderHereOption()));
        U.add(d, af, 16);
        U.add(d, U.text(act, "Bạn có thể sửa lại số nhà, hẻm, tòa nhà cho tài xế dễ tìm.", 12, U.SUBTLE), 4);
        saveAddress = U.check(act, "Lưu địa chỉ cho lần sau", true);
        U.add(d, saveAddress, 12);
        return d;
    }

    private void fillFromProfile() {
        if (profileFilled || recipient == null) return;
        AppState s = state();
        if (s.profile == null) return;
        profileFilled = true;
        if (recipient.getText().length() == 0) recipient.setText(s.profile.full_name != null ? s.profile.full_name : "");
        if (phone.getText().length() == 0) phone.setText(s.profile.phone != null ? s.profile.phone : "");
    }

    private void loadStore() {
        AppState s = state();
        // the cart's restaurant, fresh: the admin may have just opened/closed it
        Long rid = s.cart.isEmpty() ? null : s.cart.get(0).restaurant_id;
        if (rid == null || rid.equals(storeFor)) return;
        storeFor = rid;
        Store.restaurant(rid, (r, e) -> {
            store = r;
            if (page == null) return;
            renderMap();
            renderTrip();
            renderSummary();
        });
    }

    // ------------------------------------------------------------------ state reactions

    @Override
    public void onAppState() {
        AppState s = state();
        if (s.cart.isEmpty() != (page == null)) { render(); return; }
        if (page == null) return;
        fillFromProfile();
        loadStore();
        renderSummary();
        // the discount depends on the cart: re-check the applied code whenever the subtotal changes
        long sub = s.cartSubtotal();
        if (lastSubtotal >= 0 && sub != lastSubtotal && voucher != null) applyVoucher(voucher.code);
        lastSubtotal = sub;
    }

    /** the detected location becomes the delivery point unless the customer chose something else */
    @Override
    public void onLocation() {
        if (page == null) return;
        Geo.Place place = LocationState.get().place;
        if (place != null && !userPicked) {
            selected = -1;
            pin = new Geo.LatLng(place.lat, place.lng);
            address.setText(place.address);
        }
        renderAddresses();
    }

    // ------------------------------------------------------------------ address

    private Address chosen() {
        if (addresses == null) return null;
        for (Address a : addresses) if (a.id == selected) return a;
        return null;
    }

    private Geo.LatLng deliveryPos() {
        Address c = chosen();
        if (c != null) return c.lat != null && c.lng != null ? new Geo.LatLng(c.lat, c.lng) : null;
        return pin;
    }

    private Geo.LatLng restaurantPos() { return store != null && store.lat != null && store.lng != null ? new Geo.LatLng(store.lat, store.lng) : null; }

    private Double tripKm() {
        Geo.LatLng r = restaurantPos(), d = deliveryPos();
        return r != null && d != null ? Geo.distanceKm(r, d) : null;
    }

    private void movePin(Geo.LatLng p) {
        pin = p;
        resolving = true;
        renderAddresses();
        Geo.reverseGeocode(p.lat, p.lng, (a, e) -> {
            if (a != null) address.setText(a);
            resolving = false;
            renderAddresses();
        });
    }

    private void useCurrentLocation() {
        userPicked = true;
        selected = -1;
        renderAddresses();
        LocationState.get().locate((found, err) -> {
            if (found != null) { pin = new Geo.LatLng(found.lat, found.lng); address.setText(found.address); }
            renderAddresses();
        });
    }

    private View radioCard(boolean active, View content, Runnable onPick) {
        LinearLayout r = U.row(act);
        r.setGravity(Gravity.TOP);
        U.pad(r, 12, 12, 16, 12);
        if (active) U.border(r, U.PEACH2, 12, U.ORANGE, 2);
        else U.border(r, U.WHITE, 12, U.BORDER, 1);
        RadioButton rb = new RadioButton(act);
        rb.setChecked(active);
        rb.setButtonTintList(android.content.res.ColorStateList.valueOf(active ? U.ORANGE : U.FAINT));
        rb.setClickable(false);
        r.addView(rb, U.lp(U.WRAP, U.WRAP));
        U.addFlex(r, content, 4);
        U.click(r, onPick);
        return r;
    }

    private void renderHereOption() {
        if (hereOption == null) return;
        hereOption.removeAllViews();
        LocationState l = LocationState.get();
        LinearLayout c = U.col(act);
        TextView t = U.iconText(act, R.drawable.ic_locate, 16, "Vị trí hiện tại / chọn trên bản đồ", 14, U.INK, U.BOLD);
        t.getCompoundDrawablesRelative()[0].setTint(U.ORANGE);
        c.addView(t);
        String addr = address.getText().toString();
        TextView sub;
        if ("error".equals(l.status) && addr.isEmpty()) sub = U.text(act, l.error, 14, U.WARN);
        else sub = U.ellipsize(U.text(act, "locating".equals(l.status) ? "Đang xác định vị trí..." : !addr.isEmpty() ? addr : "Bấm “Lấy vị trí của tôi” hoặc chạm vào bản đồ", 14, U.MUTED), 1);
        U.add(c, sub, 4);
        hereOption.addView(radioCard(selected == -1, c, () -> { userPicked = true; selected = -1; renderAddresses(); }));
    }

    private void renderAddresses() {
        if (page == null) return;
        renderHereOption();
        hereDetails.setVisibility(selected == -1 ? View.VISIBLE : View.GONE);
        addressLabel.setText(resolving ? "Địa chỉ (đang cập nhật...)" : "Địa chỉ");
        Geo.Place place = LocationState.get().place;
        // pin still sits on a coarse IP/Wi-Fi guess the customer hasn't corrected
        boolean roughPin = !userPicked && place != null && place.accuracy > Geo.ROUGH_ACCURACY_M;
        if (roughPin) {
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Thiết bị chỉ ước tính được vị trí (sai số khoảng " + Geo.formatKm(place.accuracy / 1000) + "). Hãy ");
            int s = b.length();
            b.append("kéo ghim tới đúng nhà bạn");
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
            b.append(" hoặc sửa địa chỉ bên dưới.");
            roughNote.setText(b);
            roughNote.setTextColor(U.AMBER_DARK);
            U.pad(roughNote, 12, 8);
            U.bg(roughNote, U.AMBER_BG, 12);
        } else {
            roughNote.setText("Kéo ghim hoặc chạm vào bản đồ để chọn đúng điểm giao hàng.");
            roughNote.setTextColor(U.SUBTLE);
            roughNote.setPadding(0, 0, 0, 0);
            roughNote.setBackground(null);
        }
        renderMap();
        savedList.removeAllViews();
        if (addresses != null) for (Address a : addresses) {
            LinearLayout c = U.col(act);
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder(a.recipient);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), 0);
            int s = b.length();
            b.append(" • ").append(a.phone);
            b.setSpan(new android.text.style.ForegroundColorSpan(U.MUTED), s, b.length(), 0);
            LinearLayout line = new com.foodgo.nativeapp.ui.FlowRow(act, 8, 4);
            line.addView(U.text(act, b, 14, U.INK));
            if (a.is_default) line.addView(U.badge(act, "Mặc định", U.PEACH, U.ORANGE));
            c.addView(line);
            U.add(c, U.text(act, a.address, 14, U.MUTED), 4);
            U.add(savedList, radioCard(selected == a.id, c, () -> { userPicked = true; selected = a.id; renderAddresses(); }), 12);
        }
        renderTrip();
    }

    private void renderMap() {
        if (map == null || selected != -1) return;
        Geo.Place place = LocationState.get().place;
        Geo.LatLng picker = pin != null ? pin : place != null ? new Geo.LatLng(place.lat, place.lng) : null;
        Geo.LatLng r = restaurantPos();
        List<FoodMap.MapMarker> markers = new ArrayList<>();
        if (r != null) markers.add(new FoodMap.MapMarker("r", "restaurant", r.lat, r.lng, store.name));
        List<double[]> fit = pin != null ? listOf(pin) : r != null ? listOf(r) : null;
        map.render(markers, null, picker, fit);
    }

    private static List<double[]> listOf(Geo.LatLng p) { List<double[]> l = new ArrayList<>(); l.add(new double[]{p.lat, p.lng}); return l; }

    private void renderTrip() {
        if (tripInfo == null) return;
        tripInfo.removeAllViews();
        Double km = tripKm();
        if (km == null) return;
        LinearLayout r = U.row(act);
        U.pad(r, 16, 12);
        U.bg(r, U.SOFT, 12);
        r.addView(U.icon(act, R.drawable.ic_bike, 16, U.ORANGE));
        android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Cách nhà hàng khoảng ");
        int s = b.length();
        b.append(Geo.formatKm(km * 1.3));
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
        b.setSpan(new android.text.style.ForegroundColorSpan(U.INK), s, b.length(), 0);
        b.append(" đường đi • dự kiến ").append(String.valueOf(Math.max(10, Math.round(km * 1.3 * 3 + 12)))).append(" phút");
        U.addFlex(r, U.text(act, b, 14, U.MUTED), 8);
        U.add(tripInfo, r, 12);
    }

    // ------------------------------------------------------------------ payment

    private void renderPayments() {
        payOptions.removeAllViews();
        payOption("momo", Cards.momoIcon(act, 36, 24, 10), "Ví MoMo", "Thanh toán qua ví MoMo, xác nhận tức thì");
        payOption("qr", null, "Chuyển khoản QR", "Quét mã VietQR bằng app ngân hàng, xác nhận tự động");
        payOption("cod", null, "Thanh toán khi nhận hàng", "Trả tiền mặt cho tài xế");
    }

    private void payOption(String method, View icon, String title, String desc) {
        boolean active = payment.equals(method);
        LinearLayout b = U.col(act);
        U.pad(b, 16);
        if (active) U.border(b, U.PEACH2, 12, U.ORANGE, 2);
        else U.border(b, U.WHITE, 12, U.BORDER, 1);
        View ic = icon != null ? icon : U.icon(act, method.equals("qr") ? R.drawable.ic_qr : R.drawable.ic_cash, 24, active ? U.ORANGE : U.MUTED);
        b.addView(ic);
        U.add(b, U.text(act, title, 15, U.INK, U.BOLD), 12);
        U.add(b, U.text(act, desc, 12, U.MUTED), 4);
        U.click(b, () -> { payment = method; renderPayments(); renderBottom(); });
        U.add(payOptions, b, payOptions.getChildCount() == 0 ? 0 : 12);
    }

    // ------------------------------------------------------------------ summary & voucher

    private void renderSummary() {
        if (summaryItems == null) return;
        AppState s = state();
        summaryTitle.setText(store != null ? "Đơn từ " + store.name : "Đơn hàng của bạn");
        summaryItems.removeAllViews();
        for (CartItem x : s.cart) {
            LinearLayout r = U.row(act);
            if (x.image != null) r.addView(U.img(act, x.image, 56, 56, 8));
            LinearLayout info = U.col(act);
            info.addView(U.ellipsize(U.text(act, x.name, 14, U.INK, U.BOLD), 1));
            info.addView(U.text(act, "x" + x.qty, 14, U.SUBTLE));
            U.addFlex(r, info, x.image != null ? 12 : 0);
            r.addView(U.text(act, Fmt.money(x.price * x.qty), 14, U.INK, U.BOLD));
            U.add(summaryItems, r, summaryItems.getChildCount() == 0 ? 0 : 16);
        }
        summaryTotals.removeAllViews();
        long sub = s.cartSubtotal(), fee = Fmt.shippingFee(sub), discount = voucher != null ? voucher.discount : 0;
        summaryTotals.addView(U.divider(act, U.LINE));
        U.add(summaryTotals, line("Tạm tính", Fmt.money(sub), U.MUTED, U.MUTED), 16);
        U.add(summaryTotals, line("Phí giao hàng", fee > 0 ? Fmt.money(fee) : "Miễn phí", U.MUTED, fee > 0 ? U.MUTED : U.GREEN_SOFT), 12);
        if (discount > 0) U.add(summaryTotals, line("Giảm giá (" + voucher.code + ")", "-" + Fmt.money(discount), U.GREEN_DARK, U.GREEN_DARK), 12);
        LinearLayout total = U.row(act);
        U.addFlex(total, U.text(act, "Tổng cộng", 18, U.INK, U.XBOLD), 0);
        total.addView(U.text(act, Fmt.money(sub + fee - discount), 18, U.ORANGE, U.XBOLD));
        U.add(summaryTotals, total, 16);
        renderVoucher();
        renderBottom();
    }

    private View line(String l, String v, int lc, int vc) {
        LinearLayout r = U.row(act);
        U.addFlex(r, U.text(act, l, 14, lc), 0);
        r.addView(U.text(act, v, 14, vc));
        return r;
    }

    private void renderVoucher() {
        voucherBox.removeAllViews();
        if (voucherInput.getParent() != null) ((android.view.ViewGroup) voucherInput.getParent()).removeView(voucherInput);
        if (voucher != null) {
            LinearLayout r = U.row(act);
            U.pad(r, 12, 10);
            android.graphics.drawable.GradientDrawable d = U.round(U.GREEN_TINT, U.dp(act, 12));
            d.setStroke(U.dp(act, 1), U.GREEN_SOFT, U.dp(act, 4), U.dp(act, 3));
            r.setBackground(d);
            r.addView(U.icon(act, R.drawable.ic_ticket, 20, U.GREEN_DARK));
            LinearLayout info = U.col(act);
            info.addView(U.text(act, voucher.code, 14, U.GREEN_DARK, U.BOLD));
            info.addView(U.ellipsize(U.text(act, voucher.description != null && !voucher.description.isEmpty() ? voucher.description : "Giảm " + Fmt.money(voucher.discount), 12, U.MUTED), 1));
            U.addFlex(r, info, 12);
            TextView remove = U.text(act, "Bỏ mã", 12, U.MUTED, U.BOLD);
            U.pad(remove, 4, 4);
            U.click(remove, () -> { voucher = null; voucherInput.setText(""); renderSummary(); });
            r.addView(remove);
            voucherBox.addView(r);
        } else {
            LinearLayout r = U.row(act);
            r.addView(voucherInput, new LinearLayout.LayoutParams(0, U.dp(act, 44), 1));
            applyBtn = new PButton(act, "Áp dụng", PButton.OUTLINE);
            applyBtn.textColor(U.ORANGE);
            applyBtn.label().setTextSize(14);
            U.weight(applyBtn.label(), U.BOLD);
            applyBtn.setBusy(voucherBusy);
            applyBtn.setEnabled(!voucherInput.getText().toString().trim().isEmpty());
            applyBtn.onClick(() -> applyVoucher(voucherInput.getText().toString()));
            U.add(r, applyBtn, 8, U.WRAP, U.dp(act, 44));
            voucherBox.addView(r);
        }
        if (!voucherError.isEmpty()) U.add(voucherBox, U.text(act, voucherError, 12, U.RED_TEXT), 8);
    }

    private void applyVoucher(String code) {
        if (code.trim().isEmpty()) return;
        voucherBusy = true;
        voucherError = "";
        renderVoucher();
        AppState s = state();
        Long rid = s.cart.isEmpty() ? null : s.cart.get(0).restaurant_id;
        Db.rpc("fg_check_voucher", Json.obj("p_code", code, "p_subtotal", s.cartSubtotal(), "p_restaurant_id", rid), (data, err) -> {
            voucherBusy = false;
            // an invalid code comes back as { error } (so the DB can count failed guesses), a throttled user as an error
            String failure = err != null ? err : data != null && data.isJsonObject() ? Json.str(data.getAsJsonObject(), "error") : null;
            if (failure != null) { voucher = null; voucherError = failure; renderSummary(); return; }
            voucher = Json.as(data, VoucherQuote.class);
            if (voucher != null) voucherInput.setText(voucher.code);
            renderSummary();
        });
    }

    private void renderBottom() {
        if (bottomBox == null) return;
        bottomBox.removeAllViews();
        StoreHours hours = store != null ? StoreHours.of(store) : null;
        boolean closed = hours != null && !hours.open;
        if (!error.isEmpty()) U.add(bottomBox, U.errorBox(act, error), 16);
        if (closed) U.add(bottomBox, Cards.closedNotice(act, hours), 16);
        orderBtn = new PButton(act, payment.equals("qr") ? "Đặt hàng & lấy mã QR" : payment.equals("momo") ? "Đặt hàng & thanh toán MoMo" : "Đặt hàng", PButton.PRIMARY);
        orderBtn.label().setTextSize(16);
        orderBtn.setBusy(busy);
        orderBtn.setEnabled(!closed);
        orderBtn.onClick(this::placeOrder);
        U.add(bottomBox, orderBtn, 16, U.MATCH, U.dp(act, 48));
    }

    private void fail(String msg) {
        error = msg;
        busy = false;
        renderBottom();
    }

    private void placeOrder() {
        error = "";
        AppState s = state();
        Address c = chosen();
        String rRecipient = c != null ? c.recipient : recipient.getText().toString();
        String rPhone = c != null ? c.phone : phone.getText().toString();
        String rAddress = c != null ? c.address : address.getText().toString();
        Double km = tripKm();
        if (km != null && km > 30) { fail("Vị trí giao hàng cách nhà hàng " + Geo.formatKm(km) + ", vượt quá phạm vi giao hàng (30 km)"); return; }
        if (rRecipient == null || rRecipient.trim().isEmpty() || rAddress == null || rAddress.trim().isEmpty()) { fail("Vui lòng nhập đầy đủ người nhận và địa chỉ"); return; }
        String cleanPhone = Validate.normalizePhone(rPhone);
        if (cleanPhone == null) { fail(c != null ? Validate.PHONE_ERROR + ". Vui lòng sửa địa chỉ đã lưu trong Tài khoản." : Validate.PHONE_ERROR); return; }
        if (!agree.isChecked()) { fail("Bạn cần đồng ý với điều khoản đặt hàng"); return; }
        busy = true;
        renderBottom();
        Geo.LatLng dp = deliveryPos();
        Runnable place = () -> {
            JsonArray items = new JsonArray();
            for (CartItem x : s.cart) items.add(Json.obj("food_id", x.food_id, "qty", x.qty));
            Db.rpc("fg_place_order", Json.obj(
                    "p_items", items,
                    "p_recipient", rRecipient,
                    "p_phone", cleanPhone,
                    "p_address", rAddress,
                    "p_payment_method", payment,
                    "p_note", note.getText().toString(),
                    "p_lat", dp != null ? dp.lat : null,
                    "p_lng", dp != null ? dp.lng : null,
                    "p_voucher_code", voucher != null ? voucher.code : null), (data, err) -> {
                if (err != null) { fail(err); return; }
                Order order = Json.as(data, Order.class);
                if (order == null) { fail("Đã có lỗi xảy ra, vui lòng thử lại"); return; }
                s.clearCart();
                if (payment.equals("momo")) {
                    Momo.start(act, order.id, (v, e) -> {
                        // the order exists; the customer can retry MoMo from the order page
                        if (e != null) s.toastError(e);
                        replace("/orders/" + order.id + "?new=1");
                    });
                } else {
                    s.toast("Đặt hàng thành công");
                    replace("/orders/" + order.id + "?new=1");
                }
            });
        };
        if (c == null && saveAddress.isChecked() && s.userId() != null) {
            Db.from("fg_addresses").insert(Json.obj("user_id", s.userId(), "label", "Nhà riêng", "recipient", rRecipient, "phone", cleanPhone,
                    "address", rAddress, "lat", pin != null ? pin.lat : null, "lng", pin != null ? pin.lng : null,
                    "is_default", addresses == null || addresses.isEmpty())).run((d, e) -> place.run());
        } else place.run();
    }

    @SuppressWarnings("unused")
    private static JsonElement unusedEl() { return null; }

    @SuppressWarnings("unused")
    private static JsonObject unusedObj() { return null; }

    @SuppressWarnings("unused")
    private static ImageView unusedImg() { return null; }
}
