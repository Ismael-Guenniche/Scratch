package android.graphics;

public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {
    }

    public Rect(int l, int t, int r, int b) {
        set(l, t, r, b);
    }

    public Rect(Rect o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public void set(int l, int t, int r, int b) {
        left = l;
        top = t;
        right = r;
        bottom = b;
    }

    public void set(Rect o) {
        set(o.left, o.top, o.right, o.bottom);
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    public int centerX() {
        return (left + right) >> 1;
    }

    public int centerY() {
        return (top + bottom) >> 1;
    }

    public float exactCenterX() {
        return (left + right) * 0.5f;
    }

    public float exactCenterY() {
        return (top + bottom) * 0.5f;
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public void setEmpty() {
        set(0, 0, 0, 0);
    }

    public boolean contains(int x, int y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public void offset(int dx, int dy) {
        left += dx;
        right += dx;
        top += dy;
        bottom += dy;
    }

    public void offsetTo(int l, int t) {
        right += l - left;
        bottom += t - top;
        left = l;
        top = t;
    }

    public void inset(int dx, int dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public boolean intersects(int l, int t, int r, int b) {
        return left < r && l < right && top < b && t < bottom;
    }

    public void union(Rect o) {
        if (o.isEmpty()) {
            return;
        }
        if (isEmpty()) {
            set(o);
            return;
        }
        left = Math.min(left, o.left);
        top = Math.min(top, o.top);
        right = Math.max(right, o.right);
        bottom = Math.max(bottom, o.bottom);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Rect)) {
            return false;
        }
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        return ((left * 31 + top) * 31 + right) * 31 + bottom;
    }

    @Override
    public String toString() {
        return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }
}
