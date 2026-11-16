package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;

public class ScrollView extends FrameLayout {
    private boolean fillViewport;
    private boolean bar = true;
    private boolean dragging;
    private boolean thumbDrag;
    private float lastY;
    private float downY;
    private float thumbGrab;
    private long barUntil;
    private VelocityTracker tracker;
    private final desk.Glide glide = new desk.Glide() {
        @Override
        protected float get() {
            return mScrollY;
        }

        @Override
        protected float set(float v) {
            scrollTo(mScrollX, Math.round(v));
            return mScrollY;
        }
    };

    public ScrollView(Context c) {
        super(c);
        clipsContent = true;
    }

    public ScrollView(Context c, Object attrs) {
        this(c);
    }

    public void setFillViewport(boolean b) {
        fillViewport = b;
        requestLayout();
    }

    public boolean isFillViewport() {
        return fillViewport;
    }

    public void setSmoothScrollingEnabled(boolean b) {
    }

    @Override
    public void setVerticalScrollBarEnabled(boolean b) {
        bar = b;
    }

    @Override
    protected void measureChildWithMargins(View child, int wSpec, int wUsed, int hSpec, int hUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int cw = getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin + wUsed,
                lp.width);
        int avail = Math.max(0, MeasureSpec.getSize(hSpec) - mPaddingTop - mPaddingBottom - lp.topMargin
                - lp.bottomMargin);
        child.measure(cw, MeasureSpec.makeMeasureSpec(avail, MeasureSpec.UNSPECIFIED));
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        if (!fillViewport || MeasureSpec.getMode(hSpec) == MeasureSpec.UNSPECIFIED || getChildCount() == 0) {
            return;
        }
        View child = getChildAt(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        int want = getMeasuredHeight() - mPaddingTop - mPaddingBottom - lp.topMargin - lp.bottomMargin;
        if (child.getMeasuredHeight() < want) {
            int cw = getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin,
                    lp.width);
            child.measure(cw, MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int max = range();
        if (mScrollY > max) {
            scrollTo(mScrollX, max);
        }
    }

    private int range() {
        if (getChildCount() == 0) {
            return 0;
        }
        View c = getChildAt(0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
        return Math.max(0, c.getHeight() + lp.topMargin + lp.bottomMargin
                - (getHeight() - mPaddingTop - mPaddingBottom));
    }

    @Override
    public void scrollTo(int x, int y) {
        int cy = Math.max(0, Math.min(range(), y));
        if (cy != mScrollY) {
            barUntil = System.currentTimeMillis() + 900;
            super.scrollTo(0, cy);
            postDelayed(hideBar, 950);
        }
    }

    private final Runnable hideBar = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    public void smoothScrollTo(int x, int y) {
        glide.glideTo(Math.max(0, Math.min(range(), y)));
    }

    public void smoothScrollBy(int dx, int dy) {
        smoothScrollTo(0, mScrollY + dy);
    }

    public boolean fullScroll(int dir) {
        scrollTo(0, dir == FOCUS_UP ? 0 : range());
        return true;
    }

    public int getMaxScrollAmount() {
        return getHeight() / 2;
    }

    @Override
    public boolean canScrollVertically(int dir) {
        return dir < 0 ? mScrollY > 0 : mScrollY < range();
    }

    @Override
    protected boolean onWheel(float dx, float dy) {
        if (dy == 0 || range() == 0) {
            return false;
        }
        float cur = glide.target();
        float want = Math.max(0, Math.min(range(), cur + dy));
        if (want == cur) {
            return false;
        }
        glide.glideTo(want);
        return true;
    }

    private float barW() {
        return 12 * desk.Rt.density;
    }

    private float[] thumb() {
        int h = getHeight();
        int range = range();
        float th = Math.max(32 * desk.Rt.density, (float) h * h / (h + range));
        float ty = range == 0 ? 0 : (h - th) * mScrollY / range;
        return new float[] {ty, th};
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                glide.stop();
                lastY = e.getY();
                downY = e.getY();
                dragging = false;
                thumbDrag = false;
                if (bar && range() > 0 && e.getX() >= getWidth() - barW()) {
                    float[] t = thumb();
                    thumbDrag = true;
                    thumbGrab = e.getY() >= t[0] && e.getY() <= t[0] + t[1] ? e.getY() - t[0] : t[1] / 2f;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && range() > 0 && Math.abs(e.getY() - downY) > 6 * desk.Rt.density) {
                    dragging = true;
                    lastY = e.getY();
                    return true;
                }
                break;
            default:
                break;
        }
        return dragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (getChildCount() == 0) {
            return false;
        }
        if (tracker == null) {
            tracker = VelocityTracker.obtain();
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracker.clear();
                tracker.addMovement(e);
                if (thumbDrag) {
                    moveThumb(e.getY());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                tracker.addMovement(e);
                if (thumbDrag) {
                    moveThumb(e.getY());
                    return true;
                }
                if (!dragging && range() > 0 && Math.abs(e.getY() - downY) > 6 * desk.Rt.density) {
                    dragging = true;
                    lastY = e.getY();
                }
                if (dragging) {
                    int dy = Math.round(lastY - e.getY());
                    if (dy != 0) {
                        scrollTo(0, mScrollY + dy);
                        lastY -= dy;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    float v = -tracker.getYVelocity();
                    if (Math.abs(v) > 120 * desk.Rt.density) {
                        glide.fling(v);
                    }
                }
                dragging = false;
                thumbDrag = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                thumbDrag = false;
                return true;
            default:
                return true;
        }
    }

    private void moveThumb(float y) {
        float[] t = thumb();
        float span = getHeight() - t[1];
        if (span > 0) {
            scrollTo(0, Math.round((y - thumbGrab) / span * range()));
        }
    }

    @Override
    public void onDrawForeground(Canvas c) {
        if (!bar || range() == 0 || (!thumbDrag && System.currentTimeMillis() > barUntil)) {
            return;
        }
        float[] t = thumb();
        float d = desk.Rt.density;
        c.g.setPaint(new java.awt.Color(0x80, 0x80, 0x80, 0x88));
        c.g.fill(new java.awt.geom.RoundRectangle2D.Float(getWidth() - 6 * d, mScrollY + t[0] + 2 * d, 4 * d,
                t[1] - 4 * d, 4 * d, 4 * d));
    }
}
