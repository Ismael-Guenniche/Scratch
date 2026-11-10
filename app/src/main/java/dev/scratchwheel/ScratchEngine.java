package dev.scratchwheel;

/**
 * Motor de reproducción con scratch. Sin dependencias de Android.
 *
 * Mientras el dedo está en la rueda, la posición de lectura persigue a una
 * posición objetivo que mueve el dedo (como un plato de vinilo): la velocidad
 * de lectura sale de la distancia al objetivo, así que el tono sube y baja,
 * se oye al revés si giras hacia atrás y se calla si paras el dedo.
 *
 * La salida se genera a la frecuencia nativa del dispositivo (outRate) y la
 * pista se remuestrea con interpolación cúbica, para usar la ruta de baja latencia.
 */
public final class ScratchEngine {
    /** Una vuelta completa de la rueda = 1,8 s de audio (33 1/3 rpm). */
    public static final double SECONDS_PER_REV = 1.8;
    private static final double MAX_RATE = 12.0;
    private static final double SEEK_RATE = 3.0;
    /** Constante de tiempo del arranque y duración del frenado al dar a play/pausa, en segundos. */
    private static final double SPIN_UP = 0.12;
    private static final double SPIN_DOWN = 0.7;

    private short[] data = new short[0];
    private int frames;
    private int sampleRate = 44100;
    private int outRate = 44100;

    private double pos;
    private double rate;
    private double scratchTarget;
    private double seek;
    private float volume = 1f;
    private double pitch = 1.0;
    private boolean scratching;
    private boolean playing;
    private boolean ended;

    // ----- efectos de modo DJ (neutros por defecto: con todo a cero/1 no se procesa nada)
    private double eqLow = 1, eqMid = 1, eqHigh = 1;
    private double filter;
    private double echo;
    private double spinDown = SPIN_DOWN;
    private int backspinLeft;
    private int backspinTotal;
    private boolean fxActive;
    private final Biquad[] lpEq = {new Biquad(), new Biquad()};
    private final Biquad[] hpEq = {new Biquad(), new Biquad()};
    private final Biquad[] sweep = {new Biquad(), new Biquad()};
    private float[] delayL = new float[0];
    private float[] delayR = new float[0];
    private int delayPos;

    /** Filtro de 2.º orden (RBJ), un canal. */
    private static final class Biquad {
        double b0 = 1, b1, b2, a1, a2, z1, z2;

        void set(boolean high, double fc, double q, double fs) {
            double w = 2 * Math.PI * Math.min(fc, fs * 0.45) / fs;
            double cs = Math.cos(w);
            double al = Math.sin(w) / (2 * q);
            double a0 = 1 + al;
            if (high) {
                b0 = (1 + cs) / 2 / a0;
                b1 = -(1 + cs) / a0;
                b2 = (1 + cs) / 2 / a0;
            } else {
                b0 = (1 - cs) / 2 / a0;
                b1 = (1 - cs) / a0;
                b2 = (1 - cs) / 2 / a0;
            }
            a1 = -2 * cs / a0;
            a2 = (1 - al) / a0;
        }

        void bypass() {
            b0 = 1;
            b1 = b2 = a1 = a2 = 0;
        }

        double run(double x) {
            double y = b0 * x + z1;
            z1 = b1 * x - a1 * y + z2;
            z2 = b2 * x - a2 * y;
            return y;
        }
    }

    public synchronized void setEq(double low, double mid, double high) {
        eqLow = Math.max(0, Math.min(1.6, low));
        eqMid = Math.max(0, Math.min(1.6, mid));
        eqHigh = Math.max(0, Math.min(1.6, high));
        refreshFx();
    }

    /** -1 = paso bajo cerrado, 0 = sin filtro, +1 = paso alto cerrado. */
    public synchronized void setFilter(double f) {
        filter = Math.max(-1, Math.min(1, f));
        refreshFx();
    }

    public synchronized void setEcho(double mix) {
        echo = Math.max(0, Math.min(1, mix));
        refreshFx();
    }

    /** Duración del frenado al pausar (s). */
    public synchronized void setBrake(double seconds) {
        spinDown = Math.max(0.1, Math.min(6.0, seconds));
    }

    /** Efecto de rebobinado rápido que se frena (como tirar del disco hacia atrás). */
    public synchronized void backspin() {
        backspinTotal = (int) (0.75 * outRate);
        backspinLeft = backspinTotal;
    }

