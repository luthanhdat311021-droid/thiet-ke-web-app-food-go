package com.foodgo.nativeapp.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.core.Net;

import java.util.ArrayList;
import java.util.List;

/** The dark pill toast of app-provider.tsx (bottom-24, 2.6 s), drawn above any open sheet. */
public final class Toaster {
    private Toaster() {}

    /** Full-screen overlays (sheets, drawers) register here so toasts show on top of them. */
    private static final List<FrameLayout> hosts = new ArrayList<>();
    private static View currentToast;
    private static final Runnable hide = () -> remove();

    public static void pushHost(FrameLayout host) { hosts.add(host); }

    public static void popHost(FrameLayout host) { hosts.remove(host); }

    private static void remove() {
        if (currentToast != null && currentToast.getParent() instanceof ViewGroup) ((ViewGroup) currentToast.getParent()).removeView(currentToast);
        currentToast = null;
    }

    public static void show(Activity a, String message, boolean error) {
        if (a == null || message == null) return;
        Net.main(() -> {
            FrameLayout host = null;
            for (int i = hosts.size() - 1; i >= 0; i--) {
                FrameLayout h = hosts.get(i);
                if (h.isAttachedToWindow() && h.getContext() instanceof Context) { host = h; break; }
            }
            if (host == null) host = a.findViewById(android.R.id.content);
            if (host == null) return;
            Net.cancel(hide);
            remove();
            Context c = host.getContext();
            LinearLayout pill = U.row(c);
            U.pad(pill, 20, 12);
            U.bg(pill, U.INK, 999);
            pill.setElevation(U.dp(c, 8));
            pill.addView(U.icon(c, error ? R.drawable.ic_close : R.drawable.ic_check, 16, error ? 0xFFFF8A70 : 0xFF72D09A));
            TextView t = U.text(c, message, 14, U.WHITE, U.SEMI);
            U.ellipsize(t, 1);
            U.add(pill, t, 8, U.WRAP, U.WRAP);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            p.bottomMargin = U.dp(c, 96);
            p.leftMargin = p.rightMargin = U.dp(c, 20);
            pill.setAlpha(0f);
            host.addView(pill, p);
            pill.animate().alpha(1f).setDuration(150).start();
            currentToast = pill;
            Net.later(hide, 2600);
        });
    }
}
