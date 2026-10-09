package com.foodgo.nativeapp.screens;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.BounceInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.foodgo.nativeapp.MainActivity;
import com.foodgo.nativeapp.R;
import com.foodgo.nativeapp.state.AppState;
import com.foodgo.nativeapp.state.LocationState;
import com.foodgo.nativeapp.ui.MapKit;
import com.foodgo.nativeapp.ui.Overlay;
import com.foodgo.nativeapp.ui.PButton;
import com.foodgo.nativeapp.ui.U;
import com.foodgo.nativeapp.util.Geo;

import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * components/location-picker.tsx: full-screen "định vị" map, ShopeeFood style. Opens right after sign-in
 * (and from the "Giao đến" chip). The pin bobs over radar rings while locating, the map flies to the customer
 * and the pin drops in; dragging the map lifts the pin and the address follows wherever it is dropped.
 */
public class LocationPicker implements LocationState.Listener {
    private final MainActivity act;
    private final Overlay overlay;
    private final MapView map;
    private final LinearLayout sheet;
    private final View pinBox, shadow, locateBtn;
    private final ImageView locateIcon;
    private final List<View> rings = new ArrayList<>();
    private final List<ValueAnimator> ringAnims = new ArrayList<>();
    private ObjectAnimator bob;
    private Polygon accuracyCircle;

    private Geo.Place draft;
    private Geo.Place shownPlace;
    private boolean dragging, resolving;
    private int request;
    private String phase = "";
    private String targetKey = "";

    // touch → drag detection
    private boolean userMove, fingerDown;
    private float downX, downY;
    private long lastMapMove;
    private final Runnable settleCheck = new Runnable() {
        @Override public void run() {
            if (fingerDown) return;
            if (System.currentTimeMillis() - lastMapMove < 250) { map.postDelayed(this, 120); return; }
            if (!userMove) return;
            GeoPoint c = (GeoPoint) map.getMapCenter();
            onSettle(new Geo.LatLng(c.getLatitude(), c.getLongitude()));
        }
    };

    public LocationPicker(MainActivity act) {
        this.act = act;
        FrameLayout screen = new FrameLayout(act);
        screen.setBackgroundColor(U.BG);
        LinearLayout column = U.col(act);
        screen.addView(column, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));

