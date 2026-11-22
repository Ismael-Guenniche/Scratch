package desk;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

/** Entorno de escritorio: carpetas de datos, densidad de pantalla y reloj compartido. */
public final class Rt {
    public static volatile float density = 1f;
    public static String appName = "Scratch";
    public static String packageName = "dev.scratchwheel";
    public static String versionName = "0";
    public static int versionCode;
    public static File filesDir;
    public static File cacheDir;
    public static File musicDir;
    public static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    public static final boolean DEBUG = System.getenv("SCRATCH_DEBUG") != null;

    public static final ScheduledExecutorService TIMER = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "desk-timer");
                    t.setDaemon(true);
                    return t;
                }
            });

    private Rt() {
    }

    public static synchronized void initDirs() {
        if (filesDir != null) {
            return;
        }
        String home = System.getProperty("user.home", ".");
        String over = System.getProperty("scratch.data", System.getenv("SCRATCH_DATA"));
        File data;
        File cache;
        if (over != null && over.length() > 0) {
            data = new File(over, "files");
            cache = new File(over, "cache");
        } else if (WINDOWS) {
            String roaming = System.getenv("APPDATA");
            String local = System.getenv("LOCALAPPDATA");
            data = new File(roaming != null ? roaming : home, appName + File.separator + "files");
            cache = new File(local != null ? local : (roaming != null ? roaming : home),
                    appName + File.separator + "cache");
        } else {
            data = new File(home, ".scratch/files");
            cache = new File(home, ".scratch/cache");
        }
        data.mkdirs();
        cache.mkdirs();
        filesDir = data;
        cacheDir = cache;
        musicDir = new File(home, "Music" + File.separator + appName);
    }

    public static boolean isMain() {
        return javax.swing.SwingUtilities.isEventDispatchThread();
    }

    public static void log(String s) {
        if (DEBUG) {
            System.err.println(s);
        }
    }

    public static void log(Throwable t) {
        if (DEBUG) {
            t.printStackTrace();
        }
    }
}
