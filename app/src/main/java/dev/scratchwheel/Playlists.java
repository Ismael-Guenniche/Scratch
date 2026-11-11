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
import java.util.HashMap;
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
    /** Portadas personalizadas: nombre de la lista -> ruta de una imagen propia de la app. */
    private final HashMap<String, String> covers = new HashMap<String, String>();
    /** Carpeta enlazada a cada lista: nombre de la lista -> URI del árbol de documentos. */
    private final HashMap<String, String> folders = new HashMap<String, String>();
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
            String c = covers.remove(k);
            if (c != null) {
                new File(c).delete();
            }
            folders.remove(k);
            changed();
        }
    }

    /** Cambia el nombre conservando la posición, las canciones y la portada. false si no se pudo (nombre vacío o repetido). */
    public synchronized boolean rename(String old, String neu) {
        String k = find(old);
        String n = clean(neu);
        if (k == null || n.length() == 0) {
            return false;
        }
        String other = find(n);
        if (other != null && !other.equals(k)) {
            return false;
        }
        if (n.equals(k)) {
            return true;
        }
        LinkedHashMap<String, ArrayList<String>> nm = new LinkedHashMap<String, ArrayList<String>>();
        for (Map.Entry<String, ArrayList<String>> e : map.entrySet()) {
            nm.put(e.getKey().equals(k) ? n : e.getKey(), e.getValue());
        }
        map.clear();
        map.putAll(nm);
        String c = covers.remove(k);
        if (c != null) {
            covers.put(n, c);
        }
        String fo = folders.remove(k);
        if (fo != null) {
            folders.put(n, fo);
        }
        changed();
        return true;
    }

    /** Posición (desde 0) de la canción en la lista, o -1. */
    public synchronized int indexOf(String name, String uri) {
        ArrayList<String> l = map.get(find(name));
        return l == null ? -1 : l.indexOf(uri);
    }

    /** Mueve la canción a la posición {@code to} (desde 0, se ajusta a los límites). false si no cambió nada. */
    public synchronized boolean move(String name, String uri, int to) {
        ArrayList<String> l = map.get(find(name));
        if (l == null) {
            return false;
        }
        int from = l.indexOf(uri);
        if (from < 0) {
            return false;
        }
        int dest = Math.max(0, Math.min(l.size() - 1, to));
        if (dest == from) {
            return false;
        }
        l.remove(from);
        l.add(dest, uri);
        changed();
        return true;
    }

    /** Fija el orden de la lista: primero {@code order} (solo lo que ya estaba en la lista), luego lo que sobre. */
    public synchronized boolean setOrder(String name, List<String> order) {
        ArrayList<String> l = map.get(find(name));
        if (l == null) {
            return false;
        }
        ArrayList<String> out = new ArrayList<String>();
        java.util.HashSet<String> inList = new java.util.HashSet<String>(l);
        java.util.HashSet<String> used = new java.util.HashSet<String>();
        for (String u : order) {
            if (inList.contains(u) && used.add(u)) {
                out.add(u);
            }
        }
        for (String u : l) {
            if (used.add(u)) {
                out.add(u);
            }
        }
        if (out.equals(l)) {
            return false;
        }
        l.clear();
        l.addAll(out);
        changed();
        return true;
    }

    /** Ruta de la portada personalizada de la lista, o null si usa el mosaico por defecto. */
    public synchronized String cover(String name) {
        String k = find(name);
        return k == null ? null : covers.get(k);
    }

    /** Pone (o quita, con null) la portada personalizada. Borra la imagen anterior si era otra. */
    public synchronized void setCover(String name, String path) {
        String k = find(name);
        if (k == null) {
            return;
        }
        String old = path == null ? covers.remove(k) : covers.put(k, path);
        if (old != null && !old.equals(path)) {
            new File(old).delete();
        }
        changed();
    }

    /** URI de la carpeta enlazada a la lista, o null si no tiene. */
    public synchronized String folder(String name) {
        String k = find(name);
        return k == null ? null : folders.get(k);
    }

    /** Enlaza la lista a una carpeta (o la desenlaza con null). */
    public synchronized void setFolder(String name, String treeUri) {
        String k = find(name);
        if (k == null) {
            return;
        }
        if (treeUri == null) {
            folders.remove(k);
        } else {
            folders.put(k, treeUri);
        }
        changed();
    }

    /** Todas las canciones de las listas que tienen una carpeta enlazada (no deben desaparecer de la biblioteca). */
    public synchronized java.util.HashSet<String> linkedUris() {
        java.util.HashSet<String> out = new java.util.HashSet<String>();
        for (String k : folders.keySet()) {
            ArrayList<String> l = map.get(k);
            if (l != null) {
                out.addAll(l);
            }
        }
        return out;
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
        saveCovers();
        saveFolders();
    }

    private File coversFile() {
        return file == null ? null : new File(file.getPath() + ".covers");
    }

    private void saveCovers() {
        File f = coversFile();
        if (f == null) {
            return;
        }
        if (covers.isEmpty()) {
            f.delete();
            return;
        }
        File tmp = new File(f.getPath() + ".tmp");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            for (Map.Entry<String, String> e : covers.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue());
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
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    private void loadCovers() {
        File f = coversFile();
        if (f == null || !f.exists()) {
            return;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t > 0) {
                    String k = find(line.substring(0, t));
                    if (k != null) {
                        covers.put(k, line.substring(t + 1));
                    }
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

    private File foldersFile() {
        return file == null ? null : new File(file.getPath() + ".folders");
    }

    private void saveFolders() {
        File f = foldersFile();
        if (f == null) {
            return;
        }
        if (folders.isEmpty()) {
            f.delete();
            return;
        }
        File tmp = new File(f.getPath() + ".tmp");
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
            for (Map.Entry<String, String> e : folders.entrySet()) {
                w.write(e.getKey() + "\t" + e.getValue());
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
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    private void loadFolders() {
        File f = foldersFile();
        if (f == null || !f.exists()) {
            return;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t > 0) {
                    String k = find(line.substring(0, t));
                    if (k != null) {
                        folders.put(k, line.substring(t + 1));
                    }
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
        loadCovers();
        loadFolders();
        version++;
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
