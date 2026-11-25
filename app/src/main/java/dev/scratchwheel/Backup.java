package dev.scratchwheel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Copia de seguridad en un .zip (sin Android, testable): los archivos de la app (listas, favoritas, estadísticas,
 * nombres editados, letras guardadas, portadas de listas…) y los ajustes (shared_prefs). No incluye la música ni las
 * cachés. Al restaurar se sustituye lo que hay y la app tiene que reiniciarse.
 */
final class Backup {
    static final String MARK = "scratch-backup.txt";
    /** Archivos más grandes que esto no se copian (no debería haber ninguno). */
    private static final long MAX_FILE = 32L * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;

    private Backup() {
    }

    /** Escribe la copia. Devuelve cuántos archivos lleva. */
    static int write(OutputStream os, File filesDir, File prefsDir, String version) throws IOException {
        ZipOutputStream z = new ZipOutputStream(os);
        int n = 0;
        try {
            z.putNextEntry(new ZipEntry(MARK));
            z.write(("Scratch " + version + "\n").getBytes("UTF-8"));
            z.closeEntry();
            n += add(z, filesDir, "files/");
            if (prefsDir != null && prefsDir.isDirectory()) {
                n += add(z, prefsDir, "prefs/");
            }
        } finally {
            z.finish();
            z.flush();
        }
        return n;
    }

    private static int add(ZipOutputStream z, File dir, String prefix) throws IOException {
        File[] fs = dir.listFiles();
        if (fs == null) {
            return 0;
        }
        java.util.Arrays.sort(fs);
        int n = 0;
        byte[] buf = new byte[16384];
        for (File f : fs) {
            String name = f.getName();
            if (f.isDirectory()) {
                n += add(z, f, prefix + name + "/");
                continue;
            }
            if (name.endsWith(".tmp") || f.length() > MAX_FILE) {
                continue;
            }
            z.putNextEntry(new ZipEntry(prefix + name));
            InputStream in = new FileInputStream(f);
            try {
                int r;
                while ((r = in.read(buf)) > 0) {
                    z.write(buf, 0, r);
                }
            } finally {
                in.close();
            }
            z.closeEntry();
            n++;
        }
        return n;
    }

    /**
     * Restaura una copia. Primero lo descomprime todo en una carpeta temporal (si el archivo está roto no se toca
     * nada) y luego sustituye los archivos. Devuelve cuántos archivos restauró. Lanza IOException si no es una copia
     * de Scratch.
     */
    static int restore(InputStream is, File filesDir, File prefsDir) throws IOException {
        File tmp = new File(filesDir.getParentFile(), "restore.tmp");
        deleteTree(tmp);
        if (!tmp.mkdirs()) {
            throw new IOException("tmp");
        }
        boolean marked = false;
        int n = 0;
        long total = 0;
        ZipInputStream z = new ZipInputStream(is);
        try {
            ZipEntry e;
            byte[] buf = new byte[16384];
            while ((e = z.getNextEntry()) != null) {
                String name = e.getName();
                if (name.equals(MARK)) {
                    marked = true;
                    continue;
                }
                if (e.isDirectory()) {
                    continue;
                }
                if (!(name.startsWith("files/") || name.startsWith("prefs/")) || name.contains("..")
                        || name.contains("\\") || name.startsWith("/")) {
                    continue; // nada fuera de las carpetas de la app
                }
                File out = new File(tmp, name);
                if (!out.getCanonicalPath().startsWith(tmp.getCanonicalPath() + File.separator)) {
                    continue;
                }
                out.getParentFile().mkdirs();
                OutputStream o = new FileOutputStream(out);
                try {
                    int r;
                    while ((r = z.read(buf)) > 0) {
                        total += r;
                        if (total > MAX_TOTAL) {
                            throw new IOException("big");
                        }
                        o.write(buf, 0, r);
                    }
                } finally {
                    o.close();
                }
                n++;
            }
        } catch (IOException ex) {
            deleteTree(tmp);
            throw ex;
        }
        if (!marked) {
            deleteTree(tmp);
            throw new IOException("not a backup");
        }
        File tf = new File(tmp, "files");
        if (tf.isDirectory()) {
            replace(tf, filesDir);
        }
        File tp = new File(tmp, "prefs");
        if (tp.isDirectory() && prefsDir != null) {
            prefsDir.mkdirs();
            replace(tp, prefsDir);
        }
        deleteTree(tmp);
        return n;
    }

    /** Copia el contenido de {@code src} encima de {@code dst} (lo que hay en dst y no en la copia se queda). */
    private static void replace(File src, File dst) throws IOException {
        File[] fs = src.listFiles();
        if (fs == null) {
            return;
        }
        dst.mkdirs();
        for (File f : fs) {
            File d = new File(dst, f.getName());
            if (f.isDirectory()) {
                replace(f, d);
            } else {
                d.delete();
                if (!f.renameTo(d)) {
                    copy(f, d);
                }
            }
        }
    }

    private static void copy(File a, File b) throws IOException {
        InputStream in = new FileInputStream(a);
        OutputStream o = new FileOutputStream(b);
        try {
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) {
                o.write(buf, 0, r);
            }
        } finally {
            in.close();
            o.close();
        }
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] fs = f.listFiles();
        if (fs != null) {
            for (File c : fs) {
                deleteTree(c);
            }
        }
        f.delete();
    }
}
