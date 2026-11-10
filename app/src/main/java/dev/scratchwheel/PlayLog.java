package dev.scratchwheel;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** Historial de escuchas en history.txt (una línea por sesión: ms \t uri \t segundos). */
final class PlayLog {
    private static final List<Stats.Entry> entries = new ArrayList<Stats.Entry>();
    private static File file;

    private PlayLog() {
    }

    static synchronized void init(Context c) {
        file = new File(c.getFilesDir(), "history.txt");
        entries.clear();
        if (!file.exists()) {
            return;
        }
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            try {
                String l;
                while ((l = r.readLine()) != null) {
                    String[] f = l.split("\t", -1);
                    if (f.length >= 3) {
                        try {
                            entries.add(new Stats.Entry(Long.parseLong(f[0]), f[1], Integer.parseInt(f[2]),
                                    f.length >= 4 && "1".equals(f[3])));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } finally {
                r.close();
            }
        } catch (IOException ignored) {
        }
    }

    static synchronized void add(long ts, String uri, int secs, boolean played) {
        Stats.Entry e = new Stats.Entry(ts, uri, secs, played);
        entries.add(e);
        if (file == null) {
            return;
        }
        try {
            Writer w = new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8");
            try {
                w.write(ts + "\t" + uri + "\t" + secs + "\t" + (played ? 1 : 0) + "\n");
            } finally {
                w.close();
            }
        } catch (IOException ignored) {
        }
    }

    static synchronized List<Stats.Entry> all() {
        return new ArrayList<Stats.Entry>(entries);
    }
}
