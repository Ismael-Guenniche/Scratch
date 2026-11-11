# Benchmarks del motor de audio

Medidas reproducibles en la JVM (sin Android) con `tools/Bench.java`. Cada tarea de audio añade aquí su «antes» y «después».

## Cómo ejecutarlo

Con las clases ya compiladas como en [COMPILAR.md](COMPILAR.md) (`/tmp/cls`):

```bash
javac -encoding UTF-8 -cp /tmp/cls -d /tmp/chk tools/Bench.java
java       -cp /tmp/chk:/tmp/cls Bench         # tiempos, cerrojo y contención (~1 min)
java -Xint -cp /tmp/chk:/tmp/cls Bench alloc   # bytes asignados por bloque
```

Cómo leerlo:
- **Los tiempos dependen de la máquina.** Solo sirven para comparar antes/después en el mismo equipo. Un móvil con ART es varias veces más lento que la JVM de un PC.
- **La huella CRC no depende de la máquina.** Es el CRC32 del audio generado en cada escenario: si una optimización la cambia, el sonido ha cambiado (aunque sea en un bit).
- **Media y p99** son estables entre pasadas (±5 %). **Máx** es ruido del sistema operativo del equipo de pruebas; no compararlo.
- Las asignaciones se miden con `-Xint` (sin JIT) para ver lo que pide el bytecode de verdad: el JIT del PC elimina algunas que ART puede no eliminar.

El escenario usa una pista sintética de 150 s a 44,1 kHz, salida a 48 kHz y bloques de 256 frames, igual que `Player`. El plazo por bloque es 256 / 48000 = **5,33 ms**.

## Latencia teórica (por lectura del código, v0.51)

| Tramo | Valor | De dónde sale |
|---|---:|---|
| Bloque de render | 5,33 ms | `BLOCK = 256` a 48 kHz |
| Búfer del `AudioTrack` | ≥ 10,7 ms, real = `getMinBufferSize()` | `max(minBuf, 2 bloques)`; no se alinea con el *burst* nativo ni se ajusta nunca. **Falta medirlo en el móvil** |
| Muestreo del dedo | 0–8,3 ms (media ≈ 4 ms) | un `ACTION_MOVE` por frame de pantalla (8,3 ms a 120 Hz); no se usan las muestras históricas |
| Persecución del objetivo | ≈ 20 ms (63 %), ≈ 60 ms (95 %) | τ = 20 ms fija |
| Despertar tras pausa | creación de `AudioTrack` + hilo | el hilo se apaga a los 120 bloques (0,64 s) en reposo y el primer toque lo crea todo de nuevo |

## Memoria (por lectura del código, v0.51)

| Concepto | Valor |
|---|---:|
| PCM estéreo 16 bits a 44,1 kHz | 10,6 MB por minuto |
| Canción de 4 min | ≈ 42 MB |
| Máximo por pista (20 M frames, 7,5 min) | 80 MB |
| Precarga (5 pistas por defecto) | hasta el 45 % del heap (`largeHeap`), además de la pista actual |
| Arranque rápido | copia extra de los primeros 8 s (1,4 MB) |

## T1 · Línea base (v0.51, commit d957049)

Equipo: Intel Xeon 2,1 GHz (2 núcleos, VM compartida), OpenJDK 21. 20 000 bloques por escenario, mejor de 3 pasadas.

### Coste de `render()` por bloque

| Escenario | Media µs | p50 µs | p99 µs | % del plazo (media) | Huella CRC |
|---|---:|---:|---:|---:|---|
| Parado (silencio) | 8,73 | 8,48 | 19,3 | 0,16 % | 533bd091 |
| Reproducción normal | 7,43 | 7,21 | 17,6 | 0,14 % | be04ea6c |
| Reproducción + keylock (pitch 1,2) | 19,4 | 18,6 | 33,3 | 0,36 % | c66aad72 |
| Scratch | 9,95 | 9,14 | 25,8 | 0,19 % | c27f4ae9 |
| Scratch + keylock | 18,7 | 17,8 | 36,7 | 0,35 % | 24d9a1c8 |
| Reproducción + EQ 5 bandas | 18,8 | 17,9 | 36,9 | 0,35 % | d78e1205 |
| Reproducción + modo DJ completo | 44,2 | 41,6 | 67,8 | 0,83 % | c2c011ab |
| Reproducción + sampler (bocina) | 31,8 | 31,2 | 48,6 | 0,60 % | fc643c46 |

