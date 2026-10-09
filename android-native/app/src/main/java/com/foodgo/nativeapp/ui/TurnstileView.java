package com.foodgo.nativeapp.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.core.Net;

import java.util.function.Consumer;

/**
 * Cloudflare Turnstile (components/turnstile.tsx). Supabase Auth verifies the token when its bot protection is on,
 * so sign-in / sign-up / password reset need one. Cloudflare only ships Turnstile as a web widget, so it runs in a
 * tiny WebView ("interaction-only": invisible unless Cloudflare asks the visitor to click). Tokens are single-use.
 */
public class TurnstileView extends FrameLayout {
    private final WebView web;
    private Consumer<String> onToken;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public TurnstileView(Context c) {
        super(c);
        web = new WebView(c);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface public void token(String t) { Net.main(() -> { if (onToken != null) onToken.accept(t == null || t.isEmpty() ? null : t); }); }
        }, "FG");
        String html = "<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;padding:0;background:transparent;display:flex;justify-content:center}</style>"
                + "<script src='https://challenges.cloudflare.com/turnstile/v0/api.js?onload=onTs&render=explicit' async defer></script></head>"
                + "<body><div id='w'></div><script>"
                + "var id=null;function onTs(){id=turnstile.render('#w',{sitekey:'" + Config.TURNSTILE_SITE_KEY + "',language:'vi',appearance:'interaction-only',"
                + "callback:function(t){FG.token(t)},'expired-callback':function(){FG.token('')},'error-callback':function(){FG.token('')}});}"
                + "function resetTs(){if(id!==null&&window.turnstile){turnstile.reset(id);FG.token('')}}"
                + "</script></body></html>";
        // the widget's site key is bound to the web app's domain
        web.loadDataWithBaseURL(Config.SITE + "/login", html, "text/html", "utf-8", null);
        addView(web, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public static boolean enabled() { return !Config.TURNSTILE_SITE_KEY.isEmpty(); }

    public TurnstileView onToken(Consumer<String> cb) { onToken = cb; return this; }

    /** tokens are single-use: call after every submit */
    public void reset() { web.evaluateJavascript("resetTs()", null); }

    public void destroy() { web.destroy(); }
}
