package android.content;

public class ContextWrapper extends Context {
    private final Context base;

    public ContextWrapper(Context base) {
        this.base = base;
    }

    @Override
    public Context getBaseContext() {
        return base;
    }
}
