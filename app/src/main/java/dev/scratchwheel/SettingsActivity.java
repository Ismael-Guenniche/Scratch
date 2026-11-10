package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * Ajustes con la misma estructura que Dot Words: Tema, Colores (colores del fondo + acento),
 * Tipografía (Dot / Mono / Clean) y Acerca de. Se añade Cristal para el estilo Lucent.
 */
public class SettingsActivity extends Activity {
    private Lucent th;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        prefs = Lucent.prefs(this);
        Player.I.init(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            redo();
        }
    }

    private ScrollView scroll;
    private static int savedScroll = -1;

    /** Recrea la pantalla (para aplicar el tema) sin perder la posición del scroll ni parpadear. */
    private void redo() {
        savedScroll = scroll != null ? scroll.getScrollY() : -1;
        recreate();
        overridePendingTransition(0, 0);
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private void changed() {
        Lucent.stamp++;
        redo();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, dp(6), 0, dp(10));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(24);
        back.setTextColor(th.ink);
        back.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(th.glass);
        bg.setStroke(dp(1), th.glassLine);
        back.setBackground(bg);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(52), dp(52)));
        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText(Tr.t("AJUSTES"));
        title.setPadding(dp(16), 0, 0, 0);
        header.addView(title);
        root.addView(header);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // TEMA
        LinearLayout c = plain(list, Tr.t("TEMA"));
        c.addView(segmented(new String[] {Tr.t("SISTEMA"), Tr.t("CLARO"), Tr.t("OSCURO")}, prefs.getInt("mode", 0),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("mode", v).apply();
                        changed();
                    }
                }));

        // COLORES
        // IDIOMA
        c = plain(list, Tr.t("IDIOMA"));
        final String[] langCodes = TrJa.ENABLED
                ? new String[] {Tr.EN, Tr.ES, Tr.JA} : new String[] {Tr.EN, Tr.ES};
        final String[] langNames = TrJa.ENABLED
                ? new String[] {"ENGLISH", "ESPAÑOL", "にほんご · EXPERIMENTAL"} : new String[] {"ENGLISH", "ESPAÑOL"};
        int langSel = 0;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(Tr.lang)) {
                langSel = i;
            }
        }
        c.addView(segmented(langNames, langSel, new Pick() {
            @Override
            public void on(int v) {
                Tr.set(SettingsActivity.this, langCodes[v]);
                redo();
            }
        }));

        c = card(list, Tr.t("COLORES"));
        boolean wallOk = Build.VERSION.SDK_INT >= 27;
        c.addView(switchRow(Tr.t("Colores del fondo"),
                wallOk ? Tr.t("Usa el color principal de tu fondo de pantalla como acento.")
                        : Tr.t("Requiere Android 8.1 o superior."),
                prefs.getBoolean("wall", false) && wallOk, wallOk, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wall", v).apply();
                        changed();
                    }
                }));
        c.addView(dots());
        TextView acc = body(Tr.t("Color de acento"));
        c.addView(acc);
        c.addView(swatches());

        // TIPOGRAFIA
        c = card(list, Tr.t("TIPOGRAFÍA"));
        c.addView(segmented(new String[] {"DOT", "MONO", "CLEAN"},
                prefs.getInt("font", Lucent.defaultLucent() ? 0 : 2), new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("font", v).apply();
                        changed();
                    }
                }));
        DotTextView prev = new DotTextView(this, 4f, th);
        prev.setText("AA 0123");
        prev.setPadding(0, dp(24), 0, dp(10));
        c.addView(prev);
        TextView sample = body("The quick brown fox jumps over the lazy dog.");
        sample.setPadding(0, dp(4), 0, dp(8));
        c.addView(sample);

        // RUEDA
        c = card(list, Tr.t("RUEDA"));
        c.addView(switchRow(Tr.t("Carátula del disco"),
                Tr.t("Muestra la imagen incrustada del archivo en vez del plato de puntos."),
                prefs.getBoolean("cover", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("cover", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Mantener el tono"),
                Tr.t("Al mover la rueda rápido o lento el tono no sube ni baja (el audio se estira en vez de acelerarse). También aplica al control de velocidad."),
                prefs.getBoolean("keylock", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("keylock", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(body(Tr.t("Sensibilidad de la rueda")));
        c.addView(segmented(new String[] {Tr.t("LENTA"), "NORMAL", Tr.t("RÁPIDA")}, prefs.getInt("wsens", 1),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("wsens", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        TextView sensNote = muted(Tr.t("Cuánto audio recorre una vuelta: lenta = más preciso, rápida = más recorrido."));
        sensNote.setPadding(0, dp(8), 0, 0);
        c.addView(sensNote);
        c.addView(dots());
        c.addView(switchRow(Tr.t("Inercia al soltar"),
                Tr.t("Si sueltas la rueda lanzada con la música parada, sigue girando como un plato hasta frenarse."),
                prefs.getBoolean("inertia", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("inertia", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(body(Tr.t("Fricción")));
        c.addView(segmented(new String[] {Tr.t("ALTA"), Tr.t("MEDIA"), Tr.t("BAJA")}, prefs.getInt("wfric", 1),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("wfric", v).apply();
                        Player.I.applyWheelPrefs();
                        redo();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Invertir el sentido"),
                Tr.t("Girar en sentido horario hace retroceder el audio, como en algunos platos."),
                prefs.getBoolean("winvert", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("winvert", v).apply();
                        Player.I.applyWheelPrefs();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Vibración al girar"),
                Tr.t("Un toque suave cada pocos grados, como los pasos de un dial."),
                prefs.getBoolean("haptic", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("haptic", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Bloquear scratch"),
                Tr.t("La rueda solo gira; el centro sigue haciendo play y pausa."),
                prefs.getBoolean("wlock", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wlock", v).apply();
                    }
                }));

        // MODO DJ
        c = card(list, Tr.t("MODO DJ"));
        c.addView(muted(Tr.t("Elige qué funciones aparecen al activar el modo DJ.")));
        c.addView(switchRow("Hot cues",
                Tr.t("4 pads: toca para guardar un punto y vuelve a tocar para saltar. Pulsación larga lo borra."),
                prefs.getBoolean("dj_cues", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_cues", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Bucles y roll"),
                Tr.t("Bucle manual (IN / OUT / ½ / ×2) y roll de ¼, ½ y 1 s mientras mantienes."),
                prefs.getBoolean("dj_loop", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_loop", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow("Slip",
                "Botón SLIP: mientras haces scratch o bucle, la canción sigue por debajo y al soltar vuelve a donde estaría.",
                prefs.getBoolean("dj_slip", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_slip", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Saltos"),
                Tr.t("Botones para saltar ±4 y ±16 segundos."),
                prefs.getBoolean("dj_jump", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_jump", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Efectos"),
                Tr.t("Flanger, phaser, trans (gate), echo out y vinyl stop."),
                prefs.getBoolean("dj_fx", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_fx", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow("Sampler",
                Tr.t("Pads con sonidos sintetizados: bocina, sirena, láser y riser."),
                prefs.getBoolean("dj_sampler", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_sampler", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Cut y reverse"),
                Tr.t("Mantén CUT para silenciar a golpes y REVERSE para oír la canción hacia atrás."),
                prefs.getBoolean("dj_cut", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_cut", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Vúmetro de puntos"),
                Tr.t("Medidor de nivel de puntos arriba del panel DJ."),
                prefs.getBoolean("dj_meter", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_meter", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Modo disco"),
                Tr.t("La rueda y el vúmetro cambian de color y pulsan con el volumen."),
                prefs.getBoolean("dj_disco", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("dj_disco", v).apply();
                    }
                }));

        // PERMISOS Y PRIVACIDAD
        c = card(list, Tr.t("PERMISOS Y PRIVACIDAD"));
        c.addView(muted(Tr.t("Scratch solo lee la carpeta de música que eliges (sin permiso de almacenamiento amplio) ")
                + Tr.t("y usa notificaciones para los controles de reproducción.")));
        c.addView(actionRow(Tr.t("Notificaciones"), Tr.t("ABRIR"), new Runnable() {
            @Override
            public void run() {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(i);
            }
        }));
        c.addView(dots());
        c.addView(actionRow(Tr.t("Política de privacidad"), Tr.t("LEER"), new Runnable() {
            @Override
            public void run() {
                new DotDialog(SettingsActivity.this, th).title(Tr.t("PRIVACIDAD"))
                        .message(Privacy.text() + Tr.t("\n\nVersión ") + Privacy.VERSION + " · " + Privacy.date())
                        .button(Tr.t("CERRAR"), Lucent.GLASS, null).show();
            }
        }));

        // TEMPORIZADOR
        int left = Player.I.sleepMinutes();
        c = card(list, Tr.t("TEMPORIZADOR DE APAGADO"));
        c.addView(muted(left > 0 ? Tr.t("Se apagará en ") + left + Tr.t(" min.") : Tr.t("La música no se apagará sola.")));
        final int[] mins = {0, 15, 30, 60, 90};
        c.addView(segmented(new String[] {"NO", "15", "30", "60", "90"}, left == 0 ? 0 : -1,
                new Pick() {
                    @Override
                    public void on(int v) {
                        Player.I.setSleepMinutes(mins[v]);
                        redo();
                    }
                }));

        // CRISTAL
        c = card(list, Tr.t("CRISTAL"));
        c.addView(switchRow(Tr.t("Estilo Lucent"),
                Tr.t("Tarjetas de cristal sobre tu fondo. Apagado: tarjetas sólidas."),
                prefs.getBoolean("lucent", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("lucent", v).apply();
                        changed();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow(Tr.t("Rejilla de puntos"),
                Tr.t("Puntos tenues por toda la pantalla, sobre el fondo difuminado."),
                prefs.getBoolean("grid", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("grid", v).apply();
                        changed();
                    }
                }));
        if (Build.VERSION.SDK_INT >= 31 && th.lucent) {
            c.addView(dots());
            c.addView(body(Tr.t("Desenfoque del fondo")));
            c.addView(segmented(new String[] {"NO", Tr.t("SUAVE"), "NORMAL"}, prefs.getInt("blur", 2),
                    new Pick() {
                        @Override
                        public void on(int v) {
                            prefs.edit().putInt("blur", v).apply();
                            changed();
                        }
                    }));
        }

        // ICONO
        c = card(list, Tr.t("ICONO DE LA APP"));
        c.addView(muted(Tr.t("Elige el icono del lanzador. Puede tardar unos segundos en cambiar.")));
        iconCells = new ImageView[ICON_COUNT];
        for (int r = 0; r < 2; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(10), 0, 0);
            for (int k = 0; k < 4; k++) {
                final int n = r * 4 + k + 1;
                ImageView iv = new ImageView(this);
                int id = getResources().getIdentifier("ic_icon" + n, "drawable", getPackageName());
                if (id != 0) {
                    iv.setImageResource(id);
                }
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                iv.setPadding(dp(4), dp(4), dp(4), dp(4));
                iv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setIcon(n);
                    }
                });
                iconCells[n - 1] = iv;
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(64), 1f);
                lp.leftMargin = dp(3);
                lp.rightMargin = dp(3);
                row.addView(iv, lp);
            }
            c.addView(row);
        }
        refreshIcons();

        TextView about = new TextView(this);
        about.setText("SCRATCH 0.29");
        about.setTextSize(11);
        about.setTextColor(th.ink2);
        about.setTypeface(th.mono);
        about.setLetterSpacing(0.15f);
        about.setGravity(Gravity.CENTER);
        about.setPadding(0, dp(18), 0, dp(10));
        list.addView(about);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(false);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(list);
        scroll = sv;
        if (savedScroll > 0) {
            final int y = savedScroll;
            savedScroll = -1;
            sv.post(new Runnable() {
                @Override
                public void run() {
                    scroll.scrollTo(0, y);
                }
            });
        }
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private static final int ICON_COUNT = 8;
    private ImageView[] iconCells;

    private void refreshIcons() {
        int sel = prefs.getInt("icon", 1);
        for (int i = 0; i < iconCells.length; i++) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setStroke(dp(2), i + 1 == sel ? th.accent : 0x00000000);
            iconCells[i].setBackground(g);
        }
    }

    /** Cambia el icono activando solo el alias de lanzador elegido. */
    private void setIcon(int n) {
        android.content.pm.PackageManager pm = getPackageManager();
        String pkg = getPackageName();
        pm.setComponentEnabledSetting(new android.content.ComponentName(pkg, "dev.scratchwheel.Icon" + n),
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                android.content.pm.PackageManager.DONT_KILL_APP);
        for (int i = 1; i <= ICON_COUNT; i++) {
            if (i != n) {
                pm.setComponentEnabledSetting(new android.content.ComponentName(pkg, "dev.scratchwheel.Icon" + i),
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        android.content.pm.PackageManager.DONT_KILL_APP);
            }
        }
        prefs.edit().putInt("icon", n).apply();
        refreshIcons();
    }

    private interface Pick {
        void on(int v);
    }

    private interface Toggle {
        void on(boolean v);
    }

    private TextView body(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setTextColor(th.ink);
        t.setTypeface(th.regular);
        return t;
    }

    private TextView muted(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(th.ink2);
        t.setTypeface(th.regular);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    private LinearLayout card(LinearLayout parent, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(th.ink2);
        l.setTypeface(th.mono);
        l.setLetterSpacing(0.12f);
        l.setPadding(dp(8), dp(16), 0, dp(8));
        parent.addView(l);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(th.card(Lucent.R_L));
        c.setPadding(dp(22), dp(18), dp(22), dp(18));
        parent.addView(c, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c;
    }

    /** Grupo sin tarjeta (chips directamente sobre el fondo), como Idioma/Modo en Dot Calendar. */
    private LinearLayout plain(LinearLayout parent, String label) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(11);
        l.setTextColor(th.ink2);
        l.setTypeface(th.mono);
        l.setLetterSpacing(0.12f);
        l.setPadding(dp(8), dp(16), 0, dp(2));
        parent.addView(l);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        parent.addView(c, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private View dots() {
        View v = new View(this);
        v.setBackground(new android.graphics.drawable.ColorDrawable(th.ghost));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(14);
        v.setLayoutParams(lp);
        return v;
    }

    private View segmented(String[] opts, int selected, final Pick pick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        for (int i = 0; i < opts.length; i++) {
            final int value = i;
            TextView b = th.chip(this, opts[i], i == selected, opts.length > 4 ? 12 : 18);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.on(value);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(b, lp);
        }
        return row;
    }

    private View actionRow(String label, String action, final Runnable r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(4));
        row.addView(body(label), new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView b = th.chip(this, action, false, 18);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        row.addView(b, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private View switchRow(String label, String desc, boolean on, boolean enabled,
            final Toggle t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.addView(body(label));
        TextView d = muted(desc);
        d.setPadding(0, dp(2), 0, 0);
        txt.addView(d);
        row.addView(txt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        sw.setEnabled(enabled);
        sw.setThumbTintList(ColorStateList.valueOf(on ? th.accent : th.dotOff));
        sw.setTrackTintList(ColorStateList.valueOf(Lucent.withAlpha(on ? th.accent : th.ink, 0.35f)));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                t.on(checked);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = dp(12);
        row.addView(sw, lp);
        return row;
    }

    private View swatches() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(10), 0, dp(4));
        int sel = prefs.getInt("accent", 0);
        boolean wall = prefs.getBoolean("wall", false) && Build.VERSION.SDK_INT >= 27;
        for (int i = 0; i < Lucent.ACCENTS.length; i++) {
            final int value = i;
            int col = Lucent.ACCENTS[i] == 0 ? th.ink : Lucent.ACCENTS[i];
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            if (i == sel && !wall) {
                g.setStroke(dp(3), th.ink);
            } else {
                g.setStroke(dp(1), th.glassLine);
            }
            v.setBackground(g);
            v.setAlpha(wall ? 0.4f : 1f);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    prefs.edit().putInt("accent", value).putBoolean("wall", false).apply();
                    changed();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(10);
            row.addView(v, lp);
        }
        hs.addView(row);
        return hs;
    }
}
