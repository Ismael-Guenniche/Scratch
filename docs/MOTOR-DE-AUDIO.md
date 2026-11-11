# Motor de audio

Todo vive en `ScratchEngine.java`: Java puro, **sin dependencias de Android**, por eso se puede probar en cualquier JVM (`tools/Check.java`).

## Decodificación
`AudioLoader` decodifica el archivo a PCM estéreo de 16 bits:
- **WAV** con lector propio (`WavParser`: PCM 8/16/24/32 bits y float).
- **MP3/AAC/OGG/FLAC…** con `MediaCodec`/`MediaExtractor` del sistema.

Desde la 0.65 el PCM **no se queda en la RAM de la app**: se escribe en un archivo temporal (`cache/pcm`) mientras se decodifica y al terminar se proyecta en memoria (`mmap`) y se borra su nombre, así el espacio se libera solo cuando se suelta la pista o se cierra la app. El motor lee de ahí por una **ventana** de 262 144 frames (≈ 6 s, 1 MB) que el hilo de audio recarga centrada en la posición cuando la lectura se acerca a un borde (una copia de memoria, sin crear objetos). Una canción ya no ocupa ~42 MB de heap y el límite pasa de 7,5 min a unas 2,5 h. Los WAV de menos de 30 s, la pista de prueba y el fragmento de arranque rápido siguen en memoria. Si no hay espacio de sobra o el disco falla, se carga en memoria como antes (con el límite de 20 millones de frames y el aviso «RECORTADA»).

La forma de onda (480 picos) se calcula al terminar de decodificar y se guarda por canción en `cache/wave` (`WaveCache`); el BPM ya se guardaba en las preferencias `bpm`. La precarga de las siguientes pistas ya no está limitada por la RAM, solo por 600 MB de disco temporal.

## Salida
`Player` abre un `AudioTrack` en modo de **baja latencia** a la frecuencia nativa del dispositivo y un hilo con prioridad de audio llama a `engine.render()` en bloques del tamaño de la **ráfaga nativa** del móvil (256 frames si no se conoce). El búfer efectivo lo decide `BufferTuner`: empieza en dos bloques y solo crece cuando el `AudioTrack` cuenta underruns (`AudioTune` lo aplica con `setBufferSizeInFrames` y lo escribe en logcat con la etiqueta `ScratchAudio`). El hilo se duerme tras ~120 bloques de inactividad y se despierta al tocar la rueda o disparar un sonido.

## Hilos y cerrojo
`render()` y las órdenes que cambian el estado siguen siendo `synchronized`, pero lo que la interfaz llama muchas veces por segundo **no coge el cerrojo**:
- `rotate()` acumula el giro en un `AtomicLong` y el hilo de audio lo aplica al empezar el bloque siguiente;
- `getPositionSeconds()`, `getDurationSeconds()`, `getRate()`, `getLevel()`, `isPlaying()`, `isScratching()`, `isIdle()`, `hasSwapped()` y `snap()` leen campos `volatile` que se publican al final de cada bloque y de cada orden (`publish()`);
- el bucle de audio ya solo coge el cerrojo una vez por bloque (antes cuatro).

Así, mientras haces scratch la interfaz no puede retener el cerrojo y dejar esperando al hilo de audio (inversión de prioridad). Las órdenes puntuales (play, efectos, cues) lo cogen durante nanosegundos.

## Latencia y posición mostrada
El hilo de audio mide cada ~0,5 s la latencia de salida real con las marcas de tiempo del `AudioTrack` (frames escritos menos frames ya presentados) y se la pasa al motor. El audio no cambia: `getHeardSeconds()` devuelve la posición que el motor tenía hace esa latencia (historial de los últimos 256 bloques), que es lo que se está oyendo, y la usan la rueda, el tiempo, los pulsos y la letra. Mientras el dedo sujeta el disco, lo mostrado pasa gradualmente (0,15 s) a la posición del motor para que la rueda vaya pegada al dedo. En Ajustes → SONIDO se puede desactivar (NO / AUTO) y corregir a mano (ajuste fino de ±40 ms); ahí se ve la latencia medida.

