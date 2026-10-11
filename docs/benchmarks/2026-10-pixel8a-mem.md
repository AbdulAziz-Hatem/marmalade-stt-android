# Pixel 8a: engine memory at the 28 s utterance cap, ORT's memory arena, October 2026 (#47)

How much memory does each engine need for the longest utterance the app
decodes at once (`MAX_UTTERANCE_SECONDS`, 28 s)? #31 had measured clips up
to 20 s and scaled offline engines by ×1.54, Parakeet Ultra's growth on an
emulator. That put Ultra at 1.93 GB, too much for 4 GB phones.

**Answers:**

- **The growth is ONNX Runtime's memory arena.** The arena keeps the
  largest buffers a session ever needed and grows in steps. Without it,
  Parakeet Ultra's own memory at a 27 s utterance is 895–993 MB on the
  Pixel; with it, 1128–1522 MB, depending on the order the lengths came in.
- **Without the arena, every new length is slower.** Each decode of a length
  the session hadn't seen allocates afresh. Dictation's utterances are
  almost always new lengths. On first decodes, Ultra is 1.30–1.34× slower,
  110M 1.4–1.9×, Canary 1.6–2.1× and Qwen3 1.5–1.7×. Warm repeats of the
  same clip had hidden this (the first round's "about 7 %" was wrong).
  Transcripts are byte-identical either way.
- **So the arena stays on, and comes off per phone** (`SpeedBudget.runsLean`,
  `OrtTuning.forPhone`). An engine runs without the arena only on a phone
  it doesn't fit with it, using its `LeanMode` (memory, cost factor) in
  `SpeedBudget.REFERENCE`. The recommender counts the engine as that phone
  runs it (`SpeedBudget.forPhone`), and so do the cost tiers (the phone's
  RAM is stored with each recommendation) and the engine load
  (`SpeechEngineManager`).
- **4 GB phones get Parakeet Ultra, lean.** Ultra needs 1710 MB with the
  arena (a 6 GB phone) and 1180 MB without it, so a 4 GB phone (1475 MB
  allowed) runs it lean at 1.34× its cost. That is still well within a
  Pixel-speed budget (3.57). 2 GB phones fit 110M and Canary lean.
- **The cap and the session stay as they are.** No forced cut, no split
  decode, no join, so there was nothing to measure at joins.
- **Canary's memory on the phone:** 950 MB with the arena (770 own,
  ascending lengths), 610 without.
- **Costs in one unit** (Ultra with the arena, first decodes). Canary 1.92
  and Granite 0.76 replace their host estimates (2.46, 0.429). Qwen3 (5.15),
  turbo (10.2–10.5), both Nemotrons and 110M are within 15 % of `REFERENCE`
  and stay. Qwen3 stays over a Pixel 8a's limit (4.46).

## Method

- The debug `SpeedBenchmarkService` via `tools/speed-bench/run.sh`, headless
  over wireless adb, 4 recognizer threads, sessions off (whole-clip decodes
  only). The clips are the 9 LibriSpeech test-clean `ls05/10/20-*` plus
  two new single utterances close to the cap: `ls28-1` (2961-960-0000,
  27.2 s) and `ls28-2` (5639-40744-0003, 27.1 s). Each decodes as one VAD
  utterance.
- **First decodes** (`first47-*`, fix round): `--ez shuffleClips true --ei
  repeats 1`. Every clip is decoded once, in a shuffled order, after a
  warm-up on 0.5 s of silence (as `SttEngine.warmUp`), so each timed decode
  is the first of its length. Two runs (seeds 1 and 2). Each engine runs
  with the arena on (`cpu-arena`) and off (`cpu-noarena`); Whisper turbo and
  Nemotron run with it on only.
- **Warm repeats** (`mem47-*`, first round): ls10-1, ls20-1, ls28-1, ls28-2
  in ascending order, 3–5 decodes each after a warm-up on ls10-1. Their
  medians hid the arena-off cost; their memory (ascending lengths grow the
  arena most) still counts.
- Own memory = the turn's peak RSS minus the process's RSS before its load
  (#31 review). A turn's peak is over all its clips, so the 27 s clips set it.
- `python3 tools/speed-bench/summarize.py
  docs/benchmarks/2026-10-pixel8a-mem/first47-*.json
  docs/benchmarks/2026-10-pixel8a-mem/mem47-*.json --kotlin` pools every
  turn. It prints each cost (first decodes against Ultra with the arena),
  the highest memory and the `LeanMode`. `emu47*.json` are the emulator
  runs.

## Conditions

Google Pixel 8a, Android 17, 2026-10-02: first round 05:11–05:24, fix round
05:40–06:12. On the charger (AC), battery 82–98 %, 24.7–36.1 °C, screen off
throughout. Thermal status was 0, except 1 (light) during the second fix-round
run's Nemotron 560 ms and turbo turns. The service probe read 242–301 ms,
and 379 ms before the second fix-round offline run, which was warmer: that
run is slower across the board, so compare tunings within a run.

## Pixel 8a: first decodes (fix round)

Pooled RTF over each clip's only decode (11 clips, 158 s), per run (seed 1 /
seed 2), and own memory:

