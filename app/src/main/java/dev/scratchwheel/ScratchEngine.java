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
 * pista se remuestrea con un filtro sinc enventanado, para usar la ruta de baja latencia.
 */
public final class ScratchEngine {
    /** Una vuelta completa de la rueda = 1,8 s de audio (33 1/3 rpm). */
    public static final double SECONDS_PER_REV = 1.8;
    private static final double MAX_RATE = 12.0;
    private static final double SEEK_RATE = 1.5;
    /** Arranque del motor: el plato sube en rampa (par constante) hasta la velocidad normal en este tiempo (s). */
    private static final double MOTOR_UP = 0.2;
    /** Final suave de la rampa del motor (s). */
    private static final double MOTOR_LAND = 0.02;
    /** Slipmat: al soltar el disco, el rozamiento con el plato lo acelera 1× cada tantos segundos. */
    private static final double SLIP_CATCH = 0.06;
    /** Agarre del dedo: durante este tiempo tras tocar, el disco frena con la inercia de un vinilo (s). */
    private static final double GRIP_TIME = 0.015;
    private static final double GRIP_TAU = 0.004;
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
    /** Ventana Hann de los granos, precalculada para la frecuencia de salida actual. */
    private int grainLen;
    private double[] hann = new double[0];

    private void makeHann() {
        int len = Math.max(64, (int) (0.05 * outRate));
        if (len != grainLen) {
            double[] h = new double[len];
            for (int i = 0; i < len; i++) {
                h[i] = 0.5 * (1 - Math.cos(2 * Math.PI * i / len));
            }
            hann = h;
            grainLen = len;
        }
    }

    private double pos;
    private double rate;
    private double scratchTarget;
    private double seek;
    private float volume = 1f;
    private float trackGain = 1f;
    /** Fundido cruzado entre canciones (segundos, 0 = apagado). */
    private double xfadeSecs;
    private boolean xActive;
    private double xPos;
    private PcmData xFor;
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
    private double cutPhase = 1;
    // ----- carácter de vinilo (opcional): oscilación lenta del tono y ruido de surco
    private double vinyl;
    private double wowS, wowC = 1, flutS, flutC = 1;
    private long vrnd = 0x9E3779B97F4A7C15L;
    private double popL, popR;
    private double velGain;
    /** Duración total de la rampa del corte (s). */
    private static final double CUT_TIME = 0.002;
    private boolean flanger;
    private boolean phaser;
    private double lfoPh;
    private int echoOutLeft;
    private double stopSecs;
    private boolean stopFlag;
    private double level;
    private double levelAcc;
    private static final int NV = 4;
    /** Notas de la bocina del sampler (constante: no se crea nada en el hilo de audio). */
    private static final double[] HORN_HZ = {415.3, 523.3, 622.3};
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

        /** Filtra el bloque en el sitio, con coeficientes y estado en variables locales. */
        void runBlock(double[] x, int n) {
            runBlockTo(x, x, n);
        }

        void runBlockTo(double[] x, double[] out, int n) {
            final double c0 = b0, c1 = b1, c2 = b2, d1 = a1, d2 = a2;
            double s1 = z1, s2 = z2;
            for (int i = 0; i < n; i++) {
                double in = x[i];
                double y = c0 * in + s1;
                s1 = c1 * in - d1 * y + s2;
                s2 = c2 * in - d2 * y;
                out[i] = y;
            }
            z1 = s1;
            z2 = s2;
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
        refreshGeq();
        updateFxActive();
        publish();
    }

    /** Encola la siguiente pista: al acabar la actual entra sin hueco. */
    public synchronized void queueNext(PcmData pcm) {
        nextPcm = pcm;
        publish();
    }

    public synchronized void clearNext() {
        nextPcm = null;
        publish();
    }

    public synchronized boolean hasNext() {
        return nextPcm != null;
    }

    public boolean hasSwapped() {
        return pubSwapped;
    }

    /** true una vez cuando el motor ha saltado solo a la pista encolada. */
    public synchronized boolean consumeSwapped() {
        boolean e = swapped;
        swapped = false;
        publish();
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
        updateFxActive();
        publish();
    }

    /** -1 = paso bajo cerrado, 0 = sin filtro, +1 = paso alto cerrado. */
    public synchronized void setFilter(double f) {
        filter = Math.max(-1, Math.min(1, f));
        refreshSweep();
        updateFxActive();
        publish();
    }

    public synchronized void setEcho(double mix) {
        echo = Math.max(0, Math.min(1, mix));
        ensureDelay();
        updateFxActive();
        publish();
    }

    /** Duración del frenado al pausar (s). */
    public synchronized void setBrake(double seconds) {
        spinDown = Math.max(0.1, Math.min(6.0, seconds));
        publish();
    }

    /** Efecto de rebobinado rápido que se frena (como tirar del disco hacia atrás). */
    public synchronized void backspin() {
        backspinTotal = (int) (0.75 * outRate);
        backspinLeft = backspinTotal;
        publish();
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
        publish();
    }

    // ================= gimmicks de DJ =================

    /** Hot cue: guarda la posición actual en el pad i (0..3). */
    public synchronized void setCue(int i) {
        if (i >= 0 && i < cues.length && frames > 0) {
            cues[i] = pos / sampleRate;
        }
        publish();
    }

    /** Salta al hot cue i. Devuelve false si el pad está vacío. */
    public synchronized boolean jumpCue(int i) {
        if (i < 0 || i >= cues.length || cues[i] < 0) {
            return false;
        }
        jumpTo(cues[i] * sampleRate);
        publish();
        return true;
    }

    public synchronized void clearCue(int i) {
        if (i >= 0 && i < cues.length) {
            cues[i] = -1;
        }
        publish();
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
        publish();
    }

