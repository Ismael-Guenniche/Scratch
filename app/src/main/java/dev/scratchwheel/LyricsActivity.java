package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Letra de la canción: sincronizada (línea activa resaltada, tocar para saltar) o sin tiempos. */
public class LyricsActivity extends Activity {
    private static final int REQ_LRC = 31;
    private Lucent th;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout box;
    private ScrollView scroll;
    private TextView status;
    private TextView titleView;
    private final ArrayList<TextView> lineViews = new ArrayList<TextView>();
    private List<LyricsReader.Line> synced = new ArrayList<LyricsReader.Line>();
    private String loadedKey = "";
    private int active = -2;
    private boolean userScroll;
    private long lastTouch;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onChanged() {
            reloadIfNeeded();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            highlight();
            ui.postDelayed(this, 120);
        }
    };

    private int dp(float v) {
        return th.dp(v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
            return;
        }
        Player.I.addListener(listener);
        loadedKey = "";
        reloadIfNeeded();
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Player.I.removeListener(listener);
        ui.removeCallbacks(ticker);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        DotIconView back = new DotIconView(this, th);
        back.set(DotIcons.DOWN, th.ink);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView t = new TextView(this);
        t.setText(Tr.t("LETRAS"));
        t.setTextSize(11);
        t.setTextColor(th.accent);
        t.setTypeface(th.mono);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(8), 0, 0, 0);
        header.addView(t, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final DotIconView menu = new DotIconView(this, th);
        menu.set(DotIcons.MENU, th.ink);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(menu);
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        titleView = new TextView(this);
        titleView.setTextSize(15);
        titleView.setTextColor(th.ink);
        titleView.setTypeface(th.semi);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setPadding(dp(6), dp(4), dp(6), dp(8));
        root.addView(titleView);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                lastTouch = System.currentTimeMillis();
                return false;
            }
        });
        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(8), dp(40), dp(8), dp(200));
        scroll.addView(box, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        status = new TextView(this);
        status.setTextSize(11);
        status.setTextColor(th.ink2);
        status.setTypeface(th.mono);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(4), 0, 0);
        root.addView(status);
        return root;
    }

    private void showMenu(View anchor) {
        final boolean own = LyricsStore.hasOwn(this, Player.I.currentUri());
        String[] labels = own
                ? new String[] {Tr.t("BUSCAR EN LRCLIB"), Tr.t("IMPORTAR .LRC"), Tr.t("BORRAR LA GUARDADA")}
                : new String[] {Tr.t("BUSCAR EN LRCLIB"), Tr.t("IMPORTAR .LRC")};
        DotMenu.show(this, th, anchor, labels, new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                if (id == 0) {
                    fetchOnline();
                } else if (id == 1) {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_LRC);
                } else {
                    LyricsStore.forget(LyricsActivity.this, Player.I.currentUri());
                    loadedKey = "";
                    reloadIfNeeded();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LRC && res == RESULT_OK && data != null && data.getData() != null) {
            try {
                LyricsStore.importFrom(this, data.getData(), Player.I.currentUri());
                loadedKey = "";
                reloadIfNeeded();
            } catch (Exception e) {
                Toast.makeText(this, Tr.t("No se pudo leer el archivo"), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void fetchOnline() {
        final Player p = Player.I;
        if (p.index() < 0 || p.index() >= p.tracks.size()) {
            return;
        }
        final Player.Track tr = p.tracks.get(p.index());
        final Uri uri = p.currentUri();
        final String title = tr.display();
        final String artist = tr.artist;
        final String album = tr.album;
        final int dur = (int) p.engine.getDurationSeconds();
        if (artist.length() == 0) {
            Toast.makeText(this, Tr.t("La canción no tiene artista en sus etiquetas"), Toast.LENGTH_LONG).show();
            return;
        }
        status.setText(Tr.t("BUSCANDO EN LRCLIB…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                String found = null;
                try {
                    found = LyricsStore.fetchOnline(title, artist, album, dur);
                    if (found != null) {
                        LyricsStore.save(LyricsActivity.this, uri, found);
                    }
                } catch (Exception ignored) {
                }
                final boolean ok = found != null;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!ok) {
                            Toast.makeText(LyricsActivity.this, Tr.t("No se encontró la letra"),
                                    Toast.LENGTH_LONG).show();
                        }
                        loadedKey = "";
                        reloadIfNeeded();
                    }
                });
            }
        }, "lrclib").start();
    }

    private void reloadIfNeeded() {
        final Uri uri = Player.I.currentUri();
        final String key = uri == null ? "" : uri.toString();
        if (key.equals(loadedKey)) {
            return;
        }
        loadedKey = key;
        titleView.setText(Player.I.title().toUpperCase(Locale.ROOT));
        status.setText(Tr.t("CARGANDO…"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                final LyricsReader.Result r = LyricsStore.load(getApplicationContext(), uri);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(loadedKey)) {
                            show(r);
                        }
                    }
                });
            }
        }, "lyrics").start();
    }

    private void show(LyricsReader.Result r) {
        box.removeAllViews();
        lineViews.clear();
        synced = r.synced;
        active = -2;
        if (r.isEmpty()) {
            TextView none = new TextView(this);
            none.setText(Tr.t("Esta canción no tiene letra en sus etiquetas.\n\nPuedes buscarla en lrclib.net o importar un archivo .lrc desde el menú ⋮."));
            none.setTextSize(14);
            none.setTextColor(th.ink2);
            none.setTypeface(th.regular);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(16), dp(40), dp(16), 0);
            box.addView(none);
            status.setText("");
            return;
        }
        if (!synced.isEmpty()) {
            for (int i = 0; i < synced.size(); i++) {
                final int idx = i;
                LyricsReader.Line l = synced.get(i);
                TextView tv = new TextView(this);
                tv.setText(l.text.length() == 0 ? "♪" : l.text);
                tv.setTextSize(22);
                tv.setTypeface(th.semi);
                tv.setTextColor(th.ink2);
                tv.setAlpha(0.55f);
                tv.setPadding(dp(8), dp(9), dp(8), dp(9));
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Player.I.engine.seekTo(synced.get(idx).ms / 1000.0);
                        lastTouch = 0;
                    }
                });
                box.addView(tv);
                lineViews.add(tv);
            }
            status.setText(Tr.t("SINCRONIZADA · TOCA UNA LÍNEA PARA SALTAR"));
        } else {
            TextView tv = new TextView(this);
            tv.setText(r.plain);
            tv.setTextSize(17);
            tv.setLineSpacing(0, 1.25f);
            tv.setTypeface(th.regular);
            tv.setTextColor(th.ink);
            tv.setPadding(dp(8), 0, dp(8), 0);
            tv.setTextIsSelectable(true);
            box.addView(tv);
            status.setText(Tr.t("SIN TIEMPOS"));
        }
        highlight();
    }

    private void highlight() {
        if (synced.isEmpty() || lineViews.size() != synced.size()) {
            return;
        }
        long ms = (long) (Player.I.engine.getHeardSeconds() * 1000);
        int a = LyricsReader.lineAt(synced, ms);
        if (a == active) {
            return;
        }
        if (active >= 0 && active < lineViews.size()) {
            style(lineViews.get(active), false);
        }
        active = a;
        if (a >= 0) {
            final TextView v = lineViews.get(a);
            style(v, true);
            if (System.currentTimeMillis() - lastTouch > 2500) {
                scroll.post(new Runnable() {
                    @Override
                    public void run() {
                        int target = v.getTop() + v.getHeight() / 2 - scroll.getHeight() / 3;
                        scroll.smoothScrollTo(0, Math.max(0, target));
                    }
                });
            }
        }
    }

    private void style(TextView v, boolean on) {
        v.setTextColor(on ? th.ink : th.ink2);
        v.setAlpha(on ? 1f : 0.55f);
        v.setTextSize(on ? 26 : 22);
    }
}
