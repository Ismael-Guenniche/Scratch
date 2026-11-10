#!/bin/bash
# Compila Scratch a APK sin Android SDK: javac + dx + AXML/ARSC a mano + jarsigner.
set -euo pipefail
ROOT=/home/claude
SRC=$ROOT/scratchwheel/app/src/main/java
JAR=$ROOT/sdkjar/android.jar
DX=$ROOT/apkbuild/dx
W=$ROOT/apkbuild/work
rm -rf $W && mkdir -p $W/classes $W/apk

javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath $JAR -classpath $JAR \
      -d $W/classes $(find $SRC -name '*.java') 2>&1 | grep -v -e JAVA_TOOL -e 'warning' || true
java -cp $DX com.android.dx.command.Main --dex --min-sdk-version=24 \
     --output=$W/apk/classes.dex $W/classes 2>&1 | grep -v JAVA_TOOL || true
python3 $ROOT/apkbuild/make_res.py $W/apk

cd $W/apk
rm -f ../unsigned.apk
zip -q -0 ../unsigned.apk resources.arsc res/drawable/ic_launcher.png
mkdir -p assets && cp -r $ROOT/scratchwheel/app/src/main/assets/fonts assets/
zip -q -9 ../unsigned.apk AndroidManifest.xml classes.dex assets/fonts/*

KS=$ROOT/apkbuild/scratch.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -storepass scratchwheel -keypass scratchwheel \
   -alias scratch -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Scratch" 2>&1 | grep -v JAVA_TOOL || true
cp ../unsigned.apk ../Scratch.apk
jarsigner -keystore $KS -storepass scratchwheel -sigalg SHA256withRSA -digestalg SHA-256 \
   ../Scratch.apk scratch 2>&1 | grep -v JAVA_TOOL || true
ls -la ../Scratch.apk