### Asignaciones en el hilo de audio

| Escenario | Bytes/bloque | Bytes/s |
|---|---:|---:|
| Todos menos el sampler | 0 | 0 |
| Reproducción + sampler (bocina) | 10 176 | 1 908 000 |

Fuera del motor (leído en el código, no medido): `WavRecorder.feed()` crea un `short[512]` por bloque mientras se graba (≈ 195 KB/s).

### Tiempo con el cerrojo del motor retenido por la interfaz

| Orden | ns por llamada |
|---|---:|
| `rotate()` | 25 |
| `getPositionSeconds()` | 23 |
| `snap()` | 44 |
| `setEq()` | 525 |
| `setFilter()` | 647 |

`setEq`/`setFilter`/`setEcho`/`setGraphicEq` llaman a `refreshFx()`, que recalcula los 16 biquads (seno, coseno y potencia) aunque solo cambie uno.

### Contención (render a ritmo real + un hilo que hace de interfaz)

| Caso | Media µs | p99 µs | Bloques fuera de plazo |
|---|---:|---:|---:|
| Scratch, interfaz casi quieta (1 Hz) | 10,5 | 22,1 | 0 / 4000 |
| Scratch, rueda + ticker a 1000 Hz | 12,7 | 19,6 | 0 / 4000 |
| Modo DJ, faders EQ/filtro a 1000 Hz | 56,6 | 124,8 | 0 / 4000 |

### Conclusiones de la línea base

1. **El coste de CPU de `render()` no es el problema**: incluso con todo el modo DJ activo usa menos del 1 % del plazo en esta máquina. En el móvil será varias veces más, pero sigue habiendo mucho margen.
2. **La latencia está en la estructura, no en el cálculo**: búfer del `AudioTrack` sin ajustar, τ fija de 20 ms, una muestra del dedo por frame y recreación del `AudioTrack` tras 0,64 s en reposo.
3. **El riesgo de cortes viene del cerrojo y de la basura**: un monitor Java no hereda prioridad, así que si Android desaloja al hilo de la interfaz con el cerrojo cogido, el hilo de audio espera. La JVM de un PC con núcleos libres no lo reproduce (0 bloques fuera de plazo); en el móvil hay que medirlo con los *underruns* del `AudioTrack` (T4). El sampler genera 1,9 MB/s de basura, que en ART acaba en pausas del recolector.
4. **La memoria es el cuello más grande**: 42 MB por canción y hasta el 45 % del heap en precarga.

## Lo que hay que medir en el móvil

Con una canción sonando y el móvil conectado por USB:

```bash
adb shell dumpsys media.audio_flinger > flinger.txt
```

Del archivo `flinger.txt` interesan: tamaño del búfer en frames, si la pista sale con la marca `FAST` (ruta de baja latencia) y el contador de *underruns* tras un minuto de scratch.

## T2 · Hilo de audio limpio (v0.53)

Mismo equipo y mismo método que la línea base. **Todas las huellas CRC son idénticas a las de T1**: el audio generado no cambia ni en un bit.

| Medida | Antes (0.52) | Después (0.53) |
|---|---:|---:|
| Reproducción + keylock, media | 19,4 µs | 13,2 µs (−32 %) |
| Scratch + keylock, media | 18,7 µs | 13,2 µs (−29 %) |
| EQ 5 bandas, media | 18,8 µs | 15,4 µs (−18 %) |
| Modo DJ completo, media | 44,2 µs | 36,3 µs (−18 %) |
| Sampler (bocina), media | 31,8 µs | 27,8 µs (−13 %) |
| Reproducción normal / scratch | 7,43 / 9,95 µs | 7,43 / 9,35 µs |
| Asignaciones con el sampler | 10 176 bytes/bloque | 0 |
| Asignaciones al grabar (`WavRecorder`) | 1 040 bytes/bloque | 0 (anillo fijo) |
| `setEq()` con el cerrojo | 525 ns | 31 ns |
| `setFilter()` con el cerrojo | 647 ns | 132 ns |

