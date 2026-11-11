import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import java.util.Locale;

/**
 * Calidad del remuestreo: relación señal/ruido de un tono puro al reproducirlo (44,1 → 48 kHz), respuesta en
 * agudos y cuánto «aliasing» se cuela al leer más rápido de lo normal. Ver docs/BENCHMARKS.md.
 *
 *   java -cp /tmp/chk:/tmp/cls BenchQuality
 */
public class BenchQuality {
    static final int OUT = 48000;
    static final int SRC = 44100;
    static final double AMP = 12000;

    static PcmData sine(double hz, int secs) {
        PcmData.Builder b = new PcmData.Builder(SRC * secs);
        for (int i = 0; i < SRC * secs; i++) {
            short v = (short) Math.round(AMP * Math.sin(2 * Math.PI * hz * i / SRC));
            b.append(v, v);
        }
        return b.build(SRC);
    }

    /** Reproduce un tono a la velocidad dada y devuelve 0,5 s de salida (canal izquierdo) ya estabilizada. */
    static double[] play(double hz, double speed) {
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT);
        e.load(sine(hz, 40));
        short[] buf = new short[512];
        if (speed == 1.0) {
            e.setPlaying(true);
        } else {
            e.setSeekSpeed(speed);
            e.setSeek(1);
            e.setSeekSpeed(speed);
        }
        for (int k = 0; k < 4 * OUT / 256; k++) {
            e.render(buf, 256);
        }
        int n = OUT / 2 / 256 * 256;
        double[] out = new double[n];
        for (int k = 0; k < n / 256; k++) {
            e.render(buf, 256);
            for (int i = 0; i < 256; i++) {
                out[k * 256 + i] = buf[2 * i];
            }
        }
        return out;
    }

    /** Ajusta un seno de frecuencia f y devuelve {ganancia dB respecto a AMP, SNR dB}. */
    static double[] fit(double[] x, double f) {
        double ss = 0, cc = 0, sc = 0, xs = 0, xc = 0, xx = 0;
        for (int i = 0; i < x.length; i++) {
            double s = Math.sin(2 * Math.PI * f * i / OUT);
            double c = Math.cos(2 * Math.PI * f * i / OUT);
            ss += s * s;
            cc += c * c;
            sc += s * c;
            xs += x[i] * s;
            xc += x[i] * c;
            xx += x[i] * x[i];
        }
        double det = ss * cc - sc * sc;
        double a = (xs * cc - xc * sc) / det;
        double b = (xc * ss - xs * sc) / det;
        double sig = a * a * ss + b * b * cc + 2 * a * b * sc;
        double res = Math.max(xx - sig, 1e-9);
        double amp = Math.sqrt(a * a + b * b);
        return new double[] {20 * Math.log10(amp / AMP), 10 * Math.log10(sig / res)};
    }

    static double rmsDb(double[] x) {
        double s = 0;
        for (double v : x) {
            s += v * v;
        }
        return 20 * Math.log10(Math.max(Math.sqrt(s / x.length), 1e-3) / (AMP / Math.sqrt(2)));
    }

    static String f(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        System.out.println("| Prueba | Ganancia dB | Señal/ruido dB |");
        System.out.println("|---|---:|---:|");
        double[] hz = {1000, 5000, 10000, 15000, 18000};
        for (double h : hz) {
            double[] r = fit(play(h, 1.0), h);
            System.out.println("| Tono de " + (int) h + " Hz a velocidad normal | " + f(r[0]) + " | " + f(r[1]) + " |");
        }
        double[] r3 = fit(play(3000, 3.0), 9000);
        System.out.println("| Tono de 3 kHz a 3× (debe oírse a 9 kHz) | " + f(r3[0]) + " | " + f(r3[1]) + " |");
        System.out.println();
        System.out.println("| Aliasing: tono que a esa velocidad queda por encima de lo audible | Nivel que se cuela, dB |");
        System.out.println("|---|---:|");
        System.out.println("| 14 kHz a 2× (28 kHz → se pliega a 20 kHz) | " + f(rmsDb(play(14000, 2.0))) + " |");
        System.out.println("| 12 kHz a 3× (36 kHz → se pliega a 12 kHz) | " + f(rmsDb(play(12000, 3.0))) + " |");
        System.out.println("| 10 kHz a 4× (40 kHz → se pliega a 8 kHz) | " + f(rmsDb(play(10000, 4.0))) + " |");
    }
}
