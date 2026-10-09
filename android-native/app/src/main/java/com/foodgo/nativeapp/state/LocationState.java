package com.foodgo.nativeapp.state;

import android.app.Activity;

import com.foodgo.nativeapp.App;
import com.foodgo.nativeapp.BaseActivity;
import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.util.Geo;

import java.util.ArrayList;
import java.util.List;

/**
 * components/location-provider.tsx: detects the customer's current address once they are signed in
 * (kept for this app session, like sessionStorage on the web).
 */
public final class LocationState implements AppState.Listener {
    public interface Listener { void onLocation(); }

    private static final LocationState I = new LocationState();

    public static LocationState get() { return I; }

    private static final long MAX_AGE_MS = 10 * 60 * 1000;

    /** idle | locating | ready | error */
    public String status = "idle";
    public Geo.Place place;
    public String error = "";
    /** full-screen "định vị" map (opens by itself right after sign-in) */
    public boolean pickerOpen;

    private long savedAt;
    private boolean asked;
    /** the map greets the customer once per app session */
    private boolean introShown;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean started;

    private LocationState() {}

    /** Hooks into sign-in / sign-out (call once the UI is up). */
    public void start() {
        if (started) return;
        started = true;
        // only reuse a recent fix – the customer may have moved since
        if (place != null && System.currentTimeMillis() - savedAt < MAX_AGE_MS) asked = true;
        AppState.get().addListener(this);
        onAppState();
    }

    public void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public void removeListener(Listener l) { listeners.remove(l); }

    private void changed() { for (Listener l : new ArrayList<>(listeners)) l.onLocation(); }

    // right after sign-in (once per session): open the map and locate the customer
    @Override
    public void onAppState() {
        AppState s = AppState.get();
        // signed out: the next sign-in greets with the map again
        if (!s.signedIn() && !s.authLoading) { asked = false; introShown = false; }
        if (!s.signedIn() || asked) return;
        asked = true;
        locate(null);
        if (!introShown) { introShown = true; setPickerOpen(true); }
    }

    public void setPickerOpen(boolean open) { pickerOpen = open; changed(); }

    private void save(Geo.Place next) {
        place = next;
        status = "ready";
        savedAt = System.currentTimeMillis();
        changed();
    }

    /** data = the new place, or null on failure (status/error tell why). */
    public void locate(Cb<Geo.Place> cb) {
        status = "locating";
        error = "";
        changed();
        Activity a = App.current();
        Cb<Geo.Place> fail = (p, err) -> {
            status = "error";
            error = err != null ? err : "Không xác định được vị trí";
            changed();
            if (cb != null) cb.done(null, error);
        };
        if (!(a instanceof BaseActivity)) { fail.done(null, "Không xác định được vị trí"); return; }
        ((BaseActivity) a).withLocationPermission(granted -> {
            if (!granted) { fail.done(null, Geo.ERR_PERMISSION); return; }
            Geo.currentPosition(a, 8000, (fix, err) -> {
                if (fix == null) { fail.done(null, err); return; }
                Geo.reverseGeocode(fix.lat, fix.lng, (address, e2) -> {
                    Geo.Place next = new Geo.Place(fix.lat, fix.lng, address != null ? address : Geo.coords(fix.lat, fix.lng), fix.accuracy);
                    save(next);
                    if (cb != null) cb.done(next, null);
                });
            });
        });
    }

    /** a pin placed by hand is exact */
    public void choose(Geo.Place p) { save(new Geo.Place(p.lat, p.lng, p.address, 0)); }
}
