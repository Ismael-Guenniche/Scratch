package android.media.session;

public final class PlaybackState {
    public static final int STATE_NONE = 0;
    public static final int STATE_STOPPED = 1;
    public static final int STATE_PAUSED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_FAST_FORWARDING = 4;
    public static final int STATE_REWINDING = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_ERROR = 7;
    public static final long ACTION_STOP = 1;
    public static final long ACTION_PAUSE = 2;
    public static final long ACTION_PLAY = 4;
    public static final long ACTION_REWIND = 8;
    public static final long ACTION_SKIP_TO_PREVIOUS = 16;
    public static final long ACTION_SKIP_TO_NEXT = 32;
    public static final long ACTION_FAST_FORWARD = 64;
    public static final long ACTION_SEEK_TO = 256;
    public static final long ACTION_PLAY_PAUSE = 512;
    public static final long ACTION_PLAY_FROM_MEDIA_ID = 1024;
    public static final long ACTION_PLAY_FROM_SEARCH = 2048;
    public static final long ACTION_SKIP_TO_QUEUE_ITEM = 4096;
    public static final long PLAYBACK_POSITION_UNKNOWN = -1;
    private int state;
    private long position;
    private float speed;

    public static final class Builder {
        private final PlaybackState s = new PlaybackState();

        public Builder setActions(long a) {
            return this;
        }

        public Builder setState(int state, long pos, float speed) {
            s.state = state;
            s.position = pos;
            s.speed = speed;
            return this;
        }

        public Builder setState(int state, long pos, float speed, long time) {
            return setState(state, pos, speed);
        }

        public Builder setActiveQueueItemId(long id) {
            return this;
        }

        public Builder setErrorMessage(CharSequence m) {
            return this;
        }

        public Builder addCustomAction(String a, String n, int icon) {
            return this;
        }

        public PlaybackState build() {
            return s;
        }
    }

    public int getState() {
        return state;
    }

    public long getPosition() {
        return position;
    }

    public float getPlaybackSpeed() {
        return speed;
    }
}
