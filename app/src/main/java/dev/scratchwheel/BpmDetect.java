package dev.scratchwheel;

/**
 * Detecta el tempo (BPM) y la posición del primer pulso. Sin Android: se puede probar en un PC.
 * Método: envolvente de energía (200 Hz) → onsets → peine de pulsos con periodo fraccionario → fase.
 */
public final class BpmDetect {
    static final double MIN = 70;
    static final double MAX = 180;
    private static final int ENV_HZ = 200;

    private BpmDetect() {
    }

    /** @return {bpm, primerPulsoSeg, confianza 0..1} o null si no hay audio suficiente. */
    public static double[] detect(PcmData pcm) {
        if (pcm == null || pcm.frames < pcm.sampleRate * 8) {
            return null;
        }
        int sr = pcm.sampleRate;
        int hop = Math.max(1, sr / ENV_HZ);
        int start = (int) Math.min(pcm.frames * 0.1, sr * 15.0);
        int end = (int) Math.min(pcm.frames, start + sr * 70.0);
        int n = (end - start) / hop;
        if (n < ENV_HZ * 6) {
            return null;
        }
        double[] env = new double[n];
        for (int i = 0; i < n; i++) {
            double e = 0;
            int base = start + i * hop;
            for (int k = 0; k < hop; k++) {
                int idx = 2 * (base + k);
                double m = (pcm.sample(idx) + pcm.sample(idx + 1)) * 0.5 / 32768.0;
                e += m * m;
            }
            env[i] = Math.log(1 + 600 * e / hop);
        }
        double[] on = new double[n];
        double mean = 0;
        for (int i = 1; i < n; i++) {
            on[i] = Math.max(0, env[i] - env[i - 1]);
            mean += on[i];
        }
        mean /= n;
        for (int i = 0; i < n; i++) {
            on[i] = Math.max(0, on[i] - mean * 0.8);
        }
        double bestBpm = 0;
        double best = 0;
        double sum = 0;
        int cnt = 0;
        for (double bpm = MIN; bpm <= MAX; bpm += 0.25) {
            double period = 60.0 * ENV_HZ / bpm;
            double s = ac(on, period) + 0.5 * ac(on, period * 2) + 0.33 * ac(on, period * 3);
            // preferencia suave por el rango habitual 90-150
            double prior = 1.0 - 0.18 * Math.min(1.0, Math.abs(bpm - 120) / 60.0);
            s *= prior;
            sum += s;
            cnt++;
            if (s > best) {
                best = s;
                bestBpm = bpm;
            }
        }
        if (bestBpm == 0 || best <= 0) {
            return null;
        }
        // refinado: el mismo peine con más resolución alrededor del máximo
        double fine = bestBpm;
        double fineScore = 0;
        for (double bpm = bestBpm - 0.5; bpm <= bestBpm + 0.5; bpm += 0.05) {
            double period = 60.0 * ENV_HZ / bpm;
            double s = ac(on, period) + 0.5 * ac(on, period * 2) + 0.33 * ac(on, period * 3);
            if (s > fineScore) {
                fineScore = s;
                fine = bpm;
            }
        }
        double bpm = fine;
        while (bpm < 80) {
            bpm *= 2;
        }
        while (bpm > 165) {
            bpm /= 2;
        }
        // fase: el desfase dentro de un periodo con más energía
        double period = 60.0 * ENV_HZ / bpm;
        double bestPh = 0;
        double bestPhScore = -1;
        for (double ph = 0; ph < period; ph += 0.5) {
            double s = comb(on, period, ph);
            if (s > bestPhScore) {
                bestPhScore = s;
                bestPh = ph;
            }
        }
        double first = (start + bestPh * hop) / (double) sr;
        double beat = 60.0 / bpm;
        first = first % beat; // primer pulso desde el principio de la pista
        double conf = Math.max(0, Math.min(1, (best / (sum / cnt)) / 4.0));
        return new double[] {Math.round(bpm * 10) / 10.0, first, conf};
    }

    /** Autocorrelación de los onsets con retardo fraccionario (no depende de la fase). */
    private static double ac(double[] on, double lag) {
        int il = (int) lag;
        double f = lag - il;
        int n = on.length - il - 1;
        if (n <= 0) {
            return 0;
        }
        double s = 0;
        for (int i = 0; i < n; i++) {
            s += on[i] * (on[i + il] * (1 - f) + on[i + il + 1] * f);
        }
        return s / n;
    }

    private static double comb(double[] on, double period, double phase) {
        double s = 0;
        int k = 0;
        for (double p = phase; p < on.length - 1; p += period) {
            int i = (int) p;
            double f = p - i;
            s += on[i] * (1 - f) + on[i + 1] * f;
            k++;
        }
        return k == 0 ? 0 : s / k;
    }
}
