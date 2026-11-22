package android.media;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

/**
 * Salida de audio sobre Java Sound. El búfer de la línea es grande y fijo; el tamaño "efectivo" que pide
 * la app se imita esperando en write() a que lo pendiente baje de ese tamaño, así la latencia es la pedida.
 */
public class AudioTrack {
    public static final int MODE_STATIC = 0;
    public static final int MODE_STREAM = 1;
    public static final int PERFORMANCE_MODE_NONE = 0;
    public static final int PERFORMANCE_MODE_LOW_LATENCY = 1;
    public static final int PERFORMANCE_MODE_POWER_SAVING = 2;
    public static final int STATE_UNINITIALIZED = 0;
    public static final int STATE_INITIALIZED = 1;
    public static final int PLAYSTATE_STOPPED = 1;
    public static final int PLAYSTATE_PAUSED = 2;
    public static final int PLAYSTATE_PLAYING = 3;
    public static final int SUCCESS = 0;
    public static final int ERROR = -1;
    public static final int ERROR_BAD_VALUE = -2;
    public static final int ERROR_INVALID_OPERATION = -3;
    public static final int ERROR_DEAD_OBJECT = -6;
    public static final int WRITE_BLOCKING = 0;
    public static final int WRITE_NON_BLOCKING = 1;

    private static volatile float master = 1f;
    private static volatile boolean warned;

    /** Volumen de la app, 0..1 (curva cuadrática para que el control se sienta natural). */
    public static void setMasterVolume(float v) {
        v = Math.max(0f, Math.min(1f, v));
        master = v * v;
    }

    public static int getMinBufferSize(int rate, int channelConfig, int format) {
        return Math.max(1024, rate / 50) * 4;
    }

    public static int getNativeOutputSampleRate(int stream) {
        return 48000;
    }

    public static class Builder {
        private AudioFormat format = new AudioFormat();
        private int bytes;

        public Builder setAudioAttributes(AudioAttributes a) {
            return this;
        }

        public Builder setAudioFormat(AudioFormat f) {
            format = f;
            return this;
        }

        public Builder setBufferSizeInBytes(int b) {
            bytes = b;
            return this;
        }

        public Builder setTransferMode(int m) {
            return this;
        }

        public Builder setPerformanceMode(int m) {
            return this;
        }

        public Builder setSessionId(int id) {
            return this;
        }

        public AudioTrack build() {
            return new AudioTrack(format.rate, bytes);
        }
    }

    private final SourceDataLine line;
    private final int rate;
    private final int lineBytes;
    private final int capacityFrames;
    private final int minFrames;
    private volatile int effectiveFrames;
    private int underruns;
    private boolean wrote;
    private boolean playing;
    private volatile boolean released;
    private byte[] bytes = new byte[0];
    private float volume = 1f;
    private final long nullStart;
    private long nullWritten;

