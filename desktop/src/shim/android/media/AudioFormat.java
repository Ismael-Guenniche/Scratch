package android.media;

public final class AudioFormat {
    public static final int ENCODING_PCM_16BIT = 2;
    public static final int ENCODING_PCM_8BIT = 3;
    public static final int ENCODING_PCM_FLOAT = 4;
    public static final int CHANNEL_OUT_MONO = 4;
    public static final int CHANNEL_OUT_STEREO = 12;
    int rate = 48000;
    int encoding = ENCODING_PCM_16BIT;
    int mask = CHANNEL_OUT_STEREO;

    public static class Builder {
        private final AudioFormat f = new AudioFormat();

        public Builder setEncoding(int e) {
            f.encoding = e;
            return this;
        }

        public Builder setSampleRate(int r) {
            f.rate = r;
            return this;
        }

        public Builder setChannelMask(int m) {
            f.mask = m;
            return this;
        }

        public AudioFormat build() {
            return f;
        }
    }

    public int getSampleRate() {
        return rate;
    }

    public int getEncoding() {
        return encoding;
    }

    public int getChannelCount() {
        return mask == CHANNEL_OUT_MONO ? 1 : 2;
    }
}
