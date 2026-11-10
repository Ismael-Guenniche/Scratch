package dev.scratchwheel;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Escaneo recursivo de una carpeta (SAF) y caché de la lista para arrancar al instante. */
final class Library {
    interface Progress {
        void onCount(int n);
    }

    private static final String[] EXT = {".mp3", ".wav", ".m4a", ".aac", ".ogg", ".opus", ".flac", ".wma"};
    private static final String CACHE = "library.txt";

    private Library() {
    }

    static String labelFor(Uri tree) {
        try {
            String id = DocumentsContract.getTreeDocumentId(tree);
            return id.replace(':', '/');
        } catch (RuntimeException e) {
            return "CARPETA";
        }
    }

    static List<Player.Track> scan(Context c, Uri tree, Progress p) {
        ArrayList<Player.Track> out = new ArrayList<Player.Track>();
        try {
            String root = DocumentsContract.getTreeDocumentId(tree);
            walk(c.getContentResolver(), tree, root, out, p, 0);
        } catch (RuntimeException ignored) {
        }
        Collections.sort(out, new Comparator<Player.Track>() {
            @Override
            public int compare(Player.Track a, Player.Track b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        return out;
    }

    private static void walk(ContentResolver cr, Uri tree, String docId, List<Player.Track> out,
                             Progress p, int depth) {
        if (depth > 12) {
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        ArrayList<String> dirs = new ArrayList<String>();
        Cursor cur = null;
        try {
            cur = cr.query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (cur == null) {
                return;
            }
            while (cur.moveToNext()) {
                String id = cur.getString(0);
                String name = cur.getString(1);
                String mime = cur.getString(2);
                if (id == null) {
                    continue;
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    dirs.add(id);
                } else if (isAudio(name, mime)) {
                    String shown = name == null ? id : stripExt(name);
                    out.add(new Player.Track(DocumentsContract.buildDocumentUriUsingTree(tree, id), shown));
                    p.onCount(out.size());
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        for (String d : dirs) {
            walk(cr, tree, d, out, p, depth + 1);
        }
    }

    private static boolean isAudio(String name, String mime) {
        if (mime != null && mime.startsWith("audio/")) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (String e : EXT) {
            if (n.endsWith(e)) {
                return true;
            }
        }
        return false;
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && name.length() - dot <= 6 ? name.substring(0, dot) : name;
    }

    static void save(Context c, String label, List<Player.Track> tracks) {
        BufferedWriter w = null;
        try {
            w = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(new File(c.getFilesDir(), CACHE)), "UTF-8"));
            w.write(label.replace('\n', ' '));
            w.newLine();
            for (Player.Track t : tracks) {
                w.write(t.uri.toString());
                w.write('\t');
                w.write(t.name.replace('\t', ' ').replace('\n', ' '));
                w.newLine();
            }
        } catch (IOException ignored) {
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Devuelve la etiqueta de la biblioteca guardada, o null si no hay. */
    static String load(Context c, List<Player.Track> into) {
        File f = new File(c.getFilesDir(), CACHE);
        if (!f.exists()) {
            return null;
        }
        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String label = r.readLine();
            String line;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    into.add(new Player.Track(Uri.parse(line.substring(0, tab)), line.substring(tab + 1)));
                }
            }
            return label == null ? "" : label;
        } catch (IOException e) {
            into.clear();
            return null;
        } finally {
            if (r != null) {
                try {
                    r.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
