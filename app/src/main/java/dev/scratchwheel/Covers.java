package dev.scratchwheel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

/** Carátulas para la notificación y la pantalla de bloqueo. */
final class Covers {
    private static Bitmap placeholder;

    private Covers() {
    }

    /** Carátula incrustada en el archivo, recortada en cuadrado (máx. 512 px), o null. */
    static Bitmap embedded(Context c, Uri uri) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, uri);
            byte[] pic = r.getEmbeddedPicture();
            if (pic == null) {
                return null;
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            int side = Math.max(1, Math.min(o.outWidth, o.outHeight));
            o.inSampleSize = Math.max(1, side / 512);
            o.inJustDecodeBounds = false;
            Bitmap full = BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
            if (full == null) {
                return null;
            }
            int sq = Math.min(full.getWidth(), full.getHeight());
            return Bitmap.createBitmap(full, (full.getWidth() - sq) / 2, (full.getHeight() - sq) / 2, sq, sq);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    /** Sin carátula: la rueda de puntos sobre fondo oscuro, con el punto naranja. */
    static synchronized Bitmap placeholder() {
        if (placeholder != null) {
            return placeholder;
        }
        int s = 512;
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(0xFF161617);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = s / 2f;
        float cy = s / 2f;
        p.setColor(0xFF5E5E63);
        for (int ring = 0; ring < 7; ring++) {
            float rad = s * (0.20f + ring * 0.05f);
            int n = Math.max(6, (int) (2 * Math.PI * rad / (s * 0.052f)));
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n + ring * 0.3;
                c.drawCircle((float) (cx + rad * Math.cos(a)), (float) (cy + rad * Math.sin(a)), s * 0.0105f, p);
            }
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(s * 0.006f);
        p.setColor(0xFF8A8A90);
        c.drawCircle(cx, cy, s * 0.11f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFB471F);
        double a = -Math.PI / 3;
        c.drawCircle((float) (cx + s * 0.44 * Math.cos(a)), (float) (cy + s * 0.44 * Math.sin(a)), s * 0.026f, p);
        placeholder = b;
        return b;
    }
}
