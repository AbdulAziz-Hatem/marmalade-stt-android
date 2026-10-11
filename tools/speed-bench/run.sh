#!/usr/bin/env bash
# Start a benchmark run after checking the phone is fit for it, wait for it,
# and pull the results JSON into OUT (default ./results).
# SKIP_CHECK=1 skips phone-ok.sh (pair it with --ez unplugged true off the charger).
# Usage: run.sh [am extras for cmd run, e.g. --es engines a,b --es threads 2,4 --es tag pixel]
set -euo pipefail
here=$(dirname "$0"); . "$here/common.sh"
OUT=${OUT:-results}
[[ ${SKIP_CHECK:-} == 1 ]] || "$here/phone-ok.sh" || { echo "phone not fit for a run; not starting"; exit 2; }
since=$(logmark)
bench --es cmd run "$@"
line=$(await_log "$since" "done |ABORTED|FAILED|cancelled" "${TIMEOUT:-14400}")
echo "$line"
$ADB logcat -d -T "$since" -s SpeedBench:I | grep -E "@[0-9]+ load=" || true
file=$(grep -oE 'done [^ ]+' <<<"$line" | cut -d' ' -f2 || true)
[[ -n $file ]] || exit 1
mkdir -p "$OUT"
runas cat "$file" > "$OUT/$(basename "$file")"
echo "pulled $OUT/$(basename "$file")"
