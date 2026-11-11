package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Pantalla de inicio: todas las canciones, listas (una por carpeta) y favoritas, con una barra flotante
 * de la canción actual. Tocar la barra abre la vista de scratch.
 */
public class MainActivity extends Activity {
    private static final int REQ_TREE = 2;
    private static final int REQ_OPEN = 3;
    private static final int REQ_CSV = 4;
    private static final int REQ_WEB = 5;
    private static final int REQ_COVER = 6;
    private static final int REQ_LISTFOLDER = 7;
    private static final int TAB_ALL = 0;
    private static final int TAB_FAVS = 1;
    private static final int TAB_FOLDER = 2;
    private static final int TAB_LIST = 3;
    private static final int TAB_RECENT = 4;
    private static final int TAB_TOP = 5;
    private static final int TAB_ARTIST = 6;
    private static final int TAB_HOME = 7;
    private String artistSel = "";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Integer> shown = new ArrayList<Integer>();
    private Lucent th;
    private TextView status;
    private EditText search;
    private BaseAdapter adapter;
    private android.widget.ImageView barCover;
    private String barCoverKey = "";
    private LinearLayout listHeader;
    private DotIconView listShuffle;
    private android.widget.ImageView listCover;
    private TextView listName;
    private TextView listCount;
    private String headerKey = "";
    private String coverTarget = "";
    private String folderTarget = "";
    /** Orden de la lista abierta: 0 el de la lista, 1 título, 2 artista, 3 álbum, 4 duración. */
    private int sortMode;
    private boolean sortDesc;
    private String sortFor = "";
    private LinearLayout chips;
    private LinearLayout empty;
    private ListView list;
    private TextView barTitle;
    private TextView barTime;
    private DotIconView barPlay;
    private LineView barLine;

