package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends MarginLayoutParams {
        public static final int UNSPECIFIED_GRAVITY = -1;
        public int gravity = UNSPECIFIED_GRAVITY;

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int gravity) {
            super(w, h);
            this.gravity = gravity;
        }

        public LayoutParams(ViewGroup.LayoutParams o) {
            super(o);
            if (o instanceof LayoutParams) {
                gravity = ((LayoutParams) o).gravity;
            } else if (o instanceof LinearLayout.LayoutParams) {
                gravity = ((LinearLayout.LayoutParams) o).gravity;
            }
        }
    }

    public FrameLayout(Context c) {
        super(c);
    }

    public FrameLayout(Context c, Object attrs) {
        super(c);
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    public void setForegroundGravity(int g) {
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        boolean exact = MeasureSpec.getMode(wSpec) == MeasureSpec.EXACTLY
                && MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY;
        int maxW = 0;
        int maxH = 0;
        java.util.ArrayList<View> match = null;
        for (View v : mChildren) {
            if (v.getVisibility() == GONE) {
                continue;
            }
            measureChildWithMargins(v, wSpec, 0, hSpec, 0);
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            maxW = Math.max(maxW, v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            maxH = Math.max(maxH, v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            if (!exact && (lp.width == ViewGroup.LayoutParams.MATCH_PARENT
                    || lp.height == ViewGroup.LayoutParams.MATCH_PARENT)) {
                if (match == null) {
                    match = new java.util.ArrayList<View>();
                }
                match.add(v);
            }
        }
        maxW = Math.max(maxW + mPaddingLeft + mPaddingRight, getSuggestedMinimumWidth());
        maxH = Math.max(maxH + mPaddingTop + mPaddingBottom, getSuggestedMinimumHeight());
        int w = resolveSize(maxW, wSpec);
        int h = resolveSize(maxH, hSpec);
        setMeasuredDimension(w, h);
        if (match != null) {
            for (View v : match) {
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                int cw = lp.width == ViewGroup.LayoutParams.MATCH_PARENT
                        ? MeasureSpec.makeMeasureSpec(Math.max(0, w - mPaddingLeft - mPaddingRight - lp.leftMargin
                                - lp.rightMargin), MeasureSpec.EXACTLY)
                        : getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight + lp.leftMargin + lp.rightMargin,
                                lp.width);
                int ch = lp.height == ViewGroup.LayoutParams.MATCH_PARENT
                        ? MeasureSpec.makeMeasureSpec(Math.max(0, h - mPaddingTop - mPaddingBottom - lp.topMargin
                                - lp.bottomMargin), MeasureSpec.EXACTLY)
                        : getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom + lp.topMargin + lp.bottomMargin,
                                lp.height);
                v.measure(cw, ch);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int pl = mPaddingLeft;
        int pt = mPaddingTop;
        int pw = r - l - mPaddingLeft - mPaddingRight;
        int ph = b - t - mPaddingTop - mPaddingBottom;
        for (View v : mChildren) {
            if (v.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            int w = v.getMeasuredWidth();
            int h = v.getMeasuredHeight();
            int g = lp.gravity == LayoutParams.UNSPECIFIED_GRAVITY ? Gravity.TOP | Gravity.LEFT : lp.gravity;
            int x;
            switch (g & Gravity.HORIZONTAL_GRAVITY_MASK) {
                case Gravity.CENTER_HORIZONTAL:
                    x = pl + (pw - w) / 2 + lp.leftMargin - lp.rightMargin;
                    break;
                case Gravity.RIGHT:
                    x = pl + pw - w - lp.rightMargin;
                    break;
                default:
                    x = pl + lp.leftMargin;
                    break;
            }
            int y;
            switch (g & Gravity.VERTICAL_GRAVITY_MASK) {
                case Gravity.CENTER_VERTICAL:
                    y = pt + (ph - h) / 2 + lp.topMargin - lp.bottomMargin;
                    break;
                case Gravity.BOTTOM:
                    y = pt + ph - h - lp.bottomMargin;
                    break;
                default:
                    y = pt + lp.topMargin;
                    break;
            }
            v.layout(x, y, x + w, y + h);
        }
    }
}
