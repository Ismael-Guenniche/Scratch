package android.content;

public interface DialogInterface {
    int BUTTON_POSITIVE = -1;
    int BUTTON_NEGATIVE = -2;
    int BUTTON_NEUTRAL = -3;

    interface OnDismissListener {
        void onDismiss(DialogInterface d);
    }

    interface OnCancelListener {
        void onCancel(DialogInterface d);
    }

    interface OnShowListener {
        void onShow(DialogInterface d);
    }

    interface OnClickListener {
        void onClick(DialogInterface d, int which);
    }

    interface OnKeyListener {
        boolean onKey(DialogInterface d, int keyCode, android.view.KeyEvent e);
    }

    void cancel();

    void dismiss();
}
