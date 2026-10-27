package com.tombstone.wirelessgamepad;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

/**
 * A touch steering wheel. Drag around its center to turn it; letting go
 * springs it back to center. Reports steering in [-1, 1] (right = positive).
 */
public class SteeringWheelView extends View {

    public interface Listener {
        void onChange(float value);
    }

    private static final float MAX_DEGREES = 120f; // wheel rotation that equals full lock

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pointer = new Path();
    private final float density;

    private float wheelAngle = 0f;
    private float startTouchAngle = 0f;
    private float startWheelAngle = 0f;
    private ValueAnimator returnAnim;
    private Listener listener;

    public SteeringWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        int wheelColor = ContextCompat.getColor(context, R.color.racing_wheel);

        ringPaint.setColor(wheelColor);
        ringPaint.setStyle(Paint.Style.STROKE);

        spokePaint.setColor(wheelColor);
        spokePaint.setStyle(Paint.Style.STROKE);
        spokePaint.setStrokeCap(Paint.Cap.ROUND);

        hubPaint.setColor(wheelColor);

        markerPaint.setColor(ContextCompat.getColor(context, R.color.racing_accent));
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeCap(Paint.Cap.ROUND);

        pointerPaint.setColor(ContextCompat.getColor(context, R.color.racing_text_dim));
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - 10f * density;
        float ringW = r * 0.17f;
        float innerR = r - ringW;

        // Fixed pointer at 12 o'clock so the center position is always visible
        pointer.reset();
        pointer.moveTo(cx - 6f * density, cy - r - 9f * density);
        pointer.lineTo(cx + 6f * density, cy - r - 9f * density);
        pointer.lineTo(cx, cy - r - 2f * density);
        pointer.close();
        canvas.drawPath(pointer, pointerPaint);

        canvas.save();
        canvas.rotate(wheelAngle, cx, cy);

        ringPaint.setStrokeWidth(ringW);
        canvas.drawCircle(cx, cy, r - ringW / 2f, ringPaint);

        spokePaint.setStrokeWidth(r * 0.13f);
        canvas.drawLine(cx, cy, cx - innerR, cy, spokePaint);
        canvas.drawLine(cx, cy, cx + innerR, cy, spokePaint);
        canvas.drawLine(cx, cy, cx, cy + innerR, spokePaint);

        canvas.drawCircle(cx, cy, r * 0.2f, hubPaint);

        markerPaint.setStrokeWidth(r * 0.09f);
        canvas.drawLine(cx, cy - r + ringW * 0.18f, cx, cy - r + ringW * 0.82f, markerPaint);

        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelReturn();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                startTouchAngle = touchAngle(event.getX(), event.getY());
                startWheelAngle = wheelAngle;
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - getWidth() / 2f;
                float dy = event.getY() - getHeight() / 2f;
                float minR = Math.min(getWidth(), getHeight()) * 0.06f;
                if (dx * dx + dy * dy < minR * minR) return true; // too close to the hub to read an angle
                float delta = wrap(touchAngle(event.getX(), event.getY()) - startTouchAngle);
                setWheelAngle(Math.max(-MAX_DEGREES, Math.min(MAX_DEGREES, startWheelAngle + delta)));
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                springBack();
                return true;
        }
        return true;
    }

    private float touchAngle(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(y - getHeight() / 2f, x - getWidth() / 2f));
    }

    private static float wrap(float a) {
        while (a > 180f) a -= 360f;
        while (a <= -180f) a += 360f;
        return a;
    }

    private void setWheelAngle(float a) {
        wheelAngle = a;
        invalidate();
        if (listener != null) listener.onChange(wheelAngle / MAX_DEGREES);
    }

    private void springBack() {
        cancelReturn();
        returnAnim = ValueAnimator.ofFloat(wheelAngle, 0f);
        returnAnim.setDuration(220);
        returnAnim.setInterpolator(new DecelerateInterpolator());
        returnAnim.addUpdateListener(a -> setWheelAngle((float) a.getAnimatedValue()));
        returnAnim.start();
    }

    private void cancelReturn() {
        if (returnAnim != null) {
            returnAnim.cancel();
            returnAnim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelReturn();
        super.onDetachedFromWindow();
    }
}
