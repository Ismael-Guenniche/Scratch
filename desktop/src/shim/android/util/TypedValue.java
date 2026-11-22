package android.util;

public class TypedValue {
    public static final int COMPLEX_UNIT_PX = 0;
    public static final int COMPLEX_UNIT_DIP = 1;
    public static final int COMPLEX_UNIT_SP = 2;

    public static float applyDimension(int unit, float v, DisplayMetrics dm) {
        return unit == COMPLEX_UNIT_PX ? v : v * dm.density;
    }
}
