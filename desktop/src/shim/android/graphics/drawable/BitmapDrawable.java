package android.graphics.drawable;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;

public class BitmapDrawable extends Drawable {
    private final Bitmap bitmap;
    private int alpha = 255;

    public BitmapDrawable(android.content.res.Resources r, Bitmap b) {
        bitmap = b;
    }

    public BitmapDrawable(Bitmap b) {
        bitmap = b;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public void setAlpha(int a) {
        alpha = a;
    }

    @Override
    public int getIntrinsicWidth() {
        return bitmap == null ? -1 : bitmap.getWidth();
    }

    @Override
    public int getIntrinsicHeight() {
        return bitmap == null ? -1 : bitmap.getHeight();
    }

    @Override
    public void draw(Canvas c) {
        if (bitmap == null) {
            return;
        }
        android.graphics.Paint p = null;
        if (alpha < 255) {
            p = new android.graphics.Paint();
            p.setAlpha(alpha);
        }
        c.drawBitmap(bitmap, null, new RectF(getBounds()), p);
    }
}
