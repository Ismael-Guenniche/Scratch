package dev.scratchwheel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/** Estadísticas de escucha (lógica pura, sin Android): canciones, artistas, álbumes y resumen tipo "Wrapped". */
public final class Stats {
    /** Una sesión de escucha de una canción. */
    public static final class Entry {
        public final long ts;
        public final String uri;
        public final int secs;
        /** Esta sesión incluye el momento en que la canción contó como reproducción. */
        public final boolean played;

        public Entry(long ts, String uri, int secs, boolean played) {
            this.ts = ts;
            this.uri = uri;
            this.secs = secs;
            this.played = played;
        }
    }

    /** Etiquetas de una canción; devuelve null si ya no está en la biblioteca. */
    public interface Info {
        String[] tags(String uri); // {título, artista, álbum}
    }

    public static final class Row {
        public String name = "";
        public String sub = "";
        public long secs;
        public int plays;
    }

    public static final class Summary {
        public long secs;
        public int plays;
        public int tracks;
        public int artists;
        public int albums;
        public int activeDays;
        public int bestStreak;
        public int favHour = -1;
        public int favWeekday = -1; // 0 = lunes
        public long[] byHour = new long[24];
        public long[] byWeekday = new long[7];
        public int discoveries;
        public Row topTrack;
        public Row topArtist;
        public Row topAlbum;
    }

    public static final int TRACK = 0;
    public static final int ARTIST = 1;
    public static final int ALBUM = 2;
    /** Una canción cuenta como reproducción tras estos segundos (o la mitad si es más corta). */
    public static final int PLAY_SECS = 30;

    private final List<Entry> all;
    private final Info info;
    private final TimeZone tz;

    public Stats(List<Entry> entries, Info info, TimeZone tz) {
        this.all = entries;
        this.info = info;
        this.tz = tz;
    }

    private List<Entry> within(long from, long to) {
        ArrayList<Entry> out = new ArrayList<Entry>();
        for (Entry e : all) {
            if (e.ts >= from && e.ts < to) {
                out.add(e);
            }
        }
        return out;
    }

    private String[] tagsOf(String uri) {
        String[] t = info == null ? null : info.tags(uri);
        if (t == null) {
            String n = uri;
            int s = Math.max(n.lastIndexOf('/'), n.lastIndexOf("%2F"));
            if (s >= 0) {
                n = n.substring(s + (n.startsWith("%2F", s) ? 3 : 1));
            }
            return new String[]{n, "", ""};
        }
        return t;
    }

    private static String key(int kind, String[] t, String uri) {
        if (kind == ARTIST) {
            return t[1].length() == 0 ? "" : t[1];
        } else if (kind == ALBUM) {
            return t[2].length() == 0 ? "" : t[2] + "\u0001" + t[1];
        }
        return uri;
    }

