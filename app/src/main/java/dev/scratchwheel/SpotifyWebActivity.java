package dev.scratchwheel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

/**
 * EXPERIMENTAL: inicias sesión en el reproductor web de Spotify y la app reutiliza el token que la propia página
 * usa (solo lectura de tus listas). No usa la API de desarrollador, así que no necesita Premium.
 * Spotify puede cambiar esto cuando quiera.
 */
public class SpotifyWebActivity extends Activity {
    private volatile boolean testing;
    private volatile boolean done;
    private volatile String lastTried = "";
    private WebView web;
    private TextView hint;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                try {
                    String host = r.getUrl().getHost();
                    Map<String, String> h = r.getRequestHeaders();
                    String a = h == null ? null : h.get("Authorization");
                    if (host != null && host.endsWith("spotify.com") && a != null && a.startsWith("Bearer ")) {
                        tryToken(a.substring(7));
                    }
                } catch (RuntimeException ignored) {
                }
                return null;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        hint = new TextView(this);
        hint.setText(Tr.t("INICIA SESIÓN EN SPOTIFY · SE CERRARÁ SOLA"));
        hint.setTextColor(Color.WHITE);
        hint.setBackgroundColor(0xCC000000);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(20, 14, 20, 14);
        hint.setTextSize(12);
        root.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));
        setContentView(root);
        web.loadUrl("https://open.spotify.com/");
    }

    /** Un token anónimo también circula antes de iniciar sesión: solo vale el que pasa GET /me. */
    private void tryToken(final String tok) {
        if (done || testing || tok.equals(lastTried)) {
            return;
        }
        testing = true;
        lastTried = tok;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    HttpURLConnection h = (HttpURLConnection) new URL("https://api.spotify.com/v1/me").openConnection();
                    h.setRequestProperty("Authorization", "Bearer " + tok);
                    h.setConnectTimeout(10000);
                    h.setReadTimeout(10000);
                    ok = h.getResponseCode() == 200;
                } catch (Exception ignored) {
                }
                testing = false;
                if (ok && !done) {
                    done = true;
                    SpotifyApi.setWebToken(tok);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setResult(RESULT_OK, new Intent());
                            finish();
                        }
                    });
                }
            }
        }, "spotify-web-test").start();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
        }
        super.onDestroy();
    }
}
