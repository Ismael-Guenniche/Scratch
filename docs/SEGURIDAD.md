# Auditoría de seguridad (0.34)

Revisión estática del manifiesto y del código + fuzzing de parsers (WAV, CSV/M3U, motor y detector de BPM) con 60 000+ entradas mutadas.

## Corregido
1. **PlayerService exportado**: cualquier app podía mandarle PLAY/NEXT/PREV/STOP y explorar la biblioteca. Ahora las acciones exigen un secreto por instalación (los botones de la notificación lo llevan) y `onGetRoot` solo acepta la propia app, el sistema, Android Auto, Asistente y Wear.
2. **WAV con muchos canales** (cabecera maliciosa): reservaba ~1 GB y daba OutOfMemory. Ahora se rechaza más de 16 canales (el fuzzing lo encontró 147 veces de 30 000).
3. **WebView de Spotify**: `host.endsWith("spotify.com")` aceptaba `evilspotify.com`; ahora solo `spotify.com` y `*.spotify.com`. Acceso a ficheros/contenido del WebView desactivado.
4. **Login OAuth**: el `state` vale una sola vez; el texto de `error` del deep link se recorta.

## Revisado y correcto
- `allowBackup=false`; actividades internas `exported=false`; PKCE con `state` comprobado; tokens en SharedPreferences privadas; PendingIntents `FLAG_IMMUTABLE`.
- Fuzzing de CSV/M3U (incl. 200 000 filas, 96 ms), motor y BPM con audio de 1 muestra, silencio, ruido y saturación: sin excepciones.

## Pendiente / bajo riesgo
- El token de refresco de Spotify está en claro en SharedPreferences (solo legible con root); se podría cifrar con Android Keystore.
- Los decodificadores nativos de audio del sistema (MediaCodec/MP3) no se pueden fuzzear aquí; dependen de los parches de Android.
- `INTERNET` solo se usa para Spotify; si nunca lo usas, se puede quitar del flavor `play`.
