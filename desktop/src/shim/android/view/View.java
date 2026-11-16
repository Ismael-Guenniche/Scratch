package android.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;

public class View implements Drawable.Callback {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;
    public static final int NO_ID = -1;
    public static final int OVER_SCROLL_ALWAYS = 0;
    public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;
    public static final int OVER_SCROLL_NEVER = 2;
    public static final int SYSTEM_UI_FLAG_VISIBLE = 0;
    public static final int SYSTEM_UI_FLAG_LOW_PROFILE = 1;
    public static final int SYSTEM_UI_FLAG_HIDE_NAVIGATION = 2;
    public static final int SYSTEM_UI_FLAG_FULLSCREEN = 4;
    public static final int SYSTEM_UI_FLAG_LAYOUT_STABLE = 0x100;
    public static final int SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION = 0x200;
    public static final int SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN = 0x400;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE = 0x800;
    public static final int SYSTEM_UI_FLAG_IMMERSIVE_STICKY = 0x1000;
    public static final int SYSTEM_UI_FLAG_LIGHT_STATUS_BAR = 0x2000;
    public static final int SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR = 0x10;
    public static final int LAYER_TYPE_NONE = 0;
    public static final int LAYER_TYPE_SOFTWARE = 1;
    public static final int LAYER_TYPE_HARDWARE = 2;
    public static final int TEXT_ALIGNMENT_CENTER = 4;
    public static final int TEXT_ALIGNMENT_VIEW_START = 5;
    public static final int TEXT_ALIGNMENT_VIEW_END = 6;
    public static final int LAYOUT_DIRECTION_LTR = 0;
    public static final int LAYOUT_DIRECTION_RTL = 1;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 2;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_YES = 1;
    public static final int FOCUS_DOWN = 130;
    public static final int FOCUS_UP = 33;
    public static final int SCROLLBARS_INSIDE_OVERLAY = 0;
    public static final int SCROLLBARS_INSIDE_INSET = 0x01000000;
    public static final int SCROLLBARS_OUTSIDE_OVERLAY = 0x02000000;
    public static final int SCROLLBARS_OUTSIDE_INSET = 0x03000000;
    public static final int MEASURED_SIZE_MASK = 0x00ffffff;
    public static final int MEASURED_STATE_TOO_SMALL = 0x01000000;

    public interface OnClickListener {
        void onClick(View v);
    }

