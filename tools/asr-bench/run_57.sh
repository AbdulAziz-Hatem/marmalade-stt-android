#!/usr/bin/env bash
# #57: the stock phrases each user engine writes for noise, per language hint.
# Every engine in the app's catalog that writes free text decodes the noise
# and noise-short clips whole (--direct: what a VAD false trigger hands it),
# once per language hint the app offers it (Qwen3: the developer list), with
# the AGC stand-in and without it. Then the first 100 clips of each FLEURS
# set an engine is offered for, with that language picked, through the
# app's VAD session: what it writes for the lead-in before speech, with each
# Final's level (also without the AGC stand-in, with the app's SoftAgc, for
# Japanese). Then the phantom tables (asrbench.phantoms). Resumable like
# run_all.sh. 3 jobs x 2 threads at nice 19: the benchmark host is shared.
#
# Needs: make_synthetic noise noise-short. ACFT=<dir> adds #55's ACFT turbo
# (with its 256-frame window) from that model directory.
#
# Usage: WORK=/path/to/asr-bench-work RESULTS=results-57 [ACFT=$WORK/models-55/v-acft-h8g8] tools/asr-bench/run_57.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the scratch directory}"
PARALLEL="${PARALLEL:-3}"
RESULTS="${RESULTS:-results-57}"
PY="${PY:-$WORK/venv/bin/python}"
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1 MKL_NUM_THREADS=1

SETS="noise noise-short"
# SttEngineCatalog.kt: WHISPER_TURBO_HINTS, Qwen3's developer MULTILINGUAL_HINTS,
# Canary's four, NEMOTRON_35_HINTS. "auto" is no hint.
TURBO="auto en de es fr it pt nl pl ru ja ko zh hi ar tr vi nb"
QWEN3="auto en de es fr it pt nl pl ru ja ko zh"
CANARY="en es de fr"
NEMOTRON="auto hi ar vi"

hint() { if [ "$1" = auto ]; then echo "--auto"; else echo "--hint $1"; fi; }

jobs() {
  for agc in "" "--no-agc"; do
    for h in $TURBO; do echo "--engine whisper-turbo --direct $agc $(hint "$h") $SETS"; done
    for h in $QWEN3; do echo "--engine qwen3-asr-0-6b --direct $agc $(hint "$h") $SETS"; done
    for h in $CANARY; do echo "--engine canary-180m-flash --direct $agc $(hint "$h") $SETS"; done
    for h in $NEMOTRON; do echo "--engine nemotron-3-5-streaming-1120ms --direct $agc $(hint "$h") $SETS"; done
    for e in parakeet-ja-ctc parakeet-ultra parakeet-tdt-110m-en; do echo "--engine $e --direct $agc $SETS"; done
    if [ -n "${ACFT:-}" ]; then
      for h in $TURBO; do
        echo "--engine whisper-turbo --model-dir $ACFT --variant acft --whisper-tail-paddings 256 --direct $agc $(hint "$h") $SETS"
      done
    fi
  done
  for h in en de es fr ja zh ko hi ar tr vi nb; do echo "--engine whisper-turbo --limit 100 fleurs-$h"; done
  echo "--engine qwen3-asr-0-6b --limit 100 fleurs-zh fleurs-ko fleurs-ja"
  echo "--engine nemotron-3-5-streaming-1120ms --limit 100 fleurs-hi fleurs-ar fleurs-vi"
  echo "--engine canary-180m-flash --limit 100 fleurs-en fleurs-es fleurs-de fleurs-fr"
  echo "--engine parakeet-ultra --limit 100 fleurs-en fleurs-es fleurs-de fleurs-fr"
  echo "--engine parakeet-ja-ctc --limit 100 fleurs-ja"
  # A phone without a platform AGC (the Pixel): the lead-in's level as the app judges it.
  echo "--engine whisper-turbo --limit 100 --no-agc --soft-agc fleurs-ja"
  echo "--engine qwen3-asr-0-6b --limit 100 --no-agc --soft-agc fleurs-ja"
}

mkdir -p "$WORK/logs"
cd "$HERE"
jobs | xargs -P "$PARALLEL" -I{} sh -c \
  'nice -n 19 "$0" -m asrbench.run --work "$1" --results "$3" $2 >> "$1/logs/$3-$(echo "$2" | tr " /" "__" | cut -c1-120).log" 2>&1' \
  "$PY" "$WORK" {} "$RESULTS"
"$PY" -m asrbench.phantoms --work "$WORK" --results "$RESULTS"
"$PY" -m asrbench.phantoms --work "$WORK" --mine $(cd "$WORK" && ls -d "$RESULTS"/*/)
