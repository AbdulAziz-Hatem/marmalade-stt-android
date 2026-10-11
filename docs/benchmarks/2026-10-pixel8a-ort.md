# Pixel 8a: ONNX Runtime tuning, the user lineup and the probe, October 2026 (#31)

Can the STT engines run faster with marmalade-tts-android's ONNX Runtime
setup: the XNNPACK execution provider, `session.intra_op.allow_spinning=0`,
and pinning to the fast cores? Plus, in the same phone session: the user
lineup's speed (#23's spot check), a warm-up decode for the first dictation,
and the CPU probe reference.

**Answers:**

- **XNNPACK: no.** It takes no node of any of our int8 graphs. ORT's
  verbose log puts every node of all 16 sessions on the CPU EP, so XNNPACK
  only adds idle threads. It is out of Settings; the benchmark keeps it.
- **Spinning off: no.** Unpinned, it is 3.2–4.2× slower. Parked threads
  wake late and on any core. Pinned to the fast cores, it only ties the
  default.
- **Pinning: no default change.** It made Parakeet Ultra 8 % faster but
  Nemotron 3.5 28 % slower.
- **The default stays:** CPU EP, spinning on, no pinning (`OrtTuning.DEFAULT`).
- **4 threads stays the cap**, with spinning on or off. 5 or 6 threads are
  1.9× slower, and spinning off doesn't rescue them (3.1–3.6×).
- **Probe reference: 157 ms**, the app's own measurement. The service reads
  about 240 ms on the same phone because it runs outside Android's top-app
  cpuset (see Conditions).
- **Warm-up decode: kept, small gain.** The first partial after Keep engine
  ready's load was 0.2 s faster for Parakeet Ultra and 0.1 s for 110M. One
  clip each, so indicative only.
- **Lineup:** every engine matches its `SpeedBudget` estimate within 7 %,
  except Parakeet 110M: 0.378 × Ultra, not 0.321.

## Method

- The debug `SpeedBenchmarkService`, driven by `tools/speed-bench/run.sh`,
  headless over wireless adb (`tools/speed-bench/README.md`). It uses the
  app's own JNI build and decode path (branch `child/31-ort-tuning`).
