package android.os;

public final class PowerManager {
    public static final int PARTIAL_WAKE_LOCK = 1;

    public final class WakeLock {
        private boolean held;

        public void setReferenceCounted(boolean b) {
        }

        public void acquire() {
            held = true;
        }

        public void acquire(long timeout) {
            held = true;
        }

        public void release() {
            held = false;
        }

        public boolean isHeld() {
            return held;
        }
    }

    public WakeLock newWakeLock(int flags, String tag) {
        return new WakeLock();
    }

    public boolean isIgnoringBatteryOptimizations(String pkg) {
        return true;
    }

    public boolean isPowerSaveMode() {
        return false;
    }
}
