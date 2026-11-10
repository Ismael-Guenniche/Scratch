package dev.scratchwheel;

import java.util.Random;

/** Pista de prueba sintetizada (16 s, 120 BPM, la menor): bombo, caja, hi-hat, bajo y arpegio. */
public final class DemoTrack {
    private static final int SR = 44100;
    private static final int N = SR * 16;

    private DemoTrack() {
    }

    public static PcmData generate() {
        float[] l = new float[N];
        float[] r = new float[N];
        Random rnd = new Random(7);
        double[] bass = {55.0, 55.0, 65.41, 49.0};
        double[] chord = {1.0, 1.0, 1.1892, 0.8909};
        double[] lead = {440.0, 523.25, 659.25, 523.25, 783.99, 659.25, 523.25, 392.0};

        for (int b = 0; b < 32; b++) {
            int s0 = b * SR / 2;
            kick(l, r, s0);
            if (b % 2 == 1) {
                snare(l, r, s0, rnd);
            }
            tone(l, r, s0, bass[(b / 4) % 4], 0.45, 4.0, 0.34f, 0.5f, 0.0f);
        }
        for (int e = 0; e < 64; e++) {
            hat(l, r, e * SR / 4, rnd, e % 2 == 0 ? 0.20f : 0.12f);
        }
        for (int s = 0; s < 128; s++) {
            double f = lead[s % 8] * chord[(s / 16) % 4];
            tone(l, r, s * SR / 8, f, 0.22, 14.0, 0.11f, 0.3f, (s % 2 == 0) ? -0.4f : 0.4f);
        }

        float peak = 0.0001f;
        for (int i = 0; i < N; i++) {
            peak = Math.max(peak, Math.max(Math.abs(l[i]), Math.abs(r[i])));
        }
        float scale = 0.85f * 32767f / peak;
        PcmData.Builder b = new PcmData.Builder();
        for (int i = 0; i < N; i++) {
            b.append((short) Math.round(l[i] * scale), (short) Math.round(r[i] * scale));
        }
        return b.build(SR);
    }

    private static void add(float[] l, float[] r, int idx, float v, float pan) {
        if (idx < 0 || idx >= N) {
            return;
        }
        l[idx] += v * (1f - Math.max(0f, pan));
        r[idx] += v * (1f + Math.min(0f, pan));
    }

    private static void kick(float[] l, float[] r, int s0) {
        double phase = 0;
        int len = (int) (0.4 * SR);
        for (int i = 0; i < len; i++) {
            double t = (double) i / SR;
            double f = 48 + 120 * Math.exp(-t * 30);
            phase += 2 * Math.PI * f / SR;
            add(l, r, s0 + i, (float) (Math.sin(phase) * Math.exp(-t * 8) * 0.9), 0f);
        }
    }

    private static void snare(float[] l, float[] r, int s0, Random rnd) {
        int len = (int) (0.25 * SR);
        for (int i = 0; i < len; i++) {
            double t = (double) i / SR;
            double noise = (rnd.nextFloat() * 2 - 1) * Math.exp(-t * 20) * 0.45;
            double body = Math.sin(2 * Math.PI * 190 * t) * Math.exp(-t * 30) * 0.3;
            add(l, r, s0 + i, (float) (noise + body), 0f);
        }
    }

    private static void hat(float[] l, float[] r, int s0, Random rnd, float amp) {
        int len = (int) (0.07 * SR);
        float prev = 0;
        for (int i = 0; i < len; i++) {
            double t = (double) i / SR;
            float nz = rnd.nextFloat() * 2 - 1;
            float hp = nz - prev;
            prev = nz;
            add(l, r, s0 + i, (float) (hp * Math.exp(-t * 70) * amp), 0.15f);
        }
    }

    private static void tone(float[] l, float[] r, int s0, double freq, double seconds,
                             double decay, float amp, float overtone, float pan) {
        int len = (int) (seconds * SR);
        double w = 2 * Math.PI * freq;
        for (int i = 0; i < len; i++) {
            double t = (double) i / SR;
            double attack = Math.min(1.0, t * 400);
            double v = Math.sin(w * t) + overtone * Math.sin(2 * w * t);
            add(l, r, s0 + i, (float) (v * Math.exp(-t * decay) * attack * amp), pan);
        }
    }
}
