package android.view;

public interface ViewParent {
    ViewParent getParent();

    void requestDisallowInterceptTouchEvent(boolean disallow);

    void requestLayout();

    boolean isLayoutRequested();
}
