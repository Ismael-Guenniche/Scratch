package android.widget;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;

public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;
    private static desk.Layer current;
    private final Context ctx;
    private CharSequence text;
    private int duration;
    private desk.Layer layer;

    public Toast(Context c) {
        ctx = c;
    }

    public static Toast makeText(Context c, CharSequence text, int duration) {
        Toast t = new Toast(c);
        t.text = text;
        t.duration = duration;
        return t;
    }

    public static Toast makeText(Context c, int resId, int duration) {
        return makeText(c, "", duration);
    }

    public void setText(CharSequence t) {
        text = t;
    }

    public void setDuration(int d) {
        duration = d;
    }

    public void setGravity(int g, int x, int y) {
    }

    public void setView(View v) {
    }

    public void show() {
        if (!desk.Rt.isMain()) {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    show();
                }
            });
            return;
        }
        final desk.Pane pane = desk.Shell.toastPane(ctx);
        if (pane == null || text == null || text.length() == 0) {
            return;
        }
        if (current != null && current.pane != null) {
            current.pane.remove(current);
        }
        final float d = desk.Rt.density;
        final TextView tv = new TextView(ctx.getApplicationContext());
        tv.setText(text);
        tv.setTextSize(13.5f);
        tv.setTextColor(0xFFF4F4F5);
        tv.setGravity(Gravity.CENTER);
        tv.setMaxLines(4);
        tv.setPadding(Math.round(20 * d), Math.round(12 * d), Math.round(20 * d), Math.round(12 * d));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2262628);
        bg.setCornerRadius(24 * d);
        bg.setStroke(Math.max(1, Math.round(d)), 0x33FFFFFF);
        tv.setBackground(bg);
        layer = new desk.Layer(tv, desk.Layer.TOAST) {
            @Override
            public void place(int paneW, int paneH) {
                tv.measure(View.MeasureSpec.makeMeasureSpec(Math.max(0, Math.min(paneW - Math.round(32 * d),
                                Math.round(460 * d))), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(paneH, View.MeasureSpec.AT_MOST));
                w = tv.getMeasuredWidth();
                h = tv.getMeasuredHeight();
                x = (paneW - w) / 2;
                y = paneH - h - Math.round(84 * d);
                tv.layout(0, 0, w, h);
            }
        };
        layer.touchable = false;
        current = layer;
        pane.add(layer);
        final desk.Layer mine = layer;
        desk.Main.H.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mine.pane != null) {
                    mine.pane.remove(mine);
                }
                if (current == mine) {
                    current = null;
                }
            }
        }, duration == LENGTH_LONG ? 3600 : 2200);
    }

    public void cancel() {
        if (layer != null && layer.pane != null) {
            layer.pane.remove(layer);
        }
    }
}
