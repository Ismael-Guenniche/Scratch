#!/usr/bin/env bash
# Ejecuta TODOS los tests del motor y la regresión de rendimiento en la JVM (sin Android SDK).
#   tools/run-all.sh            tests + regresión (falla si un escenario cuesta >30 % más que la línea base)
#   tools/run-all.sh baseline   tests + guarda una línea base nueva (tools/bench-baseline.txt)
#   tools/run-all.sh full       tests + regresión + todos los benchmarks (para docs/BENCHMARKS.md)
# Requisitos: JDK 17+ y, para el test de MP3, ffmpeg (si no está, ese test se salta).
set -e
cd "$(dirname "$0")/.."
S=app/src/main/java/dev/scratchwheel
B="${TMPDIR:-/tmp}/scratch-tests"
SEP=":"; case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";";; esac
rm -rf "$B"; mkdir -p "$B/cls" "$B/out" "$B/mp3"
# Clases sin Android (el motor y lo que usan los tests) + sustituto de Tr.
javac -nowarn -encoding UTF-8 -d "$B/cls" tools/stubs/dev/scratchwheel/Tr.java \
  $S/{BpmDetect,ShuffleOrder,DotFont,DemoTrack,PcmData,ScratchEngine,BufferTuner,SeekSpeed,Stats,WavParser,DotIcons,Playlists,SpotifyImport,TrackEdits,BigLayout,Waveform,LyricsReader,M3u,WavRecorder}.java
javac -nowarn -encoding UTF-8 -cp "$B/cls" -d "$B/cls" tools/Check.java tools/CheckXfade.java tools/CheckLists.java tools/Bench*.java
MP3=""
if command -v ffmpeg >/dev/null 2>&1; then
  ffmpeg -v error -y -i tools/test-mp3/test.mp3 -f s16le -ac 2 "$B/mp3/test.pcm"
  ffmpeg -v error -y -i tools/test-mp3/mono22.mp3 -f s16le "$B/mp3/mono.pcm"
  MP3="$B/mp3"
fi
java -cp "$B/cls" Check "$B/out" $MP3 | grep -v "^OK" || true
java -cp "$B/cls" Check "$B/out" $MP3 | tail -1 | grep -q "TODO OK"
java -cp "$B/cls" dev.scratchwheel.CheckXfade | tail -1
java -cp "$B/cls" dev.scratchwheel.CheckLists | tail -1
if [ "$1" = baseline ]; then
  java -cp "$B/cls" Bench baseline tools/bench-baseline.txt
else
  java -cp "$B/cls" Bench regress tools/bench-baseline.txt 30
fi
if [ "$1" = full ]; then
  java -cp "$B/cls" Bench; java -Xint -cp "$B/cls" Bench alloc
  java -cp "$B/cls" BenchFeel; java -cp "$B/cls" BenchQuality; java -cp "$B/cls" BenchPlatter; java -cp "$B/cls" BenchCut
fi
echo "TODO CORRECTO"
