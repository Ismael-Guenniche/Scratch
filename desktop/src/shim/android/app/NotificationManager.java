package android.app;

public class NotificationManager {
    public static final int IMPORTANCE_NONE = 0;
    public static final int IMPORTANCE_MIN = 1;
    public static final int IMPORTANCE_LOW = 2;
    public static final int IMPORTANCE_DEFAULT = 3;
    public static final int IMPORTANCE_HIGH = 4;

    public void createNotificationChannel(NotificationChannel c) {
    }

    public boolean areNotificationsEnabled() {
        return true;
    }

    public void notify(int id, Notification n) {
    }

    public void cancel(int id) {
    }

    public void cancelAll() {
    }
}
