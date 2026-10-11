#!/usr/bin/env bash
# Stage catalog model files on the phone without the app downloading them, then
# let the app's installer verify every SHA-256 and install. Files stream from
# MODEL_SRC (an ssh host:dir or a local dir with <engine-id>/<file name>), over
# adb, into files/models/<id>.partial/<name>.part.
# Usage: push-models.sh <engine-id>... | all
set -euo pipefail
. "$(dirname "$0")/common.sh"
MODEL_SRC=${MODEL_SRC:?set MODEL_SRC to an ssh host:dir or a local dir}
since=$(logmark); bench --es cmd catalog; await_log "$since" "catalog written" 60 >/dev/null
catalog=$(runas cat files/bench/catalog.tsv)
ids=("$@"); [[ ${ids[0]:-all} == all ]] && ids=($(cut -f1 <<<"$catalog" | sort -u))
installed=$(runas_sh 'for d in files/models/*/; do [ -f $d.install.json ] && basename $d; done' 2>/dev/null | tr -d '\r' || true)
for id in "${ids[@]}"; do
  if grep -qx "$id" <<<"$installed"; then echo "$id: already installed"; continue; fi
  runas mkdir -p "files/models/$id.partial"
  while IFS=$'\t' read -r eid name size sha <&3; do
    [[ $eid == "$id" ]] || continue
    have=$(runas_sh "stat -c %s files/models/$id.partial/$name.part 2>/dev/null || echo 0" | tr -d '\r')
    if [[ $have == "$size" ]]; then echo "$id/$name: staged"; continue; fi
    echo "$id/$name: pushing $size bytes"
    if [[ $MODEL_SRC == *:* ]]; then
      ssh -n "${MODEL_SRC%%:*}" cat "${MODEL_SRC#*:}/$id/$name"
    else
      cat "$MODEL_SRC/$id/$name"
    fi | runas_write "files/models/$id.partial/$name.part" "$size"
  done 3<<<"$catalog"
done
since=$(logmark); bench --es cmd install --es engines "$(IFS=,; echo "${ids[*]}")"
# The installer hashes every file (a few GB takes minutes on the phone).
await_log "$since" "cancelled|FAILED|ABORTED|install ${ids[-1]}:" 1800 >/dev/null || true
$ADB logcat -d -T "$since" -s SpeedBench:I | grep -E "install|FAILED"
