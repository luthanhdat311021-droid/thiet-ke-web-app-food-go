package com.foodgo.nativeapp.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;

/** `flex flex-wrap items-center gap-x-* gap-y-*`: children flow left to right and wrap onto new lines. */
public class FlowRow extends LinearLayout {
    private final int gapX, gapY;

    public FlowRow(Context c, int gapXDp, int gapYDp) {
        super(c);
        gapX = U.dp(c, gapXDp);
        gapY = U.dp(c, gapYDp);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxW = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE : MeasureSpec.getSize(widthSpec);
        int x = 0, y = 0, lineH = 0, widest = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            ch.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + gapX + w > maxW) { y += lineH + gapY; x = 0; lineH = 0; }
            x += (x > 0 ? gapX : 0) + w;
            lineH = Math.max(lineH, h);
            widest = Math.max(widest, x);
        }
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : Math.min(widest, maxW);
        setMeasuredDimension(width, y + lineH);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int maxW = r - l;
        int x = 0, y = 0, lineH = 0;
        int lineStart = 0;
        // first pass per line to centre items vertically within the line
        for (int i = 0; i < getChildCount(); i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0 && x + gapX + w > maxW) {
                placeLine(lineStart, i, y, lineH, maxW);
                y += lineH + gapY; x = 0; lineH = 0; lineStart = i;
            }
            x += (x > 0 ? gapX : 0) + w;
            lineH = Math.max(lineH, h);
        }
        placeLine(lineStart, getChildCount(), y, lineH, maxW);
    }

    private void placeLine(int from, int to, int y, int lineH, int maxW) {
        int x = 0;
        for (int i = from; i < to; i++) {
            View ch = getChildAt(i);
            if (ch.getVisibility() == GONE) continue;
            int w = ch.getMeasuredWidth(), h = ch.getMeasuredHeight();
            if (x > 0) x += gapX;
            int top = y + (lineH - h) / 2;
            ch.layout(x, top, x + w, top + h);
            x += w;
        }
    }
}
