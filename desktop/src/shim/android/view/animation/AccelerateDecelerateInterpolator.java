package android.view.animation;

public class AccelerateDecelerateInterpolator implements Interpolator {
    @Override
    public float getInterpolation(float t) {
        return (float) (Math.cos((t + 1) * Math.PI) / 2.0) + 0.5f;
    }
}
