package dev.scratchwheel;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Diálogo propio con el estilo de la app: tarjeta redondeada, opciones como botones y botones de acción. */
final class DotDialog {
    private final Activity a;
    private final Lucent th;
    private final Dialog dialog;
    private final LinearLayout box;
    private final LinearLayout body;
    private final List<Button> actions = new ArrayList<Button>();

    DotDialog(Activity a, Lucent th) {
        this.a = a;
        this.th = th;
        dialog = new Dialog(a);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Lucent.withAlpha(th.solid, 0.98f));
        bg.setCornerRadius(th.dp(Lucent.R_L));
        bg.setStroke(th.dp(1), th.glassLine);
        box.setBackground(bg);
        box.setPadding(th.dp(20), th.dp(22), th.dp(20), th.dp(16));
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
    }

    DotDialog title(String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(15);
        t.setTextColor(th.ink);
        t.setTypeface(th.semi);
        t.setLetterSpacing(0.08f);
        t.setPadding(th.dp(2), 0, th.dp(2), th.dp(12));
        box.addView(t);
        return this;
    }

    DotDialog message(String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(th.ink2);
        t.setTypeface(th.regular);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(th.dp(2), 0, th.dp(2), th.dp(8));
        body.addView(t);
        return this;
    }

    private GradientDrawable pill(float fillAlpha, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Lucent.withAlpha(th.ink, fillAlpha));
        g.setCornerRadius(th.dp(Lucent.R_M));
        g.setStroke(th.dp(1), stroke);
        return g;
    }

    private TextView row(String label) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(12.5f);
        t.setTextColor(th.ink);
        t.setTypeface(th.semi);
        t.setLetterSpacing(0.06f);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(th.dp(52));
        t.setPadding(th.dp(16), th.dp(10), th.dp(16), th.dp(10));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(Lucent.withAlpha(th.ink, 0.18f)),
                pill(0.07f, th.glassLine), null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = th.dp(8);
        t.setLayoutParams(lp);
        return t;
    }

    /** Opciones apiladas como botones; al tocar una se cierra y se avisa con su posición. */
    DotDialog options(String[] labels, final DotMenu.Picked cb) {
        for (int i = 0; i < labels.length; i++) {
            final int id = i;
            TextView t = row(labels[i]);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    cb.onPick(id);
                }
            });
            body.addView(t);
        }
        return this;
    }

    /** Lista de casillas: tocar alterna. El estado queda en el array que pasas. */
    DotDialog multi(String[] labels, final boolean[] on) {
        for (int i = 0; i < labels.length; i++) {
            final int id = i;
            final TextView t = row(labels[i]);
            t.setCompoundDrawablePadding(th.dp(12));
            paintCheck(t, on[id]);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    on[id] = !on[id];
                    paintCheck(t, on[id]);
                }
            });
            body.addView(t);
        }
        return this;
    }

    private void paintCheck(TextView t, boolean on) {
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setSize(th.dp(14), th.dp(14));
        if (on) {
            dot.setColor(th.accent);
            dot.setStroke(th.dp(2), th.accent);
        } else {
            dot.setColor(0);
            dot.setStroke(th.dp(2), th.ink2);
        }
        t.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null);
        t.setTextColor(on ? th.ink : th.ink2);
    }

    EditText input(String hint) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTextSize(14);
        e.setTextColor(th.ink);
        e.setHintTextColor(th.ink2);
        e.setTypeface(th.regular);
        e.setPadding(th.dp(16), th.dp(12), th.dp(16), th.dp(12));
        e.setBackground(pill(0.07f, th.glassLine));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = th.dp(8);
        body.addView(e, lp);
        return e;
    }

    /** Botón de acción abajo. kind: Lucent.ACCENT (principal) o Lucent.GLASS (secundario). Cierra al tocar. */
    DotDialog button(String label, int kind, final Runnable r) {
        Button b = new Button(a);
        b.setText(label);
        th.style(b, kind);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (r != null) {
                    r.run();
                }
            }
        });
        actions.add(b);
        return this;
    }

    /** Botón que no cierra el diálogo (para validar antes). */
    Button rawButton(String label, int kind) {
        Button b = new Button(a);
        b.setText(label);
        th.style(b, kind);
        actions.add(b);
        return b;
    }

    void dismiss() {
        dialog.dismiss();
    }

    void show() {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        ScrollView sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(body);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        if (!actions.isEmpty()) {
            boolean vertical = actions.size() > 2;
            LinearLayout bar = new LinearLayout(a);
            bar.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
            bar.setPadding(0, th.dp(12), 0, 0);
            for (Button b : actions) {
                LinearLayout.LayoutParams lp = vertical
                        ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, th.dp(48))
                        : new LinearLayout.LayoutParams(0, th.dp(48), 1f);
                lp.topMargin = vertical ? th.dp(8) : 0;
                lp.leftMargin = vertical ? 0 : th.dp(4);
                lp.rightMargin = vertical ? 0 : th.dp(4);
                bar.addView(b, lp);
            }
            box.addView(bar);
        }
        dialog.setContentView(box);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setDimAmount(0.6f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setLayout((int) (dm.widthPixels * 0.90f), (int) (dm.heightPixels * 0.80f));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            w.setAttributes(lp);
        }
        dialog.show();
    }
}
