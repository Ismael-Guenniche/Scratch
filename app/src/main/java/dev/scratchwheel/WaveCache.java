package dev.scratchwheel;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Caché en disco de la forma de onda de cada canción (un archivo pequeño por canción). Sin dependencias de
 * Android. Se lee y se escribe desde el hilo que decodifica, nunca desde el de audio.
 */
public final class WaveCache {
    private static final int MAGIC = 0x57415631; // "WAV1"

    private WaveCache() {
    }

    private static File file(File dir, String key) {
        // dos hashes distintos de la clave: nombre corto y sin caracteres raros
        long h = 1125899906842597L;
        for (int i = 0; i < key.length(); i++) {
            h = 31 * h + key.charAt(i);
        }
        return new File(dir, Long.toHexString(h) + "_" + Integer.toHexString(key.hashCode()) + ".wave");
    }

    /** Devuelve los picos guardados, o null si no hay, están corruptos o son de otra versión del archivo. */
    public static float[] get(File dir, String key, int frames, int n) {
        File f = file(dir, key);
        if (!f.isFile() || f.length() != 16 + 4L * n) {
            return null;
        }
        try {
            DataInputStream in = new DataInputStream(new FileInputStream(f));
            try {
                if (in.readInt() != MAGIC || in.readInt() != frames || in.readInt() != n
                        || in.readInt() != key.hashCode()) {
                    return null;
                }
                float[] p = new float[n];
                for (int i = 0; i < n; i++) {
                    float v = in.readFloat();
                    if (!(v >= 0f && v <= 1f)) {
                        return null;
                    }
                    p[i] = v;
                }
                return p;
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
        }
    }

    public static void put(File dir, String key, int frames, float[] peaks) {
        try {
            dir.mkdirs();
            File f = file(dir, key);
            File tmp = new File(dir, f.getName() + ".tmp");
            DataOutputStream out = new DataOutputStream(new FileOutputStream(tmp));
            try {
                out.writeInt(MAGIC);
                out.writeInt(frames);
                out.writeInt(peaks.length);
                out.writeInt(key.hashCode());
                for (float v : peaks) {
                    out.writeFloat(v);
                }
            } finally {
                out.close();
            }
            if (!tmp.renameTo(f)) {
                tmp.delete();
            }
        } catch (IOException ignored) {
            // la caché es opcional
        } catch (RuntimeException ignored) {
        }
    }
}
