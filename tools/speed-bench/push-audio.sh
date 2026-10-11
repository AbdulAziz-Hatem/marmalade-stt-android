#!/usr/bin/env bash
# Copy the benchmark WAVs (+ .txt references) into the app's files/bench-audio/.
# Usage: push-audio.sh [dir]   (default $BENCH_AUDIO, the stt-bench-audio dir)
set -euo pipefail
. "$(dirname "$0")/common.sh"
dir=${1:-${BENCH_AUDIO:?pass the audio dir or set BENCH_AUDIO}}
runas mkdir -p files/bench-audio
for f in "$dir"/*.wav "$dir"/*.txt; do
  [[ -f $f ]] || continue
  runas_write "files/bench-audio/$(basename "$f")" "$(stat -c %s "$f")" < "$f"
done
runas ls -l files/bench-audio
