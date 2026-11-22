package android.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;

public class TextView extends View {
    public enum BufferType {
        NORMAL, SPANNABLE, EDITABLE
    }

    public interface OnEditorActionListener {
        boolean onEditorAction(TextView v, int actionId, KeyEvent event);
    }

    /** Una línea ya partida: el texto que se pinta y a qué tramo del original corresponde. */
    private static final class Line {
        String s;
        int start;
        int srcLen;
        float width;
    }

    private CharSequence text = "";
    private String shown = "";
    private CharSequence hint;
    private ColorStateList colors;
    private int color = 0xFFFFFFFF;
    private int hintColor = 0x80808080;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int gravity = Gravity.TOP | Gravity.START;
    private boolean singleLine;
    private int maxLines = Integer.MAX_VALUE;
    private int minLines;
    private TextUtils.TruncateAt ellipsize;
    private float spacingMult = 1f;
    private float spacingAdd;
    private boolean allCaps;
    private final Drawable[] compound = new Drawable[4];
    private int compoundPad;
    private int minWidthPx = -1;
    private int maxWidthPx = Integer.MAX_VALUE;
    private int minHeightPx = -1;
    private int maxHeightPx = Integer.MAX_VALUE;
    private final ArrayList<Line> lines = new ArrayList<Line>();
    private int laidWidth = Integer.MIN_VALUE;
    private boolean laidHint;
    private float lineH;
    private float ascent;
    protected ArrayList<TextWatcher> watchers;
    protected OnEditorActionListener editorAction;
    protected int inputType;

    public TextView(Context c) {
        super(c);
        paint.setTextSize(14 * c.getResources().getDisplayMetrics().scaledDensity);
        paint.setTypeface(Typeface.DEFAULT);
    }

    public TextView(Context c, Object attrs) {
        this(c);
    }

    // ------------------------------------------------------------ texto

    public CharSequence getText() {
        return text;
    }

    public int length() {
        return text.length();
    }

    public void setText(CharSequence t) {
        if (t == null) {
            t = "";
        }
        String before = shown;
        text = t;
        shown = display(t);
        if (!before.equals(shown) || t instanceof Spanned) {
            textChanged();
        }
    }

    public void setText(CharSequence t, BufferType type) {
        setText(t);
    }

    public void setText(char[] c, int start, int len) {
        setText(new String(c, start, len));
    }

    public void setText(int resId) {
        setText("");
    }

    public void append(CharSequence t) {
        setText(text.toString() + t);
    }

    private String display(CharSequence t) {
        String s = t.toString();
        return allCaps ? s.toUpperCase() : s;
    }

    /** El texto cambió: recolocar solo si puede cambiar el tamaño de la vista (como hace Android). */
    protected void textChanged() {
        int oldLines = lines.size();
        laidWidth = Integer.MIN_VALUE;
        ViewGroup.LayoutParams lp = getLayoutParams();
        boolean fixedW = lp != null && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT && getWidth() > 0;
        if (fixedW && layer() != null) {
            boolean fixedH = lp.height != ViewGroup.LayoutParams.WRAP_CONTENT;
            if (!fixedH) {
                ensureLines(getWidth() - extraW());
                fixedH = lines.size() == oldLines || (singleLine && oldLines > 0);
            }
            if (fixedH) {
                invalidate();
                return;
            }
        }
        requestLayout();
        invalidate();
    }

    public void setHint(CharSequence h) {
        hint = h;
        laidWidth = Integer.MIN_VALUE;
        requestLayout();
        invalidate();
    }

    public void setHint(int resId) {
    }

    public CharSequence getHint() {
        return hint;
    }

    public void setHintTextColor(int c) {
        hintColor = c;
        invalidate();
    }

    public void setHintTextColor(ColorStateList c) {
        hintColor = c.getDefaultColor();
        invalidate();
    }

    public void setTextColor(int c) {
        colors = null;
        if (color != c) {
            color = c;
            invalidate();
        }
    }

    public void setTextColor(ColorStateList c) {
        colors = c;
        color = c.getColorForState(drawableState(), c.getDefaultColor());
        invalidate();
    }

    public int getCurrentTextColor() {
        return color;
    }

    public ColorStateList getTextColors() {
        return colors != null ? colors : ColorStateList.valueOf(color);
    }

    public void setLinkTextColor(int c) {
    }

    public void setHighlightColor(int c) {
    }

    @Override
    protected void drawableStateChanged() {
        if (colors != null) {
            int c = colors.getColorForState(drawableState(), colors.getDefaultColor());
            if (c != color) {
                color = c;
                invalidate();
            }
        }
    }

