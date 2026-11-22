package android.text;

public class SpannableStringBuilder extends SpannableString implements Editable {
    /** Avisa al campo de texto dueño cuando el contenido cambia por código. */
    public Runnable onChange;

    public SpannableStringBuilder() {
        super("");
    }

    public SpannableStringBuilder(CharSequence s) {
        super(s);
    }

    private void changed() {
        if (onChange != null) {
            onChange.run();
        }
    }

    /** Sustituye todo sin avisar (lo usa el campo de texto al escribir). */
    public void setSilently(CharSequence s) {
        sb.setLength(0);
        sb.append(s);
    }

    @Override
    public SpannableStringBuilder replace(int st, int en, CharSequence text) {
        sb.replace(st, en, text.toString());
        changed();
        return this;
    }

    @Override
    public SpannableStringBuilder insert(int where, CharSequence text) {
        sb.insert(where, text);
        changed();
        return this;
    }

    @Override
    public SpannableStringBuilder delete(int st, int en) {
        sb.delete(st, en);
        changed();
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text) {
        sb.append(text);
        changed();
        return this;
    }

    @Override
    public SpannableStringBuilder append(char c) {
        sb.append(c);
        changed();
        return this;
    }

    @Override
    public SpannableStringBuilder append(CharSequence text, int start, int end) {
        sb.append(text, start, end);
        changed();
        return this;
    }

    public SpannableStringBuilder append(CharSequence text, Object what, int flags) {
        int st = sb.length();
        sb.append(text);
        setSpan(what, st, sb.length(), flags);
        changed();
        return this;
    }

    @Override
    public void clear() {
        sb.setLength(0);
        spans.clear();
        changed();
    }
}
