package dev.scratchwheel;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.List;
import org.json.JSONObject;

/**
 * Letras de la canción actual: primero una letra guardada por el usuario (importada o descargada), después la
 * incrustada en el archivo. La búsqueda en línea (lrclib.net) solo se hace cuando el usuario la pide.
 */
final class LyricsStore {
    private static String cachedKey = "";
    private static LyricsReader.Result cached;

    private LyricsStore() {
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "lyrics");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    private static String name(Uri uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(uri.toString().getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                sb.append(String.format("%02x", h[i]));
            }
            return sb.toString() + ".lrc";
        } catch (Exception e) {
            return "x.lrc";
        }
    }

    /** Lee la letra (bloqueante: llamar desde un hilo). Nunca devuelve null. */
    static synchronized LyricsReader.Result load(Context c, Uri uri) {
        if (uri == null) {
            return new LyricsReader.Result();
        }
        String key = uri.toString();
        if (key.equals(cachedKey) && cached != null) {
            return cached;
        }
        LyricsReader.Result r = new LyricsReader.Result();
        File own = new File(dir(c), name(uri));
        if (own.exists()) {
            try {
                String t = new String(readAll(new FileInputStream(own), 2 << 20), "UTF-8");
                r.synced = LyricsReader.parseLrc(t);
                r.plain = r.synced.isEmpty() ? t.trim() : LyricsReader.linesToText(r.synced);
            } catch (IOException ignored) {
            }
        }
        if (r.isEmpty()) {
            r = embedded(c, uri);
        }
        cachedKey = key;
        cached = r;
        return r;
    }

    static boolean hasOwn(Context c, Uri uri) {
        return uri != null && new File(dir(c), name(uri)).exists();
    }

    static synchronized void save(Context c, Uri uri, String text) throws IOException {
        FileOutputStream o = new FileOutputStream(new File(dir(c), name(uri)));
        try {
            o.write(text.getBytes("UTF-8"));
        } finally {
            o.close();
        }
        cachedKey = "";
    }

    static synchronized void forget(Context c, Uri uri) {
        new File(dir(c), name(uri)).delete();
        cachedKey = "";
    }

    private static LyricsReader.Result embedded(Context c, Uri uri) {
        ParcelFileDescriptor pfd = null;
        FileInputStream in = null;
        try {
            pfd = c.getContentResolver().openFileDescriptor(uri, "r");
            if (pfd == null) {
                return new LyricsReader.Result();
            }
            in = new FileInputStream(pfd.getFileDescriptor());
            final FileChannel ch = in.getChannel();
            final java.nio.ByteBuffer one = java.nio.ByteBuffer.allocate(1 << 16);
            LyricsReader.Src src = new LyricsReader.Src() {
                @Override
                public long length() {
                    try {
                        return ch.size();
                    } catch (IOException e) {
                        return 0;
                    }
                }

                @Override
                public int read(long pos, byte[] b, int off, int len) throws IOException {
                    java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(b, off, len);
                    return ch.read(bb, pos);
                }
            };
            return LyricsReader.read(src);
        } catch (Exception e) {
            return new LyricsReader.Result();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (pfd != null) {
                    pfd.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    /** Importa un archivo .lrc / .txt elegido por el usuario. */
    static void importFrom(Context c, Uri uri, Uri track) throws IOException {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("sin acceso");
        }
        String t = new String(readAll(in, 2 << 20), "UTF-8");
        save(c, track, t);
    }

    /**
     * Busca en lrclib.net por título, artista y duración. Devuelve el texto LRC (o la letra sin tiempos) o null.
     * Solo se llama cuando el usuario pulsa el botón; envía título, artista, álbum y duración.
     */
    static String fetchOnline(String title, String artist, String album, int durSec) throws IOException {
        StringBuilder q = new StringBuilder("https://lrclib.net/api/get?track_name=");
        q.append(URLEncoder.encode(title, "UTF-8")).append("&artist_name=").append(URLEncoder.encode(artist, "UTF-8"));
        if (album.length() > 0) {
            q.append("&album_name=").append(URLEncoder.encode(album, "UTF-8"));
        }
        if (durSec > 0) {
            q.append("&duration=").append(durSec);
        }
        String body = httpGet(q.toString());
        if (body == null) {
            body = httpGet("https://lrclib.net/api/search?q=" + URLEncoder.encode(artist + " " + title, "UTF-8"));
            if (body == null) {
                return null;
            }
            try {
                org.json.JSONArray arr = new org.json.JSONArray(body);
                for (int i = 0; i < arr.length(); i++) {
                    String t = pick(arr.getJSONObject(i));
                    if (t != null) {
                        return t;
                    }
                }
            } catch (org.json.JSONException ignored) {
            }
            return null;
        }
        try {
            return pick(new JSONObject(body));
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private static String pick(JSONObject o) {
        String s = o.optString("syncedLyrics", "");
        if (s.length() > 0 && !"null".equals(s)) {
            return s;
        }
        s = o.optString("plainLyrics", "");
        return s.length() > 0 && !"null".equals(s) ? s : null;
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(10000);
        h.setRequestProperty("User-Agent", "Scratch-Android (https://github.com/Ismael-Guenniche/Scratch)");
        try {
            if (h.getResponseCode() != 200) {
                return null;
            }
            return new String(readAll(h.getInputStream(), 1 << 20), "UTF-8");
        } finally {
            h.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int max) throws IOException {
        try {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) {
                o.write(b, 0, n);
                if (o.size() > max) {
                    throw new IOException("demasiado grande");
                }
            }
            return o.toByteArray();
        } finally {
            in.close();
        }
    }

    static List<LyricsReader.Line> lines(LyricsReader.Result r) {
        return r.synced;
    }
}
