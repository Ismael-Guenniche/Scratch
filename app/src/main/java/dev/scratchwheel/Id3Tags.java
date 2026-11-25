package dev.scratchwheel;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escribe título, artista y álbum en las etiquetas ID3 de un MP3 (sin Android, testable). Mantiene el resto de marcos
 * tal cual (carátula, letras, año…), conserva la versión de la etiqueta (2.3 o 2.4; si no había, crea una 2.3) y
 * actualiza también la ID3v1 del final si existe. El audio no se toca.
 */
final class Id3Tags {
    static final String TITLE = "TIT2";
    static final String ARTIST = "TPE1";
    static final String ALBUM = "TALB";

    private static final Charset LATIN1 = Charset.forName("ISO-8859-1");
    private static final Charset UTF16 = Charset.forName("UTF-16");
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int PADDING = 1024;

    private Id3Tags() {
    }

    /** No se puede escribir sin riesgo (etiqueta con «unsynchronisation» o versión rara). */
    static final class Unsupported extends Exception {
        Unsupported(String m) {
            super(m);
        }
    }

    /**
     * Devuelve el archivo con las etiquetas nuevas. Un valor null en {@code text} deja ese marco como estaba; una
     * cadena vacía lo quita.
     */
    static byte[] retag(byte[] file, Map<String, String> text) throws Unsupported {
        int major = 3;
        int audioStart = 0;
        LinkedHashMap<String, byte[]> frames = new LinkedHashMap<String, byte[]>();
        java.util.ArrayList<byte[]> rawFrames = new java.util.ArrayList<byte[]>();
        java.util.ArrayList<String> rawIds = new java.util.ArrayList<String>();
        if (file.length >= 10 && file[0] == 'I' && file[1] == 'D' && file[2] == '3') {
            major = file[3];
            int flags = file[5] & 0xFF;
            int size = syncsafe(file, 6);
            if (major != 3 && major != 4) {
                throw new Unsupported("ID3v2." + major);
            }
            if ((flags & 0x80) != 0) {
                throw new Unsupported("unsync");
            }
            int end = Math.min(file.length, 10 + size);
            audioStart = end + (major == 4 && (flags & 0x10) != 0 ? 10 : 0);
            int p = 10;
            if ((flags & 0x40) != 0) {
                int ext = major == 4 ? syncsafe(file, p) : be32(file, p) + 4;
                p += ext;
            }
            while (p + 10 <= end) {
                if (file[p] == 0) {
                    break; // relleno
                }
                String id = new String(file, p, 4, LATIN1);
                int fs = major == 4 ? syncsafe(file, p + 4) : be32(file, p + 4);
                if (fs < 0 || p + 10 + fs > end) {
                    break;
                }
                byte[] raw = new byte[10 + fs];
                System.arraycopy(file, p, raw, 0, raw.length);
                rawIds.add(id);
                rawFrames.add(raw);
                p += 10 + fs;
            }
        }
        // marcos que se quedan (los que no se cambian) y los nuevos
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        java.util.HashSet<String> done = new java.util.HashSet<String>();
        for (int i = 0; i < rawFrames.size(); i++) {
            String id = rawIds.get(i);
            if (text.containsKey(id) && text.get(id) != null) {
                if (done.add(id) && text.get(id).length() > 0) {
                    write(body, textFrame(id, text.get(id), major));
                }
                continue;
            }
            write(body, rawFrames.get(i));
        }
        for (Map.Entry<String, String> e : text.entrySet()) {
            if (e.getValue() != null && e.getValue().length() > 0 && !done.contains(e.getKey())) {
                write(body, textFrame(e.getKey(), e.getValue(), major));
            }
        }
        byte[] b = body.toByteArray();
        int tagSize = b.length + PADDING;
        ByteArrayOutputStream out = new ByteArrayOutputStream(file.length + tagSize + 10);
        out.write('I');
        out.write('D');
        out.write('3');
        out.write(major);
        out.write(0);
        out.write(0);
        writeSyncsafe(out, tagSize);
        write(out, b);
        write(out, new byte[PADDING]);
        // audio (y ID3v1 al final, actualizada)
        byte[] rest = new byte[file.length - audioStart];
        System.arraycopy(file, audioStart, rest, 0, rest.length);
        if (rest.length >= 128 && rest[rest.length - 128] == 'T' && rest[rest.length - 127] == 'A'
                && rest[rest.length - 126] == 'G') {
            int v1 = rest.length - 128;
            v1Field(rest, v1 + 3, text.get(TITLE));
            v1Field(rest, v1 + 33, text.get(ARTIST));
            v1Field(rest, v1 + 63, text.get(ALBUM));
        }
        write(out, rest);
        return out.toByteArray();
    }

