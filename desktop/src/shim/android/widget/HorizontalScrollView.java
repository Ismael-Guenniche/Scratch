package android.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;

public class HorizontalScrollView extends FrameLayout {
    private boolean fillViewport;
    private boolean dragging;
    private float lastX;
    private float downX;
    private VelocityTracker tracker;
    private final desk.Glide glide = new desk.Glide() {
        @Override
        protected float get() {
            return mScrollX;
        }

        @Override
        protected float set(float v) {
            scrollTo(Math.round(v), 0);
            return mScrollX;
        }
    };

    public HorizontalScrollView(Context c) {
        super(c);
        clipsContent = true;
    }

    public HorizontalScrollView(Context c, Object attrs) {
        this(c);
    }

    public void setFillViewport(boolean b) {
        fillViewport = b;
        requestLayout();
    }

    public void setSmoothScrollingEnabled(boolean b) {
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected void measureChildWithMargins(View child, int wSpec, int wUsed, int hSpec, int hUsed) {
        MarginLayoutParams lp = (MarginLayoutParams) child.getLayoutParams();
        int ch = getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin + hUsed,
                lp.height);
        int avail = Math.max(0, MeasureSpec.getSize(wSpec) - mPaddingLeft - mPaddingRight - lp.leftMargin
                - lp.rightMargin);
        child.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.UNSPECIFIED), ch);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        super.onMeasure(wSpec, hSpec);
        if (!fillViewport || MeasureSpec.getMode(wSpec) == MeasureSpec.UNSPECIFIED || getChildCount() == 0) {
            return;
        }
        View child = getChildAt(0);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) child.getLayoutParams();
        int want = getMeasuredWidth() - mPaddingLeft - mPaddingRight - lp.leftMargin - lp.rightMargin;
        if (child.getMeasuredWidth() < want) {
            int ch = getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin,
                    lp.height);
            child.measure(MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY), ch);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        int max = range();
        if (mScrollX > max) {
            scrollTo(max, 0);
        }
    }

    private int range() {
        if (getChildCount() == 0) {
            return 0;
        }
        View c = getChildAt(0);
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) c.getLayoutParams();
        return Math.max(0, c.getWidth() + lp.leftMargin + lp.rightMargin
                - (getWidth() - mPaddingLeft - mPaddingRight));
    }

    @Override
    public void scrollTo(int x, int y) {
        super.scrollTo(Math.max(0, Math.min(range(), x)), 0);
    }

    public void smoothScrollTo(int x, int y) {
        glide.glideTo(Math.max(0, Math.min(range(), x)));
    }

    public void smoothScrollBy(int dx, int dy) {
        smoothScrollTo(mScrollX + dx, 0);
    }

    public boolean fullScroll(int dir) {
        scrollTo(dir == 17 ? 0 : range(), 0);
        return true;
    }

    @Override
    public boolean canScrollHorizontally(int dir) {
        return dir < 0 ? mScrollX > 0 : mScrollX < range();
    }

    @Override
    protected boolean onWheel(float dx, float dy) {
        if (range() == 0) {
            return false;
        }
        float d = dx;
        if (d == 0) {
            // La rueda vertical solo mueve una tira horizontal si nada por encima se desplaza en vertical.
            for (android.view.ViewParent p = getParent(); p instanceof View; p = p.getParent()) {
                View v = (View) p;
                if (v.canScrollVertically(1) || v.canScrollVertically(-1)) {
                    return false;
                }
            }
            d = dy;
        }
        float cur = glide.target();
        float want = Math.max(0, Math.min(range(), cur + d));
        if (want == cur) {
            return false;
        }
        glide.glideTo(want);
        return true;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                glide.stop();
                lastX = e.getX();
                downX = e.getX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && range() > 0 && Math.abs(e.getX() - downX) > 6 * desk.Rt.density) {
                    dragging = true;
                    lastX = e.getX();
                    requestDisallowParent();
                    return true;
                }
                break;
            default:
                break;
        }
        return dragging;
    }

    private void requestDisallowParent() {
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (getChildCount() == 0 || range() == 0) {
            return false;
        }
        if (tracker == null) {
            tracker = VelocityTracker.obtain();
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracker.clear();
                tracker.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                tracker.addMovement(e);
                if (!dragging && Math.abs(e.getX() - downX) > 6 * desk.Rt.density) {
                    dragging = true;
                    lastX = e.getX();
                    requestDisallowParent();
                }
                if (dragging) {
                    int dx = Math.round(lastX - e.getX());
                    if (dx != 0) {
                        scrollTo(mScrollX + dx, 0);
                        lastX -= dx;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    float v = -tracker.getXVelocity();
                    if (Math.abs(v) > 120 * desk.Rt.density) {
                        glide.fling(v);
                    }
                }
                dragging = false;
                return true;
            default:
                dragging = false;
                return true;
        }
    }
}
