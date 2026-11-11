import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import java.util.Locale;

/**
 * Corte de volumen (cut / transformer / gate): rapidez de los flancos y «clic» que meten.
 * Con una pista de valor constante la salida es directamente la curva de ganancia. Ver docs/BENCHMARKS.md.
 *
 *   java -cp /tmp/chk:/tmp/cls BenchCut
 */
public class BenchCut {
    static final int OUT = 48000;

    static ScratchEngine engine(boolean sine) {
        PcmData.Builder b = new PcmData.Builder(48000 * 20);
        for (int i = 0; i < 48000 * 20; i++) {
            short v = sine ? (short) Math.round(12000 * Math.sin(2 * Math.PI * 100.0 * i / 48000)) : (short) 12000;
            b.append(v, v);
        }
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT);
        e.load(b.build(48000));
        e.setPlaying(true);
        short[] buf = new short[512];
        for (int k = 0; k < 400; k++) {
            e.render(buf, 256);
        }
        return e;
    }

    static double[] render(ScratchEngine e, int n) {
        short[] buf = new short[2];
        double[] o = new double[n];
        for (int i = 0; i < n; i++) {
            e.render(buf, 1);
            o[i] = buf[0];
        }
        return o;
    }

    static double ms(int samples) {
        return samples * 1000.0 / OUT;
    }

    /** Energía por encima de ~5 kHz (pasa-altos de 4.º orden), en dB respecto a la señal. */
    static double hfDb(double[] x) {
        double w = 2 * Math.PI * 5000 / OUT, cs = Math.cos(w), al = Math.sin(w) / (2 * 0.7071), a0 = 1 + al;
        double b0 = (1 + cs) / 2 / a0, b1 = -(1 + cs) / a0, b2 = b0, a1 = -2 * cs / a0, a2 = (1 - al) / a0;
        double[] y = x.clone();
        for (int pass = 0; pass < 2; pass++) {
            double z1 = 0, z2 = 0;
            for (int i = 0; i < y.length; i++) {
                double in = y[i];
                double out = b0 * in + z1;
                z1 = b1 * in - a1 * out + z2;
                z2 = b2 * in - a2 * out;
                y[i] = out;
            }
        }
        double s = 0;
        for (int i = 2000; i < y.length; i++) {
            s += y[i] * y[i];
        }
        return 10 * Math.log10(Math.max(s / (y.length - 2000), 1e-12) / (12000.0 * 12000.0 / 2));
    }

    static String f(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        System.out.println("| Medida | Valor |");
        System.out.println("|---|---:|");
        ScratchEngine e = engine(false);
        e.setCut(true);
        double[] d = render(e, 2400);
        int f90 = -1, f10 = -1, f1 = -1;
        for (int i = 0; i < d.length; i++) {
            if (f90 < 0 && d[i] <= 0.9 * 12000) {
                f90 = i;
            }
            if (f10 < 0 && d[i] <= 0.1 * 12000) {
                f10 = i;
            }
            if (f1 < 0 && d[i] <= 0.01 * 12000) {
                f1 = i;
            }
        }
        System.out.println("| Cerrar: del 90 % al 10 % | " + f(ms(f10 - f90)) + " ms |");
        System.out.println("| Cerrar: hasta silencio (1 %) desde la orden | " + f(ms(f1)) + " ms |");
        e.setCut(false);
        d = render(e, 2400);
        int r10 = -1, r90 = -1, r99 = -1;
        for (int i = 0; i < d.length; i++) {
            if (r10 < 0 && d[i] >= 0.1 * 12000) {
                r10 = i;
            }
            if (r90 < 0 && d[i] >= 0.9 * 12000) {
                r90 = i;
            }
            if (r99 < 0 && d[i] >= 0.99 * 12000) {
                r99 = i;
            }
        }
        System.out.println("| Abrir: del 10 % al 90 % | " + f(ms(r90 - r10)) + " ms |");
        System.out.println("| Abrir: hasta el 99 % desde la orden | " + f(ms(r99)) + " ms |");
        double[] gates = {8, 16, 30};
        for (double g : gates) {
            e = engine(true);
            e.setGate(g);
            double[] x = render(e, OUT * 2);
            System.out.println("| Transformer a " + (int) g + " Hz sobre un tono de 100 Hz: clic por encima de 5 kHz | "
                    + f(hfDb(x)) + " dB |");
        }
        e = engine(false);
        e.setGate(30);
        d = render(e, OUT);
        double mx = 0, mn = 1e9;
        for (int i = OUT / 2; i < OUT; i++) {
            mx = Math.max(mx, d[i]);
            mn = Math.min(mn, d[i]);
        }
        System.out.println("| Transformer a 30 Hz: nivel abierto / cerrado | " + f(100 * mx / 12000) + " % / " + f(100 * mn / 12000) + " % |");
    }
}
