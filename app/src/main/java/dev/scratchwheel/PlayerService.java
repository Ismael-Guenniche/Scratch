package dev.scratchwheel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.service.media.MediaBrowserService;
import android.os.Build;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;

/** Servicio en primer plano: mantiene viva la música con la pantalla apagada y muestra los controles. */
public class PlayerService extends MediaBrowserService {
    static volatile boolean active;

    private static final String CHANNEL = "scratch_playback";
    private static final int ID = 7;
    private static final String A_TOGGLE = "dev.scratchwheel.TOGGLE";
    private static final String A_NEXT = "dev.scratchwheel.NEXT";
    private static final String A_PREV = "dev.scratchwheel.PREV";
    private static final String A_STOP = "dev.scratchwheel.STOP";

    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        // Solo la propia app, el sistema y Android Auto / Asistente / Wear pueden explorar la biblioteca.
        if (!trusted(clientPackageName, clientUid)) {
            return null;
        }
        return new BrowserRoot("root", null);
    }

    private static final String[] TRUSTED = {
        "com.google.android.projection.gearhead", "com.google.android.autosimulator",
        "com.google.android.googlequicksearchbox", "com.google.android.carassistant",
        "com.google.android.wearable.app", "com.android.systemui", "android"
    };

    private boolean trusted(String pkg, int uid) {
        if (uid == android.os.Process.myUid() || uid == android.os.Process.SYSTEM_UID || pkg == null) {
            return true;
        }
        String[] owned = getPackageManager().getPackagesForUid(uid);
        if (owned == null) {
            return false;
        }
        boolean claimed = false;
        for (String o : owned) {
            if (o.equals(pkg)) {
                claimed = true;
            }
        }
        if (!claimed) {
            return false;
        }
        for (String t : TRUSTED) {
            if (t.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    /** Secreto por instalación: el servicio está exportado y las acciones solo valen con él. */
    private String secret() {
        android.content.SharedPreferences sp = getSharedPreferences("svc", MODE_PRIVATE);
        String k = sp.getString("k", null);
        if (k == null) {
            byte[] b = new byte[16];
            new java.security.SecureRandom().nextBytes(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            k = sb.toString();
            sp.edit().putString("k", k).apply();
        }
        return k;
    }

    @Override
    public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
        Player p = Player.I;
        List<MediaBrowser.MediaItem> out = new ArrayList<MediaBrowser.MediaItem>();
        if ("root".equals(parentId)) {
            out.add(folder("ALL", Tr.t("Canciones")));
            out.add(folder("FAV", Tr.t("Favoritas")));
            out.add(folder("REC", Tr.t("Recientes")));
            out.add(folder("AR", Tr.t("Artistas")));
            out.add(folder("AL", Tr.t("Álbumes")));
            List<String> names = Playlists.I.names();
            for (int i = 0; i < names.size() && i < 20; i++) {
                out.add(folder("PL:" + names.get(i), names.get(i)));
            }
        } else if ("AR".equals(parentId) || "AL".equals(parentId)) {
            boolean ar = "AR".equals(parentId);
            List<String> names = p.distinctNames(ar);
            for (int i = 0; i < names.size() && i < 300; i++) {
                out.add(folder((ar ? "AR:" : "AL:") + Player.safeId(names.get(i)), names.get(i)));
            }
        } else {
            List<Integer> idx = p.listFor(parentId);
            for (int k = 0; k < idx.size() && k < 300; k++) {
                Player.Track t = p.tracks.get(idx.get(k));
                out.add(new MediaBrowser.MediaItem(new MediaDescription.Builder()
                        .setMediaId(parentId + "|" + t.uri)
                        .setTitle(t.display())
                        .setSubtitle(t.artist.length() > 0 ? t.artist : null)
                        .build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
            }
        }
        result.sendResult(out);
    }

    @Override
    public void onSearch(String query, Bundle extras, Result<List<MediaBrowser.MediaItem>> result) {
        Player p = Player.I;
        List<MediaBrowser.MediaItem> out = new ArrayList<MediaBrowser.MediaItem>();
        List<Integer> hits = p.search(query);
        for (int k = 0; k < hits.size() && k < 50; k++) {
            Player.Track t = p.tracks.get(hits.get(k));
            out.add(new MediaBrowser.MediaItem(new MediaDescription.Builder()
                    .setMediaId("ALL|" + t.uri)
                    .setTitle(t.display())
                    .setSubtitle(t.artist.length() > 0 ? t.artist : null)
                    .build(), MediaBrowser.MediaItem.FLAG_PLAYABLE));
        }
        result.sendResult(out);
    }

    private MediaBrowser.MediaItem folder(String id, String title) {
        return new MediaBrowser.MediaItem(new MediaDescription.Builder()
                .setMediaId(id).setTitle(title).build(), MediaBrowser.MediaItem.FLAG_BROWSABLE);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Player.I.init(this);
            MediaSession.Token t = Player.I.sessionToken();
            if (t != null) {
                setSessionToken(t);
            }
        } catch (RuntimeException ignored) {
        }
        active = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, Tr.t("Reproducción"),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
    }

    @Override
    public void onDestroy() {
        active = false;
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Player p = Player.I;
        p.init(this);
        String a = intent == null ? null : intent.getAction();
        if (a != null && !secret().equals(intent.getStringExtra("k"))) {
            a = null; // acción de otra app: se ignora
        }
        if (A_STOP.equals(a)) {
            // la notificación en pausa se ha descartado con el dedo
            if (p.isPlayingOrPending()) {
                p.pause();
            }
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (A_TOGGLE.equals(a)) {
            p.toggle();
        } else if (A_NEXT.equals(a)) {
            p.next();
        } else if (A_PREV.equals(a)) {
            p.prev();
        }
        Notification n = build(p);
        try {
            // Siempre en primer plano mientras haya una canción cargada: así la notificación
            // se queda también en pausa y se puede reanudar desde ella.
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startForeground(ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            } else {
                startForeground(ID, n);
            }
        } catch (RuntimeException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    private Notification build(Player p) {
        boolean playing = p.isPlayingOrPending();
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        String title = p.title().length() == 0 ? "Scratch" : p.title();
        String sub = p.index() >= 0 ? (p.index() + 1) + " / " + p.count() : "Scratch";
        b.setSmallIcon(getResources().getIdentifier("ic_stat", "drawable", getPackageName()))
                .setColor(0xFFFB471F)
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setSubText("SCRATCH")
                .setLargeIcon(p.cover())
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(openApp())
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(action(android.R.drawable.ic_media_previous, Tr.t("Anterior"), A_PREV))
                .addAction(action(playing ? android.R.drawable.ic_media_pause
                        : android.R.drawable.ic_media_play, playing ? Tr.t("Pausa") : "Play", A_TOGGLE))
                .addAction(action(android.R.drawable.ic_media_next, Tr.t("Siguiente"), A_NEXT))
                .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, Tr.t("Cerrar"), A_STOP));
        Notification.MediaStyle style = new Notification.MediaStyle().setShowActionsInCompactView(0, 1, 2);
        MediaSession.Token token = p.sessionToken();
        if (token != null) {
            style.setMediaSession(token);
        }
        b.setStyle(style);
        return b.build();
    }

    private PendingIntent stopIntent() {
        Intent i = new Intent(this, PlayerService.class);
        i.setAction(A_STOP);
        i.putExtra("k", secret());
        return PendingIntent.getService(this, A_STOP.hashCode(), i, piFlags());
    }

    private int piFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, ScratchActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, 0, i, piFlags());
    }

    private Notification.Action action(int icon, String label, String act) {
        Intent i = new Intent(this, PlayerService.class);
        i.setAction(act);
        i.putExtra("k", secret());
        PendingIntent pi = PendingIntent.getService(this, act.hashCode(), i, piFlags());
        return new Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build();
    }
}
