package android.view;

public final class MotionEvent {
    public static final int ACTION_MASK = 0xff;
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_OUTSIDE = 4;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_SCROLL = 8;
    public static final int ACTION_POINTER_INDEX_MASK = 0xff00;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int AXIS_X = 0;
    public static final int AXIS_Y = 1;
    public static final int AXIS_VSCROLL = 9;
    public static final int AXIS_HSCROLL = 10;
    public static final int BUTTON_PRIMARY = 1;
    public static final int BUTTON_SECONDARY = 2;
    public static final int TOOL_TYPE_FINGER = 1;
    public static final int TOOL_TYPE_MOUSE = 3;

    private int action;
    private float x;
    private float y;
    private float rawX;
    private float rawY;
    private long downTime;
    private long eventTime;
    private float vscroll;
    private float hscroll;

    private MotionEvent() {
    }

    public static MotionEvent obtain(long downTime, long eventTime, int action, float x, float y, int meta) {
        MotionEvent e = new MotionEvent();
        e.downTime = downTime;
        e.eventTime = eventTime;
        e.action = action;
        e.x = x;
        e.y = y;
        e.rawX = x;
        e.rawY = y;
        return e;
    }

    public static MotionEvent obtain(MotionEvent o) {
        MotionEvent e = new MotionEvent();
        e.downTime = o.downTime;
        e.eventTime = o.eventTime;
        e.action = o.action;
        e.x = o.x;
        e.y = o.y;
        e.rawX = o.rawX;
        e.rawY = o.rawY;
        e.vscroll = o.vscroll;
        e.hscroll = o.hscroll;
        return e;
    }

    public void setRaw(float rx, float ry) {
        rawX = rx;
        rawY = ry;
    }

    public void setScroll(float h, float v) {
        hscroll = h;
        vscroll = v;
    }

    public void recycle() {
    }

    public int getAction() {
        return action;
    }

    public int getActionMasked() {
        return action & ACTION_MASK;
    }

    public void setAction(int a) {
        action = a;
    }

    public int getActionIndex() {
        return 0;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getX(int i) {
        return x;
    }

    public float getY(int i) {
        return y;
    }

    public float getRawX() {
        return rawX;
    }

    public float getRawY() {
        return rawY;
    }

    public int getPointerCount() {
        return 1;
    }

    public int getPointerId(int i) {
        return 0;
    }

    public int findPointerIndex(int id) {
        return id == 0 ? 0 : -1;
    }

    public long getDownTime() {
        return downTime;
    }

    public long getEventTime() {
        return eventTime;
    }

    public long getEventTimeNanos() {
        return eventTime * 1000000L;
    }

    public int getHistorySize() {
        return 0;
    }

    public float getHistoricalX(int i) {
        return x;
    }

    public float getHistoricalY(int i) {
        return y;
    }

    public long getHistoricalEventTime(int i) {
        return eventTime;
    }

    public float getPressure() {
        return 1f;
    }

    public float getSize() {
        return 1f;
    }

    public int getToolType(int i) {
        return TOOL_TYPE_MOUSE;
    }

    public int getButtonState() {
        return BUTTON_PRIMARY;
    }

    public int getMetaState() {
        return 0;
    }

    public int getSource() {
        return 0x2002;
    }

    public float getAxisValue(int axis) {
        return axis == AXIS_VSCROLL ? vscroll : axis == AXIS_HSCROLL ? hscroll : axis == AXIS_X ? x
                : axis == AXIS_Y ? y : 0;
    }

    public void offsetLocation(float dx, float dy) {
        x += dx;
        y += dy;
    }

    public void setLocation(float nx, float ny) {
        x = nx;
        y = ny;
    }
}
