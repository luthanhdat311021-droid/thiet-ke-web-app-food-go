package com.foodgo.nativeapp.screens;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.model.CartItem;
import com.foodgo.nativeapp.model.Food;
import com.foodgo.nativeapp.model.Recommendation;
import com.foodgo.nativeapp.model.Review;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.Recommend;
import com.foodgo.nativeapp.ui.CappedColumn;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.Overlay;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Fmt;

import java.util.List;

/** Dish detail sheet (reviews.tsx FoodDetailSheet): photo, info, add to cart, similar dishes and reviews. */
public class FoodDetailSheet implements AppState.Listener {
    private final Activity a;
    private Food food;
    private final ScrollView scroller;
    private final LinearLayout content;
    private final FrameLayout footer;
    private ImageView heart;
    private Overlay overlay;
    private int generation;

    public static void open(Activity a, Food food) {
        if (a == null) return;
        new FoodDetailSheet(a, food).show();
    }

    private FoodDetailSheet(Activity a, Food food) {
        this.a = a;
        this.food = food;
        scroller = new ScrollView(a);
        content = U.col(a);
        scroller.addView(content);
        footer = new FrameLayout(a);
        footer.setBackgroundColor(U.WHITE);
    }

    private void show() {
        LinearLayout foot = U.col(a);
        foot.addView(U.divider(a, U.LINE));
        foot.addView(footer);
        CappedColumn column = new CappedColumn(a, scroller, foot, 0.9f);
        FrameLayout panel = new FrameLayout(a);
        panel.setBackground(topRounded());
        U.rounded(panel, 0);
        panel.setClipToOutline(false);
        panel.addView(column, new FrameLayout.LayoutParams(U.MATCH, U.WRAP));
        ImageView close = U.icon(a, R.drawable.ic_close, 24, U.INK);
        U.pad(close, 8);
        U.bg(close, 0xE6FFFFFF, 999);
        close.setElevation(U.dp(a, 2));
        close.setContentDescription("Đóng");
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(U.dp(a, 40), U.dp(a, 40), Gravity.TOP | Gravity.END);
        cp.setMargins(0, U.dp(a, 16), U.dp(a, 16), 0);
        panel.addView(close, cp);
        overlay = new Overlay(a, panel, Overlay.BOTTOM);
        U.click(close, overlay::dismiss);
        AppState.get().addListener(this);
        overlay.onDismiss(() -> AppState.get().removeListener(this));
        render();
        overlay.show();
    }

