package com.foodgo.nativeapp.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.foodgo.nativeapp.R;

/**
 * Full-screen modal layer: `fixed inset-0 bg-black/30` with a panel placed inside (bottom sheet, right drawer
 * or full screen). Tapping the dim area or pressing Back closes it; toasts are drawn on top.
 */
public class Overlay {
    public static final int BOTTOM = 0, RIGHT = 1, FULL = 2, CENTER = 3;

    public final Dialog dialog;
    public final FrameLayout root;
    private Runnable onDismiss;

    public Overlay(Activity a, View panel, int placement) { this(a, panel, placement, true); }

    public Overlay(Activity a, View panel, int placement, boolean dim) {
        dialog = new Dialog(a, R.style.AppTheme_FullDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        root = new FrameLayout(a);
        root.setBackgroundColor(dim ? 0x4D000000 : Color.TRANSPARENT);
        // edge-to-edge (Android 15+): keep panels clear of the status bar and the gesture bar
        root.setFitsSystemWindows(true);
        root.setOnClickListener(v -> dismiss());
        panel.setClickable(true); // clicks inside the panel don't reach the dim layer
        FrameLayout.LayoutParams p;
        switch (placement) {
            case BOTTOM: p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM); break;
            case RIGHT: p = new FrameLayout.LayoutParams(Math.min(U.dp(a, 448), a.getResources().getDisplayMetrics().widthPixels), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END); break;
            case CENTER: p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER); break;
            default: p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        root.addView(panel, p);
        dialog.setContentView(root);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            if (placement == BOTTOM) w.setWindowAnimations(android.R.style.Animation_InputMethod);
        }
        dialog.setOnShowListener(d -> Toaster.pushHost(root));
        dialog.setOnDismissListener(d -> {
            Toaster.popHost(root);
            if (onDismiss != null) onDismiss.run();
        });
    }

    public Overlay onDismiss(Runnable r) { onDismiss = r; return this; }

    public Overlay show() { dialog.show(); return this; }

    public void dismiss() { if (dialog.isShowing()) dialog.dismiss(); }

    public boolean isShowing() { return dialog.isShowing(); }
}
