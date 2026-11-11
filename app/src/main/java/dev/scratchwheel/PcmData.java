package dev.scratchwheel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Audio decodificado: estéreo intercalado de 16 bits. Hay dos formas de guardarlo:
 * <ul>
 * <li><b>en memoria</b> ({@link #stereo}): pistas cortas, WAV de prueba, el fragmento de arranque rápido;</li>
 * <li><b>en disco</b> ({@link #mapped()}): el PCM se escribe en un archivo temporal mientras se decodifica y luego
 * se proyecta en memoria (mmap). No ocupa heap, el sistema puede soltar las páginas que no se usan y no hay
 * límite práctico de duración. El motor lee de ahí por ventanas ({@link #copy}).</li>
 * </ul>
 */
public final class PcmData {
    /** Máximo de una pista en memoria (≈ 7,5 min a 44,1 kHz, 80 MB). */
    public static final int MAX_FRAMES = 20000000;
    /** Máximo de una pista en disco (≈ 2,5 h a 44,1 kHz): el archivo proyectado no puede pasar de 2 GB. */
    public static final int DISK_MAX_FRAMES = 400000000;
    /** Tramos de la forma de onda que se calculan al terminar de decodificar. */
    public static final int PEAKS = 480;

    /** Muestras en memoria, o {@code null} si la pista está en disco. */
    public final short[] stereo;
    public final int frames;
    public final int sampleRate;
    public final boolean truncated;
    /** true si solo es el principio de la pista (arranque rápido): el resto llega después. */
    public final boolean partial;
    /** En un fragmento parcial, los frames que tendrá la pista entera (para mostrar la duración real). */
    public final long totalFrames;
    /** Forma de onda ya calculada (pico 0..1 por tramo), o {@code null} si aún no se ha calculado. */
    public volatile float[] peaks;

    private final ShortBuffer map;
    /** Vista propia del motor para copiar ventanas (solo la usa el hilo que tiene el cerrojo del motor). */
    private final ShortBuffer engineView;

    private PcmData(short[] stereo, ShortBuffer map, int frames, int sampleRate, boolean truncated, boolean partial,
                    long totalFrames) {
        this.stereo = stereo;
        this.map = map;
        this.engineView = map == null ? null : map.duplicate();
        this.frames = frames;
        this.sampleRate = sampleRate;
        this.truncated = truncated;
        this.partial = partial;
        this.totalFrames = totalFrames;
    }

    /** true si el audio está en disco (proyectado en memoria) y no en un array. */
    public boolean mapped() {
        return map != null;
    }

    /** Bytes de heap que ocupa (una pista en disco no ocupa heap). */
    public long heapBytes() {
        return stereo == null ? 0 : (long) stereo.length * 2;
    }

    /** Muestra suelta por índice intercalado (2 × frame + canal). Vale desde cualquier hilo. */
    public short sample(int index) {
        return stereo != null ? stereo[index] : map.get(index);
    }

    /** Copia {@code count} frames desde {@code frame} a {@code dst} (desde el frame {@code dstFrame}). Solo para el motor. */
    public void copy(int frame, short[] dst, int dstFrame, int count) {
        if (stereo != null) {
            System.arraycopy(stereo, 2 * frame, dst, 2 * dstFrame, 2 * count);
        } else {
            engineView.position(2 * frame);
            engineView.get(dst, 2 * dstFrame, 2 * count);
        }
    }

    /** Pico (0..1) de cada uno de los {@code n} tramos iguales de la pista (misma cuenta que {@code Waveform.peaks}). */
    public float[] computePeaks(int n) {
        float[] out = new float[n];
        if (frames <= 0 || n <= 0) {
            return out;
        }
        for (int k = 0; k < n; k++) {
            int a = (int) ((long) k * frames / n);
            int b = Math.max(a + 1, (int) ((long) (k + 1) * frames / n));
            int step = Math.max(1, (b - a) / 64);
            int m = 0;
            for (int i = a; i < b && i < frames; i += step) {
                int l = Math.abs(sample(2 * i));
                int r = Math.abs(sample(2 * i + 1));
                m = Math.max(m, Math.max(l, r));
            }
            out[k] = Math.min(1f, m / 32768f);
        }
        return out;
    }

    /** Fallo al escribir o proyectar el archivo temporal: quien decodifica reintenta en memoria. */
    public static final class DiskError extends RuntimeException {
        DiskError(Throwable cause) {
            super(cause);
        }
    }

    /** Borra los temporales que dejara una ejecución anterior (se llama una vez al arrancar). */
    public static void cleanDir(File dir) {
        File[] fs = dir == null ? null : dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.getName().startsWith("pcm") && f.getName().endsWith(".raw")) {
                    f.delete();
                }
            }
        }
    }

    public static final class Builder {
        private short[] buf;
        private int frames;
        private boolean truncated;
        // modo disco
        private File file;
        private RandomAccessFile raf;
        private FileChannel ch;
        private ByteBuffer out;

        public Builder() {
            this(0);
        }

        /** {@code expectedFrames} (p. ej. duración x frecuencia) evita copias al crecer el búfer. */
        public Builder(long expectedFrames) {
            long f = expectedFrames <= 0 ? (1 << 19) : Math.min(expectedFrames + expectedFrames / 50 + 4096,
                    (long) MAX_FRAMES);
            buf = new short[(int) (2 * f)];
        }

        /**
         * Modo disco: el PCM va a un archivo temporal en {@code dir}. Si {@code dir} es null, no se puede crear el
         * archivo o no hay espacio de sobra para {@code expectedFrames}, se queda en memoria como siempre.
         */
        public Builder(long expectedFrames, File dir) {
            if (dir != null) {
                try {
                    dir.mkdirs();
                    long need = Math.max(expectedFrames, 0) * 4 + (64L << 20);
                    if (dir.getUsableSpace() > need) {
                        file = File.createTempFile("pcm", ".raw", dir);
                        raf = new RandomAccessFile(file, "rw");
                        ch = raf.getChannel();
                        out = ByteBuffer.allocateDirect(1 << 16).order(ByteOrder.nativeOrder());
                    }
                } catch (IOException e) {
                    abort();
                    ch = null;
                } catch (RuntimeException e) {
                    abort();
                    ch = null;
                }
            }
            if (ch == null) {
                long f = expectedFrames <= 0 ? (1 << 19) : Math.min(expectedFrames + expectedFrames / 50 + 4096,
                        (long) MAX_FRAMES);
                buf = new short[(int) (2 * f)];
            }
        }

        /** true si este constructor está escribiendo a disco. */
        public boolean onDisk() {
            return ch != null;
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
            if (ch != null) {
                if (frames >= DISK_MAX_FRAMES) {
                    truncated = true;
                    return;
                }
                if (out.remaining() < 4) {
                    flush();
                }
                out.putShort(l);
                out.putShort(r);
                frames++;
                return;
            }
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

        private void flush() {
            try {
                out.flip();
                while (out.hasRemaining()) {
                    ch.write(out);
                }
                out.clear();
            } catch (IOException e) {
                abort();
                throw new DiskError(e);
            }
        }

        public int frames() {
            return frames;
        }

        public PcmData build(int sampleRate) {
            if (ch == null) {
                return new PcmData(buf, null, frames, sampleRate, truncated, false, 0);
            }
            try {
                flush();
                ShortBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, 0, 4L * frames)
                        .order(ByteOrder.nativeOrder()).asShortBuffer();
                PcmData p = new PcmData(null, m, frames, sampleRate, truncated, false, 0);
                p.peaks = p.computePeaks(PEAKS);
                return p;
            } catch (IOException e) {
                throw new DiskError(e);
            } finally {
                // El archivo ya no hace falta con nombre: proyectado sigue vivo hasta que se suelte la pista.
                abort();
            }
        }

        /** Copia de lo decodificado hasta ahora, marcada como parcial (el decodificador sigue después). */
        public PcmData snapshot(int sampleRate, long totalFrames) {
            if (ch == null) {
                return new PcmData(Arrays.copyOf(buf, 2 * frames), null, frames, sampleRate, false, true, totalFrames);
            }
            try {
                flush();
                ByteBuffer bb = ByteBuffer.allocate(4 * frames).order(ByteOrder.nativeOrder());
                long at = 0;
                while (bb.hasRemaining()) {
                    int got = ch.read(bb, at);
                    if (got < 0) {
                        break;
                    }
                    at += got;
                }
                bb.flip();
                short[] s = new short[2 * frames];
                bb.asShortBuffer().get(s, 0, bb.remaining() / 2);
                return new PcmData(s, null, frames, sampleRate, false, true, totalFrames);
            } catch (IOException e) {
                abort();
                throw new DiskError(e);
            }
        }

        /** Cierra y borra el archivo temporal (decodificación cancelada, error, o ya proyectado). */
        public void abort() {
            try {
                if (raf != null) {
                    raf.close();
                }
            } catch (IOException ignored) {
            }
            if (file != null && !file.delete()) {
                file.deleteOnExit();
            }
            raf = null;
            file = null;
        }
    }
}
