package android.app;

import android.content.Context;

/**
 * En el escritorio el "fondo de pantalla" es el color de acento de Windows, y solo cuenta si se activa
 * Ajustes → «Colores del fondo»: sin eso la app se queda con su fondo neutro en vez de teñirse.
 */
public class WallpaperManager {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_LOCK = 2;

    public static WallpaperManager getInstance(Context c) {
        return new WallpaperManager();
    }

    public WallpaperColors getWallpaperColors(int which) {
        if (!desk.Prefs.get("ui").getBoolean("wall", false)) {
            return null;
        }
        Integer a = desk.Sys.accent();
        return a == null ? null : new WallpaperColors(a);
    }
}
