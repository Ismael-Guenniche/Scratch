package dev.scratchwheel;

/** Velocidad del avance rápido según lo que se mueve el dedo (lógica pura, sin Android). */
public final class SeekSpeed {
    public static final double MIN = 0.5;
    public static final double BASE = 1.5;
    public static final double MAX = 4.0;
    /** Recorrido del dedo (dp) hacia cada lado para llegar al extremo. */
    public static final float REACH_DP = 100f;

    private SeekSpeed() {
    }

    /** Derecha acelera (hasta 4×), izquierda frena (hasta 0,5×); sin mover = 1,5×. */
    public static double forDrag(float dxPx, float density) {
        double f = Math.max(-1, Math.min(1, dxPx / (REACH_DP * density)));
        return f >= 0 ? BASE + f * (MAX - BASE) : BASE + f * (BASE - MIN);
    }
}
