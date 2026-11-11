package dev.scratchwheel;

import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.os.Build;
import android.util.Log;

/** Pegamento entre {@link BufferTuner} y el AudioTrack. Todo va protegido: si algo falla, el audio sigue como antes. */
final class AudioTune {
    private static final String TAG = "ScratchAudio";
    /** Tamaño que funcionó la última vez, para no reaprenderlo cada vez que el hilo de audio se despierta. */
    private static volatile int learned;
    /** Capacidad con la que se aprendió: si cambia (otra salida de audio), se olvida lo aprendido. */
    private static volatile int learnedCap;

    // ----- latencia de salida medida con las marcas de tiempo del AudioTrack (solo las toca el hilo de audio)
    private static final AudioTimestamp TS = new AudioTimestamp();
    private static long written;
    private static int sinceMeasure;
    private static double smoothed = -1;
    /** Última latencia medida en ms (−1 si aún no hay medida). La lee Ajustes. */
    static volatile int measuredMs = -1;
    /** Ajustes: compensar o no, y ajuste fino en segundos. */
    static volatile boolean compensate = true;
    static volatile double trimSec;

    private AudioTune() {
    }

    /** Aplica al motor la latencia a compensar según los ajustes y la última medida. */
    static void apply(ScratchEngine engine) {
        double lat = smoothed < 0 ? 0 : smoothed;
        engine.setDisplayLatency(compensate ? Math.max(0, lat + trimSec) : 0);
    }

    /**
     * Cada ~0,5 s compara los frames escritos con los que el sistema dice haber presentado ya: la diferencia es
     * lo que tarda en oírse lo que se escribe ahora. No crea objetos.
     */
    private static void measure(AudioTrack t, int block, int rate, ScratchEngine engine) {
        written += block;
        sinceMeasure += block;
        if (sinceMeasure < rate / 2) {
            return;
        }
        sinceMeasure = 0;
        if (!t.getTimestamp(TS) || TS.framePosition <= 0) {
            return;
        }
        double pending = (written - TS.framePosition) / (double) rate;
        double lat = pending - (System.nanoTime() - TS.nanoTime) / 1e9;
        if (lat < 0 || lat > 0.5) {
            return;
        }
        smoothed = smoothed < 0 ? lat : smoothed + 0.2 * (lat - smoothed);
        measuredMs = (int) Math.round(smoothed * 1000);
        apply(engine);
    }

    static BufferTuner start(AudioTrack t, int block, int burst, int rate) {
        try {
            written = 0;
            sinceMeasure = 0;
            int cap = t.getBufferCapacityInFrames();
            if (cap != learnedCap) {
                learned = 0;
                learnedCap = cap;
            }
            // Solo se encoge el búfer en la ruta rápida; si Android no la concedió, se deja entero como antes.
            boolean fast = Build.VERSION.SDK_INT < 26
                    || t.getPerformanceMode() == AudioTrack.PERFORMANCE_MODE_LOW_LATENCY;
            BufferTuner tuner = new BufferTuner(block, cap, fast ? learned : cap);
            tuner.applied(t.setBufferSizeInFrames(tuner.frames()));
            Log.i(TAG, "salida " + rate + " Hz, ráfaga " + burst + ", bloque " + block + ", capacidad " + cap
                    + (fast ? ", ruta rápida" : ", ruta normal") + ", búfer " + tuner.frames() + " frames (" + Math.round(tuner.millis(rate)) + " ms)");
            return tuner;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Una vez por bloque, desde el hilo de audio. No crea objetos salvo cuando el búfer crece (raro). */
    static void step(AudioTrack t, BufferTuner tuner, int rate, int block, ScratchEngine engine) {
        try {
            measure(t, block, rate, engine);
        } catch (RuntimeException ignored) {
        }
        if (tuner == null) {
            return;
        }
        try {
            int under = t.getUnderrunCount();
            int want = tuner.onBlock(under);
            if (want > 0) {
                tuner.applied(t.setBufferSizeInFrames(want));
                learned = tuner.frames();
                Log.i(TAG, "underruns " + under + ": búfer a " + tuner.frames() + " frames ("
                        + Math.round(tuner.millis(rate)) + " ms)");
            }
        } catch (RuntimeException ignored) {
        }
    }
}
