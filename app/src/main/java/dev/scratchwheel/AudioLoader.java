package dev.scratchwheel;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Carga audio local: WAV con lector propio, y MP3/AAC/OGG/FLAC con el decodificador de Android. */
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
    private static synchronized java.io.File pcmDir(Context c) {
        java.io.File d = new java.io.File(c.getCacheDir(), "pcm");
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
    static PcmData load(Context c, Uri uri, Early early,
                        java.util.concurrent.atomic.AtomicBoolean cancel) throws IOException {
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException(Tr.t("No se pudo abrir el archivo"));
            }
            try {
                return withPeaks(c, uri, WavParser.parse(in, pcmDir(c)));
            } finally {
                in.close();
            }
        } catch (WavParser.NotWavException e) {
            try {
                return withPeaks(c, uri, decode(c, uri, early, cancel, pcmDir(c)));
            } catch (PcmData.DiskError de) {
                // el disco falló a mitad (sin espacio…): otra vez, en memoria como antes
                return decode(c, uri, null, cancel, null);
            }
        } catch (PcmData.DiskError de) {
            InputStream in2 = c.getContentResolver().openInputStream(uri);
            if (in2 == null) {
                throw new IOException(Tr.t("No se pudo abrir el archivo"));
            }
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
            java.io.File dir = new java.io.File(c.getCacheDir(), "wave");
            float[] cached = WaveCache.get(dir, uri.toString(), pcm.frames, PcmData.PEAKS);
            if (cached != null) {
                pcm.peaks = cached;
            } else {
                WaveCache.put(dir, uri.toString(), pcm.frames, pcm.peaks);
            }
        }
        return pcm;
    }

    private static PcmData decode(Context c, Uri uri, Early early,
                                  java.util.concurrent.atomic.AtomicBoolean cancel, java.io.File dir)
            throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        PcmData.Builder b = null;
        try {
            ex.setDataSource(c, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            String mime = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    mime = m;
                    break;
                }
            }
            if (track < 0) {
                throw new IOException(Tr.t("El archivo no tiene audio"));
            }
            ex.selectTrack(track);
            int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int ch = Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            long expected = fmt.containsKey(MediaFormat.KEY_DURATION)
                    ? fmt.getLong(MediaFormat.KEY_DURATION) * sr / 1000000L : 0;
            b = new PcmData.Builder(expected, dir);
            boolean isFloat = false;
            short[] tmp = new short[8192];
            float[] ftmp = new float[8192];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            boolean earlySent = early == null || expected <= 0;
            while (!outDone) {
                if (cancel != null && cancel.get()) {
                    throw new IOException("cancelado");
                }
                if (!earlySent && b.frames() >= (long) sr * EARLY_SECONDS && expected > b.frames() * 5L / 4) {
                    earlySent = true;
                    early.onEarly(b.snapshot(sr, expected));
                }
                if (!inDone) {
                    int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(ii);
                        int sz = ex.readSampleData(ib, 0);
                        if (sz < 0) {
                            codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inDone = true;
                        } else {
                            codec.queueInputBuffer(ii, 0, sz, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    if (info.size > 0 && ob != null) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.order(ByteOrder.nativeOrder());
                        if (isFloat) {
                            FloatBuffer fb = ob.asFloatBuffer();
                            while (fb.remaining() >= ch) {
                                int take = Math.min(fb.remaining(), ftmp.length) / ch * ch;
                                fb.get(ftmp, 0, take);
                                b.appendFloat(ftmp, take, ch);
                            }
                        } else {
                            ShortBuffer sb = ob.asShortBuffer();
                            while (sb.remaining() >= ch) {
                                int take = Math.min(sb.remaining(), tmp.length) / ch * ch;
                                sb.get(tmp, 0, take);
                                b.appendInterleaved(tmp, take, ch);
                            }
                        }
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outDone = true;
                    }
                } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat nf = codec.getOutputFormat();
                    if (nf.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sr = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (nf.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        ch = Math.max(1, nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
                    }
                    // 4 = ENCODING_PCM_FLOAT; por defecto el decodificador entrega 16 bits.
                    isFloat = nf.containsKey("pcm-encoding") && nf.getInteger("pcm-encoding") == 4;
                }
            }
            if (b.frames() == 0) {
                throw new IOException(Tr.t("No se pudo decodificar el audio"));
            }
            return b.build(sr);
        } catch (IllegalStateException e) {
            throw new IOException(Tr.t("Formato no soportado"));
        } finally {
            if (b != null) {
                b.abort(); // cierra y borra el temporal (si ya se proyectó, la pista sigue viva)
            }
            if (codec != null) {
                try {
                    codec.stop();
                } catch (IllegalStateException ignored) {
                }
                codec.release();
            }
            ex.release();
        }
    }
}
