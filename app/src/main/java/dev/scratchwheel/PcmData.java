package dev.scratchwheel;

import java.util.Arrays;

/** Audio decodificado: estéreo intercalado de 16 bits. */
public final class PcmData {
    public static final int MAX_FRAMES = 20000000;

    public final short[] stereo;
    public final int frames;
    public final int sampleRate;
    public final boolean truncated;

    private PcmData(short[] stereo, int frames, int sampleRate, boolean truncated) {
        this.stereo = stereo;
        this.frames = frames;
        this.sampleRate = sampleRate;
        this.truncated = truncated;
    }

    public static final class Builder {
        private short[] buf;
        private int frames;
        private boolean truncated;

        public Builder() {
            this(0);
        }

        /** {@code expectedFrames} (p. ej. duración x frecuencia) evita copias al crecer el búfer. */
        public Builder(long expectedFrames) {
            long f = expectedFrames <= 0 ? (1 << 19) : Math.min(expectedFrames + expectedFrames / 50 + 4096,
                    (long) MAX_FRAMES);
            buf = new short[(int) (2 * f)];
        }

        /** Añade {@code count} muestras intercaladas de {@code ch} canales (mono se duplica; >2 se quedan L y R). */
        public void appendInterleaved(short[] src, int count, int ch) {
            int n = count / ch;
            for (int k = 0; k < n; k++) {
                short l = src[k * ch];
                append(l, ch > 1 ? src[k * ch + 1] : l);
            }
        }

        /** Igual que {@link #appendInterleaved} pero con muestras float (-1..1) de algunos decodificadores. */
        public void appendFloat(float[] src, int count, int ch) {
            int n = count / ch;
            for (int k = 0; k < n; k++) {
                short l = toShort(src[k * ch]);
                append(l, ch > 1 ? toShort(src[k * ch + 1]) : l);
            }
        }

        private static short toShort(float v) {
            float c = v > 1f ? 1f : (v < -1f ? -1f : v);
            return (short) Math.round(c * 32767f);
        }

        public void append(short l, short r) {
            if (frames >= MAX_FRAMES) {
                truncated = true;
                return;
            }
            int need = 2 * frames + 2;
            if (need > buf.length) {
                long grown = (long) buf.length * 5 / 4 + 2;
                int newLen = (int) Math.min(grown, 2L * MAX_FRAMES);
                buf = Arrays.copyOf(buf, Math.max(newLen, need));
            }
            buf[2 * frames] = l;
            buf[2 * frames + 1] = r;
            frames++;
        }

        public int frames() {
            return frames;
        }

        public PcmData build(int sampleRate) {
            return new PcmData(buf, frames, sampleRate, truncated);
        }
    }
}
