package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

public class PopupWindow {
    public interface OnDismissListener {
        void onDismiss();
    }

    private View content;
    private int width;
    private int height;
    private boolean focusable;
    private boolean touchable = true;
    private boolean clipping = true;
    private Drawable background;
    private OnDismissListener onDismiss;
    private desk.Layer layer;
    private View anchor;
    private boolean dropDown;
    private int px;
    private int py;
    private int gravity;

    public PopupWindow(Context c) {
        this(null, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false);
    }

    public PopupWindow(View content, int w, int h) {
        this(content, w, h, false);
    }

    public PopupWindow(View content, int w, int h, boolean focusable) {
        this.content = content;
        width = w;
        height = h;
        this.focusable = focusable;
    }

    public void setContentView(View v) {
        content = v;
    }

    public View getContentView() {
        return content;
    }

    public void setWidth(int w) {
        width = w;
    }

    public void setHeight(int h) {
        height = h;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setFocusable(boolean b) {
        focusable = b;
    }

    public void setTouchable(boolean b) {
        touchable = b;
    }

    public void setOutsideTouchable(boolean b) {
    }

    public void setClippingEnabled(boolean b) {
        clipping = b;
    }

    public void setBackgroundDrawable(Drawable d) {
        background = d;
    }

    public void setElevation(float e) {
    }

    public void setAnimationStyle(int s) {
    }

    public void setInputMethodMode(int m) {
    }

    public void setSoftInputMode(int m) {
    }

    public void setOverlapAnchor(boolean b) {
    }

    public void setTouchInterceptor(View.OnTouchListener l) {
    }

    public void setOnDismissListener(OnDismissListener l) {
        onDismiss = l;
    }

    public boolean isShowing() {
        return layer != null && layer.isShowing();
    }

    public void showAsDropDown(View anchor) {
        showAsDropDown(anchor, 0, 0);
    }

    public void showAsDropDown(View anchor, int xoff, int yoff) {
        showAsDropDown(anchor, xoff, yoff, Gravity.TOP | Gravity.START);
    }

    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        show(anchor, true, xoff, yoff, gravity);
    }

    public void showAtLocation(View parent, int gravity, int x, int y) {
        show(parent, false, x, y, gravity);
    }

    private void show(View anchorView, boolean drop, int x, int y, int g) {
        if (isShowing() || content == null || anchorView == null) {
            return;
        }
        desk.Layer al = anchorView.getRootView().layer();
        if (al == null || al.pane == null) {
            return;
        }
        anchor = anchorView;
        dropDown = drop;
        px = x;
        py = y;
        gravity = g;
        if (background != null && content.getBackground() == null) {
            content.setBackground(background);
        }
        layer = new desk.Layer(content, desk.Layer.POPUP) {
            @Override
            public void place(int paneW, int paneH) {
                int ws = width == ViewGroup.LayoutParams.MATCH_PARENT
                        ? View.MeasureSpec.makeMeasureSpec(paneW, View.MeasureSpec.EXACTLY)
                        : width >= 0 ? View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(paneW, View.MeasureSpec.AT_MOST);
                int hs = height == ViewGroup.LayoutParams.MATCH_PARENT
                        ? View.MeasureSpec.makeMeasureSpec(paneH, View.MeasureSpec.EXACTLY)
                        : height >= 0 ? View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(paneH, View.MeasureSpec.AT_MOST);
                content.measure(ws, hs);
                w = content.getMeasuredWidth();
                h = content.getMeasuredHeight();
                int nx;
                int ny;
                if (dropDown) {
                    int[] xy = new int[2];
                    anchor.getLocationInWindow(xy);
                    nx = xy[0] + px;
                    ny = xy[1] + anchor.getHeight() + py;
                    if (ny + h > paneH && xy[1] - h - py >= 0) {
                        ny = xy[1] - h - py;
                    }
                } else if ((gravity & (Gravity.HORIZONTAL_GRAVITY_MASK | Gravity.VERTICAL_GRAVITY_MASK)) == 0) {
                    nx = px;
                    ny = py;
                } else {
                    nx = Gravity.offX(gravity, paneW, w) + ((gravity & Gravity.HORIZONTAL_GRAVITY_MASK)
                            == Gravity.RIGHT ? -px : px);
                    ny = Gravity.offY(gravity, paneH, h) + ((gravity & Gravity.VERTICAL_GRAVITY_MASK)
                            == Gravity.BOTTOM ? -py : py);
                }
                if (clipping || dropDown) {
                    nx = Math.max(0, Math.min(nx, paneW - w));
                    ny = Math.max(0, Math.min(ny, paneH - h));
                }
                this.x = nx;
                this.y = ny;
                content.layout(0, 0, w, h);
            }
        };
        layer.owner = al.owner;
        layer.touchable = touchable;
        layer.modal = focusable;
        layer.onOutside = new Runnable() {
            @Override
            public void run() {
                dismiss();
            }
        };
        al.pane.add(layer);
    }

    public void update() {
        if (layer != null) {
            layer.requestLayout();
        }
    }

    public void update(int x, int y, int w, int h) {
        px = x;
        py = y;
        if (w >= 0) {
            width = w;
        }
        if (h >= 0) {
            height = h;
        }
        update();
    }

    public void dismiss() {
        desk.Layer l = layer;
        if (l == null) {
            return;
        }
        layer = null;
        if (l.pane != null) {
            l.pane.remove(l);
        }
        if (onDismiss != null) {
            onDismiss.onDismiss();
        }
    }
}
