package dev.scratchwheel;

import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import com.nothing.ketchum.Common;
import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphFrame;
import com.nothing.ketchum.GlyphManager;

/**
 * EXPERIMENTAL · SOLO SABOR PERSONAL. Glyph Bar del Nothing Phone (4a): seis zonas (A1 arriba … A6 abajo) con brillo de
 * 0 a 4000, controladas con el Glyph Developer Kit de Nothing. Solo funciona con la app en primer plano (lo exige el
 * SDK) y en Android 14 o superior. En el sabor play esta clase es un stub vacío.
 */
final class GlyphLights {
    static final boolean AVAILABLE = true;
    static final int OFF = 0;
    static final int METER = 1;
    static final int BEAT = 2;
    static final int PROGRESS = 3;

    private static final int ZONES = 6;
    private static final int MAX = 4000;

    private static GlyphManager gm;
    private static boolean ready;
    private static long lastSend;
    private static final int[] sent = new int[ZONES];
    private static float peak;

    private GlyphLights() {
    }

    /** ¿Es un Phone (4a) con Android 14+? */
    static boolean supported() {
        try {
            return Build.VERSION.SDK_INT >= 34 && Common.is25111();
        } catch (Throwable t) {
            return false;
        }
    }

    static void start(Context c) {
        if (gm != null || !supported()) {
            return;
        }
        try {
            gm = GlyphManager.getInstance(c.getApplicationContext());
            gm.init(new GlyphManager.Callback() {
                @Override
                public void onServiceConnected(ComponentName name) {
                    try {
                        gm.register(Glyph.DEVICE_25111);
                        gm.openSession();
                        ready = true;
                    } catch (Throwable t) {
                        ready = false;
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    ready = false;
                }
            });
        } catch (Throwable t) {
            gm = null;
        }
    }

    static void stop() {
        GlyphManager g = gm;
        gm = null;
        ready = false;
        java.util.Arrays.fill(sent, -1);
        if (g == null) {
            return;
        }
        try {
            g.turnOff();
            g.closeSession();
        } catch (Throwable ignored) {
        }
        try {
            g.unInit();
        } catch (Throwable ignored) {
        }
    }

    /**
     * Un fotograma (se llama desde el ticker de la pantalla). {@code level} 0..1 del vúmetro, {@code beatPhase} 0..1
     * dentro del pulso (0 = en el golpe; -1 sin BPM) y {@code progress} 0..1 de la canción.
     */
    static void frame(int mode, float level, double beatPhase, float progress, boolean playing) {
        if (!ready || gm == null) {
            return;
        }
        long now = System.nanoTime();
        if (now - lastSend < 40_000_000L) {
            return; // 25 fotogramas por segundo como mucho
        }
        int[] b = new int[ZONES];
        if (mode == METER) {
            peak = Math.max(level, peak * 0.86f);
            fillFromBottom(b, Math.min(1f, peak * 1.3f));
        } else if (mode == BEAT) {
            if (playing && beatPhase >= 0) {
                float f = (float) Math.exp(-beatPhase * 5.0);
                for (int i = 0; i < ZONES; i++) {
                    b[i] = (int) (MAX * f);
                }
            }
        } else if (mode == PROGRESS) {
            fillFromBottom(b, progress);
        }
        boolean same = true;
        for (int i = 0; i < ZONES; i++) {
            if (Math.abs(b[i] - sent[i]) > 60) {
                same = false;
            }
        }
        if (same) {
            return;
        }
        lastSend = now;
        try {
            GlyphFrame.Builder fb = gm.getGlyphFrameBuilder();
            boolean any = false;
            for (int i = 0; i < ZONES; i++) {
                if (b[i] > 0) {
                    fb.buildChannel(i, b[i]);
                    any = true;
                }
                sent[i] = b[i];
            }
            if (any) {
                gm.toggle(fb.build());
            } else {
                gm.turnOff();
            }
        } catch (Throwable t) {
            ready = false;
        }
    }

    /** A6 es la de abajo: se llena de abajo arriba, la última zona con brillo parcial. */
    private static void fillFromBottom(int[] b, float v) {
        float lit = Math.max(0, Math.min(1, v)) * ZONES;
        for (int k = 0; k < ZONES; k++) {
            int zone = ZONES - 1 - k;
            float f = Math.max(0, Math.min(1, lit - k));
            b[zone] = (int) (MAX * f);
        }
    }
}
