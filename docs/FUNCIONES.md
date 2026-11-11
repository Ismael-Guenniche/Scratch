# Funciones de Scratch

Guía pantalla por pantalla.

## Primer inicio (bienvenida)
Al abrir la app por primera vez (o si cambia la versión de la política) aparece `OnboardingActivity`:
1. **Privacidad:** se muestra la política completa; hay que pulsar «ACEPTO» para continuar («NO ACEPTO» cierra la app).
2. **Aspecto:** tema SISTEMA / CLARO / OSCURO y color de acento.
3. **Avisos:** permiso de notificaciones (Android 13+) para los controles de reproducción.
4. **Música:** elegir la carpeta con el selector del sistema (sin permisos de almacenamiento amplios) o hacerlo más tarde.
La política puede releerse en Ajustes → Permisos y privacidad ([PRIVACIDAD.md](PRIVACIDAD.md)).

## Inicio
Primera pantalla (`HomeView`).
- Tarjeta **REPRODUCIENDO** con lo último que sonaba; tócala para abrir la rueda.
- La tarjeta, las canciones escuchadas recientemente y tus listas llevan la **carátula del álbum** (las listas, su portada).
- Accesos a canciones, listas, favoritas, recientes, más escuchadas, estadísticas y ajustes.
- Etiqueta **BETA 0.xx** junto al título (útil para comprobar qué versión tienes instalada).

## Biblioteca (`MainActivity`)
- **Elegir carpeta** (SAF): se escanea recursivamente; el resultado se guarda en `library.txt` para arrancar al instante. **Rescanear** actualiza.
- Pestañas: **CANCIONES**, **CARPETAS** (una lista por carpeta), **FAVORITAS**, **RECIENTES**, **MÁS ESCUCHADAS** y tus listas.
- **Carpeta por lista:** al crear una lista (+ LISTA) puedes elegir su carpeta; sus canciones se suman a la biblioteca y a la lista. Desde el menú de la lista: cambiar carpeta, actualizar o desenlazar.
- **Listas:** play y aleatorio arriba de la lista (con aleatorio reproduce al azar, sin él de la primera a la última); ORDEN por título, artista, álbum o duración (se puede guardar como orden de la lista); barra lateral de scroll y número de posición. Botón de cola en la barra pequeña. El icono del pitch bloquea el control.
- **Precarga (Ajustes → PRECARGA):** las próximas N canciones (5 por defecto; 0 = solo la siguiente) se mantienen decodificadas en memoria. Al abrir una pista suena en cuanto se decodifican los primeros segundos y el resto se completa detrás; hasta entonces el scratch solo llega a ese tramo.
- Búsqueda por título o carpeta.
- Menú ⋮: abrir archivo suelto, rescanear, importar lista (CSV), importar de Spotify, ver faltantes, estadísticas, ajustes y **DIAGNÓSTICO** (informa de por qué una biblioteca sale vacía).
- **Big Picture** (`BigPictureActivity`, menú ⋮ del scratch): módulos en una rejilla de 4 columnas (`BigLayout`), configurables en Ajustes → BIG PICTURE (activar, tamaño, orden). Rueda con estilo PUNTOS o GRABADORA.
- **Letras** (`LyricsReader`, `LyricsStore`, `LyricsActivity`): lee la letra de las etiquetas del archivo (MP3 USLT/SYLT, FLAC/Ogg LYRICS, M4A ©lyr) y formato LRC; menú ⋮ → LETRAS; buscar en lrclib.net o importar un .lrc (se guarda en la app, no toca el archivo); módulo LETRA en Big Picture.
- **Grabación y M3U:** menú ⋮ del scratch → GRABAR SESIÓN / PARAR GRABACIÓN (WAV 16 bits en Música/Scratch); menú de cada lista → EXPORTAR COMO M3U (la importación de M3U ya existía en importar listas).
- **Sonido:** Ajustes → SONIDO: nivelar volumen con ReplayGain (solo baja, nunca sube) y fundido cruzado de 0 a 10 s (potencia constante; con la precarga activada y sin saltar a mano).
- **Android Auto (rama android-auto):** Artistas y Álbumes en el explorador, búsqueda por voz (`onPlayFromSearch`/`onSearch`), cola, salto de posición y botones de aleatorio/repetir. Ver [ANDROID-AUTO.md](ANDROID-AUTO.md).
- Vista previa de Android Auto: prueba la biblioteca, la búsqueda y los controles sin coche
- Vista previa de Android Auto: búsqueda local
- Acciones por canción: reproducir a continuación, añadir a la cola, añadir a favoritas, añadir a lista, quitar de lista, estadísticas de la canción.
- Barra flotante con la canción actual; tocarla abre la rueda.
- **Shuffle** sin repeticiones (Fisher-Yates): no repite hasta agotar la lista.
- **Temporizador de apagado** (15/30/60/90 min).