| Engine | Arena | RTF seed 1 / 2 | × Ultra (arena on) | Slowest decode | Own MB | Lean cost factor |
|---|---|---|---|---|---|---|
| parakeet-ultra | on | 0.072 / 0.079 | 1.00 | 0.103 | 1128, 1237 | |
| parakeet-ultra | off | 0.093 / 0.102 | 1.30 | 0.125 | 895, 916 | 1.29 / 1.29 |
| parakeet-tdt-110m-en | on | 0.029 / 0.029 | 0.38 | 0.037 | 383, 379 | |
| parakeet-tdt-110m-en | off | 0.041 / 0.056 | 0.65 | 0.076 | 286, 287 | 1.41 / 1.93 |
| granite-speech-5-470m | on | 0.049 / 0.067 | 0.77 | 0.101 | 890, 892 | |
| granite-speech-5-470m | off | 0.055 / 0.074 | 0.85 | 0.120 | 916, 934 | 1.12 / 1.10 |
| canary-180m-flash | on | 0.128 / 0.155 | 1.88 | 0.249 | 562, 639 | |
| canary-180m-flash | off | 0.272 / 0.254 | 3.49 | 0.347 | 429, 393 | 2.13 / 1.64 |
| qwen3-asr-0-6b | on | 0.316 / 0.464 | 5.18 | 0.635 | 2683, 2802 | |
| qwen3-asr-0-6b | off | 0.551 / 0.678 | 8.16 | 0.971 | 1542, 1603 | 1.74 / 1.46 |
| nemotron-3-5-streaming-1120ms | on | 0.183 / 0.246 | 2.84 | 0.322 | 765, 772 | |
| nemotron-3-5-streaming-560ms | on | 0.355 / 0.367 | 4.79 | 0.441 | 771, 756 | |
| whisper-turbo | on | 0.770 / 0.773 | 10.24 | 1.131 | 2085, 2082 | |

Granite saves almost nothing without the arena (it even read higher here),
so it has no lean mode. With the arena, shuffled lengths left Ultra at
1128–1237 MB; ascending ones (first round) at 1513–1522 MB.