## Cómo suena el scratch
Como un vinilo: el motor lee la pista a **velocidad variable** con un **filtro sinc enventanado** (Kaiser, 12 muestras por punto). A velocidad normal o menor usa coeficientes precalculados por fase; por encima de 1× el filtro se ensancha con la velocidad (corte más bajo, hasta 4×) para que los agudos que quedarían fuera de lo audible no se plieguen como aliasing. En silencio (velocidad 0) no se calcula nada. Mientras el dedo está en la rueda, la posición de lectura *persigue* a una posición objetivo que mueve el dedo:
- el motor estima la **velocidad del dedo** a partir de los eventos táctiles y su hora (`rotateAt`), con un filtro adaptativo: suave con el dedo lento, casi directo con el dedo rápido o ante un cambio brusco;
- entre evento y evento el objetivo se **extrapola** con esa velocidad, así la lectura no va a escalones (zumbido) ni con retraso; la distancia que quede se corrige con τ = 20 ms; todo limitado a ±12×;
- si dejan de llegar eventos (dedo parado), la velocidad estimada se apaga y la lectura acaba exactamente en el punto del dedo; si se había pasado, vuelve en silencio;
- por eso el **tono sube y baja** con la velocidad, suena **al revés** si giras hacia atrás y se **calla** si paras;
- una vuelta completa = 1,8 s de audio (33⅓ rpm); cambia con la sensibilidad (3,6 / 1,8 / 0,9 s).

## Plato
El motor distingue el **plato** (lo que mueve el motor) del **disco** (lo que se oye), unidos por el slipmat.
- **Arranque:** el plato sube en **rampa recta** (par constante, como un motor de tracción directa) hasta la velocidad pedida en 0,2 s, con un final suave de 20 ms. Los cambios de pitch siguen la misma rampa.
- **Slipmat:** cuando sueltas el disco con la música sonando, el plato ha seguido girando debajo y el rozamiento engancha el disco a razón de 1× cada 60 ms (desde parado, 60 ms; desde −2×, unos 180 ms).
- **Agarre del dedo:** al tocar un disco que gira no para en seco: frena con una constante de 4 ms durante los primeros 15 ms.
- **Frenado:** baja de forma lineal hasta parar (0,7 s por defecto), con caída de tono como un plato real.
- **Inercia:** si sueltas la rueda lanzada con la música parada, sigue girando con fricción exponencial (τ = 0,6 / 1,5 / 3 s según Alta/Media/Baja) más un rozamiento seco para que pare del todo.
- **Avance/retroceso rápido:** 1,5× con audio; velocidad ajustable.
- **Pitch:** velocidad de reproducción 0,5×–1,5× (el panel limita a ±20 %).

## Sonido de vinilo (opcional)
Ajustes → RUEDA → «Sonido de vinilo» (apagado por defecto). Añade la excentricidad del disco (el tono oscila ±0,12 % una vez por vuelta, a 33⅓ rpm), un flutter de ±0,03 % a 7,3 Hz, un siseo de surco muy bajo y chasquidos sueltos. Los osciladores van por rotación (sin senos por muestra) y el ruido sigue la velocidad del disco: al frenar baja y con el disco parado no suena.

## Mantener el tono (keylock)
Síntesis **granular (OLA)** con ventana Hann de 50 ms (precalculada en una tabla al fijar la frecuencia de salida) y solape del 50 %, hasta 4 granos simultáneos. Cada grano suena a velocidad normal (hacia delante o atrás según la rueda), así el tono no cambia aunque la posición avance más rápido o más lento. Desde la 0.60 es **WSOLA**: cada grano nuevo no empieza en un punto fijo, sino donde su onda mejor continúa la del grano anterior (correlación normalizada en ±6 ms), así los granos se suman en fase y desaparece el sonido «rugoso» y desafinado del OLA simple.

## Efectos (modo DJ)
Cadena por muestra: EQ de 3 bandas (biquads LP 250 Hz / HP 2,5 kHz) → filtro barrido → eco / echo-out → flanger → phaser → corte/puerta → sampler → medidor de nivel.
- Todos los biquads son RBJ de 2.º orden.
- Con todo en neutro no se procesa nada (`fxActive`).
- Cada orden recalcula solo sus coeficientes (`refreshGeq`, `refreshSweep`); el hilo de audio no crea objetos (hay un test que lo comprueba).
- El corte/puerta usa una rampa de duración fija (2 ms) con forma de S y derivadas continuas: flancos rápidos para el transformer y sin clics.

## Limitador suave
Cuando hay efectos, sampler o fundido cruzado activos, la suma puede pasarse del máximo de 16 bits. En vez de recortar en seco, por encima de −2,7 dB la cresta se redondea con una curva `tanh`. Por debajo de ese umbral, y siempre en la reproducción normal, la señal no se toca.

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
