package dev.scratchwheel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Búsqueda inteligente (sin Android, testable en la JVM).
 * <ul>
 * <li>Normaliza igual la búsqueda y las canciones: minúsculas, sin acentos latinos, anchura normal y los signos
 * cuentan como espacio («One-X» = «one x» = «ONE·X»).</li>
 * <li>Busca por palabras y por campos: cada palabra escrita tiene que encajar con alguna palabra de algún campo.</li>
 * <li>Una palabra encaja si es igual, si la de la canción empieza por ella, si está dentro (4+ letras) o si se parece
 * (letras cambiadas, de más, de menos o dos cruzadas). Errores permitidos con tolerancia NORMAL: hasta 3 letras
 * ninguno, de 4 a 6 uno, 7 o más dos. Con POCA: solo uno y desde 7 letras.</li>
 * <li>Palabras pegadas («onex» = «one x») solo con 4+ letras y empezando al principio de una palabra; así «ado» no
 * saca «System of a Down».</li>
 * <li>Puntos: exacta &gt; empieza por &gt; pegada &gt; dentro &gt; aproximada, y pesa más artista/título que álbum y
 * carpeta.</li>
 * </ul>
 */
final class SmartSearch {
    static final int F_TITLE = 1;
    static final int F_ARTIST = 2;
    static final int F_ALBUM = 4;
    static final int F_FOLDER = 8;
    static final int F_ALL = F_TITLE | F_ARTIST | F_ALBUM | F_FOLDER;

    static final int TOL_NO = 0;
    static final int TOL_LOW = 1;
    static final int TOL_NORMAL = 2;

    /** Campos de cada canción (el nombre de archivo cuenta con el título, pesa un poco menos). */
    private static final int K_TITLE = 0;
    private static final int K_ARTIST = 1;
    private static final int K_ALBUM = 2;
    private static final int K_FOLDER = 3;
    private static final int K_FILE = 4;
    private static final int[] WEIGHT = {10, 10, 6, 4, 7};

    private SmartSearch() {
    }

    /** Opciones (Ajustes → BÚSQUEDA). Por defecto: tolerancia NORMAL, todos los campos, dentro de palabras y relevancia. */
    static final class Opts {
        int tol = TOL_NORMAL;
        int fields = F_ALL;
        boolean inside = true;
        boolean relevance = true;
    }

    /** Palabras precalculadas de una canción. */
    static final class Entry {
        final String[][] words = new String[5][];
        /** Palabras de cada campo pegadas, y dónde empieza cada palabra (para «onex» = «one x»). */
        final String[] compact = new String[5];
        final int[][] starts = new int[5][];

        Entry(String title, String artist, String album, String folder, String file) {
            set(K_TITLE, title);
            set(K_ARTIST, artist);
            set(K_ALBUM, album);
            set(K_FOLDER, folder);
            set(K_FILE, stripExt(file));
        }

        private void set(int k, String s) {
            String[] w = split(norm(s));
            words[k] = w;
            StringBuilder b = new StringBuilder();
            int[] st = new int[w.length];
            for (int i = 0; i < w.length; i++) {
                st[i] = b.length();
                b.append(w[i]);
            }
            compact[k] = b.toString();
            starts[k] = st;
        }
    }

    /** Búsqueda ya preparada. */
    static final class Query {
        final String[] words;
        /** Todas las palabras pegadas («one x» → «onex»), o null si es una sola palabra. */
        final String joined;
        final Opts o;

        Query(String[] words, Opts o) {
            this.words = words;
            this.o = o;
            String j = null;
            if (words.length > 1) {
                StringBuilder b = new StringBuilder();
                for (String w : words) {
                    b.append(w);
                }
                j = b.toString();
            }
            joined = j;
        }
    }

    /** Prepara la búsqueda; null si no hay nada que buscar. */
    static Query query(String raw, Opts o) {
        String[] w = split(norm(raw));
        if (w.length == 0) {
            return null;
        }
        return new Query(w, o == null ? new Opts() : o);
    }

