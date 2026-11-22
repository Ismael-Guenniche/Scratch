package android.app;

import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

public class Dialog implements DialogInterface {
    private final Context ctx;
    private final Window window;
    private boolean cancelable = true;
    private boolean outside = true;
    private OnDismissListener onDismiss;
    private OnCancelListener onCancel;
    private OnShowListener onShow;
    private desk.Layer layer;

    public Dialog(Context c) {
        this(c, 0);
    }

    public Dialog(Context c, int theme) {
        ctx = c;
        window = new Window(c);
        window.layoutW = ViewGroup.LayoutParams.WRAP_CONTENT;
        window.layoutH = ViewGroup.LayoutParams.WRAP_CONTENT;
        window.getAttributes().width = ViewGroup.LayoutParams.WRAP_CONTENT;
        window.getAttributes().height = ViewGroup.LayoutParams.WRAP_CONTENT;
    }

    public Context getContext() {
        return ctx;
    }

    public Window getWindow() {
        return window;
    }

    public boolean requestWindowFeature(int f) {
        return true;
    }

    public void setContentView(View v) {
        window.setContentView(v, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setContentView(View v, ViewGroup.LayoutParams lp) {
        window.setContentView(v, lp);
    }

    public <T extends View> T findViewById(int id) {
        return window.findViewById(id);
    }

    public void setTitle(CharSequence t) {
    }

    public void setCancelable(boolean b) {
        cancelable = b;
    }

    public void setCanceledOnTouchOutside(boolean b) {
        outside = b;
    }

    public void setOnDismissListener(OnDismissListener l) {
        onDismiss = l;
    }

    public void setOnCancelListener(OnCancelListener l) {
        onCancel = l;
    }

    public void setOnShowListener(OnShowListener l) {
        onShow = l;
    }

    public void setOnKeyListener(OnKeyListener l) {
    }

    public void setOwnerActivity(Activity a) {
    }

    public boolean isShowing() {
        return layer != null && layer.isShowing();
    }

    public void create() {
    }

    public void show() {
        if (isShowing()) {
            return;
        }
        final desk.Pane pane = ctx instanceof Activity && ((Activity) ctx).pane() != null
                ? ((Activity) ctx).pane() : desk.Shell.toastPane(ctx);
        if (pane == null) {
            return;
        }
        final View decor = window.getDecorView();
        layer = new desk.Layer(decor, desk.Layer.DIALOG) {
            @Override
            public void place(int paneW, int paneH) {
                float d = desk.Rt.density;
                int margin = Math.round(16 * d);
                int maxW = Math.min(paneW - 2 * margin, Math.round(560 * d));
                int maxH = paneH - 2 * margin;
                int lw = window.layoutW;
                int lh = window.layoutH;
                int ws = lw == ViewGroup.LayoutParams.MATCH_PARENT
                        ? View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.EXACTLY)
                        : lw > 0 ? View.MeasureSpec.makeMeasureSpec(Math.min(lw, maxW), View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST);
                int hs = lh == ViewGroup.LayoutParams.MATCH_PARENT
                        ? View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.EXACTLY)
                        : lh > 0 ? View.MeasureSpec.makeMeasureSpec(Math.min(lh, maxH), View.MeasureSpec.EXACTLY)
                        : View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST);
                decor.measure(ws, hs);
                w = decor.getMeasuredWidth();
                h = decor.getMeasuredHeight();
                int g = window.getAttributes().gravity;
                if (g == 0) {
                    g = Gravity.CENTER;
                }
                x = Math.max(0, Gravity.offX(g, paneW, w));
                y = Math.max(0, Gravity.offY(g, paneH, h));
                if ((g & Gravity.VERTICAL_GRAVITY_MASK) == Gravity.BOTTOM) {
                    y = Math.max(0, y - margin);
                }
                decor.layout(0, 0, w, h);
            }
        };
        window.layer = layer;
        layer.owner = ctx instanceof Activity ? ctx : null;
        layer.dim = window.getAttributes().dimAmount;
        layer.modal = true;
        layer.onOutside = new Runnable() {
            @Override
            public void run() {
                if (cancelable && outside) {
                    cancel();
                }
            }
        };
        desk.Peers.blur();
        pane.add(layer);
        if (onShow != null) {
            onShow.onShow(this);
        }
    }

    public void hide() {
        if (layer != null && layer.pane != null) {
            layer.pane.remove(layer);
        }
    }

    @Override
    public void dismiss() {
        desk.Layer l = layer;
        if (l == null) {
            return;
        }
        layer = null;
        window.layer = null;
        if (l.pane != null) {
            l.pane.remove(l);
        }
        if (onDismiss != null) {
            onDismiss.onDismiss(this);
        }
    }

    @Override
    public void cancel() {
        if (layer == null) {
            return;
        }
        if (onCancel != null) {
            onCancel.onCancel(this);
        }
        dismiss();
    }

    public void onBackPressed() {
        if (cancelable) {
            cancel();
        }
    }
}
