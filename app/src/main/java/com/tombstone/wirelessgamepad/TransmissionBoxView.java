package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Sequential transmission box matching the RetroPad / PocketPad specification:
 * - Header: TRANSMISSION SEQUENTIAL and GEAR pill readout (R, N, 1, 2, 3, 4, 5, 6)
 * - Starts in N (Neutral)
 * - Tactile Shift Down button (-) sending LB with debounce
 * - Tactile Shift Up button (+) sending RB with debounce
 * - Tactile E-Brake card (!) sending A (or handbrake)
 */
public class TransmissionBoxView extends View {

    public interface Listener {
        void onShiftDown(boolean pressed);
        void onShiftUp(boolean pressed);
        void onEBrake(boolean pressed);
    }

    private static final String[] GEARS = {"R", "N", "1", "2", "3", "4", "5", "6"};
    private static final int DEFAULT_GEAR_INDEX = 1; // Index 1 is "N" (Neutral)
    private static final long SHIFT_DEBOUNCE_MS = 200; // Prevents accidental gear skipping

    private final float density;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint btnPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF boxRect = new RectF();
    private final RectF gearRect = new RectF();
    private final RectF downRect = new RectF();
    private final RectF upRect = new RectF();
    private final RectF ebrakeRect = new RectF();
    private final RectF badgeRect = new RectF();

    private int currentGearIndex = DEFAULT_GEAR_INDEX;
    private long lastShiftTime = 0;
    private boolean shiftDownPressed = false;
    private boolean shiftUpPressed = false;
    private boolean ebrakePressed = false;

    private Listener listener;

    public TransmissionBoxView(Context context) {
        this(context, null);
    }

