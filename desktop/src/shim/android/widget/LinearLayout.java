package android.widget;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;
    public static final int SHOW_DIVIDER_NONE = 0;
    public static final int SHOW_DIVIDER_MIDDLE = 2;

    public static class LayoutParams extends MarginLayoutParams {
        public float weight;
        public int gravity = -1;

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, float weight) {
            super(w, h);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams o) {
            super(o);
            if (o instanceof LayoutParams) {
                weight = ((LayoutParams) o).weight;
                gravity = ((LayoutParams) o).gravity;
            } else if (o instanceof FrameLayout.LayoutParams) {
                gravity = ((FrameLayout.LayoutParams) o).gravity;
            }
        }
    }

    private int orientation = HORIZONTAL;
    private int gravity = Gravity.TOP | Gravity.LEFT;
    private float weightSum;
    private int totalLength;
    private boolean baselineAligned = true;

    public LinearLayout(Context c) {
        super(c);
    }

    public LinearLayout(Context c, Object attrs) {
        super(c);
    }

    public void setOrientation(int o) {
        if (o != orientation) {
            orientation = o;
            requestLayout();
        }
    }

    public int getOrientation() {
        return orientation;
    }

    public void setGravity(int g) {
        if ((g & Gravity.HORIZONTAL_GRAVITY_MASK) == 0) {
            g |= Gravity.LEFT;
        }
        if ((g & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            g |= Gravity.TOP;
        }
        if (g != gravity) {
            gravity = g;
            requestLayout();
        }
    }

    public void setHorizontalGravity(int g) {
        setGravity((gravity & Gravity.VERTICAL_GRAVITY_MASK) | (g & Gravity.HORIZONTAL_GRAVITY_MASK));
    }

    public void setVerticalGravity(int g) {
        setGravity((gravity & Gravity.HORIZONTAL_GRAVITY_MASK) | (g & Gravity.VERTICAL_GRAVITY_MASK));
    }

    public int getGravity() {
        return gravity;
    }

    public void setWeightSum(float s) {
        weightSum = s;
    }

    @Override
    public void setBaselineAligned(boolean b) {
        baselineAligned = b;
    }

    public void setShowDividers(int m) {
    }

    public void setDividerDrawable(android.graphics.drawable.Drawable d) {
    }

    public void setDividerPadding(int p) {
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return orientation == VERTICAL
                ? new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                : new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        if (orientation == VERTICAL) {
            measureVertical(wSpec, hSpec);
        } else {
            measureHorizontal(wSpec, hSpec);
        }
    }

    private void measureVertical(int wSpec, int hSpec) {
        int wMode = MeasureSpec.getMode(wSpec);
        int hMode = MeasureSpec.getMode(hSpec);
        int total = 0;
        int maxW = 0;
        float totalWeight = 0;
        boolean matchW = false;
        boolean skipped = false;
        for (View v : mChildren) {
            if (v.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            totalWeight += lp.weight;
            if (hMode == MeasureSpec.EXACTLY && lp.height == 0 && lp.weight > 0) {
                total += lp.topMargin + lp.bottomMargin;
                skipped = true;
            } else {
                int keep = lp.height;
                if (lp.height == 0 && lp.weight > 0) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                measureChildWithMargins(v, wSpec, 0, hSpec, totalWeight == 0 ? total : 0);
                lp.height = keep;
                total += v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
            if (wMode != MeasureSpec.EXACTLY && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                matchW = true;
            } else if (!(hMode == MeasureSpec.EXACTLY && lp.height == 0 && lp.weight > 0)) {
                maxW = Math.max(maxW, v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
            }
        }
        total += mPaddingTop + mPaddingBottom;
        int hSize = resolveSize(Math.max(total, getSuggestedMinimumHeight()), hSpec);
        int delta = hSize - total;
        if (skipped || (delta != 0 && totalWeight > 0)) {
            float sum = weightSum > 0 ? weightSum : totalWeight;
            total = mPaddingTop + mPaddingBottom;
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (lp.weight > 0) {
                    int share = (int) (lp.weight * delta / sum);
                    sum -= lp.weight;
                    delta -= share;
                    int h;
                    if (hMode == MeasureSpec.EXACTLY && lp.height == 0) {
                        h = share;
                    } else {
                        h = v.getMeasuredHeight() + share;
                    }
                    h = Math.max(0, h);
                    v.measure(getChildMeasureSpec(wSpec, mPaddingLeft + mPaddingRight + lp.leftMargin
                            + lp.rightMargin, lp.width), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
                }
                if (!(wMode != MeasureSpec.EXACTLY && lp.width == ViewGroup.LayoutParams.MATCH_PARENT)) {
                    maxW = Math.max(maxW, v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                }
                total += v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin;
            }
        }
        if (matchW && maxW == 0) {
            for (View v : mChildren) {
                if (v.getVisibility() != GONE) {
                    LayoutParams lp = (LayoutParams) v.getLayoutParams();
                    maxW = Math.max(maxW, v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin);
                }
            }
        }
        maxW = Math.max(maxW + mPaddingLeft + mPaddingRight, getSuggestedMinimumWidth());
        int w = resolveSize(maxW, wSpec);
        totalLength = total;
        setMeasuredDimension(w, hSize);
        if (matchW) {
            // Los hijos que piden todo el ancho se vuelven a medir con el ancho definitivo.
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    v.measure(MeasureSpec.makeMeasureSpec(Math.max(0, w - mPaddingLeft - mPaddingRight
                                    - lp.leftMargin - lp.rightMargin), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(v.getMeasuredHeight(), MeasureSpec.EXACTLY));
                }
            }
        }
    }

    private void measureHorizontal(int wSpec, int hSpec) {
        int wMode = MeasureSpec.getMode(wSpec);
        int hMode = MeasureSpec.getMode(hSpec);
        int total = 0;
        int maxH = 0;
        float totalWeight = 0;
        boolean matchH = false;
        boolean skipped = false;
        for (View v : mChildren) {
            if (v.getVisibility() == GONE) {
                continue;
            }
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            totalWeight += lp.weight;
            boolean skip = wMode == MeasureSpec.EXACTLY && lp.width == 0 && lp.weight > 0;
            if (skip) {
                total += lp.leftMargin + lp.rightMargin;
                skipped = true;
            } else {
                int keep = lp.width;
                if (lp.width == 0 && lp.weight > 0) {
                    lp.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                measureChildWithMargins(v, wSpec, totalWeight == 0 ? total : 0, hSpec, 0);
                lp.width = keep;
                total += v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            }
            if (hMode != MeasureSpec.EXACTLY && lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                matchH = true;
            } else if (!skip) {
                maxH = Math.max(maxH, v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
            }
        }
        total += mPaddingLeft + mPaddingRight;
        int wSize = resolveSize(Math.max(total, getSuggestedMinimumWidth()), wSpec);
        int delta = wSize - total;
        if (skipped || (delta != 0 && totalWeight > 0)) {
            float sum = weightSum > 0 ? weightSum : totalWeight;
            total = mPaddingLeft + mPaddingRight;
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (lp.weight > 0) {
                    int share = (int) (lp.weight * delta / sum);
                    sum -= lp.weight;
                    delta -= share;
                    int w;
                    if (wMode == MeasureSpec.EXACTLY && lp.width == 0) {
                        w = share;
                    } else {
                        w = v.getMeasuredWidth() + share;
                    }
                    w = Math.max(0, w);
                    v.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                            getChildMeasureSpec(hSpec, mPaddingTop + mPaddingBottom + lp.topMargin
                                    + lp.bottomMargin, lp.height));
                }
                if (!(hMode != MeasureSpec.EXACTLY && lp.height == ViewGroup.LayoutParams.MATCH_PARENT)) {
                    maxH = Math.max(maxH, v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                }
                total += v.getMeasuredWidth() + lp.leftMargin + lp.rightMargin;
            }
        }
        if (matchH && maxH == 0) {
            for (View v : mChildren) {
                if (v.getVisibility() != GONE) {
                    LayoutParams lp = (LayoutParams) v.getLayoutParams();
                    maxH = Math.max(maxH, v.getMeasuredHeight() + lp.topMargin + lp.bottomMargin);
                }
            }
        }
        maxH = Math.max(maxH + mPaddingTop + mPaddingBottom, getSuggestedMinimumHeight());
        int h = resolveSize(maxH, hSpec);
        totalLength = total;
        setMeasuredDimension(wSize, h);
        if (matchH) {
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                if (lp.height == ViewGroup.LayoutParams.MATCH_PARENT) {
                    v.measure(MeasureSpec.makeMeasureSpec(v.getMeasuredWidth(), MeasureSpec.EXACTLY),
                            MeasureSpec.makeMeasureSpec(Math.max(0, h - mPaddingTop - mPaddingBottom
                                    - lp.topMargin - lp.bottomMargin), MeasureSpec.EXACTLY));
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        if (orientation == VERTICAL) {
            int y = mPaddingTop + Gravity.offY(gravity, h - mPaddingTop - mPaddingBottom,
                    totalLength - mPaddingTop - mPaddingBottom);
            int space = w - mPaddingLeft - mPaddingRight;
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                int cw = v.getMeasuredWidth();
                int ch = v.getMeasuredHeight();
                int g = lp.gravity < 0 ? gravity : lp.gravity;
                int x;
                switch (g & Gravity.HORIZONTAL_GRAVITY_MASK) {
                    case Gravity.CENTER_HORIZONTAL:
                        x = mPaddingLeft + (space - cw) / 2 + lp.leftMargin - lp.rightMargin;
                        break;
                    case Gravity.RIGHT:
                        x = mPaddingLeft + space - cw - lp.rightMargin;
                        break;
                    default:
                        x = mPaddingLeft + lp.leftMargin;
                        break;
                }
                y += lp.topMargin;
                v.layout(x, y, x + cw, y + ch);
                y += ch + lp.bottomMargin;
            }
        } else {
            int x = mPaddingLeft + Gravity.offX(gravity, w - mPaddingLeft - mPaddingRight,
                    totalLength - mPaddingLeft - mPaddingRight);
            int space = h - mPaddingTop - mPaddingBottom;
            // Alineación por línea base de los textos (lo normal en una fila sin gravedad vertical).
            int maxBase = -1;
            if (baselineAligned && (gravity & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.TOP) {
                for (View v : mChildren) {
                    if (v.getVisibility() == GONE) {
                        continue;
                    }
                    LayoutParams lp = (LayoutParams) v.getLayoutParams();
                    if (lp.gravity < 0 && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                        int bl = baselineOf(v);
                        if (bl >= 0) {
                            maxBase = Math.max(maxBase, bl + lp.topMargin);
                        }
                    }
                }
            }
            for (View v : mChildren) {
                if (v.getVisibility() == GONE) {
                    continue;
                }
                LayoutParams lp = (LayoutParams) v.getLayoutParams();
                int cw = v.getMeasuredWidth();
                int ch = v.getMeasuredHeight();
                int g = lp.gravity < 0 ? gravity : lp.gravity;
                int y;
                switch (g & Gravity.VERTICAL_GRAVITY_MASK) {
                    case Gravity.CENTER_VERTICAL:
                        y = mPaddingTop + (space - ch) / 2 + lp.topMargin - lp.bottomMargin;
                        break;
                    case Gravity.BOTTOM:
                        y = mPaddingTop + space - ch - lp.bottomMargin;
                        break;
                    default:
                        y = mPaddingTop + lp.topMargin;
                        if (maxBase >= 0 && lp.gravity < 0 && lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                            int bl = baselineOf(v);
                            if (bl >= 0) {
                                y = mPaddingTop + maxBase - bl;
                            }
                        }
                        break;
                }
                x += lp.leftMargin;
                v.layout(x, y, x + cw, y + ch);
                x += cw + lp.rightMargin;
            }
        }
    }

    /** Línea base de un texto ya medido (antes de colocarlo no tiene altura; se calcula con la medida). */
    private static int baselineOf(View v) {
        if (!(v instanceof TextView)) {
            return -1;
        }
        TextView t = (TextView) v;
        android.graphics.Paint.FontMetrics fm = t.getPaint().getFontMetrics();
        if ((t.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) != Gravity.TOP) {
            float lh = fm.descent - fm.ascent;
            int space = t.getMeasuredHeight() - t.getPaddingTop() - t.getPaddingBottom();
            int lines = Math.max(1, t.getLineCount());
            float off = (t.getGravity() & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM ? space - lines * lh
                    : (space - lines * lh) / 2f;
            return Math.round(t.getPaddingTop() + Math.max(0, off) - fm.ascent);
        }
        return Math.round(t.getPaddingTop() - fm.ascent);
    }
}
