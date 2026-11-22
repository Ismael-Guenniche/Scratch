package desk;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.GaplessTrim;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** MP3 con JLayer (LGPL, en desktop/third_party/jlayer), quitando el relleno del codificador (gapless). */
public final class Mp3 {
    public interface Sink {
        void samples(short[] interleaved, int n, int channels, int sampleRate) throws IOException;

        boolean cancelled();
    }

    private Mp3() {
    }

    public static void decode(InputStream raw, Sink sink) throws IOException {
        Bitstream bs = new Bitstream(new BufferedInputStream(raw, 1 << 16));
        Decoder dec = new Decoder();
        GaplessTrim trim = null;
        short[] tmp = new short[0];
        int errors = 0;
        long frames = 0;
        try {
            while (!sink.cancelled()) {
                Header h;
                try {
                    h = bs.readFrame();
                } catch (BitstreamException e) {
                    if (++errors > 64) {
                        break;
                    }
                    continue;
                }
                if (h == null) {
                    break;
                }
                try {
                    SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                    int ch = out.getChannelCount();
                    int n = out.getBufferLength();
                    short[] buf = out.getBuffer();
                    if (trim == null) {
                        trim = GaplessTrim.of(h, ch);
                    }
                    int keep = trim.accept(n);
                    if (keep > 0) {
                        int off = trim.offset();
                        if (off > 0) {
                            if (tmp.length < keep) {
                                tmp = new short[Math.max(keep, 2304)];
                            }
                            System.arraycopy(buf, off, tmp, 0, keep);
                            sink.samples(tmp, keep, ch, out.getSampleFrequency());
                        } else {
                            sink.samples(buf, keep, ch, out.getSampleFrequency());
                        }
                    }
                    frames++;
                    errors = 0;
                } catch (DecoderException | ArrayIndexOutOfBoundsException e) {
                    if (++errors > 64) {
                        break;
                    }
                } finally {
                    bs.closeFrame();
                }
            }
        } finally {
            try {
                bs.close();
            } catch (BitstreamException ignored) {
            }
        }
        if (frames == 0 && !sink.cancelled()) {
            throw new IOException("mp3: sin audio");
        }
    }
}
