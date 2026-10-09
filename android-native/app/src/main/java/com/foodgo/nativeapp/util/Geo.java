package com.foodgo.nativeapp.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;

import com.foodgo.nativeapp.Config;
import com.foodgo.nativeapp.core.Cb;
import com.foodgo.nativeapp.core.Json;
import com.foodgo.nativeapp.core.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** lib/geo.ts (the /api/geo/* routes of the web app are reused as-is). */
public final class Geo {
    private Geo() {}

    public static class LatLng {
        public final double lat, lng;
        public LatLng(double lat, double lng) { this.lat = lat; this.lng = lng; }
    }

    /** accuracy: radius in metres reported by the device (large = network guess, not GPS) */
    public static class Place extends LatLng {
        public final String address;
        public final double accuracy;
        public Place(double lat, double lng, String address, double accuracy) { super(lat, lng); this.address = address; this.accuracy = accuracy; }
    }

    /** Beyond this the position is a rough guess and the customer should confirm the pin. */
    public static final double ROUGH_ACCURACY_M = 150;

    public static final LatLng DEFAULT_CENTER = new LatLng(10.9806, 106.6744); // Trường ĐH Thủ Dầu Một

    public static double distanceKm(double aLat, double aLng, double bLat, double bLng) {
        double R = 6371, rad = Math.PI / 180;
        double h = Math.pow(Math.sin((bLat - aLat) * rad / 2), 2)
                + Math.cos(aLat * rad) * Math.cos(bLat * rad) * Math.pow(Math.sin((bLng - aLng) * rad / 2), 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    public static double distanceKm(LatLng a, LatLng b) { return distanceKm(a.lat, a.lng, b.lat, b.lng); }

    public static String formatKm(double km) {
        return km < 1 ? Math.round(km * 1000) + " m" : String.format(Locale.US, "%.1f km", km);
    }

    public static String coords(double lat, double lng) { return String.format(Locale.US, "%.5f, %.5f", lat, lng); }

    // ------------------------------------------------------------------ device position

    public static final String ERR_PERMISSION = "Hãy cấp quyền Vị trí cho FoodGo: Cài đặt → Ứng dụng → FoodGo → Quyền → Vị trí";

    /**
     * Best device position within a few seconds. The first fix is often a coarse network guess,
     * so keep listening until GPS is precise enough (≤ 30 m) or time runs out. Permission must already be granted.
     */
    @SuppressLint("MissingPermission")
    public static void currentPosition(Context ctx, long maxWaitMs, Cb<Place> cb) {
        LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.done(null, "Thiết bị không hỗ trợ định vị"); return; }
        List<String> providers = new ArrayList<>();
        for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try { if (lm.isProviderEnabled(p)) providers.add(p); } catch (Exception ignored) {}
        }
        // device location switched off: no fix will ever come, say so right away
        if (providers.isEmpty()) { cb.done(null, "Hãy bật Vị trí (GPS) trên điện thoại rồi thử lại"); return; }

        final Location[] best = {null};
        final boolean[] done = {false};
        final LocationListener[] listener = new LocationListener[1];
        Runnable finish = new Runnable() {
            @Override public void run() {
                if (done[0]) return;
                done[0] = true;
                try { lm.removeUpdates(listener[0]); } catch (Exception ignored) {}
                Net.cancel(this);
                if (best[0] != null) cb.done(new Place(best[0].getLatitude(), best[0].getLongitude(), null, best[0].getAccuracy()), null);
                else cb.done(null, "Lấy vị trí quá lâu, hãy bật GPS rồi thử lại");
            }
        };
        listener[0] = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (done[0]) return;
                if (best[0] == null || l.getAccuracy() < best[0].getAccuracy()) best[0] = l;
                if (l.getAccuracy() <= 30) finish.run();
            }
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(String provider) {}
            @Override public void onProviderDisabled(String provider) {}
        };
        try {
            for (String p : providers) lm.requestLocationUpdates(p, 0, 0, listener[0], Looper.getMainLooper());
        } catch (SecurityException e) {
            cb.done(null, ERR_PERMISSION);
            return;
        }
        Net.later(finish, maxWaitMs);
    }

    // ------------------------------------------------------------------ geocoding & routes

    public static void reverseGeocode(double lat, double lng, Cb<String> cb) {
        Net.getJson(Config.SITE + "/api/geo/reverse?lat=" + lat + "&lng=" + lng, (data, err) -> {
            String a = data != null && data.isJsonObject() ? Json.str(data.getAsJsonObject(), "address") : null;
            cb.done(a, a == null ? "Không lấy được địa chỉ" : null);
        });
    }

    /** data = {lat, lng, address} or null when not found. */
    public static void search(String address, Cb<Place> cb) {
        Net.getJson(Config.SITE + "/api/geo/search?q=" + android.net.Uri.encode(address), (data, err) -> {
            if (data == null || !data.isJsonObject()) { cb.done(null, err != null ? err : "Không tìm thấy địa chỉ"); return; }
            JsonObject o = data.getAsJsonObject();
            cb.done(new Place(Json.num(o, "lat", 0), Json.num(o, "lng", 0), Json.str(o, "address"), 0), null);
        });
    }

    public static class Route {
        public final List<double[]> points;
        public final double distance; // m
        public final double duration; // s
        Route(List<double[]> points, double distance, double duration) { this.points = points; this.distance = distance; this.duration = duration; }
    }

    public static void route(LatLng from, LatLng to, Cb<Route> cb) {
        String q = "fromLat=" + from.lat + "&fromLng=" + from.lng + "&toLat=" + to.lat + "&toLng=" + to.lng;
        Net.getJson(Config.SITE + "/api/geo/route?" + q, (data, err) -> {
            if (data == null || !data.isJsonObject()) { cb.done(null, err); return; }
            JsonObject o = data.getAsJsonObject();
            List<double[]> pts = new ArrayList<>();
            JsonElement p = o.get("points");
            if (p != null && p.isJsonArray()) {
                for (JsonElement e : (JsonArray) p) {
                    JsonArray a = e.getAsJsonArray();
                    pts.add(new double[]{a.get(0).getAsDouble(), a.get(1).getAsDouble()});
                }
            }
            cb.done(new Route(pts, Json.num(o, "distance", 0), Json.num(o, "duration", 0)), null);
        });
    }

    /** Point at t (0..1) of the way along a polyline, by length. */
    public static double[] pointAlong(List<double[]> points, double t) {
        if (points.size() < 2) return points.get(0);
        double[] seg = new double[points.size() - 1];
        double total = 0;
        for (int i = 0; i < seg.length; i++) {
            seg[i] = distanceKm(points.get(i)[0], points.get(i)[1], points.get(i + 1)[0], points.get(i + 1)[1]);
            total += seg[i];
        }
        double target = total * Math.min(1, Math.max(0, t));
        for (int i = 0; i < seg.length; i++) {
            if (target <= seg[i] || i == seg.length - 1) {
                double f = seg[i] != 0 ? Math.min(1, target / seg[i]) : 0;
                double[] a = points.get(i), b = points.get(i + 1);
                return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f};
            }
            target -= seg[i];
        }
        return points.get(points.size() - 1);
    }
}
