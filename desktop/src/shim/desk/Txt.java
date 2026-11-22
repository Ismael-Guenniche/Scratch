package desk;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.util.HashMap;

/** Texto con fuente de reserva: lo que la fuente de la app no tiene (kana, kanji, emoji…) sale con la del sistema. */
public final class Txt {
    public static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private static final HashMap<Font, Font> FALLBACK = new HashMap<Font, Font>();

    private Txt() {
    }

    private static synchronized Font fallback(Font f) {
        Font fb = FALLBACK.get(f);
        if (fb == null) {
            HashMap<TextAttribute, Object> at = new HashMap<TextAttribute, Object>();
            at.put(TextAttribute.FAMILY, Font.DIALOG);
            at.put(TextAttribute.SIZE, f.getSize2D());
            at.put(TextAttribute.WEIGHT, f.isBold() ? TextAttribute.WEIGHT_BOLD : TextAttribute.WEIGHT_REGULAR);
            Object tr = f.getAttributes().get(TextAttribute.TRACKING);
            if (tr != null) {
                at.put(TextAttribute.TRACKING, tr);
            }
            fb = new Font(at);
            if (FALLBACK.size() > 400) {
                FALLBACK.clear();
            }
            FALLBACK.put(f, fb);
        }
        return fb;
    }

    private static boolean plain(String s, Font f) {
        return f.canDisplayUpTo(s) < 0;
    }

    /** Fin del tramo que empieza en i y que la fuente puede (o no puede) dibujar entero. */
    private static int runEnd(String s, int i, Font f, boolean can) {
        int n = s.length();
        while (i < n) {
            int cp = s.codePointAt(i);
            boolean c = f.canDisplay(cp) || cp == ' ';
            if (c != can) {
                break;
            }
            i += Character.charCount(cp);
        }
        return i;
    }

    public static float width(String s, Font f) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        if (plain(s, f)) {
            return (float) f.getStringBounds(s, FRC).getWidth();
        }
        Font fb = fallback(f);
        float w = 0;
        int i = 0;
        int n = s.length();
        while (i < n) {
            int cp = s.codePointAt(i);
            boolean can = f.canDisplay(cp) || cp == ' ';
            int e = runEnd(s, i, f, can);
            if (e == i) {
                e = i + Character.charCount(cp);
            }
            w += (float) (can ? f : fb).getStringBounds(s.substring(i, e), FRC).getWidth();
            i = e;
        }
        return w;
    }

    public static void draw(Graphics2D g, String s, float x, float y, Font f) {
        if (s == null || s.length() == 0) {
            return;
        }
        if (plain(s, f)) {
            g.setFont(f);
            g.drawString(s, x, y);
            return;
        }
        Font fb = fallback(f);
        int i = 0;
        int n = s.length();
        while (i < n) {
            int cp = s.codePointAt(i);
            boolean can = f.canDisplay(cp) || cp == ' ';
            int e = runEnd(s, i, f, can);
            if (e == i) {
                e = i + Character.charCount(cp);
            }
            String part = s.substring(i, e);
            Font use = can ? f : fb;
            g.setFont(use);
            g.drawString(part, x, y);
            x += (float) use.getStringBounds(part, FRC).getWidth();
            i = e;
        }
    }
}