    private static void v1Field(byte[] b, int off, String v) {
        if (v == null) {
            return;
        }
        byte[] s = v.getBytes(LATIN1);
        for (int i = 0; i < 30; i++) {
            b[off + i] = i < s.length ? s[i] : 0;
        }
    }

    private static byte[] textFrame(String id, String value, int major) {
        byte[] enc;
        byte encoding;
        if (major == 4) {
            encoding = 3;
            enc = value.getBytes(UTF8);
        } else {
            encoding = 1;
            enc = value.getBytes(UTF16); // con BOM
        }
        int size = 1 + enc.length;
        byte[] f = new byte[10 + size];
        byte[] idb = id.getBytes(LATIN1);
        System.arraycopy(idb, 0, f, 0, 4);
        if (major == 4) {
            f[4] = (byte) ((size >> 21) & 0x7F);
            f[5] = (byte) ((size >> 14) & 0x7F);
            f[6] = (byte) ((size >> 7) & 0x7F);
            f[7] = (byte) (size & 0x7F);
        } else {
            f[4] = (byte) (size >>> 24);
            f[5] = (byte) (size >>> 16);
            f[6] = (byte) (size >>> 8);
            f[7] = (byte) size;
        }
        f[10] = encoding;
        System.arraycopy(enc, 0, f, 11, enc.length);
        return f;
    }

    /** Texto de un marco (para comprobarlo en los tests); null si no está. */
    static String read(byte[] file, String wanted) {
        if (file.length < 10 || file[0] != 'I' || file[1] != 'D' || file[2] != '3') {
            return null;
        }
        int major = file[3];
        int end = Math.min(file.length, 10 + syncsafe(file, 6));
        int p = 10;
        while (p + 10 <= end && file[p] != 0) {
            String id = new String(file, p, 4, LATIN1);
            int fs = major == 4 ? syncsafe(file, p + 4) : be32(file, p + 4);
            if (fs <= 0 || p + 10 + fs > end) {
                return null;
            }
            if (id.equals(wanted)) {
                int e = file[p + 10];
                Charset cs = e == 0 ? LATIN1 : e == 1 ? UTF16 : e == 2 ? Charset.forName("UTF-16BE") : UTF8;
                String s = new String(file, p + 11, fs - 1, cs);
                int z = s.indexOf('\0');
                return z >= 0 ? s.substring(0, z) : s;
            }
            p += 10 + fs;
        }
        return null;
    }

    private static int syncsafe(byte[] b, int o) {
        return ((b[o] & 0x7F) << 21) | ((b[o + 1] & 0x7F) << 14) | ((b[o + 2] & 0x7F) << 7) | (b[o + 3] & 0x7F);
    }

    private static int be32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static void writeSyncsafe(ByteArrayOutputStream o, int v) {
        o.write((v >> 21) & 0x7F);
        o.write((v >> 14) & 0x7F);
        o.write((v >> 7) & 0x7F);
        o.write(v & 0x7F);
    }

    private static void write(ByteArrayOutputStream o, byte[] b) {
        o.write(b, 0, b.length);
    }
}
