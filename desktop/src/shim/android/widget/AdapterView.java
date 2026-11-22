package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

public abstract class AdapterView<T extends Adapter> extends ViewGroup {
    public static final int INVALID_POSITION = -1;
    public static final long INVALID_ROW_ID = Long.MIN_VALUE;

    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }

    public interface OnItemLongClickListener {
        boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);

        void onNothingSelected(AdapterView<?> parent);
    }

    protected OnItemClickListener itemClick;
    protected OnItemLongClickListener itemLongClick;
    private View emptyView;

    public AdapterView(Context c) {
        super(c);
    }

    public abstract T getAdapter();

    public abstract void setAdapter(T adapter);

    public void setOnItemClickListener(OnItemClickListener l) {
        itemClick = l;
    }

    public OnItemClickListener getOnItemClickListener() {
        return itemClick;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener l) {
        itemLongClick = l;
        setLongClickable(l != null);
    }

    public void setOnItemSelectedListener(OnItemSelectedListener l) {
    }

    public boolean performItemClick(View view, int position, long id) {
        if (itemClick != null) {
            itemClick.onItemClick(this, view, position, id);
            return true;
        }
        return false;
    }

    public void setEmptyView(View v) {
        emptyView = v;
    }

    public View getEmptyView() {
        return emptyView;
    }

    public int getCount() {
        T a = getAdapter();
        return a == null ? 0 : a.getCount();
    }

    public Object getItemAtPosition(int position) {
        T a = getAdapter();
        return a == null || position < 0 ? null : a.getItem(position);
    }

    public long getItemIdAtPosition(int position) {
        T a = getAdapter();
        return a == null || position < 0 ? INVALID_ROW_ID : a.getItemId(position);
    }

    public abstract int getFirstVisiblePosition();

    public int getLastVisiblePosition() {
        return getFirstVisiblePosition() + getChildCount() - 1;
    }

    public int getPositionForView(View view) {
        View v = view;
        while (v != null && v.getParent() != this) {
            v = v.getParent() instanceof View ? (View) v.getParent() : null;
        }
        if (v == null) {
            return INVALID_POSITION;
        }
        int i = indexOfChild(v);
        return i < 0 ? INVALID_POSITION : getFirstVisiblePosition() + i;
    }

    public abstract void setSelection(int position);

    @Override
    public void addView(View child) {
        throw new UnsupportedOperationException("addView(View) is not supported in AdapterView");
    }

    @Override
    public void addView(View child, int index) {
        throw new UnsupportedOperationException("addView(View, int) is not supported in AdapterView");
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams lp) {
        throw new UnsupportedOperationException("addView(View, LayoutParams) is not supported in AdapterView");
    }

    @Override
    public void removeAllViews() {
        throw new UnsupportedOperationException("removeAllViews() is not supported in AdapterView");
    }
}
