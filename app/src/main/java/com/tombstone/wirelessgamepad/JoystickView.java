package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Analog joystick styled to match the retro arcade aesthetic in on-screen.png:
 * - Concentric ring dish base in warm beige/sand
 * - Tactile cream thumb knob with concave center depression and accent ring
 * Reports normalized X/Y in [-1, 1], with Y flipped so "up" = +1.
 */
public class JoystickView extends View {

    public interface Listener {
        void onMove(float x, float y);
    }

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseOuterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint baseRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private float baseRadius, knobRadius;
    private float centerX, centerY;
    private float knobX, knobY;
    private Listener listener;

    public JoystickView(Context context) {
        this(context, null);
    }

    public JoystickView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JoystickView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getContext().getResources().getDisplayMetrics().density;

        shadowPaint.setColor(0x33000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        baseOuterPaint.setColor(0xFFD8CCBA);
        baseOuterPaint.setStyle(Paint.Style.FILL);

        baseInnerPaint.setColor(0xFFEDE4D5);
        baseInnerPaint.setStyle(Paint.Style.FILL);

        baseRingPaint.setColor(0xFFC7BBAA);
        baseRingPaint.setStyle(Paint.Style.STROKE);
        baseRingPaint.setStrokeWidth(1.5f * density);

        knobShadowPaint.setColor(0x44000000);
        knobShadowPaint.setStyle(Paint.Style.FILL);

        knobPaint.setColor(0xFFFBF5EB);
        knobPaint.setStyle(Paint.Style.FILL);

        knobRingPaint.setColor(0x44D97706);
        knobRingPaint.setStyle(Paint.Style.STROKE);
        knobRingPaint.setStrokeWidth(1.2f * density);

        knobCenterPaint.setColor(0xFFB5A895);
        knobCenterPaint.setStyle(Paint.Style.FILL);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f - 4f * density;
        knobRadius = baseRadius / 2.3f;
        knobX = centerX;
        knobY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Outer shadow
        canvas.drawCircle(centerX, centerY + 3f * density, baseRadius, shadowPaint);

        // Concentric Base Dish
        canvas.drawCircle(centerX, centerY, baseRadius, baseOuterPaint);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.88f, baseInnerPaint);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.72f, baseRingPaint);
        canvas.drawCircle(centerX, centerY, baseRadius * 0.50f, baseRingPaint);

        // Knob shadow
        canvas.drawCircle(knobX, knobY + 3f * density, knobRadius, knobShadowPaint);

        // Knob Head
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);

        // Accent Ring on Knob
        canvas.drawCircle(knobX, knobY, knobRadius * 0.70f, knobRingPaint);

        // Concave Center Indentation
        canvas.drawCircle(knobX, knobY, knobRadius * 0.32f, baseInnerPaint);
        canvas.drawCircle(knobX, knobY, 3.5f * density, knobCenterPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                handleTouch(event.getX(), event.getY());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                knobX = centerX;
                knobY = centerY;
                notifyListener(0f, 0f);
                invalidate();
                break;
        }
        return true;
    }

    private void handleTouch(float touchX, float touchY) {
        float dx = touchX - centerX;
        float dy = touchY - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float maxDistance = baseRadius - knobRadius;

        if (distance > maxDistance) {
            float ratio = maxDistance / distance;
            dx *= ratio;
            dy *= ratio;
            distance = maxDistance;
        }

        knobX = centerX + dx;
        knobY = centerY + dy;

        float normX = maxDistance == 0 ? 0 : dx / maxDistance;
        // Flip Y: screen Y grows downward, gamepad "up" should be positive
        float normY = maxDistance == 0 ? 0 : -dy / maxDistance;

        notifyListener(normX, normY);
        invalidate();
    }

    private void notifyListener(float x, float y) {
        if (listener != null) listener.onMove(x, y);
    }
}
