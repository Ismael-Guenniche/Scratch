package android.view;

public class KeyEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int KEYCODE_UNKNOWN = 0;
    public static final int KEYCODE_BACK = 4;
    public static final int KEYCODE_DPAD_UP = 19;
    public static final int KEYCODE_DPAD_DOWN = 20;
    public static final int KEYCODE_DPAD_LEFT = 21;
    public static final int KEYCODE_DPAD_RIGHT = 22;
    public static final int KEYCODE_DPAD_CENTER = 23;
    public static final int KEYCODE_VOLUME_UP = 24;
    public static final int KEYCODE_VOLUME_DOWN = 25;
    public static final int KEYCODE_SPACE = 62;
    public static final int KEYCODE_ENTER = 66;
    public static final int KEYCODE_DEL = 67;
    public static final int KEYCODE_HEADSETHOOK = 79;
    public static final int KEYCODE_MENU = 82;
    public static final int KEYCODE_SEARCH = 84;
    public static final int KEYCODE_MEDIA_PLAY_PAUSE = 85;
    public static final int KEYCODE_MEDIA_STOP = 86;
    public static final int KEYCODE_MEDIA_NEXT = 87;
    public static final int KEYCODE_MEDIA_PREVIOUS = 88;
    public static final int KEYCODE_MEDIA_REWIND = 89;
    public static final int KEYCODE_MEDIA_FAST_FORWARD = 90;
    public static final int KEYCODE_ESCAPE = 111;
    public static final int KEYCODE_MEDIA_PLAY = 126;
    public static final int KEYCODE_MEDIA_PAUSE = 127;

    private final int action;
    private final int code;
    private final int repeat;

    public KeyEvent(int action, int code) {
        this(action, code, 0);
    }

    public KeyEvent(int action, int code, int repeat) {
        this.action = action;
        this.code = code;
        this.repeat = repeat;
    }

    public int getAction() {
        return action;
    }

    public int getKeyCode() {
        return code;
    }

    public int getRepeatCount() {
        return repeat;
    }

    public boolean isCtrlPressed() {
        return false;
    }

    public boolean isShiftPressed() {
        return false;
    }
}
