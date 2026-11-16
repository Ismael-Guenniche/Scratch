package desk;

public final class Ids {
    private static int next = 0x10000;

    private Ids() {
    }

    public static synchronized int next() {
        return next++;
    }
}
