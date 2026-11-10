package dev.scratchwheel;

import android.content.Context;
import java.util.Locale;

/**
 * Traducción de la interfaz. Los textos del código están en español; "es" los devuelve tal cual,
 * "en" (por defecto) los busca en {@link TrEn} y "ja" (solo en el sabor personal) antepone kana al inglés.
 */
final class Tr {
    static final String EN = "en";
    static final String ES = "es";
    static final String JA = "ja";

    static volatile String lang = EN;

    private Tr() {
    }

    /** Lee el idioma guardado. Llamar al arrancar (ScratchApp) y tras cambiarlo. */
    static void init(Context c) {
        String l = Lucent.prefs(c).getString("lang", EN);
        lang = valid(l) ? l : EN;
    }

    static boolean valid(String l) {
        return EN.equals(l) || ES.equals(l) || (JA.equals(l) && TrJa.ENABLED);
    }

    static void set(Context c, String l) {
        if (!valid(l)) {
            l = EN;
        }
        Lucent.prefs(c).edit().putString("lang", l).apply();
        lang = l;
        Lucent.stamp++; // las pantallas abiertas se reconstruyen al volver
    }

    static Locale locale() {
        return ES.equals(lang) ? new Locale("es") : JA.equals(lang) ? Locale.ENGLISH : Locale.ENGLISH;
    }

    /** Traduce un texto escrito en español. Respeta los espacios y saltos de línea de los extremos. */
    static String t(String es) {
        if (es == null || ES.equals(lang)) {
            return es;
        }
        int a = 0;
        int b = es.length();
        while (a < b && Character.isWhitespace(es.charAt(a))) {
            a++;
        }
        while (b > a && Character.isWhitespace(es.charAt(b - 1))) {
            b--;
        }
        String core = es.substring(a, b);
        String en = TrEn.M.get(core);
        if (en == null) {
            return es;
        }
        String out = en;
        if (JA.equals(lang) && TrJa.ENABLED) {
            String ja = TrJa.get(en);
            if (ja != null) {
                out = ja + "\n" + en;
            }
        }
        return es.substring(0, a) + out + es.substring(b);
    }

    /** Para vistas de puntos (no tienen kana): se queda con la última línea, el inglés. */
    static String dot(String s) {
        if (s == null) {
            return s;
        }
        int i = s.lastIndexOf('\n');
        return JA.equals(lang) && i >= 0 && hasKana(s.substring(0, i)) ? s.substring(i + 1) : s;
    }

    private static boolean hasKana(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x3040 && c <= 0x30FF) {
                return true;
            }
        }
        return false;
    }
}
