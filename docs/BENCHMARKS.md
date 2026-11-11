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
