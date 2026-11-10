package dev.scratchwheel;

import java.io.File;
import java.util.Arrays;

/**
 * Pruebas de las listas (renombrar, mover, portada) y de los nombres editados. Solo Java puro:
 * compila con {@code javac -d /tmp/cl app/src/main/java/dev/scratchwheel/Playlists.java
 * app/src/main/java/dev/scratchwheel/TrackEdits.java tools/CheckLists.java} y ejecuta
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

        System.out.println(fails == 0 ? "LISTAS OK" : fails + " FALLOS");
        System.exit(fails == 0 ? 0 : 1);
    }
}
