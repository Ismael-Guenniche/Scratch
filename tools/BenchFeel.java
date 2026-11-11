import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import java.util.Locale;

/**
 * Mide cómo responde el scratch al dedo, simulando una pantalla táctil de 120 Hz con llegada irregular
 * de los eventos (±1,5 ms) y bloques de audio de 256 frames a 48 kHz. Ver docs/BENCHMARKS.md.
 *
 *   java -cp /tmp/chk:/tmp/cls BenchFeel
 */
public class BenchFeel {
    static final int OUT = 48000;
    static final int SRC = 44100;
    static final int BLOCK = 256;
    static final double REV = ScratchEngine.SECONDS_PER_REV;
    static long rnd = 42;

    static double uni() {
        rnd = rnd * 6364136223846793005L + 1442695040888963407L;
        return ((rnd >>> 40) / (double) (1L << 24)) * 2 - 1;
    }

    interface Finger {
        /** Velocidad del dedo en el instante t (s), en veces la velocidad normal. */
        double speed(double t);
    }

    static final class Out {
        double[] rate;
        double[] lagMs;
        double[] level;
        double[] t;
    }

    /** Simula {@code secs} segundos y devuelve la velocidad de lectura y el retraso bloque a bloque. */
    static Out run(Finger f, double secs, double noiseRad) {
        PcmData.Builder b = new PcmData.Builder(SRC * 40);
        for (int i = 0; i < SRC * 40; i++) {
            short v = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / SRC));
            b.append(v, v);
        }
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT);
        e.load(b.build(SRC));
        e.seekTo(20);
        e.touchDown();
        int blocks = (int) (secs * OUT / BLOCK);
        Out o = new Out();
        o.rate = new double[blocks];
        o.lagMs = new double[blocks];
        o.level = new double[blocks];
        o.t = new double[blocks];
        short[] buf = new short[BLOCK * 2];
        double evPeriod = 1.0 / 120;
        double nextEv = evPeriod;
        double fingerAtEv = 0;   // posición del dedo (s de audio) en el último evento entregado
        double lastEvT = 0;
        double fingerPos = 0;    // integral exacta de la velocidad del dedo
        double tInt = 0;
        double prevNoise = 0;
        for (int k = 0; k < blocks; k++) {
            double tb = (k + 1) * (double) BLOCK / OUT;
            // eventos cuya llegada (con su irregularidad) cae antes de este bloque
            while (true) {
                double arrive = nextEv + 0.0015 * uniPeek();
                if (arrive > tb) {
                    break;
                }
                uni();
                // integra la velocidad del dedo hasta el instante del evento
                double pos = fingerAtEv;
                int steps = 20;
                for (int s = 0; s < steps; s++) {
                    double ts = lastEvT + (nextEv - lastEvT) * (s + 0.5) / steps;
                    pos += f.speed(ts) * (nextEv - lastEvT) / steps;
                }
                double noise = noiseRad * uni();
                double rad = (pos - fingerAtEv) / REV * 2 * Math.PI + noise - prevNoise;
                prevNoise = noise;
                deliver(e, rad, arrive);
                fingerAtEv = pos;
                lastEvT = nextEv;
                nextEv += evPeriod;
            }
            tick(e, tb);
            e.render(buf, BLOCK);
            // el bloque generado ahora suena durante [tb, tb + bloque): se compara con el dedo al final
            while (tInt < tb + (double) BLOCK / OUT) {
                fingerPos += f.speed(tInt) * 0.0005;
                tInt += 0.0005;
            }
            o.t[k] = tb;
            o.rate[k] = e.getRate();
            double en = 0;
            for (int q = 0; q < BLOCK; q++) {
                en += Math.abs(buf[2 * q]);
            }
            o.level[k] = en / BLOCK;
            o.lagMs[k] = (fingerPos - (e.getPositionSeconds() - 20)) * 1000;
        }
        return o;
    }

    static double uniPeek() {
        long r = rnd * 6364136223846793005L + 1442695040888963407L;
        return ((r >>> 40) / (double) (1L << 24)) * 2 - 1;
    }

    static void deliver(ScratchEngine e, double rad, double arriveSec) {
        e.rotateAt(rad, (long) (arriveSec * 1e9));
    }

    static void tick(ScratchEngine e, double nowSec) {
        e.syncClock((long) (nowSec * 1e9));
    }

    static String f(double v) {
        return String.format(Locale.US, "%.2f", v);
    }

    /** Rizado de la velocidad (desviación / media, %) y retraso medio en [t0, t1]. */
    static double[] steady(Out o, double t0, double t1) {
        double s = 0, s2 = 0, lag = 0;
        int n = 0;
        for (int k = 0; k < o.t.length; k++) {
            if (o.t[k] >= t0 && o.t[k] <= t1) {
                s += o.rate[k];
                s2 += o.rate[k] * o.rate[k];
                lag += o.lagMs[k];
                n++;
            }
        }
        double m = s / n;
        return new double[] {100 * Math.sqrt(Math.max(0, s2 / n - m * m)) / Math.abs(m), lag / n, m};
    }

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        System.out.println("| Gesto | Medida | Valor |");
        System.out.println("|---|---|---:|");
        double[] sp = {0.25, 1.0, 3.0};
        for (final double v : sp) {
            rnd = 42;
            Out o = run(new Finger() {
                public double speed(double t) {
                    return v;
                }
            }, 2.0, 0.004);
            double[] st = steady(o, 0.5, 2.0);
            System.out.println("| Dedo constante a " + f(v) + "× | rizado de la velocidad | " + f(st[0]) + " % |");
            System.out.println("| Dedo constante a " + f(v) + "× | retraso respecto al dedo | " + f(st[1]) + " ms de audio |");
        }
        rnd = 42;
        Out o = run(new Finger() {
            public double speed(double t) {
                return t < 0.5 ? 0 : 2.0;
            }
        }, 1.5, 0.0);
        double t90 = -1;
        for (int k = 0; k < o.t.length; k++) {
            if (o.t[k] > 0.5 && o.rate[k] >= 1.8) {
                t90 = (o.t[k] - 0.5) * 1000;
                break;
            }
        }
        System.out.println("| Arranque brusco 0 → 2× | tiempo hasta el 90 % | " + f(t90) + " ms |");
        rnd = 42;
        o = run(new Finger() {
            public double speed(double t) {
                return t < 0.8 ? 2.0 : 0;
            }
        }, 1.6, 0.0);
        double tStop = -1;
        double over = 0;
        for (int k = 0; k < o.t.length; k++) {
            if (o.t[k] > 0.8) {
                if (tStop < 0 && o.level[k] < 80) {
                    tStop = (o.t[k] - 0.8) * 1000;
                }
                over = Math.max(over, -o.lagMs[k]);
            }
        }
        System.out.println("| Parada brusca 2× → 0 | tiempo hasta el silencio | " + f(tStop) + " ms |");
        System.out.println("| Parada brusca 2× → 0 | se pasa de la posición del dedo | " + f(over) + " ms de audio |");
        rnd = 42;
        o = run(new Finger() {
            public double speed(double t) {
                return 2.5 * Math.sin(2 * Math.PI * 4 * t);
            }
        }, 2.0, 0.002);
        double err = 0;
        int n = 0;
        for (int k = 0; k < o.t.length; k++) {
            if (o.t[k] > 0.5) {
                double ideal = 2.5 * Math.sin(2 * Math.PI * 4 * o.t[k]);
                err += (o.rate[k] - ideal) * (o.rate[k] - ideal);
                n++;
            }
        }
        System.out.println("| Baby scratch 4 Hz ±2,5× | error RMS de velocidad | " + f(Math.sqrt(err / n)) + "× |");
    }
}
