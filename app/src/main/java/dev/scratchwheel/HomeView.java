package dev.scratchwheel;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Pantalla de inicio: lo último que escuchaste, tus listas y accesos a todo lo demás. */
final class HomeView extends ScrollView {
    interface Host {
        void play(List<Integer> idxs, int pos);

        /** ALL, FAVS, RECENT, TOP, FOLDERS, MISSING, SHUFFLE o LIST:nombre. */
        void open(String what);
    }

    private final Lucent th;
    private final Host host;
    private final LinearLayout col;
    private String key = "";

    HomeView(Context c, Lucent th, Host host) {
        super(c);
        this.th = th;
        this.host = host;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, dp(4), 0, dp(110));
        addView(col, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(getContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private TextView section(String s) {
        TextView t = text(s, 10, th.ink2, th.mono);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(2), dp(18), 0, dp(8));
        return t;
    }

    /** Reconstruye solo si cambió algo que se ve aquí. */
    void refresh() {
        Player p = Player.I;
        StringBuilder k = new StringBuilder();
        k.append(p.tracks.size()).append('|').append(Playlists.I.version).append('|').append(p.index());
        List<Integer> rec = recents(p);
        for (int i = 0; i < Math.min(3, rec.size()); i++) {
            k.append('|').append(rec.get(i));
        }
        String nk = k.toString();
        if (nk.equals(key)) {
            return;
        }
        key = nk;
        col.removeAllViews();

        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        DotTextView hi = new DotTextView(getContext(), 2.4f, th);
        hi.setText(h < 6 ? "BUENAS NOCHES" : h < 13 ? "BUENOS DIAS" : h < 21 ? "BUENAS TARDES" : "BUENAS NOCHES");
        hi.setPadding(dp(2), dp(4), 0, dp(2));
        col.addView(hi);

        col.addView(section("ESCUCHADO RECIENTEMENTE"));
        if (rec.isEmpty()) {
            TextView none = text("Reproduce algo y aparecerá aquí.", 13, th.ink2, th.regular);
            none.setPadding(dp(2), 0, 0, 0);
            col.addView(none);
        } else {
            LinearLayout row = hrow();
            final List<Integer> top = new ArrayList<Integer>(rec.subList(0, Math.min(12, rec.size())));
            for (int i = 0; i < top.size(); i++) {
                final int pos = i;
                Player.Track t = p.tracks.get(top.get(i));
                row.addView(card(t.display(), t.artist.length() > 0 ? t.artist : t.folder(),
                        top.get(i) == p.index(), new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                host.play(top, pos);
                            }
                        }));
            }
        }

        col.addView(section("TUS LISTAS"));
        LinearLayout lists = hrow();
        lists.addView(card("FAVORITAS", favCount(p) + " CANCIONES", false, open("FAVS")));
        for (final String name : Playlists.I.names()) {
            lists.addView(card(name.toUpperCase(java.util.Locale.ROOT),
                    Playlists.I.size(name) + " CANCIONES", false, open("LIST:" + name)));
        }

        col.addView(section("EXPLORAR"));
        int missing = Imports.missing(getContext(), p.tracks).size();
        String[][] tiles = {
            {"CANCIONES", String.valueOf(p.tracks.size()), "ALL"},
            {"ALEATORIO", "TODA LA BIBLIOTECA", "SHUFFLE"},
            {"MÁS ESCUCHADAS", "", "TOP"},
            {"RECIENTES", "", "RECENT"},
            {"CARPETAS", "", "FOLDERS"},
            {"PENDIENTES", missing > 0 ? String.valueOf(missing) : "AL DÍA", "MISSING"},
            {"ESTADÍSTICAS", "WRAPPED", "STATS"},
            {"AJUSTES", "", "SETTINGS"}};
        String[][] icons = {{"QUEUE"}, {"SHUFFLE"}, {"PITCH"}, {"DISC"}, {"FOLDER"}, {"DOWN"}, {"PITCH"}, {"MENU"}};
        for (int r = 0; r < tiles.length; r += 2) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 2; c++) {
                int i = r + c;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                lp.setMargins(c == 0 ? 0 : dp(5), dp(5), c == 0 ? dp(5) : 0, 0);
                row.addView(tile(icon(icons[i][0]), tiles[i][0], tiles[i][1], open(tiles[i][2])), lp);
            }
            col.addView(row);
        }
    }

    private static String[] icon(String n) {
        if ("QUEUE".equals(n)) {
            return DotIcons.QUEUE;
        } else if ("SHUFFLE".equals(n)) {
            return DotIcons.SHUFFLE;
        } else if ("PITCH".equals(n)) {
            return DotIcons.PITCH;
        } else if ("DISC".equals(n)) {
            return DotIcons.DISC;
        } else if ("FOLDER".equals(n)) {
            return DotIcons.FOLDER;
        }
        return DotIcons.DOWN;
    }

    private View.OnClickListener open(final String what) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                host.open(what);
            }
        };
    }

    private int favCount(Player p) {
        int n = 0;
        for (int i = 0; i < p.tracks.size(); i++) {
            if (p.isFav(p.tracks.get(i).uri)) {
                n++;
            }
        }
        return n;
    }

    /** Canciones escuchadas, de la más reciente a la más antigua. */
    private List<Integer> recents(final Player p) {
        ArrayList<Integer> all = new ArrayList<Integer>();
        for (int i = 0; i < p.tracks.size(); i++) {
            if (Meta.last(p.tracks.get(i).uri.toString()) > 0) {
                all.add(i);
            }
        }
        Collections.sort(all, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                long x = Meta.last(p.tracks.get(a).uri.toString());
                long y = Meta.last(p.tracks.get(b).uri.toString());
                return x == y ? 0 : (x < y ? 1 : -1);
            }
        });
        return all;
    }

    private LinearLayout hrow() {
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(row);
        col.addView(hs);
        return row;
    }

    private View card(String title, String sub, boolean current, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        c.setBackground(th.card(Lucent.R_S));
        TextView t = text(current ? "●  " + title : title, 13, th.ink, current ? th.semi : th.regular);
        t.setMaxLines(3);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setMinLines(2);
        c.addView(t);
        TextView s = text(sub, 10, th.ink2, th.mono);
        s.setSingleLine(true);
        s.setEllipsize(TextUtils.TruncateAt.END);
        s.setPadding(0, dp(6), 0, 0);
        c.addView(s);
        c.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(140), dp(104));
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View tile(String[] pattern, String title, String sub, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(getContext());
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(10), 0, dp(10), 0);
        c.setBackground(th.card(Lucent.R_S));
        DotIconView ic = new DotIconView(getContext(), th);
        ic.set(pattern, th.accent);
        c.addView(ic, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout tx = new LinearLayout(getContext());
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setPadding(dp(10), 0, 0, 0);
        TextView t = text(title, 12, th.ink, th.semi);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(t);
        if (sub.length() > 0) {
            TextView s = text(sub, 10, th.ink2, th.mono);
            s.setSingleLine(true);
            s.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(s);
        }
        c.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c.setOnClickListener(click);
        return c;
    }
}
