package dev.scratchwheel;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** Diálogos compartidos para crear listas y añadir canciones a una lista. */
final class PlaylistUi {
    interface Name {
        void onName(String name);
    }

    private PlaylistUi() {
    }

    static void promptName(final Activity a, final Name cb) {
        Lucent th = Lucent.load(a);
        final DotDialog dd = new DotDialog(a, th).title(Tr.t("NUEVA LISTA"));
        final EditText input = dd.input(Tr.t("Nombre de la lista"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        dd.button(Tr.t("CANCELAR"), Lucent.GLASS, null);
        dd.button(Tr.t("CREAR"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                String n = Playlists.clean(input.getText().toString());
                if (n.length() == 0) {
                    return;
                }
                if (Playlists.I.find(n) != null) {
                    Toast.makeText(a, Tr.t("Ya existe una lista con ese nombre"), Toast.LENGTH_SHORT).show();
                    return;
                }
                cb.onName(n);
            }
        });
        dd.show();
        input.requestFocus();
    }

    interface IntCb {
        void on(int value);
    }

    /** Pide el nuevo nombre de una lista (con el actual ya escrito). */
    static void promptRename(final Activity a, final String old, final Name cb) {
        Lucent th = Lucent.load(a);
        final DotDialog dd = new DotDialog(a, th).title(Tr.t("RENOMBRAR LISTA"));
        final EditText input = dd.input(Tr.t("Nombre de la lista"));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(old);
        input.setSelection(old.length());
        dd.button(Tr.t("CANCELAR"), Lucent.GLASS, null);
        dd.button(Tr.t("GUARDAR"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                String n = Playlists.clean(input.getText().toString());
                if (n.length() == 0 || n.equals(old)) {
                    return;
                }
                String other = Playlists.I.find(n);
                if (other != null && !other.equalsIgnoreCase(old)) {
                    Toast.makeText(a, Tr.t("Ya existe una lista con ese nombre"), Toast.LENGTH_SHORT).show();
                    return;
                }
                cb.onName(n);
            }
        });
        dd.show();
        input.requestFocus();
    }

    /** Pide la posición (1 = la primera) a la que mover una canción de la lista. */
    static void promptPosition(final Activity a, int current, final int total, final IntCb cb) {
        Lucent th = Lucent.load(a);
        final DotDialog dd = new DotDialog(a, th).title(Tr.t("MOVER A LA POSICIÓN"));
        dd.message(Tr.t("Ahora está en la ") + current + Tr.t(" de ") + total + ".");
        final EditText input = dd.input("1 - " + total);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        dd.button(Tr.t("CANCELAR"), Lucent.GLASS, null);
        dd.button(Tr.t("MOVER"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                try {
                    int v = Integer.parseInt(input.getText().toString().trim());
                    cb.on(Math.max(1, Math.min(total, v)));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        dd.show();
        input.requestFocus();
    }

    /**
     * Edita el título y el artista de una canción. Solo se guardan los campos que cambian respecto a la
     * etiqueta del archivo; el archivo de audio no se toca.
     */
    static void editTrack(final Activity a, final Player.Track t, final Runnable done) {
        Lucent th = Lucent.load(a);
        final DotDialog dd = new DotDialog(a, th).title(Tr.t("EDITAR CANCIÓN"));
        final EditText ti = dd.input(Tr.t("Título"));
        ti.setText(t.display());
        final EditText ar = dd.input(Tr.t("Artista"));
        ar.setText(t.artist);
        dd.button(Tr.t("CANCELAR"), Lucent.GLASS, null);
        if (TrackEdits.I.has(t.uri.toString())) {
            dd.button(Tr.t("RESTAURAR ORIGINAL"), Lucent.GLASS, new Runnable() {
                @Override
                public void run() {
                    TrackEdits.I.clear(t.uri.toString());
                    applied(t, done);
                }
            });
        }
        dd.button(Tr.t("GUARDAR"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                String nt = TrackEdits.clean(ti.getText().toString());
                String na = TrackEdits.clean(ar.getText().toString());
                String origTitle = t.tagTitle.length() > 0 ? t.tagTitle : t.name;
                TrackEdits.I.set(t.uri.toString(), nt.equals(origTitle) ? "" : nt, na.equals(t.tagArtist) ? "" : na);
                applied(t, done);
            }
        });
        dd.show();
        ti.requestFocus();
    }

    private static void applied(Player.Track t, Runnable done) {
        Meta.reapply(t);
        Player.I.metaEdited(t);
        if (done != null) {
            done.run();
        }
    }

    /** Crea una lista nueva y añade estas canciones. */
    static void createWith(final Activity a, final List<String> uris, final Runnable done) {
        promptName(a, new Name() {
            @Override
            public void onName(String name) {
                if (Playlists.I.create(name)) {
                    Playlists.I.add(name, uris);
                    Toast.makeText(a, Tr.t("Lista \"") + name + Tr.t("\" creada"), Toast.LENGTH_SHORT).show();
                    if (done != null) {
                        done.run();
                    }
                }
            }
        });
    }

    /** Menú "Añadir a lista": las listas existentes y la opción de crear una nueva. */
    static void pick(final Activity a, Lucent th, View anchor, final List<String> uris, final Runnable done) {
        final List<String> names = Playlists.I.names();
        String[] labels = new String[names.size() + 1];
        for (int i = 0; i < names.size(); i++) {
            labels[i] = names.get(i).toUpperCase(java.util.Locale.ROOT);
        }
        labels[names.size()] = Tr.t("+ NUEVA LISTA");
        DotMenu.show(a, th, anchor, labels, new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                if (id < names.size()) {
                    int n = Playlists.I.add(names.get(id), new ArrayList<String>(uris));
                    Toast.makeText(a, n > 0 ? Tr.t("Añadida a ") + names.get(id)
                            : Tr.t("Ya estaba en ") + names.get(id), Toast.LENGTH_SHORT).show();
                    if (done != null) {
                        done.run();
                    }
                } else {
                    createWith(a, uris, done);
                }
            }
        });
    }
}
