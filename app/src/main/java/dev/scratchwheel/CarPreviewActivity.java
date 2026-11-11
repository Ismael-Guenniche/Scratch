package dev.scratchwheel;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaController;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vista previa de Android Auto: se conecta a PlayerService igual que lo haría el coche (MediaBrowser +
 * MediaController), así que prueba de verdad el árbol de la biblioteca, la búsqueda, la cola y los controles.
 * Pantalla sencilla: textos separados, filas y botones grandes. Sirve también como modo coche en el móvil.
 */
public class CarPreviewActivity extends Activity {
    private Lucent th;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private MediaBrowser browser;
    private MediaController ctl;
    private final ArrayList<String[]> stack = new ArrayList<String[]>();
    private final ArrayList<MediaBrowser.MediaItem> items = new ArrayList<MediaBrowser.MediaItem>();
    private String subscribed;

    private TextView crumb;
    private TextView status;
    private ListView list;
    private ItemAdapter adapter;
    private EditText search;
    private TextView nowTitle;
    private TextView nowArtist;
    private TextView nowTime;
    private TextView queueInfo;
    private ProgressBar progress;
    private TextView playBtn;
    private LinearLayout extraRow;

    private int dp(float v) {
        return th.dp(v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        setContentView(buildUi());
        browser = new MediaBrowser(this, new ComponentName(this, PlayerService.class),
                new MediaBrowser.ConnectionCallback() {
                    @Override
                    public void onConnected() {
                        status.setText("");
                        ctl = new MediaController(CarPreviewActivity.this, browser.getSessionToken());
                        ctl.registerCallback(new MediaController.Callback() {
                            @Override
                            public void onPlaybackStateChanged(PlaybackState s) {
                                refreshNow();
                            }

                            @Override
                            public void onMetadataChanged(MediaMetadata m) {
                                refreshNow();
                            }

                            @Override
                            public void onQueueChanged(List<android.media.session.MediaSession.QueueItem> q) {
                                refreshNow();
                            }
                        });
                        stack.clear();
                        open(browser.getRoot(), Tr.t("INICIO"));
                        refreshNow();
                    }

                    @Override
                    public void onConnectionFailed() {
                        status.setText(Tr.t("No se pudo conectar con el servicio de música"));
                    }

                    @Override
                    public void onConnectionSuspended() {
                        status.setText(Tr.t("Conexión perdida"));
                    }
                }, null);
        browser.connect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (browser != null) {
                browser.disconnect();
            }
        } catch (RuntimeException ignored) {
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            ui.postDelayed(this, 500);
        }
    };

    // ------------------------------------------------------------------ interfaz

    private TextView label(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(th.regular);
        return t;
    }

    private TextView bigButton(String s, final Runnable r, boolean accent) {
        TextView b = new TextView(this);
        b.setText(s);
        b.setTextSize(20);
        b.setTypeface(th.semi);
        b.setGravity(Gravity.CENTER);
        b.setTextColor(accent ? th.onAccent : th.ink);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(accent ? th.accent : th.glass);
        if (!accent) {
            g.setStroke(dp(1), th.glassLine);
        }
        b.setBackground(g);
        b.setMinHeight(dp(72));
        b.setClickable(true);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        return b;
    }

    private LinearLayout.LayoutParams weight(float w, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
        lp.setMargins(dp(margin), dp(margin), dp(margin), dp(margin));
        return lp;
    }

