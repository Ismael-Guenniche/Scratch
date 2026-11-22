package android.view;

import android.content.Context;
import android.util.DisplayMetrics;

public final class Display {
    public static final int DEFAULT_DISPLAY = 0;
    private final Context ctx;

    public Display(Context c) {
        ctx = c;
    }

    public void getMetrics(DisplayMetrics out) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        out.density = dm.density;
        out.scaledDensity = dm.scaledDensity;
        out.densityDpi = dm.densityDpi;
        out.widthPixels = dm.widthPixels;
        out.heightPixels = dm.heightPixels;
        out.xdpi = dm.xdpi;
        out.ydpi = dm.ydpi;
    }

    public void getRealMetrics(DisplayMetrics out) {
        getMetrics(out);
    }

    public void getSize(android.graphics.Point p) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        p.set(dm.widthPixels, dm.heightPixels);
    }

    public void getRealSize(android.graphics.Point p) {
        getSize(p);
    }

    public int getWidth() {
        return ctx.getResources().getDisplayMetrics().widthPixels;
    }

    public int getHeight() {
        return ctx.getResources().getDisplayMetrics().heightPixels;
    }

    public int getRotation() {
        return 0;
    }

    public float getRefreshRate() {
        return 60f;
    }

    public int getDisplayId() {
        return 0;
    }
}
