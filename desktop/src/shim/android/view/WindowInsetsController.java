package android.view;

/** En el escritorio no hay barras del sistema: ocultar y mostrar no hacen nada. */
public final class WindowInsetsController {
    public static final int BEHAVIOR_DEFAULT = 1;
    public static final int BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE = 2;

    public void hide(int types) {
    }

    public void show(int types) {
    }

    public void setSystemBarsBehavior(int behavior) {
    }
}