    private View buildUi() {
        final boolean land = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(16), dp(12), dp(16), dp(16));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(bigButton("‹  " + Tr.t("ATRÁS"), new Runnable() {
            @Override
            public void run() {
                goBack();
            }
        }, false), new LinearLayout.LayoutParams(dp(140), dp(72)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(16), 0, 0, 0);
        TextView head = label("ANDROID AUTO · " + Tr.t("VISTA PREVIA"), 12, th.accent);
        head.setTypeface(th.mono);
        head.setLetterSpacing(0.1f);
        titles.addView(head);
        crumb = label("", 18, th.ink);
        crumb.setSingleLine(true);
        crumb.setEllipsize(TextUtils.TruncateAt.END);
        crumb.setPadding(0, dp(4), 0, 0);
        titles.addView(crumb);
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(bigButton(Tr.t("SALIR"), new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, false), new LinearLayout.LayoutParams(dp(120), dp(72)));
        root.addView(top);

        status = label("", 13, th.ink2);
        status.setPadding(dp(4), dp(8), dp(4), dp(8));
        root.addView(status);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // columna del explorador
        LinearLayout browse = new LinearLayout(this);
        browse.setOrientation(LinearLayout.VERTICAL);
        LinearLayout sr = new LinearLayout(this);
        sr.setOrientation(LinearLayout.HORIZONTAL);
        sr.setGravity(Gravity.CENTER_VERTICAL);
        search = new EditText(this);
        search.setHint(Tr.t("Buscar canción o artista"));
        search.setHintTextColor(th.ink2);
        search.setTextColor(th.ink);
        search.setTextSize(18);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable sg = new GradientDrawable();
        sg.setCornerRadius(dp(18));
        sg.setColor(th.glass);
        sg.setStroke(dp(1), th.glassLine);
        search.setBackground(sg);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, android.view.KeyEvent e) {
                doSearch();
                return true;
            }
        });
        sr.addView(search, new LinearLayout.LayoutParams(0, dp(72), 1f));
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(dp(130), dp(72));
        sb.leftMargin = dp(10);
        sr.addView(bigButton(Tr.t("BUSCAR"), new Runnable() {
            @Override
            public void run() {
                doSearch();
            }
        }, false), sb);
        browse.addView(sr);

        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        adapter = new ItemAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                pick(items.get(pos));
            }
        });
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        ll.topMargin = dp(10);
        browse.addView(list, ll);
        LinearLayout.LayoutParams bl = land
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        body.addView(browse, bl);

        // reproduciendo
        LinearLayout now = new LinearLayout(this);
        now.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ng = new GradientDrawable();
        ng.setCornerRadius(dp(24));
        ng.setColor(th.glass);
        ng.setStroke(dp(1), th.glassLine);
        now.setBackground(ng);
        now.setPadding(dp(20), dp(18), dp(20), dp(18));
        TextView nlabel = label(Tr.t("REPRODUCIENDO"), 12, th.ink2);
        nlabel.setTypeface(th.mono);
        nlabel.setLetterSpacing(0.1f);
        now.addView(nlabel);
        nowTitle = label("—", 24, th.ink);
        nowTitle.setTypeface(th.semi);
        nowTitle.setMaxLines(2);
        nowTitle.setEllipsize(TextUtils.TruncateAt.END);
        nowTitle.setPadding(0, dp(10), 0, 0);
        now.addView(nowTitle);
        nowArtist = label("", 16, th.ink2);
        nowArtist.setSingleLine(true);
        nowArtist.setEllipsize(TextUtils.TruncateAt.END);
        nowArtist.setPadding(0, dp(8), 0, 0);
        now.addView(nowArtist);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1000);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        pl.topMargin = dp(18);
        now.addView(progress, pl);
        nowTime = label("00:00 / 00:00", 13, th.ink2);
        nowTime.setTypeface(th.mono);
        nowTime.setPadding(0, dp(8), 0, 0);
        now.addView(nowTime);

        LinearLayout tr = new LinearLayout(this);
        tr.setOrientation(LinearLayout.HORIZONTAL);
        tr.setGravity(Gravity.CENTER);
        tr.setPadding(0, dp(14), 0, 0);
        tr.addView(bigButton("⏮", new Runnable() {
            @Override
            public void run() {
                if (ctl != null) {
                    ctl.getTransportControls().skipToPrevious();
                }
            }
        }, false), weight(1f, 4));
        playBtn = bigButton("▶", new Runnable() {
            @Override
            public void run() {
                if (ctl == null) {
                    return;
                }
                PlaybackState s = ctl.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                    ctl.getTransportControls().pause();
                } else {
                    ctl.getTransportControls().play();
                }
            }
        }, true);
        tr.addView(playBtn, weight(1.4f, 4));
        tr.addView(bigButton("⏭", new Runnable() {
            @Override
            public void run() {
                if (ctl != null) {
                    ctl.getTransportControls().skipToNext();
                }
            }
        }, false), weight(1f, 4));
        now.addView(tr);

        extraRow = new LinearLayout(this);
        extraRow.setOrientation(LinearLayout.HORIZONTAL);
        extraRow.setGravity(Gravity.CENTER);
        now.addView(extraRow);
        queueInfo = label("", 13, th.ink2);
        queueInfo.setPadding(0, dp(10), 0, 0);
        now.addView(queueInfo);

        LinearLayout.LayoutParams nl = land
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        if (land) {
            nl.leftMargin = dp(14);
        } else {
            nl.topMargin = dp(10);
        }
        body.addView(now, nl);
        root.addView(body, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    // ------------------------------------------------------------------ explorador

    private void open(String id, String title) {
        stack.add(new String[] {id, title});
        load(id);
    }

    private void load(String id) {
        if (subscribed != null) {
            try {
                browser.unsubscribe(subscribed);
            } catch (RuntimeException ignored) {
            }
        }
        subscribed = id;
        StringBuilder sb = new StringBuilder();
        for (String[] s : stack) {
            if (sb.length() > 0) {
                sb.append("  ›  ");
            }
            sb.append(s[1]);
        }
        crumb.setText(sb.toString());
        items.clear();
        adapter.notifyDataSetChanged();
        browser.subscribe(id, new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parent, List<MediaBrowser.MediaItem> children) {
                if (!parent.equals(subscribed)) {
                    return;
                }
                items.clear();
                items.addAll(children);
                adapter.notifyDataSetChanged();
                status.setText(children.isEmpty() ? Tr.t("Aquí no hay nada") : "");
            }

            @Override
            public void onError(String parent) {
                status.setText(Tr.t("El servicio no devolvió esta carpeta"));
            }
        });
    }

    private void goBack() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            load(stack.get(stack.size() - 1)[0]);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    private void pick(MediaBrowser.MediaItem it) {
        if (it.isBrowsable()) {
            open(it.getMediaId(), String.valueOf(it.getDescription().getTitle()));
        } else if (ctl != null) {
            ctl.getTransportControls().playFromMediaId(it.getMediaId(), null);
        }
    }

    private void doSearch() {
        String q = search.getText().toString().trim();
        if (q.length() == 0 || browser == null || !browser.isConnected()) {
            return;
        }
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        stack.add(new String[] {"SEARCH", Tr.t("Resultados: ") + q});
        if (subscribed != null) {
            try {
                browser.unsubscribe(subscribed);
            } catch (RuntimeException ignored) {
            }
            subscribed = "SEARCH";
        }
        crumb.setText(stack.get(0)[1] + "  ›  " + Tr.t("Resultados: ") + q);
        browser.search(q, null, new MediaBrowser.SearchCallback() {
            @Override
            public void onSearchResult(String query, Bundle extras, List<MediaBrowser.MediaItem> found) {
                items.clear();
                items.addAll(found);
                adapter.notifyDataSetChanged();
                status.setText(found.isEmpty() ? Tr.t("Sin resultados") : "");
            }

            @Override
            public void onError(String query, Bundle extras) {
                status.setText(Tr.t("La búsqueda falló"));
            }
        });
    }

    private final class ItemAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int i) {
            return items.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View old, ViewGroup parent) {
            MediaBrowser.MediaItem it = items.get(i);
            LinearLayout row = new LinearLayout(CarPreviewActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(76));
            row.setPadding(dp(18), dp(10), dp(18), dp(10));
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(16));
            g.setColor(Lucent.withAlpha(th.ink, 0.06f));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            row.setBackground(g);
            LinearLayout col = new LinearLayout(CarPreviewActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = label(String.valueOf(it.getDescription().getTitle()), 19, th.ink);
            t.setTypeface(th.semi);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(t);
            CharSequence sub = it.getDescription().getSubtitle();
            if (sub != null && sub.length() > 0) {
                TextView s = label(sub.toString(), 14, th.ink2);
                s.setSingleLine(true);
                s.setEllipsize(TextUtils.TruncateAt.END);
                s.setPadding(0, dp(3), 0, 0);
                col.addView(s);
            }
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView mark = label(it.isBrowsable() ? "›" : "▶", 22, it.isBrowsable() ? th.ink2 : th.accent);
            mark.setPadding(dp(12), 0, 0, 0);
            row.addView(mark);
            // separación entre filas dentro del propio elemento
            LinearLayout wrap = new LinearLayout(CarPreviewActivity.this);
            wrap.setPadding(0, dp(5), 0, dp(5));
            wrap.addView(row, rp);
            return wrap;
        }
    }

    // ------------------------------------------------------------------ reproduciendo

    private void refreshNow() {
        if (ctl == null) {
            return;
        }
        MediaMetadata m = ctl.getMetadata();
        nowTitle.setText(m == null || m.getString(MediaMetadata.METADATA_KEY_TITLE) == null ? "—"
                : m.getString(MediaMetadata.METADATA_KEY_TITLE));
        String ar = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
        nowArtist.setText(ar == null ? "" : ar);
        PlaybackState s = ctl.getPlaybackState();
        boolean playing = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        playBtn.setText(playing ? "⏸" : "▶");
        extraRow.removeAllViews();
        if (s != null) {
            for (final PlaybackState.CustomAction ca : s.getCustomActions()) {
                extraRow.addView(bigButton(String.valueOf(ca.getName()), new Runnable() {
                    @Override
                    public void run() {
                        ctl.getTransportControls().sendCustomAction(ca.getAction(), null);
                    }
                }, false), weight(1f, 4));
            }
        }
        List<android.media.session.MediaSession.QueueItem> q = ctl.getQueue();
        queueInfo.setText(q == null || q.isEmpty() ? Tr.t("Cola vacía")
                : Tr.t("En cola: ") + q.size() + "  ·  " + Tr.t("siguiente: ")
                        + q.get(0).getDescription().getTitle());
        updateProgress();
    }

    private void updateProgress() {
        if (ctl == null) {
            return;
        }
        PlaybackState s = ctl.getPlaybackState();
        MediaMetadata m = ctl.getMetadata();
        long dur = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = s == null ? 0 : s.getPosition();
        if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
            pos += (long) ((android.os.SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime())
                    * s.getPlaybackSpeed());
        }
        pos = Math.max(0, Math.min(dur > 0 ? dur : pos, pos));
        progress.setProgress(dur > 0 ? (int) (pos * 1000 / dur) : 0);
        nowTime.setText(String.format(Locale.US, "%02d:%02d / %02d:%02d", pos / 60000, pos / 1000 % 60,
                dur / 60000, dur / 1000 % 60));
    }
}
