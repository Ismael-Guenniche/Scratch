package android.view;

import android.content.Context;
import android.graphics.Canvas;
import java.util.ArrayList;

public abstract class ViewGroup extends View implements ViewParent {
    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000;
    public static final int FOCUS_AFTER_DESCENDANTS = 0x40000;
    public static final int FOCUS_BLOCK_DESCENDANTS = 0x60000;

    public static class LayoutParams {
        public static final int FILL_PARENT = -1;
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;
        public int width;
        public int height;

        public LayoutParams(int w, int h) {
            width = w;
            height = h;
        }

        public LayoutParams(LayoutParams o) {
            width = o.width;
            height = o.height;
        }
    }

    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;

        public MarginLayoutParams(int w, int h) {
            super(w, h);
        }

        public MarginLayoutParams(LayoutParams o) {
            super(o);
            if (o instanceof MarginLayoutParams) {
                MarginLayoutParams m = (MarginLayoutParams) o;
                leftMargin = m.leftMargin;
                topMargin = m.topMargin;
                rightMargin = m.rightMargin;
                bottomMargin = m.bottomMargin;
            }
        }

        public void setMargins(int l, int t, int r, int b) {
            leftMargin = l;
            topMargin = t;
            rightMargin = r;
            bottomMargin = b;
        }

        public void setMarginStart(int s) {
            leftMargin = s;
        }

        public void setMarginEnd(int e) {
            rightMargin = e;
        }

        public int getMarginStart() {
            return leftMargin;
        }

