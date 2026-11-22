package android.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruCache<K, V> {
    private final LinkedHashMap<K, V> map = new LinkedHashMap<K, V>(0, 0.75f, true);
    private int size;
    private int maxSize;

    public LruCache(int maxSize) {
        this.maxSize = maxSize;
    }

    protected int sizeOf(K key, V value) {
        return 1;
    }

    protected void entryRemoved(boolean evicted, K key, V oldValue, V newValue) {
    }

    protected V create(K key) {
        return null;
    }

    public final synchronized V get(K key) {
        V v = map.get(key);
        if (v == null) {
            v = create(key);
            if (v != null) {
                put(key, v);
            }
        }
        return v;
    }

    public final V put(K key, V value) {
        V old;
        synchronized (this) {
            size += sizeOf(key, value);
            old = map.put(key, value);
            if (old != null) {
                size -= sizeOf(key, old);
            }
        }
        if (old != null) {
            entryRemoved(false, key, old, value);
        }
        trimToSize(maxSize);
        return old;
    }

    public void trimToSize(int max) {
        while (true) {
            K key;
            V value;
            synchronized (this) {
                if (size <= max || map.isEmpty()) {
                    break;
                }
                Map.Entry<K, V> e = map.entrySet().iterator().next();
                key = e.getKey();
                value = e.getValue();
                map.remove(key);
                size -= sizeOf(key, value);
            }
            entryRemoved(true, key, value, null);
        }
    }

    public final V remove(K key) {
        V old;
        synchronized (this) {
            old = map.remove(key);
            if (old != null) {
                size -= sizeOf(key, old);
            }
        }
        if (old != null) {
            entryRemoved(false, key, old, null);
        }
        return old;
    }

    public final void evictAll() {
        trimToSize(-1);
    }

    public final synchronized int size() {
        return size;
    }

    public final synchronized int maxSize() {
        return maxSize;
    }

    public void resize(int max) {
        synchronized (this) {
            maxSize = max;
        }
        trimToSize(max);
    }

    public final synchronized Map<K, V> snapshot() {
        return new LinkedHashMap<K, V>(map);
    }
}
