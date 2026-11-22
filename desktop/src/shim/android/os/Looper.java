package android.os;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** El bucle principal es el hilo de eventos de Swing; los demás, un hilo propio con cola. */
public final class Looper {
    private static final Looper MAIN = new Looper(null, null);
    private static final ThreadLocal<Looper> MINE = new ThreadLocal<Looper>();
    final ExecutorService exec;
    private volatile Thread thread;

    private Looper(ExecutorService e, Thread t) {
        exec = e;
        thread = t;
    }

    /** Crea un bucle con hilo propio (lo usa HandlerThread). */
    static Looper create(final String name, final int priority) {
        final Looper[] box = new Looper[1];
        ExecutorService ex = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        MINE.set(box[0]);
                        box[0].thread = Thread.currentThread();
                        r.run();
                    }
                }, name);
                t.setDaemon(true);
                return t;
            }
        });
        box[0] = new Looper(ex, null);
        return box[0];
    }

    public static Looper getMainLooper() {
        return MAIN;
    }

    public static Looper myLooper() {
        if (javax.swing.SwingUtilities.isEventDispatchThread()) {
            return MAIN;
        }
        return MINE.get();
    }

    public static void prepare() {
    }

    public static void loop() {
    }

    public Thread getThread() {
        return this == MAIN ? null : thread;
    }

    public boolean isCurrentThread() {
        return myLooper() == this;
    }

    void dispatch(Runnable r) {
        if (exec == null) {
            javax.swing.SwingUtilities.invokeLater(r);
        } else {
            try {
                exec.execute(r);
            } catch (java.util.concurrent.RejectedExecutionException ignored) {
            }
        }
    }

    public void quit() {
        if (exec != null) {
            exec.shutdownNow();
        }
    }

    public void quitSafely() {
        if (exec != null) {
            exec.shutdown();
        }
    }
}
