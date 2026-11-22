package android.app;

import android.graphics.Color;

public final class WallpaperColors {
    public static final int HINT_SUPPORTS_DARK_TEXT = 1;
    public static final int HINT_SUPPORTS_DARK_THEME = 2;
    private final int primary;

    public WallpaperColors(int primary) {
        this.primary = primary;
    }

    public Color getPrimaryColor() {
        return Color.valueOf(primary);
    }

    public Color getSecondaryColor() {
        return null;
    }

    public Color getTertiaryColor() {
        return null;
    }

    public int getColorHints() {
        return Color.luminance(primary) > 0.6f ? HINT_SUPPORTS_DARK_TEXT : 0;
    }
}
