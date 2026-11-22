package org.json;

import java.util.ArrayList;

public class JSONArray {
    final ArrayList<Object> list = new ArrayList<Object>();

    public JSONArray() {
    }

    public JSONArray(String s) throws JSONException {
        Object v = new JSONObject.Parser(s).value();
        if (!(v instanceof JSONArray)) {
            throw new JSONException("no es una lista JSON");
        }
        list.addAll(((JSONArray) v).list);
    }

    public int length() {
        return list.size();
    }

    public Object opt(int i) {
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }

    public Object get(int i) throws JSONException {
        Object v = opt(i);
        if (v == null) {
            throw new JSONException("índice " + i);
        }
        return v;
    }

    public boolean isNull(int i) {
        Object v = opt(i);
        return v == null || v == JSONObject.NULL;
    }

    public JSONObject optJSONObject(int i) {
        Object v = opt(i);
        return v instanceof JSONObject ? (JSONObject) v : null;
    }

    public JSONObject getJSONObject(int i) throws JSONException {
        Object v = get(i);
        if (v instanceof JSONObject) {
            return (JSONObject) v;
        }
        throw new JSONException(i + " no es un objeto");
    }

    public JSONArray optJSONArray(int i) {
        Object v = opt(i);
        return v instanceof JSONArray ? (JSONArray) v : null;
    }

    public JSONArray getJSONArray(int i) throws JSONException {
        Object v = get(i);
        if (v instanceof JSONArray) {
            return (JSONArray) v;
        }
        throw new JSONException(i + " no es una lista");
    }

    public String optString(int i) {
        return optString(i, "");
    }

    public String optString(int i, String def) {
        Object v = opt(i);
        return v == null || v == JSONObject.NULL ? def : v.toString();
    }

    public String getString(int i) throws JSONException {
        return get(i).toString();
    }

    public int optInt(int i) {
        Object v = opt(i);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    public int getInt(int i) throws JSONException {
        Object v = get(i);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        throw new JSONException(i + " no es un número");
    }

    public long optLong(int i) {
        Object v = opt(i);
        return v instanceof Number ? ((Number) v).longValue() : 0;
    }

    public double optDouble(int i) {
        Object v = opt(i);
        return v instanceof Number ? ((Number) v).doubleValue() : Double.NaN;
    }

    public boolean optBoolean(int i) {
        Object v = opt(i);
        return v instanceof Boolean && (Boolean) v;
    }

    public JSONArray put(Object v) {
        list.add(v == null ? JSONObject.NULL : v);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(JSONObject.write(list.get(i)));
        }
        return b.append(']').toString();
    }
}