    // ----- bucles
    public synchronized void loopIn() {
        loopIn = pos;
        publish();
    }

    public synchronized void loopOut() {
        if (loopIn >= 0 && pos > loopIn + 0.05 * sampleRate) {
            loopStart = loopIn;
            loopEnd = pos;
            loopOn = true;
            rolling = false;
            slipBegin(false);
        }
        publish();
    }

    public synchronized void loopExit() {
        loopOn = false;
        rolling = false;
        loopIn = -1;
        slipEnd();
        publish();
    }

    /** Multiplica la longitud del bucle (0,5 o 2). */
    public synchronized void loopScale(double f) {
        if (loopOn) {
            double len = (loopEnd - loopStart) * f;
            if (len > 0.03 * sampleRate) {
                loopEnd = Math.min(Math.max(frames - 1, 0), loopStart + len);
            }
        }
        publish();
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
            publish();
            return;
        }
        loopStart = pos;
        loopEnd = Math.min(Math.max(frames - 1, 0), pos + seconds * sampleRate);
        if (loopEnd <= loopStart + 100) {
            publish();
            return;
        }
        if (!rolling) {
            rolling = true;
            slipBegin(true);
        }
        loopOn = true;
        publish();
    }

    public synchronized void rollEnd() {
        if (rolling) {
            rolling = false;
            loopOn = false;
            slipEnd();
        }
        publish();
    }

    // ----- slip
    public synchronized void setSlip(boolean on) {
        slip = on;
        if (!on) {
            slipEnd();
        }
        publish();
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
            publish();
            return;
        }
        reverse = on;
        if (on) {
            slipBegin(false);
        } else {
            slipEnd();
        }
        publish();
    }

    public synchronized void setCut(boolean on) {
        cut = on;
        publish();
    }

    /** Trans/gate rítmico: hz = 0 apagado. */
    public synchronized void setGate(double hz) {
        gateHz = Math.max(0, Math.min(30, hz));
        publish();
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
        updateFxActive();
        publish();
    }

    public synchronized void setPhaser(boolean on) {
        phaser = on;
        updateFxActive();
        publish();
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
        updateFxActive();
        publish();
    }

    /** Parada de vinilo: la próxima pausa frena en esta duración (se borra al volver a reproducir). */
    public synchronized void armVinylStop(double seconds) {
        stopSecs = Math.max(0.2, seconds);
        stopFlag = true;
        publish();
    }

    /** Dispara un sonido del sampler: 0 bocina, 1 sirena, 2 láser, 3 riser. */
    public synchronized void triggerSample(int id) {
        for (int v = 0; v < NV; v++) {
            if (!svOn[v]) {
                svOn[v] = true;
                svType[v] = id;
                svT[v] = 0;
                svPh[v] = 0;
                publish();
                return;
            }
        }
        svType[0] = id;
        svT[0] = 0;
        svPh[0] = 0;
        publish();
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
    public double getLevel() {
        return pubLevel;
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
        publish();
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
                    for (double f : HORN_HZ) {
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

    /** Solo el interruptor general: no recalcula ningún filtro. */
    private void updateFxActive() {
        boolean eq = eqLow != 1 || eqMid != 1 || eqHigh != 1;
        fxActive = eq || geqOn || filter != 0 || echo > 0 || flanger || phaser || echoOutLeft > 0;
    }

    /** Coeficientes del ecualizador gráfico (solo al cambiar una banda o la frecuencia de salida). */
    private void refreshGeq() {
        double fs = outRate;
        for (int c = 0; c < 2; c++) {
            for (int b = 0; b < 5; b++) {
                if (geq[b][c] == null) {
                    geq[b][c] = new Biquad();
                }
                geq[b][c].peak(EQ_HZ[b], 1.1, geqDb[b], fs);
            }
        }
    }

    /** Coeficientes del filtro barrido (solo al mover el filtro o cambiar la frecuencia de salida). */
    private void refreshSweep() {
        double fs = outRate;
        for (int c = 0; c < 2; c++) {
            if (filter < 0) {
                sweep[c].set(false, 20000 * Math.pow(160.0 / 20000.0, -filter), 1.1, fs);
            } else if (filter > 0) {
                sweep[c].set(true, 25 * Math.pow(5000.0 / 25.0, filter), 1.1, fs);
            } else {
                sweep[c].bypass();
            }
        }
    }

    private void ensureDelay() {
        int need = (int) (0.36 * outRate) + 1;
        if ((echo > 0 || echoOutLeft > 0) && delayL.length != need) {
            delayL = new float[need];
            delayR = new float[need];
            delayPos = 0;
        }
    }

    /** Recalcula todo (al cambiar la frecuencia de salida o volver a neutro). */
    private void refreshFx() {
        updateFxActive();
        double fs = outRate;
        refreshGeq();
        for (int c = 0; c < 2; c++) {
            lpEq[c].set(false, 250, 0.7071, fs);
            hpEq[c].set(true, 2500, 0.7071, fs);
        }
        refreshSweep();
        ensureDelay();
    }

    private double[] blkL = new double[0];
    private double[] blkR = new double[0];
    private double[] tA = new double[0];
    private double[] tB = new double[0];

    private void eq3(double[] x, Biquad lp, Biquad hp, int n) {
        final double[] lo = tA;
        final double[] hi = tB;
        lp.runBlockTo(x, lo, n);
        hp.runBlockTo(x, hi, n);
        final double gl = eqLow;
        final double gm = eqMid;
        final double gh = eqHigh;
        for (int i = 0; i < n; i++) {
            x[i] = lo[i] * gl + (x[i] - lo[i] - hi[i]) * gm + hi[i] * gh;
        }
    }

    /**
     * Efectos sobre el bloque entero (blkL/blkR), etapa a etapa: cada etapa neutra se salta una sola vez por
     * bloque y los filtros trabajan con su estado en variables locales. El resultado es idéntico, muestra a
     * muestra, a pasar cada muestra por toda la cadena (flanger y phaser van juntos porque comparten oscilador).
     */
    private void fxBlock(int n) {
        final double[] L = blkL;
        final double[] R = blkR;
        if (geqOn) {
            for (int b = 0; b < 5; b++) {
                geq[b][0].runBlock(L, n);
                geq[b][1].runBlock(R, n);
            }
            final double pre = geqPre;
            for (int i = 0; i < n; i++) {
                L[i] *= pre;
                R[i] *= pre;
            }
        }
        if (eqLow != 1 || eqMid != 1 || eqHigh != 1) {
            eq3(L, lpEq[0], hpEq[0], n);
            eq3(R, lpEq[1], hpEq[1], n);
        }
        if (filter != 0) {
            sweep[0].runBlock(L, n);
            sweep[1].runBlock(R, n);
        }
        if ((echo > 0 || echoOutLeft > 0) && delayL.length > 0) {
            for (int i = 0; i < n; i++) {
                double l = L[i];
                double r = R[i];
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
                    if (++delayPos >= len) {
                        delayPos = 0;
                    }
                    l = l * dry + dl * mix;
                    r = r * dry + dr * mix;
                }
                L[i] = l;
                R[i] = r;
            }
        }
        if ((flanger && flBufL.length > 0) || phaser) {
            for (int i = 0; i < n; i++) {
                double l = L[i];
                double r = R[i];
                if (flanger && flBufL.length > 0) {
                    int fn = flBufL.length;
                    lfoPh += 0.28 / outRate;
                    if (lfoPh >= 1) {
                        lfoPh -= 1;
                    }
                    double d = (0.0006 + 0.0034 * (0.5 + 0.5 * Math.sin(2 * Math.PI * lfoPh))) * outRate;
                    double rp = flPos - d;
                    while (rp < 0) {
                        rp += fn;
                    }
                    int i0 = (int) rp;
                    int i1 = (i0 + 1) % fn;
                    double f = rp - i0;
                    double wl = flBufL[i0] + (flBufL[i1] - flBufL[i0]) * f;
                    double wr = flBufR[i0] + (flBufR[i1] - flBufR[i0]) * f;
                    flBufL[flPos] = (float) (l + wl * 0.4);
                    flBufR[flPos] = (float) (r + wr * 0.4);
                    if (++flPos >= fn) {
                        flPos = 0;
                    }
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
                L[i] = l;
                R[i] = r;
            }
        }
    }

    private final double[] tmp = new double[2];

    // ----- estado publicado: lo que la interfaz consulta muchas veces por segundo se lee de aquí sin cerrojo
    private volatile double pubPosSec;
    private volatile double pubDurSec;
    private volatile double pubRate;
    private volatile double pubLevel;
    private volatile boolean pubPlaying;
    private volatile boolean pubScratching;
    private volatile boolean pubIdle = true;
    private volatile boolean pubSwapped;
    private volatile boolean pubEnded;
    private volatile boolean pubPartial;
    private volatile short[] pubData = new short[0];
    private volatile int pubFrames;
    private volatile int pubSampleRate = 44100;
    private volatile double pubPosFrames;
    /** Giro de la rueda pendiente de aplicar (radianes, como bits de double): rotate() no coge el cerrojo. */
    private final java.util.concurrent.atomic.AtomicLong pendingRot = new java.util.concurrent.atomic.AtomicLong();

    /** Copia el estado a los campos publicados. Se llama con el cerrojo cogido al final de cada orden y de cada bloque. */
    private void publish() {
        pubPosFrames = pos;
        pubPosSec = pos / sampleRate;
        pubDurSec = (double) Math.max(frames, partial ? totalFrames : 0) / sampleRate;
        pubRate = rate;
        pubLevel = level;
        pubPlaying = playing;
        pubScratching = scratching;
        pubSwapped = swapped;
        pubEnded = ended;
        pubPartial = partial;
        pubSampleRate = sampleRate;
        pubFrames = frames;
        pubData = data;
        pubIdle = !playing && !scratching && seek == 0 && rate == 0 && echoOutLeft == 0 && !samplerActive();
    }

    // ----- seguimiento del dedo: velocidad estimada de los eventos táctiles, con suavizado adaptativo
    /** Reloj del motor en ns: el de fuera si se llama a syncClock(), o el tiempo de audio generado si no. */
    private volatile long nowNanos;
    private boolean extClock;
    private volatile long lastRotNanos;
    private long prevEvNanos = Long.MIN_VALUE;
    /** Instante del último evento según un reloj regular (PLL): quita el temblor de la hora de llegada. */
    private double evClock;
    /** Periodo estimado entre eventos táctiles (s); se aprende solo (120 Hz ≈ 8,3 ms). */
    private double evPeriod = 1.0 / 120;
    /** Velocidad del dedo filtrada, en veces la velocidad normal. */
    private double fingerVel;
    /** Segundos desde el último evento táctil. */
    private double evAge = 1.0;
    private double lastMoveDir;
    /** Velocidad del plato (el motor); el disco (rate) se acopla a él por el slipmat cuando no lo sujeta el dedo. */
    private double platter;
    private int gripLeft;
    /** Ganancia (suavizada) que silencia la pequeña corrección de posición tras parar el dedo. */
    private double settleGain = 1;

    /**
     * Sincroniza el reloj del motor con el del sistema (lo llama el hilo de audio antes de cada bloque) para
     * que la edad de los eventos de rotateAt() sea exacta. Sin llamarlo, el motor usa su propio tiempo de audio.
     */
    public void syncClock(long nanos) {
        extClock = true;
        nowNanos = nanos;
    }

    /** Aplica el giro acumulado por rotate() (con el cerrojo cogido) y actualiza la velocidad del dedo. */
    private void applyRotation() {
        long bits = pendingRot.getAndSet(0L);
        if (bits == 0L) {
            return;
        }
        double radians = Double.longBitsToDouble(bits);
        if (!scratching) {
            return;
        }
        double d = (invert ? -radians : radians) / (2 * Math.PI) * secPerRev * sampleRate;
        scratchTarget += d;
        scratchTarget = Math.max(0, Math.min(Math.max(frames - 1, 0), scratchTarget));
        long tEv = lastRotNanos;
        double moved = d / sampleRate; // segundos de audio que ha movido el dedo
        double tReal = tEv / 1e9;
        if (prevEvNanos == Long.MIN_VALUE) {
            // Primer evento del gesto: aún no hay intervalo; media velocidad para no dar un tirón.
            fingerVel = 0.5 * moved / evPeriod;
            evClock = tReal;
        } else {
            double dt = (tEv - prevEvNanos) / 1e9;
            if (dt > 0.1) {
                // El dedo estuvo quieto: empieza otro trazo.
                fingerVel = 0.5 * moved / evPeriod;
                evClock = tReal;
            } else {
                if (dt > 0.002 && dt < 0.04) {
                    // Los eventos llegan a ritmo fijo (el de la pantalla): se aprende ese periodo y se usa en vez
                    // del intervalo medido, que tiembla con la planificación de hilos.
                    double k = Math.max(1, Math.rint(dt / evPeriod));
                    if (k == 1) {
                        evPeriod += 0.1 * (dt - evPeriod);
                    }
                    dt = k * evPeriod;
                } else if (dt <= 0.002) {
                    dt = evPeriod;
                }
                evClock += dt;
                evClock += 0.1 * (tReal - evClock);
                double vInst = moved / dt;
                // Filtro adaptativo (tipo «1 €»): corte bajo con el dedo lento (quita el temblor), alto con el
                // dedo rápido (no añade retraso), y casi directo ante un cambio brusco.
                double fc = 1.5 + 4.0 * (Math.abs(vInst) + Math.abs(fingerVel));
                double a = 1 - Math.exp(-2 * Math.PI * fc * dt);
                if (Math.abs(vInst - fingerVel) > 0.5 + 0.5 * Math.abs(fingerVel)) {
                    a = Math.max(a, 0.7);
                }
                fingerVel += a * (vInst - fingerVel);
            }
        }
        if (fingerVel > MAX_RATE) {
            fingerVel = MAX_RATE;
        } else if (fingerVel < -MAX_RATE) {
            fingerVel = -MAX_RATE;
        }
        prevEvNanos = tEv;
        lastMoveDir = Math.signum(d);
        evAge = Math.max(0, Math.min(0.05, nowNanos / 1e9 - evClock));
    }

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
        publish();
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

    public Snap snap() {
        // pubData se lee una sola vez: los frames se acotan a ese array por si la pista cambia a mitad.
        short[] d = pubData;
        int f = Math.min(pubFrames, d.length / 2);
        return new Snap(d, f, pubSampleRate, pubPartial, (int) Math.max(0, Math.min(f - 1, pubPosFrames)));
    }

    /** Sustituye el fragmento inicial por la pista entera sin tocar la posición (el principio es idéntico). */
    public synchronized void replaceData(PcmData full) {
        if (!partial || full.sampleRate != sampleRate) {
            publish();
            return;
        }
        data = full.stereo;
        frames = full.frames;
        partial = false;
        totalFrames = 0;
        publish();
    }

    /** La carga completa falló: lo que hay se trata como la pista entera. */
    public synchronized void finishPartial() {
        partial = false;
        totalFrames = 0;
        publish();
    }

    public synchronized boolean isPartial() {
        return partial;
    }

    public synchronized void setOutputRate(int hz) {
        outRate = Math.max(8000, hz);
        makeHann();
        refreshFx();
        publish();
    }

    public synchronized int getSampleRate() {
        return sampleRate;
    }

    public synchronized void setCrossfade(double secs) {
        xfadeSecs = Math.max(0, Math.min(12, secs));
        publish();
    }

    /** Ganancia por pista (ReplayGain), aparte del volumen de fundidos y temporizador. */
    public synchronized void setTrackGain(float g) {
        trackGain = Math.max(0.05f, Math.min(1f, g));
        publish();
    }

    public synchronized void setVolume(float v) {
        volume = Math.max(0f, Math.min(1f, v));
        publish();
    }

    /**
     * Carácter de vinilo, 0 (apagado) a 1: excentricidad del disco (el tono oscila ±0,12 % una vez por vuelta,
     * a 33⅓ rpm), flutter (±0,03 % a 7,3 Hz), siseo de surco muy bajo y algún chasquido. Solo suena con el disco
     * girando y sigue su velocidad (al frenar, el ruido también baja).
     */
    public synchronized void setVinyl(double amount) {
        vinyl = Math.max(0, Math.min(1, amount));
    }

    /** Velocidad de reproducción normal (como el pitch de una mesa): 1,0 = original. */
    public synchronized void setPitch(double p) {
        pitch = Math.max(0.5, Math.min(1.5, p));
        publish();
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
        publish();
    }

    public boolean isPlaying() {
        return pubPlaying;
    }

    public boolean isScratching() {
        return pubScratching;
    }

    /** true si no hay nada que sonar: se puede parar el hilo de audio. */
    public boolean isIdle() {
        return pubIdle && pendingRot.get() == 0L;
    }

    /** true una vez cuando la pista llega al final reproduciéndose. */
    public boolean consumeEnded() {
        if (!pubEnded) {
            return false;
        }
        synchronized (this) {
            boolean e = ended;
            ended = false;
            publish();
            return e;
        }
    }

    public synchronized void seekTo(double seconds) {
        double f = Math.max(0, Math.min(Math.max(frames - 1, 0), seconds * sampleRate));
        pos = f;
        scratchTarget = f;
        publish();
    }

    /** Avance/retroceso rápido con audio: -1, 0 o 1. */
    public synchronized void setSeek(int dir) {
        if (dir == 0) {
            seekMag = SEEK_RATE;
        }
        seek = Integer.signum(dir) * seekMag;
        publish();
    }

    /** Cambia la velocidad del avance rápido en curso (0,5× a 4×). */
    public synchronized void setSeekSpeed(double mag) {
        seekMag = Math.max(0.25, Math.min(5.0, mag));
        if (seek != 0) {
            seek = Math.signum(seek) * seekMag;
        }
        publish();
    }

    /** Segundos de audio por vuelta de la rueda (menos = más sensible). */
    public synchronized void setSecondsPerRev(double s) {
        secPerRev = Math.max(0.3, Math.min(10, s));
        publish();
    }

    public synchronized double getSecondsPerRev() {
        return secPerRev;
    }

    public synchronized void setInvert(boolean inv) {
        invert = inv;
        publish();
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
        publish();
    }

    public synchronized boolean isKeyLock() {
        return keyLock;
    }

    public synchronized void touchDown() {
        pendingRot.set(0L);
        fingerVel = 0;
        prevEvNanos = Long.MIN_VALUE;
        evAge = 1.0;
        lastMoveDir = 0;
        boolean grab = !scratching && rate != 0;
        freeSpin = false;
        slipBegin(false);
        scratching = true;
        scratchTarget = pos;
        if (grab) {
            // El disco no para en seco: frena en unos milisegundos y el punto de agarre es donde acabará parado.
            gripLeft = (int) (GRIP_TIME * outRate);
            scratchTarget = Math.max(0, Math.min(Math.max(frames - 1, 0), pos + rate * GRIP_TAU * sampleRate));
        } else {
            gripLeft = 0;
        }
        publish();
    }

    /** Giro de la rueda sin marca de tiempo: cuenta como ocurrido «ahora» en el reloj del motor. */
    public void rotate(double radians) {
        rotateAt(radians, nowNanos);
    }

    /**
     * Giro de la rueda ocurrido en {@code nanos} (System.nanoTime() si se usa syncClock()). No coge el cerrojo:
     * se acumula y el hilo de audio lo aplica al empezar el bloque.
     */
    public void rotateAt(double radians, long nanos) {
        if (!pubScratching || radians == 0) {
            return;
        }
        lastRotNanos = nanos;
        while (true) {
            long old = pendingRot.get();
            long upd = Double.doubleToRawLongBits(Double.longBitsToDouble(old) + radians);
            if (pendingRot.compareAndSet(old, upd)) {
                return;
            }
        }
    }

    /** Inercia: al soltar la rueda lanzada sigue girando con fricción (tau en segundos) hasta pararse. */
    public synchronized void setInertia(boolean on, double tauSeconds) {
        inertia = on;
        frictionTau = Math.max(0.2, Math.min(8, tauSeconds));
        publish();
    }

    public synchronized void touchUp() {
        applyRotation();
        if (scratching && inertia && !playing && backspinLeft == 0 && Math.abs(rate) > 0.15) {
            freeSpin = true;
        }
        scratching = false;
        slipEnd();
        publish();
    }

    public double getPositionSeconds() {
        return pubPosSec;
    }

    public double getDurationSeconds() {
        return pubDurSec;
    }

    public double getRate() {
        return pubRate;
    }

    /** Rellena out con n frames estéreo intercalados a la frecuencia de salida. */
    public synchronized void render(short[] out, int n) {
        applyRotation();
        if (blkL.length < n) {
            blkL = new double[n];
            blkR = new double[n];
            tA = new double[n];
            tB = new double[n];
        }
        final double[] bl = blkL;
        final double[] br = blkR;
        double ratio = (double) sampleRate / outRate;
        final double dtOut = 1.0 / outRate;
        // Hasta cuándo se extrapola sin eventos nuevos: un periodo táctil más lo que tarda un bloque en verlos.
        final double capT = Math.min(1.1 * evPeriod + n * dtOut + 0.002, 0.04);
        final double velDecay = dtOut / 0.015;
        final double aScale = 44100.0 / outRate;
        int last = Math.max(frames - 1, 0);
        final double motorStep = dtOut / MOTOR_UP;
        final double motorLand = dtOut / MOTOR_LAND;
        final double slipStep = dtOut / SLIP_CATCH;
        final double gripAlpha = dtOut / GRIP_TAU;
        final double cutStep = dtOut / CUT_TIME;
        final double wowW = 2 * Math.PI * (1.0 / SECONDS_PER_REV) * dtOut;
        final double flutW = 2 * Math.PI * 7.3 * dtOut;
        final double popDecay = Math.exp(-dtOut / 0.0003);
        final double popProb = 5.0 * dtOut;
        final double velGainAlpha = Math.min(1.0, dtOut / 0.0015);
        final double cutAlpha = Math.min(1.0, 1.0 / (0.002 * outRate));
        for (int i = 0; i < n; i++) {
            double target;
            double alpha;
            boolean settling = false;
            boolean slipmat = false;
            if (playing) {
                // Motor: par constante (rampa recta) con un final suave al llegar a la velocidad pedida.
                double dp = pitch - platter;
                double adp = dp < 0 ? -dp : dp;
                if (adp < 1e-6) {
                    platter = pitch;
                } else {
                    double st = adp * motorLand;
                    if (st > motorStep) {
                        st = motorStep;
                    }
                    platter += dp > 0 ? st : -st;
                }
            }
            if (scratching) {
                // El objetivo se extrapola con la velocidad del dedo entre evento y evento (como mucho algo más
                // de un periodo), así la lectura no va a escalones ni con retraso; si no llegan eventos, se queda.
                double ex = evAge < capT ? evAge : capT;
                double tgt = scratchTarget + fingerVel * ex * sampleRate;
                if (tgt < 0) {
                    tgt = 0;
                } else if (tgt > last) {
                    tgt = last;
                }
                target = fingerVel + (tgt - pos) / (0.02 * sampleRate);
                if (evAge > capT) {
                    // Dedo parado: la velocidad estimada se apaga y se llega al punto exacto del dedo; si la
                    // extrapolación se pasó un poco, se vuelve en silencio (con el dedo quieto no debe sonar nada).
                    fingerVel -= fingerVel * velDecay;
                    if (target * lastMoveDir < 0) {
                        settling = true;
                        if (target > 1.5) {
                            target = 1.5;
                        } else if (target < -1.5) {
                            target = -1.5;
                        }
                    }
                }
                evAge += dtOut;
                if (target > MAX_RATE) {
                    target = MAX_RATE;
                } else if (target < -MAX_RATE) {
                    target = -MAX_RATE;
                }
                if (gripLeft > 0) {
                    gripLeft--;
                    alpha = gripAlpha;
                } else {
                    alpha = 0.08 * aScale;
                }
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
                // El disco sigue al plato a través del slipmat (rozamiento constante): si van iguales, van juntos.
                target = platter;
                alpha = 0.0;
                slipmat = true;
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
            } else if (slipmat) {
                double dr = platter - rate;
                if (dr <= slipStep && dr >= -slipStep) {
                    rate = platter;
                } else {
                    rate += dr > 0 ? slipStep : -slipStep;
                }
            } else {
                rate += (target - rate) * alpha;
            }
            if (!playing) {
                // Motor apagado: el plato va con el disco (frenando o girando por inercia), o parado bajo el dedo.
                platter = scratching ? 0 : rate;
            }
            if (!scratching && seek == 0 && !playing && backspinLeft == 0 && Math.abs(rate) < 1e-4) {
                rate = 0;
            }
            if (vinyl > 0 && !scratching) {
                // Dos osciladores por rotación (sin senos por muestra): 0,556 Hz (una vuelta) y 7,3 Hz.
                wowS += wowC * wowW;
                wowC -= wowS * wowW;
                flutS += flutC * flutW;
                flutC -= flutS * flutW;
                pos += rate * ratio * (1 + vinyl * (0.0012 * wowS + 0.0003 * flutS));
            } else {
                pos += rate * ratio;
            }
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
                if (xActive) {
                    pos = Math.max(0, Math.min(xPos, frames - 1));
                    xActive = false;
                    xFor = null;
                }
                ratio = (double) sampleRate / outRate;
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
                double sg = settling ? 0.0 : 1.0;
                if (sg != settleGain) {
                    settleGain += (sg - settleGain) * cutAlpha;
                    if (Math.abs(sg - settleGain) < 1e-4) {
                        settleGain = sg;
                    }
                }
                // El volumen cae a cero al parar el disco; se suaviza (~1,5 ms) para que un cambio de sentido
                // rápido no meta un clic de amplitud.
                double gv = (rate < 0 ? -rate : rate) * 8.0;
                if (gv > 1.0) {
                    gv = 1.0;
                }
                velGain += (gv - velGain) * velGainAlpha;
                if (gv == 0 && velGain < 1e-5) {
                    velGain = 0;
                }
                double g = velGain * volume * trackGain * settleGain;
                if (keyLock) {
                    double[] s = grains(ratio, last);
                    vl = s[0] * g;
                    vr = s[1] * g;
                } else if (g != 0) {
                    double sp = rate * ratio;
                    sinc(pos, sp < 0 ? -sp : sp, last);
                    vl = sOut[0] * g;
                    vr = sOut[1] * g;
                }
            }
            if (vinyl > 0 && velGain > 0) {
                // Ruido de surco: siseo muy bajo y chasquidos sueltos, proporcionales a la velocidad del disco.
                vrnd = vrnd * 6364136223846793005L + 1442695040888963407L;
                double n1 = ((vrnd >>> 40) / (double) (1L << 24)) * 2 - 1;
                vrnd = vrnd * 6364136223846793005L + 1442695040888963407L;
                double n2 = ((vrnd >>> 40) / (double) (1L << 24)) * 2 - 1;
                double sp = rate < 0 ? -rate : rate;
                if ((n1 < 0 ? -n1 : n1) < popProb * (sp > 2 ? 2 : sp)) {
                    double amp = 150 + 500 * (n2 < 0 ? -n2 : n2);
                    popL += n2 > 0 ? amp : -amp;
                    popR += n1 > 0 ? amp * 0.7 : -amp * 0.7;
                }
                double ng = vinyl * velGain * volume;
                vl += (n1 * 10 + popL) * ng;
                vr += (n2 * 10 + popR) * ng;
                // se corta al llegar a cero práctico: los números denormales son muy lentos
                popL = popL > 1e-3 || popL < -1e-3 ? popL * popDecay : 0;
                popR = popR > 1e-3 || popR < -1e-3 ? popR * popDecay : 0;
            }
            if (xfadeSecs > 0 && nextPcm != null && playing && !scratching && !reverse && !partial && !loopOn
                    && rate > 0.3 && frames > 0) {
                double xf = xfadeSecs * sampleRate;
                double remain = last - pos;
                if (remain < xf) {
                    PcmData np = nextPcm;
                    if (xFor != np) {
                        xFor = np;
                        xPos = 0;
                    }
                    short[] nd = np.stereo;
                    int nl = Math.max(np.frames - 1, 0);
                    xPos = Math.min(nl, xPos + rate * ((double) np.sampleRate / outRate));
                    int j = (int) xPos;
                    int j2 = Math.min(j + 1, nl);
                    double fr = xPos - j;
                    double f = Math.max(0, Math.min(1, 1 - remain / xf));
                    double gc = Math.cos(f * Math.PI / 2);
                    double gn = Math.sin(f * Math.PI / 2) * volume;
                    vl = vl * gc + (nd[2 * j] + (nd[2 * j2] - nd[2 * j]) * fr) * gn;
                    vr = vr * gc + (nd[2 * j + 1] + (nd[2 * j2 + 1] - nd[2 * j + 1]) * fr) * gn;
                    xActive = true;
                } else {
                    xActive = false;
                    xFor = null;
                }
            } else if (xActive) {
                xActive = false;
                xFor = null;
            }
            bl[i] = vl;
            br[i] = vr;
        }
        if (fxActive) {
            fxBlock(n);
        }
        for (int i = 0; i < n; i++) {
            double vl = bl[i];
            double vr = br[i];
            // limitador suave solo cuando algo se suma a la pista; la reproducción normal queda intacta
            boolean soft = fxActive || xActive || svOn[0] || svOn[1] || svOn[2] || svOn[3];
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
            if (gt != cutPhase) {
                // Rampa de duración fija con forma de S (derivadas continuas): flancos rápidos y sin clic,
                // como un crossfader de corte seco movido con la mano.
                if (cutPhase < gt) {
                    cutPhase += cutStep;
                    if (cutPhase > 1) {
                        cutPhase = 1;
                    }
                } else {
                    cutPhase -= cutStep;
                    if (cutPhase < 0) {
                        cutPhase = 0;
                    }
                }
                double p = cutPhase;
                cutGain = p * p * p * (p * (6 * p - 15) + 10);
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
            if (soft) {
                // Con efectos, sampler o fundido la suma puede pasarse del máximo: en vez de recortar en seco
                // (muy áspero) se redondea la cresta. Por debajo del umbral no toca nada.
                if (vl > SOFT_KNEE || vl < -SOFT_KNEE) {
                    vl = softClip(vl);
                }
                if (vr > SOFT_KNEE || vr < -SOFT_KNEE) {
                    vr = softClip(vr);
                }
            }
            out[2 * i] = clip(vl);
            out[2 * i + 1] = clip(vr);
        }
        if (!extClock) {
            nowNanos += (long) (n * 1e9 / outRate);
        }
        double lv = Math.min(1.0, levelAcc / 24000.0);
        level = Math.max(lv, level * 0.8);
        levelAcc = 0;
        publish();
    }

    private boolean samplerActiveFlag() {
        return svOn[0] || svOn[1] || svOn[2] || svOn[3];
    }

    private final double[] gOut = new double[2];
    private int lastGrain = -1;

    /**
     * Busca, alrededor de {@code nom} (±6 ms), el punto de la pista cuya onda más se parece a la que está
     * sonando en {@code ref} (la continuación natural del grano anterior), por correlación normalizada sobre
     * 12 ms. Búsqueda gruesa (cada 2 muestras, comparando 1 de cada 4) y ajuste fino al final.
     */
    private double wsola(double nom, double ref, int dir, int last) {
        final short[] d = data;
        int span = (int) (0.006 * sampleRate);
        int win = (int) (0.012 * sampleRate);
        int r0 = (int) ref;
        int n0 = (int) nom;
        int lo = Math.min(r0, n0 - span);
        int hi = Math.max(r0, n0 + span);
        if (dir > 0 ? (lo < 0 || hi + win > last) : (lo - win < 0 || hi > last)) {
            return nom; // junto a los bordes de la pista no se busca
        }
        int best = 0;
        double bestScore = -1e300;
        for (int pass = 0; pass < 2; pass++) {
            int from = pass == 0 ? -span : best - 1;
            int to = pass == 0 ? span : best + 1;
            int stepC = pass == 0 ? 2 : 1;
            for (int c = from; c <= to; c += stepC) {
                long dot = 0;
                long en = 0;
                int a = 2 * r0;
                int b = 2 * (n0 + c);
                int inc = 8 * dir;
                for (int k = 0; k < win; k += 4) {
                    int x = d[a] + d[a + 1];
                    int y = d[b] + d[b + 1];
                    dot += (long) x * y;
                    en += (long) y * y;
                    a += inc;
                    b += inc;
                }
                double score = dot / Math.sqrt(en + 1.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
        }
        // Misma fracción de muestra que la referencia: la alineación queda exacta también entre muestras.
        return n0 + best + (ref - r0);
    }

    /**
     * Síntesis granular (OLA con ventana Hann y solape del 50 %): cada grano suena a velocidad normal
     * (hacia delante o hacia atrás según la rueda), así que el tono no cambia aunque la posición avance
     * más rápido o más lento.
     */
    private double[] grains(double ratio, int last) {
        if (grainLen == 0) {
            makeHann();
        }
        int len = grainLen;
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
                double start = pos - lastDir * half;
                int pv = lastGrain;
                if (pv >= 0 && gOn[pv] && gDir[pv] == lastDir) {
                    // WSOLA: el grano nuevo empieza donde su onda mejor continúa la del grano anterior.
                    start = wsola(start, gPos[pv], (int) lastDir, last);
                }
                lastGrain = slot;
                gPos[slot] = start;
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
            if (gAge[k] >= len) {
                gOn[k] = false;
                continue;
            }
            double w = hann[gAge[k]];
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

    // ----- remuestreo con sinc enventanado (Kaiser) y corte variable
    /** Cruces por cero a cada lado a velocidad normal (2 × SINC_Z muestras por punto). */
    private static final int SINC_Z = 6;
    /** Con la lectura rápida se usa un núcleo más corto: importa menos la finura y cuesta la mitad. */
    private static final int FAST_Z = 3;
    private static final double FAST_FROM = 1.25;
    /** Puntos de tabla por cruce por cero (se interpola linealmente entre ellos). */
    private static final int SINC_L = 256;
    /** A partir de esta velocidad el filtro ya no se estrecha más (limita el coste por muestra). */
    private static final double SINC_MAX_STRETCH = 4.0;
    // Kaiser β = 9 y corte al 92 % de Nyquist: >84 dB de señal/ruido hasta 10 kHz con 12 muestras por punto.
    private static final float[] SINC = makeSinc(SINC_Z, 9.0, 0.92);
    private static final float[] SINC_FAST = makeSinc(FAST_Z, 6.0, 0.85);
    /** Velocidad normal o menor: coeficientes ya calculados por fase (POLY_PH fases × POLY_N muestras, cada fila suma 1). */
    private static final int POLY_PH = 256;
    private static final int POLY_N = 2 * SINC_Z;
    private static final float[] POLY = makePoly();

    private static float[] makeSinc(int z, double beta, double cut) {
        int n = z * SINC_L;
        float[] t = new float[n + 2];
        double i0b = bessel0(beta);
        for (int k = 0; k <= n; k++) {
            double x = k / (double) SINC_L;
            double w = x / z;
            double win = bessel0(beta * Math.sqrt(Math.max(0, 1 - w * w))) / i0b;
            double a = Math.PI * x * cut;
            t[k] = (float) ((k == 0 ? 1.0 : Math.sin(a) / a) * win);
        }
        t[n] = 0;
        t[n + 1] = 0;
        return t;
    }

    private static float[] makePoly() {
        float[] t = new float[(POLY_PH + 1) * POLY_N];
        double[] row = new double[POLY_N];
        for (int ph = 0; ph <= POLY_PH; ph++) {
            double f = ph / (double) POLY_PH;
            double sum = 0;
            for (int k = 0; k < POLY_N; k++) {
                double x = Math.abs(k - (SINC_Z - 1) - f) * SINC_L;
                int ti = (int) x;
                row[k] = ti >= SINC_Z * SINC_L ? 0 : SINC[ti] + (SINC[ti + 1] - SINC[ti]) * (x - ti);
                sum += row[k];
            }
            for (int k = 0; k < POLY_N; k++) {
                t[ph * POLY_N + k] = (float) (row[k] / sum);
            }
        }
        return t;
    }

    private static double bessel0(double x) {
        double sum = 1;
        double term = 1;
        for (int k = 1; k < 40; k++) {
            term *= (x / (2 * k)) * (x / (2 * k));
            sum += term;
        }
        return sum;
    }

    private final double[] sOut = new double[2];

    /**
     * Lee la pista en {@code p} con un filtro sinc. {@code speed} es la velocidad de lectura en muestras de pista
     * por muestra de salida: por encima de 1 el filtro se ensancha (corte más bajo) para que lo que quedaría por
     * encima de lo audible no se pliegue como aliasing.
     */
    private void sinc(double p, double speed, int last) {
        final short[] d = data;
        double l = 0;
        double r = 0;
        if (speed <= 1) {
            // Camino habitual (reproducción normal): 12 coeficientes ya normalizados, interpolados entre dos fases.
            int i0 = (int) p;
            double fp = (p - i0) * POLY_PH;
            int pi = (int) fp;
            float fr = (float) (fp - pi);
            int ra = pi * POLY_N;
            int rb = ra + POLY_N;
            final float[] c = POLY;
            int base = i0 - (SINC_Z - 1);
            if (base >= 0 && base + POLY_N - 1 <= last) {
                int q = 2 * base;
                for (int k = 0; k < POLY_N; k++) {
                    float w = c[ra + k] + (c[rb + k] - c[ra + k]) * fr;
                    l += w * d[q];
                    r += w * d[q + 1];
                    q += 2;
                }
            } else {
                for (int k = 0; k < POLY_N; k++) {
                    float w = c[ra + k] + (c[rb + k] - c[ra + k]) * fr;
                    int j = base + k;
                    int q = 2 * (j < 0 ? 0 : (j > last ? last : j));
                    l += w * d[q];
                    r += w * d[q + 1];
                }
            }
            sOut[0] = l;
            sOut[1] = r;
            return;
        }
        double s = speed < SINC_MAX_STRETCH ? speed : SINC_MAX_STRETCH;
        final float[] tab = s <= FAST_FROM ? SINC : SINC_FAST;
        double half = (s <= FAST_FROM ? SINC_Z : FAST_Z) * s;
        // +64 para que el redondeo sea siempre hacia abajo aunque p - half sea negativo
        int j0 = (int) (p - half + 64) - 63;
        int j1 = (int) (p + half + 64) - 64;
        double step = SINC_L / s;
        double ws = 0;
        boolean inside = j0 >= 0 && j1 <= last;
        for (int j = j0; j <= j1; j++) {
            double t = (j - p) * step;
            if (t < 0) {
                t = -t;
            }
            int ti = (int) t;
            double w = tab[ti] + (tab[ti + 1] - tab[ti]) * (t - ti);
            int q = inside ? 2 * j : 2 * (j < 0 ? 0 : (j > last ? last : j));
            l += w * d[q];
            r += w * d[q + 1];
            ws += w;
        }
        sOut[0] = l / ws;
        sOut[1] = r / ws;
    }

    /** Umbral del limitador suave (−2,7 dB): solo actúa por encima. */
    private static final double SOFT_KNEE = 24000;

    private static double softClip(double v) {
        double room = 32767 - SOFT_KNEE;
        return v > 0 ? SOFT_KNEE + room * Math.tanh((v - SOFT_KNEE) / room)
                : -SOFT_KNEE - room * Math.tanh((-v - SOFT_KNEE) / room);
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
