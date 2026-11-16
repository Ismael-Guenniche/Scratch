package android.graphics.drawable;

import android.graphics.Canvas;

public class ColorDrawable extends Drawable {
    private int color;
    private int alpha = 255;

    public ColorDrawable() {
    }

    public ColorDrawable(int c) {
        color = c;
    }

    public int getColor() {
        return color;
    }

    public void setColor(int c) {
        color = c;
        invalidateSelf();
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    @Override
    public void draw(Canvas c) {
        int a = (color >>> 24) * alpha / 255;
        if (a == 0) {
            return;
        }
        android.graphics.Rect b = getBounds();
        c.g.setPaint(new java.awt.Color((a << 24) | (color & 0xFFFFFF), true));
        c.g.fillRect(b.left, b.top, b.width(), b.height());
    }
}
