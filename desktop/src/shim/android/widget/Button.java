package android.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;

public class Button extends TextView {
    public Button(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        setGravity(Gravity.CENTER);
        setAllCaps(true);
        setTypeface(Typeface.DEFAULT_BOLD);
        setMinHeight(Math.round(48 * d));
        setMinWidth(Math.round(88 * d));
        setPadding(Math.round(12 * d), 0, Math.round(12 * d), 0);
    }

    public Button(Context c, Object attrs) {
        this(c);
    }
}
