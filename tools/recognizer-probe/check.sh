#!/usr/bin/env bash
# Runs the probe's scenarios that don't need anyone speaking and checks how
# they end (#90). Needs the probe installed (README.md), an engine installed in
# our app, and our app chosen as the speech recognition service (or pass the
# package: scenarios use "target ours").
#
#   tools/recognizer-probe/check.sh <adb serial> <our package> <speech.wav>
#
# speech.wav: 16-bit PCM speech, any rate and channel count; it is copied into
# the probe's files dir for the EXTRA_AUDIO_SOURCE scenarios (needs Android 13+).
set -uo pipefail

serial=${1:?adb serial}; pkg=${2:?our package, e.g. app.marmalade.stt.debug}; wav=${3:?speech wav}
A="adb -s $serial"
probe=app.marmalade.stt.probe
sdk=$($A shell getprop ro.build.version.sdk | tr -d '\r')
fails=0

$A shell pm grant $probe android.permission.RECORD_AUDIO
$A push "$wav" /data/local/tmp/probe.wav >/dev/null
$A shell "run-as $probe mkdir -p files && run-as $probe cp /data/local/tmp/probe.wav files/probe.wav"
$A shell rm /data/local/tmp/probe.wav

# run <name> <seconds> <regex the log must match> [extras...]: our app in the background, like a real caller.
run() {
  local name=$1 secs=$2 want=$3; shift 3
  $A shell input keyevent HOME
  $A logcat -c
  $A shell am start -S -n $probe/.ProbeActivity --es target ours --es pkg "$pkg" "$@" >/dev/null
  local log=""
  for _ in $(seq 1 $((secs * 2))); do
    sleep 0.5
    # One line, so a pattern can span callbacks.
    log=$($A logcat -d -s RecognizerProbe:I | tr '\n' ' ')
    grep -qE "$want" <<<"$log" && break
  done
  if grep -qE "$want" <<<"$log"; then
    echo "ok   $name"
  else
    echo "FAIL $name (wanted /$want/)"
    $A logcat -d -s RecognizerProbe:I RecognitionService:* | grep -v '^-' | cut -c19- | sed 's/^/     /'
    fails=$((fails + 1))
  fi
}

# ends <name> <seconds> <end mode> [extras...]: the session's end of speech, from our timing line (#120).
# Assumes Settings → End of speech → Other apps was never chosen (a fresh install): short commands
# then get PAUSE, everything else SMART_TURN.
ends() {
  local name=$1 secs=$2 want=$3; shift 3
  $A shell input keyevent HOME
  $A logcat -c
  $A shell am start -S -n $probe/.ProbeActivity --es target ours --es pkg "$pkg" "$@" >/dev/null
  local got=""
  for _ in $(seq 1 $((secs * 2))); do
    sleep 0.5
    got=$($A logcat -d -s SttTiming:I | grep -oE 'listen apps .* end=[A-Z_]+' | grep -oE '[A-Z_]+$' | tail -1)
    [[ -n $got ]] && break
  done
  if [[ $got == "$want" ]]; then
    echo "ok   $name"
  else
    echo "FAIL $name (end=${got:-none}, wanted $want)"
    fails=$((fails + 1))
  fi
}

run "available"                    5  'isRecognitionAvailable=true' --es scenario available
# The #90 root cause: another app's request must open the mic, not fail on permissions.
run "mic opens for another app"    15 'onReadyForSpeech' --es scenario listen --ei cancelAfterMs 4000
# A length extra holding a class only the caller has: ignored, the request runs, nothing crashes.
run "hostile length extra"         15 'onReadyForSpeech' --es scenario listen --ez hostileLength true --ei cancelAfterMs 4000
run "unknown language"             15 'onError 12 ' --es scenario listen --es lang xx-YY
# A start right after a cancel, while the model may still load: no ERROR_RECOGNIZER_BUSY.
run "restart after cancel"         20 'A2 startListening.*onReadyForSpeech' --es scenario restart --ei gapMs 1500
run "language details"             10 'details resultCode=-1 .*SUPPORTED_LANGUAGES=\[' --es scenario details
# A web search is a short command: Auto-stop; free-form keeps the setting (#120).
ends "web search uses Auto-stop"     15 PAUSE --es scenario listen --es languageModel web_search --ei cancelAfterMs 3000
ends "free form keeps Smart Turn"    15 SMART_TURN --es scenario listen --ei cancelAfterMs 3000
if (( sdk >= 33 )); then
  run "support check"              10 'onSupportResult installed=\[[a-z]' --es scenario support
  run "audio source"               60 'onResults \{results_recognition=\[[^]]' --es scenario audioSource --ez fast true
  run "audio source, segmented"    60 'onSegmentResults.*onEndOfSegmentedSession' --es scenario audioSource --ez fast true --ez segmented true
  # Waze (#116): the pipe stays open 10 s (silence after the clip), the rate comes as a Serializable,
  # and the caller stops at 10 s. The result must come at the end of speech, before that stop.
  # Needs speech.wav to end well within 10 s.
  run "held audio source ends at end of speech" 25 'onResults \{results_recognition=\[[^]].*A stopListening' --es scenario audioSource --ei holdMs 10000 --ei stopAfterMs 10000 --es formatAs serializable
  # A recording handed over as a regular file runs to its end (#116).
  # A caller's own live audio is a short command too (#120).
  ends "held audio source uses Auto-stop" 25 PAUSE --es scenario audioSource --ei holdMs 10000 --ei stopAfterMs 10000
  run "file audio source"          60 'regular file.*onResults \{results_recognition=\[[^]]' --es scenario audioSource --ez fileSource true
  # A caller that stops writing but never closes its pipe: the stream ends 10 s later, with what came.
  run "stalled audio source ends"  40 'stalling.*onResults \{results_recognition=\[[^]]' --es scenario audioSource --ei stallAfterMs 3000
  # A caller trickling a byte every 2 s keeps the pipe busy with next to no audio: it ends too.
  run "trickled audio source ends" 40 'stalling.*onResults \{results_recognition=\[[^]]' --es scenario audioSource --ei stallAfterMs 3000 --ei trickleMs 2000
  # stopListening while our read waits on the caller's pipe: the read is woken, the result comes.
  run "stop during a stalled read"  25 'A stopListening.*onResults \{results_recognition=\[[^]]' --es scenario audioSource --ei stallAfterMs 3000 --ei stopAfterMs 5000
fi
if (( sdk >= 34 )); then
  run "download check"             10 'D on(Success|Error)' --es scenario download --es lang en-US
fi
# Nothing above may have left the service busy.
run "no busy left behind"          15 'onReadyForSpeech' --es scenario listen --ei cancelAfterMs 3000
$A logcat -d -s RecognizerProbe:I | grep -q RECOGNIZER_BUSY && { echo "FAIL a RECOGNIZER_BUSY was logged"; fails=$((fails + 1)); }

$A shell am force-stop $probe
echo "$fails failed"
exit $(( fails > 0 ))
