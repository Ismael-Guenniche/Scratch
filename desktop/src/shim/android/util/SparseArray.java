package android.util;

import java.util.TreeMap;

public class SparseArray<E> {
    private final TreeMap<Integer, E> map = new TreeMap<Integer, E>();

    public E get(int k) {
        return map.get(k);
    }

    public E get(int k, E def) {
        E e = map.get(k);
        return e == null ? def : e;
    }

    public void put(int k, E v) {
        map.put(k, v);
    }

    public void remove(int k) {
        map.remove(k);
    }

    public void delete(int k) {
        map.remove(k);
    }

    public int size() {
        return map.size();
    }

    public void clear() {
        map.clear();
    }

    public int keyAt(int i) {
        return (Integer) map.keySet().toArray()[i];
    }

    @SuppressWarnings("unchecked")
    public E valueAt(int i) {
        return (E) map.values().toArray()[i];
    }

    public int indexOfKey(int k) {
        return map.containsKey(k) ? map.headMap(k).size() : -1;
    }
}