        FrameLayout mapArea = new FrameLayout(act);
        map = MapKit.newMap(act, false);
        map.setOnTouchListener(null);
        map.getController().setZoom(13.0);
        map.getController().setCenter(new GeoPoint(Geo.DEFAULT_CENTER.lat, Geo.DEFAULT_CENTER.lng));
        mapArea.addView(map, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        TextView attr = U.text(act, "© Esri, OpenStreetMap", 9, U.MUTED);
        attr.setBackgroundColor(0xB3FFFFFF);
        U.pad(attr, 4, 1);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(U.WRAP, U.WRAP, Gravity.BOTTOM | Gravity.START);
        ap.bottomMargin = U.dp(act, 28);
        mapArea.addView(attr, ap);

        // centre pin: its tip marks the map centre
        FrameLayout pinLayer = new FrameLayout(act);
        for (int i = 0; i < 3; i++) {
            View ring = new View(act);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(0x26FF5B35);
            d.setStroke(U.dp(act, 2), U.ORANGE);
            ring.setBackground(d);
            ring.setVisibility(View.GONE);
            pinLayer.addView(ring, new FrameLayout.LayoutParams(U.dp(act, 224), U.dp(act, 224), Gravity.CENTER));
            rings.add(ring);
        }
        shadow = new View(act);
        GradientDrawable sh = new GradientDrawable();
        sh.setShape(GradientDrawable.OVAL);
        sh.setColor(0x40000000);
        shadow.setBackground(sh);
        pinLayer.addView(shadow, new FrameLayout.LayoutParams(U.dp(act, 20), U.dp(act, 8), Gravity.CENTER));
        ImageView pin = new ImageView(act);
        pin.setImageDrawable(MapKit.dropPin(act, 48, 62, true));
        pin.setElevation(U.dp(act, 4));
        pin.setTranslationY(-U.dp(act, 31));
        pinBox = pin;
        pinLayer.addView(pin, new FrameLayout.LayoutParams(U.dp(act, 48), U.dp(act, 62), Gravity.CENTER));
        mapArea.addView(pinLayer, new FrameLayout.LayoutParams(U.MATCH, U.MATCH));
        pinLayer.setClickable(false);

        // top bar
        LinearLayout top = U.row(act);
        top.setGravity(Gravity.TOP);
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xE6FFFFFF, 0x00FFFFFF}));
        U.pad(top, 16, 16, 16, 40);
        LinearLayout greet = U.col(act);
        U.pad(greet, 16, 10);
        U.bg(greet, U.WHITE, 16);
        greet.setElevation(U.dp(act, 4));
        String full = AppState.get().profile != null ? AppState.get().profile.full_name : null;
        String name = null;
        if (full != null && !full.trim().isEmpty()) { String[] w = full.trim().split("\\s+"); name = w[w.length - 1]; }
        greet.addView(U.text(act, name != null ? "Chào " + name + "!" : "Chào bạn!", 12, U.SUBTLE));
        greet.addView(U.text(act, "Bạn muốn giao đồ ăn tới đâu?", 14, U.INK, U.XBOLD));
        top.addView(greet, U.lp(U.WRAP, U.WRAP));
        U.addFlex(top, new View(act), 0);
        FrameLayout close = new FrameLayout(act);
        U.pressable(close, U.WHITE, 999);
        close.setElevation(U.dp(act, 4));
        close.addView(U.icon(act, R.drawable.ic_close, 20, U.INK), new FrameLayout.LayoutParams(U.dp(act, 20), U.dp(act, 20), Gravity.CENTER));
        close.setContentDescription("Để sau");
        close.setOnClickListener(v -> close());
        top.addView(close, new LinearLayout.LayoutParams(U.dp(act, 44), U.dp(act, 44)));
        mapArea.addView(top, new FrameLayout.LayoutParams(U.MATCH, U.WRAP, Gravity.TOP));

        FrameLayout locate = new FrameLayout(act);
        U.pressable(locate, U.WHITE, 999);
        locate.setElevation(U.dp(act, 6));
        locateIcon = U.icon(act, R.drawable.ic_locate, 20, U.ORANGE);
        locate.addView(locateIcon, new FrameLayout.LayoutParams(U.dp(act, 20), U.dp(act, 20), Gravity.CENTER));
        locate.setContentDescription("Định vị lại");
        locate.setOnClickListener(v -> LocationState.get().locate(null));
        locateBtn = locate;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(U.dp(act, 48), U.dp(act, 48), Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, U.dp(act, 16), U.dp(act, 56));
        mapArea.addView(locate, lp);
        column.addView(mapArea, new LinearLayout.LayoutParams(U.MATCH, 0, 1));

        // bottom sheet
        sheet = U.col(act);
        GradientDrawable sb = new GradientDrawable();
        sb.setColor(U.WHITE);
        float r = U.dp(act, 24);
        sb.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        sheet.setBackground(sb);
        sheet.setElevation(U.dp(act, 12));
        U.pad(sheet, 20, 20, 20, 20);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(U.MATCH, U.WRAP);
        sp.topMargin = -U.dp(act, 24);
        column.addView(sheet, sp);

        overlay = new Overlay(act, screen, Overlay.FULL, false);
        overlay.onDismiss(() -> {
            LocationState.get().removeListener(this);
            if (LocationState.get().pickerOpen) LocationState.get().setPickerOpen(false);
        });

        setupDragDetection();
    }

    public void show() {
        LocationState l = LocationState.get();
        // each opening starts from the saved position; without one yet, start locating
        draft = l.place;
        shownPlace = l.place;
        LocationState.get().addListener(this);
        overlay.show();
        if (l.place == null && !"locating".equals(l.status)) l.locate(null);
        onLocation();
        if (l.place != null) dropIn();
    }

    public boolean isShowing() { return overlay.isShowing(); }

    public void dismiss() { overlay.dismiss(); }

    private void close() { LocationState.get().setPickerOpen(false); }

    private void setupDragDetection() {
        int slop = ViewConfiguration.get(act).getScaledTouchSlop();
        map.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    fingerDown = true; downX = e.getX(); downY = e.getY();
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    if (!dragging) { userMove = true; onDragStart(); }
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragging && (Math.abs(e.getX() - downX) > slop || Math.abs(e.getY() - downY) > slop)) { userMove = true; onDragStart(); }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    fingerDown = false;
                    if (dragging) { map.removeCallbacks(settleCheck); map.postDelayed(settleCheck, 150); }
                    break;
            }
            return false;
        });
        map.addMapListener(new MapListener() {
            @Override public boolean onScroll(ScrollEvent event) { lastMapMove = System.currentTimeMillis(); return false; }
            @Override public boolean onZoom(ZoomEvent event) { lastMapMove = System.currentTimeMillis(); return false; }
        });
    }

    private void onDragStart() {
        request++;
        dragging = true;
        resolving = false;
        render();
    }

    private void onSettle(Geo.LatLng c) {
        dragging = false;
        resolving = true;
        userMove = false;
        render();
        dropIn();
        int id = ++request;
        Geo.reverseGeocode(c.lat, c.lng, (address, err) -> {
            if (id != request) return; // the pin moved again meanwhile
            draft = new Geo.Place(c.lat, c.lng, address != null ? address : Geo.coords(c.lat, c.lng), 0);
            resolving = false;
            render();
        });
    }

    @Override
    public void onLocation() {
        LocationState l = LocationState.get();
        // a new device fix replaces any hand-placed pin
        if (l.place != null && l.place != shownPlace) {
            shownPlace = l.place;
            draft = l.place;
            dropIn();
        }
        String key = l.place != null ? l.place.lat + "," + l.place.lng : "";
        if (!key.equals(targetKey) && l.place != null) {
            targetKey = key;
            userMove = false;
            map.getController().animateTo(new GeoPoint(l.place.lat, l.place.lng), 17.0, 1800L);
        }
        render();
    }

    private void dropIn() {
        pinBox.animate().cancel();
        pinBox.setTranslationY(-U.dp(act, 31) - U.dp(act, 40));
        pinBox.setAlpha(0f);
        pinBox.animate().translationY(-U.dp(act, 31)).alpha(1f).setDuration(600).setInterpolator(new BounceInterpolator()).start();
    }

    private void render() {
        LocationState l = LocationState.get();
        boolean locating = "locating".equals(l.status);
        String next = locating && draft == null ? "locating" : dragging ? "lifted" : "rest";
        if (!next.equals(phase)) {
            phase = next;
            animatePhase();
        }
        locateBtn.setEnabled(!locating);
        locateBtn.setAlpha(locating ? 0.7f : 1f);
        if (locating) {
            if (locateIcon.getAnimation() == null) {
                android.view.animation.RotateAnimation rot = new android.view.animation.RotateAnimation(0, 360, android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f, android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f);
                rot.setDuration(1000);
                rot.setRepeatCount(android.view.animation.Animation.INFINITE);
                rot.setInterpolator(new android.view.animation.LinearInterpolator());
                locateIcon.startAnimation(rot);
            }
        } else locateIcon.clearAnimation();

        // accuracy circle while the pin is still the device's own fix
        if (accuracyCircle != null) { map.getOverlays().remove(accuracyCircle); accuracyCircle = null; }
        if (l.place != null && draft == l.place && l.place.accuracy > 20) {
            accuracyCircle = new Polygon(map);
            accuracyCircle.setPoints(Polygon.pointsAsCircle(new GeoPoint(l.place.lat, l.place.lng), Math.min(l.place.accuracy, 2000)));
            accuracyCircle.getFillPaint().setColor(0x14FF5B35);
            accuracyCircle.getOutlinePaint().setColor(U.ORANGE);
            accuracyCircle.getOutlinePaint().setStrokeWidth(U.dp(act, 1));
            accuracyCircle.setInfoWindow(null);
            map.getOverlays().add(0, accuracyCircle);
        }
        map.invalidate();

        sheet.removeAllViews();
        View handle = new View(act);
        U.bg(handle, U.BORDER, 999);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(U.dp(act, 40), U.dp(act, 6));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = U.dp(act, 16);
        sheet.addView(handle, hp);
        if (phase.equals("locating")) {
            LinearLayout row = U.row(act);
            U.pad(row, 0, 8, 0, 8);
            row.addView(U.progress(act, 24, U.ORANGE));
            LinearLayout t = U.col(act);
            t.addView(U.text(act, "Đang xác định vị trí của bạn...", 16, U.INK, U.XBOLD));
            U.add(t, U.text(act, "Bật GPS để tài xế giao đúng chỗ hơn", 14, U.MUTED), 2);
            U.addFlex(row, t, 12);
            sheet.addView(row);
        } else if (draft == null && "error".equals(l.status)) {
            sheet.addView(U.text(act, "Không lấy được vị trí", 16, U.WARN, U.XBOLD));
            U.add(sheet, U.text(act, l.error, 14, U.MUTED), 4);
            U.add(sheet, U.text(act, "Hoặc kéo bản đồ để đặt ghim tại nơi bạn nhận hàng.", 14, U.MUTED), 4);
            LinearLayout btns = U.row(act);
            PButton later = new PButton(act, "Để sau", PButton.SOFT);
            later.label().setTextSize(14);
            later.onClick(this::close);
            PButton retry = new PButton(act, "Thử lại", PButton.PRIMARY);
            retry.label().setTextSize(14);
            retry.onClick(() -> LocationState.get().locate(null));
            btns.addView(later, new LinearLayout.LayoutParams(0, U.dp(act, 48), 1));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, U.dp(act, 48), 1);
            rp.leftMargin = U.dp(act, 12);
            btns.addView(retry, rp);
            U.add(sheet, btns, 16);
        } else {
            LinearLayout row = U.row(act);
            row.setGravity(Gravity.TOP);
            FrameLayout ic = new FrameLayout(act);
            U.bg(ic, U.PEACH, 999);
            ic.addView(U.icon(act, R.drawable.ic_pin, 20, U.ORANGE), new FrameLayout.LayoutParams(U.dp(act, 20), U.dp(act, 20), Gravity.CENTER));
            row.addView(ic, new LinearLayout.LayoutParams(U.dp(act, 40), U.dp(act, 40)));
            LinearLayout t = U.col(act);
            t.addView(U.text(act, "Giao đến", 12, U.SUBTLE, U.SEMI));
            if (dragging) t.addView(U.text(act, "Thả ghim tại nơi bạn nhận hàng", 16, U.MUTED, U.XBOLD));
            else if (resolving) {
                LinearLayout rr = U.row(act);
                rr.addView(U.progress(act, 16, U.MUTED));
                U.add(rr, U.text(act, "Đang lấy địa chỉ...", 16, U.MUTED, U.XBOLD), 8, U.WRAP, U.WRAP);
                t.addView(rr);
            } else t.addView(U.ellipsize(U.text(act, draft != null ? draft.address : "", 16, U.INK, U.XBOLD), 2));
            boolean rough = !dragging && !resolving && draft != null && draft.accuracy > Geo.ROUGH_ACCURACY_M;
            if (rough) U.add(t, U.text(act, "Vị trí chỉ là ước tính. Kéo bản đồ để đặt ghim đúng chỗ nhé.", 12, U.WARN), 4);
            U.addFlex(row, t, 12);
            sheet.addView(row);
            PButton confirm = new PButton(act, "Xác nhận vị trí", PButton.PRIMARY);
            confirm.label().setTextSize(14);
            confirm.setEnabled(draft != null && !dragging && !resolving);
            confirm.onClick(() -> {
                if (draft != null && draft != LocationState.get().place) LocationState.get().choose(draft);
                close();
            });
            U.add(sheet, confirm, 20, U.MATCH, U.dp(act, 48));
        }
    }

    private void animatePhase() {
        for (ValueAnimator a : ringAnims) a.cancel();
        ringAnims.clear();
        if (bob != null) { bob.cancel(); bob = null; }
        float base = -U.dp(act, 31);
        boolean locating = phase.equals("locating");
        for (int i = 0; i < rings.size(); i++) {
            View ring = rings.get(i);
            ring.setVisibility(locating ? View.VISIBLE : View.GONE);
            if (!locating) continue;
            ring.setScaleX(0f); ring.setScaleY(0f); ring.setAlpha(0f);
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(1800);
            va.setStartDelay(i * 600L);
            va.setRepeatCount(ValueAnimator.INFINITE);
            va.addUpdateListener(an -> {
                float f = (float) an.getAnimatedValue();
                ring.setScaleX(f); ring.setScaleY(f); ring.setAlpha(1f - f);
            });
            va.start();
            ringAnims.add(va);
        }
        if (locating) {
            pinBox.animate().cancel();
            pinBox.setAlpha(1f);
            bob = ObjectAnimator.ofFloat(pinBox, "translationY", base, base - U.dp(act, 10));
            bob.setDuration(600);
            bob.setRepeatMode(ValueAnimator.REVERSE);
            bob.setRepeatCount(ValueAnimator.INFINITE);
            bob.start();
        } else if (phase.equals("lifted")) {
            pinBox.animate().cancel();
            pinBox.setAlpha(1f);
            pinBox.animate().translationY(base - U.dp(act, 16)).setDuration(200).start();
        } else {
            pinBox.animate().translationY(base).setDuration(200).start();
        }
        // ground shadow: shrinks while the pin is in the air
        AnimatorSet s = new AnimatorSet();
        float scale = phase.equals("rest") ? 1f : 0.6f;
        s.playTogether(ObjectAnimator.ofFloat(shadow, "scaleX", scale), ObjectAnimator.ofFloat(shadow, "alpha", phase.equals("rest") ? 1f : 0.6f));
        s.setDuration(200);
        s.start();
    }
}
