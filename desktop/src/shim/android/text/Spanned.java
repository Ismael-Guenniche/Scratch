package android.text;

public interface Spanned extends CharSequence {
    int SPAN_INCLUSIVE_EXCLUSIVE = 17;
    int SPAN_INCLUSIVE_INCLUSIVE = 18;
    int SPAN_EXCLUSIVE_EXCLUSIVE = 33;
    int SPAN_EXCLUSIVE_INCLUSIVE = 34;

    <T> T[] getSpans(int start, int end, Class<T> type);

    int getSpanStart(Object span);

    int getSpanEnd(Object span);
}
