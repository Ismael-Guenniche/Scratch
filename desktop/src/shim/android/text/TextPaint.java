package android.text;

public class TextPaint extends android.graphics.Paint {
    public float density = 1f;
    public int linkColor;

    public TextPaint() {
        super(ANTI_ALIAS_FLAG);
    }

    public TextPaint(int flags) {
        super(flags);
    }
}
