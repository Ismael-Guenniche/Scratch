package desk;

import java.util.ArrayList;

/** Reloj de animación: ejecuta en el hilo principal lo pedido con postOnAnimation, una vez por cuadro. */
public final class Frames {
    private static final ArrayList<Runnable> queue = new ArrayList<Runnable>();
    private static javax.swing.Timer timer;
    private static int idleTicks;

    private Frames() {
    }

    public static void post(Runnable r) {
        synchronized (queue) {
            queue.add(r);
        }
        if (Rt.isMain()) {
            ensure();
        } else {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    ensure();
                }
            });
        }
    }

    public static void remove(Runnable r) {
        synchronized (queue) {
            queue.remove(r);
        }
    }

    private static void ensure() {
        if (timer == null) {
            int hz = 60;
            try {
                int r = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                        .getDisplayMode().getRefreshRate();
                if (r >= 30 && r <= 240) {
                    hz = r;
                }
            } catch (Throwable ignored) {
            }
            hz = Math.min(hz, 120);
            timer = new javax.swing.Timer(Math.max(4, 1000 / hz), new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    tick();
                }
            });
            timer.setCoalesce(true);
        }
        idleTicks = 0;
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private static void tick() {
        Runnable[] run;
        synchronized (queue) {
            run = queue.toArray(new Runnable[0]);
            queue.clear();
        }
        if (run.length == 0) {
            if (++idleTicks > 30) {
                timer.stop();
            }
            return;
        }
        idleTicks = 0;
        for (Runnable r : run) {
            try {
                r.run();
            } catch (RuntimeException e) {
                Rt.log(e);
            }
        }
    }
}
