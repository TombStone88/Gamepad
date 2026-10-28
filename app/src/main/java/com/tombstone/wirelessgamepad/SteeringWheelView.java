package com.tombstone.wirelessgamepad;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

/**
 * Realistic racing steering wheel: leather rim with thumb grips, stitching, 3 spokes,
 * centre hub and a 12 o'clock marker. Drag around the centre to turn it (multi-turn,
 * configurable lock-to-lock), release and it springs back.
 * In read-only mode (gyro) it ignores touch and just mirrors setSteer().
 * Reports steering in [-1, 1] (right = positive).
 */
public class SteeringWheelView extends View {

    public interface Listener {
        void onChange(float value);
    }

    private float maxDegrees = 270f; // wheel rotation that equals full lock
    private boolean readOnly = false;

    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimCorePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stitchPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokeEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spokeShinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubSubTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path spoke = new Path();
    private final Path pointer = new Path();
    private final RectF arcRect = new RectF();
    private final float density;

    private Shader hubShader;
    private float shaderR = -1f;

    private float wheelAngle = 0f;
    private float prevTouchAngle = 0f;
    private ValueAnimator returnAnim;
    private Listener listener;

    public SteeringWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;

        shadowPaint.setStyle(Paint.Style.STROKE);
        shadowPaint.setColor(0x55000000);

        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setColor(0xFF15181D);

        rimCorePaint.setStyle(Paint.Style.STROKE);
        rimCorePaint.setColor(0xFF2C313A);

        gripPaint.setStyle(Paint.Style.STROKE);
        gripPaint.setColor(0xFF0C0E11);

        stitchPaint.setStyle(Paint.Style.STROKE);
        stitchPaint.setColor(0xCCB8BFCA);
        stitchPaint.setStrokeWidth(1.3f * density);
        stitchPaint.setPathEffect(new DashPathEffect(new float[]{5f * density, 4f * density}, 0f));

        spokePaint.setStyle(Paint.Style.FILL);
        spokePaint.setColor(0xFF2A2F37);
        spokeEdgePaint.setStyle(Paint.Style.STROKE);
        spokeEdgePaint.setColor(0xFF48505C);
        spokeEdgePaint.setStrokeWidth(1.2f * density);
        spokeEdgePaint.setStrokeJoin(Paint.Join.ROUND);
        spokeShinePaint.setStyle(Paint.Style.STROKE);
        spokeShinePaint.setColor(0x22FFFFFF);
        spokeShinePaint.setStrokeCap(Paint.Cap.ROUND);

        hubRingPaint.setStyle(Paint.Style.STROKE);
        hubRingPaint.setColor(0xFF616B79);
        hubRingPaint.setStrokeWidth(1.5f * density);

        logoPaint.setColor(0xFF4B53BB);

        markerPaint.setColor(0xFFFF3B30);
        markerPaint.setStyle(Paint.Style.STROKE);

        pointerPaint.setColor(0xFFFF3B30);

