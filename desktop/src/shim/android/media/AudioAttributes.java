package android.media;

public final class AudioAttributes {
    public static final int USAGE_MEDIA = 1;
    public static final int USAGE_GAME = 14;
    public static final int CONTENT_TYPE_SPEECH = 1;
    public static final int CONTENT_TYPE_MUSIC = 2;
    public static final int CONTENT_TYPE_SONIFICATION = 4;

    public static class Builder {
        public Builder setUsage(int u) {
            return this;
        }

        public Builder setContentType(int t) {
            return this;
        }

        public Builder setFlags(int f) {
            return this;
        }

        public Builder setLegacyStreamType(int s) {
            return this;
        }

        public AudioAttributes build() {
            return new AudioAttributes();
        }
    }
}
