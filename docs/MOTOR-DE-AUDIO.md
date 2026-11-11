# Motor de audio

Todo vive en `ScratchEngine.java`: Java puro, **sin dependencias de Android**, por eso se puede probar en cualquier JVM (`tools/Check.java`).

## Decodificación
`AudioLoader` carga el archivo completo en memoria como PCM estéreo de 16 bits:
- **WAV** con lector propio (`WavParser`: PCM 8/16/24/32 bits y float).
- **MP3/AAC/OGG/FLAC…** con `MediaCodec`/`MediaExtractor` del sistema.
El límite es de 20 millones de frames (≈ 7,5 min a 44,1 kHz, unos 80 MB); si la pista es más larga se recorta y la interfaz lo avisa («RECORTADA»).

## Salida
`Player` abre un `AudioTrack` en modo de **baja latencia** a la frecuencia nativa del dispositivo y un hilo con prioridad de audio llama a `engine.render()` en bloques de 256 frames. El hilo se duerme tras ~120 bloques de inactividad y se despierta al tocar la rueda o disparar un sonido.

## Hilos y cerrojo
`render()` y las órdenes que cambian el estado siguen siendo `synchronized`, pero lo que la interfaz llama muchas veces por segundo **no coge el cerrojo**:
- `rotate()` acumula el giro en un `AtomicLong` y el hilo de audio lo aplica al empezar el bloque siguiente;
- `getPositionSeconds()`, `getDurationSeconds()`, `getRate()`, `getLevel()`, `isPlaying()`, `isScratching()`, `isIdle()`, `hasSwapped()` y `snap()` leen campos `volatile` que se publican al final de cada bloque y de cada orden (`publish()`);
- el bucle de audio ya solo coge el cerrojo una vez por bloque (antes cuatro).

Así, mientras haces scratch la interfaz no puede retener el cerrojo y dejar esperando al hilo de audio (inversión de prioridad). Las órdenes puntuales (play, efectos, cues) lo cogen durante nanosegundos.

## Cómo suena el scratch
Como un vinilo: el motor lee la pista a **velocidad variable** con **interpolación cúbica** (Catmull-Rom). Mientras el dedo está en la rueda, la posición de lectura *persigue* a una posición objetivo que mueve el dedo:
- velocidad = distancia al objetivo / τ (τ = 20 ms), limitada a ±12×;
- por eso el **tono sube y baja** con la velocidad, suena **al revés** si giras hacia atrás y se **calla** si paras;
- una vuelta completa = 1,8 s de audio (33⅓ rpm); cambia con la sensibilidad (3,6 / 1,8 / 0,9 s).

## Plato
- **Arranque:** la velocidad sube a la nominal con constante de 0,12 s.
- **Frenado:** baja de forma lineal hasta parar (0,7 s por defecto), con caída de tono como un plato real.
- **Inercia:** si sueltas la rueda lanzada con la música parada, sigue girando con fricción exponencial (τ = 0,6 / 1,5 / 3 s según Alta/Media/Baja) más un rozamiento seco para que pare del todo.
- **Avance/retroceso rápido:** 1,5× con audio; velocidad ajustable.
- **Pitch:** velocidad de reproducción 0,5×–1,5× (el panel limita a ±20 %).

## Mantener el tono (keylock)
Síntesis **granular (OLA)** con ventana Hann de 50 ms (precalculada en una tabla al fijar la frecuencia de salida) y solape del 50 %, hasta 4 granos simultáneos. Cada grano suena a velocidad normal (hacia delante o atrás según la rueda), así el tono no cambia aunque la posición avance más rápido o más lento.

## Efectos (modo DJ)
Cadena por muestra: EQ de 3 bandas (biquads LP 250 Hz / HP 2,5 kHz) → filtro barrido → eco / echo-out → flanger → phaser → corte/puerta → sampler → medidor de nivel.
- Todos los biquads son RBJ de 2.º orden.
- Con todo en neutro no se procesa nada (`fxActive`).
- Cada orden recalcula solo sus coeficientes (`refreshGeq`, `refreshSweep`); el hilo de audio no crea objetos (hay un test que lo comprueba).
- El corte/puerta usa ganancia suavizada para evitar clics.

## Gimmicks de transporte
- **Hot cues, saltos:** simples reposicionamientos.
- **Bucle:** si `pos ≥ loopEnd` salta a `loopStart + exceso` (y simétrico hacia atrás).
- **Slip:** posición «sombra» (`slipPos`) que avanza a velocidad normal mientras dura scratch, bucle, roll o reverse; al terminar, `pos = slipPos`.
- **Vinyl stop:** una duración de frenado armada que solo se usa en la siguiente pausa.

## Sampler
Cuatro voces polifónicas sintetizadas en `sampler()`: bocina (3 sierras recortadas con vibrato), sirena (seno con barrido LFO), láser (sierra con frecuencia exponencial descendente) y riser (seno exponencial ascendente + ruido). Amplitud base ≈ 9500 sobre 32767.

## Nivel
`getLevel()` devuelve el pico del último bloque con caída suave; lo lee la interfaz para el vúmetro y el modo disco.

## Rendimiento
`tools/Bench.java` mide en la JVM el coste de `render()` por bloque, las asignaciones del hilo de audio, el tiempo que la interfaz retiene el cerrojo y la contención, y calcula una huella (CRC) del audio de cada escenario para detectar cambios de sonido. Los resultados y la latencia/memoria teóricas están en [BENCHMARKS.md](BENCHMARKS.md). Línea base (v0.51): `render()` usa menos del 1 % del plazo de 5,33 ms incluso con todo el modo DJ; los cuellos reales son el búfer del `AudioTrack`, la τ fija del scratch, el cerrojo compartido con la interfaz, la basura del sampler y la pista entera en RAM.

## Tests
`tools/Check.java` cubre: WAV ida y vuelta, reproducción y arranque, scratch adelante/atrás, tono con y sin keylock, inercia, EQ/filtro/eco/backspin, hot cues, saltos, bucle, roll+slip, reverse, cut, gate, flanger/phaser/echo-out, sampler, vinyl stop, estadísticas, velocidad de avance y determinismo (mismas órdenes, mismo audio muestra a muestra).
