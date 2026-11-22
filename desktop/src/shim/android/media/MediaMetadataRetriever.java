package android.media;

import android.content.Context;
import android.net.Uri;
import java.io.File;

public class MediaMetadataRetriever implements AutoCloseable {
    public static final int METADATA_KEY_CD_TRACK_NUMBER = 0;
    public static final int METADATA_KEY_ALBUM = 1;
    public static final int METADATA_KEY_ARTIST = 2;
    public static final int METADATA_KEY_AUTHOR = 3;
    public static final int METADATA_KEY_COMPOSER = 4;
    public static final int METADATA_KEY_DATE = 5;
    public static final int METADATA_KEY_GENRE = 6;
    public static final int METADATA_KEY_TITLE = 7;
    public static final int METADATA_KEY_YEAR = 8;
    public static final int METADATA_KEY_DURATION = 9;
    public static final int METADATA_KEY_NUM_TRACKS = 10;
    public static final int METADATA_KEY_WRITER = 11;
    public static final int METADATA_KEY_MIMETYPE = 12;
    public static final int METADATA_KEY_ALBUMARTIST = 13;
    public static final int METADATA_KEY_DISC_NUMBER = 14;
    public static final int METADATA_KEY_BITRATE = 20;
    public static final int METADATA_KEY_SAMPLERATE = 38;

    private File file;
    private desk.Tags tags;
    private desk.Tags withPicture;

    public void setDataSource(Context c, Uri u) {
        File f = u == null ? null : u.toFile();
        if (f == null || !f.isFile()) {
            throw new IllegalArgumentException("no es un archivo: " + u);
        }
        file = f;
        tags = null;
        withPicture = null;
    }

    public void setDataSource(String path) {
        file = new File(path);
        if (!file.isFile()) {
            throw new IllegalArgumentException("no es un archivo: " + path);
        }
        tags = null;
        withPicture = null;
    }

    public void setDataSource(java.io.FileDescriptor fd) {
        throw new IllegalArgumentException("sin soporte en escritorio");
    }

    public String extractMetadata(int key) {
        if (file == null) {
            return null;
        }
        if (tags == null) {
            tags = withPicture != null ? withPicture : desk.Tags.read(file, false);
        }
        switch (key) {
            case METADATA_KEY_TITLE:
                return tags.title;
            case METADATA_KEY_ARTIST:
                return tags.artist;
            case METADATA_KEY_ALBUMARTIST:
                return tags.albumArtist;
            case METADATA_KEY_ALBUM:
                return tags.album;
            case METADATA_KEY_DURATION:
                return tags.durationMs > 0 ? String.valueOf(tags.durationMs) : null;
            case METADATA_KEY_SAMPLERATE:
                return tags.sampleRate > 0 ? String.valueOf(tags.sampleRate) : null;
            case METADATA_KEY_MIMETYPE:
                return android.content.ContentResolver.mimeOf(file.getName());
            default:
                return null;
        }
    }

    public byte[] getEmbeddedPicture() {
        if (file == null) {
            return null;
        }
        if (withPicture == null) {
            withPicture = desk.Tags.read(file, true);
        }
        return withPicture.picture;
    }

    public void release() {
        file = null;
        tags = null;
        withPicture = null;
    }

    @Override
    public void close() {
        release();
    }
}
