# Perfilado del audio en el móvil (T19)

Sirve para ver en el Nothing Phone (4a) lo que la JVM no puede medir: cuánto tarda de verdad cada bloque, si el hilo de audio se queda esperando y qué lo interrumpe.

## 1. Lo rápido: logcat
```bash
adb logcat -s ScratchAudio
```
Al arrancar el audio sale: frecuencia, ráfaga nativa, tamaño de bloque, capacidad, **ruta rápida o normal** y búfer en frames y ms. Cada vez que hay underruns y el búfer crece, sale otra línea. Si tras un minuto de scratch no hay líneas nuevas, no ha habido cortes.

## 2. Estado del mezclador
```bash
adb shell dumpsys media.audio_flinger > flinger.txt
```
Con una canción sonando. Busca la pista de `dev.scratchwheel`: la marca `F` (FAST) indica ruta de baja latencia; `Underruns` debe quedarse quieto.

## 3. Trace con Perfetto (lo completo)
1. En el móvil: Ajustes → Sistema → Opciones de desarrollador → **Rastreo del sistema** (System Tracing).
2. Categorías: `audio`, `sched`, `freq`, `idle`, `view`, `input`, `binder_driver`. Activa «Rastrear apps depurables» y tamaño de búfer 32 MB o más.
3. Pulsa **Grabar rastreo**, abre Scratch y durante ~20 s: reproduce, haz scratch rápido, suelta, activa keylock y efectos del modo DJ, salta de canción.
4. Para la grabación y comparte el archivo `.perfetto-trace` (o sácalo con `adb pull /data/local/traces/`).
5. Ábrelo en <https://ui.perfetto.dev> o pásamelo.

Alternativa por línea de comandos:
```bash
adb shell perfetto -o /data/misc/perfetto-traces/scratch.pftrace -t 20s sched freq idle audio view input binder_driver
adb pull /data/misc/perfetto-traces/scratch.pftrace
```

## 4. Qué mirar
- **Hilo `audio` de `dev.scratchwheel`**: debe despertarse a ritmo fijo (cada ráfaga) y ejecutar unos cientos de microsegundos. Huecos más largos que un bloque = corte.
- **Estado del hilo cuando no corre**: `Runnable` mucho tiempo = le quitan la CPU (prioridad o núcleos pequeños); `Sleeping`/`Blocked` dentro de `render` = espera de un cerrojo (no debería pasar desde la 0.54) o del recolector de basura.
- **Frecuencia de CPU** (`freq`): si el núcleo del hilo de audio está a frecuencia mínima, el bloque tarda más; es la razón habitual de cortes al empezar a tocar.
- **Hilo principal (`main`)**: cuánto tarda cada `Choreographer#doFrame` mientras giras la rueda; por encima de 8 ms a 120 Hz la rueda va a tirones aunque el audio vaya bien.
- **Latencia dedo → sonido**: distancia entre el evento `input` (`deliverInputEvent`) y el siguiente despertar del hilo `audio`, más el búfer que diga logcat.
- **`AudioTrack` nuevo**: si al tocar tras una pausa aparece creación de pista (`createTrack`), ese es el coste de despertar el audio.
