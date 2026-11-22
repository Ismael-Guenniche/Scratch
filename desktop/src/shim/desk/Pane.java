package desk;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** Zona de la ventana que muestra una pila de actividades y sus capas (diálogos, menús, avisos). */
public class Pane extends JComponent {
    public final String name;
    public final ArrayList<android.app.Activity> stack = new ArrayList<android.app.Activity>();
    private final ArrayList<Layer> layers = new ArrayList<Layer>();
    private Layer capture;
    private long downTime;
    private View hover;
    private boolean scheduled;
    private boolean inLayout;
    private int lastW = -1;
    private int lastH = -1;
    /** Color opaco bajo el fondo de la actividad (el "fondo de pantalla" del escritorio). */
    public int base = 0xFF101011;
    public Shell shell;
    /** Escala propia del panel (el del reproductor se encoge si la ventana es baja, para que quepa todo). */
    public float scale = 1f;
    /** Panel ancho: sus pantallas usan el diseño apaisado aunque sea más alto que ancho. */
    public boolean wide;

    public Pane(String name) {
        this.name = name;
        setLayout(null);
        setOpaque(true);
        setFocusable(true);
        java.awt.event.MouseAdapter ma = new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                press(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                drag(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                release(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                hover(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHover(null);
            }

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                wheel(e);
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
        addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (shell != null && shell.key(Pane.this, e, true)) {
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (shell != null && shell.key(Pane.this, e, false)) {
                    e.consume();
                }
            }
        });
    }

    // ------------------------------------------------------------ capas

    public boolean has(Layer l) {
        return layers.contains(l);
    }

    public void add(Layer l) {
        if (layers.contains(l)) {
            return;
        }
        l.pane = this;
        l.layoutDirty = true;
        // Los avisos siempre arriba del todo; lo demás, bajo ellos.
        int at = layers.size();
        if (l.kind != Layer.TOAST) {
            while (at > 0 && layers.get(at - 1).kind == Layer.TOAST) {
                at--;
            }
        }
        if (l.kind == Layer.ACTIVITY) {
            at = 0;
            while (at < layers.size() && layers.get(at).kind == Layer.ACTIVITY) {
                at++;
            }
        }
        layers.add(at, l);
        l.root.attach(l);
        schedule();
        repaint();
    }

    public void remove(Layer l) {
        if (!layers.remove(l)) {
            return;
        }
        if (capture == l) {
            capture = null;
        }
        if (hover != null && hover.layer() == l) {
            hover = null;
        }
        l.root.detach();
        l.pane = null;
        repaint();
    }

    /** Cierra los diálogos y menús de una actividad. */
    public void removeOwned(Object owner) {
        for (Layer l : new ArrayList<Layer>(layers)) {
            if (l.owner == owner && l.kind != Layer.ACTIVITY) {
                remove(l);
            }
        }
    }

    public Layer top() {
        for (int i = layers.size() - 1; i >= 0; i--) {
            if (layers.get(i).kind != Layer.TOAST) {
                return layers.get(i);
            }
        }
        return null;
    }

    public android.app.Activity topActivity() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    /** Cierra la capa de arriba si es un diálogo o menú. Devuelve true si cerró algo. */
    public boolean dismissTopOverlay() {
        Layer l = top();
        if (l != null && l.kind != Layer.ACTIVITY && l.onOutside != null) {
            l.onOutside.run();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------ recolocar y pintar

    public void schedule() {
        if (scheduled) {
            return;
        }
        scheduled = true;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                scheduled = false;
                ensureLayout();
                repaint();
            }
        });
    }

    public void ensureLayout() {
        if (inLayout) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        boolean resized = w != lastW || h != lastH;
        inLayout = true;
        try {
            if (resized) {
                lastW = w;
                lastH = h;
                if (shell != null) {
                    shell.paneResized(this);
                }
            }
            // Dos pasadas como mucho: lo que se pida recolocar durante la primera se atiende en la segunda.
            for (int pass = 0; pass < 2; pass++) {
                boolean any = false;
                for (Layer l : new ArrayList<Layer>(layers)) {
                    if ((l.layoutDirty || resized) && l.pane == this) {
                        l.layoutDirty = false;
                        any = true;
                        try {
                            l.place(w, h);
                        } catch (RuntimeException e) {
                            Rt.log(e);
                            if (Rt.DEBUG) {
                                throw e;
                            }
                        }
                    }
                }
                resized = false;
                if (!any) {
                    break;
                }
            }
        } finally {
            inLayout = false;
        }
        boolean again = false;
        for (Layer l : layers) {
            again |= l.layoutDirty;
        }
        if (again) {
            schedule();
        }
    }

