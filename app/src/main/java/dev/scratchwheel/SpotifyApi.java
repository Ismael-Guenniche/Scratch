package dev.scratchwheel;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lee tus listas de Spotify con tu propia cuenta (OAuth PKCE, solo lectura). Necesita un Client ID tuyo. */
final class SpotifyApi {
    static final String REDIRECT = "scratchwheel://spotify";
    private static final String SCOPE = "playlist-read-private playlist-read-collaborative";

    private SpotifyApi() {
    }

    static final class Pl {
        final String id;
        final String name;

        Pl(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences("spotify", Context.MODE_PRIVATE);
    }

    static String clientId(Context c) {
        return sp(c).getString("cid", "");
    }

    static void setClientId(Context c, String id) {
        sp(c).edit().putString("cid", id.trim()).apply();
    }

    static boolean loggedIn(Context c) {
        return sp(c).getString("refresh", "").length() > 0;
    }

    static void logout(Context c) {
        sp(c).edit().remove("refresh").remove("access").remove("exp").apply();
        setWebToken(null);
        try {
            android.webkit.CookieManager.getInstance().removeAllCookies(null);
        } catch (RuntimeException ignored) {
        }
    }

    private static String b64(byte[] b) {
        return Base64.encodeToString(b, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    /** URL de inicio de sesión (se abre en el navegador). */
    static String loginUrl(Context c) throws Exception {
        SecureRandom r = new SecureRandom();
        byte[] v = new byte[48];
        r.nextBytes(v);
        String verifier = b64(v);
        byte[] s = new byte[16];
        r.nextBytes(s);
        String state = b64(s);
        sp(c).edit().putString("verifier", verifier).putString("state", state).apply();
        String challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes("US-ASCII")));
        return "https://accounts.spotify.com/authorize?response_type=code"
                + "&client_id=" + URLEncoder.encode(clientId(c), "UTF-8")
                + "&scope=" + URLEncoder.encode(SCOPE, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT, "UTF-8")
                + "&code_challenge_method=S256&code_challenge=" + challenge
                + "&state=" + state;
    }

    /** Termina el login con la URL scratchwheel://spotify?code=… (en segundo plano). */
    static void finishLogin(Context c, Uri data) throws Exception {
        String err = data.getQueryParameter("error");
        if (err != null) {
            throw new IOException("Spotify: " + (err.length() > 40 ? err.substring(0, 40) : err));
        }
        String code = data.getQueryParameter("code");
        String state = data.getQueryParameter("state");
        if (code == null || state == null || !state.equals(sp(c).getString("state", ""))) {
            throw new IOException(Tr.t("Respuesta de Spotify no válida"));
        }
        sp(c).edit().remove("state").apply(); // el state vale una sola vez
        String body = "grant_type=authorization_code&code=" + URLEncoder.encode(code, "UTF-8")
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT, "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId(c), "UTF-8")
                + "&code_verifier=" + URLEncoder.encode(sp(c).getString("verifier", ""), "UTF-8");
        saveToken(c, new JSONObject(http("https://accounts.spotify.com/api/token", "POST", body, null)));
    }

    private static void saveToken(Context c, JSONObject j) {
        SharedPreferences.Editor e = sp(c).edit();
        e.putString("access", j.optString("access_token"));
        e.putLong("exp", System.currentTimeMillis() + j.optLong("expires_in", 3600) * 1000L);
        String rt = j.optString("refresh_token", "");
        if (rt.length() > 0) {
            e.putString("refresh", rt);
        }
        e.apply();
    }

    private static volatile String webToken;
    private static volatile boolean useWeb;

    /** Modo experimental: token del reproductor web (sin API de desarrollador). */
    static void setWebToken(String t) {
        webToken = t;
        useWeb = t != null;
    }

    static boolean webMode() {
        return useWeb;
    }