    public interface OnLongClickListener {
        boolean onLongClick(View v);
    }

    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent e);
    }

    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent e);
    }

    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob);
    }

    public interface OnApplyWindowInsetsListener {
        WindowInsets onApplyWindowInsets(View v, WindowInsets insets);
    }

    public interface OnScrollChangeListener {
        void onScrollChange(View v, int x, int y, int oldX, int oldY);
    }

    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);

        void onViewDetachedFromWindow(View v);
    }

    public interface OnGenericMotionListener {
        boolean onGenericMotion(View v, MotionEvent e);
    }

    public static class MeasureSpec {
        public static final int UNSPECIFIED = 0;
        public static final int EXACTLY = 1 << 30;
        public static final int AT_MOST = 2 << 30;
        private static final int MODE_MASK = 3 << 30;

        public static int makeMeasureSpec(int size, int mode) {
            return (Math.max(0, size) & ~MODE_MASK) | (mode & MODE_MASK);
        }

        public static int getMode(int spec) {
            return spec & MODE_MASK;
        }

        public static int getSize(int spec) {
            return spec & ~MODE_MASK;
        }

        public static String toString(int spec) {
            int m = getMode(spec);
            return (m == EXACTLY ? "EXACTLY " : m == AT_MOST ? "AT_MOST " : "UNSPECIFIED ") + getSize(spec);
        }
    }

    protected final Context mContext;
    ViewGroup mParent;
    protected ViewGroup.LayoutParams mLayoutParams;
    protected int mLeft;
    protected int mTop;
    protected int mRight;
    protected int mBottom;
    private int measuredW;
    private int measuredH;
    protected int mPaddingLeft;
    protected int mPaddingTop;
    protected int mPaddingRight;
    protected int mPaddingBottom;
    protected int mScrollX;
    protected int mScrollY;
    private int visibility = VISIBLE;
    private float alpha = 1f;
    private float tx;
    private float ty;
    private float sx = 1f;
    private float sy = 1f;
    private float rotation;
    private float pivotX = Float.NaN;
    private float pivotY = Float.NaN;
    private Drawable background;
    private Drawable foreground;
    private boolean clickable;
    private boolean longClickable;
    private boolean enabled = true;
    private boolean pressed;
    private boolean selected;
    private boolean activated;
    private boolean hovered;
    private boolean focusable;
    private int id = NO_ID;
    private Object tag;
    private java.util.HashMap<Integer, Object> tags;
    private int minW;
    private int minH;
    private OnClickListener onClick;
    private OnLongClickListener onLongClick;
    private OnTouchListener onTouch;
    private OnApplyWindowInsetsListener onInsets;
    private OnScrollChangeListener onScroll;
    private OnFocusChangeListener onFocus;
    private OnGenericMotionListener onGeneric;
    private ArrayList<OnLayoutChangeListener> onLayoutChange;
    private ArrayList<OnAttachStateChangeListener> onAttach;
    private desk.Layer layer;
    private boolean forceLayout = true;
    private int lastWSpec = Integer.MIN_VALUE;
    private int lastHSpec = Integer.MIN_VALUE;
    private boolean clipToOutline;
    private ViewOutlineProvider outlineProvider = ViewOutlineProvider.BACKGROUND;
    private ViewPropertyAnimator animator;
    private CharSequence contentDescription;
    private Runnable longPress;
    private boolean longPressFired;
    private float downX;
    private float downY;

    public View(Context c) {
        mContext = c;
    }

    public View(Context c, Object attrs) {
        this(c);
    }

    public View(Context c, Object attrs, int defStyle) {
        this(c);
    }

    public final Context getContext() {
        return mContext;
    }

    public Resources getResources() {
        return mContext.getResources();
    }

    // ------------------------------------------------------------ árbol y ventana

    public final ViewParent getParent() {
        return mParent;
    }

    public View getRootView() {
        View v = this;
        while (v.mParent != null) {
            v = v.mParent;
        }
        return v;
    }

    /** La capa (ventana) en la que está la vista, o null si está suelta. */
    public desk.Layer layer() {
        return layer;
    }

    public boolean isAttachedToWindow() {
        return layer != null;
    }

    public Object getWindowToken() {
        return layer;
    }

    public Object getApplicationWindowToken() {
        return layer;
    }

    public int getWindowVisibility() {
        return layer != null ? VISIBLE : GONE;
    }

    public void attach(desk.Layer l) {
        if (layer == l) {
            return;
        }
        layer = l;
        forceLayout = true;
        if (background != null) {
            background.setCallback(this);
        }
        onAttachedToWindow();
        if (onAttach != null) {
            for (OnAttachStateChangeListener a : new ArrayList<OnAttachStateChangeListener>(onAttach)) {
                a.onViewAttachedToWindow(this);
            }
        }
        if (onInsets != null) {
            onInsets.onApplyWindowInsets(this, WindowInsets.ZERO);
        }
    }

    public void detach() {
        if (layer == null) {
            return;
        }
        setPressedInternal(false);
        cancelLongPress();
        hovered = false;
        onDetachedFromWindow();
        if (onAttach != null) {
            for (OnAttachStateChangeListener a : new ArrayList<OnAttachStateChangeListener>(onAttach)) {
                a.onViewDetachedFromWindow(this);
            }
        }
        layer = null;
    }

    protected void onAttachedToWindow() {
    }

    protected void onDetachedFromWindow() {
    }

    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {
        if (onAttach == null) {
            onAttach = new ArrayList<OnAttachStateChangeListener>();
        }
        onAttach.add(l);
    }

    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l) {
        if (onAttach != null) {
            onAttach.remove(l);
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int i) {
        id = i;
    }

    public static int generateViewId() {
        return desk.Ids.next();
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object t) {
        tag = t;
    }

    public Object getTag(int key) {
        return tags == null ? null : tags.get(key);
    }

    public void setTag(int key, Object t) {
        if (tags == null) {
            tags = new java.util.HashMap<Integer, Object>();
        }
        tags.put(key, t);
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int want) {
        return want != NO_ID && want == id ? (T) this : null;
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewWithTag(Object t) {
        return t != null && t.equals(tag) ? (T) this : null;
    }

    // ------------------------------------------------------------ medidas

    public ViewGroup.LayoutParams getLayoutParams() {
        return mLayoutParams;
    }

    public void setLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp == null) {
            throw new NullPointerException("Layout parameters cannot be null");
        }
        mLayoutParams = mParent != null && !mParent.checkLayoutParams(lp) ? mParent.generateLayoutParams(lp) : lp;
        requestLayout();
    }

    public final void measure(int wSpec, int hSpec) {
        if (forceLayout || wSpec != lastWSpec || hSpec != lastHSpec) {
            lastWSpec = wSpec;
            lastHSpec = hSpec;
            onMeasure(wSpec, hSpec);
        }
    }

    protected void onMeasure(int wSpec, int hSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), wSpec),
                getDefaultSize(getSuggestedMinimumHeight(), hSpec));
    }

    protected final void setMeasuredDimension(int w, int h) {
        measuredW = w;
        measuredH = h;
    }

    public final int getMeasuredWidth() {
        return measuredW & MEASURED_SIZE_MASK;
    }

    public final int getMeasuredHeight() {
        return measuredH & MEASURED_SIZE_MASK;
    }

    public final int getMeasuredState() {
        return 0;
    }

    public static int getDefaultSize(int size, int spec) {
        int mode = MeasureSpec.getMode(spec);
        return mode == MeasureSpec.UNSPECIFIED ? size : MeasureSpec.getSize(spec);
    }

    public static int resolveSize(int size, int spec) {
        int mode = MeasureSpec.getMode(spec);
        int s = MeasureSpec.getSize(spec);
        if (mode == MeasureSpec.EXACTLY) {
            return s;
        }
        if (mode == MeasureSpec.AT_MOST) {
            return Math.min(size, s);
        }
        return size;
    }

    public static int resolveSizeAndState(int size, int spec, int childState) {
        return resolveSize(size, spec);
    }

    public static int combineMeasuredStates(int a, int b) {
        return a | b;
    }

    protected int getSuggestedMinimumWidth() {
        return background == null ? minW : Math.max(minW, background.getMinimumWidth());
    }

    protected int getSuggestedMinimumHeight() {
        return background == null ? minH : Math.max(minH, background.getMinimumHeight());
    }

    public void setMinimumWidth(int w) {
        minW = w;
        requestLayout();
    }

    public void setMinimumHeight(int h) {
        minH = h;
        requestLayout();
    }

    public int getMinimumWidth() {
        return minW;
    }

    public int getMinimumHeight() {
        return minH;
    }

    public void layout(int l, int t, int r, int b) {
        int ol = mLeft;
        int ot = mTop;
        int or = mRight;
        int ob = mBottom;
        boolean changed = l != ol || t != ot || r != or || b != ob;
        boolean sized = (r - l) != (or - ol) || (b - t) != (ob - ot);
        mLeft = l;
        mTop = t;
        mRight = r;
        mBottom = b;
        if (sized) {
            if (background != null) {
                background.setBounds(0, 0, r - l, b - t);
            }
            onSizeChanged(r - l, b - t, or - ol, ob - ot);
        }
        if (changed || forceLayout) {
            onLayout(changed, l, t, r, b);
            forceLayout = false;
            if (onLayoutChange != null) {
                for (OnLayoutChangeListener x : new ArrayList<OnLayoutChangeListener>(onLayoutChange)) {
                    x.onLayoutChange(this, l, t, r, b, ol, ot, or, ob);
                }
            }
        }
    }

    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    protected void onSizeChanged(int w, int h, int ow, int oh) {
    }

    public void addOnLayoutChangeListener(OnLayoutChangeListener l) {
        if (onLayoutChange == null) {
            onLayoutChange = new ArrayList<OnLayoutChangeListener>();
        }
        onLayoutChange.add(l);
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener l) {
        if (onLayoutChange != null) {
            onLayoutChange.remove(l);
        }
    }

    public void requestLayout() {
        View v = this;
        while (true) {
            v.forceLayout = true;
            if (v.mParent == null) {
                break;
            }
            v = v.mParent;
        }
        if (v.layer != null) {
            v.layer.requestLayout();
        }
    }

    public void forceLayout() {
        forceLayout = true;
    }

    public boolean isLayoutRequested() {
        return forceLayout;
    }

    public boolean isInLayout() {
        return false;
    }

    public boolean isLaidOut() {
        return layer != null && (mRight > mLeft || mBottom > mTop);
    }

    public final int getWidth() {
        return mRight - mLeft;
    }

    public final int getHeight() {
        return mBottom - mTop;
    }

    public final int getLeft() {
        return mLeft;
    }

    public final int getTop() {
        return mTop;
    }

    public final int getRight() {
        return mRight;
    }

    public final int getBottom() {
        return mBottom;
    }

    public float getX() {
        return mLeft + tx;
    }

    public float getY() {
        return mTop + ty;
    }

    public void setX(float x) {
        setTranslationX(x - mLeft);
    }

    public void setY(float y) {
        setTranslationY(y - mTop);
    }

    public void offsetTopAndBottom(int d) {
        mTop += d;
        mBottom += d;
        invalidateParent();
    }

    public void offsetLeftAndRight(int d) {
        mLeft += d;
        mRight += d;
        invalidateParent();
    }

    public int getBaseline() {
        return -1;
    }

    public void setPadding(int l, int t, int r, int b) {
        if (l != mPaddingLeft || t != mPaddingTop || r != mPaddingRight || b != mPaddingBottom) {
            mPaddingLeft = l;
            mPaddingTop = t;
            mPaddingRight = r;
            mPaddingBottom = b;
            requestLayout();
            invalidate();
        }
    }

    public void setPaddingRelative(int s, int t, int e, int b) {
        setPadding(s, t, e, b);
    }

    public int getPaddingLeft() {
        return mPaddingLeft;
    }

    public int getPaddingTop() {
        return mPaddingTop;
    }

    public int getPaddingRight() {
        return mPaddingRight;
    }

    public int getPaddingBottom() {
        return mPaddingBottom;
    }

    public int getPaddingStart() {
        return mPaddingLeft;
    }

    public int getPaddingEnd() {
        return mPaddingRight;
    }

    // ------------------------------------------------------------ posición en la ventana

    /** Posición dentro de la capa. Devuelve false si algún antepasado está girado o escalado. */
    private boolean offsetInLayer(int[] out) {
        float x = 0;
        float y = 0;
        boolean plain = true;
        View v = this;
        while (true) {
            x += v.mLeft + v.tx;
            y += v.mTop + v.ty;
            if (v.sx != 1f || v.sy != 1f || v.rotation != 0f) {
                plain = false;
            }
            ViewGroup p = v.mParent;
            if (p == null) {
                break;
            }
            x -= p.mScrollX;
            y -= p.mScrollY;
            v = p;
        }
        out[0] = Math.round(x);
        out[1] = Math.round(y);
        return plain;
    }

    public void getLocationInWindow(int[] out) {
        offsetInLayer(out);
        if (layer != null) {
            out[0] += layer.x;
            out[1] += layer.y;
        } else {
            View r = getRootView();
            if (r.layer != null) {
                out[0] += r.layer.x;
                out[1] += r.layer.y;
            }
        }
    }

    public void getLocationOnScreen(int[] out) {
        getLocationInWindow(out);
    }

    public boolean getGlobalVisibleRect(Rect r) {
        int[] xy = new int[2];
        getLocationInWindow(xy);
        r.set(xy[0], xy[1], xy[0] + getWidth(), xy[1] + getHeight());
        return isShown();
    }

    public boolean getLocalVisibleRect(Rect r) {
        r.set(0, 0, getWidth(), getHeight());
        return isShown();
    }

    public void getHitRect(Rect r) {
        r.set(mLeft, mTop, mRight, mBottom);
    }

    public void getDrawingRect(Rect r) {
        r.set(mScrollX, mScrollY, mScrollX + getWidth(), mScrollY + getHeight());
    }

    public void getWindowVisibleDisplayFrame(Rect r) {
        desk.Layer l = getRootView().layer;
        if (l != null && l.pane != null) {
            r.set(0, 0, l.pane.getWidth(), l.pane.getHeight());
        } else {
            r.set(0, 0, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
        }
    }

    // ------------------------------------------------------------ repintar

    public void invalidate() {
        if (layer == null || visibility != VISIBLE) {
            return;
        }
        int[] xy = new int[2];
        if (offsetInLayer(xy)) {
            layer.invalidate(xy[0], xy[1], xy[0] + getWidth(), xy[1] + getHeight());
        } else {
            layer.invalidate();
        }
    }

    public void invalidate(int l, int t, int r, int b) {
        invalidate();
    }

    public void invalidate(Rect r) {
        invalidate();
    }

    private void invalidateParent() {
        if (mParent != null) {
            mParent.invalidate();
        } else {
            invalidate();
        }
    }

    public void postInvalidate() {
        if (desk.Rt.isMain()) {
            invalidate();
        } else {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }
    }

    public void postInvalidateOnAnimation() {
        postInvalidate();
    }

    public void postInvalidateDelayed(long ms) {
        postDelayed(new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        }, ms);
    }

    public void invalidateOutline() {
        invalidate();
    }

    @Override
    public void invalidateDrawable(Drawable who) {
        invalidate();
    }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) {
    }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) {
    }

    public boolean post(Runnable r) {
        return desk.Main.H.post(r);
    }

    public boolean postDelayed(Runnable r, long ms) {
        return desk.Main.H.postDelayed(r, ms);
    }

    public void postOnAnimation(Runnable r) {
        desk.Frames.post(r);
    }

    public void postOnAnimationDelayed(Runnable r, long ms) {
        desk.Main.H.postDelayed(r, ms);
    }

    public boolean removeCallbacks(Runnable r) {
        desk.Main.H.removeCallbacks(r);
        desk.Frames.remove(r);
        return true;
    }

    public android.os.Handler getHandler() {
        return desk.Main.H;
    }

    // ------------------------------------------------------------ dibujo

    public void draw(Canvas c) {
        if (background != null) {
            if (mScrollX != 0 || mScrollY != 0) {
                c.translate(mScrollX, mScrollY);
                background.draw(c);
                c.translate(-mScrollX, -mScrollY);
            } else {
                background.draw(c);
            }
        }
        onDraw(c);
        dispatchDraw(c);
        if (foreground != null) {
            foreground.setBounds(0, 0, getWidth(), getHeight());
            foreground.draw(c);
        }
        onDrawForeground(c);
    }

    protected void onDraw(Canvas c) {
    }

    protected void dispatchDraw(Canvas c) {
    }

    public void onDrawForeground(Canvas c) {
    }

    public void setWillNotDraw(boolean b) {
    }

    public void setLayerType(int type, android.graphics.Paint p) {
    }

    public void setDrawingCacheEnabled(boolean b) {
    }

    public Drawable getBackground() {
        return background;
    }

    public void setBackground(Drawable d) {
        if (background == d) {
            return;
        }
        if (background != null) {
            background.setCallback(null);
        }
        background = d;
        if (d != null) {
            d.setCallback(this);
            d.setBounds(0, 0, getWidth(), getHeight());
            d.setState(drawableState());
            d.setHovered(hovered);
        }
        invalidate();
    }

    public void setBackgroundDrawable(Drawable d) {
        setBackground(d);
    }

    public void setBackgroundColor(int c) {
        if (background instanceof ColorDrawable) {
            ((ColorDrawable) background).setColor(c);
            invalidate();
        } else {
            setBackground(new ColorDrawable(c));
        }
    }

    public void setBackgroundResource(int id) {
        if (id == 0) {
            setBackground(null);
        } else {
            try {
                setBackground(getResources().getDrawable(id));
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void setBackgroundTintList(android.content.res.ColorStateList l) {
    }

    public Drawable getForeground() {
        return foreground;
    }

    public void setForeground(Drawable d) {
        foreground = d;
        invalidate();
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float a) {
        a = Math.max(0f, Math.min(1f, a));
        if (a != alpha) {
            alpha = a;
            invalidate();
        }
    }

    public float getTranslationX() {
        return tx;
    }

    public float getTranslationY() {
        return ty;
    }

    public void setTranslationX(float v) {
        if (v != tx) {
            tx = v;
            invalidateParent();
        }
    }

    public void setTranslationY(float v) {
        if (v != ty) {
            ty = v;
            invalidateParent();
        }
    }

    public void setTranslationZ(float v) {
    }

    public float getTranslationZ() {
        return 0;
    }

    public void setElevation(float v) {
    }

    public float getElevation() {
        return 0;
    }

    public void setZ(float v) {
    }

    public float getScaleX() {
        return sx;
    }

    public float getScaleY() {
        return sy;
    }

    public void setScaleX(float v) {
        if (v != sx) {
            sx = v;
            invalidateParent();
        }
    }

    public void setScaleY(float v) {
        if (v != sy) {
            sy = v;
            invalidateParent();
        }
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float v) {
        if (v != rotation) {
            rotation = v;
            invalidateParent();
        }
    }

    public void setPivotX(float v) {
        pivotX = v;
    }

    public void setPivotY(float v) {
        pivotY = v;
    }

    public float getPivotX() {
        return Float.isNaN(pivotX) ? getWidth() / 2f : pivotX;
    }

    public float getPivotY() {
        return Float.isNaN(pivotY) ? getHeight() / 2f : pivotY;
    }

    boolean hasTransform() {
        return sx != 1f || sy != 1f || rotation != 0f;
    }

    public ViewPropertyAnimator animate() {
        if (animator == null) {
            animator = new ViewPropertyAnimator(this);
        }
        return animator;
    }

    public void clearAnimation() {
        if (animator != null) {
            animator.cancel();
        }
    }

    public void setOutlineProvider(ViewOutlineProvider p) {
        outlineProvider = p;
    }

    public ViewOutlineProvider getOutlineProvider() {
        return outlineProvider;
    }

    public void setClipToOutline(boolean b) {
        clipToOutline = b;
    }

    public boolean getClipToOutline() {
        return clipToOutline;
    }

    /** Forma de recorte de la vista (si recorta a su contorno), o null. */
    java.awt.Shape clipShape() {
        if (!clipToOutline || outlineProvider == null) {
            return null;
        }
        Outline o = new Outline();
        outlineProvider.getOutline(this, o);
        return o.shape;
    }

    // ------------------------------------------------------------ estado

    public int getVisibility() {
        return visibility;
    }

    public void setVisibility(int v) {
        if (v != visibility) {
            int old = visibility;
            visibility = v;
            if (old == GONE || v == GONE) {
                requestLayout();
            }
            invalidateParent();
            onVisibilityChanged(this, v);
        }
    }

    protected void onVisibilityChanged(View changed, int v) {
    }

    protected void onWindowVisibilityChanged(int v) {
    }

    public boolean isShown() {
        View v = this;
        while (true) {
            if (v.visibility != VISIBLE) {
                return false;
            }
            if (v.mParent == null) {
                return v.layer != null;
            }
            v = v.mParent;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        if (e != enabled) {
            enabled = e;
            refreshDrawableState();
            invalidate();
        }
    }

    public boolean isClickable() {
        return clickable;
    }

    public void setClickable(boolean c) {
        clickable = c;
    }

    public boolean isLongClickable() {
        return longClickable;
    }

    public void setLongClickable(boolean c) {
        longClickable = c;
    }

    public void setContextClickable(boolean c) {
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean p) {
        setPressedInternal(p);
    }

    private void setPressedInternal(boolean p) {
        if (p != pressed) {
            pressed = p;
            refreshDrawableState();
            dispatchSetPressed(p);
        }
    }

    protected void dispatchSetPressed(boolean p) {
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean s) {
        if (s != selected) {
            selected = s;
            refreshDrawableState();
            invalidate();
        }
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean a) {
        if (a != activated) {
            activated = a;
            refreshDrawableState();
            invalidate();
        }
    }

    public boolean isHovered() {
        return hovered;
    }

    public void setHovered(boolean h) {
        if (h != hovered) {
            hovered = h;
            if (background != null && background.setHovered(h)) {
                invalidate();
            }
            onHoverChanged(h);
        }
    }

    public void onHoverChanged(boolean h) {
    }

    protected int[] drawableState() {
        int[] s = new int[5];
        int n = 0;
        if (enabled) {
            s[n++] = android.R.attr.state_enabled;
        }
        if (pressed) {
            s[n++] = android.R.attr.state_pressed;
        }
        if (selected) {
            s[n++] = android.R.attr.state_selected;
        }
        if (activated) {
            s[n++] = android.R.attr.state_activated;
        }
        return java.util.Arrays.copyOf(s, n);
    }

    public final int[] getDrawableState() {
        return drawableState();
    }

    public void refreshDrawableState() {
        boolean ch = background != null && background.setState(drawableState());
        drawableStateChanged();
        if (ch) {
            invalidate();
        }
    }

    protected void drawableStateChanged() {
    }

    public void jumpDrawablesToCurrentState() {
    }

    public void setStateListAnimator(Object a) {
    }

    // ------------------------------------------------------------ foco y teclado

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean f) {
        focusable = f;
    }

    public void setFocusable(int f) {
        focusable = f != 0;
    }

    public void setFocusableInTouchMode(boolean f) {
    }

    public boolean requestFocus() {
        return false;
    }

    public boolean requestFocus(int dir) {
        return requestFocus();
    }

    public void clearFocus() {
    }

    public boolean hasFocus() {
        return false;
    }

    public boolean isFocused() {
        return false;
    }

    public View findFocus() {
        return null;
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        onFocus = l;
    }

    protected void fireFocus(boolean has) {
        if (onFocus != null) {
            onFocus.onFocusChange(this, has);
        }
    }

    public void setOnKeyListener(OnKeyListener l) {
    }

    public boolean onKeyDown(int code, KeyEvent e) {
        return false;
    }

    public boolean onKeyUp(int code, KeyEvent e) {
        return false;
    }

    public boolean dispatchKeyEvent(KeyEvent e) {
        return false;
    }

    // ------------------------------------------------------------ toques

    public void setOnClickListener(OnClickListener l) {
        if (l != null) {
            clickable = true;
        }
        onClick = l;
    }

    public boolean hasOnClickListeners() {
        return onClick != null;
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        if (l != null) {
            longClickable = true;
        }
        onLongClick = l;
    }

    public void setOnTouchListener(OnTouchListener l) {
        onTouch = l;
    }

    public void setOnGenericMotionListener(OnGenericMotionListener l) {
        onGeneric = l;
    }

    public boolean performClick() {
        if (onClick != null) {
            onClick.onClick(this);
            return true;
        }
        return false;
    }

    public boolean callOnClick() {
        return performClick();
    }

    public boolean performLongClick() {
        return onLongClick != null && onLongClick.onLongClick(this);
    }

    public boolean performHapticFeedback(int kind) {
        return false;
    }

    public boolean performHapticFeedback(int kind, int flags) {
        return false;
    }

    public void setHapticFeedbackEnabled(boolean b) {
    }

    public void setSoundEffectsEnabled(boolean b) {
    }

    public void playSoundEffect(int s) {
    }

    public boolean dispatchTouchEvent(MotionEvent e) {
        if (onTouch != null && enabled && onTouch.onTouch(this, e)) {
            return true;
        }
        return onTouchEvent(e);
    }

    public boolean onTouchEvent(MotionEvent e) {
        if (!clickable && !longClickable) {
            return false;
        }
        if (!enabled) {
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                longPressFired = false;
                setPressedInternal(true);
                if (longClickable) {
                    longPress = new Runnable() {
                        @Override
                        public void run() {
                            if (pressed && longPress == this) {
                                if (performLongClick()) {
                                    longPressFired = true;
                                    setPressedInternal(false);
                                }
                            }
                        }
                    };
                    desk.Main.H.postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
                }
                break;
            case MotionEvent.ACTION_MOVE: {
                int slop = Math.round(10 * desk.Rt.density);
                float x = e.getX();
                float y = e.getY();
                if (x < -slop || y < -slop || x > getWidth() + slop || y > getHeight() + slop) {
                    cancelLongPress();
                    setPressedInternal(false);
                } else if (Math.abs(x - downX) > slop || Math.abs(y - downY) > slop) {
                    cancelLongPress();
                }
                break;
            }
            case MotionEvent.ACTION_UP:
                cancelLongPress();
                if (pressed && !longPressFired) {
                    setPressedInternal(false);
                    performClick();
                } else {
                    setPressedInternal(false);
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                setPressedInternal(false);
                break;
            default:
                break;
        }
        return true;
    }

    public void cancelLongPress() {
        if (longPress != null) {
            desk.Main.H.removeCallbacks(longPress);
            longPress = null;
        }
    }

    public boolean onGenericMotionEvent(MotionEvent e) {
        return false;
    }

    public boolean dispatchGenericMotionEvent(MotionEvent e) {
        if (onGeneric != null && onGeneric.onGenericMotion(this, e)) {
            return true;
        }
        return onGenericMotionEvent(e);
    }

    /** Escritorio: clic derecho = pulsación larga. x, y en coordenadas de esta vista. */
    public boolean dispatchContextClick(float x, float y) {
        return longClickable && enabled && performLongClick();
    }

    /** Escritorio: rueda del ratón. Devuelve true si alguien la usó. */
    public boolean dispatchWheel(float x, float y, float dx, float dy) {
        MotionEvent ev = MotionEvent.obtain(0, android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_SCROLL,
                x, y, 0);
        ev.setScroll(-dx / (56f * desk.Rt.density), -dy / (56f * desk.Rt.density));
        if (dispatchGenericMotionEvent(ev)) {
            return true;
        }
        return onWheel(dx, dy);
    }

    protected boolean onWheel(float dx, float dy) {
        return false;
    }

    /** Escritorio: la vista pulsable más profunda bajo el ratón (para el cursor y el resalte). */
    public View findHover(float x, float y) {
        return enabled && (clickable || isTextCursor()) ? this : null;
    }

    public boolean isTextCursor() {
        return false;
    }

    boolean hit(float px, float py) {
        return px >= mLeft + tx && px < mRight + tx && py >= mTop + ty && py < mBottom + ty;
    }

    // ------------------------------------------------------------ desplazamiento

    public int getScrollX() {
        return mScrollX;
    }

    public int getScrollY() {
        return mScrollY;
    }

    public void scrollTo(int x, int y) {
        if (x != mScrollX || y != mScrollY) {
            int ox = mScrollX;
            int oy = mScrollY;
            mScrollX = x;
            mScrollY = y;
            onScrollChanged(x, y, ox, oy);
            if (onScroll != null) {
                onScroll.onScrollChange(this, x, y, ox, oy);
            }
            invalidate();
        }
    }

    public void scrollBy(int dx, int dy) {
        scrollTo(mScrollX + dx, mScrollY + dy);
    }

    protected void onScrollChanged(int l, int t, int ol, int ot) {
    }

    public void setOnScrollChangeListener(OnScrollChangeListener l) {
        onScroll = l;
    }

    public boolean canScrollVertically(int dir) {
        return false;
    }

    public boolean canScrollHorizontally(int dir) {
        return false;
    }

    public void setVerticalScrollBarEnabled(boolean b) {
    }

    public void setHorizontalScrollBarEnabled(boolean b) {
    }

    public void setScrollBarStyle(int s) {
    }

    public void setVerticalScrollbarThumbDrawable(Drawable d) {
    }

    public void setVerticalScrollbarTrackDrawable(Drawable d) {
    }

    public void setScrollBarSize(int px) {
    }

    public void setScrollBarDefaultDelayBeforeFade(int ms) {
    }

    public void setScrollBarFadeDuration(int ms) {
    }

    public void setScrollbarFadingEnabled(boolean b) {
    }

    public void setVerticalFadingEdgeEnabled(boolean b) {
    }

    public void setFadingEdgeLength(int l) {
    }

    public void setOverScrollMode(int m) {
    }

    public void setNestedScrollingEnabled(boolean b) {
    }

    // ------------------------------------------------------------ varios

    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l) {
        onInsets = l;
        if (l != null && layer != null) {
            l.onApplyWindowInsets(this, WindowInsets.ZERO);
        }
    }

    public void requestApplyInsets() {
        if (onInsets != null) {
            onInsets.onApplyWindowInsets(this, WindowInsets.ZERO);
        }
    }

    public WindowInsets getRootWindowInsets() {
        return WindowInsets.ZERO;
    }

    public void setFitsSystemWindows(boolean b) {
    }

    public void setSystemUiVisibility(int f) {
    }

    public int getSystemUiVisibility() {
        return 0;
    }

    public void setKeepScreenOn(boolean b) {
    }

    public void setContentDescription(CharSequence c) {
        contentDescription = c;
    }

    public CharSequence getContentDescription() {
        return contentDescription;
    }

    public void setImportantForAccessibility(int m) {
    }

    public void announceForAccessibility(CharSequence c) {
    }

    public void setTooltipText(CharSequence c) {
    }

    public void setLayoutDirection(int d) {
    }

    public int getLayoutDirection() {
        return LAYOUT_DIRECTION_LTR;
    }

    public void setTextAlignment(int a) {
    }

    public void setSaveEnabled(boolean b) {
    }

    public void setDuplicateParentStateEnabled(boolean b) {
    }

    public void bringToFront() {
        if (mParent != null) {
            mParent.bringChildToFront(this);
        }
    }

    public android.view.Display getDisplay() {
        return new android.view.Display(mContext);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this));
    }
}
