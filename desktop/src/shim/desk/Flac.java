package desk;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Decodificador FLAC en Java puro (todas las predicciones y los tres modos estéreo), sin comprobar CRC. */
public final class Flac {
    public interface Sink {
        /** n muestras intercaladas de 16 bits (n = frames × canales, con 1 o 2 canales). */
        void samples(short[] interleaved, int n, int channels, int sampleRate) throws IOException;

        boolean cancelled();
    }

    private final InputStream in;
    private long bitBuf;
    private int bitCount;
    private int rate;
    private int channels;
    private int bps;

    private Flac(InputStream in) {
        this.in = in;
    }

    public static void decode(InputStream raw, Sink sink) throws IOException {
        new Flac(raw instanceof BufferedInputStream ? raw : new BufferedInputStream(raw, 1 << 16)).run(sink);
    }

    private int readByte() throws IOException {
        int b = in.read();
        if (b < 0) {
            throw new EOFException();
        }
        return b;
    }

    private int bits(int n) throws IOException {
        if (n == 0) {
            return 0;
        }
        if (n > 32) {
            throw new IOException("flac: bits");
        }
        while (bitCount < n) {
            bitBuf = (bitBuf << 8) | readByte();
            bitCount += 8;
        }
        bitCount -= n;
        long v = (bitBuf >>> bitCount) & ((1L << n) - 1);
        bitBuf &= (1L << bitCount) - 1;
        return (int) v;
    }

    private int signed(int n) throws IOException {
        if (n == 0) {
            return 0;
        }
        int v = bits(n);
        return (v << (32 - n)) >> (32 - n);
    }

    private long signedLong(int n) throws IOException {
        if (n <= 32) {
            return signed(n);
        }
        long hi = signed(n - 32);
        long lo = bits(32) & 0xFFFFFFFFL;
        return (hi << 32) | lo;
    }

    private void align() {
        bitCount -= bitCount % 8;
        bitBuf &= (1L << bitCount) - 1;
    }

    private void skip(long n) throws IOException {
        while (n > 0) {
            long k = in.skip(n);
            if (k <= 0) {
                readByte();
                k = 1;
            }
            n -= k;
        }
    }

    private void run(Sink sink) throws IOException {
        byte[] m = new byte[4];
        readFully(m, 4);
        if (m[0] == 'I' && m[1] == 'D' && m[2] == '3') {
            byte[] r = new byte[6];
            readFully(r, 6);
            int size = ((r[2] & 0x7F) << 21) | ((r[3] & 0x7F) << 14) | ((r[4] & 0x7F) << 7) | (r[5] & 0x7F);
            skip(size);
            readFully(m, 4);
        }
        if (!(m[0] == 'f' && m[1] == 'L' && m[2] == 'a' && m[3] == 'C')) {
            throw new IOException("no es FLAC");
        }
        boolean last = false;
        while (!last) {
            int h = readByte();
            last = (h & 0x80) != 0;
            int type = h & 0x7F;
            int len = (readByte() << 16) | (readByte() << 8) | readByte();
            if (type == 0) {
                byte[] s = new byte[len];
                readFully(s, len);
                rate = ((s[10] & 0xFF) << 12) | ((s[11] & 0xFF) << 4) | ((s[12] & 0xF0) >> 4);
                channels = ((s[12] >> 1) & 7) + 1;
                bps = (((s[12] & 1) << 4) | ((s[13] & 0xF0) >> 4)) + 1;
            } else {
                skip(len);
            }
        }
        if (rate <= 0 || channels <= 0) {
            throw new IOException("flac: sin STREAMINFO");
        }
        long[][] buf = new long[channels][];
        short[] out = new short[0];
        while (!sink.cancelled()) {
            int n;
            try {
                n = frame(buf);
            } catch (EOFException e) {
                break;
            }
            if (n < 0) {
                break;
            }
            int oc = Math.min(2, channels);
            if (out.length < n * oc) {
                out = new short[n * oc];
            }
            int depth = frameBps;
            int shift = depth - 16;
            for (int c = 0; c < oc; c++) {
                long[] src = buf[c];
                if (shift > 0) {
                    for (int i = 0; i < n; i++) {
                        out[i * oc + c] = (short) (src[i] >> shift);
                    }
                } else if (shift < 0) {
                    for (int i = 0; i < n; i++) {
                        out[i * oc + c] = (short) (src[i] << -shift);
                    }
                } else {
                    for (int i = 0; i < n; i++) {
                        out[i * oc + c] = (short) src[i];
                    }
                }
            }
            sink.samples(out, n * oc, oc, frameRate);
        }
    }

