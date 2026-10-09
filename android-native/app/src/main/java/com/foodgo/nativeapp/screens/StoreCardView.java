package com.foodgo.nativeapp.screens;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Net;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.FoodMap;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.Geo;
import com.foodgo.nativeapp.util.StoreHours;

import java.util.ArrayList;
import java.util.List;

/** <StoreCard store />: a restaurant's header card with an expandable location map; re-checks hours every minute. */
public class StoreCardView extends LinearLayout {
    private final Restaurant store;
    private final LinearLayout card;
    private boolean showMap;
    private final Runnable tick = new Runnable() {
        @Override public void run() { render(); Net.later(this, 60000); }
    };

    public StoreCardView(Context c, Restaurant store) {
        super(c);
        this.store = store;
        setOrientation(VERTICAL);
        setClipChildren(false);
        FrameLayout cover = new FrameLayout(c);
        if (store.image != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            U.load(img, store.image);
            cover.addView(img, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        }
        View shade = new View(c);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0x66000000, 0x00000000}));
        cover.addView(shade, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        addView(cover, new LayoutParams(U.MATCH, U.dp(c, 160)));
        card = U.card(c);
        LayoutParams cp = new LayoutParams(U.MATCH, U.WRAP);
        cp.setMargins(U.dp(c, 20), -U.dp(c, 48), U.dp(c, 20), 0);
        addView(card, cp);
        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { Net.cancel(tick); Net.later(tick, 60000); }
            @Override public void onViewDetachedFromWindow(View v) { Net.cancel(tick); }
        });
        render();
    }

    private void render() {
        Context c = getContext();
        StoreHours hours = StoreHours.of(store);
        card.removeAllViews();
        LinearLayout top = U.row(c);
        top.setGravity(Gravity.TOP);
        top.addView(Cards.restaurantLogo(c, store.name, store.logo, 64, 24));
        LinearLayout info = U.col(c);
        LinearLayout title = flow(c);
        title.addView(U.text(c, store.name, 24, U.INK, U.XBOLD));
        title.addView(hours.open ? U.badge(c, "Đang mở cửa", U.GREEN_BG, U.GREEN_DARK) : U.badge(c, "Đã đóng cửa", U.GREY_BG, U.MUTED));
        if (store.tag != null && !store.tag.isEmpty()) title.addView(U.badge(c, store.tag, U.AMBER_BG, U.AMBER));
        info.addView(title);
        if (store.cuisine != null && !store.cuisine.isEmpty()) U.add(info, U.text(c, store.cuisine, 14, U.MUTED), 4);
        LinearLayout meta = flow(c);
        if (store.review_count > 0) {
            TextView r = U.iconText(c, R.drawable.ic_star, 12, Fmt.fixed1(store.rating) + " (" + Fmt.count(store.review_count) + " đánh giá)", 12, U.MUTED, U.NORMAL);
            r.getCompoundDrawablesRelative()[0].setTint(U.STAR);
            meta.addView(r);
        } else meta.addView(U.text(c, "Chưa có đánh giá", 12, U.MUTED));
        if (hours.hours != null) meta.addView(U.iconText(c, R.drawable.ic_clock, 12, "Mở cửa " + hours.hours, 12, U.MUTED, U.NORMAL));
        meta.addView(U.iconText(c, R.drawable.ic_bike, 12, "Giao " + store.delivery_time, 12, U.MUTED, U.NORMAL));
        meta.addView(U.iconText(c, R.drawable.ic_pin, 12, "Cách bạn " + Cards.distanceLabel(store.lat, store.lng, store.distance_km), 12, U.MUTED, U.NORMAL));
        U.add(info, meta, 12);
        if (store.address != null && !store.address.isEmpty()) U.add(info, U.text(c, store.address, 12, U.SUBTLE), 8);
        U.addFlex(top, info, 16);
        card.addView(top);
        if (!hours.open) U.add(card, Cards.closedNotice(c, hours), 16);
        if (store.lat != null && store.lng != null) {
            LinearLayout toggle = U.row(c);
            U.pad(toggle, 16, 12);
            U.pressable(toggle, U.SOFT, 12);
            TextView l = U.iconText(c, R.drawable.ic_pin, 16, "Xem vị trí nhà hàng trên bản đồ", 14, U.INK, U.BOLD);
            l.getCompoundDrawablesRelative()[0].setTint(U.ORANGE);
            U.addFlex(toggle, l, 0);
            toggle.addView(U.text(c, showMap ? "Thu gọn" : "Mở", 14, U.ORANGE, U.BOLD));
            toggle.setOnClickListener(v -> { showMap = !showMap; render(); });
            U.add(card, toggle, 16);
            if (showMap) {
                FoodMap map = new FoodMap(c, 240);
                U.rounded(map, 12);
                List<FoodMap.MapMarker> markers = new ArrayList<>();
                markers.add(new FoodMap.MapMarker("store", "restaurant", store.lat, store.lng, store.name));
                Geo.Place p = LocationState.get().place;
                if (p != null) markers.add(new FoodMap.MapMarker("me", "home", p.lat, p.lng, "Bạn"));
                map.render(markers, null, null, null);
                U.add(card, map, 12, U.MATCH, U.dp(c, 240));
            }
        }
    }

    /** flex-wrap row: wraps its children onto new lines when they don't fit. */
    static LinearLayout flow(Context c) { return new com.foodgo.nativeapp.ui.FlowRow(c, 8, 4); }
}
