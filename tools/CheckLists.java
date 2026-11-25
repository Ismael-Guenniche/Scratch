package dev.scratchwheel;

import java.io.File;
import java.util.Arrays;

/**
 * Pruebas de las listas (renombrar, mover, portada) y de los nombres editados. Solo Java puro:
 * compila con {@code javac -d /tmp/cl app/src/main/java/dev/scratchwheel/Playlists.java
 * app/src/main/java/dev/scratchwheel/TrackEdits.java app/src/main/java/dev/scratchwheel/BigLayout.java app/src/main/java/dev/scratchwheel/Waveform.java app/src/main/java/dev/scratchwheel/LyricsReader.java app/src/main/java/dev/scratchwheel/M3u.java app/src/main/java/dev/scratchwheel/WavRecorder.java tools/CheckLists.java} y ejecuta
 * {@code java -cp /tmp/cl dev.scratchwheel.CheckLists}.
 */
public final class CheckLists {
    private static int fails;

    private static void ok(boolean c, String what) {
        if (!c) {
            fails++;
            System.out.println("FALLO: " + what);
        }
    }

    public static void main(String[] args) throws Exception {
        File dir = File.createTempFile("lists", "");
        dir.delete();
        dir.mkdirs();
        File pf = new File(dir, "playlists.txt");

        Playlists p = new Playlists();
        p.init(pf);
        ok(p.create("Rock"), "crear Rock");
        ok(p.create("Pop"), "crear Pop");
        ok(p.create("Jazz"), "crear Jazz");
        p.add("Rock", Arrays.asList("a", "b", "c", "d"));

        // mover
        ok(p.move("Rock", "d", 0), "mover d al principio");
        ok(p.uris("Rock").equals(Arrays.asList("d", "a", "b", "c")), "orden tras mover d: " + p.uris("Rock"));
        ok(p.move("Rock", "d", 99), "mover d al final (se ajusta)");
        ok(p.uris("Rock").equals(Arrays.asList("a", "b", "c", "d")), "orden tras mover al final: " + p.uris("Rock"));
        ok(!p.move("Rock", "d", 3), "mover a la misma posición no cambia nada");
        ok(!p.move("Rock", "zzz", 1), "mover una canción que no está");
        ok(p.move("Rock", "b", -5), "mover con posición negativa se ajusta a 0");
        ok(p.indexOf("Rock", "b") == 0, "indexOf b = 0");

        // portada
        p.setCover("Rock", new File(dir, "c1.jpg").getPath());
        ok(new File(dir, "c1.jpg").getPath().equals(p.cover("Rock")), "portada guardada");
        ok(p.cover("Pop") == null, "Pop sin portada");

        // renombrar: conserva posición, canciones y portada
        ok(p.rename("Rock", "Metal"), "renombrar Rock a Metal");
        ok(p.names().equals(Arrays.asList("Metal", "Pop", "Jazz")), "posición conservada: " + p.names());
        ok(p.uris("Metal").size() == 4, "canciones conservadas");
        ok(new File(dir, "c1.jpg").getPath().equals(p.cover("Metal")), "portada conservada al renombrar");
        ok(p.cover("Rock") == null && p.find("Rock") == null, "el nombre viejo ya no existe");
        ok(!p.rename("Metal", "pop"), "no se puede renombrar a un nombre repetido (sin mayúsculas)");
        ok(!p.rename("Metal", "   "), "no se puede renombrar a vacío");
        ok(p.rename("Metal", "METAL"), "cambiar solo mayúsculas sí se puede");
        ok(p.names().get(0).equals("METAL"), "queda METAL");
        ok(p.rename("Pop", "Pop"), "mismo nombre: true");

        // persistencia
        Playlists q = new Playlists();
        q.init(pf);
        ok(q.names().equals(Arrays.asList("METAL", "Pop", "Jazz")), "recarga: nombres " + q.names());
        ok(q.uris("METAL").equals(Arrays.asList("b", "a", "c", "d")), "recarga: orden " + q.uris("METAL"));
        ok(new File(dir, "c1.jpg").getPath().equals(q.cover("METAL")), "recarga: portada");

        // quitar portada borra la imagen
        File img = new File(dir, "c2.jpg");
        ok(img.createNewFile(), "crear imagen de prueba");
        q.setCover("METAL", img.getPath());
        ok(img.getPath().equals(q.cover("METAL")), "portada nueva");
        q.setCover("METAL", null);
        ok(q.cover("METAL") == null, "portada quitada");
        ok(!img.exists(), "la imagen se borra al quitar la portada");

        // borrar lista borra su portada
        File img3 = new File(dir, "c3.jpg");
        img3.createNewFile();
        q.setCover("Jazz", img3.getPath());
        q.delete("Jazz");
        ok(!img3.exists(), "al borrar la lista se borra su imagen");

        // carpeta enlazada a la lista
        q.setFolder("Pop", "content://tree/pop");
        ok("content://tree/pop".equals(q.folder("Pop")), "carpeta enlazada");
        q.add("Pop", Arrays.asList("p1", "p2"));
        ok(q.linkedUris().equals(new java.util.HashSet<String>(Arrays.asList("p1", "p2"))), "linkedUris: " + q.linkedUris());
        ok(q.rename("Pop", "Pop2") && "content://tree/pop".equals(q.folder("Pop2")), "la carpeta sigue al renombrar");
        Playlists q2 = new Playlists();
        q2.init(pf);
        ok("content://tree/pop".equals(q2.folder("Pop2")), "la carpeta persiste");
        q2.setFolder("Pop2", null);
        ok(q2.folder("Pop2") == null && q2.linkedUris().isEmpty(), "desenlazar");
        q2.setFolder("Pop2", "content://tree/pop");
        q2.delete("Pop2");
        ok(q2.folder("Pop2") == null, "borrar la lista quita el enlace");

        // guardar un orden
        q2.create("Orden");
        q2.add("Orden", Arrays.asList("a", "b", "c", "d"));
        ok(q2.setOrder("Orden", Arrays.asList("c", "x", "a")), "setOrder cambia");
        ok(q2.uris("Orden").equals(Arrays.asList("c", "a", "b", "d")), "setOrder: lo que sobra va detrás: " + q2.uris("Orden"));
        ok(!q2.setOrder("Orden", Arrays.asList("c", "a", "b", "d")), "setOrder igual no cambia");

        // Big Picture: rejilla de módulos
        {
            java.util.List<BigLayout.Item> d = BigLayout.defaults();
            java.util.List<BigLayout.Placed> pl = BigLayout.pack(d, BigLayout.COLS);
            ok(!pl.isEmpty(), "big: hay módulos activos de serie");
            boolean[][] occ = new boolean[40][BigLayout.COLS];
            boolean overlap = false;
            boolean inside = true;
            for (BigLayout.Placed x : pl) {
                if (x.col < 0 || x.col + x.w > BigLayout.COLS) {
                    inside = false;
                }
                for (int yy = x.row; yy < x.row + x.h; yy++) {
                    for (int xx = x.col; xx < x.col + x.w; xx++) {
                        if (occ[yy][xx]) {
                            overlap = true;
                        }
                        occ[yy][xx] = true;
                    }
                }
            }
            ok(!overlap, "big: sin solapes");
            {
                // en horizontal: los mismos módulos, sin solapes, dentro de 8 columnas y más bajo que en vertical
                java.util.List<BigLayout.Placed> wp = BigLayout.packWide(d, BigLayout.COLS_WIDE);
                boolean[][] wo = new boolean[40][BigLayout.COLS_WIDE];
                boolean wOver = false;
                boolean wIn = true;
                for (BigLayout.Placed x : wp) {
                    if (x.col < 0 || x.col + x.w > BigLayout.COLS_WIDE) {
                        wIn = false;
                        continue;
                    }
                    for (int yy = x.row; yy < x.row + x.h; yy++) {
                        for (int xx = x.col; xx < x.col + x.w; xx++) {
                            wOver |= wo[yy][xx];
                            wo[yy][xx] = true;
                        }
                    }
                }
                ok(wp.size() == pl.size(), "big horizontal: están todos los módulos (" + wp.size() + ")");
                ok(!wOver && wIn, "big horizontal: sin solapes y dentro de 8 columnas");
                ok(BigLayout.rows(wp) < BigLayout.rows(pl), "big horizontal: ocupa menos filas ("
                        + BigLayout.rows(wp) + " frente a " + BigLayout.rows(pl) + ")");
            }
            ok(inside, "big: dentro de las columnas");
            ok(pl.get(0).item.id.equals("info") && pl.get(0).row == 0 && pl.get(0).col == 0, "big: el primero arriba a la izquierda");
            ok(pl.get(1).item.id.equals("cover") && pl.get(1).row == 1 && pl.get(1).col == 0, "big: portada bajo la info");
            ok(pl.get(2).item.id.equals("wheel") && pl.get(2).row == 1 && pl.get(2).col == 2, "big: scratch junto a la portada");
            // un módulo pequeño rellena el hueco que dejó uno grande
            java.util.List<BigLayout.Item> t = new java.util.ArrayList<BigLayout.Item>();
            t.add(new BigLayout.Item("cover", 1, true));  // 3x3
            t.add(new BigLayout.Item("wave", 1, true));   // 4x2
            t.add(new BigLayout.Item("volume", 0, true)); // 2x1
            t.add(new BigLayout.Item("pitch", 0, true));  // 2x1
            java.util.List<BigLayout.Placed> tp = BigLayout.pack(t, 4);
            ok(tp.get(1).row == 3, "big: la onda 4x2 va debajo de la portada 3x3");
            ok(tp.get(2).row == 5 && tp.get(3).row == 5, "big: volumen y pitch debajo de la onda: " + tp.get(2).row);
            // serializar y leer
            d.get(1).size = 2;
            d.get(4).on = true;
            String ser = BigLayout.serialize(d);
            java.util.List<BigLayout.Item> back = BigLayout.parse(ser);
            ok(BigLayout.serialize(back).equals(ser), "big: serializar y leer da lo mismo");
            java.util.List<BigLayout.Item> junk = BigLayout.parse("zzz:1:1;cover:99:1;cover:0:1;wave:1:0");
            ok(junk.size() == BigLayout.IDS.length, "big: lo desconocido se ignora y falta se completa: " + junk.size());
            ok(junk.get(0).id.equals("cover") && junk.get(0).size == 0 && junk.get(0).on, "big: tamaño fuera de rango vuelve al de serie");
            ok(junk.get(1).id.equals("wave") && !junk.get(1).on, "big: se respeta el orden y el estado guardados");
            ok(BigLayout.rows(pl) >= 5, "big: filas");
            // onda y espectro
            short[] sig = new short[2 * 44100];
            for (int i = 0; i < 44100; i++) {
                short v = (short) (12000 * Math.sin(2 * Math.PI * 1000 * i / 44100.0));
                sig[2 * i] = v;
                sig[2 * i + 1] = v;
            }
            float[] pk = Waveform.peaks(sig, 44100, 10);
            ok(pk.length == 10 && pk[3] > 0.3f && pk[3] < 0.4f, "onda: pico de un seno de 12000/32768: " + pk[3]);
            float[] sp = new float[24];
            Waveform.spectrum(sig, 44100, 20000, 44100, sp);
            int best = 0;
            for (int i = 1; i < sp.length; i++) {
                if (sp[i] > sp[best]) {
                    best = i;
                }
            }
            double fc = 60 * Math.pow(Math.min(16000, 44100 * 0.45) / 60, (best + 0.5) / 24);
            ok(fc > 700 && fc < 1500, "espectro: el seno de 1 kHz cae en la banda de " + Math.round(fc) + " Hz");
            float[] sil = new float[24];
            Waveform.spectrum(new short[2 * 4096], 4096, 2000, 44100, sil);
            ok(sil[5] == 0f, "espectro: el silencio da 0");
        }

        // nombres editados
        File ef = new File(dir, "edits.txt");
        TrackEdits e = new TrackEdits();
        e.init(ef);
        ok(e.get("u1") == null, "sin edición");
        e.set("u1", "Nuevo título", "");
        ok(Arrays.equals(e.get("u1"), new String[] {"Nuevo título", ""}), "título editado");
        e.set("u1", "Nuevo\ttítulo\n", "Artista  ");
        ok(Arrays.equals(e.get("u1"), new String[] {"Nuevo título", "Artista"}), "limpia tabuladores y saltos");
        TrackEdits e2 = new TrackEdits();
        e2.init(ef);
        ok(Arrays.equals(e2.get("u1"), new String[] {"Nuevo título", "Artista"}), "persiste");
        e2.set("u1", "", " ");
        ok(e2.get("u1") == null && !e2.has("u1"), "vacío quita la edición");
        TrackEdits e3 = new TrackEdits();
        e3.init(ef);
        ok(e3.get("u1") == null, "la eliminación persiste");

        lyricsTests();
        extraTests();
        gainTests();
        bigEditTests();
        searchTests();
        jsonListTests();
        groupTests();
        smartListTests();
        backupTests();
        keyTests();
        automixTests();
        System.out.println(fails == 0 ? "LISTAS OK" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static byte[] cat(byte[]... parts) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        for (byte[] p : parts) {
            o.write(p, 0, p.length);
        }
        return o.toByteArray();
    }

    private static byte[] b(String s) throws Exception {
        return s.getBytes("ISO-8859-1");
    }

    private static byte[] be(int v) {
        return new byte[] {(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v};
    }

    private static byte[] le(int v) {
        return new byte[] {(byte) v, (byte) (v >>> 8), (byte) (v >>> 16), (byte) (v >>> 24)};
    }

    private static byte[] ss(int v) {
        return new byte[] {(byte) ((v >> 21) & 0x7F), (byte) ((v >> 14) & 0x7F), (byte) ((v >> 7) & 0x7F),
            (byte) (v & 0x7F)};
    }

    private static byte[] id3(int ver, byte[]... frames) throws Exception {
        byte[] body = cat(frames);
        return cat(b("ID3"), new byte[] {(byte) ver, 0, 0}, ss(body.length), body, new byte[64]);
    }

    private static byte[] frame(String id, byte[] data) throws Exception {
        return cat(b(id), be(data.length), new byte[] {0, 0}, data);
    }

    private static void lyricsTests() throws Exception {
        // USLT en UTF-8 con tildes
        byte[] uslt = cat(new byte[] {3}, b("spa"), new byte[] {0}, "Hola mundo\nñandú".getBytes("UTF-8"));
        LyricsReader.Result r = LyricsReader.read(LyricsReader.of(id3(3, frame("USLT", uslt))));
        ok("Hola mundo\nñandú".equals(r.plain), "USLT utf-8: " + r.plain);
        // USLT UTF-16 con BOM
        byte[] u16 = cat(new byte[] {1}, b("eng"), new byte[] {(byte) 0xFF, (byte) 0xFE, 0, 0},
                new byte[] {(byte) 0xFF, (byte) 0xFE}, "Hi\nyou".getBytes("UTF-16LE"));
        r = LyricsReader.read(LyricsReader.of(id3(3, frame("USLT", u16))));
        ok("Hi\nyou".equals(r.plain), "USLT utf-16: " + r.plain);
        // SYLT en ms
        byte[] sylt = cat(new byte[] {0}, b("eng"), new byte[] {2, 1}, new byte[] {0},
                b("uno"), new byte[] {0}, be(1000), b("dos"), new byte[] {0}, be(2500));
        r = LyricsReader.read(LyricsReader.of(id3(3, frame("SYLT", sylt))));
        ok(r.synced.size() == 2 && r.synced.get(1).ms == 2500 && "dos".equals(r.synced.get(1).text), "SYLT");
        // USLT con formato LRC dentro se detecta como sincronizada
        byte[] lrc = cat(new byte[] {3}, b("eng"), new byte[] {0}, "[00:01.00]a\n[00:02.50]b\n[00:04.00]c".getBytes("UTF-8"));
        r = LyricsReader.read(LyricsReader.of(id3(3, frame("USLT", lrc))));
        ok(r.synced.size() == 3 && r.synced.get(2).ms == 4000 && "a\nb\nc".equals(r.plain), "LRC dentro de USLT");
        // sin letra
        r = LyricsReader.read(LyricsReader.of(id3(3, frame("TIT2", cat(new byte[] {0}, b("x"))))));
        ok(r.isEmpty(), "sin letra");
        // FLAC con Vorbis comment
        byte[] kv1 = "ARTIST=Yo".getBytes("UTF-8");
        byte[] kv2 = "LYRICS=Linea 1\nLinea ñ".getBytes("UTF-8");
        byte[] vc = cat(le(4), b("test"), le(2), le(kv1.length), kv1, le(kv2.length), kv2);
        byte[] flac = cat(b("fLaC"), new byte[] {(byte) 0x84, (byte) (vc.length >> 16), (byte) (vc.length >> 8),
            (byte) vc.length}, vc, new byte[32]);
        r = LyricsReader.read(LyricsReader.of(flac));
        ok("Linea 1\nLinea ñ".equals(r.plain), "FLAC: " + r.plain);
        // MP4 ©lyr
        byte[] txt = "Verso\nCoro".getBytes("UTF-8");
        byte[] data = cat(be(16 + txt.length), b("data"), be(1), be(0), txt);
        byte[] lyr = cat(be(8 + data.length), new byte[] {(byte) 0xA9, 'l', 'y', 'r'}, data);
        byte[] ilst = cat(be(8 + lyr.length), b("ilst"), lyr);
        byte[] meta = cat(be(12 + ilst.length), b("meta"), be(0), ilst);
        byte[] udta = cat(be(8 + meta.length), b("udta"), meta);
        byte[] moov = cat(be(8 + udta.length), b("moov"), udta);
        byte[] ftyp = cat(be(16), b("ftyp"), b("M4A "), be(0));
        r = LyricsReader.read(LyricsReader.of(cat(ftyp, moov, new byte[16])));
        ok("Verso\nCoro".equals(r.plain), "MP4: " + r.plain);
        // LRC: varios tiempos, offset, palabras
        java.util.List<LyricsReader.Line> l = LyricsReader.parseLrc(
                "[ar:X]\n[offset:500]\n[00:10.00][00:20.00]coro <00:10.50>fuerte\n[00:15.5]medio\nbasura");
        ok(l.size() == 3 && l.get(0).ms == 9500 && l.get(1).ms == 15000 && l.get(2).ms == 19500
                && "coro fuerte".equals(l.get(0).text), "LRC: " + l.size());
        ok(LyricsReader.lineAt(l, 9000) == -1 && LyricsReader.lineAt(l, 9500) == 0
                && LyricsReader.lineAt(l, 16000) == 1 && LyricsReader.lineAt(l, 99999) == 2, "lineAt");
        // basura no revienta
        ok(LyricsReader.read(LyricsReader.of(new byte[] {1, 2, 3})).isEmpty(), "archivo diminuto");
        ok(LyricsReader.read(LyricsReader.of(cat(b("ID3"), new byte[] {3, 0, 0}, ss(9999999)))).isEmpty(), "ID3 truncado");
    }

    private static void extraTests() throws Exception {
        java.util.List<M3u.Entry> es = new java.util.ArrayList<M3u.Entry>();
        es.add(new M3u.Entry("content://x/1", "Artista", "Tema\nuno", 200));
        es.add(new M3u.Entry("content://x/2", "", "Sin artista", 0));
        String m = M3u.write("Mi lista", es);
        ok(m.startsWith("#EXTM3U\n#PLAYLIST:Mi lista\n#EXTINF:200,Artista - Tema uno\ncontent://x/1\n"), "m3u: " + m);
        ok(m.contains("#EXTINF:-1,Sin artista\ncontent://x/2\n"), "m3u sin duración");
        File w = File.createTempFile("rec", ".wav");
        WavRecorder rec = new WavRecorder(w, 44100);
        rec.start();
        short[] blk = new short[2048];
        for (int i = 0; i < blk.length; i++) {
            blk[i] = (short) (i * 7 - 3000);
        }
        for (int k = 0; k < 50; k++) {
            rec.feed(blk, blk.length);
        }
        long n = rec.stop();
        ok(n == 50L * 2048 * 2, "bytes grabados " + n);
        byte[] raw = java.nio.file.Files.readAllBytes(w.toPath());
        ok(raw.length == 44 + n, "tamaño del archivo");
        ok(raw[0] == 'R' && raw[8] == 'W' && raw[36] == 'd', "cabecera");
        long riff = (raw[4] & 0xFF) | (raw[5] & 0xFF) << 8 | (raw[6] & 0xFF) << 16 | (long) (raw[7] & 0xFF) << 24;
        ok(riff == 36 + n, "RIFF size");
        ok((raw[44] & 0xFF) == (blk[0] & 0xFF) && raw[45] == (byte) (blk[0] >> 8), "primera muestra");
        ok(!rec.hadDrops(), "sin pérdidas");
        w.delete();
    }

    private static void gainTests() throws Exception {
        byte[] tx = cat(new byte[] {0}, b("REPLAYGAIN_TRACK_GAIN"), new byte[] {0}, b("-7.25 dB"));
        LyricsReader.Result r = LyricsReader.read(LyricsReader.of(id3(3, frame("TXXX", tx))));
        ok(Math.abs(r.gainDb + 7.25) < 1e-9, "ReplayGain ID3: " + r.gainDb);
        byte[] kv = "REPLAYGAIN_TRACK_GAIN=-3.10 dB".getBytes("UTF-8");
        byte[] vc = cat(le(1), b("x"), le(1), le(kv.length), kv);
        byte[] flac = cat(b("fLaC"), new byte[] {(byte) 0x84, 0, 0, (byte) vc.length}, vc, new byte[32]);
        ok(Math.abs(LyricsReader.read(LyricsReader.of(flac)).gainDb + 3.1) < 1e-9, "ReplayGain FLAC");
        ok(Double.isNaN(LyricsReader.read(LyricsReader.of(id3(3, frame("TIT2", cat(new byte[] {0}, b("x")))))).gainDb), "sin ReplayGain");
    }

    private static void bigEditTests() {
        java.util.List<BigLayout.Item> d = BigLayout.defaults();
        BigLayout.freeze(d, BigLayout.COLS);
        for (BigLayout.Item it : d) {
            ok(!it.on || it.fixed(), "freeze fija " + it.id);
        }
        java.util.List<BigLayout.Placed> a = BigLayout.pack(d, BigLayout.COLS);
        BigLayout.freeze(d, BigLayout.COLS);
        java.util.List<BigLayout.Placed> b2 = BigLayout.pack(d, BigLayout.COLS);
        boolean same = a.size() == b2.size();
        for (int i = 0; same && i < a.size(); i++) {
            same = a.get(i).col == b2.get(i).col && a.get(i).row == b2.get(i).row && a.get(i).w == b2.get(i).w
                    && a.get(i).h == b2.get(i).h;
        }
        ok(same, "freeze no cambia la disposición");
        // serialización con posiciones y tamaños libres
        BigLayout.Item cov = null;
        for (BigLayout.Item it : d) {
            if (it.id.equals("cover")) {
                cov = it;
            }
        }
        cov.on = true;
        cov.col = 1;
        cov.row = 20;
        cov.cw = 3;
        cov.ch = 5;
        java.util.List<BigLayout.Item> back = BigLayout.parse(BigLayout.serialize(d));
        BigLayout.Item c2 = null;
        for (BigLayout.Item it : back) {
            if (it.id.equals("cover")) {
                c2 = it;
            }
        }
        ok(c2.col == 1 && c2.row == 20 && c2.cw == 3 && c2.ch == 5 && c2.w() == 3 && c2.h() == 5, "pos y tamaño libres se guardan");
        ok(BigLayout.canPlace(d, cov, 1, 20, 3, 5, 4), "cabe donde está");
        ok(!BigLayout.canPlace(d, cov, 2, 20, 3, 5, 4), "no se sale por la derecha");
        BigLayout.Item wave = null;
        for (BigLayout.Item it : d) {
            if (it.id.equals("wave")) {
                wave = it;
            }
        }
        ok(!BigLayout.canPlace(d, cov, wave.col, wave.row, 2, 2, 4), "no pisa a otro módulo");
        int[] ff = BigLayout.firstFree(d, cov, 4, 2, 4);
        ok(BigLayout.canPlace(d, cov, ff[0], ff[1], 4, 2, 4), "primer hueco libre válido");
        // posición fija que choca: el módulo pasa a automático y nada se pisa
        java.util.List<BigLayout.Item> x = new java.util.ArrayList<BigLayout.Item>();
        BigLayout.Item i1 = new BigLayout.Item("cover", 0, true);
        i1.col = 0; i1.row = 0;
        BigLayout.Item i2 = new BigLayout.Item("wheel", 0, true);
        i2.col = 1; i2.row = 1;
        x.add(i1);
        x.add(i2);
        java.util.List<BigLayout.Placed> px = BigLayout.pack(x, 4);
        boolean[][] occ = new boolean[40][4];
        boolean clash = false;
        for (BigLayout.Placed q : px) {
            for (int yy = q.row; yy < q.row + q.h; yy++) {
                for (int xx = q.col; xx < q.col + q.w; xx++) {
                    clash |= occ[yy][xx];
                    occ[yy][xx] = true;
                }
            }
        }
        ok(!clash && px.size() == 2, "choque de posiciones se resuelve");
        // formato antiguo (3 campos) sigue leyéndose
        java.util.List<BigLayout.Item> old = BigLayout.parse("cover:1:1;wave:0:1");
        ok(!old.get(0).fixed() && old.get(0).w() == 3, "formato antiguo");
    }

    private static SmartSearch.Entry se(String title, String artist, String album, String folder) {
        return new SmartSearch.Entry(title, artist, album, folder, artist + " - " + title + ".mp3");
    }

    private static java.util.List<Integer> find(final SmartSearch.Entry[] lib, String q, SmartSearch.Opts o) {
        java.util.List<Integer> order = new java.util.ArrayList<Integer>();
        for (int i = 0; i < lib.length; i++) {
            order.add(i);
        }
        return SmartSearch.filter(order, SmartSearch.query(q, o), new SmartSearch.Lookup() {
            @Override
            public SmartSearch.Entry get(int i) {
                return lib[i];
            }
        });
    }

    private static void searchTests() {
        final SmartSearch.Entry[] lib = {
            se("Toxicity", "System of a Down", "Toxicity", "Musica"),       // 0
            se("Usseewa", "Ado", "Kyougen", "Musica"),                      // 1
            se("Como Camarón", "Estopa", "Estopa", "Rumba"),                // 2
            se("Bandida", "One-X", "One-X", "Musica"),                      // 3
            se("Numb", "Linkin Park", "Meteora", "Rock"),                   // 4
            se("Tamacun", "Rodrigo y Gabriela", "Rodrigo y Gabriela", "Musica"), // 5
            se("うっせぇわ", "Ado", "狂言", "Japon"),                         // 6
            se("Maldito duende", "Héroes del Silencio", "Senderos de traición", "Rock"), // 7
        };
        SmartSearch.Opts o = new SmartSearch.Opts();
        ok(SmartSearch.norm("ONE·X").equals("one x") && SmartSearch.norm("One-X").equals("one x"), "normaliza One-X");
        ok(SmartSearch.norm("Héroes").equals("heroes"), "quita acentos");
        ok(SmartSearch.norm("うっせぇわ").equals("うっせぇわ") && SmartSearch.norm("が").equals("が"), "el japonés conserva el dakuten");
        ok(SmartSearch.norm("ＡＢＣ").equals("abc"), "anchura normal");
        ok(find(lib, "one x", o).contains(3), "«one x» → One-X");
        ok(find(lib, "onex", o).contains(3), "«onex» → One-X");
        ok(find(lib, "ONE-X", o).get(0) == 3, "«ONE-X» → One-X primero");
        ok(find(lib, "estepa", o).contains(2), "«estepa» → Estopa");
        java.util.List<Integer> ado = find(lib, "ado", o);
        ok(ado.contains(1) && ado.contains(6) && !ado.contains(0), "«ado» → Ado sin System of a Down: " + ado);
        ok(find(lib, "heroes silencio", o).equals(Arrays.asList(7)), "varias palabras y acentos");
        ok(find(lib, "linkn", o).contains(4), "principio parecido mientras escribes");
        ok(find(lib, "lnkin park", o).get(0) == 4, "una letra de menos");
        ok(find(lib, "lniknin", o).isEmpty() || find(lib, "lniknin", o).get(0) == 4, "letras cruzadas");
        ok(find(lib, "linkni", o).contains(4), "dos letras cruzadas");
        ok(find(lib, "camaron", o).contains(2), "«camaron» → Camarón");
        ok(find(lib, "うっせ", o).contains(6), "japonés por prefijo");
        ok(find(lib, "zzzz", o).isEmpty(), "nada encaja");
        ok(find(lib, "   ", o).size() == lib.length, "vacío lo deja todo");
        // relevancia: el artista exacto antes que la coincidencia en carpeta
        java.util.List<Integer> rock = find(lib, "rock", o);
        ok(rock.size() == 2, "«rock» por carpeta: " + rock);
        java.util.List<Integer> rel = find(lib, "rodrigo", o);
        ok(rel.equals(Arrays.asList(5)), "rodrigo");
        java.util.List<Integer> tox = find(lib, "toxicity", o);
        ok(tox.get(0) == 0, "toxicity");
        // tolerancias
        SmartSearch.Opts no = new SmartSearch.Opts();
        no.tol = SmartSearch.TOL_NO;
        ok(!find(lib, "estepa", no).contains(2), "sin tolerancia «estepa» no sale");
        ok(find(lib, "one x", no).contains(3), "sin tolerancia sigue limpiando signos");
        SmartSearch.Opts low = new SmartSearch.Opts();
        low.tol = SmartSearch.TOL_LOW;
        ok(!find(lib, "estepa", low).contains(2) && find(lib, "silencoi", low).contains(7), "tolerancia POCA");
        // campos
        SmartSearch.Opts onlyArtist = new SmartSearch.Opts();
        onlyArtist.fields = SmartSearch.F_ARTIST;
        ok(find(lib, "meteora", onlyArtist).isEmpty() && find(lib, "linkin", onlyArtist).contains(4), "solo artista");
        SmartSearch.Opts noInside = new SmartSearch.Opts();
        noInside.inside = false;
        ok(find(lib, "acun", o).contains(5) && !find(lib, "acun", noInside).contains(5), "dentro de palabras");
        // orden de la lista
        SmartSearch.Opts keep = new SmartSearch.Opts();
        keep.relevance = false;
        java.util.List<Integer> k1 = find(lib, "musica", keep);
        ok(k1.equals(Arrays.asList(0, 1, 3, 5)), "orden de la lista: " + k1);
        // relevancia: exacta arriba
        final SmartSearch.Entry[] lib2 = {
            se("Estopa remix", "DJ X", "Mix", "Musica"),
            se("Estopa", "Estopa", "Estopa", "Rumba"),
        };
        ok(find(lib2, "estopa", o).get(0) == 1, "exacta en artista primero");
        ok(find(lib2, "estopa", keep).get(0) == 0, "sin relevancia, orden original");
        // rendimiento: 5.000 canciones
        final SmartSearch.Entry[] big = new SmartSearch.Entry[5000];
        java.util.Random r = new java.util.Random(7);
        String[] art = {"Estopa", "Linkin Park", "Ado", "System of a Down", "One-X", "Rosalía", "Bad Bunny", "Queen"};
        for (int i = 0; i < big.length; i++) {
            big[i] = se("Canción número " + i + " " + Long.toString(r.nextLong(), 36), art[i % art.length],
                    "Álbum " + (i % 300), "Carpeta " + (i % 20));
        }
        for (int w = 0; w < 200; w++) {
            find(big, "estepa remix", o);
        }
        long t0 = System.nanoTime();
        int n = 0;
        String[] qs = {"estepa", "linkn park", "ado", "one x", "cancion 4999", "queen"};
        for (String q : qs) {
            n += find(big, q, o).size();
        }
        double ms = (System.nanoTime() - t0) / 1e6 / qs.length;
        System.out.println("búsqueda en 5.000 canciones: " + String.format(java.util.Locale.ROOT, "%.1f", ms) + " ms");
        ok(ms < 40, "5.000 canciones en menos de 40 ms (" + ms + ")");
        ok(n > 0, "encuentra en la biblioteca grande");
    }

    private static void jsonListTests() {
        java.util.List<PlaylistJson.Song> lib = new java.util.ArrayList<PlaylistJson.Song>();
        lib.add(new PlaylistJson.Song("Como Camarón", "Estopa", "Estopa", 215000, "Estopa - Como Camarón.mp3", "Rumba"));
        lib.add(new PlaylistJson.Song("Numb", "Linkin Park", "Meteora", 187000, "Linkin Park - Numb.mp3", "Rock"));
        lib.add(new PlaylistJson.Song("", "", "", 0, "Estopa - La Raja de tu Falda.mp3", "Rumba"));
        lib.add(new PlaylistJson.Song("Chop Suey!", "System of a Down", "Toxicity", 210000, "SOAD - Chop Suey.mp3", ""));
        lib.add(new PlaylistJson.Song("Numb", "Linkin Park", "Meteora", 187000, "copia.mp3", "Otra"));
        String js = PlaylistJson.exportLibrary(lib, "0.76", "2026-11-25");
        java.util.List<PlaylistJson.Playlist> back = null;
        try {
            PlaylistJson.parse(js);
            ok(false, "la biblioteca no se importa como lista");
        } catch (IllegalArgumentException e) {
            ok("library".equals(e.getMessage()), "aviso de que es la biblioteca");
        }
        ok(js.contains("\"title\": \"La Raja de tu Falda\"") && js.contains("\"artist\": \"Estopa\""),
                "sin etiquetas, título y artista salen del nombre del archivo");
        String id0 = PlaylistJson.id(lib.get(0));
        ok(id0.length() == 12, "id de 12 letras");
        ok(id0.equals(PlaylistJson.id(new PlaylistJson.Song("COMO CAMARON", "estopa", "", 215400, "x.mp3", "Otra"))),
                "el id no depende de la ruta, mayúsculas ni acentos");
        ok(js.contains(PlaylistJson.id(lib.get(1)) + "-2"), "id repetido lleva sufijo");
        String reply = "Aquí tienes:\n```json\n{\"format\":\"scratch-playlist\",\"version\":1,\"name\":\"Solo Estopa\","
                + "\"description\":\"Rumba \\\"catalana\\\" \\u00e9\",\"songs\":[{\"id\":\"" + id0 + "\"},"
                + "{\"id\":\"zzzzzzzzzzzz\",\"artist\":\"estopa\",\"title\":\"la raja de tu falda\"},"
                + "{\"id\":\"nope\",\"artist\":\"Estopa\",\"title\":\"Cacho a Cacho\"},"
                + "{\"id\":\"" + id0 + "\"}]}\n```\n";
        back = PlaylistJson.parse(reply);
        ok(back.size() == 1 && back.get(0).name.equals("Solo Estopa"), "lee la lista pegada del chat");
        ok(back.get(0).description.equals("Rumba \"catalana\" é"), "escapes: " + back.get(0).description);
        PlaylistJson.Match m = PlaylistJson.match(back.get(0), lib);
        ok(m.found.equals(Arrays.asList(0, 2)), "empareja por id y por artista+título, sin repetir: " + m.found);
        ok(m.missing.equals(Arrays.asList("Estopa - Cacho a Cacho")), "lo que falta: " + m.missing);
        java.util.List<PlaylistJson.Playlist> many = PlaylistJson.parse("{\"format\":\"scratch-playlist\",\"playlists\":["
                + "{\"name\":\"A\",\"songs\":[\"" + PlaylistJson.id(lib.get(3)) + "\"]},{\"name\":\"B\",\"songs\":[]}]}");
        ok(many.size() == 2 && PlaylistJson.match(many.get(0), lib).found.equals(Arrays.asList(3)), "varias listas e ids sueltos");
        ok(PlaylistJson.match(many.get(0), lib).missing.isEmpty(), "nada falta");
        String[] bad = {"", "{", "{\"a\":}", "[1,2", "\"abc", "{\"songs\":[{\"id\":\"\\u12\"}]}"};
        for (String b : bad) {
            try {
                PlaylistJson.parse(b);
                ok(false, "JSON roto aceptado: " + b);
            } catch (IllegalArgumentException e) {
                // bien
            }
        }
        StringBuilder deep = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            deep.append('[');
        }
        try {
            PlaylistJson.parse(deep.toString());
            ok(false, "anidado infinito");
        } catch (IllegalArgumentException e) {
            // bien: sin desbordar la pila
        }
    }

    private static void groupTests() {
        java.util.List<String> ar = Arrays.asList("Estopa", "estopa", "", "Linkin Park", "Varios", "");
        java.util.List<String> al = Arrays.asList("Estopa", "Destrangis", "", "Meteora", "Meteora", "");
        java.util.List<String> fi = Arrays.asList("a.mp3", "b.mp3", "Estopa - Tu Calorro.mp3", "c.mp3", "d.mp3", "suelta.mp3");
        java.util.List<Groups.Group> a = Groups.build(ar, al, fi, false);
        ok(a.size() == 4, "artistas: " + a.size());
        ok(a.get(0).name.equals("Estopa") && a.get(0).tracks.equals(Arrays.asList(0, 1, 2)),
                "Estopa junta mayúsculas y el nombre del archivo: " + a.get(0).tracks);
        ok(a.get(0).sub.equals("2"), "Estopa tiene 2 álbumes");
        ok(a.get(a.size() - 1).key.length() == 0 && a.get(a.size() - 1).tracks.equals(Arrays.asList(5)), "sin artista al final");
        java.util.List<Groups.Group> b = Groups.build(ar, al, fi, true);
        Groups.Group met = null;
        for (Groups.Group g : b) {
            if (g.name.equals("Meteora")) {
                met = g;
            }
        }
        ok(met != null && met.sub.equals("*") && met.tracks.size() == 2, "álbum con varios artistas");
        ok(b.get(0).name.equals("Destrangis") && b.get(0).sub.equals("estopa"), "álbumes en orden y su artista");
        ok(b.get(b.size() - 1).key.length() == 0 && b.get(b.size() - 1).tracks.size() == 2, "sin álbum al final");
    }

    private static void smartListTests() {
        long now = 1_000_000_000_000L;
        long d = SmartLists.DAY;
        long[] mod = {now - 2 * d, now - 40 * d, 0, now - 1 * d, now - 10 * d};
        long[] cnt = {0, 5, 3, 1, 0};
        long[] last = {0, now - 100 * d, now - 3 * d, now - 1 * d, 0};
        boolean[] fav = {false, true, true, false, true};
        ok(SmartLists.build(SmartLists.NEW, mod, cnt, last, fav, now).equals(Arrays.asList(3, 0, 4)), "nuevas, lo último primero");
        ok(SmartLists.build(SmartLists.UNPLAYED, mod, cnt, last, fav, now).equals(Arrays.asList(0, 4)), "sin escuchar");
        ok(SmartLists.build(SmartLists.WEEK, mod, cnt, last, fav, now).equals(Arrays.asList(2, 3)), "esta semana, más escuchadas primero");
        ok(SmartLists.build(SmartLists.REDISCOVER, mod, cnt, last, fav, now).equals(Arrays.asList(1)), "redescubre");
        ok(SmartLists.build(SmartLists.FORGOTTEN_FAVS, mod, cnt, last, fav, now).equals(Arrays.asList(1, 4)), "favoritas olvidadas");
    }

    private static void backupTests() throws Exception {
        File root = java.nio.file.Files.createTempDirectory("bk").toFile();
        File files = new File(root, "files");
        File prefs = new File(root, "shared_prefs");
        new File(files, "plcovers").mkdirs();
        prefs.mkdirs();
        java.nio.file.Files.write(new File(files, "favs.txt").toPath(), "a\nb\n".getBytes("UTF-8"));
        java.nio.file.Files.write(new File(files, "plcovers/x.png").toPath(), new byte[] {1, 2, 3});
        java.nio.file.Files.write(new File(files, "basura.tmp").toPath(), new byte[] {9});
        java.nio.file.Files.write(new File(prefs, "ui.xml").toPath(), "<map/>".getBytes("UTF-8"));
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        int n = Backup.write(bo, files, prefs, "0.77");
        ok(n == 3, "copia con 3 archivos (sin .tmp): " + n);
        // se pierde todo y se restaura en otra carpeta
        File root2 = java.nio.file.Files.createTempDirectory("bk2").toFile();
        File files2 = new File(root2, "files");
        File prefs2 = new File(root2, "shared_prefs");
        files2.mkdirs();
        java.nio.file.Files.write(new File(files2, "favs.txt").toPath(), "viejo".getBytes("UTF-8"));
        int r = Backup.restore(new java.io.ByteArrayInputStream(bo.toByteArray()), files2, prefs2);
        ok(r == 3, "restaura 3 archivos: " + r);
        ok(new String(java.nio.file.Files.readAllBytes(new File(files2, "favs.txt").toPath()), "UTF-8").equals("a\nb\n"),
                "favoritas restauradas");
        ok(new File(files2, "plcovers/x.png").length() == 3 && new File(prefs2, "ui.xml").exists(), "subcarpetas y ajustes");
        ok(!new File(root2, "restore.tmp").exists(), "sin restos temporales");
        // un zip que no es una copia no toca nada
        java.io.ByteArrayOutputStream ev = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(ev);
        z.putNextEntry(new java.util.zip.ZipEntry("files/favs.txt"));
        z.write("MAL".getBytes("UTF-8"));
        z.closeEntry();
        z.putNextEntry(new java.util.zip.ZipEntry("files/../../fuera.txt"));
        z.write(1);
        z.closeEntry();
        z.close();
        try {
            Backup.restore(new java.io.ByteArrayInputStream(ev.toByteArray()), files2, prefs2);
            ok(false, "un zip cualquiera no se restaura");
        } catch (java.io.IOException e) {
            ok(new String(java.nio.file.Files.readAllBytes(new File(files2, "favs.txt").toPath()), "UTF-8").equals("a\nb\n")
                    && !new File(root2.getParentFile(), "fuera.txt").exists(), "zip ajeno: nada cambia");
        }
    }

    /** Canción sintética: acordes de una tonalidad (con armónicos) durante 30 s. */
    private static PcmData chords(int[][] prog, int sr) {
        PcmData.Builder b = new PcmData.Builder();
        int per = sr * 2;
        for (int t = 0; t < sr * 30; t++) {
            int[] ch = prog[(t / per) % prog.length];
            double v = 0;
            for (int note : ch) {
                double f = 440 * Math.pow(2, (note - 69) / 12.0);
                for (int h = 1; h <= 3; h++) {
                    v += Math.sin(2 * Math.PI * f * h * t / sr) / (h * h);
                }
            }
            short x = (short) (v * 2500);
            b.append(x, x);
        }
        return b.build(sr);
    }

    private static void keyTests() {
        ok(KeyDetect.camelot(0, 0).equals("8B") && KeyDetect.camelot(12 + 9, 0).equals("8A"), "Camelot: C=8B, Am=8A");
        ok(KeyDetect.camelot(7, 0).equals("9B") && KeyDetect.camelot(12 + 4, 0).equals("9A"), "Camelot: G=9B, Em=9A");
        ok(KeyDetect.camelot(0, 2).equals("10B") && KeyDetect.name(0, 2).equals("D"), "con PITCH +2: C → D = 10B");
        // Do mayor: C - F - G - C
        double[] c = KeyDetect.detect(chords(new int[][] {{60, 64, 67}, {65, 69, 72}, {67, 71, 74}, {60, 64, 67}}, 44100));
        ok(c != null && (int) c[0] == 0, "Do mayor detectado: " + (c == null ? null : KeyDetect.name((int) c[0], 0)));
        // La menor: Am - Dm - E - Am
        double[] a = KeyDetect.detect(chords(new int[][] {{57, 60, 64}, {62, 65, 69}, {64, 68, 71}, {57, 60, 64}}, 44100));
        ok(a != null && (int) a[0] == 12 + 9, "La menor detectado: " + (a == null ? null : KeyDetect.name((int) a[0], 0)));
        // Sol mayor
        double[] g = KeyDetect.detect(chords(new int[][] {{67, 71, 74}, {60, 64, 67}, {62, 66, 69}, {67, 71, 74}}, 48000));
        ok(g != null && (int) g[0] == 7, "Sol mayor detectado: " + (g == null ? null : KeyDetect.name((int) g[0], 0)));
    }

    private static void automixTests() {
        // C(8B) con Am(8A): compatibles; C con G(9B): compatibles; C con F#(2B): lejos
        ok(Automix.keyDistance(0, 21) == 0 && Automix.keyDistance(0, 7) == 0 && Automix.keyDistance(0, 6) == 2, "distancia Camelot");
        ok(Automix.bpmDiff(70, 140) == 0 && Math.abs(Automix.bpmDiff(120, 126) - 5) < 1e-9, "tempo: doble y mitad cuentan");
        java.util.List<Automix.Item> it = new java.util.ArrayList<Automix.Item>();
        it.add(new Automix.Item(128, 0, 0.9));   // 0: C, fuerte
        it.add(new Automix.Item(100, 6, 0.2));   // 1: F#, tranquila → empieza aquí
        it.add(new Automix.Item(124, 7, 0.7));   // 2: G
        it.add(new Automix.Item(102, 1, 0.3));   // 3: C# (2B ... cerca de F# 2B? 3B) 
        java.util.List<Integer> o = Automix.order(it);
        ok(o.size() == 4 && o.get(0) == 1 && new java.util.HashSet<Integer>(o).size() == 4, "automix: empieza tranquilo y no repite: " + o);
        ok(o.indexOf(2) == o.indexOf(0) - 1 || o.indexOf(0) == o.indexOf(2) - 1, "automix: C y G juntas: " + o);
    }
}
