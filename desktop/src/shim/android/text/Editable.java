package android.text;

public interface Editable extends Spannable, Appendable {
    Editable replace(int st, int en, CharSequence text);

    Editable insert(int where, CharSequence text);

    Editable delete(int st, int en);

    @Override
    Editable append(CharSequence text);

    @Override
    Editable append(char c);

    @Override
    Editable append(CharSequence text, int start, int end);

    void clear();
}
