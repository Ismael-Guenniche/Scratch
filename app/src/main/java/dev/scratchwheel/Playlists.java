package dev.scratchwheel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Listas de reproducción propias: nombre + lista de URIs, guardadas en un archivo de texto. Sin dependencias de Android. */
public final class Playlists {
    public Playlists() {
    }

    static final Playlists I = new Playlists();

    private final LinkedHashMap<String, ArrayList<String>> map = new LinkedHashMap<String, ArrayList<String>>();
    private File file;
    /** Sube cada vez que algo cambia, para que las pantallas sepan que deben redibujar. */
    volatile int version;

    public synchronized void init(File f) {
        if (file != null) {
            return;
        }
        file = f;
        load();
    }

    public synchronized List<String> names() {
        return new ArrayList<String>(map.keySet());
    }

    public synchronized List<String> uris(String name) {
        ArrayList<String> l = map.get(find(name));
        return l == null ? new ArrayList<String>() : new ArrayList<String>(l);
    }

    public synchronized int size(String name) {
        ArrayList<String> l = map.get(find(name));
        return l == null ? 0 : l.size();
    }

    /** Devuelve el nombre tal como está guardado (sin distinguir mayúsculas) o null. */
    public synchronized String find(String name) {
        if (name == null) {
            return null;
        }
        for (String k : map.keySet()) {
            if (k.equalsIgnoreCase(name.trim())) {
                return k;
            }
        }
        return null;
    }

    public static String clean(String name) {
        String n = name == null ? "" : name.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ').trim();
        return n.length() > 40 ? n.substring(0, 40).trim() : n;
    }

    public synchronized boolean create(String name) {
        String n = clean(name);
        if (n.length() == 0 || find(n) != null) {
            return false;
        }
        map.put(n, new ArrayList<String>());
        changed();
        return true;
    }

    public synchronized void delete(String name) {
        String k = find(name);
        if (k != null) {
            map.remove(k);
            changed();
        }
    }

    /** Añade al final las canciones que aún no estén; devuelve cuántas se añadieron. */
    public synchronized int add(String name, List<String> uris) {
        ArrayList<String> l = map.get(find(name));
        if (l == null) {
            return 0;
        }
        int n = 0;
        for (String u : uris) {
            if (u != null && u.length() > 0 && !l.contains(u)) {
                l.add(u);
                n++;
            }
        }
        if (n > 0) {
            changed();
        }
        return n;
    }

    public synchronized void remove(String name, String uri) {
        ArrayList<String> l = map.get(find(name));
        if (l != null && l.remove(uri)) {
            changed();
        }
    }

    private void changed() {
        version++;
        save();
    }

    private void save() {
        if (file == null) {
            return;
        }
        File tmp = new File(file.getPath() + ".tmp");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            for (Map.Entry<String, ArrayList<String>> e : map.entrySet()) {
                w.write("#" + e.getKey());
                w.newLine();
                for (String u : e.getValue()) {
                    w.write(u);
                    w.newLine();
                }
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
            ArrayList<String> cur = null;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    String n = clean(line.substring(1));
                    cur = n.length() == 0 || find(n) != null ? null : new ArrayList<String>();
                    if (cur != null) {
                        map.put(n, cur);
                    }
                } else if (cur != null && line.length() > 0) {
                    cur.add(line);
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
        version++;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
