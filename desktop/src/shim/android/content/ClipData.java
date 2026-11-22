package android.content;

import android.net.Uri;
import java.util.ArrayList;

public class ClipData {
    public static class Item {
        private final CharSequence text;
        private final Uri uri;

        public Item(CharSequence t) {
            text = t;
            uri = null;
        }

        public Item(Uri u) {
            text = null;
            uri = u;
        }

        public CharSequence getText() {
            return text;
        }

        public Uri getUri() {
            return uri;
        }

        public CharSequence coerceToText(Context c) {
            return text != null ? text : uri != null ? uri.toString() : "";
        }
    }

    private final ArrayList<Item> items = new ArrayList<Item>();

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        ClipData c = new ClipData();
        c.items.add(new Item(text));
        return c;
    }

    public static ClipData newUris(java.util.List<Uri> uris) {
        ClipData c = new ClipData();
        for (Uri u : uris) {
            c.items.add(new Item(u));
        }
        return c;
    }

    public int getItemCount() {
        return items.size();
    }

    public Item getItemAt(int i) {
        return items.get(i);
    }

    public void addItem(Item i) {
        items.add(i);
    }
}
