package dev.scratchwheel;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Resumen tipo "Wrapped": páginas a pantalla completa, toca izquierda/derecha para avanzar. */
public class WrappedActivity extends Activity {
    private Lucent th;
    private final List<View> pages = new ArrayList<View>();
    private int cur;
    private FrameLayout stage;
    private ProgressDots dots;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        int period = getIntent().getIntExtra("period", 1);
        final Map<String, String[]> tags = new HashMap<String, String[]>();
        for (Player.Track t : Player.I.tracks) {
            tags.put(t.uri.toString(), new String[]{t.display(), t.artist, t.album});
        }
        Stats st = new Stats(PlayLog.all(), new Stats.Info() {
            @Override
            public String[] tags(String uri) {
                return tags.get(uri);
            }
        }, TimeZone.getDefault());
        long[] r = StatsActivity.range(period);
        build(st, st.summary(r[0], r[1]), st.top(Stats.TRACK, r[0], r[1], 5),
                st.top(Stats.ARTIST, r[0], r[1], 1), st.top(Stats.ALBUM, r[0], r[1], 1),
                StatsActivity.PERIODS[period]);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        stage = new FrameLayout(this);
        root.addView(stage);
        dots = new ProgressDots(this);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, th.dp(14), Gravity.TOP);
        dp.topMargin = th.dp(30);
        dp.leftMargin = dp.rightMargin = th.dp(20);
        root.addView(dots, dp);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP) {
                    if (e.getX() < v.getWidth() / 3f) {
                        show(cur - 1);
                    } else {
                        show(cur + 1);
                    }
                }
                return true;
            }
        });
        setContentView(root);
        show(0);
    }

    private void show(int i) {
        if (i >= pages.size()) {
            finish();
            return;
        }
        cur = Math.max(0, i);
        stage.removeAllViews();
        stage.addView(pages.get(cur));
        dots.set(cur, pages.size());
    }

    private TextView tv(String s, float sp, int color, android.graphics.Typeface f) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(f);
        return t;
    }

    private LinearLayout page(String tag) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(th.dp(28), th.dp(70), th.dp(28), th.dp(40));
        TextView t = tv(tag, 11, 0xFF8A8A8F, th.mono);
        t.setLetterSpacing(0.14f);
        p.addView(t);
        return p;
    }

    private void bigDot(LinearLayout p, String s, float cell, int color) {
        DotTextView d = new DotTextView(this, cell, th);
        d.setText(s);
        d.setColors(color, 0xFF2A2A2E, th.accent);
        d.setPadding(0, th.dp(14), 0, th.dp(10));
        p.addView(d);
    }

    private void line(LinearLayout p, String s, float sp, int color) {
        TextView t = tv(s, sp, color, th.medium);
        t.setPadding(0, th.dp(6), 0, 0);
        p.addView(t);
    }

    private void build(Stats st, Stats.Summary s, List<Stats.Row> tracks, List<Stats.Row> art,
                       List<Stats.Row> alb, String per) {
        LinearLayout p = page("SCRATCH · WRAPPED");
        bigDot(p, "TU MUSICA", 4.4f, th.accent);
        line(p, per, 22, Color.WHITE);
        line(p, "Toca para avanzar", 13, 0xFF8A8A8F);
        pages.add(p);

        if (s.plays == 0 && s.secs == 0) {
            LinearLayout e = page("SIN DATOS");
            line(e, "Aún no has escuchado nada en este periodo. Vuelve cuando hayas puesto música.", 17, Color.WHITE);
            pages.add(e);
            return;
        }

        p = page("HAS ESCUCHADO");
        bigDot(p, StatsActivity.bigTime(s.secs), 5f, th.accent);
        line(p, s.plays + " reproducciones · " + s.tracks + " canciones", 16, Color.WHITE);
        line(p, s.activeDays + " días con música", 13, 0xFF8A8A8F);
        pages.add(p);

        if (!art.isEmpty()) {
            p = page("ARTISTA Nº 1");
            bigDot(p, "01", 6f, th.accent);
            line(p, art.get(0).name, 28, Color.WHITE);
            line(p, Stats.duration(art.get(0).secs) + " · " + art.get(0).plays + " reproducciones", 14, 0xFF8A8A8F);
            pages.add(p);
        }
        if (!tracks.isEmpty()) {
            p = page("CANCIÓN Nº 1");
            bigDot(p, "01", 6f, th.accent);
            line(p, tracks.get(0).name, 28, Color.WHITE);
            if (tracks.get(0).sub.length() > 0) {
                line(p, tracks.get(0).sub, 15, 0xFFB5B5BA);
            }
            line(p, tracks.get(0).plays + " reproducciones · " + Stats.duration(tracks.get(0).secs), 14, 0xFF8A8A8F);
            pages.add(p);
        }
        if (!alb.isEmpty()) {
            p = page("ÁLBUM Nº 1");
            bigDot(p, "01", 6f, th.accent);
            line(p, alb.get(0).name, 28, Color.WHITE);
            if (alb.get(0).sub.length() > 0) {
                line(p, alb.get(0).sub, 15, 0xFFB5B5BA);
            }
            line(p, Stats.duration(alb.get(0).secs), 14, 0xFF8A8A8F);
            pages.add(p);
        }
        if (tracks.size() > 1) {
            p = page("TOP " + tracks.size());
            for (int i = 0; i < tracks.size(); i++) {
                LinearLayout row = new LinearLayout(this);
                row.setPadding(0, th.dp(14), 0, 0);
                TextView n = tv(String.format(java.util.Locale.US, "%02d", i + 1), 13,
                        i == 0 ? th.accent : 0xFF8A8A8F, th.mono);
                n.setMinWidth(th.dp(34));
                row.addView(n);
                TextView name = tv(tracks.get(i).name, 16, Color.WHITE, th.medium);
                name.setSingleLine(true);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);
                row.addView(name);
                p.addView(row);
            }
            pages.add(p);
        }
        if (s.favHour >= 0) {
            p = page("TU HORA");
            bigDot(p, s.favHour + ":00", 5f, th.accent);
            line(p, "Es cuando más escuchas música.", 16, Color.WHITE);
            Hist h = new Hist(this, s.byHour);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, th.dp(110));
            lp.topMargin = th.dp(24);
            p.addView(h, lp);
            pages.add(p);
        }
        p = page("CONSTANCIA");
        bigDot(p, s.bestStreak + " D", 5f, th.accent);
        line(p, "Tu racha más larga seguida escuchando.", 16, Color.WHITE);
        if (s.discoveries > 0) {
            line(p, s.discoveries + " canciones descubiertas en este periodo.", 14, 0xFF8A8A8F);
        }
        pages.add(p);
    }

    private final class ProgressDots extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int a, n = 1;

        ProgressDots(Activity c) {
            super(c);
        }

        void set(int cur, int total) {
            a = cur;
            n = Math.max(1, total);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float step = getWidth() / (float) n;
            float r = th.dp(3);
            for (int i = 0; i < n; i++) {
                p.setColor(i <= a ? th.accent : 0xFF3A3A3E);
                c.drawCircle(step * i + step / 2f, getHeight() / 2f, r, p);
            }
        }
    }

    private final class Hist extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long[] v;

        Hist(Activity c, long[] v) {
            super(c);
            this.v = v;
        }

        @Override
        protected void onDraw(Canvas c) {
            long max = 1;
            for (long x : v) {
                max = Math.max(max, x);
            }
            float colW = getWidth() / 24f;
            float r = Math.min(colW * 0.3f, th.dp(3));
            int rows = 9;
            float rowH = (getHeight() - r) / rows;
            for (int h = 0; h < 24; h++) {
                int lit = (int) Math.round((double) rows * v[h] / max);
                if (v[h] > 0 && lit == 0) {
                    lit = 1;
                }
                for (int k = 0; k < rows; k++) {
                    p.setColor(k < lit ? th.accent : 0xFF2A2A2E);
                    c.drawCircle(h * colW + colW / 2f, getHeight() - r - k * rowH, r, p);
                }
            }
        }
    }
}
