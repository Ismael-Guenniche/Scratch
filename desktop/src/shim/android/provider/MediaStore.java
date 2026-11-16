package android.provider;

import android.net.Uri;

public final class MediaStore {
    public static final class Audio {
        public static final class Media {
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/media");
            public static final String DISPLAY_NAME = "_display_name";
            public static final String MIME_TYPE = "mime_type";
            public static final String RELATIVE_PATH = "relative_path";
            public static final String IS_PENDING = "is_pending";
            public static final String TITLE = "title";
            public static final String ARTIST = "artist";
            public static final String ALBUM = "album";
            public static final String DURATION = "duration";
            public static final String DATA = "_data";
            public static final String _ID = "_id";
        }
    }
}