    public void setTextSize(float sp) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    public void setTextSize(int unit, float size) {
        float px = unit == TypedValue.COMPLEX_UNIT_PX ? size
                : size * getResources().getDisplayMetrics().scaledDensity;
        if (px != paint.getTextSize()) {
            paint.setTextSize(px);
            relayoutText();
        }
    }

    public float getTextSize() {
        return paint.getTextSize();
    }

    public void setTypeface(Typeface tf) {
        if (tf != paint.getTypeface()) {
            paint.setTypeface(tf);
            relayoutText();
        }
    }

    public void setTypeface(Typeface tf, int style) {
        if (style > 0) {
            setTypeface(Typeface.create(tf == null ? Typeface.DEFAULT : tf, style));
        } else {
            setTypeface(tf);
        }
    }

    public Typeface getTypeface() {
        return paint.getTypeface();
    }

    public TextPaint getPaint() {
        return paint;
    }

    public void setPaintFlags(int f) {
        paint.setFlags(f);
        invalidate();
    }

    public int getPaintFlags() {
        return paint.getFlags();
    }

    public void setLetterSpacing(float s) {
        if (s != paint.getLetterSpacing()) {
            paint.setLetterSpacing(s);
            relayoutText();
        }
    }

    public float getLetterSpacing() {
        return paint.getLetterSpacing();
    }

    public void setTextScaleX(float s) {
    }

    public void setFontFeatureSettings(String s) {
    }

    public void setAllCaps(boolean b) {
        if (b != allCaps) {
            allCaps = b;
            shown = display(text);
            relayoutText();
        }
    }

    public void setSingleLine() {
        setSingleLine(true);
    }

    public void setSingleLine(boolean b) {
        singleLine = b;
        maxLines = b ? 1 : Integer.MAX_VALUE;
        relayoutText();
    }

    public void setMaxLines(int n) {
        maxLines = n;
        if (n > 1) {
            singleLine = false;
        }
        relayoutText();
    }

    public int getMaxLines() {
        return maxLines;
    }

    public void setMinLines(int n) {
        minLines = n;
        requestLayout();
    }

    public void setLines(int n) {
        minLines = n;
        setMaxLines(n);
    }

    public void setEllipsize(TextUtils.TruncateAt e) {
        ellipsize = e;
        relayoutText();
    }

    public TextUtils.TruncateAt getEllipsize() {
        return ellipsize;
    }

    public void setHorizontallyScrolling(boolean b) {
    }

    public void setMarqueeRepeatLimit(int n) {
    }

    public void setGravity(int g) {
        if ((g & Gravity.HORIZONTAL_GRAVITY_MASK) == 0) {
            g |= Gravity.START;
        }
        if ((g & Gravity.VERTICAL_GRAVITY_MASK) == 0) {
            g |= Gravity.TOP;
        }
        gravity = g;
        invalidate();
    }

    public int getGravity() {
        return gravity;
    }

