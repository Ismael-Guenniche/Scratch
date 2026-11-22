package android.content;

import java.util.HashMap;

public final class ContentValues {
    private final HashMap<String, Object> map = new HashMap<String, Object>();

    public void put(String k, String v) {
        map.put(k, v);
    }

    public void put(String k, Integer v) {
        map.put(k, v);
    }

    public void put(String k, Long v) {
        map.put(k, v);
    }

    public void put(String k, Boolean v) {
        map.put(k, v);
    }

    public Object get(String k) {
        return map.get(k);
    }

    public String getAsString(String k) {
        Object o = map.get(k);
        return o == null ? null : o.toString();
    }

    public void clear() {
        map.clear();
    }
}
