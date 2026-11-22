package android.graphics;

public class LinearGradient extends Shader {
    public LinearGradient(float x0, float y0, float x1, float y1, int c0, int c1, TileMode tm) {
        this(x0, y0, x1, y1, new int[] {c0, c1}, null, tm);
    }

    public LinearGradient(float x0, float y0, float x1, float y1, int[] colors, float[] pos, TileMode tm) {
        if (x0 == x1 && y0 == y1) {
            awt = new java.awt.Color(colors[0], true);
            return;
        }
        awt = new java.awt.LinearGradientPaint(new java.awt.geom.Point2D.Float(x0, y0),
                new java.awt.geom.Point2D.Float(x1, y1), stops(colors, pos), awtColors(colors),
                tm == TileMode.REPEAT ? java.awt.MultipleGradientPaint.CycleMethod.REPEAT
                        : tm == TileMode.MIRROR ? java.awt.MultipleGradientPaint.CycleMethod.REFLECT
                        : java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE);
    }

    static float[] stops(int[] colors, float[] pos) {
        float[] f = new float[colors.length];
        float last = -1;
        for (int i = 0; i < f.length; i++) {
            float v = pos != null ? pos[i] : (f.length == 1 ? 0 : (float) i / (f.length - 1));
            if (v <= last) {
                v = Math.min(1f, last + 1e-5f);
            }
            f[i] = v;
            last = v;
        }
        return f;
    }

    static java.awt.Color[] awtColors(int[] colors) {
        java.awt.Color[] c = new java.awt.Color[colors.length];
        for (int i = 0; i < c.length; i++) {
            c[i] = new java.awt.Color(colors[i], true);
        }
        return c;
    }
}
