package dev.scratchwheel;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Menú desplegable propio: tarjeta redondeada con opciones en mayúsculas, sin el PopupMenu del sistema. */
final class DotMenu {
    interface Picked {
        void onPick(int id);
    }

    private DotMenu() {
    }

    static void show(Context c, Lucent th, View anchor, String[] labels, final Picked cb) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Lucent.withAlpha(th.solid, 0.97f));
        bg.setCornerRadius(th.dp(Lucent.R_M));
        bg.setStroke(th.dp(1), th.glassLine);
        box.setBackground(bg);
        box.setPadding(th.dp(6), th.dp(6), th.dp(6), th.dp(6));
        final PopupWindow pw = new PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, true);
        for (int i = 0; i < labels.length; i++) {
            final int id = i;
            TextView t = new TextView(c);
            t.setText(labels[i]);
            t.setTextSize(13);
            t.setTextColor(th.ink);
            t.setTypeface(th.semi);
            t.setLetterSpacing(0.1f);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(th.dp(18), 0, th.dp(32), 0);
            GradientDrawable press = new GradientDrawable();
            press.setColor(Lucent.withAlpha(th.ink, 0.10f));
            press.setCornerRadius(th.dp(Lucent.R_S));
            StateListDrawable sl = new StateListDrawable();
            sl.addState(new int[] {android.R.attr.state_pressed}, press);
            t.setBackground(sl);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pw.dismiss();
                    cb.onPick(id);
                }
            });
            box.addView(t, new LinearLayout.LayoutParams(th.dp(210), th.dp(48)));
        }
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setElevation(th.dp(10));
        pw.showAsDropDown(anchor, anchor.getWidth() - th.dp(222), -th.dp(6));
    }
}
