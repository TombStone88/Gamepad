package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Read-only horizontal spirit level bar matching the cockpit telemetry style in wheel.png & gyro.png.
 * Shows current steering value (-1 left … +1 right) with center zero notch and glowing cursor.
 */
public class SteerBarView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerNotchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint beadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final float density;
    private float value = 0f;

    public SteerBarView(Context context) {
        this(context, null);
    }

    public SteerBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SteerBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        trackPaint.setColor(0xFF181C26);
        trackPaint.setStyle(Paint.Style.FILL);

        centerNotchPaint.setColor(0xFF384358);
        centerNotchPaint.setStyle(Paint.Style.STROKE);
        centerNotchPaint.setStrokeWidth(1.5f * density);

        tickPaint.setColor(0xFF2E3747);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(1f * density);

        borderPaint.setColor(0xFFD3C5AB);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.2f * density);

        glowPaint.setStyle(Paint.Style.FILL);
        beadPaint.setStyle(Paint.Style.FILL);
    }

    public void setValue(float v) {
        v = Math.max(-1f, Math.min(1f, v));
        if (Math.abs(v - value) < 0.005f) return;
        value = v;
        invalidate();
    }

    public float getValue() {
        return value;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float trackH = 8f * density;
        float cy = h / 2f;
        float pad = 12f * density;
        trackRect.set(pad, cy - trackH / 2f, w - pad, cy + trackH / 2f);

        // Dark track with tactile bezel border
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint);
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, borderPaint);

        float cx = w / 2f;
        float travel = (w - pad * 2f) / 2f;

        // Tick marks (-50%, 0, +50%)
        canvas.drawLine(cx - travel * 0.5f, cy - 6f * density, cx - travel * 0.5f, cy + 6f * density, tickPaint);
        canvas.drawLine(cx + travel * 0.5f, cy - 6f * density, cx + travel * 0.5f, cy + 6f * density, tickPaint);
        canvas.drawLine(cx, cy - 8f * density, cx, cy + 8f * density, centerNotchPaint);

        // Active glowing cursor
        float cursorX = cx + value * travel;

        // Glow
        glowPaint.setColor(0x448B93FF);
        canvas.drawCircle(cursorX, cy, 9f * density, glowPaint);

        // Core bead
        beadPaint.setColor(0xFF00E5FF);
        canvas.drawCircle(cursorX, cy, 5.5f * density, beadPaint);
        beadPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cursorX, cy, 3f * density, beadPaint);
    }
}
