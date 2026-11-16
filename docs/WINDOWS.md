# Scratch para Windows

La versión de escritorio es **la misma app**: las mismas pantallas, el mismo motor de audio y los mismos ajustes que en Android. No se ha reescrito nada; el código de `app/src/main/java` se compila tal cual junto a una capa (`desktop/src/shim`) que hace de «Android» sobre Java de escritorio. Lo que se añada a la app llega al PC al volver a compilar.

> Estado: **primera versión (beta)**. Se ha probado en Linux con capturas automáticas y la compilación de GitHub hace una prueba de humo en Windows, pero falta rodaje a mano en un PC con Windows.

## Instalar
1. En GitHub, pestaña **Actions → Windows → última ejecución → Artifacts → `Scratch-Windows`** (o la Release, si la versión tiene etiqueta).
2. Dentro hay dos opciones:
   - `Scratch-x.y.exe`: instalador. No pide permisos de administrador, crea el acceso directo del **escritorio** y el del **menú Inicio**; desde ahí se puede anclar a la barra de tareas.
   - `Scratch-x.y-windows-portable.zip`: descomprimir y abrir `Scratch\Scratch.exe`, sin instalar.
3. No hace falta instalar Java: va dentro (reducido a lo que usa la app).

Windows puede avisar con SmartScreen porque el instalador no está firmado: «Más información → Ejecutar de todas formas».

## Cómo es en el PC
- **Dos paneles:** la biblioteca (inicio, canciones, listas, ajustes, estadísticas, cola, letras) a la izquierda y el reproductor con la rueda a la derecha, siempre visible.
- **Modo DJ:** el panel del reproductor se ensancha a dos columnas (rueda y transporte a un lado, la mesa con scroll al otro).
- **Big Picture, Wrapped, el editor de Big Picture y la bienvenida** ocupan toda la ventana.
- **Ratón:** arrastrar la rueda hace scratch; **clic derecho = pulsación larga** (menú de la canción, de la lista…); la rueda del ratón desplaza (con Mayús, en horizontal); las listas tienen barra lateral arrastrable.
- **Teclado:**

| Tecla | Acción |
|---|---|
| Espacio | Play / pausa |
| ← / → | Retroceder / avanzar 5 s |
| Ctrl + ← / → | Anterior / siguiente |
| ↑ / ↓ | Volumen de la app |
| Esc o Alt + ← | Atrás (cierra menús y diálogos) |
| Ctrl + `+` / `-` / `0` | Tamaño de la interfaz |
| Teclas multimedia | Play/pausa, siguiente, anterior, stop (con la ventana activa) |

- El tema SISTEMA sigue el modo claro/oscuro de Windows y «Colores del fondo» usa el color de acento de Windows.
- Ajustes → ICONO cambia el icono de la ventana (el del acceso directo es fijo).
- El título de la ventana muestra lo que suena.
- Solo se abre una instancia: abrir Scratch otra vez trae la ventana al frente.

## Formatos de audio
| Formato | Cómo se decodifica |
|---|---|
| MP3 | JLayer (incluido), sin el relleno del codificador (gapless) |
| FLAC | decodificador propio (`desk/Flac.java`), bit a bit igual que ffmpeg |
| WAV | el lector de la app (`WavParser`) |
| M4A/AAC, OGG, Opus, WMA | con **ffmpeg**, si está instalado (en el `PATH` o `ffmpeg.exe` junto a `Scratch.exe`) |

Las etiquetas, la duración y la carátula se leen sin ffmpeg en todos los formatos (ID3v2/v1, FLAC, Ogg, MP4, WAV).

## Dónde guarda las cosas
| Qué | Dónde |
|---|---|
| Biblioteca, listas, historial, ajustes | `%APPDATA%\Scratch\` |
| Cachés (carátulas, forma de onda, audio decodificado) | `%LOCALAPPDATA%\Scratch\cache\` |
| Grabaciones de sesión | `Música\Scratch\` |

Los datos del móvil y los del PC son independientes (las rutas de los archivos no coinciden).

## Diferencias con Android
- **Sin Android Auto**, notificación ni pantalla de bloqueo (no existen en el PC).
- **Importar de Spotify:** funcionan el CSV de Exportify y la API con tu Client ID (el instalador registra `scratchwheel://` para volver del navegador). La **sesión web** no existe en PC: necesita un navegador incrustado.
- **Latencia:** la salida usa Java Sound (DirectSound). El búfer empieza en 40 ms y crece solo si hay cortes; el scratch responde bien, pero no al nivel del móvil con ruta rápida. Se puede probar otro mínimo con `-Dscratch.minbuf=25`.
- **Teclas multimedia** solo con la ventana activa (sin atajos globales).
- El volumen del reproductor es el de la app, no el del sistema.
- Sin vibración.

## Compilar
Requisitos: JDK 17 o superior. Sin Gradle ni Android SDK.

```bash
desktop/build.sh          # genera desktop/build/Scratch.jar
desktop/build.sh run      # compila y ejecuta
java -jar desktop/build/Scratch.jar --selftest captura.png   # prueba de humo
```

En Windows, el instalador y la carpeta portable (hace falta WiX Toolset 3):

```powershell
bash desktop/build.sh
desktop\packaging\empaquetar.ps1      # deja el .exe y el .zip en desktop\build\dist
```

GitHub lo hace solo en cada cambio (`.github/workflows/windows.yml`): compila, pasa la prueba de humo en Windows y sube el instalador como artefacto; con una etiqueta `vX.Y` lo adjunta a la Release.

## Cómo está hecho
```
desktop/src/shim/android/…   las clases de Android que usa la app (View, TextView, LinearLayout, ListView,
                             Canvas, Paint, Activity, Dialog, SharedPreferences, AudioTrack…) sobre Java2D/Swing
desktop/src/shim/desk/…      el «sistema»: ventana y paneles (Shell, Pane), capas (diálogos, menús, avisos),
                             selectores de archivos, etiquetas (Tags), FLAC, MP3, ffmpeg, instancia única
desktop/src/shim/org/json/…  JSON mínimo (en Android viene con el sistema)
desktop/src/app/…            lo poco propio del escritorio: DesktopMain y las versiones de AudioLoader,
                             PlayerService y SpotifyWebActivity
desktop/third_party/jlayer   decodificador de MP3 (LGPL 2.1, ver LICENSE.txt)
desktop/packaging            icono y script del instalador
```

- **Una ventana = paneles con pilas de actividades.** Cada `Activity` se abre en el panel que le toca (`DesktopMain.paneFor`). Los diálogos, menús y avisos son capas dentro del panel.
- **Todo se dibuja con Java2D** en un único componente por panel; los campos de texto con foco son un `JTextField` superpuesto (cursor, selección, portapapeles, IME).
- **Los archivos son URIs `file:///` con la forma de los documentos de Android** (`C:Users/yo/Music/a.mp3` en un solo segmento), así el código que saca la carpeta de la URI funciona igual.
- `Build.VERSION.SDK_INT` vale 28 para que la app evite rutas que no existen en un PC (MediaStore, permiso de notificaciones).
- Si la app empieza a usar una clase o un método de Android que la capa no tiene, la compilación falla diciendo cuál: se añade en `desktop/src/shim`.

## Pendiente
- Probar a mano en Windows (latencia real, escalado en pantallas 125–150 %, instalador).
- Salida WASAPI de baja latencia.
- Atajos multimedia globales y controles en la miniatura de la barra de tareas.
- Decodificador propio de AAC/Ogg para no depender de ffmpeg.
- Arrastrar archivos a la ventana.
