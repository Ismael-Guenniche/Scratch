package dev.scratchwheel;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recuerda las listas importadas (imports.txt) para reemparejarlas al rescanear y listar faltantes. */
final class Imports {
    private Imports() {
    }

    private static File file(Context c) {
        return new File(c.getFilesDir(), "imports.txt");
    }

    private static String f(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    static synchronized Map<String, List<SpotifyImport.Row>> load(Context c) {
        Map<String, List<SpotifyImport.Row>> out = new LinkedHashMap<String, List<SpotifyImport.Row>>();
        File fl = file(c);
        if (!fl.exists()) {
            return out;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(fl), "UTF-8"));
            try {
                List<SpotifyImport.Row> cur = null;
                String l;
                while ((l = r.readLine()) != null) {
                    if (l.startsWith("#")) {
                        cur = new ArrayList<SpotifyImport.Row>();
                        out.put(l.substring(1), cur);
                    } else if (cur != null) {
                        String[] p = l.split("\t", -1);
                        if (p.length >= 3) {
                            cur.add(new SpotifyImport.Row(p[0], p[1], p[2]));
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    static synchronized void save(Context c, String name, List<SpotifyImport.Row> rows) {
        Map<String, List<SpotifyImport.Row>> all = load(c);
        all.put(name, rows);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<SpotifyImport.Row>> e : all.entrySet()) {
            sb.append('#').append(f(e.getKey())).append('\n');
            for (SpotifyImport.Row r : e.getValue()) {
                sb.append(f(r.title)).append('\t').append(f(r.artist)).append('\t')
                        .append(f(r.album)).append('\n');
            }
        }
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(file(c)), "UTF-8");
            try {
                w.write(sb.toString());
            } finally {
                w.close();
            }
        } catch (java.io.IOException ignored) {
        }
    }

    static List<SpotifyImport.Cand> cands(List<Player.Track> tracks) {
        ArrayList<SpotifyImport.Cand> l = new ArrayList<SpotifyImport.Cand>();
        for (Player.Track t : tracks) {
            l.add(new SpotifyImport.Cand(t.tagTitle, t.tagArtist, t.name));
        }
        return l;
    }

    /** Reempareja todas las listas importadas con la biblioteca actual; añade las nuevas coincidencias. */
    static void resync(Context c, List<Player.Track> tracks) {
        List<SpotifyImport.Cand> local = cands(tracks);
        for (Map.Entry<String, List<SpotifyImport.Row>> e : load(c).entrySet()) {
            SpotifyImport.Result r = SpotifyImport.match(e.getValue(), local);
            ArrayList<String> uu = new ArrayList<String>();
            for (int i : r.matched) {
                uu.add(tracks.get(i).uri.toString());
            }
            if (!uu.isEmpty()) {
                Playlists.I.create(e.getKey());
                Playlists.I.add(e.getKey(), uu);
            }
        }
    }

    /** Todas las canciones que siguen sin aparecer en la biblioteca. */
    static List<SpotifyImport.Row> missing(Context c, List<Player.Track> tracks) {
        List<SpotifyImport.Cand> local = cands(tracks);
        ArrayList<SpotifyImport.Row> out = new ArrayList<SpotifyImport.Row>();
        for (List<SpotifyImport.Row> rows : load(c).values()) {
            for (SpotifyImport.Row r : SpotifyImport.match(rows, local).missing) {
                out.add(r);
            }
        }
        return out;
    }
}
