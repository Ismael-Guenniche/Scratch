package android.view;

import android.animation.TimeInterpolator;

/** Animación de las propiedades de una vista (posición, opacidad, escala, giro) con el reloj de cuadros. */
public class ViewPropertyAnimator {
    private final View v;
    private long duration = 300;
    private long delay;
    private TimeInterpolator interp = new android.view.animation.AccelerateDecelerateInterpolator();
    private Runnable end;
    private Runnable startAction;
    private final float[] to = new float[7];
    private final boolean[] has = new boolean[7];
    private float[] from;
    private long t0;
    private boolean running;
    private int generation;

    ViewPropertyAnimator(View v) {
        this.v = v;
    }

    private ViewPropertyAnimator set(int i, float val) {
        to[i] = val;
        has[i] = true;
        plan();
        return this;
    }

    public ViewPropertyAnimator translationX(float x) {
        return set(0, x);
    }

    public ViewPropertyAnimator translationY(float y) {
        return set(1, y);
    }

    public ViewPropertyAnimator alpha(float a) {
        return set(2, a);
    }

    public ViewPropertyAnimator scaleX(float s) {
        return set(3, s);
    }

    public ViewPropertyAnimator scaleY(float s) {
        return set(4, s);
    }

    public ViewPropertyAnimator rotation(float r) {
        return set(5, r);
    }

    public ViewPropertyAnimator translationZ(float z) {
        return this;
    }

    public ViewPropertyAnimator setDuration(long ms) {
        duration = Math.max(0, ms);
        return this;
    }

    public ViewPropertyAnimator setStartDelay(long ms) {
        delay = Math.max(0, ms);
        return this;
    }

    public ViewPropertyAnimator setInterpolator(TimeInterpolator i) {
        interp = i == null ? new android.view.animation.LinearInterpolator() : i;
        return this;
    }

    public ViewPropertyAnimator withEndAction(Runnable r) {
        end = r;
        return this;
    }

    public ViewPropertyAnimator withStartAction(Runnable r) {
        startAction = r;
        return this;
    }

    public ViewPropertyAnimator setListener(Object l) {
        return this;
    }

    public ViewPropertyAnimator withLayer() {
        return this;
    }

    public void cancel() {
        generation++;
        running = false;
        java.util.Arrays.fill(has, false);
        end = null;
        startAction = null;
    }

    public void start() {
        plan();
    }

    private float get(int i) {
        switch (i) {
            case 0:
                return v.getTranslationX();
            case 1:
                return v.getTranslationY();
            case 2:
                return v.getAlpha();
            case 3:
                return v.getScaleX();
            case 4:
                return v.getScaleY();
            default:
                return v.getRotation();
        }
    }

    private void put(int i, float val) {
        switch (i) {
            case 0:
                v.setTranslationX(val);
                break;
            case 1:
                v.setTranslationY(val);
                break;
            case 2:
                v.setAlpha(val);
                break;
            case 3:
                v.setScaleX(val);
                break;
            case 4:
                v.setScaleY(val);
                break;
            default:
                v.setRotation(val);
                break;
        }
    }

    /** Arranca en el siguiente cuadro, cuando ya se han fijado todas las propiedades de la cadena. */
    private void plan() {
        if (running) {
            return;
        }
        running = true;
        final int gen = ++generation;
        desk.Frames.post(new Runnable() {
            @Override
            public void run() {
                if (gen != generation) {
                    return;
                }
                long now = System.nanoTime() / 1000000L;
                if (from == null) {
                    t0 = now + delay;
                    from = new float[7];
                    for (int i = 0; i < 6; i++) {
                        from[i] = get(i);
                    }
                    if (startAction != null) {
                        Runnable s = startAction;
                        startAction = null;
                        s.run();
                    }
                }
                float t = duration <= 0 ? 1f : Math.max(0f, Math.min(1f, (now - t0) / (float) duration));
                float k = interp.getInterpolation(t);
                for (int i = 0; i < 6; i++) {
                    if (has[i]) {
                        put(i, from[i] + (to[i] - from[i]) * k);
                    }
                }
                if (t >= 1f) {
                    running = false;
                    from = null;
                    java.util.Arrays.fill(has, false);
                    Runnable e = end;
                    end = null;
                    delay = 0;
                    if (e != null) {
                        e.run();
                    }
                } else {
                    desk.Frames.post(this);
                }
            }
        });
    }
}
