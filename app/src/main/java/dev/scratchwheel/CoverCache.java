package dev.scratchwheel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.LruCache;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Miniaturas de las carátulas incrustadas en los archivos, para las listas, la barra pequeña y el inicio.
 * Se leen en segundo plano (2 hilos), se guardan reducidas en memoria y en disco (cache/covers) y las
 * vistas reciclables cancelan la petición anterior, así que desplazarse rápido por 3.000 canciones no atasca.
 * También construye la portada por defecto de cada lista: un mosaico 2x2 con 4 carátulas distintas.
 */
final class CoverCache {
    /** Lado de las miniaturas, en píxeles. */
    static final int THUMB = 160;
    private static final int SCAN_SONGS = 16;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>(
            Math.max(4 * 1024 * 1024, (int) (Runtime.getRuntime().maxMemory() / 12))) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    /** Canciones sin carátula (solo en memoria: si arreglas las etiquetas, se vuelve a mirar al reiniciar). */
    private static final HashSet<String> NONE = new HashSet<String>();
    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(), new ThreadFactory() {
                @Override
                public Thread newThread(final Runnable r) {
                    return new Thread(new Runnable() {
                        @Override
                        public void run() {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                            r.run();
                        }
                    }, "covers");
                }
            });
    private static File dir;

    private CoverCache() {
    }

    /** Petición pendiente de una vista; la vista guarda la última y cancela la anterior. */
    private static final class Req {
        final String key;
        volatile boolean cancelled;

        Req(String key) {
            this.key = key;
        }
    }

    static synchronized void init(Context c) {
        if (dir == null) {
            dir = new File(c.getCacheDir(), "covers");
            dir.mkdirs();
        }
    }

    // ------------------------------------------------------------------ vistas

    /** Imagen cuadrada con esquinas redondeadas para una carátula. */
    static ImageView view(Context c, float radiusDp) {
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        final float r = radiusDp * c.getResources().getDisplayMetrics().density;
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        iv.setClipToOutline(true);
        return iv;
    }

    /** Pone la carátula de esa canción en la vista (la rueda de puntos mientras carga o si no tiene). */
    static void into(Context c, final ImageView iv, final Uri uri) {
        cancel(iv);
        final String key = uri == null ? "" : uri.toString();
        final Req req = new Req(key);
        iv.setTag(req);
        Bitmap hit = key.length() == 0 ? null : MEM.get(key);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        iv.setImageBitmap(Covers.placeholder());
        if (key.length() == 0 || isNone(key)) {
            return;
        }
        final Context app = c.getApplicationContext();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                if (req.cancelled) {
                    return;
                }
                final Bitmap b = get(app, uri);
                deliver(iv, req, b);
            }
        });
    }

    /** Portada de una lista: la personalizada si la hay; si no, un mosaico con las carátulas de sus primeras canciones. */
    static void intoPlaylist(Context c, final ImageView iv, final String name) {
        cancel(iv);
        final String custom = Playlists.I.cover(name);
        final List<String> us = Playlists.I.uris(name);
        final int n = Math.min(SCAN_SONGS, us.size());
        final String key;
        if (custom != null) {
            key = "plc:" + custom + "|" + new File(custom).lastModified();
        } else {
            key = "pl:" + name + "|" + us.subList(0, n).hashCode();
        }
        final Req req = new Req(key);
        iv.setTag(req);
        Bitmap hit = MEM.get(key);
        if (hit != null) {
            iv.setImageBitmap(hit);
            return;
        }
        iv.setImageBitmap(Covers.placeholder());
        if (custom == null && n == 0) {
            return;
        }
        final Context app = c.getApplicationContext();
        final List<String> first = new ArrayList<String>(us.subList(0, n));
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                if (req.cancelled) {
                    return;
                }
                Bitmap out = custom != null ? decodeCustom(custom) : mosaic(app, first, req);
                if (out != null) {
                    MEM.put(key, out);
                }
                deliver(iv, req, out);
            }
        });
    }

    private static void cancel(ImageView iv) {
        Object old = iv.getTag();
        if (old instanceof Req) {
            ((Req) old).cancelled = true;
        }
    }

    private static void deliver(final ImageView iv, final Req req, final Bitmap b) {
        if (b == null || req.cancelled) {
            return;
        }
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                if (!req.cancelled && iv.getTag() == req) {
                    iv.setImageBitmap(b);
                }
            }
        });
    }

    // ------------------------------------------------------------------ lectura (hilo de fondo)

    private static boolean isNone(String key) {
        synchronized (NONE) {
            return NONE.contains(key);
        }
    }

    /** Miniatura de la carátula incrustada del archivo, o null si no tiene. Llamar desde un hilo de fondo. */
    static Bitmap get(Context c, Uri uri) {
        String key = uri.toString();
        Bitmap b = MEM.get(key);
        if (b != null) {
            return b;
        }
        if (isNone(key)) {
            return null;
        }
        File f = diskFile(key);
        if (f != null && f.exists()) {
            b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) {
                MEM.put(key, b);
                return b;
            }
        }
        Bitmap full = Covers.embedded(c, uri);
        if (full == null) {
            synchronized (NONE) {
                NONE.add(key);
            }
            return null;
        }
        b = full.getWidth() > THUMB ? Bitmap.createScaledBitmap(full, THUMB, THUMB, true) : full;
        if (b != full) {
            full.recycle();
        }
        if (f != null) {
            FileOutputStream o = null;
            try {
                o = new FileOutputStream(f);
                b.compress(Bitmap.CompressFormat.JPEG, 85, o);
            } catch (Exception ignored) {
                f.delete();
            } finally {
                if (o != null) {
                    try {
                        o.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        MEM.put(key, b);
        return b;
    }

    private static File diskFile(String key) {
        if (dir == null) {
            return null;
        }
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) {
                sb.append(String.format("%02x", x));
            }
            return new File(dir, sb.append(".jpg").toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap decodeCustom(String path) {
        try {
            return BitmapFactory.decodeFile(path);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    /**
     * Mosaico 2x2 con las 4 primeras carátulas distintas entre las primeras canciones de la lista (varias
     * canciones de un mismo álbum no repiten el cuadro). Con menos de 4 distintas, usa solo la primera.
     */
    private static Bitmap mosaic(Context c, List<String> uris, Req req) {
        ArrayList<Bitmap> picks = new ArrayList<Bitmap>();
        HashSet<Integer> seen = new HashSet<Integer>();
        Bitmap firstAny = null;
        for (String u : uris) {
            if (req.cancelled) {
                return null;
            }
            Bitmap b = get(c, Uri.parse(u));
            if (b == null) {
                continue;
            }
            if (firstAny == null) {
                firstAny = b;
            }
            if (seen.add(signature(b))) {
                picks.add(b);
                if (picks.size() == 4) {
                    break;
                }
            }
        }
        if (picks.size() < 4) {
            return firstAny;
        }
        Bitmap m = Bitmap.createBitmap(THUMB, THUMB, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(m);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        int h = THUMB / 2;
        for (int i = 0; i < 4; i++) {
            Rect dst = new Rect((i % 2) * h, (i / 2) * h, (i % 2) * h + h, (i / 2) * h + h);
            cv.drawBitmap(picks.get(i), null, dst, p);
        }
        return m;
    }

    /** Huella barata de una imagen (rejilla 8x8 de colores cuantizados) para saber si dos carátulas son la misma. */
    private static int signature(Bitmap b) {
        int h = 17;
        int w = b.getWidth();
        int ht = b.getHeight();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int px = b.getPixel(x * w / 8 + w / 16, y * ht / 8 + ht / 16);
                h = h * 31 + (px & 0x00F0F0F0);
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ imagen propia para una lista

    /** Copia una imagen elegida por el usuario como portada de lista: cuadrada, máx. 512 px, en la carpeta de la app. */
    static File importImage(Context c, Uri src) {
        InputStream in = null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            in = c.getContentResolver().openInputStream(src);
            BitmapFactory.decodeStream(in, null, o);
            in.close();
            int side = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, side / 512);
            o.inJustDecodeBounds = false;
            in = c.getContentResolver().openInputStream(src);
            Bitmap full = BitmapFactory.decodeStream(in, null, o);
            if (full == null) {
                return null;
            }
            int sq = Math.min(full.getWidth(), full.getHeight());
            Bitmap crop = Bitmap.createBitmap(full, (full.getWidth() - sq) / 2, (full.getHeight() - sq) / 2, sq, sq);
            File d = new File(c.getFilesDir(), "plcovers");
            d.mkdirs();
            File out = new File(d, "pl_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream os = new FileOutputStream(out);
            try {
                crop.compress(Bitmap.CompressFormat.JPEG, 90, os);
            } finally {
                os.close();
            }
            return out;
        } catch (Exception | OutOfMemoryError e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