    /** Ranking por tiempo escuchado (desempate por reproducciones). */
    public List<Row> top(int kind, long from, long to, int limit) {
        Map<String, Row> map = new HashMap<String, Row>();
        for (Entry e : within(from, to)) {
            String[] t = tagsOf(e.uri);
            String k = key(kind, t, e.uri);
            if (k.length() == 0) {
                continue;
            }
            Row r = map.get(k);
            if (r == null) {
                r = new Row();
                if (kind == TRACK) {
                    r.name = t[0];
                    r.sub = t[1];
                } else if (kind == ARTIST) {
                    r.name = t[1];
                } else {
                    r.name = t[2];
                    r.sub = t[1];
                }
                map.put(k, r);
            }
            r.secs += e.secs;
            if (e.played) {
                r.plays++;
            }
        }
        ArrayList<Row> rows = new ArrayList<Row>(map.values());
        Collections.sort(rows, new Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                if (a.secs != b.secs) {
                    return a.secs < b.secs ? 1 : -1;
                }
                return b.plays - a.plays;
            }
        });
        return limit > 0 && rows.size() > limit ? new ArrayList<Row>(rows.subList(0, limit)) : rows;
    }

    public Summary summary(long from, long to) {
        Summary s = new Summary();
        List<Entry> in = within(from, to);
        Set<String> tr = new HashSet<String>();
        Set<String> ar = new HashSet<String>();
        Set<String> al = new HashSet<String>();
        Set<Long> days = new HashSet<Long>();
        Set<String> before = new HashSet<String>();
        for (Entry e : all) {
            if (e.ts < from) {
                before.add(e.uri);
            }
        }
        Set<String> seenNew = new HashSet<String>();
        Calendar c = Calendar.getInstance(tz);
        for (Entry e : in) {
            s.secs += e.secs;
            if (e.played) {
                s.plays++;
            }
            tr.add(e.uri);
            String[] t = tagsOf(e.uri);
            if (t[1].length() > 0) {
                ar.add(t[1]);
            }
            if (t[2].length() > 0) {
                al.add(t[2] + "\u0001" + t[1]);
            }
            c.setTimeInMillis(e.ts);
            s.byHour[c.get(Calendar.HOUR_OF_DAY)] += e.secs;
            int wd = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            s.byWeekday[wd] += e.secs;
            days.add(dayNumber(e.ts));
            if (!before.contains(e.uri) && seenNew.add(e.uri)) {
                s.discoveries++;
            }
        }
        s.tracks = tr.size();
        s.artists = ar.size();
        s.albums = al.size();
        s.activeDays = days.size();
        s.bestStreak = streak(days);
        s.favHour = argmax(s.byHour);
        s.favWeekday = argmax(s.byWeekday);
        List<Row> a = top(TRACK, from, to, 1);
        s.topTrack = a.isEmpty() ? null : a.get(0);
        a = top(ARTIST, from, to, 1);
        s.topArtist = a.isEmpty() ? null : a.get(0);
        a = top(ALBUM, from, to, 1);
        s.topAlbum = a.isEmpty() ? null : a.get(0);
        return s;
    }

    /** Estadísticas de una sola canción en todo el historial. */
    public Row track(String uri) {
        Row r = new Row();
        String[] t = tagsOf(uri);
        r.name = t[0];
        r.sub = t[1];
        for (Entry e : all) {
            if (e.uri.equals(uri)) {
                r.secs += e.secs;
                if (e.played) {
                    r.plays++;
                }
            }
        }
        return r;
    }

    /** Posición (1 = la más escuchada) de una canción por tiempo, o 0 si no se ha escuchado. */
    public int rankOf(String uri) {
        List<Row> rows = top(TRACK, 0, Long.MAX_VALUE, 0);
        String name = tagsOf(uri)[0];
        // las filas no guardan el uri; se vuelve a calcular por tiempo
        long mine = track(uri).secs;
        if (mine == 0) {
            return 0;
        }
        int better = 0;
        Map<String, Long> per = new HashMap<String, Long>();
        for (Entry e : all) {
            Long v = per.get(e.uri);
            per.put(e.uri, (v == null ? 0 : v) + e.secs);
        }
        for (long v : per.values()) {
            if (v > mine) {
                better++;
            }
        }
        return better + 1;
    }

    public long firstListen(String uri) {
        long f = 0;
        for (Entry e : all) {
            if (e.uri.equals(uri) && (f == 0 || e.ts < f)) {
                f = e.ts;
            }
        }
        return f;
    }

    private long dayNumber(long ts) {
        return (ts + tz.getOffset(ts)) / 86400000L;
    }

    private static int streak(Set<Long> days) {
        ArrayList<Long> d = new ArrayList<Long>(days);
        Collections.sort(d);
        int best = 0;
        int cur = 0;
        long prev = Long.MIN_VALUE;
        for (long x : d) {
            cur = x == prev + 1 ? cur + 1 : 1;
            best = Math.max(best, cur);
            prev = x;
        }
        return best;
    }

    private static int argmax(long[] a) {
        int b = -1;
        long m = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] > m) {
                m = a[i];
                b = i;
            }
        }
        return b;
    }

    /** "3 H 20 M", "45 M" o "30 S". */
    public static String duration(long secs) {
        if (secs < 60) {
            return secs + " S";
        }
        long m = secs / 60;
        if (m < 60) {
            return m + " M";
        }
        return (m / 60) + " H " + (m % 60) + " M";
    }
}
