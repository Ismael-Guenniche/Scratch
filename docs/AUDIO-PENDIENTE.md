# Plan de audio T1–T20: estado y pendientes

Todo lo que se podía verificar en la JVM está hecho y medido (ver [BENCHMARKS.md](BENCHMARKS.md)). Lo que sigue necesita compilar con el SDK de Android y probar en un móvil; hacerlo a ciegas rompería la regla de «no romper lo que funciona».

| Tarea | Estado |
|---|---|
| T1–T8 | Hechas (0.52–0.59) |
| T9 PCM en disco | Hecha (0.65). Falta probar en el móvil con canciones largas |
| T10 Precarga con menos memoria | Hecha (0.65) |
| T11 Caché de BPM y de forma de onda | Hecha (0.65); el BPM ya se guardaba |
| T12 Ahorro de batería | **Parcial.** El motor en reposo cuesta 0,8 µs por bloque (antes 6,8) y no calcula el filtro en silencio. Falta mantener el `AudioTrack` en pausa en vez de destruirlo y bajar el ticker; hay que medir consumo en el móvil |
| T13 Oboe/AAudio | **Evaluado, no implementado.** Por CPU no compensa (el motor usa < 1 % del plazo). Solo compensaría por latencia: decidirlo con los datos de `adb logcat -s ScratchAudio` (si ya sale «ruta rápida» y el búfer se queda en 2–3 ráfagas, la ganancia de Oboe sería de pocos ms). Requiere NDK |
| T14 Efectos por bloque | Hecha (0.64), con el mismo sonido bit a bit |
| T15 Keylock WSOLA | Hecha (0.60) |
| T16 Calibración de latencia | Hecha (0.66) con medida automática y ajuste fino manual; sin loopback por micrófono |
| T17 Sonido de vinilo | Hecha (0.62) |
| T18 Regresión de rendimiento | Hecha (0.61) |
| T19 Perfilado en móvil | Guía en [PERFILADO.md](PERFILADO.md); falta que captures el trace |
| T20 Mejora propia | Hecha (0.63): limitador suave |

## T20: las tres propuestas
1. **PCM en disco con ventana en RAM (es la T9 bien hecha).** La de más valor: quita el límite de 7,5 min y baja la RAM de ~42 MB por canción a unos pocos MB. No se puede validar sin móvil.
2. **Muestras táctiles históricas** (`getHistoricalX/Y` y su hora): la rueda pasaría de 120 a 240 eventos por segundo en pantallas con muestreo alto; con el filtro de T5 bajaría aún más el rizado. Cambio pequeño en `JogWheelView`, pero hay que probarlo con el dedo.
3. **Limitador suave de salida.** La de menos valor de las tres, pero la única verificable sin móvil: es la que se ha hecho.

## Antes de publicar
Desde la 0.64 todo se compila con `javac` contra el `android.jar` del SDK 35 (los dos sabores, `play` y `personal`), pero no se ha generado un APK ni se ha ejecutado en un móvil. **Hay que probar en el móvil** antes de subir nada a Google Play, sobre todo la carga de canciones (0.65).
