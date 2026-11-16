package android.view;

import android.graphics.Outline;

public abstract class ViewOutlineProvider {
    public static final ViewOutlineProvider BACKGROUND = new ViewOutlineProvider() {
        @Override
        public void getOutline(View v, Outline o) {
            java.awt.Shape s = v.getBackground() == null ? null : v.getBackground().outlineShape();
            if (s != null) {
                o.shape = s;
            } else {
                o.setRect(0, 0, v.getWidth(), v.getHeight());
            }
        }
    };
    public static final ViewOutlineProvider BOUNDS = new ViewOutlineProvider() {
        @Override
        public void getOutline(View v, Outline o) {
            o.setRect(0, 0, v.getWidth(), v.getHeight());
        }
    };

    public abstract void getOutline(View view, Outline outline);
}
