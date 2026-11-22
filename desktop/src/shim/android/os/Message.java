package android.os;

public final class Message {
    public int what;
    public int arg1;
    public int arg2;
    public Object obj;
    Handler target;

    public static Message obtain() {
        return new Message();
    }

    public void sendToTarget() {
        if (target != null) {
            target.sendMessage(this);
        }
    }
}
