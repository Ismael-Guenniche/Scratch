# Sin huecos y ecualizador (0.30)

## Reproducción sin huecos (gapless)
- `Player.prepNext()` decodifica en segundo plano la **siguiente** pista (cola → siguiente de la lista → siguiente del orden aleatorio; con «repetir una» no precarga).
- `ScratchEngine.queueNext(pcm)` la deja encolada. Cuando `render()` llega al último frame, `swapToNext()` cambia los datos **en el mismo bloque**, conservando la velocidad, así que no hay silencio. Después `Player.onGapless()` actualiza índice, título, historial, carátula y notificación.
- Si el usuario cambia a mano de pista, la precarga se reutiliza si coincide; si cambia la cola/aleatorio/repetir, se descarta y se prepara otra. Si estás haciendo scratch justo al final, cae al método clásico (`onEnded`).
- Coste: una segunda pista en memoria (el motor ya usa `largeHeap`).

## Ecualizador de 5 bandas
- Bandas: 60, 230, 910, 3.6 k y 14 kHz, ±12 dB, filtros peaking (Q 1,1) en el motor (`setGraphicEq`), con reducción automática de ganancia al subir bandas para no saturar.
- Ajustes → ECUALIZADOR: interruptor, presets (Flat, Bass boost, Vocal, Treble boost, Rock, Electronic) y un deslizador por banda. Claves: `eq_on`, `eq_preset`, `eq0..eq4`.
- Es independiente del EQ de tres bandas del modo DJ (que son los faders KILL).

## Kana en los títulos de puntos
- `DotFont` rasteriza cada hiragana/katakana en una rejilla 7×7 con la fuente del sistema (no hay glifos a mano). `DotTextView` admite varias líneas: en japonés, kana arriba e inglés debajo.
