package dev.scratchwheel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Listas hechas por Claude (solo el sabor «personal»). Sin Android, testable en la JVM.
 * <ol>
 * <li>La app exporta la biblioteca a {@code biblioteca.json}: id, título, artista, álbum, duración, archivo y carpeta
 * de cada canción, y unas instrucciones con el formato de respuesta.</li>
 * <li>Se le manda a Claude junto con la petición («solo Estopa», «canciones pesadas tipo Linkin Park»…).</li>
 * <li>Claude devuelve un {@code playlist.json} con el nombre, una descripción y los id en orden; la app lo importa y
 * crea la lista. Si un id no coincide (por ejemplo, la canción cambió de duración) se busca por artista y título.</li>
 * </ol>
 */
final class PlaylistJson {
    static final String FORMAT_LIBRARY = "scratch-library";
    static final String FORMAT_PLAYLIST = "scratch-playlist";

    private PlaylistJson() {
    }

    /** Una canción de la biblioteca. */
    static final class Song {
        final String title;
        final String artist;
        final String album;
        final long durMs;
        final String file;
        final String folder;

        Song(String title, String artist, String album, long durMs, String file, String folder) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
            this.album = album == null ? "" : album;
            this.durMs = durMs;
            this.file = file == null ? "" : file;
            this.folder = folder == null ? "" : folder;
        }

        /** Título para el id: la etiqueta, o el nombre del archivo sin «Artista - » ni extensión. */
        String bestTitle() {
            if (title.length() > 0) {
                return title;
            }
            String f = file;
            int dot = f.lastIndexOf('.');
            if (dot > 0) {
                f = f.substring(0, dot);
            }
            int dash = f.indexOf(" - ");
            return dash > 0 ? f.substring(dash + 3) : f;
        }

