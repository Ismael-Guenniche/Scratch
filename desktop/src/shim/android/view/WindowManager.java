package android.view;

import android.content.Context;

public class WindowManager {
    public static class LayoutParams extends ViewGroup.LayoutParams {
        public static final int FLAG_KEEP_SCREEN_ON = 0x80;
        public static final int FLAG_FULLSCREEN = 0x400;
        public static final int FLAG_LAYOUT_NO_LIMITS = 0x200;
        public static final int FLAG_SHOW_WALLPAPER = 0x100000;
        public static final int FLAG_TRANSLUCENT_STATUS = 0x04000000;
        public static final int FLAG_TRANSLUCENT_NAVIGATION = 0x08000000;
        public static final int FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS = 0x80000000;
        public static final int FLAG_DIM_BEHIND = 2;
        public static final int FLAG_BLUR_BEHIND = 4;
        public static final int FLAG_NOT_FOCUSABLE = 8;
        public static final int FLAG_NOT_TOUCH_MODAL = 0x20;
        public static final int FLAG_SECURE = 0x2000;
        public static final int FLAG_SHOW_WHEN_LOCKED = 0x80000;
        public static final int FLAG_TURN_SCREEN_ON = 0x200000;
        public static final int SOFT_INPUT_ADJUST_RESIZE = 0x10;
        public static final int SOFT_INPUT_ADJUST_PAN = 0x20;
        public static final int SOFT_INPUT_ADJUST_NOTHING = 0x30;
        public static final int SOFT_INPUT_STATE_HIDDEN = 2;
        public static final int SOFT_INPUT_STATE_ALWAYS_HIDDEN = 3;
        public static final int SOFT_INPUT_STATE_VISIBLE = 4;
        public static final int SOFT_INPUT_STATE_ALWAYS_VISIBLE = 5;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES = 1;
        public static final int LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS = 3;
        public int flags;
        public int gravity;
        public float dimAmount = 0.6f;
        public float screenBrightness = -1f;
        public int x;
        public int y;
        public int softInputMode;
        public int layoutInDisplayCutoutMode;
        public int windowAnimations;
        public int blurBehindRadius;

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public void setBlurBehindRadius(int r) {
            blurBehindRadius = r;
        }
    }

    private final Context ctx;

    public WindowManager(Context c) {
        ctx = c;
    }

    public Display getDefaultDisplay() {
        return new Display(ctx);
    }

    public boolean isCrossWindowBlurEnabled() {
        return false;
    }
}
