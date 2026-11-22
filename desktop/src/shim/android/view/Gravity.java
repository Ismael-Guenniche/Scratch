package android.view;

public class Gravity {
    public static final int NO_GRAVITY = 0;
    public static final int AXIS_SPECIFIED = 1;
    public static final int TOP = 0x30;
    public static final int BOTTOM = 0x50;
    public static final int LEFT = 0x03;
    public static final int RIGHT = 0x05;
    public static final int CENTER_VERTICAL = 0x10;
    public static final int FILL_VERTICAL = 0x70;
    public static final int CENTER_HORIZONTAL = 0x01;
    public static final int FILL_HORIZONTAL = 0x07;
    public static final int CENTER = 0x11;
    public static final int FILL = 0x77;
    public static final int START = 0x00800003;
    public static final int END = 0x00800005;
    public static final int HORIZONTAL_GRAVITY_MASK = 0x07;
    public static final int VERTICAL_GRAVITY_MASK = 0x70;
    public static final int RELATIVE_HORIZONTAL_GRAVITY_MASK = 0x00800007;

    /** Desplazamiento horizontal de algo de ancho w dentro de un hueco de ancho space. */
    public static int offX(int gravity, int space, int w) {
        switch (gravity & HORIZONTAL_GRAVITY_MASK) {
            case CENTER_HORIZONTAL:
                return (space - w) / 2;
            case RIGHT:
                return space - w;
            default:
                return 0;
        }
    }

    public static int offY(int gravity, int space, int h) {
        switch (gravity & VERTICAL_GRAVITY_MASK) {
            case CENTER_VERTICAL:
                return (space - h) / 2;
            case BOTTOM:
                return space - h;
            default:
                return 0;
        }
    }
}
