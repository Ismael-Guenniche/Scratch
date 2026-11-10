package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Botón-icono minimalista hecho con puntos. Los puntos apagados se ven fantasma. */
public class DotIconView extends View {
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String[] icon = DotIcons.MENU;
    private final float density;
    private boolean ghost = true;

    public DotIconView(Context c, Lucent t) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        on.setColor(t.ink);
        off.setColor(t.ghost);
        setClickable(true);
        setFocusable(true);
    }

    /** Cambia el icono y el color de los puntos encendidos. */
    public void set(String[] icn, int color) {
        icon = icn;
        on.setColor(color);
        invalidate();
    }

    /** Muestra u oculta los puntos apagados (fantasma). */
    public DotIconView ghost(boolean g) {
        ghost = g;
        invalidate();
        return this;
    }

    @Override
    protected void onMeasure(int w, int h) {
        int d = (int) (48 * density);
        setMeasuredDimension(resolveSize(d, w), resolveSize(d, h));
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = Math.min(getWidth(), getHeight()) * 0.62f;
        float cell = size / DotIcons.N;
        float x0 = (getWidth() - size) / 2f;
        float y0 = (getHeight() - size) / 2f;
        float r = cell * 0.36f;
        for (int y = 0; y < DotIcons.N; y++) {
            String row = icon[y];
            for (int x = 0; x < DotIcons.N; x++) {
                boolean lit = row.charAt(x) == '1';
                if (lit || ghost) {
                    c.drawCircle(x0 + (x + 0.5f) * cell, y0 + (y + 0.5f) * cell, r, lit ? on : off);
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            setAlpha(0.45f);
        } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            setAlpha(1f);
        }
        return super.onTouchEvent(e);
    }
}
