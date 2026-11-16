package desk;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * La ventana del escritorio: dos paneles lado a lado (cada uno con su pila de actividades) y un tercero
 * a pantalla completa que los tapa cuando tiene algo. Hace de "sistema" para las actividades.
 */
public final class Shell {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int FULL = 2;

    public interface Config {
        /** En qué panel se abre cada actividad. */
        int paneFor(Class<?> activity);

        /** ¿El panel derecho va en ancho (mesa de DJ a dos columnas)? */
        boolean rightWide();

        /** Ancho máximo del contenido en dp (0 = todo el panel). */
        int maxWidthDp(Class<?> activity);

        /** Atajos de teclado globales. Devuelve true si la tecla se usó. */
        boolean key(KeyEvent e, boolean down);

        /** La ventana se cierra: guardar lo que haga falta. */
        void onExit();
    }

    private static Shell I;
    private static Application app;
    private static final Resources APP_RES = new Resources();
    /** Lo llama PackageManager cuando cambia un componente (el icono elegido en Ajustes). */
    public static volatile Runnable onComponentChange;

    private final Config cfg;
    private final JFrame frame;
    private final JPanel root;
    private final Pane left = new Pane("left");
    private final Pane right = new Pane("right");
    private final Pane full = new Pane("full");
    private Pane focused;
    private javax.swing.Timer resizeTimer;

    public static Shell get() {
        return I;
    }

    public static Application application() {
        return app;
    }

    public static Resources appResources() {
        return APP_RES;
    }

    public static int maxWidthDp(Class<?> c) {
        return I == null ? 0 : I.cfg.maxWidthDp(c);
    }

    public JFrame frame() {
        return frame;
    }

    public Pane pane(int which) {
        return which == RIGHT ? right : which == FULL ? full : left;
    }

    private Shell(String title, Config cfg, List<java.awt.Image> icons) {
        this.cfg = cfg;
        frame = new JFrame(title);
        if (icons != null && !icons.isEmpty()) {
            frame.setIconImages(icons);
        }
        root = new JPanel(new Split());
        root.setBackground(new java.awt.Color(0x101011));
        root.setOpaque(true);
        for (Pane p : new Pane[] {full, left, right}) {
            p.shell = this;
            root.add(p);
        }
        full.setVisible(false);
        frame.setContentPane(root);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }

