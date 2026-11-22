package android.content;

import java.util.ArrayList;

public class IntentFilter {
    private final ArrayList<String> actions = new ArrayList<String>();

    public IntentFilter() {
    }

    public IntentFilter(String action) {
        actions.add(action);
    }

    public void addAction(String a) {
        actions.add(a);
    }

    public boolean hasAction(String a) {
        return actions.contains(a);
    }

    public void setPriority(int p) {
    }
}
