package android.content;

public final class ComponentName {
    private final String pkg;
    private final String cls;

    public ComponentName(String pkg, String cls) {
        this.pkg = pkg;
        this.cls = cls;
    }

    public ComponentName(Context c, Class<?> k) {
        this(c.getPackageName(), k.getName());
    }

    public ComponentName(Context c, String cls) {
        this(c.getPackageName(), cls);
    }

    public String getPackageName() {
        return pkg;
    }

    public String getClassName() {
        return cls;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ComponentName && ((ComponentName) o).pkg.equals(pkg)
                && ((ComponentName) o).cls.equals(cls);
    }

    @Override
    public int hashCode() {
        return pkg.hashCode() * 31 + cls.hashCode();
    }

    @Override
    public String toString() {
        return pkg + "/" + cls;
    }
}
