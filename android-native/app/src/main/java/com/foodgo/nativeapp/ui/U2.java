package com.foodgo.nativeapp.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;

import androidx.browser.customtabs.CustomTabsIntent;

/** Platform helpers: activity lookup, clipboard, links. */
public final class U2 {
    private U2() {}

    public static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return com.foodgo.nativeapp.App.current();
    }

    public static void copy(Context c, String value) {
        ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("FoodGo", value));
    }

    /** <a href="tel:..."> */
    public static void dial(Context c, String phone) {
        try { c.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone))); } catch (Exception ignored) {}
    }

    /** target="_blank" links: Google Maps directions etc. */
    public static void openExternal(Context c, String url) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            openTab(c, url);
        }
    }

    /** System browser tab (Custom Tabs), used for Google sign-in. */
    public static void openTab(Context c, String url) {
        try {
            new CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(c, Uri.parse(url));
        } catch (Exception e) {
            try { c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) {}
        }
    }
}
