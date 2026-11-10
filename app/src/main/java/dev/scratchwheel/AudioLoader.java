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

    static PcmData load(Context c, Uri uri) throws IOException {
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) {
                throw new IOException("No se pudo abrir el archivo");
            }
            try {
                return WavParser.parse(in);
            } finally {
                in.close();
            }
        } catch (WavParser.NotWavException e) {
            return decode(c, uri);
        }
    }

    private static PcmData decode(Context c, Uri uri) throws IOException {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
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
                throw new IOException("El archivo no tiene audio");
            }
            ex.selectTrack(track);
            int sr = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int ch = Math.max(1, fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT));

            codec = MediaCodec.createDecoderByType(mime);
            codec.configure(fmt, null, null, 0);
            codec.start();

            long expected = fmt.containsKey(MediaFormat.KEY_DURATION)
                    ? fmt.getLong(MediaFormat.KEY_DURATION) * sr / 1000000L : 0;
            PcmData.Builder b = new PcmData.Builder(expected);
            boolean isFloat = false;
            short[] tmp = new short[8192];
            float[] ftmp = new float[8192];
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inDone = false;
            boolean outDone = false;
            while (!outDone) {
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
                throw new IOException("No se pudo decodificar el audio");
            }
            return b.build(sr);
        } catch (IllegalStateException e) {
            throw new IOException("Formato no soportado");
        } finally {
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
