package android.database;

public interface Cursor extends java.io.Closeable {
    int getCount();

    boolean moveToFirst();

    boolean moveToNext();

    boolean moveToPosition(int p);

    int getPosition();

    int getColumnIndex(String name);

    int getColumnIndexOrThrow(String name);

    int getColumnCount();

    String getColumnName(int i);

    String getString(int col);

    int getInt(int col);

    long getLong(int col);

    boolean isNull(int col);

    boolean isClosed();

    @Override
    void close();
}
