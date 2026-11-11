package dev.scratchwheel;

import java.util.List;

/** Escritura de listas en formato M3U8 extendido (UTF-8). Sin dependencias de Android. */
final class M3u {
    private M3u() {
    }

    static final class Entry {
        final String path;
        final String artist;
        final String title;
        final int seconds;

        Entry(String path, String artist, String title, int seconds) {
            this.path = path;
            this.artist = artist == null ? "" : artist;
            this.title = title == null ? "" : title;
            this.seconds = seconds;
        }
    }

    static String write(String listName, List<Entry> entries) {
        StringBuilder sb = new StringBuilder("#EXTM3U\n");
        sb.append("#PLAYLIST:").append(oneLine(listName)).append('\n');
        for (Entry e : entries) {
            String label = e.artist.length() > 0 ? e.artist + " - " + e.title : e.title;
            sb.append("#EXTINF:").append(e.seconds > 0 ? e.seconds : -1).append(',').append(oneLine(label))
                    .append('\n');
            sb.append(oneLine(e.path)).append('\n');
        }
        return sb.toString();
    }

    private static String oneLine(String s) {
        return s.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
