# Shared by the speed-bench scripts. adb honours ANDROID_SERIAL for the device.
PKG=app.marmalade.stt.debug
SVC=$PKG/app.marmalade.stt.debug.SpeedBenchmarkService
ADB=${ADB:-/usr/bin/adb}

bench() { # bench <am extras...>: start the benchmark service with a command
  $ADB shell am start-foreground-service -n "$SVC" "$@" </dev/null >/dev/null
}

# adb joins its arguments into one remote command line, so anything with
# spaces or redirections goes through runas_sh as a single string.
# Both leave stdin alone (adb shell would swallow a caller's loop input).
runas() { $ADB shell run-as "$PKG" "$@" </dev/null; }
runas_sh() { $ADB shell "run-as $PKG sh -c '$1'" </dev/null; }
# Streams stdin into an app-private file. exec-in can return before the phone
# has written the last bytes, so wait until the file reaches <size>. Over
# wireless adb a 650 MB file was still 260 KB short after 60 s (#31).
runas_write() { # runas_write <path> <size> < data
  $ADB exec-in "run-as $PKG sh -c 'cat > $1'"
  local have end=$((SECONDS + 300))
  while ((SECONDS < end)); do
    have=$(runas_sh "stat -c %s $1 2>/dev/null || echo 0" | tr -d '\r')
    [[ $have == "$2" ]] && return 0
    sleep 1
  done
  echo "$1: $have of $2 bytes on the phone" >&2
  return 1
}

# Waits for "SpeedBench: <pattern>" in logcat after a start mark; prints the line.
await_log() { # await_log <since> <regex> [timeout s]
  local since=$1 re=$2 t=${3:-600} end=$((SECONDS + ${3:-600}))
  while ((SECONDS < end)); do
    # || true: with pipefail, grep's "no match yet" would end a `set -e` caller.
    line=$($ADB logcat -d -T "$since" -s SpeedBench:I 2>/dev/null | grep -E "$re" | tail -1 || true)
    [[ -n $line ]] && { echo "$line"; return 0; }
    sleep 5
  done
  echo "timed out after ${t}s waiting for /$re/" >&2
  return 1
}

logmark() { $ADB shell "date '+%m-%d %H:%M:%S.000'" </dev/null | tr -d '\r'; }