    private void readFully(byte[] b, int n) throws IOException {
        int got = 0;
        while (got < n) {
            int k = in.read(b, got, n - got);
            if (k < 0) {
                throw new EOFException();
            }
            got += k;
        }
    }

    private int frameBps;
    private int frameRate;

    /** Decodifica un frame en buf (un array por canal). Devuelve el número de muestras, o -1 al final. */
    private int frame(long[][] buf) throws IOException {
        bitCount = 0;
        bitBuf = 0;
        // Sincronía: 0xFF seguido de 0xF8 o 0xF9.
        int b = in.read();
        if (b < 0) {
            return -1;
        }
        int tries = 0;
        int b2;
        while (true) {
            if (b == 0xFF) {
                b2 = readByte();
                if ((b2 & 0xFE) == 0xF8) {
                    break;
                }
                b = b2;
            } else {
                b = readByte();
            }
            if (++tries > (1 << 22)) {
                throw new IOException("flac: sin sincronía");
            }
        }
        int bsCode = bits(4);
        int srCode = bits(4);
        int chan = bits(4);
        int ssCode = bits(3);
        bits(1);
        // Número de frame o de muestra, en "UTF-8" extendido.
        int lead = bits(8);
        int extra = 0;
        while ((lead & 0x80) != 0) {
            lead <<= 1;
            extra++;
        }
        for (int i = 1; i < extra; i++) {
            bits(8);
        }
        int n;
        if (bsCode == 1) {
            n = 192;
        } else if (bsCode >= 2 && bsCode <= 5) {
            n = 576 << (bsCode - 2);
        } else if (bsCode == 6) {
            n = bits(8) + 1;
        } else if (bsCode == 7) {
            n = bits(16) + 1;
        } else if (bsCode >= 8) {
            n = 256 << (bsCode - 8);
        } else {
            throw new IOException("flac: tamaño de bloque");
        }
        int sr = rate;
        switch (srCode) {
            case 1:
                sr = 88200;
                break;
            case 2:
                sr = 176400;
                break;
            case 3:
                sr = 192000;
                break;
            case 4:
                sr = 8000;
                break;
            case 5:
                sr = 16000;
                break;
            case 6:
                sr = 22050;
                break;
            case 7:
                sr = 24000;
                break;
            case 8:
                sr = 32000;
                break;
            case 9:
                sr = 44100;
                break;
            case 10:
                sr = 48000;
                break;
            case 11:
                sr = 96000;
                break;
            case 12:
                sr = bits(8) * 1000;
                break;
            case 13:
                sr = bits(16);
                break;
            case 14:
                sr = bits(16) * 10;
                break;
            default:
                break;
        }
        frameRate = sr;
        int depth;
        switch (ssCode) {
            case 1:
                depth = 8;
                break;
            case 2:
                depth = 12;
                break;
            case 4:
                depth = 16;
                break;
            case 5:
                depth = 20;
                break;
            case 6:
                depth = 24;
                break;
            case 7:
                depth = 32;
                break;
            default:
                depth = bps;
                break;
        }
        frameBps = depth;
        bits(8); // CRC-8
        int nch = chan < 8 ? chan + 1 : 2;
        if (nch > channels) {
            throw new IOException("flac: canales");
        }
        for (int c = 0; c < nch; c++) {
            if (buf[c] == null || buf[c].length < n) {
                buf[c] = new long[Math.max(n, 4608)];
            }
            int d = depth;
            if ((chan == 8 && c == 1) || (chan == 9 && c == 0) || (chan == 10 && c == 1)) {
                d++;
            }
            subframe(buf[c], n, d);
        }
        if (chan == 8) {
            long[] l = buf[0];
            long[] s = buf[1];
            for (int i = 0; i < n; i++) {
                s[i] = l[i] - s[i];
            }
        } else if (chan == 9) {
            long[] s = buf[0];
            long[] r = buf[1];
            for (int i = 0; i < n; i++) {
                s[i] = s[i] + r[i];
            }
        } else if (chan == 10) {
            long[] mid = buf[0];
            long[] side = buf[1];
            for (int i = 0; i < n; i++) {
                long sd = side[i];
                long md = (mid[i] << 1) | (sd & 1);
                mid[i] = (md + sd) >> 1;
                side[i] = (md - sd) >> 1;
            }
        }
        align();
        bits(16); // CRC-16
        return n;
    }

