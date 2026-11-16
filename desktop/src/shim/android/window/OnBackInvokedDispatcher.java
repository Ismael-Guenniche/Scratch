package android.window;

public interface OnBackInvokedDispatcher {
    int PRIORITY_DEFAULT = 0;
    int PRIORITY_OVERLAY = 1000000;

    void registerOnBackInvokedCallback(int priority, OnBackInvokedCallback cb);

    void unregisterOnBackInvokedCallback(OnBackInvokedCallback cb);
}
