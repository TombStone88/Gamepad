package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

/**
 * A vertical racing pedal. Touch anywhere on it: a finger near the top means fully
 * pressed, near the bottom means released. Lifting the finger snaps back to 0,
 * like a spring-loaded pedal.
 */
public class PedalView extends View {

    public interface Listener {
        void onChange(float value);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private final float density;

    private String label = "";
    private float value = 0f;
    private Listener listener;

    public PedalView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        trackPaint.setColor(ContextCompat.getColor(context, R.color.racing_track));
        fillPaint.setColor(ContextCompat.getColor(context, R.color.racing_gas));

        ridgePaint.setColor(ContextCompat.getColor(context, R.color.racing_stroke));
        ridgePaint.setStrokeWidth(2f * density);
        ridgePaint.setStrokeCap(Paint.Cap.ROUND);

        borderPaint.setColor(ContextCompat.getColor(context, R.color.racing_stroke));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);

        textPaint.setColor(ContextCompat.getColor(context, R.color.racing_text));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    public void setLabelAndColor(String label, int fillColor) {
        this.label = label;
        fillPaint.setColor(fillColor);
        invalidate();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float inset = 0.75f * density;
        float radius = 18f * density;
        rect.set(inset, inset, w - inset, h - inset);

        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (value > 0f) {
            clipPath.reset();
            clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);
            canvas.drawRect(0, h * (1f - value), w, h, fillPaint);
            canvas.restore();
        }

        // Grip ridges, so it reads as a pedal
        float gap = 22f * density;
        for (float y = gap; y < h - gap / 2f; y += gap) {
            canvas.drawLine(w * 0.22f, y, w * 0.78f, y, ridgePaint);
        }

        canvas.drawRoundRect(rect, radius, radius, borderPaint);

        textPaint.setTextSize(Math.min(w * 0.2f, 18f * density));
        canvas.drawText(label, w / 2f, h - 34f * density, textPaint);
        if (value > 0f) {
            textPaint.setTextSize(Math.min(w * 0.16f, 14f * density));
            canvas.drawText(Math.round(value * 100f) + "%", w / 2f, h - 14f * density, textPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float h = getHeight();
                // Top 8% = full press, bottom 8% = released, so thumbs don't need to hit the very edge.
                float t = (event.getY() - 0.08f * h) / (0.84f * h);
                t = Math.max(0f, Math.min(1f, t));
                setValue(1f - t);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setValue(0f);
                return true;
        }
        return true;
    }

    private void setValue(float v) {
        if (v == value) return;
        value = v;
        invalidate();
        if (listener != null) listener.onChange(value);
    }
}
