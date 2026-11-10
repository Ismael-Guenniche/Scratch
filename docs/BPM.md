# BPM y sincronía (0.31)

Qué es: **BPM** = pulsos por minuto, la velocidad de la canción. **Sincronía** = hacer que dos canciones vayan al mismo BPM para enlazarlas sin que se note el cambio de ritmo (lo que hacen los DJ).

- **Detección** (`BpmDetect`): envolvente de energía a 200 Hz → onsets → autocorrelación con retardo fraccionario en 70–180 BPM → se pliega a 80–165 → fase del primer pulso. Analiza ~70 s de la pista tras abrirla, en segundo plano, y guarda el resultado por canción (preferencias `bpm`). Los tests sintéticos (128, 100, 93,5 BPM) salen con error < 0,5 BPM.
- **Panel DJ → BPM Y SINCRONÍA**: BPM real con el pitch aplicado, cuatro puntos que marcan los pulsos, **SYNC**, **BPM ½** y **BPM ×2** (si el detector se queda con el doble o la mitad).
- **SYNC**: al activarlo, el tempo actual pasa a ser el *maestro*. Cada pista siguiente se ajusta de pitch (±20 %) para sonar a ese tempo (probando también el doble y la mitad). Mover el fader de pitch con SYNC activo cambia el maestro. Si no cabe en ±20 %, esa pista suena a pitch 1,0.
- Limitación: hay una sola cubierta, así que la sincronía es entre pistas consecutivas (con la reproducción sin huecos suena como una mezcla encadenada). No alinea la fase de los golpes entre dos pistas a la vez.
- Se puede quitar en Ajustes → Modo DJ → «BPM y sincronía».
