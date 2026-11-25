#!/usr/bin/env bash
# Compila Scratch para escritorio (Windows, y también Linux/macOS para probar) sin Gradle ni Android SDK.
#   desktop/build.sh            -> desktop/build/Scratch.jar
#   desktop/build.sh run        -> compila y ejecuta
#   FLAVOR=personal desktop/build.sh   -> incluye el japonés experimental
# Requisitos: JDK 17 o superior.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=desktop/build
CLS=$OUT/classes
FLAVOR="${FLAVOR:-play}"
rm -rf "$CLS"; mkdir -p "$CLS"

APP=app/src/main/java/dev/scratchwheel
# Las clases de la app, salvo las que tienen versión propia de escritorio en desktop/src/app.
SRC=()
for f in "$APP"/*.java; do
  [ -f "desktop/src/app/dev/scratchwheel/$(basename "$f")" ] || SRC+=("$f")
done
SRC+=("app/src/$FLAVOR/java/dev/scratchwheel/TrJa.java" "app/src/$FLAVOR/java/dev/scratchwheel/Flavor.java")
while IFS= read -r f; do SRC+=("$f"); done < <(find desktop/src desktop/third_party -name '*.java' | sort)

javac -nowarn -encoding UTF-8 --release 17 -d "$CLS" "${SRC[@]}" 2>&1 | grep -v -e JAVA_TOOL_OPTIONS -e '^Note:' || true
[ -f "$CLS/dev/scratchwheel/DesktopMain.class" ] || { echo "ERROR de compilación" >&2; exit 1; }

# Recursos: fuentes, iconos y las tablas del decodificador de MP3.
mkdir -p "$CLS/assets" "$CLS/res"
cp -r app/src/main/assets/. "$CLS/assets/"
for d in drawable mipmap-nodpi mipmap-xxxhdpi; do
  [ -d "app/src/main/res/$d" ] && mkdir -p "$CLS/res/$d" && cp app/src/main/res/$d/*.png "$CLS/res/$d/" 2>/dev/null || true
done
cp desktop/third_party/jlayer/javazoom/jl/decoder/*.ser "$CLS/javazoom/jl/decoder/"
VN=$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle | head -1)
VC=$(sed -n "s/.*versionCode \([0-9]*\).*/\1/p" app/build.gradle | head -1)
echo "$VN $VC" > "$CLS/desk-version.txt"

printf 'Main-Class: dev.scratchwheel.DesktopMain\nImplementation-Version: %s\n' "$VN" > "$OUT/manifest.txt"
jar cfm "$OUT/Scratch.jar" "$OUT/manifest.txt" -C "$CLS" .
echo "OK: $OUT/Scratch.jar (Scratch $VN)"
if [ "${1:-}" = run ]; then
  exec java -XX:+UseSerialGC -Xss512k -jar "$OUT/Scratch.jar"
fi
