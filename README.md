# Scratch v0.2

Reproductor local con rueda de scratch (inspirado en la TP-7 de Teenage Engineering), estilo Nothing.

- Arrastra la rueda para hacer scratch; toca el centro para play/pausa.
- Barra de progreso para saltar a cualquier punto y deslizador de pitch de ±8 % (toca la etiqueta para volver a 0).
- `<<` y `>>` mantenidos: rebobinado y avance rápido con sonido.
- BIBLIOTECA: elige la carpeta con tu música (WAV, MP3, M4A, OGG, FLAC...). Se recuerda y se escanea una vez.
- Sigue sonando con la pantalla apagada; controles en la notificación y en los auriculares.

Compilar: abrir esta carpeta en Android Studio (Gradle) o ejecutar `build-sin-sdk/build.sh` (sin SDK, ver rutas dentro).
Pruebas del motor: `tools/Check.java` (Java normal, sin Android).

## Diseño Lucent (v0.3)

La interfaz sigue la guía "Lucent" de Nothing OS 5.0:

- Tarjetas de cristal (blanco 60 % en claro, negro 55 % en oscuro; sube a 72 % si el fondo de pantalla
  resta contraste) con borde fino y esquinas de 28 dp.
- Acento `#FB471F` (`#FF5A33` en oscuro) usado como relleno (pastilla activa, marcador, punto), nunca como
  texto sobre cristal claro; texto blanco sobre el acento solo en negrita de 14 sp o más.
- Fondo: pantalla de inicio con desenfoque de ventana en Android 12+ (16/28 dp) y velo si no hay desenfoque.
- Títulos y reloj en matriz de puntos 5x7 con puntos apagados "fantasma" (`DotFont`, `DotTextView`); los dos
  puntos del reloj llevan el acento.
- Tipografía Geist y Geist Mono (SIL OFL 1.1) en `app/src/main/assets/fonts/`.
- Ajustes (botón AJUSTES): Estilo Clásico/Lucent (Lucent por defecto en móviles Nothing), Modo Auto/Claro/Oscuro,
  Acento Nothing/Clásico/Fondo/Mono y Desenfoque No/Suave/Normal.

`tools/Check.java` incluye ahora pruebas de la fuente de puntos.

## v0.4: funciones tipo Spotify, ajustes de Dot Words, optimización

- **Cola**: mantén pulsada una canción en BIBLIOTECA → "Reproducir a continuación" / "Añadir a la cola" /
  favoritas. Pantalla COLA (toca = reproducir ya, mantén = quitar, VACIAR).
- **Shuffle** sin repeticiones (se recorre una permutación completa, `ShuffleOrder`) con historial real para `|<`.
- **Repetir**: sin repetir (al acabar la lista se para), repetir lista, repetir una (REPETIR 1).
- **Favoritas** (corazón) y filtro FAVORITAS en la biblioteca; se guardan en local.
- **Temporizador de sueño** (15/30/60/90 min) con fundido de salida.
- **Ajustes** con la misma estructura que Dot Words: Tema (Sistema/Claro/Oscuro), Colores (colores del fondo +
  acento: Nothing 5 y los de Dot Words), Tipografía (Dot / Mono con Space Mono / Clean con Geist) y Cristal.
- **Rendimiento**: búfer de decodificación dimensionado por la duración (sin copias al crecer), decodificación
  por bloques, soporte de decodificadores que devuelven PCM float, refresco de la rueda a 60 fps solo si suena
  o se toca (5 fps en reposo), nombres en minúsculas precalculados para el buscador.
- **Pruebas MP3**: `tools/Check.java <dir> <dir-con-test.pcm-y-mono.pcm>` valida la ruta posterior al
  decodificador (estéreo 44,1 kHz, mono 22 kHz, float). El decodificador MP3 en sí (MediaCodec) solo corre en Android.

## v0.5: controles minimalistas de puntos y fondo que lee tu fondo de pantalla

- Botones de transporte y modos (shuffle, repetir, favorita, cola, pitch, temporizador) son iconos de matriz de
  puntos (`DotIcons`, `DotIconView`). Biblioteca, abrir archivo y ajustes están en el menú de los tres puntos; el
  temporizador y el pitch son desplegables.
- El fondo es un degradado teñido con los colores de tu fondo de pantalla (casi opaco; algo menos si hay desenfoque).

## Instalar en el móvil

- **Sin Android Studio:** abre `Scratch.apk` en el móvil e instálalo (permite "instalar apps desconocidas").
- **Con Android Studio:** el proyecto usa la misma configuración de Gradle que Dot Words (AGP 8.10.1, Gradle 8.14.3,
  compileSdk 35). Abre esta carpeta, espera el sync, activa "Depuración inalámbrica" (o USB) en el móvil y pulsa Run.
