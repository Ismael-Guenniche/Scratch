package dev.scratchwheel;

/** Cálculos de visualización sobre audio estéreo intercalado de 16 bits: onda y espectro. Sin Android. */
public final class Waveform {
    private static final int N = 1024;
    private static final float[] HANN = new float[N];

    static {
        for (int i = 0; i < N; i++) {
            HANN[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1)));
        }
    }

    private Waveform() {
    }

    /** Pico (0..1) de cada uno de los {@code n} tramos iguales de la pista. */
    public static float[] peaks(short[] d, int frames, int n) {
        float[] out = new float[n];
        if (frames <= 0 || n <= 0) {
            return out;
        }
        for (int k = 0; k < n; k++) {
            int a = (int) ((long) k * frames / n);
            int b = Math.max(a + 1, (int) ((long) (k + 1) * frames / n));
            int step = Math.max(1, (b - a) / 64);
            int m = 0;
            for (int i = a; i < b && i < frames; i += step) {
                int l = Math.abs(d[2 * i]);
                int r = Math.abs(d[2 * i + 1]);
                m = Math.max(m, Math.max(l, r));
            }
            out[k] = Math.min(1f, m / 32768f);
        }
        return out;
    }

    /**
     * Espectro en {@code out.length} bandas repartidas en escala logarítmica (60 Hz a ~16 kHz) de los 1024
     * muestras alrededor de {@code center}. Valores 0..1 (de -70 dB a 0 dB).
     */
    public static void spectrum(short[] d, int frames, int center, int rate, float[] out) {
        int bands = out.length;
        if (frames <= 0 || bands == 0) {
            java.util.Arrays.fill(out, 0f);
            return;
        }
        int start = Math.max(0, Math.min(frames - N, center - N / 2));
        float[] x = new float[N];
        for (int i = 0; i < N; i++) {
            int f = start + i;
            float v = f < frames ? (d[2 * f] + d[2 * f + 1]) * 0.5f : 0f;
            x[i] = v * HANN[i];
        }
        double fmin = 60;
        double fmax = Math.min(16000, rate * 0.45);
        for (int b = 0; b < bands; b++) {
            double fc = fmin * Math.pow(fmax / fmin, (b + 0.5) / bands);
            double coeff = 2 * Math.cos(2 * Math.PI * fc / rate);
            double s1 = 0;
            double s2 = 0;
            for (int i = 0; i < N; i++) {
                double s0 = x[i] + coeff * s1 - s2;
                s2 = s1;
                s1 = s0;
            }
            double power = s1 * s1 + s2 * s2 - coeff * s1 * s2;
            double mag = Math.sqrt(Math.max(0, power)) / (N / 4.0) / 32768.0;
            double db = 20 * Math.log10(mag + 1e-9);
            out[b] = (float) Math.max(0, Math.min(1, (db + 70) / 70));
        }
    }
}
