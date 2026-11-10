package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

public class ScratchActivity extends Activity {
    private static final int REQ_OPEN = 1;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private JogWheelView wheel;
    private TextView nameView;
    private TextView metaView;
    private DotTextView timeView;
    private TextView durView;
    private Lucent th;
    private DotIconView playBtn;
    private DotIconView shuffleBtn;
    private DotIconView lockBtn;
    private DotIconView coverBtn;
    private TextView lockLabel;
    private DotIconView modeBtn;
    private View volRow;
    private View pitchRow;
    private TextView modeLabel;
    private LinearLayout djCard;
    private boolean djMode;
    private TextView coverLabel;
    private SeekBar volBar;
    private SeekBar pitchBar;
    private TextView pitchLabel;
    private int lastVol = -1;
    private android.media.AudioManager audio;
    private SeekBar progress;
    private boolean locked;
    private boolean coverOn;
    private String coverKey = "";
    private boolean dragging;
    private int lastSec = -1;
    private int lastDur = -1;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onChanged() {
            refresh();
        }
    };

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            // Fluido (un cuadro por vsync) mientras suena, se toca la rueda o el plato aún está frenando
            // o arrancando; en reposo 5 fps para ahorrar batería.
            ScratchEngine e = Player.I.engine;
            boolean busy = Player.I.isPlayingOrPending() || e.isScratching()
                    || Math.abs(e.getRate()) > 0.001 || (djMode && e.getLevel() > 0.004);
            if (busy) {
                wheel.postOnAnimation(this);
            } else {
                ui.postDelayed(this, 200);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        Player.I.init(this);
        setContentView(buildUi());
        if (state == null) {
            // Entrada: el panel sube desde abajo, como el reproductor de una app de música.
            final View content = findViewById(android.R.id.content);
            content.setTranslationY(getResources().getDisplayMetrics().heightPixels);
            content.setAlpha(0.6f);
            content.animate().translationY(0).alpha(1f).setDuration(360)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f)).start();
        }
        overridePendingTransition(0, 0);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // Giro automático: se vuelve a montar la pantalla con el diseño vertical u horizontal.
        ui.removeCallbacks(ticker);
        coverKey = "";
        lastVol = -1;
        setContentView(buildUi());
        refresh();
        volBar.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        ui.post(ticker);
    }

    private boolean closing;

    /** Salida: el panel baja y deja ver la lista. */
    private void closeAnimated() {
        if (closing) {
            return;
        }
        closing = true;
        View content = findViewById(android.R.id.content);
        content.animate().translationY(getResources().getDisplayMetrics().heightPixels).alpha(0.6f)
                .setDuration(280).setInterpolator(new android.view.animation.AccelerateInterpolator(1.4f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }).start();
    }

    @Override
    public void onBackPressed() {
        closeAnimated();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
            return;
        }
        Player.I.addListener(listener);
        if (djMode) {
            rebuildDj();
        }
        refresh();
        volBar.setProgress(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC));
        ui.post(ticker);
    }

    @Override
    public void finish() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        super.finish();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Player.I.removeListener(listener);
        ui.removeCallbacks(ticker);
        wheel.removeCallbacks(ticker);
        Player.I.touchUp();
        Player.I.seekHold(0);
        Player.I.savePos();
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(10));
        DotIconView back = icon(DotIcons.DOWN, th.ink);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeAnimated();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText("SCRATCH");
        title.setPadding(dp(6), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        TextView beta = text("BETA " + ver, 11, th.accent, th.mono);
        beta.setLetterSpacing(0.1f);
        beta.setPadding(dp(10), dp(14), 0, 0);
        header.addView(beta, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final DotIconView menu = icon(DotIcons.MENU, th.ink);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(menu);
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        final boolean land = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        LinearLayout leftCol = root;
        LinearLayout rightCol = root;
        if (land) {
            LinearLayout body = new LinearLayout(this);
            body.setOrientation(LinearLayout.HORIZONTAL);
            leftCol = new LinearLayout(this);
            leftCol.setOrientation(LinearLayout.VERTICAL);
            rightCol = new LinearLayout(this);
            rightCol.setOrientation(LinearLayout.VERTICAL);
            body.addView(leftCol, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            android.widget.ScrollView rs = new android.widget.ScrollView(this);
            rs.setVerticalScrollBarEnabled(false);
            rs.setOverScrollMode(View.OVER_SCROLL_NEVER);
            rs.addView(rightCol, new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1.25f);
            rlp.leftMargin = dp(10);
            body.addView(rs, rlp);
            root.addView(body, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        }

        LinearLayout info = card();
        info.setPadding(dp(18), dp(14), dp(18), dp(8));
        nameView = text("", 17, th.ink, th.semi);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nameView);

        metaView = text("", 11, th.ink2, th.mono);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setPadding(0, dp(2), 0, 0);
        info.addView(metaView);

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.BOTTOM);
        timeRow.setPadding(0, dp(12), 0, 0);
        timeView = new DotTextView(this, 4.2f, th);
        timeView.setText("00:00");
        timeRow.addView(timeView);
        durView = text("/ 00:00", 13, th.ink2, th.mono);
        durView.setPadding(dp(10), 0, 0, 0);
        timeRow.addView(durView);
        info.addView(timeRow);

        progress = new SeekBar(this);
        progress.setMax(1000);
        th.styleSeek(progress);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) {
                    Player.I.seekFraction(value / 1000.0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
                dragging = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                Player.I.savePos();
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32));
        plp.topMargin = dp(6);
        info.addView(progress, plp);
        rightCol.addView(info);

        wheel = new JogWheelView(this, th);
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
        locked = Lucent.prefs(this).getBoolean("wlock", false);
        coverOn = Lucent.prefs(this).getBoolean("cover", false);
        wheel.setLocked(locked);
        wheel.setHaptic(Lucent.prefs(this).getBoolean("haptic", true));
        wheel.setCoverOn(coverOn);
        LinearLayout wheelCard = card();
        wheelCard.setPadding(dp(8), dp(8), dp(8), dp(8));
        wheelCard.addView(wheel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        wlp.topMargin = dp(10);
        wlp.bottomMargin = dp(10);
        wheelCard.setMinimumHeight(land ? dp(120) : dp(340));
        if (land) {
            wlp.topMargin = 0;
            wlp.bottomMargin = 0;
        }
        leftCol.addView(wheelCard, wlp);

        LinearLayout controls = card();
        controls.setPadding(dp(10), dp(10), dp(10), dp(12));

        LinearLayout transport = row();
        transport.setGravity(Gravity.CENTER_VERTICAL);
        DotIconView prev = icon(DotIcons.PREV, th.ink);
        DotIconView rew = icon(DotIcons.REW, th.ink);
        playBtn = icon(DotIcons.PLAY, th.accent).ghost(false);
        DotIconView ffw = icon(DotIcons.FFW, th.ink);
        DotIconView next = icon(DotIcons.NEXT, th.ink);
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
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.toggle();
            }
        });
        holdToSeek(rew, -1);
        holdToSeek(ffw, 1);
        addIcon(transport, prev, 1f, 48);
        addIcon(transport, rew, 1f, 48);
        addIcon(transport, playBtn, 1.5f, 68);
        addIcon(transport, ffw, 1f, 48);
        addIcon(transport, next, 1f, 48);
        controls.addView(transport);

        rightCol.addView(controls);
        rightCol.addView(buildOptions());
        rightCol.addView(buildDj());
        refreshMode();
        if (land) {
            return root;
        }
        // En modo DJ hay más controles que alto de pantalla: la rueda mantiene su tamaño y se desplaza.
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(root, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        return sv;
    }

    /** Opción pequeña: icono de puntos con su etiqueta debajo. */
    private LinearLayout chip(DotIconView icon, TextView label) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        c.setClickable(true);
        c.setFocusable(true);
        // el icono no debe tragarse el toque: toda la casilla es el botón
        icon.setClickable(false);
        icon.setFocusable(false);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(Lucent.R_S));
        bg.setColor(0x00000000);
        c.setBackground(bg);
        final android.graphics.drawable.GradientDrawable bgf = bg;
        c.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    bgf.setColor(th.glassLine);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    bgf.setColor(0x00000000);
                }
                return false;
            }
        });
        c.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        c.addView(label);
        return c;
    }

    private TextView tag(String s) {
        TextView t = text(s, 10, th.ink2, th.mono);
        t.setLetterSpacing(0.1f);
        return t;
    }

    /** Segunda tarjeta, separada de la botonera: aleatorio, bloqueo de la rueda, carátula y volumen. */
    private View buildOptions() {
        LinearLayout card = card();
        card.setPadding(0, 0, 0, dp(10));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);

        LinearLayout opts = row();
        shuffleBtn = icon(DotIcons.SHUFFLE, th.ink);
        LinearLayout shuffleChip = chip(shuffleBtn, tag("ALEATORIO"));
        shuffleChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Player.I.setShuffle(!Player.I.isShuffle());
            }
        });
        lockBtn = icon(DotIcons.DISC, th.ink);
        lockLabel = tag("SCRATCH");
        LinearLayout lockChip = chip(lockBtn, lockLabel);
        lockChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locked = !locked;
                Lucent.prefs(ScratchActivity.this).edit().putBoolean("wlock", locked).apply();
                wheel.setLocked(locked);
                refreshMode();
            }
        });
        coverBtn = icon(DotIcons.COVER, th.ink);
        coverLabel = tag("PORTADA");
        LinearLayout coverChip = chip(coverBtn, coverLabel);
        coverChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                coverOn = !coverOn;
                Lucent.prefs(ScratchActivity.this).edit().putBoolean("cover", coverOn).apply();
                wheel.setCoverOn(coverOn);
                refreshMode();
                if (coverOn) {
                    loadCover(true);
                }
            }
        });
        djMode = Lucent.prefs(this).getBoolean("djmode", false);
        modeBtn = icon(DotIcons.DISC, th.ink);
        modeLabel = tag("ESTÁNDAR");
        LinearLayout modeChip = chip(modeBtn, modeLabel);
        modeChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setDjMode(!djMode);
            }
        });
        opts.addView(modeChip, new LinearLayout.LayoutParams(0, dp(96), 1f));
        opts.addView(shuffleChip, new LinearLayout.LayoutParams(0, dp(96), 1f));
        opts.addView(lockChip, new LinearLayout.LayoutParams(0, dp(96), 1f));
        opts.addView(coverChip, new LinearLayout.LayoutParams(0, dp(96), 1f));
        card.addView(opts);

        LinearLayout vol = row();
        vol.setGravity(Gravity.CENTER_VERTICAL);
        vol.setPadding(dp(20), dp(2), dp(22), 0);
        DotIconView vi = icon(DotIcons.VOL, th.ink2);
        vol.addView(vi, new LinearLayout.LayoutParams(dp(30), dp(30)));
        audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        volBar = new SeekBar(this);
        th.styleSeek(volBar);
        volBar.setMax(audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC));
        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int value, boolean fromUser) {
                if (fromUser) {
                    audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, value, 0);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        vlp.leftMargin = dp(4);
        vol.addView(volBar, vlp);
        volRow = vol;
        card.addView(vol);

        LinearLayout pr = row();
        pr.setGravity(Gravity.CENTER_VERTICAL);
        pr.setPadding(dp(20), dp(4), dp(22), 0);
        pr.addView(icon(DotIcons.PITCH, th.ink2), new LinearLayout.LayoutParams(dp(30), dp(30)));
        pitchBar = new SeekBar(this);
        th.styleSeek(pitchBar);
        pitchBar.setMax(40);
        pitchBar.setProgress((int) Math.round((Player.I.pitch() - 0.8) * 100));
        pitchLabel = text(pitchText(Player.I.pitch()), 11, th.ink2, th.mono);
        pitchLabel.setMinWidth(dp(54));
        pitchLabel.setGravity(Gravity.END);
        pitchLabel.setClickable(true);
        pitchLabel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pitchBar.setProgress(20);
                Player.I.setPitch(1.0);
                pitchLabel.setText(pitchText(1.0));
            }
        });
        pitchBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                if (fromUser) {
                    double p = value == 20 ? 1.0 : 0.8 + value / 100.0;
                    Player.I.setPitch(p);
                    pitchLabel.setText(pitchText(p));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, dp(34), 1f);
        plp.leftMargin = dp(4);
        pr.addView(pitchBar, plp);
        pr.addView(pitchLabel);
        pitchRow = pr;
        card.addView(pr);
        return card;
    }

    private static String pitchText(double p) {
        double pct = (p - 1.0) * 100;
        return (pct >= 0 ? "+" : "") + String.format(java.util.Locale.US, "%.0f", pct) + "%";
    }

    private void refreshMode() {
        lockBtn.set(locked ? DotIcons.LOCK : DotIcons.DISC, locked ? th.ink : th.accent);
        lockLabel.setText(locked ? "BLOQUEADA" : "SCRATCH");
        coverBtn.set(DotIcons.COVER, coverOn ? th.accent : th.ink);
        coverLabel.setText(coverOn ? "PORTADA" : "PUNTOS");
        modeBtn.set(DotIcons.PITCH, djMode ? th.accent : th.ink);
        modeLabel.setText(djMode ? "DJ" : "ESTÁNDAR");
        if (djCard != null) {
            djCard.setVisibility(djMode ? View.VISIBLE : View.GONE);
        }
        if (volRow != null) {
            volRow.setVisibility(djMode ? View.GONE : View.VISIBLE);
            pitchRow.setVisibility(djMode ? View.GONE : View.VISIBLE);
        }
    }

    private void setDjMode(boolean on) {
        djMode = on;
        Lucent.prefs(this).edit().putBoolean("djmode", on).apply();
        if (!on) {
            Player.I.engine.resetFx();
            faderVals.clear();
            wheel.setDisco(false);
        }
        if (on) {
            Player.I.engine.setBrake(brakeSecs);
        }
        refreshMode();
        rebuildDj();
    }

    private double brakeSecs = 0.7;
    private LinearLayout djHost;

    private void rebuildDj() {
        if (djHost == null) {
            return;
        }
        djHost.removeAllViews();
        djCard = null;
        if (djMode) {
            djCard = buildDjCard();
            djHost.addView(djCard);
        }
    }

    /** Contenedor fijo; la tarjeta DJ se construye de nuevo al activar el modo (sliders a neutro). */
    private View buildDj() {
        djHost = new LinearLayout(this);
        djHost.setOrientation(LinearLayout.VERTICAL);
        if (djMode) {
            Player.I.engine.setBrake(brakeSecs);
            djCard = buildDjCard();
            djHost.addView(djCard);
        }
        return djHost;
    }

    private final java.util.HashMap<String, Double> faderVals = new java.util.HashMap<String, Double>();
    private DotMeterView meter;
    private boolean discoOn;
    private boolean meterOn;

    private boolean opt(String key, boolean def) {
        return Lucent.prefs(this).getBoolean(key, def);
    }

    private double fv(String key, double def) {
        Double d = faderVals.get(key);
        return d == null ? def : d;
    }

    private static String dbText(double gain) {
        if (gain < 0.01) {
            return "-∞";
        }
        double db = 20 * Math.log10(gain);
        return (db > 0.05 ? "+" : "") + String.format(java.util.Locale.US, "%.1f", db);
    }

    /** Panel DJ: faders numerados, vúmetro, cues, bucles, efectos, sampler. Cada grupo se puede apagar en Ajustes. */
    private LinearLayout buildDjCard() {
        final LinearLayout card = card();
        card.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(10);
        card.setLayoutParams(clp);
        final ScratchEngine en = Player.I.engine;
        meterOn = opt("dj_meter", true);
        discoOn = opt("dj_disco", false);
        wheel.setDisco(discoOn);
        if (!opt("dj_fx", true)) {
            en.setFlanger(false);
            en.setPhaser(false);
            en.setGate(0);
        }
        if (!opt("dj_cut", true)) {
            en.setCut(false);
            en.setReverse(false);
        }
        if (!opt("dj_loop", true)) {
            en.loopExit();
        }
        en.setSlip(opt("dj_slip", true) && Lucent.prefs(this).getBoolean("slip_on", false));

        if (meterOn) {
            meter = new DotMeterView(this, th);
            meter.setDisco(discoOn);
            card.addView(meter, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(22)));
        } else {
            meter = null;
        }

        final double[] eq = {fv("GRAVES", 1 / 1.6) * 1.6, fv("MEDIOS", 1 / 1.6) * 1.6,
                fv("AGUDOS", 1 / 1.6) * 1.6};
        LinearLayout faders = row();
        faders.setGravity(Gravity.CENTER);
        FaderView filter = fader(faders, "FILTRO", 0.5, new FaderView.Listener() {
            @Override
            public void onValue(double v) {
                en.setFilter((v - 0.5) * 2);
            }
        });
        filter.setTicks(new double[] {0, 0.25, 0.5, 0.75, 1}, new String[] {"LP", "", "OFF", "", "HP"});
        filter.setReadout(new FaderView.Readout() {
            @Override
            public String text(double v) {
                if (Math.abs(v - 0.5) < 0.02) {
                    return "OFF";
                }
                return (v < 0.5 ? "LP " : "HP ") + Math.round(Math.abs(v - 0.5) * 200) + "%";
            }
        });
        String[] eqNames = {"GRAVES", "MEDIOS", "AGUDOS"};
        for (int k = 0; k < 3; k++) {
            final int idx = k;
            FaderView f = fader(faders, eqNames[k], 1 / 1.6, new FaderView.Listener() {
                @Override
                public void onValue(double v) {
                    eq[idx] = v * 1.6;
                    en.setEq(eq[0], eq[1], eq[2]);
                }
            });
            f.setTicks(new double[] {1, 0.625, 0.3125, 0.156, 0},
                    new String[] {"+4", "0", "-6", "-12", "-∞"});
            f.setReadout(new FaderView.Readout() {
                @Override
                public String text(double v) {
                    return dbText(v * 1.6) + "dB";
                }
            });
        }
        FaderView echo = fader(faders, "ECO", 0, new FaderView.Listener() {
            @Override
            public void onValue(double v) {
                en.setEcho(v);
            }
        });
        echo.setTicks(new double[] {1, 0.5, 0}, new String[] {"100", "50", "0"});
        echo.setReadout(new FaderView.Readout() {
            @Override
            public String text(double v) {
                return Math.round(v * 100) + "%";
            }
        });
        FaderView pitch = fader(faders, "PITCH", 0.5, new FaderView.Listener() {
            @Override
            public void onValue(double v) {
                double pc = v == 0.5 ? 1.0 : 0.8 + v * 0.4;
                Player.I.setPitch(pc);
                if (pitchLabel != null) {
                    pitchLabel.setText(pitchText(pc));
                }
                if (pitchBar != null) {
                    pitchBar.setProgress((int) Math.round((pc - 0.8) * 100));
                }
            }
        });
        pitch.setTicks(new double[] {1, 0.75, 0.5, 0.25, 0}, new String[] {"+20", "+10", "0", "-10", "-20"});
        pitch.setReadout(new FaderView.Readout() {
            @Override
            public String text(double v) {
                return pitchText(v == 0.5 ? 1.0 : 0.8 + v * 0.4);
            }
        });
        pitch.setValue((Player.I.pitch() - 0.8) / 0.4);
        card.addView(faders);

        // --- frenado / backspin / vinyl stop
        LinearLayout br = padRow();
        final TextView back = pad("BACKSPIN", true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                en.backspin();
            }
        });
        br.addView(back, padLp());
        final TextView brake = pad("FRENADO " + brakeText(), false);
        brake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                brakeSecs = brakeSecs < 0.5 ? 0.7 : brakeSecs < 1.0 ? 2.0 : 0.3;
                en.setBrake(brakeSecs);
                brake.setText("FRENADO " + brakeText());
            }
        });
        br.addView(brake, padLp());
        if (opt("dj_fx", true)) {
            TextView vs = pad("VINYL STOP", true);
            vs.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Player.I.vinylStop(1.2);
                }
            });
            br.addView(vs, padLp());
        }
        card.addView(br);

        if (opt("dj_cues", true)) {
            card.addView(sectionTag("HOT CUES · TOCA PARA GUARDAR / SALTAR · LARGO BORRA"));
            LinearLayout cr = padRow();
            final TextView[] cp = new TextView[4];
            for (int k = 0; k < 4; k++) {
                final int idx = k;
                cp[k] = pad("", false);
                cueText(cp[k], idx);
                cp[k].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!en.jumpCue(idx)) {
                            en.setCue(idx);
                        }
                        cueText(cp[idx], idx);
                    }
                });
                cp[k].setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        en.clearCue(idx);
                        cueText(cp[idx], idx);
                        return true;
                    }
                });
                cr.addView(cp[k], padLp());
            }
            card.addView(cr);
        }

        if (opt("dj_loop", true) || opt("dj_slip", true)) {
            card.addView(sectionTag("BUCLE"));
            LinearLayout lr = padRow();
            if (opt("dj_loop", true)) {
                final TextView loop = pad("", false);
                loopText(loop, en);
                loop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (en.isLoopOn()) {
                            en.loopExit();
                        } else if (en.hasLoopIn()) {
                            en.loopOut();
                        } else {
                            en.loopIn();
                        }
                        loopText(loop, en);
                    }
                });
                lr.addView(loop, padLp());
                TextView half = pad("½", false);
                half.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        en.loopScale(0.5);
                    }
                });
                lr.addView(half, padLp());
                TextView twice = pad("×2", false);
                twice.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        en.loopScale(2.0);
                    }
                });
                lr.addView(twice, padLp());
            }
            if (opt("dj_slip", true)) {
                final TextView slip = pad("SLIP", false);
                padOn(slip, Lucent.prefs(this).getBoolean("slip_on", false));
                slip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean on = !Lucent.prefs(ScratchActivity.this).getBoolean("slip_on", false);
                        Lucent.prefs(ScratchActivity.this).edit().putBoolean("slip_on", on).apply();
                        en.setSlip(on);
                        padOn(slip, on);
                    }
                });
                lr.addView(slip, padLp());
            }
            card.addView(lr);
        }
        if (opt("dj_loop", true)) {
            LinearLayout rr = padRow();
            double[] secs = {0.25, 0.5, 1.0};
            String[] names = {"ROLL ¼", "ROLL ½", "ROLL 1"};
            for (int k = 0; k < 3; k++) {
                final double sc = secs[k];
                TextView roll = pad(names[k], false);
                hold(roll, new Runnable() {
                    @Override
                    public void run() {
                        en.rollStart(sc);
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        en.rollEnd();
                    }
                });
                rr.addView(roll, padLp());
            }
            card.addView(rr);
        }

        if (opt("dj_jump", true)) {
            card.addView(sectionTag("SALTO"));
            LinearLayout jr = padRow();
            final double[] js = {-16, -4, 4, 16};
            for (int k = 0; k < 4; k++) {
                final double sec = js[k];
                TextView j = pad((sec > 0 ? "+" : "−") + (int) Math.abs(sec) + "S", false);
                j.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        en.jump(sec);
                    }
                });
                jr.addView(j, padLp());
            }
            card.addView(jr);
        }

        if (opt("dj_fx", true)) {
            card.addView(sectionTag("EFECTOS"));
            LinearLayout fr = padRow();
            final TextView fl = pad("FLANGER", false);
            padOn(fl, en.isFlanger());
            fl.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    en.setFlanger(!en.isFlanger());
                    padOn(fl, en.isFlanger());
                }
            });
            fr.addView(fl, padLp());
            final TextView ph = pad("PHASER", false);
            padOn(ph, en.isPhaser());
            ph.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    en.setPhaser(!en.isPhaser());
                    padOn(ph, en.isPhaser());
                }
            });
            fr.addView(ph, padLp());
            final TextView tr = pad("", false);
            transText(tr, en);
            tr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    en.setGate(en.getGate() > 0 ? 0 : transHz);
                    transText(tr, en);
                }
            });
            tr.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    transHz = transHz < 3 ? 4 : transHz < 6 ? 8 : transHz < 12 ? 16 : 2;
                    if (en.getGate() > 0) {
                        en.setGate(transHz);
                    }
                    transText(tr, en);
                    return true;
                }
            });
            fr.addView(tr, padLp());
            TextView eo = pad("ECHO OUT", true);
            eo.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    en.echoOut();
                }
            });
            fr.addView(eo, padLp());
            card.addView(fr);
        }

        if (opt("dj_sampler", true)) {
            card.addView(sectionTag("SAMPLER"));
            LinearLayout sr = padRow();
            String[] sn = {"BOCINA", "SIRENA", "LÁSER", "RISER"};
            for (int k = 0; k < 4; k++) {
                final int id = k;
                TextView sp = pad(sn[k], true);
                sp.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Player.I.sample(id);
                    }
                });
                sr.addView(sp, padLp());
            }
            card.addView(sr);
        }

        if (opt("dj_cut", true)) {
            card.addView(sectionTag("MANTÉN PULSADO"));
            LinearLayout xr = padRow();
            TextView cutB = pad("CUT", true);
            hold(cutB, new Runnable() {
                @Override
                public void run() {
                    en.setCut(true);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    en.setCut(false);
                }
            });
            xr.addView(cutB, padLp());
            TextView rev = pad("REVERSE", true);
            hold(rev, new Runnable() {
                @Override
                public void run() {
                    en.setReverse(true);
                }
            }, new Runnable() {
                @Override
                public void run() {
                    en.setReverse(false);
                }
            });
            xr.addView(rev, padLp());
            card.addView(xr);
        }
        return card;
    }

    private double transHz = 4;

    private void transText(TextView t, ScratchEngine en) {
        t.setText("TRANS " + (int) transHz + "HZ");
        padOn(t, en.getGate() > 0);
    }

    private void loopText(TextView t, ScratchEngine en) {
        boolean on = en.isLoopOn();
        t.setText(on ? "SALIR" : en.hasLoopIn() ? "OUT" : "IN");
        padOn(t, on || en.hasLoopIn());
    }

    private void cueText(TextView t, int i) {
        double c = Player.I.engine.getCue(i);
        t.setText(c < 0 ? (i + 1) + " · —" : (i + 1) + " · " + fmt((int) c));
        padOn(t, c >= 0);
    }

    /** Pulsa y mantén: acción al apretar y otra al soltar. */
    private void hold(final TextView t, final Runnable down, final Runnable up) {
        t.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        padOn(t, true);
                        down.run();
                        return true;
                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        padOn(t, false);
                        up.run();
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private TextView sectionTag(String s) {
        TextView t = text(s, 9, th.ink2, th.mono);
        t.setLetterSpacing(0.1f);
        t.setPadding(dp(6), dp(8), 0, dp(2));
        return t;
    }

    private LinearLayout padRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout.LayoutParams padLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        return lp;
    }

    /** Pad de cristal con hit box de toda la casilla. */
    private TextView pad(String label, boolean accentText) {
        TextView t = text(label, 11, accentText ? th.accent : th.ink, th.mono);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setClickable(true);
        t.setFocusable(true);
        t.setTag(accentText ? Boolean.TRUE : Boolean.FALSE);
        padOn(t, false);
        return t;
    }

    private void padOn(TextView t, boolean on) {
        boolean acc = Boolean.TRUE.equals(t.getTag());
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(Lucent.R_M));
        g.setColor(on ? Lucent.withAlpha(th.accent, 0.22f) : th.glass);
        g.setStroke(dp(1), on ? th.accent : th.glassLine);
        t.setBackground(g);
        t.setTextColor(on || acc ? th.accent : th.ink);
    }

    /** Botón de texto grande (≥48dp) con fondo de cristal. */
    private void btnStyle(TextView t) {
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(48));
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(Lucent.R_M));
        g.setColor(th.glass);
        g.setStroke(dp(1), th.glassLine);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
    }

    private FaderView fader(LinearLayout row, final String label, double neutral,
            final FaderView.Listener inner) {
        FaderView f = new FaderView(this, th, label, neutral);
        f.setListener(new FaderView.Listener() {
            @Override
            public void onValue(double v) {
                faderVals.put(label, v);
                inner.onValue(v);
            }
        });
        Double saved = faderVals.get(label);
        if (saved != null) {
            f.setValue(saved);
        }
        row.addView(f, new LinearLayout.LayoutParams(0, dp(176), 1f));
        return f;
    }

    private String brakeText() {
        return String.format(java.util.Locale.US, "%.1fS", brakeSecs);
    }

    /** Lee la carátula incrustada del archivo en segundo plano y se la pasa a la rueda. */
    private void loadCover(boolean force) {
        final android.net.Uri uri = Player.I.currentUri();
        final String key = uri == null ? "" : uri.toString();
        if (!force && key.equals(coverKey)) {
            return;
        }
        coverKey = key;
        if (uri == null) {
            wheel.setCover(null);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.graphics.Bitmap out = null;
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    r.setDataSource(ScratchActivity.this, uri);
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inJustDecodeBounds = true;
                        android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
                        int side = Math.max(1, Math.min(o.outWidth, o.outHeight));
                        o.inSampleSize = Math.max(1, side / 640);
                        o.inJustDecodeBounds = false;
                        android.graphics.Bitmap full = android.graphics.BitmapFactory
                                .decodeByteArray(pic, 0, pic.length, o);
                        if (full != null) {
                            int sq = Math.min(full.getWidth(), full.getHeight());
                            out = android.graphics.Bitmap.createBitmap(full,
                                    (full.getWidth() - sq) / 2, (full.getHeight() - sq) / 2, sq, sq);
                        }
                    }
                } catch (RuntimeException | OutOfMemoryError ignored) {
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                    }
                }
                final android.graphics.Bitmap result = out;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(coverKey)) {
                            wheel.setCover(result);
                        }
                    }
                });
            }
        }).start();
    }

    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        l.setLayoutParams(lp);
        return l;
    }

    private void add(LinearLayout row, Button b, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), weight);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        row.addView(b, lp);
    }

    private DotIconView icon(String[] pattern, int color) {
        DotIconView v = new DotIconView(this, th);
        v.set(pattern, color);
        return v;
    }

    private void addIcon(LinearLayout row, View v, float weight, int heightDp) {
        row.addView(v, new LinearLayout.LayoutParams(0, dp(heightDp), weight));
    }

    private void showMenu(final View anchor) {
        int left = Player.I.sleepMinutes();
        DotMenu.show(this, th, anchor, new String[] {"COLA",
            left > 0 ? "TEMPORIZADOR · " + left + " MIN" : "TEMPORIZADOR", "ABRIR ARCHIVO", "AJUSTES"},
                new DotMenu.Picked() {
                    @Override
                    public void onPick(int id) {
                        if (id == 0) {
                            startActivity(new Intent(ScratchActivity.this, QueueActivity.class));
                        } else if (id == 1) {
                            anchor.post(new Runnable() {
                                @Override
                                public void run() {
                                    showSleepMenu(anchor);
                                }
                            });
                        } else if (id == 2) {
                            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("audio/*");
                            startActivityForResult(i, REQ_OPEN);
                        } else {
                            startActivity(new Intent(ScratchActivity.this, SettingsActivity.class));
                        }
                    }
                });
    }

    private void showSleepMenu(View anchor) {
        final int[] mins = {0, 15, 30, 60, 90};
        String[] labels = {"SIN TEMPORIZADOR", "APAGAR EN 15 MIN", "APAGAR EN 30 MIN",
            "APAGAR EN 60 MIN", "APAGAR EN 90 MIN"};
        DotMenu.show(this, th, anchor, labels, new DotMenu.Picked() {
            @Override
            public void onPick(int id) {
                Player.I.setSleepMinutes(mins[id]);
            }
        });
    }

    /**
     * Avance/retroceso rápido a 1,5×. Si se mantiene pulsado, aparece un globo con una barra:
     * mover el dedo a la derecha acelera y a la izquierda frena.
     */
    private void holdToSeek(final View b, final int dir) {
        final Handler h = new Handler(Looper.getMainLooper());
        final SeekPopup pop = new SeekPopup(this, th);
        final float[] down = new float[1];
        final boolean[] shown = new boolean[1];
        final Runnable show = new Runnable() {
            @Override
            public void run() {
                shown[0] = true;
                pop.show(b);
            }
        };
        final float density = getResources().getDisplayMetrics().density;
        b.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (v.getParent() != null) {
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        v.setPressed(true);
                        down[0] = e.getRawX();
                        shown[0] = false;
                        Player.I.seekHold(dir);
                        h.postDelayed(show, 400);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (shown[0]) {
                            double s = SeekSpeed.forDrag(e.getRawX() - down[0], density);
                            Player.I.seekSpeed(s);
                            pop.set(s);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        h.removeCallbacks(show);
                        pop.dismiss();
                        shown[0] = false;
                        v.setPressed(false);
                        Player.I.seekHold(0);
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(th.card(Lucent.R_L));
        return l;
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

    private Button pill(String label, int kind) {
        Button b = new Button(this);
        b.setText(label);
        th.style(b, kind);
        return b;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OPEN && res == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Player.I.openSingle(uri, displayName(uri).toUpperCase(Locale.ROOT));
        }
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

    private void refresh() {
        Player p = Player.I;
        String t = p.isLoading() ? "CARGANDO…" : p.title();
        nameView.setText(t.toUpperCase(Locale.ROOT) + (p.isTruncated() ? " (RECORTADA)" : ""));
        StringBuilder meta = new StringBuilder();
        if (p.index() >= 0) {
            meta.append(p.index() + 1).append(" / ").append(p.count());
            if (p.libraryLabel().length() > 0) {
                meta.append("  ·  ").append(p.libraryLabel().toUpperCase(Locale.ROOT));
            }
        } else if (p.count() > 0) {
            meta.append(p.count()).append(" CANCIONES EN LA BIBLIOTECA");
        } else {
            meta.append("SIN BIBLIOTECA · PULSA BIBLIOTECA");
        }
        metaView.setText(meta.toString());
        boolean playing = p.isPlayingOrPending();
        playBtn.set(playing ? DotIcons.PAUSE : DotIcons.PLAY, th.accent);
        wheel.setPlayingState(playing);
        shuffleBtn.set(DotIcons.SHUFFLE, p.isShuffle() ? th.accent : th.ink);
        if (coverOn) {
            loadCover(false);
        }
        lastSec = -1;
        tick();
    }

    private void tick() {
        if (audio != null && volBar != null) {
            int v = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
            if (v != lastVol) {
                lastVol = v;
                volBar.setProgress(v);
            }
        }
        ScratchEngine e = Player.I.engine;
        if (djMode) {
            float lv = (float) e.getLevel();
            if (meter != null) {
                meter.setLevel(lv);
            }
            if (discoOn) {
                wheel.setLevel(lv);
            }
        }
        double pos = e.getPositionSeconds();
        wheel.setAngle((float) (pos / Player.I.engine.getSecondsPerRev() * 2 * Math.PI * (Player.I.engine.isInverted() ? -1 : 1)));
        double total = e.getDurationSeconds();
        if (!dragging && total > 0) {
            progress.setProgress((int) Math.round(pos / total * 1000));
        }
        int sec = (int) pos;
        int dur = (int) total;
        if (sec != lastSec || dur != lastDur) {
            lastSec = sec;
            lastDur = dur;
            timeView.setText(fmt(sec));
            durView.setText("/ " + fmt(dur));
        }
    }

    private static String fmt(int s) {
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
