package android.content;

import android.net.Uri;

public final class UriPermission {
    private final Uri uri;

    public UriPermission(Uri u) {
        uri = u;
    }

    public Uri getUri() {
        return uri;
    }

    public boolean isReadPermission() {
        return true;
    }

    public boolean isWritePermission() {
        return true;
    }

    public long getPersistedTime() {
        return 0;
    }
}
