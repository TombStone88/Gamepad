package com.tombstone.wirelessgamepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** The little retro handheld illustration on the TombPad home screen. Decoration only. */
public class TombPadConsoleView extends View {

    private final float d;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF body = new RectF();
    private final RectF screen = new RectF();
    private final RectF r = new RectF();

    public TombPadConsoleView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = context.getResources().getDisplayMetrics().density;
        setClickable(false);
        setFocusable(false);
        t.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        t.setLetterSpacing(0.06f);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, Math.round(w * 0.74f));
    }

    private void text(Canvas c, String s, float x, float y, float size, int color,
                      Paint.Align align, float maxW) {
        t.setColor(color);
        t.setTextAlign(align);
        t.setTextSize(size);
        while (maxW > 0 && t.measureText(s) > maxW && size > 5f * d) {
            size *= 0.94f;
            t.setTextSize(size);
        }
        c.drawText(s, x, y, t);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;
        float edge = 4f * d;
        float press = 0f;

        // 3D underlay + body
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFD3C5AB);
        r.set(1.5f * d, 1.5f * d + edge, w - 1.5f * d, h - 0.5f * d);
        c.drawRoundRect(r, 22f * d, 22f * d, p);

        c.save();
        c.translate(0, press);
        body.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - edge - 0.5f * d);
        p.setColor(0xFFEFE6D5);
        c.drawRoundRect(body, 22f * d, 22f * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * d);
        p.setColor(0xFFD3C5AB);
        c.drawRoundRect(body, 22f * d, 22f * d, p);

        float bw = body.width();
        float bh = body.height();

        // Screen
        screen.set(body.left + bw * 0.06f, body.top + bh * 0.08f,
                body.right - bw * 0.06f, body.top + bh * 0.08f + bh * 0.52f);
        float sw = screen.width();
        float sh = screen.height();
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFCBE4DE);
        c.drawRoundRect(screen, 10f * d, 10f * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setColor(0xFF7F9A94);
        c.drawRoundRect(screen, 10f * d, 10f * d, p);

        float pad = sh * 0.10f + 4f * d;
        float tiny = sh * 0.115f;
        int dim = 0xFF4E6560;
        int ink = 0xFF2B2A33;

        text(c, "STEREO LINK", screen.left + pad, screen.top + pad + tiny * 0.7f, tiny, dim, Paint.Align.LEFT, sw * 0.4f);
        text(c, "CH-01", screen.right - pad, screen.top + pad + tiny * 0.7f, tiny, dim, Paint.Align.RIGHT, sw * 0.2f);

        // Icon box (mini gamepad glyph)
        float box = sh * 0.40f;
        float bx = screen.left + pad * 1.4f;
        float by = screen.centerY() - box * 0.5f + tiny * 0.2f;
        r.set(bx, by, bx + box, by + box);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFDCE0FF);
        c.drawRoundRect(r, 8f * d, 8f * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.5f * d);
        p.setColor(0xFF8B95FF);
        c.drawRoundRect(r, 8f * d, 8f * d, p);
        float gx = r.centerX();
        float gy = r.centerY();
        float gw = box * 0.62f;
        float gh = box * 0.36f;
        p.setColor(0xFF4B53BD);
        RectF g = new RectF(gx - gw / 2f, gy - gh / 2f, gx + gw / 2f, gy + gh / 2f);
        c.drawRoundRect(g, gh * 0.45f, gh * 0.45f, p);
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(g.left + gw * 0.22f, gy, g.left + gw * 0.38f, gy, p);
        c.drawLine(g.left + gw * 0.30f, gy - gh * 0.18f, g.left + gw * 0.30f, gy + gh * 0.18f, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(g.right - gw * 0.28f, gy - gh * 0.1f, 1.4f * d, p);
        c.drawCircle(g.right - gw * 0.18f, gy + gh * 0.1f, 1.4f * d, p);

        // Title lines
        float tx = bx + box + pad * 0.9f;
        float avail = screen.right - pad - tx;
        text(c, "POCKET ARCHITECTURE", tx, by + box * 0.45f, sh * 0.15f, ink, Paint.Align.LEFT, avail);
        text(c, "ULTRA-LOW LATENCY", tx, by + box * 0.45f + tiny * 1.5f, tiny, dim, Paint.Align.LEFT, avail);

        // Bottom row
        float by2 = screen.bottom - pad * 0.8f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFF5C62);
        c.drawCircle(screen.left + pad + tiny * 0.3f, by2 - tiny * 0.3f, tiny * 0.3f, p);
        text(c, "SYNC ACTIVE", screen.left + pad + tiny, by2, tiny, dim, Paint.Align.LEFT, sw * 0.4f);
        text(c, "90s HARDPADS", screen.right - pad, by2, tiny, dim, Paint.Align.RIGHT, sw * 0.4f);

        // Controls area
        float area = body.bottom - screen.bottom;
        float cy = screen.bottom + area * 0.5f;
        float L = Math.min(area * 0.34f, bw * 0.07f);
        float T = L * 0.62f;

        // D-pad
        float dx = body.left + bw * 0.2f;
        p.setColor(0xFF3B3B46);
        r.set(dx - L, cy - T / 2f, dx + L, cy + T / 2f);
        c.drawRoundRect(r, T * 0.3f, T * 0.3f, p);
        r.set(dx - T / 2f, cy - L, dx + T / 2f, cy + L);
        c.drawRoundRect(r, T * 0.3f, T * 0.3f, p);
        p.setColor(0xFF2A2A33);
        c.drawCircle(dx, cy, T * 0.28f, p);

        // Speaker lines
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(0xFFB9AD97);
        float mx = body.centerX();
        for (int i = -1; i <= 1; i++) {
            c.drawLine(mx - bw * 0.04f, cy + i * area * 0.14f, mx + bw * 0.04f, cy + i * area * 0.14f, p);
        }

        // Face buttons
        p.setStyle(Paint.Style.FILL);
        float fx = body.right - bw * 0.2f;
        float o = L * 0.78f;
        float rb = L * 0.4f;
        p.setColor(0xFF4B53BD);
        c.drawCircle(fx, cy - o, rb, p);
        p.setColor(0xFFFF5C62);
        c.drawCircle(fx - o, cy, rb, p);
        p.setColor(0xFF3E5F5A);
        c.drawCircle(fx + o, cy, rb, p);
        p.setColor(0xFFFF5C62);
        c.drawCircle(fx, cy + o, rb, p);

        c.restore();
    }
}
