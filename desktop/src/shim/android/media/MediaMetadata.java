package android.media;

import android.graphics.Bitmap;
import java.util.HashMap;

public final class MediaMetadata {
    public static final String METADATA_KEY_TITLE = "android.media.metadata.TITLE";
    public static final String METADATA_KEY_ARTIST = "android.media.metadata.ARTIST";
    public static final String METADATA_KEY_ALBUM = "android.media.metadata.ALBUM";
    public static final String METADATA_KEY_ALBUM_ARTIST = "android.media.metadata.ALBUM_ARTIST";
    public static final String METADATA_KEY_DURATION = "android.media.metadata.DURATION";
    public static final String METADATA_KEY_ALBUM_ART = "android.media.metadata.ALBUM_ART";
    public static final String METADATA_KEY_ART = "android.media.metadata.ART";
    public static final String METADATA_KEY_DISPLAY_ICON = "android.media.metadata.DISPLAY_ICON";
    public static final String METADATA_KEY_DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE";
    public static final String METADATA_KEY_DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE";
    public static final String METADATA_KEY_MEDIA_ID = "android.media.metadata.MEDIA_ID";
    private final HashMap<String, Object> map = new HashMap<String, Object>();

    public static final class Builder {
        private final MediaMetadata m = new MediaMetadata();

        public Builder putString(String k, String v) {
            m.map.put(k, v);
            return this;
        }

        public Builder putText(String k, CharSequence v) {
            m.map.put(k, v == null ? null : v.toString());
            return this;
        }

        public Builder putLong(String k, long v) {
            m.map.put(k, v);
            return this;
        }

        public Builder putBitmap(String k, Bitmap v) {
            m.map.put(k, v);
            return this;
        }

        public MediaMetadata build() {
            return m;
        }
    }

    public String getString(String k) {
        Object o = map.get(k);
        return o instanceof String ? (String) o : null;
    }

    public long getLong(String k) {
        Object o = map.get(k);
        return o instanceof Long ? (Long) o : 0;
    }

    public Bitmap getBitmap(String k) {
        Object o = map.get(k);
        return o instanceof Bitmap ? (Bitmap) o : null;
    }
}
