# Compilar y probar

## Con Android Studio (lo normal)
Requisitos: **JDK 17+**, **Android Gradle Plugin 8.10**, **Gradle 8.14**, **compileSdk 35**, `minSdk 24`, `targetSdk 28`.

1. Abre la carpeta del proyecto en Android Studio y espera al *sync*.
2. Activa la depuración USB en el móvil y pulsa *Run*.
3. En la pantalla de inicio y en Ajustes debe verse la versión («BETA 0.27» / «SCRATCH 0.27»); si no, estás ejecutando una build antigua.

Hay dos sabores (*product flavors*): **`play`** (el que se publica; sin japonés) y **`personal`** (uso propio; añade el japonés experimental). En Android Studio se elige en *Build Variants* (`personalDebug`, `playDebug`, `playRelease`).

Por línea de comandos: `./gradlew assemblePersonalDebug` (o `assemblePlayDebug`) (necesita `local.properties` con `sdk.dir`).

> La versión de la app está en `app/build.gradle` (`versionCode` / `versionName`); súbela en cada entrega para distinguir builds.

## Tests del motor, sin Android
Los tests usan solo `android.jar` para compilar (no se ejecuta Android):

```bash
J=ruta/a/android.jar        # p. ej. $ANDROID_HOME/platforms/android-35/android.jar
mkdir -p /tmp/cls /tmp/chk /tmp/out /tmp/mp3
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath $J -classpath $J \
      -d /tmp/cls $(find app/src/main/java -name '*.java')
javac -cp /tmp/cls -d /tmp/chk tools/Check.java
java  -cp /tmp/chk:/tmp/cls Check /tmp/out /tmp/mp3
```

Debe terminar con **`TODO OK`**. Cubre el motor de audio (scratch, tono, inercia, efectos, gimmicks DJ), el lector de WAV, las estadísticas y la velocidad de avance. No prueba la interfaz.

## APK sin Android SDK
`build-sin-sdk/build.sh` y `make_res.py` generan un APK instalable solo con `javac`, `dx` y utilidades de zip/firma, escribiendo a mano el manifiesto binario y los recursos. Es un camino de emergencia usado durante el desarrollo; las rutas dentro del script son las del equipo donde se creó y hay que adaptarlas.

## Iconos
`tools/make_icons.py` genera los 8 iconos de la app (los PNG de vista previa están en `icons/` y los recursos en `app/src/main/res/`). Revisa las rutas de salida del script antes de ejecutarlo.

## Instalar y primera vez
1. Instala el APK, abre Scratch y pulsa **ELEGIR CARPETA** para dar acceso a tu música.
2. Si la biblioteca sale vacía, usa menú ⋮ → **DIAGNÓSTICO**.
3. Sin música todavía, la app incluye una pista de prueba sintetizada (`DemoTrack`).
