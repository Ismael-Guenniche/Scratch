package android.graphics;

import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

public class Path {
    public enum Direction {
        CW, CCW
    }

    public enum FillType {
        WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD
    }

    public final Path2D.Float p = new Path2D.Float();

    public Path() {
    }

    public Path(Path o) {
        p.append(o.p, false);
    }

    public void reset() {
        p.reset();
    }

    public void rewind() {
        p.reset();
    }

    public void set(Path o) {
        p.reset();
        p.append(o.p, false);
    }

    public boolean isEmpty() {
        return p.getCurrentPoint() == null;
    }

    public void setFillType(FillType t) {
        p.setWindingRule(t == FillType.EVEN_ODD ? Path2D.WIND_EVEN_ODD : Path2D.WIND_NON_ZERO);
    }

    public void moveTo(float x, float y) {
        p.moveTo(x, y);
    }

    public void lineTo(float x, float y) {
        if (p.getCurrentPoint() == null) {
            p.moveTo(x, y);
        } else {
            p.lineTo(x, y);
        }
    }

    public void rMoveTo(float dx, float dy) {
        java.awt.geom.Point2D c = p.getCurrentPoint();
        p.moveTo((c == null ? 0 : c.getX()) + dx, (c == null ? 0 : c.getY()) + dy);
    }

    public void rLineTo(float dx, float dy) {
        java.awt.geom.Point2D c = p.getCurrentPoint();
        lineTo((float) ((c == null ? 0 : c.getX()) + dx), (float) ((c == null ? 0 : c.getY()) + dy));
    }

    public void quadTo(float x1, float y1, float x2, float y2) {
        p.quadTo(x1, y1, x2, y2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        p.curveTo(x1, y1, x2, y2, x3, y3);
    }

    public void close() {
        if (p.getCurrentPoint() != null) {
            p.closePath();
        }
    }

    public void addCircle(float cx, float cy, float r, Direction d) {
        p.append(new Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r), false);
    }

    public void addOval(RectF o, Direction d) {
        p.append(new Ellipse2D.Float(o.left, o.top, o.width(), o.height()), false);
    }

    public void addOval(float l, float t, float r, float b, Direction d) {
        p.append(new Ellipse2D.Float(l, t, r - l, b - t), false);
    }

    public void addRect(RectF r, Direction d) {
        p.append(new Rectangle2D.Float(r.left, r.top, r.width(), r.height()), false);
    }

    public void addRect(float l, float t, float r, float b, Direction d) {
        p.append(new Rectangle2D.Float(l, t, r - l, b - t), false);
    }

    public void addRoundRect(RectF r, float rx, float ry, Direction d) {
        p.append(new RoundRectangle2D.Float(r.left, r.top, r.width(), r.height(), 2 * rx, 2 * ry), false);
    }

    public void addRoundRect(float l, float t, float r, float b, float rx, float ry, Direction d) {
        p.append(new RoundRectangle2D.Float(l, t, r - l, b - t, 2 * rx, 2 * ry), false);
    }

    public void addRoundRect(RectF r, float[] radii, Direction d) {
        p.append(android.graphics.drawable.GradientDrawable.roundRect(r.left, r.top, r.right, r.bottom, radii),
                false);
    }

    public void addArc(RectF o, float start, float sweep) {
        p.append(new Arc2D.Float(o.left, o.top, o.width(), o.height(), -start, -sweep, Arc2D.OPEN), false);
    }

    public void arcTo(RectF o, float start, float sweep) {
        arcTo(o, start, sweep, false);
    }

    public void arcTo(RectF o, float start, float sweep, boolean forceMove) {
        p.append(new Arc2D.Float(o.left, o.top, o.width(), o.height(), -start, -sweep, Arc2D.OPEN),
                !forceMove && p.getCurrentPoint() != null);
    }

    public void addPath(Path o) {
        p.append(o.p, false);
    }

    public void offset(float dx, float dy) {
        p.transform(java.awt.geom.AffineTransform.getTranslateInstance(dx, dy));
    }

    public void computeBounds(RectF out, boolean exact) {
        Rectangle2D b = p.getBounds2D();
        out.set((float) b.getMinX(), (float) b.getMinY(), (float) b.getMaxX(), (float) b.getMaxY());
    }
}
