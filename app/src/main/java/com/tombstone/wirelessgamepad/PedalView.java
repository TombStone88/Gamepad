package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * Enhanced realistic racing pedal matching RetroPad / PocketPad specifications:
 * - TYPE_BRAKE: Deep hydraulic red pedal, horizontal ribs, HYDRAULIC badge, PRESSURE SENSITIVE
 * - TYPE_GAS: Anodized warm metallic pedal, 4 corner screws, vertical flutes, center illuminated throttle channel
 */
public class PedalView extends View {

    public static final int TYPE_BRAKE = 0;
    public static final int TYPE_GAS = 1;

    public interface Listener {
        void onChange(float value);
    }

    private final float density;
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ridgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint screwPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF pedalRect = new RectF();
    private final RectF badgeRect = new RectF();
    private final Path clipPath = new Path();

    private int pedalType = TYPE_GAS;
    private String label = "GAS";
    private float value = 0f;
    private Listener listener;

    public PedalView(Context context) {
        this(context, null);
    }

    public PedalView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PedalView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);

        subTextPaint.setTypeface(Typeface.create("sans-serif-bold", Typeface.NORMAL));
        subTextPaint.setTextAlign(Paint.Align.CENTER);
        subTextPaint.setLetterSpacing(0.06f);

        badgePaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        badgePaint.setTextAlign(Paint.Align.CENTER);
        badgePaint.setLetterSpacing(0.08f);

        borderPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStyle(Paint.Style.STROKE);
    }

    public void setPedalType(int type) {
        this.pedalType = type;
        if (type == TYPE_BRAKE) {
            this.label = "BRAKE";
        } else {
            this.label = "GAS";
        }
        invalidate();
    }

    public void setLabelAndColor(String label, int fillColor) {
        this.label = label;
        if ("BRAKE".equalsIgnoreCase(label)) {
            setPedalType(TYPE_BRAKE);
        } else {
            setPedalType(TYPE_GAS);
        }
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padX = 4f * density;
        float padY = 4f * density;
        float radius = 18f * density;
        pedalRect.set(padX, padY, w - padX, h - padY);

        if (pedalType == TYPE_BRAKE) {
            drawBrakePedal(canvas, w, h, radius);
        } else {
            drawGasPedal(canvas, w, h, radius);
        }
    }

    private void drawBrakePedal(Canvas canvas, float w, float h, float radius) {
        // Drop shadow under pedal
        shadowPaint.setColor(0x88000000);
        canvas.drawRoundRect(pedalRect.left, pedalRect.top + 3f * density, pedalRect.right, pedalRect.bottom + 3f * density, radius, radius, shadowPaint);

        // Base red hydraulic pedal plate
        basePaint.setColor(0xFF9E1A22);
        canvas.drawRoundRect(pedalRect, radius, radius, basePaint);

        // Active pressure fill (fills from bottom to top with brighter crimson)
        if (value > 0f) {
            clipPath.reset();
            clipPath.addRoundRect(pedalRect, radius, radius, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clipPath);

            fillPaint.setColor(0xFFFF3B30);
            float fillTop = pedalRect.bottom - (pedalRect.height() * value);
            canvas.drawRect(pedalRect.left, fillTop, pedalRect.right, pedalRect.bottom, fillPaint);

            // Glowing boundary line
            highlightPaint.setColor(0xFFFF8E87);
            highlightPaint.setStrokeWidth(2.5f * density);
            canvas.drawLine(pedalRect.left, fillTop, pedalRect.right, fillTop, highlightPaint);
            canvas.restore();
        }

        // Top HYDRAULIC slot/badge
        float badgeW = 60f * density;
        float badgeH = 16f * density;
        badgeRect.set(w / 2f - badgeW / 2f, pedalRect.top + 14f * density, w / 2f + badgeW / 2f, pedalRect.top + 14f * density + badgeH);

        badgePaint.setColor(0xFF4A0B10);
        canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, badgePaint);

        // Side slots
        badgePaint.setColor(0xFF2E0509);
        canvas.drawRoundRect(pedalRect.left + 10f * density, badgeRect.top + 4f * density, pedalRect.left + 16f * density, badgeRect.top + 12f * density, 3f * density, 3f * density, badgePaint);
        canvas.drawRoundRect(pedalRect.right - 16f * density, badgeRect.top + 4f * density, pedalRect.right - 10f * density, badgeRect.top + 12f * density, 3f * density, 3f * density, badgePaint);

        badgePaint.setColor(0xFFFFA4A2);
        badgePaint.setTextSize(8f * density);
        canvas.drawText("HYDRAULIC", badgeRect.centerX(), badgeRect.centerY() + 3f * density, badgePaint);

        // 5 Horizontal ribbed grip bars
        float ribStartY = badgeRect.bottom + 18f * density;
        float ribGap = (pedalRect.height() * 0.38f) / 5f;
        float ribW = pedalRect.width() * 0.65f;
        float ribH = 6f * density;

        ridgePaint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 5; i++) {
            float ry = ribStartY + i * ribGap;
            float rx = w / 2f - ribW / 2f;
            // shadow
            ridgePaint.setColor(0xFF5A0C11);
            canvas.drawRoundRect(rx, ry + 1.5f * density, rx + ribW, ry + ribH + 1.5f * density, 3f * density, 3f * density, ridgePaint);
            // bar
            ridgePaint.setColor(0xFFBA222B);
            canvas.drawRoundRect(rx, ry, rx + ribW, ry + ribH, 3f * density, 3f * density, ridgePaint);
            // highlight line
            highlightPaint.setColor(0x44FFFFFF);
            highlightPaint.setStrokeWidth(1f * density);
            canvas.drawLine(rx + 4f * density, ry + 1f * density, rx + ribW - 4f * density, ry + 1f * density, highlightPaint);
        }

        // Outer border
        borderPaint.setColor(0xFF630F15);
        borderPaint.setStrokeWidth(1.5f * density);
        canvas.drawRoundRect(pedalRect, radius, radius, borderPaint);

        // Bottom text: BRAKE & PRESSURE SENSITIVE
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(Math.min(w * 0.22f, 18f * density));
        canvas.drawText(label, w / 2f, pedalRect.bottom - 26f * density, textPaint);

        subTextPaint.setColor(0xFFFFA4A2);
        subTextPaint.setTextSize(Math.min(w * 0.11f, 8.5f * density));
        if (value > 0f) {
            subTextPaint.setColor(0xFFFFFFFF);
            canvas.drawText(Math.round(value * 100f) + "%", w / 2f, pedalRect.bottom - 12f * density, subTextPaint);
        } else {
            canvas.drawText("PRESSURE SENSITIVE", w / 2f, pedalRect.bottom - 12f * density, subTextPaint);
        }
    }

    private void drawGasPedal(Canvas canvas, float w, float h, float radius) {
        // Drop shadow under pedal
        shadowPaint.setColor(0x88000000);
        canvas.drawRoundRect(pedalRect.left, pedalRect.top + 3f * density, pedalRect.right, pedalRect.bottom + 3f * density, radius, radius, shadowPaint);

        // Anodized metallic plate (warm cream / silver)
        basePaint.setColor(0xFFECE6D8);
        canvas.drawRoundRect(pedalRect, radius, radius, basePaint);

        borderPaint.setColor(0xFF8C8475);
        borderPaint.setStrokeWidth(2f * density);
        canvas.drawRoundRect(pedalRect, radius, radius, borderPaint);

        // 4 Corner screws / rivets
        float screwInset = 12f * density;
        float screwR = 3.5f * density;
        drawScrew(canvas, pedalRect.left + screwInset, pedalRect.top + screwInset, screwR);
        drawScrew(canvas, pedalRect.right - screwInset, pedalRect.top + screwInset, screwR);
        drawScrew(canvas, pedalRect.left + screwInset, pedalRect.bottom - screwInset, screwR);
        drawScrew(canvas, pedalRect.right - screwInset, pedalRect.bottom - screwInset, screwR);

        // Vertical flutes / grooves
        float fluteTop = pedalRect.top + 34f * density;
        float fluteBottom = pedalRect.bottom - 44f * density;
        float fluteGap = 12f * density;
        float fluteW = 3f * density;
        float cx = w / 2f;

        // Side grooves
        ridgePaint.setStyle(Paint.Style.STROKE);
        ridgePaint.setStrokeWidth(fluteW);
        ridgePaint.setStrokeCap(Paint.Cap.ROUND);
        ridgePaint.setColor(0xFFB5AC9E);

        canvas.drawLine(cx - fluteGap * 1.8f, fluteTop + 14f * density, cx - fluteGap * 1.8f, fluteBottom - 10f * density, ridgePaint);
        canvas.drawLine(cx - fluteGap * 0.9f, fluteTop + 6f * density, cx - fluteGap * 0.9f, fluteBottom - 6f * density, ridgePaint);
        canvas.drawLine(cx + fluteGap * 0.9f, fluteTop + 6f * density, cx + fluteGap * 0.9f, fluteBottom - 6f * density, ridgePaint);
        canvas.drawLine(cx + fluteGap * 1.8f, fluteTop + 14f * density, cx + fluteGap * 1.8f, fluteBottom - 10f * density, ridgePaint);

        // Center Neon Throttle Channel
        float neonW = 7f * density;
        ridgePaint.setStrokeWidth(neonW);
        ridgePaint.setColor(0xFFC7BFAE);
        canvas.drawLine(cx, fluteTop, cx, fluteBottom, ridgePaint);

        if (value > 0f) {
            float activeTop = fluteBottom - (fluteBottom - fluteTop) * value;
            // Glowing neon blue/cyan channel
            ridgePaint.setColor(0xFF4B53BB);
            ridgePaint.setStrokeWidth(neonW + 2f * density);
            canvas.drawLine(cx, activeTop, cx, fluteBottom, ridgePaint);

            ridgePaint.setColor(0xFF00E5FF);
            ridgePaint.setStrokeWidth(neonW);
            canvas.drawLine(cx, activeTop, cx, fluteBottom, ridgePaint);

            // Throttle cursor bead
            screwPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(cx, activeTop, 5f * density, screwPaint);
        }

        // Outer border
        borderPaint.setColor(0xFFC4BCAD);
        borderPaint.setStrokeWidth(1.5f * density);
        canvas.drawRoundRect(pedalRect, radius, radius, borderPaint);

        // Bottom text: GAS & DRIVE-BY-WIRE
        textPaint.setColor(0xFF1F1B14);
        textPaint.setTextSize(Math.min(w * 0.22f, 18f * density));
        canvas.drawText(label, w / 2f, pedalRect.bottom - 26f * density, textPaint);

        subTextPaint.setColor(0xFF6E685C);
        subTextPaint.setTextSize(Math.min(w * 0.11f, 8.5f * density));
        if (value > 0f) {
            subTextPaint.setColor(0xFF4B53BB);
            canvas.drawText(Math.round(value * 100f) + "%", w / 2f, pedalRect.bottom - 12f * density, subTextPaint);
        } else {
            canvas.drawText("DRIVE-BY-WIRE", w / 2f, pedalRect.bottom - 12f * density, subTextPaint);
        }
    }

    private void drawScrew(Canvas canvas, float cx, float cy, float r) {
        screwPaint.setColor(0xFFB5AC9E);
        canvas.drawCircle(cx, cy, r, screwPaint);
        screwPaint.setColor(0xFF6E685C);
        canvas.drawLine(cx - r * 0.7f, cy, cx + r * 0.7f, cy, screwPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE: {
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float h = getHeight();
                // Top 10% = 1.0, bottom 10% = 0.0
                float t = (event.getY() - 0.10f * h) / (0.80f * h);
                t = Math.max(0f, Math.min(1f, t));
                setValue(1f - t);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                setValue(0f);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private void setValue(float v) {
        if (Math.abs(v - value) < 0.005f) return;
        value = v;
        invalidate();
        if (listener != null) listener.onChange(value);
    }
}
