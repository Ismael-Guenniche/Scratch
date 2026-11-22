package android.text;

public class TextUtils {
    public enum TruncateAt {
        START, MIDDLE, END, MARQUEE
    }

    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) {
            return true;
        }
        return a != null && b != null && a.toString().equals(b.toString());
    }

    public static String join(CharSequence sep, Iterable<?> parts) {
        StringBuilder b = new StringBuilder();
        boolean first = true;
        for (Object o : parts) {
            if (!first) {
                b.append(sep);
            }
            first = false;
            b.append(o);
        }
        return b.toString();
    }

    public static String join(CharSequence sep, Object[] parts) {
        return join(sep, java.util.Arrays.asList(parts));
    }

    public static String htmlEncode(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static boolean isDigitsOnly(CharSequence s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static CharSequence ellipsize(CharSequence text, TextPaint p, float avail, TruncateAt where) {
        String s = text.toString();
        if (p.measureText(s) <= avail) {
            return text;
        }
        int n = p.breakText(s, true, Math.max(0, avail - p.measureText("…")), null);
        return s.substring(0, n) + "…";
    }
}
