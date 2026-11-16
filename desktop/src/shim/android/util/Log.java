package android.util;

public final class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;

    private Log() {
    }

    private static int out(String lvl, String tag, String msg, Throwable t) {
        if (desk.Rt.DEBUG) {
            System.err.println(lvl + "/" + tag + ": " + msg);
            if (t != null) {
                t.printStackTrace();
            }
        }
        return 0;
    }

    public static int v(String tag, String msg) {
        return 0;
    }

    public static int d(String tag, String msg) {
        return out("D", tag, msg, null);
    }

    public static int i(String tag, String msg) {
        return out("I", tag, msg, null);
    }

    public static int w(String tag, String msg) {
        return out("W", tag, msg, null);
    }

    public static int w(String tag, String msg, Throwable t) {
        return out("W", tag, msg, t);
    }

    public static int w(String tag, Throwable t) {
        return out("W", tag, "", t);
    }

    public static int e(String tag, String msg) {
        return out("E", tag, msg, null);
    }

    public static int e(String tag, String msg, Throwable t) {
        return out("E", tag, msg, t);
    }

    public static boolean isLoggable(String tag, int level) {
        return desk.Rt.DEBUG;
    }

    public static String getStackTraceString(Throwable t) {
        java.io.StringWriter w = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(w));
        return w.toString();
    }
}
