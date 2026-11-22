package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

public class LayerDrawable extends Drawable {
    private final Drawable[] layers;
    private final int[][] insets;

    public LayerDrawable(Drawable[] layers) {
        this.layers = layers;
        insets = new int[layers.length][4];
    }

    public int getNumberOfLayers() {
        return layers.length;
    }

    public Drawable getDrawable(int i) {
        return layers[i];
    }

    public void setLayerInset(int i, int l, int t, int r, int b) {
        insets[i] = new int[] {l, t, r, b};
        onBoundsChange(getBounds());
    }

    @Override
    protected void onBoundsChange(Rect b) {
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] != null) {
                layers[i].setBounds(b.left + insets[i][0], b.top + insets[i][1], b.right - insets[i][2],
                        b.bottom - insets[i][3]);
            }
        }
    }

    @Override
    public void setAlpha(int a) {
        for (Drawable d : layers) {
            if (d != null) {
                d.setAlpha(a);
            }
        }
    }

    @Override
    protected boolean onStateChange(int[] s) {
        boolean ch = false;
        for (Drawable d : layers) {
            if (d != null) {
                ch |= d.setState(s);
            }
        }
        return ch;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void draw(Canvas c) {
        for (Drawable d : layers) {
            if (d != null) {
                d.draw(c);
            }
        }
    }

    @Override
    public java.awt.Shape outlineShape() {
        return layers.length > 0 && layers[0] != null ? layers[0].outlineShape() : null;
    }
}
