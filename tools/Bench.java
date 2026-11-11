import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.CRC32;

/**
 * Benchmark del motor de audio en la JVM (sin Android). Ver docs/BENCHMARKS.md.
 *
 *   java -cp /tmp/chk:/tmp/cls Bench            tiempos por bloque, contención y huella del audio
 *   java -Xint -cp /tmp/chk:/tmp/cls Bench alloc   bytes asignados por bloque (sin JIT: lo que pide el bytecode)
 *
 * Los tiempos dependen de la máquina: solo valen para comparar antes/después en el mismo equipo.
 * La huella (CRC) no depende de la máquina: si cambia, el audio generado ha cambiado.
 */
public class Bench {
    static final int OUT_RATE = 48000;
    static final int SRC_RATE = 44100;
    static final int BLOCK = 256;
    static final double BUDGET_US = BLOCK * 1e6 / OUT_RATE;
    static final int WARM = 3000;
    static int blocks = 20000;

    interface Setup {
        void init(ScratchEngine e);

        /** Se llama antes de cada bloque (simula las órdenes de la interfaz). */
        void tick(ScratchEngine e, int block);
    }

    static abstract class Base implements Setup {
        public void tick(ScratchEngine e, int block) {
            // Vuelve al principio antes de llegar al final para que todas las pasadas sean iguales.
            if (block % 9000 == 8999) {
                e.seekTo(5.0);
            }
        }
    }

    /** Gesto de scratch: vaivén de 4 Hz con ±0,35 rad por bloque como mucho, alrededor del segundo 60. */
    static abstract class Scratch implements Setup {
        public void tick(ScratchEngine e, int block) {
            double t = block * (double) BLOCK / OUT_RATE;
            e.rotate(0.35 * Math.sin(2 * Math.PI * 4 * t));
        }
    }

    static PcmData track;

    /** Pista sintética determinista de 150 s: tres senos, un barrido y ruido, estéreo distinto en L y R. */
    static PcmData makeTrack() {
        int frames = SRC_RATE * 150;
        PcmData.Builder b = new PcmData.Builder(frames);
        long rnd = 987654321L;
        double ph = 0;
        for (int i = 0; i < frames; i++) {
            double t = i / (double) SRC_RATE;
            rnd = rnd * 6364136223846793005L + 1442695040888963407L;
            double noise = ((rnd >>> 40) / (double) (1L << 24)) * 2 - 1;
            ph += (200 + 3000 * (0.5 + 0.5 * Math.sin(t * 0.7))) / SRC_RATE;
            double l = 0.3 * Math.sin(2 * Math.PI * 110 * t) + 0.25 * Math.sin(2 * Math.PI * 440 * t)
                    + 0.2 * Math.sin(2 * Math.PI * ph) + 0.1 * noise;
            double r = 0.3 * Math.sin(2 * Math.PI * 110 * t) + 0.25 * Math.sin(2 * Math.PI * 660 * t)
                    + 0.2 * Math.cos(2 * Math.PI * ph) - 0.1 * noise;
            b.append((short) Math.round(l * 30000), (short) Math.round(r * 30000));
        }
        return b.build(SRC_RATE);
    }

    static ScratchEngine fresh() {
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(OUT_RATE);
        e.load(track);
        return e;
    }

    static final String[] NAMES = {
        "Parado (silencio)",
        "Reproducción normal",
        "Reproducción + keylock (pitch 1,2)",
        "Scratch",
        "Scratch + keylock",
        "Reproducción + EQ 5 bandas",
        "Reproducción + modo DJ completo",
        "Reproducción + sampler (bocina)",
    };

