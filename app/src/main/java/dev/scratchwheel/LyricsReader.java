package dev.scratchwheel;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Lee la letra incrustada en un archivo de audio: ID3v2 (USLT y SYLT, MP3 y otros), FLAC / Ogg (Vorbis comments:
 * LYRICS, UNSYNCEDLYRICS, SYNCEDLYRICS) y MP4/M4A (átomo ©lyr). Sin dependencias de Android para poder probarlo.
 * También interpreta el formato LRC ([mm:ss.xx]texto).
 */
final class LyricsReader {
    private LyricsReader() {
    }

    /** Acceso aleatorio de solo lectura. */
    interface Src {
        long length();

        /** Lee hasta len bytes desde pos; devuelve los leídos o -1 al final. */
        int read(long pos, byte[] b, int off, int len) throws IOException;
    }

    static final class Line {
        final long ms;
        final String text;

        Line(long ms, String text) {
            this.ms = ms;
            this.text = text;
        }
    }

    static final class Result {
        /** Letra sin tiempos (puede ser null si solo hay sincronizada). */
        String plain;
        /** Letra con tiempos, ordenada (vacía si no hay). */
        List<Line> synced = new ArrayList<Line>();

        boolean isEmpty() {
            return (plain == null || plain.trim().length() == 0) && synced.isEmpty();
        }
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final Charset UTF16BE = Charset.forName("UTF-16BE");
    private static final Charset UTF16LE = Charset.forName("UTF-16LE");
    private static final Charset UTF16 = Charset.forName("UTF-16");
    private static final long MAX_TAG = 8L << 20;

    /** Memoria como fuente (tests y archivos ya cargados). */
    static Src of(final byte[] data) {
        return new Src() {
            @Override
            public long length() {
                return data.length;
            }

            @Override
            public int read(long pos, byte[] b, int off, int len) {
                if (pos >= data.length) {
                    return -1;
                }
                int n = (int) Math.min(len, data.length - pos);
                System.arraycopy(data, (int) pos, b, off, n);
                return n;
            }
        };
    }

    static byte[] readBytes(Src s, long pos, int len) throws IOException {
        if (len < 0 || pos < 0 || pos + len > s.length()) {
            throw new IOException("fuera de rango");
        }
        byte[] b = new byte[len];
        int got = 0;
        while (got < len) {
            int n = s.read(pos + got, b, got, len - got);
            if (n <= 0) {
                throw new IOException("fin inesperado");
            }
            got += n;
        }
        return b;
    }

