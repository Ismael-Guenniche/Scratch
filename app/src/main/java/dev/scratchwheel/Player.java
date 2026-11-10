package dev.scratchwheel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

/**
 * Estado global del reproductor: vive mientras viva el proceso, así que la música
 * sigue aunque se cierre la pantalla. Todos los métodos públicos se llaman desde el hilo principal.
 */
final class Player {
    static final Player I = new Player();

    interface Listener {
        void onChanged();
    }

    static final class Track {
        final Uri uri;
        final String name;
        String lower;
        String title = "";
        String artist = "";
        String album = "";
        /** Etiquetas originales del archivo (sin los nombres editados); las usa la importación de listas. */
        String tagTitle = "";
        String tagArtist = "";

        Track(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
            this.lower = name.toLowerCase(java.util.Locale.ROOT);
        }

        /** Título para mostrar: la etiqueta si existe, si no el nombre del archivo. */
        String display() {
            return title.length() > 0 ? title : name;
        }

        void setMeta(String t, String ar, String al) {
            tagTitle = t == null ? "" : t;
            tagArtist = ar == null ? "" : ar;
            title = tagTitle;
            artist = tagArtist;
            album = al == null ? "" : al;
            String[] e = TrackEdits.I.get(uri.toString());
            if (e != null) {
                if (e[0].length() > 0) {
                    title = e[0];
                }
                if (e[1].length() > 0) {
                    artist = e[1];
                }
            }
            lower = (name + " " + title + " " + artist + " " + album).toLowerCase(java.util.Locale.ROOT);
        }

        /** Nombre de la carpeta que contiene la pista ("" si no se puede saber). Hace de lista de reproducción. */
        String folder() {
            String seg = uri.getLastPathSegment();
            if (seg == null) {
                return "";
            }
            int last = seg.lastIndexOf('/');
            if (last <= 0) {
                return "";
            }
            int prev = seg.lastIndexOf('/', last - 1);
            String f = seg.substring(prev + 1, last);
            int colon = f.indexOf(':');
            return colon >= 0 ? f.substring(colon + 1) : f;
        }
    }

    private static final int BLOCK = 256;
    private static final int IDLE_BLOCKS = 120;

    final ScratchEngine engine = new ScratchEngine();
    final List<Track> tracks = new ArrayList<Track>();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final Random rnd = new Random();
    private final Object audioLock = new Object();

    private Context app;
    private AudioManager am;
    private SharedPreferences prefs;
    private PowerManager.WakeLock wake;
    private MediaSession session;
    private boolean initialized;
    private int outRate = 48000;

    private AudioTrack track;
    private boolean audioRunning;

    private int index = -1;
    private String title = "";
    private Uri currentUri;
    private boolean loading;
    private boolean playWhenReady;
    private boolean truncated;
    private int loadGen;

    // ---- BPM y sincronía
    private double bpmRaw;
    private double bpmOffset;
    private double bpmScale = 1;
    private boolean bpmReady;

    /** BPM de la pista actual (0 = aún sin calcular o sin pulso claro). */
    double bpm() {
        return bpmReady ? bpmRaw * bpmScale : 0;
    }

    double beatOffset() {
        return bpmOffset;
    }

    /** BPM efectivo con el pitch aplicado. */
    double effectiveBpm() {
        return bpm() * pitch();
    }

    boolean syncOn() {
        return prefs != null && prefs.getBoolean("sync_on", false);
    }

    void setSync(boolean on) {
        SharedPreferences.Editor e = prefs.edit().putBoolean("sync_on", on);
        if (on && bpm() > 0) {
            e.putFloat("sync_master", (float) effectiveBpm());
        }
        e.apply();
        changed();
    }

    /** ×2 o ×0,5 cuando el detector se ha quedado con el doble o la mitad. */
    void scaleBpm(double mult) {
        if (!bpmReady) {
            return;
        }
        double nb = bpmRaw * bpmScale * mult;
        if (nb < 50 || nb > 220) {
            return;
        }
        bpmScale *= mult;
        storeBpm();
        applySync();
        changed();
    }

    private void storeBpm() {
        if (currentUri != null) {
            app.getSharedPreferences("bpm", 0).edit().putString(currentUri.toString(),
                    bpmRaw + "|" + bpmOffset + "|" + bpmScale).apply();
        }
    }