    static Setup scenario(int i) {
        switch (i) {
            case 0:
                return new Base() {
                    public void init(ScratchEngine e) {
                    }
                };
            case 1:
                return new Base() {
                    public void init(ScratchEngine e) {
                        e.setPlaying(true);
                    }
                };
            case 2:
                return new Base() {
                    public void init(ScratchEngine e) {
                        e.setKeyLock(true);
                        e.setPitch(1.2);
                        e.setPlaying(true);
                    }
                };
            case 3:
                return new Scratch() {
                    public void init(ScratchEngine e) {
                        e.seekTo(60);
                        e.touchDown();
                    }
                };
            case 4:
                return new Scratch() {
                    public void init(ScratchEngine e) {
                        e.setKeyLock(true);
                        e.seekTo(60);
                        e.touchDown();
                    }
                };
            case 5:
                return new Base() {
                    public void init(ScratchEngine e) {
                        e.setGraphicEq(new double[] {4, -3, 2, -2, 5});
                        e.setPlaying(true);
                    }
                };
            case 6:
                return new Base() {
                    public void init(ScratchEngine e) {
                        e.setGraphicEq(new double[] {4, -3, 2, -2, 5});
                        e.setEq(1.2, 0.8, 1.1);
                        e.setFilter(-0.4);
                        e.setEcho(0.4);
                        e.setFlanger(true);
                        e.setPhaser(true);
                        e.setGate(8);
                        e.setPlaying(true);
                    }
                };
            default:
                return new Base() {
                    public void init(ScratchEngine e) {
                        e.setPlaying(true);
                    }

                    public void tick(ScratchEngine e, int block) {
                        super.tick(e, block);
                        if (block % 170 == 0) {
                            e.triggerSample(0);
                        }
                    }
                };
        }
    }

    static final class Res {
        double mean, p50, p99, max;
        int over;
        long crc;
    }

    static Res stats(long[] ns) {
        Res r = new Res();
        long[] s = ns.clone();
        Arrays.sort(s);
        double sum = 0;
        for (long v : s) {
            sum += v;
            if (v / 1000.0 > BUDGET_US) {
                r.over++;
            }
        }
        r.mean = sum / s.length / 1000.0;
        r.p50 = s[s.length / 2] / 1000.0;
        r.p99 = s[(int) (s.length * 0.99)] / 1000.0;
        r.max = s[s.length - 1] / 1000.0;
        return r;
    }

    /** Mide el tiempo de render() por bloque. La mejor de 3 pasadas (por media) reduce el ruido de la máquina. */
    static Res time(int sc) {
        Res best = null;
        for (int pass = 0; pass < 3; pass++) {
            ScratchEngine e = fresh();
            Setup s = scenario(sc);
            s.init(e);
            short[] buf = new short[BLOCK * 2];
            long[] ns = new long[blocks];
            CRC32 crc = new CRC32();
            byte[] bytes = new byte[BLOCK * 4];
            for (int i = 0; i < WARM + blocks; i++) {
                s.tick(e, i);
                long t0 = System.nanoTime();
                e.render(buf, BLOCK);
                long t1 = System.nanoTime();
                if (i >= WARM) {
                    ns[i - WARM] = t1 - t0;
                }
                for (int k = 0; k < buf.length; k++) {
                    bytes[2 * k] = (byte) buf[k];
                    bytes[2 * k + 1] = (byte) (buf[k] >> 8);
                }
                crc.update(bytes, 0, bytes.length);
            }
            Res r = stats(ns);
            r.crc = crc.getValue();
            if (best == null || r.mean < best.mean) {
                best = r;
            }
        }
        return best;
    }

