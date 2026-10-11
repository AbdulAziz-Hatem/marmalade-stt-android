#!/usr/bin/env bash
# Is the phone fit for a heavy run? Charging, >= 30 %, screen off (or on with
# only the launcher or our app in front), thermal status below moderate.
# Exit 0 = OK. Read-only: dumpsys only.
set -uo pipefail
. "$(dirname "$0")/common.sh"
bat=$($ADB shell dumpsys battery)
level=$(awk '/^ *level:/{print $2}' <<<"$bat")  # not "Capacity level:"
powered=$(grep -E '(AC|USB|Wireless|Dock) powered: true' <<<"$bat" | head -1)
status=$(awk '/ status:/{print $2}' <<<"$bat")
current=$($ADB shell cat /sys/class/power_supply/battery/current_now 2>/dev/null | tr -d '\r')
pw=$($ADB shell dumpsys power)
wake=$(grep -oE 'mWakefulness=[A-Za-z]+' <<<"$pw" | head -1)
top=$($ADB shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | grep -oE '[a-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1)
thermal=$($ADB shell dumpsys thermalservice | awk -F': ' '/Thermal Status/{print $2; exit}' | tr -d '\r')
echo "battery=$level% status=$status(2=charging) powered=[${powered:-none}] current_now=$current"
echo "$wake top=$top thermal=$thermal"
ok=1
[[ -z $powered ]] && { echo "NOT OK: not powered"; ok=0; }
(( ${level:-0} < 30 )) && { echo "NOT OK: battery below 30 %"; ok=0; }
(( ${thermal:-0} >= 2 )) && { echo "NOT OK: thermal status $thermal"; ok=0; }
if [[ $wake == *Awake* ]]; then
  case "$top" in
    *launcher*|*Launcher*|$PKG/*) echo "screen on, idle (top: $top)";;
    *) echo "NOT OK: screen on with $top in front (someone is using the phone)"; ok=0;;
  esac
fi
((ok)) && echo OK
((ok))