Qué se hizo: ventana Hann en tabla (antes un `Math.cos` por grano y muestra), notas de la bocina como constante, cada orden de efectos recalcula solo sus coeficientes (antes los 16 biquads siempre), constantes del bucle fuera del bucle, índices circulares sin `%`, y grabación con anillo preasignado.

## T3 · Menos cerrojo (v0.54)

Medida nueva en `Bench`: 4 hilos de interfaz llamando sin pausa a la rueda y a las consultas (más hilos que núcleos, para forzar que alguno sea desalojado con el cerrojo cogido) mientras el audio renderiza a ritmo real. Se mide cuánto espera el hilo de audio para conseguir el cerrojo antes de cada bloque. **Huellas CRC idénticas a T1/T2.**

| Medida | Antes (0.53) | Después (0.54) |
|---|---:|---:|
| Espera por el cerrojo, p99 | 5 089 µs | 1,56 µs |
| Espera por el cerrojo, máxima | 11 679 µs (más de 2 bloques: corte seguro) | 19,7 µs |
| Bloques con espera > 1 ms | 240 / 4000 | 0 / 4000 |
| Veces que el bucle de audio coge el cerrojo por bloque | 4 | 1 |
| `rotate()` | 14 ns con cerrojo | 7 ns sin cerrojo |
| `snap()` | 63 ns con cerrojo | 26 ns sin cerrojo |
| Coste de `render()` (scratch / modo DJ) | 9,35 / 36,3 µs | 9,42 / 37,6 µs (igual, dentro del ruido) |

## T4 · Búfer del AudioTrack adaptativo (v0.55)

La latencia real solo se puede medir en el móvil; en la JVM se prueba la lógica (`BufferTuner`, tests en `Check.java`) y se calcula lo teórico. El motor no cambia (huellas CRC idénticas).

| Medida | Antes (0.54) | Después (0.55) |
|---|---|---|
| Bloque de render | 256 frames fijos | la ráfaga nativa del móvil (`PROPERTY_OUTPUT_FRAMES_PER_BUFFER`) si está entre 64 y 512; si no, 256 |
| Búfer efectivo | `getMinBufferSize()` entero, fijo | 2 bloques al empezar (8 ms con ráfaga de 192 a 48 kHz) |
| Ante underruns | nada (ni se contaban) | crece 1, 2, 4, 8 bloques (con 0,5 s de calma entre subidas) hasta la capacidad |
| Al despertar el hilo | — | reutiliza el tamaño aprendido (se olvida si cambia la salida de audio) |
| Si Android no da la ruta rápida | — | búfer entero, como antes |

Para ver los números reales en el Nothing Phone (4a):

```bash
adb logcat -s ScratchAudio
```

Sale una línea al arrancar el audio (frecuencia, ráfaga, bloque, capacidad, ruta rápida o normal, búfer en frames y ms) y otra cada vez que el búfer crece por underruns.

## T5 · Respuesta del dedo (v0.56)

Herramienta nueva: `tools/BenchFeel.java` simula una pantalla táctil de 120 Hz con llegada irregular de los eventos (±1,5 ms), ruido de posición y bloques de 256 frames a 48 kHz. El retraso se mide en milisegundos de audio entre donde está el dedo y donde está la lectura.

| Gesto | Medida | Antes (0.55) | Después (0.56) |
|---|---|---:|---:|
| Dedo constante a 0,25× | rizado de la velocidad | 17,1 % | 17,8 % |
| Dedo constante a 1× | rizado de la velocidad | 12,6 % | 7,1 % |
| Dedo constante a 3× | rizado de la velocidad | 12,3 % | 4,2 % |
| Dedo constante a 0,25× / 1× / 3× | retraso respecto al dedo | 6,8 / 27,3 / 82,0 ms | −0,2 / 0,3 / 1,1 ms |
| Arranque brusco 0 → 2× | tiempo hasta el 90 % | 60 ms | 17 ms |
| Parada brusca 2× → 0 | tiempo hasta el silencio | 128 ms | 32 ms |
| Parada brusca 2× → 0 | se pasa de la posición del dedo | 0 ms | 40 ms (se corrige en silencio) |
| Baby scratch 4 Hz ±2,5× | error RMS de velocidad | 0,98× | 0,44× |
| Coste de `render()` en scratch | media | 9,4 µs | 11,0 µs |

