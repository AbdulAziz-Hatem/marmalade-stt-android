#!/usr/bin/env bash
# #52: Japanese engines on FLEURS-ja, the Kokoro Speech Dataset and the
# homophone set: the app's Japanese options (Whisper turbo, Nemotron 3.5
# 1120 ms, Qwen3-ASR 0.6B, SenseVoice-Small) and the candidates (Parakeet-ja
# CTC, ReazonSpeech k2-v2, Fun-ASR-Nano, Kotoba-Whisper v2.0, Qwen3-ASR 1.7B).
# Then score. Resumable like run_all.sh. 3 jobs x 2 threads at nice 19:
# the benchmark host is shared.
#
# Usage: WORK=/path/to/asr-bench-work RESULTS=results-52 tools/asr-bench/run_52.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory}"
PARALLEL="${PARALLEL:-3}"
RESULTS="${RESULTS:-results-52}"
PY="$WORK/venv/bin/python"
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

ENGINES="whisper-turbo nemotron-3-5-streaming-1120ms qwen3-asr-0-6b sense-voice-small parakeet-ja-ctc
reazonspeech-k2-v2 funasr-nano kotoba-whisper-v2-0 qwen3-asr-1-7b"

jobs() {
  for e in $ENGINES; do echo "--engine $e homophones-ja fleurs-ja kokoro-speech-ja"; done
}

mkdir -p "$WORK/logs"
cd "$HERE"
jobs | xargs -P "$PARALLEL" -I{} sh -c \
  'nice -n 19 "$0" -m asrbench.run --work "$1" --results "$3" $2 >> "$1/logs/$3-$(echo "$2" | tr " " "_" | cut -c1-80).log" 2>&1' \
  "$PY" "$WORK" {} "$RESULTS"
"$PY" -m asrbench.score --work "$WORK" --results "$RESULTS"
