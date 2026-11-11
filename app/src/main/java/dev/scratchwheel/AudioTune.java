package dev.scratchwheel;

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

    private AudioTune() {
    }

    static BufferTuner start(AudioTrack t, int block, int burst, int rate) {
        try {
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
    static void step(AudioTrack t, BufferTuner tuner, int rate) {
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
