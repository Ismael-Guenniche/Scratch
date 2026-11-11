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
    private final Paint groove = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF box = new RectF();
    private Bitmap cover;
    private boolean locked;
    private boolean coverOn;
    private int vinylSkin;
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
        groove.setStyle(Paint.Style.STROKE);
        groove.setStrokeWidth(dp(0.8f));
        groove.setColor(0x26FFFFFF);
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

    /** Aspecto del vinilo con portada: 0 clásico, 1 puntos, 2 rayas, 3 espiral, 4 color, 5 cristal, 6 neón. */
    public void setVinylSkin(int skin) {
        if (skin != vinylSkin) {
            vinylSkin = skin;
            invalidate();
        }
    }

    private void drawVinylBody(Canvas c, float cx, float cy, float r) {
        float outer = r - dp(2);
        float lab = r * 0.36f;
        int a = accentColor;
        switch (vinylSkin) {
            case 1: { // puntos
                scrim.setColor(0xFF101010);
                c.drawCircle(cx, cy, outer, scrim);
                float dotR = dp(1.5f);
                for (int k = 0; k < 9; k++) {
                    float rad = r * (0.47f + 0.055f * k);
                    int n = Math.max(6, Math.round((float) (TWO_PI * rad / dp(11))));
                    double step = TWO_PI / n;
                    double stagger = (k % 2) * step / 2;
                    for (int i = 0; i < n; i++) {
                        double an = angle - Math.PI / 2 + i * step + stagger;
                        c.drawCircle(cx + (float) Math.cos(an) * rad, cy + (float) Math.sin(an) * rad, dotR, dots);
                    }
                }
                break;
            }
            case 2: { // rayas radiales
                scrim.setColor(0xFF101010);
                c.drawCircle(cx, cy, outer, scrim);
                c.save();
                c.rotate((float) Math.toDegrees(angle), cx, cy);
                groove.setColor(0x40FFFFFF);
                for (int d = 0; d < 360; d += 8) {
                    double an = Math.toRadians(d);
                    c.drawLine(cx + (float) Math.cos(an) * lab * 1.25f, cy + (float) Math.sin(an) * lab * 1.25f,
                            cx + (float) Math.cos(an) * outer * 0.96f, cy + (float) Math.sin(an) * outer * 0.96f, groove);
                }
                c.restore();
                groove.setColor(0x26FFFFFF);
                c.drawCircle(cx, cy, outer * 0.97f, groove);
                break;
            }
            case 3: { // una sola espiral, como el surco real
                scrim.setColor(0xFF101010);
                c.drawCircle(cx, cy, outer, scrim);
                path.reset();
                float r0 = lab * 1.2f;
                float r1 = outer * 0.97f;
                int turns = 22;
                int steps = turns * 48;
                for (int i = 0; i <= steps; i++) {
                    float t = i / (float) steps;
                    float rad = r0 + (r1 - r0) * t;
                    double an = angle + t * turns * TWO_PI;
                    float x = cx + (float) Math.cos(an) * rad;
                    float y = cy + (float) Math.sin(an) * rad;
                    if (i == 0) {
                        path.moveTo(x, y);
                    } else {
                        path.lineTo(x, y);
                    }
                }
                groove.setColor(0x4DFFFFFF);
                c.drawPath(path, groove);
                groove.setColor(0x26FFFFFF);
                break;
            }
            case 4: { // color del tema
                scrim.setColor((a & 0x00FFFFFF) | 0xFF000000);
                c.drawCircle(cx, cy, outer, scrim);
                groove.setColor(0x33000000);
                for (float g = 0.44f; g < 0.97f; g += 0.04f) {
                    c.drawCircle(cx, cy, r * g, groove);
                }
                groove.setColor(0x26FFFFFF);
                break;
            }
            case 5: { // cristal: vinilo transparente
                scrim.setColor(0x40FFFFFF);
                c.drawCircle(cx, cy, outer, scrim);
                groove.setColor(0x55FFFFFF);
                for (float g = 0.44f; g < 0.97f; g += 0.05f) {
                    c.drawCircle(cx, cy, r * g, groove);
                }
                c.drawCircle(cx, cy, outer, groove);
                groove.setColor(0x26FFFFFF);
                break;
            }
            case 6: { // neón: tres aros de color
                scrim.setColor(0xFF0A0A0A);
                c.drawCircle(cx, cy, outer, scrim);
                groove.setColor(0x1FFFFFFF);
                for (float g = 0.44f; g < 0.97f; g += 0.06f) {
                    c.drawCircle(cx, cy, r * g, groove);
                }
                float w = groove.getStrokeWidth();
                groove.setStrokeWidth(dp(2.2f));
                groove.setColor((a & 0x00FFFFFF) | 0xCC000000);
                c.drawCircle(cx, cy, r * 0.50f, groove);
                c.drawCircle(cx, cy, r * 0.74f, groove);
                c.drawCircle(cx, cy, r * 0.95f, groove);
                groove.setStrokeWidth(w);
                groove.setColor(0x26FFFFFF);
                break;
            }
            default: { // clásico: plástico negro con surcos
                scrim.setColor(0xFF101010);
                c.drawCircle(cx, cy, outer, scrim);
                for (float g = 0.44f; g < 0.97f; g += 0.04f) {
                    c.drawCircle(cx, cy, r * g, groove);
                }
            }
        }
    }

    public void setCover(Bitmap b) {
        cover = b;
        invalidate();
    }

    public void setListener(Listener l) {
        listener = l;
    }

    private boolean disco;
    private float level;
    private final float[] hsv = {0f, 0.85f, 1f};
    private int baseDots;
    private boolean baseDotsSet;

    /** Modo disco: los puntos y el aro cambian de color y pulsan con el volumen. */
    public void setDisco(boolean on) {
        if (disco != on) {
            disco = on;
            if (!on) {
                level = 0;
                if (baseDotsSet) {
                    dots.setColor(baseDots);
                }
            }
            invalidate();
        }
    }

    public void setLevel(float l) {
        if (disco) {
            level = l;
            invalidate();
        }
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

    private void baseDot() {
        baseDots = dots.getColor();
        baseDotsSet = true;
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

        int dc = 0;
        if (disco) {
            if (!baseDotsSet) {
                baseDot();
            }
            hsv[0] = (System.nanoTime() / 6000000L) % 360;
            dc = android.graphics.Color.HSVToColor(hsv);
            dots.setColor(dc);
            accent.setColor(dc);
        } else if (accent.getColor() != accentColor) {
            accent.setColor(accentColor);
        }
        boolean showCover = coverOn && cover != null;
        boolean square = showCover && locked;
        if (!square) {
            ring.setColor(touching ? accentColor : (disco ? dc : ringIdle));
            c.drawCircle(cx, cy, r, ring);
        }

        if (square) {
            // Scratch bloqueado + portada: carátula cuadrada, quieta, con esquinas redondeadas.
            float half = r;
            box.set(cx - half, cy - half, cx + half, cy + half);
            clip.reset();
            clip.addRoundRect(box, dp(22), dp(22), Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.drawBitmap(cover, null, box, bmp);
            scrim.setColor(0x33000000);
            c.drawRect(box, scrim);
            c.restore();
        } else if (showCover) {
            // Disco de vinilo: según el aspecto elegido, y la carátula como etiqueta circular en el centro.
            drawVinylBody(c, cx, cy, r);
            float lr = r * 0.36f;
            clip.reset();
            clip.addCircle(cx, cy, lr, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            c.rotate((float) Math.toDegrees(angle), cx, cy);
            box.set(cx - lr, cy - lr, cx + lr, cy + lr);
            c.drawBitmap(cover, null, box, bmp);
            c.restore();
            groove.setColor(0x66FFFFFF);
            c.drawCircle(cx, cy, lr, groove);
            groove.setColor(0x26FFFFFF);
        } else {
            float dotR = dp(1.7f) * (1f + level * 1.2f);
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

        if (!square) {
            double m = angle - Math.PI / 2;
            c.drawCircle(cx + (float) Math.cos(m) * r * 0.95f, cy + (float) Math.sin(m) * r * 0.95f,
                    dp(5), accent);
        }

        if (showCover && !square) {
            // Vinilo: sin botón; solo el agujero del eje en el centro de la etiqueta (tocar el centro sigue dando play/pausa).
            hubFill.setColor(0xFF101010);
            c.drawCircle(cx, cy, r * 0.035f, hubFill);
            groove.setColor(0x99FFFFFF);
            c.drawCircle(cx, cy, r * 0.035f, groove);
            groove.setColor(0x26FFFFFF);
            return;
        }
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
