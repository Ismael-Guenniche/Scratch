package android.os;

public final class Build {
    /** "Nothing" para que el estilo por defecto sea el de puntos, como en el móvil del autor. */
    public static final String MANUFACTURER = "Nothing";
    public static final String MODEL = "PC";
    public static final String BRAND = "Windows";
    public static final String DEVICE = "pc";
    public static final String PRODUCT = "pc";

    public static final class VERSION {
        /** 28: evita MediaStore, permisos de notificaciones y otras rutas que no existen en un PC. */
        public static final int SDK_INT = 28;
        public static final String RELEASE = System.getProperty("os.name", "PC");
    }

    public static final class VERSION_CODES {
        public static final int M = 23;
        public static final int N = 24;
        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
        public static final int Q = 29;
        public static final int R = 30;
        public static final int S = 31;
        public static final int TIRAMISU = 33;
        public static final int UPSIDE_DOWN_CAKE = 34;
    }
}
