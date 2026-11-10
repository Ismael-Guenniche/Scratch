# Estilo gráfico de Scratch

Base: la guía **Lucent / Nothing OS 5.0** y las apps hermanas *DotWords* y *Dot Calendar*. Todo el aspecto está en `Lucent.java` (tokens) y en las vistas `Dot*`.

## Principios
- **Los puntos son el lenguaje.** Títulos y cifras grandes en matriz de puntos 5×7 (`DotTextView` + `DotFont`); iconos en matriz 7×7 (`DotIcons`, `DotIconView`). Los puntos apagados se ven «fantasma».
- **Un solo acento:** `#FB471F` (`#FF5A33` en oscuro), reservado para lo activo: progreso, cabezal, estados encendidos, CTA. El usuario puede cambiarlo entre 8 colores o tomarlo del fondo de pantalla.
- **Superficies de cristal** (`th.card`) sobre el fondo, con borde fino (`glassLine`); sin sombras duras. Opción de tarjetas sólidas.
- **Rejilla de puntos** tenue por toda la pantalla (`DotGrid`), sobre el fondo difuminado, como en Dot Calendar.
- **Tipografía:** sans (Geist) para texto, mono (Geist Mono / Space Mono) para etiquetas con espaciado, puntos para cifras. Tres presets: Dot / Mono / Clean.

## Tokens (`Lucent`)
| Token | Uso |
|---|---|
| `dark` | tema oscuro activo |
| `ink`, `ink2` | texto principal y secundario |
| `glass`, `glassLine` | relleno y borde de las tarjetas |
| `solid` | relleno cuando se desactiva el cristal |
| `accent`, `onAccent` | color de acento y texto sobre él |
| `dotOff`, `ghost` | puntos apagados y pistas de sliders |
| `veil`, `grid`, `blur` | velo del fondo, rejilla y desenfoque |
| Radios | `R_L = 34`, `R_M = 40` (píldoras), `R_S = 22` (casillas) |

Helpers: `th.card(r)`, `th.chip(ctx, label, on, padH)`, `th.style(button, kind)`, `th.styleSeek(seekbar)`, `Lucent.insets`, `Lucent.withAlpha`, `Lucent.prefs`.

## Componentes
- **Píldoras** de radio grande para botones; **chips** de tinta para selección.
- **Tarjetas** con etiqueta mono arriba → cifra grande de puntos → contenido.
- **Controles segmentados** para opciones de 2–5 valores (`segmented`).
- **Interruptores** con descripción en gris bajo el título.
- **Menú y diálogos propios** (`DotMenu`, `DotDialog`) en lugar de los del sistema.
- **Hit boxes enormes:** los botones de la rueda y los pads DJ ocupan toda su casilla (≥ 48 dp) y los iconos no se tragan el toque.

## La firma de Scratch
El **disco de puntos concéntrico** (la rueda del TP-7): siete anillos de puntos que giran con la canción y un punto naranja como cabezal. Los 8 iconos de la app (`tools/make_icons.py`) varían fondo y colores sobre esa misma figura. Son adaptativos y llevan capa **monocroma** para el tema de iconos de Nothing.

## Modo DJ
- Faders verticales con escala numerada y lectura en vivo en el acento.
- Pads de cristal en rejilla de 3–4 columnas; encendidos = borde y texto en acento con relleno al 22 %.
- Vúmetro de 32 puntos; con modo disco, un arcoíris que gira por HSV.

## Wrapped
Fondo negro, páginas a pantalla completa, progreso con puntos e histograma de 24 columnas de puntos.

## Cómo añadir una pantalla nueva
1. `Lucent th = Lucent.load(this); th.apply(this);` en `onCreate`.
2. Cabecera: botón circular de volver + título en `DotTextView`.
3. Contenido en tarjetas `th.card(Lucent.R_L)`; botones con `th.style`.
4. En `onResume`, si `th.myStamp != Lucent.stamp` → `recreate()`.
