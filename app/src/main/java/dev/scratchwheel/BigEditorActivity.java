package dev.scratchwheel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Editor de Big Picture a pantalla completa: se ve la rejilla con cada módulo como una pieza; se arrastra para
 * moverla, se cambia su ancho y alto, se quita o se añade uno nuevo. Todo se guarda al momento (pref "bp_cfg").
 */
public class BigEditorActivity extends Activity {
    private Lucent th;
    private android.content.SharedPreferences sp;
    private List<BigLayout.Item> items;
    private BigLayout.Item selected;
    private GridView grid;
    private TextView info;
    private LinearLayout selRow;
    private ScrollView scroll;

    private int dp(float v) {
        return th.dp(v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        th = Lucent.load(this);
        th.apply(this);
        sp = Lucent.prefs(this);
        items = BigLayout.parse(sp.getString("bp_cfg", null));
        BigLayout.freeze(items, BigLayout.COLS);
        save();
        setContentView(buildUi());
        refreshBar();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        sp.edit().putString("bp_cfg", BigLayout.serialize(items)).apply();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Lucent.insets(root, dp(12), dp(8), dp(12), dp(10));

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
        TextView title = new TextView(this);
        title.setText(Tr.t("PERSONALIZAR BIG PICTURE"));
        title.setTextSize(11);
        title.setTextColor(th.accent);
        title.setTypeface(th.mono);
        title.setLetterSpacing(0.12f);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final DotIconView menu = new DotIconView(this, th);
        menu.set(DotIcons.MENU, th.ink);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DotMenu.show(BigEditorActivity.this, th, menu, new String[] {Tr.t("RESTABLECER")},
                        new DotMenu.Picked() {
                            @Override
                            public void onPick(int id) {
                                sp.edit().remove("bp_cfg").apply();
                                items = BigLayout.parse(null);
                                BigLayout.freeze(items, BigLayout.COLS);
                                selected = null;
                                save();
                                grid.relayout();
                                refreshBar();
                            }
                        });
            }
        });
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header);

        info = new TextView(this);
        info.setTextSize(11);
        info.setTextColor(th.ink2);
        info.setTypeface(th.mono);
        info.setPadding(dp(8), dp(2), dp(8), dp(8));
        root.addView(info);

        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid = new GridView(this);
        scroll.addView(grid, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        selRow = new LinearLayout(this);
        selRow.setOrientation(LinearLayout.HORIZONTAL);
        selRow.setGravity(Gravity.CENTER);
        selRow.setPadding(0, dp(8), 0, 0);
        root.addView(selRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(0, dp(6), 0, 0);
        actions.addView(barChip(Tr.t("+ AÑADIR"), false, new Runnable() {
            @Override
            public void run() {
                addModule();
            }
        }));
        actions.addView(barChip(Tr.t("VISTA PREVIA"), false, new Runnable() {
            @Override
            public void run() {
                save();
                startActivity(new Intent(BigEditorActivity.this, BigPictureActivity.class));
            }
        }));
        actions.addView(barChip(Tr.t("LISTO"), true, new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }));
        root.addView(actions);
        return root;
    }

    private View barChip(String label, boolean on, final Runnable r) {
        TextView c = th.chip(this, label, on, 10);
        c.setTextSize(10);
        c.setPadding(c.getPaddingLeft(), dp(10), c.getPaddingRight(), dp(10));
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                r.run();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        c.setLayoutParams(lp);
        return c;
    }

    private void refreshBar() {
        selRow.removeAllViews();
        if (selected == null) {
            info.setText(Tr.t("Toca un módulo para elegirlo; arrástralo para moverlo."));
            return;
        }
        int ix = BigLayout.index(selected.id);
        info.setText(Tr.t(BigLayout.NAMES[ix]) + " · " + selected.w() + "×" + selected.h());
        selRow.addView(barChip("◂ " + Tr.t("ANCHO"), false, new Runnable() {
            @Override
            public void run() {
                resize(-1, 0);
            }
        }));
        selRow.addView(barChip(Tr.t("ANCHO") + " ▸", false, new Runnable() {
            @Override
            public void run() {
                resize(1, 0);
            }
        }));
        selRow.addView(barChip("▴ " + Tr.t("ALTO"), false, new Runnable() {
            @Override
            public void run() {
                resize(0, -1);
            }
        }));
        selRow.addView(barChip(Tr.t("ALTO") + " ▾", false, new Runnable() {
            @Override
            public void run() {
                resize(0, 1);
            }
        }));
        selRow.addView(barChip(Tr.t("QUITAR"), false, new Runnable() {
            @Override
            public void run() {
                selected.on = false;
                selected.col = -1;
                selected.row = -1;
                selected = null;
                save();
                grid.relayout();
                refreshBar();
            }
        }));
    }

    private void resize(int dw, int dh) {
        if (selected == null) {
            return;
        }
        int w = Math.max(1, Math.min(BigLayout.COLS, selected.w() + dw));
        int h = Math.max(1, Math.min(BigLayout.MAX_H, selected.h() + dh));
        if (!BigLayout.canPlace(items, selected, selected.col, selected.row, w, h, BigLayout.COLS)) {
            Toast.makeText(this, Tr.t("No cabe ahí"), Toast.LENGTH_SHORT).show();
            return;
        }
        selected.cw = w;
        selected.ch = h;
        save();
        grid.relayout();
        refreshBar();
    }

    private void addModule() {
        final ArrayList<BigLayout.Item> off = new ArrayList<BigLayout.Item>();
        ArrayList<String> labels = new ArrayList<String>();
        for (BigLayout.Item it : items) {
            if (!it.on) {
                off.add(it);
                labels.add(Tr.t(BigLayout.NAMES[BigLayout.index(it.id)]));
            }
        }
        if (off.isEmpty()) {
            Toast.makeText(this, Tr.t("Ya están todos los módulos"), Toast.LENGTH_SHORT).show();
            return;
        }
        new DotDialog(this, th).title(Tr.t("AÑADIR MÓDULO")).options(labels.toArray(new String[0]),
                new DotMenu.Picked() {
                    @Override
                    public void onPick(int id) {
                        BigLayout.Item it = off.get(id);
                        it.cw = 0;
                        it.ch = 0;
                        int w = it.w();
                        int h = it.h();
                        int[] at = BigLayout.firstFree(items, it, w, h, BigLayout.COLS);
                        it.on = true;
                        it.col = at[0];
                        it.row = at[1];
                        it.cw = w;
                        it.ch = h;
                        selected = it;
                        save();
                        grid.relayout();
                        refreshBar();
                    }
                }).button(Tr.t("CANCELAR"), Lucent.GLASS, null).show();
    }

    /** Rejilla con las piezas; se arrastran para moverlas. */
    private final class GridView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private BigLayout.Item drag;
        private float grabX;
        private float grabY;
        private float fx;
        private float fy;
        private int tc;
        private int tr;
        private boolean valid;

        GridView(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            txt.setTypeface(th.mono);
            dot.setColor(th.ghost);
        }

        private int cell() {
            return Math.max(dp(40), getWidth() / BigLayout.COLS);
        }

        private int rowsShown() {
            int n = 0;
            for (BigLayout.Item it : items) {
                if (it.on && it.fixed()) {
                    n = Math.max(n, it.row + it.h());
                }
            }
            return Math.max(10, n + 3);
        }

        void relayout() {
            requestLayout();
            invalidate();
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            int cell = Math.max(dp(40), w / BigLayout.COLS);
            setMeasuredDimension(w, cell * rowsShown());
        }

        private BigLayout.Item hit(float x, float y) {
            int cell = cell();
            for (int i = items.size() - 1; i >= 0; i--) {
                BigLayout.Item it = items.get(i);
                if (!it.on || !it.fixed()) {
                    continue;
                }
                if (x >= it.col * cell && x < (it.col + it.w()) * cell && y >= it.row * cell
                        && y < (it.row + it.h()) * cell) {
                    return it;
                }
            }
            return null;
        }

        @Override
        protected void onDraw(Canvas c) {
            int cell = cell();
            int rows = rowsShown();
            for (int y = 0; y <= rows; y++) {
                for (int x = 0; x <= BigLayout.COLS; x++) {
                    c.drawCircle(x * cell, y * cell, dp(1.4f), dot);
                }
            }
            for (BigLayout.Item it : items) {
                if (!it.on || !it.fixed() || it == drag) {
                    continue;
                }
                tile(c, it, it.col * cell, it.row * cell, cell, it == selected, 1f);
            }
            if (drag != null) {
                float gx = tc * cell;
                float gy = tr * cell;
                r.set(gx + dp(3), gy + dp(3), gx + drag.w() * cell - dp(3), gy + drag.h() * cell - dp(3));
                line.setStrokeWidth(dp(2));
                line.setColor(valid ? th.accent : 0xFFD71921);
                c.drawRoundRect(r, dp(14), dp(14), line);
                tile(c, drag, fx - grabX, fy - grabY, cell, true, 0.85f);
            }
        }

        private void tile(Canvas c, BigLayout.Item it, float x, float y, int cell, boolean sel, float alpha) {
            r.set(x + dp(3), y + dp(3), x + it.w() * cell - dp(3), y + it.h() * cell - dp(3));
            fill.setColor(sel ? Lucent.withAlpha(th.accent, 0.18f * alpha) : Lucent.withAlpha(th.ink, 0.09f * alpha));
            c.drawRoundRect(r, dp(14), dp(14), fill);
            line.setStrokeWidth(dp(sel ? 2 : 1));
            line.setColor(sel ? th.accent : th.glassLine);
            c.drawRoundRect(r, dp(14), dp(14), line);
            txt.setColor(th.ink);
            txt.setTextSize(dp(11));
            String name = Tr.t(BigLayout.NAMES[BigLayout.index(it.id)]);
            c.save();
            c.clipRect(r);
            c.drawText(name, r.left + dp(10), r.top + dp(20), txt);
            txt.setColor(th.ink2);
            txt.setTextSize(dp(10));
            c.drawText(it.w() + "×" + it.h(), r.left + dp(10), r.bottom - dp(10), txt);
            c.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int cell = cell();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    BigLayout.Item it = hit(e.getX(), e.getY());
                    selected = it;
                    refreshBar();
                    if (it != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        drag = it;
                        grabX = e.getX() - it.col * cell;
                        grabY = e.getY() - it.row * cell;
                        fx = e.getX();
                        fy = e.getY();
                        tc = it.col;
                        tr = it.row;
                        valid = true;
                    }
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (drag != null) {
                        fx = e.getX();
                        fy = e.getY();
                        tc = Math.max(0, Math.min(BigLayout.COLS - drag.w(), Math.round((fx - grabX) / cell)));
                        tr = Math.max(0, Math.round((fy - grabY) / cell));
                        valid = BigLayout.canPlace(items, drag, tc, tr, drag.w(), drag.h(), BigLayout.COLS);
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    if (drag != null) {
                        if (valid && e.getActionMasked() == MotionEvent.ACTION_UP) {
                            drag.col = tc;
                            drag.row = tr;
                            save();
                        }
                        drag = null;
                        refreshBar();
                        relayout();
                    }
                    return true;
                }
                default:
                    return true;
            }
        }
    }
}
