package desk;

import android.os.Handler;
import android.os.Looper;

/** Handler compartido del hilo principal (para View.post y compañía). */
public final class Main {
    public static final Handler H = new Handler(Looper.getMainLooper());

    private Main() {
    }
}
