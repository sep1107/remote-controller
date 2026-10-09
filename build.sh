#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
: "${ANDROID_SDK_ROOT:=$HOME/Library/Android/sdk}"
: "${JAVA_HOME:?Set JAVA_HOME to a JDK 17 installation}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
tools="$ANDROID_SDK_ROOT/build-tools/33.0.2"
android="$ANDROID_SDK_ROOT/platforms/android-33/android.jar"
work="$(mktemp -d)"
mkdir -p "$work/classes" "$work/dex"
"$tools/aapt2" compile --dir res -o "$work/resources.zip"
"$tools/aapt2" link -o "$work/base.apk" -I "$android" --manifest AndroidManifest.xml "$work/resources.zip"
javac -encoding UTF-8 --release 8 -classpath "$android" -d "$work/classes" src/fun/hpqq/kindleremote/*.java
jar cf "$work/classes.jar" -C "$work/classes" .
"$tools/d8" --lib "$android" --min-api 26 --output "$work/dex" "$work/classes.jar"
cp "$work/base.apk" "$work/unsigned.apk"
(cd "$work/dex" && zip -q "$work/unsigned.apk" classes.dex)
"$tools/zipalign" -f 4 "$work/unsigned.apk" "$work/aligned.apk"
# Local test signing only; keep a supplied keystore outside this source project.
key="${KINDLE_DEBUG_KEYSTORE:-${TMPDIR:-/tmp}/kindle-remote-debug.keystore}"
if [ ! -f "$key" ]; then
  keytool -genkeypair -keystore "$key" -storepass android -keypass android -alias androiddebugkey \
    -dname 'CN=Kindle Remote Debug' -keyalg RSA -keysize 2048 -validity 10000 -noprompt
fi
mkdir -p dist
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --key-pass pass:android \
  --out dist/RemoteController-0.3.2-debug.apk "$work/aligned.apk"
"$tools/apksigner" verify dist/RemoteController-0.3.2-debug.apk
printf 'APK: %s/dist/RemoteController-0.3.2-debug.apk\nBuild workspace: %s\n' "$PWD" "$work"