    private android.graphics.drawable.GradientDrawable topRounded() {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(U.WHITE);
        float r = U.dp(a, 24);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    /** a "Có thể bạn cũng thích" dish opens in place */
    private void openFood(Food f) {
        food = f;
        render();
        scroller.scrollTo(0, 0);
    }

    private void render() {
        int gen = ++generation;
        Recommend.track("view", food.id, AppState.get().signedIn());
        content.removeAllViews();
        if (food.image != null) {
            ImageView img = U.img(a, food.image, U.MATCH, 240, 0);
            if (!food.is_available) U.grayscale(img, 1f);
            U.rounded(img, 0);
            content.addView(img);
        } else content.addView(U.space(a, 64));

        LinearLayout body = U.col(a);
        U.pad(body, 20);
        LinearLayout head = U.row(a);
        head.setGravity(Gravity.TOP);
        U.addFlex(head, U.text(a, food.name, 24, U.INK, U.XBOLD), 0);
        heart = U.icon(a, R.drawable.ic_heart_outline, 24, U.ORANGE);
        U.pad(heart, 4);
        heart.setLayoutParams(new LinearLayout.LayoutParams(U.dp(a, 32), U.dp(a, 32)));
        U.click(heart, () -> AppState.get().toggleFavorite(food.id));
        U.add(head, heart, 12, U.dp(a, 32), U.dp(a, 32));
        body.addView(head);
        if (food.restaurants != null) {
            TextView r = U.iconText(a, R.drawable.ic_store, 16, food.restaurants.name, 14, U.MUTED, U.SEMI);
            long rid = food.restaurants.id;
            U.click(r, () -> { overlay.dismiss(); App.navigate("/restaurant/" + rid); });
            U.add(body, r, 4, U.WRAP, U.WRAP);
        }
        LinearLayout meta = U.row(a);
        meta.addView(Cards.ratingBadge(a, food.rating, food.review_count, 14, null));
        if (food.sold_count > 0) U.add(meta, U.text(a, "Đã bán " + food.sold_count, 12, U.SUBTLE), 12, U.WRAP, U.WRAP);
        if (!food.is_available) {
            TextView t = U.text(a, "Tạm hết", 12, U.MUTED, U.BOLD);
            U.pad(t, 8, 2);
            U.bg(t, U.GREY_BG, 999);
            U.add(meta, t, 12, U.WRAP, U.WRAP);
        }
        U.add(body, meta, 8);
        if (food.description != null && !food.description.isEmpty()) U.add(body, U.text(a, food.description, 14, U.BODY), 12);
        U.add(body, Cards.price(a, food.price, food.old_price, 24, 14), 16);

        LinearLayout similarBox = U.col(a);
        body.addView(similarBox);
        LinearLayout reviewsBox = U.col(a);
        body.addView(reviewsBox);
        content.addView(body);

        Recommend.similar(food.id, 8, (list, err) -> {
            if (gen != generation || list == null || list.isEmpty()) return;
            similarBox.addView(sectionHeader(true, "Có thể bạn cũng thích"));
            LinearLayout row = U.row(a);
            row.setGravity(Gravity.TOP);
            for (Recommendation rec : list) {
                Food f = rec.food;
                LinearLayout item = U.col(a);
                item.addView(U.img(a, f.image, 128, 128, 12));
                item.getChildAt(0).setBackground(U.round(U.SOFT, U.dp(a, 12)));
                U.add(item, U.ellipsize(U.text(a, f.name, 14, U.INK, U.BOLD), 1), 6, U.dp(a, 128), U.WRAP);
                item.addView(U.ellipsize(U.text(a, f.restaurants != null ? f.restaurants.name : "", 12, U.SUBTLE), 1), new LinearLayout.LayoutParams(U.dp(a, 128), U.WRAP));
                item.addView(U.text(a, Fmt.money(f.price), 14, U.ORANGE, U.BOLD));
                U.click(item, () -> openFood(f));
                U.add(row, item, row.getChildCount() == 0 ? 0 : 12, U.dp(a, 128), U.WRAP);
            }
            U.add(similarBox, U.hscroll(a, row), 12);
        });

        reviewsBox.addView(sectionHeader(false, "Đánh giá từ khách hàng"));
        View loading = U.progress(a, 20, U.ORANGE);
        LinearLayout loadingRow = U.row(a);
        loadingRow.setGravity(Gravity.CENTER);
        U.pad(loadingRow, 0, 32, 0, 32);
        loadingRow.addView(loading);
        U.add(reviewsBox, loadingRow, 16);
        Db.from("fg_reviews").select("*").eq("food_id", food.id).order("created_at", false).limit(50).list(Review.class, (list, err) -> {
            if (gen != generation) return;
            reviewsBox.removeView(loadingRow);
            if (list.isEmpty()) {
                LinearLayout e = U.col(a);
                e.setGravity(Gravity.CENTER_HORIZONTAL);
                U.pad(e, 0, 24, 0, 24);
                e.addView(U.icon(a, R.drawable.ic_chat, 32, U.PEACH_ICON));
                TextView t = U.text(a, "Món này chưa có đánh giá. Đặt món và là người đầu tiên đánh giá nhé!", 14, U.SUBTLE);
                t.setGravity(Gravity.CENTER);
                U.add(e, t, 8);
                U.add(reviewsBox, e, 16);
                return;
            }
            LinearLayout listBox = U.col(a);
            for (int i = 0; i < list.size(); i++) {
                Review r = list.get(i);
                LinearLayout item = U.col(a);
                LinearLayout top = U.row(a);
                U.addFlex(top, U.text(a, r.reviewer_name, 14, U.INK, U.BOLD), 0);
                top.addView(U.text(a, Fmt.timeAgo(r.updated_at), 12, U.SUBTLE));
                item.addView(top);
                LinearLayout stars = U.row(a);
                stars.addView(Cards.stars(a, r.rating, 14, null));
                U.add(stars, U.text(a, Cards.RATING_LABELS[Math.max(0, Math.min(5, r.rating))], 12, U.MUTED), 8, U.WRAP, U.WRAP);
                U.add(item, stars, 4);
                if (r.comment != null && !r.comment.isEmpty()) U.add(item, U.text(a, r.comment, 14, U.BODY), 8);
                U.pad(item, 0, 0, 0, 16);
                U.add(listBox, item, i == 0 ? 0 : 16);
                if (i < list.size() - 1) listBox.addView(U.divider(a, U.SOFT));
            }
            U.add(reviewsBox, listBox, 16);
        });
        onAppState();
    }

    private View sectionHeader(boolean sparkle, String title) {
        LinearLayout box = U.col(a);
        U.add(box, U.divider(a, U.LINE), 24);
        TextView t = sparkle ? U.iconText(a, R.drawable.ic_sparkles, 16, title, 16, U.INK, U.XBOLD) : U.text(a, title, 16, U.INK, U.XBOLD);
        if (sparkle) t.getCompoundDrawablesRelative()[0].setTint(U.ORANGE);
        U.add(box, t, 20);
        return box;
    }

    @Override
    public void onAppState() {
        AppState s = AppState.get();
        boolean liked = s.favoriteIds.contains(food.id);
        if (heart != null) {
            heart.setImageDrawable(U.drawable(a, liked ? R.drawable.ic_heart : R.drawable.ic_heart_outline, U.ORANGE));
            heart.setContentDescription(liked ? "Bỏ yêu thích" : "Yêu thích");
        }
        footer.removeAllViews();
        LinearLayout f = U.col(a);
        U.pad(f, 16);
        CartItem inCart = s.inCart(food.id);
        if (!food.is_available) {
            TextView t = U.text(a, "Món tạm hết", 14, U.SUBTLE, U.BOLD);
            t.setGravity(Gravity.CENTER);
            U.bg(t, U.GREY_BG, 12);
            f.addView(t, new LinearLayout.LayoutParams(U.MATCH, U.dp(a, 48)));
        } else if (inCart != null) {
            LinearLayout r = U.row(a);
            View minus = qtyButton(R.drawable.ic_remove, U.SOFT, U.INK, () -> s.setQty(food.id, inCart.qty - 1));
            minus.setContentDescription("Giảm");
            r.addView(minus, new LinearLayout.LayoutParams(U.dp(a, 44), U.dp(a, 44)));
            TextView q = U.text(a, String.valueOf(inCart.qty), 16, U.INK, U.BOLD);
            q.setGravity(Gravity.CENTER);
            U.add(r, q, 12, U.dp(a, 24), U.WRAP);
            View plus = qtyButton(R.drawable.ic_add, U.ORANGE, U.WHITE, () -> s.setQty(food.id, inCart.qty + 1));
            plus.setContentDescription("Tăng");
            U.add(r, plus, 12, U.dp(a, 44), U.dp(a, 44));
            U.addFlex(r, new View(a), 0);
            r.addView(U.text(a, Fmt.money(food.price * inCart.qty), 16, U.ORANGE, U.BOLD));
            f.addView(r);
        } else {
            PButton add = new PButton(a, "Thêm vào giỏ • " + Fmt.money(food.price), PButton.PRIMARY, R.drawable.ic_add);
            add.onClick(() -> s.addToCart(food));
            f.addView(add, new LinearLayout.LayoutParams(U.MATCH, U.dp(a, 48)));
        }
        footer.addView(f);
    }

    private View qtyButton(int icon, int bg, int fg, Runnable r) {
        FrameLayout b = new FrameLayout(a);
        U.pressable(b, bg, 12);
        b.addView(U.icon(a, icon, 16, fg), new FrameLayout.LayoutParams(U.dp(a, 16), U.dp(a, 16), Gravity.CENTER));
        b.setOnClickListener(v -> r.run());
        return b;
    }

    @SuppressWarnings("unused")
    private static void unused(List<?> l) {}
}
