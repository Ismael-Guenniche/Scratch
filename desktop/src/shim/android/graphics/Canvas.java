package android.graphics;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;

/** Canvas de Android sobre un Graphics2D de Java2D. */
public class Canvas {
    public Graphics2D g;
    private int width;
    private int height;
    private final ArrayList<Object[]> stack = new ArrayList<Object[]>();
    private final Ellipse2D.Float oval = new Ellipse2D.Float();
    private final Rectangle2D.Float rect = new Rectangle2D.Float();
    private final RoundRectangle2D.Float rrect = new RoundRectangle2D.Float();
    private final Line2D.Float line = new Line2D.Float();

    public Canvas() {
    }

    public Canvas(Bitmap b) {
        setBitmap(b);
    }

    public Canvas(Graphics2D g, int w, int h) {
        this.g = g;
        width = w;
        height = h;
        hints(g);
    }

    public static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    public void setBitmap(Bitmap b) {
        g = b.img.createGraphics();
        width = b.getWidth();
        height = b.getHeight();
        hints(g);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isHardwareAccelerated() {
        return false;
    }

    // ---- estado

    public int save() {
        stack.add(new Object[] {g.getTransform(), g.getClip(), g.getComposite()});
        return stack.size() - 1;
    }

    public void restore() {
        if (stack.isEmpty()) {
            return;
        }
        Object[] s = stack.remove(stack.size() - 1);
        g.setTransform((AffineTransform) s[0]);
        g.setClip((Shape) s[1]);
        g.setComposite((Composite) s[2]);
    }

    public int getSaveCount() {
        return stack.size();
    }

    public void restoreToCount(int n) {
        while (stack.size() > n) {
            restore();
        }
    }

    /** Multiplica la opacidad de todo lo que se pinte hasta el restore. */
    public void mulAlpha(float a) {
        float cur = 1f;
        Composite c = g.getComposite();
        if (c instanceof AlphaComposite) {
            cur = ((AlphaComposite) c).getAlpha();
        }
        g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, cur * a))));
    }

    public int saveLayerAlpha(float l, float t, float r, float b, int alpha) {
        int n = save();
        mulAlpha(alpha / 255f);
        return n;
    }

    public int saveLayerAlpha(RectF r, int alpha) {
        return saveLayerAlpha(0, 0, 0, 0, alpha);
    }

    public int saveLayer(RectF r, Paint p) {
        int n = save();
        if (p != null) {
            mulAlpha(p.getAlpha() / 255f);
        }
        return n;
    }

    public int saveLayer(float l, float t, float r, float b, Paint p) {
        return saveLayer(null, p);
    }

    public void translate(float dx, float dy) {
        g.translate((double) dx, (double) dy);
    }

    public void scale(float sx, float sy) {
        g.scale(sx, sy);
    }

    public void scale(float sx, float sy, float px, float py) {
        g.translate((double) px, (double) py);
        g.scale(sx, sy);
        g.translate((double) -px, (double) -py);
    }

    public void rotate(float deg) {
        g.rotate(Math.toRadians(deg));
    }

    public void rotate(float deg, float px, float py) {
        g.rotate(Math.toRadians(deg), px, py);
    }

    public boolean clipRect(float l, float t, float r, float b) {
        rect.setRect(l, t, r - l, b - t);
        g.clip(rect);
        return true;
    }

    public boolean clipRect(int l, int t, int r, int b) {
        return clipRect((float) l, (float) t, (float) r, (float) b);
    }

    public boolean clipRect(Rect r) {
        return clipRect((float) r.left, (float) r.top, (float) r.right, (float) r.bottom);
    }

    public boolean clipRect(RectF r) {
        return clipRect(r.left, r.top, r.right, r.bottom);
    }

    public boolean clipPath(Path p) {
        g.clip(p.p);
        return true;
    }

    public void clipShape(Shape s) {
        g.clip(s);
    }

    public boolean getClipBounds(Rect out) {
        java.awt.Rectangle r = g.getClipBounds();
        if (r == null) {
            out.set(0, 0, width, height);
            return true;
        }
        out.set(r.x, r.y, r.x + r.width, r.y + r.height);
        return !r.isEmpty();
    }

    public boolean quickReject(float l, float t, float r, float b) {
        java.awt.Rectangle c = g.getClipBounds();
        return c != null && !(l < c.x + c.width && r > c.x && t < c.y + c.height && b > c.y);
    }

    public boolean quickReject(RectF r) {
        return quickReject(r.left, r.top, r.right, r.bottom);
    }

    // ---- formas

    private void shape(Shape s, Paint p) {
        g.setPaint(p.awtPaint());
        Paint.Style st = p.getStyle();
        if (st != Paint.Style.STROKE) {
            g.fill(s);
        }
        if (st != Paint.Style.FILL) {
            g.setStroke(p.awtStroke());
            g.draw(s);
        }
    }

    public void drawColor(int c) {
        if ((c >>> 24) == 0) {
            return;
        }
        java.awt.Rectangle r = g.getClipBounds();
        g.setPaint(new java.awt.Color(c, true));
        if (r != null) {
            g.fillRect(r.x, r.y, r.width, r.height);
        } else {
            AffineTransform t = g.getTransform();
            g.setTransform(new AffineTransform());
            g.fillRect(0, 0, 100000, 100000);
            g.setTransform(t);
        }
    }

    public void drawColor(int c, PorterDuff.Mode m) {
        if (m == PorterDuff.Mode.CLEAR || m == PorterDuff.Mode.SRC) {
            Composite old = g.getComposite();
            g.setComposite(m == PorterDuff.Mode.CLEAR ? AlphaComposite.Clear : AlphaComposite.Src);
            java.awt.Rectangle r = g.getClipBounds();
            g.setPaint(new java.awt.Color(c, true));
            if (r != null) {
                g.fillRect(r.x, r.y, r.width, r.height);
            } else {
                g.fillRect(0, 0, width, height);
            }
            g.setComposite(old);
        } else {
            drawColor(c);
        }
    }

    public void drawARGB(int a, int r, int gr, int b) {
        drawColor(Color.argb(a, r, gr, b));
    }

    public void drawRGB(int r, int gr, int b) {
        drawColor(Color.rgb(r, gr, b));
    }

    public void drawPaint(Paint p) {
        java.awt.Rectangle r = g.getClipBounds();
        g.setPaint(p.awtPaint());
        if (r != null) {
            g.fillRect(r.x, r.y, r.width, r.height);
        }
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        rect.setRect(l, t, r - l, b - t);
        shape(rect, p);
    }

    public void drawRect(RectF r, Paint p) {
        drawRect(r.left, r.top, r.right, r.bottom, p);
    }

    public void drawRect(Rect r, Paint p) {
        drawRect(r.left, r.top, r.right, r.bottom, p);
    }

    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        float w = r - l;
        float h = b - t;
        rrect.setRoundRect(l, t, w, h, Math.min(2 * rx, w), Math.min(2 * ry, h));
        shape(rrect, p);
    }

    public void drawRoundRect(RectF r, float rx, float ry, Paint p) {
        drawRoundRect(r.left, r.top, r.right, r.bottom, rx, ry, p);
    }

    public void drawCircle(float cx, float cy, float r, Paint p) {
        oval.setFrame(cx - r, cy - r, 2 * r, 2 * r);
        shape(oval, p);
    }

    public void drawOval(float l, float t, float r, float b, Paint p) {
        oval.setFrame(l, t, r - l, b - t);
        shape(oval, p);
    }

    public void drawOval(RectF r, Paint p) {
        drawOval(r.left, r.top, r.right, r.bottom, p);
    }

    public void drawArc(float l, float t, float r, float b, float start, float sweep, boolean center, Paint p) {
        if (Math.abs(sweep) >= 360f) {
            drawOval(l, t, r, b, p);
            return;
        }
        shape(new Arc2D.Float(l, t, r - l, b - t, -start, -sweep, center ? Arc2D.PIE : Arc2D.OPEN), p);
    }

    public void drawArc(RectF o, float start, float sweep, boolean center, Paint p) {
        drawArc(o.left, o.top, o.right, o.bottom, start, sweep, center, p);
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint p) {
        line.setLine(x0, y0, x1, y1);
        g.setPaint(p.awtPaint());
        g.setStroke(p.awtStroke());
        g.draw(line);
    }

    public void drawLines(float[] pts, Paint p) {
        drawLines(pts, 0, pts.length, p);
    }

    public void drawLines(float[] pts, int off, int count, Paint p) {
        g.setPaint(p.awtPaint());
        g.setStroke(p.awtStroke());
        for (int i = off; i + 3 < off + count; i += 4) {
            line.setLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
            g.draw(line);
        }
    }

    public void drawPoint(float x, float y, Paint p) {
        float w = Math.max(1f, p.getStrokeWidth());
        g.setPaint(p.awtPaint());
        if (p.getStrokeCap() == Paint.Cap.ROUND) {
            oval.setFrame(x - w / 2, y - w / 2, w, w);
            g.fill(oval);
        } else {
            rect.setRect(x - w / 2, y - w / 2, w, w);
            g.fill(rect);
        }
    }

    public void drawPoints(float[] pts, Paint p) {
        for (int i = 0; i + 1 < pts.length; i += 2) {
            drawPoint(pts[i], pts[i + 1], p);
        }
    }

    public void drawPath(Path path, Paint p) {
        shape(path.p, p);
    }

    // ---- texto

    public void drawText(String s, float x, float y, Paint p) {
        if (s == null || s.length() == 0) {
            return;
        }
        java.awt.Font f = p.awtFont();
        Paint.Align a = p.getTextAlign();
        if (a != Paint.Align.LEFT) {
            float w = desk.Txt.width(s, f);
            x -= a == Paint.Align.CENTER ? w / 2f : w;
        }
        g.setPaint(p.awtPaint());
        desk.Txt.draw(g, s, x, y, f);
    }

    public void drawText(String s, int start, int end, float x, float y, Paint p) {
        drawText(s.substring(start, end), x, y, p);
    }

    public void drawText(CharSequence s, int start, int end, float x, float y, Paint p) {
        drawText(s.subSequence(start, end).toString(), x, y, p);
    }

    public void drawText(char[] c, int off, int n, float x, float y, Paint p) {
        drawText(new String(c, off, n), x, y, p);
    }

    // ---- imágenes

    private Composite alphaFor(Paint p) {
        if (p != null && p.getAlpha() < 255) {
            Composite old = g.getComposite();
            mulAlpha(p.getAlpha() / 255f);
            return old;
        }
        return null;
    }

    public void drawBitmap(Bitmap b, float left, float top, Paint p) {
        if (b == null) {
            return;
        }
        Composite old = alphaFor(p);
        AffineTransform t = AffineTransform.getTranslateInstance(left, top);
        g.drawImage(b.img, t, null);
        if (old != null) {
            g.setComposite(old);
        }
    }

    public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p) {
        if (b == null || dst.width() <= 0 || dst.height() <= 0) {
            return;
        }
        int sx = src == null ? 0 : src.left;
        int sy = src == null ? 0 : src.top;
        int sw = src == null ? b.getWidth() : src.width();
        int sh = src == null ? b.getHeight() : src.height();
        if (sw <= 0 || sh <= 0) {
            return;
        }
        Composite old = alphaFor(p);
        AffineTransform keep = g.getTransform();
        g.translate((double) dst.left, (double) dst.top);
        g.scale(dst.width() / sw, dst.height() / sh);
        g.drawImage(b.img, 0, 0, sw, sh, sx, sy, sx + sw, sy + sh, null);
        g.setTransform(keep);
        if (old != null) {
            g.setComposite(old);
        }
    }

    public void drawBitmap(Bitmap b, Rect src, Rect dst, Paint p) {
        drawBitmap(b, src, new RectF(dst), p);
    }
}
