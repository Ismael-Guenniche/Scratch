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