Contrapartida: al predecir la posición del dedo entre eventos, en una parada perfectamente brusca la lectura se pasa un poco y vuelve al punto exacto en silencio. Con un dedo real, que frena en varios eventos, el exceso es mucho menor. A 0,25× el rizado no mejora: ahí manda el ruido de posición del táctil simulado.

## T6 · Interpolación y antialiasing (v0.57)

Herramienta nueva: `tools/BenchQuality.java` reproduce tonos puros (pista a 44,1 kHz, salida a 48 kHz) y mide la relación señal/ruido y cuánto se cuela un tono que, por la velocidad, queda por encima de lo audible.

| Prueba | Antes (cúbica, 0.56) | Después (sinc, 0.57) |
|---|---:|---:|
| Tono de 1 kHz, señal/ruido | 85,6 dB | 87,4 dB |
| Tono de 5 kHz, señal/ruido | 46,5 dB | 85,5 dB |
| Tono de 10 kHz, señal/ruido | 26,4 dB | 84,4 dB |
| Tono de 15 kHz, señal/ruido (ganancia) | 13,8 dB (−1,7 dB) | 51,5 dB (−0,6 dB) |
| Tono de 18 kHz, señal/ruido (ganancia) | 7,6 dB (−3,1 dB) | 24,5 dB (−2,6 dB) |
| Aliasing, 14 kHz a 2× | −1,2 dB | −19,2 dB |
| Aliasing, 12 kHz a 3× | −0,7 dB | −66,0 dB |
| Aliasing, 10 kHz a 4× | −0,4 dB | −75,2 dB |
| Coste: reproducción normal | 7,7 µs (0,14 %) | 14,6 µs (0,27 %) |
| Coste: scratch | 11,0 µs (0,21 %) | 39,4 µs (0,74 %) |
| Coste: parado | 6,8 µs | 0,31 µs |

El sonido cambia (las huellas CRC ya no coinciden con las de T1–T5, como se esperaba). El coste sube, pero sigue por debajo del 1 % del plazo; la primera versión del filtro costaba 243 µs en scratch y se optimizó hasta 39 µs (coeficientes por fase a velocidad normal y núcleo corto en lectura rápida). Por encima de 4× el filtro deja de estrecharse: a esas velocidades queda algo de aliasing.

## T7 · Física del plato (v0.58)

Herramienta nueva: `tools/BenchPlatter.java` (traza la velocidad cada 0,33 ms).

| Situación | Medida | Antes (0.57) | Después (0.58) |
|---|---|---:|---:|
| Arranque del motor (play) | 10 / 50 / 90 / 99 % | 13 / 83 / 276 / 553 ms | 20 / 100 / 180 / 226 ms |
| Arranque del motor (play) | forma (0,5 = rampa recta de motor) | 0,27 (exponencial) | 0,50 |
| Agarrar el disco sonando (1× → 0) | tiempo hasta parar | 1,0 ms | 12,7 ms |
| Agarrar el disco sonando (1× → 0) | salto máximo de velocidad en 0,33 ms | 0,710× | 0,065× |
| Soltar el disco parado con música (0 → 1×) | 90 / 99 % | 276 / 553 ms | 54 / 60 ms |
| Soltar el disco yendo atrás (−2,4× → 1×) | llega al 90 % | 424 ms | 199 ms |
| Frenado (pausa) | 50 % / parado | 350 / 699 ms | igual (ya era lineal) |

El frenado y la inercia al soltar con la música parada no cambian. El coste de `render()` no varía de forma medible.

## T8 · Corte sin clics (v0.59)

Herramienta nueva: `tools/BenchCut.java` (con una pista de valor constante la salida es la curva de ganancia).

| Medida | Antes (0.58) | Después (0.59) |
|---|---:|---:|
| Cerrar: del 90 % al 10 % | 4,35 ms | 1,02 ms |
| Cerrar: hasta el silencio desde la orden | 9,15 ms | 1,77 ms |
| Abrir: del 10 % al 90 % | 4,35 ms | 1,02 ms |
| Abrir: hasta el 99 % desde la orden | 9,15 ms | 1,77 ms |
| Clic por encima de 5 kHz, transformer a 8 / 16 / 30 Hz sobre un tono de 100 Hz | −78,5 / −75,5 / −72,8 dB | −91,5 / −91,3 / −90,9 dB |

