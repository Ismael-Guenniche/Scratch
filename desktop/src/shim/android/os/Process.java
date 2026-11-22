package android.os;

public final class Process {
    public static final int THREAD_PRIORITY_DEFAULT = 0;
    public static final int THREAD_PRIORITY_LOWEST = 19;
    public static final int THREAD_PRIORITY_BACKGROUND = 10;
    public static final int THREAD_PRIORITY_FOREGROUND = -2;
    public static final int THREAD_PRIORITY_DISPLAY = -4;
    public static final int THREAD_PRIORITY_URGENT_DISPLAY = -8;
    public static final int THREAD_PRIORITY_AUDIO = -16;
    public static final int THREAD_PRIORITY_URGENT_AUDIO = -19;
    public static final int THREAD_PRIORITY_MORE_FAVORABLE = -1;
    public static final int THREAD_PRIORITY_LESS_FAVORABLE = 1;
    public static final int SYSTEM_UID = 1000;

    private Process() {
    }

    public static void setThreadPriority(int p) {
        try {
            int j = p <= THREAD_PRIORITY_AUDIO ? Thread.MAX_PRIORITY
                    : p < 0 ? Thread.NORM_PRIORITY + 2
                    : p >= THREAD_PRIORITY_BACKGROUND ? Thread.MIN_PRIORITY + 1 : Thread.NORM_PRIORITY;
            Thread.currentThread().setPriority(j);
        } catch (RuntimeException ignored) {
        }
    }

    public static void setThreadPriority(int tid, int p) {
    }

    public static int myPid() {
        return (int) ProcessHandle.current().pid();
    }

    public static int myTid() {
        return (int) Thread.currentThread().getId();
    }

    public static int myUid() {
        return 10001;
    }

    public static void killProcess(int pid) {
        System.exit(0);
    }
}
