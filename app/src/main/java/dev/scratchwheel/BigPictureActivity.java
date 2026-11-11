package dev.scratchwheel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Big Picture: pantalla de módulos sueltos (sin cajas) colocados en una rejilla. Qué módulos salen, su tamaño y su
 * orden se eligen en Ajustes (BigLayout). Hay info, portada, scratch, onda, espectro, controles, volumen, pitch y
 * ajustes rápidos.
 */
public class BigPictureActivity extends Activity {
    private Lucent th;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<Part> parts = new ArrayList<Part>();
    private AudioManager audio;

    private abstract class Part {
        View view;

        /** Cada cuadro de animación. */
        void tick() {
        }

        /** Cuando cambia la canción o el estado del reproductor. */
        void changed() {
        }
    }

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onChanged() {
            for (Part p : parts) {
                p.changed();
            }
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            for (Part p : parts) {
                p.tick();
            }
            ScratchEngine e = Player.I.engine;
            boolean busy = Player.I.isPlayingOrPending() || e.isScratching() || Math.abs(e.getRate()) > 0.001;
            if (anim != null && busy) {
                anim.postOnAnimation(this);
            } else {
                ui.postDelayed(this, 200);
            }
        }
    };
    private View anim;

    private int dp(float v) {
        return th.dp(v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
            return;
        }
        // por si cambió la configuración en Ajustes
        setContentView(buildUi());
        Player.I.addListener(listener);
        for (Part p : parts) {
            p.changed();
        }
        ui.post(ticker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Player.I.removeListener(listener);
        ui.removeCallbacks(ticker);
        if (anim != null) {
            anim.removeCallbacks(ticker);
        }
        Player.I.touchUp();
        Player.I.savePos();
    }

    private TextView text(String s, float sp, int color, Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private View buildUi() {
        parts.clear();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(10), dp(8), dp(10), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        DotIconView close = new DotIconView(this, th);
        close.set(DotIcons.DOWN, th.ink);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("BIG PICTURE", 11, th.accent, th.mono);
        title.setLetterSpacing(0.12f);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final DotIconView menu = new DotIconView(this, th);
        menu.set(DotIcons.MENU, th.ink);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DotMenu.show(BigPictureActivity.this, th, menu,
                        new String[] {Tr.t("PERSONALIZAR MÓDULOS"), Tr.t("VOLVER A LA RUEDA")},
                        new DotMenu.Picked() {
                            @Override
                            public void onPick(int id) {
                                if (id == 0) {
                                    startActivity(new Intent(BigPictureActivity.this, BigEditorActivity.class));
                                } else {
                                    finish();
                                }
                            }
                        });
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        List<BigLayout.Item> items = BigLayout.parse(Lucent.prefs(this).getString("bp_cfg", null));
        List<BigLayout.Placed> placed = BigLayout.pack(items, BigLayout.COLS);
        int rows = BigLayout.rows(placed);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int cell = Math.min((dm.widthPixels - dp(20)) / BigLayout.COLS, (dm.heightPixels - dp(120)) / 5);
        cell = Math.max(cell, dp(60));
        FrameLayout grid = new FrameLayout(this);
        for (BigLayout.Placed p : placed) {
            Part part = makePart(p.item.id, p.w, p.h, cell);
            if (part == null) {
                continue;
            }
            parts.add(part);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(p.w * cell, p.h * cell,
                    Gravity.TOP | Gravity.START);
            lp.leftMargin = p.col * cell;
            lp.topMargin = p.row * cell;
            part.view.setPadding(dp(6), dp(6), dp(6), dp(6));
            grid.addView(part.view, lp);
        }
        if (parts.isEmpty()) {
            TextView none = text(Tr.t("No hay módulos activos. Actívalos en Ajustes → BIG PICTURE."), 13, th.ink2,
                    th.regular);
            none.setGravity(Gravity.CENTER);
            none.setPadding(dp(20), dp(60), dp(20), 0);
            root.addView(none);
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER_HORIZONTAL);
        holder.addView(grid, new LinearLayout.LayoutParams(BigLayout.COLS * cell, Math.max(rows, 1) * cell));
        sv.addView(holder, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        anim = grid;
        return root;
    }

    private Part makePart(String id, int w, int h, int cell) {
        if ("info".equals(id)) {
            return infoPart(w, h);
        } else if ("cover".equals(id)) {
            return coverPart();
        } else if ("wheel".equals(id)) {
            return wheelPart();
        } else if ("wave".equals(id)) {
            return wavePart(h);
        } else if ("spectrum".equals(id)) {
            return spectrumPart(w);
        } else if ("transport".equals(id)) {
            return transportPart(w);
        } else if ("volume".equals(id)) {
            return volumePart();
        } else if ("pitch".equals(id)) {
            return pitchPart(w);
        } else if ("basic".equals(id)) {
            return basicPart(h);
        } else if ("lyrics".equals(id)) {
            return lyricsPart(h);
        }
        return null;
    }

    private static String fmt(int s) {
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ info

    private final class InfoPart extends Part {
        TextView name;
        TextView sub;
        TextView time;
        DotTextView big;
        TextView dur;
        int lastSec = -1;
        int lastDur = -1;

        @Override
        void changed() {
            Player p = Player.I;
            String t = p.isLoading() ? Tr.t("CARGANDO…") : p.title();
            name.setText(t.toUpperCase(Locale.ROOT));
            if (sub != null) {
                String a = "";
                if (p.index() >= 0 && p.index() < p.tracks.size()) {
                    Player.Track tr = p.tracks.get(p.index());
                    a = tr.artist + (tr.album.length() > 0 && tr.artist.length() > 0 ? "  \u00b7  " : "") + tr.album;
                }
                sub.setText(a);
            }
            lastSec = -1;
        }

        @Override
        void tick() {
            ScratchEngine e = Player.I.engine;
            int sec = (int) e.getPositionSeconds();
            int d = (int) e.getDurationSeconds();
            if (sec == lastSec && d == lastDur) {
                return;
            }
            lastSec = sec;
            lastDur = d;
            if (big != null) {
                big.setText(fmt(sec));
                dur.setText("/ " + fmt(d));
            } else {
                time.setText(fmt(sec) + " / " + fmt(d));
            }
        }
    }

    private Part infoPart(final int w, final int h) {
        InfoPart part = new InfoPart();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        part.name = text("", h >= 2 ? 19 : 16, th.ink, th.semi);
        part.name.setMaxLines(h >= 2 ? 2 : 1);
        part.name.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(part.name);
        if (h >= 2) {
            part.sub = text("", 12, th.ink2, th.mono);
            part.sub.setSingleLine(true);
            part.sub.setEllipsize(TextUtils.TruncateAt.END);
            part.sub.setPadding(0, dp(2), 0, 0);
            box.addView(part.sub);
            LinearLayout tr = new LinearLayout(this);
            tr.setOrientation(LinearLayout.HORIZONTAL);
            tr.setGravity(Gravity.BOTTOM);
            tr.setPadding(0, dp(10), 0, 0);
            part.big = new DotTextView(this, w >= 4 ? 4.2f : 3.0f, th);
            part.big.setText("00:00");
            tr.addView(part.big);
            part.dur = text("/ 00:00", 12, th.ink2, th.mono);
            part.dur.setPadding(dp(8), 0, 0, 0);
            tr.addView(part.dur);
            box.addView(tr);
        } else {
            part.time = text("00:00 / 00:00", 12, th.ink2, th.mono);
            part.time.setPadding(0, dp(2), 0, 0);
            box.addView(part.time);
        }
        part.view = box;
        return part;
    }

    // ------------------------------------------------------------------ portada

    private Part coverPart() {
        final ImageView iv = CoverCache.view(this, 18);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Part part = new Part() {
            String key = "";

            @Override
            void tick() {
                android.net.Uri u = Player.I.currentUri();
                String k = u == null ? "" : u.toString();
                if (!k.equals(key)) {
                    key = k;
                    CoverCache.into(BigPictureActivity.this, iv, u);
                }
            }
        };
        part.view = iv;
        return part;
    }

    // ------------------------------------------------------------------ scratch

    private Part wheelPart() {
        final android.content.SharedPreferences sp = Lucent.prefs(this);
        final JogWheelView wheel = new JogWheelView(this, th);
        wheel.setListener(new JogWheelView.Listener() {
            @Override
            public void onTouchDown() {
                Player.I.touchDown();
            }

            @Override
            public void onRotate(double radians) {
                Player.I.rotate(radians);
            }

            @Override
            public void onTouchUp() {
                Player.I.touchUp();
            }

            @Override
            public void onHubTap() {
                Player.I.toggle();
            }
        });
        wheel.setHaptic(sp.getBoolean("haptic", true));
        Part part = new Part() {
            String coverKey = "";

            @Override
            void changed() {
                wheel.setPlayingState(Player.I.isPlayingOrPending());
            }

            @Override
            void tick() {
                wheel.setCoverOn(sp.getBoolean("cover", false));
                wheel.setLocked(sp.getBoolean("wlock", false));
                wheel.setVinylSkin(sp.getInt("vskin", 0));
                wheel.setWheelStyle(sp.getInt("wstyle", 0));
                final android.net.Uri u = Player.I.currentUri();
                String k = u == null ? "" : u.toString();
                if (!k.equals(coverKey)) {
                    coverKey = k;
                    wheel.setCover(null);
                    if (u != null) {
                        final String fk = k;
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                final Bitmap b = Covers.embedded(getApplicationContext(), u);
                                wheel.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (fk.equals(coverKey)) {
                                            wheel.setCover(b);
                                        }
                                    }
                                });
                            }
                        }, "bpcover").start();
                    }
                }
                ScratchEngine e = Player.I.engine;
                wheel.setAngle((float) (e.getPositionSeconds() / e.getSecondsPerRev() * 2 * Math.PI
                        * (e.isInverted() ? -1 : 1)));
                wheel.setPlayingState(Player.I.isPlayingOrPending());
            }
        };
        part.view = wheel;
        return part;
    }

    // ------------------------------------------------------------------ onda

    private final class WaveView extends View {
        private float[] peaks = new float[0];
        private String key = "";
        private float frac;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean computing;

        WaveView(Context c) {
            super(c);
            on.setColor(th.ink);
            off.setColor(Lucent.withAlpha(th.ink, 0.28f));
            head.setColor(th.accent);
            on.setStrokeCap(Paint.Cap.ROUND);
            off.setStrokeCap(Paint.Cap.ROUND);
            on.setStrokeWidth(dp(2.5f));
            off.setStrokeWidth(dp(2.5f));
            head.setStrokeWidth(dp(2.5f));
        }

        void update() {
            ScratchEngine.Snap s = Player.I.engine.snap();
            double dur = Player.I.engine.getDurationSeconds();
            frac = dur > 0 ? (float) Math.max(0, Math.min(1, Player.I.engine.getPositionSeconds() / dur)) : 0f;
            String k = System.identityHashCode(s.peaks != null ? (Object) s.peaks : (Object) s.data) + ":" + s.frames
                    + ":" + s.partial;
            if (!k.equals(key) && s.peaks != null) {
                // pista en disco: la forma de onda ya viene calculada (o de la caché)
                peaks = s.peaks;
                key = k;
            } else if (!k.equals(key) && !computing && s.frames > 0 && s.have >= s.frames) {
                computing = true;
                final ScratchEngine.Snap fs = s;
                final String fk = k;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final float[] pk = Waveform.peaks(fs.data, fs.frames, 480);
                        post(new Runnable() {
                            @Override
                            public void run() {
                                peaks = pk;
                                key = fk;
                                computing = false;
                                invalidate();
                            }
                        });
                    }
                }, "wave").start();
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            float h = getHeight() - getPaddingTop() - getPaddingBottom();
            float cy = getPaddingTop() + h / 2f;
            float step = dp(5);
            int n = Math.max(1, (int) (w / step));
            for (int i = 0; i < n; i++) {
                float x = getPaddingLeft() + (i + 0.5f) * (w / n);
                float amp = 0.04f;
                if (peaks.length > 0) {
                    int a = i * peaks.length / n;
                    int b = Math.max(a + 1, (i + 1) * peaks.length / n);
                    float m = 0;
                    for (int j = a; j < b && j < peaks.length; j++) {
                        m = Math.max(m, peaks[j]);
                    }
                    amp = Math.max(0.04f, (float) Math.sqrt(m));
                }
                float half = amp * h / 2f;
                c.drawLine(x, cy - half, x, cy + half, (i + 0.5f) / n <= frac ? on : off);
            }
            float hx = getPaddingLeft() + frac * w;
            c.drawLine(hx, getPaddingTop(), hx, getPaddingTop() + h, head);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                float f = (e.getX() - getPaddingLeft()) / Math.max(1f, w);
                Player.I.seekFraction(Math.max(0f, Math.min(1f, f)));
                frac = Math.max(0f, Math.min(1f, f));
                invalidate();
            } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                Player.I.savePos();
            }
            return true;
        }
    }

    private Part wavePart(int h) {
        final WaveView v = new WaveView(this);
        Part part = new Part() {
            @Override
            void tick() {
                v.update();
            }
        };
        part.view = v;
        return part;
    }

    // ------------------------------------------------------------------ espectro

    private final class SpectrumView extends View {
        private final float[] lv;
        private final float[] shown;
        private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);

        SpectrumView(Context c, int bands) {
            super(c);
            lv = new float[bands];
            shown = new float[bands];
            on.setColor(th.ink);
            off.setColor(th.ghost);
            top.setColor(th.accent);
        }

        void update() {
            ScratchEngine e = Player.I.engine;
            boolean active = Player.I.isPlayingOrPending() || e.isScratching() || Math.abs(e.getRate()) > 0.02;
            if (active) {
                ScratchEngine.Snap s = e.snap();
                Waveform.spectrum(s.data, s.have, s.pos - s.base, s.rate, lv);
                float gain = (float) Math.min(1.0, Math.abs(e.getRate()) * 4.0);
                for (int i = 0; i < lv.length; i++) {
                    lv[i] *= gain;
                    shown[i] = lv[i] > shown[i] ? lv[i] : shown[i] * 0.86f;
                }
            } else {
                for (int i = 0; i < shown.length; i++) {
                    shown[i] *= 0.8f;
                }
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth() - getPaddingLeft() - getPaddingRight();
            float h = getHeight() - getPaddingTop() - getPaddingBottom();
            int n = shown.length;
            float gap = w / n;
            float r = Math.max(dp(1.4f), Math.min(gap * 0.32f, dp(3.2f)));
            int rowsN = Math.max(4, (int) (h / (r * 2.9f)));
            float rowGap = h / rowsN;
            for (int i = 0; i < n; i++) {
                float x = getPaddingLeft() + (i + 0.5f) * gap;
                int lit = Math.round(shown[i] * rowsN);
                for (int k = 0; k < rowsN; k++) {
                    float y = getPaddingTop() + h - (k + 0.5f) * rowGap;
                    Paint p = k < lit ? (k == lit - 1 ? top : on) : off;
                    c.drawCircle(x, y, r, p);
                }
            }
        }
    }

    private Part spectrumPart(int w) {
        final SpectrumView v = new SpectrumView(this, w >= 4 ? 28 : 14);
        Part part = new Part() {
            @Override
            void tick() {
                v.update();
            }
        };
        part.view = v;
        return part;
    }

    // ------------------------------------------------------------------ controles

    private Part transportPart(int w) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        final DotIconView shuffle = new DotIconView(this, th);
        final DotIconView repeat = new DotIconView(this, th);
        final DotIconView play = new DotIconView(this, th).ghost(false);
        DotIconView prev = new DotIconView(this, th);
        DotIconView next = new DotIconView(this, th);
        prev.set(DotIcons.PREV, th.ink);
        next.set(DotIcons.NEXT, th.ink);
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.prev();
            }
        });
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.next();
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.toggle();
            }
        });
        shuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.setShuffle(!Player.I.isShuffle());
            }
        });
        repeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.cycleRepeat();
            }
        });
        boolean wide = w >= 4;
        if (wide) {
            row.addView(shuffle, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        row.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1f));
        row.addView(play, new LinearLayout.LayoutParams(0, dp(56), 1.2f));
        row.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1f));
        if (wide) {
            row.addView(repeat, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        Part part = new Part() {
            @Override
            void changed() {
                Player p = Player.I;
                play.set(p.isPlayingOrPending() ? DotIcons.PAUSE : DotIcons.PLAY, th.accent);
                shuffle.set(DotIcons.SHUFFLE, p.isShuffle() ? th.accent : th.ink);
                repeat.set(p.repeat() == 2 ? DotIcons.REPEAT_ONE : DotIcons.REPEAT, p.repeat() == 0 ? th.ink : th.accent);
            }
        };
        part.view = row;
        return part;
    }

    // ------------------------------------------------------------------ volumen y pitch

    private Part volumePart() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        DotIconView ic = new DotIconView(this, th);
        ic.set(DotIcons.VOL, th.ink2);
        row.addView(ic, new LinearLayout.LayoutParams(dp(36), dp(36)));
        final SeekBar bar = new SeekBar(this);
        th.styleSeek(bar);
        final int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        bar.setMax(max);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) {
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1f));
        Part part = new Part() {
            int last = -1;

            @Override
            void tick() {
                int v = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (v != last) {
                    last = v;
                    bar.setProgress(v);
                }
            }
        };
        part.view = row;
        return part;
    }

    private Part pitchPart(int w) {
        final android.content.SharedPreferences sp = Lucent.prefs(this);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        final DotIconView ic = new DotIconView(this, th);
        row.addView(ic, new LinearLayout.LayoutParams(dp(36), dp(36)));
        final SeekBar bar = new SeekBar(this);
        th.styleSeek(bar);
        bar.setMax(40);
        final TextView label = text("", 11, th.ink2, th.mono);
        label.setGravity(Gravity.END);
        label.setMinWidth(dp(w >= 4 ? 54 : 44));
        ic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().putBoolean("pitch_lock", !sp.getBoolean("pitch_lock", false)).apply();
            }
        });
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser && !sp.getBoolean("pitch_lock", false)) {
                    Player.I.setPitch(value == 20 ? 1.0 : 0.8 + value / 100.0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        row.addView(bar, new LinearLayout.LayoutParams(0, dp(40), 1f));
        row.addView(label);
        Part part = new Part() {
            double last = -1;
            boolean lastLock;

            @Override
            void tick() {
                boolean l = sp.getBoolean("pitch_lock", false);
                double pc = Player.I.pitch();
                if (l != lastLock || last < 0) {
                    lastLock = l;
                    bar.setEnabled(!l);
                    bar.setAlpha(l ? 0.45f : 1f);
                    ic.set(DotIcons.PITCH, l ? th.accent : th.ink2);
                }
                if (Math.abs(pc - last) > 1e-4) {
                    last = pc;
                    bar.setProgress((int) Math.round((pc - 0.8) * 100));
                    label.setText(String.format(Locale.US, "%+.1f%%", (pc - 1.0) * 100));
                }
            }
        };
        part.view = row;
        return part;
    }

    // ------------------------------------------------------------------ ajustes rápidos

    private Part basicPart(final int h) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        final android.content.SharedPreferences sp = Lucent.prefs(this);
        Part part = new Part() {
            String key = "";

            @Override
            void changed() {
                tick();
            }

            @Override
            void tick() {
                Player p = Player.I;
                String k = p.isShuffle() + "|" + p.repeat() + "|" + sp.getBoolean("wlock", false) + "|"
                        + sp.getBoolean("cover", false) + "|" + sp.getBoolean("pitch_lock", false);
                if (k.equals(key)) {
                    return;
                }
                key = k;
                box.removeAllViews();
                String[] labels = {Tr.t("ALEATORIO"), Tr.t("REPETIR"), Tr.t("RUEDA FIJA"), Tr.t("PORTADA"),
                    Tr.t("PITCH FIJO")};
                boolean[] on = {p.isShuffle(), p.repeat() != 0, sp.getBoolean("wlock", false),
                    sp.getBoolean("cover", false), sp.getBoolean("pitch_lock", false)};
                int per = h >= 2 ? 3 : 5;
                LinearLayout row = null;
                for (int i = 0; i < labels.length; i++) {
                    if (i % per == 0) {
                        row = new LinearLayout(BigPictureActivity.this);
                        row.setOrientation(LinearLayout.HORIZONTAL);
                        row.setGravity(Gravity.CENTER);
                        row.setPadding(0, dp(3), 0, dp(3));
                        box.addView(row);
                    }
                    final int which = i;
                    TextView chip = th.chip(BigPictureActivity.this, labels[i], on[i], h >= 2 ? 8 : 4);
                    chip.setTextSize(h >= 2 ? 11 : 9);
                    chip.setPadding(chip.getPaddingLeft(), dp(8), chip.getPaddingRight(), dp(8));
                    chip.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            toggleBasic(which);
                        }
                    });
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                            LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    lp.leftMargin = dp(2);
                    lp.rightMargin = dp(2);
                    row.addView(chip, lp);
                }
            }
        };
        part.view = box;
        return part;
    }

    // ------------------------------------------------------------------ letra

    private Part lyricsPart(final int h) {
        final int n = Math.max(1, h * 2 - (h > 1 ? 1 : 0));
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        final TextView[] tv = new TextView[n];
        for (int i = 0; i < n; i++) {
            tv[i] = text("", 14, th.ink2, th.semi);
            tv[i].setGravity(Gravity.CENTER);
            tv[i].setMaxLines(2);
            tv[i].setEllipsize(TextUtils.TruncateAt.END);
            col.addView(tv[i], new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }
        col.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(BigPictureActivity.this, LyricsActivity.class));
            }
        });
        Part part = new Part() {
            String key = "";
            LyricsReader.Result res;
            int shown = -3;

            @Override
            void tick() {
                android.net.Uri u = Player.I.currentUri();
                String k = u == null ? "" : u.toString();
                if (!k.equals(key)) {
                    key = k;
                    res = null;
                    shown = -3;
                    final android.net.Uri fu = u;
                    final String fk = k;
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            final LyricsReader.Result r = LyricsStore.load(getApplicationContext(), fu);
                            col.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (fk.equals(key)) {
                                        res = r;
                                        shown = -3;
                                    }
                                }
                            });
                        }
                    }, "bplyrics").start();
                }
                if (res == null) {
                    return;
                }
                List<LyricsReader.Line> ls = res.synced;
                int a = ls.isEmpty() ? -1
                        : LyricsReader.lineAt(ls, (long) (Player.I.engine.getPositionSeconds() * 1000));
                if (a == shown) {
                    return;
                }
                shown = a;
                if (ls.isEmpty()) {
                    for (int i = 0; i < n; i++) {
                        tv[i].setText("");
                    }
                    tv[n / 2].setText(res.isEmpty() ? Tr.t("SIN LETRA") : Tr.t("LETRA SIN TIEMPOS · TOCA PARA VERLA"));
                    tv[n / 2].setTextSize(12);
                    tv[n / 2].setTextColor(th.ink2);
                    tv[n / 2].setAlpha(0.8f);
                    return;
                }
                int cur = n / 2;
                for (int i = 0; i < n; i++) {
                    int idx = a + (i - cur);
                    String t = idx >= 0 && idx < ls.size() ? ls.get(idx).text : "";
                    if (idx >= 0 && idx < ls.size() && t.length() == 0) {
                        t = "\u266a";
                    }
                    boolean on = i == cur;
                    tv[i].setText(t);
                    tv[i].setTextSize(on ? 19 : 13);
                    tv[i].setTextColor(on ? th.ink : th.ink2);
                    tv[i].setAlpha(on ? 1f : 0.5f);
                }
            }
        };
        part.view = col;
        return part;
    }

    private void toggleBasic(int which) {
        android.content.SharedPreferences sp = Lucent.prefs(this);
        if (which == 0) {
            Player.I.setShuffle(!Player.I.isShuffle());
        } else if (which == 1) {
            Player.I.cycleRepeat();
        } else if (which == 2) {
            sp.edit().putBoolean("wlock", !sp.getBoolean("wlock", false)).apply();
        } else if (which == 3) {
            sp.edit().putBoolean("cover", !sp.getBoolean("cover", false)).apply();
        } else {
            sp.edit().putBoolean("pitch_lock", !sp.getBoolean("pitch_lock", false)).apply();
        }
    }
}
