package android.app;

import android.content.Context;

/** En el escritorio el "fondo de pantalla" es el color de acento de Windows. */
public class WallpaperManager {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_LOCK = 2;

    public static WallpaperManager getInstance(Context c) {
        return new WallpaperManager();
    }

    public WallpaperColors getWallpaperColors(int which) {
        Integer a = desk.Sys.accent();
        return a == null ? null : new WallpaperColors(a);
    }
}
