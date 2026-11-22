package desk;

/**
 * Movimiento de un desplazamiento en un eje: inercia al soltar y aproximación suave a un destino
 * (rueda del ratón, smoothScrollTo). La vista aplica cada paso con step().
 */
public abstract class Glide implements Runnable {
    private float velocity;
    private float target = Float.NaN;
    private float pos;
    private long last;
    private boolean running;

    /** Posición actual del desplazamiento. */
    protected abstract float get();

    /** Coloca el desplazamiento; devuelve la posición real tras limitarla. */
    protected abstract float set(float v);

    public void stop() {
        velocity = 0;
        target = Float.NaN;
        if (running) {
            running = false;
            Frames.remove(this);
        }
    }

    public boolean isRunning() {
        return running;
    }

    /** Inercia: velocidad en píxeles por segundo. */
    public void fling(float pxPerSec) {
        target = Float.NaN;
        velocity = pxPerSec;
        start();
    }

    /** Destino actual (o la posición, si no hay animación hacia un destino). */
    public float target() {
        return Float.isNaN(target) ? get() : target;
    }

    public void glideTo(float t) {
        velocity = 0;
        target = t;
        start();
    }

    private void start() {
        pos = get();
        last = System.nanoTime();
        if (!running) {
            running = true;
            Frames.post(this);
        }
    }

    @Override
    public void run() {
        if (!running) {
            return;
        }
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        float want;
        boolean done;
        if (!Float.isNaN(target)) {
            float k = 1f - (float) Math.pow(0.0000005, dt);
            want = pos + (target - pos) * k;
            done = Math.abs(target - want) < 0.5f;
            if (done) {
                want = target;
            }
        } else {
            want = pos + velocity * dt;
            velocity *= (float) Math.pow(0.02, dt);
            done = Math.abs(velocity) < 25f * Rt.density;
        }
        float got = set(want);
        if (Math.abs(got - want) > 0.75f) {
            done = true;
        }
        pos = Float.isNaN(target) ? got : want;
        if (done) {
            running = false;
            velocity = 0;
            target = Float.NaN;
        } else {
            Frames.post(this);
        }
    }
}