- 4 recognizer threads (the Pixel's `RecognizerThreads.count`) unless stated.
  Clips are LibriSpeech test-clean. RTF = decode time / clip length. Tables
  give the median of 3 decodes after a warm-up decode, with the range.
- Four runs, back to back, 01:54–03:02 on 2026-10-02. Each waited until the
  phone was idle (screen off), then probed 5 times. Engines and tunings ran
  in a shuffled order with a 30 s cool-down (5 s in `ort-xnn`).
  - `ort-xnn`: six engines with `xnnpack-nospin-verbose`, ORT's verbose log
    captured from logcat.
  - `ort-tune`: Ultra and Nemotron 3.5 560 ms × 5 tunings.
  - `ort-threads`: Ultra at 3, 5 and 6 threads, spinning on and off.
  - `ort-lineup`: six engines at the default, with sessions (first partial,
    end of speech → Final, cold start with and without the warm-up).
- Raw results: `2026-10-pixel8a-ort/*.json`. `python3
  tools/speed-bench/summarize.py <files> --sweep` builds the tuning and
  thread tables, `--kotlin` the costs, and `--cold` the cold starts.

## Conditions

- Google Pixel 8a, Android 17. **Charging** (Max plugged it in during the
  wait), battery 37–50 %, 33.7–35.3 °C, thermal status 0 throughout. **Screen
  off**; no turn was disturbed.
- **The service gets cores 0–7 only.** After a load, every thread's
  `Cpus_allowed_list` reads `0-7`: the Cortex-X3 (core 8) is outside the
  cpuset Android gives a foreground service. Pinning to the detected fast
  cores (4–8) therefore became 4–7 (`threadAffinity` in the JSON; the
  kernel intersects the two masks). All the benchmark's numbers, including
  #34's thread sweep, are on 4 A715 + 4 A510 cores. The app in front gets
  the X3 too, plus Android's top-app boost. That is the 157 vs 240 ms probe
  difference below.

## XNNPACK: which nodes it takes

`--es tunings xnnpack-nospin-verbose` sets ORT's session log to verbose
(`LogSeverityLevel=0` in sherpa-onnx's provider config). ORT then logs
`Node placements` per session:

| Engine | Sessions (nodes) | On XNNPACK |
|---|---|---|
| parakeet-ultra | encoder 2590, decoder 14, joiner 9 | 0 |
| parakeet-tdt-110m-en | 1959, 8, 9 | 0 |
| nemotron-3-5-streaming-560ms | 3223, 14, 9 | 0 |
| nemotron-3-5-streaming-1120ms | 3223, 14, 9 | 0 |
| whisper-turbo | encoder 1119, decoder 802 | 0 |
| distil-small-en | encoder 439, decoder 802 | 0 |

Every line read `All nodes placed on [CPUExecutionProvider]`. Our models
are dynamic int8 (`MatMulInteger`), which ORT's XNNPACK EP doesn't
implement. Nothing crashed, on the Pixel or on the x86_64 emulator (ORT
1.28). The TTS app's crash was with QDQ graphs on ORT 1.26.

## Tunings (4 threads, median RTF of 3)

| Engine | Tuning | RTF ls10-1 | RTF ls20-1 | vs best | Threads on pinned cores |
|---|---|---|---|---|---|
| parakeet-ultra | **cpu (default)** | 0.072 (0.071–0.072) | 0.069 (0.069–0.072) | 1.08× | – |
| parakeet-ultra | cpu-pin | 0.065 (0.065–0.069) | 0.065 (0.064–0.068) | 1.00× | 9 |
| parakeet-ultra | cpu-nospin-pin | 0.069 (0.068–0.070) | 0.067 (0.066–0.075) | 1.04× | 9 |
| parakeet-ultra | cpu-nospin | 0.282 (0.279–0.283) | 0.267 (0.267–0.271) | 4.22× | – |
| parakeet-ultra | xnnpack-nospin | 0.278 (0.278–0.282) | 0.261 (0.259–0.269) | 4.13× | – |
| nemotron-3-5-streaming-560ms | **cpu (default)** | 0.251 (0.236–0.261) | 0.255 (0.252–0.255) | 1.00× | – |
| nemotron-3-5-streaming-560ms | cpu-nospin-pin | 0.233 (0.233–0.245) | 0.273 (0.243–0.299) | 1.00× | 9 |
| nemotron-3-5-streaming-560ms | cpu-pin | 0.309 (0.286–0.343) | 0.339 (0.312–0.344) | 1.28× | 9 |
| nemotron-3-5-streaming-560ms | cpu-nospin | 0.837 (0.832–0.838) | 0.795 (0.781–0.796) | 3.23× | – |
| nemotron-3-5-streaming-560ms | xnnpack-nospin | 0.833 (0.831–0.837) | 0.804 (0.802–0.816) | 3.24× | – |

"Threads on pinned cores" counts the threads whose allowed cores were all
inside the pin: 9, the pool workers of the 3 sessions (encoder, decoder,
joiner) at 4 threads. The loading thread went back to 0–7, as
`CpuAffinity.pinned` restores it.

Why spinning off hurts: a transducer decode alternates the encoder's,
decoder's and joiner's thread pools many times per utterance. A spinning
pool picks up its next job at once. A parked one waits for a wake-up, and
the scheduler may then put it on an A510. Pinned to the big cores, only the
wake-up is left, and it costs about what spinning saves. XNNPACK + no
spinning is just "no spinning", since XNNPACK runs nothing.

## Thread cap, spinning on and off (Parakeet Ultra)

| Threads | cpu | cpu-nospin |
|---|---|---|
| 3 | 0.083 / 0.081 (1.26×) | 0.307 / 0.234 (4.14×) |
| **4** | **0.072 / 0.069** (1.08×) | 0.282 / 0.267 (4.22×) |
| 5 | 0.127 / 0.123 (1.91×) | 0.238 / 0.227 (3.57×) |
| 6 | 0.119 / 0.127 (1.88×) | 0.209 / 0.193 (3.08×) |

RTF ls10-1 / ls20-1; "×" is against the best Ultra row above (cpu-pin).
With spinning on, 4 threads is the clear best, as in #34. Spinning off isn't
why 5+ threads are slow: it is slower at every count. In this cpuset, the
5th thread has to run on an A510, since the X3 isn't available. With the X3
available (the app in front), 5 might do better. The service can't measure
that; the cap stays at 4.

## User lineup (default tuning, 4 threads)

| Engine | Load s | RTF 5 s | RTF 10 s | RTF 20 s | RTF LS | × distil | First partial s (5/10/20) | End of speech → Final s (5/10/20) | Peak RSS MB | WER LS % |
|---|---|---|---|---|---|---|---|---|---|---|
| parakeet-tdt-110m-en | 2.0 | 0.031 | 0.030 | 0.029 | 0.029 | 0.11 | 0.5/0.5/0.5 | 1.5/1.8/1.5 | 587 | 5.6 |
| parakeet-ultra | 4.5 | 0.082 | 0.073 | 0.079 | 0.078 | 0.29 | 0.7/0.6/0.6 | 1.9/2.2/2.5 | 1315 | 5.6 |
| nemotron-3-5-streaming-1120ms | 4.8 | 0.192 | 0.197 | 0.219 | 0.208 | 0.77 | 1.7/0.6/0.6 | 1.4/1.6/1.1 | 919 | 3.4 |
| distil-small-en | 1.8 | 0.240 | 0.297 | 0.265 | 0.271 | 1.00 | 1.6/1.0/1.6 | 3.0/5.8/5.3 | 1070 | 6.2 |
| nemotron-3-5-streaming-560ms | 4.6 | 0.282 | 0.327 | 0.364 | 0.341 | 1.26 | 1.4/1.2/0.5 | 1.7/1.6/1.1 | 917 | 3.4 |
| whisper-turbo | 4.5 | 0.773 | 0.620 | 0.819 | 0.751 | 2.77 | –/–/– | 6.4/9.0/11.9 | 2179 | 5.6 |

Nemotron is the streaming path. Its RTF includes the #27 tail padding,
decoded in whole chunks, so 1120 ms is cheaper per second of audio than
560 ms. Whisper turbo gives no partials (the offline sessions' live partials
are off for it).

Against `SpeedBudget.REFERENCE` (host ratios moved to 4 threads):

#45 re-based `REFERENCE` on Parakeet Ultra (distil's 0.28 → 1.0) while this
ran. In Ultra units:

| Engine | Pixel × Ultra | REFERENCE (#45) | Difference | Action |
|---|---|---|---|---|
| parakeet-tdt-110m-en | 0.378 | 0.321 | +18 % | **now 0.378** |
| nemotron-3-5-streaming-1120ms | 2.67 | 2.86 | −7 % | kept |
| distil-small-en (`REFERENCE_BUDGET`) | 3.48 | 3.57 | −2.5 % | kept |
| nemotron-3-5-streaming-560ms | 4.38 | 4.29 | +2 % | kept |
| whisper-turbo | 9.64 | 9.79 | −1.5 % | kept |

In distil units (as before #45): Ultra 0.29 (estimate 0.28), 110M 0.11
(0.09), Nemotron 560 ms 1.26 (1.20), 1120 ms 0.77 (0.80), turbo 2.77
(2.74).

Peak RSS is the app process's, far below the host's Python figures that
`REFERENCE` held until the #31 review (Ultra 1315 vs 2407 MB, Whisper turbo
2179 vs 3872 MB). See "Memory" below.

## Memory (#31 review)

**Superseded by #47** (`2026-10-pixel8a-mem.md`): the growth past 20 s was
ONNX Runtime's memory arena. Engines now run without it only on phones
they fit only that way (slower), and every offline engine was measured on
the Pixel at a 27 s utterance. The figures below are the #31 state.

`REFERENCE`'s memory column feeds the recommender's RAM check (an engine's
peak within 40 % of `totalMem`). It is now the app's peak for the longest
utterance the app decodes at once (`MAX_UTTERANCE_SECONDS`, 28 s).

**The engine's own memory** is a turn's peak minus the process's RSS before
its load. Raw peaks carry earlier turns' leftovers: in the tuning and
thread runs the baseline was 395–627 MB and Ultra's peak 1292–1740 MB, but
its own memory was 1118–1154 MB at 3–6 threads, the same as in the lineup
(1135 MB, baseline 180 MB). The lineup's baselines were 162–190 MB, so
`REFERENCE` adds 180 MB.

**It grows with the utterance** for engines that decode a whole utterance at
once. The lineup's longest clip is 20 s. On an x86_64 emulator (API 35,
same lineup, `cpu` tuning, 4 threads; `2026-10-pixel8a-ort/emu-mem-*.json`),
Parakeet Ultra's own memory was:

| Clips | Own memory MB |
|---|---|
| ls05-1 (5 s) | 791 |
| ls05-1, ls10-1, ls20-1 | 933 |
| ls30-x (ls20-1 + ls10-1, 30.6 s, one utterance) | 1438 |

The jump past 20 s is far steeper than 5 s to 20 s (ONNX Runtime's arena
grows in steps). So offline engines' own memory is multiplied by 1.54
(1438 / 933): a 30.6 s utterance stands in for the 28 s cap plus the 0.6 s
pre-roll and 0.3 s tail padding. Nemotron (streaming, fixed chunks) and
Whisper (a fixed 30 s window) don't grow and keep their measured values.

**Engines the Pixel didn't run** (Canary, Granite, Qwen3) use the emulator's
own memory times 1.22, the Pixel / emulator ratio for Ultra (1135 / 933).
Parakeet 110M's was 1.0 (407 / 413), so 1.22 is the higher of the two.
Canary was 339 MB at 5 s and 483 MB up to 20 s, a steeper relative growth
than Ultra's, so its 1.54 is an estimate.

| Engine | Source | Own memory MB | × growth | `REFERENCE` MB | Host's (before) |
|---|---|---|---|---|---|
| parakeet-ultra | Pixel | 1135 | 1.54 | 1930 | 2407 |
| parakeet-tdt-110m-en | Pixel | 403 | 1.54 | 810 | 608 |
| nemotron-3-5-streaming-560ms | Pixel | 745 | – | 930 | 2013 |
| nemotron-3-5-streaming-1120ms | Pixel | 757 | – | 940 | 2030 |
| whisper-turbo | Pixel | 1989 | – | 2170 | 3872 |
| canary-180m-flash | emulator 483 × 1.22 | 589 | 1.54 | 1090 | 1101 |
| granite-speech-5-470m | emulator 888 × 1.22 | 1081 | 1.54 | 1850 | 1400 |
| qwen3-asr-0-6b | emulator 2091 × 1.22 | 2545 | 1.54 | 4110 | 4082 |

`summarize.py --kotlin` prints the same values from the result files.

**What changes** (recommendations before and after, at the Pixel's speed
and at half of it; phones report a little under their nominal RAM, so a
6 GB phone is taken as 5.5 GB, 4 GB as 3.6, 3 GB as 2.7, 2 GB as 1.8):
- 6 GB phones get Parakeet Ultra for its languages: Ultra needs 4.7 GB of
  RAM, against 5.9 GB before. They used to get Parakeet 110M (English),
  Canary (German, French, Spanish) or Nemotron (Italian, Portuguese, …).
- 4 GB phones still don't fit Ultra: English 110M, German, French and
  Spanish Canary, as before. Nemotron now fits them and 3 GB phones
  (2.3 GB needed, 5 GB before): the same engine for Japanese, Chinese or
  Italian, no longer marked low on memory.
- 6 and 8 GB phones fit Whisper turbo (5.3 GB needed, 9.5 GB before): for
  a language only turbo or Qwen3 covers (Thai, Swahili, …) it is the same
  engine, now marked "may be slow" instead of low on memory. Qwen3 still
  needs a 12 GB phone.
- 2 GB phones: nothing fits but Parakeet 110M (now just over 40 %). German,
  French and Spanish get Nemotron instead of Canary, as the leanest engine
  for the language (0.93 against 1.09 GB).
- An 8 GB phone where both Nemotron chunk sizes fell behind real time got
  Whisper turbo (it fits now), since an engine that fell behind was the
  last resort, even against turbo's 3.6× higher estimate. #47 changed that:
  it keeps Nemotron, marked slow. An unproven engine wins only at the same
  or a lower estimated cost.

## First dictation: plain load vs warm-up decode

`SttEngine.warmUp` (what Keep engine ready now calls) loads the model and
then decodes 0.5 s of silence. On the first 5 s clip (ls05-1), in seconds:

| Engine | First partial: cold / warmed / steady | End of speech → Final: cold / warmed / steady | warmUp (load + decode) |
|---|---|---|---|
| parakeet-ultra | 0.90 / 0.70 / 0.70 | 1.98 / 2.00 / 1.85 | 3.72 |
| parakeet-tdt-110m-en | 0.53 / 0.43 / 0.47 | 1.54 / 1.50 / 1.52 | 1.67 |
| nemotron-3-5-streaming-560ms | 1.14 / 1.08 / 1.42 | 1.77 / 1.59 / 1.66 | 3.84 |
| nemotron-3-5-streaming-1120ms | 1.76 / 1.74 / 1.68 | 1.59 / 1.51 / 1.44 | 3.63 |
| whisper-turbo | – | 6.78 / 6.77 / 6.40 | 6.78 |
| distil-small-en | 1.47 / 2.06 / 1.59 | 3.27 / 4.17 / 2.97 | 2.26 |

- **Cold:** the first session after a plain load.
- **Warmed:** the first session after `warmUp`.
- **Steady:** the median of later sessions on the same clip.

The Parakeets' first partial drops to the steady value (0.2 and 0.1 s
saved). Nemotron and Whisper turbo barely change. distil's warmed row is
slower, but its first partial varies by 0.5 s between runs anyway. One
clip per engine, so this is indicative only.

The warm-up decode isn't free. As measured here it ran inside the load, so
a dictation that cold-started the process (a keyboard or
RecognitionService request) also waited for it. For Whisper turbo that was
about 0.4–2 s (warmUp 6.8 s against plain loads of 4.5–6.4 s), for a
0.1–0.2 s gain. The #31 review round changed it:
- The model is published as soon as it is loaded, and the decode runs
  after it.
- A session waiting for the load skips the decode.
- A session that starts during the decode waits for that decode only.
- Whisper skips it.
marmalade-tts-android saw 400–500 ms on its first run; the STT engines'
first-run cost is smaller.

## The CPU probe

`SpeedProbe`: a warm-up pass, then the fastest of 5 runs. In each of the four
runs, idle, screen off, charging:

| Run | 5 probes (ms) | Median |
|---|---|---|
| ort-xnn | 239, 240, 238, 243, 243 | 240 |
| ort-tune | 245, 244, 243, 244, 245 | 244 |
| ort-threads | 244, 242, 245, 244, 239 | 244 |
| ort-lineup | 239, 246, 241, 242, 239 | 241 |

The probe is steady (±2 %) and the first one carries no extra init cost,
because `SpeedProbe` already does a warm-up pass. But the app itself had
stored **157 ms** on this phone (`device_probe_ms` in its DataStore,
measured in the app, screen on). That is why it showed "1.7× a Pixel 8a"
against the old 264 ms. The service is the odd one out: no top-app cpuset,
so no X3 core and no boost. #34's 264 and 291 ms were service probes too,
under load. `PROBE_REFERENCE_MS` is now 157, the app's own measurement.
That is one in-app sample, though each probe is already the fastest of 5
runs. Re-measuring in the app (Settings → Developer → Re-measure phone
speed) is the check.

## Not measured

- Anything with the app in front (top-app cpuset, X3 core): the service
  can't. The thread cap and pinning might differ there, and so might the
  dictation engines' real speed, if the IME's process runs as top-app.
- Granite (Max: no Granite focus) and the other developer engines.
- A screen-on rerun: the screen stayed off, which the benchmark requires.
