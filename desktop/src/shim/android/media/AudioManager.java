package android.media;

/** Volumen propio de la app (0..100) y propiedades de salida. El foco de audio siempre se concede. */
public class AudioManager {
    public static final int STREAM_VOICE_CALL = 0;
    public static final int STREAM_SYSTEM = 1;
    public static final int STREAM_RING = 2;
    public static final int STREAM_MUSIC = 3;
    public static final int STREAM_ALARM = 4;
    public static final int STREAM_NOTIFICATION = 5;
    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT = -2;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3;
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
    public static final int FLAG_SHOW_UI = 1;
    public static final int FLAG_PLAY_SOUND = 4;
    public static final int ADJUST_RAISE = 1;
    public static final int ADJUST_LOWER = -1;
    public static final int ADJUST_SAME = 0;
    public static final int GET_DEVICES_OUTPUTS = 2;
    public static final String ACTION_AUDIO_BECOMING_NOISY = "android.media.AUDIO_BECOMING_NOISY";
    public static final String PROPERTY_OUTPUT_SAMPLE_RATE = "android.media.property.OUTPUT_SAMPLE_RATE";
    public static final String PROPERTY_OUTPUT_FRAMES_PER_BUFFER =
            "android.media.property.OUTPUT_FRAMES_PER_BUFFER";
    public static final int MAX = 100;

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
    }

    private int volume = -1;

    public String getProperty(String key) {
        if (PROPERTY_OUTPUT_SAMPLE_RATE.equals(key)) {
            return System.getProperty("scratch.rate", "48000");
        }
        if (PROPERTY_OUTPUT_FRAMES_PER_BUFFER.equals(key)) {
            return System.getProperty("scratch.burst", "480");
        }
        return null;
    }

    public int requestAudioFocus(OnAudioFocusChangeListener l, int stream, int hint) {
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int abandonAudioFocus(OnAudioFocusChangeListener l) {
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int getStreamMaxVolume(int stream) {
        return MAX;
    }

    public int getStreamMinVolume(int stream) {
        return 0;
    }

    public synchronized int getStreamVolume(int stream) {
        if (volume < 0) {
            volume = desk.Prefs.get("desk").getInt("volume", 80);
            AudioTrack.setMasterVolume(volume / (float) MAX);
        }
        return volume;
    }

    public synchronized void setStreamVolume(int stream, int index, int flags) {
        volume = Math.max(0, Math.min(MAX, index));
        AudioTrack.setMasterVolume(volume / (float) MAX);
        desk.Prefs.get("desk").edit().putInt("volume", volume).apply();
    }

    public void adjustStreamVolume(int stream, int direction, int flags) {
        setStreamVolume(stream, getStreamVolume(stream) + direction * 4, flags);
    }

    public void adjustVolume(int direction, int flags) {
        adjustStreamVolume(STREAM_MUSIC, direction, flags);
    }

    public boolean isMusicActive() {
        return false;
    }

    public boolean isStreamMute(int stream) {
        return getStreamVolume(stream) == 0;
    }

    public boolean isWiredHeadsetOn() {
        return false;
    }

    public boolean isBluetoothA2dpOn() {
        return false;
    }

    public int getMode() {
        return 0;
    }

    public int getRingerMode() {
        return 2;
    }
}