    /** Busca la letra en el archivo. Nunca lanza: si algo falla devuelve lo que haya o vacío. */
    static Result read(Src s) {
        Result r = new Result();
        try {
            long len = s.length();
            if (len < 12) {
                return r;
            }
            byte[] h = readBytes(s, 0, 10);
            long pos = 0;
            if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
                long size = syncsafe(h, 6) + 10;
                readId3(s, 0, Math.min(size, len), r);
                pos = size;
                if (pos + 4 > len) {
                    return finish(r);
                }
                h = readBytes(s, pos, 4);
            }
            if (len >= pos + 4) {
                byte[] m = readBytes(s, pos, 4);
                if (m[0] == 'f' && m[1] == 'L' && m[2] == 'a' && m[3] == 'C') {
                    readFlac(s, pos + 4, r);
                } else if (m[0] == 'O' && m[1] == 'g' && m[2] == 'g' && m[3] == 'S') {
                    readOgg(s, r);
                } else if (len >= 12) {
                    byte[] f = readBytes(s, 4, 4);
                    if (f[0] == 'f' && f[1] == 't' && f[2] == 'y' && f[3] == 'p') {
                        readMp4(s, 0, len, 0, r);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // se devuelve lo leído hasta ahora
        }
        return finish(r);
    }

    private static Result finish(Result r) {
        if (r.synced.isEmpty() && r.plain != null && looksLikeLrc(r.plain)) {
            r.synced = parseLrc(r.plain);
            if (!r.synced.isEmpty()) {
                r.plain = linesToText(r.synced);
            }
        }
        return r;
    }

    static String linesToText(List<Line> l) {
        StringBuilder sb = new StringBuilder();
        for (Line x : l) {
            sb.append(x.text).append('\n');
        }
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ ID3v2

    private static int syncsafe(byte[] b, int o) {
        return (b[o] & 0x7F) << 21 | (b[o + 1] & 0x7F) << 14 | (b[o + 2] & 0x7F) << 7 | (b[o + 3] & 0x7F);
    }

    private static int be32(byte[] b, int o) {
        return (b[o] & 0xFF) << 24 | (b[o + 1] & 0xFF) << 16 | (b[o + 2] & 0xFF) << 8 | (b[o + 3] & 0xFF);
    }

    private static void readId3(Src s, long start, long end, Result r) throws IOException {
        byte[] h = readBytes(s, start, 10);
        int ver = h[3] & 0xFF;
        int flags = h[5] & 0xFF;
        long total = end - start - 10;
        if (total <= 0 || total > MAX_TAG * 4 || ver < 2 || ver > 4) {
            return;
        }
        byte[] tag = readBytes(s, start + 10, (int) total);
        if ((flags & 0x80) != 0 && ver < 4) {
            tag = deUnsync(tag);
        }
        int p = 0;
        if ((flags & 0x40) != 0 && ver >= 3 && tag.length >= 4) {
            int ext = ver == 4 ? syncsafe(tag, 0) : be32(tag, 0) + 4;
            p = Math.max(0, Math.min(ext, tag.length));
        }
        int idLen = ver == 2 ? 3 : 4;
        int hdr = ver == 2 ? 6 : 10;
        while (p + hdr <= tag.length) {
            if (tag[p] == 0) {
                break;
            }
            String id = new String(tag, p, idLen, LATIN1);
            int size;
            if (ver == 2) {
                size = (tag[p + 3] & 0xFF) << 16 | (tag[p + 4] & 0xFF) << 8 | (tag[p + 5] & 0xFF);
            } else if (ver == 3) {
                size = be32(tag, p + 4);
            } else {
                size = syncsafe(tag, p + 4);
            }
            int fl = ver == 2 ? 0 : (tag[p + 8] & 0xFF) << 8 | (tag[p + 9] & 0xFF);
            int body = p + hdr;
            if (size < 0 || body + size > tag.length) {
                break;
            }
            byte[] data = new byte[size];
            System.arraycopy(tag, body, data, 0, size);
            if (ver == 4 && (fl & 0x0002) != 0) {
                data = deUnsync(data);
            }
            boolean usable = ver == 2 || (ver == 3 ? (fl & 0x00C0) == 0 : (fl & 0x000C) == 0); // sin compresión ni cifrado
            if (usable) {
                if (id.equals("USLT") || id.equals("ULT")) {
                    String t = parseUslt(data);
                    if (t != null && (r.plain == null || t.length() > r.plain.length())) {
                        r.plain = t;
                    }
                } else if (id.equals("SYLT") || id.equals("SLT")) {
                    List<Line> l = parseSylt(data);
                    if (l.size() > r.synced.size()) {
                        r.synced = l;
                    }
                }
            }
            p = body + size;
        }
    }

    private static byte[] deUnsync(byte[] b) {
        byte[] o = new byte[b.length];
        int n = 0;
        for (int i = 0; i < b.length; i++) {
            o[n++] = b[i];
            if (b[i] == (byte) 0xFF && i + 1 < b.length && b[i + 1] == 0) {
                i++;
            }
        }
        byte[] r = new byte[n];
        System.arraycopy(o, 0, r, 0, n);
        return r;
    }

    private static Charset charset(int enc) {
        switch (enc) {
            case 1:
                return UTF16;
            case 2:
                return UTF16BE;
            case 3:
                return UTF8;
            default:
                return LATIN1;
        }
    }

    /** Posición del terminador de texto de la codificación (2 bytes nulos en UTF-16), o -1. */
    private static int findTerm(byte[] d, int from, int enc) {
        if (enc == 1 || enc == 2) {
            for (int i = from; i + 1 < d.length; i += 2) {
                if (d[i] == 0 && d[i + 1] == 0) {
                    return i;
                }
            }
            return -1;
        }
        for (int i = from; i < d.length; i++) {
            if (d[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int termLen(int enc) {
        return enc == 1 || enc == 2 ? 2 : 1;
    }

    private static String decode(byte[] d, int from, int to, int enc) {
        if (to <= from) {
            return "";
        }
        Charset cs = charset(enc);
        if (enc == 1 && to - from >= 2) {
            int b0 = d[from] & 0xFF;
            int b1 = d[from + 1] & 0xFF;
            if (b0 == 0xFF && b1 == 0xFE) {
                return new String(d, from + 2, to - from - 2, UTF16LE);
            } else if (b0 == 0xFE && b1 == 0xFF) {
                return new String(d, from + 2, to - from - 2, UTF16BE);
            }
            return new String(d, from, to - from, UTF16LE);
        }
        return new String(d, from, to - from, cs);
    }

    private static String parseUslt(byte[] d) {
        if (d.length < 5) {
            return null;
        }
        int enc = d[0] & 0xFF;
        int term = findTerm(d, 4, enc);
        if (term < 0) {
            return null;
        }
        String t = decode(d, term + termLen(enc), d.length, enc);
        t = t.replace("\r\n", "\n").replace('\r', '\n');
        return t.trim().length() == 0 ? null : t.trim();
    }

    private static List<Line> parseSylt(byte[] d) {
        List<Line> out = new ArrayList<Line>();
        if (d.length < 8) {
            return out;
        }
        int enc = d[0] & 0xFF;
        int tsFormat = d[4] & 0xFF; // 1 = fotogramas MPEG, 2 = milisegundos
        int term = findTerm(d, 6, enc);
        if (term < 0 || tsFormat != 2) {
            return out;
        }
        int p = term + termLen(enc);
        while (p < d.length) {
            int e = findTerm(d, p, enc);
            if (e < 0 || e + termLen(enc) + 4 > d.length) {
                break;
            }
            String text = decode(d, p, e, enc);
            int q = e + termLen(enc);
            long ms = ((long) (d[q] & 0xFF) << 24) | (d[q + 1] & 0xFF) << 16 | (d[q + 2] & 0xFF) << 8
                    | (d[q + 3] & 0xFF);
            p = q + 4;
            String clean = text.replace("\n", "").replace("\r", "");
            if (clean.length() > 0 || out.isEmpty()) {
                out.add(new Line(ms, clean));
            }
        }
        Collections.sort(out, BY_TIME);
        return out;
    }

    // ------------------------------------------------------------------ FLAC / Ogg

    private static void readFlac(Src s, long pos, Result r) throws IOException {
        long len = s.length();
        while (pos + 4 <= len) {
            byte[] h = readBytes(s, pos, 4);
            boolean last = (h[0] & 0x80) != 0;
            int type = h[0] & 0x7F;
            int size = (h[1] & 0xFF) << 16 | (h[2] & 0xFF) << 8 | (h[3] & 0xFF);
            pos += 4;
            if (pos + size > len) {
                return;
            }
            if (type == 4 && size < MAX_TAG) {
                readVorbisComment(readBytes(s, pos, size), 0, r);
            }
            pos += size;
            if (last) {
                return;
            }
        }
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    private static void readVorbisComment(byte[] d, int p, Result r) {
        if (p + 4 > d.length) {
            return;
        }
        int vend = le32(d, p);
        p += 4;
        if (vend < 0 || p + vend + 4 > d.length) {
            return;
        }
        p += vend;
        int n = le32(d, p);
        p += 4;
        for (int i = 0; i < n && p + 4 <= d.length; i++) {
            int l = le32(d, p);
            p += 4;
            if (l < 0 || p + l > d.length) {
                return;
            }
            String kv = new String(d, p, l, UTF8);
            p += l;
            int eq = kv.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = kv.substring(0, eq).toUpperCase(java.util.Locale.ROOT);
            String v = kv.substring(eq + 1).replace("\r\n", "\n").replace('\r', '\n');
            if (v.trim().length() == 0) {
                continue;
            }
            if (k.equals("LYRICS") || k.equals("UNSYNCEDLYRICS") || k.equals("UNSYNCED LYRICS")
                    || k.equals("SYNCEDLYRICS") || k.equals("LYRICS-XXX") || k.equals("LYRICS_SYNCED")) {
                if (r.plain == null || v.length() > r.plain.length()) {
                    r.plain = v.trim();
                }
            }
        }
    }

    /** Ogg Vorbis/Opus: el paquete de comentarios va al principio; se busca la clave y la longitud que la precede. */
    private static void readOgg(Src s, Result r) throws IOException {
        int n = (int) Math.min(s.length(), 1L << 20);
        byte[] d = readBytes(s, 0, n);
        String[] keys = {"LYRICS=", "UNSYNCEDLYRICS=", "SYNCEDLYRICS="};
        for (String key : keys) {
            byte[] kb = key.getBytes(LATIN1);
            for (int i = 4; i + kb.length < d.length; i++) {
                boolean ok = true;
                for (int j = 0; j < kb.length && ok; j++) {
                    ok = Character.toUpperCase((char) (d[i + j] & 0xFF)) == kb[j];
                }
                if (!ok) {
                    continue;
                }
                int l = le32(d, i - 4);
                if (l > kb.length && i + l <= d.length) {
                    String v = new String(d, i + kb.length, l - kb.length, UTF8).replace("\r\n", "\n")
                            .replace('\r', '\n').trim();
                    if (v.length() > 0 && (r.plain == null || v.length() > r.plain.length())) {
                        r.plain = v;
                    }
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ MP4

    private static void readMp4(Src s, long pos, long end, int depth, Result r) throws IOException {
        while (pos + 8 <= end && depth < 8) {
            byte[] h = readBytes(s, pos, 8);
            long size = be32(h, 0) & 0xFFFFFFFFL;
            int hdr = 8;
            if (size == 1 && pos + 16 <= end) {
                byte[] ex = readBytes(s, pos + 8, 8);
                size = (be32(ex, 0) & 0xFFFFFFFFL) << 32 | (be32(ex, 4) & 0xFFFFFFFFL);
                hdr = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < hdr || pos + size > end) {
                return;
            }
            String type = new String(h, 4, 4, LATIN1);
            long body = pos + hdr;
            long bodyEnd = pos + size;
            if (type.equals("moov") || type.equals("udta") || type.equals("ilst")) {
                readMp4(s, body, bodyEnd, depth + 1, r);
            } else if (type.equals("meta")) {
                readMp4(s, body + 4, bodyEnd, depth + 1, r);
            } else if (type.equals("©lyr")) {
                readMp4(s, body, bodyEnd, depth + 1, r);
            } else if (type.equals("data") && depth >= 2 && bodyEnd - body > 8 && bodyEnd - body < MAX_TAG) {
                byte[] d = readBytes(s, body + 8, (int) (bodyEnd - body - 8));
                String t = new String(d, UTF8).replace("\r\n", "\n").replace('\r', '\n').trim();
                if (t.length() > 0 && (r.plain == null || t.length() > r.plain.length())) {
                    r.plain = t;
                }
            }
            pos = bodyEnd;
        }
    }

    // ------------------------------------------------------------------ LRC

    private static final Comparator<Line> BY_TIME = new Comparator<Line>() {
        @Override
        public int compare(Line a, Line b) {
            return a.ms < b.ms ? -1 : a.ms > b.ms ? 1 : 0;
        }
    };

    static synchronized boolean looksLikeLrc(String t) {
        int c = 0;
        int i = 0;
        while (c < 3 && (i = t.indexOf('[', i)) >= 0) {
            if (timestampAt(t, i) > 0) {
                c++;
            }
            i++;
        }
        return c >= 2;
    }

    /** Si en pos hay "[mm:ss.xx]" devuelve su longitud (con corchetes) y deja el valor en tsOut[0]; si no, 0. */
    private static long[] tsOut = new long[1];

    private static int timestampAt(String t, int pos) {
        int close = t.indexOf(']', pos);
        if (close < 0 || close - pos > 11 || close - pos < 5) {
            return 0;
        }
        String in = t.substring(pos + 1, close);
        int colon = in.indexOf(':');
        if (colon <= 0) {
            return 0;
        }
        try {
            int mm = Integer.parseInt(in.substring(0, colon));
            String rest = in.substring(colon + 1);
            int dot = Math.max(rest.indexOf('.'), rest.indexOf(':'));
            int ss;
            long frac = 0;
            if (dot >= 0) {
                ss = Integer.parseInt(rest.substring(0, dot));
                String f = rest.substring(dot + 1);
                if (f.length() == 0 || f.length() > 3) {
                    return 0;
                }
                frac = Integer.parseInt(f);
                frac = f.length() == 1 ? frac * 100 : f.length() == 2 ? frac * 10 : frac;
            } else {
                ss = Integer.parseInt(rest);
            }
            if (mm < 0 || ss < 0 || ss > 99) {
                return 0;
            }
            tsOut[0] = (mm * 60L + ss) * 1000L + frac;
            return close - pos + 1;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Interpreta un texto LRC. Varios tiempos por línea, [offset:+ms] y etiquetas por palabra <mm:ss.xx>. */
    static synchronized List<Line> parseLrc(String text) {
        List<Line> out = new ArrayList<Line>();
        long offset = 0;
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (String raw : lines) {
            String l = raw.trim();
            if (l.startsWith("[offset:")) {
                try {
                    offset = Long.parseLong(l.substring(8, l.indexOf(']')).trim().replace("+", ""));
                } catch (RuntimeException ignored) {
                }
                continue;
            }
            List<Long> times = new ArrayList<Long>();
            int p = 0;
            int n;
            while (p < l.length() && l.charAt(p) == '[' && (n = timestampAt(l, p)) > 0) {
                times.add(tsOut[0]);
                p += n;
            }
            if (times.isEmpty()) {
                continue;
            }
            String body = l.substring(p).replaceAll("<\\d+:\\d+(?:[.:]\\d+)?>", "").trim();
            for (long t : times) {
                out.add(new Line(Math.max(0, t - offset), body));
            }
        }
        Collections.sort(out, BY_TIME);
        return out;
    }

    /** Índice de la línea activa en ms (la última cuyo tiempo ya pasó), o -1 antes de la primera. */
    static int lineAt(List<Line> lines, long ms) {
        int lo = 0;
        int hi = lines.size() - 1;
        int ans = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).ms <= ms) {
                ans = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return ans;
    }
}
