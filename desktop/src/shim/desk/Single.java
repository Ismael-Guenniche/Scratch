package desk;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Una sola instancia: la primera escucha en un puerto local (guardado en un archivo); las siguientes le
 * pasan sus argumentos (p. ej. el enlace scratchwheel://… de vuelta de Spotify) y terminan.
 */
public final class Single {
    public interface Receiver {
        /** Otra instancia se abrió con estos argumentos (puede venir vacío: solo traer al frente). */
        void onArgs(String[] args);
    }

    private Single() {
    }

    private static File portFile() {
        Rt.initDirs();
        return new File(Rt.filesDir.getParentFile(), "instance.port");
    }

    /** Devuelve true si ya había otra instancia y se le pasaron los argumentos (hay que salir). */
    public static boolean forward(String[] args) {
        File f = portFile();
        if (!f.exists()) {
            return false;
        }
        try {
            String[] parts = new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    .trim().split(" ");
            int port = Integer.parseInt(parts[0]);
            String key = parts.length > 1 ? parts[1] : "";
            Socket s = new Socket();
            s.connect(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), port), 400);
            try {
                s.setSoTimeout(1500);
                Writer w = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                w.write("SCRATCH " + key + "\n");
                for (String a : args) {
                    w.write(a.replace("\n", " ") + "\n");
                }
                w.write("\n");
                w.flush();
                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(),
                        StandardCharsets.UTF_8));
                return "OK".equals(r.readLine());
            } finally {
                s.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static void listen(final Receiver rec) {
        try {
            final ServerSocket ss = new ServerSocket(0, 4, InetAddress.getLoopbackAddress());
            final String key = Long.toHexString(new java.security.SecureRandom().nextLong());
            File f = portFile();
            java.nio.file.Files.write(f.toPath(), (ss.getLocalPort() + " " + key).getBytes(StandardCharsets.UTF_8));
            f.deleteOnExit();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        try {
                            Socket s = ss.accept();
                            try {
                                s.setSoTimeout(1500);
                                BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream(),
                                        StandardCharsets.UTF_8));
                                String hello = r.readLine();
                                if (!("SCRATCH " + key).equals(hello)) {
                                    continue;
                                }
                                java.util.ArrayList<String> args = new java.util.ArrayList<String>();
                                String l;
                                while ((l = r.readLine()) != null && l.length() > 0 && args.size() < 16) {
                                    args.add(l);
                                }
                                Writer w = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8);
                                w.write("OK\n");
                                w.flush();
                                rec.onArgs(args.toArray(new String[0]));
                            } finally {
                                s.close();
                            }
                        } catch (Exception e) {
                            if (ss.isClosed()) {
                                return;
                            }
                        }
                    }
                }
            }, "single-instance");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            Rt.log(e);
        }
    }
}
