#!/usr/bin/env bash
# #37: does Nemotron 3.5 stay? Whisper turbo (with the byte-level BPE fix,
# third_party/patches/sherpa-onnx) vs Nemotron 3.5 560 / 1120 ms vs Qwen3-ASR
# on the CJK sets and on the five most-spoken languages Nemotron covers and
# Parakeet doesn't (hi, ar, tr, vi, nb), hinted and auto. Then score.
# Resumable like run_all.sh. 4 jobs x 2 threads at nice 19: the benchmark host is shared.
#
# Usage: WORK=/path/to/asr-bench-work RESULTS=results-37 tools/asr-bench/run_37.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory}"
PARALLEL="${PARALLEL:-4}"
RESULTS="${RESULTS:-results-37}"
PY="$WORK/venv/bin/python"
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

CJK="fleurs-ja fleurs-zh aishell1 fleurs-ko zeroth-ko"
WORLD="fleurs-hi fleurs-ar fleurs-tr fleurs-vi fleurs-nb"
ENGINES="whisper-turbo qwen3-asr-0-6b nemotron-3-5-streaming-560ms nemotron-3-5-streaming-1120ms"

jobs() {
  for e in $ENGINES; do echo "--engine $e $CJK"; done
  for e in $ENGINES; do echo "--engine $e $WORLD"; done
  for e in $ENGINES; do echo "--engine $e --auto $WORLD"; done
}

mkdir -p "$WORK/logs"
cd "$HERE"
jobs | xargs -P "$PARALLEL" -I{} sh -c \
  'nice -n 19 "$0" -m asrbench.run --work "$1" --results "$3" $2 >> "$1/logs/$3-$(echo "$2" | tr " " "_" | cut -c1-80).log" 2>&1' \
  "$PY" "$WORK" {} "$RESULTS"
"$PY" -m asrbench.score --work "$WORK" --results "$RESULTS"