    @Override
    public void setBounds(int x, int y, int w, int h) {
        boolean ch = w != getWidth() || h != getHeight();
        super.setBounds(x, y, w, h);
        if (ch) {
            ensureLayout();
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        ensureLayout();
        Graphics2D g = (Graphics2D) g0.create();
        try {
            int w = getWidth();
            int h = getHeight();
            g.setColor(new java.awt.Color(base, false));
            g.fillRect(0, 0, w, h);
            Canvas c = new Canvas(g, w, h);
            for (Layer l : new ArrayList<Layer>(layers)) {
                if (l.dim > 0) {
                    g.setColor(new java.awt.Color(0f, 0f, 0f, Math.min(1f, l.dim)));
                    g.fillRect(0, 0, w, h);
                }
                int n = c.save();
                c.translate(l.x, l.y);
                if (l.alpha < 1f) {
                    c.mulAlpha(l.alpha);
                }
                try {
                    l.root.draw(c);
                } catch (RuntimeException e) {
                    Rt.log(e);
                    if (Rt.DEBUG) {
                        throw e;
                    }
                }
                c.restoreToCount(n);
            }
        } finally {
            g.dispose();
        }
    }

    // ------------------------------------------------------------ ratón

    private Layer layerAt(int x, int y, boolean forPress) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Layer l = layers.get(i);
            if (!l.touchable) {
                continue;
            }
            if (x >= l.x && x < l.x + l.w && y >= l.y && y < l.y + l.h) {
                return l;
            }
            if (l.modal) {
                if (forPress && l.onOutside != null) {
                    l.onOutside.run();
                }
                return null;
            }
        }
        return null;
    }

    private void send(Layer l, int action, MouseEvent e) {
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(downTime, now, action, e.getX() - l.x, e.getY() - l.y, 0);
        ev.setRaw(e.getX(), e.getY());
        try {
            l.root.dispatchTouchEvent(ev);
        } catch (RuntimeException ex) {
            Rt.log(ex);
            if (Rt.DEBUG) {
                throw ex;
            }
        }
    }

    private void press(MouseEvent e) {
        if (shell != null) {
            shell.focused(this);
        }
        desk.Peers.blurIfOutside(this, e.getX(), e.getY());
        if (!hasFocus() && !desk.Peers.anyFocused()) {
            requestFocusInWindow();
        }
        int b = e.getButton();
        if (b == MouseEvent.BUTTON1) {
            capture = layerAt(e.getX(), e.getY(), true);
            if (capture != null) {
                downTime = SystemClock.uptimeMillis();
                send(capture, MotionEvent.ACTION_DOWN, e);
            }
        } else if (b == MouseEvent.BUTTON3) {
            Layer l = layerAt(e.getX(), e.getY(), true);
            if (l != null) {
                l.root.dispatchContextClick(e.getX() - l.x, e.getY() - l.y);
            }
        } else if (b >= 4 && shell != null) {
            shell.back(this);
        }
    }

    private void drag(MouseEvent e) {
        if (capture != null && SwingUtilities.isLeftMouseButton(e)) {
            send(capture, MotionEvent.ACTION_MOVE, e);
        }
    }

    private void release(MouseEvent e) {
        if (capture != null && e.getButton() == MouseEvent.BUTTON1) {
            Layer l = capture;
            capture = null;
            if (l.pane == this) {
                send(l, MotionEvent.ACTION_UP, e);
            }
            hover(e.getX(), e.getY());
        }
    }

    private void wheel(MouseWheelEvent e) {
        Layer l = layerAt(e.getX(), e.getY(), false);
        if (l == null) {
            return;
        }
        float step = (float) e.getPreciseWheelRotation() * 56f * Rt.density;
        boolean horizontal = e.isShiftDown();
        l.root.dispatchWheel(e.getX() - l.x, e.getY() - l.y, horizontal ? step : 0, horizontal ? 0 : step);
        hover(e.getX(), e.getY());
    }

    private void hover(int x, int y) {
        if (capture != null) {
            return;
        }
        Layer l = layerAt(x, y, false);
        View v = l == null ? null : l.root.findHover(x - l.x, y - l.y);
        setHover(v);
    }

    private void setHover(View v) {
        if (v == hover) {
            return;
        }
        if (hover != null) {
            hover.setHovered(false);
        }
        hover = v;
        if (v != null) {
            v.setHovered(true);
        }
        setCursor(v == null ? Cursor.getDefaultCursor()
                : Cursor.getPredefinedCursor(v.isTextCursor() ? Cursor.TEXT_CURSOR : Cursor.HAND_CURSOR));
    }
}
