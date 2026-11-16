package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Handler {
    public interface Callback {
        boolean handleMessage(Message msg);
    }

    private final Looper looper;
    private final Callback callback;
    private final HashMap<Object, ArrayList<Tok>> pending = new HashMap<Object, ArrayList<Tok>>();

    private final class Tok implements Runnable {
        final Object key;
        final Runnable r;
        volatile boolean cancelled;
        ScheduledFuture<?> future;

        Tok(Object key, Runnable r) {
            this.key = key;
            this.r = r;
        }

        @Override
        public void run() {
            synchronized (pending) {
                if (cancelled) {
                    return;
                }
                ArrayList<Tok> l = pending.get(key);
                if (l != null) {
                    l.remove(this);
                    if (l.isEmpty()) {
                        pending.remove(key);
                    }
                }
            }
            try {
                r.run();
            } catch (RuntimeException e) {
                desk.Rt.log(e);
                if (desk.Rt.DEBUG) {
                    throw e;
                }
            }
        }
    }

    public Handler() {
        this(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper());
    }

    public Handler(Looper l) {
        this(l, null);
    }

    public Handler(Callback cb) {
        this(Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper(), cb);
    }

    public Handler(Looper l, Callback cb) {
        looper = l == null ? Looper.getMainLooper() : l;
        callback = cb;
    }

    public Looper getLooper() {
        return looper;
    }

    private boolean enqueue(Object key, Runnable r, long delay) {
        final Tok t = new Tok(key, r);
        synchronized (pending) {
            ArrayList<Tok> l = pending.get(key);
            if (l == null) {
                l = new ArrayList<Tok>(2);
                pending.put(key, l);
            }
            l.add(t);
        }
        if (delay <= 0) {
            looper.dispatch(t);
        } else {
            t.future = desk.Rt.TIMER.schedule(new Runnable() {
                @Override
                public void run() {
                    if (!t.cancelled) {
                        looper.dispatch(t);
                    }
                }
            }, delay, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    public final boolean post(Runnable r) {
        return enqueue(r, r, 0);
    }

    public final boolean postDelayed(Runnable r, long ms) {
        return enqueue(r, r, ms);
    }

    public final boolean postAtTime(Runnable r, long uptime) {
        return enqueue(r, r, uptime - SystemClock.uptimeMillis());
    }

    public final boolean postAtFrontOfQueue(Runnable r) {
        return enqueue(r, r, 0);
    }

    public final void removeCallbacks(Runnable r) {
        cancel(r);
    }

    private void cancel(Object key) {
        synchronized (pending) {
            ArrayList<Tok> l = pending.remove(key);
            if (l != null) {
                for (Tok t : l) {
                    t.cancelled = true;
                    if (t.future != null) {
                        t.future.cancel(false);
                    }
                }
            }
        }
    }

    public final void removeCallbacksAndMessages(Object token) {
        synchronized (pending) {
            for (ArrayList<Tok> l : pending.values()) {
                for (Tok t : l) {
                    t.cancelled = true;
                    if (t.future != null) {
                        t.future.cancel(false);
                    }
                }
            }
            pending.clear();
        }
    }

    public void handleMessage(Message msg) {
    }

    public final Message obtainMessage() {
        Message m = new Message();
        m.target = this;
        return m;
    }

    public final Message obtainMessage(int what) {
        Message m = obtainMessage();
        m.what = what;
        return m;
    }

    public final Message obtainMessage(int what, Object obj) {
        Message m = obtainMessage(what);
        m.obj = obj;
        return m;
    }

    public final boolean sendMessage(Message m) {
        return sendMessageDelayed(m, 0);
    }

    public final boolean sendEmptyMessage(int what) {
        return sendMessageDelayed(obtainMessage(what), 0);
    }

    public final boolean sendEmptyMessageDelayed(int what, long ms) {
        return sendMessageDelayed(obtainMessage(what), ms);
    }

    public final boolean sendMessageDelayed(final Message m, long ms) {
        return enqueue(Integer.valueOf(m.what), new Runnable() {
            @Override
            public void run() {
                if (callback == null || !callback.handleMessage(m)) {
                    handleMessage(m);
                }
            }
        }, ms);
    }

    public final void removeMessages(int what) {
        cancel(Integer.valueOf(what));
    }

    public final boolean hasMessages(int what) {
        synchronized (pending) {
            return pending.containsKey(Integer.valueOf(what));
        }
    }
}
