import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import java.util.Locale;

/**
 * Física del plato: curva de arranque del motor, enganche del disco al soltarlo (slipmat) y agarre del dedo.
 * Ver docs/BENCHMARKS.md.
 *
 *   java -cp /tmp/chk:/tmp/cls BenchPlatter
 */
public class BenchPlatter {
    static final int OUT = 48000;

    static ScratchEngine engine() {
        PcmData.Builder b = new PcmData.Builder(44100 * 30);
        for (int i = 0; i < 44100 * 30; i++) {
            short v = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / 44100.0));
            b.append(v, v);
        }
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT);
        e.load(b.build(44100));
        e.seekTo(10);
        return e;
    }

    /** Renderiza de 16 en 16 frames y devuelve la velocidad en cada paso (0,33 ms). */
    static double[] trace(ScratchEngine e, double secs) {
        short[] buf = new short[32];
        int n = (int) (secs * OUT / 16);
        double[] r = new double[n];
        for (int k = 0; k < n; k++) {
            e.render(buf, 16);
            r[k] = e.getRate();
        }
        return r;
    }

    /** Primer instante (ms) en que la velocidad cruza {@code level} (subiendo si up, bajando si no). */
    static double cross(double[] r, double level, boolean up) {
        for (int k = 0; k < r.length; k++) {
            if (up ? r[k] >= level : r[k] <= level) {
                return (k + 1) * 16 * 1000.0 / OUT;
            }
        }
        return -1;
    }

    static double maxStep(double[] r, double from) {
        double m = 0;
        double prev = from;
        for (double v : r) {
            m = Math.max(m, Math.abs(v - prev));
            prev = v;
        }
        return m;
    }

    static String f(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        System.out.println("| Situación | Medida | Valor |");
        System.out.println("|---|---|---:|");
        ScratchEngine e = engine();
        e.setPlaying(true);
        double[] r = trace(e, 1.5);
        double t10 = cross(r, 0.1, true), t50 = cross(r, 0.5, true), t90 = cross(r, 0.9, true), t99 = cross(r, 0.99, true);
        System.out.println("| Arranque del motor (play) | 10 % / 50 % / 90 % / 99 % | " + f(t10) + " / " + f(t50) + " / "
                + f(t90) + " / " + f(t99) + " ms |");
        // linealidad: en un motor con par constante el 50 % llega a mitad de camino entre el 10 % y el 90 %
        System.out.println("| Arranque del motor (play) | forma (0,5 = rampa recta de motor) | "
                + String.format(Locale.US, "%.2f", (t50 - t10) / (t90 - t10)) + " |");

        e = engine();
        e.setPlaying(true);
        trace(e, 2.0);
        e.touchDown();
        r = trace(e, 0.2);
        System.out.println("| Agarrar el disco sonando (1× → 0) | tiempo hasta parar (< 0,05×) | " + f(cross(r, 0.05, false)) + " ms |");
        System.out.println("| Agarrar el disco sonando (1× → 0) | salto máximo de velocidad en 0,33 ms | "
                + String.format(Locale.US, "%.3f", maxStep(r, 1.0)) + "× |");
        trace(e, 0.3);
        e.touchUp();
        r = trace(e, 1.0);
        System.out.println("| Soltar el disco parado con la música sonando (0 → 1×) | 90 % / 99 % | " + f(cross(r, 0.9, true))
                + " / " + f(cross(r, 0.99, true)) + " ms |");

        e = engine();
        e.setPlaying(true);
        trace(e, 2.0);
        e.touchDown();
        short[] buf = new short[512];
        for (int k = 0; k < 60; k++) {
            e.rotate(-0.045);
            e.render(buf, 256);
        }
        double back = e.getRate();
        e.touchUp();
        r = trace(e, 1.5);
        System.out.println("| Soltar el disco yendo hacia atrás (" + f(back) + "× → 1×) | pasa por 0 / llega al 90 % | "
                + f(cross(r, 0.0, true)) + " / " + f(cross(r, 0.9, true)) + " ms |");

        e = engine();
        e.setPlaying(true);
        trace(e, 2.0);
        e.setPlaying(false);
        r = trace(e, 1.5);
        System.out.println("| Frenado (pausa) | 50 % / parado | " + f(cross(r, 0.5, false)) + " / " + f(cross(r, 0.001, false)) + " ms |");
    }
}
