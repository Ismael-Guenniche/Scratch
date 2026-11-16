package android.view;

public final class WindowInsets {
    public static final WindowInsets ZERO = new WindowInsets();

    public static final class Type {
        public static int systemBars() {
            return 7;
        }

        public static int statusBars() {
            return 1;
        }

        public static int navigationBars() {
            return 2;
        }

        public static int ime() {
            return 8;
        }

        public static int displayCutout() {
            return 128;
        }
    }

    public int getSystemWindowInsetLeft() {
        return 0;
    }

    public int getSystemWindowInsetTop() {
        return 0;
    }

    public int getSystemWindowInsetRight() {
        return 0;
    }

    public int getSystemWindowInsetBottom() {
        return 0;
    }

    public int getStableInsetTop() {
        return 0;
    }

    public int getStableInsetBottom() {
        return 0;
    }

    public android.graphics.Insets getInsets(int mask) {
        return android.graphics.Insets.NONE;
    }

    public boolean isVisible(int mask) {
        return false;
    }

    public WindowInsets consumeSystemWindowInsets() {
        return this;
    }
}
