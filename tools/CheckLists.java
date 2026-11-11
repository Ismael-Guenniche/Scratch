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
}