            @Override
            public void windowActivated(WindowEvent e) {
                if (!Peers.anyFocused()) {
                    (full.isVisible() ? full : focused != null ? focused : left).requestFocusInWindow();
                }
            }
        });
        focused = left;
    }

    /** Reparto: el panel derecho (reproductor) ocupa ~38 % con límites; el izquierdo, el resto. */
    private final class Split implements LayoutManager {
        @Override
        public void addLayoutComponent(String name, java.awt.Component c) {
        }

        @Override
        public void removeLayoutComponent(java.awt.Component c) {
        }

        @Override
        public Dimension preferredLayoutSize(Container p) {
            return new Dimension(Math.round(1240 * Rt.density), Math.round(900 * Rt.density));
        }

        @Override
        public Dimension minimumLayoutSize(Container p) {
            return new Dimension(Math.round(820 * Rt.density), Math.round(600 * Rt.density));
        }

        @Override
        public void layoutContainer(Container p) {
            int w = p.getWidth();
            int h = p.getHeight();
            float d = Rt.density;
            boolean wide = cfg.rightWide();
            int rw = wide ? Math.round(Math.max(640 * d, Math.min(1000 * d, w * 0.6f)))
                    : Math.round(Math.max(380 * d, Math.min(520 * d, w * 0.38f)));
            rw = Math.min(rw, Math.max(0, w - Math.round(360 * d)));
            int gap = Math.max(1, Math.round(d));
            // El reproductor está pensado para ~900 dp de alto (640 en apaisado): si hay menos, se reduce.
            right.wide = wide && rw >= 600 * d;
            float sc = Math.max(0.72f, Math.min(1f, h / ((right.wide ? 640f : 900f) * d)));
            right.scale = Math.round(sc * 50f) / 50f;
            left.setBounds(0, 0, Math.max(0, w - rw - gap), h);
            right.setBounds(w - rw, 0, rw, h);
            full.setBounds(0, 0, w, h);
        }
    }

    // ------------------------------------------------------------ arranque

    public static void launch(final String title, final Class<? extends Application> appClass,
                              final Class<? extends Activity> leftRoot, final Class<? extends Activity> rightRoot,
                              final Config cfg, final List<java.awt.Image> icons) {
        Rt.initDirs();
        try {
            javax.swing.UIManager.setLookAndFeel(javax.swing.UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }
        float zoom = Prefs.get("desk").getFloat("zoom", 1f);
        Rt.density = Math.max(0.75f, Math.min(2f, zoom));
        I = new Shell(title, cfg, icons);
        final Shell s = I;
        android.content.SharedPreferences p = Prefs.get("desk");
        int w = p.getInt("win_w", Math.round(1240 * Rt.density));
        int h = p.getInt("win_h", Math.round(900 * Rt.density));
        java.awt.Rectangle scr = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        w = Math.max(Math.round(820 * Rt.density), Math.min(w, scr.width));
        h = Math.max(Math.round(600 * Rt.density), Math.min(h, scr.height - 40));
        s.root.setPreferredSize(new Dimension(w, h));
        s.frame.pack();
        s.frame.setMinimumSize(new Dimension(Math.round(840 * Rt.density), Math.round(640 * Rt.density)));
        if (p.contains("win_x")) {
            int x = p.getInt("win_x", 0);
            int y = p.getInt("win_y", 0);
            if (x >= scr.x - 50 && y >= scr.y - 50 && x < scr.x + scr.width - 100 && y < scr.y + scr.height - 100) {
                s.frame.setLocation(x, y);
            } else {
                s.frame.setLocationRelativeTo(null);
            }
        } else {
            s.frame.setLocationRelativeTo(null);
        }
        s.syncApp();
        try {
            app = appClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        app.onCreate();
        if (rightRoot != null) {
            s.open(rightRoot, new Intent(app, rightRoot), null, -1);
        }
        s.open(leftRoot, new Intent(app, leftRoot), null, -1);
        if (p.getBoolean("win_max", false)) {
            s.frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        s.frame.setVisible(true);
        s.left.requestFocusInWindow();
    }

    private void syncApp() {
        DisplayMetrics dm = APP_RES.getDisplayMetrics();
        dm.density = Rt.density;
        dm.scaledDensity = Rt.density;
        dm.densityDpi = Math.round(160 * Rt.density);
        dm.widthPixels = Math.max(1, root.getWidth());
        dm.heightPixels = Math.max(1, root.getHeight());
        Configuration c = APP_RES.getConfiguration();
        c.uiMode = Configuration.UI_MODE_TYPE_NORMAL
                | (Sys.isDark() ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        c.orientation = dm.widthPixels > dm.heightPixels ? Configuration.ORIENTATION_LANDSCAPE
                : Configuration.ORIENTATION_PORTRAIT;
    }

    // ------------------------------------------------------------ actividades

    public static void start(final Context from, final Intent i, final int req) {
        if (I == null || i == null) {
            return;
        }
        if (!Rt.isMain()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    start(from, i, req);
                }
            });
            return;
        }
        Activity caller = from instanceof Activity ? (Activity) from : null;
        Class<?> cls = i.targetClass();
        if (cls == null) {
            Pickers.handle(I, caller, i, req);
            return;
        }
        if (Activity.class.isAssignableFrom(cls)) {
            I.open(cls.asSubclass(Activity.class), i, caller, req);
        }
    }

    private void open(Class<? extends Activity> cls, Intent intent, Activity caller, int req) {
        Peers.blur();
        Pane p = pane(cfg.paneFor(cls));
        Activity top = p.topActivity();
        // Una sola instancia: la raíz del panel derecho, y cualquier actividad que ya esté arriba.
        if (top != null && top.getClass() == cls && req < 0) {
            top.deliverNewIntent(intent);
            return;
        }
        if (p != full && !p.stack.isEmpty() && p.stack.get(0).getClass() == cls) {
            p.stack.get(0).deliverNewIntent(intent);
            return;
        }
        Activity a;
        try {
            java.lang.reflect.Constructor<? extends Activity> k = cls.getDeclaredConstructor();
            k.setAccessible(true);
            a = k.newInstance();
        } catch (Exception e) {
            Rt.log(e);
            return;
        }
        a.attach(p, intent, caller, req);
        if (top != null) {
            top.performPause();
        }
        boolean firstFull = p == full && full.stack.isEmpty();
        if (firstFull) {
            full.setVisible(true);
            for (Pane q : new Pane[] {left, right}) {
                Activity t = q.topActivity();
                if (t != null) {
                    t.performPause();
                }
            }
            full.ensureLayout();
            a.syncMetrics();
        }
        p.stack.add(a);
        try {
            a.performCreate();
        } catch (RuntimeException e) {
            Rt.log(e);
            p.stack.remove(a);
            if (top != null) {
                top.performResume();
            }
            if (p == full && full.stack.isEmpty()) {
                closeFull();
            }
            if (Rt.DEBUG) {
                throw e;
            }
            return;
        }
        if (a.isFinishing() || a.isDestroyed()) {
            return;
        }
        if (top != null) {
            p.remove(top.layer());
        }
        p.add(a.layer());
        a.performStart();
        a.performResume();
        if (top != null) {
            top.performStop();
        }
        if (p != right) {
            focus(p);
        }
    }

    private void closeFull() {
        full.setVisible(false);
        for (Pane q : new Pane[] {left, right}) {
            Activity t = q.topActivity();
            if (t != null) {
                t.performResume();
            }
        }
        focus(left);
    }

    private void focus(Pane p) {
        focused = p;
        if (!Peers.anyFocused()) {
            p.requestFocusInWindow();
        }
    }

    public static void finish(final Activity a) {
        if (I == null) {
            return;
        }
        if (!Rt.isMain()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    finish(a);
                }
            });
            return;
        }
        I.close(a);
    }

    private void close(final Activity a) {
        if (a.isDestroyed() || a.isFinishing()) {
            return;
        }
        final Pane p = a.pane();
        int idx = p.stack.indexOf(a);
        if (idx < 0) {
            return;
        }
        if (idx == 0 && p != full && a.everResumed()) {
            // Las raíces no se cierran: la biblioteca se queda, y el reproductor se vuelve a montar.
            // (Sí pueden cerrarse antes de mostrarse: la biblioteca cede el paso a la bienvenida.)
            if (p == right) {
                recreate(a);
            }
            return;
        }
        a.markFinishing();
        boolean wasTop = idx == p.stack.size() - 1;
        if (wasTop) {
            a.performPause();
        }
        p.stack.remove(a);
        p.removeOwned(a);
        p.remove(a.layer());
        a.performStop();
        a.performDestroy();
        final Activity caller = a.callerActivity();
        if (a.requestCode() >= 0 && caller != null) {
            caller.deliverResult(a.requestCode(), a.resultCode(), a.resultData());
        }
        if (wasTop) {
            Activity next = p.topActivity();
            if (next != null) {
                p.add(next.layer());
                p.base = next.getWindow().baseColor(p.base);
                next.performRestart();
                next.performStart();
                next.performResume();
            }
        }
        if (p == full && full.stack.isEmpty()) {
            closeFull();
        } else {
            refreshOthers(p);
        }
    }

    /**
     * Las pantallas de Android se enteran de los cambios de ajustes al reanudarse. Aquí hay varias a la
     * vez, así que al cerrar una (o cambiar el tema) se "reanudan" las de los otros paneles.
     */
    public void refreshOthers(final Pane except) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (full.isVisible() && except != full) {
                    return;
                }
                for (Pane q : new Pane[] {left, right}) {
                    if (q == except) {
                        continue;
                    }
                    Activity t = q.topActivity();
                    if (t != null && t.isResumedNow() && !t.isFinishing()) {
                        t.performPause();
                        t.performResume();
                    }
                }
            }
        });
    }

    /** Vuelve a repartir los paneles (cuando cambia lo que devuelve Config.rightWide). */
    public static void relayout() {
        if (I != null) {
            I.root.revalidate();
            I.root.repaint();
        }
    }

    public static void refreshAll() {
        if (I != null) {
            I.refreshOthers(null);
        }
    }

    public static void recreate(final Activity a) {
        if (I == null) {
            return;
        }
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                I.rebuild(a);
            }
        });
    }

    private void rebuild(Activity a) {
        if (a.isDestroyed() || a.isFinishing()) {
            return;
        }
        Pane p = a.pane();
        int idx = p.stack.indexOf(a);
        if (idx < 0) {
            return;
        }
        boolean wasTop = idx == p.stack.size() - 1;
        boolean visible = wasTop && !(full.isVisible() && p != full);
        Activity b;
        try {
            java.lang.reflect.Constructor<? extends Activity> k = a.getClass().getDeclaredConstructor();
            k.setAccessible(true);
            b = k.newInstance();
        } catch (Exception e) {
            Rt.log(e);
            return;
        }
        Peers.blur();
        a.markFinishing();
        a.performPause();
        p.removeOwned(a);
        p.remove(a.layer());
        a.performStop();
        a.performDestroy();
        b.attach(p, a.getIntent(), a.callerActivity(), a.requestCode());
        p.stack.set(idx, b);
        b.performCreate();
        if (b.isFinishing()) {
            return;
        }
        if (wasTop) {
            p.add(b.layer());
            b.performStart();
            if (visible) {
                b.performResume();
            }
        }
    }

    public static void exit() {
        if (I == null) {
            System.exit(0);
            return;
        }
        final Shell s = I;
        try {
            android.content.SharedPreferences.Editor e = Prefs.get("desk").edit();
            boolean max = (s.frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
            e.putBoolean("win_max", max);
            if (!max) {
                e.putInt("win_w", s.root.getWidth()).putInt("win_h", s.root.getHeight())
                        .putInt("win_x", s.frame.getX()).putInt("win_y", s.frame.getY());
            }
            e.commit();
            for (Pane p : new Pane[] {s.full, s.left, s.right}) {
                Activity t = p.topActivity();
                if (t != null) {
                    t.performPause();
                }
            }
            s.cfg.onExit();
        } catch (RuntimeException ex) {
            Rt.log(ex);
        }
        Prefs.flushAll();
        System.exit(0);
    }

    /** Cambia el tamaño de la interfaz (Ctrl + / Ctrl −; 0 = volver al 100 %) y vuelve a montar las pantallas. */
    public static void zoomBy(float delta) {
        if (I == null) {
            return;
        }
        float z = delta == 0f ? 1f : Math.round((Rt.density + delta) * 10f) / 10f;
        z = Math.max(0.8f, Math.min(1.8f, z));
        if (z == Rt.density) {
            return;
        }
        Rt.density = z;
        Prefs.get("desk").edit().putFloat("zoom", z).apply();
        I.frame.setMinimumSize(new Dimension(Math.round(840 * z), Math.round(640 * z)));
        I.root.revalidate();
        I.root.doLayout();
        I.syncApp();
        for (Pane p : new Pane[] {I.left, I.right, I.full}) {
            for (Activity a : new java.util.ArrayList<Activity>(p.stack)) {
                a.syncMetrics();
                recreate(a);
            }
        }
    }

    public static void minimize() {
        if (I != null) {
            I.frame.setExtendedState(JFrame.ICONIFIED);
        }
    }

    public static Pane toastPane(Context c) {
        if (I == null) {
            return null;
        }
        if (I.full.isVisible()) {
            return I.full;
        }
        if (c instanceof Activity && ((Activity) c).pane() != null) {
            return ((Activity) c).pane();
        }
        return I.left;
    }

    // ------------------------------------------------------------ lo que avisan los paneles

    void focused(Pane p) {
        focused = p;
    }

    void paneResized(final Pane p) {
        syncApp();
        for (Activity a : p.stack) {
            a.syncMetrics();
        }
        // Al terminar de redimensionar se avisa a la pantalla (como un giro en Android) para que se recoloque.
        if (resizeTimer == null) {
            resizeTimer = new javax.swing.Timer(220, new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    for (Pane q : new Pane[] {left, right, full}) {
                        Activity t = q.topActivity();
                        if (t != null && !t.isFinishing() && q.isVisible()) {
                            if (Math.abs(t.scaleUsed() - q.scale) > 0.005f) {
                                for (Activity a : new java.util.ArrayList<Activity>(q.stack)) {
                                    recreate(a);
                                }
                                continue;
                            }
                            t.syncMetrics();
                            try {
                                t.performConfigurationChanged();
                            } catch (RuntimeException ex) {
                                Rt.log(ex);
                            }
                        }
                    }
                }
            });
            resizeTimer.setRepeats(false);
        }
        if (frame.isVisible()) {
            resizeTimer.restart();
        }
    }

    void back(Pane p) {
        Peers.blur();
        Pane q = full.isVisible() ? full : p;
        if (q.dismissTopOverlay()) {
            return;
        }
        if (q == right) {
            q = left;
            if (q.dismissTopOverlay()) {
                return;
            }
        }
        Activity a = q.topActivity();
        if (a != null) {
            a.performBack();
        }
    }

    private static int androidKey(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP:
                return android.view.KeyEvent.KEYCODE_DPAD_UP;
            case KeyEvent.VK_DOWN:
                return android.view.KeyEvent.KEYCODE_DPAD_DOWN;
            case KeyEvent.VK_LEFT:
                return android.view.KeyEvent.KEYCODE_DPAD_LEFT;
            case KeyEvent.VK_RIGHT:
                return android.view.KeyEvent.KEYCODE_DPAD_RIGHT;
            case KeyEvent.VK_ENTER:
                return android.view.KeyEvent.KEYCODE_ENTER;
            case KeyEvent.VK_SPACE:
                return android.view.KeyEvent.KEYCODE_SPACE;
            default:
                return 0;
        }
    }

    boolean key(Pane p, KeyEvent e, boolean down) {
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE || (e.getKeyCode() == KeyEvent.VK_LEFT && e.isAltDown())) {
            if (down) {
                back(p);
            }
            return true;
        }
        Pane q = full.isVisible() ? full : p;
        Activity a = q.topActivity();
        int code = androidKey(e);
        if (a != null && code != 0 && q.top() != null && q.top().kind == Layer.ACTIVITY) {
            if (a.dispatchKeyEvent(new android.view.KeyEvent(down ? android.view.KeyEvent.ACTION_DOWN
                    : android.view.KeyEvent.ACTION_UP, code))) {
                return true;
            }
        }
        return cfg.key(e, down);
    }

    /** Intro en un campo de texto sin oyente propio: en un diálogo, pulsa su botón principal. */
    void enterInField(android.widget.EditText e) {
        Layer l = e.layer();
        if (l == null || l.kind != Layer.DIALOG) {
            Peers.blur();
            return;
        }
        android.view.View v = lastButton(l.root);
        if (v != null) {
            v.performClick();
        }
    }

    private static android.view.View lastButton(android.view.View v) {
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = g.getChildCount() - 1; i >= 0; i--) {
                android.view.View f = lastButton(g.getChildAt(i));
                if (f != null) {
                    return f;
                }
            }
            return null;
        }
        return v instanceof android.widget.Button && !(v instanceof android.widget.CompoundButton)
                && v.getVisibility() == android.view.View.VISIBLE && v.isEnabled() && v.hasOnClickListeners()
                ? v : null;
    }
}
