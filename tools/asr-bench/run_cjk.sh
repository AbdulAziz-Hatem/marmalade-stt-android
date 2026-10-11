#!/usr/bin/env bash
# #29: SenseVoice-Small vs Nemotron 3.5 (with Whisper turbo and Qwen3 as
# references) on the Japanese, Chinese and Korean sets, then score.
# Resumable like run_all.sh. Runs at nice 19: the benchmark host is shared.
#
# Usage: WORK=/path/to/asr-bench-work RESULTS=results-29 tools/asr-bench/run_cjk.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory}"
PARALLEL="${PARALLEL:-3}"
RESULTS="${RESULTS:-results-29}"
PY="$WORK/venv/bin/python"
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

CJK="fleurs-ja fleurs-zh aishell1 fleurs-ko zeroth-ko"

jobs() {
  for e in whisper-turbo qwen3-asr-0-6b nemotron-3-5-streaming-320ms nemotron-3-5-streaming-560ms sense-voice-small; do
    echo "--engine $e $CJK"
  done
  for e in nemotron-3-5-streaming-320ms nemotron-3-5-streaming-560ms sense-voice-small; do
    echo "--engine $e --auto fleurs-ja fleurs-zh fleurs-ko"
  done
  echo "--engine sense-voice-small noise"
  echo "--engine sense-voice-small --direct noise"
}

mkdir -p "$WORK/logs"
cd "$HERE"
jobs | xargs -P "$PARALLEL" -I{} sh -c \
  'nice -n 19 "$0" -m asrbench.run --work "$1" --results "$3" $2 >> "$1/logs/$3-$(echo "$2" | tr " " "_" | cut -c1-80).log" 2>&1' \
  "$PY" "$WORK" {} "$RESULTS"
"$PY" -m asrbench.score --work "$WORK" --results "$RESULTS"
