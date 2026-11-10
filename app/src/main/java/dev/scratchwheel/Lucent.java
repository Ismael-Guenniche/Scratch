package dev.scratchwheel;

import android.app.Activity;
import android.app.WallpaperManager;
import android.app.WallpaperColors;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;

/**
 * Tokens de la guía Nothing OS 5.0 "Lucent": cristal claro/oscuro, acento naranja-rojo, puntos.
 * El estilo "Clásico" conserva el aspecto negro con rojo de la primera versión.
 */
public final class Lucent {
    /** Escala de esquinas: tarjetas, controles (chips, botones, menús) y filas. */
    public static final int R_L = 28;
    public static final int R_M = 20;
    public static final int R_S = 14;
    public static final int GLASS = 0;
    public static final int SOLID = 1;
    public static final int ACCENT = 2;

    public static final int NOTHING_ACCENT = 0xFFFB471F;
    public static final int NOTHING_ACCENT_DARK = 0xFFFF5A33;
    public static final int CLASSIC_RED = 0xFFD71921;
    private static final int INK_LIGHT = 0xFF161617;

    /** Se incrementa al cambiar un ajuste; las pantallas abiertas se recrean al volver. */
    public static volatile int stamp;

    private static Typeface fRegular;
    private static Typeface fMedium;
    private static Typeface fSemi;
    private static Typeface fMono;
    private static Typeface fSpace;
    private static Typeface fSpaceBold;

    public boolean lucent;
    public boolean dark;
    public boolean dotTitles;
    public int blur;
    public int glass;
    public int glassLine;
    public int ink;
    public int ink2;
    public int dotOff;
    public int ghost;
    public int solid;
    public int accent;
    public int onAccent;
    public int veil;
    public int wallPrimary;
    public int wallSecondary;
    public boolean wallKnown;
    public int myStamp;
    public Typeface regular;
    public Typeface medium;
    public Typeface semi;
    public Typeface mono;
    private final float density;

    private Lucent(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    public static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("ui", Context.MODE_PRIVATE);
    }