    /** Minúsculas, sin acentos latinos (el japonés conserva sus marcas), anchura normal y signos como espacio. */
    static String norm(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        String d = Normalizer.normalize(Normalizer.normalize(s, Normalizer.Form.NFKC), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(d.length());
        char base = ' ';
        boolean space = true;
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            int t = Character.getType(c);
            if (t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
                    || t == Character.ENCLOSING_MARK) {
                if (base < 0x0370) {
                    continue;
                }
                b.append(c);
                continue;
            }
            if (Character.isLetterOrDigit(c)) {
                b.append(Character.toLowerCase(c));
                base = c;
                space = false;
            } else if (!space) {
                b.append(' ');
                space = true;
                base = ' ';
            }
        }
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == ' ') {
            b.setLength(n - 1);
        }
        String r = Normalizer.normalize(b, Normalizer.Form.NFC);
        // ß y similares: toLowerCase(Locale.ROOT) ya no cambia nada más.
        return r.toLowerCase(Locale.ROOT);
    }

    private static final String[] NONE = new String[0];

    private static String[] split(String n) {
        if (n.length() == 0) {
            return NONE;
        }
        return n.split(" ");
    }

    private static String stripExt(String f) {
        if (f == null) {
            return "";
        }
        int dot = f.lastIndexOf('.');
        return dot > 0 && f.length() - dot <= 5 ? f.substring(0, dot) : f;
    }

    /** Errores permitidos para una palabra escrita de esta longitud. */
    static int allowed(int len, int tol) {
        if (tol == TOL_NORMAL) {
            return len <= 3 ? 0 : len <= 6 ? 1 : 2;
        }
        if (tol == TOL_LOW) {
            return len >= 7 ? 1 : 0;
        }
        return 0;
    }

    /** Puntos de la canción para esta búsqueda; -1 si no encaja. */
    static int score(Entry e, Query q) {
        int total = 0;
        boolean all = true;
        for (String w : q.words) {
            int s = best(e, w, q.o);
            if (s <= 0) {
                all = false;
                break;
            }
            total += s;
        }
        if (all) {
            return total;
        }
        if (q.joined != null && q.joined.length() >= 4) {
            // «one x» escrito separado y la canción lo tiene pegado («onex»)
            int s = best(e, q.joined, q.o);
            if (s > 0) {
                return s * q.words.length;
            }
        }
        return -1;
    }

    private static boolean fieldOn(int k, int fields) {
        switch (k) {
            case K_TITLE:
            case K_FILE:
                return (fields & F_TITLE) != 0;
            case K_ARTIST:
                return (fields & F_ARTIST) != 0;
            case K_ALBUM:
                return (fields & F_ALBUM) != 0;
            default:
                return (fields & F_FOLDER) != 0;
        }
    }

    /** Mejor encaje de una palabra escrita en toda la canción (puntos × peso del campo). */
    private static int best(Entry e, String w, Opts o) {
        int bestScore = 0;
        int sum = 0;
        int k = allowed(w.length(), o.tol);
        for (int f = 0; f < 5; f++) {
            if (!fieldOn(f, o.fields)) {
                continue;
            }
            int s = 0;
            for (String fw : e.words[f]) {
                int m = word(w, fw, k, o.inside);
                if (m > s) {
                    s = m;
                    if (s == 100) {
                        break;
                    }
                }
            }
            if (s < 70 && w.length() >= 4 && e.starts[f].length > 1) {
                String c = e.compact[f];
                for (int st : e.starts[f]) {
                    if (c.startsWith(w, st)) {
                        s = 70;
                        break;
                    }
                }
            }
            int v = s * WEIGHT[f];
            sum += v;
            if (v > bestScore) {
                bestScore = v;
            }
        }
        // Si la palabra está en varios campos (artista y álbum, por ejemplo) suma un poco más.
        return bestScore + (sum - bestScore) / 10;
    }

    /** Puntos de una palabra escrita contra una palabra de la canción (0 = no encaja). */
    static int word(String w, String fw, int k, boolean inside) {
        int wl = w.length();
        int fl = fw.length();
        if (fl >= wl && fw.startsWith(w)) {
            return fl == wl ? 100 : 80;
        }
        if (inside && wl >= 4 && fl > wl && fw.indexOf(w) > 0) {
            return 50;
        }
        if (k == 0) {
            return 0;
        }
        // Palabra entera parecida
        if (Math.abs(fl - wl) <= k) {
            int d = dist(w, fw, fl, k);
            if (d <= k) {
                return 45 - 10 * d;
            }
        }
        // Principio de la palabra parecido (se está escribiendo: «linkn» → «linkin park»)
        if (fl > wl) {
            int best = k + 1;
            for (int len = Math.max(1, wl - k); len <= Math.min(fl - 1, wl + k); len++) {
                int d = dist(w, fw, len, k);
                if (d < best) {
                    best = d;
                }
            }
            if (best <= k) {
                return 35 - 10 * best;
            }
        }
        return 0;
    }

    /** Distancia de Damerau-Levenshtein (letras cambiadas, de más, de menos o dos cruzadas), con corte en max+1. */
    static int dist(String a, String b, int max) {
        return dist(a, b, b.length(), max);
    }

    /** Igual, pero solo con las {@code m} primeras letras de {@code b} (sin crear subcadenas). */
    static int dist(String a, String b, int m, int max) {
        int n = a.length();
        if (Math.abs(n - m) > max) {
            return max + 1;
        }
        int[] p2 = new int[m + 1];
        int[] p1 = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            p1[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int rowMin = cur[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                char cb = b.charAt(j - 1);
                int cost = ca == cb ? 0 : 1;
                int v = Math.min(Math.min(p1[j] + 1, cur[j - 1] + 1), p1[j - 1] + cost);
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    v = Math.min(v, p2[j - 2] + 1);
                }
                cur[j] = v;
                if (v < rowMin) {
                    rowMin = v;
                }
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] t = p2;
            p2 = p1;
            p1 = cur;
            cur = t;
        }
        return p1[m];
    }

    /**
     * Filtra y (si la opción está activa) ordena por relevancia, conservando el orden original en los empates.
     * {@code order} son índices de canciones; {@code entries} da la Entry de cada índice.
     */
    static ArrayList<Integer> filter(java.util.List<Integer> order, Query q, Lookup entries) {
        final ArrayList<Integer> out = new ArrayList<Integer>();
        if (q == null) {
            out.addAll(order);
            return out;
        }
        final ArrayList<int[]> hits = new ArrayList<int[]>();
        int pos = 0;
        for (Integer i : order) {
            int s = score(entries.get(i), q);
            if (s > 0) {
                hits.add(new int[] {i, s, pos});
            }
            pos++;
        }
        if (q.o.relevance) {
            java.util.Collections.sort(hits, new java.util.Comparator<int[]>() {
                @Override
                public int compare(int[] a, int[] b) {
                    if (a[1] != b[1]) {
                        return a[1] > b[1] ? -1 : 1;
                    }
                    return a[2] < b[2] ? -1 : (a[2] > b[2] ? 1 : 0);
                }
            });
        }
        for (int[] h : hits) {
            out.add(h[0]);
        }
        return out;
    }

    interface Lookup {
        Entry get(int index);
    }
}
