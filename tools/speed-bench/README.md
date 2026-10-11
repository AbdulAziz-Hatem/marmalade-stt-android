# speed-bench: engine speed on a real phone (#23)

Drives the debug-only `SpeedBenchmarkService`
(`app/src/debug/java/app/marmalade/stt/debug/`) over adb, headless: no taps,
no settings changes. It measures every installed engine through the app's
own code (same JNI build, thread count and session pipeline). Results:
`docs/benchmarks/2026-09-pixel8a-speed.md`.

Per engine and thread count, in a shuffled order with a cool-down between:

- **Load** time, a warm-up decode, then **RTF** (decode time / clip length)
  for every WAV. The VAD split isn't timed; the model's decode is.
- **Probe**, once per run: the app's CPU probe (`speech/SpeedProbe.kt`: the
  bundled Silero VAD over `app/src/main/assets/calibration/speech.wav`), 5
  times once the phone is idle (#31). The service reads slower than the app
  (see below), so `SpeedBudget.PROBE_REFERENCE_MS` is the app's own probe;
  `--es cmd probe` runs only the probe (5 times, a few seconds).
- **Sessions**: each `ls*` clip is played in real time through a real
  listening session (`PacedClipMic`, 0.5 s lead, 3 s tail, no platform AGC
  like the Pixel): first partial after the VAD onset, and end of speech (last
  20 ms frame within 35 dB of the loudest, as `asrbench.latency`) to the last
  Final. The first clip plays once before them as the cold start.
- Peak RSS, thermal status and battery temperature before/after.

The run refuses to start off the charger or below 30 %, waits while the
screen is on (someone using the phone), stops below 20 % battery or at
thermal status severe, and re-measures once at the end any engine that saw
thermal status moderate or the screen on. It holds a partial wake lock.

## Steps

```bash
export ANDROID_SERIAL=<serial or ip:port>        # adb honours it
./gradlew -PabiFilters=arm64-v8a :app:assembleFdroidDebug && adb install -r app/build/outputs/apk/fdroid/debug/app-fdroid-arm64-v8a-debug.apk
tools/speed-bench/push-audio.sh                  # $BENCH_AUDIO or a dir argument (see Test audio below)
tools/speed-bench/push-models.sh all             # streams from $MODEL_SRC (ssh host:dir or local dir, e.g. $WORK/models);
                                                 # the app's installer verifies every sha256; nothing is downloaded
tools/speed-bench/phone-ok.sh                    # charging, >= 30 %, idle, cool?
OUT=docs/benchmarks/pixel8a TIMEOUT=14400 tools/speed-bench/run.sh --es tag pixel
OUT=docs/benchmarks/pixel8a tools/speed-bench/run.sh --es tag threads \
    --es engines parakeet-ultra,parakeet-tdt-110m-en,nemotron-3-5-streaming-1120ms --es threads 2,4
python3 tools/speed-bench/summarize.py docs/benchmarks/pixel8a/*.json --kotlin   # REFERENCE (+ REFERENCE_BUDGET with a distil turn)
```

`--kotlin` prints memory by `SpeedBudget.REFERENCE`'s rule (the engine's
own peak plus a 180 MB process baseline: see `reference_memory_mb`), not the
raw peak. Offline engines need a turn with an `ls28-*` clip (#47), the
longest utterance the app decodes at once; without one it falls back to
×1.54 and says so on stderr. It also suggests `SpeedBudget.REFERENCE_BUDGET` (Max's ceiling,
distil-small.en's cost in Ultra units), but only when the files include a
distil-small-en turn at those threads. The app can't run distil since #45,
so that needs a pre-#45 result file from the same phone; otherwise keep the
current value (a host ratio, see its KDoc).

`run.sh` passes its arguments to the service: `--es engines a,b` (default: all
installed), `--es threads 2,4` (default: the device's), `--ei cooldown 60`,
`--el seed N`, `--ez sessions false`, `--ez screenOk true` (emulators, whose
screen is always on), `--es tag name`, `--es clips ls10-1,ls20-1` (default:
every WAV), `--ei repeats 3` (timed decodes per clip). Off the charger (only
when Max says so): `SKIP_CHECK=1` for `run.sh` plus `--ez unplugged true` and
`--ei stopBattery 10`; battery level and temperature are in every turn.

ONNX Runtime tuning (#31, `docs/benchmarks/2026-10-pixel8a-ort.md`):
`--es tunings cpu,cpu-nospin,cpu-pin,cpu-nospin-pin,xnnpack-nospin` measures
each engine and thread count under each `OrtTuning` id (default: `cpu`, what
users get). `-verbose` (e.g. `xnnpack-nospin-verbose`) turns ORT's verbose log
on: `adb logcat | grep "Node placements" -A1` shows which execution provider
took each graph's nodes. `-arena` / `-noarena` force ORT's memory arena on or
off (#47). It is on by default; the app turns it off only where an engine
fits the phone only without it (`OrtTuning.forPhone`), so the default `cpu`
is arena on here. Each turn records `arena`.

First decodes (#47): `--ez shuffleClips true --ei repeats 1` decodes every
clip once, in a shuffled order (by `seed`), after a warm-up on 0.5 s of
silence, so every timed decode is the first of its length, as dictation's
utterances are. Without the arena that first decode is the slow one: warm
repeats hid a 1.4× cost. `summarize.py --kotlin` pools every file given and
takes each engine's cost from those first decodes, against Parakeet Ultra
with the arena, plus a `LeanMode` (memory, cost factor) where the arena was
also off. `cpu-pin:0.1` pins to given cores (the emulator has
no clusters to pick from). Every turn records its tuning, the pinned cores and
`threadAffinity` (how many threads may run on which cores after the load).
`summarize.py --sweep` compares the tunings; `--tuning <id>` picks the one the
main table and `--kotlin` use.

The run probes 5 times once the phone is idle (`probeRuns`, median in
`probeMs`). The service isn't in Android's top-app cpuset, so its probe is
slower than the app's own (Pixel 8a: about 240 ms against 157 ms in the app,
#31); `SpeedBudget.PROBE_REFERENCE_MS` is the app's.

With sessions on, each turn also plays the first clip after
`SttEngine.warmUp` (load plus a silence decode, #31) as `coldWarmed`, next to
the plain-load `cold`; `summarize.py --cold` compares them.

Thread sweep (#34, `docs/benchmarks/2026-09-pixel8a-threads.md`): the rerun
commands are at the end of that doc; `summarize.py --sweep` prints one table
per engine (median RTF per clip over the repeats, and each turn's thermal
status and battery).

The phone only needs `app-fdroid-arm64-v8a-debug.apk`: build it with
`./gradlew -PabiFilters=arm64-v8a :app:assembleFdroidDebug` (#31), which
compiles sherpa-onnx for that ABI only. A build of every ABI took about 25 min
on a loaded dev machine. `--es cmd stop` via `common.sh`'s
`bench` stops a run.

Test audio: 12 LibriSpeech test-clean clips (3 each of ~2, 5, 10, 20 s; the 2 s ones since #55; CC-BY-4.0)
with their transcripts, two single ~27 s utterances (`ls28-1`, `ls28-2`, #47:
memory at the 28 s cap), plus the three #11 Kokoro sentences. They are kept
out of the repo in an `stt-bench-audio/` directory (`BENCH_AUDIO`); its
README lists the LibriSpeech ids. Only the `ls*` clips run through sessions.

## Comparing engines across noisy turns (#55)

Off the charger, two turns of the same engine in one run differed by ~20 % on the Pixel 8a (a warmer phone is
slower), and within a turn later clips decode up to ~2× slower than the first ones. Every turn decodes the clips
in the same order, so compare clip by clip against a baseline turn of the same run:
`python3 tools/speed-bench/paired.py results/*.json --base whisper-turbo` prints the geometric mean of the per-clip
time ratios (decode and end of speech → Final) per length group, over the undisturbed turns of each run. For a
decision, use at least two runs on the charger.

## Which cpuset does dictation get? (#47)

`tools/speed-bench/cpuset-watch.sh [seconds] [packages…]` samples, once a
second, every process of the debug app, the release app and FUTO Keyboard:
`/proc/<pid>/cpuset` (`/top-app`, `/foreground`, …), ps's scheduling policy,
each thread's `Cpus_allowed_list` and the core each busy thread last ran
on. Read-only. Run it while someone dictates through the keyboard: on a
Pixel 8a, `/top-app` gets all nine cores and the X3 (core 8), a service
outside it cores 0–7 only.