Antes la ganancia seguía una exponencial (arranque brusco, cola larga); ahora es una rampa de 2 ms con forma de S. Los −91 dB son ya el ruido de cuantificación de 16 bits.

En el test de baby scratch (vaivén de 3 Hz sobre un tono de 440 Hz) el mayor salto entre muestras consecutivas era de 6 269 sobre 12 000: un clic de amplitud en cada cambio de sentido, porque el volumen ligado a la velocidad cambiaba de golpe al pasar por cero. Ahora ese volumen se suaviza 1,5 ms y el salto máximo queda dentro de lo que da la propia onda (ver `Check.java`).

## T14 · Efectos por bloque: probado y descartado

Se reescribió la cadena de efectos para procesar el bloque entero etapa a etapa (filtros con el estado en variables locales, etapas neutras saltadas una vez por bloque). Resultado en la JVM, mismo equipo:

| Escenario | Por muestra (0.59) | Por bloque (prueba) |
|---|---:|---:|
| Reproducción + EQ 5 bandas, p50 | 20,1 µs | 22,5 µs (+12 %) |
| Reproducción + modo DJ completo, p50 | 41,0 µs | 40,2 µs (−2 %) |

No mejora (el JIT ya hacía ese trabajo) y además el flanger y el phaser comparten oscilador, así que procesarlos por etapas cambiaba el sonido del modo DJ. Como la cadena completa ya cuesta menos del 1 % del plazo, **se descarta** y el código queda como estaba. SIMD solo tendría sentido con un módulo nativo (T13), que tampoco se justifica por CPU.

## T15 · Keylock con WSOLA (v0.60)

Prueba objetiva en `tools/BenchQuality.java`: se reproduce un tono puro con keylock a 0,8× y 1,2× y se mide cuánto de la salida es ese mismo tono (señal/ruido) y cuánto tiembla el volumen. Con OLA simple los granos se sumaban desfasados y el tono salía desafinado (por eso el ajuste da valores negativos).

| Prueba (0,8× y 1,2× dan lo mismo) | OLA (0.59) | WSOLA (0.60) |
|---|---:|---:|
| Tono de 220 Hz, señal/ruido | −50 dB | 48 dB |
| Tono de 440 Hz, señal/ruido | −52 dB | 48 dB |
| Tono de 1 kHz, señal/ruido | 65 dB (caso favorable: el salto entre granos coincide con el periodo) | 34 dB |
| Tono de 440 Hz, temblor de volumen | 5,7 % | 0,8 % |
| Nota con 6 armónicos, temblor de volumen | 1,0 % | 1,0 % |
| Coste con keylock, media / p99 | 13 µs / 25 µs | 27 µs / 93 µs |

El coste sube porque cada 25 ms se busca el mejor punto de enganche (búsqueda gruesa y ajuste fino); sigue por debajo del 2 % del plazo en el peor bloque. Sin keylock no cambia nada.

## T18 · Regresión de rendimiento automática (v0.61)

`tools/run-all.sh` ejecuta todos los tests y `Bench regress`: el coste p50 por bloque de cada escenario, dividido por una carga de referencia medida en la misma pasada (para que no dependa de la máquina), se compara con `tools/bench-baseline.txt`. Falla si un escenario sube más del 30 % (el ruido entre pasadas en la máquina de pruebas llega al 15 %). Tras un cambio de coste aceptado (como el sinc de T6) se regenera con `tools/run-all.sh baseline`.

## T17 · Sonido de vinilo opcional (v0.62)

Con el ajuste apagado (por defecto) el audio es idéntico bit a bit al de la 0.61 (mismas huellas CRC). Encendido:

| Medida | Valor |
|---|---:|
| Coste extra por bloque | +5,8 µs (de 14,7 a 20,5 µs; 0,1 % del plazo) |
| Nivel del ruido de surco | RMS 12 de 32767 (−69 dB), picos de los chasquidos hasta 644 (−34 dB) |
| Oscilación del tono | ±0,12 % a 0,556 Hz y ±0,03 % a 7,3 Hz |
| Deriva de posición tras 10 s | < 1 ms |
| Asignaciones en el hilo de audio | 0 |

## T19 · Perfilado en el móvil

