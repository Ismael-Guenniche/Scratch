package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;

/** Sin animación de onda: el color se superpone mientras se pulsa (y, más suave, al pasar el ratón). */
public class RippleDrawable extends Drawable {
    private ColorStateList color;
    private final Drawable content;
    private final Drawable mask;
    private boolean pressed;

    public RippleDrawable(ColorStateList color, Drawable content, Drawable mask) {
        this.color = color;
        this.content = content;
        this.mask = mask;
    }

    public void setColor(ColorStateList c) {
        color = c;
    }

    public Drawable getDrawable(int i) {
        return content;
    }

    public int getNumberOfLayers() {
        return content == null ? 0 : 1;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] s) {
        boolean p = false;
        boolean en = false;
        for (int v : s) {
            if (v == android.R.attr.state_pressed) {
                p = true;
            } else if (v == android.R.attr.state_enabled) {
                en = true;
            }
        }
        p = p && en;
        boolean ch = p != pressed;
        pressed = p;
        if (content != null) {
            ch |= content.setState(s);
        }
        return ch;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        if (content != null) {
            content.setBounds(b);
        }
        if (mask != null) {
            mask.setBounds(b);
        }
    }

    @Override
    public void setAlpha(int a) {
        if (content != null) {
            content.setAlpha(a);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        return content == null ? -1 : content.getIntrinsicWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return content == null ? -1 : content.getIntrinsicHeight();
    }

    @Override
    public java.awt.Shape outlineShape() {
        Drawable d = content != null ? content : mask;
        return d == null ? null : d.outlineShape();
    }

    @Override
    public void draw(Canvas c) {
        if (content != null) {
            content.draw(c);
        }
        if ((pressed || hovered) && color != null) {
            int col = color.getDefaultColor();
            int a = col >>> 24;
            if (!pressed) {
                a = a * 2 / 5;
            }
            if (a > 0) {
                java.awt.Shape s = outlineShape();
                Rect b = getBounds();
                c.g.setPaint(new java.awt.Color((a << 24) | (col & 0xFFFFFF), true));
                if (s != null) {
                    c.g.fill(s);
                } else if (content != null || mask != null) {
                    c.g.fillRect(b.left, b.top, b.width(), b.height());
                } else {
                    // Sin fondo ni máscara: onda "sin límites" → un círculo centrado.
                    float r = Math.min(b.width(), b.height()) / 2f;
                    c.g.fill(new java.awt.geom.Ellipse2D.Float(b.exactCenterX() - r, b.exactCenterY() - r,
                            2 * r, 2 * r));
                }
            }
        }
    }
}
