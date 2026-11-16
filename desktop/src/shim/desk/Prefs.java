package desk;

import android.content.SharedPreferences;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/** SharedPreferences sobre un archivo de texto (clave, tipo, valor) en la carpeta de datos. */
public final class Prefs implements SharedPreferences {
    private static final HashMap<String, Prefs> ALL = new HashMap<String, Prefs>();
    private final File file;
    private final HashMap<String, Object> map = new HashMap<String, Object>();
    private final ArrayList<OnSharedPreferenceChangeListener> listeners =
            new ArrayList<OnSharedPreferenceChangeListener>();
    private boolean dirty;
    private boolean scheduled;

    public static synchronized Prefs get(String name) {
        Prefs p = ALL.get(name);
        if (p == null) {
            Rt.initDirs();
            File dir = new File(Rt.filesDir.getParentFile(), "prefs");
            dir.mkdirs();
            p = new Prefs(new File(dir, name + ".txt"));
            ALL.put(name, p);
        }
        return p;
    }

    /** Escribe ya todo lo pendiente (al cerrar la app). */
    public static void flushAll() {
        ArrayList<Prefs> l;
        synchronized (Prefs.class) {
            l = new ArrayList<Prefs>(ALL.values());
        }
        for (Prefs p : l) {
            p.write();
        }
    }

    private Prefs(File f) {
        file = f;
        read();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String unesc(String s) {
        if (s.indexOf('\\') < 0) {
            return s;
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' ? '\n' : n == 'r' ? '\r' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private void read() {
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            try {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] f = l.split("\t", 3);
                    if (f.length < 3) {
                        continue;
                    }
                    String k = unesc(f[0]);
                    String v = f[2];
                    try {
                        switch (f[1]) {
                            case "b":
                                map.put(k, Boolean.valueOf(v));
                                break;
                            case "i":
                                map.put(k, Integer.valueOf(v));
                                break;
                            case "l":
                                map.put(k, Long.valueOf(v));
                                break;
                            case "f":
                                map.put(k, Float.valueOf(v));
                                break;
                            case "S": {
                                HashSet<String> set = new HashSet<String>();
                                if (v.length() > 0) {
                                    for (String e : v.split("\t", -1)) {
                                        set.add(unesc(e));
                                    }
                                }
                                map.put(k, set);
                                break;
                            }
                            default:
                                map.put(k, unesc(v));
                                break;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            } finally {
                r.close();
            }
        } catch (java.io.IOException ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private void write() {
        TreeMap<String, Object> snap;
        synchronized (this) {
            if (!dirty) {
                scheduled = false;
                return;
            }
            dirty = false;
            scheduled = false;
            snap = new TreeMap<String, Object>(map);
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Object> e : snap.entrySet()) {
            Object v = e.getValue();
            b.append(esc(e.getKey())).append('\t');
            if (v instanceof Boolean) {
                b.append("b\t").append(v);
            } else if (v instanceof Integer) {
                b.append("i\t").append(v);
            } else if (v instanceof Long) {
                b.append("l\t").append(v);
            } else if (v instanceof Float) {
                b.append("f\t").append(v);
            } else if (v instanceof Set) {
                b.append("S\t");
                boolean first = true;
                for (String s : (Set<String>) v) {
                    if (!first) {
                        b.append('\t');
                    }
                    first = false;
                    b.append(esc(s));
                }
            } else {
                b.append("s\t").append(esc(String.valueOf(v)));
            }
            b.append('\n');
        }
        synchronized (file) {
            File tmp = new File(file.getPath() + ".tmp");
            try {
                Writer w = new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8");
                try {
                    w.write(b.toString());
                } finally {
                    w.close();
                }
                java.nio.file.Files.move(tmp.toPath(), file.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.io.IOException ignored) {
            }
        }
    }

    private void changed(ArrayList<String> keys, boolean sync) {
        boolean plan = false;
        synchronized (this) {
            dirty = true;
            if (!sync && !scheduled) {
                scheduled = true;
                plan = true;
            }
        }
        if (sync) {
            write();
        } else if (plan) {
            Rt.TIMER.schedule(new Runnable() {
                @Override
                public void run() {
                    write();
                }
            }, 400, TimeUnit.MILLISECONDS);
        }
        final OnSharedPreferenceChangeListener[] ls;
        synchronized (listeners) {
            ls = listeners.toArray(new OnSharedPreferenceChangeListener[0]);
        }
        if (ls.length > 0) {
            final ArrayList<String> ks = keys;
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    for (OnSharedPreferenceChangeListener l : ls) {
                        for (String k : ks) {
                            l.onSharedPreferenceChanged(Prefs.this, k);
                        }
                    }
                }
            };
            if (Rt.isMain()) {
                r.run();
            } else {
                javax.swing.SwingUtilities.invokeLater(r);
            }
        }
    }

    @Override
    public synchronized Map<String, ?> getAll() {
        return new HashMap<String, Object>(map);
    }

    @Override
    public synchronized String getString(String k, String def) {
        Object o = map.get(k);
        return o instanceof String ? (String) o : def;
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized Set<String> getStringSet(String k, Set<String> def) {
        Object o = map.get(k);
        return o instanceof Set ? new HashSet<String>((Set<String>) o) : def;
    }

    @Override
    public synchronized int getInt(String k, int def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    @Override
    public synchronized long getLong(String k, long def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    @Override
    public synchronized float getFloat(String k, float def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).floatValue() : def;
    }

    @Override
    public synchronized boolean getBoolean(String k, boolean def) {
        Object o = map.get(k);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    @Override
    public synchronized boolean contains(String k) {
        return map.containsKey(k);
    }

    @Override
    public Editor edit() {
        return new Ed();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    private static final Object REMOVE = new Object();

    private final class Ed implements Editor {
        private final HashMap<String, Object> mods = new HashMap<String, Object>();
        private boolean clear;

        @Override
        public Editor putString(String k, String v) {
            mods.put(k, v == null ? REMOVE : v);
            return this;
        }

        @Override
        public Editor putStringSet(String k, Set<String> v) {
            mods.put(k, v == null ? REMOVE : new HashSet<String>(v));
            return this;
        }

        @Override
        public Editor putInt(String k, int v) {
            mods.put(k, v);
            return this;
        }

        @Override
        public Editor putLong(String k, long v) {
            mods.put(k, v);
            return this;
        }

        @Override
        public Editor putFloat(String k, float v) {
            mods.put(k, v);
            return this;
        }

        @Override
        public Editor putBoolean(String k, boolean v) {
            mods.put(k, v);
            return this;
        }

        @Override
        public Editor remove(String k) {
            mods.put(k, REMOVE);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        private void run(boolean sync) {
            ArrayList<String> keys = new ArrayList<String>();
            synchronized (Prefs.this) {
                if (clear) {
                    keys.addAll(map.keySet());
                    map.clear();
                }
                for (Map.Entry<String, Object> e : mods.entrySet()) {
                    if (e.getValue() == REMOVE) {
                        map.remove(e.getKey());
                    } else {
                        map.put(e.getKey(), e.getValue());
                    }
                    keys.add(e.getKey());
                }
            }
            changed(keys, sync);
        }

        @Override
        public boolean commit() {
            run(true);
            return true;
        }

        @Override
        public void apply() {
            run(false);
        }
    }
}
