package android.content.res;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

/** Recursos: solo imágenes (res/drawable y res/mipmap-*) empaquetadas en el .jar bajo /res/. */
public class Resources {
    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String s) {
            super(s);
        }
    }

    public static final class Theme {
    }

    private static final HashMap<String, Integer> IDS = new HashMap<String, Integer>();
    private static final ArrayList<String> NAMES = new ArrayList<String>();
    private static final HashMap<Integer, Bitmap> CACHE = new HashMap<Integer, Bitmap>();
    private static final String[] DIRS = {"drawable", "mipmap-nodpi", "mipmap-xxxhdpi", "mipmap"};
    private static final Resources SYSTEM = new Resources();

    private final DisplayMetrics dm = new DisplayMetrics();
    private final Configuration cfg = new Configuration();

    public static Resources getSystem() {
        return SYSTEM;
    }

    public DisplayMetrics getDisplayMetrics() {
        return dm;
    }

    public Configuration getConfiguration() {
        return cfg;
    }

    public AssetManager getAssets() {
        return new AssetManager();
    }

    private static InputStream find(String name) {
        for (String d : DIRS) {
            for (String ext : new String[] {".png", ".jpg", ".webp"}) {
                InputStream in = Resources.class.getResourceAsStream("/res/" + d + "/" + name + ext);
                if (in != null) {
                    return in;
                }
            }
        }
        return null;
    }

    public synchronized int getIdentifier(String name, String type, String pkg) {
        Integer id = IDS.get(name);
        if (id != null) {
            return id;
        }
        int v = 0;
        InputStream in = find(name);
        if (in != null) {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
            }
            NAMES.add(name);
            v = 0x7f000000 + NAMES.size();
        }
        IDS.put(name, v);
        return v;
    }

    public synchronized String getResourceEntryName(int id) {
        int i = id - 0x7f000001;
        if (i < 0 || i >= NAMES.size()) {
            throw new NotFoundException("id " + id);
        }
        return NAMES.get(i);
    }

    /** Imagen de un recurso, o null. */
    public synchronized Bitmap bitmap(int id) {
        Bitmap b = CACHE.get(id);
        if (b != null) {
            return b;
        }
        int i = id - 0x7f000001;
        if (i < 0 || i >= NAMES.size()) {
            return null;
        }
        InputStream in = find(NAMES.get(i));
        if (in == null) {
            return null;
        }
        try {
            b = BitmapFactory.decodeStream(in);
        } finally {
            try {
                in.close();
            } catch (java.io.IOException ignored) {
            }
        }
        if (b != null) {
            CACHE.put(id, b);
        }
        return b;
    }

    public Drawable getDrawable(int id) {
        Bitmap b = bitmap(id);
        if (b == null) {
            throw new NotFoundException("drawable " + id);
        }
        return new BitmapDrawable(this, b);
    }

    public Drawable getDrawable(int id, Theme t) {
        return getDrawable(id);
    }

    public int getColor(int id) {
        return 0xFF000000;
    }

    public int getColor(int id, Theme t) {
        return 0xFF000000;
    }

    public String getString(int id) {
        return "";
    }

    public float getDimension(int id) {
        return 0;
    }

    public int getDimensionPixelSize(int id) {
        return 0;
    }
}
