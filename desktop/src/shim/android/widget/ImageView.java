package android.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

public class ImageView extends android.view.View {
    public enum ScaleType {
        MATRIX, FIT_XY, FIT_START, FIT_CENTER, FIT_END, CENTER, CENTER_CROP, CENTER_INSIDE
    }

    private Drawable drawable;
    private Bitmap bitmap;
    private ScaleType scale = ScaleType.FIT_CENTER;
    private int imageAlpha = 255;
    private final android.graphics.Paint paint = new android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private final android.graphics.Rect src = new android.graphics.Rect();

    public ImageView(Context c) {
        super(c);
    }

    public ImageView(Context c, Object attrs) {
        super(c);
    }

    public void setImageBitmap(Bitmap b) {
        if (b == bitmap && drawable == null) {
            return;
        }
        boolean sized = bitmap == null || b == null || b.getWidth() != bitmap.getWidth()
                || b.getHeight() != bitmap.getHeight() || drawable != null;
        bitmap = b;
        drawable = null;
        if (sized) {
            wrapChanged();
        }
        invalidate();
    }

    public void setImageDrawable(Drawable d) {
        if (d instanceof BitmapDrawable) {
            setImageBitmap(((BitmapDrawable) d).getBitmap());
            return;
        }
        drawable = d;
        bitmap = null;
        wrapChanged();
        invalidate();
    }

    public void setImageResource(int id) {
        setImageBitmap(id == 0 ? null : getResources().bitmap(id));
    }

    public void setImageURI(android.net.Uri u) {
        java.io.File f = u == null ? null : u.toFile();
        setImageBitmap(f == null ? null : android.graphics.BitmapFactory.decodeFile(f.getPath()));
    }

    public void setImageIcon(Object icon) {
    }

    private void wrapChanged() {
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null || lp.width == android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                || lp.height == android.view.ViewGroup.LayoutParams.WRAP_CONTENT) {
            requestLayout();
        }
    }

    public Drawable getDrawable() {
        return drawable != null ? drawable : bitmap != null ? new BitmapDrawable(bitmap) : null;
    }

    public void setScaleType(ScaleType t) {
        scale = t;
        invalidate();
    }

    public ScaleType getScaleType() {
        return scale;
    }

    public void setImageAlpha(int a) {
        imageAlpha = a;
        invalidate();
    }

    public void setColorFilter(int c) {
    }

    public void setColorFilter(int c, android.graphics.PorterDuff.Mode m) {
    }

    public void setColorFilter(android.graphics.ColorFilter f) {
    }

    public void clearColorFilter() {
    }

    public void setImageTintList(android.content.res.ColorStateList l) {
    }

    public void setAdjustViewBounds(boolean b) {
    }

    public void setCropToPadding(boolean b) {
    }

    public void setMaxWidth(int w) {
    }

    public void setMaxHeight(int h) {
    }

    private int iw() {
        return bitmap != null ? bitmap.getWidth() : drawable != null ? Math.max(0, drawable.getIntrinsicWidth()) : 0;
    }

    private int ih() {
        return bitmap != null ? bitmap.getHeight() : drawable != null ? Math.max(0, drawable.getIntrinsicHeight())
                : 0;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = Math.max(iw() + mPaddingLeft + mPaddingRight, getSuggestedMinimumWidth());
        int h = Math.max(ih() + mPaddingTop + mPaddingBottom, getSuggestedMinimumHeight());
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        int vw = getWidth() - mPaddingLeft - mPaddingRight;
        int vh = getHeight() - mPaddingTop - mPaddingBottom;
        int w = iw();
        int h = ih();
        if (vw <= 0 || vh <= 0) {
            return;
        }
        if (bitmap == null) {
            if (drawable != null) {
                if (w <= 0 || h <= 0 || scale == ScaleType.FIT_XY) {
                    drawable.setBounds(mPaddingLeft, mPaddingTop, mPaddingLeft + vw, mPaddingTop + vh);
                } else {
                    float k = Math.min((float) vw / w, (float) vh / h);
                    if (scale == ScaleType.CENTER || (scale == ScaleType.CENTER_INSIDE && k > 1f)) {
                        k = 1f;
                    }
                    int dw = Math.round(w * k);
                    int dh = Math.round(h * k);
                    int l = mPaddingLeft + (vw - dw) / 2;
                    int t = mPaddingTop + (vh - dh) / 2;
                    drawable.setBounds(l, t, l + dw, t + dh);
                }
                drawable.draw(c);
            }
            return;
        }
        if (w <= 0 || h <= 0) {
            return;
        }
        paint.setAlpha(imageAlpha);
        switch (scale) {
            case FIT_XY:
                src.set(0, 0, w, h);
                dst.set(mPaddingLeft, mPaddingTop, mPaddingLeft + vw, mPaddingTop + vh);
                break;
            case CENTER_CROP: {
                float k = Math.max((float) vw / w, (float) vh / h);
                int sw = Math.max(1, Math.min(w, Math.round(vw / k)));
                int sh = Math.max(1, Math.min(h, Math.round(vh / k)));
                src.set((w - sw) / 2, (h - sh) / 2, (w - sw) / 2 + sw, (h - sh) / 2 + sh);
                dst.set(mPaddingLeft, mPaddingTop, mPaddingLeft + vw, mPaddingTop + vh);
                break;
            }
            default: {
                float k = Math.min((float) vw / w, (float) vh / h);
                if (scale == ScaleType.CENTER || (scale == ScaleType.CENTER_INSIDE && k > 1f)) {
                    k = 1f;
                }
                float dw = w * k;
                float dh = h * k;
                float l = mPaddingLeft + (vw - dw) / 2f;
                float t = mPaddingTop + (vh - dh) / 2f;
                if (scale == ScaleType.FIT_START) {
                    l = mPaddingLeft;
                    t = mPaddingTop;
                }
                src.set(0, 0, w, h);
                dst.set(l, t, l + dw, t + dh);
                break;
            }
        }
        c.drawBitmap(bitmap, src, dst, paint);
    }
}
