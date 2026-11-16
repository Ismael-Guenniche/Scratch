package android.text;

import java.util.ArrayList;

public class SpannableString implements Spannable {
    protected final StringBuilder sb;
    protected final ArrayList<Object[]> spans = new ArrayList<Object[]>();

    public SpannableString(CharSequence s) {
        sb = new StringBuilder(s == null ? "" : s);
        if (s instanceof SpannableString) {
            for (Object[] sp : ((SpannableString) s).spans) {
                spans.add(sp.clone());
            }
        }
    }

    public static SpannableString valueOf(CharSequence s) {
        return s instanceof SpannableString ? (SpannableString) s : new SpannableString(s);
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        removeSpan(what);
        spans.add(new Object[] {what, start, end});
    }

    @Override
    public void removeSpan(Object what) {
        for (int i = spans.size() - 1; i >= 0; i--) {
            if (spans.get(i)[0] == what) {
                spans.remove(i);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        ArrayList<T> out = new ArrayList<T>();
        for (Object[] sp : spans) {
            if (type.isInstance(sp[0]) && (Integer) sp[1] <= end && (Integer) sp[2] >= start) {
                out.add((T) sp[0]);
            }
        }
        return out.toArray((T[]) java.lang.reflect.Array.newInstance(type, out.size()));
    }

    @Override
    public int getSpanStart(Object span) {
        for (Object[] sp : spans) {
            if (sp[0] == span) {
                return (Integer) sp[1];
            }
        }
        return -1;
    }

    @Override
    public int getSpanEnd(Object span) {
        for (Object[] sp : spans) {
            if (sp[0] == span) {
                return (Integer) sp[2];
            }
        }
        return -1;
    }

    @Override
    public int length() {
        return sb.length();
    }

    @Override
    public char charAt(int i) {
        return sb.charAt(i);
    }

    @Override
    public CharSequence subSequence(int s, int e) {
        return sb.subSequence(s, e);
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