        hubTextPaint.setColor(0xFFFFFFFF);
        hubTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL));
        hubTextPaint.setTextAlign(Paint.Align.CENTER);

        hubSubTextPaint.setColor(0xFFBEC2FF);
        hubSubTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        hubSubTextPaint.setTextAlign(Paint.Align.CENTER);
        hubSubTextPaint.setLetterSpacing(0.08f);

        hintTextPaint.setColor(0xFF7A8799);
        hintTextPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        hintTextPaint.setTextAlign(Paint.Align.CENTER);
        hintTextPaint.setLetterSpacing(0.06f);

        glossPaint.setStyle(Paint.Style.STROKE);
        glossPaint.setColor(0x40FFFFFF);
        glossPaint.setStrokeCap(Paint.Cap.ROUND);
        darkPaint.setStyle(Paint.Style.STROKE);
        darkPaint.setColor(0x44000000);
        darkPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    /** Full-lock rotation in degrees (each side). */
    public void setMaxDegrees(float degrees) {
        maxDegrees = Math.max(90f, degrees);
        wheelAngle = Math.max(-maxDegrees, Math.min(maxDegrees, wheelAngle));
        invalidate();
    }

    /** Read-only = display only (gyro mode). */
    public void setReadOnly(boolean ro) {
        readOnly = ro;
        cancelReturn();
    }

    /** Show a steering value in [-1,1] without notifying the listener. */
    public void setSteer(float v) {
        cancelReturn();
        v = Math.max(-1f, Math.min(1f, v));
        wheelAngle = v * maxDegrees;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float r = Math.min(w, h) / 2f - 10f * density;
        if (r <= 0f) return;
        float hubR = r * 0.24f;
        hubShader = new RadialGradient(w / 2f, h / 2f, hubR,
                new int[]{0xFF5A626E, 0xFF2B3039, 0xFF13161B},
                new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP);
        hubPaint.setShader(hubShader);
        shaderR = r;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - 10f * density;
        if (r <= 0f) return;
        float rimW = r * 0.17f;
        float rimMid = r - rimW / 2f;
        float innerR = r - rimW;
        float hubR = r * 0.24f;

        // Fixed pointer at 12 o'clock
        pointer.reset();
        pointer.moveTo(cx - 6f * density, cy - r - 9f * density);
        pointer.lineTo(cx + 6f * density, cy - r - 9f * density);
        pointer.lineTo(cx, cy - r - 2f * density);
        pointer.close();
        canvas.drawPath(pointer, pointerPaint);

        // Soft shadow under the rim (fixed, doesn't rotate)
        shadowPaint.setStrokeWidth(rimW);
        canvas.drawCircle(cx, cy + 3f * density, rimMid, shadowPaint);

        canvas.save();
        canvas.rotate(wheelAngle, cx, cy);

        // Spokes (tuck under the rim): left, right, bottom
        drawSpoke(canvas, cx, cy, 180f, hubR, innerR + rimW * 0.4f, r);
        drawSpoke(canvas, cx, cy, 0f, hubR, innerR + rimW * 0.4f, r);
        drawSpoke(canvas, cx, cy, 90f, hubR, innerR + rimW * 0.4f, r);

        // Rim: dark tube + lighter core for a rounded look
        rimPaint.setStrokeWidth(rimW);
        canvas.drawCircle(cx, cy, rimMid, rimPaint);
        rimCorePaint.setStrokeWidth(rimW * 0.6f);
        canvas.drawCircle(cx, cy, rimMid, rimCorePaint);

        // Thumb grips at 10 and 2 o'clock
        gripPaint.setStrokeWidth(rimW * 0.98f);
        arcRect.set(cx - rimMid, cy - rimMid, cx + rimMid, cy + rimMid);
        canvas.drawArc(arcRect, -172f, 44f, false, gripPaint);
        canvas.drawArc(arcRect, -52f, 44f, false, gripPaint);

        // Stitching
        canvas.drawCircle(cx, cy, rimMid, stitchPaint);

        // 12 o'clock centre marker
        markerPaint.setStrokeWidth(r * 0.075f);
        canvas.drawLine(cx, cy - r + rimW * 0.08f, cx, cy - r + rimW * 0.92f, markerPaint);

        // Hub
        canvas.drawCircle(cx, cy, hubR, hubPaint);
        canvas.drawCircle(cx, cy, hubR, hubRingPaint);

        // Center periwinkle emblem with TURBO GT-90
        float emblemR = hubR * 0.58f;
        canvas.drawCircle(cx, cy, emblemR, logoPaint);
        hubRingPaint.setColor(0xFF8B93FF);
        canvas.drawCircle(cx, cy, emblemR, hubRingPaint);

        // Helmet / turbo mini icon
        Path miniIcon = new Path();
        float iconSize = emblemR * 0.32f;
        miniIcon.addArc(new RectF(cx - iconSize, cy - emblemR * 0.55f, cx + iconSize, cy - emblemR * 0.05f), 180f, 180f);
        miniIcon.lineTo(cx + iconSize, cy - emblemR * 0.15f);
        miniIcon.lineTo(cx - iconSize, cy - emblemR * 0.15f);
        miniIcon.close();
        Paint iconP = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconP.setColor(0xFFFFFFFF);
        iconP.setStyle(Paint.Style.FILL);
        canvas.drawPath(miniIcon, iconP);

        // Hub labels
        hubTextPaint.setTextSize(emblemR * 0.38f);
        canvas.drawText("TURBO", cx, cy + emblemR * 0.18f, hubTextPaint);
        hubSubTextPaint.setTextSize(emblemR * 0.24f);
        canvas.drawText("GT-90", cx, cy + emblemR * 0.46f, hubSubTextPaint);

        canvas.restore();

        // Fixed lighting on top (doesn't rotate) so it feels like a real object
        glossPaint.setStrokeWidth(rimW * 0.28f);
        arcRect.set(cx - (r - rimW * 0.33f), cy - (r - rimW * 0.33f),
                cx + (r - rimW * 0.33f), cy + (r - rimW * 0.33f));
        canvas.drawArc(arcRect, 205f, 55f, false, glossPaint);
        darkPaint.setStrokeWidth(rimW * 0.45f);
        arcRect.set(cx - (r - rimW * 0.5f), cy - (r - rimW * 0.5f),
                cx + (r - rimW * 0.5f), cy + (r - rimW * 0.5f));
        canvas.drawArc(arcRect, 15f, 80f, false, darkPaint);

        // Subtitle hint below wheel
        hintTextPaint.setTextSize(9f * density);
        canvas.drawText("DRAG OR MOVE TO STEER (TOUCH / GYRO)", cx, h - 3f * density, hintTextPaint);
    }

    private void drawSpoke(Canvas c, float cx, float cy, float deg, float hubR, float outer, float r) {
        float wHub = r * 0.15f;
        float wRim = r * 0.085f;
        c.save();
        c.rotate(deg, cx, cy);
        spoke.reset();
        spoke.moveTo(cx + hubR * 0.4f, cy - wHub);
        spoke.lineTo(cx + outer, cy - wRim);
        spoke.lineTo(cx + outer, cy + wRim);
        spoke.lineTo(cx + hubR * 0.4f, cy + wHub);
        spoke.close();
        c.drawPath(spoke, spokePaint);
        c.drawPath(spoke, spokeEdgePaint);
        spokeShinePaint.setStrokeWidth(r * 0.03f);
        c.drawLine(cx + hubR, cy - wHub * 0.45f, cx + outer - r * 0.05f, cy - wRim * 0.45f, spokeShinePaint);
        c.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (readOnly) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancelReturn();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                prevTouchAngle = touchAngle(event.getX(), event.getY());
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - getWidth() / 2f;
                float dy = event.getY() - getHeight() / 2f;
                float minR = Math.min(getWidth(), getHeight()) * 0.06f;
                if (dx * dx + dy * dy < minR * minR) return true; // too close to the hub
                float a = touchAngle(event.getX(), event.getY());
                float delta = wrap(a - prevTouchAngle); // incremental, so multi-turn works
                prevTouchAngle = a;
                float next = Math.max(-maxDegrees, Math.min(maxDegrees, wheelAngle + delta));
                if ((wheelAngle < 0f && next >= 0f) || (wheelAngle > 0f && next <= 0f)) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); // centre detent
                } else if (Math.abs(next) >= maxDegrees && Math.abs(wheelAngle) < maxDegrees) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP); // hit the lock
                }
                setWheelAngle(next);
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
        if (listener != null) listener.onChange(wheelAngle / maxDegrees);
    }

    private void springBack() {
        cancelReturn();
        if (wheelAngle == 0f) return;
        returnAnim = ValueAnimator.ofFloat(wheelAngle, 0f);
        returnAnim.setDuration((long) (140 + 260 * Math.abs(wheelAngle) / maxDegrees));
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
