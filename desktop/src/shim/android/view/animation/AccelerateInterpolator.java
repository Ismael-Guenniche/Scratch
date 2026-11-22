package android.view.animation;

public class AccelerateInterpolator implements Interpolator {
    private final double f;

    public AccelerateInterpolator() {
        this(1f);
    }

    public AccelerateInterpolator(float factor) {
        f = 2.0 * factor;
    }

    @Override
    public float getInterpolation(float t) {
        return (float) Math.pow(t, f);
    }
}
