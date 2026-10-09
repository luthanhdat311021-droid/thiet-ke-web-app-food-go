package com.foodgo.nativeapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Shared by every screen host: light status bar and the location-permission prompt. */
public abstract class BaseActivity extends AppCompatActivity {
    private static final int REQ_LOCATION = 41;
    private final List<Consumer<Boolean>> waitingForLocation = new ArrayList<>();

    private Consumer<android.net.Uri> imageCallback;
    private final androidx.activity.result.ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                Consumer<android.net.Uri> cb = imageCallback;
                imageCallback = null;
                if (cb != null && uri != null) cb.accept(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFFFFFFFF);
        getWindow().setNavigationBarColor(0xFFFFFFFF);
    }

    /** <input type="file" accept="image/*"> */
    public void pickImage(Consumer<android.net.Uri> cb) {
        imageCallback = cb;
        imagePicker.launch("image/*");
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Asks for location access when needed (like the WebView's geolocation prompt). */
    public void withLocationPermission(Consumer<Boolean> cb) {
        if (hasLocationPermission()) { cb.accept(true); return; }
        waitingForLocation.add(cb);
        if (waitingForLocation.size() == 1) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_LOCATION) return;
        boolean granted = hasLocationPermission();
        List<Consumer<Boolean>> list = new ArrayList<>(waitingForLocation);
        waitingForLocation.clear();
        for (Consumer<Boolean> c : list) c.accept(granted);
    }
}
