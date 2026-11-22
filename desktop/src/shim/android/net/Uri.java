package android.net;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/** URI inmutable guardada como texto. Los archivos locales son file:///C:/ruta/cancion.mp3. */
public final class Uri implements Comparable<Uri> {
    public static final Uri EMPTY = new Uri("");
    private final String s;

    private Uri(String s) {
        this.s = s;
    }

    public static Uri parse(String s) {
        return new Uri(s == null ? "" : s);
    }

    /**
     * Como los documentos de Android: toda la ruta va en un solo segmento ("C:Users/yo/Music/a.mp3"),
     * porque la app saca de ahí el nombre de la carpeta.
     */
    public static Uri fromFile(File f) {
        return new Uri("file:///" + encode(android.provider.DocumentsContract.idOf(f)));
    }

    /** El archivo local de una URI file://, o null si no lo es. */
    public File toFile() {
        if (!"file".equals(getScheme())) {
            return null;
        }
        String p = getPath();
        if (p == null || p.length() < 2) {
            return null;
        }
        return android.provider.DocumentsContract.fileOf(p.substring(1));
    }

    @Override
    public String toString() {
        return s;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Uri && ((Uri) o).s.equals(s);
    }

    @Override
    public int hashCode() {
        return s.hashCode();
    }

    @Override
    public int compareTo(Uri o) {
        return s.compareTo(o.s);
    }

    public String getScheme() {
        int c = s.indexOf(':');
        if (c <= 0) {
            return null;
        }
        for (int i = 0; i < c; i++) {
            char ch = s.charAt(i);
            if (!(Character.isLetterOrDigit(ch) || ch == '+' || ch == '-' || ch == '.')) {
                return null;
            }
        }
        return s.substring(0, c);
    }

    private int afterScheme() {
        String sc = getScheme();
        return sc == null ? 0 : sc.length() + 1;
    }

    public String getSchemeSpecificPart() {
        int f = s.indexOf('#');
        return decode(s.substring(afterScheme(), f < 0 ? s.length() : f));
    }

    public String getEncodedAuthority() {
        int i = afterScheme();
        if (!s.startsWith("//", i)) {
            return null;
        }
        int e = i + 2;
        while (e < s.length() && "/?#".indexOf(s.charAt(e)) < 0) {
            e++;
        }
        return s.substring(i + 2, e);
    }

    public String getAuthority() {
        return decode(getEncodedAuthority());
    }

    public String getHost() {
        String a = getEncodedAuthority();
        if (a == null) {
            return null;
        }
        int at = a.lastIndexOf('@');
        if (at >= 0) {
            a = a.substring(at + 1);
        }
        int c = a.lastIndexOf(':');
        if (c >= 0 && a.indexOf(']') < c) {
            a = a.substring(0, c);
        }
        return decode(a);
    }

    public int getPort() {
        String a = getEncodedAuthority();
        if (a == null) {
            return -1;
        }
        int c = a.lastIndexOf(':');
        try {
            return c >= 0 ? Integer.parseInt(a.substring(c + 1)) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String getEncodedPath() {
        int i = afterScheme();
        if (s.startsWith("//", i)) {
            i += 2;
            while (i < s.length() && "/?#".indexOf(s.charAt(i)) < 0) {
                i++;
            }
        } else if (i > 0 && (i >= s.length() || s.charAt(i) != '/')) {
            return null;
        }
        int e = i;
        while (e < s.length() && "?#".indexOf(s.charAt(e)) < 0) {
            e++;
        }
        return s.substring(i, e);
    }

    public String getPath() {
        return decode(getEncodedPath());
    }

    public List<String> getPathSegments() {
        ArrayList<String> out = new ArrayList<String>();
        String p = getEncodedPath();
        if (p != null) {
            for (String seg : p.split("/")) {
                if (seg.length() > 0) {
                    out.add(decode(seg));
                }
            }
        }
        return out;
    }

    public String getLastPathSegment() {
        List<String> l = getPathSegments();
        return l.isEmpty() ? null : l.get(l.size() - 1);
    }

    public String getEncodedQuery() {
        int q = s.indexOf('?');
        if (q < 0) {
            return null;
        }
        int f = s.indexOf('#', q);
        return s.substring(q + 1, f < 0 ? s.length() : f);
    }

    public String getQuery() {
        return decode(getEncodedQuery());
    }

    public String getQueryParameter(String key) {
        String q = getEncodedQuery();
        if (q == null) {
            return null;
        }
        String ek = encode(key);
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            if (k.equals(ek) || k.equals(key)) {
                return eq < 0 ? "" : decode(part.substring(eq + 1).replace('+', ' '));
            }
        }
        return null;
    }

    public java.util.Set<String> getQueryParameterNames() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<String>();
        String q = getEncodedQuery();
        if (q != null) {
            for (String part : q.split("&")) {
                int eq = part.indexOf('=');
                out.add(decode(eq < 0 ? part : part.substring(0, eq)));
            }
        }
        return out;
    }

