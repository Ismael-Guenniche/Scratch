package android.widget;

import android.database.DataSetObserver;
import java.util.ArrayList;

public abstract class BaseAdapter implements ListAdapter {
    private final ArrayList<DataSetObserver> observers = new ArrayList<DataSetObserver>();

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver o) {
        observers.add(o);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver o) {
        observers.remove(o);
    }

    public void notifyDataSetChanged() {
        for (DataSetObserver o : new ArrayList<DataSetObserver>(observers)) {
            o.onChanged();
        }
    }

    public void notifyDataSetInvalidated() {
        for (DataSetObserver o : new ArrayList<DataSetObserver>(observers)) {
            o.onInvalidated();
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }
}
