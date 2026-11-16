package android.content.pm;

import android.content.ComponentName;
import android.content.Intent;

public class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public static final int PERMISSION_DENIED = -1;
    public static final int COMPONENT_ENABLED_STATE_DEFAULT = 0;
    public static final int COMPONENT_ENABLED_STATE_ENABLED = 1;
    public static final int COMPONENT_ENABLED_STATE_DISABLED = 2;
    public static final int DONT_KILL_APP = 1;
    public static final int MATCH_DEFAULT_ONLY = 0x10000;
    public static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

    public static class NameNotFoundException extends Exception {
    }

    public void setComponentEnabledSetting(ComponentName n, int state, int flags) {
        desk.Prefs.get("desk_components").edit().putInt(n.getClassName(), state).apply();
        Runnable r = desk.Shell.onComponentChange;
        if (r != null) {
            r.run();
        }
    }

    public int getComponentEnabledSetting(ComponentName n) {
        return desk.Prefs.get("desk_components").getInt(n.getClassName(), COMPONENT_ENABLED_STATE_DEFAULT);
    }

    public boolean hasSystemFeature(String f) {
        return false;
    }

    public PackageInfo getPackageInfo(String pkg, int flags) throws NameNotFoundException {
        PackageInfo i = new PackageInfo();
        i.packageName = pkg;
        i.versionName = desk.Rt.versionName;
        i.versionCode = desk.Rt.versionCode;
        return i;
    }

    public ComponentName resolveActivityName(Intent i) {
        return null;
    }

    public java.util.List<ResolveInfo> queryIntentActivities(Intent i, int flags) {
        return new java.util.ArrayList<ResolveInfo>();
    }

    public ResolveInfo resolveActivity(Intent i, int flags) {
        return new ResolveInfo();
    }

    public Intent getLaunchIntentForPackage(String pkg) {
        return null;
    }

    public String getNameForUid(int uid) {
        return desk.Rt.packageName;
    }

    public String[] getPackagesForUid(int uid) {
        return new String[] {desk.Rt.packageName};
    }

    public int checkPermission(String perm, String pkg) {
        return PERMISSION_GRANTED;
    }
}
