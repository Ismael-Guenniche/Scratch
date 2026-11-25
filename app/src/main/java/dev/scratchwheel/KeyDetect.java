package dev.scratchwheel;

/**
 * Tonalidad de una canción (sin Android, testable). Cromagrama con FFT de 60 s de audio (de 55 Hz a 2 kHz) y
 * correlación con los perfiles de Krumhansl-Kessler para las 24 tonalidades. Se muestra en notación Camelot
 * (8B = Do mayor, 8A = La menor), la que usan los DJ para mezclar en armonía: dos canciones mezclan bien si tienen el
 * mismo número, ±1, o el mismo número con la otra letra.
 */
public final class KeyDetect {
    private static final double[] MAJOR = {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    private static final double[] MINOR = {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};
    private static final String[] NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final int N = 8192;

    private KeyDetect() {
    }

    /** @return {tonalidad 0..23 (0–11 mayores desde Do, 12–23 menores desde Do), confianza 0..1} o null. */
    public static double[] detect(PcmData pcm) {
        if (pcm == null || pcm.frames < pcm.sampleRate * 8) {
            return null;
        }
        int sr = pcm.sampleRate;
        int start = (int) Math.min(pcm.frames * 0.1, sr * 15.0);
        int end = (int) Math.min(pcm.frames, start + sr * 60.0);
        double[] chroma = new double[12];
        double[] re = new double[N];
        double[] im = new double[N];
        double[] win = new double[N];
        for (int i = 0; i < N; i++) {
            win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (N - 1));
        }
        // a qué clase de nota va cada bin de la FFT
        int[] pc = new int[N / 2];
        int lo = (int) Math.ceil(55.0 * N / sr);
        int hi = Math.min(N / 2 - 1, (int) (2000.0 * N / sr));
        for (int b = lo; b <= hi; b++) {
            double f = (double) b * sr / N;
            double midi = 69 + 12 * Math.log(f / 440.0) / Math.log(2);
            pc[b] = ((int) Math.round(midi) % 12 + 12) % 12;
        }
        int hop = N;
        int frames = 0;
        for (int s = start; s + N <= end; s += hop) {
            for (int i = 0; i < N; i++) {
                int idx = 2 * (s + i);
                re[i] = (pcm.sample(idx) + pcm.sample(idx + 1)) * 0.5 / 32768.0 * win[i];
                im[i] = 0;
            }
            fft(re, im);
            double[] fc = new double[12];
            double tot = 0;
            for (int b = lo; b <= hi; b++) {
                double m = Math.sqrt(re[b] * re[b] + im[b] * im[b]);
                fc[pc[b]] += m;
                tot += m;
            }
            if (tot > 1e-6) {
                for (int k = 0; k < 12; k++) {
                    chroma[k] += fc[k] / tot;
                }
                frames++;
            }
        }
        if (frames < 4) {
            return null;
        }
        double best = -2;
        double second = -2;
        int bestKey = 0;
        for (int key = 0; key < 24; key++) {
            double[] prof = key < 12 ? MAJOR : MINOR;
            int root = key % 12;
            double r = corr(chroma, prof, root);
            if (r > best) {
                second = best;
                best = r;
                bestKey = key;
            } else if (r > second) {
                second = r;
            }
        }
        double conf = Math.max(0, Math.min(1, (best - second) * 5 + best * 0.5));
        return new double[] {bestKey, conf};
    }

    private static double corr(double[] c, double[] prof, int root) {
        double mc = 0;
        double mp = 0;
        for (int i = 0; i < 12; i++) {
            mc += c[i];
            mp += prof[i];
        }
        mc /= 12;
        mp /= 12;
        double num = 0;
        double dc = 0;
        double dp = 0;
        for (int i = 0; i < 12; i++) {
            double a = c[(i + root) % 12] - mc;
            double b = prof[i] - mp;
            num += a * b;
            dc += a * a;
            dp += b * b;
        }
        return dc <= 0 || dp <= 0 ? 0 : num / Math.sqrt(dc * dp);
    }

    /** Camelot: «8B» (Do mayor), «8A» (La menor). {@code shift} sube o baja semitonos (el fader PITCH). */
    public static String camelot(int key, int shift) {
        boolean minor = key >= 12;
        int root = (((key % 12) + shift) % 12 + 12) % 12;
        int majorRoot = minor ? (root + 3) % 12 : root;
        int num = (7 * majorRoot + 7) % 12 + 1;
        return num + (minor ? "A" : "B");
    }

    /** Nombre clásico: «C», «Am», «F#m»… */
    public static String name(int key, int shift) {
        int root = (((key % 12) + shift) % 12 + 12) % 12;
        return NAMES[root] + (key >= 12 ? "m" : "");
    }

    /** FFT radix-2 in situ. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }
}
