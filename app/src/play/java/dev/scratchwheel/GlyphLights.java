package dev.scratchwheel;

import android.content.Context;

/** Sabor play (y escritorio): sin Glyph. La versión de verdad está en el sabor personal. */
final class GlyphLights {
    static final boolean AVAILABLE = false;
    static final int OFF = 0;
    static final int METER = 1;
    static final int BEAT = 2;
    static final int PROGRESS = 3;

    private GlyphLights() {
    }

    static boolean supported() {
        return false;
    }

    static void start(Context c) {
    }

    static void stop() {
    }

    static void frame(int mode, float level, double beatPhase, float progress, boolean playing) {
    }
}
