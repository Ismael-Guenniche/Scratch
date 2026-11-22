package android.provider;

import android.net.Uri;
import java.io.File;

/**
 * En el escritorio un "árbol" es una carpeta normal. El id de documento imita al de Android
 * ("C:Users/yo/Music", como "primary:Music") para que las etiquetas se vean igual.
 */
public final class DocumentsContract {
    public static final String EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI";
    public static final String CHILDREN_SCHEME = "deskchildren";

    public static final class Document {
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_DISPLAY_NAME = "_display_name";
        public static final String COLUMN_MIME_TYPE = "mime_type";
        public static final String COLUMN_SIZE = "_size";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
        public static final String COLUMN_FLAGS = "flags";
        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";
    }

    private DocumentsContract() {
    }

    public static String idOf(File f) {
        String p = f.getAbsolutePath().replace('\\', '/');
        if (p.length() > 2 && p.charAt(1) == ':' && p.charAt(2) == '/') {
            return p.substring(0, 2) + p.substring(3);
        }
        return p;
    }

    public static File fileOf(String id) {
        if (id.length() >= 2 && id.charAt(1) == ':' && (id.length() == 2 || id.charAt(2) != '/')) {
            return new File(id.substring(0, 2) + "/" + id.substring(2));
        }
        return new File(id);
    }

    public static String getTreeDocumentId(Uri tree) {
        File f = tree.toFile();
        if (f == null) {
            throw new IllegalArgumentException("no es una carpeta: " + tree);
        }
        return idOf(f);
    }

    public static String getDocumentId(Uri doc) {
        File f = doc.toFile();
        if (f == null) {
            throw new IllegalArgumentException("no es un archivo: " + doc);
        }
        return idOf(f);
    }

    public static Uri buildChildDocumentsUriUsingTree(Uri tree, String parentId) {
        return Uri.parse(CHILDREN_SCHEME + ":///" + Uri.encode(parentId));
    }

    public static Uri buildDocumentUriUsingTree(Uri tree, String id) {
        return Uri.fromFile(fileOf(id));
    }

    public static Uri buildTreeDocumentUri(String authority, String id) {
        return Uri.fromFile(fileOf(id));
    }

    public static Uri buildDocumentUri(String authority, String id) {
        return Uri.fromFile(fileOf(id));
    }

    public static boolean isTreeUri(Uri u) {
        File f = u.toFile();
        return f != null && f.isDirectory();
    }

    public static boolean isDocumentUri(android.content.Context c, Uri u) {
        return u.toFile() != null;
    }

    public static boolean deleteDocument(android.content.ContentResolver cr, Uri u) {
        File f = u.toFile();
        return f != null && f.delete();
    }
}
