package android.graphics;

import android.content.res.AssetManager;
import java.awt.Font;
import java.io.InputStream;

public class Typeface {
    public static final int NORMAL = 0;
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int BOLD_ITALIC = 3;

    private static final String SANS = pick("Segoe UI", Font.SANS_SERIF);
    private static final String MONO = pick("Consolas", Font.MONOSPACED);
    public static final Typeface DEFAULT = new Typeface(new Font(SANS, Font.PLAIN, 12));
    public static final Typeface DEFAULT_BOLD = new Typeface(new Font(SANS, Font.BOLD, 12));
    public static final Typeface SANS_SERIF = DEFAULT;
    public static final Typeface SERIF = new Typeface(new Font(Font.SERIF, Font.PLAIN, 12));
    public static final Typeface MONOSPACE = new Typeface(new Font(MONO, Font.PLAIN, 12));

    public final Font font;

    private static String pick(String want, String otherwise) {
        try {
            Font f = new Font(want, Font.PLAIN, 12);
            if (f.getFamily().equalsIgnoreCase(want)) {
                return want;
            }
        } catch (Throwable ignored) {
        }
        return otherwise;
    }

    public Typeface(Font f) {
        font = f;
    }

    public static Typeface createFromAsset(AssetManager am, String path) {
        try {
            InputStream in = am.open(path);
            try {
                return new Typeface(Font.createFont(Font.TRUETYPE_FONT, in));
            } finally {
                in.close();
            }
        } catch (Exception e) {
            throw new RuntimeException("Font asset not found " + path);
        }
    }

    public static Typeface createFromFile(java.io.File f) {
        try {
            return new Typeface(Font.createFont(Font.TRUETYPE_FONT, f));
        } catch (Exception e) {
            throw new RuntimeException("Font not found " + f);
        }
    }

    public static Typeface createFromFile(String path) {
        return createFromFile(new java.io.File(path));
    }

    private static int awtStyle(int s) {
        return ((s & BOLD) != 0 ? Font.BOLD : 0) | ((s & ITALIC) != 0 ? Font.ITALIC : 0);
    }

    public static Typeface create(Typeface base, int style) {
        Typeface b = base == null ? DEFAULT : base;
        return new Typeface(b.font.deriveFont(awtStyle(style)));
    }

    public static Typeface create(String family, int style) {
        String fam = family == null ? SANS : family;
        if ("monospace".equalsIgnoreCase(fam)) {
            fam = MONO;
        } else if (fam.toLowerCase().startsWith("sans")) {
            fam = SANS;
        } else if ("serif".equalsIgnoreCase(fam)) {
            fam = Font.SERIF;
        }
        return new Typeface(new Font(fam, awtStyle(style), 12));
    }

    public static Typeface defaultFromStyle(int style) {
        return create(DEFAULT, style);
    }

    public int getStyle() {
        return (font.isBold() ? BOLD : 0) | (font.isItalic() ? ITALIC : 0);
    }

    public boolean isBold() {
        return font.isBold();
    }

    public boolean isItalic() {
        return font.isItalic();
    }
}
