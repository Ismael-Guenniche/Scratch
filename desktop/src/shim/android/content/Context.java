package android.content;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.net.Uri;
import java.io.File;

/** Contexto único del proceso; las actividades redefinen los recursos (tamaño de su panel). */
public class Context {
    public static final int MODE_PRIVATE = 0;
    public static final int BIND_AUTO_CREATE = 1;
    public static final int RECEIVER_NOT_EXPORTED = 4;
    public static final int RECEIVER_EXPORTED = 2;
    public static final String AUDIO_SERVICE = "audio";
    public static final String POWER_SERVICE = "power";
    public static final String WINDOW_SERVICE = "window";
    public static final String CLIPBOARD_SERVICE = "clipboard";
    public static final String INPUT_METHOD_SERVICE = "input_method";
    public static final String NOTIFICATION_SERVICE = "notification";
    public static final String VIBRATOR_SERVICE = "vibrator";
    public static final String WALLPAPER_SERVICE = "wallpaper";
    public static final String UI_MODE_SERVICE = "uimode";
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public static final String MEDIA_SESSION_SERVICE = "media_session";

    private static final ContentResolver RESOLVER = new ContentResolver();
    private static final android.content.pm.PackageManager PM = new android.content.pm.PackageManager();
    private static final android.media.AudioManager AUDIO = new android.media.AudioManager();
    private static final android.os.PowerManager POWER = new android.os.PowerManager();
    private static final ClipboardManager CLIP = new ClipboardManager();
    private static final android.view.inputmethod.InputMethodManager IMM =
            new android.view.inputmethod.InputMethodManager();
    private static final android.app.NotificationManager NOTIF = new android.app.NotificationManager();

    public Context getApplicationContext() {
        return desk.Shell.application();
    }

    public Context getBaseContext() {
        return this;
    }

    public String getPackageName() {
        return desk.Rt.packageName;
    }

    public String getOpPackageName() {
        return desk.Rt.packageName;
    }

    public android.content.pm.PackageManager getPackageManager() {
        return PM;
    }

    public android.content.pm.ApplicationInfo getApplicationInfo() {
        android.content.pm.ApplicationInfo i = new android.content.pm.ApplicationInfo();
        i.packageName = desk.Rt.packageName;
        return i;
    }

    public SharedPreferences getSharedPreferences(String name, int mode) {
        return desk.Prefs.get(name);
    }

    public File getFilesDir() {
        desk.Rt.initDirs();
        return desk.Rt.filesDir;
    }

    public File getCacheDir() {
        desk.Rt.initDirs();
        return desk.Rt.cacheDir;
    }

    public File getExternalCacheDir() {
        return getCacheDir();
    }

    public File getNoBackupFilesDir() {
        return getFilesDir();
    }

    public File getExternalFilesDir(String type) {
        desk.Rt.initDirs();
        if (android.os.Environment.DIRECTORY_MUSIC.equals(type)) {
            return desk.Rt.musicDir;
        }
        File f = type == null ? new File(desk.Rt.filesDir, "external")
                : new File(new File(System.getProperty("user.home", ".")), type + File.separator + desk.Rt.appName);
        f.mkdirs();
        return f;
    }

    public File getDir(String name, int mode) {
        File f = new File(getFilesDir().getParentFile(), "app_" + name);
        f.mkdirs();
        return f;
    }

    public File getFileStreamPath(String name) {
        return new File(getFilesDir(), name);
    }

    public java.io.FileInputStream openFileInput(String name) throws java.io.FileNotFoundException {
        return new java.io.FileInputStream(getFileStreamPath(name));
    }

    public java.io.FileOutputStream openFileOutput(String name, int mode) throws java.io.FileNotFoundException {
        return new java.io.FileOutputStream(getFileStreamPath(name));
    }

    public boolean deleteFile(String name) {
        return getFileStreamPath(name).delete();
    }

    public Resources getResources() {
        return desk.Shell.appResources();
    }

    public AssetManager getAssets() {
        return new AssetManager();
    }

    public ContentResolver getContentResolver() {
        return RESOLVER;
    }

    public android.os.Looper getMainLooper() {
        return android.os.Looper.getMainLooper();
    }

    public Resources.Theme getTheme() {
        return new Resources.Theme();
    }

    public void setTheme(int id) {
    }

    public Object getSystemService(String name) {
        if (AUDIO_SERVICE.equals(name)) {
            return AUDIO;
        } else if (POWER_SERVICE.equals(name)) {
            return POWER;
        } else if (WINDOW_SERVICE.equals(name)) {
            return new android.view.WindowManager(this);
        } else if (CLIPBOARD_SERVICE.equals(name)) {
            return CLIP;
        } else if (INPUT_METHOD_SERVICE.equals(name)) {
            return IMM;
        } else if (NOTIFICATION_SERVICE.equals(name)) {
            return NOTIF;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getSystemService(Class<T> c) {
        if (c == android.media.AudioManager.class) {
            return (T) AUDIO;
        } else if (c == android.os.PowerManager.class) {
            return (T) POWER;
        } else if (c == ClipboardManager.class) {
            return (T) CLIP;
        } else if (c == android.view.inputmethod.InputMethodManager.class) {
            return (T) IMM;
        } else if (c == android.app.NotificationManager.class) {
            return (T) NOTIF;
        } else if (c == android.view.WindowManager.class) {
            return (T) new android.view.WindowManager(this);
        }
        return null;
    }

    public int checkSelfPermission(String p) {
        return android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public int checkCallingOrSelfPermission(String p) {
        return android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public void startActivity(Intent i) {
        desk.Shell.start(this, i, -1);
    }

    public void startActivity(Intent i, android.os.Bundle options) {
        startActivity(i);
    }

    public ComponentName startService(Intent i) {
        return null;
    }

    public ComponentName startForegroundService(Intent i) {
        return null;
    }

    public boolean stopService(Intent i) {
        return false;
    }

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f) {
        return null;
    }

    public Intent registerReceiver(BroadcastReceiver r, IntentFilter f, int flags) {
        return null;
    }

    public void unregisterReceiver(BroadcastReceiver r) {
    }

    public void sendBroadcast(Intent i) {
    }

    public String getString(int id) {
        return "";
    }

    public int getColor(int id) {
        return 0xFF000000;
    }

    public void grantUriPermission(String pkg, Uri u, int flags) {
    }

    public void revokeUriPermission(Uri u, int flags) {
    }
}
