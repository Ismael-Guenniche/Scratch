package dev.scratchwheel;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Versión de escritorio de AudioLoader (misma interfaz que la de Android): WAV con el lector propio,
 * MP3 con JLayer, FLAC con el decodificador de desk.Flac y el resto (M4A, OGG, Opus, WMA) con ffmpeg
 * si está instalado.
 */
final class AudioLoader {
    private AudioLoader() {
    }

    static PcmData loadDemo() {
        return DemoTrack.generate();
    }

    /** Se llama una vez con el principio de la pista en cuanto está decodificado (arranque rápido). */
    interface Early {
        void onEarly(PcmData part);
    }

    private static final int EARLY_SECONDS = 8;
    private static boolean cleaned;

    /** Carpeta del PCM temporal (caché de la app). La primera vez borra lo que dejara una ejecución anterior. */
    private static synchronized File pcmDir(Context c) {
        File d = new File(c.getCacheDir(), "pcm");
        if (!cleaned) {
            cleaned = true;
            PcmData.cleanDir(d);
        }
        return d;
    }

    static PcmData load(Context c, Uri uri) throws IOException {
        return load(c, uri, null, null);
    }

    /** {@code early} recibe un fragmento al principio y {@code cancel} permite abandonar la decodificación. */
    static PcmData load(Context c, Uri uri, Early early, AtomicBoolean cancel) throws IOException {
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            try {
                return withPeaks(c, uri, WavParser.parse(in, pcmDir(c)));
            } finally {
                in.close();
            }
        } catch (WavParser.NotWavException e) {
            try {
                return withPeaks(c, uri, decode(uri, early, cancel, pcmDir(c)));
            } catch (PcmData.DiskError de) {
                // el disco falló a mitad (sin espacio…): otra vez, en memoria
                return decode(uri, null, cancel, null);
            }
        } catch (PcmData.DiskError de) {
            InputStream in2 = c.getContentResolver().openInputStream(uri);
            try {
                return WavParser.parse(in2);
            } finally {
                in2.close();
            }
        }
    }

    /** Forma de onda: de la caché si la canción ya se analizó; si no, se guarda la recién calculada. */
    private static PcmData withPeaks(Context c, Uri uri, PcmData pcm) {
        if (pcm.peaks != null && uri != null) {
            File dir = new File(c.getCacheDir(), "wave");
            float[] cached = WaveCache.get(dir, uri.toString(), pcm.frames, PcmData.PEAKS);
            if (cached != null) {
                pcm.peaks = cached;
            } else {
                WaveCache.put(dir, uri.toString(), pcm.frames, pcm.peaks);
            }
        }
        return pcm;
    }

    private static final int MP3 = 0;
    private static final int FLAC = 1;
    private static final int OTHER = 2;

    /** Mira la cabecera (saltando la etiqueta ID3v2) para saber qué decodificador toca. */
    private static int kind(File f) throws IOException {
        java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
        try {
            byte[] h = new byte[12];
            long pos = 0;
            raf.readFully(h, 0, (int) Math.min(10, raf.length()));
            if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
                pos = 10 + (((h[6] & 0x7F) << 21) | ((h[7] & 0x7F) << 14) | ((h[8] & 0x7F) << 7) | (h[9] & 0x7F));
            }
            if (pos + 12 <= raf.length()) {
                raf.seek(pos);
                raf.readFully(h, 0, 12);
                if (h[0] == 'f' && h[1] == 'L' && h[2] == 'a' && h[3] == 'C') {
                    return FLAC;
                }
                if ((h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S')
                        || (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p')) {
                    return OTHER;
                }
            }
        } finally {
            raf.close();
        }
        String n = f.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".ogg") || n.endsWith(".opus")
                || n.endsWith(".wma") || n.endsWith(".mp4") || n.endsWith(".webm")) {
            return OTHER;
        }
        return n.endsWith(".flac") ? FLAC : MP3;
    }

    /** Recoge las muestras decodificadas en el PcmData.Builder y avisa del arranque rápido. */
    private static final class Feed implements desk.Mp3.Sink, desk.Flac.Sink {
        final Early early;
        final AtomicBoolean cancel;
        final File dir;
        final long durationMs;
        PcmData.Builder b;
        int rate;
        long expected;
        boolean earlySent;

        Feed(Early early, AtomicBoolean cancel, File dir, long durationMs) {
            this.early = early;
            this.cancel = cancel;
            this.dir = dir;
            this.durationMs = durationMs;
        }

        @Override
        public boolean cancelled() {
            return cancel != null && cancel.get();
        }

        @Override
        public void samples(short[] s, int n, int ch, int sr) {
            if (b == null) {
                rate = sr;
                expected = durationMs > 0 ? durationMs * sr / 1000L : 0;
                b = new PcmData.Builder(expected, dir);
                earlySent = early == null || expected <= 0;
            }
            if (!earlySent && b.frames() >= (long) rate * EARLY_SECONDS && expected > b.frames() * 5L / 4) {
                earlySent = true;
                early.onEarly(b.snapshot(rate, expected));
            }
            b.appendInterleaved(s, n, ch);
        }
    }

    private static PcmData decode(Uri uri, Early early, AtomicBoolean cancel, File dir) throws IOException {
        final File f = uri == null ? null : uri.toFile();
        if (f == null || !f.isFile()) {
            throw new IOException(Tr.t("No se pudo abrir el archivo"));
        }
        desk.Tags tags = desk.Tags.read(f, false);
        final Feed feed = new Feed(early, cancel, dir, tags.durationMs);
        try {
            int kind = kind(f);
            if (kind == FLAC) {
                InputStream in = new FileInputStream(f);
                try {
                    desk.Flac.decode(in, feed);
                } finally {
                    in.close();
                }
            } else if (kind == MP3) {
                InputStream in = new FileInputStream(f);
                try {
                    desk.Mp3.decode(in, feed);
                } finally {
                    in.close();
                }
            } else {
                final int rate = 48000;
                desk.Ffmpeg.decode(f, rate, new desk.Ffmpeg.Sink() {
                    @Override
                    public void samples(short[] s, int n) {
                        feed.samples(s, n, 2, rate);
                    }

                    @Override
                    public boolean cancelled() {
                        return feed.cancelled();
                    }
                });
            }
            if (feed.cancelled()) {
                throw new IOException("cancelado");
            }
            if (feed.b == null || feed.b.frames() == 0) {
                throw new IOException(Tr.t("No se pudo decodificar el audio"));
            }
            return feed.b.build(feed.rate);
        } catch (PcmData.DiskError de) {
            throw de;
        } catch (IOException e) {
            if (feed.cancelled()) {
                throw new IOException("cancelado");
            }
            if (feed.b != null && feed.b.frames() > feed.rate) {
                // archivo dañado a mitad: se queda con lo decodificado
                return feed.b.build(feed.rate);
            }
            if (desk.Ffmpeg.find() == null && e.getMessage() != null && e.getMessage().startsWith("ffmpeg")) {
                throw new IOException(Tr.t("Formato no soportado") + " · ffmpeg");
            }
            throw new IOException(Tr.t("Formato no soportado"));
        } catch (RuntimeException e) {
            if (feed.b != null && feed.b.frames() > feed.rate) {
                return feed.b.build(feed.rate);
            }
            throw new IOException(Tr.t("Formato no soportado"));
        } finally {
            if (feed.b != null) {
                feed.b.abort(); // cierra y borra el temporal (si ya se proyectó, la pista sigue viva)
            }
        }
    }
}
