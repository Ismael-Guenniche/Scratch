package dev.scratchwheel;

import android.app.Application;

/** Carga el idioma antes de que arranque cualquier pantalla o el servicio. */
public class ScratchApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Tr.init(this);
    }
}
