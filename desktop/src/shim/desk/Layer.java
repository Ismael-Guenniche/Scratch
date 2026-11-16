package desk;

import android.view.View;

/**
 * Una "ventana" dentro de un panel: el contenido de una actividad, un diálogo, un menú emergente o un aviso.
 * Guarda su marco (x, y, w, h) dentro del panel y avisa al panel cuando hay que recolocar o repintar.
 */
public class Layer {
    public static final int ACTIVITY = 0;
    public static final int DIALOG = 1;
    public static final int POPUP = 2;
    public static final int TOAST = 3;

    public final View root;
    public final int kind;
    public Pane pane;
    public int x;
    public int y;
    public int w;
    public int h;
    /** Oscurecido del fondo (0..1), para los diálogos. */
    public float dim;
    /** Recibe el ratón. */
    public boolean touchable = true;
    /** Un clic fuera se queda aquí (no pasa a lo de debajo) y llama a onOutside. */
    public boolean modal;
    public Runnable onOutside;
    /** La actividad a la que pertenece (para cerrarlo con ella). */
    public Object owner;
    public boolean layoutDirty = true;
    /** Opacidad global, para avisos que se desvanecen. */
    public float alpha = 1f;

    public Layer(View root, int kind) {
        this.root = root;
        this.kind = kind;
    }

    /** Calcula el marco y coloca la raíz. Los diálogos y menús lo redefinen. */
    public void place(int paneW, int paneH) {
        x = 0;
        y = 0;
        w = paneW;
        h = paneH;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
    }

    public void requestLayout() {
        layoutDirty = true;
        if (pane != null) {
            pane.schedule();
        }
    }

    public void invalidate() {
        if (pane != null) {
            pane.repaint();
        }
    }

    public void invalidate(int l, int t, int r, int b) {
        if (pane != null) {
            pane.repaint(x + l - 1, y + t - 1, r - l + 2, b - t + 2);
        }
    }

    public boolean isShowing() {
        return pane != null && pane.has(this);
    }
}
