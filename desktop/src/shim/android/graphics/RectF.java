package android.graphics;

public class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {
    }

    public RectF(float l, float t, float r, float b) {
        set(l, t, r, b);
    }

    public RectF(RectF o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public RectF(Rect o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public void set(float l, float t, float r, float b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public void set(RectF o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public void set(Rect o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public void setEmpty() {
        set(0, 0, 0, 0);
    }

    public boolean contains(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public void offset(float dx, float dy) {
        left += dx;
        right += dx;
        top += dy;
        bottom += dy;
    }

    public void offsetTo(float l, float t) {
        right += l - left;
        bottom += t - top;
        left = l;
        top = t;
    }

    public void inset(float dx, float dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public void round(Rect dst) {
        dst.set(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
    }

    public void roundOut(Rect dst) {
        dst.set((int) Math.floor(left), (int) Math.floor(top), (int) Math.ceil(right), (int) Math.ceil(bottom));
    }

    @Override
    public String toString() {
        return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }
}