    private void subframe(long[] out, int n, int depth) throws IOException {
        bits(1);
        int type = bits(6);
        int wasted = 0;
        if (bits(1) == 1) {
            wasted = 1;
            while (bits(1) == 0) {
                wasted++;
            }
            depth -= wasted;
        }
        if (type == 0) {
            long v = signedLong(depth);
            for (int i = 0; i < n; i++) {
                out[i] = v;
            }
        } else if (type == 1) {
            for (int i = 0; i < n; i++) {
                out[i] = signedLong(depth);
            }
        } else if (type >= 8 && type <= 12) {
            int order = type - 8;
            for (int i = 0; i < order; i++) {
                out[i] = signedLong(depth);
            }
            residual(out, n, order);
            switch (order) {
                case 1:
                    for (int i = 1; i < n; i++) {
                        out[i] += out[i - 1];
                    }
                    break;
                case 2:
                    for (int i = 2; i < n; i++) {
                        out[i] += 2 * out[i - 1] - out[i - 2];
                    }
                    break;
                case 3:
                    for (int i = 3; i < n; i++) {
                        out[i] += 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3];
                    }
                    break;
                case 4:
                    for (int i = 4; i < n; i++) {
                        out[i] += 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4];
                    }
                    break;
                default:
                    break;
            }
        } else if (type >= 32) {
            int order = type - 31;
            for (int i = 0; i < order; i++) {
                out[i] = signedLong(depth);
            }
            int prec = bits(4) + 1;
            int shift = signed(5);
            int[] coef = new int[order];
            for (int i = 0; i < order; i++) {
                coef[i] = signed(prec);
            }
            residual(out, n, order);
            for (int i = order; i < n; i++) {
                long sum = 0;
                for (int j = 0; j < order; j++) {
                    sum += coef[j] * out[i - 1 - j];
                }
                out[i] += sum >> shift;
            }
        } else {
            throw new IOException("flac: subframe reservado");
        }
        if (wasted > 0) {
            for (int i = 0; i < n; i++) {
                out[i] <<= wasted;
            }
        }
    }

    private void residual(long[] out, int n, int order) throws IOException {
        int method = bits(2);
        if (method > 1) {
            throw new IOException("flac: residuo reservado");
        }
        int pbits = method == 0 ? 4 : 5;
        int escape = method == 0 ? 15 : 31;
        int po = bits(4);
        int parts = 1 << po;
        int i = order;
        for (int p = 0; p < parts; p++) {
            int count = (n >> po) - (p == 0 ? order : 0);
            int param = bits(pbits);
            if (param == escape) {
                int nb = bits(5);
                for (int k = 0; k < count; k++) {
                    out[i++] = signed(nb);
                }
            } else {
                for (int k = 0; k < count; k++) {
                    int q = 0;
                    while (true) {
                        if (bitCount == 0) {
                            bitBuf = readByte();
                            bitCount = 8;
                        }
                        // ceros a la izquierda dentro de los bits pendientes
                        int lz = Long.numberOfLeadingZeros(bitBuf) - (64 - bitCount);
                        if (lz >= bitCount) {
                            q += bitCount;
                            bitCount = 0;
                            bitBuf = 0;
                        } else {
                            q += lz;
                            bitCount -= lz + 1;
                            bitBuf &= (1L << bitCount) - 1;
                            break;
                        }
                    }
                    int u = (q << param) | bits(param);
                    out[i++] = (u >>> 1) ^ -(u & 1);
                }
            }
        }
    }
}
