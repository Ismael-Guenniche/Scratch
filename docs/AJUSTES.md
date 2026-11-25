# Ajustes

Las opciones están agrupadas en tarjetas. Entre paréntesis, la clave de `SharedPreferences` (archivo `ui`) y el valor por defecto.

## Colores
| Opción | Clave | Por defecto |
|---|---|---|
| Tema: SISTEMA / CLARO / OSCURO | `mode` | 0 (sistema) |
| Colores del fondo de pantalla (Android 8.1+) | `wall` | no |
| Acento: 8 colores + «del fondo» | `accent` | 0 (naranja `#FB471F`) |

## Tipografía
`font`: Dot / Mono / Clean, con vista previa. Por defecto Dot con estilo Lucent.

## Rueda
| Opción | Clave | Por defecto |
|---|---|---|
| Carátula del disco | `cover` | no |
| Mantener el tono (keylock) | `keylock` | no |
| Sensibilidad LENTA / NORMAL / RÁPIDA (3,6 / 1,8 / 0,9 s por vuelta) | `wsens` | NORMAL |
| Inercia al soltar | `inertia` | sí |
| Sonido de vinilo | `vinyl` | no |
| Compensar la latencia NO / AUTO (Ajustes → SONIDO) | `latcomp` | AUTO |
| Ajuste fino de la latencia −40 / −20 / 0 / +20 / +40 ms | `lattrim` | 0 |
| Física del scratch CLÁSICA (0.51) / NUEVA (0.58–0.72) | `wphys` (0 / 1) | CLÁSICA |
| Fricción ALTA / MEDIA / BAJA | `wfric` | MEDIA |
| Invertir el sentido | `winvert` | no |
| Vibración al girar | `haptic` | sí |
| Bloquear scratch | `wlock` | no |

## Modo DJ
Interruptor por grupo de funciones; apagado = el grupo no aparece en el panel.

| Opción | Clave | Por defecto |
|---|---|---|
| Hot cues | `dj_cues` | sí |
| Bucles y roll | `dj_loop` | sí |
| Slip | `dj_slip` | sí |
| Saltos | `dj_jump` | sí |
| Efectos (flanger, phaser, trans, echo out, vinyl stop) | `dj_fx` | sí |
| Cut y reverse | `dj_cut` | sí |
| Vúmetro de puntos | `dj_meter` | sí |
| Modo disco | `dj_disco` | **no** |

Estado interno: `djmode` (modo DJ activo), `slip_on` (botón SLIP).

## Búsqueda
| Opción | Clave | Por defecto |
|---|---|---|
| Tolerancia a errores NO / POCA / NORMAL | `s_tol` (0 / 1 / 2) | NORMAL |
| Dónde buscar: título (y nombre de archivo), artista, álbum, carpeta | `s_fields` (bits 1 / 2 / 4 / 8) | todos (15) |
| Dentro de las palabras (4+ letras) | `s_inside` | sí |
| Ordenar por relevancia | `s_rel` | sí |

NORMAL: un error en palabras de 4–6 letras y dos desde 7; POCA: uno, solo desde 7 letras; NO: ninguno. Android Auto ordena siempre por relevancia. Detalles en `SmartSearch.java`.

## Copia de seguridad
- **Crear una copia** → `scratch-copia-AAAA-MM-DD.zip` con todo lo que hay en la carpeta de datos de la app (listas, favoritas, estadísticas, nombres editados, letras, portadas de listas, biblioteca) y los ajustes. Sin música ni cachés.
- **Restaurar una copia** → comprueba que es una copia de Scratch, la descomprime aparte y solo entonces sustituye; luego la app se cierra. Las listas apuntan a las canciones por su ruta: en otro móvil funcionan si la carpeta de música está en el mismo sitio.

## Listas con Claude (solo sabor personal)
- **Exportar la biblioteca** → `biblioteca-scratch.json`.
- **Importar una lista de Claude** → crea la lista (si el nombre existe, «Nombre (2)»). Ver [LISTAS-CLAUDE.md](LISTAS-CLAUDE.md).

## Permisos y privacidad
- **Notificaciones:** abre los ajustes de notificaciones del sistema para Scratch.
- **Política de privacidad:** lectura dentro de la app.
Claves internas de la bienvenida: `privacy_ok` (versión de la política aceptada), `onboarded`, `pick_pending` (abrir el selector de carpeta al entrar).

## Temporizador de apagado
NO / 15 / 30 / 60 / 90 min.

## Cristal
| Opción | Clave | Por defecto |
|---|---|---|
| Estilo Lucent (cristal) o tarjetas sólidas | `lucent` | sí |
| Rejilla de puntos de fondo | `grid` | sí |
| Desenfoque NO / SUAVE / NORMAL | `blur` | NORMAL |

## Icono de la app
8 variantes (`icon`): oscuro naranja, claro naranja, blanco Nothing, naranja sólido, oscuro cian, oscuro lima, oscuro violeta y monocromo. Todas son *activity-alias* del manifiesto con icono adaptable y capa monocromática para el tema de iconos del sistema.

## Otras
`stats_period` recuerda el periodo elegido en Estadísticas.
