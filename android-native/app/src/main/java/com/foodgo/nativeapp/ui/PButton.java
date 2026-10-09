package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** <Button> with an optional icon and a busy spinner (Loader2 animate-spin). */
public class PButton extends LinearLayout {
    public static final int PRIMARY = 0, OUTLINE = 1, DARK = 2, SOFT = 3, MOMO = 4, DANGER = 5, GHOST = 6;

    private final TextView label;
    private final ProgressBar spinner;
    private final ImageView icon;
    private boolean busy;
    private boolean enabled = true;

    public PButton(Context c, String text, int style) { this(c, text, style, 0); }

    public PButton(Context c, String text, int style, int iconRes) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        int fg;
        GradientDrawable bg;
        float r = U.dp(c, 12);
        switch (style) {
            case OUTLINE: fg = U.INK; bg = U.stroke(U.WHITE, r, U.BORDER, U.dp(c, 1)); break;
            case DARK: fg = U.WHITE; bg = U.round(U.INK, r); break;
            case SOFT: fg = U.INK; bg = U.round(U.SOFT, r); break;
            case MOMO: fg = U.WHITE; bg = U.round(U.MOMO, r); break;
            case DANGER: fg = U.WHITE; bg = U.round(U.RED, r); break;
            case GHOST: fg = U.ORANGE; bg = U.round(0x00000000, r); break;
            default: fg = U.WHITE; bg = U.round(U.ORANGE, r);
        }
        U.pressable(this, bg);
        U.pad(this, 20, 0);
        setMinimumHeight(U.dp(c, 48));
        spinner = U.progress(c, 18, fg);
        spinner.setVisibility(GONE);
        addView(spinner);
        icon = new ImageView(c);
        if (iconRes != 0) icon.setImageDrawable(U.drawable(c, iconRes, fg));
        icon.setVisibility(iconRes != 0 ? VISIBLE : GONE);
        LayoutParams ip = new LayoutParams(U.dp(c, 18), U.dp(c, 18));
        addView(icon, ip);
        label = U.text(c, text, 15, fg, U.SEMI);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        addView(label, lp);
        updateSpacing();
    }

    private void updateSpacing() {
        boolean lead = spinner.getVisibility() == VISIBLE || icon.getVisibility() == VISIBLE;
        ((LayoutParams) label.getLayoutParams()).leftMargin = lead && label.getText().length() > 0 ? U.dp(getContext(), 8) : 0;
        if (spinner.getVisibility() == VISIBLE && icon.getVisibility() == VISIBLE) ((LayoutParams) icon.getLayoutParams()).leftMargin = U.dp(getContext(), 8);
        label.requestLayout();
    }

    public PButton text(String s) { label.setText(s); updateSpacing(); return this; }

    public TextView label() { return label; }

    public PButton textColor(int color) { label.setTextColor(color); return this; }

    public PButton textSize(float sp) { label.setTextSize(sp); return this; }

    public PButton height(int dp) {
        setMinimumHeight(U.dp(getContext(), dp));
        return this;
    }

    /** Shows the spinner (and hides the icon), like `{busy && <Loader2 className="animate-spin" />}`. */
    public void setBusy(boolean b) {
        busy = b;
        spinner.setVisibility(b ? VISIBLE : GONE);
        updateSpacing();
        refresh();
    }

    public PButton onClick(Runnable r) {
        setOnClickListener(v -> { if (enabled && !busy) r.run(); });
        return this;
    }

    @Override
    public void setEnabled(boolean e) {
        enabled = e;
        refresh();
    }

    public boolean isOn() { return enabled && !busy; }

    private void refresh() {
        // disabled={busy || ...} in the web app: dimmed and not clickable
        super.setEnabled(enabled && !busy);
        setAlpha(enabled && !busy ? 1f : 0.5f);
    }
}