    private int tab = TAB_HOME;
    private HomeView home;
    private int metaStamp;
    private String folderSel = "";
    private String listSel = "";
    private java.util.HashMap<String, Integer> uriIndex = new java.util.HashMap<String, Integer>();
    private int listVersion = -1;
    private DotIconView searchBtn;
    private boolean searchOpen;
    private String[] folderOf = new String[0];
    private int folderStamp = -1;
    private String chipKey = "";
    private int lastSec = -1;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onChanged() {
            refilter();
            refreshBar();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tickBar();
            ui.postDelayed(this, Player.I.isPlayingOrPending() ? 120 : 400);
        }
    };

    /** Línea fina de progreso de la barra flotante. */
    private static final class LineView extends View {
        private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float frac;

        LineView(android.content.Context c, int backColor, int fillColor) {
            super(c);
            back.setColor(backColor);
            fill.setColor(fillColor);
        }

        void set(float f) {
            if (f != frac) {
                frac = f;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = getHeight() / 2f;
            c.drawRoundRect(0, 0, getWidth(), getHeight(), r, r, back);
            if (frac > 0) {
                c.drawRoundRect(0, 0, getWidth() * Math.min(1f, frac), getHeight(), r, r, fill);
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (OnboardingActivity.needed(this)) {
            // primer inicio (o política nueva): bienvenida antes de nada
            startActivity(new Intent(this, OnboardingActivity.class));
            finish();
            return;
        }
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        setContentView(buildUi());
        handleSpotifyIntent(getIntent());
        backCallback();
        if (Lucent.prefs(this).getBoolean("pick_pending", false)) {
            Lucent.prefs(this).edit().putBoolean("pick_pending", false).apply();
            pickFolder();
        }
    }

    /** Con targetSdk 36 el sistema ya no llama a onBackPressed: se registra el gesto atrás a mano. */
    private void backCallback() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    new android.window.OnBackInvokedCallback() {
                        @Override
                        public void onBackInvoked() {
                            onBackPressed();
                        }
                    });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSpotifyIntent(intent);
    }

    // ------------------------------------------------------- sincronizar Spotify

    /** Elige cómo traer las listas: con tu cuenta (API oficial) o desde un archivo CSV/M3U. */
    private void chooseImportMethod() {
        new DotDialog(this, th).title(Tr.t("IMPORTAR DE SPOTIFY"))
                .message(Tr.t("Elige cómo traer tus listas. Después se emparejan con tus archivos."))
                .options(new String[] {Tr.t("CON MI CUENTA · API OFICIAL (PREMIUM)"),
                        Tr.t("SIN PREMIUM · SESIÓN WEB (EXPERIMENTAL)"),
                        Tr.t("DESDE ARCHIVO · CSV O M3U")}, new DotMenu.Picked() {
                    @Override
                    public void onPick(int w) {
                        if (w == 0) {
                            SpotifyApi.setWebToken(null);
                            spotifySync();
                        } else if (w == 1) {
                            startActivityForResult(new Intent(MainActivity.this, SpotifyWebActivity.class), REQ_WEB);
                        } else {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("*/*");
                            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                            startActivityForResult(i, REQ_CSV);
                        }
                    }
                }).button(Tr.t("CANCELAR"), Lucent.GLASS, null).show();
    }

    private void handleSpotifyIntent(Intent in) {
        final Uri d = in == null ? null : in.getData();
        if (d == null || !"scratchwheel".equals(d.getScheme())) {
            return;
        }
        in.setData(null);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    SpotifyApi.finishLogin(MainActivity.this, d);
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                final String fe = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fe != null) {
                            Toast.makeText(MainActivity.this, fe, Toast.LENGTH_LONG).show();
                        } else {
                            spotifySync();
                        }
                    }
                });
            }
        }, "spotify-login").start();
    }

    private void spotifySync() {
        if (SpotifyApi.clientId(this).length() == 0) {
            final DotDialog dd = new DotDialog(this, th).title("SPOTIFY · CLIENT ID")
                    .message("1) developer.spotify.com/dashboard → Create app.\n"
                            + "2) Redirect URI: " + SpotifyApi.REDIRECT + "\n"
                            + Tr.t("3) En User Management añade tu email.\n")
                            + Tr.t("4) Pega aquí el Client ID."));
            final EditText et = dd.input("Client ID");
            dd.button(Tr.t("CANCELAR"), Lucent.GLASS, null);
            dd.button(Tr.t("GUARDAR"), Lucent.ACCENT, new Runnable() {
                @Override
                public void run() {
                    if (et.getText().toString().trim().length() > 0) {
                        SpotifyApi.setClientId(MainActivity.this, et.getText().toString());
                        spotifySync();
                    }
                }
            });
            dd.show();
            return;
        }
        if (!SpotifyApi.loggedIn(this)) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SpotifyApi.loginUrl(this))));
            } catch (Exception e) {
                Toast.makeText(this, Tr.t("No se pudo abrir el navegador"), Toast.LENGTH_LONG).show();
            }
            return;
        }
        fetchPlaylistsDialog();
    }

    private void fetchPlaylistsDialog() {
        Toast.makeText(this, Tr.t("Leyendo tus listas…"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                java.util.List<SpotifyApi.Pl> pls = null;
                String err = null;
                try {
                    pls = SpotifyApi.playlists(MainActivity.this);
                } catch (Exception e) {
                    err = String.valueOf(e.getMessage());
                }
                final java.util.List<SpotifyApi.Pl> fp = pls;
                final String fe = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (fe != null) {
                            Toast.makeText(MainActivity.this, fe, Toast.LENGTH_LONG).show();
                        } else {
                            choosePlaylists(fp);
                        }
                    }
                });
            }
        }, "spotify-lists").start();
    }

    private void choosePlaylists(final java.util.List<SpotifyApi.Pl> pls) {
        if (pls.isEmpty()) {
            Toast.makeText(this, Tr.t("No hay listas"), Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[pls.size()];
        final boolean[] on = new boolean[pls.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = pls.get(i).name;
        }
        new DotDialog(this, th).title(Tr.t("ELIGE LISTAS")).multi(names, on)
                .button(Tr.t("CERRAR SESIÓN"), Lucent.GLASS, new Runnable() {
                    @Override
                    public void run() {
                        SpotifyApi.logout(MainActivity.this);
                    }
                })
                .button(Tr.t("CANCELAR"), Lucent.GLASS, null)
                .button(Tr.t("IMPORTAR"), Lucent.ACCENT, new Runnable() {
                    @Override
                    public void run() {
                        ArrayList<SpotifyApi.Pl> sel = new ArrayList<SpotifyApi.Pl>();
                        for (int i = 0; i < on.length; i++) {
                            if (on[i]) {
                                sel.add(pls.get(i));
                            }
                        }
                        importFromSpotify(sel);
                    }
                }).show();
    }

    private void importFromSpotify(final ArrayList<SpotifyApi.Pl> sel) {
        if (sel.isEmpty()) {
            return;
        }
        Toast.makeText(this, Tr.t("Importando…"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder report = new StringBuilder();
                final StringBuilder missingAll = new StringBuilder();
                final ArrayList<SpotifyImport.Row> missingRows = new ArrayList<SpotifyImport.Row>();
                java.util.List<SpotifyImport.Cand> local = Imports.cands(Player.I.tracks);
                for (SpotifyApi.Pl pl : sel) {
                    try {
                        java.util.List<SpotifyImport.Row> rows = SpotifyApi.items(MainActivity.this, pl.id);
                        if (rows.isEmpty()) {
                            report.append(pl.name).append(Tr.t(": sin acceso a sus canciones (¿no es tuya?)\n"));
                            continue;
                        }
                        Imports.save(MainActivity.this, pl.name, rows);
                        SpotifyImport.Result r = SpotifyImport.match(rows, local);
                        missingRows.addAll(r.missing);
                        ArrayList<String> uu = new ArrayList<String>();
                        for (int i : r.matched) {
                            uu.add(Player.I.tracks.get(i).uri.toString());
                        }
                        if (!uu.isEmpty()) {
                            Playlists.I.create(pl.name);
                            Playlists.I.add(pl.name, uu);
                        }
                        report.append(pl.name).append(": ").append(r.matched.size()).append(Tr.t(" de "))
                                .append(r.total).append("\n");
                        for (SpotifyImport.Row m : r.missing) {
                            missingAll.append(m).append('\n');
                        }
                    } catch (Exception e) {
                        report.append(pl.name).append(": ").append(e.getMessage()).append("\n");
                    }
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        showImportResult(report.toString(), missingAll.toString(), missingRows);
                    }
                });
            }
        }, "spotify-import").start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
            return;
        }
        Player.I.addListener(listener);
        refilter();
        refreshBar();
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Player.I.removeListener(listener);
        ui.removeCallbacks(ticker);
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        root.addView(col, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText("SCRATCH");
        title.setPadding(dp(8), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView beta = text("BETA " + ver, 11, th.accent, th.mono);
        beta.setLetterSpacing(0.1f);
        beta.setPadding(dp(10), dp(14), 0, 0);
        head.addView(beta, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        searchBtn = new DotIconView(this, th);
        searchBtn.set(DotIcons.SEARCH, th.ink);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleSearch();
            }
        });
        head.addView(searchBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
        DotIconView headQueue = new DotIconView(this, th);
        headQueue.set(DotIcons.QUEUE, th.ink);
        headQueue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, QueueActivity.class));
            }
        });
        head.addView(headQueue, new LinearLayout.LayoutParams(dp(48), dp(48)));
        final DotIconView menu = new DotIconView(this, th);
        menu.set(DotIcons.MENU, th.ink);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMainMenu(menu);
            }
        });
        head.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        col.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(2), dp(6), dp(2), dp(6));
        hs.addView(chips);
        col.addView(hs);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(th.card(Lucent.R_L));
        panel.setPadding(dp(16), dp(12), dp(16), dp(0));
        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        panelLp.topMargin = dp(6);

        status = text("", 11, th.ink2, th.mono);
        panel.addView(status);

        search = new EditText(this);
        search.setHint(Tr.t("BUSCAR POR TÍTULO O CARPETA"));
        search.setVisibility(View.GONE);
        search.setHintTextColor(th.dotOff);
        search.setTextColor(th.ink);
        search.setTextSize(14);
        search.setTypeface(th.regular);
        search.setSingleLine(true);
        search.setBackgroundColor(0);
        search.setPadding(0, dp(12), 0, dp(8));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
                refilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        panel.addView(search);

        listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setPadding(0, dp(8), 0, dp(10));
        listHeader.setVisibility(View.GONE);
        listCover = CoverCache.view(this, 14);
        listHeader.addView(listCover, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout lhText = new LinearLayout(this);
        lhText.setOrientation(LinearLayout.VERTICAL);
        lhText.setPadding(dp(14), 0, dp(8), 0);
        listName = text("", 18, th.ink, th.semi);
        listName.setMaxLines(2);
        listName.setEllipsize(TextUtils.TruncateAt.END);
        listCount = text("", 11, th.ink2, th.mono);
        listCount.setPadding(0, dp(4), 0, 0);
        lhText.addView(listName);
        lhText.addView(listCount);
        LinearLayout lhBtns = new LinearLayout(this);
        lhBtns.setOrientation(LinearLayout.HORIZONTAL);
        lhBtns.setGravity(Gravity.CENTER_VERTICAL);
        lhBtns.setPadding(0, dp(6), 0, 0);
        DotIconView listPlay = new DotIconView(this, th).ghost(false);
        listPlay.set(DotIcons.PLAY, th.accent);
        listPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playWholeList();
            }
        });
        lhBtns.addView(listPlay, new LinearLayout.LayoutParams(dp(52), dp(52)));
        listShuffle = new DotIconView(this, th);
        listShuffle.set(DotIcons.SHUFFLE, th.ink);
        listShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.setShuffle(!Player.I.isShuffle());
                listShuffle.set(DotIcons.SHUFFLE, Player.I.isShuffle() ? th.accent : th.ink);
            }
        });
        LinearLayout.LayoutParams lsp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lsp.leftMargin = dp(6);
        lhBtns.addView(listShuffle, lsp);
        lhText.addView(lhBtns);
        listHeader.addView(lhText, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView sortChip = th.chip(this, Tr.t("ORDEN"), false, 16);
        LinearLayout.LayoutParams sclp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        sclp.rightMargin = dp(8);
        listHeader.addView(sortChip, sclp);
        sortChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortMenu(v);
            }
        });
        TextView editChip = th.chip(this, Tr.t("EDITAR"), false, 16);
        listHeader.addView(editChip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44)));
        View.OnClickListener editList = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Playlists.I.find(listSel) != null) {
                    showListMenu(v, listSel);
                }
            }
        };
        editChip.setOnClickListener(editList);
        listCover.setOnClickListener(editList);
        panel.addView(listHeader);

        home = new HomeView(this, th, new HomeView.Host() {
            @Override
            public void play(List<Integer> idxs, int pos) {
                Player.I.playFrom(new ArrayList<Integer>(idxs), pos);
            }

            @Override
            public void open(String what) {
                openFromHome(what);
            }
        });
        panel.addView(home, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        list = new ListView(this);
        list.setDivider(new ColorDrawable(th.ghost));
        list.setDividerHeight(1);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, dp(96));
        list.setVerticalScrollBarEnabled(false);
        list.setFastScrollEnabled(true);
        list.setFastScrollAlwaysVisible(false);
        adapter = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int pos) {
                return shown.get(pos);
            }

            @Override
            public long getItemId(int pos) {
                return shown.get(pos);
            }

            @Override
            public View getView(int pos, View convert, ViewGroup parent) {
                LinearLayout row;
                if (convert instanceof LinearLayout && ((LinearLayout) convert).getChildCount() == 3
                        && ((LinearLayout) convert).getChildAt(0) instanceof android.widget.ImageView) {
                    row = (LinearLayout) convert;
                } else {
                    row = new LinearLayout(MainActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(0, dp(8), dp(20), dp(8));
                    row.addView(CoverCache.view(MainActivity.this, 8), new LinearLayout.LayoutParams(dp(54), dp(54)));
                    LinearLayout tx = new LinearLayout(MainActivity.this);
                    tx.setOrientation(LinearLayout.VERTICAL);
                    tx.setPadding(dp(12), 0, 0, 0);
                    TextView n = text("", 14, th.ink, th.regular);
                    n.setSingleLine(true);
                    n.setEllipsize(TextUtils.TruncateAt.END);
                    TextView s = text("", 11, th.ink2, th.mono);
                    s.setSingleLine(true);
                    s.setEllipsize(TextUtils.TruncateAt.END);
                    tx.addView(n);
                    tx.addView(s);
                    row.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                    TextView num = text("", 11, th.ink2, th.mono);
                    num.setGravity(Gravity.END);
                    num.setPadding(dp(8), 0, 0, 0);
                    row.addView(num, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                }
                ((TextView) row.getChildAt(2)).setText(String.valueOf(pos + 1));
                int idx = shown.get(pos);
                Player.Track t = Player.I.tracks.get(idx);
                CoverCache.into(MainActivity.this, (android.widget.ImageView) row.getChildAt(0), t.uri);
                LinearLayout tx = (LinearLayout) row.getChildAt(1);
                TextView n = (TextView) tx.getChildAt(0);
                TextView s = (TextView) tx.getChildAt(1);
                boolean cur = idx == Player.I.index();
                n.setText(cur ? "●  " + t.display() : t.display());
                n.setTypeface(cur ? th.semi : th.regular);
                String f = t.artist.length() > 0 ? t.artist
                        + (t.album.length() > 0 ? "  ·  " + t.album : "")
                        : (tab == TAB_FOLDER ? "" : folderLabel(idx));
                s.setText(f);
                s.setVisibility(f.length() == 0 ? View.GONE : View.VISIBLE);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                playAt(pos);
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                showTrackMenu(view, shown.get(pos));
                return true;
            }
        });
        panel.addView(list, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        DotTextView et = new DotTextView(this, 2.2f, th);
        et.setText(Tr.t("SIN MÚSICA"));
        empty.addView(et);
        TextView msg = text(Tr.t("Elige la carpeta donde tienes tus canciones y listas."), 13, th.ink2, th.regular);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(dp(20), dp(14), dp(20), dp(14));
        empty.addView(msg);
        Button pick = new Button(this);
        pick.setText(Tr.t("ELEGIR CARPETA"));
        th.style(pick, Lucent.ACCENT);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder();
            }
        });
        empty.addView(pick, new LinearLayout.LayoutParams(dp(220), dp(52)));
        panel.addView(empty, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        col.addView(panel, panelLp);

        root.addView(buildBar(), new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));
        return root;
    }

    /** Barra flotante con la canción actual: tocarla abre la vista de scratch. */
    private View buildBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Lucent.withAlpha(th.solid, 0.96f));
        bg.setCornerRadius(dp(Lucent.R_L));
        bg.setStroke(dp(1), th.glassLine);
        bar.setBackground(bg);
        bar.setElevation(dp(8));
        bar.setPadding(dp(12), dp(8), dp(10), dp(12));
        bar.setClickable(true);
        bar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ScratchActivity.class));
                overridePendingTransition(0, 0);
            }
        });

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        barCover = CoverCache.view(this, 10);
        CoverCache.into(this, barCover, null);
        LinearLayout.LayoutParams bcp = new LinearLayout.LayoutParams(dp(48), dp(48));
        bcp.rightMargin = dp(12);
        row.addView(barCover, bcp);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        barTitle = text("", 14, th.ink, th.semi);
        barTitle.setSingleLine(true);
        barTitle.setEllipsize(TextUtils.TruncateAt.END);
        barTime = text("", 11, th.ink2, th.mono);
        barTime.setSingleLine(true);
        texts.addView(barTitle);
        texts.addView(barTime);
        row.addView(texts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        DotIconView prev = new DotIconView(this, th);
        prev.set(DotIcons.PREV, th.ink);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.prev();
            }
        });
        barPlay = new DotIconView(this, th).ghost(false);
        barPlay.set(DotIcons.PLAY, th.accent);
        barPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.toggle();
            }
        });
        DotIconView next = new DotIconView(this, th);
        next.set(DotIcons.NEXT, th.ink);
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.next();
            }
        });
        DotIconView queueBtn = new DotIconView(this, th);
        queueBtn.set(DotIcons.QUEUE, th.ink2);
        queueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, QueueActivity.class));
            }
        });
        row.addView(queueBtn, new LinearLayout.LayoutParams(dp(40), dp(44)));
        row.addView(prev, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(barPlay, new LinearLayout.LayoutParams(dp(52), dp(52)));
        row.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44)));
        bar.addView(row);

        barLine = new LineView(this, th.ghost, th.accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3));
        lp.topMargin = dp(4);
        lp.rightMargin = dp(8);
        bar.addView(barLine, lp);
        return bar;
    }

    private void refreshBar() {
        Player p = Player.I;
        String t = p.isLoading() ? Tr.t("CARGANDO…") : p.title();
        barTitle.setText(t.toUpperCase(Locale.ROOT));
        barPlay.set(p.isPlayingOrPending() ? DotIcons.PAUSE : DotIcons.PLAY, th.accent);
        Uri cu = p.currentUri();
        String ck = cu == null ? "" : cu.toString();
        if (!ck.equals(barCoverKey)) {
            barCoverKey = ck;
            CoverCache.into(this, barCover, cu);
        }
        lastSec = -1;
        tickBar();
    }

    private void tickBar() {
        ScratchEngine e = Player.I.engine;
        double pos = e.getPositionSeconds();
        double total = e.getDurationSeconds();
        barLine.set(total > 0 ? (float) (pos / total) : 0f);
        int sec = (int) pos;
        if (sec != lastSec) {
            lastSec = sec;
            barTime.setText(fmt(sec) + " / " + fmt((int) total));
        }
    }

    private static String fmt(int s) {
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    @Override
    public void onBackPressed() {
        if (searchOpen) {
            toggleSearch();
        } else if (tab != TAB_HOME) {
            tab = TAB_HOME;
            refilter();
        } else {
            super.onBackPressed();
        }
    }

    /** Accesos de la pantalla de inicio. */
    private void openFromHome(String what) {
        final Player p = Player.I;
        if (what.equals("NOW")) {
            startActivity(new Intent(this, ScratchActivity.class));
            return;
        }
        if (what.equals("STATS")) {
            startActivity(new Intent(this, StatsActivity.class));
            return;
        }
        if (what.equals("SETTINGS")) {
            startActivity(new Intent(this, SettingsActivity.class));
            return;
        }
        if (what.equals("ALL")) {
            tab = TAB_ALL;
        } else if (what.equals("FAVS")) {
            tab = TAB_FAVS;
        } else if (what.equals("RECENT")) {
            tab = TAB_RECENT;
        } else if (what.equals("TOP")) {
            tab = TAB_TOP;
        } else if (what.startsWith("LIST:")) {
            tab = TAB_LIST;
            listSel = what.substring(5);
        } else if (what.equals("SHUFFLE")) {
            if (p.tracks.isEmpty()) {
                return;
            }
            ArrayList<Integer> all = new ArrayList<Integer>();
            for (int i = 0; i < p.tracks.size(); i++) {
                all.add(i);
            }
            p.setShuffle(true);
            p.playFrom(all, new java.util.Random().nextInt(all.size()));
            return;
        } else if (what.equals("MISSING")) {
            showMissing(Imports.missing(this, p.tracks));
            return;
        } else if (what.equals("FOLDERS")) {
            TreeMap<String, Integer> fs = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
            for (String f : folderOf) {
                Integer n = fs.get(f);
                fs.put(f, n == null ? 1 : n + 1);
            }
            final ArrayList<String> keys = new ArrayList<String>(fs.keySet());
            String[] labels = new String[keys.size()];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = (keys.get(i).length() == 0 ? Tr.t("RAÍZ") : keys.get(i).toUpperCase(Locale.ROOT))
                        + "  " + fs.get(keys.get(i));
            }
            new DotDialog(this, th).title(Tr.t("CARPETAS")).options(labels, new DotMenu.Picked() {
                @Override
                public void onPick(int id) {
                    tab = TAB_FOLDER;
                    folderSel = keys.get(id);
                    refilter();
                }
            }).button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
            return;
        }
        refilter();
    }

    private void toggleSearch() {
        searchOpen = !searchOpen;
        if (searchOpen && tab == TAB_HOME) {
            tab = TAB_ALL;
            refilter();
        }
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                getSystemService(INPUT_METHOD_SERVICE);
        if (searchOpen) {
            search.setVisibility(View.VISIBLE);
            search.requestFocus();
            imm.showSoftInput(search, 0);
        } else {
            search.setText("");
            search.setVisibility(View.GONE);
            imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
        }
        searchBtn.set(searchOpen ? DotIcons.CLOSE : DotIcons.SEARCH, searchOpen ? th.accent : th.ink);
    }

    // ------------------------------------------------------------------ lista

    private String folderLabel(int idx) {
        return idx < folderOf.length ? folderOf[idx] : "";
    }

    private void playAt(int pos) {
        Player p = Player.I;
        boolean plain = tab == TAB_ALL && search.getText().toString().trim().length() == 0;
        if (plain) {
            p.choose(shown.get(pos));
        } else {
            p.playFrom(new ArrayList<Integer>(shown), pos);
        }
    }

    private void refilter() {
        Player p = Player.I;
        if (folderStamp != p.tracks.size()) {
            folderStamp = p.tracks.size();
            folderOf = new String[folderStamp];
            uriIndex = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < folderStamp; i++) {
                folderOf[i] = p.tracks.get(i).folder();
                uriIndex.put(p.tracks.get(i).uri.toString(), i);
            }
            chipKey = "";
        }
        if (listVersion != Playlists.I.version) {
            listVersion = Playlists.I.version;
            chipKey = "";
            if (tab == TAB_LIST && Playlists.I.find(listSel) == null) {
                tab = TAB_ALL;
            }
        }
        buildChips();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        if (tab == TAB_HOME) {
            // el inicio no usa la lista
        } else if (tab == TAB_LIST) {
            for (String u : Playlists.I.uris(listSel)) {
                Integer i = uriIndex.get(u);
                if (i != null && matches(i, q)) {
                    shown.add(i);
                }
            }
            loadSort();
            if (sortMode != 0) {
                sortShown();
            }
        } else if (tab == TAB_RECENT || tab == TAB_TOP) {
            final boolean top = tab == TAB_TOP;
            ArrayList<Integer> all = new ArrayList<Integer>();
            for (int i = 0; i < p.tracks.size(); i++) {
                if (Meta.count(p.tracks.get(i).uri.toString()) > 0 && matches(i, q)) {
                    all.add(i);
                }
            }
            final Player pp = p;
            java.util.Collections.sort(all, new java.util.Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    String ua = pp.tracks.get(a).uri.toString();
                    String ub = pp.tracks.get(b).uri.toString();
                    long x = top ? Meta.count(ua) : Meta.last(ua);
                    long y = top ? Meta.count(ub) : Meta.last(ub);
                    return x == y ? 0 : (x < y ? 1 : -1);
                }
            });
            shown.addAll(all);
        } else {
            for (int i = 0; i < p.tracks.size(); i++) {
                if (tab == TAB_FAVS && !p.isFav(p.tracks.get(i).uri)) {
                    continue;
                }
                if (tab == TAB_FOLDER && !folderOf[i].equals(folderSel)) {
                    continue;
                }
                if (matches(i, q)) {
                    shown.add(i);
                }
            }
        }
        boolean none = p.count() == 0 && !p.isScanning();
        boolean isHome = tab == TAB_HOME;
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        list.setVisibility(none || isHome ? View.GONE : View.VISIBLE);
        home.setVisibility(none || !isHome ? View.GONE : View.VISIBLE);
        boolean showHeader = tab == TAB_LIST && !none;
        listHeader.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        if (showHeader) {
            bindListHeader();
        }
        if (isHome && !none) {
            home.refresh();
        }
        if (none) {
            search.setVisibility(View.GONE);
        }
        if (p.isScanning()) {
            status.setText(Tr.t("ESCANEANDO… ") + p.scanCount());
        } else if (p.count() == 0 || isHome) {
            status.setText("");
        } else {
            status.setText(shown.size() + Tr.t(" CANCIONES")
                    + ((tab == TAB_RECENT || tab == TAB_TOP) && shown.isEmpty() ? Tr.t(" · REPRODUCE ALGO") : ""));
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matches(int i, String q) {
        if (q.length() == 0) {
            return true;
        }
        return Player.I.tracks.get(i).lower.contains(q)
                || folderOf[i].toLowerCase(Locale.ROOT).contains(q);
    }

    /** Pestañas: CANCIONES, FAVORITAS y una por carpeta. Solo se reconstruyen si cambia algo. */
    private void buildChips() {
        String key = folderStamp + "|" + metaStamp + "|" + tab + "|" + folderSel + "|" + listSel + "|" + Playlists.I.version;
        if (key.equals(chipKey)) {
            return;
        }
        chipKey = key;
        chips.removeAllViews();
        addChip(Tr.t("INICIO"), tab == TAB_HOME, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = TAB_HOME;
                refilter();
            }
        });
        addChip(Tr.t("CANCIONES"), tab == TAB_ALL, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = TAB_ALL;
                refilter();
            }
        });
        addChip(Tr.t("FAVORITAS"), tab == TAB_FAVS, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = TAB_FAVS;
                refilter();
            }
        });
        addChip(Tr.t("RECIENTES"), tab == TAB_RECENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = TAB_RECENT;
                refilter();
            }
        });
        addChip(Tr.t("MÁS ESCUCHADAS"), tab == TAB_TOP, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tab = TAB_TOP;
                refilter();
            }
        });
        for (final String name : Playlists.I.names()) {
            final String label = name.toUpperCase(Locale.ROOT) + "  " + Playlists.I.size(name);
            TextView chip = addChip(label, tab == TAB_LIST && name.equals(listSel), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = TAB_LIST;
                    listSel = name;
                    refilter();
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    showListMenu(v, name);
                    return true;
                }
            });
        }
        addChip(Tr.t("+ LISTA"), false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PlaylistUi.promptName(MainActivity.this, new PlaylistUi.Name() {
                    @Override
                    public void onName(String name) {
                        if (Playlists.I.create(name)) {
                            tab = TAB_LIST;
                            listSel = name;
                            refilter();
                            askListFolder(name);
                        }
                    }
                });
            }
        });
        TreeMap<String, Integer> folders = new TreeMap<String, Integer>(String.CASE_INSENSITIVE_ORDER);
        for (String f : folderOf) {
            Integer n = folders.get(f);
            folders.put(f, n == null ? 1 : n + 1);
        }
        for (final java.util.Map.Entry<String, Integer> en : folders.entrySet()) {
            final String f = en.getKey();
            String label = (f.length() == 0 ? Tr.t("RAÍZ") : f.toUpperCase(Locale.ROOT)) + "  " + en.getValue();
            addChip(label, tab == TAB_FOLDER && f.equals(folderSel), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    tab = TAB_FOLDER;
                    folderSel = f;
                    refilter();
                }
            });
        }
    }

    private TextView addChip(String label, boolean selected, View.OnClickListener click) {
        TextView t = th.chip(this, label, selected, 16);
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        lp.rightMargin = dp(8);
        chips.addView(t, lp);
        return t;
    }

    // ------------------------------------------------------------------ menús

    private void showMainMenu(View anchor) {
        DotMenu.show(this, th, anchor,
                new String[] {Tr.t("ELEGIR CARPETA"), Tr.t("RESCANEAR"), Tr.t("ABRIR ARCHIVO"), Tr.t("IMPORTAR DE SPOTIFY"), Tr.t("FALTANTES"), Tr.t("DIAGNÓSTICO"), Tr.t("ESTADÍSTICAS"), Tr.t("AJUSTES")},
                new DotMenu.Picked() {
                    @Override
                    public void onPick(int id) {
                        if (id == 0) {
                            pickFolder();
                        } else if (id == 1) {
                            if (Player.I.hasLibraryFolder() && !Player.I.isScanning()) {
                                Player.I.rescan();
                            }
                        } else if (id == 2) {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("audio/*");
                            startActivityForResult(i, REQ_OPEN);
                        } else if (id == 3) {
                            chooseImportMethod();
                        } else if (id == 4) {
                            showMissing(Imports.missing(MainActivity.this, Player.I.tracks));
                        } else if (id == 5) {
                            String head = Tr.t("Canciones en la biblioteca: ") + Player.I.tracks.size()
                                    + (Player.I.isScanning() ? Tr.t("\nEscaneando… ") + Player.I.scanCount() : "")
                                    + Tr.t("\nCarpeta guardada: ") + (Player.I.hasLibraryFolder() ? Tr.t("sí") : "no") + "\n\n";
                            new DotDialog(MainActivity.this, th).title(Tr.t("DIAGNÓSTICO"))
                                    .message(head + Library.report)
                                    .button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
                        } else if (id == 6) {
                            startActivity(new Intent(MainActivity.this, StatsActivity.class));
                        } else {
                            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                        }
                    }
                });
    }

    private void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_TREE);
    }

    private void showSongStats(Player.Track t) {
        final Map<String, String[]> tags = new HashMap<String, String[]>();
        for (Player.Track x : Player.I.tracks) {
            tags.put(x.uri.toString(), new String[] {x.display(), x.artist, x.album});
        }
        Stats st = new Stats(PlayLog.all(), new Stats.Info() {
            @Override
            public String[] tags(String uri) {
                return tags.get(uri);
            }
        }, java.util.TimeZone.getDefault());
        String u = t.uri.toString();
        Stats.Row r = st.track(u);
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
        long first = st.firstListen(u);
        long last = Meta.last(u);
        String msg;
        if (r == null || (r.plays == 0 && r.secs == 0)) {
            msg = Tr.t("Aún sin escuchas registradas.");
        } else {
            int rank = st.rankOf(u);
            msg = Tr.t("Reproducciones: ") + r.plays + Tr.t("\nTiempo: ") + Stats.duration(r.secs)
                    + (rank > 0 ? Tr.t("\nPuesto en tu top: ") + rank : "")
                    + (first > 0 ? Tr.t("\nPrimera vez: ") + df.format(new java.util.Date(first)) : "")
                    + (last > 0 ? Tr.t("\nÚltima vez: ") + df.format(new java.util.Date(last)) : "");
        }
        new DotDialog(this, th).title(Tr.t("ESTADISTICAS")).message(t.display() + "\n\n" + msg)
                .button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
    }

    private static final int A_NEXT = 0;
    private static final int A_QUEUE = 1;
    private static final int A_ADD = 2;
    private static final int A_REMOVE = 3;
    private static final int A_UP = 4;
    private static final int A_DOWN = 5;
    private static final int A_MOVE = 6;
    private static final int A_FAV = 7;
    private static final int A_EDIT = 8;
    private static final int A_STATS = 9;

    private void showTrackMenu(View anchor, final int idx) {
        final Player p = Player.I;
        final Player.Track t = p.tracks.get(idx);
        final boolean inList = tab == TAB_LIST;
        // Mover solo tiene sentido con la lista completa a la vista (sin filtrar con la búsqueda).
        final boolean canMove = inList && sortMode == 0 && search.getText().toString().trim().length() == 0;
        final ArrayList<Integer> acts = new ArrayList<Integer>();
        final ArrayList<String> labs = new ArrayList<String>();
        acts.add(A_NEXT);
        labs.add(Tr.t("REPRODUCIR A CONTINUACIÓN"));
        acts.add(A_QUEUE);
        labs.add(Tr.t("AÑADIR A LA COLA"));
        acts.add(A_ADD);
        labs.add(Tr.t("AÑADIR A LISTA…"));
        if (inList) {
            acts.add(A_REMOVE);
            labs.add(Tr.t("QUITAR DE ESTA LISTA"));
        }
        if (canMove) {
            acts.add(A_UP);
            labs.add(Tr.t("SUBIR UN PUESTO"));
            acts.add(A_DOWN);
            labs.add(Tr.t("BAJAR UN PUESTO"));
            acts.add(A_MOVE);
            labs.add(Tr.t("MOVER A LA POSICIÓN…"));
        }
        acts.add(A_FAV);
        labs.add(p.isFav(t.uri) ? Tr.t("QUITAR DE FAVORITAS") : Tr.t("AÑADIR A FAVORITAS"));
        acts.add(A_EDIT);
        labs.add(Tr.t("EDITAR NOMBRE…"));
        acts.add(A_STATS);
        labs.add(Tr.t("ESTADÍSTICAS"));
        final View a = anchor;
        DotMenu.show(this, th, anchor, labs.toArray(new String[0]), new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                final String uri = t.uri.toString();
                switch (acts.get(id)) {
                    case A_NEXT:
                        p.addToQueue(idx, true);
                        break;
                    case A_QUEUE:
                        p.addToQueue(idx, false);
                        break;
                    case A_ADD:
                        final List<String> one = new ArrayList<String>();
                        one.add(uri);
                        a.post(new Runnable() {
                            @Override
                            public void run() {
                                PlaylistUi.pick(MainActivity.this, th, a, one, null);
                            }
                        });
                        break;
                    case A_REMOVE:
                        Playlists.I.remove(listSel, uri);
                        break;
                    case A_UP:
                        Playlists.I.move(listSel, uri, Playlists.I.indexOf(listSel, uri) - 1);
                        refilter();
                        break;
                    case A_DOWN:
                        Playlists.I.move(listSel, uri, Playlists.I.indexOf(listSel, uri) + 1);
                        refilter();
                        break;
                    case A_MOVE:
                        PlaylistUi.promptPosition(MainActivity.this, Playlists.I.indexOf(listSel, uri) + 1,
                                Playlists.I.size(listSel), new PlaylistUi.IntCb() {
                                    @Override
                                    public void on(int pos) {
                                        Playlists.I.move(listSel, uri, pos - 1);
                                        refilter();
                                    }
                                });
                        break;
                    case A_FAV:
                        p.toggleFav(t.uri);
                        break;
                    case A_EDIT:
                        PlaylistUi.editTrack(MainActivity.this, t, new Runnable() {
                            @Override
                            public void run() {
                                refilter();
                                refreshBar();
                            }
                        });
                        break;
                    default:
                        showSongStats(t);
                        break;
                }
            }
        });
    }

    /** Cabecera de la lista abierta: su portada (mosaico o propia), el nombre y cuántas canciones tiene. */
    /** Reproduce la lista que se ve: de la primera a la última, o desde una al azar y el resto mezclado si el aleatorio está activo. */
    private void playWholeList() {
        if (shown.isEmpty()) {
            return;
        }
        Player p = Player.I;
        int start = p.isShuffle() ? new java.util.Random().nextInt(shown.size()) : 0;
        p.playFrom(new ArrayList<Integer>(shown), start);
    }

    private void bindListHeader() {
        listShuffle.set(DotIcons.SHUFFLE, Player.I.isShuffle() ? th.accent : th.ink);
        String k = listSel + "|" + Playlists.I.version + "|" + Tr.lang;
        if (k.equals(headerKey)) {
            return;
        }
        headerKey = k;
        listName.setText(listSel);
        listCount.setText(Playlists.I.size(listSel) + Tr.t(" CANCIONES"));
        CoverCache.intoPlaylist(this, listCover, listSel);
    }

    private void loadSort() {
        if (listSel == null || listSel.equals(sortFor)) {
            return;
        }
        sortFor = listSel;
        android.content.SharedPreferences sp = Lucent.prefs(this);
        sortMode = sp.getInt("sortm_" + listSel, 0);
        sortDesc = sp.getBoolean("sortd_" + listSel, false);
    }

    private void saveSort() {
        if (listSel != null) {
            Lucent.prefs(this).edit().putInt("sortm_" + listSel, sortMode)
                    .putBoolean("sortd_" + listSel, sortDesc).apply();
        }
    }

    private static int cmpText(String a, String b) {
        boolean ea = a.length() == 0;
        boolean eb = b.length() == 0;
        if (ea || eb) {
            return ea == eb ? 0 : (ea ? 1 : -1);
        }
        return a.compareToIgnoreCase(b);
    }

    /** Ordena la lista que se ve según el modo elegido (los vacíos siempre al final). */
    private void sortShown() {
        final Player p = Player.I;
        final int mode = sortMode;
        final boolean desc = sortDesc;
        java.util.Collections.sort(shown, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                Player.Track a = p.tracks.get(x);
                Player.Track b = p.tracks.get(y);
                int c;
                if (mode == 1) {
                    c = cmpText(a.display(), b.display());
                } else if (mode == 2) {
                    c = cmpText(a.artist, b.artist);
                } else if (mode == 3) {
                    c = cmpText(a.album, b.album);
                } else {
                    boolean za = a.durMs <= 0;
                    boolean zb = b.durMs <= 0;
                    if (za || zb) {
                        c = za == zb ? 0 : (za ? 1 : -1);
                        return c != 0 ? c : cmpText(a.display(), b.display());
                    }
                    c = a.durMs < b.durMs ? -1 : (a.durMs > b.durMs ? 1 : 0);
                }
                if (c != 0 && desc) {
                    boolean ea = (mode == 2 ? a.artist : (mode == 3 ? a.album : a.display())).length() == 0;
                    boolean eb = (mode == 2 ? b.artist : (mode == 3 ? b.album : b.display())).length() == 0;
                    if (!(mode >= 2 && mode <= 3 && (ea || eb))) {
                        c = -c;
                    }
                }
                return c != 0 ? c : cmpText(a.display(), b.display());
            }
        });
    }

    private void showSortMenu(View anchor) {
        if (Playlists.I.find(listSel) == null) {
            return;
        }
        loadSort();
        final String[] names = {Tr.t("ORDEN DE LA LISTA"), Tr.t("TÍTULO A–Z"), Tr.t("ARTISTA"), Tr.t("ÁLBUM"),
                Tr.t("DURACIÓN")};
        final ArrayList<String> labels = new ArrayList<String>();
        for (int i = 0; i < names.length; i++) {
            labels.add((i == sortMode ? "● " : "") + names[i]);
        }
        if (sortMode != 0) {
            labels.add(sortDesc ? Tr.t("ORDEN ASCENDENTE") : Tr.t("ORDEN DESCENDENTE"));
            labels.add(Tr.t("GUARDAR ESTE ORDEN EN LA LISTA"));
        }
        DotMenu.show(this, th, anchor, labels.toArray(new String[0]), new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                if (id < names.length) {
                    sortMode = id;
                    sortDesc = false;
                    saveSort();
                    refilter();
                } else if (id == names.length) {
                    sortDesc = !sortDesc;
                    saveSort();
                    refilter();
                } else {
                    if (search.getText().toString().trim().length() > 0) {
                        Toast.makeText(MainActivity.this, Tr.t("Borra la búsqueda para guardar el orden"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ArrayList<String> uris = new ArrayList<String>();
                    for (int i : shown) {
                        uris.add(Player.I.tracks.get(i).uri.toString());
                    }
                    Playlists.I.setOrder(listSel, uris);
                    sortMode = 0;
                    sortDesc = false;
                    saveSort();
                    Toast.makeText(MainActivity.this, Tr.t("Orden guardado en la lista"),
                            Toast.LENGTH_SHORT).show();
                    refilter();
                }
            }
        });
    }

    /** Opciones de una lista: renombrar, cambiar o quitar la portada, carpeta, borrar. */
    private static final int L_RENAME = 0;
    private static final int L_COVER = 1;
    private static final int L_NOCOVER = 2;
    private static final int L_FOLDER = 3;
    private static final int L_REFRESH = 4;
    private static final int L_UNLINK = 5;
    private static final int L_DELETE = 6;
    private static final int L_EXPORT = 7;
    private static final int REQ_EXPORT = 8;
    private String exportTarget = "";

    /** Pregunta si la lista nueva debe enlazarse a su propia carpeta de canciones. */
    private void askListFolder(final String name) {
        new DotDialog(this, th).title(Tr.t("CARPETA DE LA LISTA"))
                .message(Tr.t("¿Quieres que esta lista use su propia carpeta? Sus canciones se añadirán solas."))
                .button(Tr.t("SIN CARPETA"), Lucent.GLASS, null)
                .button(Tr.t("ELEGIR CARPETA"), Lucent.ACCENT, new Runnable() {
                    @Override
                    public void run() {
                        pickListFolder(name);
                    }
                }).show();
    }

    private void pickListFolder(String name) {
        folderTarget = name;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_LISTFOLDER);
    }

    private void writeM3u(String list, Uri dest) {
        ArrayList<M3u.Entry> es = new ArrayList<M3u.Entry>();
        for (String u : Playlists.I.uris(list)) {
            Player.Track found = null;
            for (Player.Track t : Player.I.tracks) {
                if (t.uri.toString().equals(u)) {
                    found = t;
                    break;
                }
            }
            if (found != null) {
                es.add(new M3u.Entry(found.name, found.artist, found.display(), (int) (found.durMs / 1000)));
            }
        }
        try {
            java.io.OutputStream o = getContentResolver().openOutputStream(dest, "wt");
            o.write(M3u.write(list, es).getBytes("UTF-8"));
            o.close();
            Toast.makeText(this, Tr.t("Lista exportada: ") + es.size(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, Tr.t("No se pudo exportar la lista"), Toast.LENGTH_SHORT).show();
        }
    }

    private void showListMenu(View anchor, final String name) {
        final boolean hasCover = Playlists.I.cover(name) != null;
        final boolean linked = Playlists.I.folder(name) != null;
        final ArrayList<Integer> codes = new ArrayList<Integer>();
        final ArrayList<String> labels = new ArrayList<String>();
        codes.add(L_RENAME);
        labels.add(Tr.t("RENOMBRAR LISTA"));
        codes.add(L_COVER);
        labels.add(hasCover ? Tr.t("CAMBIAR PORTADA…") : Tr.t("ELEGIR PORTADA…"));
        if (hasCover) {
            codes.add(L_NOCOVER);
            labels.add(Tr.t("QUITAR PORTADA PROPIA"));
        }
        codes.add(L_FOLDER);
        labels.add(linked ? Tr.t("CAMBIAR CARPETA…") : Tr.t("ENLAZAR CARPETA…"));
        if (linked) {
            codes.add(L_REFRESH);
            labels.add(Tr.t("ACTUALIZAR DESDE LA CARPETA"));
            codes.add(L_UNLINK);
            labels.add(Tr.t("DESENLAZAR CARPETA"));
        }
        codes.add(L_EXPORT);
        labels.add(Tr.t("EXPORTAR COMO M3U…"));
        codes.add(L_DELETE);
        labels.add(Tr.t("BORRAR LISTA"));
        DotMenu.show(this, th, anchor, labels.toArray(new String[0]), new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                int code = codes.get(id);
                if (code == L_RENAME) {
                    PlaylistUi.promptRename(MainActivity.this, name, new PlaylistUi.Name() {
                        @Override
                        public void onName(String n) {
                            if (Playlists.I.rename(name, n)) {
                                if (name.equals(listSel)) {
                                    listSel = Playlists.I.find(n);
                                }
                                refilter();
                            }
                        }
                    });
                } else if (code == L_COVER) {
                    coverTarget = name;
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("image/*");
                    startActivityForResult(i, REQ_COVER);
                } else if (code == L_NOCOVER) {
                    Playlists.I.setCover(name, null);
                    refilter();
                } else if (code == L_FOLDER) {
                    pickListFolder(name);
                } else if (code == L_REFRESH) {
                    Player.I.refreshListFolder(name);
                } else if (code == L_UNLINK) {
                    Playlists.I.setFolder(name, null);
                    refilter();
                } else if (code == L_EXPORT) {
                    exportTarget = name;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("audio/x-mpegurl");
                    i.putExtra(Intent.EXTRA_TITLE, name + ".m3u8");
                    startActivityForResult(i, REQ_EXPORT);
                } else {
                    Playlists.I.delete(name);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_LISTFOLDER) {
            if (res == RESULT_OK && data != null && data.getData() != null && folderTarget.length() > 0) {
                Uri tree = data.getData();
                try {
                    getContentResolver().takePersistableUriPermission(tree,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (RuntimeException ignored) {
                }
                Player.I.addFolder(tree, folderTarget);
            }
            folderTarget = "";
            return;
        }
        if (req == REQ_COVER) {
            if (res == RESULT_OK && data != null && data.getData() != null && coverTarget.length() > 0) {
                final Uri src = data.getData();
                final String target = coverTarget;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final java.io.File f = CoverCache.importImage(MainActivity.this, src);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (f == null) {
                                    Toast.makeText(MainActivity.this, Tr.t("No se pudo usar esa imagen"),
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Playlists.I.setCover(target, f.getPath());
                                    refilter();
                                }
                            }
                        });
                    }
                }).start();
            }
            return;
        }
        if (req == REQ_EXPORT) {
            if (res == RESULT_OK && data != null && data.getData() != null && exportTarget.length() > 0) {
                writeM3u(exportTarget, data.getData());
            }
            exportTarget = "";
            return;
        }
        if (req == REQ_WEB) {
            if (res == RESULT_OK) {
                fetchPlaylistsDialog();
            }
            return;
        }
        if (res == RESULT_OK && data != null && req == REQ_CSV) {
            ArrayList<Uri> us = new ArrayList<Uri>();
            if (data.getClipData() != null) {
                for (int k = 0; k < data.getClipData().getItemCount(); k++) {
                    us.add(data.getClipData().getItemAt(k).getUri());
                }
            } else if (data.getData() != null) {
                us.add(data.getData());
            }
            importCsvs(us);
            return;
        }
        if (res != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (req == REQ_TREE) {
            try {
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (RuntimeException ignored) {
            }
            Player.I.scan(uri);
        } else if (req == REQ_OPEN) {
            Player.I.openSingle(uri, displayName(uri).toUpperCase(Locale.ROOT));
        }
    }

    /** Importa listas de Spotify (CSV de Exportify): empareja con tus archivos y crea una lista por CSV. */
    private void importCsvs(final ArrayList<Uri> uris) {
        Toast.makeText(this, Tr.t("Importando…"), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder report = new StringBuilder();
                final StringBuilder missingAll = new StringBuilder();
                final ArrayList<SpotifyImport.Row> missingRows = new ArrayList<SpotifyImport.Row>();
                ArrayList<SpotifyImport.Cand> local = new ArrayList<SpotifyImport.Cand>();
                for (Player.Track t : Player.I.tracks) {
                    local.add(new SpotifyImport.Cand(t.title, t.artist, t.name));
                }
                for (Uri u : uris) {
                    String name = displayName(u);
                    String listName = name.replaceAll("(?i)\\.csv$", "").replace('_', ' ').trim();
                    if (listName.length() == 0) {
                        listName = "SPOTIFY";
                    }
                    try {
                        java.io.InputStream in = getContentResolver().openInputStream(u);
                        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while (in != null && (n = in.read(buf)) > 0) {
                            bo.write(buf, 0, n);
                        }
                        if (in != null) {
                            in.close();
                        }
                        java.util.List<SpotifyImport.Row> rows =
                                SpotifyImport.parseTracks(new String(bo.toByteArray(), "UTF-8"));
                        if (rows.isEmpty()) {
                            report.append(listName).append(Tr.t(": no parece un CSV de Exportify\n"));
                            continue;
                        }
                        Imports.save(MainActivity.this, listName, rows);
                        SpotifyImport.Result r = SpotifyImport.match(rows, local);
                        missingRows.addAll(r.missing);
                        ArrayList<String> uu = new ArrayList<String>();
                        for (int i : r.matched) {
                            uu.add(Player.I.tracks.get(i).uri.toString());
                        }
                        if (!uu.isEmpty()) {
                            Playlists.I.create(listName);
                            Playlists.I.add(Playlists.I.find(listName) == null ? listName
                                    : Playlists.I.find(listName), uu);
                        }
                        report.append(listName).append(": ").append(r.matched.size()).append(Tr.t(" de "))
                                .append(r.total).append("\n");
                        for (SpotifyImport.Row m : r.missing) {
                            missingAll.append(m).append('\n');
                        }
                    } catch (Exception e) {
                        report.append(listName).append(Tr.t(": error al leer\n"));
                    }
                }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        showImportResult(report.toString(), missingAll.toString(), missingRows);
                    }
                });
            }
        }, "csv").start();
    }

    private void showImportResult(String report, final String missing,
                                  final java.util.List<SpotifyImport.Row> rows) {
        String warn = Player.I.count() == 0
                ? (Player.I.isScanning() ? Tr.t("Aún se está escaneando tu carpeta; ")
                : Tr.t("Tu biblioteca está vacía; ")) + Tr.t("las listas se guardaron y se rellenarán solas al terminar el escaneo.\n\n")
                : "";
        DotDialog d = new DotDialog(this, th).title(Tr.t("IMPORTACIÓN"))
                .message(warn + report + (rows.isEmpty() ? "" : Tr.t("\nNo encontradas: ") + rows.size()));
        if (!rows.isEmpty()) {
            d.button(Tr.t("COPIAR FALTANTES"), Lucent.GLASS, new Runnable() {
                @Override
                public void run() {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("faltantes", missing));
                    Toast.makeText(MainActivity.this, Tr.t("Copiado"), Toast.LENGTH_SHORT).show();
                }
            });
            d.button(Tr.t("VER FALTANTES"), Lucent.ACCENT, new Runnable() {
                @Override
                public void run() {
                    showMissing(rows);
                }
            });
        } else {
            d.button("OK", Lucent.ACCENT, null);
        }
        d.show();
    }

    /** Lista de faltantes; al tocar una se abre su búsqueda en una tienda o en YouTube Music (en su propia app). */
    private void showMissing(final java.util.List<SpotifyImport.Row> rows) {
        if (rows.isEmpty()) {
            Toast.makeText(this, Tr.t("No falta ninguna"), Toast.LENGTH_SHORT).show();
            return;
        }
        int n = Math.min(rows.size(), 300);
        final String[] items = new String[n];
        for (int i = 0; i < n; i++) {
            items[i] = rows.get(i).toString();
        }
        new DotDialog(this, th).title(Tr.t("FALTANTES · ") + rows.size())
                .options(items, new DotMenu.Picked() {
                    @Override
                    public void onPick(int w) {
                        final String q = Uri.encode(items[w].replace(" - ", " "));
                        new DotDialog(MainActivity.this, th).title(items[w])
                                .options(new String[] {Tr.t("BUSCAR EN YOUTUBE MUSIC"), Tr.t("BUSCAR EN BANDCAMP"),
                                        Tr.t("BUSCAR EN QOBUZ")}, new DotMenu.Picked() {
                                    @Override
                                    public void onPick(int k) {
                                        String url = k == 0 ? "https://music.youtube.com/search?q=" + q
                                                : k == 1 ? "https://bandcamp.com/search?q=" + q
                                                : "https://www.qobuz.com/es-es/search?q=" + q;
                                        try {
                                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                        } catch (RuntimeException e) {
                                            Toast.makeText(MainActivity.this, Tr.t("Sin navegador"), Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                }).button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
                    }
                }).button(Tr.t("CERRAR"), Lucent.ACCENT, null).show();
    }

    private String displayName(Uri uri) {
        Cursor c = null;
        try {
            c = getContentResolver().query(uri, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) {
                    return c.getString(i);
                }
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        String seg = uri.getLastPathSegment();
        return seg == null ? "AUDIO" : seg;
    }

    private TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        if (face == th.mono) {
            t.setLetterSpacing(0.04f);
            t.setLineSpacing(0, 1.25f);
        }
        return t;
    }
}
