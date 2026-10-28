package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Vertical analog trigger styled to match the retro arcade aesthetic in on-screen.png:
 * Tactile rounded diamond/capsule trigger with L2/R2 label, fill gauge, and pressure sensitivity.
 */
public class TriggerSliderView extends View {

    public interface Listener {
        void onChange(float value);
    }

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float density;
    private float value = 0f;
    private Listener listener;
    private boolean hardMode = false;
    private String label = "L2";

    public TriggerSliderView(Context context) {
        this(context, null);
    }

    public TriggerSliderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TriggerSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        shadowPaint.setColor(0xFFC7BBAA);
        shadowPaint.setStyle(Paint.Style.FILL);

        bgPaint.setColor(0xFFFBF4E8);
        bgPaint.setStyle(Paint.Style.FILL);

        fillPaint.setColor(0xFF4B53BB);
        fillPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFFC7BBAA);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);

        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(0xFF1F1B14);
    }

    public void setLabel(String label) {
        this.label = label;
        invalidate();
    }

    public void setHardMode(boolean hard) {
        this.hardMode = hard;
        if (value != 0f) {
            value = 0f;
            notifyListener();
            invalidate();
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = 14f * density;
        float shadowOffset = 3f * density;
        float pressOffset = value > 0f ? 2f * density : 0f;

        // Underside shadow
        canvas.drawRoundRect(0, shadowOffset, w, h + shadowOffset, r, r, shadowPaint);

        // Face
        rect.set(0, pressOffset, w, h + pressOffset);
        canvas.drawRoundRect(rect, r, r, bgPaint);

        // Active fill
        if (value > 0f) {
            canvas.save();
            canvas.clipRect(rect);
            float fillTop = rect.bottom - (rect.height() * value);
            fillPaint.setColor(0x444B53BB);
            canvas.drawRect(rect.left, fillTop, rect.right, rect.bottom, fillPaint);
            canvas.restore();
        }

        // Border
        canvas.drawRoundRect(rect, r, r, borderPaint);

        // Label
        textPaint.setTextSize(14f * density);
        textPaint.setColor(value > 0.5f ? 0xFF4B53BB : 0xFF1F1B14);
        canvas.drawText(label, w / 2f, rect.centerY() + 5f * density, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (hardMode) {
                    value = 1f;
                } else {
                    float h = getHeight();
                    float y = Math.max(0, Math.min(h, event.getY()));
                    value = 1f - (y / h);
                }
                notifyListener();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                value = 0f;
                notifyListener();
                invalidate();
                return true;
        }
        return false;
    }

    private void notifyListener() {
        if (listener != null) listener.onChange(value);
    }
}
