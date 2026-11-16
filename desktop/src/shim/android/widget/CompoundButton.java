package android.widget;

import android.content.Context;

public abstract class CompoundButton extends Button {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    private boolean checked;
    private OnCheckedChangeListener listener;

    public CompoundButton(Context c) {
        super(c);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean b) {
        if (b != checked) {
            checked = b;
            invalidate();
            if (listener != null) {
                listener.onCheckedChanged(this, b);
            }
        }
    }

    public void toggle() {
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    public void setButtonTintList(android.content.res.ColorStateList l) {
    }

    @Override
    public boolean performClick() {
        toggle();
        return super.performClick() || true;
    }
}
