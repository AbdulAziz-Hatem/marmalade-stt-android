#!/usr/bin/env bash
# Builds the recognizer probe APK (README.md) without Gradle: javac, d8,
# aapt2, zipalign, apksigner with the debug keystore. Seconds, no network.
#
#   tools/recognizer-probe/build.sh [out.apk]    (default: build/recognizer-probe.apk here)
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
platform="$sdk/platforms/android-36/android.jar"
tools="$sdk/build-tools/36.0.0"
out="${1:-$here/build/recognizer-probe.apk}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

mkdir -p "$work/classes" "$(dirname "$out")"
javac --release 17 -Xlint:-options -nowarn -cp "$platform" -d "$work/classes" \
    $(find "$here/src" -name '*.java')
"$tools/d8" --min-api 28 --lib "$platform" --output "$work" $(find "$work/classes" -name '*.class')
"$tools/aapt2" link -o "$work/unaligned.apk" -I "$platform" --manifest "$here/AndroidManifest.xml"
(cd "$work" && zip -q unaligned.apk classes.dex)
"$tools/zipalign" -f 4 "$work/unaligned.apk" "$work/aligned.apk"
keystore="$HOME/.android/debug.keystore"
"$tools/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --out "$out" "$work/aligned.apk"
echo "$out"