    @Override
    public void setTextAlignment(int a) {
        if (a == TEXT_ALIGNMENT_CENTER) {
            setGravity((gravity & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.CENTER_HORIZONTAL);
        } else if (a == TEXT_ALIGNMENT_VIEW_END) {
            setGravity((gravity & Gravity.VERTICAL_GRAVITY_MASK) | Gravity.RIGHT);
        }
    }

    public void setLineSpacing(float add, float mult) {
        spacingAdd = add;
        spacingMult = mult;
        relayoutText();
    }

    public void setIncludeFontPadding(boolean b) {
    }

    public void setTextIsSelectable(boolean b) {
    }

    public void setMovementMethod(android.text.method.MovementMethod m) {
    }

    public void setShadowLayer(float r, float dx, float dy, int c) {
    }

    public void setInputType(int t) {
        inputType = t;
    }

    public int getInputType() {
        return inputType;
    }

    public void setRawInputType(int t) {
        inputType = t;
    }

    public void setImeOptions(int o) {
    }

    public void setFilters(android.text.InputFilter[] f) {
    }

    public void setOnEditorActionListener(OnEditorActionListener l) {
        editorAction = l;
    }

    public void setMinWidth(int px) {
        minWidthPx = px;
        requestLayout();
    }

    public void setMaxWidth(int px) {
        maxWidthPx = px;
        requestLayout();
    }

    public void setMinHeight(int px) {
        minHeightPx = px;
        requestLayout();
    }

    public void setMaxHeight(int px) {
        maxHeightPx = px;
        requestLayout();
    }

    public void setWidth(int px) {
        minWidthPx = px;
        maxWidthPx = px;
        requestLayout();
    }

    public void setHeight(int px) {
        minHeightPx = px;
        maxHeightPx = px;
        requestLayout();
    }

    public void setMinEms(int n) {
    }

    public void setEms(int n) {
    }

    public void addTextChangedListener(TextWatcher w) {
        if (watchers == null) {
            watchers = new ArrayList<TextWatcher>();
        }
        watchers.add(w);
    }

    public void removeTextChangedListener(TextWatcher w) {
        if (watchers != null) {
            watchers.remove(w);
        }
    }

    public void setCompoundDrawables(Drawable l, Drawable t, Drawable r, Drawable b) {
        compound[0] = l;
        compound[1] = t;
        compound[2] = r;
        compound[3] = b;
        requestLayout();
        invalidate();
    }

    public void setCompoundDrawablesWithIntrinsicBounds(Drawable l, Drawable t, Drawable r, Drawable b) {
        for (Drawable d : new Drawable[] {l, t, r, b}) {
            if (d != null) {
                d.setBounds(0, 0, Math.max(0, d.getIntrinsicWidth()), Math.max(0, d.getIntrinsicHeight()));
            }
        }
        setCompoundDrawables(l, t, r, b);
    }

    public void setCompoundDrawablesWithIntrinsicBounds(int l, int t, int r, int b) {
        setCompoundDrawables(null, null, null, null);
    }

    public void setCompoundDrawablesRelativeWithIntrinsicBounds(Drawable s, Drawable t, Drawable e, Drawable b) {
        setCompoundDrawablesWithIntrinsicBounds(s, t, e, b);
    }

    public void setCompoundDrawablesRelative(Drawable s, Drawable t, Drawable e, Drawable b) {
        setCompoundDrawables(s, t, e, b);
    }

    public Drawable[] getCompoundDrawables() {
        return compound.clone();
    }

    public void setCompoundDrawablePadding(int p) {
        compoundPad = p;
        requestLayout();
    }

    public void setCompoundDrawableTintList(ColorStateList l) {
    }

    private void relayoutText() {
        laidWidth = Integer.MIN_VALUE;
        requestLayout();
        invalidate();
    }

    // ------------------------------------------------------------ partir en líneas

    private int compW(int i) {
        return compound[i] == null ? 0 : compound[i].getBounds().width() + compoundPad;
    }

    private int compH(int i) {
        return compound[i] == null ? 0 : compound[i].getBounds().height() + compoundPad;
    }

    private int extraW() {
        return mPaddingLeft + mPaddingRight + compW(0) + compW(2);
    }

    private int extraH() {
        return mPaddingTop + mPaddingBottom + compH(1) + compH(3);
    }

    /** Lo que se pinta: el texto, o la pista si está vacío. */
    private String content() {
        if (shown.length() == 0 && hint != null) {
            return hint.toString();
        }
        return shown;
    }

    private void metrics() {
        Paint.FontMetrics fm = paint.getFontMetrics();
        ascent = fm.ascent;
        lineH = (fm.descent - fm.ascent) * spacingMult + spacingAdd;
    }

    private float naturalWidth(String s) {
        float w = 0;
        int from = 0;
        while (true) {
            int nl = singleLine ? -1 : s.indexOf('\n', from);
            String part = nl < 0 ? s.substring(from) : s.substring(from, nl);
            w = Math.max(w, paint.measureText(singleLine ? part.replace('\n', ' ') : part));
            if (nl < 0) {
                break;
            }
            from = nl + 1;
        }
        return w;
    }

    private Line line(String s, int start, int srcLen) {
        Line l = new Line();
        l.s = s;
        l.start = start;
        l.srcLen = srcLen;
        l.width = paint.measureText(s);
        return l;
    }

    private Line ellipsized(String src, int start, int avail) {
        String rest = src;
        float dots = paint.measureText("…");
        if (ellipsize == TextUtils.TruncateAt.START) {
            int lo = 0;
            int hi = rest.length();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (paint.measureText(rest, mid, rest.length()) + dots <= avail) {
                    hi = mid;
                } else {
                    lo = mid + 1;
                }
            }
            return line("…" + rest.substring(lo), start + lo - 1, rest.length() - lo + 1);
        }
        if (ellipsize == TextUtils.TruncateAt.MIDDLE) {
            int keep = paint.breakText(rest, true, Math.max(0, (avail - dots) / 2f), null);
            int tail = keep;
            while (tail > 0 && paint.measureText(rest, 0, keep) + dots
                    + paint.measureText(rest, rest.length() - tail, rest.length()) > avail) {
                tail--;
            }
            return line(rest.substring(0, keep) + "…" + rest.substring(rest.length() - tail), start, keep);
        }
        int n = paint.breakText(rest, true, Math.max(0, avail - dots), null);
        while (n > 0 && rest.charAt(n - 1) == ' ') {
            n--;
        }
        return line(rest.substring(0, n) + "…", start, n);
    }

