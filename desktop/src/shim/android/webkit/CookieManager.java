package android.webkit;

/** En el escritorio no hay navegador incrustado: no hay cookies que guardar ni borrar. */
public class CookieManager {
    private static final CookieManager I = new CookieManager();

    public static CookieManager getInstance() {
        return I;
    }

    public void setAcceptCookie(boolean b) {
    }

    public void removeAllCookies(ValueCallback<Boolean> cb) {
        if (cb != null) {
            cb.onReceiveValue(Boolean.TRUE);
        }
    }

    public void flush() {
    }
}
