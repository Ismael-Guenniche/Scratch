package android.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Rect;
import java.awt.BasicStroke;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

public class GradientDrawable extends Drawable {
    public static final int RECTANGLE = 0;
    public static final int OVAL = 1;
    public static final int LINE = 2;
    public static final int RING = 3;
    public static final int LINEAR_GRADIENT = 0;
    public static final int RADIAL_GRADIENT = 1;
    public static final int SWEEP_GRADIENT = 2;

    public enum Orientation {
        TOP_BOTTOM, TR_BL, RIGHT_LEFT, BR_TL, BOTTOM_TOP, BL_TR, LEFT_RIGHT, TL_BR
    }

    private int shape = RECTANGLE;
    private int color;
    private boolean hasColor;
    private int[] colors;
    private Orientation orientation = Orientation.TOP_BOTTOM;
    private int gradientType = LINEAR_GRADIENT;
    private float radius;
    private float[] radii;
    private int strokeWidth;
    private int strokeColor;
    private float dashWidth;
    private float dashGap;
    private int alpha = 255;
    private int iw = -1;
    private int ih = -1;

    public GradientDrawable() {
    }

    public GradientDrawable(Orientation o, int[] colors) {
        orientation = o;
        this.colors = colors;
    }

    public void setShape(int s) {
        shape = s;
        invalidateSelf();
    }

    public int getShape() {
        return shape;
    }

    public void setColor(int c) {
        color = c;
        hasColor = true;
        colors = null;
        invalidateSelf();
    }

    public void setColor(ColorStateList l) {
        if (l == null) {
            hasColor = false;
        } else {
            setColor(l.getDefaultColor());
        }
    }

    public void setColors(int[] c) {
        colors = c;
        invalidateSelf();
    }

    public void setOrientation(Orientation o) {
        orientation = o;
    }

    public void setGradientType(int t) {
        gradientType = t;
    }

    public void setGradientRadius(float r) {
    }

    public void setCornerRadius(float r) {
        radius = Math.max(0, r);
        radii = null;
        invalidateSelf();
    }

    public float getCornerRadius() {
        return radius;
    }

    public void setCornerRadii(float[] r) {
        radii = r;
        invalidateSelf();
    }

    public void setStroke(int w, int c) {
        setStroke(w, c, 0, 0);
    }

    public void setStroke(int w, ColorStateList c) {
        setStroke(w, c == null ? 0 : c.getDefaultColor(), 0, 0);
    }

    public void setStroke(int w, int c, float dashW, float gap) {
        strokeWidth = w;
        strokeColor = c;
        dashWidth = dashW;
        dashGap = gap;
        invalidateSelf();
    }

    public void setSize(int w, int h) {
        iw = w;
        ih = h;
    }

    @Override
    public int getIntrinsicWidth() {
        return iw;
    }