    public TransmissionBoxView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TransmissionBoxView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = context.getResources().getDisplayMetrics().density;
        init();
    }

    private void init() {
        bgPaint.setColor(0xFF141720);
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFF282F3D);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.5f * density);

        textPaint.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        labelPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setGearIndex(int index) {
        this.currentGearIndex = Math.max(0, Math.min(GEARS.length - 1, index));
        invalidate();
    }

    public String getCurrentGear() {
        return GEARS[currentGearIndex];
    }

    public void reset() {
        currentGearIndex = DEFAULT_GEAR_INDEX;
        shiftDownPressed = false;
        shiftUpPressed = false;
        ebrakePressed = false;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (w <= 0) w = Math.round(190 * density);
        if (h <= 0) h = Math.round(180 * density);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = 8f * density;
        boxRect.set(pad, pad, w - pad, h - pad);
        float cardRadius = 14f * density;

        // Container drop shadow, background + border
        shadowPaint.setColor(0x40000000);
        canvas.drawRoundRect(boxRect.left, boxRect.top + 3f * density, boxRect.right, boxRect.bottom + 3f * density, cardRadius, cardRadius, shadowPaint);
        canvas.drawRoundRect(boxRect, cardRadius, cardRadius, bgPaint);
        borderPaint.setColor(0xFF384355);
        borderPaint.setStrokeWidth(2f * density);
        canvas.drawRoundRect(boxRect, cardRadius, cardRadius, borderPaint);

        float innerLeft = boxRect.left + 10f * density;
        float innerRight = boxRect.right - 10f * density;
        float innerWidth = innerRight - innerLeft;
        float topY = boxRect.top + 10f * density;

        // --- 1. Header: TRANSMISSION SEQUENTIAL + GEAR pill ---
        labelPaint.setColor(0xFF7A8799);
        labelPaint.setTextSize(8.5f * density);
        labelPaint.setLetterSpacing(0.06f);
        canvas.drawText("TRANSMISSION", innerLeft, topY + 8f * density, labelPaint);

        textPaint.setColor(0xFF00E5FF);
        textPaint.setTextSize(11f * density);
        textPaint.setLetterSpacing(0.04f);
        canvas.drawText("SEQUENTIAL", innerLeft, topY + 20f * density, textPaint);

        // Gear Pill
        float pillW = 56f * density;
        float pillH = 22f * density;
        gearRect.set(innerRight - pillW, topY + 2f * density, innerRight, topY + 2f * density + pillH);

        btnPaint.setStyle(Paint.Style.FILL);
        btnPaint.setColor(0xFF1E2333);
        canvas.drawRoundRect(gearRect, pillH / 2f, pillH / 2f, btnPaint);

        borderPaint.setColor(0xFF38435C);
        canvas.drawRoundRect(gearRect, pillH / 2f, pillH / 2f, borderPaint);

        labelPaint.setColor(0xFF8B93FF);
        labelPaint.setTextSize(8f * density);
        canvas.drawText("GEAR", gearRect.left + 7f * density, gearRect.centerY() + 3f * density, labelPaint);

        // Current Gear string: R (coral), N (cyan), 1..6 (white)
        String gearStr = GEARS[currentGearIndex];
        if ("R".equals(gearStr)) {
            textPaint.setColor(0xFFFF5E62);
        } else if ("N".equals(gearStr)) {
            textPaint.setColor(0xFF00E5FF);
        } else {
            textPaint.setColor(0xFFFFFFFF);
        }
        textPaint.setTextSize(13f * density);
        canvas.drawText(gearStr, gearRect.right - 15f * density, gearRect.centerY() + 4.5f * density, textPaint);

        // --- 2. Shift Buttons Row ---
        float btnY = topY + 28f * density;
        float btnGap = 8f * density;
        float btnW = (innerWidth - btnGap) / 2f;
        float btnH = 50f * density;
        float shadowOffset = 3f * density;
        float btnRadius = 10f * density;

        // Shift Down (-) Button: Coral Red
        downRect.set(innerLeft, btnY, innerLeft + btnW, btnY + btnH);
        float dPress = shiftDownPressed ? shadowOffset : 0f;

        // Shadow under Shift Down
        shadowPaint.setColor(0xFF8B1E22);
        canvas.drawRoundRect(downRect.left, downRect.top + shadowOffset, downRect.right, downRect.bottom + shadowOffset, btnRadius, btnRadius, shadowPaint);

        // Front Face
        btnPaint.setColor(shiftDownPressed ? 0xFFD32F2F : 0xFFE53935);
        canvas.drawRoundRect(downRect.left, downRect.top + dPress, downRect.right, downRect.bottom + dPress, btnRadius, btnRadius, btnPaint);

        // Minus symbol
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(22f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("−", downRect.centerX(), downRect.centerY() + dPress - 2f * density, textPaint);

        labelPaint.setColor(0xFFFFA4A2);
        labelPaint.setTextSize(7.5f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("SHIFT DOWN", downRect.centerX(), downRect.bottom + dPress - 6f * density, labelPaint);

        // Shift Up (+) Button: Indigo / Periwinkle
        upRect.set(innerLeft + btnW + btnGap, btnY, innerRight, btnY + btnH);
        float uPress = shiftUpPressed ? shadowOffset : 0f;

        // Shadow under Shift Up
        shadowPaint.setColor(0xFF2A3182);
        canvas.drawRoundRect(upRect.left, upRect.top + shadowOffset, upRect.right, upRect.bottom + shadowOffset, btnRadius, btnRadius, shadowPaint);

        // Front Face
        btnPaint.setColor(shiftUpPressed ? 0xFF3D46A6 : 0xFF4B53BB);
        canvas.drawRoundRect(upRect.left, upRect.top + uPress, upRect.right, upRect.bottom + uPress, btnRadius, btnRadius, btnPaint);

        // Plus symbol
        textPaint.setTextSize(20f * density);
        canvas.drawText("+", upRect.centerX(), upRect.centerY() + uPress - 2f * density, textPaint);

        labelPaint.setColor(0xFFBEC2FF);
        canvas.drawText("SHIFT UP", upRect.centerX(), upRect.bottom + uPress - 6f * density, labelPaint);

        // --- 3. E-Brake Button Card ---
        float ebY = btnY + btnH + 12f * density;
        float ebH = 46f * density;
        ebrakeRect.set(innerLeft, ebY, innerRight, ebY + ebH);
        float ebPress = ebrakePressed ? shadowOffset : 0f;

        // E-Brake Shadow
        shadowPaint.setColor(0xFFC7BBAA);
        canvas.drawRoundRect(ebrakeRect.left, ebrakeRect.top + shadowOffset, ebrakeRect.right, ebrakeRect.bottom + shadowOffset, btnRadius, btnRadius, shadowPaint);

        // E-Brake Face (Warm Cream tactile surface)
        btnPaint.setColor(ebrakePressed ? 0xFFEDE2D2 : 0xFFFBF4E8);
        canvas.drawRoundRect(ebrakeRect.left, ebrakeRect.top + ebPress, ebrakeRect.right, ebrakeRect.bottom + ebPress, btnRadius, btnRadius, btnPaint);

        // Exclamation Mark Badge
        float badgeSize = 24f * density;
        float badgeX = ebrakeRect.left + 8f * density;
        float badgeY = ebrakeRect.centerY() + ebPress - badgeSize / 2f;
        badgeRect.set(badgeX, badgeY, badgeX + badgeSize, badgeY + badgeSize);

        iconPaint.setColor(0xFFFF5E62);
        canvas.drawRoundRect(badgeRect, 6f * density, 6f * density, iconPaint);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(14f * density);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("!", badgeRect.centerX(), badgeRect.centerY() + 5f * density, textPaint);

        // E-BRAKE text
        textPaint.setColor(0xFF1F1B14);
        textPaint.setTextSize(12f * density);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("E-BRAKE", badgeRect.right + 8f * density, ebrakeRect.centerY() + ebPress - 1f * density, textPaint);

        labelPaint.setColor(0xFF767065);
        labelPaint.setTextSize(8f * density);
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("DRIFT LOCK", badgeRect.right + 8f * density, ebrakeRect.centerY() + ebPress + 11f * density, labelPaint);

        // Active indicator dot
        float dotR = 5f * density;
        float dotCx = ebrakeRect.right - 14f * density;
        float dotCy = ebrakeRect.centerY() + ebPress;
        dotPaint.setColor(ebrakePressed ? 0xFFFF3B30 : 0xFFBFB7AA);
        canvas.drawCircle(dotCx, dotCy, dotR, dotPaint);

        if (ebrakePressed) {
            dotPaint.setColor(0x55FF3B30);
            canvas.drawCircle(dotCx, dotCy, dotR + 3f * density, dotPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        long now = SystemClock.uptimeMillis();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (downRect.contains(x, y)) {
                    shiftDownPressed = true;
                    if (now - lastShiftTime > SHIFT_DEBOUNCE_MS) {
                        lastShiftTime = now;
                        if (currentGearIndex > 0) {
                            currentGearIndex--;
                        }
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (listener != null) listener.onShiftDown(true);
                    invalidate();
                    return true;
                } else if (upRect.contains(x, y)) {
                    shiftUpPressed = true;
                    if (now - lastShiftTime > SHIFT_DEBOUNCE_MS) {
                        lastShiftTime = now;
                        if (currentGearIndex < GEARS.length - 1) {
                            currentGearIndex++;
                        }
                    }
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    if (listener != null) listener.onShiftUp(true);
                    invalidate();
                    return true;
                } else if (ebrakeRect.contains(x, y)) {
                    ebrakePressed = true;
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (listener != null) listener.onEBrake(true);
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean changed = false;
                if (shiftDownPressed) {
                    shiftDownPressed = false;
                    if (listener != null) listener.onShiftDown(false);
                    changed = true;
                }
                if (shiftUpPressed) {
                    shiftUpPressed = false;
                    if (listener != null) listener.onShiftUp(false);
                    changed = true;
                }
                if (ebrakePressed) {
                    ebrakePressed = false;
                    if (listener != null) listener.onEBrake(false);
                    changed = true;
                }
                if (changed) invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }
}