    /** Bytes asignados por bloque en el hilo que renderiza. */
    static double alloc(int sc, int n) {
        com.sun.management.ThreadMXBean mx =
                (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long id = Thread.currentThread().getId();
        ScratchEngine e = fresh();
        Setup s = scenario(sc);
        s.init(e);
        short[] buf = new short[BLOCK * 2];
        for (int i = 0; i < 50; i++) {
            s.tick(e, i);
            e.render(buf, BLOCK);
        }
        long a0 = mx.getThreadAllocatedBytes(id);
        for (int i = 50; i < 50 + n; i++) {
            s.tick(e, i);
            e.render(buf, BLOCK);
        }
        long a1 = mx.getThreadAllocatedBytes(id);
        return (a1 - a0) / (double) n;
    }

    /**
     * Contención del cerrojo: otro hilo hace de interfaz y llama al motor {@code hz} veces por segundo
     * mientras este renderiza a ritmo real (un bloque cada 5,33 ms). Devuelve los tiempos de render().
     */
    static Res contend(final int sc, final int hz, final boolean faders, int n) throws Exception {
        final ScratchEngine e = fresh();
        final Setup s = scenario(sc);
        s.init(e);
        final boolean[] stop = {false};
        Thread ui = new Thread(new Runnable() {
            public void run() {
                long period = 1000000000L / hz;
                long next = System.nanoTime();
                int k = 0;
                while (!stop[0]) {
                    if (faders) {
                        double v = 1 + 0.3 * Math.sin(k * 0.05);
                        e.setEq(v, 1, 2 - v);
                        e.setFilter(0.5 * Math.sin(k * 0.03));
                    } else {
                        e.rotate(0.02 * Math.sin(k * 0.1));
                        e.getPositionSeconds();
                        e.getRate();
                        e.getLevel();
                        if (k % 4 == 0) {
                            e.snap();
                        }
                    }
                    k++;
                    next += period;
                    long w = next - System.nanoTime();
                    if (w > 0) {
                        java.util.concurrent.locks.LockSupport.parkNanos(w);
                    }
                }
            }
        }, "ui-sim");
        ui.setDaemon(true);
        ui.start();
        short[] buf = new short[BLOCK * 2];
        long[] ns = new long[n];
        long period = (long) (BUDGET_US * 1000);
        long next = System.nanoTime();
        for (int i = 0; i < 400 + n; i++) {
            long t0 = System.nanoTime();
            e.render(buf, BLOCK);
            long t1 = System.nanoTime();
            if (i >= 400) {
                ns[i - 400] = t1 - t0;
            }
            next += period;
            long w = next - System.nanoTime();
            if (w > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(w);
            }
        }
        stop[0] = true;
        ui.join();
        return stats(ns);
    }

    /**
     * Inversión de prioridad simulada: {@code threads} hilos de interfaz llaman sin pausa a la rueda y a los
     * getters (más hilos que núcleos), mientras este renderiza a ritmo real. Mide cuánto espera el hilo de
     * audio para conseguir el cerrojo del motor antes de cada bloque. Devuelve {p50, p99, máx (µs), esperas > 1 ms}.
     */
    static double[] lockWait(int threads, int n) throws Exception {
        final ScratchEngine e = fresh();
        scenario(3).init(e);
        final boolean[] stop = {false};
        Thread[] ui = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            ui[t] = new Thread(new Runnable() {
                public void run() {
                    int k = 0;
                    double sink = 0;
                    while (!stop[0]) {
                        e.rotate(0.0005 * Math.sin(k++ * 0.01));
                        sink += e.getPositionSeconds() + e.getRate() + e.getLevel() + e.getDurationSeconds();
                        if (e.isPlaying() || !e.isScratching()) {
                            sink += 1;
                        }
                    }
                }
            }, "ui-sim-" + t);
            ui[t].setDaemon(true);
            ui[t].start();
        }
        short[] buf = new short[BLOCK * 2];
        long[] ns = new long[n];
        long period = (long) (BUDGET_US * 1000);
        long next = System.nanoTime();
        for (int i = 0; i < 200 + n; i++) {
            long t0 = System.nanoTime();
            long t1;
            synchronized (e) {
                t1 = System.nanoTime();
                e.render(buf, BLOCK);
            }
            if (i >= 200) {
                ns[i - 200] = t1 - t0;
            }
            next += period;
            long w = next - System.nanoTime();
            if (w > 0) {
                java.util.concurrent.locks.LockSupport.parkNanos(w);
            }
        }
        stop[0] = true;
        for (Thread t : ui) {
            t.join();
        }
        Arrays.sort(ns);
        int late = 0;
        for (long v : ns) {
            if (v > 1000000) {
                late++;
            }
        }
        return new double[] {ns[n / 2] / 1000.0, ns[(int) (n * 0.99)] / 1000.0, ns[n - 1] / 1000.0, late};
    }

