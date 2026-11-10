import dev.scratchwheel.BpmDetect;
import dev.scratchwheel.ShuffleOrder;
import dev.scratchwheel.DotFont;
import dev.scratchwheel.DemoTrack;
import dev.scratchwheel.PcmData;
import dev.scratchwheel.ScratchEngine;
import dev.scratchwheel.SeekSpeed;
import dev.scratchwheel.Stats;
import dev.scratchwheel.WavParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class Check {
    static int fails = 0;

    static void ok(boolean cond, String msg) {
        System.out.println((cond ? "OK   " : "FAIL ") + msg);
        if (!cond) {
            fails++;
        }
    }

    static void writeWav(String path, short[] st, int frames, int sr) throws IOException {
        int dataLen = frames * 4;
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write("RIFF".getBytes());
        w32(o, 36 + dataLen);
        o.write("WAVEfmt ".getBytes());
        w32(o, 16);
        w16(o, 1);
        w16(o, 2);
        w32(o, sr);
        w32(o, sr * 4);
        w16(o, 4);
        w16(o, 16);
        o.write("data".getBytes());
        w32(o, dataLen);
        for (int i = 0; i < frames * 2; i++) {
            w16(o, st[i]);
        }
        try (OutputStream f = new FileOutputStream(path)) {
            o.writeTo(f);
        }
    }

    static void w16(ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF);
        o.write((v >> 8) & 0xFF);
    }

    static void w32(ByteArrayOutputStream o, int v) {
        w16(o, v);
        w16(o, v >> 16);
    }

    static double avgEnergy(ScratchEngine e, short[] buf, int n) {
        double s = 0;
        for (int i = 0; i < n; i++) {
            e.render(buf, 256);
            s += energy(buf, 256);
        }
        return s / n;
    }

    static double energy(short[] b, int n) {
        double e = 0;
        for (int i = 0; i < n * 2; i++) {
            e += Math.abs(b[i]);
        }
        return e / (n * 2);
    }

    static short[] readPcm(String path) throws IOException {
        byte[] b = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
        short[] s = new short[b.length / 2];
        for (int i = 0; i < s.length; i++) {
            s[i] = (short) ((b[2 * i] & 0xFF) | (b[2 * i + 1] << 8));
        }
        return s;
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        PcmData demo = DemoTrack.generate();
        String wav = dir + "/demo.wav";
        writeWav(wav, demo.stereo, demo.frames, demo.sampleRate);

        PcmData p;
        try (FileInputStream in = new FileInputStream(wav)) {
            p = WavParser.parse(in);
        }
        ok(p.frames == demo.frames && p.sampleRate == 44100, "WAV: ida y vuelta (" + p.frames + " frames)");
        boolean same = true;
        for (int i = 0; i < 2 * p.frames; i += 997) {
            if (p.stereo[i] != demo.stereo[i]) {
                same = false;
            }
        }
        ok(same, "WAV: muestras idénticas tras releer");
        try {
            WavParser.parse(new ByteArrayInputStream("ID3 esto no es un wav ".getBytes()));
            ok(false, "no-WAV debe lanzar NotWavException");
        } catch (WavParser.NotWavException e) {
            ok(true, "no-WAV lanza NotWavException (pasa al decodificador)");
        }

        ScratchEngine e = new ScratchEngine();
        e.load(p);
        short[] buf = new short[512];

        e.setPlaying(true);
        for (int i = 0; i < 172; i++) {
            e.render(buf, 256);
        }
        double pos1 = e.getPositionSeconds();
        ok(pos1 > 0.85 && pos1 < 0.93, "reproducción normal (con arranque suave): tras 1 s de audio la posición es " + pos1);

        e.setPlaying(false);
        for (int i = 0; i < 20; i++) {
            e.render(buf, 256);
        }
        ok(e.getRate() > 0.2 && e.getRate() < 0.95, "pausa: frena poco a poco, no corta en seco (velocidad " + e.getRate() + ")");
        for (int i = 0; i < 120; i++) {
            e.render(buf, 256);
        }
        double pausedPos = e.getPositionSeconds();
        for (int i = 0; i < 10; i++) {
            e.render(buf, 256);
        }
        ok(e.getPositionSeconds() == pausedPos && e.getRate() == 0, "pausa: frena y se queda quieto en " + pausedPos);

        double start = e.getPositionSeconds();
        e.touchDown();
        double maxRate = 0;
        double minRate = 0;
        for (int ev = 0; ev < 30; ev++) {
            e.rotate(0.1);
            for (int k = 0; k < 3; k++) {
                e.render(buf, 256);
                maxRate = Math.max(maxRate, e.getRate());
            }
        }
        for (int k = 0; k < 20; k++) {
            e.render(buf, 256);
        }
        double fwd = e.getPositionSeconds() - start;
        double expected = 3.0 / (2 * Math.PI) * ScratchEngine.SECONDS_PER_REV;
        ok(Math.abs(fwd - expected) < 0.02, "scratch adelante: avanza " + fwd + " s (esperado " + expected + ")");
        ok(maxRate > 0.5, "scratch adelante: velocidad de lectura máx " + maxRate);

        for (int ev = 0; ev < 30; ev++) {
            e.rotate(-0.1);
            for (int k = 0; k < 3; k++) {
                e.render(buf, 256);
                minRate = Math.min(minRate, e.getRate());
            }
        }
        for (int k = 0; k < 20; k++) {
            e.render(buf, 256);
        }
        ok(minRate < -0.5, "scratch atrás: velocidad negativa " + minRate);
        ok(Math.abs(e.getPositionSeconds() - start) < 0.02, "scratch atrás: vuelve al punto de partida");

        for (int k = 0; k < 10; k++) {
            e.render(buf, 256);
        }
        e.render(buf, 256);
        ok(energy(buf, 256) < 2.0, "dedo quieto en la rueda: silencio (energía " + energy(buf, 256) + ")");

        e.touchUp();
        e.setPlaying(true);
        double before = e.getPositionSeconds();
        for (int k = 0; k < 70; k++) {
            e.render(buf, 256);
        }
        ok(e.getPositionSeconds() > before + 0.15, "soltar y reproducir: reanuda");

        e.touchDown();
        for (int ev = 0; ev < 200; ev++) {
            e.rotate(-0.5);
            e.render(buf, 256);
        }
        ok(e.getPositionSeconds() >= 0, "scratch hacia atrás más allá del inicio: posición acotada a 0");
        e.touchUp();

        ScratchEngine e2 = new ScratchEngine();
        e2.load(p);
        e2.setPlaying(true);
        int guard = 0;
        while (e2.isPlaying() && guard++ < 10000) {
            e2.render(buf, 256);
        }
        ok(!e2.isPlaying() && guard < 10000, "fin de pista: se detiene sola");

        boolean clean = true;
        ScratchEngine e3 = new ScratchEngine();
        e3.load(p);
        e3.setPlaying(true);
        e3.touchDown();
        for (int ev = 0; ev < 300; ev++) {
            e3.rotate(Math.sin(ev * 0.3) * 1.2);
            e3.render(buf, 256);
            for (int i = 0; i < 512; i++) {
                if (buf[i] < -32768 || buf[i] > 32767) {
                    clean = false;
                }
            }
        }
        ok(clean, "scratch agresivo: salida siempre dentro de rango");

        ScratchEngine e4 = new ScratchEngine();
        e4.setOutputRate(48000);
        e4.load(p);
        ok(e4.isIdle(), "motor recién cargado: inactivo (el hilo de audio puede parar)");
        e4.setPlaying(true);
        ok(!e4.isIdle(), "reproduciendo: no inactivo");
        for (int i = 0; i < 188; i++) {
            e4.render(buf, 256);
        }
        double p48 = e4.getPositionSeconds();
        ok(p48 > 0.85 && p48 < 0.93, "salida a 48 kHz con pista a 44,1 kHz: 1 s real = " + p48 + " s de pista");

        e4.setPlaying(false);
        for (int i = 0; i < 400; i++) {
            e4.render(buf, 256);
        }
        ok(e4.isIdle(), "tras pausar: vuelve a inactivo");
        double s0 = e4.getPositionSeconds();
        e4.setSeek(1);
        for (int i = 0; i < 50; i++) {
            e4.render(buf, 256);
        }
        double s1 = e4.getPositionSeconds();
        ok(s1 - s0 > 0.25, "avance rápido en pausa: +" + (s1 - s0) + " s con sonido");
        double energyFf = energy(buf, 256);
        ok(energyFf > 50, "avance rápido: suena (energía " + energyFf + ")");
        // la barra del globo: cambiar la velocidad en caliente (0,5x lento, 4x rápido)
        e4.setSeekSpeed(4.0);
        double f0 = e4.getPositionSeconds();
        for (int i = 0; i < 50; i++) {
            e4.render(buf, 256);
        }
        double fFast = e4.getPositionSeconds() - f0;
        e4.setSeekSpeed(0.5);
        f0 = e4.getPositionSeconds();
        for (int i = 0; i < 50; i++) {
            e4.render(buf, 256);
        }
        double fSlow = e4.getPositionSeconds() - f0;
        ok(fFast > fSlow * 3, "avance con barra: 4x (" + fFast + " s) mucho más que 0,5x (" + fSlow + " s)");
        ok(Math.abs(SeekSpeed.forDrag(100f, 1f) - 4.0) < 1e-9 && Math.abs(SeekSpeed.forDrag(-100f, 1f) - 0.5) < 1e-9
                && Math.abs(SeekSpeed.forDrag(0f, 1f) - 1.5) < 1e-9, "barra: 0 = 1,5x, derecha = 4x, izquierda = 0,5x");
        double beforeRew = e4.getPositionSeconds();
        e4.setSeek(-1);
        for (int i = 0; i < 80; i++) {
            e4.render(buf, 256);
        }
        ok(e4.getPositionSeconds() < beforeRew, "rebobinado rápido: retrocede");
        e4.setSeek(0);
        for (int i = 0; i < 400; i++) {
            e4.render(buf, 256);
        }
        ok(e4.isIdle(), "soltar el avance rápido: se detiene");

        ScratchEngine e5 = new ScratchEngine();
        e5.load(p);
        e5.setPlaying(true);
        int g2 = 0;
        boolean endedSeen = false;
        while (e5.isPlaying() && g2++ < 10000) {
            e5.render(buf, 256);
            if (e5.consumeEnded()) {
                endedSeen = true;
            }
        }
        ok(endedSeen && !e5.consumeEnded(), "fin de pista: avisa una sola vez (para pasar a la siguiente)");

        ScratchEngine e6 = new ScratchEngine();
        e6.load(p);
        e6.setVolume(0.25f);
        e6.setPlaying(true);
        for (int i = 0; i < 300; i++) {
            e6.render(buf, 256);
        }
        ScratchEngine e7 = new ScratchEngine();
        e7.load(p);
        e7.setPlaying(true);
        short[] buf7 = new short[512];
        for (int i = 0; i < 300; i++) {
            e7.render(buf7, 256);
        }
        ok(energy(buf, 256) < energy(buf7, 256) * 0.5, "volumen reducido (ducking) baja la energía");

        ScratchEngine e8 = new ScratchEngine();
        e8.load(p);
        e8.setPitch(1.08);
        e8.setPlaying(true);
        for (int i = 0; i < 172; i++) {
            e8.render(buf, 256);
        }
        double pp = e8.getPositionSeconds();
        ok(pp > 0.92 && pp < 0.99, "pitch +8%: en 1 s real avanza " + pp + " s de pista");
        e8.setPitch(0.92);
        double q0 = e8.getPositionSeconds();
        for (int i = 0; i < 172; i++) {
            e8.render(buf, 256);
        }
        double q = e8.getPositionSeconds() - q0;
        ok(q > 0.90 && q < 0.95, "pitch -8%: en 1 s real avanza " + q + " s de pista");
        e8.seekTo(2.0);
        ok(Math.abs(e8.getPositionSeconds() - 2.0) < 1e-6, "seek a mano (barra de progreso): salta a 2,0 s");


        // Modo DJ: efectos (energía media sobre 1 s para no depender del punto de la pista)
        ScratchEngine d1 = new ScratchEngine();
        d1.load(p);
        d1.setPlaying(true);
        short[] bd = new short[512];
        for (int i = 0; i < 100; i++) {
            d1.render(bd, 256);
        }
        double eN = avgEnergy(d1, bd, 172);
        d1.setEq(0, 0, 0);
        avgEnergy(d1, bd, 20);
        double eK = avgEnergy(d1, bd, 100);
        ok(eK < eN * 0.2, "EQ con todo en kill: casi silencio (" + eK + " vs " + eN + ")");
        d1.setEq(1, 1, 1);
        d1.setFilter(1.0);
        avgEnergy(d1, bd, 20);
        double eH = avgEnergy(d1, bd, 172);
        ok(eH < eN * 0.7, "filtro paso alto cerrado: baja la energía (" + eH + " vs " + eN + ")");
        d1.resetFx();
        avgEnergy(d1, bd, 20);
        double eR = avgEnergy(d1, bd, 172);
        ok(eR > eN * 0.5 && eR < eN * 1.5, "resetFx vuelve a sonar como antes (" + eR + ")");
        boolean fxClean = true;
        d1.setEcho(1.0);
        d1.setFilter(-0.5);
        d1.setEq(1.6, 0.3, 1.6);
        for (int i = 0; i < 300; i++) {
            d1.render(bd, 256);
            for (int k = 0; k < 512; k++) {
                if (bd[k] > 32767 || bd[k] < -32768) {
                    fxClean = false;
                }
            }
        }
        ok(fxClean, "efectos al máximo: salida dentro de rango");
        d1.resetFx();
        double b0 = d1.getPositionSeconds();
        d1.backspin();
        double minRb = 0;
        for (int i = 0; i < 40; i++) {
            d1.render(bd, 256);
            minRb = Math.min(minRb, d1.getRate());
        }
        ok(minRb < -1.0, "backspin: la velocidad se vuelve negativa (" + minRb + ")");
        ok(d1.getPositionSeconds() < b0, "backspin: retrocede en la pista");
        for (int i = 0; i < 200; i++) {
            d1.render(bd, 256);
        }
        ok(d1.getRate() > 0.5, "backspin: termina y recupera la reproducción (" + d1.getRate() + ")");

        // Fuente de puntos (guía Lucent)
        boolean glyphsOk = true;
        String all = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:/.- ";
        for (int i = 0; i < all.length(); i++) {
            String g = DotFont.pattern(all.charAt(i));
            glyphsOk &= g != null && g.length() % 7 == 0 && g.matches("[01]+");
        }
        ok(glyphsOk, "DotFont: A-Z, 0-9 y símbolos tienen glifos válidos de 7 filas");
        ok(DotFont.width('A') == 5 && DotFont.width(':') == 1 && DotFont.width(' ') == 3,
                "DotFont: anchos 5 / 1 / 3");
        ok(DotFont.cols("00:00") == 25, "DotFont: '00:00' ocupa 25 columnas (" + DotFont.cols("00:00") + ")");
        ok(DotFont.fold('\u00E1') == 'A' && DotFont.fold('\u00F1') == 'N' && DotFont.fold('#') == ' ',
                "DotFont: acentos plegados y desconocidos a espacio");
        ok(DotFont.on('I', 2, 0) && !DotFont.on('I', 0, 0) && DotFont.on(':', 0, 2) && !DotFont.on(':', 0, 3),
                "DotFont: puntos de I y de los dos puntos");

        // MP3 (PCM decodificado con ffmpeg: comprueba la ruta posterior al decodificador de Android)
        if (args.length > 1) {
            String d2 = args[1];
            short[] st = readPcm(d2 + "/test.pcm");
            PcmData.Builder pb = new PcmData.Builder(132300);
            for (int off = 0; off < st.length; off += 8192) {
                int cnt = Math.min(8192, st.length - off) / 2 * 2;
                short[] chunk = java.util.Arrays.copyOfRange(st, off, off + cnt);
                pb.appendInterleaved(chunk, cnt, 2);
            }
            PcmData mp = pb.build(44100);
            ok(mp.frames == 132300, "MP3 estéreo 44,1 kHz: " + mp.frames + " frames = 3,0 s");
            ScratchEngine me = new ScratchEngine();
            me.setOutputRate(48000);
            me.load(mp);
            me.setPlaying(true);
            short[] mbuf = new short[512];
            double energy = 0;
            for (int i = 0; i < 188; i++) {
                me.render(mbuf, 256);
                for (int k = 0; k < 512; k++) {
                    energy += Math.abs(mbuf[k]);
                }
            }
            ok(me.getPositionSeconds() > 0.85 && me.getPositionSeconds() < 0.95,
                    "MP3: tras 1 s suena y avanza " + me.getPositionSeconds() + " s");
            ok(energy / (188 * 512) > 500, "MP3: señal audible (media " + (long) (energy / (188 * 512)) + ")");
            short[] mono = readPcm(d2 + "/mono.pcm");
            PcmData.Builder mb = new PcmData.Builder();
            mb.appendInterleaved(mono, mono.length, 1);
            PcmData mm = mb.build(22050);
            boolean dup = true;
            for (int i = 0; i < mm.frames; i++) {
                dup &= mm.stereo[2 * i] == mm.stereo[2 * i + 1];
            }
            ok(mm.frames == 44100 && dup, "MP3 mono 22 kHz: se duplica a estéreo (" + mm.frames + " frames)");
            ScratchEngine em = new ScratchEngine();
            em.setOutputRate(48000);
            em.load(mm);
            ok(Math.abs(em.getDurationSeconds() - 2.0) < 0.01, "MP3 mono: duración 2,0 s");
            PcmData.Builder fb = new PcmData.Builder();
            fb.appendFloat(new float[] {1f, -1f, 0.5f, 2f, -3f, 0f}, 6, 2);
            PcmData fp = fb.build(44100);
            ok(fp.frames == 3 && fp.stereo[0] == 32767 && fp.stereo[1] == -32767 && fp.stereo[3] == 32767
                    && fp.stereo[4] == -32767, "float PCM: conversión y recorte a 16 bits");
        }

        boolean iconsOk = true;
        for (String[] ic : new String[][] {dev.scratchwheel.DotIcons.PLAY, dev.scratchwheel.DotIcons.PAUSE,
                dev.scratchwheel.DotIcons.NEXT, dev.scratchwheel.DotIcons.PREV, dev.scratchwheel.DotIcons.FFW,
                dev.scratchwheel.DotIcons.REW, dev.scratchwheel.DotIcons.SHUFFLE, dev.scratchwheel.DotIcons.REPEAT,
                dev.scratchwheel.DotIcons.REPEAT_ONE, dev.scratchwheel.DotIcons.HEART,
                dev.scratchwheel.DotIcons.HEART_FILLED, dev.scratchwheel.DotIcons.QUEUE,
                dev.scratchwheel.DotIcons.MENU, dev.scratchwheel.DotIcons.PITCH, dev.scratchwheel.DotIcons.MOON, dev.scratchwheel.DotIcons.LOCK, dev.scratchwheel.DotIcons.DISC, dev.scratchwheel.DotIcons.COVER, dev.scratchwheel.DotIcons.DOWN, dev.scratchwheel.DotIcons.FOLDER, dev.scratchwheel.DotIcons.VOL, dev.scratchwheel.DotIcons.SEARCH, dev.scratchwheel.DotIcons.PLUS, dev.scratchwheel.DotIcons.CLOSE}) {
            iconsOk &= dev.scratchwheel.DotIcons.valid(ic);
        }
        ok(iconsOk, "DotIcons: los 24 iconos son rejillas 7x7 válidas");

        try {
            java.io.File pf = java.io.File.createTempFile("lists", ".txt");
            pf.delete();
            dev.scratchwheel.Playlists pl = new dev.scratchwheel.Playlists();
            pl.init(pf);
            ok(pl.create("Rock  duro") && !pl.create("rock  duro") && !pl.create("   "), "listas: crear, sin duplicados ni vacías");
            java.util.List<String> us = new java.util.ArrayList<String>();
            us.add("content://a/1");
            us.add("content://a/2");
            us.add("content://a/1");
            ok(pl.add("ROCK  DURO", us) == 2 && pl.size("Rock  duro") == 2, "listas: añadir sin repetir, nombre sin distinguir mayúsculas");
            pl.remove("Rock  duro", "content://a/1");
            dev.scratchwheel.Playlists pl2 = new dev.scratchwheel.Playlists();
            pl2.init(pf);
            ok(pl2.names().size() == 1 && pl2.uris("Rock  duro").size() == 1 && pl2.uris("Rock  duro").get(0).equals("content://a/2"),
                    "listas: se guardan y se recargan desde el archivo");
            pl2.delete("rock  duro");
            ok(pl2.names().isEmpty(), "listas: borrar");
            pf.delete();
        } catch (Exception ex) {
            ok(false, "listas: excepción " + ex);
        }

        // Shuffle estilo Spotify
        java.util.Random rr = new java.util.Random(7);
        boolean perm = true;
        for (int trial = 0; trial < 50; trial++) {
            int[] o = ShuffleOrder.build(200, trial % 3 == 0 ? 17 : -1, 5, rr);
            boolean[] seen = new boolean[200];
            for (int v : o) {
                perm &= !seen[v];
                seen[v] = true;
            }
            if (trial % 3 == 0) {
                perm &= o[0] == 17;
            } else {
                perm &= o[0] != 5;
            }
        }
        ok(perm, "shuffle: permutación sin repeticiones, primero fijado y sin repetir la actual");
        int[] one = ShuffleOrder.build(1, -1, 0, rr);
        ok(one.length == 1 && one[0] == 0, "shuffle: lista de 1 elemento");

        {
            String csv = "\uFEFF\"Track URI\",\"Track Name\",\"Album Name\",\"Artist Name(s)\",\"Duration (ms)\"\n"
                    + "\"spotify:track:1\",\"Héroe (feat. X) - Remastered 2011\",\"Alb, \"\"Uno\"\"\",\"Ana Pérez;Otro\",200\"\n";
            csv = csv.replace("200\"\n", "200\n");
            csv += "\"spotify:track:2\",\"Song Two\",\"B\",\"The Band\",100\n"
                    + "\"spotify:track:3\",\"Nada\",\"C\",\"Nadie\",100\n";
            java.util.List<dev.scratchwheel.SpotifyImport.Row> rows = dev.scratchwheel.SpotifyImport.parseTracks(csv);
            ok(rows.size() == 3 && rows.get(0).title.startsWith("H") && rows.get(0).album.equals("Alb, \"Uno\""),
                    "SpotifyImport: CSV con comillas, BOM y comas");
            java.util.List<dev.scratchwheel.SpotifyImport.Cand> loc = new java.util.ArrayList<dev.scratchwheel.SpotifyImport.Cand>();
            loc.add(new dev.scratchwheel.SpotifyImport.Cand("", "", "Ana Perez - Heroe"));
            loc.add(new dev.scratchwheel.SpotifyImport.Cand("Song Two", "The Band", "track02"));
            loc.add(new dev.scratchwheel.SpotifyImport.Cand("Song Two", "Otro", "x"));
            dev.scratchwheel.SpotifyImport.Result r = dev.scratchwheel.SpotifyImport.match(rows, loc);
            ok(r.matched.size() == 2 && r.matched.contains(0) && r.matched.contains(1) && r.missing.size() == 1,
                    "SpotifyImport: empareja por título/artista sin acentos y deja las no encontradas");
        }
        {
            java.util.List<dev.scratchwheel.SpotifyImport.Row> r = dev.scratchwheel.SpotifyImport.parseTracks(
                    "#EXTM3U\n#EXTINF:200,Ana - Heroe\nfile.mp3\n#EXTINF:100,Solo titulo\nx.mp3\n");
            ok(r.size() == 2 && r.get(0).artist.equals("Ana") && r.get(0).title.equals("Heroe")
                    && r.get(1).title.equals("Solo titulo"), "SpotifyImport: M3U extendido");
        }

        // ---- Stats
        {
            final java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");
            long day = 86400000L;
            long t0 = 1700000000000L - (1700000000000L % day) + 20 * 3600000L; // 20:00 UTC
            java.util.List<Stats.Entry> es = new java.util.ArrayList<Stats.Entry>();
            es.add(new Stats.Entry(t0, "a", 200, true));
            es.add(new Stats.Entry(t0 + 1000, "a", 100, true));
            es.add(new Stats.Entry(t0 + day, "b", 50, false));
            es.add(new Stats.Entry(t0 + 2 * day, "a", 60, true));
            es.add(new Stats.Entry(t0 + 5 * day, "c", 30, true));
            Stats st = new Stats(es, new Stats.Info() {
                public String[] tags(String u) {
                    return new String[] {"T" + u, u.equals("c") ? "Y" : "X", "AL"};
                }
            }, utc);
            java.util.List<Stats.Row> top = st.top(Stats.TRACK, 0, Long.MAX_VALUE, 5);
            ok(top.size() == 3 && top.get(0).name.equals("Ta") && top.get(0).secs == 360 && top.get(0).plays == 3,
                    "Stats: top canciones por tiempo y plays");
            java.util.List<Stats.Row> ar = st.top(Stats.ARTIST, 0, Long.MAX_VALUE, 5);
            ok(ar.get(0).name.equals("X") && ar.get(0).secs == 410, "Stats: top artistas agrega");
            Stats.Summary sm = st.summary(0, Long.MAX_VALUE);
            ok(sm.bestStreak == 3 && sm.activeDays == 4, "Stats: racha " + sm.bestStreak + " dias " + sm.activeDays);
            ok(sm.favHour == 20, "Stats: hora favorita " + sm.favHour);
            ok(sm.plays == 4 && sm.tracks == 3 && sm.artists == 2, "Stats: totales");
            ok(st.rankOf("a") == 1, "Stats: rankOf");
            ok(Stats.duration(2700).equals("45 M"), "Stats: duration " + Stats.duration(2700));
        }

        // ---- Mantener el tono: con pitch 1.5 el tono sube sin keylock y se mantiene con keylock
        {
            int sr = 44100;
            int fr = sr * 10;
            short[] sine = new short[fr * 2];
            for (int i = 0; i < fr; i++) {
                short v = (short) (12000 * Math.sin(2 * Math.PI * 440.0 * i / sr));
                sine[2 * i] = v;
                sine[2 * i + 1] = v;
            }
            String sw = dir + "/sine.wav";
            writeWav(sw, sine, fr, sr);
            PcmData sp;
            try (FileInputStream in = new FileInputStream(sw)) {
                sp = WavParser.parse(in);
            }
            double[] hz = new double[2];
            for (int k = 0; k < 2; k++) {
                ScratchEngine se = new ScratchEngine();
                se.setOutputRate(44100);
                se.load(sp);
                se.setPitch(1.5);
                se.setKeyLock(k == 1);
                se.setPlaying(true);
                short[] b = new short[512];
                for (int i = 0; i < 60; i++) {
                    se.render(b, 256); // arranque
                }
                int cross = 0;
                int frames = 0;
                short prev = 0;
                for (int i = 0; i < 300; i++) {
                    se.render(b, 256);
                    for (int j = 0; j < 256; j++) {
                        short v = b[2 * j];
                        if (prev < 0 && v >= 0) {
                            cross++;
                        }
                        prev = v;
                    }
                    frames += 256;
                }
                hz[k] = cross / (frames / 44100.0);
            }
            ok(hz[0] > 600 && hz[0] < 720, "sin keylock a 1.5x el tono sube (" + hz[0] + " Hz)");
            ok(hz[1] > 410 && hz[1] < 470, "con keylock a 1.5x el tono se mantiene (" + hz[1] + " Hz)");
        }

        // ---- Inercia: soltar la rueda lanzada con la música parada
        {
            ScratchEngine ie = new ScratchEngine();
            ie.setOutputRate(44100);
            ie.load(p);
            ie.setInertia(true, 1.5);
            ie.seekTo(5);
            short[] b = new short[512];
            ie.touchDown();
            for (int i = 0; i < 60; i++) {
                ie.rotate(0.35);
                ie.render(b, 256);
            }
            double rUp = ie.getRate();
            ie.touchUp();
            for (int i = 0; i < 86; i++) {
                ie.render(b, 256); // ~0.5 s
            }
            double rMid = ie.getRate();
            ok(rUp > 1.0 && rMid > 0.25 * rUp, "inercia: tras soltar sigue girando (" + rUp + " -> " + rMid + ")");
            for (int i = 0; i < 2000; i++) {
                ie.render(b, 256);
            }
            ok(ie.getRate() == 0, "inercia: acaba parando (" + ie.getRate() + ")");
            ScratchEngine ne = new ScratchEngine();
            ne.setOutputRate(44100);
            ne.load(p);
            ne.setInertia(false, 1.5);
            ne.seekTo(5);
            ne.touchDown();
            for (int i = 0; i < 60; i++) {
                ne.rotate(0.35);
                ne.render(b, 256);
            }
            ne.touchUp();
            for (int i = 0; i < 86; i++) {
                ne.render(b, 256);
            }
            ok(ne.getRate() > 5, "sin inercia: frenado lineal de siempre, sigue girando (" + ne.getRate() + ")");
        }
        // ---- gimmicks de DJ
        {
            ScratchEngine g = new ScratchEngine();
            g.setOutputRate(44100);
            g.load(p);
            short[] b = new short[512];
            g.seekTo(3.0);
            g.setCue(0);
            g.seekTo(10.0);
            ok(g.jumpCue(0) && Math.abs(g.getPositionSeconds() - 3.0) < 0.01, "hot cue: guarda y salta");
            ok(!g.jumpCue(2), "hot cue vacío no salta");
            g.jump(4);
            ok(Math.abs(g.getPositionSeconds() - 7.0) < 0.01, "beat jump +4 s");
            g.jump(-100);
            ok(g.getPositionSeconds() == 0, "beat jump se queda en 0");

            // bucle manual
            g.seekTo(5.0);
            g.setPlaying(true);
            for (int i = 0; i < 40; i++) {
                g.render(b, 256);
            }
            g.loopIn();
            double lo = g.getPositionSeconds();
            for (int i = 0; i < 172; i++) {
                g.render(b, 256);
            }
            g.loopOut();
            boolean inside = true;
            for (int i = 0; i < 700; i++) {
                g.render(b, 256);
                double qq = g.getPositionSeconds();
                if (qq < lo - 0.05 || qq > lo + 1.1) {
                    inside = false;
                }
            }
            ok(inside && g.isLoopOn(), "bucle: la posición se queda dentro");
            g.loopExit();
            for (int i = 0; i < 400; i++) {
                g.render(b, 256);
            }
            ok(g.getPositionSeconds() > lo + 2, "bucle: al salir sigue adelante");

            // roll + slip: al soltar vuelve a donde estaría
            ScratchEngine r = new ScratchEngine();
            r.setOutputRate(44100);
            r.load(p);
            r.seekTo(2.0);
            r.setPlaying(true);
            for (int i = 0; i < 300; i++) {
                r.render(b, 256);
            }
            double bef = r.getPositionSeconds();
            r.rollStart(0.25);
            for (int i = 0; i < 172; i++) {
                r.render(b, 256);
            }
            double during = r.getPositionSeconds();
            r.rollEnd();
            double after = r.getPositionSeconds();
            ok(during < bef + 0.4, "roll: repite un trozo corto (" + during + ")");
            ok(after > bef + 0.9, "roll: al soltar salta a donde habría llegado (" + after + ")");

            // reverse suena hacia atrás
            ScratchEngine v = new ScratchEngine();
            v.setOutputRate(44100);
            v.load(p);
            v.seekTo(8.0);
            v.setPlaying(true);
            for (int i = 0; i < 200; i++) {
                v.render(b, 256);
            }
            double pa = v.getPositionSeconds();
            v.setReverse(true);
            for (int i = 0; i < 100; i++) {
                v.render(b, 256);
            }
            ok(v.getPositionSeconds() < pa, "reverse: retrocede");
            v.setReverse(false);

            // cut silencia
            ScratchEngine c = new ScratchEngine();
            c.setOutputRate(44100);
            c.load(p);
            c.seekTo(4.0);
            c.setPlaying(true);
            for (int i = 0; i < 100; i++) {
                c.render(b, 256);
            }
            double eOn = avgEnergy(c, b, 20);
            c.setCut(true);
            for (int i = 0; i < 20; i++) {
                c.render(b, 256);
            }
            double eCut = avgEnergy(c, b, 20);
            ok(eOn > 100 && eCut < eOn * 0.02, "cut: silencia (" + eOn + " -> " + eCut + ")");
            c.setCut(false);
            c.setGate(4);
            double mn = 1e18;
            double mx = 0;
            for (int i = 0; i < 100; i++) {
                c.render(b, 256);
                double en = 0;
                for (int k = 0; k < 512; k++) {
                    en += Math.abs(b[k]);
                }
                mn = Math.min(mn, en);
                mx = Math.max(mx, en);
            }
            ok(mx > 1000 && mn < mx * 0.05, "trans/gate: corta a ritmo");
            c.setGate(0);

            // efectos no rompen y no desbordan
            for (int mode = 0; mode < 3; mode++) {
                ScratchEngine f = new ScratchEngine();
                f.setOutputRate(48000);
                f.load(p);
                f.seekTo(4.0);
                f.setPlaying(true);
                if (mode == 0) {
                    f.setFlanger(true);
                } else if (mode == 1) {
                    f.setPhaser(true);
                } else {
                    f.echoOut();
                }
                boolean fin = true;
                double maxAbs = 0;
                for (int i = 0; i < 700; i++) {
                    f.render(b, 256);
                    for (int k = 0; k < 512; k++) {
                        maxAbs = Math.max(maxAbs, Math.abs(b[k]));
                    }
                }
                ok(maxAbs > 500, "efecto " + (mode == 0 ? "flanger" : mode == 1 ? "phaser" : "echo out") + " suena (" + maxAbs + ")");
            }
            // echo out: se calla y vuelve
            ScratchEngine eo = new ScratchEngine();
            eo.setOutputRate(44100);
            eo.load(p);
            eo.seekTo(4.0);
            eo.setPlaying(true);
            eo.echoOut();
            double midE = 0;
            double endE = 0;
            for (int i = 0; i < 700; i++) {
                eo.render(b, 256);
                double en = 0;
                for (int k = 0; k < 512; k++) {
                    en += Math.abs(b[k]);
                }
                if (i == 340) {
                    midE = en;
                }
                if (i == 690) {
                    endE = en;
                }
            }
            ok(endE > 2000, "echo out: el sonido vuelve (" + endE + ")");

            // sampler suena aunque no haya pista reproduciendo y mantiene el motor activo
            ScratchEngine sm = new ScratchEngine();
            sm.setOutputRate(44100);
            sm.load(p);
            for (int id = 0; id < 4; id++) {
                sm.triggerSample(id);
                double pk = 0;
                for (int i = 0; i < 120; i++) {
                    sm.render(b, 256);
                    for (int k = 0; k < 512; k++) {
                        pk = Math.max(pk, Math.abs(b[k]));
                    }
                }
                ok(pk > 1500, "sampler " + id + " suena (" + pk + ")");
                ok(sm.getLevel() >= 0, "nivel ok");
            }
            sm.triggerSample(0);
            ok(!sm.isIdle(), "sampler activo: el motor no se duerme");
            for (int i = 0; i < 400; i++) {
                sm.render(b, 256);
            }
            ok(sm.isIdle(), "sampler terminado: el motor puede dormir");

            // vinyl stop dura lo pedido
            ScratchEngine vs = new ScratchEngine();
            vs.setOutputRate(44100);
            vs.load(p);
            vs.seekTo(3.0);
            vs.setPlaying(true);
            for (int i = 0; i < 100; i++) {
                vs.render(b, 256);
            }
            vs.armVinylStop(1.0);
            vs.setPlaying(false);
            int n = 0;
            while (vs.getRate() != 0 && n < 400) {
                vs.render(b, 256);
                n++;
            }
            double secs = n * 256 / 44100.0;
            ok(secs > 0.8 && secs < 1.3, "vinyl stop ~1 s (" + secs + ")");

            // sin huecos: al acabar la pista el motor entra solo en la siguiente, sin silencio ni "ended"
            PcmData.Builder gb = new PcmData.Builder();
            for (int i = 0; i < 44100; i++) {
                short tv = (short) (12000 * Math.sin(2 * Math.PI * 440 * i / 44100.0));
                gb.append(tv, tv);
            }
            PcmData tone = gb.build(44100);
            ScratchEngine gp = new ScratchEngine();
            gp.setOutputRate(44100);
            gp.load(tone);
            gp.queueNext(tone);
            gp.seekTo(0.95);
            gp.setPlaying(true);
            int zeroRun = 0;
            int maxZero = 0;
            boolean swappedOk = false;
            boolean endedBad = false;
            int aft = 0;
            for (int i = 0; i < 600 && aft < 40; i++) {
                if (swappedOk) {
                    aft++;
                }
                gp.render(b, 256);
                for (int k = 0; k < 256; k++) {
                    if (b[2 * k] == 0 && b[2 * k + 1] == 0) {
                        zeroRun++;
                        maxZero = Math.max(maxZero, zeroRun);
                    } else {
                        zeroRun = 0;
                    }
                }
                swappedOk |= gp.consumeSwapped();
                endedBad |= gp.consumeEnded();
            }
            ok(swappedOk, "sin huecos: el motor salta solo a la pista encolada");
            ok(!endedBad, "sin huecos: no se marca fin de pista");
            ok(maxZero < 40, "sin huecos: sin silencio entre pistas (racha de ceros " + maxZero + ")");
            ok(gp.getPositionSeconds() > 0, "sin huecos: sigue sonando en la nueva pista");

            // ecualizador gráfico: +12 dB sube un tono de 60 Hz y -12 dB lo baja
            PcmData.Builder eb = new PcmData.Builder();
            for (int i = 0; i < 44100 * 2; i++) {
                short lv = (short) (6000 * Math.sin(2 * Math.PI * 60 * i / 44100.0));
                eb.append(lv, lv);
            }
            PcmData low = eb.build(44100);
            double[] rms = new double[3];
            double[][] gains = {{0, 0, 0, 0, 0}, {12, 0, 0, 0, 0}, {-12, 0, 0, 0, 0}};
            for (int gi = 0; gi < 3; gi++) {
                ScratchEngine ge = new ScratchEngine();
                ge.setOutputRate(44100);
                ge.setGraphicEq(gains[gi]);
                ge.load(low);
                ge.seekTo(0.2);
                ge.setPlaying(true);
                double sum = 0;
                int cnt = 0;
                for (int i = 0; i < 200; i++) {
                    ge.render(b, 256);
                    if (i > 60) {
                        for (int k = 0; k < 512; k++) {
                            sum += (double) b[k] * b[k];
                            cnt++;
                        }
                    }
                }
                rms[gi] = Math.sqrt(sum / cnt);
            }
            ok(rms[1] > rms[0] * 1.5, "EQ gráfico: +12 dB en 60 Hz sube el nivel (" + rms[0] + " → " + rms[1] + ")");
            ok(rms[2] < rms[0] * 0.5, "EQ gráfico: −12 dB en 60 Hz baja el nivel (" + rms[2] + ")");

            // BPM: pistas de bombos sintéticas a 128 y 100 BPM con el primer golpe en 0,25 s
            double[] tempos = {128, 100, 93.5};
            for (int ti = 0; ti < tempos.length; ti++) {
                PcmData.Builder bb = new PcmData.Builder();
                int total = 44100 * 40;
                double beatLen = 44100 * 60.0 / tempos[ti];
                double next = 44100 * 0.25;
                int age = -1;
                for (int i = 0; i < total; i++) {
                    if (i >= next) {
                        age = 0;
                        next += beatLen;
                    }
                    double kv = 0;
                    if (age >= 0 && age < 4000) {
                        kv = 20000 * Math.exp(-age / 900.0) * Math.sin(2 * Math.PI * 70 * age / 44100.0);
                        age++;
                    }
                    short sv = (short) kv;
                    bb.append(sv, sv);
                }
                double[] br = BpmDetect.detect(bb.build(44100));
                ok(br != null && Math.abs(br[0] - tempos[ti]) < 0.8, "BPM " + tempos[ti] + " detectado: " + (br == null ? "null" : br[0]));
                double beatSec = 60.0 / tempos[ti];
                double dph = br == null ? 9 : Math.abs(((br[1] - 0.25) % beatSec + beatSec * 1.5) % beatSec - beatSec * 0.5);
                ok(br != null && dph < 0.04, "BPM " + tempos[ti] + ": fase del pulso correcta (error " + dph + " s)");
            }
        }
        System.out.println(fails == 0 ? "TODO OK" : (fails + " FALLOS"));
        System.exit(fails == 0 ? 0 : 1);
    }
}