        public int getMarginEnd() {
            return rightMargin;
        }
    }

    protected final ArrayList<View> mChildren = new ArrayList<View>();
    private View touchTarget;
    private boolean disallowIntercept;
    private boolean clipChildren = true;
    private boolean clipToPadding = true;
    /** Solo se recorta a los hijos cuando hace falta (contenedores con desplazamiento). */
    protected boolean clipsContent;

    public ViewGroup(Context c) {
        super(c);
    }

    public ViewGroup(Context c, Object attrs) {
        super(c);
    }

    // ------------------------------------------------------------ hijos

    public int getChildCount() {
        return mChildren.size();
    }

    public View getChildAt(int i) {
        return i >= 0 && i < mChildren.size() ? mChildren.get(i) : null;
    }

    public int indexOfChild(View v) {
        return mChildren.indexOf(v);
    }

    protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    protected LayoutParams generateLayoutParams(LayoutParams p) {
        return p;
    }

    protected boolean checkLayoutParams(LayoutParams p) {
        return p != null;
    }

    public void addView(View child) {
        addView(child, -1);
    }

    public void addView(View child, int index) {
        LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            lp = generateDefaultLayoutParams();
        }
        addView(child, index, lp);
    }

    public void addView(View child, int w, int h) {
        LayoutParams lp = generateDefaultLayoutParams();
        lp.width = w;
        lp.height = h;
        addView(child, -1, lp);
    }

    public void addView(View child, LayoutParams lp) {
        addView(child, -1, lp);
    }

    public void addView(View child, int index, LayoutParams lp) {
        addViewInner(child, index, lp);
        requestLayout();
        invalidate();
    }

    /** Añade sin pedir recolocación (para listas que añaden filas mientras se colocan). */
    protected boolean addViewInLayout(View child, int index, LayoutParams lp) {
        addViewInner(child, index, lp);
        return true;
    }

    private void addViewInner(View child, int index, LayoutParams lp) {
        if (child.mParent != null) {
            throw new IllegalStateException("The specified child already has a parent. "
                    + "You must call removeView() on the child's parent first.");
        }
        if (lp == null) {
            lp = generateDefaultLayoutParams();
        }
        if (!checkLayoutParams(lp)) {
            lp = generateLayoutParams(lp);
        }
        child.mLayoutParams = lp;
        child.mParent = this;
        if (index < 0 || index > mChildren.size()) {
            mChildren.add(child);
        } else {
            mChildren.add(index, child);
        }
        child.forceLayout();
        if (layer() != null) {
            child.attach(layer());
        }
        onViewAdded(child);
    }

    public void onViewAdded(View child) {
    }

    public void onViewRemoved(View child) {
    }

    private void removeInner(View child) {
        if (touchTarget == child) {
            touchTarget = null;
        }
        if (child.layer() != null) {
            child.detach();
        }
        child.mParent = null;
        onViewRemoved(child);
    }

    public void removeView(View child) {
        if (mChildren.remove(child)) {
            removeInner(child);
            requestLayout();
            invalidate();
        }
    }

    public void removeViewInLayout(View child) {
        if (mChildren.remove(child)) {
            removeInner(child);
        }
    }

    public void removeViewAt(int i) {
        if (i >= 0 && i < mChildren.size()) {
            removeView(mChildren.get(i));
        }
    }

    public void removeViews(int start, int count) {
        for (int i = 0; i < count && start < mChildren.size(); i++) {
            removeInner(mChildren.remove(start));
        }
        requestLayout();
        invalidate();
    }

    public void removeAllViews() {
        if (mChildren.isEmpty()) {
            return;
        }
        ArrayList<View> old = new ArrayList<View>(mChildren);
        mChildren.clear();
        for (View v : old) {
            removeInner(v);
        }
        requestLayout();
        invalidate();
    }

    public void removeAllViewsInLayout() {
        ArrayList<View> old = new ArrayList<View>(mChildren);
        mChildren.clear();
        for (View v : old) {
            removeInner(v);
        }
    }

    public void bringChildToFront(View child) {
        if (mChildren.remove(child)) {
            mChildren.add(child);
            invalidate();
        }
    }

    @Override
    public void attach(desk.Layer l) {
        super.attach(l);
        for (View v : new ArrayList<View>(mChildren)) {
            v.attach(l);
        }
    }

    @Override
    public void detach() {
        for (View v : new ArrayList<View>(mChildren)) {
            v.detach();
        }
        super.detach();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int want) {
        if (want == NO_ID) {
            return null;
        }
        if (want == getId()) {
            return (T) this;
        }
        for (View v : mChildren) {
            View f = v.findViewById(want);
            if (f != null) {
                return (T) f;
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends View> T findViewWithTag(Object t) {
        if (t != null && t.equals(getTag())) {
            return (T) this;
        }
        for (View v : mChildren) {
            View f = v.findViewWithTag(t);
            if (f != null) {
                return (T) f;
            }
        }
        return null;
    }

    public void setClipChildren(boolean b) {
        clipChildren = b;
    }

    public boolean getClipChildren() {
        return clipChildren;
    }

    public void setClipToPadding(boolean b) {
        clipToPadding = b;
    }

    public boolean getClipToPadding() {
        return clipToPadding;
    }

    public void setLayoutTransition(Object t) {
    }

    public void setDescendantFocusability(int f) {
    }

    public void setMotionEventSplittingEnabled(boolean b) {
    }

    public void setAddStatesFromChildren(boolean b) {
    }

    public void setBaselineAligned(boolean b) {
    }

    public void setMeasureAllChildren(boolean b) {
    }

    // ------------------------------------------------------------ medidas

    public static int getChildMeasureSpec(int spec, int padding, int childDim) {
        int mode = MeasureSpec.getMode(spec);
        int size = Math.max(0, MeasureSpec.getSize(spec) - padding);
        if (childDim >= 0) {
            return MeasureSpec.makeMeasureSpec(childDim, MeasureSpec.EXACTLY);
        }
        switch (mode) {
            case MeasureSpec.EXACTLY:
                return MeasureSpec.makeMeasureSpec(size, childDim == LayoutParams.MATCH_PARENT
                        ? MeasureSpec.EXACTLY : MeasureSpec.AT_MOST);
            case MeasureSpec.AT_MOST:
                return MeasureSpec.makeMeasureSpec(size, MeasureSpec.AT_MOST);
            default:
                return MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
    }

    protected void measureChild(View child, int wSpec, int hSpec) {
        LayoutParams lp = child.getLayoutParams();
        child.measure(getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight, lp.width),
                getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom, lp.height));
    }

    protected void measureChildren(int wSpec, int hSpec) {
        for (View v : mChildren) {
            if (v.getVisibility() != GONE) {
                measureChild(v, wSpec, hSpec);
            }
        }
    }

    protected void measureChildWithMargins(View child, int wSpec, int wUsed, int hSpec, int hUsed) {
        LayoutParams p = child.getLayoutParams();
        int mh = 0;
        int mv = 0;
        if (p instanceof MarginLayoutParams) {
            MarginLayoutParams lp = (MarginLayoutParams) p;
            mh = lp.leftMargin + lp.rightMargin;
            mv = lp.topMargin + lp.bottomMargin;
        }
        child.measure(getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight + mh + wUsed, p.width),
                getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom + mv + hUsed, p.height));
    }

    @Override
    protected abstract void onLayout(boolean changed, int l, int t, int r, int b);

    // ------------------------------------------------------------ dibujo

    @Override
    protected void dispatchDraw(Canvas c) {
        boolean clip = clipsContent && clipToPadding;
        int n0 = 0;
        if (clip) {
            n0 = c.save();
            c.clipRect(mScrollX + mPaddingLeft, mScrollY + mPaddingTop, mScrollX + getWidth() - mPaddingRight,
                    mScrollY + getHeight() - mPaddingBottom);
        } else if (clipsContent) {
            n0 = c.save();
            c.clipRect(mScrollX, mScrollY, mScrollX + getWidth(), mScrollY + getHeight());
        }
        final int count = mChildren.size();
        for (int i = 0; i < count && i < mChildren.size(); i++) {
            View v = mChildren.get(i);
            if (v.getVisibility() == VISIBLE && v.getAlpha() > 0f) {
                drawChild(c, v, 0);
            }
        }
        if (clipsContent) {
            c.restoreToCount(n0);
        }
    }

    protected boolean drawChild(Canvas c, View v, long time) {
        float x = v.mLeft + v.getTranslationX();
        float y = v.mTop + v.getTranslationY();
        int w = v.getWidth();
        int h = v.getHeight();
        boolean tf = v.hasTransform();
        if (!tf && c.quickReject(x, y, x + w, y + h) && !(v instanceof ViewGroup && !((ViewGroup) v).clipChildren)) {
            return false;
        }
        int n = c.save();
        c.translate(x, y);
        if (tf) {
            float px = v.getPivotX();
            float py = v.getPivotY();
            if (v.getRotation() != 0f) {
                c.rotate(v.getRotation(), px, py);
            }
            if (v.getScaleX() != 1f || v.getScaleY() != 1f) {
                c.scale(v.getScaleX(), v.getScaleY(), px, py);
            }
        }
        if (v.getAlpha() < 1f) {
            c.mulAlpha(v.getAlpha());
        }
        java.awt.Shape cs = v.clipShape();
        if (cs != null) {
            c.clipShape(cs);
        }
        c.translate(-v.mScrollX, -v.mScrollY);
        v.draw(c);
        c.restoreToCount(n);
        return false;
    }

    // ------------------------------------------------------------ toques

    public boolean onInterceptTouchEvent(MotionEvent e) {
        return false;
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean d) {
        disallowIntercept = d;
        if (mParent != null) {
            mParent.requestDisallowInterceptTouchEvent(d);
        }
    }

    private boolean toChild(View v, MotionEvent e, int action) {
        MotionEvent c = MotionEvent.obtain(e);
        c.setAction(action);
        c.offsetLocation(mScrollX - v.mLeft - v.getTranslationX(), mScrollY - v.mTop - v.getTranslationY());
        return v.dispatchTouchEvent(c);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchTarget = null;
            disallowIntercept = false;
            if (!onInterceptTouchEvent(e)) {
                float px = e.getX() + mScrollX;
                float py = e.getY() + mScrollY;
                for (int i = mChildren.size() - 1; i >= 0; i--) {
                    View v = mChildren.get(i);
                    if (v.getVisibility() == VISIBLE && v.hit(px, py)) {
                        if (toChild(v, e, MotionEvent.ACTION_DOWN)) {
                            touchTarget = v;
                            return true;
                        }
                    }
                }
            }
            return super.dispatchTouchEvent(e);
        }
        if (touchTarget != null) {
            View t = touchTarget;
            if (!disallowIntercept && onInterceptTouchEvent(e)) {
                toChild(t, e, MotionEvent.ACTION_CANCEL);
                touchTarget = null;
                return true;
            }
            boolean r = toChild(t, e, action);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchTarget = null;
                disallowIntercept = false;
            }
            return r;
        }
        return super.dispatchTouchEvent(e);
    }

    @Override
    public boolean dispatchContextClick(float x, float y) {
        float px = x + mScrollX;
        float py = y + mScrollY;
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View v = mChildren.get(i);
            if (v.getVisibility() == VISIBLE && v.hit(px, py)) {
                if (v.dispatchContextClick(px - v.mLeft - v.getTranslationX(), py - v.mTop - v.getTranslationY())) {
                    return true;
                }
                break;
            }
        }
        return super.dispatchContextClick(x, y);
    }

    @Override
    public boolean dispatchWheel(float x, float y, float dx, float dy) {
        float px = x + mScrollX;
        float py = y + mScrollY;
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View v = mChildren.get(i);
            if (v.getVisibility() == VISIBLE && v.hit(px, py)) {
                if (v.dispatchWheel(px - v.mLeft - v.getTranslationX(), py - v.mTop - v.getTranslationY(), dx, dy)) {
                    return true;
                }
                break;
            }
        }
        return super.dispatchWheel(x, y, dx, dy);
    }

    @Override
    public View findHover(float x, float y) {
        float px = x + mScrollX;
        float py = y + mScrollY;
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View v = mChildren.get(i);
            if (v.getVisibility() == VISIBLE && v.hit(px, py)) {
                View f = v.findHover(px - v.mLeft - v.getTranslationX(), py - v.mTop - v.getTranslationY());
                if (f != null) {
                    return f;
                }
                break;
            }
        }
        return super.findHover(x, y);
    }

    @Override
    public View findFocus() {
        for (View v : mChildren) {
            View f = v.findFocus();
            if (f != null) {
                return f;
            }
        }
        return null;
    }

    @Override
    public boolean hasFocus() {
        return findFocus() != null;
    }
}