    private void analyzeBpm(final Uri uri, final PcmData pcm) {
        bpmReady = false;
        bpmRaw = 0;
        bpmScale = 1;
        bpmOffset = 0;
        if (uri == null || pcm == null) {
            return;
        }
        final int gen = loadGen;
        String c = app.getSharedPreferences("bpm", 0).getString(uri.toString(), null);
        if (c != null) {
            try {
                String[] p = c.split("\\|");
                bpmRaw = Double.parseDouble(p[0]);
                bpmOffset = Double.parseDouble(p[1]);
                bpmScale = Double.parseDouble(p[2]);
                bpmReady = bpmRaw > 0;
                applySync();
                return;
            } catch (RuntimeException ignored) {
                bpmReady = false;
            }
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                double[] r = null;
                try {
                    r = BpmDetect.detect(pcm);
                } catch (RuntimeException ignored) {
                }
                final double[] fr = r;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen || fr == null) {
                            return;
                        }
                        bpmRaw = fr[0];
                        bpmOffset = fr[1];
                        bpmScale = 1;
                        bpmReady = true;
                        storeBpm();
                        applySync();
                        changed();
                    }
                });
            }
        }, "bpm").start();
    }

    /** Con SYNC activo, cada pista se ajusta de pitch (±20 %) al tempo maestro; si no cabe, a 1,0. */
    private void applySync() {
        if (!syncOn() || bpm() <= 0) {
            return;
        }
        double master = prefs.getFloat("sync_master", 0f);
        if (master <= 0) {
            return;
        }
        double best = 1.0;
        double bestDist = 9;
        double[] mults = {1, 2, 0.5};
        for (double m : mults) {
            double p = master / (bpm() * m);
            if (p >= 0.8 && p <= 1.2 && Math.abs(p - 1) < bestDist) {
                best = p;
                bestDist = Math.abs(p - 1);
            }
        }
        engine.setPitch(best);
        prefs.edit().putFloat("pitch", (float) best).apply();
    }

    // ---- reproducción sin huecos: se decodifica la siguiente pista y el motor la empalma al terminar
    private PcmData prepPcm;
    private Track prepTrack;
    private String prepKey;
    private String prepFailed;
    private int prepGen;
    private boolean prepScheduled;
    private volatile boolean swapPosted;
    private final Runnable prepRun = new Runnable() {
        @Override
        public void run() {
            prepScheduled = false;
            prepNext();
        }
    };
    private int errorStreak;
    private boolean shuffle;
    private int repeat;
    private int[] order;
    private int orderPos;
    private final ArrayList<Integer> history = new ArrayList<Integer>();
    private final HashSet<String> favs = new HashSet<String>();
    final List<Track> queue = new ArrayList<Track>();
    private int sleepIdx;
    private long sleepEnd;
    private Runnable sleepTask;
    private boolean resumeAfterFocus;
    private boolean noisyOn;

    private volatile android.graphics.Bitmap cover = Covers.placeholder();
    private boolean scanning;
    private int scanCount;
    private String libraryLabel = "";

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(final int change) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            onFocus(change);
                        }
                    });
                }
            };

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            pause();
        }
    };

    private Player() {
    }

    // ------------------------------------------------------------------ init

    void init(Context ctx) {
        if (initialized) {
            return;
        }
        initialized = true;
        app = ctx.getApplicationContext();
        am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        prefs = app.getSharedPreferences("scratch", Context.MODE_PRIVATE);
        shuffle = prefs.getBoolean("shuffle", false);
        repeat = prefs.getInt("repeat", 0);
        loadFavs();
        Playlists.I.init(new java.io.File(app.getFilesDir(), "playlists.txt"));
        TrackEdits.I.init(new java.io.File(app.getFilesDir(), "edits.txt"));
        CoverCache.init(app);
        try {
            String r = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            if (r != null) {
                outRate = Integer.parseInt(r.trim());
            }
        } catch (RuntimeException ignored) {
        }
        if (outRate < 8000 || outRate > 192000) {
            outRate = 48000;
        }
        engine.setOutputRate(outRate);
        engine.setPitch(prefs.getFloat("pitch", 1f));
        applyWheelPrefs();
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scratch:play");
        wake.setReferenceCounted(false);
        initSession();

        String label = Library.load(app, tracks);
        libraryLabel = label == null ? "" : label;
        Meta.init(app, tracks);
        PlayLog.init(app);
        startMetaScan();
        int last = prefs.getInt("index", -1);
        if (last >= 0 && last < tracks.size()) {
            load(tracks.get(last).uri, tracks.get(last).display(), last, false,
                    prefs.getFloat("pos", 0f));
        } else {
            load(null, Tr.t("DEMO · BEAT DE PRUEBA"), -1, false, 0);
        }
    }

    // ------------------------------------------------------------- listeners

    void addListener(Listener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

    void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Tras editar el nombre de una canción: actualiza el título que suena y avisa a las pantallas y a la notificación. */
    void metaEdited(Track t) {
        if (t.uri.equals(currentUri)) {
            title = t.display();
        }
        for (Listener l : new ArrayList<Listener>(listeners)) {
            l.onChanged();
        }
    }

    private void changed() {
        if (!prepScheduled) {
            prepScheduled = true;
            main.postDelayed(prepRun, 1500);
        }
        for (Listener l : new ArrayList<Listener>(listeners)) {
            l.onChanged();
        }
    }

    // ---------------------------------------------------------------- sin huecos

    private Track peekNext() {
        if (!queue.isEmpty()) {
            return queue.get(0);
        }
        int n = tracks.size();
        if (repeat == 2 || index < 0 || n < 2) {
            return null;
        }
        if (shuffle) {
            ensureOrder();
            int k = orderPos + 1;
            return order != null && k < order.length ? tracks.get(order[k]) : null;
        }
        int i = index + 1;
        if (i >= n) {
            if (repeat != 1) {
                return null;
            }
            i = 0;
        }
        return tracks.get(i);
    }

    private void dropPrep() {
        prepGen++;
        prepPcm = null;
        prepTrack = null;
        prepKey = null;
        engine.clearNext();
    }

    private void prepNext() {
        if (loading || engine.getDurationSeconds() <= 0) {
            return;
        }
        if (engine.consumeSwapped()) {
            onGapless();
        }
        Track t = peekNext();
        if (t == null || t.uri == null) {
            if (prepKey != null) {
                dropPrep();
            }
            return;
        }
        final String key = t.uri.toString();
        if (key.equals(prepKey) || key.equals(prepFailed)) {
            return;
        }
        dropPrep();
        prepKey = key;
        prepTrack = t;
        final int g = prepGen;
        final Uri u = t.uri;
        new Thread(new Runnable() {
            @Override
            public void run() {
                PcmData pcm = null;
                try {
                    pcm = AudioLoader.load(app, u);
                } catch (Throwable ignored) {
                }
                final PcmData fp = pcm;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (g != prepGen) {
                            return;
                        }
                        if (fp == null) {
                            prepFailed = key;
                            prepKey = null;
                            prepTrack = null;
                            return;
                        }
                        prepFailed = null;
                        prepPcm = fp;
                        engine.queueNext(fp);
                    }
                });
            }
        }, "prep").start();
    }

    /** El motor ya está sonando la pista encolada: se pone al día la lista, el título y la notificación. */
    private void onGapless() {
        Track t = prepTrack;
        PcmData pcm = prepPcm;
        prepTrack = null;
        prepPcm = null;
        prepKey = null;
        prepGen++;
        if (t == null || pcm == null) {
            return;
        }
        pushHistory();
        if (!queue.isEmpty() && queue.get(0).uri.equals(t.uri)) {
            queue.remove(0);
        }
        int idx = indexOf(t.uri);
        if (shuffle && idx >= 0 && order != null) {
            for (int k = 0; k < order.length; k++) {
                if (order[k] == idx) {
                    orderPos = k;
                }
            }
        }
        index = idx;
        title = t.display();
        currentUri = t.uri;
        truncated = pcm.truncated;
        startListen(t.uri);
        loadCoverAsync(++loadGen);
        analyzeBpm(t.uri, pcm);
        savePos();
        updateSession();
        syncService();
        changed();
    }

    private void toast(String msg) {
        try {
            Toast.makeText(app, msg, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------ etiquetas y reproducciones

    private boolean metaRunning;

    /** Lee título/artista/álbum de las pistas nuevas en segundo plano (con caché en meta.txt). */
    private void startMetaScan() {
        if (metaRunning) {
            return;
        }
        final List<Track> snapshot = new ArrayList<Track>(tracks);
        metaRunning = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean any = Meta.scan(app, snapshot);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        metaRunning = false;
                        if (any) {
                            changed();
                        }
                    }
                });
            }
        }, "meta").start();
    }

    // ------------------------------------------------------- historial de escucha

    private String listenUri;
    private double listenSecs;
    private boolean listenCounted;
    private boolean listenReported;
    private boolean listenTicking;

    private final Runnable listenTick = new Runnable() {
        @Override
        public void run() {
            listenTicking = false;
            if (listenUri != null && engine.isPlaying() && !engine.isScratching()
                    && engine.getDurationSeconds() > 0) {
                listenSecs += 1;
                double need = Math.min(Stats.PLAY_SECS, engine.getDurationSeconds() / 2);
                if (!listenCounted && listenSecs >= need) {
                    listenCounted = true;
                    countPlay(listenUri);
                }
            }
            if (listenUri != null) {
                listenTicking = true;
                main.postDelayed(listenTick, 1000);
            }
        }
    };

    /** Guarda en el historial lo escuchado de la canción actual (si llega a 10 s). */
    private void flushListen(boolean trackChanged) {
        if (listenUri != null && listenSecs >= 10) {
            final String u = listenUri;
            final int s = (int) listenSecs;
            final long ts = System.currentTimeMillis();
            final boolean played = listenCounted && !listenReported;
            if (played) {
                listenReported = true;
            }
            io().post(new Runnable() {
                @Override
                public void run() {
                    PlayLog.add(ts, u, s, played);
                }
            });
            listenSecs = 0;
        }
        if (trackChanged) {
            listenSecs = 0;
            listenCounted = false;
            listenReported = false;
        }
    }

    private void startListen(Uri u) {
        flushListen(true);
        listenUri = u == null ? null : u.toString();
        if (listenUri != null && !listenTicking) {
            listenTicking = true;
            main.postDelayed(listenTick, 1000);
        }
    }

    private void countPlay(final String uri) {
        io().post(new Runnable() {
            @Override
            public void run() {
                Meta.countPlay(app, uri);
            }
        });
    }

    // --------------------------------------------------------------- getters

    String title() {
        return title;
    }

    int index() {
        return index;
    }

    int count() {
        return tracks.size();
    }

    boolean isLoading() {
        return loading;
    }

    boolean isShuffle() {
        return shuffle;
    }

    boolean isScanning() {
        return scanning;
    }

    int scanCount() {
        return scanCount;
    }

    String libraryLabel() {
        return libraryLabel;
    }

    boolean isTruncated() {
        return truncated;
    }

    boolean isPlayingOrPending() {
        return engine.isPlaying() || playWhenReady;
    }

    MediaSession.Token sessionToken() {
        return session == null ? null : session.getSessionToken();
    }

    // --------------------------------------------------------------- loading

    void openTrack(int i, boolean autoplay, double startSec) {
        if (i < 0 || i >= tracks.size()) {
            return;
        }
        Track t = tracks.get(i);
        load(t.uri, t.display(), i, autoplay, startSec);
    }

    void openSingle(Uri uri, String name) {
        load(uri, name, -1, false, 0);
    }

    private void load(final Uri uri, final String name, final int idx, boolean autoplay,
                      final double startSec) {
        if (engine.consumeSwapped()) {
            onGapless();
        }
        final PcmData reuse = uri != null && prepPcm != null && uri.toString().equals(prepKey) ? prepPcm : null;
        dropPrep();
        final int gen = ++loadGen;
        playWhenReady = autoplay;
        loading = true;
        index = idx;
        title = name;
        startListen(uri);
        currentUri = uri;
        changed();
        if (reuse != null) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    finishLoad(gen, reuse, null, name, startSec);
                }
            });
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                PcmData pcm = null;
                Throwable err = null;
                try {
                    pcm = uri == null ? DemoTrack.generate() : AudioLoader.load(app, uri);
                } catch (Throwable e) {
                    err = e;
                }
                final PcmData fp = pcm;
                final Throwable fe = err;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        finishLoad(gen, fp, fe, name, startSec);
                    }
                });
            }
        }, "loader").start();
    }

    private void finishLoad(int gen, PcmData pcm, Throwable err, String name, double startSec) {
        if (gen != loadGen) {
            return;
        }
        loading = false;
        if (err != null || pcm == null) {
            String m = err == null ? null : err.getMessage();
            toast(Tr.t("No se pudo abrir: ") + name + (m == null ? "" : " (" + m + ")"));
            if (playWhenReady && tracks.size() > 1 && ++errorStreak <= 5) {
                next();
                return;
            }
            errorStreak = 0;
            playWhenReady = false;
            changed();
            return;
        }
        errorStreak = 0;
        engine.load(pcm);
        engine.seekTo(startSec);
        analyzeBpm(currentUri, pcm);
        truncated = pcm.truncated;
        loadCoverAsync(gen);
        if (playWhenReady) {
            playWhenReady = false;
            play();
        } else {
            releasePlaybackResources();
            updateSession();
            syncService();
            changed();
        }
        savePos();
    }

    android.graphics.Bitmap cover() {
        return cover;
    }

    /** Carátula para la notificación y la pantalla de bloqueo; mientras llega, la rueda de puntos. */
    private void loadCoverAsync(final int gen) {
        cover = Covers.placeholder();
        final Uri u = currentUri;
        if (u == null) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final android.graphics.Bitmap b = Covers.embedded(app, u);
                if (b == null) {
                    return;
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (gen != loadGen) {
                            return;
                        }
                        cover = b;
                        updateSession();
                        syncService();
                    }
                });
            }
        }, "cover").start();
    }

    // ------------------------------------------------------------- transport

    void play() {
        if (loading) {
            playWhenReady = true;
            changed();
            return;
        }
        if (engine.getDurationSeconds() <= 0) {
            return;
        }
        // Lo que se oye y se ve va primero; las llamadas al sistema (foco, sesión, servicio) salen del hilo
        // principal para que la rueda no dé tirones al arrancar.
        engine.setVolume(1f);
        engine.setPlaying(true);
        ensureAudio();
        changed();
        io().post(new Runnable() {
            @Override
            public void run() {
                int r = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);
                if (r != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            toast(Tr.t("Otra app está usando el audio"));
                            pauseInternal(false);
                        }
                    });
                    return;
                }
                if (!wake.isHeld()) {
                    wake.acquire(4L * 60 * 60 * 1000);
                }
                registerNoisy();
                updateSession();
            }
        });
        syncServiceLater();
    }

    void pause() {
        pauseInternal(true);
    }

    private void pauseInternal(final boolean abandonFocus) {
        flushListen(false);
        playWhenReady = false;
        engine.setPlaying(false);
        changed();
        io().post(new Runnable() {
            @Override
            public void run() {
                releasePlaybackResources();
                if (abandonFocus) {
                    am.abandonAudioFocus(focusListener);
                }
                savePos();
                updateSession();
            }
        });
        syncServiceLater();
    }

    private HandlerThread ioThread;
    private Handler ioHandler;

    private synchronized Handler io() {
        if (ioHandler == null) {
            ioThread = new HandlerThread("player-io");
            ioThread.start();
            ioHandler = new Handler(ioThread.getLooper());
        }
        return ioHandler;
    }

    /** El servicio y su notificación se actualizan con un pequeño retraso, cuando el plato ya casi ha terminado de moverse. */
    private void syncServiceLater() {
        main.removeCallbacks(syncTask);
        main.postDelayed(syncTask, 600);
    }

    private final Runnable syncTask = new Runnable() {
        @Override
        public void run() {
            syncService();
        }
    };

    void toggle() {
        if (isPlayingOrPending()) {
            pause();
        } else {
            play();
        }
    }

    void next() {
        goNext(true);
    }

    private void goNext(boolean manual) {
        if (!queue.isEmpty()) {
            Track q = queue.remove(0);
            pushHistory();
            load(q.uri, q.display(), indexOf(q.uri), true, 0);
            return;
        }
        int n = tracks.size();
        if (n == 0) {
            engine.seekTo(0);
            if (!manual) {
                pause();
            }
            return;
        }
        int i;
        if (shuffle && n > 1) {
            ensureOrder();
            orderPos++;
            if (orderPos >= order.length) {
                if (!manual && repeat != 1) {
                    stopAtEnd();
                    return;
                }
                buildOrder(-1);
            }
            i = order[orderPos];
        } else {
            i = index + 1;
            if (i >= n) {
                if (!manual && repeat != 1) {
                    stopAtEnd();
                    return;
                }
                i = 0;
            }
        }
        pushHistory();
        openTrack(i, true, 0);
    }

    /** Fin de la lista sin repetir: se queda parado en la primera canción. */
    private void stopAtEnd() {
        pause();
        int first = 0;
        if (shuffle && tracks.size() > 1) {
            buildOrder(-1);
            first = order[0];
        }
        openTrack(first, false, 0);
    }

    void prev() {
        int n = tracks.size();
        if (n == 0 || index < 0 || engine.getPositionSeconds() > 3.0) {
            engine.seekTo(0);
            changed();
            return;
        }
        int i;
        if (!history.isEmpty()) {
            i = history.remove(history.size() - 1);
            if (i >= n) {
                i = (index - 1 + n) % n;
            }
            if (shuffle) {
                ensureOrder();
                for (int k = 0; k < order.length; k++) {
                    if (order[k] == i) {
                        orderPos = k;
                        break;
                    }
                }
            }
        } else {
            i = (index - 1 + n) % n;
        }
        openTrack(i, true, 0);
    }

    /** Reproduce idxs[pos] y deja el resto de la lista en la cola, para que "siguiente" siga en esa lista. */
    void playFrom(List<Integer> idxs, int pos) {
        if (pos < 0 || pos >= idxs.size()) {
            return;
        }
        ArrayList<Integer> rest = new ArrayList<Integer>();
        for (int k = 0; k < idxs.size(); k++) {
            if (k > pos || (shuffle && k != pos)) {
                rest.add(idxs.get(k));
            }
        }
        if (shuffle) {
            java.util.Collections.shuffle(rest, rnd);
        }
        queue.clear();
        for (int k = 0; k < rest.size() && k < 1000; k++) {
            int r = rest.get(k);
            if (r >= 0 && r < tracks.size()) {
                queue.add(tracks.get(r));
            }
        }
        choose(idxs.get(pos));
    }

    /** Índices de una lista navegable (Android Auto): ALL, FAV, REC o PL:nombre. */
    List<Integer> listFor(String folder) {
        final ArrayList<Integer> out = new ArrayList<Integer>();
        if ("ALL".equals(folder)) {
            for (int i = 0; i < tracks.size(); i++) {
                out.add(i);
            }
        } else if ("FAV".equals(folder)) {
            for (int i = 0; i < tracks.size(); i++) {
                if (favs.contains(tracks.get(i).uri.toString())) {
                    out.add(i);
                }
            }
        } else if ("REC".equals(folder)) {
            for (int i = 0; i < tracks.size(); i++) {
                if (Meta.count(tracks.get(i).uri.toString()) > 0) {
                    out.add(i);
                }
            }
            java.util.Collections.sort(out, new java.util.Comparator<Integer>() {
                @Override
                public int compare(Integer a, Integer b) {
                    long x = Meta.last(tracks.get(a).uri.toString());
                    long y = Meta.last(tracks.get(b).uri.toString());
                    return x == y ? 0 : (x < y ? 1 : -1);
                }
            });
        } else if (folder != null && folder.startsWith("PL:")) {
            java.util.HashMap<String, Integer> idx = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < tracks.size(); i++) {
                idx.put(tracks.get(i).uri.toString(), i);
            }
            for (String u : Playlists.I.uris(folder.substring(3))) {
                Integer k = idx.get(u);
                if (k != null) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    /** Reproduce un id "carpeta|uri" elegido desde el coche; "siguiente" sigue en esa carpeta. */
    void playMediaId(String id) {
        int bar = id == null ? -1 : id.indexOf('|');
        if (bar < 0) {
            return;
        }
        List<Integer> l = listFor(id.substring(0, bar));
        int idx = indexOf(Uri.parse(id.substring(bar + 1)));
        int pos = l.indexOf(idx);
        if (pos >= 0) {
            playFrom(l, pos);
        } else if (idx >= 0) {
            choose(idx);
        }
    }

    /** El usuario elige una canción de la biblioteca. */
    void choose(int i) {
        if (i < 0 || i >= tracks.size()) {
            return;
        }
        pushHistory();
        if (shuffle && tracks.size() > 1) {
            buildOrder(i);
        }
        openTrack(i, true, 0);
    }

    private void pushHistory() {
        if (index >= 0) {
            history.add(index);
            if (history.size() > 300) {
                history.remove(0);
            }
        }
    }

    private void ensureOrder() {
        if (order == null || order.length != tracks.size()) {
            buildOrder(index);
        }
    }

    private void buildOrder(int first) {
        order = ShuffleOrder.build(tracks.size(), first, index, rnd);
        orderPos = 0;
    }

    private int indexOf(Uri u) {
        for (int k = 0; k < tracks.size(); k++) {
            if (tracks.get(k).uri.equals(u)) {
                return k;
            }
        }
        return -1;
    }

    /** 1,0 = velocidad original; la rueda y los botones de avance rápido no se ven afectados. */
    void setPitch(double p) {
        engine.setPitch(p);
        prefs.edit().putFloat("pitch", (float) p).apply();
        if (syncOn() && bpm() > 0) {
            prefs.edit().putFloat("sync_master", (float) (bpm() * p)).apply();
        }
    }

    double pitch() {
        return prefs == null ? 1.0 : prefs.getFloat("pitch", 1f);
    }

    void seekFraction(double f) {
        engine.seekTo(engine.getDurationSeconds() * Math.max(0.0, Math.min(1.0, f)));
    }

    void setShuffle(boolean on) {
        shuffle = on;
        order = null;
        if (on && tracks.size() > 1) {
            ensureOrder();
        }
        prefs.edit().putBoolean("shuffle", on).apply();
        changed();
    }

    private void onEnded() {
        if (repeat == 2) {
            engine.seekTo(0);
            play();
        } else if (!queue.isEmpty() || (tracks.size() > 1 && index >= 0)) {
            goNext(false);
        } else {
            pause();
        }
    }

    // ------------------------------------------------- rueda y avance rápido

    /** Lee los ajustes de la rueda (Ajustes → Rueda) y los aplica al motor. */
    void applyWheelPrefs() {
        android.content.SharedPreferences sp = Lucent.prefs(app);
        engine.setKeyLock(sp.getBoolean("keylock", false));
        boolean eqOn = sp.getBoolean("eq_on", false);
        double[] eqDb = new double[5];
        for (int i = 0; i < 5; i++) {
            eqDb[i] = eqOn ? sp.getInt("eq" + i, 0) : 0;
        }
        engine.setGraphicEq(eqDb);
        int sens = sp.getInt("wsens", 1);
        engine.setSecondsPerRev(sens == 0 ? 3.6 : sens == 2 ? 0.9 : ScratchEngine.SECONDS_PER_REV);
        engine.setInvert(sp.getBoolean("winvert", false));
        int fr = sp.getInt("wfric", 1);
        engine.setInertia(sp.getBoolean("inertia", true), fr == 0 ? 0.6 : fr == 2 ? 3.0 : 1.5);
    }

    void touchDown() {
        engine.touchDown();
        ensureAudio();
    }

    void rotate(double radians) {
        engine.rotate(radians);
    }

    void touchUp() {
        engine.touchUp();
    }

    void sample(int id) {
        engine.triggerSample(id);
        ensureAudio();
    }

    void vinylStop(double secs) {
        if (engine.isPlaying()) {
            engine.armVinylStop(secs);
            pause();
        }
    }

    void seekSpeed(double mag) {
        engine.setSeekSpeed(mag);
    }

    void seekHold(int dir) {
        engine.setSeek(dir);
        if (dir != 0) {
            ensureAudio();
        }
    }

    // ---------------------------------------------------------- hilo de audio

    private void ensureAudio() {
        synchronized (audioLock) {
            if (audioRunning) {
                return;
            }
            int minBuf = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int bytes = Math.max(minBuf, BLOCK * 4 * 2);
            AudioTrack.Builder tb = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(outRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build())
                    .setBufferSizeInBytes(bytes)
                    .setTransferMode(AudioTrack.MODE_STREAM);
            if (Build.VERSION.SDK_INT >= 26) {
                tb.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY);
            }
            final AudioTrack t;
            try {
                t = tb.build();
                t.play();
            } catch (RuntimeException e) {
                toast(Tr.t("No se pudo iniciar el audio"));
                return;
            }
            track = t;
            audioRunning = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    audioLoop(t);
                }
            }, "audio").start();
        }
    }

    private void audioLoop(AudioTrack t) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] buf = new short[BLOCK * 2];
        int idle = 0;
        int errors = 0;
        try {
            while (true) {
                engine.render(buf, BLOCK);
                if (t.write(buf, 0, buf.length) < 0) {
                    if (++errors > 20) {
                        break;
                    }
                } else {
                    errors = 0;
                }
                if (engine.hasSwapped() && !swapPosted) {
                    swapPosted = true;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            swapPosted = false;
                            if (engine.consumeSwapped()) {
                                onGapless();
                            }
                        }
                    });
                }
                if (engine.consumeEnded()) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            onEnded();
                        }
                    });
                }
                if (engine.isIdle()) {
                    if (++idle > IDLE_BLOCKS) {
                        synchronized (audioLock) {
                            if (engine.isIdle()) {
                                audioRunning = false;
                                break;
                            }
                        }
                        idle = 0;
                    }
                } else {
                    idle = 0;
                }
            }
        } catch (RuntimeException ignored) {
            // el AudioTrack dejó de ser válido: se limpia abajo
        } finally {
            try {
                t.stop();
            } catch (RuntimeException ignored) {
            }
            t.release();
            synchronized (audioLock) {
                if (track == t) {
                    track = null;
                    audioRunning = false;
                }
            }
        }
    }

    // ------------------------------------------------------- foco y auriculares

    private void onFocus(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS:
                resumeAfterFocus = false;
                pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                resumeAfterFocus = engine.isPlaying();
                pauseInternal(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                engine.setVolume(0.25f);
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                engine.setVolume(1f);
                if (resumeAfterFocus) {
                    resumeAfterFocus = false;
                    play();
                }
                break;
            default:
                break;
        }
    }

    private synchronized void registerNoisy() {
        if (!noisyOn) {
            try {
                app.registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                noisyOn = true;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private synchronized void releasePlaybackResources() {
        if (wake != null && wake.isHeld()) {
            wake.release();
        }
        if (noisyOn) {
            try {
                app.unregisterReceiver(noisy);
            } catch (RuntimeException ignored) {
            }
            noisyOn = false;
        }
    }

    // ---------------------------------------------------- sesión y notificación

    private void initSession() {
        try {
            session = new MediaSession(app, "Scratch");
            session.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    play();
                }

                @Override
                public void onPause() {
                    pause();
                }

                @Override
                public void onStop() {
                    pause();
                }

                @Override
                public void onSkipToNext() {
                    next();
                }

                @Override
                public void onSkipToPrevious() {
                    prev();
                }

                @Override
                public void onPlayFromMediaId(String mediaId, android.os.Bundle extras) {
                    playMediaId(mediaId);
                }
            }, main);
            session.setActive(true);
        } catch (RuntimeException e) {
            session = null;
        }
    }

    private void updateSession() {
        if (session == null) {
            return;
        }
        try {
            boolean pl = engine.isPlaying();
            PlaybackState.Builder b = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                            | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_STOP
                            | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID)
                    .setState(pl ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            (long) (engine.getPositionSeconds() * 1000), pl ? 1f : 0f);
            session.setPlaybackState(b.build());
            Track cur = index >= 0 && index < tracks.size() ? tracks.get(index) : null;
            session.setMetadata(new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, cur == null ? "" : cur.artist)
                    .putString(MediaMetadata.METADATA_KEY_ALBUM, cur == null ? "" : cur.album)
                    .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, cover)
                    .putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, cover)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION,
                            (long) (engine.getDurationSeconds() * 1000))
                    .build());
        } catch (RuntimeException ignored) {
        }
    }

    private void syncService() {
        try {
            boolean playing = engine.isPlaying() || playWhenReady;
            if (!playing && !PlayerService.active) {
                return;
            }
            Intent i = new Intent(app, PlayerService.class);
            if (playing && Build.VERSION.SDK_INT >= 26) {
                app.startForegroundService(i);
            } else {
                app.startService(i);
            }
        } catch (RuntimeException ignored) {
        }
    }

    // ------------------------------------------------------ repetir, cola, favoritas

    int repeat() {
        return repeat;
    }

    void cycleRepeat() {
        repeat = (repeat + 1) % 3;
        prefs.edit().putInt("repeat", repeat).apply();
        changed();
    }

    void addToQueue(int libIdx, boolean atFront) {
        if (libIdx < 0 || libIdx >= tracks.size()) {
            return;
        }
        if (queue.size() >= 500) {
            toast(Tr.t("La cola está llena (500)"));
            return;
        }
        Track t = tracks.get(libIdx);
        if (atFront) {
            queue.add(0, t);
            toast(Tr.t("Suena a continuación"));
        } else {
            queue.add(t);
            toast(Tr.t("Añadida a la cola (") + queue.size() + ")");
        }
        changed();
    }

    void removeFromQueue(int pos) {
        if (pos >= 0 && pos < queue.size()) {
            queue.remove(pos);
            changed();
        }
    }

    void clearQueue() {
        queue.clear();
        changed();
    }

    void playQueueItem(int pos) {
        if (pos < 0 || pos >= queue.size()) {
            return;
        }
        Track q = queue.remove(pos);
        pushHistory();
        load(q.uri, q.display(), indexOf(q.uri), true, 0);
    }

    boolean isFav(Uri u) {
        return u != null && favs.contains(u.toString());
    }

    Uri currentUri() {
        return currentUri;
    }

    boolean currentFav() {
        return isFav(currentUri);
    }

    int favCount() {
        return favs.size();
    }

    void toggleFav(Uri u) {
        if (u == null) {
            toast(Tr.t("Esta pista no se puede guardar en favoritas"));
            return;
        }
        String k = u.toString();
        if (!favs.remove(k)) {
            favs.add(k);
        }
        saveFavs();
        changed();
    }

    private void loadFavs() {
        java.io.File f = new java.io.File(app.getFilesDir(), "favs.txt");
        if (!f.exists()) {
            return;
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), "UTF-8"));
            try {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.length() > 0) {
                        favs.add(line);
                    }
                }
            } finally {
                r.close();
            }
        } catch (java.io.IOException ignored) {
        }
    }

    private void saveFavs() {
        final String[] copy = favs.toArray(new String[0]);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.io.File tmp = new java.io.File(app.getFilesDir(), "favs.tmp");
                    java.io.Writer w = new java.io.OutputStreamWriter(
                            new java.io.FileOutputStream(tmp), "UTF-8");
                    try {
                        for (String s : copy) {
                            w.write(s);
                            w.write('\n');
                        }
                    } finally {
                        w.close();
                    }
                    tmp.renameTo(new java.io.File(app.getFilesDir(), "favs.txt"));
                } catch (java.io.IOException ignored) {
                }
            }
        }, "favs").start();
    }

    // ---------------------------------------------------------- temporizador

    private static final int[] SLEEP_MIN = {0, 15, 30, 60, 90};

    /** Minutos que faltan para que se apague (0 = desactivado). */
    int sleepMinutes() {
        if (sleepIdx == 0) {
            return 0;
        }
        return (int) Math.max(1, (sleepEnd - System.currentTimeMillis() + 59999) / 60000);
    }

    /** Programa el apagado en {@code minutes} (0 = desactivar). */
    void setSleepMinutes(int minutes) {
        if (sleepTask != null) {
            main.removeCallbacks(sleepTask);
        }
        sleepIdx = minutes > 0 ? 1 : 0;
        if (minutes > 0) {
            sleepEnd = System.currentTimeMillis() + minutes * 60000L;
            sleepTask = new Runnable() {
                @Override
                public void run() {
                    fadeOutAndPause(20);
                }
            };
            main.postDelayed(sleepTask, minutes * 60000L);
        }
        changed();
    }

    void cycleSleep() {
        if (sleepTask != null) {
            main.removeCallbacks(sleepTask);
        }
        sleepIdx = (sleepIdx + 1) % SLEEP_MIN.length;
        if (sleepIdx > 0) {
            sleepEnd = System.currentTimeMillis() + SLEEP_MIN[sleepIdx] * 60000L;
            sleepTask = new Runnable() {
                @Override
                public void run() {
                    fadeOutAndPause(20);
                }
            };
            main.postDelayed(sleepTask, SLEEP_MIN[sleepIdx] * 60000L);
        }
        changed();
    }

    private void fadeOutAndPause(final int steps) {
        if (steps <= 0) {
            sleepIdx = 0;
            pause();
            engine.setVolume(1f);
            return;
        }
        engine.setVolume(steps / 20f);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                fadeOutAndPause(steps - 1);
            }
        }, 150);
    }

    // ------------------------------------------------------------ persistencia

    void savePos() {
        if (prefs == null || index < 0) {
            return;
        }
        prefs.edit().putInt("index", index)
                .putFloat("pos", (float) engine.getPositionSeconds()).apply();
    }

    // ------------------------------------------------------------- biblioteca

    void scan(final Uri tree) {
        if (scanning) {
            return;
        }
        scanning = true;
        scanCount = 0;
        prefs.edit().putString("tree", tree.toString()).apply();
        changed();
        final String label = Library.labelFor(tree);
        new Thread(new Runnable() {
            @Override
            public void run() {
                List<Track> got;
                try {
                    got = Library.scan(app, tree, new Library.Progress() {
                    @Override
                    public void onCount(final int n) {
                        if (n % 50 == 0) {
                            main.post(new Runnable() {
                                @Override
                                public void run() {
                                    scanCount = n;
                                    changed();
                                }
                            });
                        }
                    }
                    });
                } catch (Throwable t) {
                    Library.fail(t);
                    Library.buildReport(tree, 0, 0);
                    got = new ArrayList<Track>();
                }
                final List<Track> found = got;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        finishScan(found, label);
                    }
                });
            }
        }, "scan").start();
    }

    void rescan() {
        String s = prefs.getString("tree", null);
        if (s != null) {
            scan(Uri.parse(s));
        }
    }

    boolean hasLibraryFolder() {
        return prefs != null && prefs.getString("tree", null) != null;
    }

    private void finishScan(List<Track> found, String label) {
        scanning = false;
        scanCount = found.size();
        if (found.isEmpty()) {
            toast(Tr.t("No se encontró audio · mira MENÚ → DIAGNÓSTICO"));
            changed();
            return;
        }
        tracks.clear();
        tracks.addAll(found);
        order = null;
        history.clear();
        queue.clear();
        libraryLabel = label;
        Library.save(app, label, tracks);
        Meta.apply(tracks);
        startMetaScan();
        Imports.resync(app, tracks);
        index = -1;
        if (currentUri != null) {
            for (int i = 0; i < tracks.size(); i++) {
                if (tracks.get(i).uri.equals(currentUri)) {
                    index = i;
                    break;
                }
            }
        }
        changed();
    }
}
