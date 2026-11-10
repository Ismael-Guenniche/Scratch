package dev.scratchwheel;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fuente de matriz de puntos 5x7 (la de la guía Lucent). Cada glifo es una cadena de filas de 0/1. */
public final class DotFont {
    public static final int ROWS = 7;
    private static final Map<Character, String> G = new HashMap<Character, String>();

    static {
        put('0', "01110100011001110101110011000101110");
        put('1', "00100011000010000100001000010001110");
        put('2', "01110100010000100010001000100011111");
        put('3', "11111000100010000010000011000101110");
        put('4', "00010001100101010010111110001000010");
        put('5', "11111100001111000001000011000101110");
        put('6', "00110010001000011110100011000101110");
        put('7', "11111000010001000100010000100001000");
        put('8', "01110100011000101110100011000101110");
        put('9', "01110100011000101111000010001001100");
        put('A', "01110100011000111111100011000110001");
        put('B', "11110100011000111110100011000111110");
        put('C', "01110100011000010000100001000101110");
        put('D', "11100100101000110001100011001011100");
        put('E', "11111100001000011110100001000011111");
        put('F', "11111100001111010000100001000010000");
        put('G', "01110100011000010111100011000101111");
        put('H', "10001100011000111111100011000110001");
        put('I', "01110001000010000100001000010001110");
        put('J', "00111000100001000010000101001001100");
        put('K', "10001100101010011000101001001010001");
        put('L', "10000100001000010000100001000011111");
        put('M', "10001110111010110101100011000110001");
        put('N', "10001110011010110011100011000110001");
        put('O', "01110100011000110001100011000101110");
        put('P', "11110100011000111110100001000010000");
        put('Q', "01110100011000110001101011001001101");
        put('R', "11110100011000111110101001001010001");
        put('S', "01111100001000001110000010000111110");
        put('T', "11111001000010000100001000010000100");
        put('U', "10001100011000110001100011000101110");
        put('V', "10001100011000110001100010101000100");
        put('W', "10001100011000110101101011101110001");
        put('X', "10001100010101000100010101000110001");
        put('Y', "10001100011000101010001000010000100");
        put('Z', "11111000010001000100010001000011111");
        put('/', "00001000100001000100010000100010000");
        put('-', "00000000000000011111000000000000000");
        put('.', "0000001");
        put(':', "0010100");
        put(' ', "000000000000000000000");
    }

    private DotFont() {
    }

    private static void put(char c, String rows) {
        G.put(c, rows);
    }

    /** Pasa a mayúsculas y quita acentos; lo desconocido se queda como espacio. */
    public static char fold(char c) {
        char u = Character.toUpperCase(c);
        switch (u) {
            case 'Á': return 'A';
            case 'É': return 'E';
            case 'Í': return 'I';
            case 'Ó': return 'O';
            case 'Ú':
            case 'Ü': return 'U';
            case 'Ñ': return 'N';
            default: return G.containsKey(u) ? u : ' ';
        }
    }

    /** Hiragana y katakana: no hay glifos a mano, se rasterizan 7x7 desde la fuente del sistema. */
    public static boolean isKana(char c) {
        return (c >= 0x3041 && c <= 0x30FF) || c == 0x30FC;
    }

    private static final Map<Character, String> K = new HashMap<Character, String>();

    private static synchronized String kana(char c) {
        String v = K.get(c);
        if (v != null) {
            return v;
        }
        final int px = 28;
        final int n = 7;
        StringBuilder sb = new StringBuilder();
        try {
            android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
            android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFF000000);
            p.setTextSize(px * 0.96f);
            p.setFakeBoldText(true);
            p.setTextAlign(android.graphics.Paint.Align.CENTER);
            cv.drawText(String.valueOf(c), px / 2f, px * 0.84f, p);
            int step = px / n;
            for (int row = 0; row < n; row++) {
                for (int col = 0; col < n; col++) {
                    int sum = 0;
                    for (int y = 0; y < step; y++) {
                        for (int x = 0; x < step; x++) {
                            sum += android.graphics.Color.alpha(bmp.getPixel(col * step + x, row * step + y));
                        }
                    }
                    sb.append(sum > step * step * 255 * 0.30 ? '1' : '0');
                }
            }
            bmp.recycle();
        } catch (RuntimeException e) {
            sb.setLength(0);
            for (int i = 0; i < n * n; i++) {
                sb.append('0');
            }
        }
        v = sb.toString();
        K.put(c, v);
        return v;
    }

    public static String pattern(char c) {
        if (isKana(c)) {
            return kana(c);
        }
        return G.get(fold(c));
    }

    public static int width(char c) {
        return pattern(c).length() / ROWS;
    }

    public static boolean on(char c, int col, int row) {
        String p = pattern(c);
        int w = p.length() / ROWS;
        if (col < 0 || col >= w || row < 0 || row >= ROWS) {
            return false;
        }
        return p.charAt(row * w + col) == '1';
    }

    /** Columnas totales del texto, con una columna de separación entre letras. */
    public static int cols(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        if (s.indexOf('\n') >= 0) {
            int best = 0;
            for (String line : s.split("\n", -1)) {
                best = Math.max(best, cols(line));
            }
            return best;
        }
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            n += width(s.charAt(i));
        }
        return n + s.length() - 1;
    }

    public static String upper(String s) {
        return s.toUpperCase(Locale.ROOT);
    }
}
