package dev.scratchwheel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Graba a WAV (PCM 16 bits estéreo) lo que sale por el audio. {@link #feed} se llama desde el hilo de audio y solo
 * copia el bloque a un anillo ya reservado (no crea objetos ni bloquea); la escritura a disco va en otro hilo para
 * no cortar el sonido. Sin dependencias de Android.
 */
final class WavRecorder {
    private final File file;
    private final int rate;
    /** Anillo de muestras (≈1,5 s): un único hilo escribe (audio) y otro lee (disco). */
    private final short[] ring;
    private volatile long wr;
    private volatile long rd;
    private volatile boolean running;
    private volatile boolean dropped;
    private Thread writer;
    private long bytes;
    private IOException error;

    WavRecorder(File file, int rate) {
        this.file = file;
        this.rate = rate;
        this.ring = new short[Math.max(16384, rate * 3)];
    }

    void start() throws IOException {
        final RandomAccessFile f = new RandomAccessFile(file, "rw");
        f.setLength(0);
        f.write(header(0));
        running = true;
        writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] out = new byte[16384];
                    while (true) {
                        long r = rd;
                        int avail = (int) (wr - r);
                        if (avail == 0) {
                            if (!running) {
                                break;
                            }
                            Thread.sleep(20);
                            continue;
                        }
                        int take = Math.min(avail, out.length / 2);
                        int at = (int) (r % ring.length);
                        for (int i = 0; i < take; i++) {
                            short v = ring[at];
                            if (++at == ring.length) {
                                at = 0;
                            }
                            out[2 * i] = (byte) v;
                            out[2 * i + 1] = (byte) (v >> 8);
                        }
                        rd = r + take;
                        f.write(out, 0, take * 2);
                        bytes += take * 2L;
                    }
                    f.seek(0);
                    f.write(header(bytes));
                } catch (IOException e) {
                    error = e;
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    try {
                        f.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }, "wav-rec");
        writer.start();
    }

    /** Desde el hilo de audio: copia n muestras (intercaladas L/R). Si la cola está llena se descarta (y se avisa). */
    void feed(short[] buf, int n) {
        if (!running) {
            return;
        }
        long w = wr;
        if (n > ring.length - (int) (w - rd)) {
            dropped = true;
            return;
        }
        int at = (int) (w % ring.length);
        int first = Math.min(n, ring.length - at);
        System.arraycopy(buf, 0, ring, at, first);
        if (first < n) {
            System.arraycopy(buf, first, ring, 0, n - first);
        }
        wr = w + n;
    }

    boolean hadDrops() {
        return dropped;
    }

    /** Termina, espera al hilo de escritura y deja la cabecera correcta. Devuelve los bytes de audio escritos. */
    long stop() throws IOException {
        running = false;
        try {
            if (writer != null) {
                writer.join(5000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (error != null) {
            throw error;
        }
        return bytes;
    }

    File file() {
        return file;
    }

    private byte[] header(long dataBytes) {
        long total = 36 + dataBytes;
        int br = rate * 4;
        byte[] h = new byte[44];
        put(h, 0, "RIFF");
        le32(h, 4, total);
        put(h, 8, "WAVE");
        put(h, 12, "fmt ");
        le32(h, 16, 16);
        h[20] = 1;
        h[22] = 2;
        le32(h, 24, rate);
        le32(h, 28, br);
        h[32] = 4;
        h[34] = 16;
        put(h, 36, "data");
        le32(h, 40, dataBytes);
        return h;
    }

    private static void put(byte[] h, int o, String s) {
        for (int i = 0; i < s.length(); i++) {
            h[o + i] = (byte) s.charAt(i);
        }
    }

    private static void le32(byte[] h, int o, long v) {
        h[o] = (byte) v;
        h[o + 1] = (byte) (v >> 8);
        h[o + 2] = (byte) (v >> 16);
        h[o + 3] = (byte) (v >> 24);
    }
}
