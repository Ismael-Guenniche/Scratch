package android.os;

public final class SystemClock {
    private static final long T0 = System.nanoTime();

    private SystemClock() {
    }

    public static long uptimeMillis() {
        return (System.nanoTime() - T0) / 1000000L + 100000L;
    }

    public static long elapsedRealtime() {
        return uptimeMillis();
    }

    public static long elapsedRealtimeNanos() {
        return System.nanoTime() - T0 + 100000000000L;
    }

    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
