package desk;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.awt.FileDialog;
import java.io.File;
import java.util.ArrayList;
import javax.swing.JFileChooser;

/** Intents "del sistema": elegir carpeta o archivo, guardar, abrir un enlace en el navegador. */
final class Pickers {
    private Pickers() {
    }

    private static File lastDir(String key) {
        String s = Prefs.get("desk").getString("dir_" + key, null);
        if (s != null) {
            File f = new File(s);
            if (f.isDirectory()) {
                return f;
            }
        }
        File music = new File(System.getProperty("user.home", "."), "Music");
        return music.isDirectory() ? music : new File(System.getProperty("user.home", "."));
    }

    private static void remember(String key, File dir) {
        if (dir != null) {
            Prefs.get("desk").edit().putString("dir_" + key, dir.getAbsolutePath()).apply();
        }
    }

    private static void result(final Activity caller, final int req, final Intent data) {
        if (caller == null || req < 0) {
            return;
        }
        Main.H.post(new Runnable() {
            @Override
            public void run() {
                caller.deliverResult(req, data == null ? Activity.RESULT_CANCELED : Activity.RESULT_OK, data);
            }
        });
    }

    private static String patterns(Intent i) {
        ArrayList<String> types = new ArrayList<String>();
        String[] extra = i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
        if (extra != null) {
            for (String t : extra) {
                types.add(t);
            }
        }
        if (i.getType() != null) {
            types.add(i.getType());
        }
        StringBuilder b = new StringBuilder();
        for (String t : types) {
            String add;
            if (t.startsWith("audio/") && !t.contains("mpegurl")) {
                add = "*.mp3;*.wav;*.flac;*.m4a;*.aac;*.ogg;*.opus;*.wma";
            } else if (t.startsWith("image/")) {
                add = "*.jpg;*.jpeg;*.png;*.webp;*.bmp;*.gif";
            } else if (t.contains("csv") || t.contains("comma-separated")) {
                add = "*.csv";
            } else if (t.contains("mpegurl")) {
                add = "*.m3u;*.m3u8";
            } else {
                return null;
            }
            if (b.indexOf(add) < 0) {
                if (b.length() > 0) {
                    b.append(';');
                }
                b.append(add);
            }
        }
        return b.length() == 0 ? null : b.toString();
    }

    static void handle(Shell shell, Activity caller, Intent i, int req) {
        String action = i.getAction();
        if (action == null) {
            return;
        }
        Peers.blur();
        // Pruebas automáticas: -Dscratch.test.pick=ruta responde a cualquier selector sin abrir diálogos.
        String auto = System.getProperty("scratch.test.pick");
        if (auto != null && !Intent.ACTION_VIEW.equals(action) && !Intent.ACTION_SEND.equals(action)) {
            Intent data = new Intent();
            data.setData(Uri.fromFile(new File(auto)));
            result(caller, req, data);
            return;
        }
        if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) {
            JFileChooser fc = new JFileChooser(lastDir("tree"));
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle(i.chooserTitle != null ? i.chooserTitle : "Elige la carpeta");
            Intent data = null;
            if (fc.showOpenDialog(shell.frame()) == JFileChooser.APPROVE_OPTION && fc.getSelectedFile() != null) {
                File dir = fc.getSelectedFile();
                if (dir.isDirectory()) {
                    remember("tree", dir.getParentFile() != null ? dir.getParentFile() : dir);
                    data = new Intent();
                    data.setData(Uri.fromFile(dir));
                }
            }
            result(caller, req, data);
        } else if (Intent.ACTION_OPEN_DOCUMENT.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)
                || Intent.ACTION_PICK.equals(action)) {
            FileDialog fd = new FileDialog(shell.frame(), i.chooserTitle != null ? i.chooserTitle : "Abrir",
                    FileDialog.LOAD);
            fd.setDirectory(lastDir("open").getAbsolutePath());
            String pat = patterns(i);
            if (pat != null) {
                fd.setFile(pat);
                final String[] exts = pat.replace("*", "").split(";");
                fd.setFilenameFilter(new java.io.FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        String n = name.toLowerCase(java.util.Locale.ROOT);
                        for (String e : exts) {
                            if (n.endsWith(e)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
            }
            fd.setMultipleMode(i.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false));
            fd.setVisible(true);
            File[] files = fd.getFiles();
            Intent data = null;
            if (files != null && files.length > 0) {
                remember("open", files[0].getParentFile());
                data = new Intent();
                data.setData(Uri.fromFile(files[0]));
                if (files.length > 1) {
                    ArrayList<Uri> us = new ArrayList<Uri>();
                    for (File f : files) {
                        us.add(Uri.fromFile(f));
                    }
                    data.setClipData(ClipData.newUris(us));
                }
            }
            result(caller, req, data);
        } else if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            FileDialog fd = new FileDialog(shell.frame(), "Guardar", FileDialog.SAVE);
            fd.setDirectory(lastDir("save").getAbsolutePath());
            String name = i.getStringExtra(Intent.EXTRA_TITLE);
            if (name != null) {
                fd.setFile(name);
            }
            fd.setVisible(true);
            Intent data = null;
            if (fd.getFile() != null) {
                File f = new File(fd.getDirectory(), fd.getFile());
                remember("save", f.getParentFile());
                data = new Intent();
                data.setData(Uri.fromFile(f));
            }
            result(caller, req, data);
        } else if (Intent.ACTION_VIEW.equals(action) && i.getData() != null) {
            Uri u = i.getData();
            try {
                File f = u.toFile();
                if (f != null) {
                    java.awt.Desktop.getDesktop().open(f);
                } else {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(u.toString()));
                }
            } catch (Exception e) {
                Rt.log(e);
                android.widget.Toast.makeText(Shell.application(), u.toString(),
                        android.widget.Toast.LENGTH_LONG).show();
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = i.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                new android.content.ClipboardManager().setText(text);
                android.widget.Toast.makeText(Shell.application(), "Copiado al portapapeles",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }
}
