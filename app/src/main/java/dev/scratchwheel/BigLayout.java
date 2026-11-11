package dev.scratchwheel;

import java.util.ArrayList;
import java.util.List;

/**
 * Módulos de la pantalla Big Picture: cuáles hay, qué tamaños admiten (en celdas de una rejilla de 4 columnas),
 * cuáles están activos y en qué orden. Se colocan solos en la primera hueco libre. Sin dependencias de Android.
 */
public final class BigLayout {
    public static final int COLS = 4;

    public static final String[] IDS = {"info", "cover", "wheel", "wave", "spectrum", "transport", "volume",
        "pitch", "basic", "lyrics"};
    public static final String[] NAMES = {"INFO", "PORTADA", "SCRATCH", "ONDA", "ESPECTRO", "CONTROLES",
        "VOLUMEN", "PITCH", "AJUSTES RÁPIDOS", "LETRA"};

    /** Tamaños {ancho, alto} en celdas que admite cada módulo (el primero es el predeterminado). */
    private static final int[][][] SIZES = {
        {{4, 1}, {2, 1}, {2, 2}, {4, 2}},
        {{2, 2}, {3, 3}, {4, 4}},
        {{2, 2}, {3, 3}, {4, 4}},
        {{4, 1}, {4, 2}, {2, 1}},
        {{4, 1}, {2, 1}, {2, 2}, {4, 2}},
        {{4, 1}, {2, 1}},
        {{2, 1}, {4, 1}},
        {{2, 1}, {4, 1}},
        {{4, 1}, {4, 2}},
        {{4, 2}, {4, 3}, {2, 2}, {4, 1}},
    };
    /** Activos de serie. */
    private static final boolean[] ON = {true, true, true, true, false, true, true, true, false, false};
    /** Tamaño de serie de cada módulo (índice dentro de SIZES). */
    private static final int[] DEF_SIZE = {0, 0, 0, 0, 1, 0, 0, 0, 0, 0};

    private BigLayout() {
    }

    public static final class Item {
        public final String id;
        public int size;
        public boolean on;

        public Item(String id, int size, boolean on) {
            this.id = id;
            this.size = size;
            this.on = on;
        }

        public int w() {
            return SIZES[index(id)][size][0];
        }

        public int h() {
            return SIZES[index(id)][size][1];
        }
    }

    public static final class Placed {
        public final Item item;
        public final int col;
        public final int row;
        public final int w;
        public final int h;

        Placed(Item item, int col, int row, int w, int h) {
            this.item = item;
            this.col = col;
            this.row = row;
            this.w = w;
            this.h = h;
        }
    }

    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    public static int sizeCount(String id) {
        return SIZES[index(id)].length;
    }

    public static String sizeLabel(String id, int size) {
        int[] s = SIZES[index(id)][size];
        return s[0] + "×" + s[1];
    }

    public static List<Item> defaults() {
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < IDS.length; i++) {
            out.add(new Item(IDS[i], DEF_SIZE[i], ON[i]));
        }
        return out;
    }

    public static String serialize(List<Item> items) {
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(it.id).append(':').append(it.size).append(':').append(it.on ? 1 : 0);
        }
        return sb.toString();
    }

    /** Lee la configuración guardada: ignora lo desconocido y añade (con su valor de serie) los módulos que falten. */
    public static List<Item> parse(String cfg) {
        ArrayList<Item> out = new ArrayList<Item>();
        boolean[] seen = new boolean[IDS.length];
        if (cfg != null) {
            for (String part : cfg.split(";")) {
                String[] f = part.split(":");
                if (f.length < 3) {
                    continue;
                }
                int ix = index(f[0]);
                if (ix < 0 || seen[ix]) {
                    continue;
                }
                int size;
                try {
                    size = Integer.parseInt(f[1]);
                } catch (NumberFormatException e) {
                    size = DEF_SIZE[ix];
                }
                if (size < 0 || size >= SIZES[ix].length) {
                    size = DEF_SIZE[ix];
                }
                seen[ix] = true;
                out.add(new Item(IDS[ix], size, "1".equals(f[2])));
            }
        }
        for (int i = 0; i < IDS.length; i++) {
            if (!seen[i]) {
                out.add(new Item(IDS[i], DEF_SIZE[i], (cfg == null || cfg.length() == 0) && ON[i]));
            }
        }
        return out;
    }

    /** Coloca los módulos activos, en orden, en el primer hueco donde quepan. */
    public static List<Placed> pack(List<Item> items, int cols) {
        ArrayList<Placed> out = new ArrayList<Placed>();
        ArrayList<boolean[]> grid = new ArrayList<boolean[]>();
        for (Item it : items) {
            if (!it.on) {
                continue;
            }
            int w = Math.min(it.w(), cols);
            int h = it.h();
            boolean done = false;
            for (int r = 0; !done; r++) {
                for (int c = 0; c + w <= cols && !done; c++) {
                    if (fits(grid, r, c, w, h)) {
                        mark(grid, r, c, w, h, cols);
                        out.add(new Placed(it, c, r, w, h));
                        done = true;
                    }
                }
            }
        }
        return out;
    }

    public static int rows(List<Placed> placed) {
        int n = 0;
        for (Placed p : placed) {
            n = Math.max(n, p.row + p.h);
        }
        return n;
    }

    private static boolean fits(ArrayList<boolean[]> grid, int r, int c, int w, int h) {
        for (int y = r; y < r + h; y++) {
            if (y >= grid.size()) {
                continue;
            }
            boolean[] row = grid.get(y);
            for (int x = c; x < c + w; x++) {
                if (row[x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void mark(ArrayList<boolean[]> grid, int r, int c, int w, int h, int cols) {
        while (grid.size() < r + h) {
            grid.add(new boolean[cols]);
        }
        for (int y = r; y < r + h; y++) {
            for (int x = c; x < c + w; x++) {
                grid.get(y)[x] = true;
            }
        }
    }
}
