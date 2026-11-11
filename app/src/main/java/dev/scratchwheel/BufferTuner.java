package dev.scratchwheel;

/**
 * Decide el tamaño efectivo del búfer del AudioTrack: empieza pequeño (poca latencia) y solo crece, de
 * ráfaga en ráfaga, cuando el dispositivo demuestra con underruns que no llega. Sin dependencias de Android.
 */
public final class BufferTuner {
    private final int block;
    private final int capacity;
    private int frames;
    private int lastUnderruns = -1;
    private int warm;
    private int cool;
    private int grown;

    /**
     * @param block       frames por bloque de render (la ráfaga del dispositivo)
     * @param capacity    capacidad real del AudioTrack en frames
     * @param startFrames tamaño aprendido en un arranque anterior (0 = empezar en dos bloques)
     */
    public BufferTuner(int block, int capacity, int startFrames) {
        this.block = Math.max(16, block);
        this.capacity = Math.max(this.block, capacity);
        this.frames = Math.min(this.capacity, Math.max(2 * this.block, startFrames));
        // Los primeros ~0,3 s no cuentan: al arrancar siempre hay algún underrun.
        this.warm = Math.max(8, 14400 / this.block);
    }

    /** Bloque de render recomendado para una ráfaga nativa (se usa tal cual si es razonable). */
    public static int blockFor(int burst) {
        return burst >= 64 && burst <= 512 ? burst : 256;
    }

    /** Capacidad del AudioTrack con la que se creó. */
    public int capacity() {
        return capacity;
    }

    /** Tamaño efectivo actual en frames. */
    public int frames() {
        return frames;
    }

    /** Cuántas veces ha tenido que crecer. */
    public int grown() {
        return grown;
    }

    /**
     * Se llama una vez por bloque con el contador de underruns del AudioTrack. Devuelve el nuevo tamaño
     * a aplicar, o -1 si no hay que cambiar nada.
     */
    public int onBlock(int underruns) {
        if (warm > 0) {
            warm--;
            lastUnderruns = underruns;
            return -1;
        }
        if (cool > 0) {
            cool--;
            lastUnderruns = underruns;
            return -1;
        }
        if (lastUnderruns >= 0 && underruns > lastUnderruns && frames < capacity) {
            // Cada vez crece más (1, 2, 4, 8 bloques): si la salida es lenta (p. ej. Bluetooth) converge en ~2 s.
            frames = Math.min(capacity, frames + block * (1 << Math.min(grown, 3)));
            grown++;
            // ~0,5 s de calma antes de volver a reaccionar: un mismo tropiezo no debe contar varias veces.
            cool = Math.max(8, 24000 / block);
            lastUnderruns = underruns;
            return frames;
        }
        lastUnderruns = underruns;
        return -1;
    }

    /** El AudioTrack puede ajustar lo pedido: se guarda lo que aplicó de verdad. */
    public void applied(int actual) {
        if (actual > 0) {
            frames = Math.min(capacity, actual);
        }
    }

    /** Latencia que añade el búfer, en milisegundos. */
    public double millis(int rate) {
        return frames * 1000.0 / rate;
    }
}
