package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Estadísticas de escucha: tiempo, reproducciones, rankings de canciones, artistas y álbumes, horas del día. */
public class StatsActivity extends Activity {
    static final String[] PERIODS = {"7 DÍAS", "30 DÍAS", "AÑO", "TODO"};

    private Lucent th;
    private Stats stats;
    private int period = 1;
    private int kind = Stats.TRACK;
    private LinearLayout body;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        period = Lucent.prefs(this).getInt("stats_period", 1);
        final Map<String, String[]> tags = new HashMap<String, String[]>();
        for (Player.Track t : Player.I.tracks) {
            tags.put(t.uri.toString(), new String[]{t.display(), t.artist, t.album});
        }
        stats = new Stats(PlayLog.all(), new Stats.Info() {
            @Override
            public String[] tags(String uri) {
                return tags.get(uri);
            }
        }, TimeZone.getDefault());
        setContentView(buildUi());
        fill();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
        }
    }

    private int dp(float v) {
        return th.dp(v);
    }

    static long[] range(int period) {
        long now = System.currentTimeMillis();
        long from;
        if (period == 0) {
            from = now - 7L * 86400000L;
        } else if (period == 1) {
            from = now - 30L * 86400000L;
        } else if (period == 2) {
            Calendar c = Calendar.getInstance();
            c.set(c.get(Calendar.YEAR), Calendar.JANUARY, 1, 0, 0, 0);
            c.set(Calendar.MILLISECOND, 0);
            from = c.getTimeInMillis();
        } else {
            from = 0;
        }
        return new long[]{from, now + 1};
    }

    private TextView text(String s, float sp, int color, android.graphics.Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s, 10, th.ink2, th.mono);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(th.card(Lucent.R_L));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        c.setLayoutParams(lp);
        return c;
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
        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText("ESTADISTICAS");
        title.setPadding(dp(6), 0, 0, 0);
        header.addView(title);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(24));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void fill() {
        body.removeAllViews();
        long[] r = range(period);
        final Stats.Summary s = stats.summary(r[0], r[1]);

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(4), 0, 0);
        for (int i = 0; i < PERIODS.length; i++) {
            final int p = i;
            chips.addView(chip(PERIODS[i], i == period, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    period = p;
                    Lucent.prefs(StatsActivity.this).edit().putInt("stats_period", p).apply();
                    fill();
                }
            }));
        }
        body.addView(chips);

        if (s.plays == 0 && s.secs == 0) {
            LinearLayout e = card();
            e.addView(label("SIN ESCUCHAS"));
            TextView m = text("Aún no hay historial en este periodo. Reproduce canciones (se cuentan a partir de "
                    + "unos segundos) y aquí verás tu tiempo, tus favoritas y tu resumen.", 13, th.ink2, th.regular);
            m.setPadding(0, dp(8), 0, 0);
            e.addView(m);
            body.addView(e);
            return;
        }

        LinearLayout big = card();
        big.addView(label("TIEMPO ESCUCHADO · " + PERIODS[period]));
        DotTextView t = new DotTextView(this, 5f, th);
        t.setText(bigTime(s.secs));
        t.setPadding(0, dp(10), 0, dp(2));
        big.addView(t);
        body.addView(big);

        LinearLayout row1 = tilesRow(tile("REPRODUCCIONES", String.valueOf(s.plays)),
                tile("CANCIONES", String.valueOf(s.tracks)));
        body.addView(row1);
        body.addView(tilesRow(tile("ARTISTAS", String.valueOf(s.artists)),
                tile("RACHA", s.bestStreak + " D")));

        // rankings
        final LinearLayout rank = card();
        LinearLayout tabs = new LinearLayout(this);
        String[] names = {"CANCIONES", "ARTISTAS", "ÁLBUMES"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            tabs.addView(chip(names[i], i == kind, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    kind = k;
                    fill();
                }
            }));
        }
        rank.addView(tabs);
        List<Stats.Row> rows = stats.top(kind, r[0], r[1], 10);
        long max = rows.isEmpty() ? 1 : Math.max(1, rows.get(0).secs);
        if (rows.isEmpty()) {
            TextView m = text(kind == Stats.TRACK ? "Sin datos." : "Tus archivos no tienen "
                    + (kind == Stats.ARTIST ? "artista" : "álbum") + " en las etiquetas.", 13, th.ink2, th.regular);
            m.setPadding(0, dp(12), 0, 0);
            rank.addView(m);
        }
        for (int i = 0; i < rows.size(); i++) {
            rank.addView(rankRow(i + 1, rows.get(i), max));
        }
        body.addView(rank);

        // horas del día
        LinearLayout hours = card();
        hours.addView(label("HORAS DEL DÍA"));
        HourBars hb = new HourBars(this);
        hb.set(s.byHour);
        hours.addView(hb, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(90)));
        LinearLayout ax = new LinearLayout(this);
        String[] ticks = {"0", "6", "12", "18", "23"};
        for (String x : ticks) {
            TextView tv = text(x, 9, th.ink2, th.mono);
            tv.setGravity(Gravity.CENTER);
            ax.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        }
        hours.addView(ax);
        if (s.favHour >= 0) {
            TextView m = text("Escuchas más a las " + s.favHour + ":00"
                    + (s.favWeekday >= 0 ? " y los " + weekday(s.favWeekday) : "") + ".", 12, th.ink2, th.regular);
            m.setPadding(0, dp(8), 0, 0);
            hours.addView(m);
        }
        body.addView(hours);

        TextView wr = text("VER WRAPPED", 13, th.onAccent, th.mono);
        wr.setGravity(Gravity.CENTER);
        wr.setLetterSpacing(0.1f);
        wr.setBackground(accentPill());
        wr.setPadding(0, dp(16), 0, dp(16));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wl.topMargin = dp(14);
        body.addView(wr, wl);
        wr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(StatsActivity.this, WrappedActivity.class);
                i.putExtra("period", period);
                startActivity(i);
            }
        });
    }

    static String weekday(int i) {
        String[] d = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábados", "domingos"};
        return d[Math.max(0, Math.min(6, i))].replace("lunes", "lunes").replace("martes", "martes");
    }

    static String bigTime(long secs) {
        long m = secs / 60;
        if (m < 60) {
            return m + "M";
        }
        long h = m / 60;
        if (h < 100) {
            return h + "H " + (m % 60) + "M";
        }
        return h + "H";
    }

    private android.graphics.drawable.GradientDrawable accentPill() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(th.accent);
        g.setCornerRadius(dp(Lucent.R_L));
        return g;
    }

    private TextView chip(String s, boolean on, View.OnClickListener l) {
        TextView t = text(s, 11, on ? th.onAccent : th.ink, th.mono);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(Lucent.R_M));
        if (on) {
            g.setColor(th.accent);
        } else {
            g.setColor(th.glass);
            g.setStroke(dp(1), th.glassLine);
        }
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private LinearLayout tile(String name, String value) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(th.card(Lucent.R_L));
        DotTextView v = new DotTextView(this, 3.6f, th);
        v.setText(value);
        c.addView(v);
        TextView l = label(name);
        l.setPadding(0, dp(8), 0, 0);
        c.addView(l);
        return c;
    }

    private LinearLayout tilesRow(LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        la.rightMargin = dp(5);
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lb.leftMargin = dp(5);
        row.addView(a, la);
        row.addView(b, lb);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(10);
        row.setLayoutParams(rp);
        return row;
    }

    private View rankRow(int n, Stats.Row r, long max) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(0, dp(12), 0, 0);
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        TextView num = text(String.format(java.util.Locale.US, "%02d", n), 12, n == 1 ? th.accent : th.ink2, th.mono);
        num.setMinWidth(dp(28));
        line.addView(num);
        LinearLayout tx = new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(r.name, 14, th.ink, th.medium);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        tx.addView(name);
        if (r.sub.length() > 0) {
            TextView sub = text(r.sub, 11, th.ink2, th.regular);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            tx.addView(sub);
        }
        line.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView val = text(Stats.duration(r.secs) + (r.plays > 0 ? " · " + r.plays + "×" : ""), 11, th.ink2, th.mono);
        val.setPadding(dp(8), 0, 0, 0);
        line.addView(val);
        c.addView(line);
        DotBar bar = new DotBar(this);
        bar.set((double) r.secs / max);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
        bp.topMargin = dp(6);
        bp.leftMargin = dp(28);
        c.addView(bar, bp);
        return c;
    }

    /** Barra de puntos: llenos en acento, vacíos en gris. */
    private final class DotBar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private double frac;

        DotBar(Activity a) {
            super(a);
        }

        void set(double f) {
            frac = Math.max(0, Math.min(1, f));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float step = dp(9);
            int n = Math.max(1, (int) (getWidth() / step));
            int filled = (int) Math.round(frac * n);
            float r = dp(2);
            for (int i = 0; i < n; i++) {
                p.setColor(i < filled ? th.accent : th.ghost);
                c.drawCircle(i * step + r, getHeight() / 2f, r, p);
            }
        }
    }

    /** Histograma de 24 horas con columnas de puntos. */
    private final class HourBars extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private long[] v = new long[24];

        HourBars(Activity a) {
            super(a);
        }

        void set(long[] values) {
            v = values;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            long max = 1;
            for (long x : v) {
                max = Math.max(max, x);
            }
            int rows = 8;
            float colW = getWidth() / 24f;
            float r = Math.min(colW * 0.28f, dp(3));
            float rowH = (getHeight() - r) / rows;
            for (int h = 0; h < 24; h++) {
                int lit = (int) Math.round(8.0 * v[h] / max);
                if (v[h] > 0 && lit == 0) {
                    lit = 1;
                }
                for (int k = 0; k < rows; k++) {
                    boolean on = k < lit;
                    p.setColor(on ? th.accent : th.ghost);
                    c.drawCircle(h * colW + colW / 2f, getHeight() - r - k * rowH, r, p);
                }
            }
        }
    }
}
