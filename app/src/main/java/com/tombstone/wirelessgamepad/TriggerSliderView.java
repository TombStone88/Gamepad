package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * A vertical analog trigger. Drag anywhere on it: top = fully pressed (1.0),
 * bottom = released (0.0). Releasing touch snaps back to 0, like a real trigger.
 */
public class TriggerSliderView extends View {

    public interface Listener {
        void onChange(float value);
    }

    private Paint bgPaint;
    private Paint fillPaint;
    private float value = 0f;
    private Listener listener;

    public TriggerSliderView(Context context) {
        this(context, null);
    }

    public TriggerSliderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TriggerSliderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        bgPaint = new Paint();
        bgPaint.setColor(Color.parseColor("#33FFFFFF"));
        bgPaint.setAntiAlias(true);

        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#CCFFFFFF"));
        fillPaint.setAntiAlias(true);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRoundRect(0, 0, w, h, 20, 20, bgPaint);
        float fillTop = h * (1 - value);
        canvas.drawRoundRect(0, fillTop, w, h, 20, 20, fillPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                float h = getHeight();
                float y = Math.max(0, Math.min(h, event.getY()));
                value = 1f - (y / h);
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
