package desk;

import android.widget.EditText;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

/** El campo de texto nativo que se superpone al EditText que tiene el foco (solo uno a la vez). */
public final class Peers {
    private static EditText active;
    private static JTextComponent comp;
    private static Pane pane;
    private static boolean setting;

    private Peers() {
    }

    public static boolean isActive(EditText e) {
        return active == e;
    }

    public static boolean anyFocused() {
        return active != null;
    }

    public static void focus(final EditText e) {
        if (active == e) {
            if (comp != null) {
                comp.requestFocusInWindow();
            }
            return;
        }
        blur();
        Layer l = e.layer();
        if (l == null || l.pane == null) {
            return;
        }
        pane = l.pane;
        active = e;
        final JTextComponent c;
        if (e.isMultiLine()) {
            JTextArea a = new JTextArea();
            a.setLineWrap(true);
            a.setWrapStyleWord(true);
            c = a;
        } else {
            JTextField f = new JTextField();
            f.addActionListener(new java.awt.event.ActionListener() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    EditText a = active;
                    if (a != null && !a.enterPressed()) {
                        Shell sh = pane == null ? null : pane.shell;
                        if (sh != null) {
                            sh.enterInField(a);
                        }
                    }
                }
            });
            c = f;
        }
        comp = c;
        c.setOpaque(false);
        c.setBorder(BorderFactory.createEmptyBorder());
        c.setFont(e.getPaint().awtFont());
        int col = e.getCurrentTextColor();
        c.setForeground(new Color(col | 0xFF000000, false));
        c.setCaretColor(new Color(col | 0xFF000000, false));
        c.setSelectionColor(new Color((col & 0xFFFFFF) | 0x55000000, true));
        c.setSelectedTextColor(new Color(col | 0xFF000000, false));
        setting = true;
        c.setText(e.getText().toString());
        setting = false;
        int a = e.selStart < 0 ? c.getText().length() : Math.min(e.selStart, c.getText().length());
        int b = e.selEnd < 0 ? c.getText().length() : Math.min(e.selEnd, c.getText().length());
        c.select(a, b);
        c.getDocument().addDocumentListener(new DocumentListener() {
            private void ch() {
                if (!setting && active == e && comp == c) {
                    e.typed(c.getText());
                }
            }

            @Override
            public void insertUpdate(DocumentEvent ev) {
                ch();
            }

            @Override
            public void removeUpdate(DocumentEvent ev) {
                ch();
            }

            @Override
            public void changedUpdate(DocumentEvent ev) {
            }
        });
        c.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent ev) {
                if (!ev.isTemporary() && comp == c) {
                    blur();
                }
            }
        });
        c.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "desk-esc");
        c.getActionMap().put("desk-esc", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                Pane p = pane;
                blur();
                if (p != null) {
                    p.requestFocusInWindow();
                }
            }
        });
        pane.add(c);
        reposition();
        c.requestFocusInWindow();
        e.focusChanged(true);
    }

    public static void blur() {
        EditText e = active;
        JTextComponent c = comp;
        Pane p = pane;
        if (e == null) {
            return;
        }
        active = null;
        comp = null;
        pane = null;
        if (c != null) {
            e.typed(c.getText());
            if (p != null) {
                p.remove(c);
                p.repaint();
            }
        }
        e.focusChanged(false);
    }

    /** Se pulsó en el panel (fuera del campo nativo): se suelta el foco. */
    public static void blurIfOutside(Pane p, int x, int y) {
        if (active != null) {
            Pane was = pane;
            blur();
            if (was != null) {
                was.requestFocusInWindow();
            }
        }
    }

    public static void textSetByCode(EditText e) {
        if (active == e && comp != null && !comp.getText().equals(e.getText().toString())) {
            setting = true;
            comp.setText(e.getText().toString());
            setting = false;
        }
    }

    public static void selection(EditText e) {
        if (active == e && comp != null) {
            int n = comp.getText().length();
            comp.select(Math.max(0, Math.min(n, e.selStart)), Math.max(0, Math.min(n, e.selEnd)));
        }
    }

    /** Coloca el campo nativo sobre el EditText (se llama al pintarlo, por si se movió). */
    public static void reposition() {
        final EditText e = active;
        final JTextComponent c = comp;
        if (e == null || c == null) {
            return;
        }
        int[] xy = new int[2];
        e.getLocationInWindow(xy);
        final Rectangle r = new Rectangle(xy[0] + e.getPaddingLeft(), xy[1] + e.getPaddingTop(),
                Math.max(8, e.getWidth() - e.getPaddingLeft() - e.getPaddingRight()),
                Math.max(8, e.getHeight() - e.getPaddingTop() - e.getPaddingBottom()));
        if (!r.equals(c.getBounds())) {
            javax.swing.SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (comp == c) {
                        c.setBounds(r);
                    }
                }
            });
        }
    }
}
