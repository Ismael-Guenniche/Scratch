package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Fader vertical del modo DJ: grande para el dedo, con marca de valor neutro. Tocar la etiqueta lo resetea. */
final class FaderView extends View {
    interface Listener {
        void onValue(double v);
    }

    /** Texto del valor en vivo que se dibuja encima de la barra. */
    interface Readout {
        String text(double v);
    }

    private double[] tickPos = new double[0];
    private String[] tickTxt = new String[0];
    private Readout readout;
    private final Lucent th;
    private final String label;
    private final double neutral;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double value;
    private Listener listener;

    FaderView(Context c, Lucent th, String label, double neutral) {
        super(c);
        this.th = th;
        this.label = label;
        this.neutral = neutral;
        this.value = neutral;
    }

    void setListener(Listener l) {
        listener = l;
    }

    /** Numeración: posición 0..1 de cada marca y su texto. */
    void setTicks(double[] pos, String[] txt) {
        tickPos = pos;
        tickTxt = txt;
        invalidate();
    }

    void setReadout(Readout r) {
        readout = r;
        invalidate();
    }

    void setValue(double v) {
        value = Math.max(0, Math.min(1, v));
        invalidate();
    }

    private float top() {
        return th.dp(28);
    }

    private float bottom() {
        return getHeight() - th.dp(26) - (label.split("\n").length - 1) * th.dp(10);
    }

    private float yOf(double v) {
        return (float) (bottom() - v * (bottom() - top()));
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f;
        float d = th.dp(1);
        p.setStyle(Paint.Style.FILL);
        p.setColor(th.ghost);
        c.drawRoundRect(cx - 2 * d, top(), cx + 2 * d, bottom(), 2 * d, 2 * d, p);
        float yn = yOf(neutral);
        float yv = yOf(value);
        p.setColor(th.accent);
        c.drawRoundRect(cx - 2 * d, Math.min(yn, yv), cx + 2 * d, Math.max(yn, yv), 2 * d, 2 * d, p);
        p.setColor(th.ink2);
        c.drawRect(cx - 9 * d, yn - d / 2, cx + 9 * d, yn + d / 2, p);
        p.setColor(th.accent);
        c.drawCircle(cx, yv, 10 * d, p);
        p.setColor(th.ink2);
        p.setTypeface(th.mono);
        p.setTextSize(th.dp(9));
        p.setTextAlign(Paint.Align.CENTER);
        p.setLetterSpacing(0.06f);
        String[] ls = label.split("\n");
        float maxW = getWidth() - th.dp(2);
        for (int i = 0; i < ls.length; i++) {
            float sz = th.dp(9);
            p.setTextSize(sz);
            float w = p.measureText(ls[i]);
            if (w > maxW) {
                p.setTextSize(sz * maxW / w);
            }
            c.drawText(ls[i], cx, getHeight() - th.dp(8) - (ls.length - 1 - i) * th.dp(10), p);
        }
        // numeración a la izquierda de la barra
        p.setTextSize(th.dp(7.5f));
        p.setLetterSpacing(0f);
        p.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < tickPos.length && i < tickTxt.length; i++) {
            float y = yOf(tickPos[i]);
            p.setColor(th.ink2);
            c.drawRect(cx - 7 * d, y - d / 4, cx - 4 * d, y + d / 4, p);
            c.drawText(tickTxt[i], cx - 11 * d, y + th.dp(2.6f), p);
        }
        if (readout != null) {
            p.setColor(th.accent);
            p.setTextSize(th.dp(10));
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(readout.text(value), cx, th.dp(11), p);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (e.getY() > getHeight() - th.dp(26)) {
                    setValue(neutral);
                    fire();
                    return true;
                }
                set(e.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getY() <= getHeight() - th.dp(20)) {
                    set(e.getY());
                }
                return true;
            default:
                return true;
        }
    }

    private void set(float y) {
        double v = (bottom() - y) / (bottom() - top());
        v = Math.max(0, Math.min(1, v));
        if (Math.abs(v - neutral) < 0.04) {
            v = neutral;
        }
        value = v;
        invalidate();
        fire();
    }

    private void fire() {
        if (listener != null) {
            listener.onValue(value);
        }
    }
}
