package desk;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Formatos sin decodificador propio (M4A, OGG, Opus, WMA…): se usa ffmpeg si está instalado. */
public final class Ffmpeg {
    private static String exe;
    private static boolean looked;

    private Ffmpeg() {
    }

    public static synchronized String find() {
        if (looked) {
            return exe;
        }
        looked = true;
        java.util.ArrayList<String> tries = new java.util.ArrayList<String>();
        String prop = System.getProperty("scratch.ffmpeg");
        if (prop != null) {
            tries.add(prop);
        }
        String name = Rt.WINDOWS ? "ffmpeg.exe" : "ffmpeg";
        String app = System.getProperty("jpackage.app-path");
        if (app != null) {
            File dir = new File(app).getParentFile();
            if (dir != null) {
                tries.add(new File(dir, name).getPath());
            }
        }
        Rt.initDirs();
        tries.add(new File(Rt.filesDir.getParentFile(), name).getPath());
        String path = System.getenv("PATH");
        if (path != null) {
            for (String d : path.split(File.pathSeparator)) {
                if (d.length() > 0) {
                    tries.add(new File(d, name).getPath());
                }
            }
        }
        for (String t : tries) {
            if (new File(t).isFile()) {
                exe = t;
                break;
            }
        }
        return exe;
    }

    public interface Sink {
        void samples(short[] interleaved, int n) throws IOException;

        boolean cancelled();
    }

    /** Decodifica a 16 bits estéreo a la frecuencia pedida. Lanza IOException si no hay ffmpeg o falla. */
    public static void decode(File f, int rate, Sink sink) throws IOException {
        String bin = find();
        if (bin == null) {
            throw new IOException("ffmpeg no encontrado");
        }
        ProcessBuilder pb = new ProcessBuilder(bin, "-v", "error", "-nostdin", "-i", f.getAbsolutePath(), "-vn",
                "-f", "s16le", "-ac", "2", "-ar", String.valueOf(rate), "-");
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process p = pb.start();
        try {
            p.getOutputStream().close();
            InputStream in = p.getInputStream();
            byte[] b = new byte[1 << 16];
            short[] s = new short[1 << 15];
            int have = 0;
            long total = 0;
            while (true) {
                if (sink.cancelled()) {
                    break;
                }
                int k = in.read(b, have, b.length - have);
                if (k < 0) {
                    break;
                }
                have += k;
                int n = (have / 4) * 2;
                for (int i = 0; i < n; i++) {
                    s[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
                }
                if (n > 0) {
                    sink.samples(s, n);
                    total += n;
                }
                int used = n * 2;
                System.arraycopy(b, used, b, 0, have - used);
                have -= used;
            }
            if (total == 0 && !sink.cancelled()) {
                throw new IOException("ffmpeg no pudo decodificar");
            }
        } finally {
            p.destroy();
        }
    }
}
