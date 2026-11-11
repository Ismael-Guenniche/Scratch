package dev.scratchwheel;
/** Prueba del fundido cruzado: compila con ScratchEngine y PcmData y ejecuta dev.scratchwheel.CheckXfade (imprime XFADE OK). */
public class CheckXfade {
    static PcmData dc(int amp, int frames) {
        PcmData.Builder b = new PcmData.Builder();
        for (int i = 0; i < frames; i++) b.append((short) amp, (short) amp);
        return b.build(44100);
    }
    public static void main(String[] a) {
        ScratchEngine e = new ScratchEngine();
        e.setOutputRate(44100);
        e.load(dc(10000, 44100 * 3));
        e.queueNext(dc(5000, 44100 * 3));
        e.setCrossfade(2);
        e.seekTo(0.5);
        e.setPlaying(true);
        short[] buf = new short[512];
        int prev = -1, maxJump = 0, maxVal = 0, minAfter = 99999;
        boolean swapped = false, ended = false;
        double posAtSwap = -1;
        int blocks = (int) (3.1 * 44100 / 256);
        for (int i = 0; i < blocks; i++) {
            e.render(buf, 256);
            for (int k = 0; k < 256; k++) {
                int v = buf[2 * k];
                if (i > 200 && prev >= 0) maxJump = Math.max(maxJump, Math.abs(v - prev));
                prev = v;
                maxVal = Math.max(maxVal, v);
            }
            if (e.consumeSwapped() && !swapped) { swapped = true; posAtSwap = e.getPositionSeconds(); }
            if (e.consumeEnded()) ended = true;
        }
        System.out.println("swapped=" + swapped + " posAtSwap=" + posAtSwap + " maxJump=" + maxJump + " max=" + maxVal + " last=" + prev + " ended=" + ended);
        boolean ok = swapped && !ended && maxJump < 400 && maxVal < 12000 && Math.abs(prev - 5000) < 50 && posAtSwap > 1.5;
        System.out.println(ok ? "XFADE OK" : "XFADE FALLO");
    }
}
