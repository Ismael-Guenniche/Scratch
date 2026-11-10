package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Vúmetro de puntos (estilo Nothing): una fila de puntos que se encienden de izquierda a derecha con el volumen. */
final class DotMeterView extends View {
    private static final int N = 32;
    private final Lucent th;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level;
    private float peak;
    private boolean disco;
    private final float[] hsv = {0f, 0.85f, 1f};

    DotMeterView(Context c, Lucent th) {
        super(c);
        this.th = th;
    }

    void setDisco(boolean on) {
        disco = on;
    }

    void setLevel(float l) {
        l = Math.max(0f, Math.min(1f, l));
        peak = Math.max(l, peak - 0.03f);
        if (Math.abs(l - level) > 0.002f || peak > 0f) {
            level = l;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float cy = getHeight() / 2f;
        float step = w / N;
        float r = Math.min(step * 0.28f, getHeight() / 2f - 1);
        int lit = Math.round(level * N);
        int pk = Math.min(N - 1, Math.round(peak * N));
        for (int i = 0; i < N; i++) {
            boolean on = i < lit;
            if (on) {
                if (disco) {
                    hsv[0] = (i * 360f / N + (System.nanoTime() / 8000000L)) % 360;
                    p.setColor(android.graphics.Color.HSVToColor(hsv));
                } else {
                    p.setColor(i > N * 0.85f ? 0xFFFF3B30 : th.accent);
                }
            } else if (i == pk && pk > lit) {
                p.setColor(th.ink2);
            } else {
                p.setColor(th.ghost);
            }
            c.drawCircle(step * (i + 0.5f), cy, on ? r * 1.15f : r, p);
        }
    }
}
