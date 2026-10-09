package com.foodgo.nativeapp.ui;

import android.app.Activity;
import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;

import com.foodgo.nativeapp.R;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** <select className="h-11 rounded-xl border bg-white px-3 text-sm"> */
public class Select extends TextView {
    private final List<String> values = new ArrayList<>();
    private final List<String> labels = new ArrayList<>();
    private String value;
    private Consumer<String> onChange;
    private String title;

    public Select(Context c) {
        super(c);
        setTextSize(14);
        setTextColor(U.INK);
        setGravity(Gravity.CENTER_VERTICAL);
        setSingleLine(true);
        setEllipsize(android.text.TextUtils.TruncateAt.END);
        U.pad(this, 12, 0, 8, 0);
        setMinHeight(U.dp(c, 44));
        U.pressable(this, U.stroke(U.WHITE, U.dp(c, 12), U.BORDER, U.dp(c, 1)));
        android.graphics.drawable.Drawable d = U.drawable(c, R.drawable.ic_chevron_down, U.MUTED);
        d.setBounds(0, 0, U.dp(c, 18), U.dp(c, 18));
        setCompoundDrawablesRelative(null, null, d, null);
        setCompoundDrawablePadding(U.dp(c, 4));
        setOnClickListener(v -> open());
    }

    public Select title(String t) { title = t; return this; }

    public Select option(String v, String label) { values.add(v); labels.add(label); if (value == null) set(v); return this; }

    public Select clearOptions() { values.clear(); labels.clear(); value = null; setText(""); return this; }

    public Select onChange(Consumer<String> c) { onChange = c; return this; }

    public String value() { return value; }

    public Select set(String v) {
        value = v;
        int i = values.indexOf(v);
        setText(i >= 0 ? labels.get(i) : "");
        return this;
    }

    private void open() {
        Activity a = U2.activity(getContext());
        Dialogs.choose(a, title, labels.toArray(new String[0]), values.indexOf(value), which -> {
            String v = values.get(which);
            boolean changed = !v.equals(value);
            set(v);
            if (changed && onChange != null) onChange.accept(v);
        });
    }
}
