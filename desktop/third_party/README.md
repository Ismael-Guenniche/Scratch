# Código de terceros

## jlayer/
Decodificador de MP3 **JLayer** (JavaZOOM), del fork mantenido en https://github.com/umjammer/jlayer
(commit `fe05c5f`, 2026-09-27). Licencia **LGPL 2.1** (ver `jlayer/LICENSE.txt`).

Solo se incluye el paquete `javazoom.jl.decoder` (el decodificador y sus tablas `.ser`). Único cambio respecto
al original: se ha quitado de `Decoder.java` el método de conveniencia `convertToWav`, que dependía del
paquete `converter` (no incluido).
