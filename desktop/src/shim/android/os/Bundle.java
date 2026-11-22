package android.os;

import java.util.HashMap;
import java.util.Set;

public final class Bundle {
    public static final Bundle EMPTY = new Bundle();
    private final HashMap<String, Object> map = new HashMap<String, Object>();

    public Bundle() {
    }

    public Bundle(Bundle o) {
        if (o != null) {
            map.putAll(o.map);
        }
    }

    public void putAll(Bundle o) {
        if (o != null) {
            map.putAll(o.map);
        }
    }

    public void putObject(String k, Object v) {
        map.put(k, v);
    }

    public Object get(String k) {
        return map.get(k);
    }

    public void putString(String k, String v) {
        map.put(k, v);
    }

    public void putCharSequence(String k, CharSequence v) {
        map.put(k, v);
    }

    public void putInt(String k, int v) {
        map.put(k, v);
    }

    public void putLong(String k, long v) {
        map.put(k, v);
    }

    public void putFloat(String k, float v) {
        map.put(k, v);
    }

    public void putDouble(String k, double v) {
        map.put(k, v);
    }

    public void putBoolean(String k, boolean v) {
        map.put(k, v);
    }

    public void putStringArray(String k, String[] v) {
        map.put(k, v);
    }

    public void putIntArray(String k, int[] v) {
        map.put(k, v);
    }

    public void putStringArrayList(String k, java.util.ArrayList<String> v) {
        map.put(k, v);
    }

    public void putBundle(String k, Bundle v) {
        map.put(k, v);
    }

    public void putParcelable(String k, Object v) {
        map.put(k, v);
    }

    public void putSerializable(String k, java.io.Serializable v) {
        map.put(k, v);
    }

    public String getString(String k) {
        Object o = map.get(k);
        return o == null ? null : o.toString();
    }

    public String getString(String k, String def) {
        String s = getString(k);
        return s == null ? def : s;
    }

    public CharSequence getCharSequence(String k) {
        Object o = map.get(k);
        return o instanceof CharSequence ? (CharSequence) o : null;
    }

    public int getInt(String k) {
        return getInt(k, 0);
    }

    public int getInt(String k, int def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).intValue() : def;
    }

    public long getLong(String k) {
        return getLong(k, 0);
    }

    public long getLong(String k, long def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).longValue() : def;
    }

    public float getFloat(String k) {
        return getFloat(k, 0);
    }

    public float getFloat(String k, float def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).floatValue() : def;
    }

    public double getDouble(String k, double def) {
        Object o = map.get(k);
        return o instanceof Number ? ((Number) o).doubleValue() : def;
    }

    public boolean getBoolean(String k) {
        return getBoolean(k, false);
    }

    public boolean getBoolean(String k, boolean def) {
        Object o = map.get(k);
        return o instanceof Boolean ? (Boolean) o : def;
    }

    public String[] getStringArray(String k) {
        Object o = map.get(k);
        return o instanceof String[] ? (String[]) o : null;
    }

    public int[] getIntArray(String k) {
        Object o = map.get(k);
        return o instanceof int[] ? (int[]) o : null;
    }

    @SuppressWarnings("unchecked")
    public java.util.ArrayList<String> getStringArrayList(String k) {
        Object o = map.get(k);
        return o instanceof java.util.ArrayList ? (java.util.ArrayList<String>) o : null;
    }

    public Bundle getBundle(String k) {
        Object o = map.get(k);
        return o instanceof Bundle ? (Bundle) o : null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getParcelable(String k) {
        return (T) map.get(k);
    }

    public java.io.Serializable getSerializable(String k) {
        Object o = map.get(k);
        return o instanceof java.io.Serializable ? (java.io.Serializable) o : null;
    }

    public boolean containsKey(String k) {
        return map.containsKey(k);
    }

    public void remove(String k) {
        map.remove(k);
    }

    public void clear() {
        map.clear();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public int size() {
        return map.size();
    }

    public Set<String> keySet() {
        return map.keySet();
    }
}
