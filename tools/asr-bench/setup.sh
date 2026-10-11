#!/usr/bin/env bash
# Build the benchmark's Python environment in $WORK/venv:
#   - sherpa-onnx v1.13.8 (the app's version) built from source with TTS,
#     speaker diarization, PortAudio and the binaries off. The PyPI wheel
#     links espeak-ng (GPL-3.0); this build never fetches it (ARCHITECTURE D3).
#     The app's patches (third_party/patches/sherpa-onnx and, since #53,
#     third_party/patches/kaldi-native-fbank, sherpa's feature extraction
#     dependency) are applied to copies of the clones first; a changed patch
#     set rebuilds the module.
#   - the pinned pure-Python dependencies in requirements.txt.
#
# Usage: WORK=/path/to/asr-bench-work tools/asr-bench/setup.sh
#        (copied out of the repo: PATCH_ROOT=<repo>/third_party/patches)
#        VENV=<name> builds $WORK/<name> instead of $WORK/venv, e.g. to
#        compare a patch set with the one in the shared venv.
# Needs: uv, cmake, a C++ compiler, network access (sherpa's CMake fetches
# its dependencies and the ONNX Runtime 1.28.2 linux-x64 build).
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory (models, data, venv, results)}"
JOBS="${JOBS:-8}"
SHERPA_TAG=v1.13.8
SHERPA_COMMIT=11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf
# The kaldi-native-fbank version sherpa v1.13.8's cmake/kaldi-native-fbank.cmake pins.
KNF_TAG=v1.22.3
KNF_COMMIT=b09e686fe2084732ddd30d1ef80acfc0f13eaf01
VENV="${VENV:-venv}"

mkdir -p "$WORK"
cd "$WORK"

if [ ! -d "$VENV" ]; then
  uv venv --python 3.12 "$VENV"
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"
uv pip install -r "$HERE/requirements.txt"

# The package's __init__ imports TTS classes this build leaves out, so load
# the native module directly, as asrbench/backends.py does.
NATIVE_IMPORT='import importlib.util, pathlib, sys
spec = importlib.util.find_spec("sherpa_onnx")
sys.path.insert(0, str(pathlib.Path(spec.submodule_search_locations[0]) / "lib"))
import _sherpa_onnx'

# PATCH_ROOT: the patch directories, for a harness copied out of the repo.
# Each may hold no patches (after a bump past all of them); both must exist.
PATCH_ROOT="${PATCH_ROOT:-$HERE/../../third_party/patches}"
test -d "$PATCH_ROOT/sherpa-onnx" && test -d "$PATCH_ROOT/kaldi-native-fbank"
shopt -s nullglob
SHERPA_PATCHES=("$PATCH_ROOT"/sherpa-onnx/*.patch)
KNF_PATCHES=("$PATCH_ROOT"/kaldi-native-fbank/*.patch)
shopt -u nullglob
patch_set="$( (cd "$PATCH_ROOT" && sha256sum sherpa-onnx/*.patch kaldi-native-fbank/*.patch 2>/dev/null || true) | sha256sum | cut -c1-16)"

# Copy a clone (keeping it at its tag) and apply patches to the copy, with
# --fuzz=0 as in the app's CMake: a patch whose context moved fails.
patched_copy() {  # <clone> <copy> <patches...>
  local src="$1" dst="$2"; shift 2
  rm -rf "$dst"
  cp -a "$src" "$dst"
  rm -rf "$dst/build"
  local p
  for p in "$@"; do
    patch -d "$dst" -p1 --forward --batch --fuzz=0 --no-backup-if-mismatch --quiet -i "$p"
  done
}

if ! python -c "$NATIVE_IMPORT" 2>/dev/null || [ "$(cat "$VENV/sherpa-patches" 2>/dev/null)" != "$patch_set" ]; then
  if [ ! -d sherpa-onnx ]; then
    git clone --depth 1 --branch "$SHERPA_TAG" https://github.com/k2-fsa/sherpa-onnx.git
  fi
  test "$(git -C sherpa-onnx rev-parse HEAD)" = "$SHERPA_COMMIT"
  if [ ! -d kaldi-native-fbank ]; then
    git clone --depth 1 --branch "$KNF_TAG" https://github.com/csukuangfj/kaldi-native-fbank.git
  fi
  test "$(git -C kaldi-native-fbank rev-parse HEAD)" = "$KNF_COMMIT"
  # One build directory per venv, so two venvs can be built side by side.
  build="$WORK/patched-$VENV"
  mkdir -p "$build"
  patched_copy sherpa-onnx "$build/sherpa-onnx" ${SHERPA_PATCHES[@]+"${SHERPA_PATCHES[@]}"}
  patched_copy kaldi-native-fbank "$build/kaldi-native-fbank" ${KNF_PATCHES[@]+"${KNF_PATCHES[@]}"}
  # sherpa's CMake builds the patched kaldi-native-fbank instead of downloading it.
  export SHERPA_ONNX_CMAKE_ARGS="-DSHERPA_ONNX_ENABLE_TTS=OFF -DSHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION=OFF \
    -DSHERPA_ONNX_ENABLE_PORTAUDIO=OFF -DSHERPA_ONNX_ENABLE_WEBSOCKET=OFF -DSHERPA_ONNX_ENABLE_BINARY=OFF \
    -DSHERPA_ONNX_ENABLE_C_API=OFF -DFETCHCONTENT_SOURCE_DIR_KALDI_NATIVE_FBANK=$build/kaldi-native-fbank"
  export SHERPA_ONNX_MAKE_ARGS="-j$JOBS"
  (cd "$build/sherpa-onnx" && nice -n 10 uv pip install --reinstall-package sherpa-onnx -v .)
  echo "$patch_set" > "$VENV/sherpa-patches"
fi

# The same guard as the app's check<Variant>NativeLicenses: no espeak-ng or
# piper code in anything this build produced.
site="$(python -c 'import sysconfig; print(sysconfig.get_path("purelib"))')"
if find "$site" -path '*sherpa*' -name '*.so*' -exec strings {} + | grep -qiP '(?<![a-z])espeak|piper'; then
  echo "GPL guard: espeak/piper strings found in the sherpa-onnx build" >&2
  exit 1
fi
python -c "$NATIVE_IMPORT"$'\n''print("sherpa-onnx native module ok, patches", open("'"$VENV"'/sherpa-patches").read().strip())'
