package android.view.animation;

public class DecelerateInterpolator implements Interpolator {
    private final double f;

    public DecelerateInterpolator() {
        this(1f);
    }

    public DecelerateInterpolator(float factor) {
        f = 2.0 * factor;
    }

    @Override
    public float getInterpolation(float t) {
        return (float) (1.0 - Math.pow(1.0 - t, f));
    }
}
