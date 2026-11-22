package android.media.session;

import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadata;
import android.os.Bundle;

/** Sin sistema detrás: guarda el estado y avisa al escritorio para el título de la ventana. */
public final class MediaSession {
    public static final int FLAG_HANDLES_MEDIA_BUTTONS = 1;
    public static final int FLAG_HANDLES_TRANSPORT_CONTROLS = 2;

    public static final class Token {
    }

    /** Elemento de la cola (en el escritorio solo se guarda). */
    public static final class QueueItem {
        private final android.media.MediaDescription desc;
        private final long id;

        public QueueItem(android.media.MediaDescription desc, long id) {
            this.desc = desc;
            this.id = id;
        }

        public android.media.MediaDescription getDescription() {
            return desc;
        }

        public long getQueueId() {
            return id;
        }
    }

    public abstract static class Callback {
        public void onPlay() {
        }

        public void onPause() {
        }

        public void onStop() {
        }

        public void onSkipToNext() {
        }

        public void onSkipToPrevious() {
        }

        public void onFastForward() {
        }

        public void onRewind() {
        }

        public void onSeekTo(long pos) {
        }

        public void onPlayFromMediaId(String mediaId, Bundle extras) {
        }

        public void onPlayFromSearch(String query, Bundle extras) {
        }

        public void onCustomAction(String action, Bundle extras) {
        }

        public void onSkipToQueueItem(long id) {
        }

        public boolean onMediaButtonEvent(Intent i) {
            return false;
        }
    }

    /** Escritorio: última sesión creada y oyente de cambios (título, estado). */
    public static volatile MediaSession current;
    public static volatile Runnable onChange;

    private final Token token = new Token();
    private Callback callback;
    private MediaMetadata metadata;
    private PlaybackState state;
    private boolean active;

    public MediaSession(Context c, String tag) {
        current = this;
    }

    public void setCallback(Callback cb) {
        callback = cb;
    }

    public void setCallback(Callback cb, android.os.Handler h) {
        callback = cb;
    }

    public Callback callback() {
        return callback;
    }

    public void setFlags(int f) {
    }

    public void setActive(boolean a) {
        active = a;
    }

    public boolean isActive() {
        return active;
    }

    public void setMetadata(MediaMetadata m) {
        metadata = m;
        changed();
    }

    public MediaMetadata metadata() {
        return metadata;
    }

    public void setPlaybackState(PlaybackState s) {
        state = s;
        changed();
    }

    public PlaybackState playbackState() {
        return state;
    }

    private void changed() {
        Runnable r = onChange;
        if (r != null) {
            r.run();
        }
    }

    public void setSessionActivity(android.app.PendingIntent pi) {
    }

    public void setMediaButtonReceiver(android.app.PendingIntent pi) {
    }

    public void setQueue(java.util.List<?> q) {
    }

    public void setQueueTitle(CharSequence t) {
    }

    public void setExtras(Bundle b) {
    }

    public Token getSessionToken() {
        return token;
    }

    public void release() {
        if (current == this) {
            current = null;
        }
    }
}
