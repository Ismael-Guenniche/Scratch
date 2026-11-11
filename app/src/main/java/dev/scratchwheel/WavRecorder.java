package dev.scratchwheel;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Graba a WAV (PCM 16 bits estéreo) lo que sale por el audio. {@link #feed} se llama desde el hilo de audio y solo
 * copia el bloque a una cola; la escritura a disco va en otro hilo para no cortar el sonido. Sin dependencias de
 * Android.
 */
final class WavRecorder {
    private final File file;
    private final int rate;
    private final ArrayBlockingQueue<short[]> queue = new ArrayBlockingQueue<short[]>(256);
    private volatile boolean running;
    private volatile boolean dropped;
    private Thread writer;
    private long bytes;
    private IOException error;

    WavRecorder(File file, int rate) {
        this.file = file;
        this.rate = rate;
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
                    byte[] out = new byte[0];
                    while (running || !queue.isEmpty()) {
                        short[] b = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (b == null) {
                            continue;
                        }
                        if (out.length < b.length * 2) {
                            out = new byte[b.length * 2];
                        }
                        for (int i = 0; i < b.length; i++) {
                            out[2 * i] = (byte) b[i];
                            out[2 * i + 1] = (byte) (b[i] >> 8);
                        }
                        f.write(out, 0, b.length * 2);
                        bytes += b.length * 2L;
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
        short[] c = new short[n];
        System.arraycopy(buf, 0, c, 0, n);
        if (!queue.offer(c)) {
            dropped = true;
        }
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
