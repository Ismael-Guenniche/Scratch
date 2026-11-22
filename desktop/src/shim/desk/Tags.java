package desk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Etiquetas, duración y carátula de un archivo de audio, sin librerías: ID3v2/ID3v1 (MP3), FLAC,
 * Ogg Vorbis/Opus, MP4 (M4A) y WAV. Hace en el PC lo que MediaMetadataRetriever hace en Android.
 */
public final class Tags {
    public String title;
    public String artist;
    public String albumArtist;
    public String album;
    public long durationMs;
    public byte[] picture;
    public int sampleRate;
    public int channels;

    private static final int MAX_TEXT = 1 << 16;
    private static final int MAX_PIC = 24 << 20;
    private int picRank = -1;

    private Tags() {
    }

    /** Lee las etiquetas; la carátula solo si se pide (es lo que más pesa). Nunca lanza. */
    public static Tags read(File f, boolean wantPicture) {
        Tags t = new Tags();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            long len = raf.length();
            byte[] head = new byte[12];
            int n = readAt(raf, 0, head, 0, 12);
            if (n < 12) {
                return t;
            }
            long audioStart = 0;
            if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                audioStart = t.id3v2(raf, wantPicture);
                readAt(raf, audioStart, head, 0, 12);
            }
            if (is(head, 0, "fLaC")) {
                t.flac(raf, audioStart, len, wantPicture);
            } else if (is(head, 0, "OggS")) {
                t.ogg(raf, audioStart, len, wantPicture);
            } else if (is(head, 4, "ftyp")) {
                t.mp4(raf, len, wantPicture);
            } else if (is(head, 0, "RIFF") && is(head, 8, "WAVE")) {
                t.wav(raf, len);
            } else {
                String name = f.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".mp3") || audioStart > 0 || (head[0] & 0xFF) == 0xFF) {
                    t.mpegDuration(raf, audioStart, len);
                    if (t.title == null && t.artist == null) {
                        t.id3v1(raf, len);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            Rt.log(e);
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (IOException ignored) {
                }
            }
        }
        return t;
    }

    // ------------------------------------------------------------ utilidades

    private static int readAt(RandomAccessFile raf, long pos, byte[] b, int off, int n) throws IOException {
        if (pos < 0 || pos >= raf.length()) {
            return -1;
        }
        raf.seek(pos);
        int got = 0;
        while (got < n) {
            int k = raf.read(b, off + got, n - got);
            if (k < 0) {
                break;
            }
            got += k;
        }
        return got;
    }

    private static byte[] bytesAt(RandomAccessFile raf, long pos, int n) throws IOException {
        byte[] b = new byte[n];
        int got = readAt(raf, pos, b, 0, n);
        if (got < n) {
            throw new IOException("corto");
        }
        return b;
    }

    private static boolean is(byte[] b, int off, String s) {
        if (off + s.length() > b.length) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if ((b[off + i] & 0xFF) != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static int be24(byte[] b, int o) {
        return ((b[o] & 0xFF) << 16) | ((b[o + 1] & 0xFF) << 8) | (b[o + 2] & 0xFF);
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static int syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7F) << 21) | ((b[o + 1] & 0x7F) << 14) | ((b[o + 2] & 0x7F) << 7) | (b[o + 3] & 0x7F);
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        int z = s.indexOf('\0');
        if (z >= 0) {
            String rest = s.substring(z + 1).replace('\0', ' ').trim();
            s = s.substring(0, z) + (rest.length() > 0 ? ", " + rest : "");
        }
        s = s.trim();
        return s.length() == 0 ? null : s;
    }

    private void picture(byte[] data, int rank) {
        if (data != null && data.length > 16 && rank > picRank) {
            picture = data;
            picRank = rank;
        }
    }

    // ------------------------------------------------------------ ID3

    private static final Charset LATIN = StandardCharsets.ISO_8859_1;

    private static String id3Text(byte[] b, int off, int len) {
        if (len <= 1) {
            return null;
        }
        int enc = b[off] & 0xFF;
        Charset cs = enc == 1 ? StandardCharsets.UTF_16 : enc == 2 ? StandardCharsets.UTF_16BE
                : enc == 3 ? StandardCharsets.UTF_8 : LATIN;
        return clean(new String(b, off + 1, len - 1, cs));
    }

    /** Fin de una cadena terminada en nulo según la codificación; devuelve el índice tras el terminador. */
    private static int skipString(byte[] b, int off, int end, int enc) {
        if (enc == 1 || enc == 2) {
            for (int i = off; i + 1 < end; i += 2) {
                if (b[i] == 0 && b[i + 1] == 0) {
                    return i + 2;
                }
            }
            return end;
        }
        for (int i = off; i < end; i++) {
            if (b[i] == 0) {
                return i + 1;
            }
        }
        return end;
    }

    private static byte[] deunsync(byte[] b, int off, int len) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(len);
        for (int i = off; i < off + len; i++) {
            o.write(b[i]);
            if ((b[i] & 0xFF) == 0xFF && i + 1 < off + len && b[i + 1] == 0) {
                i++;
            }
        }
        return o.toByteArray();
    }

    /** Lee la etiqueta ID3v2 del principio. Devuelve dónde empieza el audio. */
    private long id3v2(RandomAccessFile raf, boolean wantPicture) throws IOException {
        byte[] h = bytesAt(raf, 0, 10);
        int ver = h[3] & 0xFF;
        int flags = h[5] & 0xFF;
        int size = syncsafe(h, 6);
        long end = 10L + size;
        if ((flags & 0x10) != 0) {
            end += 10;
        }
        long audio = Math.min(end, raf.length());
        if (ver < 2 || ver > 4) {
            return audio;
        }
        boolean wholeUnsync = (flags & 0x80) != 0;
        byte[] mem = null;
        long pos = 10;
        long limit = Math.min(10L + size, raf.length());
        if (wholeUnsync && ver < 4) {
            if (size > MAX_PIC) {
                return audio;
            }
            mem = deunsync(bytesAt(raf, 10, (int) (limit - 10)), 0, (int) (limit - 10));
            pos = 0;
            limit = mem.length;
        }
        if ((flags & 0x40) != 0 && ver >= 3) {
            byte[] e = get(raf, mem, pos, 4);
            pos += ver == 4 ? syncsafe(e, 0) : 4L + be32(e, 0);
        }
        int hl = ver == 2 ? 6 : 10;
        while (pos + hl <= limit) {
            byte[] fh = get(raf, mem, pos, hl);
            if (fh[0] == 0) {
                break;
            }
            String id;
            int fsize;
            int fflags = 0;
            if (ver == 2) {
                id = new String(fh, 0, 3, LATIN);
                fsize = be24(fh, 3);
            } else {
                id = new String(fh, 0, 4, LATIN);
                fsize = ver == 4 ? syncsafe(fh, 4) : be32(fh, 4);
                fflags = fh[9] & 0xFF;
            }
            long body = pos + hl;
            if (fsize <= 0 || body + fsize > limit) {
                break;
            }
            pos = body + fsize;
            boolean text = id.equals("TIT2") || id.equals("TPE1") || id.equals("TPE2") || id.equals("TALB")
                    || id.equals("TT2") || id.equals("TP1") || id.equals("TP2") || id.equals("TAL")
                    || id.equals("TLEN") || id.equals("TLE");
            boolean pic = wantPicture && (id.equals("APIC") || id.equals("PIC"));
            if (!text && !pic) {
                continue;
            }
            if (fsize > (pic ? MAX_PIC : MAX_TEXT)) {
                continue;
            }
            byte[] b = get(raf, mem, body, fsize);
            int off = 0;
            int len = fsize;
            if (ver == 4) {
                if ((fflags & 0x0C) != 0) {
                    continue;
                }
                if ((fflags & 0x01) != 0 && len >= 4) {
                    off += 4;
                    len -= 4;
                }
                if ((fflags & 0x02) != 0 || wholeUnsync) {
                    b = deunsync(b, off, len);
                    off = 0;
                    len = b.length;
                }
            }
            if (text) {
                String v = id3Text(b, off, len);
                if (v == null) {
                    continue;
                }
                if (id.equals("TIT2") || id.equals("TT2")) {
                    title = v;
                } else if (id.equals("TPE1") || id.equals("TP1")) {
                    artist = v;
                } else if (id.equals("TPE2") || id.equals("TP2")) {
                    albumArtist = v;
                } else if (id.equals("TALB") || id.equals("TAL")) {
                    album = v;
                } else {
                    try {
                        durationMs = Long.parseLong(v.trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                int endB = off + len;
                int enc = b[off] & 0xFF;
                int p = off + 1;
                if (ver == 2) {
                    p += 3;
                } else {
                    p = skipString(b, p, endB, 0);
                }
                if (p >= endB) {
                    continue;
                }
                int type = b[p] & 0xFF;
                p = skipString(b, p + 1, endB, enc);
                if (p < endB) {
                    picture(java.util.Arrays.copyOfRange(b, p, endB), type == 3 ? 3 : type == 0 ? 2 : 1);
                }
            }
        }
        return audio;
    }

    private static byte[] get(RandomAccessFile raf, byte[] mem, long pos, int n) throws IOException {
        if (mem != null) {
            if (pos + n > mem.length) {
                throw new IOException("corto");
            }
            return java.util.Arrays.copyOfRange(mem, (int) pos, (int) pos + n);
        }
        return bytesAt(raf, pos, n);
    }

    private void id3v1(RandomAccessFile raf, long len) throws IOException {
        if (len < 128) {
            return;
        }
        byte[] b = bytesAt(raf, len - 128, 128);
        if (!is(b, 0, "TAG")) {
            return;
        }
        title = clean(new String(b, 3, 30, LATIN));
        artist = clean(new String(b, 33, 30, LATIN));
        album = clean(new String(b, 63, 30, LATIN));
    }

    private static final int[][] BITRATE = {
        {0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
        {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320},
        {0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
        {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
    };
    private static final int[] RATES = {44100, 48000, 32000};

    private void mpegDuration(RandomAccessFile raf, long start, long len) throws IOException {
        byte[] buf = new byte[8192];
        int n = readAt(raf, start, buf, 0, buf.length);
        if (n < 64) {
            return;
        }
        for (int i = 0; i + 4 < n; i++) {
            if ((buf[i] & 0xFF) != 0xFF || (buf[i + 1] & 0xE0) != 0xE0) {
                continue;
            }
            int verBits = (buf[i + 1] >> 3) & 3;
            int layerBits = (buf[i + 1] >> 1) & 3;
            int brIdx = (buf[i + 2] >> 4) & 15;
            int srIdx = (buf[i + 2] >> 2) & 3;
            if (verBits == 1 || layerBits == 0 || brIdx == 0 || brIdx == 15 || srIdx == 3) {
                continue;
            }
            boolean v1 = verBits == 3;
            int layer = 4 - layerBits;
            int sr = RATES[srIdx] >> (verBits == 3 ? 0 : verBits == 2 ? 1 : 2);
            int row = v1 ? layer - 1 : (layer == 1 ? 3 : 4);
            int kbps = BITRATE[row][brIdx];
            boolean mono = ((buf[i + 3] >> 6) & 3) == 3;
            int spf = layer == 1 ? 384 : (layer == 3 && !v1) ? 576 : 1152;
            sampleRate = sr;
            channels = mono ? 1 : 2;
            if (durationMs > 0) {
                return;
            }
            int side = v1 ? (mono ? 17 : 32) : (mono ? 9 : 17);
            int x = i + 4 + side;
            long frames = -1;
            if (x + 12 < n && (is(buf, x, "Xing") || is(buf, x, "Info"))) {
                if ((be32(buf, x + 4) & 1) != 0) {
                    frames = be32(buf, x + 8) & 0xFFFFFFFFL;
                }
            } else if (i + 36 + 18 < n && is(buf, i + 36, "VBRI")) {
                frames = be32(buf, i + 36 + 14) & 0xFFFFFFFFL;
            }
            if (frames > 0) {
                durationMs = frames * spf * 1000L / sr;
            } else if (kbps > 0) {
                durationMs = (len - start - i) * 8L / kbps;
            }
            return;
        }
    }

    // ------------------------------------------------------------ comentarios Vorbis (FLAC y Ogg)

    private void vorbisComments(byte[] b, int off, int end, boolean wantPicture) {
        if (off + 8 > end) {
            return;
        }
        int vendor = le32(b, off);
        int p = off + 4 + vendor;
        if (vendor < 0 || p + 4 > end) {
            return;
        }
        int count = le32(b, p);
        p += 4;
        for (int i = 0; i < count && p + 4 <= end; i++) {
            int l = le32(b, p);
            p += 4;
            if (l < 0 || p + l > end) {
                break;
            }
            int eq = -1;
            for (int k = p; k < p + Math.min(l, 64); k++) {
                if (b[k] == '=') {
                    eq = k;
                    break;
                }
            }
            if (eq > 0) {
                String key = new String(b, p, eq - p, LATIN).toUpperCase(Locale.ROOT);
                int vl = p + l - eq - 1;
                if (key.equals("METADATA_BLOCK_PICTURE")) {
                    if (wantPicture && vl < MAX_PIC * 2) {
                        try {
                            byte[] raw = java.util.Base64.getMimeDecoder().decode(
                                    new String(b, eq + 1, vl, LATIN));
                            flacPicture(raw, 0, raw.length);
                        } catch (RuntimeException ignored) {
                        }
                    }
                } else if (vl <= MAX_TEXT) {
                    String v = clean(new String(b, eq + 1, vl, StandardCharsets.UTF_8));
                    if (v != null) {
                        if (key.equals("TITLE")) {
                            title = v;
                        } else if (key.equals("ARTIST")) {
                            artist = artist == null ? v : artist + ", " + v;
                        } else if (key.equals("ALBUMARTIST") || key.equals("ALBUM ARTIST")) {
                            albumArtist = v;
                        } else if (key.equals("ALBUM")) {
                            album = v;
                        }
                    }
                }
            }
            p += l;
        }
    }

    private void flacPicture(byte[] b, int off, int end) {
        if (off + 32 > end) {
            return;
        }
        int type = be32(b, off);
        int p = off + 4;
        int ml = be32(b, p);
        p += 4 + ml;
        if (ml < 0 || p + 4 > end) {
            return;
        }
        int dl = be32(b, p);
        p += 4 + dl + 16;
        if (dl < 0 || p + 4 > end) {
            return;
        }
        int n = be32(b, p);
        p += 4;
        if (n > 0 && p + n <= end) {
            picture(java.util.Arrays.copyOfRange(b, p, p + n), type == 3 ? 3 : type == 0 ? 2 : 1);
        }
    }

    private void flac(RandomAccessFile raf, long start, long len, boolean wantPicture) throws IOException {
        long pos = start + 4;
        while (pos + 4 <= len) {
            byte[] h = bytesAt(raf, pos, 4);
            int type = h[0] & 0x7F;
            boolean last = (h[0] & 0x80) != 0;
            int size = be24(h, 1);
            long body = pos + 4;
            pos = body + size;
            if (type == 0 && size >= 18) {
                byte[] s = bytesAt(raf, body, 18);
                int sr = ((s[10] & 0xFF) << 12) | ((s[11] & 0xFF) << 4) | ((s[12] & 0xF0) >> 4);
                long total = ((long) (s[13] & 0x0F) << 32) | (be32(s, 14) & 0xFFFFFFFFL);
                sampleRate = sr;
                channels = ((s[12] >> 1) & 7) + 1;
                if (sr > 0) {
                    durationMs = total * 1000L / sr;
                }
            } else if (type == 4 && size < (4 << 20)) {
                byte[] c = bytesAt(raf, body, size);
                vorbisComments(c, 0, size, false);
            } else if (type == 6 && wantPicture && size < MAX_PIC) {
                byte[] c = bytesAt(raf, body, size);
                flacPicture(c, 0, size);
            }
            if (last) {
                break;
            }
        }
    }

    private void ogg(RandomAccessFile raf, long start, long len, boolean wantPicture) throws IOException {
        long pos = start;
        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        int packets = 0;
        int rate = 0;
        int preskip = 0;
        boolean opus = false;
        int limit = wantPicture ? MAX_PIC * 2 : (1 << 20);
        outer:
        while (pos + 27 <= len && packets < 2) {
            byte[] h = bytesAt(raf, pos, 27);
            if (!is(h, 0, "OggS")) {
                break;
            }
            int nseg = h[26] & 0xFF;
            byte[] lac = bytesAt(raf, pos + 27, nseg);
            long data = pos + 27 + nseg;
            for (int i = 0; i < nseg; i++) {
                int l = lac[i] & 0xFF;
                if (packet.size() + l <= limit) {
                    packet.write(bytesAt(raf, data, l));
                } else if (packets >= 1 && !wantPicture) {
                    // comentario enorme (carátula incrustada) y no se pidió: basta con lo leído
                    byte[] b = packet.toByteArray();
                    parseOggComment(b, opus, false);
                    packets = 2;
                    break outer;
                }
                data += l;
                if (l < 255) {
                    byte[] b = packet.toByteArray();
                    packet.reset();
                    if (packets == 0) {
                        if (b.length >= 16 && b[0] == 1 && is(b, 1, "vorbis")) {
                            channels = b[11] & 0xFF;
                            rate = le32(b, 12);
                        } else if (b.length >= 12 && is(b, 0, "OpusHead")) {
                            opus = true;
                            channels = b[9] & 0xFF;
                            preskip = (b[10] & 0xFF) | ((b[11] & 0xFF) << 8);
                            rate = 48000;
                        } else {
                            return;
                        }
                    } else {
                        parseOggComment(b, opus, wantPicture);
                    }
                    packets++;
                    if (packets >= 2) {
                        break outer;
                    }
                }
            }
            pos = data;
        }
        sampleRate = rate;
        if (rate > 0) {
            int tail = (int) Math.min(len, 1 << 16);
            byte[] b = bytesAt(raf, len - tail, tail);
            for (int i = tail - 27; i >= 0; i--) {
                if (is(b, i, "OggS")) {
                    long gran = (le32(b, i + 6) & 0xFFFFFFFFL) | ((long) le32(b, i + 10) << 32);
                    if (gran > 0) {
                        durationMs = Math.max(0, gran - preskip) * 1000L / rate;
                    }
                    break;
                }
            }
        }
    }

    private void parseOggComment(byte[] b, boolean opus, boolean wantPicture) {
        if (opus) {
            if (b.length > 8 && is(b, 0, "OpusTags")) {
                vorbisComments(b, 8, b.length, wantPicture);
            }
        } else if (b.length > 7 && b[0] == 3 && is(b, 1, "vorbis")) {
            vorbisComments(b, 7, b.length, wantPicture);
        }
    }

    // ------------------------------------------------------------ MP4

    private void mp4(RandomAccessFile raf, long len, boolean wantPicture) throws IOException {
        long pos = 0;
        while (pos + 8 <= len) {
            byte[] h = bytesAt(raf, pos, 8);
            long size = be32(h, 0) & 0xFFFFFFFFL;
            int hl = 8;
            if (size == 1) {
                byte[] x = bytesAt(raf, pos + 8, 8);
                size = ((long) be32(x, 0) << 32) | (be32(x, 4) & 0xFFFFFFFFL);
                hl = 16;
            } else if (size == 0) {
                size = len - pos;
            }
            if (size < hl) {
                break;
            }
            if (is(h, 4, "moov")) {
                mp4Box(raf, pos + hl, pos + size, 0, wantPicture);
                return;
            }
            pos += size;
        }
    }

    private void mp4Box(RandomAccessFile raf, long pos, long end, int depth, boolean wantPicture)
            throws IOException {
        while (pos + 8 <= end && depth < 8) {
            byte[] h = bytesAt(raf, pos, 8);
            long size = be32(h, 0) & 0xFFFFFFFFL;
            if (size < 8 || pos + size > end) {
                break;
            }
            String type = new String(h, 4, 4, LATIN);
            long body = pos + 8;
            long bend = pos + size;
            if (type.equals("mvhd")) {
                byte[] b = bytesAt(raf, body, (int) Math.min(32, bend - body));
                if (b[0] == 1 && b.length >= 32) {
                    long ts = be32(b, 20) & 0xFFFFFFFFL;
                    long du = ((long) be32(b, 24) << 32) | (be32(b, 28) & 0xFFFFFFFFL);
                    if (ts > 0) {
                        durationMs = du * 1000L / ts;
                    }
                } else if (b.length >= 20) {
                    long ts = be32(b, 12) & 0xFFFFFFFFL;
                    long du = be32(b, 16) & 0xFFFFFFFFL;
                    if (ts > 0) {
                        durationMs = du * 1000L / ts;
                    }
                }
            } else if (type.equals("udta") || type.equals("ilst")) {
                mp4Box(raf, body, bend, depth + 1, wantPicture);
            } else if (type.equals("meta")) {
                mp4Box(raf, body + 4, bend, depth + 1, wantPicture);
            } else if (depth >= 2 && (type.equals("©nam") || type.equals("©ART") || type.equals("aART")
                    || type.equals("©alb") || (wantPicture && type.equals("covr")))) {
                if (bend - body >= 16) {
                    byte[] dh = bytesAt(raf, body, 16);
                    long ds = be32(dh, 0) & 0xFFFFFFFFL;
                    if (is(dh, 4, "data") && ds >= 16 && body + ds <= bend) {
                        int n = (int) Math.min(ds - 16, type.equals("covr") ? MAX_PIC : MAX_TEXT);
                        byte[] v = bytesAt(raf, body + 16, n);
                        if (type.equals("covr")) {
                            picture(v, 3);
                        } else {
                            String s = clean(new String(v, StandardCharsets.UTF_8));
                            if (type.equals("©nam")) {
                                title = s;
                            } else if (type.equals("©ART")) {
                                artist = s;
                            } else if (type.equals("aART")) {
                                albumArtist = s;
                            } else {
                                album = s;
                            }
                        }
                    }
                }
            }
            pos = bend;
        }
    }

    // ------------------------------------------------------------ WAV

    private void wav(RandomAccessFile raf, long len) throws IOException {
        long pos = 12;
        int byteRate = 0;
        while (pos + 8 <= len) {
            byte[] h = bytesAt(raf, pos, 8);
            long size = le32(h, 4) & 0xFFFFFFFFL;
            long body = pos + 8;
            if (is(h, 0, "fmt ") && size >= 16) {
                byte[] f = bytesAt(raf, body, 16);
                channels = (f[2] & 0xFF) | ((f[3] & 0xFF) << 8);
                sampleRate = le32(f, 4);
                byteRate = le32(f, 8);
            } else if (is(h, 0, "data")) {
                long n = Math.min(size, len - body);
                if (byteRate > 0) {
                    durationMs = n * 1000L / byteRate;
                }
            } else if (is(h, 0, "LIST") && size >= 4 && size < (1 << 20) && body + size <= len) {
                byte[] l = bytesAt(raf, body, (int) size);
                if (is(l, 0, "INFO")) {
                    int p = 4;
                    while (p + 8 <= l.length) {
                        int s = le32(l, p + 4);
                        if (s < 0 || p + 8 + s > l.length) {
                            break;
                        }
                        String v = clean(new String(l, p + 8, s, StandardCharsets.UTF_8));
                        if (is(l, p, "INAM")) {
                            title = v;
                        } else if (is(l, p, "IART")) {
                            artist = v;
                        } else if (is(l, p, "IPRD")) {
                            album = v;
                        }
                        p += 8 + s + (s & 1);
                    }
                }
            }
            pos = body + size + (size & 1);
        }
    }
}
