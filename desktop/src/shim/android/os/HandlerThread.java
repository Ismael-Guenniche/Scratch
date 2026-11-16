package android.os;

public class HandlerThread {
    private final String name;
    private final int priority;
    private Looper looper;

    public HandlerThread(String name) {
        this(name, 0);
    }

    public HandlerThread(String name, int priority) {
        this.name = name;
        this.priority = priority;
    }

    public synchronized void start() {
        if (looper == null) {
            looper = Looper.create(name, priority);
        }
    }

    public synchronized Looper getLooper() {
        start();
        return looper;
    }

    public boolean quit() {
        if (looper != null) {
            looper.quit();
        }
        return true;
    }

    public boolean quitSafely() {
        if (looper != null) {
            looper.quitSafely();
        }
        return true;
    }

    public String getName() {
        return name;
    }
}
