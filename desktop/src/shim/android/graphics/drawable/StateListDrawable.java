package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;
import java.util.ArrayList;

public class StateListDrawable extends Drawable {
    private final ArrayList<int[]> specs = new ArrayList<int[]>();
    private final ArrayList<Drawable> items = new ArrayList<Drawable>();
    private Drawable current;

    /** ¿El estado cumple la especificación? (negativo = debe faltar). */
    public static boolean matches(int[] spec, int[] state) {
        if (spec == null) {
            return true;
        }
        for (int want : spec) {
            if (want == 0) {
                continue;
            }
            boolean must = want > 0;
            int id = Math.abs(want);
            boolean has = false;
            if (state != null) {
                for (int s : state) {
                    if (s == id) {
                        has = true;
                        break;
                    }
                }
            }
            if (has != must) {
                return false;
            }
        }
        return true;
    }

    public void addState(int[] spec, Drawable d) {
        specs.add(spec);
        items.add(d);
        if (d != null) {
            d.setBounds(getBounds());
        }
        pick(getState());
    }

    private boolean pick(int[] s) {
        Drawable was = current;
        current = null;
        for (int i = 0; i < specs.size(); i++) {
            if (matches(specs.get(i), s)) {
                current = items.get(i);
                break;
            }
        }
        return was != current;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    protected boolean onStateChange(int[] s) {
        return pick(s);
    }

    @Override
    protected void onBoundsChange(Rect b) {
        for (Drawable d : items) {
            if (d != null) {
                d.setBounds(b);
            }
        }
    }

    @Override
    public Drawable getCurrent() {
        return current;
    }

    @Override
    public void draw(Canvas c) {
        if (current != null) {
            current.draw(c);
        } else if (hovered) {
            // Escritorio: el estado "pulsado" a media intensidad como resalte al pasar el ratón.
            for (int i = 0; i < specs.size(); i++) {
                int[] sp = specs.get(i);
                if (sp != null && sp.length == 1 && sp[0] == android.R.attr.state_pressed && items.get(i) != null) {
                    int n = c.save();
                    c.mulAlpha(0.5f);
                    items.get(i).draw(c);
                    c.restoreToCount(n);
                    break;
                }
            }
        }
    }
}
