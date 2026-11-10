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
| Sampler | `dj_sampler` | sí |
| Cut y reverse | `dj_cut` | sí |
| Vúmetro de puntos | `dj_meter` | sí |
| Modo disco | `dj_disco` | **no** |

Estado interno: `djmode` (modo DJ activo), `slip_on` (botón SLIP).

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
