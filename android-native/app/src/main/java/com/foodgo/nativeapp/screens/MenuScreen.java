package com.foodgo.nativeapp.screens;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.state.Store;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Select;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.StoreHours;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** app/menu/page.tsx: all restaurants, open ones first, then nearest (or best rated). */
public class MenuScreen extends Screen implements LocationState.Listener {
    private List<Restaurant> restaurants;
    private TextView summary;
    private EditText q;
    private Select sort;
    private CheckBox openOnly;
    private LinearLayout list;
    private final Runnable clock = new Runnable() {
        @Override public void run() { render(); Net.later(this, 60000); }
    };

    @Override
    protected View build() {
        LinearLayout page = U.col(act);
        page.addView(U.text(act, "NHÀ HÀNG", 14, U.ORANGE, U.SEMI));
        U.add(page, U.h1(act, "Chọn nhà hàng"), 8);
        summary = U.text(act, "Đang tải...", 14, U.MUTED);
        U.add(page, summary, 8);

        q = U.input(act, "Tìm tên nhà hàng, món chính...");
        q.setTextSize(14);
        q.setLayoutParams(U.lp(U.MATCH, U.dp(act, 44)));
        android.graphics.drawable.Drawable sd = U.drawable(act, R.drawable.ic_search, U.SUBTLE);
        sd.setBounds(0, 0, U.dp(act, 16), U.dp(act, 16));
        q.setCompoundDrawablesRelative(sd, null, null, null);
        q.setCompoundDrawablePadding(U.dp(act, 8));
        U.pad(q, 12, 0);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { render(); }
        });
        U.add(page, q, 24, U.MATCH, U.dp(act, 44));

        LinearLayout filters = U.row(act);
        sort = new Select(act).title("Sắp xếp").option("near", "Gần tôi nhất").option("rating", "Đánh giá cao");
        sort.onChange(v -> render());
        filters.addView(sort, U.lp(U.WRAP, U.dp(act, 44)));
        openOnly = U.check(act, "Đang mở cửa", false);
        openOnly.setTextColor(U.INK);
        U.weight(openOnly, U.SEMI);
        U.pad(openOnly, 4, 0, 12, 0);
        U.card(openOnly, 12);
        openOnly.setOnCheckedChangeListener((b, c) -> render());
        U.add(filters, openOnly, 12, U.WRAP, U.dp(act, 44));
        U.add(page, filters, 12);

        list = U.col(act);
        for (int i = 0; i < 4; i++) U.add(list, U.skeleton(act, 240), i == 0 ? 0 : 20);
        U.add(page, list, 24);

        Store.restaurants((l, e) -> { restaurants = l; render(); });
        LocationState.get().addListener(this);
        Net.later(clock, 60000);
        return scrollPage(page);
    }

    @Override
    public void onDestroy() {
        LocationState.get().removeListener(this);
        Net.cancel(clock);
    }

    @Override
    public void onLocation() { render(); }

    private void render() {
        if (restaurants == null || list == null) return;
        String term = q.getText().toString().trim().toLowerCase(Locale.ROOT);
        boolean rating = "rating".equals(sort.value());
        List<Restaurant> shown = new ArrayList<>();
        for (Restaurant r : restaurants) {
            boolean open = StoreHours.of(r).open;
            String hay = (r.name + " " + (r.cuisine != null ? r.cuisine : "") + " " + (r.address != null ? r.address : "")).toLowerCase(Locale.ROOT);
            if ((!openOnly.isChecked() || open) && (term.isEmpty() || hay.contains(term))) shown.add(r);
        }
        Collections.sort(shown, (a, b) -> {
            int o = Boolean.compare(StoreHours.of(b).open, StoreHours.of(a).open);
            if (o != 0) return o;
            if (rating) {
                int r = Double.compare(b.rating, a.rating);
                return r != 0 ? r : Integer.compare(b.review_count, a.review_count);
            }
            return Double.compare(Cards.distanceOrStored(a), Cards.distanceOrStored(b));
        });
        summary.setText(shown.size() + " nhà hàng" + (LocationState.get().place != null ? " • sắp xếp theo khoảng cách tới bạn" : ""));
        list.removeAllViews();
        for (int i = 0; i < shown.size(); i++) U.add(list, Cards.restaurantCard(act, shown.get(i)), i == 0 ? 0 : 20);
        if (shown.isEmpty()) {
            TextView hint = restaurants.isEmpty() ? null : U.text(act, "Thử từ khóa khác hoặc bỏ lọc \"Đang mở cửa\".", 14, U.MUTED);
            list.addView(U.empty(act, R.drawable.ic_store, restaurants.isEmpty() ? "Chưa có nhà hàng nào" : "Không có nhà hàng phù hợp", hint));
        }
    }
}
