package com.foodgo.nativeapp.screens;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.core.Db;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.model.Order;
import com.foodgo.nativeapp.model.OrderItem;
import com.foodgo.nativeapp.model.Review;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.Cards;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** OrderReviewPanel (reviews.tsx): on a delivered order, rate each dish (1–5 stars + optional comment); editable later. */
public class OrderReviewPanel extends LinearLayout {
    private Order order;
    private long loadedFor = -1;
    private final Map<Long, Review> mine = new HashMap<>();
    private final Map<Long, int[]> draftRating = new HashMap<>();
    private final Map<Long, String> draftComment = new HashMap<>();
    private Long editing;
    private Long busy;

    public OrderReviewPanel(Context c) {
        super(c);
        setOrientation(VERTICAL);
        U.card(this, 16);
        U.pad(this, 20);
    }

    public void update(Order o) {
        order = o;
        if (o.id != loadedFor) {
            loadedFor = o.id;
            Db.from("fg_reviews").select("*").eq("order_id", o.id).list(Review.class, (list, err) -> {
                mine.clear();
                for (Review r : list) mine.put(r.food_id, r);
                render();
            });
        }
        render();
    }

    private List<OrderItem> items() {
        // one row per dish (an order can list the same dish once)
        List<OrderItem> out = new ArrayList<>();
        for (OrderItem i : order.items()) if (i.food_id != null) out.add(i);
        return out;
    }

    private void render() {
        Context c = getContext();
        removeAllViews();
        List<OrderItem> items = items();
        if (!"delivered".equals(order.status) || items.isEmpty()) { setVisibility(GONE); return; }
        setVisibility(VISIBLE);
        int pending = 0;
        for (OrderItem i : items) if (!mine.containsKey(i.food_id)) pending++;
        LinearLayout head = U.row(c);
        U.addFlex(head, U.text(c, "Đánh giá món ăn", 18, U.INK, U.XBOLD), 0);
        if (pending > 0) head.addView(U.pill(c, pending + " món chờ đánh giá", U.PEACH, U.ORANGE));
        addView(head);
        for (OrderItem i : items) U.add(this, item(i), 16);
    }

    private LinearLayout item(OrderItem i) {
        Context c = getContext();
        long foodId = i.food_id;
        Review done = mine.get(foodId);
        boolean open = done == null || (editing != null && editing == foodId);
        LinearLayout box = U.col(c);
        U.pad(box, 16);
        U.border(box, U.WHITE, 12, U.LINE, 1);
        LinearLayout top = U.row(c);
        if (i.image != null) top.addView(U.img(c, i.image, 48, 48, 8));
        U.addFlex(top, U.ellipsize(U.text(c, i.name, 14, U.INK, U.BOLD), 1), i.image != null ? 12 : 0);
        if (done != null && !open) {
            TextView edit = U.text(c, "Sửa", 12, U.ORANGE, U.BOLD);
            U.pad(edit, 4, 4);
            U.click(edit, () -> {
                draftRating.put(foodId, new int[]{done.rating});
                draftComment.put(foodId, done.comment != null ? done.comment : "");
                editing = foodId;
                render();
            });
            top.addView(edit);
        }
        box.addView(top);
        if (done != null && !open) {
            LinearLayout r = U.row(c);
            r.addView(Cards.stars(c, done.rating, 16, null));
            U.add(r, U.text(c, Cards.RATING_LABELS[clamp(done.rating)], 12, U.MUTED), 8, U.WRAP, U.WRAP);
            U.add(box, r, 12);
            if (done.comment != null && !done.comment.isEmpty()) U.add(box, U.text(c, done.comment, 14, U.BODY), 4);
            return box;
        }
        int[] rating = draftRating.get(foodId);
        if (rating == null) { rating = new int[]{done != null ? done.rating : 0}; draftRating.put(foodId, rating); }
        if (!draftComment.containsKey(foodId)) draftComment.put(foodId, done != null && done.comment != null ? done.comment : "");
        int[] rr = rating;
        LinearLayout starsRow = U.row(c);
        TextView label = U.text(c, rr[0] > 0 ? Cards.RATING_LABELS[clamp(rr[0])] : "", 14, U.ORANGE, U.SEMI);
        PButton submit = new PButton(c, done != null ? "Lưu đánh giá" : "Gửi đánh giá", PButton.PRIMARY);
        Runnable[] redrawStars = new Runnable[1];
        redrawStars[0] = () -> {
            starsRow.removeAllViews();
            starsRow.addView(Cards.stars(c, rr[0], 28, v -> { rr[0] = v; redrawStars[0].run(); }));
            label.setText(rr[0] > 0 ? Cards.RATING_LABELS[clamp(rr[0])] : "");
            if (label.getParent() == null) U.add(starsRow, label, 12, U.WRAP, U.WRAP);
            else { ((android.view.ViewGroup) label.getParent()).removeView(label); U.add(starsRow, label, 12, U.WRAP, U.WRAP); }
            submit.setEnabled(rr[0] > 0);
        };
        redrawStars[0].run();
        U.add(box, starsRow, 12);
        EditText comment = U.maxLength(U.textarea(c, "Món ăn thế nào? (không bắt buộc)", 2), 500);
        comment.setTextSize(14);
        comment.setText(draftComment.get(foodId));
        comment.setContentDescription("Nhận xét về " + i.name);
        comment.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) {}
            @Override public void afterTextChanged(Editable s) { draftComment.put(foodId, s.toString()); }
        });
        U.add(box, comment, 12);
        LinearLayout btns = U.row(c);
        submit.label().setTextSize(14);
        submit.setBusy(busy != null && busy == foodId);
        submit.onClick(() -> submit(foodId));
        btns.addView(submit, new LayoutParams(U.WRAP, U.dp(c, 40)));
        if (done != null) {
            PButton cancel = new PButton(c, "Hủy", PButton.OUTLINE);
            cancel.label().setTextSize(14);
            cancel.onClick(() -> { editing = null; render(); });
            U.add(btns, cancel, 8, U.WRAP, U.dp(c, 40));
        }
        U.add(box, btns, 12);
        return box;
    }

    private static int clamp(int r) { return Math.max(0, Math.min(5, r)); }

    private void submit(long foodId) {
        int rating = draftRating.containsKey(foodId) ? draftRating.get(foodId)[0] : 0;
        if (rating == 0) { AppState.get().toastError("Vui lòng chọn số sao"); return; }
        busy = foodId;
        render();
        Db.rpc("fg_submit_review", Json.obj("p_order_id", order.id, "p_food_id", foodId, "p_rating", rating, "p_comment", draftComment.get(foodId)), (data, err) -> {
            busy = null;
            if (err != null) { render(); AppState.get().toastError(err); return; }
            Review r = Json.as(data, Review.class);
            if (r != null) mine.put(foodId, r);
            editing = null;
            render();
            AppState.get().toast("Cảm ơn bạn đã đánh giá!");
        });
    }

    @SuppressWarnings("unused")
    private static int unused() { return Gravity.NO_GRAVITY; }
}
