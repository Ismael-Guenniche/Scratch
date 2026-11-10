package dev.scratchwheel;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** Rejilla de puntos tenue que cubre toda la pantalla (firma Nothing), dibujada sobre el fondo. */
public class DotGrid extends Drawable {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float step;
    private final float r;

    public DotGrid(int color, float density) {
        p.setColor(color);
        step = 16f * density;
        r = 1.1f * density;
    }

    @Override
    public void draw(Canvas c) {
        int w = getBounds().width();
        int h = getBounds().height();
        float x0 = (w % step) / 2f + step / 2f;
        float y0 = (h % step) / 2f + step / 2f;
        for (float y = y0; y < h; y += step) {
            for (float x = x0; x < w; x += step) {
                c.drawCircle(x, y, r, p);
            }
        }
    }

    @Override
    public void setAlpha(int a) {
        p.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter f) {
        p.setColorFilter(f);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
