# Arquitectura

Java, sin librerías externas. Paquete `dev.scratchwheel`. Las vistas se construyen **por código** (no hay XML de layouts), y los recursos se consultan con `getIdentifier` para poder compilar también sin SDK.

## Capas

```
Pantallas        MainActivity · ScratchActivity · SettingsActivity · StatsActivity
                 WrappedActivity · QueueActivity · SpotifyWebActivity
Vistas propias   JogWheelView · FaderView · DotMeterView · HomeView · DotTextView
                 DotIconView · DotGrid · SeekPopup · DotMenu · DotDialog
Estado global    Player (singleton)  ──  PlayerService (primer plano / Android Auto)
Motor            ScratchEngine · AudioLoader · WavParser · PcmData
Datos            Library · Playlists · Meta · PlayLog · Imports · Stats
Importación      SpotifyImport · SpotifyApi
Tema             Lucent · DotFont · DotIcons
```

## Player: el corazón
`Player.I` es un singleton que **vive mientras viva el proceso**, así la música sigue aunque se cierren las pantallas. Todos sus métodos públicos se llaman desde el hilo principal. Se encarga de:
- cola, orden aleatorio (`ShuffleOrder`), índice, favoritas y recientes;
- carga de pistas en hilos aparte (`AudioLoader`) con *generaciones* para descartar cargas obsoletas;
- foco de audio, auriculares (`ACTION_AUDIO_BECOMING_NOISY`), `WakeLock`, temporizador de apagado;
- `MediaSession` (notificación, pantalla de bloqueo, Android Auto);
- hilo de audio (`ensureAudio` / `audioLoop`);
- registro de escuchas (`PlayLog`) y contadores (`Meta`);
- aplicar los ajustes de la rueda (`applyWheelPrefs`).

Las pantallas se suscriben con `Player.Listener.onChanged()`.

## Hilos
| Hilo | Qué hace |
|---|---|
| Principal | UI, `Player`, entrada táctil de la rueda |
| `audio` (prioridad URGENT_AUDIO) | `engine.render()` → `AudioTrack.write()` |
| Carga | decodificar una pista, leer carátulas, escanear la biblioteca |
| `player-io` (HandlerThread) | foco de audio, sesión, servicio, guardado de posición |

`ScratchEngine` es `synchronized`: la UI le manda órdenes y el hilo de audio renderiza con el mismo cerrojo.

## Flujo de la rueda
```
JogWheelView (toque) → ScratchActivity → Player.touchDown/rotate/touchUp
                    → ScratchEngine.scratchTarget → render() persigue el objetivo
ticker (vsync mientras suena) → engine.getPositionSeconds() → wheel.setAngle()
```
El ticker va a un cuadro por vsync mientras suena, se toca la rueda o el plato aún se mueve; en reposo, 5 fps.

## Datos en disco (`filesDir`)
| Archivo | Contenido |
|---|---|
| `library.txt` | caché de la lista de canciones escaneadas |
| `playlists.txt` | listas propias: nombre + URIs |
| `favs.txt` | favoritas |
| `meta.txt` | caché de etiquetas (título, artista, álbum) |
| `plays.txt` | contador de reproducciones por canción |
| `history.txt` | una línea por sesión de escucha: `ms \t uri \t segundos` |
| `imports.txt` | listas importadas, para reemparejar al rescanear |

Las preferencias van en `SharedPreferences` (claves en [AJUSTES.md](AJUSTES.md)).

## Estadísticas
`Stats` es lógica pura (sin Android) sobre el historial: agregados por canción/artista/álbum, horas del día y el resumen de *Wrapped*. Una escucha cuenta tras 30 s (o la mitad de la duración si es más corta).

## Tema
`Lucent.load(ctx)` lee las preferencias y construye los *tokens* (colores, radios, tipografías). Si cambian, `Lucent.stamp` sube y las pantallas abiertas se recrean en `onResume`. Ver [ESTILO.md](ESTILO.md).

## Android Auto
`PlayerService` extiende `MediaBrowserService`: expone Canciones, Favoritas, Recientes, Artistas, Álbumes y las listas como árbol (con búsqueda) de medios y delega los controles en `Player`.

## Giro de pantalla
`ScratchActivity` declara `screenOrientation="fullUser"` y gestiona `configChanges`: al girar monta de nuevo la interfaz con el diseño vertical u horizontal sin recrear la actividad (la música y los efectos no se interrumpen, y los faders conservan su valor).
