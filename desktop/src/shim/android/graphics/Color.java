package android.graphics;

public class Color {
    public static final int BLACK = 0xFF000000;
    public static final int DKGRAY = 0xFF444444;
    public static final int GRAY = 0xFF888888;
    public static final int LTGRAY = 0xFFCCCCCC;
    public static final int WHITE = 0xFFFFFFFF;
    public static final int RED = 0xFFFF0000;
    public static final int GREEN = 0xFF00FF00;
    public static final int BLUE = 0xFF0000FF;
    public static final int YELLOW = 0xFFFFFF00;
    public static final int CYAN = 0xFF00FFFF;
    public static final int MAGENTA = 0xFFFF00FF;
    public static final int TRANSPARENT = 0;

    private final int argb;

    private Color(int c) {
        argb = c;
    }

    public static Color valueOf(int c) {
        return new Color(c);
    }

    public int toArgb() {
        return argb;
    }

    public static int alpha(int c) {
        return c >>> 24;
    }

    public static int red(int c) {
        return (c >> 16) & 0xFF;
    }

    public static int green(int c) {
        return (c >> 8) & 0xFF;
    }

    public static int blue(int c) {
        return c & 0xFF;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float luminance(int c) {
        return (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f;
    }

    public static int parseColor(String s) {
        if (s != null && s.startsWith("#")) {
            long v = Long.parseLong(s.substring(1), 16);
            if (s.length() == 7) {
                v |= 0xFF000000L;
            } else if (s.length() != 9) {
                throw new IllegalArgumentException("Unknown color");
            }
            return (int) v;
        }
        throw new IllegalArgumentException("Unknown color");
    }

    public static int HSVToColor(float[] hsv) {
        return HSVToColor(0xFF, hsv);
    }

    public static int HSVToColor(int alpha, float[] hsv) {
        float h = ((hsv[0] % 360f) + 360f) % 360f / 360f;
        float s = Math.max(0, Math.min(1, hsv[1]));
        float v = Math.max(0, Math.min(1, hsv[2]));
        return (alpha << 24) | (java.awt.Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
    }

    public static void colorToHSV(int c, float[] hsv) {
        RGBToHSV(red(c), green(c), blue(c), hsv);
    }

    public static void RGBToHSV(int r, int g, int b, float[] hsv) {
        float[] o = java.awt.Color.RGBtoHSB(r, g, b, null);
        hsv[0] = o[0] * 360f;
        hsv[1] = o[1];
        hsv[2] = o[2];
    }
}
