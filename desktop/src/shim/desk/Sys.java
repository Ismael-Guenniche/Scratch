package desk;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Lo que se le pregunta a Windows: tema claro/oscuro y color de acento. */
public final class Sys {
    private static volatile Boolean dark;
    private static volatile Integer accent;
    private static volatile boolean asked;

    private Sys() {
    }

    private static String reg(String key, String value) {
        if (!Rt.WINDOWS) {
            return null;
        }
        try {
            Process p = new ProcessBuilder("reg", "query", key, "/v", value).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String l;
            String found = null;
            while ((l = r.readLine()) != null) {
                int i = l.indexOf("REG_DWORD");
                if (i >= 0) {
                    found = l.substring(i + 9).trim();
                }
            }
            p.waitFor();
            return found;
        } catch (Exception e) {
            return null;
        }
    }

    private static synchronized void ask() {
        if (asked) {
            return;
        }
        asked = true;
        String over = System.getProperty("scratch.dark");
        if (over != null) {
            dark = Boolean.valueOf(over);
        } else {
            String v = reg("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                    "AppsUseLightTheme");
            if (v != null) {
                try {
                    dark = Long.decode(v) == 0;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        String a = reg("HKCU\\Software\\Microsoft\\Windows\\DWM", "AccentColor");
        if (a != null) {
            try {
                long abgr = Long.decode(a);
                int r = (int) (abgr & 0xFF);
                int g = (int) ((abgr >> 8) & 0xFF);
                int b = (int) ((abgr >> 16) & 0xFF);
                accent = 0xFF000000 | (r << 16) | (g << 8) | b;
            } catch (NumberFormatException ignored) {
            }
        }
    }

    /** ¿Windows está en modo oscuro? (oscuro por defecto si no se puede saber). */
    public static boolean isDark() {
        ask();
        return dark == null || dark;
    }

    /** Color de acento de Windows (ARGB), o null. */
    public static Integer accent() {
        ask();
        return accent;
    }
}