    private AudioTrack(int rate, int requestedBytes) {
        this.rate = rate;
        javax.sound.sampled.AudioFormat f = new javax.sound.sampled.AudioFormat(rate, 16, 2, true, false);
        SourceDataLine l0;
        try {
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, f);
            SourceDataLine l = (SourceDataLine) AudioSystem.getLine(info);
            int want = Math.max(requestedBytes, (rate / 5) * 4);
            l.open(f, want);
            l0 = l;
        } catch (Exception | LinkageError e) {
            // Sin tarjeta de sonido: se "reproduce" en silencio a velocidad real para que la app siga funcionando.
            desk.Rt.log("sin salida de audio: " + e);
            l0 = null;
            if (!warned) {
                warned = true;
                android.widget.Toast.makeText(desk.Shell.application(),
                        "Sin dispositivo de audio: no se oir\u00e1 nada / No audio device",
                        android.widget.Toast.LENGTH_LONG).show();
            }
        }
        line = l0;
        nullStart = System.nanoTime();
        lineBytes = line == null ? (rate / 5) * 4 : line.getBufferSize();
        capacityFrames = lineBytes / 4;
        int minMs = 40;
        try {
            minMs = Integer.parseInt(System.getProperty("scratch.minbuf", "40"));
        } catch (NumberFormatException ignored) {
        }
        minFrames = Math.min(capacityFrames, Math.max(256, rate * minMs / 1000));
        effectiveFrames = capacityFrames;
    }

    public int getState() {
        return released ? STATE_UNINITIALIZED : STATE_INITIALIZED;
    }

    public int getPlayState() {
        return playing ? PLAYSTATE_PLAYING : PLAYSTATE_STOPPED;
    }

    public int getSampleRate() {
        return rate;
    }

    public int getChannelCount() {
        return 2;
    }

    public int getAudioSessionId() {
        return 1;
    }

    public int getPerformanceMode() {
        return PERFORMANCE_MODE_LOW_LATENCY;
    }

    public int getBufferCapacityInFrames() {
        return capacityFrames;
    }

    public int getBufferSizeInFrames() {
        return effectiveFrames;
    }

    public int setBufferSizeInFrames(int frames) {
        effectiveFrames = Math.max(minFrames, Math.min(capacityFrames, frames));
        return effectiveFrames;
    }

    public int getUnderrunCount() {
        return underruns;
    }

    private long head() {
        if (line != null) {
            return line.getLongFramePosition();
        }
        return Math.min(nullWritten, (System.nanoTime() - nullStart) * rate / 1000000000L);
    }

    public int getPlaybackHeadPosition() {
        return (int) head();
    }

    public boolean getTimestamp(AudioTimestamp ts) {
        long p = head();
        if (p <= 0) {
            return false;
        }
        ts.framePosition = p;
        ts.nanoTime = System.nanoTime();
        return true;
    }

    public int setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        return SUCCESS;
    }

    public int setStereoVolume(float l, float r) {
        return setVolume(Math.max(l, r));
    }

    public void play() {
        if (released) {
            throw new IllegalStateException("released");
        }
        playing = true;
        if (line != null) {
            line.start();
        }
    }

    public void pause() {
        playing = false;
        if (line != null) {
            line.stop();
        }
    }

    public void stop() {
        playing = false;
        try {
            if (line != null) {
                line.stop();
                line.flush();
            }
        } catch (RuntimeException ignored) {
        }
    }

    public void flush() {
        if (line != null) {
            line.flush();
        }
    }

    public void release() {
        if (!released) {
            released = true;
            try {
                if (line != null) {
                    line.close();
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    public int write(short[] data, int off, int len) {
        return write(data, off, len, WRITE_BLOCKING);
    }

    public int write(short[] data, int off, int len, int mode) {
        if (released) {
            return ERROR_DEAD_OBJECT;
        }
        int frames = len / 2;
        if (line == null) {
            nullWritten += frames;
            long ahead = nullWritten * 1000000000L / rate - (System.nanoTime() - nullStart) - 20000000L;
            if (ahead > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(ahead);
            }
            return len;
        }
        int n = len * 2;
        if (bytes.length < n) {
            bytes = new byte[n];
        }
        float g = master * volume;
        if (g >= 0.999f) {
            for (int i = 0, j = 0; i < len; i++) {
                short s = data[off + i];
                bytes[j++] = (byte) s;
                bytes[j++] = (byte) (s >> 8);
            }
        } else {
            for (int i = 0, j = 0; i < len; i++) {
                int s = Math.round(data[off + i] * g);
                bytes[j++] = (byte) s;
                bytes[j++] = (byte) (s >> 8);
            }
        }
        try {
            // Esperar a que lo pendiente quepa en el búfer efectivo.
            int eff = Math.max(effectiveFrames, frames);
            long deadline = System.nanoTime() + 2000000000L;
            while (true) {
                int queued = (lineBytes - line.available()) / 4;
                if (queued <= 0 && wrote && playing) {
                    underruns++;
                    break;
                }
                if (queued + frames <= eff) {
                    break;
                }
                if (released || System.nanoTime() > deadline) {
                    break;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(700000L);
            }
            wrote = true;
            int w = line.write(bytes, 0, n);
            return w / 2;
        } catch (RuntimeException e) {
            return ERROR_DEAD_OBJECT;
        }
    }

    public int write(byte[] data, int off, int len) {
        if (released) {
            return ERROR_DEAD_OBJECT;
        }
        if (line == null) {
            return len;
        }
        return line.write(data, off, len - (len % 4));
    }
}
