package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A minimal analog joystick. Drag the knob anywhere inside the base circle;
 * releasing snaps it back to center. Reports normalized X/Y in [-1, 1],
 * with Y already flipped so "up" = +1 (matches typical gamepad convention).
 */
public class JoystickView extends View {

    public interface Listener {
        void onMove(float x, float y);
    }

    private Paint basePaint, knobPaint;
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

    private Paint borderPaint;

    private void init() {
        basePaint = new Paint();
        basePaint.setColor(Color.parseColor("#55202020"));
        basePaint.setAntiAlias(true);

        borderPaint = new Paint();
        borderPaint.setColor(Color.parseColor("#55FFFFFF"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setAntiAlias(true);

        knobPaint = new Paint();
        knobPaint.setColor(Color.parseColor("#DDFFFFFF"));
        knobPaint.setAntiAlias(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        centerX = w / 2f;
        centerY = h / 2f;
        baseRadius = Math.min(w, h) / 2f;
        knobRadius = baseRadius / 2.2f;
        knobX = centerX;
        knobY = centerY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawCircle(centerX, centerY, baseRadius, basePaint);
        canvas.drawCircle(centerX, centerY, baseRadius - 1.5f, borderPaint);
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
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
