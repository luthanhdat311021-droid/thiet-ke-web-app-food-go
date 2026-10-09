package com.foodgo.nativeapp.pay;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.BaseActivity;
import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.ui.U;

/**
 * MoMo's own payment page (a website run by MoMo). As in the WebView app it stays inside FoodGo, so the
 * redirect to …/orders/<id>?resultCode=… is caught here and handed to the native order screen.
 */
public class MomoPayActivity extends BaseActivity {
    public static final String EXTRA_URL = "url";
    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = U.col(this);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(U.WHITE);
        LinearLayout bar = U.row(this);
        U.pad(bar, 12, 8);
        android.widget.ImageView close = U.icon(this, R.drawable.ic_close, 24, U.INK);
        U.pad(close, 8);
        U.click(close, this::finish);
        bar.addView(close, new LinearLayout.LayoutParams(U.dp(this, 40), U.dp(this, 40)));
        TextView title = U.text(this, "Thanh toán MoMo", 16, U.INK, U.BOLD);
        U.add(bar, title, 8, U.WRAP, U.WRAP);
        root.addView(bar);
        root.addView(U.divider(this, U.LINE));
        FrameLayout box = new FrameLayout(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        ProgressBar loading = U.progress(this, 28, U.MOMO);
        box.addView(web, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        box.addView(loading, new FrameLayout.LayoutParams(U.dp(this, 28), U.dp(this, 28), Gravity.CENTER));
        root.addView(box, new LinearLayout.LayoutParams(U.MATCH, 0, 1));
        setContentView(root);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handle(request.getUrl()); }
            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handle(Uri.parse(url)); }
            @Override public void onPageFinished(WebView view, String url) { loading.setVisibility(android.view.View.GONE); }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (web.canGoBack()) web.goBack(); else finish(); }
        });
        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url != null) web.loadUrl(url);
    }

    private boolean handle(Uri u) {
        String url = u.toString();
        // back from MoMo: <site>/orders/<id>?partnerCode=...&resultCode=...&signature=...
        if (url.startsWith(Config.SITE + "/orders/")) {
            String path = url.substring(Config.SITE.length());
            App.navigate(path);
            finish();
            return true;
        }
        String scheme = u.getScheme();
        if (scheme != null && !scheme.equals("http") && !scheme.equals("https")) {
            // momo:// or intent:// — hand over to the MoMo app when it's installed
            try {
                Intent i = scheme.equals("intent") ? Intent.parseUri(url, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, u);
                startActivity(i);
            } catch (Exception ignored) {}
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
