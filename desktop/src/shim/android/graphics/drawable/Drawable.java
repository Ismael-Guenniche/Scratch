package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;

public abstract class Drawable {
    public interface Callback {
        void invalidateDrawable(Drawable who);

        void scheduleDrawable(Drawable who, Runnable what, long when);

        void unscheduleDrawable(Drawable who, Runnable what);
    }

    private final Rect bounds = new Rect();
    private int[] state = new int[0];
    private Callback callback;
    private boolean visible = true;
    /** Solo escritorio: el ratón está encima de la vista. */
    protected boolean hovered;

    public abstract void draw(Canvas c);

    public void setAlpha(int a) {
    }

    public int getAlpha() {
        return 255;
    }

    public void setColorFilter(ColorFilter f) {
    }

    public void setColorFilter(int color, android.graphics.PorterDuff.Mode m) {
        setColorFilter(new android.graphics.PorterDuffColorFilter(color, m));
    }

    public void clearColorFilter() {
        setColorFilter(null);
    }

    public void setTint(int c) {
    }

    public void setTintList(android.content.res.ColorStateList l) {
    }

    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }

    public void setBounds(int l, int t, int r, int b) {
        if (bounds.left != l || bounds.top != t || bounds.right != r || bounds.bottom != b) {
            bounds.set(l, t, r, b);
            onBoundsChange(bounds);
        }
    }

    public void setBounds(Rect r) {
        setBounds(r.left, r.top, r.right, r.bottom);
    }

    public final Rect getBounds() {
        return bounds;
    }

    public final Rect copyBounds() {
        return new Rect(bounds);
    }

    protected void onBoundsChange(Rect b) {
    }

    public int getIntrinsicWidth() {
        return -1;
    }

    public int getIntrinsicHeight() {
        return -1;
    }

    public int getMinimumWidth() {
        return Math.max(0, getIntrinsicWidth());
    }

    public int getMinimumHeight() {
        return Math.max(0, getIntrinsicHeight());
    }

    public boolean getPadding(Rect p) {
        p.set(0, 0, 0, 0);
        return false;
    }

    public boolean isStateful() {
        return false;
    }

    public boolean setState(int[] s) {
        if (!java.util.Arrays.equals(state, s)) {
            state = s == null ? new int[0] : s;
            return onStateChange(state);
        }
        return false;
    }

    public int[] getState() {
        return state;
    }

    protected boolean onStateChange(int[] s) {
        return false;
    }

    /** Solo escritorio. Devuelve true si hay que repintar. */
    public boolean setHovered(boolean h) {
        if (hovered != h) {
            hovered = h;
            return true;
        }
        return false;
    }

    public boolean setVisible(boolean v, boolean restart) {
        boolean ch = visible != v;
        visible = v;
        return ch;
    }

    public boolean isVisible() {
        return visible;
    }

    public Drawable mutate() {
        return this;
    }

    public void jumpToCurrentState() {
    }

    public Drawable getCurrent() {
        return this;
    }

    public void setCallback(Callback cb) {
        callback = cb;
    }

    public Callback getCallback() {
        return callback;
    }

    public void invalidateSelf() {
        if (callback != null) {
            callback.invalidateDrawable(this);
        }
    }

    public boolean setLevel(int l) {
        return false;
    }

    public void setFilterBitmap(boolean b) {
    }

    public void setDither(boolean b) {
    }

    /** Forma del fondo, para recortar ondas y sombras; null = el rectángulo de los límites. */
    public java.awt.Shape outlineShape() {
        return null;
    }
}
