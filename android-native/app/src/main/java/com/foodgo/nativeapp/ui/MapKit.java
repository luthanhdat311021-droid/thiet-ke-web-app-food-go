package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourcePolicy;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;

/** Shared osmdroid setup: Esri World Street Map tiles (same as the web), marker bitmaps. */
public final class MapKit {
    private MapKit() {}

    /** Esri World Street Map: no API key, Vietnamese street labels; attribution is required. */
    public static final OnlineTileSourceBase ESRI = new OnlineTileSourceBase("EsriWorldStreetMap", 0, 19, 256, "",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/"},
            "Tiles © Esri — Esri, HERE, Garmin, © OpenStreetMap contributors",
            new TileSourcePolicy(2, TileSourcePolicy.FLAG_NO_PREVENTIVE | TileSourcePolicy.FLAG_USER_AGENT_MEANINGFUL | TileSourcePolicy.FLAG_USER_AGENT_NORMALIZED)) {
        @Override
        public String getTileURLString(long index) {
            return getBaseUrl() + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index);
        }
    };

    public static MapView newMap(Context c, boolean zoomButtons) {
        MapView m = new MapView(c);
        m.setTileSource(ESRI);
        m.setMultiTouchControls(true);
        m.setTilesScaledToDpi(true);
        m.setMinZoomLevel(3.0);
        m.setMaxZoomLevel(19.0);
        m.getZoomController().setVisibility(zoomButtons ? CustomZoomButtonsController.Visibility.ALWAYS : CustomZoomButtonsController.Visibility.NEVER);
        m.setBackgroundColor(0xFFE8EFE9);
        // screens stay in the back stack and get re-attached: don't tear the map down on detach
        m.setDestroyMode(false);
        // a map inside a scrolling page: the finger pans the map, not the page
        m.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) parentScroll(v, false);
            else if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) parentScroll(v, true);
            return false;
        });
        return m;
    }

    private static void parentScroll(View v, boolean allow) {
        ViewGroup p = (ViewGroup) v.getParent();
        if (p != null) p.requestDisallowInterceptTouchEvent(!allow);
    }

    /** Leaflet divIcon: a round badge with an emoji glyph and a white border. */
    public static Drawable badge(Context c, int bgColor, String glyph, int sizeDp) {
        int s = U.dp(c, sizeDp);
        int shadow = U.dp(c, 4);
        Bitmap b = Bitmap.createBitmap(s + shadow * 2, s + shadow * 2, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float cx = b.getWidth() / 2f, cy = b.getHeight() / 2f, r = s / 2f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x40000000);
        cv.drawCircle(cx, cy + U.dp(c, 2), r, p);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(cx, cy, r, p);
        p.setColor(bgColor);
        cv.drawCircle(cx, cy, r - U.dp(c, 3), p);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(s * 0.45f);
        t.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = t.getFontMetrics();
        cv.drawText(glyph, cx, cy - (fm.ascent + fm.descent) / 2, t);
        return new BitmapDrawable(c.getResources(), b);
    }

    /** Classic drop pin (36×48) for choosing a delivery spot; anchor at its tip. */
    public static Drawable dropPin(Context c, int widthDp, int heightDp, boolean dot) {
        int w = U.dp(c, widthDp), h = U.dp(c, heightDp);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float sx = w / 36f, sy = h / 48f;
        Path path = new Path();
        path.moveTo(18 * sx, 0);
        path.cubicTo(8 * sx, 0, 0, 8 * sy, 0, 18 * sy);
        path.cubicTo(0, 31 * sy, 18 * sx, 48 * sy, 18 * sx, 48 * sy);
        path.cubicTo(18 * sx, 48 * sy, 36 * sx, 31 * sy, 36 * sx, 18 * sy);
        path.cubicTo(36 * sx, 8 * sy, 28 * sx, 0, 18 * sx, 0);
        path.close();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(U.ORANGE);
        cv.drawPath(path, p);
        p.setColor(0xFFFFFFFF);
        cv.drawCircle(18 * sx, 18 * sy, (dot ? 8 : 7) * sx, p);
        if (dot) {
            p.setColor(U.ORANGE);
            cv.drawCircle(18 * sx, 18 * sy, 3.5f * sx, p);
        }
        return new BitmapDrawable(c.getResources(), b);
    }
}