    private void ensureLines(int avail) {
        boolean isHint = shown.length() == 0 && hint != null;
        if (avail == laidWidth && isHint == laidHint) {
            return;
        }
        laidWidth = avail;
        laidHint = isHint;
        lines.clear();
        metrics();
        String s = content();
        if (s.length() == 0) {
            return;
        }
        if (singleLine || maxLines == 1) {
            String one = s.indexOf('\n') >= 0 ? (singleLine ? s.replace('\n', ' ') : s.substring(0, s.indexOf('\n')))
                    : s;
            boolean cut = !singleLine && s.indexOf('\n') >= 0;
            if (ellipsize != null && avail > 0 && (cut || paint.measureText(one) > avail + 0.5f)) {
                lines.add(cut && paint.measureText(one + "…") <= avail ? line(one + "…", 0, one.length())
                        : ellipsized(one, 0, avail));
            } else {
                lines.add(line(one, 0, one.length()));
            }
            return;
        }
        int from = 0;
        int n = s.length();
        boolean more = false;
        outer:
        while (from <= n) {
            int nl = s.indexOf('\n', from);
            int end = nl < 0 ? n : nl;
            int i = from;
            if (i == end) {
                if (lines.size() >= maxLines) {
                    more = true;
                    break;
                }
                lines.add(line("", i, 0));
            }
            while (i < end) {
                if (lines.size() >= maxLines) {
                    more = true;
                    break outer;
                }
                String rest = s.substring(i, end);
                int fit = avail <= 0 ? rest.length() : paint.breakText(rest, true, avail + 0.5f, null);
                if (fit >= rest.length()) {
                    lines.add(line(rest, i, rest.length()));
                    i = end;
                    break;
                }
                int br = rest.lastIndexOf(' ', fit);
                int next;
                if (br <= 0) {
                    br = Math.max(1, fit);
                    next = br;
                } else {
                    next = br + 1;
                }
                lines.add(line(rest.substring(0, br), i, br));
                i += next;
            }
            if (nl < 0) {
                break;
            }
            from = nl + 1;
        }
        if (more && ellipsize != null && !lines.isEmpty()) {
            Line last = lines.get(lines.size() - 1);
            String t = last.s;
            while (t.length() > 0 && paint.measureText(t + "…") > avail) {
                t = t.substring(0, t.length() - 1);
            }
            lines.set(lines.size() - 1, line(t + "…", last.start, t.length()));
        }
    }

    public int getLineCount() {
        return lines.size();
    }

    public int getLineHeight() {
        metrics();
        return Math.round(lineH);
    }

    @Override
    public int getBaseline() {
        metrics();
        int avail = getHeight() - extraH();
        float th = Math.max(1, lines.size()) * lineH;
        return Math.round(mPaddingTop + compH(1) + Gravity.offY(gravity, avail, Math.round(th)) - ascent);
    }

