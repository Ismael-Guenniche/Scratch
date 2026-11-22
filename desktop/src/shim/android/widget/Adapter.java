package android.widget;

import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;

public interface Adapter {
    int IGNORE_ITEM_VIEW_TYPE = -1;

    void registerDataSetObserver(DataSetObserver o);

    void unregisterDataSetObserver(DataSetObserver o);

    int getCount();

    Object getItem(int position);

    long getItemId(int position);

    boolean hasStableIds();

    View getView(int position, View convertView, ViewGroup parent);

    int getItemViewType(int position);

    int getViewTypeCount();

    boolean isEmpty();
}