        String bestArtist() {
            if (artist.length() > 0) {
                return artist;
            }
            String f = file;
            int dash = f.indexOf(" - ");
            return dash > 0 ? f.substring(0, dash) : "";
        }
    }

    /** Una canción pedida en la lista que devuelve Claude. */
    static final class Item {
        final String id;
        final String title;
        final String artist;

        Item(String id, String title, String artist) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
        }
    }

    /** Una lista importada. */
    static final class Playlist {
        String name = "";
        String description = "";
        final List<Item> items = new ArrayList<Item>();
    }

    /** Resultado de emparejar una lista con la biblioteca. */
    static final class Match {
        /** Índices de canciones de la biblioteca, en el orden de la lista y sin repetir. */
        final List<Integer> found = new ArrayList<Integer>();
        /** Lo que no se encontró («Artista - Título» o el id). */
        final List<String> missing = new ArrayList<String>();
    }

    /**
     * Id estable: no depende de la ruta (mover la carpeta no lo rompe). Artista y título normalizados más la
     * duración redondeada a 2 s; 12 letras/cifras.
     */
    static String id(Song s) {
        String key = SmartSearch.norm(s.bestArtist()) + "|" + SmartSearch.norm(s.bestTitle()) + "|"
                + Math.round(s.durMs / 2000.0);
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x100000001b3L;
        }
        String b = Long.toString(h & Long.MAX_VALUE, 36);
        while (b.length() < 12) {
            b = "0" + b;
        }
        return b.substring(b.length() - 12);
    }

    static final String INSTRUCTIONS = "Biblioteca de Scratch. Para crear una lista, responde con un archivo JSON así: "
            + "{\"format\":\"scratch-playlist\",\"version\":1,\"name\":\"Nombre (máx. 40 letras)\","
            + "\"description\":\"Qué es la lista\",\"songs\":[{\"id\":\"...\",\"artist\":\"...\",\"title\":\"...\"}]}. "
            + "Usa los id de esta biblioteca, en el orden en que deben sonar; artist y title sirven de respaldo si un id "
            + "no coincide. Para varias listas a la vez: {\"format\":\"scratch-playlist\",\"version\":1,"
            + "\"playlists\":[{...},{...}]}.";

    /** biblioteca.json */
    static String exportLibrary(List<Song> songs, String appVersion, String date) {
        StringBuilder b = new StringBuilder(songs.size() * 160 + 1024);
        b.append("{\n  \"format\": \"").append(FORMAT_LIBRARY).append("\",\n  \"version\": 1,\n");
        b.append("  \"app\": ").append(q("Scratch " + appVersion)).append(",\n");
        b.append("  \"exported\": ").append(q(date)).append(",\n");
        b.append("  \"count\": ").append(songs.size()).append(",\n");
        b.append("  \"instructions\": ").append(q(INSTRUCTIONS)).append(",\n");
        b.append("  \"songs\": [\n");
        HashMap<String, Integer> seen = new HashMap<String, Integer>();
        for (int i = 0; i < songs.size(); i++) {
            Song s = songs.get(i);
            String id = id(s);
            Integer n = seen.get(id);
            seen.put(id, n == null ? 1 : n + 1);
            if (n != null) {
                id = id + "-" + (n + 1);
            }
            b.append("    {\"id\": ").append(q(id));
            b.append(", \"title\": ").append(q(s.bestTitle()));
            b.append(", \"artist\": ").append(q(s.bestArtist()));
            if (s.album.length() > 0) {
                b.append(", \"album\": ").append(q(s.album));
            }
            if (s.durMs > 0) {
                b.append(", \"durationSec\": ").append(Math.round(s.durMs / 1000.0));
            }
            b.append(", \"file\": ").append(q(s.file));
            if (s.folder.length() > 0) {
                b.append(", \"folder\": ").append(q(s.folder));
            }
            b.append(i + 1 < songs.size() ? "},\n" : "}\n");
        }
        b.append("  ]\n}\n");
        return b.toString();
    }

    /** Lee un playlist.json (una lista o varias). Lanza IllegalArgumentException si no es un JSON válido. */
    @SuppressWarnings("unchecked")
    static List<Playlist> parse(String json) {
        Object root = new Parser(json).value();
        List<Object> lists = new ArrayList<Object>();
        if (root instanceof List) {
            lists.addAll((List<Object>) root);
        } else if (root instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) root;
            if (FORMAT_LIBRARY.equals(m.get("format"))) {
                throw new IllegalArgumentException("library");
            }
            Object pl = m.get("playlists");
            if (pl instanceof List) {
                lists.addAll((List<Object>) pl);
            } else {
                lists.add(m);
            }
        } else {
            throw new IllegalArgumentException("json");
        }
        List<Playlist> out = new ArrayList<Playlist>();
        for (Object o : lists) {
            if (!(o instanceof Map)) {
                continue;
            }
            Map<String, Object> m = (Map<String, Object>) o;
            Playlist p = new Playlist();
            p.name = str(m.get("name"));
            p.description = str(m.get("description"));
            Object songs = m.get("songs");
            if (songs == null) {
                songs = m.get("tracks");
            }
            if (songs instanceof List) {
                for (Object s : (List<Object>) songs) {
                    if (s instanceof Map) {
                        Map<String, Object> sm = (Map<String, Object>) s;
                        p.items.add(new Item(str(sm.get("id")), str(sm.get("title")), str(sm.get("artist"))));
                    } else if (s instanceof String) {
                        p.items.add(new Item((String) s, "", ""));
                    }
                }
            }
            if (p.name.length() == 0) {
                p.name = "Claude";
            }
            out.add(p);
        }
        return out;
    }

    /** Empareja con la biblioteca: primero por id, si no por artista + título y, si no, por título si es único. */
    static Match match(Playlist p, List<Song> lib) {
        HashMap<String, Integer> byId = new HashMap<String, Integer>();
        HashMap<String, Integer> byArtTitle = new HashMap<String, Integer>();
        HashMap<String, Integer> byTitle = new HashMap<String, Integer>();
        HashMap<String, Integer> seen = new HashMap<String, Integer>();
        for (int i = 0; i < lib.size(); i++) {
            Song s = lib.get(i);
            String id = id(s);
            Integer n = seen.get(id);
            seen.put(id, n == null ? 1 : n + 1);
            byId.put(n == null ? id : id + "-" + (n + 1), i);
            String a = SmartSearch.norm(s.bestArtist());
            String t = SmartSearch.norm(s.bestTitle());
            if (!byArtTitle.containsKey(a + "|" + t)) {
                byArtTitle.put(a + "|" + t, i);
            }
            byTitle.put(t, byTitle.containsKey(t) ? -1 : i);
        }
        Match m = new Match();
        java.util.HashSet<Integer> used = new java.util.HashSet<Integer>();
        for (Item it : p.items) {
            Integer idx = it.id.length() > 0 ? byId.get(it.id) : null;
            if (idx == null && it.title.length() > 0) {
                String t = SmartSearch.norm(it.title);
                idx = byArtTitle.get(SmartSearch.norm(it.artist) + "|" + t);
                if (idx == null) {
                    Integer u = byTitle.get(t);
                    idx = u != null && u >= 0 ? u : null;
                }
            }
            if (idx == null) {
                m.missing.add(it.title.length() > 0
                        ? (it.artist.length() > 0 ? it.artist + " - " : "") + it.title : it.id);
            } else if (used.add(idx)) {
                m.found.add(idx);
            }
        }
        return m;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    /** Cadena JSON con comillas y escapes. */
    static String q(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
            }
        }
        return b.append('"').toString();
    }

    /** Lector de JSON mínimo (objetos, listas, cadenas, números, true/false/null). */
    private static final class Parser {
        private final String s;
        private int i;
        private int depth;

        Parser(String s) {
            String t = s == null ? "" : s;
            // BOM, y un bloque ```json ... ``` si se pega tal cual desde el chat
            if (t.length() > 0 && t.charAt(0) == '﻿') {
                t = t.substring(1);
            }
            int a = t.indexOf("```");
            if (a >= 0) {
                int nl = t.indexOf('\n', a);
                int b = t.indexOf("```", nl < 0 ? a + 3 : nl);
                if (nl >= 0 && b > nl) {
                    t = t.substring(nl + 1, b);
                }
            }
            this.s = t;
        }

        private IllegalArgumentException err() {
            return new IllegalArgumentException("json @" + i);
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            ws();
            if (i >= s.length()) {
                throw err();
            }
            if (++depth > 64) {
                throw err();
            }
            try {
                char c = s.charAt(i);
                if (c == '{') {
                    i++;
                    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
                    ws();
                    if (i < s.length() && s.charAt(i) == '}') {
                        i++;
                        return m;
                    }
                    while (true) {
                        ws();
                        String k = string();
                        ws();
                        expect(':');
                        m.put(k, value());
                        ws();
                        if (i < s.length() && s.charAt(i) == ',') {
                            i++;
                            continue;
                        }
                        expect('}');
                        return m;
                    }
                }
                if (c == '[') {
                    i++;
                    ArrayList<Object> l = new ArrayList<Object>();
                    ws();
                    if (i < s.length() && s.charAt(i) == ']') {
                        i++;
                        return l;
                    }
                    while (true) {
                        l.add(value());
                        ws();
                        if (i < s.length() && s.charAt(i) == ',') {
                            i++;
                            continue;
                        }
                        expect(']');
                        return l;
                    }
                }
                if (c == '"') {
                    return string();
                }
                if (s.startsWith("true", i)) {
                    i += 4;
                    return Boolean.TRUE;
                }
                if (s.startsWith("false", i)) {
                    i += 5;
                    return Boolean.FALSE;
                }
                if (s.startsWith("null", i)) {
                    i += 4;
                    return null;
                }
                int st = i;
                while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                    i++;
                }
                if (st == i) {
                    throw err();
                }
                String n = s.substring(st, i);
                try {
                    return n.indexOf('.') >= 0 || n.indexOf('e') >= 0 || n.indexOf('E') >= 0
                            ? (Object) Double.valueOf(n) : (Object) Long.valueOf(n);
                } catch (NumberFormatException e) {
                    throw err();
                }
            } finally {
                depth--;
            }
        }

        private void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw err();
            }
            i++;
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    if (i >= s.length()) {
                        throw err();
                    }
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n':
                            b.append('\n');
                            break;
                        case 't':
                            b.append('\t');
                            break;
                        case 'r':
                            b.append('\r');
                            break;
                        case 'b':
                            b.append('\b');
                            break;
                        case 'f':
                            b.append('\f');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) {
                                throw err();
                            }
                            try {
                                b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (NumberFormatException x) {
                                throw err();
                            }
                            i += 4;
                            break;
                        default:
                            b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
            throw err();
        }
    }
}
