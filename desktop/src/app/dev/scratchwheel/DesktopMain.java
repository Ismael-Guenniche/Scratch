package dev.scratchwheel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.net.Uri;
import desk.Shell;
import java.awt.Image;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Arranque de Scratch en Windows (y cualquier escritorio con Java). Las pantallas son las mismas clases
 * que en Android, dibujadas por la capa de desktop/src/shim: biblioteca a la izquierda, rueda a la derecha.
 */
public final class DesktopMain {
    private DesktopMain() {
    }

    public static void main(final String[] args) {
        System.setProperty("sun.awt.noerasebackground", "true");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        desk.Rt.appName = "Scratch";
        desk.Rt.packageName = "dev.scratchwheel";
        readVersion();
        if (args.length >= 2 && "--selftest".equals(args[0])) {
            selfTest(args[1]);
            return;
        }
        if (desk.Single.forward(args)) {
            System.exit(0);
            return;
        }
        desk.Single.listen(new desk.Single.Receiver() {
            @Override
            public void onArgs(final String[] a) {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        incoming(a);
                    }
                });
            }
        });
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                start(args);
            }
        });
    }

    /**
     * Prueba de humo (la usa la compilación en GitHub): abre la ventana, espera, guarda una captura y sale
     * con 0 si todo fue bien. Usa una carpeta de datos temporal para no tocar la del usuario.
     */
    private static void selfTest(final String png) {
        try {
            java.io.File tmp = java.nio.file.Files.createTempDirectory("scratch-selftest").toFile();
            System.setProperty("scratch.data", tmp.getPath());
            desk.Prefs.get("ui").edit().putString("lang", "es").putInt("privacy_ok", 99)
                    .putBoolean("onboarded", true).commit();
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    start(new String[0]);
                }
            });
            Thread.sleep(4000);
            final boolean[] ok = {false};
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    try {
                        java.awt.Container c = Shell.get().frame().getContentPane();
                        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(),
                                BufferedImage.TYPE_INT_RGB);
                        java.awt.Graphics2D g = img.createGraphics();
                        c.paint(g);
                        g.dispose();
                        javax.imageio.ImageIO.write(img, "png", new java.io.File(png));
                        ok[0] = Shell.get().pane(Shell.RIGHT).topActivity() != null
                                && (Shell.get().pane(Shell.LEFT).topActivity() != null
                                || Shell.get().pane(Shell.FULL).topActivity() != null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            System.out.println(ok[0] ? "SELFTEST OK" : "SELFTEST FALLO");
            System.exit(ok[0] ? 0 : 1);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(2);
        }
    }

    private static void readVersion() {
        try {
            InputStream in = DesktopMain.class.getResourceAsStream("/desk-version.txt");
            if (in != null) {
                try {
                    String[] v = new String(in.readAllBytes(), "UTF-8").trim().split("\\s+");
                    desk.Rt.versionName = v[0];
                    if (v.length > 1) {
                        desk.Rt.versionCode = Integer.parseInt(v[1]);
                    }
                } finally {
                    in.close();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void start(String[] args) {
        Shell.onComponentChange = new Runnable() {
            @Override
            public void run() {
                Shell s = Shell.get();
                if (s != null) {
                    s.frame().setIconImages(icons());
                }
            }
        };
        Shell.launch("Scratch", ScratchApp.class, MainActivity.class, ScratchActivity.class, new Shell.Config() {
            @Override
            public int paneFor(Class<?> a) {
                if (a == ScratchActivity.class) {
                    return Shell.RIGHT;
                }
                if (a == BigPictureActivity.class || a == BigEditorActivity.class || a == WrappedActivity.class
                        || a == OnboardingActivity.class) {
                    return Shell.FULL;
                }
                return Shell.LEFT;
            }

            @Override
            public boolean rightWide() {
                // Modo DJ: el reproductor pasa a dos columnas (rueda a un lado, mesa con scroll al otro).
                return desk.Prefs.get("ui").getBoolean("djmode", false);
            }

            @Override
            public int maxWidthDp(Class<?> a) {
                return a == OnboardingActivity.class || a == WrappedActivity.class ? 560 : 0;
            }

            @Override
            public boolean key(KeyEvent e, boolean down) {
                return DesktopMain.key(e, down);
            }

            @Override
            public void onExit() {
            }
        }, icons());
        // El tema, el idioma o el acento cambian en Ajustes: los otros paneles se enteran al "reanudarse".
        final SharedPreferences ui = desk.Prefs.get("ui");
        ui.registerOnSharedPreferenceChangeListener(new SharedPreferences.OnSharedPreferenceChangeListener() {
            private boolean queued;

            @Override
            public void onSharedPreferenceChanged(SharedPreferences sp, String k) {
                if ("djmode".equals(k)) {
                    Shell.relayout();
                    return;
                }
                if (queued) {
                    return;
                }
                queued = true;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        queued = false;
                        Shell.refreshAll();
                    }
                });
            }
        });
        MediaSession.onChange = new Runnable() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        title();
                    }
                });
            }
        };
        title();
        registerProtocol();
        incoming(args);
    }

    /** Título de la ventana: «Canción — Artista · Scratch». */
    private static void title() {
        Shell s = Shell.get();
        MediaSession ms = MediaSession.current;
        if (s == null) {
            return;
        }
        String t = "Scratch";
        MediaMetadata m = ms == null ? null : ms.metadata();
        if (m != null) {
            String ti = m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String ar = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (ti != null && ti.length() > 0) {
                t = ti + (ar != null && ar.length() > 0 ? " — " + ar : "") + " · Scratch";
            }
        }
        if (!t.equals(s.frame().getTitle())) {
            s.frame().setTitle(t);
        }
    }

    /** Argumentos de esta instancia o de otra que se abrió después: enlaces scratchwheel:// y archivos. */
    private static void incoming(String[] args) {
        Shell s = Shell.get();
        if (s == null) {
            return;
        }
        if (s.frame().isVisible()) {
            if ((s.frame().getExtendedState() & java.awt.Frame.ICONIFIED) != 0) {
                s.frame().setExtendedState(s.frame().getExtendedState() & ~java.awt.Frame.ICONIFIED);
            }
            s.frame().toFront();
            s.frame().requestFocus();
        }
        for (String a : args) {
            if (a.startsWith("scratchwheel://")) {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(a), Shell.application(), MainActivity.class);
                Shell.start(Shell.application(), i, -1);
            } else {
                java.io.File f = new java.io.File(a);
                if (f.isFile()) {
                    String name = f.getName();
                    int dot = name.lastIndexOf('.');
                    Player.I.openSingle(Uri.fromFile(f), dot > 0 ? name.substring(0, dot) : name);
                }
            }
        }
    }

    /** Registra scratchwheel:// para este usuario (hace falta para volver del login de Spotify). */
    private static void registerProtocol() {
        final String exe = System.getProperty("jpackage.app-path");
        if (!desk.Rt.WINDOWS || exe == null) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String base = "HKCU\\Software\\Classes\\scratchwheel";
                    run("reg", "add", base, "/ve", "/d", "URL:Scratch", "/f");
                    run("reg", "add", base, "/v", "URL Protocol", "/d", "", "/f");
                    run("reg", "add", base + "\\shell\\open\\command", "/ve", "/d",
                            "\"" + exe + "\" \"%1\"", "/f");
                } catch (Exception ignored) {
                }
            }

            private void run(String... cmd) throws Exception {
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                p.waitFor();
            }
        }, "protocol");
        t.setDaemon(true);
        t.start();
    }

    // ------------------------------------------------------------ teclado

    private static int rawCode(KeyEvent e) {
        String s = e.paramString();
        int i = s.indexOf("rawCode=");
        if (i < 0) {
            return 0;
        }
        int j = i + 8;
        int v = 0;
        while (j < s.length() && Character.isDigit(s.charAt(j))) {
            v = v * 10 + (s.charAt(j) - '0');
            j++;
        }
        return v;
    }

    private static void seekBy(double secs) {
        ScratchEngine en = Player.I.engine;
        double dur = en.getDurationSeconds();
        if (dur > 0) {
            Player.I.seekFraction(Math.max(0, Math.min(0.999, (en.getPositionSeconds() + secs) / dur)));
        }
    }

    private static void volume(int dir) {
        AudioManager am = (AudioManager) Shell.application().getSystemService(android.content.Context.AUDIO_SERVICE);
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0);
        Shell.refreshAll();
    }

    static boolean key(KeyEvent e, boolean down) {
        int code = e.getKeyCode();
        if (code == 0 || code == KeyEvent.VK_UNDEFINED) {
            // Teclas multimedia del teclado (llegan sin código de AWT).
            int raw = rawCode(e);
            if (raw == 179 || raw == 176 || raw == 177 || raw == 178) {
                if (down) {
                    if (raw == 179) {
                        Player.I.toggle();
                    } else if (raw == 176) {
                        Player.I.next();
                    } else if (raw == 177) {
                        Player.I.prev();
                    } else {
                        Player.I.pause();
                    }
                }
                return true;
            }
            return false;
        }
        boolean ctrl = e.isControlDown();
        switch (code) {
            case KeyEvent.VK_SPACE:
                if (down) {
                    Player.I.toggle();
                }
                return true;
            case KeyEvent.VK_RIGHT:
                if (down) {
                    if (ctrl) {
                        Player.I.next();
                    } else {
                        seekBy(5);
                    }
                }
                return true;
            case KeyEvent.VK_LEFT:
                if (down) {
                    if (ctrl) {
                        Player.I.prev();
                    } else {
                        seekBy(-5);
                    }
                }
                return true;
            case KeyEvent.VK_UP:
                if (down) {
                    volume(1);
                }
                return true;
            case KeyEvent.VK_DOWN:
                if (down) {
                    volume(-1);
                }
                return true;
            case KeyEvent.VK_PLUS:
            case KeyEvent.VK_ADD:
            case KeyEvent.VK_EQUALS:
                if (ctrl && down) {
                    Shell.zoomBy(0.1f);
                }
                return ctrl;
            case KeyEvent.VK_MINUS:
            case KeyEvent.VK_SUBTRACT:
                if (ctrl && down) {
                    Shell.zoomBy(-0.1f);
                }
                return ctrl;
            case KeyEvent.VK_0:
            case KeyEvent.VK_NUMPAD0:
                if (ctrl && down) {
                    Shell.zoomBy(0f);
                }
                return ctrl;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------ icono

    /** El icono elegido en Ajustes → ICONO (los mismos ocho que en Android), en varios tamaños. */
    private static List<Image> icons() {
        ArrayList<Image> out = new ArrayList<Image>();
        int which = 1;
        SharedPreferences comp = desk.Prefs.get("desk_components");
        for (int n = 1; n <= 8; n++) {
            if (comp.getInt("dev.scratchwheel.Icon" + n, 0) == 1) {
                which = n;
            }
        }
        try {
            InputStream in = DesktopMain.class.getResourceAsStream("/res/drawable/ic_icon" + which + ".png");
            if (in == null) {
                in = DesktopMain.class.getResourceAsStream("/res/drawable/ic_launcher.png");
            }
            if (in == null) {
                return out;
            }
            BufferedImage src;
            try {
                src = javax.imageio.ImageIO.read(in);
            } finally {
                in.close();
            }
            for (int s : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) {
                BufferedImage b = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = b.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                        java.awt.RenderingHints.VALUE_RENDER_QUALITY);
                // esquinas redondeadas, como el icono adaptable del móvil
                float r = s * 0.44f;
                g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, s, s, r, r));
                g.drawImage(src.getScaledInstance(s, s, Image.SCALE_AREA_AVERAGING), 0, 0, null);
                g.dispose();
                out.add(b);
            }
        } catch (Exception ignored) {
        }
        return out;
    }
}
