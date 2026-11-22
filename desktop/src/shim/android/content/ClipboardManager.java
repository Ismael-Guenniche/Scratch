package android.content;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

public class ClipboardManager {
    public void setPrimaryClip(ClipData clip) {
        try {
            CharSequence t = clip.getItemCount() > 0 ? clip.getItemAt(0).coerceToText(null) : "";
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(String.valueOf(t)), null);
        } catch (Throwable ignored) {
        }
    }

    public boolean hasPrimaryClip() {
        return getPrimaryClip() != null;
    }

    public ClipData getPrimaryClip() {
        try {
            Object o = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            return o == null ? null : ClipData.newPlainText("", o.toString());
        } catch (Throwable e) {
            return null;
        }
    }

    public void setText(CharSequence t) {
        setPrimaryClip(ClipData.newPlainText("", t));
    }
}
