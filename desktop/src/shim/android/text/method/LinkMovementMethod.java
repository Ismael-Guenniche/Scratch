package android.text.method;

public class LinkMovementMethod implements MovementMethod {
    public static MovementMethod getInstance() {
        return new LinkMovementMethod();
    }
}
