package android.content;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Acceso a archivos locales con la forma de la API de Android (file:// en vez de content://). */
public class ContentResolver {
    private static final String[][] MIME = {
        {".mp3", "audio/mpeg"}, {".wav", "audio/x-wav"}, {".m4a", "audio/mp4"}, {".aac", "audio/aac"},
        {".ogg", "audio/ogg"}, {".opus", "audio/opus"}, {".flac", "audio/flac"}, {".wma", "audio/x-ms-wma"},
        {".jpg", "image/jpeg"}, {".jpeg", "image/jpeg"}, {".png", "image/png"}, {".webp", "image/webp"},
        {".gif", "image/gif"}, {".bmp", "image/bmp"}, {".csv", "text/csv"}, {".txt", "text/plain"},
        {".lrc", "text/plain"}, {".m3u", "audio/x-mpegurl"}, {".m3u8", "audio/x-mpegurl"},
        {".json", "application/json"},
    };

    public static String mimeOf(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String[] m : MIME) {
            if (n.endsWith(m[0])) {
                return m[1];
            }
        }
        return "application/octet-stream";
    }

    private static File need(Uri u) throws FileNotFoundException {
        File f = u == null ? null : u.toFile();
        if (f == null) {
            throw new FileNotFoundException(String.valueOf(u));
        }
        return f;
    }

    public InputStream openInputStream(Uri u) throws FileNotFoundException {
        return new java.io.BufferedInputStream(new FileInputStream(need(u)), 65536);
    }

    public OutputStream openOutputStream(Uri u) throws FileNotFoundException {
        return openOutputStream(u, "w");
    }

    public OutputStream openOutputStream(Uri u, String mode) throws FileNotFoundException {
        File f = need(u);
        File p = f.getParentFile();
        if (p != null) {
            p.mkdirs();
        }
        return new FileOutputStream(f, mode != null && mode.contains("a"));
    }

    public ParcelFileDescriptor openFileDescriptor(Uri u, String mode) throws FileNotFoundException {
        try {
            return new ParcelFileDescriptor(need(u), mode != null && mode.contains("w") ? "rw" : "r");
        } catch (FileNotFoundException e) {
            throw e;
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    public String getType(Uri u) {
        File f = u == null ? null : u.toFile();
        if (f == null) {
            return null;
        }
        return f.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : mimeOf(f.getName());
    }

    private static Object column(String col, File f) {
        if (DocumentsContract.Document.COLUMN_DOCUMENT_ID.equals(col)) {
            return DocumentsContract.idOf(f);
        } else if ("_display_name".equals(col)) {
            return f.getName();
        } else if ("mime_type".equals(col)) {
            return f.isDirectory() ? DocumentsContract.Document.MIME_TYPE_DIR : mimeOf(f.getName());
        } else if ("_size".equals(col)) {
            return f.length();
        } else if ("last_modified".equals(col)) {
            return f.lastModified();
        } else if ("flags".equals(col)) {
            return 0;
        } else if ("_data".equals(col)) {
            return f.getAbsolutePath();
        }
        return null;
    }

    private static Object[] row(String[] proj, File f) {
        Object[] r = new Object[proj.length];
        for (int i = 0; i < proj.length; i++) {
            r[i] = column(proj[i], f);
        }
        return r;
    }

    public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        if (u == null) {
            return null;
        }
        if (proj == null) {
            proj = new String[] {"document_id", "_display_name", "mime_type", "_size", "last_modified"};
        }
        if (DocumentsContract.CHILDREN_SCHEME.equals(u.getScheme())) {
            String id = u.getPath();
            if (id != null && id.startsWith("/")) {
                id = id.substring(1);
            }
            File dir = DocumentsContract.fileOf(id == null ? "" : id);
            File[] kids = dir.listFiles();
            if (kids == null) {
                return null;
            }
            Arrays.sort(kids);
            MatrixCursor c = new MatrixCursor(proj);
            for (File k : kids) {
                if (!k.isHidden() || k.isFile()) {
                    c.addRow(row(proj, k));
                }
            }
            return c;
        }
        File f = u.toFile();
        if (f == null || !f.exists()) {
            return null;
        }
        MatrixCursor c = new MatrixCursor(proj);
        c.addRow(row(proj, f));
        return c;
    }

    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    public int update(Uri u, ContentValues v, String where, String[] args) {
        return 0;
    }

    public int delete(Uri u, String where, String[] args) {
        File f = u == null ? null : u.toFile();
        return f != null && f.delete() ? 1 : 0;
    }

    public void takePersistableUriPermission(Uri u, int flags) {
        SharedPreferences p = desk.Prefs.get("desk_perms");
        java.util.Set<String> s = p.getStringSet("uris", new java.util.HashSet<String>());
        s.add(u.toString());
        p.edit().putStringSet("uris", s).apply();
    }

    public void releasePersistableUriPermission(Uri u, int flags) {
        SharedPreferences p = desk.Prefs.get("desk_perms");
        java.util.Set<String> s = p.getStringSet("uris", new java.util.HashSet<String>());
        s.remove(u.toString());
        p.edit().putStringSet("uris", s).apply();
    }

    public List<UriPermission> getPersistedUriPermissions() {
        ArrayList<UriPermission> out = new ArrayList<UriPermission>();
        for (String s : desk.Prefs.get("desk_perms").getStringSet("uris", new java.util.HashSet<String>())) {
            out.add(new UriPermission(Uri.parse(s)));
        }
        return out;
    }
}
