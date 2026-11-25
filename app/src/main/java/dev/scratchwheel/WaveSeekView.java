package dev.scratchwheel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * Forma de onda de la pista que hace de barra de progreso: lo escuchado se pinta en color, lo que falta atenuado, y
 * tocar o arrastrar salta a esa posición. La usan Big Picture y la pantalla del scratch.
 */
public final class WaveSeekView extends View {
    private float[] peaks = new float[0];
    private String key = "";
    private float frac;
    private final Paint on = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint off = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean computing;

    private final Lucent th;
    /** Mientras se arrastra no se mueve con la reproducción. */
    boolean dragging;

    public WaveSeekView(Context c, Lucent th) {
        super(c);
        this.th = th;
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
        if (dragging) {
            return;
        }
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
        } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            Player.I.savePos();
        }
        dragging = e.getActionMasked() == MotionEvent.ACTION_DOWN || e.getActionMasked() == MotionEvent.ACTION_MOVE;
        return true;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
