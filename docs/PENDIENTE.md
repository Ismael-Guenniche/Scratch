# Pendiente (cosas por hacer)

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
