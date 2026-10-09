package com.foodgo.nativeapp.ui;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.foodgo.nativeapp.state.AppState;

/** window.confirm() */
public final class Dialogs {
    private Dialogs() {}

    public static void confirm(Activity a, String message, Runnable onOk) {
        if (!AppState.isAlive(a)) return;
        new AlertDialog.Builder(a)
                .setMessage(message)
                .setPositiveButton("OK", (d, w) -> onOk.run())
                .setNegativeButton("Hủy", null)
                .show();
    }

    /** Single choice from a list (the native counterpart of a <select>). */
    public static void choose(Activity a, String title, String[] labels, int selected, java.util.function.IntConsumer onPick) {
        if (!AppState.isAlive(a)) return;
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        if (title != null) b.setTitle(title);
        b.setSingleChoiceItems(labels, selected, (d, which) -> { d.dismiss(); onPick.accept(which); });
        b.show();
    }
}