    /** Coste medio (ns) de una orden de la interfaz: es el tiempo que retiene el cerrojo del motor. */
    static double callNs(int which) {
        ScratchEngine e = fresh();
        e.setFlanger(true);
        e.setPlaying(true);
        int n = 200000;
        double sink = 0;
        for (int rep = 0; rep < 2; rep++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < n; i++) {
                switch (which) {
                    case 0:
                        e.rotate(0.001);
                        break;
                    case 1:
                        sink += e.getPositionSeconds();
                        break;
                    case 2:
                        e.setEq(1 + (i & 7) * 0.01, 1, 1);
                        break;
                    case 3:
                        e.setFilter(((i & 15) - 8) / 16.0);
                        break;
                    default:
                        sink += e.snap().pos;
                        break;
                }
            }
            long t1 = System.nanoTime();
            if (rep == 1) {
                return (t1 - t0) / (double) n + sink * 0;
            }
        }
        return 0;
    }

    static String f(double v) {
        return String.format(Locale.US, v < 10 ? "%.2f" : "%.1f", v);
    }

    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8"));
        track = makeTrack();
        boolean allocMode = a.length > 0 && a[0].equals("alloc");
        if (allocMode) {
            System.out.println("| Escenario | Bytes/bloque | Bytes/s |");
            System.out.println("|---|---:|---:|");
            for (int i = 0; i < NAMES.length; i++) {
                double b = alloc(i, 400);
                System.out.println("| " + NAMES[i] + " | " + Math.round(b) + " | "
                        + Math.round(b * OUT_RATE / BLOCK) + " |");
            }
            return;
        }
        if (a.length > 0) {
            blocks = Integer.parseInt(a[0]);
        }
        System.out.println("Bloque: " + BLOCK + " frames a " + OUT_RATE + " Hz = " + f(BUDGET_US / 1000)
                + " ms de plazo (" + f(BUDGET_US) + " µs). Pista " + SRC_RATE + " Hz. " + blocks
                + " bloques por escenario, mejor de 3 pasadas.");
        System.out.println();
        System.out.println("| Escenario | Media µs | p50 µs | p99 µs | Máx µs | % del plazo (media) | Huella CRC |");
        System.out.println("|---|---:|---:|---:|---:|---:|---|");
        for (int i = 0; i < NAMES.length; i++) {
            Res r = time(i);
            System.out.println("| " + NAMES[i] + " | " + f(r.mean) + " | " + f(r.p50) + " | " + f(r.p99) + " | "
                    + f(r.max) + " | " + f(100 * r.mean / BUDGET_US) + " % | "
                    + String.format("%08x", r.crc) + " |");
        }
        System.out.println();
        System.out.println("| Orden de la interfaz | ns por llamada (cerrojo retenido) |");
        System.out.println("|---|---:|");
        String[] cn = {"rotate()", "getPositionSeconds()", "setEq() (recalcula 16 biquads)",
            "setFilter() (recalcula 16 biquads)", "snap()"};
        for (int i = 0; i < cn.length; i++) {
            System.out.println("| " + cn[i] + " | " + Math.round(callNs(i)) + " |");
        }
        System.out.println();
        System.out.println("| Contención (render a ritmo real + hilo de interfaz) | Media µs | p99 µs | Máx µs | Bloques fuera de plazo |");
        System.out.println("|---|---:|---:|---:|---:|");
        int n = Math.min(blocks, 4000);
        Res c0 = contend(3, 1, false, n);
        System.out.println("| Scratch, interfaz casi quieta (1 Hz) | " + f(c0.mean) + " | " + f(c0.p99) + " | "
                + f(c0.max) + " | " + c0.over + " / " + n + " |");
        Res c1 = contend(3, 1000, false, n);
        System.out.println("| Scratch, rueda + ticker a 1000 Hz | " + f(c1.mean) + " | " + f(c1.p99) + " | "
                + f(c1.max) + " | " + c1.over + " / " + n + " |");
        double[] lw = lockWait(4, n);
        System.out.println();
        System.out.println("Espera del hilo de audio por el cerrojo con 4 hilos de interfaz sin pausa: p50 " + f(lw[0])
                + " µs, p99 " + f(lw[1]) + " µs, máx " + f(lw[2]) + " µs, esperas > 1 ms: " + (int) lw[3] + " / " + n);
        System.out.println();
        Res c2 = contend(6, 1000, true, n);
        System.out.println("| Modo DJ, faders EQ/filtro a 1000 Hz | " + f(c2.mean) + " | " + f(c2.p99) + " | "
                + f(c2.max) + " | " + c2.over + " / " + n + " |");
    }
}
