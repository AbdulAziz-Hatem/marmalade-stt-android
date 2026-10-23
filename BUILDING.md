# Building Marmalade STT

How to build the app from source, how its native speech runtime is put
together, and the developer engines. For what the app does, see the
[README](README.md); design decisions are in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Build

You need JDK 17 or newer and the Android SDK (platform 36) with
**NDK 26.3.11579264** and **CMake 3.22.1** (both from the SDK manager:
`sdkmanager "ndk;26.3.11579264" "cmake;3.22.1"`).

1. Clone with submodules, or fetch them afterwards. The native speech runtime
   is built from them, and so is the app's design library
   (`third_party/marmalade-ui-android`, the Gradle module `:marmalade-ui`
   that Marmalade TTS shares):

   ```
   git submodule update --init
   ```

2. Point Gradle at the SDK with a `local.properties` file in the repo root
   (it is gitignored):

   ```
   sdk.dir=/path/to/Android/Sdk
   ```

3. Run the unit tests and build a debug APK:

   ```
   ./gradlew test
   ./gradlew :app:assembleFdroidDebug
   ```

   There is one APK per ABI in `app/build/outputs/apk/fdroid/debug/`. On a
   phone, install `app-fdroid-arm64-v8a-debug.apk`. The x86_64 one is for
   the emulator.

   For a quicker test build, limit the ABIs with `-PabiFilters`, e.g.
   `./gradlew -PabiFilters=arm64-v8a :app:assembleFdroidDebug` for a phone
   (`arm64-v8a,x86_64` adds the emulator). It narrows both the native build
   and the per-ABI APKs, so sherpa-onnx compiles once instead of three
   times. Without it, every build type makes its usual ABIs. A variant left
   with no ABI fails to build: release with `-PabiFilters=x86_64` (release
   builds ship only ARM).

There are two flavors, `fdroid` and `play`. Today they build the same app;
they exist so store-specific differences have a place to go. Debug builds
install as `app.marmalade.stt.debug`, next to a release install.

## Release builds

Release builds (`./gradlew :app:assembleFdroidRelease`) are minified with R8.
They come as one APK per ABI (arm64-v8a and armeabi-v7a), each with its own
versionCode: the base code plus 1 for armv7 or 2 for arm64. The fdroid
flavor stores its native libraries compressed, which makes the download
smaller. The Play bundle (`./gradlew :app:bundlePlayRelease`) holds both ABIs
and uses the base versionCode. Build bundles in their own Gradle run: a run
that includes a `bundle` task switches the per-ABI split off.
They are unsigned unless a `keystore.properties` file sits at the repo root.

Release builds are reproducible: two clean builds give byte-identical APKs.
Build the APKs you publish without `-PabiFilters`, because a one-ABI build's
arm64 APK differs from the two-ABI build's (one line number in the manifest),
and F-Droid's rebuild compares bytes.
The dependency graph is pinned in `app/gradle.lockfile`; after changing a
dependency, regenerate it with `./gradlew :app:dependencies --write-locks`.

## Native speech runtime

The app runs speech recognition and voice-activity detection through
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), **built from source**
with TTS turned off. The prebuilt sherpa-onnx AAR statically links espeak-ng
(GPL-3.0), so it is never used (`docs/ARCHITECTURE.md`, D3).

- `third_party/sherpa-onnx` is pinned to v1.13.8. The eight libraries its
  CMake would otherwise download (kaldi-native-fbank, kissfft, kaldi-decoder,
  kaldifst, openfst, eigen, simple-sentencepiece, json) are submodules next to
  it, pinned to the exact versions sherpa-onnx asks for. The native build
  runs offline.
- Changes that aren't in a sherpa-onnx release (an upstream fix and our
  own patches) are patch files in `third_party/patches/sherpa-onnx/`, and
  one of ours for kaldi-native-fbank is in
  `third_party/patches/kaldi-native-fbank/`. Each README lists the patches
  and when to drop them. The build applies them to a copy of the sources in
  the CMake build tree; the submodules stay at their tags.
- `app/src/main/cpp/CMakeLists.txt` wraps it and produces one
  `libsherpa-onnx-jni.so` per ABI (arm64-v8a and armeabi-v7a; debug builds
  add x86_64 for the emulator).
- ONNX Runtime comes from Maven (`com.microsoft.onnxruntime:onnxruntime-android`).
  Gradle unpacks its headers and `libonnxruntime.so` for the native build
  (`extractOnnxRuntime`), so both sides always use the same version.
- The Kotlin API is vendored in `app/src/main/java/com/k2fsa/sherpa/onnx/`,
  unmodified except `OfflineStream.cancel`, which patch 0003 adds. Update it
  together with the submodule.
- **Guard:** `check<Variant>NativeLicenses` scans every `.so` in the built APK
  and fails the build on any `espeak` or `piper` string. It runs after every
  `assemble<Variant>` and as part of `./gradlew check`.

A clean native build compiles about 270 C++ files per ABI. On a machine with
little RAM, cap the parallel compiles: `./gradlew -PnativeJobs=4 …`.

On-device smoke test (loads the JNI library and runs the bundled Silero VAD):

```
./gradlew :app:connectedFdroidDebugAndroidTest
```

## Developer engines

Settings → Developer → **Developer engines** (off by default) adds the
models being tried out: today Granite Speech (IBM's Granite Speech 5.0 470M
TurboCTC; English, 552 MB). It also adds **Re-measure phone speed**, a
per-engine "Language for this engine" setting, and the **A/B benchmark**:
record up to 30 seconds (kept in memory, never saved), optionally type what
you said, and compare each installed engine's transcript, word error rate,
load and decode time, real-time factor and peak memory, one engine loaded at
a time; **Copy as Markdown** exports the table. Word error rates on public
test sets are in `docs/benchmarks/2026-09-host-wer.md`; the harness is
`tools/asr-bench/`.