    private static String token(Context c) throws Exception {
        if (useWeb && webToken != null) {
            return webToken;
        }
        SharedPreferences p = sp(c);
        if (p.getLong("exp", 0) > System.currentTimeMillis() + 60000 && p.getString("access", "").length() > 0) {
            return p.getString("access", "");
        }
        String body = "grant_type=refresh_token&refresh_token=" + URLEncoder.encode(p.getString("refresh", ""), "UTF-8")
                + "&client_id=" + URLEncoder.encode(clientId(c), "UTF-8");
        saveToken(c, new JSONObject(http("https://accounts.spotify.com/api/token", "POST", body, null)));
        return sp(c).getString("access", "");
    }

    static List<Pl> playlists(Context c) throws Exception {
        List<Pl> out = new ArrayList<Pl>();
        String url = "https://api.spotify.com/v1/me/playlists?limit=50";
        int guard = 0;
        while (url != null && guard++ < 40) {
            JSONObject j = new JSONObject(http(url, "GET", null, token(c)));
            JSONArray a = j.optJSONArray("items");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && o.optString("id").length() > 0) {
                    out.add(new Pl(o.optString("id"), o.optString("name", "SPOTIFY")));
                }
            }
            url = j.isNull("next") ? null : j.optString("next", null);
        }
        return out;
    }

    /** Canciones de una lista (solo listas propias o colaborativas desde 2026). */
    static List<SpotifyImport.Row> items(Context c, String id) throws Exception {
        List<SpotifyImport.Row> out = new ArrayList<SpotifyImport.Row>();
        String url = "https://api.spotify.com/v1/playlists/" + id + "/items?limit=50";
        boolean fellBack = false;
        int guard = 0;
        while (url != null && guard++ < 400) {
            JSONObject j;
            try {
                j = new JSONObject(http(url, "GET", null, token(c)));
            } catch (IOException e) {
                if (useWeb && !fellBack && out.isEmpty()) {
                    fellBack = true;
                    url = "https://api.spotify.com/v1/playlists/" + id + "/tracks?limit=50";
                    continue;
                }
                throw e;
            }
            JSONArray a = j.optJSONArray("items");
            for (int i = 0; a != null && i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                JSONObject t = o == null ? null : o.optJSONObject("item");
                if (t == null && o != null) {
                    t = o.optJSONObject("track");
                }
                if (t == null || t.optString("name").length() == 0) {
                    continue;
                }
                String artists = "";
                JSONArray ar = t.optJSONArray("artists");
                for (int k = 0; ar != null && k < ar.length(); k++) {
                    JSONObject x = ar.optJSONObject(k);
                    if (x != null) {
                        artists += (artists.length() > 0 ? ", " : "") + x.optString("name");
                    }
                }
                JSONObject al = t.optJSONObject("album");
                out.add(new SpotifyImport.Row(t.optString("name"), artists, al == null ? "" : al.optString("name")));
            }
            url = j.isNull("next") ? null : j.optString("next", null);
        }
        return out;
    }

    private static String http(String url, String method, String body, String bearer) throws IOException {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setRequestMethod(method);
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        if (bearer != null) {
            h.setRequestProperty("Authorization", "Bearer " + bearer);
        }
        if (body != null) {
            h.setDoOutput(true);
            h.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream o = h.getOutputStream();
            o.write(body.getBytes("UTF-8"));
            o.close();
        }
        int code = h.getResponseCode();
        InputStream in = code >= 400 ? h.getErrorStream() : h.getInputStream();
        StringBuilder sb = new StringBuilder();
        if (in != null) {
            BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String l;
            while ((l = r.readLine()) != null) {
                sb.append(l).append('\n');
            }
            r.close();
        }
        if (code >= 400) {
            throw new IOException("Spotify " + code + (code == 403 ? Tr.t(" (¿Premium / usuario añadido en el panel?)") : "")
                    + ": " + (sb.length() > 160 ? sb.substring(0, 160) : sb.toString()).trim());
        }
        return sb.toString();
    }
}
