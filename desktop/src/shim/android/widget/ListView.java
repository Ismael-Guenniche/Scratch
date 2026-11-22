package android.widget;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Lista virtual: solo existen las filas visibles. Las que salen de pantalla se guardan y se
 * reutilizan como convertView, así que 5.000 canciones cuestan lo mismo que 20.
 */
public class ListView extends AbsListView {
    private ListAdapter adapter;
    private int first;
    private final ArrayList<ArrayList<View>> scrap = new ArrayList<ArrayList<View>>();
    private Drawable divider;
    private int dividerHeight;
    private boolean dataChanged = true;
    private boolean blockLayout;
    private int pendingPos = -1;
    private int pendingOffset;
    private OnScrollListener onScroll;
    private boolean fastScroll;
    private boolean barEnabled = true;
    private boolean dragging;
    private boolean thumbDrag;
    private float lastY;
    private float downY;
    private int motionPos = -1;
    private View pressedChild;
    private Runnable longPress;
    private boolean longFired;
    private VelocityTracker tracker;
    private long barUntil;
    private float avgRow = 56;
    private float carry;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override
        public void onChanged() {
            dataChanged = true;
            requestLayout();
            invalidate();
        }

        @Override
        public void onInvalidated() {
            onChanged();
        }
    };
    private final desk.Glide glide = new desk.Glide() {
        private float virtual;

        @Override
        protected float get() {
            virtual = 0;
            return 0;
        }

        @Override
        protected float set(float v) {
            float want = v - virtual;
            int d = Math.round(want);
            if (d != 0) {
                int moved = scrollList(d);
                virtual += moved;
                if (moved != d) {
                    return virtual + 100000f * Math.signum(d);
                }
            }
            return v;
        }
    };

    public ListView(Context c) {
        super(c);
        clipsContent = true;
        setClickable(true);
    }

    public ListView(Context c, Object attrs) {
        this(c);
    }

    // ------------------------------------------------------------ configuración

    @Override
    public ListAdapter getAdapter() {
        return adapter;
    }

    @Override
    public void setAdapter(ListAdapter a) {
        if (adapter != null) {
            adapter.unregisterDataSetObserver(observer);
        }
        adapter = a;
        scrap.clear();
        removeAllViewsInLayout();
        first = 0;
        if (a != null) {
            a.registerDataSetObserver(observer);
        }
        dataChanged = true;
        requestLayout();
        invalidate();
    }

    public void setDivider(Drawable d) {
        divider = d;
        if (d != null && d.getIntrinsicHeight() > 0) {
            dividerHeight = d.getIntrinsicHeight();
        } else if (d == null) {
            dividerHeight = 0;
        }
        requestLayout();
    }

    public void setDividerHeight(int h) {
        dividerHeight = h;
        requestLayout();
    }

    public int getDividerHeight() {
        return dividerHeight;
    }

    public void setSelector(Drawable d) {
    }

    public void setSelector(int id) {
    }

    public void setCacheColorHint(int c) {
    }

    public void setScrollingCacheEnabled(boolean b) {
    }

    public void setFastScrollEnabled(boolean b) {
        fastScroll = b;
    }

    public void setFastScrollAlwaysVisible(boolean b) {
    }

    public void setSmoothScrollbarEnabled(boolean b) {
    }

    public void setHeaderDividersEnabled(boolean b) {
    }

    public void setFooterDividersEnabled(boolean b) {
    }

    public void setChoiceMode(int m) {
    }

    public void setItemsCanFocus(boolean b) {
    }

    public void setStackFromBottom(boolean b) {
    }

    public void setTranscriptMode(int m) {
    }

    public void setFriction(float f) {
    }

    public void setRecyclerListener(Object l) {
    }

    @Override
    public void setVerticalScrollBarEnabled(boolean b) {
        barEnabled = b;
    }

    public void setOnScrollListener(OnScrollListener l) {
        onScroll = l;
    }

    public void invalidateViews() {
        dataChanged = true;
        requestLayout();
        invalidate();
    }

    @Override
    public int getFirstVisiblePosition() {
        return first;
    }

    @Override
    public void setSelection(int position) {
        setSelectionFromTop(position, 0);
    }

    public void setSelectionFromTop(int position, int y) {
        glide.stop();
        pendingPos = position;
        pendingOffset = y;
        requestLayout();
        invalidate();
    }

    public void smoothScrollToPosition(int position) {
        setSelection(position);
    }

    public void smoothScrollToPositionFromTop(int position, int offset) {
        setSelectionFromTop(position, offset);
    }

    public void smoothScrollBy(int distance, int duration) {
        glide.glideTo(distance);
    }

    public int pointToPosition(int x, int y) {
        for (int i = 0; i < mChildren.size(); i++) {
            View v = mChildren.get(i);
            if (y >= v.getTop() && y < v.getBottom() && x >= v.getLeft() && x < v.getRight()) {
                return first + i;
            }
        }
        return INVALID_POSITION;
    }

    @Override
    public void requestLayout() {
        if (!blockLayout) {
            super.requestLayout();
        }
    }

    // ------------------------------------------------------------ filas

    private int count() {
        return adapter == null ? 0 : adapter.getCount();
    }

    private int topEdge() {
        return getClipToPadding() ? mPaddingTop : 0;
    }

    private int bottomEdge() {
        return getClipToPadding() ? getHeight() - mPaddingBottom : getHeight();
    }

    private void toScrap(View v) {
        int type = 0;
        Object t = v.getTag(0x7f0f0001);
        if (t instanceof Integer) {
            type = (Integer) t;
        }
        if (type < 0) {
            return;
        }
        while (scrap.size() <= type) {
            scrap.add(new ArrayList<View>());
        }
        ArrayList<View> l = scrap.get(type);
        if (l.size() < 24) {
            l.add(v);
        }
    }

    private View obtain(int pos, HashMap<Integer, View> active) {
        View v = active == null ? null : active.remove(pos);
        if (v == null) {
            int type = adapter.getItemViewType(pos);
            View convert = null;
            if (type >= 0 && type < scrap.size() && !scrap.get(type).isEmpty()) {
                ArrayList<View> l = scrap.get(type);
                convert = l.remove(l.size() - 1);
            }
            v = adapter.getView(pos, convert, this);
            if (v != convert && convert != null) {
                toScrap(convert);
            }
            v.setTag(0x7f0f0001, type);
        }
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) {
            lp = new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(lp);
        }
        return v;
    }

    private void measureRow(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        int w = Math.max(0, getWidth() - mPaddingLeft - mPaddingRight);
        v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), lp.height > 0
                ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
    }

    private void fillDown(int pos, int top, HashMap<Integer, View> active) {
        int n = count();
        int end = bottomEdge();
        while (top < end && pos < n) {
            View v = obtain(pos, active);
            addViewInLayout(v, -1, v.getLayoutParams());
            measureRow(v);
            v.layout(mPaddingLeft, top, mPaddingLeft + v.getMeasuredWidth(), top + v.getMeasuredHeight());
            top += v.getMeasuredHeight() + dividerHeight;
            pos++;
        }
    }

    private void fillUp(int pos, int bottom, HashMap<Integer, View> active) {
        int start = topEdge();
        while (bottom > start && pos >= 0) {
            View v = obtain(pos, active);
            addViewInLayout(v, 0, v.getLayoutParams());
            measureRow(v);
            v.layout(mPaddingLeft, bottom - v.getMeasuredHeight(), mPaddingLeft + v.getMeasuredWidth(), bottom);
            bottom -= v.getMeasuredHeight() + dividerHeight;
            first = pos;
            pos--;
        }
    }

    private void offsetChildren(int d) {
        for (View v : mChildren) {
            v.layout(v.getLeft(), v.getTop() + d, v.getRight(), v.getBottom() + d);
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = getDefaultSize(getSuggestedMinimumWidth(), wSpec);
        int h;
        if (MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY) {
            h = MeasureSpec.getSize(hSpec);
        } else {
            h = Math.round(Math.min(count(), 8) * (avgRow + dividerHeight)) + mPaddingTop + mPaddingBottom;
            if (MeasureSpec.getMode(hSpec) == MeasureSpec.AT_MOST) {
                h = Math.min(h, MeasureSpec.getSize(hSpec));
            }
        }
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutChildren();
    }

    private void layoutChildren() {
        if (blockLayout) {
            return;
        }
        blockLayout = true;
        try {
            int n = count();
            unpress();
            if (n == 0 || getHeight() <= 0) {
                for (View v : new ArrayList<View>(mChildren)) {
                    removeViewInLayout(v);
                    toScrap(v);
                }
                first = 0;
                dataChanged = false;
                return;
            }
            int anchorPos = first;
            int anchorTop = mChildren.isEmpty() ? mPaddingTop : mChildren.get(0).getTop();
            if (pendingPos >= 0) {
                anchorPos = pendingPos;
                anchorTop = mPaddingTop + pendingOffset;
                pendingPos = -1;
            }
            if (anchorPos >= n) {
                anchorPos = n - 1;
                anchorTop = mPaddingTop;
            }
            anchorPos = Math.max(0, anchorPos);
            HashMap<Integer, View> active = null;
            ArrayList<View> old = new ArrayList<View>(mChildren);
            if (!dataChanged) {
                active = new HashMap<Integer, View>();
            }
            for (int i = 0; i < old.size(); i++) {
                View v = old.get(i);
                removeViewInLayout(v);
                if (active != null) {
                    active.put(first + i, v);
                } else {
                    toScrap(v);
                }
            }
            first = anchorPos;
            fillDown(anchorPos, anchorTop, active);
            if (!mChildren.isEmpty()) {
                fillUp(first - 1, mChildren.get(0).getTop() - dividerHeight, active);
            }
            adjust(active);
            if (active != null) {
                for (View v : active.values()) {
                    toScrap(v);
                }
            }
            dataChanged = false;
            if (!mChildren.isEmpty()) {
                float sum = 0;
                for (View v : mChildren) {
                    sum += v.getHeight();
                }
                avgRow = Math.max(1f, sum / mChildren.size());
            }
        } finally {
            blockLayout = false;
        }
        fireScroll();
    }

    /** Sin huecos: ni por arriba del primer elemento ni por debajo del último (si hay con qué llenar). */
    private void adjust(HashMap<Integer, View> active) {
        if (mChildren.isEmpty()) {
            return;
        }
        int n = count();
        int contentBottom = getHeight() - mPaddingBottom;
        View lastV = mChildren.get(mChildren.size() - 1);
        if (first + mChildren.size() == n && lastV.getBottom() < contentBottom) {
            int shift = contentBottom - lastV.getBottom();
            if (first == 0) {
                shift = Math.min(shift, Math.max(0, mPaddingTop - mChildren.get(0).getTop()));
            }
            if (shift > 0) {
                offsetChildren(shift);
                fillUp(first - 1, mChildren.get(0).getTop() - dividerHeight, active);
                if (first == 0 && mChildren.get(0).getTop() > mPaddingTop) {
                    offsetChildren(mPaddingTop - mChildren.get(0).getTop());
                }
            }
        }
        if (first == 0 && mChildren.get(0).getTop() > mPaddingTop) {
            offsetChildren(mPaddingTop - mChildren.get(0).getTop());
            View lv = mChildren.get(mChildren.size() - 1);
            fillDown(first + mChildren.size(), lv.getBottom() + dividerHeight, active);
        }
    }

    /** Desplaza d píxeles (positivo = avanzar). Devuelve lo que se movió de verdad. */
    private int scrollList(int d) {
        if (mChildren.isEmpty() || d == 0 || blockLayout) {
            return 0;
        }
        int n = count();
        View firstV = mChildren.get(0);
        View lastV = mChildren.get(mChildren.size() - 1);
        if (d > 0) {
            if (first + mChildren.size() >= n) {
                d = Math.min(d, Math.max(0, lastV.getBottom() - (getHeight() - mPaddingBottom)));
            }
        } else if (first == 0) {
            d = Math.max(d, Math.min(0, firstV.getTop() - mPaddingTop));
        }
        if (d == 0) {
            return 0;
        }
        blockLayout = true;
        try {
            unpress();
            offsetChildren(-d);
            int te = topEdge();
            int be = bottomEdge();
            while (mChildren.size() > 1 && mChildren.get(0).getBottom() + dividerHeight <= te) {
                View v = mChildren.get(0);
                removeViewInLayout(v);
                toScrap(v);
                first++;
            }
            while (mChildren.size() > 1 && mChildren.get(mChildren.size() - 1).getTop() >= be) {
                View v = mChildren.get(mChildren.size() - 1);
                removeViewInLayout(v);
                toScrap(v);
            }
            View lv = mChildren.get(mChildren.size() - 1);
            fillDown(first + mChildren.size(), lv.getBottom() + dividerHeight, null);
            fillUp(first - 1, mChildren.get(0).getTop() - dividerHeight, null);
        } finally {
            blockLayout = false;
        }
        barUntil = System.currentTimeMillis() + 900;
        removeCallbacks(hideBar);
        postDelayed(hideBar, 950);
        invalidate();
        fireScroll();
        return d;
    }

    private final Runnable hideBar = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    private void fireScroll() {
        if (onScroll != null) {
            onScroll.onScroll(this, first, mChildren.size(), count());
        }
    }

    @Override
    public boolean canScrollVertically(int dir) {
        if (mChildren.isEmpty()) {
            return false;
        }
        if (dir < 0) {
            return first > 0 || mChildren.get(0).getTop() < mPaddingTop;
        }
        return first + mChildren.size() < count()
                || mChildren.get(mChildren.size() - 1).getBottom() > getHeight() - mPaddingBottom;
    }

    @Override
    protected boolean onWheel(float dx, float dy) {
        if (dy == 0 || !canScrollVertically(dy > 0 ? 1 : -1)) {
            return false;
        }
        glide.stop();
        carry += dy;
        int d = Math.round(carry);
        carry -= d;
        scrollList(d);
        return true;
    }

    // ------------------------------------------------------------ barra de desplazamiento

    private boolean scrollable() {
        return canScrollVertically(1) || canScrollVertically(-1);
    }

    private float barW() {
        return 14 * desk.Rt.density;
    }

    private float[] thumb() {
        int h = getHeight();
        int n = Math.max(1, count());
        float vis = Math.max(1, mChildren.size());
        float th = Math.max(36 * desk.Rt.density, h * Math.min(1f, vis / n));
        float frac = 0;
        if (!mChildren.isEmpty()) {
            View f = mChildren.get(0);
            float part = f.getHeight() <= 0 ? 0 : Math.max(0f, (mPaddingTop - f.getTop()) / (float) f.getHeight());
            float maxFirst = Math.max(1f, n - vis + 1);
            frac = Math.max(0f, Math.min(1f, (first + part) / maxFirst));
        }
        return new float[] {(h - th) * frac, th};
    }

    private void moveThumb(float y) {
        float[] t = thumb();
        float span = getHeight() - t[1];
        if (span <= 0) {
            return;
        }
        float frac = Math.max(0f, Math.min(1f, (y - t[1] / 2f) / span));
        int n = count();
        int target = Math.round(frac * Math.max(0, n - 1));
        if (frac >= 0.999f) {
            target = n - 1;
        }
        pendingPos = target;
        pendingOffset = 0;
        barUntil = System.currentTimeMillis() + 900;
        requestLayout();
        invalidate();
    }

    @Override
    public void onDrawForeground(Canvas c) {
        if (!(barEnabled || fastScroll) || !scrollable()) {
            return;
        }
        boolean show = thumbDrag || System.currentTimeMillis() <= barUntil || fastScroll;
        if (!show) {
            return;
        }
        float[] t = thumb();
        float d = desk.Rt.density;
        boolean strong = thumbDrag || System.currentTimeMillis() <= barUntil;
        c.g.setPaint(new java.awt.Color(0x80, 0x80, 0x80, strong ? 0xAA : 0x55));
        float w = strong ? 6 * d : 4 * d;
        c.g.fill(new java.awt.geom.RoundRectangle2D.Float(getWidth() - w - 3 * d, t[0] + 2 * d, w, t[1] - 4 * d,
                w, w));
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
        if (divider != null && dividerHeight > 0) {
            int n = count();
            for (int i = 0; i < mChildren.size(); i++) {
                if (first + i >= n - 1) {
                    break;
                }
                View v = mChildren.get(i);
                divider.setBounds(mPaddingLeft, v.getBottom(), getWidth() - mPaddingRight,
                        v.getBottom() + dividerHeight);
                divider.draw(c);
            }
        }
        if (pressedChild != null && pressedChild.getParent() == this) {
            c.g.setPaint(new java.awt.Color(0x80, 0x80, 0x80, 0x26));
            c.g.fillRect(pressedChild.getLeft(), pressedChild.getTop(), pressedChild.getWidth(),
                    pressedChild.getHeight());
        }
    }

    // ------------------------------------------------------------ toques

    private void unpress() {
        if (longPress != null) {
            removeCallbacks(longPress);
            longPress = null;
        }
        if (pressedChild != null) {
            pressedChild = null;
            invalidate();
        }
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
                if ((barEnabled || fastScroll) && scrollable() && e.getX() >= getWidth() - barW()) {
                    thumbDrag = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!dragging && Math.abs(e.getY() - downY) > 6 * desk.Rt.density && scrollable()) {
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
        if (tracker == null) {
            tracker = VelocityTracker.obtain();
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                tracker.clear();
                tracker.addMovement(e);
                if (thumbDrag) {
                    moveThumb(e.getY());
                    return true;
                }
                motionPos = pointToPosition((int) e.getX(), (int) e.getY());
                longFired = false;
                if (motionPos >= 0 && adapter != null && adapter.isEnabled(motionPos)) {
                    pressedChild = getChildAt(motionPos - first);
                    invalidate();
                    if (itemLongClick != null) {
                        final int pos = motionPos;
                        final View row = pressedChild;
                        longPress = new Runnable() {
                            @Override
                            public void run() {
                                if (longPress == this && pressedChild == row && row != null) {
                                    longPress = null;
                                    if (itemLongClick.onItemLongClick(ListView.this, row, pos,
                                            adapter.getItemId(pos))) {
                                        longFired = true;
                                        unpress();
                                    }
                                }
                            }
                        };
                        postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                    }
                } else {
                    motionPos = -1;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                tracker.addMovement(e);
                if (thumbDrag) {
                    moveThumb(e.getY());
                    return true;
                }
                if (!dragging && Math.abs(e.getY() - downY) > 6 * desk.Rt.density) {
                    if (scrollable()) {
                        dragging = true;
                        lastY = e.getY();
                    }
                    unpress();
                    motionPos = -1;
                }
                if (dragging) {
                    int dy = Math.round(lastY - e.getY());
                    if (dy != 0) {
                        scrollList(dy);
                        lastY -= dy;
                    }
                }
                return true;
            case MotionEvent.ACTION_UP: {
                if (thumbDrag) {
                    thumbDrag = false;
                    invalidate();
                    return true;
                }
                if (dragging) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    float v = -tracker.getYVelocity();
                    if (Math.abs(v) > 120 * desk.Rt.density) {
                        glide.fling(v);
                    }
                } else if (motionPos >= 0 && !longFired && adapter != null && motionPos < count()) {
                    View row = getChildAt(motionPos - first);
                    int pos = motionPos;
                    unpress();
                    if (row != null) {
                        performItemClick(row, pos, adapter.getItemId(pos));
                    }
                }
                dragging = false;
                motionPos = -1;
                unpress();
                return true;
            }
            default:
                dragging = false;
                thumbDrag = false;
                motionPos = -1;
                unpress();
                return true;
        }
    }

    @Override
    public boolean dispatchContextClick(float x, float y) {
        for (int i = mChildren.size() - 1; i >= 0; i--) {
            View v = mChildren.get(i);
            if (y >= v.getTop() && y < v.getBottom()) {
                if (v.dispatchContextClick(x - v.getLeft(), y - v.getTop())) {
                    return true;
                }
                int pos = first + i;
                return itemLongClick != null && adapter != null
                        && itemLongClick.onItemLongClick(this, v, pos, adapter.getItemId(pos));
            }
        }
        return false;
    }

    @Override
    public View findHover(float x, float y) {
        View v = super.findHover(x, y);
        return v == this ? null : v;
    }
}
