package dev.scratchwheel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Artistas y álbumes de la biblioteca (sin Android, testable). Se agrupa sin mayúsculas, acentos ni signos; si una
 * canción no tiene etiqueta de artista se saca del nombre «Artista - Título.mp3».
 */
final class Groups {
    private Groups() {
    }

    /** Un artista o un álbum. */
    static final class Group {
        final String key;
        String name;
        /** Álbum: su artista (o «VARIOS»). Artista: número de álbumes. */
        String sub = "";
        final List<Integer> tracks = new ArrayList<Integer>();
        private String firstArtist;
        private boolean mixed;

        Group(String key, String name) {
            this.key = key;
            this.name = name;
        }
    }

    /** Artista de una canción: la etiqueta o, si no hay, lo que va antes de « - » en el nombre del archivo. */
    static String artistOf(String artist, String file) {
        if (artist != null && artist.trim().length() > 0) {
            return artist.trim();
        }
        if (file == null) {
            return "";
        }
        int dash = file.indexOf(" - ");
        return dash > 0 ? file.substring(0, dash).trim() : "";
    }

    /**
     * Agrupa. {@code artists}, {@code albums} y {@code files} van en paralelo (una entrada por canción).
     * Por artista: las canciones sin artista van a un grupo «» al final. Por álbum: las que no tienen álbum, igual.
     */
    static List<Group> build(List<String> artists, List<String> albums, List<String> files, boolean byAlbum) {
        Map<String, Group> m = new LinkedHashMap<String, Group>();
        Map<String, java.util.Set<String>> albumsOfArtist = new java.util.HashMap<String, java.util.Set<String>>();
        for (int i = 0; i < files.size(); i++) {
            String ar = artistOf(artists.get(i), files.get(i));
            String al = albums.get(i) == null ? "" : albums.get(i).trim();
            String name = byAlbum ? al : ar;
            String key = SmartSearch.norm(name);
            Group g = m.get(key);
            if (g == null) {
                g = new Group(key, name);
                m.put(key, g);
            }
            g.tracks.add(i);
            if (byAlbum) {
                String ak = SmartSearch.norm(ar);
                if (g.firstArtist == null) {
                    g.firstArtist = ar;
                } else if (!SmartSearch.norm(g.firstArtist).equals(ak)) {
                    g.mixed = true;
                }
            } else if (al.length() > 0) {
                java.util.Set<String> s = albumsOfArtist.get(key);
                if (s == null) {
                    s = new java.util.HashSet<String>();
                    albumsOfArtist.put(key, s);
                }
                s.add(SmartSearch.norm(al));
            }
        }
        List<Group> out = new ArrayList<Group>(m.values());
        for (Group g : out) {
            if (byAlbum) {
                g.sub = g.mixed ? "*" : (g.firstArtist == null ? "" : g.firstArtist);
            } else {
                java.util.Set<String> s = albumsOfArtist.get(g.key);
                g.sub = String.valueOf(s == null ? 0 : s.size());
            }
        }
        Collections.sort(out, new Comparator<Group>() {
            @Override
            public int compare(Group a, Group b) {
                boolean ea = a.key.length() == 0;
                boolean eb = b.key.length() == 0;
                if (ea != eb) {
                    return ea ? 1 : -1;
                }
                return a.key.compareTo(b.key);
            }
        });
        return out;
    }
}