    // ------------------------------------------------------------ medir y pintar

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int wMode = MeasureSpec.getMode(wSpec);
        int wSize = MeasureSpec.getSize(wSpec);
        int hMode = MeasureSpec.getMode(hSpec);
        int hSize = MeasureSpec.getSize(hSpec);
        int ew = extraW();
        int width;
        if (wMode == MeasureSpec.EXACTLY) {
            width = wSize;
        } else {
            int want = (int) Math.ceil(naturalWidth(content())) + ew;
            want = Math.max(want, Math.max(minWidthPx, getSuggestedMinimumWidth()));
            want = Math.min(want, maxWidthPx);
            width = wMode == MeasureSpec.AT_MOST ? Math.min(want, wSize) : want;
        }
        ensureLines(width - ew);
        int height;
        if (hMode == MeasureSpec.EXACTLY) {
            height = hSize;
        } else {
            int n = Math.max(lines.size(), Math.max(1, minLines));
            if (maxLines > 0) {
                n = Math.min(n, Math.max(maxLines, 1));
            }
            int want = (int) Math.ceil(n * lineH) + extraH();
            want = Math.max(want, Math.max(compound[0] == null ? 0 : compound[0].getBounds().height(),
                    compound[2] == null ? 0 : compound[2].getBounds().height()) + mPaddingTop + mPaddingBottom);
            want = Math.max(want, Math.max(minHeightPx, getSuggestedMinimumHeight()));
            want = Math.min(want, maxHeightPx);
            height = hMode == MeasureSpec.AT_MOST ? Math.min(want, hSize) : want;
        }
        setMeasuredDimension(width, height);
    }

    /** ¿Se está editando con el campo nativo encima? Entonces el texto no se pinta aquí. */
    protected boolean peerShowsText() {
        return false;
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        int x0 = mPaddingLeft + compW(0);
        int x1 = w - mPaddingRight - compW(2);
        int y0 = mPaddingTop + compH(1);
        int y1 = h - mPaddingBottom - compH(3);
        if (compound[0] != null) {
            Drawable d = compound[0];
            int dh = d.getBounds().height();
            int n = c.save();
            c.translate(mPaddingLeft, y0 + (y1 - y0 - dh) / 2f);
            d.draw(c);
            c.restoreToCount(n);
        }
        if (compound[2] != null) {
            Drawable d = compound[2];
            int dh = d.getBounds().height();
            int n = c.save();
            c.translate(w - mPaddingRight - d.getBounds().width(), y0 + (y1 - y0 - dh) / 2f);
            d.draw(c);
            c.restoreToCount(n);
        }
        if (compound[1] != null) {
            Drawable d = compound[1];
            int n = c.save();
            c.translate(x0 + (x1 - x0 - d.getBounds().width()) / 2f, mPaddingTop);
            d.draw(c);
            c.restoreToCount(n);
        }
        if (compound[3] != null) {
            Drawable d = compound[3];
            int n = c.save();
            c.translate(x0 + (x1 - x0 - d.getBounds().width()) / 2f, h - mPaddingBottom - d.getBounds().height());
            d.draw(c);
            c.restoreToCount(n);
        }
        ensureLines(x1 - x0);
        if (lines.isEmpty() || (peerShowsText() && !laidHint)) {
            return;
        }
        int count = lines.size();
        float textH = count * lineH;
        float y = y0 + (textH <= (y1 - y0) ? Gravity.offY(gravity, y1 - y0, Math.round(textH)) : 0);
        Paint.Align keep = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.LEFT);
        int base = laidHint ? hintColor : color;
        if (!isEnabled() && colors == null) {
            base = (base & 0xFFFFFF) | (((base >>> 24) * 2 / 5) << 24);
        }
        Spanned sp = !laidHint && text instanceof Spanned ? (Spanned) text : null;
        ForegroundColorSpan[] spans = sp == null ? null : sp.getSpans(0, sp.length(), ForegroundColorSpan.class);
        boolean clip = false;
        int saved = 0;
        for (int i = 0; i < count; i++) {
            Line l = lines.get(i);
            float baseline = y + i * lineH - ascent;
            if (baseline + ascent > h || baseline + lineH < 0) {
                continue;
            }
            float x = x0 + Math.max(0, offX(x1 - x0, l.width));
            if (l.width > (x1 - x0) + 1 && !clip) {
                clip = true;
                saved = c.save();
                c.clipRect(x0, 0, x1, h);
            }
            if (spans == null || spans.length == 0) {
                paint.setColor(base);
                c.drawText(l.s, x, baseline, paint);
            } else {
                drawSpanned(c, l, x, baseline, base, sp, spans);
            }
        }
        if (clip) {
            c.restoreToCount(saved);
        }
        paint.setTextAlign(keep);
    }

    private float offX(int space, float lw) {
        switch (gravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                return (space - lw) / 2f;
            case Gravity.RIGHT:
                return space - lw;
            default:
                return 0;
        }
    }

    private void drawSpanned(Canvas c, Line l, float x, float baseline, int base, Spanned sp,
                             ForegroundColorSpan[] spans) {
        int n = l.s.length();
        int[] cols = new int[n];
        for (int i = 0; i < n; i++) {
            cols[i] = base;
        }
        for (ForegroundColorSpan s : spans) {
            int a = sp.getSpanStart(s) - l.start;
            int b = sp.getSpanEnd(s) - l.start;
            for (int i = Math.max(0, a); i < Math.min(l.srcLen, b); i++) {
                cols[i] = s.getForegroundColor();
            }
        }
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n && cols[j] == cols[i]) {
                j++;
            }
            String part = l.s.substring(i, j);
            paint.setColor(cols[i]);
            c.drawText(part, x, baseline, paint);
            x += paint.measureText(part);
            i = j;
        }
    }
}
