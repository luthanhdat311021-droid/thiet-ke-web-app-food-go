package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.model.Restaurant;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.util.Fmt;
import com.foodgo.nativeapp.util.Geo;
import com.foodgo.nativeapp.util.StoreHours;

/** components/cards.tsx (+ RatingBadge / Stars from reviews.tsx) */
public final class Cards {
    private Cards() {}

    /** <SectionTitle title href action /> */
    public static View sectionTitle(Context c, String title, String href, String action) {
        LinearLayout r = U.row(c);
        TextView t = U.text(c, title, 22, U.INK, U.XBOLD);
        U.addFlex(r, t, 0);
        if (href != null) {
            TextView a = U.text(c, (action != null ? action : "Xem tất cả") + " ", 14, U.ORANGE, U.BOLD);
            android.graphics.drawable.Drawable d = U.drawable(c, R.drawable.ic_arrow_forward, U.ORANGE);
            d.setBounds(0, 0, U.dp(c, 16), U.dp(c, 16));
            a.setCompoundDrawablesRelative(null, null, d, null);
            a.setCompoundDrawablePadding(U.dp(c, 4));
            a.setGravity(Gravity.CENTER_VERTICAL);
            U.pad(a, 0, 12, 0, 12);
            U.click(a, () -> App.navigate(href));
            r.addView(a);
        }
        return r;
    }

    /** <Panel title action>children</Panel>: returns [section, body]. */
    public static LinearLayout[] panel(Context c, String title, View action) {
        LinearLayout section = U.card(c);
        LinearLayout head = U.row(c);
        U.addFlex(head, U.text(c, title, 18, U.INK, U.XBOLD), 0);
        if (action != null) head.addView(action);
        section.addView(head);
        LinearLayout body = U.col(c);
        U.add(section, body, 20);
        return new LinearLayout[]{section, body};
    }

