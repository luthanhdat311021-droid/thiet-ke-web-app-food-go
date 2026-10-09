package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * `flex max-h-[90vh] flex-col`: a scrolling body above a fixed footer; the whole column grows with its
 * content until it reaches `maxFraction` of the screen height, then the body scrolls.
 */
public class CappedColumn extends ViewGroup {
    private final View body;
    private final View footer;
    private final float maxFraction;

    public CappedColumn(Context c, View body, View footer, float maxFraction) {
        super(c);
        this.body = body;
        this.footer = footer;
        this.maxFraction = maxFraction;
        addView(body);
        if (footer != null) addView(footer);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int max = (int) (getResources().getDisplayMetrics().heightPixels * maxFraction);
        if (MeasureSpec.getMode(heightSpec) != MeasureSpec.UNSPECIFIED) max = Math.min(max, MeasureSpec.getSize(heightSpec));
        int exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int footerH = 0;
        if (footer != null) {
            footer.measure(exactW, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            footerH = footer.getMeasuredHeight();
        }
        body.measure(exactW, MeasureSpec.makeMeasureSpec(Math.max(0, max - footerH), MeasureSpec.AT_MOST));
        setMeasuredDimension(w, body.getMeasuredHeight() + footerH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int bh = body.getMeasuredHeight();
        body.layout(0, 0, r - l, bh);
        if (footer != null) footer.layout(0, bh, r - l, bh + footer.getMeasuredHeight());
    }
}
