package android.view;

public final class VelocityTracker {
    private final float[] xs = new float[8];
    private final float[] ys = new float[8];
    private final long[] ts = new long[8];
    private int n;
    private float vx;
    private float vy;

    public static VelocityTracker obtain() {
        return new VelocityTracker();
    }

    public void addMovement(MotionEvent e) {
        int i = n % xs.length;
        xs[i] = e.getRawX();
        ys[i] = e.getRawY();
        ts[i] = e.getEventTime();
        n++;
    }

    public void computeCurrentVelocity(int units) {
        computeCurrentVelocity(units, Float.MAX_VALUE);
    }

    public void computeCurrentVelocity(int units, float max) {
        vx = 0;
        vy = 0;
        if (n < 2) {
            return;
        }
        int last = (n - 1) % xs.length;
        int count = Math.min(n, xs.length);
        int first = last;
        for (int k = 1; k < count; k++) {
            int i = ((n - 1 - k) % xs.length + xs.length) % xs.length;
            if (ts[last] - ts[i] > 100) {
                break;
            }
            first = i;
        }
        long dt = ts[last] - ts[first];
        if (dt <= 0) {
            return;
        }
        vx = Math.max(-max, Math.min(max, (xs[last] - xs[first]) * units / dt));
        vy = Math.max(-max, Math.min(max, (ys[last] - ys[first]) * units / dt));
    }

    public float getXVelocity() {
        return vx;
    }

    public float getYVelocity() {
        return vy;
    }

    public void clear() {
        n = 0;
    }

    public void recycle() {
    }
}
