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
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.ui.U2;

/** <FoodCard food reason />: photo, heart, name, restaurant, rating, price and "+". */
public class FoodCardView extends LinearLayout implements AppState.Listener {
    private final Food food;
    private final ImageView heart;

    public FoodCardView(Context c, Food food, String reason) {
        super(c);
        this.food = food;
        setOrientation(VERTICAL);
        U.card(this, 16);
        U.rounded(this, 16);

        FrameLayout top = new FrameLayout(c);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackgroundColor(U.SOFT);
        U.load(img, food.image);
        img.setContentDescription("Xem chi tiết " + food.name);
        U.click(img, this::openDetail);
        top.addView(img, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        if (!food.is_available) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
            p.setMargins(U.dp(c, 12), U.dp(c, 12), 0, 0);
            top.addView(U.pill(c, "Tạm hết", 0xCC241C19, U.WHITE), p);
        }
        heart = new ImageView(c);
        U.pad(heart, 8);
        U.bg(heart, 0xE6FFFFFF, 999);
        heart.setElevation(U.dp(c, 1));
        U.click(heart, () -> AppState.get().toggleFavorite(food.id));
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(U.dp(c, 36), U.dp(c, 36), Gravity.TOP | Gravity.END);
        hp.setMargins(0, U.dp(c, 12), U.dp(c, 12), 0);
        top.addView(heart, hp);
        addView(top, new LayoutParams(U.MATCH, U.dp(c, 192)));

        LinearLayout body = U.col(c);
        U.pad(body, 16);
        if (reason != null && !reason.isEmpty()) {
            TextView r = U.ellipsize(U.iconText(c, R.drawable.ic_sparkles, 12, reason, 12, U.ORANGE, U.SEMI), 1);
            body.addView(r);
            ((LayoutParams) r.getLayoutParams()).bottomMargin = U.dp(c, 4);
        }
        TextView name = U.ellipsize(U.text(c, food.name, 16, U.INK, U.BOLD), 1);
        U.click(name, this::openDetail);
        body.addView(name);
        if (food.restaurants != null) {
            TextView rest = U.ellipsize(U.iconText(c, R.drawable.ic_store, 12, food.restaurants.name, 12, U.MUTED, U.SEMI), 1);
            U.click(rest, () -> App.navigate("/restaurant/" + food.restaurants.id));
            U.add(body, rest, 2, U.WRAP, U.WRAP);
        }
        U.add(body, Cards.ratingBadge(c, food.rating, food.review_count, 12, this::openDetail), 4, U.WRAP, U.WRAP);
        if (food.description != null && !food.description.isEmpty()) {
            U.add(body, U.ellipsize(U.text(c, food.description, 12, U.SUBTLE), 2), 4);
        }
        LinearLayout bottom = U.row(c);
        U.addFlex(bottom, Cards.price(c, food.price, food.old_price, 18, 12), 0);
        ImageView add = U.icon(c, R.drawable.ic_add, 24, U.ORANGE);
        U.pad(add, 8);
        U.pressable(add, U.PEACH, 12);
        add.setContentDescription("Thêm " + food.name + " vào giỏ");
        if (!food.is_available) add.setAlpha(0.4f);
        else add.setOnClickListener(v -> AppState.get().addToCart(food));
        bottom.addView(add, new LayoutParams(U.dp(c, 40), U.dp(c, 40)));
        U.add(body, bottom, 16);
        addView(body);
        setLayoutParams(new LayoutParams(U.MATCH, U.WRAP));

        addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { AppState.get().addListener(FoodCardView.this); onAppState(); }
            @Override public void onViewDetachedFromWindow(View v) { AppState.get().removeListener(FoodCardView.this); }
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
