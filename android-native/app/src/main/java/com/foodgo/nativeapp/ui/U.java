package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.widget.CompoundButtonCompat;

import com.bumptech.glide.Glide;
import com.foodgo.nativeapp.R;

import java.util.List;

/** Small view-building toolkit: the Tailwind classes of the web app, expressed as Java helpers. */
public final class U {
    private U() {}

    // palette (same hex values as the web app)
    public static final int ORANGE = 0xFFFF5B35, ORANGE_DARK = 0xFFE94C29, ORANGE_DEEP = 0xFFC43D1D;
    public static final int INK = 0xFF241C19, MUTED = 0xFF746B67, SUBTLE = 0xFF9C918C, FAINT = 0xFFAAA09B, BODY = 0xFF4A403C;
    public static final int BG = 0xFFFFFAF7, LINE = 0xFFF1E7E2, BORDER = 0xFFEADED8, SOFT = 0xFFF8F3F0, SKELETON = 0xFFF4ECE8;
    public static final int PEACH = 0xFFFFF0EB, PEACH2 = 0xFFFFF5F1, PEACH3 = 0xFFFFE0D5, HERO = 0xFFFFF0E8;
    public static final int GREEN = 0xFF3EAA68, GREEN_DARK = 0xFF2F7D4F, GREEN_BG = 0xFFE4F8EB, GREEN_SOFT = 0xFF72A77F, GREEN_TINT = 0xFFF0FAF3;
    public static final int AMBER = 0xFFBD8300, AMBER_BG = 0xFFFFF7DF, AMBER_DARK = 0xFF8A6100;
    public static final int RED = 0xFFEF4444, RED_TEXT = 0xFFDC2626, RED_DARK = 0xFFB91C1C, RED_BG = 0xFFFEF2F2;
    public static final int WARN = 0xFFC2410C, GREY_BG = 0xFFF4F0EE, STAR = 0xFFFFB21C, STAR_OFF = 0xFFE4DAD5, DOT_OFF = 0xFFC9BDB7;
    public static final int MOMO = 0xFFA50064, MOMO_DARK = 0xFF8A0054, PEACH_ICON = 0xFFFFB9A5;
    public static final int WHITE = Color.WHITE;

    public static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    public static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    public static final int NORMAL = 0, SEMI = 1, BOLD = 2, XBOLD = 3;

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    // ------------------------------------------------------------------ text

