package dev.scratchwheel;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Primer inicio: 1) política de privacidad (hay que aceptarla), 2) apariencia, 3) permiso de notificaciones,
 * 4) carpeta de música (acceso por el selector del sistema, sin permisos de almacenamiento amplios).
 */
public class OnboardingActivity extends Activity {
    private static final int STEPS = 5;
    private static final int REQ_NOTIF = 10;

    private Lucent th;
    private SharedPreferences prefs;
    private int step;

    /** true si falta aceptar la política (o su versión nueva) o terminar la bienvenida. */
    static boolean needed(android.content.Context c) {
        SharedPreferences p = Lucent.prefs(c);
        return p.getInt("privacy_ok", 0) < Privacy.VERSION || !p.getBoolean("onboarded", false);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        prefs = Lucent.prefs(this);
        if (state != null) {
            step = state.getInt("step", 0);
        } else {
            step = prefs.getInt("privacy_ok", 0) >= Privacy.VERSION ? 2 : 0;
        }
        setContentView(build());
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("step", step);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (th.myStamp != Lucent.stamp) {
            recreate();
        }
    }

    @Override
    public void onBackPressed() {
        if (step == 1) {
            go(0);
        } else if (step > 2) {
            go(step - 1);
        } else {
            finishAffinity();
        }
    }

    private int dp(float v) {
        return th.dp(v);
    }

    private void go(int s) {
        step = s;
        setContentView(build());
    }

