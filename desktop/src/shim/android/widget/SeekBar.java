package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.view.MotionEvent;

public class SeekBar extends android.view.View {
    public interface OnSeekBarChangeListener {
        void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser);

        void onStartTrackingTouch(SeekBar seekBar);

        void onStopTrackingTouch(SeekBar seekBar);
    }

    private int max = 100;
    private int min;
    private int progress;
    private OnSeekBarChangeListener listener;
    private int progressColor = 0xFFFFFFFF;
    private int backColor = 0x40FFFFFF;
    private int thumbColor = 0xFFFFFFFF;
    private boolean tracking;

    public SeekBar(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setPadding(Math.round(16 * d), 0, Math.round(16 * d), 0);
        setClickable(true);
    }

    public SeekBar(Context c, Object attrs) {
        this(c);
    }

    public void setMax(int m) {
        max = Math.max(min, m);
        if (progress > max) {
            progress = max;
        }
        invalidate();
    }

    public int getMax() {
        return max;
    }

    public void setMin(int m) {
        min = m;
        if (progress < min) {
            progress = min;
        }
        invalidate();
    }

    public int getMin() {
        return min;
    }

    public void setProgress(int p) {
        set(p, false);
    }

    public void setProgress(int p, boolean animate) {
        set(p, false);
    }

    public int getProgress() {
        return progress;
    }

    public void incrementProgressBy(int d) {
        set(progress + d, false);
    }

    private void set(int p, boolean fromUser) {
        p = Math.max(min, Math.min(max, p));
        if (p != progress) {
            progress = p;
            invalidate();
            if (listener != null) {
                listener.onProgressChanged(this, p, fromUser);
            }
        }
    }

    public void setOnSeekBarChangeListener(OnSeekBarChangeListener l) {
        listener = l;
    }

    public void setProgressTintList(ColorStateList l) {
        progressColor = l.getDefaultColor();
        invalidate();
    }

    public void setProgressBackgroundTintList(ColorStateList l) {
        backColor = l.getDefaultColor();
        invalidate();
    }

    public void setThumbTintList(ColorStateList l) {
        thumbColor = l.getDefaultColor();
        invalidate();
    }

    public void setSecondaryProgress(int p) {
    }

    public void setThumb(android.graphics.drawable.Drawable d) {
    }

    public void setProgressDrawable(android.graphics.drawable.Drawable d) {
    }

    public void setSplitTrack(boolean b) {
    }

    public void setKeyProgressIncrement(int i) {
    }

    public void setThumbOffset(int o) {
    }

    public void setIndeterminate(boolean b) {
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        float d = getResources().getDisplayMetrics().density;
        int h = Math.max(Math.round(24 * d) + mPaddingTop + mPaddingBottom, getSuggestedMinimumHeight());
        setMeasuredDimension(getDefaultSize(Math.round(120 * d), wSpec), resolveSize(h, hSpec));
    }

    private void fromX(float x) {
        float l = mPaddingLeft;
        float r = getWidth() - mPaddingRight;
        float f = r <= l ? 0 : Math.max(0f, Math.min(1f, (x - l) / (r - l)));
        set(min + Math.round(f * (max - min)), true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (listener != null) {
                    listener.onStartTrackingTouch(this);
                }
                fromX(e.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking) {
                    fromX(e.getX());
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (tracking) {
                    if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                        fromX(e.getX());
                    }
                    tracking = false;
                    if (listener != null) {
                        listener.onStopTrackingTouch(this);
                    }
                    invalidate();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density;
        float l = mPaddingLeft;
        float r = getWidth() - mPaddingRight;
        float cy = mPaddingTop + (getHeight() - mPaddingTop - mPaddingBottom) / 2f;
        float f = max == min ? 0 : (progress - min) / (float) (max - min);
        float x = l + (r - l) * f;
        float th = 3 * d;
        java.awt.Graphics2D g = c.g;
        float a = isEnabled() ? 1f : 0.4f;
        g.setPaint(alpha(backColor, a));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(l, cy - th / 2, r - l, th, th, th));
        g.setPaint(alpha(progressColor, a));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(l, cy - th / 2, x - l, th, th, th));
        float rad = (tracking ? 8.5f : 7f) * d;
        g.setPaint(alpha(thumbColor, a));
        g.fill(new java.awt.geom.Ellipse2D.Float(x - rad, cy - rad, 2 * rad, 2 * rad));
    }

    private static java.awt.Color alpha(int c, float a) {
        return new java.awt.Color((Math.round((c >>> 24) * a) << 24) | (c & 0xFFFFFF), true);
    }
}
