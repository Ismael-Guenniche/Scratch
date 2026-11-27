# Pendiente (cosas por hacer)

## Pantalla completa: tocar título, portada o artista para ir a su página (pedido el 2026-11-27)
En el reproductor a pantalla completa (y lo mismo en Big Picture y la mini-barra si encaja):
- **Tocar el título de la canción o la portada** → abre la página del **álbum** de esa canción, o la del **single** si es una canción suelta (álbum con una sola pista, sin álbum en las etiquetas o con el mismo nombre que la canción).
- **Tocar el nombre del artista** → abre la página del **artista**: sus álbumes y singles, sus canciones y los datos que haya (nº de canciones, tiempo total, lo más escuchado según `Stats`).
- **Todo local**, sin internet: sale solo de las etiquetas (ID3/FLAC/MP4) y de lo que ya agrupa `Groups.java` para las pestañas ARTISTAS y ÁLBUMES, más las ediciones de `TrackEdits`. Reutilizar esas pantallas en vez de hacer otras nuevas.
- **Muchas canciones no traen metadatos.** Si no hay artista, se saca del nombre del archivo («Artista - Título.mp3», que es como están casi todas); si no hay álbum, la canción cuenta como single del artista. Si no se puede saber nada, el toque no hace nada (o avisa con un toast), nunca abre una página vacía.
- Si un artista aparece escrito de varias formas («Estopa» / «ESTOPA» / «Estopa feat. …»), juntarlo con la misma normalización de `SmartSearch` y usar el primer artista en los «feat.».
- Mantener el estilo de la app (`docs/ESTILO.md`), en `play` y `personal`, y traducir los textos nuevos en `TrEn`.

## Claude DJ con IA local (idea para el futuro, experimental)
Pedido el 2026-11-25. Ahora no hay clave de la API, así que la idea es probarlo con una IA local en el PC (RTX 3060), no como función habitual: pedirle listas o que «pinche» hablando, con voz (texto a voz) y una animación, y que se pueda levantar con un comando de PowerShell. El móvil le pasaría la biblioteca (el mismo JSON de LISTAS CON CLAUDE) y recibiría las listas por la red local.

## Letras que no aparecen (pedido el 2026-11-25)
En el móvil no se ven las letras. Ismael pasará varias canciones de su biblioteca para analizarlas: comprobar si llevan letra incrustada (USLT/SYLT en MP3, LYRICS en FLAC/Ogg, ©lyr en M4A) y si `LyricsReader` la lee. Si los archivos no traen letra, buscar una forma de arreglarlo (por ejemplo, bajar las letras en bloque de lrclib.net para toda la biblioteca y guardarlas en la app, o incrustarlas en los archivos desde el PC).

## Búsqueda inteligente — HECHA en la 0.76
Decidido el 2026-11-25: orden por relevancia por defecto y tolerancia NORMAL. Lo de abajo es el diseño original (todo implementado en `SmartSearch.java`; tests en `tools/CheckLists.java`).

Pedido el 2026-11-23. Problemas de ahora: la búsqueda solo mira si el texto escrito está tal cual dentro de «archivo + título + artista + álbum» (o de la carpeta). Por eso «one x» no encuentra «One-X», una letra mal («estepa») no encuentra «Estopa», y no hay orden por relevancia.

**Cómo hacerlo**
1. **Normalizar** igual el texto buscado y el de cada canción: minúsculas, sin acentos (NFD), anchura normal (NFKC, para japonés), y los signos (`- . ' _ / & ,`) cuentan como espacio. Así «One-X», «one x» y «ONE·X» son lo mismo.
2. **Por palabras y por campos.** Cada canción guarda (precalculado al escanear) las palabras de título, artista, álbum, carpeta y nombre de archivo. La búsqueda se parte en palabras y **todas** tienen que encajar en alguna palabra de algún campo.
3. **Cómo encaja una palabra** (de más a menos puntos): igual → empieza por → (solo con 4+ letras) dentro de una palabra → **aproximada**: distancia de Damerau-Levenshtein letra a letra (una letra cambiada, de más, de menos o dos letras cruzadas). Errores permitidos según la longitud: hasta 3 letras ninguno, 4–6 letras uno, 7 o más dos. «estepa» → «estopa» = 1 error.
4. **Palabras pegadas.** También se compara sin espacios («onex» = «one x»), pero solo empezando al principio de una palabra y con 4+ letras. Esto evita lo de «Ado»: «ado» no está al principio de ninguna palabra de «System of a Down» (system, of, a, down), así que no sale; el artista «Ado» sí, por palabra exacta.
5. **Orden por relevancia** al buscar: exactas arriba, aproximadas abajo, y pesa más el campo (artista/título > álbum > carpeta/archivo).
6. **Rendimiento:** palabras precalculadas, distancia con corte temprano, búsqueda en segundo plano con 120 ms de espera al teclear. Con 5.000 canciones debería ir en pocos ms.
7. **Ajustes → BÚSQUEDA:** tolerancia a errores (NO / POCA / NORMAL), dónde buscar (título, artista, álbum, carpeta), «dentro de palabras» sí/no, y ordenar por relevancia o mantener el orden de la lista.
8. Lo mismo en listas, Android Auto (búsqueda por voz) y la vista previa del coche.
9. **Tests** en `tools/CheckLists.java`: «one x» → One-X, «onex» → One-X, «estepa» → Estopa, «ado» → Ado y no System of a Down, y 5.000 canciones en menos de 20 ms.
