package android.app;

import android.content.Intent;

public abstract class Service extends android.content.Context {
    public static final int START_STICKY = 1;
    public static final int START_NOT_STICKY = 2;
    public static final int STOP_FOREGROUND_REMOVE = 1;
    public static final int STOP_FOREGROUND_DETACH = 2;

    public void onCreate() {
    }

    public int onStartCommand(Intent i, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public void onDestroy() {
    }

    public void onTaskRemoved(Intent root) {
    }

    public abstract android.os.IBinder onBind(Intent i);

    public final void startForeground(int id, Notification n) {
    }

    public final void startForeground(int id, Notification n, int type) {
    }

    public final void stopForeground(boolean remove) {
    }

    public final void stopForeground(int flags) {
    }

    public final void stopSelf() {
    }
}
