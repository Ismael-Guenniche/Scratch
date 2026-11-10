package dev.scratchwheel;

import java.util.Random;

/** Orden aleatorio sin repeticiones (Fisher-Yates), como el shuffle de Spotify: no repite hasta agotar la lista. */
public final class ShuffleOrder {
    private ShuffleOrder() {
    }

    /**
     * @param first elemento que debe ir primero, o -1 si no importa
     * @param avoid elemento que no debe ir primero cuando {@code first} es -1 (p. ej. el que suena ahora)
     */
    public static int[] build(int n, int first, int avoid, Random rnd) {
        int[] o = new int[n];
        for (int k = 0; k < n; k++) {
            o[k] = k;
        }
        for (int k = n - 1; k > 0; k--) {
            int j = rnd.nextInt(k + 1);
            int t = o[k];
            o[k] = o[j];
            o[j] = t;
        }
        if (first >= 0 && first < n) {
            for (int k = 0; k < n; k++) {
                if (o[k] == first) {
                    o[k] = o[0];
                    o[0] = first;
                    break;
                }
            }
        } else if (n > 1 && o[0] == avoid) {
            int t = o[0];
            o[0] = o[1];
            o[1] = t;
        }
        return o;
    }
}
