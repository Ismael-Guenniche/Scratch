package dev.scratchwheel;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Caché de etiquetas (meta.txt) y contador de reproducciones (plays.txt). */
final class Meta {
    private static final Map<String, String[]> cache = new HashMap<String, String[]>();
    /** uri -> {conteo, última vez (ms)} */
    private static final Map<String, long[]> plays = new HashMap<String, long[]>();
    private static File metaFile;
    private static File playsFile;

    private Meta() {
    }

    static synchronized void init(Context c, List<Player.Track> tracks) {
        metaFile = new File(c.getFilesDir(), "meta.txt");
        playsFile = new File(c.getFilesDir(), "plays.txt");
        cache.clear();
        plays.clear();
        for (String l : readLines(metaFile)) {
            String[] f = l.split("\t", -1);
            if (f.length >= 4) {
                cache.put(f[0], new String[]{f[1], f[2], f[3]});
            }
        }
        for (String l : readLines(playsFile)) {
            String[] f = l.split("\t", -1);
            if (f.length >= 3) {
                try {
                    plays.put(f[0], new long[]{Long.parseLong(f[1]), Long.parseLong(f[2])});
                } catch (NumberFormatException ignored) {
                }
            }
        }
        apply(tracks);
    }

    static synchronized void apply(List<Player.Track> tracks) {
        for (Player.Track t : tracks) {
            String[] m = cache.get(t.uri.toString());
            if (m != null) {
                t.setMeta(m[0], m[1], m[2]);
            }
        }
    }

    /** Vuelve a aplicar las etiquetas guardadas (y los nombres editados) a una pista, tras editarla. */
    static synchronized void reapply(Player.Track t) {
        String[] m = cache.get(t.uri.toString());
        if (m != null) {
            t.setMeta(m[0], m[1], m[2]);
        } else {
            t.setMeta(t.tagTitle, t.tagArtist, t.album);
        }
    }

    /** Lee las etiquetas que faltan. Devuelve true si cambió algo. */
    static boolean scan(Context c, List<Player.Track> tracks) {
        boolean changed = false;
        int sinceSave = 0;
        for (Player.Track t : tracks) {
            String key = t.uri.toString();
            synchronized (Meta.class) {
                if (cache.containsKey(key)) {
                    continue;
                }
            }
            String[] m = read(c, t);
            synchronized (Meta.class) {
                cache.put(key, m);
            }
            t.setMeta(m[0], m[1], m[2]);
            changed = true;
            if (++sinceSave >= 200) {
                sinceSave = 0;
                save();
            }
        }
        if (changed) {
            save();
        }
        return changed;
    }

    private static String[] read(Context c, Player.Track t) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, t.uri);
            String ti = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
            String ar = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
            if (ar.length() == 0) {
                ar = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            }
            String al = clean(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
            return new String[]{ti, ar, al};
        } catch (RuntimeException e) {
            return new String[]{"", "", ""};
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static synchronized void save() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String[]> e : cache.entrySet()) {
            String[] m = e.getValue();
            sb.append(e.getKey()).append('\t').append(m[0]).append('\t').append(m[1])
                    .append('\t').append(m[2]).append('\n');
        }
        write(metaFile, sb.toString());
    }

    static synchronized void countPlay(Context c, String uri) {
        if (playsFile == null) {
            return;
        }
        long[] p = plays.get(uri);
        if (p == null) {
            p = new long[]{0, 0};
            plays.put(uri, p);
        }
        p[0]++;
        p[1] = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, long[]> e : plays.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()[0]).append('\t')
                    .append(e.getValue()[1]).append('\n');
        }
        write(playsFile, sb.toString());
    }

    static synchronized long count(String uri) {
        long[] p = plays.get(uri);
        return p == null ? 0 : p[0];
    }

    static synchronized long last(String uri) {
        long[] p = plays.get(uri);
        return p == null ? 0 : p[1];
    }

    private static java.util.List<String> readLines(File f) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        if (f == null || !f.exists()) {
            return out;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            try {
                String l;
                while ((l = r.readLine()) != null) {
                    out.add(l);
                }
            } finally {
                r.close();
            }
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    private static void write(File f, String s) {
        if (f == null) {
            return;
        }
        File tmp = new File(f.getPath() + ".tmp");
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
            try {
                w.write(s);
            } finally {
                w.close();
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (java.io.IOException ignored) {
        }
    }
}