    public static TextView text(Context c, CharSequence s, float sp, int color, int weight) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        weight(t, weight);
        t.setIncludeFontPadding(true);
        return t;
    }

    public static TextView text(Context c, CharSequence s, float sp, int color) { return text(c, s, sp, color, NORMAL); }

    public static TextView weight(TextView t, int weight) {
        switch (weight) {
            case SEMI: t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); break;
            case BOLD: t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); break;
            case XBOLD: t.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL)); break;
            default: t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        }
        return t;
    }

    public static TextView h1(Context c, String s) { return text(c, s, 28, INK, XBOLD); }

    public static TextView h2(Context c, String s) { return text(c, s, 22, INK, XBOLD); }

    public static TextView ellipsize(TextView t, int lines) {
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (lines == 1) t.setSingleLine(true);
        return t;
    }

    /** text-sm font-bold text-[#ff5b35] link */
    public static TextView link(Context c, String s, Runnable onClick) {
        TextView t = text(c, s, 14, ORANGE, BOLD);
        t.setPadding(0, dp(c, 8), 0, dp(c, 8));
        click(t, onClick);
        return t;
    }

    // ------------------------------------------------------------------ layout

    public static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    public static LinearLayout.LayoutParams lp(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    /** Adds `child` to a LinearLayout with a top margin (dp) — the `mt-*` of Tailwind. */
    public static <V extends View> V add(LinearLayout parent, V child, int topDp) {
        // keep a size the view was built with (images, fixed-height boxes)
        ViewGroup.LayoutParams old = child.getLayoutParams();
        if (old != null) return add(parent, child, topDp, old.width, old.height);
        return add(parent, child, topDp, MATCH, WRAP);
    }

    public static <V extends View> V add(LinearLayout parent, V child, int topDp, int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        if (parent.getOrientation() == LinearLayout.VERTICAL) p.topMargin = dp(parent.getContext(), topDp);
        else p.leftMargin = dp(parent.getContext(), topDp);
        parent.addView(child, p);
        return child;
    }

    /** Horizontal child taking the remaining width (flex-1 min-w-0). */
    public static <V extends View> V addFlex(LinearLayout parent, V child, int leftDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, WRAP, 1);
        p.leftMargin = dp(parent.getContext(), leftDp);
        parent.addView(child, p);
        return child;
    }

    public static <V extends View> V pad(V v, int all) { int p = dp(v.getContext(), all); v.setPadding(p, p, p, p); return v; }

    public static <V extends View> V pad(V v, int h, int vert) {
        int ph = dp(v.getContext(), h), pv = dp(v.getContext(), vert);
        v.setPadding(ph, pv, ph, pv);
        return v;
    }

    public static <V extends View> V pad(V v, int l, int t, int r, int b) {
        Context c = v.getContext();
        v.setPadding(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return v;
    }

    public static View space(Context c, int dpH) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, dpH)));
        return v;
    }

    public static View divider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, Math.max(1, dp(c, 1))));
        return v;
    }

    /** grid-cols-N with a gap: rows of equal-width cells. */
    public static LinearLayout grid(Context c, int cols, List<? extends View> cells, int gapDp) {
        LinearLayout g = col(c);
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            if (i % cols == 0) {
                row = new LinearLayout(c);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(MATCH, WRAP);
                if (i > 0) rp.topMargin = dp(c, gapDp);
                g.addView(row, rp);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, MATCH, 1);
            if (i % cols > 0) p.leftMargin = dp(c, gapDp);
            row.addView(cells.get(i), p);
        }
        if (row != null) for (int i = cells.size() % cols; i > 0 && i < cols; i++) row.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        return g;
    }

    public static HorizontalScrollView hscroll(Context c, LinearLayout inner) {
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setOverScrollMode(View.OVER_SCROLL_NEVER);
        h.addView(inner, new FrameLayout.LayoutParams(WRAP, WRAP));
        return h;
    }

    // ------------------------------------------------------------------ backgrounds

    public static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable stroke(int color, float radiusPx, int strokeColor, int strokePx) {
        GradientDrawable d = round(color, radiusPx);
        d.setStroke(strokePx, strokeColor);
        return d;
    }

    public static <V extends View> V bg(V v, int color, float radiusDp) {
        v.setBackground(round(color, dp(v.getContext(), radiusDp)));
        return v;
    }

    public static <V extends View> V border(V v, int color, float radiusDp, int strokeColor, float strokeDp) {
        v.setBackground(stroke(color, dp(v.getContext(), radiusDp), strokeColor, Math.max(1, dp(v.getContext(), strokeDp))));
        return v;
    }

    /** Background with a touch ripple, for clickable rounded boxes. */
    public static <V extends View> V pressable(V v, int color, float radiusDp) {
        float r = dp(v.getContext(), radiusDp);
        GradientDrawable content = round(color, r);
        GradientDrawable mask = round(Color.BLACK, r);
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FF5B35), content, mask));
        return v;
    }

    public static <V extends View> V pressable(V v, GradientDrawable content) {
        GradientDrawable mask = round(Color.BLACK, 0);
        float[] radii = content.getCornerRadii();
        if (radii != null) mask.setCornerRadii(radii);
        else try { mask.setCornerRadius(content.getCornerRadius()); } catch (Exception ignored) {}
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22FF5B35), content, mask));
        return v;
    }

    /** shadow-sm white card */
    public static <V extends View> V card(V v, float radiusDp) {
        bg(v, WHITE, radiusDp);
        v.setElevation(dp(v.getContext(), 1));
        return v;
    }

    public static LinearLayout card(Context c) { return card(pad(col(c), 20), 16); }

    public static void click(View v, Runnable r) {
        v.setClickable(true);
        v.setOnClickListener(x -> r.run());
        if (v.getBackground() == null) {
            TypedValue tv = new TypedValue();
            v.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
            v.setForeground(ContextCompat.getDrawable(v.getContext(), tv.resourceId));
        }
    }

    // ------------------------------------------------------------------ icons & images

    public static Drawable drawable(Context c, int res, int color) {
        Drawable d = ContextCompat.getDrawable(c, res).mutate();
        d.setTint(color);
        return d;
    }

    public static ImageView icon(Context c, int res, int sizeDp, int color) {
        ImageView i = new ImageView(c);
        i.setImageDrawable(drawable(c, res, color));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    /** TextView with a leading icon (lucide icon + label). */
    public static TextView iconText(Context c, int res, int iconDp, CharSequence s, float sp, int color, int weight) {
        TextView t = text(c, s, sp, color, weight);
        Drawable d = drawable(c, res, color);
        int px = dp(c, iconDp);
        d.setBounds(0, 0, px, px);
        t.setCompoundDrawablesRelative(d, null, null, null);
        t.setCompoundDrawablePadding(dp(c, 6));
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    public static void rounded(View v, float radiusDp) {
        float r = dp(v.getContext(), radiusDp);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) { outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r); }
        });
        v.setClipToOutline(true);
    }

    /** Photo with object-cover and rounded corners; size in dp (MATCH/WRAP allowed). */
    public static ImageView img(Context c, String url, int wDp, int hDp, float radiusDp) {
        ImageView i = new ImageView(c);
        i.setScaleType(ImageView.ScaleType.CENTER_CROP);
        i.setLayoutParams(new LinearLayout.LayoutParams(wDp < 0 ? wDp : dp(c, wDp), hDp < 0 ? hDp : dp(c, hDp)));
        if (radiusDp > 0) rounded(i, radiusDp);
        load(i, url);
        return i;
    }

    public static void load(ImageView i, String url) {
        if (url == null || url.isEmpty()) { i.setImageDrawable(null); return; }
        Glide.with(i.getContext().getApplicationContext()).load(url).centerCrop().into(i);
    }

    /** grayscale-[n%] */
    public static void grayscale(ImageView i, float amount) {
        if (amount <= 0) { i.clearColorFilter(); return; }
        ColorMatrix m = new ColorMatrix();
        m.setSaturation(1 - amount);
        i.setColorFilter(new ColorMatrixColorFilter(m));
    }

    // ------------------------------------------------------------------ pills, states

    /** rounded-full px-3 py-1 text-xs font-bold */
    public static TextView pill(Context c, String s, int bgColor, int fg) {
        TextView t = text(c, s, 12, fg, BOLD);
        pad(t, 12, 4);
        bg(t, bgColor, 999);
        t.setSingleLine(true);
        return t;
    }

    /** rounded-md badge (px-2 py-1) */
    public static TextView badge(Context c, String s, int bgColor, int fg) {
        TextView t = text(c, s, 12, fg, BOLD);
        pad(t, 8, 4);
        bg(t, bgColor, 6);
        return t;
    }

    public static ProgressBar progress(Context c, int sizeDp, int color) {
        ProgressBar p = new ProgressBar(c);
        p.setIndeterminateTintList(ColorStateList.valueOf(color));
        p.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return p;
    }

    /** <Spinner />: "Đang tải..." with a spinning loader, py-20 */
    public static View spinner(Context c, String label) {
        LinearLayout r = row(c);
        r.setGravity(Gravity.CENTER);
        pad(r, 0, 80, 0, 80);
        r.addView(progress(c, 20, ORANGE));
        TextView t = text(c, label != null ? label : "Đang tải...", 14, SUBTLE);
        add(r, t, 8, WRAP, WRAP);
        return r;
    }

    public static View spinner(Context c) { return spinner(c, null); }

    /** <EmptyState icon title>children</EmptyState> */
    public static LinearLayout empty(Context c, int iconRes, String title, View child) {
        LinearLayout box = card(pad(col(c), 24, 64), 16);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout circle = new FrameLayout(c);
        bg(circle, PEACH, 999);
        ImageView i = icon(c, iconRes, 24, ORANGE);
        circle.addView(i, new FrameLayout.LayoutParams(dp(c, 24), dp(c, 24), Gravity.CENTER));
        box.addView(circle, new LinearLayout.LayoutParams(dp(c, 64), dp(c, 64)));
        TextView t = text(c, title, 18, INK, BOLD);
        t.setGravity(Gravity.CENTER);
        add(box, t, 16, WRAP, WRAP);
        if (child != null) add(box, child, 8, WRAP, WRAP);
        return box;
    }

    /** animate-pulse placeholder */
    public static View skeleton(Context c, int heightDp) {
        View v = new View(c);
        bg(v, SKELETON, 16);
        v.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(c, heightDp)));
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofFloat(v, "alpha", 1f, 0.5f);
        a.setDuration(1000);
        a.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.start();
        return v;
    }

    /** Error / info boxes (rounded-xl px-4 py-3 text-sm) */
    public static TextView note(Context c, String s, int bgColor, int fg) {
        TextView t = text(c, s, 14, fg);
        pad(t, 16, 12);
        bg(t, bgColor, 12);
        return t;
    }

    public static TextView errorBox(Context c, String s) { return note(c, s, RED_BG, RED_TEXT); }

    public static TextView infoBox(Context c, String s) { return note(c, s, GREEN_BG, GREEN_DARK); }

    // ------------------------------------------------------------------ forms

    /** h-12 rounded-xl border px-4, orange border while focused */
    public static EditText input(Context c, String hint) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(SUBTLE);
        e.setTextColor(INK);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setSingleLine(true);
        e.setBackground(inputBg(c));
        pad(e, 16, 0);
        e.setMinHeight(dp(c, 48));
        e.setLayoutParams(new LinearLayout.LayoutParams(MATCH, dp(c, 48)));
        return e;
    }

    public static StateListDrawable inputBg(Context c) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, stroke(WHITE, dp(c, 12), ORANGE, dp(c, 1)));
        s.addState(new int[]{-android.R.attr.state_enabled}, stroke(SOFT, dp(c, 12), BORDER, dp(c, 1)));
        s.addState(new int[]{}, stroke(WHITE, dp(c, 12), BORDER, dp(c, 1)));
        return s;
    }

    public static EditText textarea(Context c, String hint, int rows) {
        EditText e = input(c, hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setMinLines(rows);
        e.setGravity(Gravity.TOP | Gravity.START);
        pad(e, 16, 12);
        e.setLayoutParams(new LinearLayout.LayoutParams(MATCH, WRAP));
        return e;
    }

    public static EditText maxLength(EditText e, int n) {
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(n)});
        return e;
    }

    /** <Field label> : text-sm font-semibold label above the input */
    public static LinearLayout field(Context c, String label, View input) {
        LinearLayout l = col(c);
        if (label != null && !label.isEmpty()) l.addView(text(c, label, 14, INK, SEMI));
        add(l, input, label != null && !label.isEmpty() ? 8 : 0);
        return l;
    }

    public static CheckBox check(Context c, String label, boolean checked) {
        CheckBox b = new CheckBox(c);
        b.setText(label);
        b.setTextColor(MUTED);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setChecked(checked);
        CompoundButtonCompat.setButtonTintList(b, new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}}, new int[]{ORANGE, FAINT}));
        return b;
    }

    public static int resId(String name) {
        try { return R.drawable.class.getField(name).getInt(null); } catch (Exception e) { return 0; }
    }
}
