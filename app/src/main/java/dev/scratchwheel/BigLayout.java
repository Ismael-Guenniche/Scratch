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
        /** Posición fija en la rejilla (-1 = automática: primer hueco libre). */
        public int col = -1;
        public int row = -1;
        /** Tamaño libre en celdas (0 = el de la lista de tamaños). */
        public int cw;
        public int ch;

        public Item(String id, int size, boolean on) {
            this.id = id;
            this.size = size;
            this.on = on;
        }

        public int w() {
            return cw > 0 ? cw : SIZES[index(id)][size][0];
        }

        public int h() {
            return ch > 0 ? ch : SIZES[index(id)][size][1];
        }

        public boolean fixed() {
            return col >= 0 && row >= 0;
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
            sb.append(it.id).append(':').append(it.size).append(':').append(it.on ? 1 : 0).append(':')
                    .append(it.col).append(':').append(it.row).append(':').append(it.cw).append(':')
                    .append(it.ch);
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
                Item it = new Item(IDS[ix], size, "1".equals(f[2]));
                if (f.length >= 7) {
                    it.col = num(f[3], -1, -1, COLS - 1);
                    it.row = num(f[4], -1, -1, MAX_ROWS);
                    it.cw = num(f[5], 0, 0, COLS);
                    it.ch = num(f[6], 0, 0, MAX_H);
                    if (it.col >= 0 && it.cw > 0 && it.col + it.cw > COLS) {
                        it.col = COLS - it.cw;
                    }
                    if (it.col < 0 || it.row < 0) {
                        it.col = -1;
                        it.row = -1;
                    }
                }
                out.add(it);
            }
        }
        for (int i = 0; i < IDS.length; i++) {
            if (!seen[i]) {
                out.add(new Item(IDS[i], DEF_SIZE[i], (cfg == null || cfg.length() == 0) && ON[i]));
            }
        }
        return out;
    }

    public static final int MAX_ROWS = 60;
    public static final int MAX_H = 10;

    private static int num(String t, int def, int lo, int hi) {
        try {
            int v = Integer.parseInt(t);
            return v < lo || v > hi ? def : v;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Coloca los módulos activos, en orden, en el primer hueco donde quepan. */
    public static List<Placed> pack(List<Item> items, int cols) {
        ArrayList<Placed> out = new ArrayList<Placed>();
        ArrayList<boolean[]> grid = new ArrayList<boolean[]>();
        ArrayList<Item> auto = new ArrayList<Item>();
        // primero los que tienen posición fija (si caben y no se pisan); el resto, en el primer hueco
        for (Item it : items) {
            if (!it.on) {
                continue;
            }
            int w = Math.min(it.w(), cols);
            int h = it.h();
            if (it.fixed() && it.col + w <= cols && fits(grid, it.row, it.col, w, h)) {
                mark(grid, it.row, it.col, w, h, cols);
                out.add(new Placed(it, it.col, it.row, w, h));
            } else {
                auto.add(it);
            }
        }
        for (Item it : auto) {
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

    /** Fija la posición actual de todos los módulos activos (el editor trabaja siempre con posiciones). */
    public static void freeze(List<Item> items, int cols) {
        for (Placed p : pack(items, cols)) {
            p.item.col = p.col;
            p.item.row = p.row;
            p.item.cw = p.w;
            p.item.ch = p.h;
        }
    }

    /** true si el módulo cabe en (col,row) con tamaño w×h sin pisar a los demás activos ni salirse. */
    public static boolean canPlace(List<Item> items, Item self, int col, int row, int w, int h, int cols) {
        if (col < 0 || row < 0 || col + w > cols || w < 1 || h < 1 || row + h > MAX_ROWS) {
            return false;
        }
        for (Item o : items) {
            if (o == self || !o.on || !o.fixed()) {
                continue;
            }
            int ow = Math.min(o.w(), cols);
            int oh = o.h();
            if (col < o.col + ow && o.col < col + w && row < o.row + oh && o.row < row + h) {
                return false;
            }
        }
        return true;
    }

    /** Primer hueco libre para un módulo de w×h entre los demás (que deben estar fijados); {col,row}. */
    public static int[] firstFree(List<Item> items, Item self, int w, int h, int cols) {
        for (int r = 0; r < MAX_ROWS; r++) {
            for (int c = 0; c + w <= cols; c++) {
                if (canPlace(items, self, c, r, w, h, cols)) {
                    return new int[] {c, r};
                }
            }
        }
        return new int[] {0, 0};
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
