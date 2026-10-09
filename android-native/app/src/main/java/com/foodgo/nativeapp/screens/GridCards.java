package com.foodgo.nativeapp.screens;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.StoreHours;

/** Compact cards for the home page's grid view (2 per row). */
public final class GridCards {
    private GridCards() {}

    /** An ImageView that is always as tall as it is wide. */
    static class SquareImage extends ImageView {
        SquareImage(Context c) { super(c); setScaleType(ScaleType.CENTER_CROP); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, w); }
    }

    /** Food tile: square photo with heart, name, restaurant, rating, price and "+". */
    public static class FoodTile extends LinearLayout implements AppState.Listener {
        private final Food food;
        private final ImageView heart;

        public FoodTile(Context c, Food food, String reason) {
            super(c);
            this.food = food;
            setOrientation(VERTICAL);
            U.card(this, 16);
            U.rounded(this, 16);

            FrameLayout top = new FrameLayout(c);
            SquareImage img = new SquareImage(c);
            img.setBackgroundColor(U.SOFT);
            U.load(img, food.image);
            img.setContentDescription("Xem chi tiết " + food.name);
            U.click(img, this::openDetail);
            top.addView(img, new FrameLayout.LayoutParams(U.MATCH, U.WRAP));
            if (!food.is_available) {
                FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
                p.setMargins(U.dp(c, 8), U.dp(c, 8), 0, 0);
                TextView out = U.pill(c, "Tạm hết", 0xCC241C19, U.WHITE);
                out.setTextSize(11);
                top.addView(out, p);
            }
            heart = new ImageView(c);
            U.pad(heart, 7);
            U.bg(heart, 0xE6FFFFFF, 999);
            heart.setElevation(U.dp(c, 1));
            U.click(heart, () -> AppState.get().toggleFavorite(food.id));
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32), Gravity.TOP | Gravity.END);
            hp.setMargins(0, U.dp(c, 8), U.dp(c, 8), 0);
            top.addView(heart, hp);
            addView(top, new LayoutParams(U.MATCH, U.WRAP));

            LinearLayout body = U.col(c);
            U.pad(body, 12);
            if (reason != null && !reason.isEmpty()) {
                TextView r = U.ellipsize(U.iconText(c, R.drawable.ic_sparkles, 11, reason, 11, U.ORANGE, U.SEMI), 1);
                body.addView(r);
            }
            TextView name = U.ellipsize(U.text(c, food.name, 14, U.INK, U.BOLD), 2);
            name.setMinLines(2);
            U.click(name, this::openDetail);
            U.add(body, name, reason != null && !reason.isEmpty() ? 2 : 0);
            if (food.restaurants != null) {
                TextView rest = U.ellipsize(U.text(c, food.restaurants.name, 11, U.MUTED, U.SEMI), 1);
                U.click(rest, () -> App.navigate("/restaurant/" + food.restaurants.id));
                U.add(body, rest, 2);
            }
            U.add(body, Cards.ratingBadge(c, food.rating, food.review_count, 11, null), 4, U.WRAP, U.WRAP);
            LinearLayout bottom = U.row(c);
            LinearLayout prices = U.col(c);
            prices.addView(U.text(c, Fmt.money(food.price), 15, U.ORANGE, U.BOLD));
            if (food.old_price != null && food.old_price > food.price) {
                TextView old = U.text(c, Fmt.money(food.old_price), 11, U.FAINT);
                old.setPaintFlags(old.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                prices.addView(old);
            }
            U.addFlex(bottom, prices, 0);
            ImageView add = U.icon(c, R.drawable.ic_add, 20, U.ORANGE);
            U.pad(add, 7);
            U.pressable(add, U.PEACH, 10);
            add.setContentDescription("Thêm " + food.name + " vào giỏ");
            if (!food.is_available) add.setAlpha(0.4f);
            else add.setOnClickListener(v -> AppState.get().addToCart(food));
            bottom.addView(add, new LayoutParams(U.dp(c, 34), U.dp(c, 34)));
            U.add(body, bottom, 8);
            addView(body);

            addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { AppState.get().addListener(FoodTile.this); onAppState(); }
                @Override public void onViewDetachedFromWindow(View v) { AppState.get().removeListener(FoodTile.this); }
            });
            onAppState();
        }

        private void openDetail() { FoodDetailSheet.open(U2.activity(getContext()), food); }

        @Override
        public void onAppState() {
            boolean liked = AppState.get().favoriteIds.contains(food.id);
            heart.setImageDrawable(U.drawable(getContext(), liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, U.ORANGE));
            heart.setContentDescription(liked ? "Bỏ yêu thích" : "Yêu thích");
        }
    }

    /** Restaurant tile: photo with open/closed badge, name, rating · distance. */
    public static View restaurantTile(Context c, Restaurant r) {
        StoreHours hours = StoreHours.of(r);
        LinearLayout card = U.col(c);
        U.card(card, 16);
        U.rounded(card, 16);
        FrameLayout top = new FrameLayout(c);
        top.setBackgroundColor(U.SOFT);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        U.load(img, r.image);
        if (!hours.open) U.grayscale(img, 0.6f);
        top.addView(img, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        String status = hours.open ? "Đang mở" : hours.paused ? "Tạm đóng" : hours.reopens != null ? "Mở lúc " + hours.reopens : "Đã đóng";
        TextView pill = hours.open ? U.pill(c, status, 0xF2FFFFFF, U.GREEN_DARK) : U.pill(c, status, 0xCC241C19, U.WHITE);
        pill.setTextSize(11);
        U.pad(pill, 8, 3);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
        pp.setMargins(U.dp(c, 8), U.dp(c, 8), 0, 0);
        top.addView(pill, pp);
        card.addView(top, new LinearLayout.LayoutParams(U.MATCH, U.dp(c, 110)));
        LinearLayout body = U.col(c);
        U.pad(body, 12);
        LinearLayout head = U.row(c);
        head.addView(Cards.restaurantLogo(c, r.name, r.logo, 28, 12));
        U.addFlex(head, U.ellipsize(U.text(c, r.name, 14, U.INK, U.BOLD), 2), 8);
        body.addView(head);
        if (r.cuisine != null && !r.cuisine.isEmpty()) U.add(body, U.ellipsize(U.text(c, r.cuisine, 11, U.MUTED), 1), 4);
        String rating = r.review_count > 0 ? "★ " + Fmt.fixed1(r.rating) : "Chưa có đánh giá";
        U.add(body, U.ellipsize(U.text(c, rating + " • " + Cards.distanceLabel(r.lat, r.lng, r.distance_km), 11, U.MUTED), 1), 4);
        card.addView(body);
        U.click(card, () -> App.navigate("/restaurant/" + r.id));
        return card;
    }
}
