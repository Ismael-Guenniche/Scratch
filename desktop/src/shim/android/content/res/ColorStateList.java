package android.content.res;

public class ColorStateList {
    private final int[][] states;
    private final int[] colors;

    public ColorStateList(int[][] states, int[] colors) {
        this.states = states;
        this.colors = colors;
    }

    public static ColorStateList valueOf(int c) {
        return new ColorStateList(new int[][] {{}}, new int[] {c});
    }

    public int getDefaultColor() {
        return colors.length == 0 ? 0 : colors[colors.length - 1];
    }

    public int getColorForState(int[] state, int def) {
        for (int i = 0; i < states.length; i++) {
            if (android.graphics.drawable.StateListDrawable.matches(states[i], state)) {
                return colors[i];
            }
        }
        return def;
    }

    public boolean isStateful() {
        return states.length > 1;
    }
}