    /** Vuelve todo a neutro (al salir del modo DJ). */
    public synchronized void resetFx() {
        eqLow = eqMid = eqHigh = 1;
        filter = 0;
        echo = 0;
        spinDown = SPIN_DOWN;
        backspinLeft = 0;
        refreshFx();
    }

    private void refreshFx() {
        boolean eq = eqLow != 1 || eqMid != 1 || eqHigh != 1;
        fxActive = eq || filter != 0 || echo > 0;
        double fs = outRate;
        for (int c = 0; c < 2; c++) {
            lpEq[c].set(false, 250, 0.7071, fs);
            hpEq[c].set(true, 2500, 0.7071, fs);
            if (filter < 0) {
                sweep[c].set(false, 20000 * Math.pow(160.0 / 20000.0, -filter), 1.1, fs);
            } else if (filter > 0) {
                sweep[c].set(true, 25 * Math.pow(5000.0 / 25.0, filter), 1.1, fs);
            } else {
                sweep[c].bypass();
            }
        }
        int need = (int) (0.36 * outRate) + 1;
        if (echo > 0 && delayL.length != need) {
            delayL = new float[need];
            delayR = new float[need];
            delayPos = 0;
        }
    }

    /** Efectos sobre una muestra (l, r) a la frecuencia de salida. */
    private void fx(double[] lr) {
        double l = lr[0];
        double r = lr[1];
        if (eqLow != 1 || eqMid != 1 || eqHigh != 1) {
            double lowL = lpEq[0].run(l);
            double highL = hpEq[0].run(l);
            double lowR = lpEq[1].run(r);
            double highR = hpEq[1].run(r);
            l = lowL * eqLow + (l - lowL - highL) * eqMid + highL * eqHigh;
            r = lowR * eqLow + (r - lowR - highR) * eqMid + highR * eqHigh;
        }
        if (filter != 0) {
            l = sweep[0].run(l);
            r = sweep[1].run(r);
        }
        if (echo > 0 && delayL.length > 0) {
            int len = delayL.length;
            double dl = delayL[delayPos];
            double dr = delayR[delayPos];
            delayL[delayPos] = (float) (l + dl * 0.5);
            delayR[delayPos] = (float) (r + dr * 0.5);
            delayPos = (delayPos + 1) % len;
            l += dl * echo;
            r += dr * echo;
        }
        lr[0] = l;
        lr[1] = r;
    }

    private final double[] tmp = new double[2];

    public synchronized void load(PcmData pcm) {
        data = pcm.stereo;
        frames = pcm.frames;
        sampleRate = pcm.sampleRate;
        pos = 0;
        rate = 0;
        scratchTarget = 0;
        scratching = false;
        playing = false;
        ended = false;
        seek = 0;
    }

    public synchronized void setOutputRate(int hz) {
        outRate = Math.max(8000, hz);
        refreshFx();
    }

    public synchronized int getSampleRate() {
        return sampleRate;
    }