No se mide en la JVM: la guía para capturar un trace con Perfetto y qué mirar está en [PERFILADO.md](PERFILADO.md).

## T20 · Limitador suave de salida (v0.63)

Tono de 440 Hz fuerte con los tres faders de EQ del modo DJ al máximo (×1,6, la suma se pasa del máximo de 16 bits):

| Medida | Antes (recorte en seco) | Después (limitador suave) |
|---|---:|---:|
| Armónicos por encima de 5 kHz respecto a la señal | −44,2 dB | −53,5 dB |
| Reproducción normal y scratch (huella CRC) | — | idénticas: sin efectos no actúa |

## Estado de las tareas que no se han podido cerrar aquí

Ver [AUDIO-PENDIENTE.md](AUDIO-PENDIENTE.md): T9–T13 y T16 necesitan compilar con el SDK de Android y probar en el móvil.

## T14 · Efectos por bloque, segunda vuelta (v0.64)

Se rehízo la prueba descartada corrigiendo su fallo: flanger y phaser comparten oscilador, así que ahora van juntos en un mismo bucle y el resto de etapas (EQ gráfico, EQ de 3 bandas, filtro, eco) se procesan por bloque con el estado de los filtros en variables locales. **Todas las huellas CRC son idénticas a las de la 0.63**: el sonido no cambia en ningún escenario.

| Escenario (p50, misma sesión) | Por muestra (0.63) | Por bloque (0.64) |
|---|---:|---:|
| Reproducción + EQ 5 bandas | 25,4 µs | 23,5 µs |
| Reproducción + modo DJ completo | 50,7 µs | 39,7 µs |
| Reproducción normal | 14,3 µs | 13,3 µs |

La mejora en la JVM es pequeña (y dentro del ruido en el EQ) porque el JIT ya optimizaba el camino por muestra; en ART, que optimiza menos, debería notarse algo más. SIMD queda fuera: exige un módulo nativo.

## T9, T10 y T11 · PCM en disco, precarga y caché de onda (v0.65)

Las canciones se guardan en un archivo temporal proyectado en memoria y el motor las lee por una ventana. En `Check.java` se reproduce la misma pista desde memoria y desde disco durante 48 s de uso variado (saltos, scratch, keylock, fundido y cambio de pista sin hueco) y **la salida es idéntica muestra a muestra**; las huellas CRC de `Bench` también coinciden.

| Medida | Antes (0.64) | Después (0.65) |
|---|---:|---:|
| Heap por canción de 4 min | ≈ 42 MB | 0 (más 1 MB fijo de ventana en el motor) |
| Heap con 5 pistas precargadas | hasta el 45 % del heap (≈ 210 MB) | ≈ 0 |
| Duración máxima | 7,5 min (se recortaba) | ≈ 2,5 h |
| Disco temporal | 0 | 10,6 MB por minuto de audio; precarga limitada a 600 MB |
| Coste de `render()`, reproducción | 13,4 µs | 14,8 µs |
| Coste de `render()`, scratch | 37,3 µs | 37,1 µs |
| Asignaciones en el hilo de audio | 0 | 0 |
| Forma de onda en Big Picture | se recalculaba al abrir cada canción | se calcula una vez al decodificar y se guarda en caché por canción |

Lo que la JVM no mide y hay que probar en el móvil: la primera lectura de una zona de la canción que Android haya sacado de memoria puede tardar algún milisegundo (lectura de almacenamiento flash) y dar un microcorte al saltar muy lejos tras mucho rato.

## T16 · Latencia medida y posición mostrada (v0.66)

El audio no cambia (mismas huellas CRC). Se prueba en `Check.java`:

| Medida | Antes (0.65) | Después (0.66) |
|---|---:|---:|
| Desfase entre lo mostrado y lo que suena, con 50 ms de latencia de salida | 50 ms (la rueda, el tiempo y la letra iban por delante) | 0 ms |
| Salto de lo mostrado al tocar el disco | — | ninguno: como mucho 7 ms por bloque durante la transición de 0,15 s |
| Asignaciones en el hilo de audio | 0 | 0 |

La latencia real del Nothing Phone (4a) se ve en Ajustes → SONIDO («Latencia medida») tras unos segundos de reproducción. La medida no incluye lo que añada un altavoz o unos auriculares Bluetooth por su cuenta: para eso está el ajuste fino.
