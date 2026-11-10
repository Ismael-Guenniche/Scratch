package dev.scratchwheel;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Importa listas de Spotify desde un CSV (Exportify u otros) y las empareja con tus archivos locales. Sin dependencias de Android. */
public final class SpotifyImport {
    private SpotifyImport() {
    }

    public static final class Row {
        public final String title;
        public final String artist;
        public final String album;

        public Row(String title, String artist, String album) {
            this.title = title;
            this.artist = artist;
            this.album = album;
        }

        @Override
        public String toString() {
            return artist.length() == 0 ? title : artist + " - " + title;
        }
    }

    /** Pista local: título (etiqueta o archivo), artista y nombre de archivo. */
    public static final class Cand {
        final String title;
        final String artist;
        final String file;

        public Cand(String title, String artist, String file) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.file = file == null ? "" : file;
        }
    }

    public static final class Result {
        public final List<Integer> matched = new ArrayList<Integer>();
        public final List<Row> missing = new ArrayList<Row>();
        public int total;
    }

    // ------------------------------------------------------------------ CSV

    /** CSV RFC 4180: comillas, comillas dobles escapadas y saltos de línea dentro de campos. Detecta , o ;. */
    public static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<List<String>>();
        if (text == null) {
            return rows;
        }
        if (text.length() > 0 && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        char sep = ',';
        int nl = text.indexOf('\n');
        String head = nl < 0 ? text : text.substring(0, nl);
        if (count(head, ';') > count(head, ',')) {
            sep = ';';
        }
        List<String> row = new ArrayList<String>();
        StringBuilder f = new StringBuilder();
        boolean q = false;
        boolean any = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        f.append('"');
                        i++;
                    } else {
                        q = false;
                    }
                } else {
                    f.append(c);
                }
            } else if (c == '"') {
                q = true;
                any = true;
            } else if (c == sep) {
                row.add(f.toString());
                f.setLength(0);
                any = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (any || f.length() > 0) {
                    row.add(f.toString());
                    rows.add(row);
                }
                row = new ArrayList<String>();
                f.setLength(0);
                any = false;
            } else {
                f.append(c);
                any = true;
            }
        }
        if (any || f.length() > 0) {
            row.add(f.toString());
            rows.add(row);
        }
        return rows;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) {
                n++;
            }
        }
        return n;
    }

    /** Convierte un CSV en filas canción. Reconoce las cabeceras de Exportify y similares (en/es). */
    public static List<Row> parseTracks(String csv) {
        if (csv != null && csv.replace("\uFEFF", "").trim().startsWith("#EXTM3U")) {
            return parseM3u(csv);
        }
        List<List<String>> rows = parseCsv(csv);
        List<Row> out = new ArrayList<Row>();
        if (rows.isEmpty()) {
            return out;
        }
        List<String> h = rows.get(0);
        int ti = col(h, "track name", "trackname", "title", "name", "titulo", "título", "cancion", "canción", "song");
        int ar = col(h, "artist name(s)", "artist names", "artist name", "artists", "artist", "artista", "artistas");
        int al = col(h, "album name", "album", "álbum");
        if (ti < 0) {
            return out;
        }
        for (int r = 1; r < rows.size(); r++) {
            List<String> x = rows.get(r);
            String t = get(x, ti).trim();
            if (t.length() == 0) {
                continue;
            }
            out.add(new Row(t, get(x, ar).trim(), get(x, al).trim()));
        }
        return out;
    }

    /** M3U extendido: "#EXTINF:segundos,Artista - Título". */
    public static List<Row> parseM3u(String text) {
        List<Row> out = new ArrayList<Row>();
        for (String line : text.split("\\r?\\n")) {
            line = line.trim();
            if (!line.startsWith("#EXTINF:")) {
                continue;
            }
            int c = line.indexOf(',');
            if (c < 0 || c + 1 >= line.length()) {
                continue;
            }
            String t = line.substring(c + 1).trim();
            int d = t.indexOf(" - ");
            if (d > 0) {
                out.add(new Row(t.substring(d + 3).trim(), t.substring(0, d).trim(), ""));
            } else if (t.length() > 0) {
                out.add(new Row(t, "", ""));
            }
        }
        return out;
    }

    private static String get(List<String> r, int i) {
        return i >= 0 && i < r.size() ? r.get(i) : "";
    }

    private static int col(List<String> head, String... names) {
        for (String n : names) {
            for (int i = 0; i < head.size(); i++) {
                if (head.get(i).trim().toLowerCase(Locale.ROOT).equals(n)) {
                    return i;
                }
            }
        }
        return -1;
    }

    // ----------------------------------------------------------- emparejado

    /** Minúsculas, sin acentos, sin (feat…)/[…], sin "- remastered…", solo letras y números. */
    public static String norm(String s) {
        if (s == null) {
            return "";
        }
        String t = s.toLowerCase(Locale.ROOT);
        t = t.replaceAll("\\([^)]*\\)|\\[[^\\]]*\\]", " ");
        t = t.replaceAll("\\s-\\s(remaster(ed)?|live|mono|stereo|single|radio|version|edit|bonus|deluxe|\\d{4}).*$", " ");
        t = t.replaceAll("\\b(feat|ft|featuring)\\b.*$", " ");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        t = t.replaceAll("[^a-z0-9]+", " ").trim();
        return t;
    }

    private static String firstArtist(String a) {
        int i = a.length();
        for (String sp : new String[]{",", ";", " & ", " feat", " ft"}) {
            int k = a.toLowerCase(Locale.ROOT).indexOf(sp);
            if (k > 0 && k < i) {
                i = k;
            }
        }
        return norm(a.substring(0, i));
    }

    public static Result match(List<Row> rows, List<Cand> local) {
        Map<String, List<Integer>> byTitle = new HashMap<String, List<Integer>>();
        for (int i = 0; i < local.size(); i++) {
            Cand c = local.get(i);
            List<String> keys = new ArrayList<String>();
            keys.add(norm(c.title));
            keys.add(norm(c.file));
            int d = c.file.lastIndexOf(" - ");
            if (d >= 0) {
                keys.add(norm(c.file.substring(d + 3)));
            }
            for (String k : keys) {
                if (k.length() == 0) {
                    continue;
                }
                List<Integer> l = byTitle.get(k);
                if (l == null) {
                    l = new ArrayList<Integer>();
                    byTitle.put(k, l);
                }
                if (!l.contains(i)) {
                    l.add(i);
                }
            }
        }
        Result res = new Result();
        res.total = rows.size();
        for (Row r : rows) {
            int best = -1;
            List<Integer> l = byTitle.get(norm(r.title));
            if (l != null) {
                String fa = firstArtist(r.artist);
                for (int i : l) {
                    Cand c = local.get(i);
                    boolean artistOk = fa.length() == 0
                            || norm(c.artist).contains(fa) || norm(c.file).contains(fa)
                            || (c.artist.length() == 0 && c.file.indexOf(" - ") < 0);
                    if (artistOk) {
                        best = i;
                        break;
                    }
                }
            }
            if (best >= 0) {
                if (!res.matched.contains(best)) {
                    res.matched.add(best);
                }
            } else {
                res.missing.add(r);
            }
        }
        return res;
    }
}
