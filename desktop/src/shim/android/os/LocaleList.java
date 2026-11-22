package android.os;

import java.util.Locale;

public final class LocaleList {
    private final Locale l;

    public LocaleList(Locale l) {
        this.l = l;
    }

    public Locale get(int i) {
        return l;
    }

    public int size() {
        return 1;
    }

    public boolean isEmpty() {
        return false;
    }

    public static LocaleList getDefault() {
        return new LocaleList(Locale.getDefault());
    }
}
