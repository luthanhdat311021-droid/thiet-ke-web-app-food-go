package com.foodgo.nativeapp.util;

import com.foodgo.nativeapp.core.Iso;
import com.foodgo.nativeapp.model.Restaurant;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** storeHours() from lib/store.ts: same rule as fg_store_open() in the DB. */
public final class StoreHours {
    public final boolean open;
    public final boolean paused;
    public final String hours;
    public final String reopens;

    private StoreHours(boolean open, boolean paused, String hours, String reopens) {
        this.open = open; this.paused = paused; this.hours = hours; this.reopens = reopens;
    }

    private static String hhmm(String t) { return t.length() >= 5 ? t.substring(0, 5) : t; }

    /** Current time-of-day in Vietnam as "HH:MM", whatever the device's timezone. */
    static String vnNow() {
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return f.format(new Date());
    }

    public static StoreHours of(boolean isOpen, String openTime, String closeTime) {
        boolean paused = !isOpen;
        if (openTime == null || closeTime == null || hhmm(openTime).equals(hhmm(closeTime))) {
            return new StoreHours(!paused, paused, null, null);
        }
        String o = hhmm(openTime), c = hhmm(closeTime), t = vnNow();
        boolean inHours = o.compareTo(c) < 0 ? t.compareTo(o) >= 0 && t.compareTo(c) < 0 : t.compareTo(o) >= 0 || t.compareTo(c) < 0;
        return new StoreHours(!paused && inHours, paused, o + " - " + c, paused ? null : o);
    }

    public static StoreHours of(Restaurant r) { return of(r.is_open, r.open_time, r.close_time); }

    // ------------------------------------------------------------------ subscription (lib/shop.ts)

    public static final long SUBSCRIPTION_FEE = 200000;
    public static final int[] SUBSCRIPTION_MONTHS = {1, 3, 6, 12};

    public static final class Subscription {
        public final boolean active;
        public final long daysLeft;
        public final Long until; // millis
        Subscription(boolean active, long daysLeft, Long until) { this.active = active; this.daysLeft = daysLeft; this.until = until; }
    }

    /** Admin-run restaurants (no owner) never expire. */
    public static Subscription subscriptionOf(String ownerId, String paidUntil) {
        if (ownerId == null) return new Subscription(true, Long.MAX_VALUE, null);
        Long until = paidUntil != null ? Iso.parse(paidUntil) : null;
        long ms = until != null ? until - System.currentTimeMillis() : -1;
        return new Subscription(ms > 0, Math.max(0, (long) Math.ceil(ms / 86400000.0)), until);
    }

    public static Subscription subscriptionOf(Restaurant r) { return subscriptionOf(r.owner_id, r.paid_until); }
}
