package com.foodgo.nativeapp;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import com.foodgo.nativeapp.core.Auth;
import com.foodgo.nativeapp.state.AppState;

import org.osmdroid.config.Configuration;

import java.lang.ref.WeakReference;

public class App extends Application {
    private static App instance;
    private static WeakReference<Activity> current = new WeakReference<>(null);

    public static App get() { return instance; }

    /** The activity on screen (toasts and confirm dialogs attach to it). */
    public static Activity current() { return current.get(); }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("FoodGo/1.0 (" + getPackageName() + ")");
        Auth.init(this);
        AppState.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { current = new WeakReference<>(a); }
            @Override public void onActivityStarted(Activity a) { current = new WeakReference<>(a); }
            @Override public void onActivityResumed(Activity a) { current = new WeakReference<>(a); }
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) { if (current.get() == a) current = new WeakReference<>(null); }
        });
    }

    /**
     * Opens a web-style path ("/orders/12", "/account?tab=favorites", "/admin") from anywhere,
     * the same links the web app uses (notifications carry them too).
     */
    public static void navigate(String path) {
        Activity a = current();
        if (a instanceof MainActivity) { ((MainActivity) a).navigate(path); return; }
        // MainActivity is singleTask: admin / shop screens above it close, the path arrives in onNewIntent
        Intent i = new Intent(instance, MainActivity.class).putExtra(MainActivity.EXTRA_PATH, path)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (a != null) a.startActivity(i);
        else instance.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