    public static boolean defaultLucent() {
        return "nothing".equalsIgnoreCase(Build.MANUFACTURER);
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    public static int withAlpha(int color, float a) {
        return (Math.round(a * 255) << 24) | (color & 0xFFFFFF);
    }

    /** Luminancia relativa WCAG (0..1). */
    public static double luminance(int c) {
        double r = lin(((c >> 16) & 0xFF) / 255.0);
        double g = lin(((c >> 8) & 0xFF) / 255.0);
        double b = lin((c & 0xFF) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lin(double v) {
        return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Paleta de acentos: la de DotWords más el naranja-rojo de Nothing 5 como primero. */
    public static final int[] ACCENTS = {
        0xFFFB471F, 0xFFD71921, 0xFFFF6A13, 0xFFF5C400, 0xFF1FA463,
        0xFF2E6BFF, 0xFF7C5CFF, 0xFFFF4F9A, 0
    };

    public static Lucent load(Context ctx) {
        Lucent t = new Lucent(ctx);
        SharedPreferences p = prefs(ctx);
        t.myStamp = stamp;
        t.lucent = p.getBoolean("lucent", true);
        int mode = p.getInt("mode", 0);
        int acc = p.getInt("accent", 0);
        boolean accWall = p.getBoolean("wall", false);
        int font = p.getInt("font", defaultLucent() ? 0 : 2);
        t.blur = p.getInt("blur", 2);
        loadFonts(ctx);
        if (mode == 1) {
            t.dark = false;
        } else if (mode == 2) {
            t.dark = true;
        } else {
            t.dark = (ctx.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        int wallPrimary = 0;
        int wallSecondary = 0;
        boolean wallKnown = false;
        boolean wallDark = true;
        if (Build.VERSION.SDK_INT >= 27 && (t.lucent || accWall)) {
            try {
                WallpaperColors wc = WallpaperManager.getInstance(ctx)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wc != null) {
                    wallPrimary = wc.getPrimaryColor().toArgb();
                    wallSecondary = wc.getSecondaryColor() != null
                            ? wc.getSecondaryColor().toArgb() : wallPrimary;
                    wallKnown = true;
                    if (Build.VERSION.SDK_INT >= 29) {
                        wallDark = (wc.getColorHints() & WallpaperColors.HINT_SUPPORTS_DARK_TEXT) == 0;
                    } else {
                        wallDark = luminance(wallPrimary) < 0.4;
                    }
                }
            } catch (RuntimeException ignored) {
            }
        }
        if (t.dark) {
            t.ink = 0xFFF4F4F5;
            t.ink2 = t.lucent ? 0xFFB8B9BB : 0xFF9A9A9A;
        } else {
            t.ink = t.lucent ? INK_LIGHT : 0xFF0B0B0B;
            t.ink2 = t.lucent ? 0xFF4A4B4D : 0xFF6E6E6A;
        }
        if (t.lucent) {
            // Cristal (guía Lucent). Si el fondo resta contraste, o no se conoce, la opacidad sube a 72 %.
            if (!t.dark) {
                t.glassLine = 0x8CFFFFFF;
                t.solid = 0xFFF9F9F9;
                t.glass = withAlpha(0xFFFFFF, (!wallKnown || wallDark) ? 0.72f : 0.60f);
                t.veil = 0xE6F2F2F2;
            } else {
                t.glassLine = 0x1AFFFFFF;
                t.solid = 0xFF1C1C1E;
                t.glass = withAlpha(0x000000, (wallKnown && wallDark) ? 0.55f : 0.72f);
                t.veil = 0xE6000000;
            }
        } else {
            // Sólido (neutros de DotWords).
            t.glass = t.dark ? 0xFF141414 : 0xFFFFFFFF;
            t.glassLine = t.dark ? 0xFF262626 : 0xFFDDDDD9;
            t.solid = t.dark ? 0xFF1D1D1D : 0xFFECECEA;
            t.veil = t.dark ? 0xFF000000 : 0xFFF3F3F1;
        }
        t.wallKnown = wallKnown;
        t.wallPrimary = 0xFF000000 | wallPrimary;
        t.wallSecondary = 0xFF000000 | wallSecondary;
        t.dotOff = withAlpha(t.ink, t.dark ? 0.22f : 0.45f);
        t.ghost = withAlpha(t.ink, t.dark ? 0.09f : 0.10f);
        int a = ACCENTS[Math.max(0, Math.min(acc, ACCENTS.length - 1))];
        if (a == NOTHING_ACCENT && t.dark) {
            a = NOTHING_ACCENT_DARK;
        } else if (a == 0) {
            a = t.ink;
        }
        if (accWall && wallKnown) {
            double l = luminance(wallPrimary);
            if (l > 0.08 && l < 0.75) {
                a = 0xFF000000 | wallPrimary;
            }
        }
        t.accent = a;
        t.onAccent = luminance(a) > 0.45 ? INK_LIGHT : 0xFFFFFFFF;
        t.dotTitles = font == 0;
        if (font == 2 && fRegular != null) {
            t.regular = fRegular;
            t.medium = fMedium;
            t.semi = fSemi;
            t.mono = fMono;
        } else if (fSpace != null) {
            t.regular = fSpace;
            t.medium = fSpace;
            t.semi = fSpaceBold;
            t.mono = fSpace;
        } else {
            t.regular = Typeface.SANS_SERIF;
            t.medium = Typeface.SANS_SERIF;
            t.semi = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
            t.mono = Typeface.MONOSPACE;
        }
        return t;
    }

    private static synchronized void loadFonts(Context c) {
        if (fRegular != null) {
            return;
        }
        try {
            fRegular = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-Regular.ttf");
            fMedium = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-Medium.ttf");
            fSemi = Typeface.createFromAsset(c.getAssets(), "fonts/Geist-SemiBold.ttf");
            fMono = Typeface.createFromAsset(c.getAssets(), "fonts/GeistMono-Regular.ttf");
            fSpace = Typeface.createFromAsset(c.getAssets(), "fonts/SpaceMono-Regular.ttf");
            fSpaceBold = Typeface.createFromAsset(c.getAssets(), "fonts/SpaceMono-Bold.ttf");
        } catch (RuntimeException e) {
            fRegular = null;
            fMedium = null;
            fSemi = null;
            fMono = null;
            fSpace = null;
            fSpaceBold = null;
        }
    }

    public int dp(float v) {
        return Math.round(v * density);
    }

    /** Barras transparentes, fondo de pantalla con desenfoque (Android 12+) o velo, iconos claros/oscuros. */
    @SuppressWarnings("deprecation")
    public void apply(Activity a) {
        Window w = a.getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!dark && Build.VERSION.SDK_INT >= 23) {
            f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        w.getDecorView().setSystemUiVisibility(f);
        boolean blurred = false;
        if (lucent && blur > 0 && Build.VERSION.SDK_INT >= 31) {
            WindowManager wm = (WindowManager) a.getSystemService(Context.WINDOW_SERVICE);
            if (wm != null && wm.isCrossWindowBlurEnabled()) {
                w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                w.setBackgroundBlurRadius(dp(blur == 1 ? 16 : 28));
                blurred = true;
            }
        }
        if (lucent && wallKnown) {
            // "Lee el fondo": degradado teñido con los colores de tu fondo de pantalla, casi opaco
            // para que las tarjetas siempre se lean (y algo menos opaco si hay desenfoque detrás).
            int base = dark ? 0xFF0A0A0A : 0xFFF2F2F2;
            int top = mix(base, wallPrimary, dark ? 0.32f : 0.30f);
            int bot = mix(base, wallSecondary, dark ? 0.22f : 0.20f);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[] {top, bot});
            g.setAlpha(blurred ? 222 : 255);
            w.setBackgroundDrawable(g);
        } else if (!blurred) {
            w.setBackgroundDrawable(new ColorDrawable(veil));
        } else {
            w.setBackgroundDrawable(new ColorDrawable(withAlpha(veil, 0.80f)));
        }
    }

    /** Padding base más las barras del sistema. */
    public static void insets(View root, final int l, final int t, final int r, final int b) {
        root.setPadding(l, t, r, b);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(l + in.getSystemWindowInsetLeft(), t + in.getSystemWindowInsetTop(),
                        r + in.getSystemWindowInsetRight(), b + in.getSystemWindowInsetBottom());
                return in;
            }
        });
    }

    public GradientDrawable card(float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(glass);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), glassLine);
        return g;
    }

    public void style(Button b, int kind) {
        int fill;
        int text;
        int stroke;
        switch (kind) {
            case ACCENT:
                fill = accent;
                text = onAccent;
                stroke = 0;
                break;
            case SOLID:
                fill = solid;
                text = ink;
                stroke = lucent ? 0 : glassLine;
                break;
            default:
                fill = glass;
                text = ink;
                stroke = glassLine;
                break;
        }
        boolean bold = kind == ACCENT;
        b.setAllCaps(false);
        b.setTextSize(bold ? 14 : 13);
        b.setTypeface(bold ? semi : medium);
        b.setLetterSpacing(dotTitles ? 0.04f : 0.02f);
        b.setTextColor(text);
        b.setStateListAnimator(null);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(0, 0, 0, 0);
        b.setSingleLine(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(R_M));
        if (stroke != 0) {
            g.setStroke(dp(1), stroke);
        }
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(withAlpha(text, 0.18f)), g, null));
    }

    public void styleSeek(SeekBar s) {
        s.setProgressTintList(ColorStateList.valueOf(ink));
        s.setProgressBackgroundTintList(ColorStateList.valueOf(withAlpha(ink, 0.25f)));
        s.setThumbTintList(ColorStateList.valueOf(accent));
        s.setPadding(dp(10), 0, dp(10), 0);
    }
}
