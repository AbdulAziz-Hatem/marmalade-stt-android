#!/usr/bin/env bash
# Which cpuset do the app's threads get while someone dictates (#47)? The
# benchmark service runs outside Android's top-app cpuset (Pixel 8a: cores
# 0-7, no X3), so its speeds are lower than the app's in front. This samples,
# once a second for <seconds>, every process of the given packages:
#   cpuset    /proc/<pid>/cpuset (/top-app, /foreground, /background, ...)
#   pcy       the scheduling policy ps reports (ta = top-app, fg, bg, ...)
#   allowed   per-thread Cpus_allowed_list, as "<cores>x<threads>"
#   last      per-thread last core run on (/proc/<pid>/task/*/stat field 39),
#             as "<core>x<threads>", for threads that used CPU since the last sample
# Read-only: nothing is installed or changed on the phone.
# Usage: cpuset-watch.sh [seconds=60] [packages...]
#   default packages: the debug and release app, FUTO Keyboard
set -euo pipefail
. "$(dirname "$0")/common.sh"
secs=${1:-60}
shift || true
pkgs=${*:-"$PKG app.marmalade.stt org.futo.inputmethod.latin"}

# The loop runs on the phone (one adb call); its script comes in on stdin.
$ADB shell sh -s "$secs" $pkgs <<'EOF'
secs=$1; shift
end=$(( $(date +%s) + secs ))
prev=/data/local/tmp/.cpuset-watch-$$
trap 'rm -f $prev.*' EXIT
while [ "$(date +%s)" -lt "$end" ]; do
  now=$(date +%H:%M:%S)
  for p in "$@"; do
    for pid in $(pidof "$p"); do
      cs=$(cat /proc/$pid/cpuset 2>/dev/null)
      pcy=$(ps -o PCY= -p "$pid" 2>/dev/null | tail -n 1 | tr -d ' \r\n')
      allowed=$(grep -h Cpus_allowed_list /proc/$pid/task/*/status 2>/dev/null | awk '{print $2}' | sort | uniq -c | awk '{printf "%sx%s ", $2, $1}')
      # utime+stime (fields 14+15) and last core (39) per thread; comm can hold spaces, so cut at ") ".
      cur=$prev.$pid.now
      for s in /proc/$pid/task/*/stat; do
        sed 's/^\([0-9]*\) .*) /\1 /' "$s" 2>/dev/null
      done | awk '{print $1, $13 + $14, $38}' > "$cur"
      last=""
      if [ -f "$prev.$pid" ]; then
        last=$(awk 'NR == FNR { t[$1] = $2; next } ($1 in t) && $2 > t[$1] { print $3 }' "$prev.$pid" "$cur" | sort -n | uniq -c | awk '{printf "%sx%s ", $2, $1}')
      fi
      mv "$cur" "$prev.$pid"
      echo "$now $p pid=$pid cpuset=$cs pcy=$pcy allowed=[$allowed] last=[$last]"
    done
  done
  sleep 1
done
EOF
