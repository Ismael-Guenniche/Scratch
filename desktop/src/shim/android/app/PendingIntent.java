package android.app;

public final class PendingIntent {
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final int FLAG_UPDATE_CURRENT = 0x08000000;

    public static PendingIntent getActivity(android.content.Context c, int req, android.content.Intent i, int f) {
        return new PendingIntent();
    }

    public static PendingIntent getService(android.content.Context c, int req, android.content.Intent i, int f) {
        return new PendingIntent();
    }

    public static PendingIntent getBroadcast(android.content.Context c, int req, android.content.Intent i, int f) {
        return new PendingIntent();
    }
}
