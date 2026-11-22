package android.media;

/** Descripción de un elemento multimedia (título, subtítulo, id); en el escritorio solo se guarda. */
public final class MediaDescription {
    private String mediaId;
    private CharSequence title;
    private CharSequence subtitle;

    public String getMediaId() {
        return mediaId;
    }

    public CharSequence getTitle() {
        return title;
    }

    public CharSequence getSubtitle() {
        return subtitle;
    }

    public static final class Builder {
        private final MediaDescription d = new MediaDescription();

        public Builder setMediaId(String id) {
            d.mediaId = id;
            return this;
        }

        public Builder setTitle(CharSequence t) {
            d.title = t;
            return this;
        }

        public Builder setSubtitle(CharSequence t) {
            d.subtitle = t;
            return this;
        }

        public Builder setIconBitmap(android.graphics.Bitmap b) {
            return this;
        }

        public Builder setExtras(android.os.Bundle b) {
            return this;
        }

        public MediaDescription build() {
            return d;
        }
    }
}
