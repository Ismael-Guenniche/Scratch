package android.widget;

import android.content.Context;
import android.view.ViewGroup;

public abstract class AbsListView extends AdapterView<ListAdapter> {
    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChanged(AbsListView view, int scrollState);

        void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }

    public static class LayoutParams extends ViewGroup.LayoutParams {
        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(int w, int h, int viewType) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams o) {
            super(o);
        }
    }

    public static final int CHOICE_MODE_NONE = 0;
    public static final int TRANSCRIPT_MODE_DISABLED = 0;

    public AbsListView(Context c) {
        super(c);
    }
}
