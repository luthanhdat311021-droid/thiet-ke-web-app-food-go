package com.foodgo.nativeapp.screens;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.foodgo.nativeapp.MainActivity;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.ui.U;

/** One page of the web app (a Next.js route), shown inside MainActivity's shell. */
public abstract class Screen {
    protected MainActivity act;
    /** web-style path incl. query, e.g. "/search?q=pho" */
    public String path;
    protected Uri uri;
    private View view;
    boolean built;

    public void attach(MainActivity act, String path) {
        this.act = act;
        this.path = path;
        this.uri = Uri.parse("https://foodgo.local" + path);
    }

    /** RequireAuth: only for signed-in users (others go to /login?next=…). */
    public boolean requiresAuth() { return false; }

    /** Path without query (window.location.pathname). */
    public String pathname() { return uri.getPath(); }

    protected abstract View build();

    public final View view() {
        if (view == null) { view = build(); built = true; }
        return view;
    }

    public boolean isBuilt() { return built; }

    /** Became the visible page (again). */
    public void onShow() {}

    /** Removed from the back stack for good: drop listeners / realtime channels. */
    public void onDestroy() {}

    /** Android back: return true when handled inside the screen. */
    public boolean onBack() { return false; }

    protected Context ctx() { return act; }

    protected AppState state() { return AppState.get(); }

    protected String param(String name) { return uri.getQueryParameter(name); }

    protected void navigate(String p) { act.navigate(p); }

    protected void replace(String p) { act.replace(p); }

    protected boolean alive() { return act != null && !act.isFinishing() && act.isCurrent(this); }

    protected boolean exists() { return act != null && !act.isFinishing() && act.inStack(this); }

    // ------------------------------------------------------------------ page helpers

    /** <main className="mx-auto px-5 pb-24 pt-8"> inside a vertical scroll. */
    protected ScrollView scrollPage(LinearLayout content) {
        ScrollView s = new ScrollView(act);
        s.setFillViewport(true);
        s.setBackgroundColor(U.BG);
        U.pad(content, 20, 32, 20, 32);
        s.addView(content, new ScrollView.LayoutParams(U.MATCH, U.WRAP));
        return s;
    }
}