    public String getFragment() {
        return decode(getEncodedFragment());
    }

    public String getEncodedFragment() {
        int f = s.indexOf('#');
        return f < 0 ? null : s.substring(f + 1);
    }

    public boolean isAbsolute() {
        return getScheme() != null;
    }

    public boolean isHierarchical() {
        int i = afterScheme();
        return i == 0 || (i < s.length() && s.charAt(i) == '/');
    }

    public Builder buildUpon() {
        Builder b = new Builder();
        b.scheme = getScheme();
        b.authority = getEncodedAuthority();
        String p = getEncodedPath();
        b.path = p == null ? "" : p;
        b.query = getEncodedQuery();
        b.fragment = getEncodedFragment();
        return b;
    }

    public static Uri withAppendedPath(Uri base, String seg) {
        return base.buildUpon().appendEncodedPath(seg).build();
    }

    public static final class Builder {
        String scheme;
        String authority;
        String path = "";
        String query;
        String fragment;

        public Builder scheme(String v) {
            scheme = v;
            return this;
        }

        public Builder authority(String v) {
            authority = v;
            return this;
        }

        public Builder encodedAuthority(String v) {
            authority = v;
            return this;
        }

        public Builder path(String v) {
            path = v == null ? "" : encode(v, "/");
            return this;
        }

        public Builder encodedPath(String v) {
            path = v == null ? "" : v;
            return this;
        }

        public Builder appendPath(String seg) {
            return appendEncodedPath(encode(seg));
        }

        public Builder appendEncodedPath(String seg) {
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += seg;
            return this;
        }

        public Builder appendQueryParameter(String k, String v) {
            String kv = encode(k) + "=" + encode(v);
            query = query == null || query.length() == 0 ? kv : query + "&" + kv;
            return this;
        }

        public Builder encodedQuery(String v) {
            query = v;
            return this;
        }

        public Builder query(String v) {
            query = v == null ? null : encode(v, "=&");
            return this;
        }

        public Builder clearQuery() {
            query = null;
            return this;
        }

        public Builder fragment(String v) {
            fragment = v == null ? null : encode(v);
            return this;
        }

        public Builder encodedFragment(String v) {
            fragment = v;
            return this;
        }

        public Uri build() {
            StringBuilder b = new StringBuilder();
            if (scheme != null) {
                b.append(scheme).append(':');
            }
            if (authority != null) {
                b.append("//").append(authority);
                if (path.length() > 0 && !path.startsWith("/")) {
                    b.append('/');
                }
            }
            b.append(path);
            if (query != null) {
                b.append('?').append(query);
            }
            if (fragment != null) {
                b.append('#').append(fragment);
            }
            return new Uri(b.toString());
        }

        @Override
        public String toString() {
            return build().toString();
        }
    }

    public static String encode(String v) {
        return encode(v, null);
    }

    public static String encode(String v, String allow) {
        if (v == null) {
            return null;
        }
        StringBuilder b = null;
        byte[] bytes;
        try {
            bytes = v.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return v;
        }
        boolean clean = true;
        for (int i = 0; i < v.length(); i++) {
            if (!ok(v.charAt(i), allow)) {
                clean = false;
                break;
            }
        }
        if (clean) {
            return v;
        }
        b = new StringBuilder(bytes.length * 2);
        for (byte x : bytes) {
            int c = x & 0xFF;
            if (c < 128 && ok((char) c, allow)) {
                b.append((char) c);
            } else {
                b.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 15, 16)));
            }
        }
        return b.toString();
    }

    private static boolean ok(char c, String allow) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "_-!.~'()*".indexOf(c) >= 0 || (allow != null && allow.indexOf(c) >= 0);
    }

    public static String decode(String v) {
        if (v == null) {
            return null;
        }
        if (v.indexOf('%') < 0) {
            return v;
        }
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(v.length());
        byte[] in;
        try {
            in = v.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return v;
        }
        for (int i = 0; i < in.length; i++) {
            if (in[i] == '%' && i + 2 < in.length + 0 && i + 2 <= in.length - 1 + 0) {
                int h = Character.digit((char) in[i + 1], 16);
                int l = Character.digit((char) in[i + 2], 16);
                if (h >= 0 && l >= 0) {
                    o.write(h * 16 + l);
                    i += 2;
                    continue;
                }
            }
            o.write(in[i]);
        }
        try {
            return o.toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return v;
        }
    }
}
