package android.graphics;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.util.HashMap;

public class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public static final int FILTER_BITMAP_FLAG = 2;
    public static final int DITHER_FLAG = 4;
    public static final int UNDERLINE_TEXT_FLAG = 8;
    public static final int STRIKE_THRU_TEXT_FLAG = 16;
    public static final int FAKE_BOLD_TEXT_FLAG = 32;
    public static final int LINEAR_TEXT_FLAG = 64;
    public static final int SUBPIXEL_TEXT_FLAG = 128;

    public enum Style {
        FILL, STROKE, FILL_AND_STROKE
    }

    public enum Cap {
        BUTT, ROUND, SQUARE
    }

    public enum Join {
        MITER, ROUND, BEVEL
    }

    public enum Align {
        LEFT, CENTER, RIGHT
    }

    public static class FontMetrics {
        public float top;
        public float ascent;
        public float descent;
        public float bottom;
        public float leading;
    }

    public static class FontMetricsInt {
        public int top;
        public int ascent;
        public int descent;
        public int bottom;
        public int leading;
    }

    private int flags;
    private int color = 0xFF000000;
    private Style style = Style.FILL;
    private float strokeWidth;
    private Cap cap = Cap.BUTT;
    private Join join = Join.MITER;
    private float textSize = 12f;
    private Typeface typeface;
    private Align align = Align.LEFT;
    private float letterSpacing;
    private Shader shader;
    private ColorFilter filter;
    private float[] dash;

    private java.awt.Color awtColor;
    private int awtColorFor = 1;
    private BasicStroke stroke;
    private Font font;

    public Paint() {
    }

    public Paint(int flags) {
        this.flags = flags;
    }

    public Paint(Paint o) {
        set(o);
    }

    public void set(Paint o) {
        flags = o.flags;
        color = o.color;
        style = o.style;
        strokeWidth = o.strokeWidth;
        cap = o.cap;
        join = o.join;
        textSize = o.textSize;
        typeface = o.typeface;
        align = o.align;
        letterSpacing = o.letterSpacing;
        shader = o.shader;
        filter = o.filter;
        dash = o.dash;
        stroke = null;
        font = null;
    }

    public void reset() {
        set(new Paint());
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int f) {
        flags = f;
    }

    public void setAntiAlias(boolean b) {
        flags = b ? flags | ANTI_ALIAS_FLAG : flags & ~ANTI_ALIAS_FLAG;
    }

    public boolean isAntiAlias() {
        return (flags & ANTI_ALIAS_FLAG) != 0;
    }

    public void setFilterBitmap(boolean b) {
        flags = b ? flags | FILTER_BITMAP_FLAG : flags & ~FILTER_BITMAP_FLAG;
    }

    public void setDither(boolean b) {
    }

    public void setSubpixelText(boolean b) {
    }

    public void setLinearText(boolean b) {
    }

    public void setFakeBoldText(boolean b) {
        flags = b ? flags | FAKE_BOLD_TEXT_FLAG : flags & ~FAKE_BOLD_TEXT_FLAG;
        font = null;
    }

    public boolean isFakeBoldText() {
        return (flags & FAKE_BOLD_TEXT_FLAG) != 0;
    }

    public void setUnderlineText(boolean b) {
    }

    public int getColor() {
        return color;
    }

    public void setColor(int c) {
        color = c;
    }

    public void setARGB(int a, int r, int g, int b) {
        color = (a << 24) | (r << 16) | (g << 8) | b;
    }

    public int getAlpha() {
        return color >>> 24;
    }

    public void setAlpha(int a) {
        color = (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style s) {
        style = s;
    }

    public float getStrokeWidth() {
        return strokeWidth;
    }

    public void setStrokeWidth(float w) {
        strokeWidth = w;
        stroke = null;
    }

    public Cap getStrokeCap() {
        return cap;
    }

    public void setStrokeCap(Cap c) {
        cap = c;
        stroke = null;
    }

    public Join getStrokeJoin() {
        return join;
    }

    public void setStrokeJoin(Join j) {
        join = j;
        stroke = null;
    }

    public void setStrokeMiter(float m) {
    }

    /** Solo escritorio: trazo discontinuo. */
    public void setDash(float[] d) {
        dash = d;
        stroke = null;
    }

    public float getTextSize() {
        return textSize;
    }

    public void setTextSize(float s) {
        if (s != textSize) {
            textSize = s;
            font = null;
        }
    }

    public Typeface getTypeface() {
        return typeface;
    }

    public Typeface setTypeface(Typeface t) {
        if (t != typeface) {
            typeface = t;
            font = null;
        }
        return t;
    }

    public Align getTextAlign() {
        return align;
    }

    public void setTextAlign(Align a) {
        align = a;
    }

    public float getLetterSpacing() {
        return letterSpacing;
    }

    public void setLetterSpacing(float s) {
        if (s != letterSpacing) {
            letterSpacing = s;
            font = null;
        }
    }

    public void setTextScaleX(float s) {
    }

    public void setTextSkewX(float s) {
    }

    public Shader getShader() {
        return shader;
    }

    public Shader setShader(Shader s) {
        shader = s;
        return s;
    }

    public ColorFilter getColorFilter() {
        return filter;
    }

    public ColorFilter setColorFilter(ColorFilter f) {
        filter = f;
        return f;
    }

    public void setShadowLayer(float r, float dx, float dy, int c) {
    }

    public void clearShadowLayer() {
    }

    public Object setXfermode(Object x) {
        return x;
    }

    public Object setMaskFilter(Object m) {
        return m;
    }

    public Object setPathEffect(Object e) {
        return e;
    }

    // ---- puente con Java2D

    public java.awt.Paint awtPaint() {
        if (shader != null && shader.awt != null) {
            return shader.awt;
        }
        int c = color;
        if (filter instanceof PorterDuffColorFilter) {
            c = (((PorterDuffColorFilter) filter).color & 0xFFFFFF) | (c & 0xFF000000);
        }
        if (awtColor == null || awtColorFor != c) {
            awtColor = new java.awt.Color(c, true);
            awtColorFor = c;
        }
        return awtColor;
    }

    public BasicStroke awtStroke() {
        if (stroke == null) {
            int c = cap == Cap.ROUND ? BasicStroke.CAP_ROUND : cap == Cap.SQUARE ? BasicStroke.CAP_SQUARE
                    : BasicStroke.CAP_BUTT;
            int j = join == Join.ROUND ? BasicStroke.JOIN_ROUND : join == Join.BEVEL ? BasicStroke.JOIN_BEVEL
                    : BasicStroke.JOIN_MITER;
            float w = strokeWidth <= 0 ? 1f : strokeWidth;
            stroke = dash != null ? new BasicStroke(w, c, j, 10f, dash, 0f) : new BasicStroke(w, c, j, 10f);
        }
        return stroke;
    }

    private static final HashMap<String, Font> FONTS = new HashMap<String, Font>();

    public Font awtFont() {
        if (font == null) {
            Typeface tf = typeface == null ? Typeface.DEFAULT : typeface;
            String key = System.identityHashCode(tf.font) + "|" + textSize + "|" + letterSpacing + "|"
                    + (flags & FAKE_BOLD_TEXT_FLAG);
            synchronized (FONTS) {
                font = FONTS.get(key);
                if (font == null) {
                    Font f = tf.font.deriveFont(textSize);
                    if ((flags & FAKE_BOLD_TEXT_FLAG) != 0 && !f.isBold()) {
                        f = f.deriveFont(Font.BOLD);
                    }
                    if (letterSpacing != 0) {
                        HashMap<TextAttribute, Object> at = new HashMap<TextAttribute, Object>();
                        at.put(TextAttribute.TRACKING, letterSpacing);
                        f = f.deriveFont(at);
                    }
                    if (FONTS.size() > 600) {
                        FONTS.clear();
                    }
                    FONTS.put(key, f);
                    font = f;
                }
            }
        }
        return font;
    }

    public float measureText(String s) {
        return desk.Txt.width(s, awtFont());
    }

    public float measureText(String s, int start, int end) {
        return desk.Txt.width(s.substring(start, end), awtFont());
    }

    public float measureText(CharSequence s, int start, int end) {
        return desk.Txt.width(s.subSequence(start, end).toString(), awtFont());
    }

    public float measureText(char[] c, int off, int n) {
        return desk.Txt.width(new String(c, off, n), awtFont());
    }

    public int getTextWidths(String s, float[] widths) {
        for (int i = 0; i < s.length(); i++) {
            widths[i] = measureText(s, i, i + 1);
        }
        return s.length();
    }

    /** Cuántos caracteres caben en maxWidth. */
    public int breakText(String s, boolean forward, float maxWidth, float[] measured) {
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (measureText(s, 0, mid) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        if (measured != null && measured.length > 0) {
            measured[0] = measureText(s, 0, lo);
        }
        return lo;
    }

    public void getTextBounds(String s, int start, int end, Rect out) {
        if (end <= start) {
            out.set(0, 0, 0, 0);
            return;
        }
        Font f = awtFont();
        String t = s.substring(start, end);
        java.awt.geom.Rectangle2D r;
        if (f.canDisplayUpTo(t) < 0) {
            r = f.createGlyphVector(desk.Txt.FRC, t).getVisualBounds();
        } else {
            LineMetrics lm = f.getLineMetrics(t, desk.Txt.FRC);
            r = new java.awt.geom.Rectangle2D.Float(0, -lm.getAscent() * 0.8f, desk.Txt.width(t, f),
                    lm.getAscent() * 0.8f);
        }
        out.set((int) Math.floor(r.getMinX()), (int) Math.floor(r.getMinY()), (int) Math.ceil(r.getMaxX()),
                (int) Math.ceil(r.getMaxY()));
    }

    public void getTextBounds(char[] c, int off, int n, Rect out) {
        getTextBounds(new String(c, off, n), 0, n, out);
    }

    private LineMetrics lm() {
        return awtFont().getLineMetrics("Hg", desk.Txt.FRC);
    }

    public float ascent() {
        return -lm().getAscent();
    }

    public float descent() {
        return lm().getDescent();
    }

    public FontMetrics getFontMetrics() {
        FontMetrics m = new FontMetrics();
        getFontMetrics(m);
        return m;
    }

    public float getFontMetrics(FontMetrics m) {
        LineMetrics l = lm();
        if (m != null) {
            m.ascent = -l.getAscent();
            m.descent = l.getDescent();
            m.top = m.ascent * 1.08f;
            m.bottom = m.descent * 1.08f;
            m.leading = l.getLeading();
        }
        return l.getAscent() + l.getDescent() + l.getLeading();
    }

    public FontMetricsInt getFontMetricsInt() {
        FontMetrics f = getFontMetrics();
        FontMetricsInt m = new FontMetricsInt();
        m.ascent = Math.round(f.ascent);
        m.descent = Math.round(f.descent);
        m.top = Math.round(f.top);
        m.bottom = Math.round(f.bottom);
        m.leading = Math.round(f.leading);
        return m;
    }

    public float getFontSpacing() {
        return getFontMetrics(null);
    }
}
