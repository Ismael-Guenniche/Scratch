package android.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import java.awt.image.BufferedImage;

public class Window {
    public static final int FEATURE_NO_TITLE = 1;
    public static final int FEATURE_ACTION_BAR = 8;
    public static final int FEATURE_CONTENT_TRANSITIONS = 12;
    public static final int FEATURE_ACTIVITY_TRANSITIONS = 13;

    /** Raíz de la ventana. El fondo (degradado + rejilla de puntos) se guarda en una imagen. */
    private final class Decor extends FrameLayout {
        private BufferedImage cache;
        private double cacheScale;
        private boolean dirty = true;

        Decor(Context c) {
            super(c);
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            dirty = true;
            super.invalidateDrawable(who);
        }

        @Override
        public void setBackground(Drawable d) {
            dirty = true;
            super.setBackground(d);
        }

        @Override
        public void draw(Canvas c) {
            Drawable bg = getBackground();
            int w = getWidth();
            int h = getHeight();
            if (bg != null && w > 0 && h > 0) {
                double sc = Math.max(1.0, c.g.getTransform().getScaleX());
                int iw = (int) Math.ceil(w * sc);
                int ih = (int) Math.ceil(h * sc);
                if (cache == null || dirty || cache.getWidth() != iw || cache.getHeight() != ih
                        || cacheScale != sc) {
                    if (cache == null || cache.getWidth() != iw || cache.getHeight() != ih) {
                        cache = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB_PRE);
                    }
                    java.awt.Graphics2D g = cache.createGraphics();
                    g.setComposite(java.awt.AlphaComposite.Clear);
                    g.fillRect(0, 0, iw, ih);
                    g.setComposite(java.awt.AlphaComposite.SrcOver);
                    g.scale(sc, sc);
                    Canvas cc = new Canvas(g, w, h);
                    bg.setBounds(0, 0, w, h);
                    bg.draw(cc);
                    g.dispose();
                    cacheScale = sc;
                    dirty = false;
                }
                java.awt.geom.AffineTransform keep = c.g.getTransform();
                c.g.scale(1.0 / sc, 1.0 / sc);
                c.g.drawImage(cache, 0, 0, null);
                c.g.setTransform(keep);
            }
            dispatchDraw(c);
        }
    }

    private final Context ctx;
    private final Decor decor;
    private final FrameLayout content;
    private final WindowManager.LayoutParams attrs = new WindowManager.LayoutParams();
    private Callback callback;
    public int layoutW = ViewGroup.LayoutParams.MATCH_PARENT;
    public int layoutH = ViewGroup.LayoutParams.MATCH_PARENT;
    /** Capa en la que se muestra (la pone la actividad o el diálogo). */
    public desk.Layer layer;

    public interface Callback {
    }

    public Window(Context c) {
        ctx = c;
        decor = new Decor(c);
        content = new FrameLayout(c);
        content.setId(android.R.id.content);
        decor.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public Context getContext() {
        return ctx;
    }

    public View getDecorView() {
        return decor;
    }

    public View peekDecorView() {
        return decor;
    }

    public FrameLayout contentParent() {
        return content;
    }

    public void setContentView(View v) {
        setContentView(v, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        content.removeAllViews();
        if (v != null) {
            content.addView(v, lp);
        }
    }

    public <T extends View> T findViewById(int id) {
        return decor.findViewById(id);
    }

    public View getCurrentFocus() {
        return decor.findFocus();
    }

    public void setBackgroundDrawable(Drawable d) {
        decor.setBackground(d);
        if (layer != null && layer.pane != null && layer.kind == desk.Layer.ACTIVITY) {
            layer.pane.base = baseColor(layer.pane.base);
            layer.pane.repaint();
        }
    }

    /** Color opaco que va debajo del fondo de la ventana (hace de "fondo de pantalla"). */
    public int baseColor(int otherwise) {
        Drawable d = decor.getBackground();
        if (d instanceof android.graphics.drawable.LayerDrawable
                && ((android.graphics.drawable.LayerDrawable) d).getNumberOfLayers() > 0) {
            d = ((android.graphics.drawable.LayerDrawable) d).getDrawable(0);
        }
        if (d instanceof ColorDrawable) {
            int c = ((ColorDrawable) d).getColor();
            if ((c >>> 24) > 0x40) {
                return 0xFF000000 | c;
            }
        }
        return otherwise;
    }

    public void setBackgroundDrawableResource(int id) {
    }

    public void setBackgroundBlurRadius(int r) {
    }

    private final WindowInsetsController insets = new WindowInsetsController();

    public WindowInsetsController getInsetsController() {
        return insets;
    }

    public void setStatusBarColor(int c) {
    }

    public void setNavigationBarColor(int c) {
    }

    public void setNavigationBarDividerColor(int c) {
    }

    public void setNavigationBarContrastEnforced(boolean b) {
    }

    public void setStatusBarContrastEnforced(boolean b) {
    }

    public void setDecorFitsSystemWindows(boolean b) {
    }

    public void addFlags(int f) {
        attrs.flags |= f;
    }

    public void clearFlags(int f) {
        attrs.flags &= ~f;
    }

    public void setFlags(int f, int mask) {
        attrs.flags = (attrs.flags & ~mask) | (f & mask);
    }

    public void setDimAmount(float a) {
        attrs.dimAmount = a;
        if (layer != null) {
            layer.dim = a;
        }
    }

    public void setSoftInputMode(int m) {
        attrs.softInputMode = m;
    }

    public void setLayout(int w, int h) {
        layoutW = w;
        layoutH = h;
        attrs.width = w;
        attrs.height = h;
        if (layer != null) {
            layer.requestLayout();
        }
    }

    public void setGravity(int g) {
        attrs.gravity = g;
    }

    public WindowManager.LayoutParams getAttributes() {
        return attrs;
    }

    public void setAttributes(WindowManager.LayoutParams a) {
        layoutW = a.width;
        layoutH = a.height;
        if (a != attrs) {
            attrs.width = a.width;
            attrs.height = a.height;
            attrs.gravity = a.gravity;
            attrs.dimAmount = a.dimAmount;
            attrs.flags = a.flags;
        }
        if (layer != null) {
            layer.dim = layer.kind == desk.Layer.DIALOG ? attrs.dimAmount : 0;
            layer.requestLayout();
        }
    }

    public boolean requestFeature(int f) {
        return true;
    }

    public void setWindowAnimations(int id) {
    }

    public void setCallback(Callback c) {
        callback = c;
    }

    public Callback getCallback() {
        return callback;
    }

    public WindowManager getWindowManager() {
        return new WindowManager(ctx);
    }

    public void setSustainedPerformanceMode(boolean b) {
    }

    public void setColorMode(int m) {
    }

    public void setElevation(float e) {
    }

    public void setClipToOutline(boolean b) {
    }

    public void setType(int t) {
    }
}
