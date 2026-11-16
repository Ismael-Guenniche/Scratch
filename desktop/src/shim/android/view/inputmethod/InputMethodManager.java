package android.view.inputmethod;

import android.view.View;

public final class InputMethodManager {
    public static final int SHOW_IMPLICIT = 1;
    public static final int SHOW_FORCED = 2;
    public static final int HIDE_IMPLICIT_ONLY = 1;
    public static final int HIDE_NOT_ALWAYS = 2;

    public boolean showSoftInput(View v, int flags) {
        return v != null && v.requestFocus();
    }

    public boolean hideSoftInputFromWindow(Object token, int flags) {
        desk.Peers.blur();
        return true;
    }

    public void toggleSoftInput(int a, int b) {
    }

    public boolean isActive() {
        return desk.Peers.anyFocused();
    }
}
