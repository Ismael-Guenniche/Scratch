package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.view.Gravity;

/**
 * Campo de texto. Sin foco se pinta como un TextView; al pulsarlo se coloca encima un campo de Swing
 * (desk.Peers) que aporta cursor, selección, portapapeles y métodos de entrada.
 */
public class EditText extends TextView {
    private final SpannableStringBuilder editable = new SpannableStringBuilder();
    private boolean wantFocus;
    private boolean syncing;
    public int selStart = -1;
    public int selEnd = -1;

    public EditText(Context c) {
        super(c);
        setClickable(true);
        setFocusable(true);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        editable.onChange = new Runnable() {
            @Override
            public void run() {
                if (!syncing) {
                    EditText.super.setText(editable.toString());
                    desk.Peers.textSetByCode(EditText.this);
                }
            }
        };
    }

    public EditText(Context c, Object attrs) {
        this(c);
    }

    @Override
    public Editable getText() {
        return editable;
    }

    @Override
    public void setText(CharSequence t) {
        String s = t == null ? "" : t.toString();
        String old = editable.toString();
        if (s.equals(old)) {
            return;
        }
        fireBefore(old, s);
        syncing = true;
        editable.setSilently(s);
        syncing = false;
        super.setText(s);
        desk.Peers.textSetByCode(this);
        fireAfter(old, s);
    }

    /** El usuario escribió en el campo nativo. */
    public void typed(String s) {
        String old = editable.toString();
        if (s.equals(old)) {
            return;
        }
        fireBefore(old, s);
        syncing = true;
        editable.setSilently(s);
        syncing = false;
        super.setText(s);
        fireAfter(old, s);
    }

    private void fireBefore(String old, String now) {
        if (watchers != null) {
            for (TextWatcher w : new java.util.ArrayList<TextWatcher>(watchers)) {
                w.beforeTextChanged(old, 0, old.length(), now.length());
            }
        }
    }

    private void fireAfter(String old, String now) {
        if (watchers != null) {
            for (TextWatcher w : new java.util.ArrayList<TextWatcher>(watchers)) {
                w.onTextChanged(now, 0, old.length(), now.length());
            }
            for (TextWatcher w : new java.util.ArrayList<TextWatcher>(watchers)) {
                w.afterTextChanged(editable);
            }
        }
    }

    /** Intro en el campo: avisa al oyente de acción del editor. Devuelve true si lo atendió. */
    public boolean enterPressed() {
        return editorAction != null && editorAction.onEditorAction(this,
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE, null);
    }

    public void setSelection(int i) {
        selStart = i;
        selEnd = i;
        desk.Peers.selection(this);
    }

    public void setSelection(int a, int b) {
        selStart = a;
        selEnd = b;
        desk.Peers.selection(this);
    }

    public void selectAll() {
        setSelection(0, editable.length());
    }

    public int getSelectionStart() {
        return selStart < 0 ? editable.length() : selStart;
    }

    public int getSelectionEnd() {
        return selEnd < 0 ? editable.length() : selEnd;
    }

    public void setSelectAllOnFocus(boolean b) {
    }

    public void setCursorVisible(boolean b) {
    }

    public boolean isMultiLine() {
        return getMaxLines() > 1 && (inputType & android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                || (getMaxLines() > 1 && inputType == 0);
    }

    @Override
    public boolean isTextCursor() {
        return isEnabled();
    }

    @Override
    public boolean performClick() {
        super.performClick();
        requestFocus();
        return true;
    }

    @Override
    public boolean requestFocus() {
        if (!isEnabled()) {
            return false;
        }
        if (layer() == null || getWidth() == 0) {
            wantFocus = true;
            if (layer() != null) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (wantFocus && layer() != null) {
                            wantFocus = false;
                            desk.Peers.focus(EditText.this);
                        }
                    }
                });
            }
            return true;
        }
        desk.Peers.focus(this);
        return true;
    }

    @Override
    public void clearFocus() {
        wantFocus = false;
        if (desk.Peers.isActive(this)) {
            desk.Peers.blur();
        }
    }

    @Override
    public boolean hasFocus() {
        return desk.Peers.isActive(this);
    }

    @Override
    public boolean isFocused() {
        return desk.Peers.isActive(this);
    }

    @Override
    public android.view.View findFocus() {
        return desk.Peers.isActive(this) ? this : null;
    }

    public void focusChanged(boolean has) {
        fireFocus(has);
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (wantFocus) {
            post(new Runnable() {
                @Override
                public void run() {
                    if (wantFocus && layer() != null) {
                        wantFocus = false;
                        desk.Peers.focus(EditText.this);
                    }
                }
            });
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (desk.Peers.isActive(this)) {
            desk.Peers.blur();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(android.view.View v, int vis) {
        if (vis != VISIBLE && desk.Peers.isActive(this)) {
            desk.Peers.blur();
        }
    }

    @Override
    protected boolean peerShowsText() {
        return desk.Peers.isActive(this);
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        if (desk.Peers.isActive(this)) {
            desk.Peers.reposition();
        }
    }
}
