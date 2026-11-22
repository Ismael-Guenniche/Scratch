package org.json;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSON mínimo con la forma de org.json de Android (lo justo para leer respuestas de APIs). */
public class JSONObject {
    public static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    final LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();

    public JSONObject() {
    }

    public JSONObject(String s) throws JSONException {
        Parser p = new Parser(s);
        Object v = p.value();
        if (!(v instanceof JSONObject)) {
            throw new JSONException("no es un objeto JSON");
        }
        map.putAll(((JSONObject) v).map);
    }

    static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s == null ? "" : s;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() throws JSONException {
            ws();
            if (i >= s.length()) {
                throw new JSONException("fin inesperado");
            }
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                JSONObject o = new JSONObject();
                ws();
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    return o;
                }
                while (true) {
                    ws();
                    if (i >= s.length() || s.charAt(i) != '"') {
                        throw new JSONException("se esperaba una clave en " + i);
                    }
                    String k = string();
                    ws();
                    if (i >= s.length() || s.charAt(i) != ':') {
                        throw new JSONException("se esperaba ':' en " + i);
                    }
                    i++;
                    o.map.put(k, value());
                    ws();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    if (i < s.length() && s.charAt(i) == '}') {
                        i++;
                        return o;
                    }
                    throw new JSONException("se esperaba ',' o '}' en " + i);
                }
            }
            if (c == '[') {
                i++;
                JSONArray a = new JSONArray();
                ws();
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    return a;
                }
                while (true) {
                    a.list.add(value());
                    ws();
                    if (i < s.length() && s.charAt(i) == ',') {
                        i++;
                        continue;
                    }
                    if (i < s.length() && s.charAt(i) == ']') {
                        i++;
                        return a;
                    }
                    throw new JSONException("se esperaba ',' o ']' en " + i);
                }
            }
            if (c == '"') {
                return string();
            }
            int st = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) {
                i++;
            }
            String lit = s.substring(st, i);
            if (lit.equals("true")) {
                return Boolean.TRUE;
            }
            if (lit.equals("false")) {
                return Boolean.FALSE;
            }
            if (lit.equals("null")) {
                return NULL;
            }
            try {
                if (lit.indexOf('.') >= 0 || lit.indexOf('e') >= 0 || lit.indexOf('E') >= 0) {
                    return Double.valueOf(lit);
                }
                long l = Long.parseLong(lit);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return l;
            } catch (NumberFormatException e) {
                throw new JSONException("valor no válido: " + lit);
            }
        }

        private String string() throws JSONException {
            StringBuilder b = new StringBuilder();
            i++;
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\' && i < s.length()) {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n':
                            b.append('\n');
                            break;
                        case 't':
                            b.append('\t');
                            break;
                        case 'r':
                            b.append('\r');
                            break;
                        case 'b':
                            b.append('\b');
                            break;
                        case 'f':
                            b.append('\f');
                            break;
                        case 'u':
                            if (i + 4 > s.length()) {
                                throw new JSONException("escape incompleto");
                            }
                            try {
                                b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            } catch (NumberFormatException ex) {
                                throw new JSONException("escape no válido");
                            }
                            i += 4;
                            break;
                        default:
                            b.append(e);
                            break;
                    }
                } else {
                    b.append(c);
                }
            }
            throw new JSONException("cadena sin cerrar");
        }
    }

    static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            switch (c) {
                case '"':
                    b.append("\\\"");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                case '\t':
                    b.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                    break;
            }
        }
        return b.append('"').toString();
    }

    static String write(Object v) {
        if (v == null || v == NULL) {
            return "null";
        }
        if (v instanceof String) {
            return quote((String) v);
        }
        return v.toString();
    }

    public boolean has(String k) {
        return map.containsKey(k);
    }

    public boolean isNull(String k) {
        Object v = map.get(k);
        return v == null || v == NULL;
    }

    public Object opt(String k) {
        return map.get(k);
    }

    public Object get(String k) throws JSONException {
        Object v = map.get(k);
        if (v == null) {
            throw new JSONException("No value for " + k);
        }
        return v;
    }

    public String optString(String k) {
        return optString(k, "");
    }

    public String optString(String k, String def) {
        Object v = map.get(k);
        return v == null || v == NULL ? def : v.toString();
    }

    public String getString(String k) throws JSONException {
        return get(k).toString();
    }

    public int optInt(String k) {
        return optInt(k, 0);
    }

    public int optInt(String k, int def) {
        Object v = map.get(k);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        if (v instanceof String) {
            try {
                return (int) Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public int getInt(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        throw new JSONException(k + " no es un número");
    }

    public long optLong(String k) {
        return optLong(k, 0);
    }

    public long optLong(String k, long def) {
        Object v = map.get(k);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        if (v instanceof String) {
            try {
                return (long) Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public long getLong(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        throw new JSONException(k + " no es un número");
    }

    public double optDouble(String k) {
        return optDouble(k, Double.NaN);
    }

    public double optDouble(String k, double def) {
        Object v = map.get(k);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        if (v instanceof String) {
            try {
                return Double.parseDouble((String) v);
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public double getDouble(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        throw new JSONException(k + " no es un número");
    }

    public boolean optBoolean(String k) {
        return optBoolean(k, false);
    }

    public boolean optBoolean(String k, boolean def) {
        Object v = map.get(k);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof String) {
            return "true".equalsIgnoreCase((String) v) || (!"false".equalsIgnoreCase((String) v) && def);
        }
        return def;
    }

    public boolean getBoolean(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        throw new JSONException(k + " no es un booleano");
    }

    public JSONObject optJSONObject(String k) {
        Object v = map.get(k);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONObject getJSONObject(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof JSONObject) {
            return (JSONObject) v;
        }
        throw new JSONException(k + " no es un objeto");
    }

    public JSONArray optJSONArray(String k) {
        Object v = map.get(k);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public JSONArray getJSONArray(String k) throws JSONException {
        Object v = get(k);
        if (v instanceof JSONArray) {
            return (JSONArray) v;
        }
        throw new JSONException(k + " no es una lista");
    }

    public JSONObject put(String k, Object v) throws JSONException {
        if (v == null) {
            map.remove(k);
        } else {
            map.put(k, v);
        }
        return this;
    }

    public JSONObject put(String k, int v) throws JSONException {
        map.put(k, v);
        return this;
    }

    public JSONObject put(String k, long v) throws JSONException {
        map.put(k, v);
        return this;
    }

    public JSONObject put(String k, double v) throws JSONException {
        map.put(k, v);
        return this;
    }

    public JSONObject put(String k, boolean v) throws JSONException {
        map.put(k, v);
        return this;
    }

    public Object remove(String k) {
        return map.remove(k);
    }

    public Iterator<String> keys() {
        return map.keySet().iterator();
    }

    public int length() {
        return map.size();
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                b.append(',');
            }
            first = false;
            b.append(quote(e.getKey())).append(':').append(write(e.getValue()));
        }
        return b.append('}').toString();
    }
}
