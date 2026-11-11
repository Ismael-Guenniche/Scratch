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
    private static final double SEEK_RATE = 1.5;
    /** Constante de tiempo del arranque y duración del frenado al dar a play/pausa, en segundos. */
    private static final double SPIN_UP = 0.12;
    private static final double SPIN_DOWN = 0.7;

    private short[] data = new short[0];
    private int frames;
    /** true mientras solo está cargado el principio de la pista (arranque rápido). */
    private boolean partial;
    private long totalFrames;
    private int sampleRate = 44100;
    private int outRate = 44100;

    // ----- ajustes de la rueda
    private double secPerRev = SECONDS_PER_REV;
    private boolean invert;
    private boolean inertia;
    private double frictionTau = 1.5;
    private boolean freeSpin;
    private boolean keyLock;
    private static final int MAXG = 4;
    private final double[] gPos = new double[MAXG];
    private final double[] gDir = new double[MAXG];
    private final int[] gAge = new int[MAXG];
    private final boolean[] gOn = new boolean[MAXG];
    private int hopCnt;
    private double lastDir = 1;

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
    private double seekMag = SEEK_RATE;
    private int backspinLeft;
    private int backspinTotal;
    private boolean fxActive;
    private PcmData nextPcm;
    private boolean swapped;
    // Ecualizador gráfico de 5 bandas (siempre activo si alguna banda no es 0 dB).
    public static final double[] EQ_HZ = {60, 230, 910, 3600, 14000};
    private final double[] geqDb = new double[5];
    private double geqPre = 1;
    private boolean geqOn;
    private final Biquad[][] geq = new Biquad[5][2];
    // ----- gimmicks de DJ
    private final double[] cues = {-1, -1, -1, -1};
    private boolean loopOn;
    private boolean rolling;
    private double loopStart;
    private double loopEnd;
    private double loopIn = -1;
    private boolean slip;
    private boolean slipping;
    private double slipPos;
    private boolean reverse;
    private boolean cut;
    private double gateHz;
    private double gatePhase;
    private double cutGain = 1;
    private boolean flanger;
    private boolean phaser;
    private double lfoPh;
    private int echoOutLeft;
    private double stopSecs;
    private boolean stopFlag;
    private double level;
    private double levelAcc;
    private static final int NV = 4;
    private final int[] svType = new int[NV];
    private final double[] svT = new double[NV];
    private final double[] svPh = new double[NV];
    private final boolean[] svOn = new boolean[NV];
    private long rnd = 12345;
    private float[] flBufL = new float[0];
    private float[] flBufR = new float[0];
    private int flPos;
    private final double[] apL = new double[4];
    private final double[] apR = new double[4];
    private double apCoef;
    private double phFbL;
    private double phFbR;
    private int phCnt;
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

        void peak(double fc, double q, double db, double fs) {
            double a = Math.pow(10, db / 40);
            double w = 2 * Math.PI * Math.min(fc, fs * 0.45) / fs;
            double cs = Math.cos(w);
            double al = Math.sin(w) / (2 * q);
            double a0 = 1 + al / a;
            b0 = (1 + al * a) / a0;
            b1 = -2 * cs / a0;
            b2 = (1 - al * a) / a0;
            a1 = -2 * cs / a0;
            a2 = (1 - al / a) / a0;
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

    /** Ecualizador gráfico: ganancia en dB (−12…+12) de cada banda de EQ_HZ. */
    public synchronized void setGraphicEq(double[] db) {
        boolean on = false;
        double max = 0;
        for (int i = 0; i < 5; i++) {
            geqDb[i] = db == null || i >= db.length ? 0 : Math.max(-12, Math.min(12, db[i]));
            on |= Math.abs(geqDb[i]) > 0.05;
            max = Math.max(max, geqDb[i]);
        }
        geqOn = on;
        geqPre = Math.pow(10, -(max * 0.7) / 20); // evita saturar al subir bandas
        refreshFx();
    }

    /** Encola la siguiente pista: al acabar la actual entra sin hueco. */
    public synchronized void queueNext(PcmData pcm) {
        nextPcm = pcm;
    }

    public synchronized void clearNext() {
        nextPcm = null;
    }

    public synchronized boolean hasNext() {
        return nextPcm != null;
    }

    public synchronized boolean hasSwapped() {
        return swapped;
    }

    /** true una vez cuando el motor ha saltado solo a la pista encolada. */
    public synchronized boolean consumeSwapped() {
        boolean e = swapped;
        swapped = false;
        return e;
    }

    private void swapToNext() {
        PcmData n = nextPcm;
        nextPcm = null;
        data = n.stereo;
        frames = n.frames;
        partial = false;
        totalFrames = 0;
        sampleRate = n.sampleRate;
        pos = 0;
        swapped = true;
        for (int i = 0; i < cues.length; i++) {
            cues[i] = -1;
        }
        loopOn = false;
        rolling = false;
        loopIn = -1;
        slipping = false;
        for (int i = 0; i < gAge.length; i++) {
            gAge[i] = Integer.MAX_VALUE / 2;
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
        resetGimmicks();
        refreshFx();
    }

    // ================= gimmicks de DJ =================

    /** Hot cue: guarda la posición actual en el pad i (0..3). */
    public synchronized void setCue(int i) {
        if (i >= 0 && i < cues.length && frames > 0) {
            cues[i] = pos / sampleRate;
        }
    }

    /** Salta al hot cue i. Devuelve false si el pad está vacío. */
    public synchronized boolean jumpCue(int i) {
        if (i < 0 || i >= cues.length || cues[i] < 0) {
            return false;
        }
        jumpTo(cues[i] * sampleRate);
        return true;
    }

    public synchronized void clearCue(int i) {
        if (i >= 0 && i < cues.length) {
            cues[i] = -1;
        }
    }

    /** Segundos del cue i, o -1 si está vacío. */
    public synchronized double getCue(int i) {
        return i >= 0 && i < cues.length ? cues[i] : -1;
    }

    private void jumpTo(double f) {
        f = Math.max(0, Math.min(Math.max(frames - 1, 0), f));
        if (slipping) {
            slipPos = f;
        }
        pos = f;
        scratchTarget = f;
    }

    /** Salto relativo en segundos (beat jump a mano). */
    public synchronized void jump(double seconds) {
        jumpTo(pos + seconds * sampleRate);
    }

    // ----- bucles
    public synchronized void loopIn() {
        loopIn = pos;
    }

    public synchronized void loopOut() {
        if (loopIn >= 0 && pos > loopIn + 0.05 * sampleRate) {
            loopStart = loopIn;
            loopEnd = pos;
            loopOn = true;
            rolling = false;
            slipBegin(false);
        }
    }

    public synchronized void loopExit() {
        loopOn = false;
        rolling = false;
        loopIn = -1;
        slipEnd();
    }

    /** Multiplica la longitud del bucle (0,5 o 2). */
    public synchronized void loopScale(double f) {
        if (loopOn) {
            double len = (loopEnd - loopStart) * f;
            if (len > 0.03 * sampleRate) {
                loopEnd = Math.min(Math.max(frames - 1, 0), loopStart + len);
            }
        }
    }

    public synchronized boolean isLoopOn() {
        return loopOn && !rolling;
    }

    public synchronized boolean hasLoopIn() {
        return loopIn >= 0 && !loopOn;
    }

    /** Loop roll: mientras se mantiene, repite los últimos segundos; al soltar vuelve a donde habría llegado la canción. */
    public synchronized void rollStart(double seconds) {
        if (!playing || frames == 0 || (loopOn && !rolling)) {
            return;
        }
        loopStart = pos;
        loopEnd = Math.min(Math.max(frames - 1, 0), pos + seconds * sampleRate);
        if (loopEnd <= loopStart + 100) {
            return;
        }
        if (!rolling) {
            rolling = true;
            slipBegin(true);
        }
        loopOn = true;
    }

    public synchronized void rollEnd() {
        if (rolling) {
            rolling = false;
            loopOn = false;
            slipEnd();
        }
    }

    // ----- slip
    public synchronized void setSlip(boolean on) {
        slip = on;
        if (!on) {
            slipEnd();
        }
    }

    private void slipBegin(boolean force) {
        if (!slipping && playing && (slip || force)) {
            slipping = true;
            slipPos = pos;
        }
    }

    private void slipEnd() {
        if (slipping && !scratching && !rolling && !reverse && !loopOn) {
            pos = Math.max(0, Math.min(Math.max(frames - 1, 0), slipPos));
            scratchTarget = pos;
            slipping = false;
        }
    }

    // ----- reverse / cut / gate
    public synchronized void setReverse(boolean on) {
        if (on == reverse) {
            return;
        }
        reverse = on;
        if (on) {
            slipBegin(false);
        } else {
            slipEnd();
        }
    }

    public synchronized void setCut(boolean on) {
        cut = on;
    }

    /** Trans/gate rítmico: hz = 0 apagado. */
    public synchronized void setGate(double hz) {
        gateHz = Math.max(0, Math.min(30, hz));
    }

    public synchronized double getGate() {
        return gateHz;
    }

    // ----- efectos
    public synchronized void setFlanger(boolean on) {
        flanger = on;
        if (on && flBufL.length == 0) {
            int n = (int) (0.012 * outRate) + 8;
            flBufL = new float[n];
            flBufR = new float[n];
        }
        refreshFx();
    }

    public synchronized void setPhaser(boolean on) {
        phaser = on;
        refreshFx();
    }

    public synchronized boolean isFlanger() {
        return flanger;
    }

    public synchronized boolean isPhaser() {
        return phaser;
    }

    /** Echo out: el sonido se va en ecos y vuelve a los 3 s. */
    public synchronized void echoOut() {
        int need = (int) (0.36 * outRate) + 1;
        if (delayL.length != need) {
            delayL = new float[need];
            delayR = new float[need];
            delayPos = 0;
        }
        echoOutLeft = (int) (3.0 * outRate);
        refreshFx();
    }

    /** Parada de vinilo: la próxima pausa frena en esta duración (se borra al volver a reproducir). */
    public synchronized void armVinylStop(double seconds) {
        stopSecs = Math.max(0.2, seconds);
        stopFlag = true;
    }

    /** Dispara un sonido del sampler: 0 bocina, 1 sirena, 2 láser, 3 riser. */
    public synchronized void triggerSample(int id) {
        for (int v = 0; v < NV; v++) {
            if (!svOn[v]) {
                svOn[v] = true;
                svType[v] = id;
                svT[v] = 0;
                svPh[v] = 0;
                return;
            }
        }
        svType[0] = id;
        svT[0] = 0;
        svPh[0] = 0;
    }

    private boolean samplerActive() {
        for (int v = 0; v < NV; v++) {
            if (svOn[v]) {
                return true;
            }
        }
        return false;
    }

    /** Nivel de salida 0..1 (para el vúmetro). */
    public synchronized double getLevel() {
        return level;
    }

    /** Deja los gimmicks en reposo (al salir del modo DJ o al cargar otra pista). */
    public synchronized void resetGimmicks() {
        for (int i = 0; i < cues.length; i++) {
            cues[i] = -1;
        }
        loopOn = false;
        rolling = false;
        loopIn = -1;
        slipping = false;
        reverse = false;
        cut = false;
        gateHz = 0;
        flanger = false;
        phaser = false;
        echoOutLeft = 0;
        stopFlag = false;
        slip = false;
    }

    /** Un paso del sampler: suma las voces activas (frecuencia de salida). */
    private double sampler() {
        double sum = 0;
        for (int v = 0; v < NV; v++) {
            if (!svOn[v]) {
                continue;
            }
            double t = svT[v];
            double dur;
            double s;
            switch (svType[v]) {
                case 0: { // bocina: acorde de ondas casi cuadradas
                    dur = 0.9;
                    double a = 0;
                    double[] fr = {415.3, 523.3, 622.3};
                    for (double f : fr) {
                        double ph = (t * f * (1 + 0.004 * Math.sin(t * 40))) % 1.0;
                        double saw = 2 * ph - 1;
                        a += Math.max(-1, Math.min(1, 3 * saw));
                    }
                    s = a / 3 * Math.min(1, t / 0.02) * Math.min(1, (dur - t) / 0.15);
                    break;
                }
                case 1: { // sirena
                    dur = 1.8;
                    double f = 760 + 360 * Math.sin(2 * Math.PI * 1.4 * t);
                    svPh[v] += f / outRate;
                    s = (Math.sin(2 * Math.PI * svPh[v]) + 0.3 * Math.sin(4 * Math.PI * svPh[v]))
                            * 0.75 * Math.min(1, t / 0.03) * Math.min(1, (dur - t) / 0.3);
                    break;
                }
                case 2: { // láser
                    dur = 0.35;
                    double f = 3200 * Math.exp(-t * 9) + 120;
                    svPh[v] += f / outRate;
                    double ph = svPh[v] % 1.0;
                    s = (2 * ph - 1) * (1 - t / dur);
                    break;
                }
                default: { // riser: tono y ruido que suben y se cortan
                    dur = 2.0;
                    double f = 150 * Math.exp(t * 1.8);
                    svPh[v] += f / outRate;
                    rnd = rnd * 6364136223846793005L + 1442695040888963407L;
                    double noise = ((rnd >>> 40) / (double) (1L << 24)) * 2 - 1;
                    double env = t / dur;
                    s = (0.7 * Math.sin(2 * Math.PI * svPh[v]) + 0.3 * noise) * env;
                    break;
                }
            }
            sum += s;
            svT[v] += 1.0 / outRate;
            if (svT[v] >= dur) {
                svOn[v] = false;
            }
        }
        return sum * 9500;
    }

    private void refreshFx() {
        boolean eq = eqLow != 1 || eqMid != 1 || eqHigh != 1;
        fxActive = eq || geqOn || filter != 0 || echo > 0 || flanger || phaser || echoOutLeft > 0;
        double fs = outRate;
        for (int c = 0; c < 2; c++) {
            for (int b = 0; b < 5; b++) {
                if (geq[b][c] == null) {
                    geq[b][c] = new Biquad();
                }
                geq[b][c].peak(EQ_HZ[b], 1.1, geqDb[b], fs);
            }
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
        if ((echo > 0 || echoOutLeft > 0) && delayL.length != need) {
            delayL = new float[need];
            delayR = new float[need];
            delayPos = 0;
        }
    }

    /** Efectos sobre una muestra (l, r) a la frecuencia de salida. */
    private void fx(double[] lr) {
        double l = lr[0];
        double r = lr[1];
        if (geqOn) {
            for (int b = 0; b < 5; b++) {
                l = geq[b][0].run(l);
                r = geq[b][1].run(r);
            }
            l *= geqPre;
            r *= geqPre;
        }
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
        boolean eo = echoOutLeft > 0;
        if ((echo > 0 || eo) && delayL.length > 0) {
            int len = delayL.length;
            double dl = delayL[delayPos];
            double dr = delayR[delayPos];
            double fb = 0.5;
            double inG = 1;
            double dry = 1;
            double mix = echo;
            if (eo) {
                double t = (3.0 * outRate - echoOutLeft) / outRate;
                inG = t < 0.3 ? 1 - t / 0.3 : 0;
                dry = t < 0.3 ? inG : (t < 2.4 ? 0 : (t < 3.0 ? (t - 2.4) / 0.6 : 1));
                fb = 0.72;
                mix = 1;
                echoOutLeft--;
                if (echoOutLeft == 0) {
                    java.util.Arrays.fill(delayL, 0f);
                    java.util.Arrays.fill(delayR, 0f);
                }
            }
            delayL[delayPos] = (float) (l * inG + dl * fb);
            delayR[delayPos] = (float) (r * inG + dr * fb);
            delayPos = (delayPos + 1) % len;
            l = l * dry + dl * mix;
            r = r * dry + dr * mix;
        }
        if (flanger && flBufL.length > 0) {
            int n = flBufL.length;
            lfoPh += 0.28 / outRate;
            if (lfoPh >= 1) {
                lfoPh -= 1;
            }
            double d = (0.0006 + 0.0034 * (0.5 + 0.5 * Math.sin(2 * Math.PI * lfoPh))) * outRate;
            double rp = flPos - d;
            while (rp < 0) {
                rp += n;
            }
            int i0 = (int) rp;
            int i1 = (i0 + 1) % n;
            double f = rp - i0;
            double wl = flBufL[i0] + (flBufL[i1] - flBufL[i0]) * f;
            double wr = flBufR[i0] + (flBufR[i1] - flBufR[i0]) * f;
            flBufL[flPos] = (float) (l + wl * 0.4);
            flBufR[flPos] = (float) (r + wr * 0.4);
            flPos = (flPos + 1) % n;
            l = (l + wl) * 0.5;
            r = (r + wr) * 0.5;
        }
        if (phaser) {
            if (phCnt-- <= 0) {
                phCnt = 31;
                lfoPh += 32.0 * 0.35 / outRate;
                if (lfoPh >= 1) {
                    lfoPh -= 1;
                }
                double fc = 350 * Math.pow(8.0, 0.5 + 0.5 * Math.sin(2 * Math.PI * lfoPh));
                double tn = Math.tan(Math.PI * Math.min(fc, outRate * 0.45) / outRate);
                apCoef = (tn - 1) / (tn + 1);
            }
            double xl = l + phFbL * 0.5;
            double xr = r + phFbR * 0.5;
            for (int k = 0; k < 4; k++) {
                double yl = apCoef * xl + apL[k];
                apL[k] = xl - apCoef * yl;
                xl = yl;
                double yr = apCoef * xr + apR[k];
                apR[k] = xr - apCoef * yr;
                xr = yr;
            }
            phFbL = xl;
            phFbR = xr;
            l = (l + xl) * 0.5;
            r = (r + xr) * 0.5;
        }
        lr[0] = l;
        lr[1] = r;
    }

    private final double[] tmp = new double[2];

    public synchronized void load(PcmData pcm) {
        nextPcm = null;
        swapped = false;
        data = pcm.stereo;
        frames = pcm.frames;
        partial = pcm.partial;
        totalFrames = pcm.totalFrames;
        sampleRate = pcm.sampleRate;
        pos = 0;
        rate = 0;
        scratchTarget = 0;
        scratching = false;
        playing = false;
        ended = false;
        seek = 0;
        for (int i = 0; i < cues.length; i++) {
            cues[i] = -1;
        }
        loopOn = false;
        rolling = false;
        loopIn = -1;
        slipping = false;
        reverse = false;
    }

    /** Vista de solo lectura del audio cargado para dibujar la onda y el espectro (el array no se modifica nunca). */
    public static final class Snap {
        public final short[] data;
        public final int frames;
        public final int rate;
        public final boolean partial;
        public final int pos;

        Snap(short[] data, int frames, int rate, boolean partial, int pos) {
            this.data = data;
            this.frames = frames;
            this.rate = rate;
            this.partial = partial;
            this.pos = pos;
        }
    }

    public synchronized Snap snap() {
        return new Snap(data, frames, sampleRate, partial, (int) Math.max(0, Math.min(frames - 1, pos)));
    }

    /** Sustituye el fragmento inicial por la pista entera sin tocar la posición (el principio es idéntico). */
    public synchronized void replaceData(PcmData full) {
        if (!partial || full.sampleRate != sampleRate) {
            return;
        }
        data = full.stereo;
        frames = full.frames;
        partial = false;
        totalFrames = 0;
    }

    /** La carga completa falló: lo que hay se trata como la pista entera. */
    public synchronized void finishPartial() {
        partial = false;
        totalFrames = 0;
    }

    public synchronized boolean isPartial() {
        return partial;
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
            stopFlag = false;
        } else {
            slipping = false;
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
        return !playing && !scratching && seek == 0 && rate == 0 && echoOutLeft == 0 && !samplerActive();
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
        if (dir == 0) {
            seekMag = SEEK_RATE;
        }
        seek = Integer.signum(dir) * seekMag;
    }

    /** Cambia la velocidad del avance rápido en curso (0,5× a 4×). */
    public synchronized void setSeekSpeed(double mag) {
        seekMag = Math.max(0.25, Math.min(5.0, mag));
        if (seek != 0) {
            seek = Math.signum(seek) * seekMag;
        }
    }

    /** Segundos de audio por vuelta de la rueda (menos = más sensible). */
    public synchronized void setSecondsPerRev(double s) {
        secPerRev = Math.max(0.3, Math.min(10, s));
    }

    public synchronized double getSecondsPerRev() {
        return secPerRev;
    }

    public synchronized void setInvert(boolean inv) {
        invert = inv;
    }

    public synchronized boolean isInverted() {
        return invert;
    }

    /** Mantener el tono: el audio se lee en granos a velocidad normal, así que mover la rueda no cambia el tono. */
    public synchronized void setKeyLock(boolean on) {
        if (keyLock != on) {
            keyLock = on;
            for (int g = 0; g < MAXG; g++) {
                gOn[g] = false;
            }
            hopCnt = 0;
        }
    }

    public synchronized boolean isKeyLock() {
        return keyLock;
    }

    public synchronized void touchDown() {
        freeSpin = false;
        slipBegin(false);
        scratching = true;
        scratchTarget = pos;
    }

    public synchronized void rotate(double radians) {
        if (!scratching) {
            return;
        }
        scratchTarget += (invert ? -radians : radians) / (2 * Math.PI) * secPerRev * sampleRate;
        scratchTarget = Math.max(0, Math.min(Math.max(frames - 1, 0), scratchTarget));
    }

    /** Inercia: al soltar la rueda lanzada sigue girando con fricción (tau en segundos) hasta pararse. */
    public synchronized void setInertia(boolean on, double tauSeconds) {
        inertia = on;
        frictionTau = Math.max(0.2, Math.min(8, tauSeconds));
    }

    public synchronized void touchUp() {
        if (scratching && inertia && !playing && backspinLeft == 0 && Math.abs(rate) > 0.15) {
            freeSpin = true;
        }
        scratching = false;
        slipEnd();
    }

    public synchronized double getPositionSeconds() {
        return pos / sampleRate;
    }

    public synchronized double getDurationSeconds() {
        return (double) Math.max(frames, partial ? totalFrames : 0) / sampleRate;
    }

    public synchronized double getRate() {
        return rate;
    }

    /** Rellena out con n frames estéreo intercalados a la frecuencia de salida. */
    public synchronized void render(short[] out, int n) {
        double ratio = (double) sampleRate / outRate;
        double tau = 0.02 * sampleRate;
        final double aScale = 44100.0 / outRate;
        int last = Math.max(frames - 1, 0);
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
            } else if (reverse && playing) {
                target = -pitch;
                alpha = 0.012 * aScale;
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
            if (freeSpin && (playing || seek != 0 || backspinLeft > 0)) {
                freeSpin = false;
            }
            if (freeSpin && !scratching) {
                // Inercia: fricción exponencial con un pequeño rozamiento seco para que acabe parando.
                rate -= rate * (1.0 / (frictionTau * outRate));
                double dry = 1.0 / (6.0 * outRate);
                if (Math.abs(rate) <= dry * 4) {
                    rate = 0;
                    freeSpin = false;
                } else {
                    rate -= Math.signum(rate) * dry;
                }
            } else if (!scratching && seek == 0 && !playing && backspinLeft == 0) {
                // Frenado tipo vinilo: baja la velocidad de forma lineal hasta parar (el tono cae).
                double step = 1.0 / ((stopFlag ? stopSecs : spinDown) * outRate);
                if (Math.abs(rate) <= step) {
                    rate = 0;
                    stopFlag = false;
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
            if (slipping && playing) {
                slipPos += pitch * ratio;
                if (slipPos > last) {
                    slipPos = last;
                }
            }
            if (loopOn && !scratching) {
                if (rate > 0 && pos >= loopEnd) {
                    pos = loopStart + (pos - loopEnd);
                } else if (rate < 0 && pos < loopStart) {
                    pos = loopEnd - (loopStart - pos);
                }
            }
            if (pos < 0) {
                pos = 0;
                if (rate < 0) {
                    rate = 0;
                }
            }
            if (partial && pos > last) {
                // Aún falta por decodificar: se espera en silencio al final del fragmento.
                pos = last;
                rate = 0;
            } else if (pos > last && playing && !scratching && nextPcm != null && !reverse) {
                swapToNext();
                ratio = (double) sampleRate / outRate;
                tau = 0.02 * sampleRate;
                last = Math.max(frames - 1, 0);
            } else if (pos > last) {
                pos = last;
                rate = 0;
                if (!scratching) {
                    if (playing) {
                        ended = true;
                    }
                    playing = false;
                }
            }

            double vl = 0;
            double vr = 0;
            if (frames > 0) {
                double g = Math.min(1.0, Math.abs(rate) * 8.0) * volume;
                if (keyLock) {
                    double[] s = grains(ratio, last);
                    vl = s[0] * g;
                    vr = s[1] * g;
                } else {
                    int i0 = (int) pos;
                    double f = pos - i0;
                    int im = Math.max(i0 - 1, 0);
                    int ip = Math.min(i0 + 1, last);
                    int ip2 = Math.min(i0 + 2, last);
                    vl = cubic(data[2 * im], data[2 * i0], data[2 * ip], data[2 * ip2], f) * g;
                    vr = cubic(data[2 * im + 1], data[2 * i0 + 1], data[2 * ip + 1],
                            data[2 * ip2 + 1], f) * g;
                }
            }
            if (fxActive) {
                tmp[0] = vl;
                tmp[1] = vr;
                fx(tmp);
                vl = tmp[0];
                vr = tmp[1];
            }
            // corte (transformer) y trans/gate: ganancia suavizada para que no haya clics
            double gt = cut ? 0 : 1;
            if (gateHz > 0) {
                gatePhase += gateHz / outRate;
                if (gatePhase >= 1) {
                    gatePhase -= 1;
                }
                if (gatePhase >= 0.5) {
                    gt = 0;
                }
            }
            if (gt != cutGain) {
                cutGain += (gt - cutGain) * Math.min(1.0, 1.0 / (0.002 * outRate));
                if (Math.abs(gt - cutGain) < 1e-4) {
                    cutGain = gt;
                }
            }
            vl *= cutGain;
            vr *= cutGain;
            if (samplerActiveFlag()) {
                double sm = sampler();
                vl += sm;
                vr += sm;
            }
            double pk = Math.max(Math.abs(vl), Math.abs(vr));
            if (pk > levelAcc) {
                levelAcc = pk;
            }
            out[2 * i] = clip(vl);
            out[2 * i + 1] = clip(vr);
        }
        double lv = Math.min(1.0, levelAcc / 24000.0);
        level = Math.max(lv, level * 0.8);
        levelAcc = 0;
    }

    private boolean samplerActiveFlag() {
        return svOn[0] || svOn[1] || svOn[2] || svOn[3];
    }

    private final double[] gOut = new double[2];

    /**
     * Síntesis granular (OLA con ventana Hann y solape del 50 %): cada grano suena a velocidad normal
     * (hacia delante o hacia atrás según la rueda), así que el tono no cambia aunque la posición avance
     * más rápido o más lento.
     */
    private double[] grains(double ratio, int last) {
        int len = Math.max(64, (int) (0.05 * outRate));
        int hop = len / 2;
        if (hopCnt <= 0) {
            if (Math.abs(rate) > 0.02) {
                lastDir = rate > 0 ? 1 : -1;
            }
            int slot = -1;
            for (int k = 0; k < MAXG; k++) {
                if (!gOn[k]) {
                    slot = k;
                    break;
                }
            }
            if (slot >= 0) {
                double half = len * ratio / 2.0;
                gPos[slot] = pos - lastDir * half;
                gDir[slot] = lastDir;
                gAge[slot] = 0;
                gOn[slot] = true;
            }
            hopCnt = hop;
        }
        hopCnt--;
        double l = 0;
        double r = 0;
        for (int k = 0; k < MAXG; k++) {
            if (!gOn[k]) {
                continue;
            }
            double w = 0.5 * (1 - Math.cos(2 * Math.PI * gAge[k] / len));
            double p = Math.max(0, Math.min(last, gPos[k]));
            int i0 = (int) p;
            int i1 = Math.min(i0 + 1, last);
            double f = p - i0;
            l += w * (data[2 * i0] + (data[2 * i1] - data[2 * i0]) * f);
            r += w * (data[2 * i0 + 1] + (data[2 * i1 + 1] - data[2 * i0 + 1]) * f);
            gPos[k] += gDir[k] * ratio;
            if (++gAge[k] >= len) {
                gOn[k] = false;
            }
        }
        gOut[0] = l;
        gOut[1] = r;
        return gOut;
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
