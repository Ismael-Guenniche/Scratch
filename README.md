# Scratch

Reproductor de música local para Android (MP3/WAV) con una rueda tipo *jog wheel* inspirada en el TP-7 de Teenage Engineering, y una interfaz estilo Nothing (puntos, cristal, acento naranja).

## Qué hace
- Reproduce los archivos de tu carpeta de música, sin cuentas ni streaming.
- Rueda táctil: gira para mover la pista, con frenado y arranque suaves.
- Listas, favoritos, recientes y más escuchadas; cola de reproducción.
- Importar listas de Spotify (CSV/M3U, API o sesión web experimental) y ver las canciones que faltan en tu biblioteca.
- Compatible con Android Auto (fuente de audio, siguiente/anterior).
- Icono de la app a elegir entre 8 variantes.

## Modo DJ (extra opcional)
Un modo extra para divertirse: filtro, ecualizador de 3 bandas con kill, eco, backspin y frenado configurable.

## Compilar
Abre la carpeta en Android Studio y ejecuta *Run*. Requiere JDK 21, AGP 8.10 y Android SDK 35 (minSdk 24).

## Documentación
- [Importar de Spotify](docs/IMPORTAR-SPOTIFY.md)
- [Android Auto](docs/ANDROID-AUTO.md)
