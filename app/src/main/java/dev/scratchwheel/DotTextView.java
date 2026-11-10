package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Texto dibujado con puntos (estilo Doto). Los puntos apagados se ven como "fantasma". Los dos puntos van en el acento. */
public class DotTextView extends View {
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint acc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final boolean dots;
    private final android.graphics.Typeface face;
    private String text = "";
    private String[] lines = {""};
    private float cell;

    public DotTextView(Context c, float cellDp, Lucent t) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        dots = t.dotTitles;
        face = t.semi;
        on.setTypeface(face);
        acc.setTypeface(face);
        cell = cellDp * density;
        setColors(t.ink, t.ghost, t.accent);
    }

    public void setColors(int onColor, int ghostColor, int accentColor) {
        on.setColor(onColor);
        off.setColor(ghostColor);
        acc.setColor(accentColor);
        invalidate();
    }

    public void setText(String s) {
        String v = s == null ? "" : s;
        if (!v.equals(text)) {
            boolean relayout = DotFont.cols(v) != DotFont.cols(text)
                    || v.split("\n", -1).length != lines.length;
            text = v;
            lines = v.split("\n", -1);
            if (relayout) {
                requestLayout();
            }
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = (dots ? (int) Math.ceil(DotFont.cols(text) * cell) : (int) Math.ceil(textWidth()))
                + getPaddingLeft() + getPaddingRight();
        int h = (int) Math.ceil((DotFont.ROWS + (lines.length - 1) * (DotFont.ROWS + 2)) * cell)
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
    }

    private float textWidth() {
        on.setTextSize(cell * 7f);
        float best = 0;
        for (String line : lines) {
            best = Math.max(best, on.measureText(DotFont.upper(line)));
        }
        return best;
    }

    @Override
    protected void onDraw(Canvas c) {
        c.translate(getPaddingLeft(), getPaddingTop());
        if (!dots) {
            // Modo Mono / Clean: texto normal con la misma altura que la matriz de puntos.
            on.setTextSize(cell * 7f);
            acc.setTextSize(cell * 7f);
            for (int li = 0; li < lines.length; li++) {
                float x = 0;
                float base = cell * 7f * 0.92f + li * (DotFont.ROWS + 2) * cell;
                String up = DotFont.upper(lines[li]);
                for (int i = 0; i < up.length(); i++) {
                    String ch = up.substring(i, i + 1);
                    Paint p = ch.equals(":") ? acc : on;
                    c.drawText(ch, x, base, p);
                    x += on.measureText(ch);
                }
            }
            return;
        }
        float r = cell * 0.36f;
        for (int li = 0; li < lines.length; li++) {
            String line = lines[li];
            float yo = li * (DotFont.ROWS + 2) * cell;
            int col = 0;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                int w = DotFont.width(ch);
                boolean colon = DotFont.fold(ch) == ':';
                boolean blank = DotFont.fold(ch) == ' ' && !DotFont.isKana(ch);
                for (int y = 0; y < DotFont.ROWS; y++) {
                    for (int x = 0; x < w; x++) {
                        if (blank) {
                            continue;
                        }
                        Paint p = DotFont.on(ch, x, y) ? (colon ? acc : on) : off;
                        c.drawCircle((col + x + 0.5f) * cell, yo + (y + 0.5f) * cell, r, p);
                    }
                }
                col += w + 1;
            }
        }
    }
}
