package android.view;

public class ViewConfiguration {
    private static final ViewConfiguration I = new ViewConfiguration();

    public static ViewConfiguration get(android.content.Context c) {
        return I;
    }

    public int getScaledTouchSlop() {
        return Math.round(6 * desk.Rt.density);
    }

    public int getScaledMinimumFlingVelocity() {
        return Math.round(50 * desk.Rt.density);
    }

    public int getScaledMaximumFlingVelocity() {
        return Math.round(8000 * desk.Rt.density);
    }

    public static int getLongPressTimeout() {
        return 450;
    }

    public static int getTapTimeout() {
        return 100;
    }

    public static int getDoubleTapTimeout() {
        return 300;
    }
}
