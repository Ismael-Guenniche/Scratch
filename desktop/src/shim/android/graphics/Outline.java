package android.graphics;

public final class Outline {
    public java.awt.Shape shape;

    public void setRect(int l, int t, int r, int b) {
        shape = new java.awt.geom.Rectangle2D.Float(l, t, r - l, b - t);
    }

    public void setRect(Rect r) {
        setRect(r.left, r.top, r.right, r.bottom);
    }

    public void setRoundRect(int l, int t, int r, int b, float radius) {
        float d = Math.min(2 * radius, Math.min(r - l, b - t));
        shape = new java.awt.geom.RoundRectangle2D.Float(l, t, r - l, b - t, d, d);
    }

    public void setRoundRect(Rect r, float radius) {
        setRoundRect(r.left, r.top, r.right, r.bottom, radius);
    }

    public void setOval(int l, int t, int r, int b) {
        shape = new java.awt.geom.Ellipse2D.Float(l, t, r - l, b - t);
    }

    public void setOval(Rect r) {
        setOval(r.left, r.top, r.right, r.bottom);
    }

    public void setEmpty() {
        shape = null;
    }

    public void setAlpha(float a) {
    }
}
