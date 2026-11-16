package android.content.res;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** Los assets van dentro del .jar, en /assets/. */
public final class AssetManager {
    public InputStream open(String name) throws IOException {
        InputStream in = AssetManager.class.getResourceAsStream("/assets/" + name);
        if (in == null) {
            throw new FileNotFoundException(name);
        }
        return in;
    }

    public InputStream open(String name, int mode) throws IOException {
        return open(name);
    }

    public String[] list(String path) {
        return new String[0];
    }
}
