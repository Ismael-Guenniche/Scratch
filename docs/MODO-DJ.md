# Modo DJ

Un extra de **gimmicks inspirados en mesas y controladoras reales**. No hay BPM, sincronía ni dos platos: es un juguete sobre el mismo reproductor. Se activa con el chip *MODO* de la rueda. Cada grupo de funciones se puede ocultar en **Ajustes → MODO DJ**.

Al salir del modo DJ se resetea todo (faders, efectos, cues, bucles).

## Faders (siempre visibles)
Seis barras verticales con **escala numerada a la izquierda** y **valor en vivo arriba**. Tocar el nombre de una barra la devuelve a su valor neutro; cerca del neutro se "imanta".

| Fader | Rango | Escala |
|---|---|---|
| FILTRO | paso bajo ↔ OFF ↔ paso alto | LP · OFF · HP, lectura en % |
| GRAVES / MEDIOS / AGUDOS | −∞ … +4 dB (ganancia 0–1,6) | +4 · 0 · −6 · −12 · −∞, lectura en dB |
| ECO | 0–100 % | 100 · 50 · 0 |
| PITCH | −20 % … +20 % | +20 · +10 · 0 · −10 · −20 |

El EQ divide en tres bandas (corte a 250 Hz y 2,5 kHz). El filtro es un biquad de 2.º orden con resonancia; el eco es un delay de 0,36 s con realimentación.

## Frenado
- **BACKSPIN:** rebobinado rápido que se frena (0,75 s).
- **FRENADO 0,3 / 0,7 / 2,0 s:** duración del frenado de plato al pausar.
- **VINYL STOP:** pausa con un frenado de 1,2 s independiente del ajuste anterior.

## Hot cues
4 pads. *Toque* en un pad vacío guarda la posición; en uno ocupado salta a ella (muestra el tiempo). *Pulsación larga* lo borra. Los cues se borran al cambiar de canción.

## Bucle
- **IN → OUT → SALIR:** un solo botón con tres estados.
- **½ y ×2:** acortan o alargan el bucle activo.
- **ROLL ¼ / ½ / 1:** mientras mantienes, repite ese trozo; al soltar, la canción salta a donde habría llegado (slip implícito).
- **SLIP:** si está activo, durante scratch, bucle y reverse la canción "sigue por debajo" y al terminar vuelve a donde estaría.

## Saltos
−16 s, −4 s, +4 s, +16 s.

## Efectos
- **FLANGER:** delay modulado (0,6–4 ms, LFO 0,28 Hz) con realimentación.
- **PHASER:** 4 etapas all-pass con barrido de 350 Hz a ~2,8 kHz.
- **TRANS xHZ:** puerta rítmica; toque activa/desactiva, *pulsación larga* cambia 2 → 4 → 8 → 16 Hz.
- **ECHO OUT:** la música se va en ecos durante ~2,4 s y vuelve a los 3 s.

## Sampler
Cuatro sonidos **sintetizados al vuelo** (no son archivos): BOCINA (acorde de ondas casi cuadradas), SIRENA, LÁSER (barrido descendente) y RISER (tono + ruido ascendente). Se mezclan sobre la música, incluso con la pista parada.

## Mantener pulsado
- **CUT:** silencia mientras lo mantienes (transformer); la ganancia se suaviza 2 ms para evitar clics.
- **REVERSE:** la pista suena hacia atrás mientras lo mantienes; al soltar vuelve (con SLIP, a donde estaría).

## Vúmetro y modo disco
- **Vúmetro:** 32 puntos que se encienden con el nivel de salida, con memoria de pico; los últimos se ponen rojos.
- **Modo disco** (apagado por defecto): el aro y los puntos de la rueda y el vúmetro cambian de color y pulsan con el volumen.

## Rotación
En horizontal, la rueda ocupa la columna izquierda y los controles (incluido el panel DJ) van en una columna con scroll a la derecha.
