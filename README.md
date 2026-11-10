# Scratch

Reproductor de música **local** para Android (MP3, WAV y los formatos que decodifica el sistema) con una **rueda tipo jog wheel** inspirada en el TP-7 de Teenage Engineering y una interfaz estilo **Nothing OS / Lucent**: matriz de puntos, cristal y un único acento naranja (`#FB471F`).

Sin cuentas, sin streaming, sin anuncios. Tu música, tu carpeta, tu rueda.

> **Versión actual: 0.35 (beta).** La lógica del motor de audio se prueba con tests sin Android (`tools/Check.java`); la interfaz se ha ido probando en un Nothing Phone (4a).

## Lo esencial

| | |
|---|---|
| **Rueda de scratch** | Arrastra el disco de puntos para mover la pista como un vinilo: el tono sube y baja con la velocidad, suena al revés si giras hacia atrás y se calla si paras el dedo. Arranque y frenado de plato, inercia al soltar. |
| **Biblioteca local** | Elige una carpeta y Scratch la escanea (recursivo, con caché). Listas por carpeta, favoritas, recientes, más escuchadas, cola. |
| **Listas propias e importación** | Crea listas, importa listas de Spotify (CSV de Exportify, API propia o sesión web) y empareja con tus archivos; ve y copia las canciones que faltan. |
| **Estadísticas y Wrapped** | Tiempo escuchado, rankings de canciones/artistas/álbumes, horas del día y un resumen tipo *Wrapped* a pantalla completa. |
| **Android Auto** | Aparece como fuente de audio con explorador y controles. |
| **Modo DJ (extra)** | Gimmicks de mesa: filtro, EQ de 3 bandas, eco, pitch, hot cues, bucles y roll, slip, efectos, sampler, vúmetro y modo disco. Todo se puede apagar en Ajustes. |
| **Personalización** | Tema claro/oscuro/sistema, 8 colores de acento, colores del fondo de pantalla, 3 tipografías, estilo Lucent, rejilla de puntos, 8 iconos de app. |
| **Giro automático** | La pantalla del reproductor sigue la rotación del sistema (vertical y horizontal). |

## Documentación

| Documento | Contenido |
|---|---|
| [docs/FUNCIONES.md](docs/FUNCIONES.md) | Todas las funciones, pantalla por pantalla |
| [docs/MODO-DJ.md](docs/MODO-DJ.md) | Cada control del modo DJ y cómo funciona por dentro |
| [docs/MOTOR-DE-AUDIO.md](docs/MOTOR-DE-AUDIO.md) | Cómo suena la rueda: remuestreo, keylock, inercia, efectos |
| [docs/ARQUITECTURA.md](docs/ARQUITECTURA.md) | Clases, hilos, ficheros de datos y flujo de la música |
| [docs/AJUSTES.md](docs/AJUSTES.md) | Todas las opciones y sus claves de preferencias |
| [docs/ESTILO.md](docs/ESTILO.md) | Lenguaje visual: puntos, cristal, acento, componentes |
| [docs/COMPILAR.md](docs/COMPILAR.md) | Compilar con Android Studio, sin SDK y ejecutar los tests |
| [docs/IMPORTAR-SPOTIFY.md](docs/IMPORTAR-SPOTIFY.md) | Importar listas de Spotify |
| [docs/ANDROID-AUTO.md](docs/ANDROID-AUTO.md) | Activar Android Auto |
| [docs/PUBLICAR-PLAY-STORE.md](docs/PUBLICAR-PLAY-STORE.md) | Tutorial para publicar en Google Play |
| [docs/PRIVACIDAD.md](docs/PRIVACIDAD.md) | Política de privacidad |
| [docs/CAMBIOS.md](docs/CAMBIOS.md) | Historial de versiones |

## Compilar en 30 segundos

1. Abre la carpeta en **Android Studio** (JDK 17+, AGP 8.10, SDK 35).
2. Conecta el móvil (minSdk 24) y pulsa *Run*.
3. Opcional: `javac` + `tools/Check.java` para los tests del motor (ver [COMPILAR](docs/COMPILAR.md)).

## Estructura

```
app/src/main/java/dev/scratchwheel/   código (Java, sin librerías externas)
app/src/main/res/                      iconos adaptables, colores de iconos
app/src/main/assets/fonts/             Geist, Geist Mono, Space Mono
docs/                                  documentación
icons/                                 las 8 variantes del icono (vista previa)
tools/                                 Check.java (tests), make_icons.py
build-sin-sdk/                         compilación a APK sin Android SDK
```

## Principios

- **Local primero:** no descarga audio de ningún servicio. Las canciones que faltan en una lista importada se consiguen por vías legales (ver [IMPORTAR-SPOTIFY](docs/IMPORTAR-SPOTIFY.md)).
- **Sin dependencias:** solo la API de Android; el motor de audio, los parsers y las estadísticas son Java puro y testeable.
- **El DJ es un juguete:** el producto es el reproductor con la rueda. El modo DJ es un extra para divertirse, sin BPM ni sincronía.

## Licencias

Las fuentes **Geist** (Vercel) se distribuyen con su licencia en `app/src/main/assets/fonts/LICENSE-Geist.txt`. Space Mono y Geist Mono se incluyen para el estilo mono/matriz.
