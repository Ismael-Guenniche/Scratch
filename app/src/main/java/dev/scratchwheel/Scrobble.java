package dev.scratchwheel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrobbling a ListenBrainz (opcional; Ajustes → SCROBBLING). Cada reproducción que cuenta en las estadísticas se
 * guarda en una cola ({@code scrobble.txt}) y se envía en lotes cuando hay conexión; si falla, se reintenta luego.
 * Solo se envía título, artista, álbum, duración y la hora. Sin Android: testable en la JVM.
 */
final class Scrobble {
    static final String API = "https://api.listenbrainz.org/1/submit-listens";
    private static final int BATCH = 50;
    private static final int MAX_QUEUE = 5000;

    private Scrobble() {
    }

    /** Una escucha pendiente. */
    static final class Listen {
        final long at;
        final String artist;
        final String title;
        final String album;
        final long durMs;

        Listen(long at, String artist, String title, String album, long durMs) {
            this.at = at;
            this.artist = artist == null ? "" : artist;
            this.title = title == null ? "" : title;
            this.album = album == null ? "" : album;
            this.durMs = durMs;
        }

        String line() {
            return at + "\t" + f(artist) + "\t" + f(title) + "\t" + f(album) + "\t" + durMs;
        }

        static Listen parse(String l) {
            String[] p = l.split("\t", -1);
            if (p.length < 5) {
                return null;
            }
            try {
                return new Listen(Long.parseLong(p[0]), p[1], p[2], p[3], Long.parseLong(p[4]));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static String f(String s) {
            return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        }
    }

    /** Cuerpo JSON de un envío («import» si son varias, «single» si es una). */
    static String json(List<Listen> ls) {
        StringBuilder b = new StringBuilder();
        b.append("{\"listen_type\":\"").append(ls.size() == 1 ? "single" : "import").append("\",\"payload\":[");
        for (int i = 0; i < ls.size(); i++) {
            Listen l = ls.get(i);
            if (i > 0) {
                b.append(',');
            }
            b.append("{\"listened_at\":").append(l.at / 1000).append(",\"track_metadata\":{");
            b.append("\"artist_name\":").append(PlaylistJson.q(l.artist));
            b.append(",\"track_name\":").append(PlaylistJson.q(l.title));
            if (l.album.length() > 0) {
                b.append(",\"release_name\":").append(PlaylistJson.q(l.album));
            }
            b.append(",\"additional_info\":{\"media_player\":\"Scratch\",\"submission_client\":\"Scratch\"");
            if (l.durMs > 0) {
                b.append(",\"duration_ms\":").append(l.durMs);
            }
            b.append("}}}");
        }
        return b.append("]}").toString();
    }

    /** Añade una escucha a la cola (las que no tienen artista o título no se envían: ListenBrainz las rechaza). */
    static synchronized void enqueue(File queue, Listen l) {
        if (l.artist.trim().length() == 0 || l.title.trim().length() == 0) {
            return;
        }
        List<String> lines = read(queue);
        lines.add(l.line());
        while (lines.size() > MAX_QUEUE) {
            lines.remove(0);
        }
        write(queue, lines);
    }

    static synchronized int pending(File queue) {
        return read(queue).size();
    }

    /**
     * Envía lo pendiente. Devuelve cuántas se enviaron; lanza IOException con el código HTTP si el servidor dice que
     * no (401 = token mal).
     */
    static int flush(File queue, String token) throws IOException {
        int sent = 0;
        while (true) {
            List<String> lines;
            synchronized (Scrobble.class) {
                lines = read(queue);
            }
            if (lines.isEmpty()) {
                return sent;
            }
            int n = Math.min(BATCH, lines.size());
            List<Listen> batch = new ArrayList<Listen>();
            for (int i = 0; i < n; i++) {
                Listen l = Listen.parse(lines.get(i));
                if (l != null) {
                    batch.add(l);
                }
            }
            if (!batch.isEmpty()) {
                post(json(batch), token);
            }
            synchronized (Scrobble.class) {
                List<String> now = read(queue);
                // quita las enviadas (las primeras n; lo nuevo se añade al final)
                for (int i = 0; i < n && !now.isEmpty(); i++) {
                    now.remove(0);
                }
                write(queue, now);
            }
            sent += batch.size();
        }
    }

    private static void post(String body, String token) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        try {
            c.setRequestMethod("POST");
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Token " + token.trim());
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            OutputStream o = c.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
            int code = c.getResponseCode();
            if (code / 100 != 2) {
                throw new IOException("HTTP " + code);
            }
            InputStream in = c.getInputStream();
            in.close();
        } finally {
            c.disconnect();
        }
    }

    private static List<String> read(File f) {
        List<String> out = new ArrayList<String>();
        if (f == null || !f.exists()) {
            return out;
        }
        try {
            String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            for (String l : s.split("\n")) {
                if (l.length() > 0) {
                    out.add(l);
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    private static void write(File f, List<String> lines) {
        StringBuilder b = new StringBuilder();
        for (String l : lines) {
            b.append(l).append('\n');
        }
        try {
            File tmp = new File(f.getPath() + ".tmp");
            OutputStream o = new FileOutputStream(tmp);
            try {
                o.write(b.toString().getBytes("UTF-8"));
            } finally {
                o.close();
            }
            if (!tmp.renameTo(f)) {
                f.delete();
                tmp.renameTo(f);
            }
        } catch (IOException ignored) {
        }
    }
}
