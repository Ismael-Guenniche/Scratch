# Importar listas de Spotify en Scratch

1. Entra en https://exportify.app (o exportify.net), inicia sesión con Spotify (solo lectura) y descarga el CSV de cada lista.
2. Pasa los CSV al móvil.
3. En Scratch: menú ⋮ → IMPORTAR LISTA (CSV). Puedes elegir varios a la vez.
4. La app empareja cada canción con tus archivos locales (título + artista, sin acentos, ignorando "(feat…)" y "Remastered"), crea una lista con el nombre del CSV y te muestra las que no encontró (botón COPIAR FALTANTES).

Las faltantes hay que conseguirlas por vías legales (compra en Bandcamp/Qobuz/Amazon, CD propios, etc.). Scratch no descarga desde YouTube Music ni Apple Music: sus condiciones y el DRM (FairPlay) lo prohíben.

La API de Spotify no se usa: desde 2026 exige Premium para apps en modo desarrollo, limita a 5 usuarios y no permite descargar audio.

## Sincronización directa (sin CSV)
1. developer.spotify.com/dashboard → Create app. Redirect URI: `scratchwheel://spotify`. En User Management añade tu email.
2. En Scratch: menú ⋮ → SINCRONIZAR SPOTIFY → pega el Client ID → inicia sesión en el navegador.
3. Elige las listas. Se importan solo las listas tuyas o colaborativas (limitación de Spotify desde 2026; además exige Premium para apps en modo desarrollo).
Vuelve a pulsar SINCRONIZAR SPOTIFY cuando cambies la lista; al rescanear la carpeta se reempareja solo.
