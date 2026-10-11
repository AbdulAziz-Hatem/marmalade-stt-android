#!/usr/bin/env bash
# Run the whole benchmark: every engine on every set it supports, then score.
# Resumable: finished utterances are skipped, so rerunning after an
# interruption continues where it stopped.
#
# Usage: WORK=/path/to/asr-bench-work tools/asr-bench/run_all.sh
# PARALLEL jobs (default 3) each run one recognizer with 2 ONNX Runtime
# threads, the app's setting, at nice 10: about 6-7 busy cores in total.
# RESULTS (default results) is the output directory under $WORK.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory}"
PARALLEL="${PARALLEL:-3}"
RESULTS="${RESULTS:-results}"
PY="$WORK/venv/bin/python"
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

EN="ls-clean ls-other fleurs-en ami-ihm dictation noise"
MULTI="fleurs-es fleurs-de fleurs-fr"

# One line per job: run.py arguments. Slowest first so the tail is short.
jobs() {
  # Buffered streaming decodes slower than real time on the host (#25): 50 utterances per set.
  echo "--engine parakeet-unified-en-streaming-560ms --limit 50 $EN"
  for e in qwen3-asr-0-6b whisper-turbo; do
    echo "--engine $e $EN $MULTI"
  done
  for e in parakeet-tdt-0-6b-v3 canary-180m-flash nemotron-3-5-streaming-320ms nemotron-3-5-streaming-560ms; do
    echo "--engine $e $EN $MULTI"
  done
  for e in nemotron-en-streaming-560ms whisper-small-en distil-small-en whisper-base-en whisper-tiny-en \
           parakeet-tdt-0-6b-v2 parakeet-unified-en-0-6b parakeet-tdt-110m-en; do
    echo "--engine $e $EN"
  done
  # Auto-detect (no hint) for the engines whose app default is auto.
  for e in qwen3-asr-0-6b whisper-turbo nemotron-3-5-streaming-320ms nemotron-3-5-streaming-560ms; do
    echo "--engine $e --auto fleurs-en $MULTI"
  done
  # No VAD: the noise clips straight into every model.
  for e in qwen3-asr-0-6b whisper-turbo parakeet-tdt-0-6b-v3 canary-180m-flash nemotron-3-5-streaming-320ms \
           nemotron-3-5-streaming-560ms nemotron-en-streaming-560ms whisper-small-en distil-small-en \
           whisper-base-en whisper-tiny-en parakeet-tdt-0-6b-v2 parakeet-unified-en-0-6b parakeet-tdt-110m-en \
           parakeet-unified-en-streaming-560ms; do
    echo "--engine $e --direct noise"
  done
}

mkdir -p "$WORK/logs"
cd "$HERE"
jobs | xargs -P "$PARALLEL" -I{} sh -c \
  'nice -n 10 "$0" -m asrbench.run --work "$1" --results "$3" $2 >> "$1/logs/$3-$(echo "$2" | tr " " "_" | cut -c1-80).log" 2>&1' \
  "$PY" "$WORK" {} "$RESULTS"
"$PY" -m asrbench.score --work "$WORK" --results "$RESULTS"
