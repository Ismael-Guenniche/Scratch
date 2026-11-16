package android.database;

import java.util.ArrayList;

public class MatrixCursor implements Cursor {
    private final String[] cols;
    private final ArrayList<Object[]> rows = new ArrayList<Object[]>();
    private int pos = -1;
    private boolean closed;

    public MatrixCursor(String[] cols) {
        this.cols = cols == null ? new String[0] : cols;
    }

    public void addRow(Object[] r) {
        rows.add(r);
    }

    @Override
    public int getCount() {
        return rows.size();
    }

    @Override
    public boolean moveToFirst() {
        pos = 0;
        return !rows.isEmpty();
    }

    @Override
    public boolean moveToNext() {
        pos++;
        return pos < rows.size();
    }

    @Override
    public boolean moveToPosition(int p) {
        pos = p;
        return p >= 0 && p < rows.size();
    }

    @Override
    public int getPosition() {
        return pos;
    }

    @Override
    public int getColumnIndex(String name) {
        for (int i = 0; i < cols.length; i++) {
            if (cols[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getColumnIndexOrThrow(String name) {
        int i = getColumnIndex(name);
        if (i < 0) {
            throw new IllegalArgumentException("columna " + name);
        }
        return i;
    }

    @Override
    public int getColumnCount() {
        return cols.length;
    }

    @Override
    public String getColumnName(int i) {
        return cols[i];
    }

    private Object at(int col) {
        Object[] r = rows.get(pos);
        return col >= 0 && col < r.length ? r[col] : null;
    }

    @Override
    public String getString(int col) {
        Object o = at(col);
        return o == null ? null : o.toString();
    }

    @Override
    public int getInt(int col) {
        Object o = at(col);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    @Override
    public long getLong(int col) {
        Object o = at(col);
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }

    @Override
    public boolean isNull(int col) {
        return at(col) == null;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