## Rueda (`ScratchActivity`)
- **Disco de puntos** concéntrico que gira con la canción. Arrástralo para hacer scratch; el centro hace play/pausa.
- Tiempo grande en puntos, barra de progreso, nombre y metadatos.
- Transporte: anterior, retroceso rápido, play/pausa, avance rápido, siguiente. **Mantener** retroceso/avance mueve a 1,5× con audio; deslizar el dedo ajusta la velocidad (0,5×–4×) con un globo con barra.
- Chips: **modo** (estándar/DJ), **aleatorio**, **scratch/bloqueada** (la rueda solo gira), **portada/puntos**.
- Modo estándar: volumen del sistema y velocidad de reproducción (±20 %).
- **Portada** incrustada del archivo en lugar del plato de puntos (opcional). Con **scratch bloqueado** la portada se muestra **cuadrada y sin girar**.
- Vibración suave cada pocos grados al girar.
- Giro automático vertical/horizontal.
- Ver [MODO-DJ.md](MODO-DJ.md) para el panel DJ.

## Cola (`QueueActivity`)
Lo que suena y lo que viene después; quitar canciones de la cola.

## Listas
- Listas propias (`playlists.txt`): crear, añadir, quitar, borrar, **renombrar**.
- **Portada de lista:** por defecto un mosaico 2x2 con las carátulas de sus primeras canciones (4 distintas; con menos, solo la primera). Con **EDITAR** (cabecera de la lista) o manteniendo pulsado el chip de la lista se elige una imagen propia (`plcovers/`, cuadrada y reducida a 512 px) o se vuelve al mosaico.
- **Orden:** dentro de una lista, mantén pulsada una canción → SUBIR / BAJAR UN PUESTO o MOVER A LA POSICIÓN…. Con la búsqueda activa no se ofrece mover.
- **Editar nombre:** mantén pulsada cualquier canción → EDITAR NOMBRE… cambia el título y el artista que se muestran (también en la notificación). Se guarda en `edits.txt` y no toca el archivo de audio; RESTAURAR ORIGINAL lo deshace. La importación de Spotify sigue emparejando con las etiquetas originales.
- **Favoritas** (`favs.txt`).
- **Recientes** y **Más escuchadas** (`plays.txt`, `history.txt`).

## Importar de Spotify
Tres vías (detalle en [IMPORTAR-SPOTIFY.md](IMPORTAR-SPOTIFY.md)):
1. CSV de Exportify.
2. API de Spotify con tu propio Client ID (OAuth PKCE, solo lectura).
3. Sesión web (experimental, no necesita Premium).

Cada canción se empareja con tus archivos por título + artista sin acentos, ignorando «(feat…)» y «Remastered». Las que no se encuentran aparecen en **FALTANTES**, con botón de copiar y búsqueda en Bandcamp/Qobuz/YouTube Music **solo como enlace de búsqueda** (la app no descarga nada).

## Estadísticas y Wrapped
- **Estadísticas:** tiempo escuchado, reproducciones, rankings de canciones/artistas/álbumes, horas del día, por periodo.
- **Wrapped:** páginas a pantalla completa (toca izquierda/derecha), progreso con puntos e histograma de 24 columnas.
- **Por canción:** menú de la canción → estadísticas.
- Una reproducción cuenta cuando se escucha lo suficiente (30 s, o la mitad de la duración si la canción es más corta).

## Android Auto y sistema
- Servicio en primer plano con notificación persistente (con carátula), pantalla de bloqueo y botones de auricular.
- Pausa al desconectar auriculares; gestiona el foco de audio.
- Fuente de audio para Android Auto: ver [ANDROID-AUTO.md](ANDROID-AUTO.md).

## Ajustes
Ver [AJUSTES.md](AJUSTES.md).
