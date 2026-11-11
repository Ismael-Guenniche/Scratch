package dev.scratchwheel;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Lector de WAV (PCM 8/16/24/32 bits y float de 32). Sin dependencias de Android. */
public final class WavParser {
    private WavParser() {
    }

    public static final class NotWavException extends IOException {
        public NotWavException(String message) {
            super(message);
        }
    }

    /** A partir de este tamaño (≈ 30 s) un WAV se guarda en disco si hay carpeta temporal. */
    private static final long DISK_FROM_FRAMES = 1300000;

    public static PcmData parse(InputStream raw) throws IOException {
        return parse(raw, null);
    }

    /** {@code pcmDir}: carpeta para el PCM temporal de los WAV largos (null = siempre en memoria). */
    public static PcmData parse(InputStream raw, java.io.File pcmDir) throws IOException {
        InputStream in = new BufferedInputStream(raw, 1 << 16);
        byte[] hdr = new byte[12];
        if (readUpTo(in, hdr, 12) < 12 || !tag(hdr, 0, "RIFF") || !tag(hdr, 8, "WAVE")) {
            throw new NotWavException(Tr.t("No es un WAV"));
        }
        int fmtTag = 0;
        int channels = 0;
        int rate = 0;
        int bits = 0;
        byte[] ch = new byte[8];
        while (true) {
            if (readUpTo(in, ch, 8) < 8) {
                throw new IOException(Tr.t("WAV sin bloque de datos"));
            }
            long size = le32(ch, 4) & 0xFFFFFFFFL;
            if (tag(ch, 0, "fmt ")) {
                if (size < 16 || size > 1024) {
                    throw new IOException(Tr.t("Cabecera fmt no válida"));
                }
                byte[] f = new byte[(int) size];
                if (readUpTo(in, f, f.length) < f.length) {
                    throw new IOException(Tr.t("fmt truncado"));
                }
                fmtTag = le16(f, 0);
                channels = le16(f, 2);
                rate = le32(f, 4);
                bits = le16(f, 14);
                if (fmtTag == 0xFFFE && f.length >= 26) {
                    fmtTag = le16(f, 24);
                }
                if ((size & 1) == 1) {
                    skip(in, 1);
                }
            } else if (tag(ch, 0, "data")) {
                return readData(in, size, fmtTag, channels, rate, bits, pcmDir);
            } else {
                skip(in, size + (size & 1));
            }
        }
    }

    private static PcmData readData(InputStream in, long size, int fmtTag, int channels,
                                    int rate, int bits, java.io.File pcmDir) throws IOException {
        if (channels < 1 || channels > 16 || rate < 1000 || rate > 400000) {
            throw new IOException(Tr.t("Formato WAV no válido"));
        }
        boolean isFloat = fmtTag == 3;
        if (fmtTag != 1 && !isFloat) {
            throw new IOException(Tr.t("WAV no soportado (solo PCM o float)"));
        }
        int bps = bits / 8;
        boolean okBits = isFloat ? bps == 4 : (bps >= 1 && bps <= 4);
        if (!okBits) {
            throw new IOException(Tr.t("Bits por muestra no soportados: ") + bits);
        }
        int frameBytes = bps * channels;
        boolean known = size > 0 && size < 0xFFFFFFF0L;
        long remaining = known ? size : Long.MAX_VALUE;
        // WAV grande y con carpeta temporal: a disco (sin límite de 7,5 min); si no, en memoria como siempre.
        long expect = known ? size / frameBytes : 0;
        PcmData.Builder b = pcmDir != null && expect > DISK_FROM_FRAMES ? new PcmData.Builder(expect, pcmDir)
                : new PcmData.Builder();
        byte[] buf = new byte[frameBytes * 4096];
        try {
        while (remaining > 0) {
            int want = (int) Math.min((long) buf.length, remaining);
            want -= want % frameBytes;
            if (want == 0) {
                break;
            }
            int got = readUpTo(in, buf, want);
            int nf = got / frameBytes;
            for (int f = 0; f < nf; f++) {
                int o = f * frameBytes;
                short l = sample(buf, o, bps, isFloat);
                short r = channels > 1 ? sample(buf, o + bps, bps, isFloat) : l;
                b.append(l, r);
            }
            remaining -= got;
            if (got < want) {
                break;
            }
        }
        if (b.frames() == 0) {
            throw new IOException(Tr.t("WAV vacío"));
        }
        return b.build(rate);
        } catch (IOException e) {
            b.abort();
            throw e;
        } catch (RuntimeException e) {
            b.abort();
            throw e;
        }
    }

    private static short sample(byte[] b, int o, int bps, boolean isFloat) {
        if (isFloat) {
            float v = Float.intBitsToFloat(le32(b, o));
            v = Math.max(-1f, Math.min(1f, v));
            return (short) Math.round(v * 32767f);
        }
        switch (bps) {
            case 1:
                return (short) (((b[o] & 0xFF) - 128) << 8);
            case 2:
                return (short) ((b[o] & 0xFF) | (b[o + 1] << 8));
            case 3:
                return (short) ((b[o + 1] & 0xFF) | (b[o + 2] << 8));
            default:
                return (short) ((b[o + 2] & 0xFF) | (b[o + 3] << 8));
        }
    }

    private static boolean tag(byte[] b, int o, String t) {
        for (int i = 0; i < 4; i++) {
            if (b[o + i] != (byte) t.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16)
                | ((b[o + 3] & 0xFF) << 24);
    }

    private static int readUpTo(InputStream in, byte[] buf, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int n = in.read(buf, total, len - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    private static void skip(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) {
                    throw new IOException(Tr.t("Fin de archivo inesperado"));
                }
                s = 1;
            }
            n -= s;
        }
    }
}
