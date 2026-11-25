package dev.scratchwheel;

import java.util.ArrayList;
import java.util.List;

/**
 * EXPERIMENTAL (sabor personal). Ordena una lista para pincharla seguida: empieza por la canción más tranquila y
 * elige cada siguiente por tonalidad compatible (rueda Camelot), tempo parecido (contando doble y mitad) y energía
 * que sube poco a poco. Sin Android, testable.
 */
final class Automix {
    private Automix() {
    }

    /** Datos de una canción: BPM (0 si no se sabe), tonalidad 0..23 (-1 si no) y energía 0..1. */
    static final class Item {
        final double bpm;
        final int key;
        final double energy;

        Item(double bpm, int key, double energy) {
            this.bpm = bpm;
            this.key = key;
            this.energy = energy;
        }
    }

    /** Distancia en la rueda Camelot: 0 compatible (igual, ±1, relativa), 1 a dos pasos, 2 lejos, 1 si no se sabe. */
    static int keyDistance(int a, int b) {
        if (a < 0 || b < 0) {
            return 1;
        }
        String ca = KeyDetect.camelot(a, 0);
        String cb = KeyDetect.camelot(b, 0);
        int na = Integer.parseInt(ca.substring(0, ca.length() - 1));
        int nb = Integer.parseInt(cb.substring(0, cb.length() - 1));
        boolean sameLetter = ca.charAt(ca.length() - 1) == cb.charAt(cb.length() - 1);
        int d = Math.abs(na - nb);
        d = Math.min(d, 12 - d);
        if (sameLetter && d <= 1) {
            return 0;
        }
        if (!sameLetter && d == 0) {
            return 0;
        }
        if (d <= 2) {
            return 1;
        }
        return 2;
    }

    /** Diferencia de tempo en % (contando que 70 y 140 BPM encajan). */
    static double bpmDiff(double a, double b) {
        if (a <= 0 || b <= 0) {
            return 6;
        }
        double d = Math.min(Math.abs(a - b), Math.min(Math.abs(a - 2 * b), Math.abs(2 * a - b)));
        return d / a * 100;
    }

    static double cost(Item a, Item b) {
        double c = keyDistance(a.key, b.key) * 3.0;
        c += Math.min(bpmDiff(a.bpm, b.bpm), 30) / 3.0;
        double de = b.energy - a.energy;
        // que la energía suba poco a poco: bajar cuesta más que subir un poco
        c += de >= 0 ? de * 4 : -de * 8;
        return c;
    }

    /** Orden de reproducción (índices de {@code items}). */
    static List<Integer> order(List<Item> items) {
        int n = items.size();
        List<Integer> out = new ArrayList<Integer>();
        if (n == 0) {
            return out;
        }
        boolean[] used = new boolean[n];
        int cur = 0;
        for (int i = 1; i < n; i++) {
            if (items.get(i).energy < items.get(cur).energy) {
                cur = i;
            }
        }
        used[cur] = true;
        out.add(cur);
        for (int step = 1; step < n; step++) {
            int best = -1;
            double bc = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (!used[j]) {
                    double c = cost(items.get(cur), items.get(j));
                    if (c < bc) {
                        bc = c;
                        best = j;
                    }
                }
            }
            used[best] = true;
            out.add(best);
            cur = best;
        }
        return out;
    }

    /** Energía 0..1 de un trozo de audio (RMS en dB, de -40 a -6 dBFS). */
    static double energy(PcmData pcm) {
        if (pcm == null || pcm.frames <= 0) {
            return 0.5;
        }
        int start = (int) Math.min(pcm.frames * 0.2, pcm.sampleRate * 20.0);
        int end = (int) Math.min(pcm.frames, start + pcm.sampleRate * 60.0);
        double sum = 0;
        long n = 0;
        for (int i = start; i < end; i += 4) {
            double m = (pcm.sample(2 * i) + pcm.sample(2 * i + 1)) * 0.5 / 32768.0;
            sum += m * m;
            n++;
        }
        if (n == 0) {
            return 0.5;
        }
        double db = 10 * Math.log10(sum / n + 1e-12);
        return Math.max(0, Math.min(1, (db + 40) / 34.0));
    }
}