    public synchronized void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
    }

    /** Velocidad de reproducción normal (como el pitch de una mesa): 1,0 = original. */
    public synchronized void setPitch(double p) {
        pitch = Math.max(0.5, Math.min(1.5, p));
    }

    public synchronized void setPlaying(boolean p) {
        if (p && frames > 0 && pos >= frames - 1) {
            pos = 0;
        }
        playing = p && frames > 0;
        if (playing) {
            ended = false;
        }
    }

    public synchronized boolean isPlaying() {
        return playing;
    }

    public synchronized boolean isScratching() {
        return scratching;
    }

    /** true si no hay nada que sonar: se puede parar el hilo de audio. */
    public synchronized boolean isIdle() {
        return !playing && !scratching && seek == 0 && rate == 0;
    }

    /** true una vez cuando la pista llega al final reproduciéndose. */
    public synchronized boolean consumeEnded() {
        boolean e = ended;
        ended = false;
        return e;
    }

    public synchronized void seekTo(double seconds) {
        double f = Math.max(0, Math.min(Math.max(frames - 1, 0), seconds * sampleRate));
        pos = f;
        scratchTarget = f;
    }

    /** Avance/retroceso rápido con audio: -1, 0 o 1. */
    public synchronized void setSeek(int dir) {
        seek = Integer.signum(dir) * SEEK_RATE;
    }

    public synchronized void touchDown() {
        scratching = true;
        scratchTarget = pos;
    }

    public synchronized void rotate(double radians) {
        if (!scratching) {
            return;
        }
        scratchTarget += radians / (2 * Math.PI) * SECONDS_PER_REV * sampleRate;
        scratchTarget = Math.max(0, Math.min(Math.max(frames - 1, 0), scratchTarget));
    }

    public synchronized void touchUp() {
        scratching = false;
    }

    public synchronized double getPositionSeconds() {
        return pos / sampleRate;
    }

    public synchronized double getDurationSeconds() {
        return (double) frames / sampleRate;
    }

    public synchronized double getRate() {
        return rate;
    }

    /** Rellena out con n frames estéreo intercalados a la frecuencia de salida. */
    public synchronized void render(short[] out, int n) {
        final double ratio = (double) sampleRate / outRate;
        final double tau = 0.02 * sampleRate;
        final double aScale = 44100.0 / outRate;
        final int last = Math.max(frames - 1, 0);
        for (int i = 0; i < n; i++) {
            double target;
            double alpha;
            if (scratching) {
                target = (scratchTarget - pos) / tau;
                if (target > MAX_RATE) {
                    target = MAX_RATE;
                } else if (target < -MAX_RATE) {
                    target = -MAX_RATE;
                }
                alpha = 0.08 * aScale;
            } else if (backspinLeft > 0) {
                target = -6.0 * backspinLeft / backspinTotal;
                alpha = 0.01 * aScale;
                backspinLeft--;
            } else if (seek != 0) {
                target = seek;
                alpha = 0.004 * aScale;
            } else if (playing) {
                // Arranque tipo motor de plato: ~0,35 s hasta la velocidad normal.
                target = pitch;
                alpha = 1.0 / (SPIN_UP * outRate);
            } else {
                target = 0.0;
                alpha = 0.0;
            }
            if (!scratching && seek == 0 && !playing && backspinLeft == 0) {
                // Frenado tipo vinilo: baja la velocidad de forma lineal hasta parar (el tono cae).
                double step = 1.0 / (spinDown * outRate);
                if (Math.abs(rate) <= step) {
                    rate = 0;
                } else {
                    rate -= Math.signum(rate) * step;
                }
            } else {
                rate += (target - rate) * alpha;
            }
            if (!scratching && seek == 0 && !playing && backspinLeft == 0 && Math.abs(rate) < 1e-4) {
                rate = 0;
            }
            pos += rate * ratio;
            if (pos < 0) {
                pos = 0;
                if (rate < 0) {
                    rate = 0;
                }
            }
            if (pos > last) {
                pos = last;
                rate = 0;
                if (!scratching) {
                    if (playing) {
                        ended = true;
                    }
                    playing = false;
                }
            }

            if (frames == 0) {
                out[2 * i] = 0;
                out[2 * i + 1] = 0;
                continue;
            }
            int i0 = (int) pos;
            double f = pos - i0;
            int im = Math.max(i0 - 1, 0);
            int ip = Math.min(i0 + 1, last);
            int ip2 = Math.min(i0 + 2, last);
            double g = Math.min(1.0, Math.abs(rate) * 8.0) * volume;
            double vl = cubic(data[2 * im], data[2 * i0], data[2 * ip], data[2 * ip2], f) * g;
            double vr = cubic(data[2 * im + 1], data[2 * i0 + 1], data[2 * ip + 1],
                    data[2 * ip2 + 1], f) * g;
            if (fxActive) {
                tmp[0] = vl;
                tmp[1] = vr;
                fx(tmp);
                vl = tmp[0];
                vr = tmp[1];
            }
            out[2 * i] = clip(vl);
            out[2 * i + 1] = clip(vr);
        }
    }

    private static double cubic(double y0, double y1, double y2, double y3, double f) {
        double a = -0.5 * y0 + 1.5 * y1 - 1.5 * y2 + 0.5 * y3;
        double b = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3;
        double c = -0.5 * y0 + 0.5 * y2;
        return ((a * f + b) * f + c) * f + y1;
    }

    private static short clip(double v) {
        long r = Math.round(v);
        if (r > 32767) {
            return 32767;
        }
        if (r < -32768) {
            return -32768;
        }
        return (short) r;
    }
}
