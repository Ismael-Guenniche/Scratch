# Historial de versiones

Resumen a partir de los commits (más reciente primero).

- 0.36: cada lista puede tener su propia carpeta (al crearla, o desde su menú: enlazar, actualizar, desenlazar); la carátula de la canción aparece arriba a la derecha de la tarjeta del título en el reproductor
- 0.35: carátulas del álbum primero en las listas, la cola, el inicio y la barra pequeña (miniaturas con caché en memoria y disco); portada de cada lista con mosaico 2x2 de 4 carátulas distintas, o una imagen propia; renombrar listas, mover canciones dentro de una lista (subir, bajar, a una posición) y editar el título y el artista de una canción sin tocar el archivo
- 0.34: auditoría de seguridad (ver docs/SEGURIDAD.md): servicio exportado protegido, WAV malicioso, host de Spotify, state de login de un solo uso
- 0.33: etiquetas de los botones del reproductor en dos líneas (kana arriba, inglés abajo)
- 0.32: el kana ya no se dibuja con puntos (ilegible), usa fuente normal con el inglés en puntos; textos de dos líneas ajustados en faders, pads, chips y botones
- 0.31: BPM y sincronía en el modo DJ (detección de tempo, pulsos, SYNC, ½/×2); corrige build.gradle (flavorDimensions fuera de defaultConfig)
- 0.30: reproducción sin huecos (precarga y empalme sample a sample de la siguiente pista); ecualizador de 5 bandas con presets en Ajustes; más kana en el japonés experimental y fuente de puntos con kana (rasterizado) en dos líneas
- 0.29: app en inglés por defecto y español opcional; el idioma es lo primero que se pregunta al abrir; sabores `play` / `personal` (el japonés experimental en kana solo existe en `personal`)
- 0.28: bienvenida con política de privacidad, ajustes básicos y permisos; targetSdk 36 (servicio multimedia, notificaciones, gesto atrás); barra de scroll en modo DJ; portada cuadrada con scratch bloqueado
- Tutorial para publicar en Google Play y .gitignore de claves de firma
- Documentación completa: README, funciones, modo DJ, motor de audio, arquitectura, ajustes, estilo, compilar y cambios
- 0.27: numeración en faders DJ; hot cues, bucle/roll, slip, saltos, flanger/phaser/trans, echo out, vinyl stop, sampler, cut, reverse, vúmetro, modo disco, giro automático; interruptores en Ajustes
- 0.26: inercia al soltar la rueda con fricción ajustable
- 0.25: mantener el tono (keylock granular), sensibilidad, invertir sentido y vibración de la rueda
- 0.24: etiqueta BETA con la versión junto al título
- 0.23: icono adaptable con capa monocroma, DotTextView respeta padding, botones con hitbox enorme
- 0.22: estilo Dot Calendar (píldoras, radios grandes, chips tinta, cabecera y home)
- 0.21: rejilla de puntos de fondo, scroll estable en Ajustes y separación en Tipografía
- 0.20: botones de la rueda más fáciles de pulsar
- 0.19: estadísticas, Wrapped, estilo y nuevos iconos
- 0.18: modo DJ compacto con faders y rueda de tamaño fijo, notificación persistente con carátula, globo legible
- 0.17: la notificación se queda al pausar y el globo de velocidad se ve bien
- 0.16: avance/retroceso a 1,5x y globo con barra de velocidad al mantener pulsado
- 0.15: pantalla de inicio con recientes, listas y accesos
- 0.14: diagnóstico de la biblioteca (menú DIAGNÓSTICO) y escaneo a prueba de fallos
- README: describe el reproductor y deja el modo DJ como extra
- Añade el código fuente de Scratch 0.13
- Scratch 0.13: reproductor con rueda tipo TP-7, importación de Spotify, Android Auto y modo DJ
