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

    /** Keylock: reproduce {@code pcm} a la velocidad {@code pitch} manteniendo el tono; 1 s de salida estable. */
    static double[] keylock(PcmData pcm, double pitch) {
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT);
        e.load(pcm);
        e.setKeyLock(true);
        e.setPitch(pitch);
        e.setPlaying(true);
        short[] buf = new short[512];
        for (int k = 0; k < 2 * OUT / 256; k++) {
            e.render(buf, 256);
        }
        int n = OUT / 256 * 256;
        double[] out = new double[n];
        for (int k = 0; k < n / 256; k++) {
            e.render(buf, 256);
            for (int i = 0; i < 256; i++) {
                out[k * 256 + i] = buf[2 * i];
            }
        }
        return out;
    }

    /** Temblor de volumen: desviación del nivel (ventanas de 10 ms) respecto a su media, en %. */
    static double tremolo(double[] x) {
        int w = OUT / 100;
        int n = x.length / w;
        double[] lv = new double[n];
        double m = 0;
        for (int k = 0; k < n; k++) {
            double s = 0;
            for (int i = 0; i < w; i++) {
                s += x[k * w + i] * x[k * w + i];
            }
            lv[k] = Math.sqrt(s / w);
            m += lv[k];
        }
        m /= n;
        double v = 0;
        for (int k = 0; k < n; k++) {
            v += (lv[k] - m) * (lv[k] - m);
        }
        return 100 * Math.sqrt(v / n) / m;
    }

    static PcmData rich(int secs) {
        PcmData.Builder b = new PcmData.Builder(SRC * secs);
        for (int i = 0; i < SRC * secs; i++) {
            double t = i / (double) SRC;
            double s = 0;
            for (int h = 1; h <= 6; h++) {
                s += Math.sin(2 * Math.PI * 196 * h * t + h) / h;
            }
            short v = (short) Math.round(5000 * s);
            b.append(v, v);
        }
        return b.build(SRC);
    }

    static void keylockTable() {
        System.out.println();
        System.out.println("| Keylock (mantener el tono) | Señal/ruido dB | Temblor de volumen |");
        System.out.println("|---|---:|---:|");
        double[] pitches = {0.8, 1.2};
        for (double p : pitches) {
            for (double h : new double[] {220, 440, 1000}) {
                double[] x = keylock(sine(h, 12), p);
                System.out.println("| Tono de " + (int) h + " Hz a " + p + "× | " + f(fit(x, h)[1]) + " | " + f(tremolo(x)) + " % |");
            }
            System.out.println("| Nota con 6 armónicos (196 Hz) a " + p + "× | — | " + f(tremolo(keylock(rich(12), p))) + " % |");
        }
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
        keylockTable();
        // Saturación: tono de 440 Hz fuerte con los graves/medios/agudos del modo DJ al máximo (×1,6).
        ScratchEngine se = new ScratchEngine();
        se.setOutputRate(OUT);
        PcmData.Builder ob = new PcmData.Builder(SRC * 6);
        for (int i = 0; i < SRC * 6; i++) {
            short v = (short) Math.round(26000 * Math.sin(2 * Math.PI * 440.0 * i / SRC));
            ob.append(v, v);
        }
        se.load(ob.build(SRC));
        se.setEq(1.6, 1.6, 1.6);
        se.setPlaying(true);
        short[] sbuf = new short[512];
        for (int k = 0; k < 3 * OUT / 256; k++) {
            se.render(sbuf, 256);
        }
        double[] sx = new double[OUT / 256 * 256];
        for (int k = 0; k < sx.length / 256; k++) {
            se.render(sbuf, 256);
            for (int i = 0; i < 256; i++) {
                sx[k * 256 + i] = sbuf[2 * i];
            }
        }
        // armónicos altos (por encima de 5 kHz) que mete el recorte: pasa-altos simple de 2.º orden dos veces
        double w = 2 * Math.PI * 5000 / OUT, cs = Math.cos(w), al = Math.sin(w) / 1.4142, a0 = 1 + al;
        double b0 = (1 + cs) / 2 / a0, b1 = -(1 + cs) / a0, a1 = -2 * cs / a0, a2 = (1 - al) / a0;
        double[] y = sx.clone();
        for (int pass = 0; pass < 2; pass++) {
            double z1 = 0, z2 = 0;
            for (int i = 0; i < y.length; i++) {
                double in = y[i], o = b0 * in + z1;
                z1 = b1 * in - a1 * o + z2;
                z2 = b0 * in - a2 * o;
                y[i] = o;
            }
        }
        double hs = 0, ts = 0;
        for (int i = 4000; i < y.length; i++) {
            hs += y[i] * y[i];
            ts += sx[i] * sx[i];
        }
        System.out.println();
        System.out.println("Saturación (440 Hz fuerte × 1,6 con el EQ del modo DJ): armónicos por encima de 5 kHz a "
                + f(10 * Math.log10(hs / ts)) + " dB de la señal");
    }
}
