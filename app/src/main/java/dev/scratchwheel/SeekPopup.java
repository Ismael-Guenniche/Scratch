package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

/** Globo con una barra que aparece sobre el botón de avance/retroceso al mantenerlo pulsado. */
final class SeekPopup {
    private static final double MIN = SeekSpeed.MIN;
    private static final double BASE = SeekSpeed.BASE;
    private static final double MAX = SeekSpeed.MAX;

    private final Lucent th;
    private final PopupWindow win;
    private final BarView bar;

    private final class BarView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private double speed = BASE;

        BarView(Context c) {
            super(c);
        }

        void set(double s) {
            speed = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            float d = th.dp(1);
            p.setStyle(Paint.Style.FILL);
            p.setColor(th.solid);
            p.setAlpha(255);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, th.dp(18), th.dp(18), p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(th.dp(1));
            p.setColor(th.glassLine);
            r.set(d / 2, d / 2, w - d / 2, h - d / 2);
            c.drawRoundRect(r, th.dp(18), th.dp(18), p);
            p.setStyle(Paint.Style.FILL);
            // etiqueta
            p.setColor(th.ink);
            p.setTypeface(th.mono);
            p.setTextSize(th.dp(13));
            p.setTextAlign(Paint.Align.CENTER);
            String t = String.format(java.util.Locale.US, "%.1f×", speed);
            c.drawText(t, w / 2f, th.dp(24), p);
            // barra
            float pad = th.dp(18);
            float y = h - th.dp(16);
            float x0 = pad;
            float x1 = w - pad;
            p.setColor(th.ghost);
            r.set(x0, y - 2 * d, x1, y + 2 * d);
            c.drawRoundRect(r, 2 * d, 2 * d, p);
            float mid = x0 + (float) ((BASE - MIN) / (MAX - MIN)) * (x1 - x0);
            float kx = x0 + (float) ((speed - MIN) / (MAX - MIN)) * (x1 - x0);
            p.setColor(th.accent);
            r.set(Math.min(mid, kx), y - 2 * d, Math.max(mid, kx), y + 2 * d);
            c.drawRoundRect(r, 2 * d, 2 * d, p);
            p.setColor(th.ink2);
            c.drawRect(mid - d / 2, y - 6 * d, mid + d / 2, y + 6 * d, p);
            p.setColor(th.accent);
            c.drawCircle(kx, y, 7 * d, p);
        }
    }

    SeekPopup(Context c, Lucent th) {
        this.th = th;
        bar = new BarView(c);
        win = new PopupWindow(bar, th.dp(220), th.dp(68), false);
        win.setTouchable(false);
        win.setClippingEnabled(false);
        win.setElevation(th.dp(8));
    }

    void show(View anchor) {
        int[] xy = new int[2];
        anchor.getLocationOnScreen(xy);
        int x = xy[0] + anchor.getWidth() / 2 - th.dp(110);
        int y = xy[1] - th.dp(76);
        bar.set(BASE);
        try {
            win.showAtLocation(anchor, Gravity.NO_GRAVITY, Math.max(th.dp(8), x), Math.max(th.dp(8), y));
        } catch (RuntimeException ignored) {
        }
    }

    void set(double speed) {
        bar.set(speed);
    }

    void dismiss() {
        try {
            win.dismiss();
        } catch (RuntimeException ignored) {
        }
    }
}
