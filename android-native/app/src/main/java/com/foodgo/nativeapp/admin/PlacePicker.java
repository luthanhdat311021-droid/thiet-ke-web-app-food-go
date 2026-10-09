package com.foodgo.nativeapp.admin;

import android.content.Context;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.BaseActivity;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.ui.FoodMap;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;
import com.foodgo.nativeapp.util.Geo;

import java.util.ArrayList;
import java.util.List;

/**
 * components/admin/location-picker.tsx: pin a place on the map — search an address, use the device location,
 * tap the map or drag the pin. The address found for the pin is offered (and filled in when still empty).
 */
public class PlacePicker extends LinearLayout {
    public interface Listener {
        /** address == null: keep the current one */
        void onChange(Double lat, Double lng, String address);
    }

    private Double lat, lng;
    private String address;
    private final Listener listener;
    private final EditText query;
    private final PButton searchBtn;
    private final FoodMap map;
    private final TextView hintOnMap, status, error;
    private final LinearLayout suggestion;
    private final PButton locateBtn;
    private String busy; // search | locate | reverse
    private String suggested;

    public PlacePicker(Context c, Double lat, Double lng, String address, Listener l) {
        super(c);
        setOrientation(VERTICAL);
        this.lat = lat; this.lng = lng; this.address = address; this.listener = l;
        LinearLayout row = U.row(c);
        query = U.input(c, "Tìm địa chỉ, vd: 12 Trần Văn Ơn, Thủ Dầu Một");
        query.setTextSize(14);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setContentDescription("Tìm địa chỉ trên bản đồ");
        query.setOnEditorActionListener((v, a, e) -> { search(); return true; });
        row.addView(query, new LayoutParams(0, U.dp(c, 44), 1));
        searchBtn = new PButton(c, "Tìm", PButton.DARK);
        searchBtn.label().setTextSize(14);
        searchBtn.onClick(this::search);
        U.add(row, searchBtn, 8, U.WRAP, U.dp(c, 44));
        addView(row);

        FrameLayout box = new FrameLayout(c);
        U.border(box, U.WHITE, 12, U.BORDER, 1);
        U.rounded(box, 12);
        map = new FoodMap(c, 256);
        map.onPick(p -> place(p, null));
        box.addView(map, new FrameLayout.LayoutParams(U.MATCH, U.dp(c, 256)));
        locateBtn = new PButton(c, "Vị trí của tôi", PButton.OUTLINE, R.drawable.ic_locate);
        locateBtn.textColor(U.ORANGE);
        locateBtn.label().setTextSize(12);
        U.pad(locateBtn, 12, 0);
        locateBtn.setElevation(U.dp(c, 4));
        locateBtn.onClick(this::locate);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(U.WRAP, U.dp(c, 36), Gravity.TOP | Gravity.END);
        lp.setMargins(0, U.dp(c, 12), U.dp(c, 12), 0);
        box.addView(locateBtn, lp);
        hintOnMap = U.text(c, "Bấm lên bản đồ để ghim vị trí nhà hàng", 12, U.WHITE, U.SEMI);
        U.pad(hintOnMap, 12, 6);
        U.bg(hintOnMap, 0xD9241C19, 999);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = U.dp(c, 12);
        box.addView(hintOnMap, hp);
        U.add(this, box, 8);
        status = U.text(c, "", 12, U.MUTED);
        U.add(this, status, 8);
        suggestion = U.col(c);
        addView(suggestion);
        error = U.text(c, "", 12, U.RED_TEXT);
        addView(error);
        render();
    }

    private static double round(double n) { return Math.round(n * 1e6) / 1e6; }

    private void render() {
        boolean has = lat != null && lng != null;
        List<double[]> fit = null;
        if (has) { fit = new ArrayList<>(); fit.add(new double[]{lat, lng}); }
        map.render(null, null, has ? new Geo.LatLng(lat, lng) : null, fit);
        hintOnMap.setVisibility(has ? GONE : VISIBLE);
        searchBtn.setBusy("search".equals(busy));
        searchBtn.setEnabled(busy == null);
        locateBtn.setBusy("locate".equals(busy));
        locateBtn.setEnabled(busy == null);
        if (has) {
            status.setTextColor(U.MUTED);
            status.setText("reverse".equals(busy) ? "Đang tìm địa chỉ của điểm ghim..." : "Đã ghim: " + Geo.coords(lat, lng) + " • kéo ghim để chỉnh");
        } else {
            status.setTextColor(U.WARN);
            status.setText("Chưa ghim vị trí");
        }
        suggestion.removeAllViews();
        if (suggested != null) {
            LinearLayout r = U.row(getContext());
            r.setGravity(Gravity.TOP);
            U.pad(r, 12, 8);
            U.bg(r, U.AMBER_BG, 12);
            android.text.SpannableStringBuilder b = new android.text.SpannableStringBuilder("Địa chỉ tại điểm ghim: ");
            int s = b.length();
            b.append(suggested);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), s, b.length(), 0);
            U.addFlex(r, U.text(getContext(), b, 12, U.AMBER_DARK), 0);
            TextView use = U.text(getContext(), "Dùng địa chỉ này", 12, U.ORANGE, U.BOLD);
            String sug = suggested;
            U.click(use, () -> { address = sug; listener.onChange(lat, lng, sug); suggested = null; render(); });
            U.add(r, use, 8, U.WRAP, U.WRAP);
            U.add(suggestion, r, 8);
        }
        error.setVisibility(error.getText().length() > 0 ? VISIBLE : GONE);
    }

    /** The parent edited the address text field. */
    public void setAddress(String a) { address = a; }

    private void place(Geo.LatLng p, String knownAddress) {
        error.setText("");
        lat = round(p.lat);
        lng = round(p.lng);
        listener.onChange(lat, lng, null);
        if (knownAddress != null) { gotAddress(knownAddress); return; }
        busy = "reverse";
        render();
        Geo.reverseGeocode(p.lat, p.lng, (a, e) -> { busy = null; gotAddress(a); });
    }

    private void gotAddress(String a) {
        if (a == null) { suggested = null; render(); return; }
        // keep an address the admin typed; otherwise take the one for the pin
        if (address == null || address.trim().isEmpty()) { address = a; listener.onChange(lat, lng, a); suggested = null; }
        else suggested = a.equals(address) ? null : a;
        render();
    }

    private void search() {
        String q = query.getText().toString().trim();
        if (q.isEmpty() && address != null) q = address.trim();
        if (q.length() < 4) { error.setText("Nhập ít nhất 4 ký tự để tìm địa chỉ"); render(); return; }
        busy = "search";
        error.setText("");
        render();
        Geo.search(q, (hit, err) -> {
            busy = null;
            if (hit == null) { error.setText("Không tìm thấy địa chỉ này. Thử ghi rõ hơn, hoặc bấm thẳng lên bản đồ."); render(); return; }
            place(hit, hit.address);
        });
    }

    private void locate() {
        android.app.Activity a = U2.activity(getContext());
        if (!(a instanceof BaseActivity)) return;
        busy = "locate";
        error.setText("");
        render();
        ((BaseActivity) a).withLocationPermission(granted -> {
            if (!granted) { busy = null; error.setText(Geo.ERR_PERMISSION); render(); return; }
            Geo.currentPosition(a, 8000, (p, err) -> {
                busy = null;
                if (p == null) { error.setText(err != null ? err : "Không lấy được vị trí"); render(); return; }
                render();
                place(p, null);
            });
        });
    }
}
