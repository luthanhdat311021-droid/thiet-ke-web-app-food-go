package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.util.Geo;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** components/map/leaflet-map.tsx: markers, an optional route and an optional draggable picker pin. */
public class FoodMap extends FrameLayout {
    public static final class MapMarker {
        final String id; final double lat, lng; final String kind; final String label;
        /** kind: restaurant | home | driver */
        public MapMarker(String id, String kind, double lat, double lng, String label) { this.id = id; this.kind = kind; this.lat = lat; this.lng = lng; this.label = label; }
        public double lat() { return lat; }
        public double lng() { return lng; }
    }

    public final MapView map;
    private final List<Overlay> dynamic = new ArrayList<>();
    private Marker pickerMarker;
    private Consumer<Geo.LatLng> onPick;
    private String fitKey = "";
    private int paddingDp = 48;

    public FoodMap(Context c, int heightDp) {
        super(c);
        map = MapKit.newMap(c, true);
        addView(map, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        TextView attr = U.text(c, "© Esri, OpenStreetMap", 9, U.MUTED);
        attr.setBackgroundColor(0xB3FFFFFF);
        U.pad(attr, 4, 1);
        addView(attr, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END));
        setLayoutParams(new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, U.dp(c, heightDp)));
        map.getController().setZoom(15.0);
        map.getController().setCenter(new GeoPoint(Geo.DEFAULT_CENTER.lat, Geo.DEFAULT_CENTER.lng));
        // clicking also works before any pin exists (e.g. a new restaurant)
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override public boolean singleTapConfirmedHelper(GeoPoint p) {
                if (onPick == null) return false;
                onPick.accept(new Geo.LatLng(p.getLatitude(), p.getLongitude()));
                return true;
            }
            @Override public boolean longPressHelper(GeoPoint p) { return false; }
        }));
    }

    public FoodMap padding(int dp) { paddingDp = dp; return this; }

    public FoodMap onPick(Consumer<Geo.LatLng> cb) { onPick = cb; return this; }

    /** Re-draws markers / route / picker; the view re-frames only when `fit` changes (like FitBounds' key). */
    public void render(List<MapMarker> markers, List<double[]> route, Geo.LatLng picker, List<double[]> fit) {
        Context c = getContext();
        for (Overlay o : dynamic) map.getOverlays().remove(o);
        dynamic.clear();
        if (route != null && route.size() > 1) {
            Polyline line = new Polyline(map);
            List<GeoPoint> pts = new ArrayList<>();
            for (double[] p : route) pts.add(new GeoPoint(p[0], p[1]));
            line.setPoints(pts);
            line.getOutlinePaint().setColor(0xD9FF5B35);
            line.getOutlinePaint().setStrokeWidth(U.dp(c, 5));
            line.getOutlinePaint().setStrokeCap(android.graphics.Paint.Cap.ROUND);
            line.setInfoWindow(null);
            dynamic.add(line);
        }
        if (markers != null) {
            // the driver is drawn last, above the other markers (zIndexOffset 1000)
            List<MapMarker> ordered = new ArrayList<>();
            for (MapMarker m : markers) if (!"driver".equals(m.kind)) ordered.add(m);
            for (MapMarker m : markers) if ("driver".equals(m.kind)) ordered.add(m);
            for (MapMarker m : ordered) {
                Marker mk = new Marker(map);
                mk.setPosition(new GeoPoint(m.lat, m.lng));
                mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                mk.setIcon("driver".equals(m.kind) ? MapKit.badge(c, U.GREEN, "🛵", 44)
                        : "home".equals(m.kind) ? MapKit.badge(c, U.ORANGE, "🏠", 40) : MapKit.badge(c, U.INK, "🍽️", 40));
                mk.setTitle(m.label);
                mk.setInfoWindow(null);
                dynamic.add(mk);
            }
        }
        if (picker != null && onPick != null) {
            pickerMarker = new Marker(map);
            pickerMarker.setPosition(new GeoPoint(picker.lat, picker.lng));
            pickerMarker.setIcon(MapKit.dropPin(c, 36, 48, false));
            pickerMarker.setAnchor(Marker.ANCHOR_CENTER, 46f / 48f);
            pickerMarker.setDraggable(true);
            pickerMarker.setInfoWindow(null);
            pickerMarker.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
                @Override public void onMarkerDrag(Marker marker) {}
                @Override public void onMarkerDragStart(Marker marker) {}
                @Override public void onMarkerDragEnd(Marker marker) {
                    GeoPoint p = marker.getPosition();
                    if (onPick != null) onPick.accept(new Geo.LatLng(p.getLatitude(), p.getLongitude()));
                }
            });
            dynamic.add(pickerMarker);
        }
        map.getOverlays().addAll(dynamic);
        map.invalidate();

        List<double[]> pts = fit;
        if (pts == null) {
            pts = new ArrayList<>();
            if (markers != null) for (MapMarker m : markers) pts.add(new double[]{m.lat, m.lng});
            if (picker != null) pts.add(new double[]{picker.lat, picker.lng});
        }
        StringBuilder key = new StringBuilder();
        for (double[] p : pts) key.append(p[0]).append(',').append(p[1]).append('|');
        if (!key.toString().equals(fitKey)) {
            fitKey = key.toString();
            frame(pts);
        }
    }

    private void frame(List<double[]> pts) {
        if (pts.isEmpty()) return;
        Runnable go = () -> {
            if (pts.size() == 1) {
                double z = Math.max(map.getZoomLevelDouble(), 16);
                map.getController().setZoom(z);
                map.getController().setCenter(new GeoPoint(pts.get(0)[0], pts.get(0)[1]));
            } else {
                List<GeoPoint> g = new ArrayList<>();
                for (double[] p : pts) g.add(new GeoPoint(p[0], p[1]));
                BoundingBox box = BoundingBox.fromGeoPointsSafe(g);
                map.zoomToBoundingBox(box, false, U.dp(getContext(), paddingDp), 17.0, null);
            }
        };
        if (map.getWidth() > 0 && map.getHeight() > 0) go.run();
        else map.addOnFirstLayoutListener((v, l, t, r, b) -> go.run());
    }
}
