package dev.scratchwheel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Listas inteligentes: se calculan al abrirlas a partir de las fechas y las reproducciones (sin Android). */
final class SmartLists {
    static final int NEW = 0;
    static final int UNPLAYED = 1;
    static final int WEEK = 2;
    static final int REDISCOVER = 3;
    static final int FORGOTTEN_FAVS = 4;
    static final int COUNT = 5;

    static final long DAY = 24L * 3600 * 1000;

    private SmartLists() {
    }

    /** Nombre (en español; pasa por Tr) de cada lista. */
    static String name(int kind) {
        switch (kind) {
            case NEW:
                return "NUEVAS";
            case UNPLAYED:
                return "SIN ESCUCHAR";
            case WEEK:
                return "ESTA SEMANA";
            case REDISCOVER:
                return "REDESCUBRE";
            default:
                return "FAVORITAS OLVIDADAS";
        }
    }

    /** Qué entra en cada lista (para la ayuda). */
    static String help(int kind) {
        switch (kind) {
            case NEW:
                return "Archivos de los últimos 30 días, lo más nuevo primero.";
            case UNPLAYED:
                return "Canciones que aún no has escuchado nunca.";
            case WEEK:
                return "Lo que has escuchado en los últimos 7 días, lo más escuchado primero.";
            case REDISCOVER:
                return "Canciones que escuchaste 3 veces o más y llevan 90 días sin sonar.";
            default:
                return "Favoritas que llevan 60 días sin sonar.";
        }
    }

    /**
     * Índices de la lista. Los arrays van en paralelo (una entrada por canción): fecha del archivo (ms, 0 si no se
     * sabe), reproducciones, última reproducción (ms, 0 si nunca) y favorita.
     */
    static List<Integer> build(int kind, final long[] mod, final long[] count, final long[] last, boolean[] fav,
                               long now) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        int n = count.length;
        for (int i = 0; i < n; i++) {
            boolean in;
            switch (kind) {
                case NEW:
                    in = mod[i] > 0 && now - mod[i] <= 30 * DAY;
                    break;
                case UNPLAYED:
                    in = count[i] == 0;
                    break;
                case WEEK:
                    in = last[i] > 0 && now - last[i] <= 7 * DAY;
                    break;
                case REDISCOVER:
                    in = count[i] >= 3 && last[i] > 0 && now - last[i] >= 90 * DAY;
                    break;
                default:
                    in = fav[i] && (last[i] == 0 || now - last[i] >= 60 * DAY);
                    break;
            }
            if (in) {
                out.add(i);
            }
        }
        Comparator<Integer> cmp = null;
        if (kind == NEW) {
            cmp = new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Long.compare(mod[b], mod[a]);
                }
            };
        } else if (kind == WEEK) {
            cmp = new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return count[a] != count[b] ? Long.compare(count[b], count[a]) : Long.compare(last[b], last[a]);
                }
            };
        } else if (kind == REDISCOVER) {
            cmp = new Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    return Long.compare(count[b], count[a]);
                }
            };
        }
        if (cmp != null) {
            Collections.sort(out, cmp);
        }
        return out;
    }
}