    /** "★ 4.8 (12)" or "Chưa có đánh giá" */
    public static TextView ratingBadge(Context c, double rating, int count, float sp, Runnable onClick) {
        TextView t = U.text(c, "", sp, U.INK, U.BOLD);
        if (count > 0) {
            SpannableStringBuilder b = new SpannableStringBuilder(Fmt.fixed1(rating));
            int s = b.length();
            b.append("(").append(String.valueOf(count)).append(")");
            b.setSpan(new ForegroundColorSpan(U.SUBTLE), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(b);
            android.graphics.drawable.Drawable d = U.drawable(c, R.drawable.ic_star, U.STAR);
            d.setBounds(0, 0, U.dp(c, 12), U.dp(c, 12));
            t.setCompoundDrawablesRelative(d, null, null, null);
            t.setCompoundDrawablePadding(U.dp(c, 4));
        } else {
            t.setText("Chưa có đánh giá");
            t.setTextColor(U.SUBTLE);
            U.weight(t, U.NORMAL);
        }
        t.setGravity(Gravity.CENTER_VERTICAL);
        if (onClick != null) {
            U.pad(t, 4, 4);
            U.pressable(t, 0x00FFFFFF, 4);
            t.setOnClickListener(v -> onClick.run());
        }
        return t;
    }

    /** <Stars value onChange size /> */
    public static LinearLayout stars(Context c, int value, int sizeDp, java.util.function.IntConsumer onChange) {
        LinearLayout r = U.row(c);
        for (int n = 1; n <= 5; n++) {
            ImageView s = U.icon(c, R.drawable.ic_star, sizeDp, n <= value ? U.STAR : U.STAR_OFF);
            if (onChange != null) {
                int v = n;
                U.pad(s, 2);
                s.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, sizeDp + 4), U.dp(c, sizeDp + 4)));
                s.setContentDescription(n + " sao");
                U.click(s, () -> onChange.accept(v));
            }
            U.add(r, s, n == 1 ? 0 : (onChange != null ? 0 : 4), s.getLayoutParams().width, s.getLayoutParams().height);
        }
        return r;
    }

    public static final String[] RATING_LABELS = {"", "Rất tệ", "Chưa ngon", "Bình thường", "Ngon", "Tuyệt vời"};

    /** price + struck-through old price */
    public static TextView price(Context c, long price, Long oldPrice, float sp, float oldSp) {
        SpannableStringBuilder b = new SpannableStringBuilder(Fmt.money(price));
        b.setSpan(new ForegroundColorSpan(U.ORANGE), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (oldPrice != null && oldPrice > price) {
            int s = b.length();
            b.append("  ").append(Fmt.money(oldPrice));
            b.setSpan(new StrikethroughSpan(), s + 2, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(U.FAINT), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new android.text.style.AbsoluteSizeSpan((int) oldSp, true), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), s, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return U.text(c, b, sp, U.ORANGE, U.BOLD);
    }

    /** Square logo, or the restaurant's initial when it has none. */
    public static View restaurantLogo(Context c, String name, String logo, int sizeDp, float textSp) {
        FrameLayout f = new FrameLayout(c);
        int border = U.dp(c, 4);
        GradientDrawable bg = U.stroke(logo != null ? U.WHITE : U.ORANGE, U.dp(c, 16), U.WHITE, border);
        f.setBackground(bg);
        f.setElevation(U.dp(c, 4));
        if (logo != null) {
            ImageView i = new ImageView(c);
            i.setScaleType(ImageView.ScaleType.CENTER_CROP);
            U.rounded(i, 12);
            U.load(i, logo);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(U.MATCH, U.MATCH);
            p.setMargins(border, border, border, border);
            f.addView(i, p);
        } else {
            String n = name != null ? name.trim() : "";
            TextView t = U.text(c, n.isEmpty() ? "" : n.substring(0, 1).toUpperCase(), textSp, U.WHITE, U.XBOLD);
            f.addView(t, new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.CENTER));
        }
        f.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, sizeDp), U.dp(c, sizeDp)));
        return f;
    }

    /** Real distance from the customer's detected location, falling back to the stored estimate. */
    public static String distanceLabel(Double lat, Double lng, double distanceKm) {
        Geo.Place p = LocationState.get().place;
        return p != null && lat != null && lng != null ? Geo.formatKm(Geo.distanceKm(p.lat, p.lng, lat, lng)) : Fmt.js(distanceKm) + " km";
    }

    public static double distanceOrStored(Restaurant r) {
        Geo.Place p = LocationState.get().place;
        return p != null && r.lat != null && r.lng != null ? Geo.distanceKm(p.lat, p.lng, r.lat, r.lng) : r.distance_km;
    }

    /** One restaurant in a list: photo, open/closed, rating, distance. */
    public static View restaurantCard(Context c, Restaurant r) {
        StoreHours hours = StoreHours.of(r);
        LinearLayout card = U.col(c);
        U.card(card, 16);
        U.rounded(card, 16);
        card.setClipChildren(false);

        FrameLayout top = new FrameLayout(c);
        top.setBackgroundColor(U.SOFT);
        ImageView img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        U.load(img, r.image);
        if (!hours.open) U.grayscale(img, 0.6f);
        top.addView(img, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        String status = hours.open ? "Đang mở cửa" : hours.paused ? "Tạm đóng cửa" : hours.reopens != null ? "Mở lúc " + hours.reopens : "Đã đóng cửa";
        TextView pill = hours.open ? U.pill(c, status, 0xF2FFFFFF, U.GREEN_DARK) : U.pill(c, status, 0xCC241C19, U.WHITE);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.START);
        pp.setMargins(U.dp(c, 12), U.dp(c, 12), 0, 0);
        top.addView(pill, pp);
        if (r.tag != null && !r.tag.isEmpty()) {
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.TOP | Gravity.END);
            tp.setMargins(0, U.dp(c, 12), U.dp(c, 12), 0);
            top.addView(U.pill(c, r.tag, U.AMBER_BG, U.AMBER), tp);
        }
        card.addView(top, new LinearLayout.LayoutParams(U.MATCH, U.dp(c, 144)));

        LinearLayout body = U.row(c);
        body.setGravity(Gravity.TOP);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        U.pad(body, 16);
        View logo = restaurantLogo(c, r.name, r.logo, 56, 20);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(U.dp(c, 56), U.dp(c, 56));
        lp.topMargin = -U.dp(c, 40);
        body.addView(logo, lp);
        LinearLayout info = U.col(c);
        info.addView(U.ellipsize(U.text(c, r.name, 16, U.INK, U.BOLD), 1));
        if (r.cuisine != null && !r.cuisine.isEmpty()) info.addView(U.ellipsize(U.text(c, r.cuisine, 12, U.MUTED), 1));
        LinearLayout meta = new FlowRow(c, 12, 4);
        if (r.review_count > 0) {
            meta.addView(U.iconText(c, R.drawable.ic_star, 12, Fmt.fixed1(r.rating) + " (" + Fmt.count(r.review_count) + ")", 12, U.MUTED, U.NORMAL));
            ((TextView) meta.getChildAt(0)).getCompoundDrawablesRelative()[0].setTint(U.STAR);
        } else meta.addView(U.text(c, "Chưa có đánh giá", 12, U.SUBTLE));
        meta.addView(U.iconText(c, R.drawable.ic_bike, 12, r.delivery_time, 12, U.MUTED, U.NORMAL));
        meta.addView(U.iconText(c, R.drawable.ic_pin, 12, distanceLabel(r.lat, r.lng, r.distance_km), 12, U.MUTED, U.NORMAL));
        U.add(info, meta, 8);
        U.addFlex(body, info, 12);
        card.addView(body);
        U.click(card, () -> App.navigate("/restaurant/" + r.id));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(U.MATCH, U.WRAP);
        card.setLayoutParams(cp);
        return card;
    }

    /** <ClosedNotice hours /> */
    public static View closedNotice(Context c, StoreHours hours) {
        LinearLayout r = U.row(c);
        r.setGravity(Gravity.TOP);
        U.pad(r, 16, 12);
        U.bg(r, U.AMBER_BG, 12);
        ImageView i = U.icon(c, R.drawable.ic_moon, 16, U.AMBER_DARK);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(U.dp(c, 16), U.dp(c, 16));
        ip.topMargin = U.dp(c, 2);
        r.addView(i, ip);
        SpannableStringBuilder b = new SpannableStringBuilder(hours.paused ? "Nhà hàng đang tạm đóng cửa." : "Nhà hàng đã đóng cửa.");
        b.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append(" ").append(hours.reopens != null ? "Mở lại lúc " + hours.reopens + ". Bạn vẫn có thể xem thực đơn và chọn món trước." : "Vui lòng quay lại sau nhé.");
        U.addFlex(r, U.text(c, b, 14, U.AMBER_DARK), 12);
        return r;
    }

    /** Thumbnail used in admin tables: size-12 rounded-lg */
    public static View thumb(Context c, String src) {
        if (src != null && !src.isEmpty()) return U.img(c, src, 48, 48, 8);
        View v = new View(c);
        U.bg(v, U.SOFT, 8);
        v.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, 48), U.dp(c, 48)));
        return v;
    }

    /** yesNo(v, yes, no) pill */
    public static TextView yesNo(Context c, boolean v, String yes, String no) {
        TextView t = U.text(c, v ? yes : no, 12, v ? U.GREEN : U.SUBTLE, U.BOLD);
        U.pad(t, 8, 4);
        U.bg(t, v ? U.GREEN_BG : U.GREY_BG, 999);
        return t;
    }

    /** MoMo text badge */
    public static TextView momoIcon(Context c, int wDp, int hDp, float sp) {
        TextView t = U.text(c, "MoMo", sp, U.WHITE, U.XBOLD);
        t.setGravity(Gravity.CENTER);
        U.bg(t, U.MOMO, 6);
        t.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, wDp), U.dp(c, hDp)));
        return t;
    }

    /** CopyRow: label · value · copy button (shows a check for 1.5 s) */
    public static View copyRow(Context c, String label, String value, String display) {
        LinearLayout r = U.row(c);
        U.pad(r, 16, 8);
        U.bg(r, U.SOFT, 12);
        U.addFlex(r, U.text(c, label, 14, U.MUTED), 0);
        r.addView(U.text(c, display != null ? display : value, 14, U.INK, U.BOLD));
        ImageView btn = U.icon(c, R.drawable.ic_copy, 16, U.ORANGE);
        U.pad(btn, 8);
        btn.setLayoutParams(new LinearLayout.LayoutParams(U.dp(c, 32), U.dp(c, 32)));
        btn.setContentDescription("Sao chép " + label);
        U.click(btn, () -> {
            U2.copy(c, value);
            btn.setImageDrawable(U.drawable(c, R.drawable.ic_check, U.ORANGE));
            btn.postDelayed(() -> btn.setImageDrawable(U.drawable(c, R.drawable.ic_copy, U.ORANGE)), 1500);
        });
        U.add(r, btn, 8, U.dp(c, 32), U.dp(c, 32));
        return r;
    }
}
