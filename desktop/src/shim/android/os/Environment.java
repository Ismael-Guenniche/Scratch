package android.os;

import java.io.File;

public final class Environment {
    public static final String DIRECTORY_MUSIC = "Music";
    public static final String DIRECTORY_DOWNLOADS = "Downloads";
    public static final String DIRECTORY_PICTURES = "Pictures";
    public static final String DIRECTORY_DOCUMENTS = "Documents";

    private Environment() {
    }

    public static File getExternalStorageDirectory() {
        return new File(System.getProperty("user.home", "."));
    }

    public static File getExternalStoragePublicDirectory(String type) {
        return new File(getExternalStorageDirectory(), type);
    }
}