    private View build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(18), dp(14), dp(18), dp(14));

        TextView tag = text(Tr.t("BIENVENIDA · ") + (step + 1) + " / " + STEPS, 11, th.ink2, th.mono);
        tag.setLetterSpacing(0.12f);
        root.addView(tag);
        LinearLayout dots = new LinearLayout(this);
        dots.setPadding(0, dp(8), 0, dp(6));
        for (int i = 0; i < STEPS; i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i <= step ? th.accent : th.ghost);
            d.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(8), dp(8));
            lp.rightMargin = dp(8);
            dots.addView(d, lp);
        }
        root.addView(dots);

        DotTextView title = new DotTextView(this, 3.2f, th);
        title.setText(step == 0 ? "LANGUAGE" : step == 1 ? Tr.t("PRIVACIDAD") : step == 2 ? Tr.t("ASPECTO") : step == 3 ? Tr.t("AVISOS") : Tr.t("MÚSICA"));
        title.setPadding(0, dp(6), 0, dp(14));
        root.addView(title);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        switch (step) {
            case 0:
                stepLanguage(body, buttons);
                break;
            case 1:
                stepPrivacy(body, buttons);
                break;
            case 2:
                stepLook(body, buttons);
                break;
            case 3:
                stepNotifications(body, buttons);
                break;
            default:
                stepMusic(body, buttons);
                break;
        }
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(th.card(Lucent.R_L));
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.addView(body);
        sv.addView(card);
        root.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(12);
        root.addView(buttons, blp);
        return root;
    }

    // ------------------------------------------------------------------ pasos

    private void stepLanguage(LinearLayout body, LinearLayout buttons) {
        body.addView(text("Choose your language.\nElige tu idioma.", 15, th.ink, th.regular));
        TextView n = text("You can change it later in Settings.\nPodrás cambiarlo luego en Ajustes.", 12, th.ink2, th.regular);
        n.setPadding(0, dp(14), 0, 0);
        body.addView(n);
        buttons.addView(button("ENGLISH", Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                Tr.set(OnboardingActivity.this, Tr.EN);
                go(1);
            }
        }));
        buttons.addView(button("ESPAÑOL", Lucent.GLASS, new Runnable() {
            @Override
            public void run() {
                Tr.set(OnboardingActivity.this, Tr.ES);
                go(1);
            }
        }));
    }

    private void stepPrivacy(LinearLayout body, LinearLayout buttons) {
        body.addView(text(Tr.t("Antes de empezar, lee cómo trata Scratch tus datos. Resumen: todo se queda en tu móvil."),
                14, th.ink, th.regular));
        for (String[] s : Privacy.sections()) {
            TextView h = text(s[0], 11, th.accent, th.mono);
            h.setLetterSpacing(0.1f);
            h.setPadding(0, dp(16), 0, dp(4));
            body.addView(h);
            TextView b = text(s[1], 13, th.ink2, th.regular);
            b.setLineSpacing(0, 1.2f);
            body.addView(b);
        }
        TextView v = text(Tr.t("Versión ") + Privacy.VERSION + " · " + Privacy.date(), 11, th.ink2, th.mono);
        v.setPadding(0, dp(16), 0, 0);
        body.addView(v);
        buttons.addView(button(Tr.t("ACEPTO LA POLÍTICA DE PRIVACIDAD"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                prefs.edit().putInt("privacy_ok", Privacy.VERSION).apply();
                go(2);
            }
        }));
        buttons.addView(button(Tr.t("NO ACEPTO · SALIR"), Lucent.GLASS, new Runnable() {
            @Override
            public void run() {
                finishAffinity();
            }
        }));
    }

    private void stepLook(LinearLayout body, LinearLayout buttons) {
        body.addView(text(Tr.t("Elige el tema y el color de acento. Podrás cambiarlos cuando quieras en Ajustes."),
                14, th.ink, th.regular));
        TextView l = text(Tr.t("TEMA"), 11, th.ink2, th.mono);
        l.setLetterSpacing(0.12f);
        l.setPadding(0, dp(18), 0, dp(8));
        body.addView(l);
        LinearLayout row = new LinearLayout(this);
        String[] names = {Tr.t("SISTEMA"), Tr.t("CLARO"), Tr.t("OSCURO")};
        int sel = prefs.getInt("mode", 0);
        for (int i = 0; i < names.length; i++) {
            final int val = i;
            TextView c = th.chip(this, names[i], i == sel, 4);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.edit().putInt("mode", val).apply();
                    Lucent.stamp++;
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(6);
            row.addView(c, lp);
        }
        body.addView(row);
        TextView a = text(Tr.t("COLOR DE ACENTO"), 11, th.ink2, th.mono);
        a.setLetterSpacing(0.12f);
        a.setPadding(0, dp(20), 0, dp(4));
        body.addView(a);
        body.addView(swatches());
        buttons.addView(button(Tr.t("SIGUIENTE"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                go(3);
            }
        }));
    }

    private View swatches() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(8), 0, dp(4));
        int sel = prefs.getInt("accent", 0);
        for (int i = 0; i < Lucent.ACCENTS.length; i++) {
            if (Lucent.ACCENTS[i] == 0) {
                continue; // "del fondo de pantalla" se elige en Ajustes
            }
            final int value = i;
            View v = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(Lucent.ACCENTS[i]);
            g.setStroke(i == sel ? dp(3) : dp(1), i == sel ? th.ink : th.glassLine);
            v.setBackground(g);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    prefs.edit().putInt("accent", value).putBoolean("wall", false).apply();
                    Lucent.stamp++;
                    recreate();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
            lp.rightMargin = dp(10);
            row.addView(v, lp);
        }
        hs.addView(row);
        return hs;
    }

    private boolean notifGranted() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void stepNotifications(LinearLayout body, LinearLayout buttons) {
        body.addView(text(Tr.t("Scratch usa una notificación para mostrar los controles de reproducción (anterior, ")
                + Tr.t("pausa, siguiente) en la barra de estado y en la pantalla de bloqueo, y para que la música ")
                + Tr.t("siga sonando con la pantalla apagada."), 14, th.ink, th.regular));
        TextView st = text(notifGranted() ? (Build.VERSION.SDK_INT < 33 ? Tr.t("No hace falta permiso en tu versión de Android.")
                : Tr.t("PERMISO CONCEDIDO")) : Tr.t("Puedes cambiarlo luego en Ajustes del sistema."), 12, th.accent, th.mono);
        st.setPadding(0, dp(16), 0, 0);
        body.addView(st);
        if (!notifGranted()) {
            buttons.addView(button(Tr.t("PERMITIR NOTIFICACIONES"), Lucent.ACCENT, new Runnable() {
                @Override
                public void run() {
                    requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
                }
            }));
            buttons.addView(button(Tr.t("AHORA NO"), Lucent.GLASS, new Runnable() {
                @Override
                public void run() {
                    go(4);
                }
            }));
        } else {
            buttons.addView(button(Tr.t("SIGUIENTE"), Lucent.ACCENT, new Runnable() {
                @Override
                public void run() {
                    go(4);
                }
            }));
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_NOTIF) {
            go(4);
        }
    }

    private void stepMusic(LinearLayout body, LinearLayout buttons) {
        body.addView(text(Tr.t("Scratch reproduce los archivos de tu móvil. No necesita acceso a todo tu almacenamiento: ")
                + Tr.t("eliges una carpeta con el selector del sistema y la app solo lee esa carpeta."),
                14, th.ink, th.regular));
        TextView n = text(Tr.t("Puedes cambiarla o rescanearla cuando quieras desde el menú ⋮."), 12, th.ink2, th.regular);
        n.setPadding(0, dp(14), 0, 0);
        body.addView(n);
        buttons.addView(button(Tr.t("ELEGIR CARPETA DE MÚSICA"), Lucent.ACCENT, new Runnable() {
            @Override
            public void run() {
                prefs.edit().putBoolean("pick_pending", true).apply();
                finishOnboarding();
            }
        }));
        buttons.addView(button(Tr.t("MÁS TARDE"), Lucent.GLASS, new Runnable() {
            @Override
            public void run() {
                finishOnboarding();
            }
        }));
    }

    private void finishOnboarding() {
        prefs.edit().putBoolean("onboarded", true).apply();
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(String s, float sp, int color, android.graphics.Typeface face) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face);
        return t;
    }

    private Button button(String label, int kind, final Runnable r) {
        Button b = new Button(this);
        b.setText(label);
        th.style(b, kind);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        b.setGravity(Gravity.CENTER);
        return b;
    }
}
