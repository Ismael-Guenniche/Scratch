# Listas con Claude (solo sabor `personal`)

Claude crea listas a partir de tu biblioteca local («solo Estopa», «canciones pesadas tipo Linkin Park»…) sin que la app se conecte a nada: todo va en archivos.

## Cómo se usa
1. **Ajustes → LISTAS CON CLAUDE → Exportar la biblioteca.** Guarda `biblioteca-scratch.json`.
2. Mándale ese archivo a Claude con lo que quieras: «hazme una lista solo con Estopa», «canciones pesadas tipo Linkin Park, unas 40», «para conducir de noche»…
3. Claude responde con un `playlist.json` (también vale el texto copiado del chat, aunque venga dentro de un bloque ```json).
4. **Ajustes → LISTAS CON CLAUDE → Importar una lista de Claude** y elige el archivo (se pueden elegir varios). La lista aparece en LISTAS; si ya hay una con ese nombre, se llama «Nombre (2)». Al final sale un resumen con las canciones que no se encontraron.

## biblioteca-scratch.json
```json
{
  "format": "scratch-library", "version": 1, "app": "Scratch 0.76-personal",
  "exported": "2026-11-25", "count": 3000,
  "instructions": "… formato de respuesta …",
  "songs": [
    {"id": "0k3x9a1b2c3d", "title": "Como Camarón", "artist": "Estopa", "album": "Estopa",
     "durationSec": 215, "file": "Estopa - Como Camarón.mp3", "folder": "Música"}
  ]
}
```
- **id**: 12 letras/cifras a partir de artista + título (normalizados: sin mayúsculas, acentos ni signos) + duración redondeada a 2 s. No depende de la ruta, así que mover la carpeta no rompe nada. Si dos canciones dan el mismo id, la segunda lleva `-2`, la tercera `-3`…
- Si el archivo no tiene etiquetas, título y artista salen del nombre «Artista - Título.mp3».
- El género y el año no se incluyen (habría que abrir cada archivo y tardaría minutos con miles de canciones): Claude se guía por artista, título, álbum y carpeta.

## playlist.json (lo que devuelve Claude)
```json
{"format": "scratch-playlist", "version": 1, "name": "Solo Estopa",
 "description": "Rumba catalana de los hermanos Muñoz",
 "songs": [{"id": "0k3x9a1b2c3d", "artist": "Estopa", "title": "Como Camarón"}]}
```
- Varias listas a la vez: `{"format": "scratch-playlist", "version": 1, "playlists": [{…}, {…}]}`.
- `songs` también acepta solo los id: `"songs": ["0k3x9a1b2c3d", …]`.
- Nombre de hasta 40 letras.

## Cómo se emparejan las canciones
1. Por **id**.
2. Si no coincide: por **artista + título** normalizados.
3. Si tampoco: por **título** solo cuando hay una única canción con ese título.
Las repetidas se añaden una sola vez. Lo que no se encuentra sale en el resumen.

Código: `PlaylistJson.java` (sin Android, con su propio lector de JSON), interfaz en `SettingsActivity`. Tests en `tools/CheckLists.java`.