    @Override
    public int getIntrinsicHeight() {
        return ih;
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    /** Rectángulo con un radio por esquina: radii = {tlx, tly, trx, try, brx, bry, blx, bly}. */
    public static Shape roundRect(float l, float t, float r, float b, float[] rr) {
        float w = r - l;
        float h = b - t;
        float m = Math.min(w, h) / 2f;
        float tl = Math.min(rr[0], m);
        float tr = Math.min(rr[2], m);
        float br = Math.min(rr[4], m);
        float bl = Math.min(rr[6], m);
        final float k = 0.5523f;
        Path2D.Float p = new Path2D.Float();
        p.moveTo(l + tl, t);
        p.lineTo(r - tr, t);
        p.curveTo(r - tr + tr * k, t, r, t + tr - tr * k, r, t + tr);
        p.lineTo(r, b - br);
        p.curveTo(r, b - br + br * k, r - br + br * k, b, r - br, b);
        p.lineTo(l + bl, b);
        p.curveTo(l + bl - bl * k, b, l, b - bl + bl * k, l, b - bl);
        p.lineTo(l, t + tl);
        p.curveTo(l, t + tl - tl * k, l + tl - tl * k, t, l + tl, t);
        p.closePath();
        return p;
    }

    private Shape shape(float inset) {
        Rect bd = getBounds();
        float l = bd.left + inset;
        float t = bd.top + inset;
        float w = bd.width() - 2 * inset;
        float h = bd.height() - 2 * inset;
        if (w <= 0 || h <= 0) {
            return null;
        }
        if (shape == OVAL) {
            return new Ellipse2D.Float(l, t, w, h);
        }
        if (radii != null) {
            float[] rr = new float[8];
            for (int i = 0; i < 8; i++) {
                rr[i] = Math.max(0, radii[i] - inset);
            }
            return roundRect(l, t, l + w, t + h, rr);
        }
        if (radius <= 0) {
            return new Rectangle2D.Float(l, t, w, h);
        }
        float d = Math.min(2 * Math.max(0, radius - inset), Math.min(w, h));
        return new RoundRectangle2D.Float(l, t, w, h, d, d);
    }

    @Override
    public Shape outlineShape() {
        return shape(0);
    }

    @Override
    public void draw(Canvas c) {
        Rect bd = getBounds();
        if (bd.width() <= 0 || bd.height() <= 0) {
            return;
        }
        java.awt.Graphics2D g = c.g;
        java.awt.Composite keep = null;
        if (alpha < 255) {
            keep = g.getComposite();
            c.mulAlpha(alpha / 255f);
        }
        boolean stroke = strokeWidth > 0 && (strokeColor >>> 24) != 0;
        Shape fill = shape(stroke ? strokeWidth / 2f : 0);
        if (fill != null) {
            if (colors != null && colors.length > 1) {
                g.setPaint(gradient(bd));
                g.fill(fill);
            } else if (colors != null && colors.length == 1) {
                g.setPaint(new java.awt.Color(colors[0], true));
                g.fill(fill);
            } else if (hasColor && (color >>> 24) != 0) {
                g.setPaint(new java.awt.Color(color, true));
                g.fill(fill);
            }
            if (stroke) {
                g.setPaint(new java.awt.Color(strokeColor, true));
                g.setStroke(dashWidth > 0 ? new BasicStroke(strokeWidth, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER, 10f, new float[] {dashWidth, Math.max(0.1f, dashGap)}, 0f)
                        : new BasicStroke(strokeWidth));
                g.draw(fill);
            }
        }
        if (keep != null) {
            g.setComposite(keep);
        }
    }

    private java.awt.Paint gradient(Rect b) {
        java.awt.Color[] cs = new java.awt.Color[colors.length];
        float[] st = new float[colors.length];
        for (int i = 0; i < cs.length; i++) {
            cs[i] = new java.awt.Color(colors[i], true);
            st[i] = (float) i / (cs.length - 1);
        }
        if (gradientType == RADIAL_GRADIENT) {
            return new java.awt.RadialGradientPaint(b.exactCenterX(), b.exactCenterY(),
                    Math.max(1f, Math.max(b.width(), b.height()) / 2f), st, cs);
        }
        float x0 = b.left;
        float y0 = b.top;
        float x1 = b.left;
        float y1 = b.bottom;
        switch (orientation) {
            case BOTTOM_TOP:
                y0 = b.bottom;
                y1 = b.top;
                break;
            case LEFT_RIGHT:
                x1 = b.right;
                y1 = b.top;
                break;
            case RIGHT_LEFT:
                x0 = b.right;
                y1 = b.top;
                break;
            case TL_BR:
                x1 = b.right;
                break;
            case BR_TL:
                x0 = b.right;
                y0 = b.bottom;
                y1 = b.top;
                break;
            case TR_BL:
                x0 = b.right;
                break;
            case BL_TR:
                y0 = b.bottom;
                x1 = b.right;
                y1 = b.top;
                break;
            default:
                break;
        }
        return new java.awt.LinearGradientPaint(x0, y0, x1, y1, st, cs);
    }
}
