package android.content.res;

import java.util.Locale;

public final class Configuration {
    public static final int UI_MODE_NIGHT_MASK = 0x30;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_YES = 0x20;
    public static final int UI_MODE_TYPE_MASK = 0x0f;
    public static final int UI_MODE_TYPE_NORMAL = 1;
    public static final int UI_MODE_TYPE_CAR = 3;
    public static final int UI_MODE_TYPE_TELEVISION = 4;
    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;
    public static final int SCREENLAYOUT_SIZE_MASK = 0x0f;
    public static final int SCREENLAYOUT_SIZE_LARGE = 3;

    public int uiMode = UI_MODE_TYPE_NORMAL | UI_MODE_NIGHT_NO;
    public int orientation = ORIENTATION_PORTRAIT;
    public int screenWidthDp = 400;
    public int screenHeightDp = 800;
    public int smallestScreenWidthDp = 400;
    public int screenLayout = 2;
    public int densityDpi = 160;
    public float fontScale = 1f;
    public Locale locale = Locale.getDefault();

    public Configuration() {
    }

    public Configuration(Configuration o) {
        uiMode = o.uiMode;
        orientation = o.orientation;
        screenWidthDp = o.screenWidthDp;
        screenHeightDp = o.screenHeightDp;
        smallestScreenWidthDp = o.smallestScreenWidthDp;
        fontScale = o.fontScale;
        locale = o.locale;
    }

    public android.os.LocaleList getLocales() {
        return new android.os.LocaleList(locale);
    }

    public boolean isNightModeActive() {
        return (uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES;
    }
}
