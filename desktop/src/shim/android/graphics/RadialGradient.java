package android.graphics;

public class RadialGradient extends Shader {
    public RadialGradient(float cx, float cy, float r, int c0, int c1, TileMode tm) {
        this(cx, cy, r, new int[] {c0, c1}, null, tm);
    }

    public RadialGradient(float cx, float cy, float r, int[] colors, float[] pos, TileMode tm) {
        awt = new java.awt.RadialGradientPaint(cx, cy, Math.max(0.01f, r), LinearGradient.stops(colors, pos),
                LinearGradient.awtColors(colors));
    }
}
