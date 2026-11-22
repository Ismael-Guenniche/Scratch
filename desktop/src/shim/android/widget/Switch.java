package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;

public class Switch extends CompoundButton {
    private int thumbColor = 0xFFECECEC;
    private int trackColor = 0x61FFFFFF;
    private final float d;

    public Switch(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setAllCaps(false);
        setMinWidth(0);
        setMinHeight(0);
        setPadding(0, 0, 0, 0);
        setBackground(null);
    }

    public void setThumbTintList(ColorStateList l) {
        thumbColor = l.getDefaultColor();
        invalidate();
    }

    public void setTrackTintList(ColorStateList l) {
        trackColor = l.getDefaultColor();
        invalidate();
    }

    public void setThumbDrawable(android.graphics.drawable.Drawable dr) {
    }

    public void setTrackDrawable(android.graphics.drawable.Drawable dr) {
    }

    public void setSwitchMinWidth(int w) {
    }

    public void setSwitchPadding(int p) {
    }

    public void setShowText(boolean b) {
    }

    private int switchW() {
        return Math.round(44 * d);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int keep = mPaddingRight;
        mPaddingRight += switchW() + (length() > 0 ? Math.round(8 * d) : 0);
        super.onMeasure(wSpec, hSpec);
        mPaddingRight = keep;
        int h = Math.max(getMeasuredHeight(), Math.round(28 * d));
        setMeasuredDimension(getMeasuredWidth(), resolveSize(h, hSpec));
    }

    @Override
    protected void onDraw(Canvas c) {
        int keep = mPaddingRight;
        mPaddingRight += switchW() + (length() > 0 ? Math.round(8 * d) : 0);
        super.onDraw(c);
        mPaddingRight = keep;
        float a = isEnabled() ? 1f : 0.45f;
        float tw = 38 * d;
        float th = 16 * d;
        float x = getWidth() - mPaddingRight - tw - 3 * d;
        float cy = getHeight() / 2f;
        java.awt.Graphics2D g = c.g;
        g.setPaint(new java.awt.Color((Math.round((trackColor >>> 24) * a) << 24) | (trackColor & 0xFFFFFF),
                true));
        g.fill(new java.awt.geom.RoundRectangle2D.Float(x, cy - th / 2, tw, th, th, th));
        float r = 11 * d;
        float cx = isChecked() ? x + tw - r + 2 * d : x + r - 2 * d;
        g.setPaint(new java.awt.Color(0, 0, 0, 40));
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r + d, 2 * r, 2 * r));
        g.setPaint(new java.awt.Color((Math.round((thumbColor >>> 24) * a) << 24) | (thumbColor & 0xFFFFFF),
                true));
        g.fill(new java.awt.geom.Ellipse2D.Float(cx - r, cy - r, 2 * r, 2 * r));
    }
}
