package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * Round CarX-style action button with a drawn icon (handbrake, nitro, horn, camera, lights).
 * Hold mode: pressed while the finger is down. Latch mode: tap on / tap off.
 */
public class RacingButtonView extends View {

    public static final int ICON_HANDBRAKE = 0;
    public static final int ICON_NITRO = 1;
    public static final int ICON_HORN = 2;
    public static final int ICON_CAMERA = 3;
    public static final int ICON_LIGHTS = 4;

    public interface Listener {
        void onChange(boolean active);
    }

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final int accent;

    private int icon = ICON_HANDBRAKE;
    private boolean toggleMode = false;
    private boolean active = false;
    private Listener listener;

    public RacingButtonView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        accent = ContextCompat.getColor(context, R.color.racing_accent);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(2f * density);

        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(2.2f * density);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);

        solidPaint.setStyle(Paint.Style.FILL_AND_STROKE);
        solidPaint.setStrokeWidth(1.2f * density);
        solidPaint.setStrokeJoin(Paint.Join.ROUND);

        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setToggleMode(boolean toggle) {
        this.toggleMode = toggle;
        reset();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Release without notifying (caller resets GamepadState itself). */
    public void reset() {
        if (active) {
            active = false;
            invalidate();
        }
    }

    private void setActive(boolean a) {
        if (a == active) return;
        active = a;
        invalidate();
        if (listener != null) listener.onChange(active);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float R = Math.min(getWidth(), getHeight()) / 2f - 2f * density;
        if (R <= 0f) return;

        fillPaint.setColor(active ? ((accent & 0x00FFFFFF) | 0x77000000) : 0xB0121620);
        canvas.drawCircle(cx, cy, R, fillPaint);
        ringPaint.setColor(active ? accent : 0x88FFFFFF);
        canvas.drawCircle(cx, cy, R, ringPaint);

        int ic = active ? 0xFFFFFFFF : 0xFFD5DBE5;
        strokePaint.setColor(ic);
        solidPaint.setColor(ic);
        float u = R * 0.52f;

        switch (icon) {
            case ICON_HANDBRAKE: {
                // (P) parking-brake symbol
                canvas.drawCircle(cx, cy, u * 0.78f, strokePaint);
                rect.set(cx - u * 1.2f, cy - u * 1.2f, cx + u * 1.2f, cy + u * 1.2f);
                canvas.drawArc(rect, 135f, 90f, false, strokePaint);
                canvas.drawArc(rect, -45f, 90f, false, strokePaint);
                textPaint.setColor(ic);
                textPaint.setTextSize(u * 1.05f);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                canvas.drawText("P", cx, cy - (fm.ascent + fm.descent) / 2f, textPaint);
                break;
            }
            case ICON_NITRO: {
                path.reset();
                path.moveTo(cx + 0.25f * u, cy - 1.0f * u);
                path.lineTo(cx - 0.6f * u, cy + 0.15f * u);
                path.lineTo(cx - 0.05f * u, cy + 0.15f * u);
                path.lineTo(cx - 0.3f * u, cy + 1.0f * u);
                path.lineTo(cx + 0.6f * u, cy - 0.2f * u);
                path.lineTo(cx + 0.05f * u, cy - 0.2f * u);
                path.close();
                canvas.drawPath(path, solidPaint);
                break;
            }
            case ICON_HORN: {
                path.reset();
                path.moveTo(cx - 0.95f * u, cy - 0.35f * u);
                path.lineTo(cx - 0.5f * u, cy - 0.35f * u);
                path.lineTo(cx - 0.05f * u, cy - 0.75f * u);
                path.lineTo(cx - 0.05f * u, cy + 0.75f * u);
                path.lineTo(cx - 0.5f * u, cy + 0.35f * u);
                path.lineTo(cx - 0.95f * u, cy + 0.35f * u);
                path.close();
                canvas.drawPath(path, solidPaint);
                rect.set(cx - 0.05f * u - 0.5f * u, cy - 0.5f * u, cx - 0.05f * u + 0.5f * u, cy + 0.5f * u);
                canvas.drawArc(rect, -45f, 90f, false, strokePaint);
                rect.set(cx - 0.05f * u - 0.9f * u, cy - 0.9f * u, cx - 0.05f * u + 0.9f * u, cy + 0.9f * u);
                canvas.drawArc(rect, -45f, 90f, false, strokePaint);
                break;
            }
            case ICON_CAMERA: {
                rect.set(cx - 0.95f * u, cy - 0.5f * u, cx + 0.95f * u, cy + 0.7f * u);
                canvas.drawRoundRect(rect, 0.2f * u, 0.2f * u, strokePaint);
                path.reset();
                path.moveTo(cx - 0.4f * u, cy - 0.5f * u);
                path.lineTo(cx - 0.25f * u, cy - 0.8f * u);
                path.lineTo(cx + 0.25f * u, cy - 0.8f * u);
                path.lineTo(cx + 0.4f * u, cy - 0.5f * u);
                canvas.drawPath(path, strokePaint);
                canvas.drawCircle(cx, cy + 0.1f * u, 0.38f * u, strokePaint);
                break;
            }
            case ICON_LIGHTS: {
                path.reset();
                path.moveTo(cx + 0.05f * u, cy - 0.65f * u);
                path.cubicTo(cx - 0.95f * u, cy - 0.65f * u, cx - 0.95f * u, cy + 0.65f * u,
                        cx + 0.05f * u, cy + 0.65f * u);
                path.close();
                canvas.drawPath(path, active ? solidPaint : strokePaint);
                canvas.drawLine(cx + 0.3f * u, cy - 0.45f * u, cx + 0.95f * u, cy - 0.45f * u, strokePaint);
                canvas.drawLine(cx + 0.3f * u, cy, cx + 0.95f * u, cy, strokePaint);
                canvas.drawLine(cx + 0.3f * u, cy + 0.45f * u, cx + 0.95f * u, cy + 0.45f * u, strokePaint);
                break;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                setActive(toggleMode ? !active : true);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!toggleMode) setActive(false);
                return true;
        }
        return true;
    }
}
