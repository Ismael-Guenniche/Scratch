package dev.scratchwheel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Nombres de canción editados por el usuario (título y artista), guardados en un archivo de texto
 * aparte: no se toca el archivo de audio y sobreviven a un rescaneo. Un campo vacío significa
 * "sin cambio" (se usa la etiqueta del archivo). Sin dependencias de Android.
 */
public final class TrackEdits {
    public TrackEdits() {
    }

    static final TrackEdits I = new TrackEdits();

    private final HashMap<String, String[]> map = new HashMap<String, String[]>();
    private File file;

    public synchronized void init(File f) {
        if (file != null) {
            return;
        }
        file = f;
        load();
    }

    /** {título, artista} editados (cada uno puede ser ""), o null si la canción no tiene cambios. */
    public synchronized String[] get(String uri) {
        String[] e = map.get(uri);
        return e == null ? null : new String[] {e[0], e[1]};
    }

    public synchronized boolean has(String uri) {
        return map.containsKey(uri);
    }

    /** Guarda los cambios; si ambos campos quedan vacíos, se quita la edición. */
    public synchronized void set(String uri, String title, String artist) {
        String t = clean(title);
        String a = clean(artist);
        if (t.length() == 0 && a.length() == 0) {
            if (map.remove(uri) != null) {
                save();
            }
            return;
        }
        map.put(uri, new String[] {t, a});
        save();
    }

    public synchronized void clear(String uri) {
        if (map.remove(uri) != null) {
            save();
        }
    }

    public static String clean(String s) {
        String n = s == null ? "" : s.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ').trim();
        return n.length() > 120 ? n.substring(0, 120).trim() : n;
    }

    private void save() {
        if (file == null) {
            return;
        }
        File tmp = new File(file.getPath() + ".tmp");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            for (Map.Entry<String, String[]> e : map.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue()[0] + "\t" + e.getValue()[1]);
                w.newLine();
            }
        } catch (IOException ignored) {
            return;
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete();
            tmp.renameTo(file);
        }
    }

    private void load() {
        if (file == null || !file.exists()) {
            return;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length >= 3 && f[0].length() > 0) {
                    map.put(f[0], new String[] {clean(f[1]), clean(f[2])});
                }
            }
        } catch (IOException ignored) {
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
