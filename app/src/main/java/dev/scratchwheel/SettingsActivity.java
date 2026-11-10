package dev.scratchwheel;

import android.app.Activity;
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
            recreate();
        }
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private void changed() {
        Lucent.stamp++;
        recreate();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(14), dp(8), dp(14), dp(10));

        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText("AJUSTES");
        title.setPadding(dp(8), 0, 0, dp(6));
        root.addView(title);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        // TEMA
        LinearLayout c = card(list, "TEMA");
        c.addView(segmented(new String[] {"SISTEMA", "CLARO", "OSCURO"}, prefs.getInt("mode", 0),
                new Pick() {
                    @Override
                    public void on(int v) {
                        prefs.edit().putInt("mode", v).apply();
                        changed();
                    }
                }));

        // COLORES
        c = card(list, "COLORES");
        boolean wallOk = Build.VERSION.SDK_INT >= 27;
        c.addView(switchRow("Colores del fondo",
                wallOk ? "Usa el color principal de tu fondo de pantalla como acento."
                        : "Requiere Android 8.1 o superior.",
                prefs.getBoolean("wall", false) && wallOk, wallOk, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wall", v).apply();
                        changed();
                    }
                }));
        c.addView(dots());
        TextView acc = body("Color de acento");
        c.addView(acc);
        c.addView(swatches());

        // TIPOGRAFIA
        c = card(list, "TIPOGRAFÍA");
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
        prev.setPadding(0, dp(18), 0, dp(6));
        c.addView(prev);
        TextView sample = body("The quick brown fox jumps over the lazy dog.");
        c.addView(sample);

        // RUEDA
        c = card(list, "RUEDA");
        c.addView(switchRow("Carátula del disco",
                "Muestra la imagen incrustada del archivo en vez del plato de puntos.",
                prefs.getBoolean("cover", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("cover", v).apply();
                    }
                }));
        c.addView(dots());
        c.addView(switchRow("Bloquear scratch",
                "La rueda solo gira; el centro sigue haciendo play y pausa.",
                prefs.getBoolean("wlock", false), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("wlock", v).apply();
                    }
                }));

        // TEMPORIZADOR
        int left = Player.I.sleepMinutes();
        c = card(list, "TEMPORIZADOR DE APAGADO");
        c.addView(muted(left > 0 ? "Se apagará en " + left + " min." : "La música no se apagará sola."));
        final int[] mins = {0, 15, 30, 60, 90};
        c.addView(segmented(new String[] {"NO", "15", "30", "60", "90"}, left == 0 ? 0 : -1,
                new Pick() {
                    @Override
                    public void on(int v) {
                        Player.I.setSleepMinutes(mins[v]);
                        recreate();
                    }
                }));

        // CRISTAL
        c = card(list, "CRISTAL");
        c.addView(switchRow("Estilo Lucent",
                "Tarjetas de cristal sobre tu fondo. Apagado: tarjetas sólidas.",
                prefs.getBoolean("lucent", true), true, new Toggle() {
                    @Override
                    public void on(boolean v) {
                        prefs.edit().putBoolean("lucent", v).apply();
                        changed();
                    }
                }));
        if (Build.VERSION.SDK_INT >= 31 && th.lucent) {
            c.addView(dots());
            c.addView(body("Desenfoque del fondo"));
            c.addView(segmented(new String[] {"NO", "SUAVE", "NORMAL"}, prefs.getInt("blur", 2),
                    new Pick() {
                        @Override
                        public void on(int v) {
                            prefs.edit().putInt("blur", v).apply();
                            changed();
                        }
                    }));
        }

        // ICONO
        c = card(list, "ICONO DE LA APP");
        c.addView(muted("Elige el icono del lanzador. Puede tardar unos segundos en cambiar."));
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
        about.setText("SCRATCH 0.17");
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
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
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
            Button b = new Button(this);
            b.setText(opts[i]);
            th.style(b, i == selected ? Lucent.ACCENT : Lucent.SOLID);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.on(value);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            row.addView(b, lp);
        }
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
