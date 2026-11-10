package dev.scratchwheel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/** Plato circular de puntos estilo Nothing. Gira con la canción y se puede arrastrar para hacer scratch. */
public class JogWheelView extends View {
    public interface Listener {
        void onTouchDown();

        void onRotate(double radians);

        void onTouchUp();

        void onHubTap();
    }

    private static final double TWO_PI = 2 * Math.PI;
    private static final float HUB = 0.25f;

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float density;
    private final int ringIdle;
    private final int ringHub;
    private final int accentColor;
    private final int glyphColor;

    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private Bitmap cover;
    private boolean locked;
    private boolean coverOn;
    private Listener listener;
    private boolean haptic = true;
    private double hapticAcc;

    public void setHaptic(boolean on) {
        haptic = on;
    }
    private float angle;
    private boolean touching;
    private boolean hubDown;
    private boolean playing;
    private double lastTouchAngle;

    public JogWheelView(Context context, Lucent t) {
        super(context);
        accentColor = t.accent;
        ringIdle = Lucent.withAlpha(t.ink, 0.30f);
        ringHub = t.dotOff;
        density = context.getResources().getDisplayMetrics().density;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(1.5f));
        dots.setStyle(Paint.Style.FILL);
        dots.setColor(Lucent.withAlpha(t.ink, t.dark ? 0.62f : 0.55f));
        accent.setStyle(Paint.Style.FILL);
        accent.setColor(accentColor);
        glyph.setStyle(Paint.Style.FILL);
        glyphColor = t.ink;
        glyph.setColor(t.ink);
    }

    /** Bloqueada: la rueda solo gira; solo responde el centro (play/pausa). */
    public void setLocked(boolean l) {
        if (l != locked) {
            locked = l;
            touching = false;
            invalidate();
        }
    }

    /** Dibuja la carátula del archivo en vez de los puntos (si el archivo tiene carátula). */
    public void setCoverOn(boolean on) {
        if (on != coverOn) {
            coverOn = on;
            invalidate();
        }
    }

    public void setCover(Bitmap b) {
        cover = b;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public void setPlayingState(boolean p) {
        if (p != playing) {
            playing = p;
            invalidate();
        }
    }

    public void setAngle(float radians) {
        float a = (float) (radians % TWO_PI);
        if (a != angle) {
            angle = a;
            invalidate();
        }
    }

    private float dp(float v) {
        return v * density;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float r = Math.min(w, h) / 2f - dp(6);

        ring.setColor(touching ? accentColor : ringIdle);
        c.drawCircle(cx, cy, r, ring);

        boolean showCover = coverOn && cover != null;
        if (showCover) {
            clip.reset();
            clip.addCircle(cx, cy, r - dp(2), Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.rotate((float) Math.toDegrees(angle), cx, cy);
            box.set(cx - r, cy - r, cx + r, cy + r);
            c.drawBitmap(cover, null, box, bmp);
            c.restore();
            scrim.setColor(0x55000000);
            c.drawCircle(cx, cy, r - dp(2), scrim);
        } else {
            float dotR = dp(1.7f);
            int rings = 7;
            for (int k = 0; k < rings; k++) {
                float rad = r * (0.38f + 0.085f * k);
                int n = Math.max(6, Math.round((float) (TWO_PI * rad / dp(14))));
                double step = TWO_PI / n;
                double stagger = (k % 2) * step / 2;
                for (int i = 0; i < n; i++) {
                    double a = angle - Math.PI / 2 + i * step + stagger;
                    float x = cx + (float) Math.cos(a) * rad;
                    float y = cy + (float) Math.sin(a) * rad;
                    c.drawCircle(x, y, dotR, dots);
                }
            }

        }

        double m = angle - Math.PI / 2;
        c.drawCircle(cx + (float) Math.cos(m) * r * 0.95f, cy + (float) Math.sin(m) * r * 0.95f,
                dp(5), accent);

        if (showCover) {
            hubFill.setColor(0xAA000000);
            c.drawCircle(cx, cy, r * HUB, hubFill);
        }
        ring.setColor(hubDown ? accentColor : (showCover ? 0x99FFFFFF : ringHub));
        c.drawCircle(cx, cy, r * HUB, ring);
        glyph.setColor(showCover ? 0xFFFFFFFF : glyphColor);

        String[] g = playing ? DotIcons.PAUSE : DotIcons.PLAY;
        float size = r * HUB * 1.1f;
        float cell = size / DotIcons.N;
        float x0 = cx - size / 2f;
        float y0 = cy - size / 2f;
        for (int y = 0; y < DotIcons.N; y++) {
            for (int x = 0; x < DotIcons.N; x++) {
                if (g[y].charAt(x) == '1') {
                    c.drawCircle(x0 + (x + 0.5f) * cell, y0 + (y + 0.5f) * cell, cell * 0.38f, glyph);
                }
            }
        }
    }

    private double touchAngle(MotionEvent e) {
        return Math.atan2(e.getY() - getHeight() / 2f, e.getX() - getWidth() / 2f);
    }

    private boolean inHub(MotionEvent e) {
        float dx = e.getX() - getWidth() / 2f;
        float dy = e.getY() - getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f - dp(6);
        return Math.hypot(dx, dy) < r * HUB;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                if (!locked && getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (inHub(e)) {
                    hubDown = true;
                    invalidate();
                    return true;
                }
                if (locked) {
                    return true; // bloqueada: solo el centro responde
                }
                touching = true;
                lastTouchAngle = touchAngle(e);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                if (listener != null) {
                    listener.onTouchDown();
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!touching) {
                    return true;
                }
                double a = touchAngle(e);
                double d = a - lastTouchAngle;
                while (d > Math.PI) {
                    d -= TWO_PI;
                }
                while (d < -Math.PI) {
                    d += TWO_PI;
                }
                lastTouchAngle = a;
                if (haptic) {
                    hapticAcc += Math.abs(d);
                    if (hapticAcc >= 0.30) {
                        hapticAcc = 0;
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    }
                }
                if (listener != null && d != 0) {
                    listener.onRotate(d);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (hubDown) {
                    boolean tap = e.getActionMasked() == MotionEvent.ACTION_UP && inHub(e);
                    hubDown = false;
                    invalidate();
                    if (tap && listener != null) {
                        listener.onHubTap();
                    }
                    return true;
                }
                if (touching) {
                    touching = false;
                    if (listener != null) {
                        listener.onTouchUp();
                    }
                    invalidate();
                }
                return true;
            }
            default:
                return true;
        }
    }
}
