# Recognizer probe (#90)

A tiny third-party app (`app.marmalade.stt.probe`) that drives Android's speech
APIs against Marmalade STT the way other apps do. It matters that it is a
**separate package**: the debug `SpeechTestActivity` runs inside our own app,
so our app is in the foreground while it listens, and a bug that only shows
when another app asks (#90: Duolingo, `ERROR_INSUFFICIENT_PERMISSIONS` at
once) never shows there.

Every callback is logged under the logcat tag `RecognizerProbe`, with
milliseconds since the run started. No Gradle: `build.sh` makes the APK in a
few seconds with javac, d8, aapt2 and the debug keystore.

```sh
tools/recognizer-probe/build.sh /tmp/probe.apk
adb install -r /tmp/probe.apk
adb shell pm grant app.marmalade.stt.probe android.permission.RECORD_AUDIO
adb shell input keyevent HOME            # our app in the background, like a real caller
adb shell am start -S -n app.marmalade.stt.probe/.ProbeActivity --es scenario listen --es target default
adb logcat -s RecognizerProbe:I
```

The probe has a launcher icon, like any app a person talks to: Android only
lets our service answer callers our app can see (#96), and our manifest makes
apps with a launcher icon visible. Right after the probe is (re)installed it
is briefly visible either way, so wait a little before measuring.

## Regression check

`check.sh <adb serial> <our package> <speech.wav>` runs the scenarios that
need nobody speaking (the mic opening for another app, an unknown language,
a restart after cancel, language details, the support and download checks,
`EXTRA_AUDIO_SOURCE` plain, segmented, stalled, trickled and stopped while stalled) and checks how each ends; exit
status 0 when all pass. It needs an engine installed in our app.

## Extras

| Extra | Meaning |
|---|---|
| `--es scenario` | see below (default `listen`) |
| `--es target` | `default`: `createSpeechRecognizer(context)`, the phone's chosen service; `ours` (default): our component by name |
| `--es pkg` | our package for `target ours` and `details` (default `app.marmalade.stt`; `app.marmalade.stt.debug` for debug builds) |
| `--es lang` | `EXTRA_LANGUAGE`, e.g. `es-ES` |
| `--ez partials` | `EXTRA_PARTIAL_RESULTS` (default true) |
| `--ei maxResults` | `EXTRA_MAX_RESULTS` |
| `--ez preferOffline` | `EXTRA_PREFER_OFFLINE` |
| `--ei completeSilenceMs`, `--ei minLengthMs` | the `EXTRA_SPEECH_INPUT_*` lengths |
| `--ez hostileLength` | a probe-only `Serializable` as `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS`: the request must still run (no crash) |
| `--ez segmented` | `EXTRA_SEGMENTED_SESSION` |
| `--es biasing` | `EXTRA_BIASING_STRINGS`, comma-separated |
| `--es formatting` | `EXTRA_ENABLE_FORMATTING` (`quality` or `latency`) |
| `--ez hideTrailingPunct`, `--ez mask` | `EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION`, `EXTRA_MASK_OFFENSIVE_WORDS` |
| `--ez detect`, `--es detectLangs` | `EXTRA_ENABLE_LANGUAGE_DETECTION` and its allowed languages |
| `--ei delayMs` | `listen`: wait this long between creating the recognizer (the bind) and `startListening` (#96) |
| `--ei stopAfterMs`, `--ei cancelAfterMs` | `listen`, `audioSource`: call `stopListening` / `cancel` after this long |
| `--ei gapMs`, `--ei restartMs` | timing for the restart scenarios |
| `--es wav`, `--ez fast` | `audioSource`: the WAV in the probe's files dir (default `probe.wav`), streamed in real time unless `fast` |
| `--ei stallAfterMs` | `audioSource`: stop writing after this long, keeping the pipe open for a minute (a stalled caller) |
| `--ei trickleMs` | with `stallAfterMs`: write one byte this often instead of nothing (a caller keeping the pipe busy); a write after the recognizer closed its end is logged as failed |

## Scenarios

| Scenario | What it does |
|---|---|
| `listen` | one request |
| `restart` | start, `cancel` after `gapMs`, start again at once |
| `startTwice` | a second start on the same recognizer, no cancel |
| `stopRestart` | start, `stopListening` after `gapMs`, a new start `restartMs` later, before the result |
| `recreate` | start, `destroy` the recognizer, start on a new one at once |
| `twoClients` | two recognizers start together |
| `die` | start, then the probe kills its own process after `gapMs`; run `listen` next to see the service recovered |
| `bind` | binds our service directly for half a second. Android then lets our app see the probe for good, which no `SpeechRecognizer` caller gets (#96); reinstall the probe to undo it |
| `available` | `isRecognitionAvailable`, `isOnDeviceRecognitionAvailable` |
| `support` | `checkRecognitionSupport` (API 33+) |
| `download` | `triggerModelDownload` with a listener (API 34+) |
| `details` | the `ACTION_GET_LANGUAGE_DETAILS` ordered broadcast |
| `audioSource` | streams a 16-bit WAV through `EXTRA_AUDIO_SOURCE` (API 33+) |

Put a WAV in place for `audioSource` (it is test audio, read only by the probe):

```sh
adb shell run-as app.marmalade.stt.probe sh -c 'cat > files/probe.wav' < speech.wav
```

On the emulator, speech for the mic scenarios goes in through a PulseAudio
null sink (see the project memory note on emulator mic audio); `adb emu avd
hostmicon` must be sent shortly before the mic opens, and even then a run is
sometimes silent (`ERROR_SPEECH_TIMEOUT` with `onRmsChanged -2.0` throughout):
run it again before blaming the app.