`REFERENCE` (own memory + 180 MB baseline, up to 10 MB; the highest of all
runs; costs and factors pooled over every #47 file):

| Engine | Cost × Ultra | MB with the arena | `LeanMode` (MB, cost factor) | Before #47 (cost, MB) |
|---|---|---|---|---|
| parakeet-ultra | 1.00 | 1710 | 1180, 1.34 | 1.00, 1930 |
| parakeet-tdt-110m-en | 0.378 (kept; 0.39 here) | 830 | 570, 1.68 | 0.378, 810 |
| canary-180m-flash | 1.92 | 950 | 610, 1.87 | 2.46, 1090 |
| qwen3-asr-0-6b | 5.96 (kept; 5.15 here) | 3130 | 1790, 1.63 | 5.96, 4110 |
| granite-speech-5-470m | 0.76 | 1150 | – | 0.429, 1850 |
| nemotron-3-5-streaming-560ms | 4.29 (kept; 4.92 here) | 960 | – | 4.29, 930 |
| nemotron-3-5-streaming-1120ms | 2.86 (kept; 2.92 here) | 960 | – | 2.86, 940 |
| whisper-turbo | 9.79 (kept; 10.52 here) | 2270 | – | 9.79, 2170 |

Every cost is against Ultra with the arena, as #31's are (its lineup's
Ultra RTF, 0.078, matches the first decodes' 0.075). `REFERENCE_BUDGET`
(3.57, distil) is in that unit too.

## Pixel 8a: warm repeats (first round)

Median RTF of 3 decodes (5 in the rerun) after a warm-up on ls10-1, lengths
in ascending order. The first decode of each clip without the arena was
0.086–0.105 for Ultra; the medians below hide it.

| Engine | Tuning | Arena | Own MB | RTF ls10-1 | RTF ls20-1 | RTF ls28-1 | RTF ls28-2 |
|---|---|---|---|---|---|---|---|
| parakeet-ultra | cpu (then off) | off | 981 | 0.073 | 0.071 | 0.074 | 0.076 |
| parakeet-ultra | cpu-arena | on | 1522 | 0.072 | 0.068 | 0.067 | 0.069 |
| parakeet-ultra (rerun) | cpu (then off) | off | 993 | 0.074 | 0.072 | 0.077 | 0.080 |
| parakeet-ultra (rerun) | cpu-arena | on | 1513 | 0.071 | 0.068 | 0.072 | 0.072 |
| parakeet-tdt-110m-en | cpu (then off) | off | 381 | 0.029 | 0.027 | 0.030 | 0.030 |
| parakeet-tdt-110m-en | cpu-arena | on | 650 | 0.029 | 0.027 | 0.028 | 0.028 |
| granite-speech-5-470m | cpu (then off) | off | 876 | 0.055 | 0.048 | 0.047 | 0.046 |
| granite-speech-5-470m | cpu-arena | on | 968 | 0.055 | 0.047 | 0.045 | 0.046 |
| canary-180m-flash | cpu | on | 770 | 0.116 | 0.129 | 0.122 | 0.151 |
| qwen3-asr-0-6b | cpu | on | 2949 | 0.286 | 0.307 | 0.286 | 0.362 |

In that round's build, `cpu` meant the arena off for Parakeet and Granite;
each turn's `arena` field says which.

## Emulator (x86_64, API 35, 3 threads)

First, to see whether the arena explains the growth. Own memory in MB:

| Engine | No arena, clips ≤ 20 s | No arena, + 27 s | Arena, + 27 s |
|---|---|---|---|
| parakeet-ultra | 902 | 992 | 1494 |
| parakeet-tdt-110m-en | 289 | 369 | 577 |
| canary-180m-flash | 362 | 436 | 765 |
| nemotron-3-5-streaming-560ms | 748 | 765 | 780 |

An arm64 build ran on the host during that sweep, so its RTFs are noise. A
second run on a quiet host, alternating the tunings twice (`emu47b-*`, 3
repeats of ls10-1, ls20-1, ls28-1):

| Engine | Arena | Own MB | RTF ls10-1 / ls20-1 / ls28-1 |
|---|---|---|---|
| parakeet-ultra | on | 1490–1501 | 0.179–0.187 / 0.189–0.195 / 0.192–0.194 |
| parakeet-ultra | off | 983–984 | 0.180–0.191 / 0.186–0.194 / 0.198 |
| canary-180m-flash | on | 775–777 | 0.291–0.300 / 0.393–0.405 / 0.385–0.396 |
| canary-180m-flash | off | 455–456 | 0.370–0.382 / 0.488–0.496 / 0.456–0.467 |

Canary is 1.2–1.27× slower without the arena: its decoder runs once per
token, each run allocating and freeing its buffers. (Warm repeats again:
on the Pixel's first decodes it was 1.6–2.1×.) Those runs were made
with an interim build where `cpu` meant arena off for every engine; their
`arena` field was added afterwards (`note` in each file).

## What changes for phones

RAM as phones report it (a 6 GB phone ≈ 5.5 GB, 4 GB ≈ 3.6, 3 GB ≈ 2.7, 2 GB ≈
1.8), 40 % for the engine (2253, 1475, 1106 and 737 MB):

- **6 GB and up** run every engine they fit with the arena, as before #47.
  Ultra fits (1710 of 2253). For a language only turbo and Qwen3 cover,
  6 and 8 GB phones still get turbo. Qwen3 would fit both lean (1790 MB,
  cost 5.96 × 1.63 = 9.7, a hair under turbo's 9.79), but only user
  engines count lean in the recommendation, so a developer engine is never
  promoted that way (#47 review). Turbo itself (2270) no longer quite fits a
  6 GB phone (2253 allowed): it is still the pick there, as the one needing
  the least memory, but marked low on memory instead of "may be slow". An
  8 GB phone (≈ 7.4 GB reported) fits turbo and not Qwen3 with the arena,
  as before. 12 GB phones get Qwen3, as before.
- **4 GB phones** get Parakeet Ultra for its 25 languages, lean (1180 of
  1475 MB, cost 1.34). Before: 110M for English, Canary for German, French
  and Spanish, Nemotron for the rest.
- **3 GB phones** don't fit Ultra either way: 110M for English, Canary for
  German, French and Spanish (both with the arena), Nemotron for the rest,
  as before.
- **2 GB phones** fit 110M lean (570 of 737 MB, cost 0.64), and Canary lean
  (610 MB, cost 3.6, within a Pixel-speed budget but not a slower phone's)
  for German, French and Spanish. Before, nothing fit: 110M and Nemotron
  were the least-memory fallbacks.
- **Canary** at 1.92 is within the budget of a phone half as fast as a
  Pixel 8a (2.23), where it was over at 2.46.
- **Qwen3** stays at 5.96, over a Pixel 8a's limit (4.46).

## Not measured

- Nemotron and Whisper turbo at 27 s on the Pixel (they don't grow:
  Nemotron 765 vs 748 MB on the emulator).
- Whisper turbo and Nemotron without the arena: they have no lean mode.
- Speed with the app in front (top-app cpuset): see #47's cpuset check.
- Silero's 28 s cap isn't a hard cut. Past it, sherpa-onnx only waits for a
  0.1 s pause at a stricter threshold (`voice-activity-detector.cc`), so
  continuous speech can run longer. Memory then grows past these figures,
  and Whisper (30 s window) could drop the end. A follow-up.
