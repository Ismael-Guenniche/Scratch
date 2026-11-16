package android.os;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;

public class ParcelFileDescriptor implements java.io.Closeable {
    public static final int MODE_READ_ONLY = 0x10000000;
    private final RandomAccessFile raf;
    public final File file;

    public ParcelFileDescriptor(File f, String mode) throws IOException {
        file = f;
        raf = new RandomAccessFile(f, mode);
    }

    public static ParcelFileDescriptor open(File f, int mode) throws java.io.FileNotFoundException {
        try {
            return new ParcelFileDescriptor(f, "r");
        } catch (IOException e) {
            throw new java.io.FileNotFoundException(e.getMessage());
        }
    }

    public FileDescriptor getFileDescriptor() {
        try {
            return raf.getFD();
        } catch (IOException e) {
            return null;
        }
    }

    public long getStatSize() {
        return file.length();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}
