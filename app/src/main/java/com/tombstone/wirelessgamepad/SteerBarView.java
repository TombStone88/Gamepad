package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/** Read-only horizontal bar that shows the current steering value (-1 left … +1 right). */
public class SteerBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private float value = 0f;

    public SteerBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        trackPaint.setColor(ContextCompat.getColor(context, R.color.racing_track));
        fillPaint.setColor(ContextCompat.getColor(context, R.color.racing_accent));
        fillPaint.setAlpha(120);
        tickPaint.setColor(ContextCompat.getColor(context, R.color.racing_text_dim));
        tickPaint.setStrokeWidth(2f * density);
        markerPaint.setColor(ContextCompat.getColor(context, R.color.racing_accent));
    }

    public void setValue(float v) {
        v = Math.max(-1f, Math.min(1f, v));
        if (v == value) return;
        value = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float markerR = 13f * density;
        float trackH = 10f * density;
        float cy = h / 2f;
        float x0 = markerR;
        float x1 = w - markerR;
        float cx = (x0 + x1) / 2f;
        float mx = cx + value * (x1 - x0) / 2f;

        rect.set(x0, cy - trackH / 2f, x1, cy + trackH / 2f);
        canvas.drawRoundRect(rect, trackH / 2f, trackH / 2f, trackPaint);

        rect.set(Math.min(cx, mx), cy - trackH / 2f, Math.max(cx, mx), cy + trackH / 2f);
        canvas.drawRect(rect, fillPaint);

        canvas.drawLine(cx, cy - 12f * density, cx, cy + 12f * density, tickPaint);
        canvas.drawCircle(mx, cy, markerR, markerPaint);
    }
}
