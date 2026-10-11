# Engine speed on a Pixel 8a (#23) — PROVISIONAL

**Status (2026-09-27): the full Pixel 8a run hasn't happened.** The phone
wasn't reachable over adb while #23, #24 and #27 were built. #34's thread
sweep has since run on it (`2026-09-pixel8a-threads.md`): it measured the
probe anchor (`SpeedBudget.PROBE_REFERENCE_MS`, below) and three engines'
RTF, which agree with the relative costs here. The rest of
`SpeedBudget.REFERENCE` is still the provisional estimates below. The full
run is `tools/speed-bench/README.md`; `summarize.py --kotlin` prints the
replacement values.

## Relative costs: host ratios

Each engine's cost relative to distil-small.en is its host RTF (benchmark host,
the same int8 files, #14's uncontended 2-thread LibriSpeech RTF,
`2026-09-host-wer.md`) divided by distil's, then moved to the Pixel's 4
threads with #20's measured 2→4 speed-ups: distil 1.26×, Parakeet 1.49×,
Nemotron EN 1.37×. Nemotron 3.5 takes Nemotron EN's; every other engine
distil's (so its ratio doesn't move). Peak RSS is the host process's, Python
included.

| Engine | Host RTF, 2 threads | Speed-up to 4 threads | Cost vs distil, 4 threads | Peak RSS MB |
|---|---|---|---|---|
| distil-small-en | 0.101 | 1.26× | 1.00 | 1121 |
| whisper-tiny-en | 0.047 | 1.26× (assumed) | 0.47 | 596 |
| whisper-base-en | 0.085 | 1.26× (assumed) | 0.84 | 874 |
| whisper-small-en | 0.242 | 1.26× (assumed) | 2.40 | 1486 |
| whisper-turbo | 0.277 | 1.26× (assumed) | 2.74 | 3872 |
| parakeet-tdt-0-6b-v3 | 0.031 | 1.49× | 0.26 | 2259 |
| canary-180m-flash | 0.070 | 1.26× (assumed) | 0.69 | 1101 |
| nemotron-3-5-streaming-320ms | 0.207 | 1.37× (Nemotron EN's) | 1.88 | 2004 |
| nemotron-3-5-streaming-560ms | 0.132 | 1.37× (Nemotron EN's) | 1.20 | 2013 |
| nemotron-3-5-streaming-1120ms (2026-09-28) | 0.67× 560 ms's | 1.37× (Nemotron EN's) | 0.80 | 2030 |
| nemotron-en-streaming-560ms | 0.145 | 1.37× | 1.32 | 1015 |
| qwen3-asr-0-6b | 0.169 | 1.26× (assumed) | 1.67 | 4082 |
| parakeet-tdt-110m-en (#25) | 0.33× v3's | 1.49× (Parakeet's) | 0.09 | 608 |
| parakeet-tdt-0-6b-v2 (#25) | 0.98× v3's | 1.49× (Parakeet's) | 0.25 | 1369 |
| parakeet-unified-en-0-6b (#25) | 1.02× v3's | 1.49× (Parakeet's) | 0.27 | 1575 |
| parakeet-unified-en-streaming-560ms (#25) | 40× v3's | 1.49× (Parakeet's, assumed) | 10.4 | 1149 |

#25's engines ran next to other jobs, so their RTF is a ratio to Parakeet
v3's in the same run, times v3's cost here (0.26).

Why this is only an estimate: the host is an x86 desktop CPU, the phone an
Arm big.LITTLE one, and the ratio between engines moves with the CPU. The
emulator run below (x86 again, 3 threads) already disagrees on the streaming
engine: Nemotron EN cost 2.01× distil there against 1.32× here, and Whisper
tiny 0.28× against 0.47×.

#34's Pixel sweep (4 threads, the screen on, on battery) cross-checks two
of these: Parakeet v3 at RTF 0.066–0.072 against distil's 0.27–0.31 is
0.22–0.25 (0.26 here), and Nemotron 3.5 at about 0.36 is about 1.25 (1.20
here).

## Thread counts

`REFERENCE` is at 4 recognizer threads, the Pixel's. A phone with fewer
(`RecognizerThreads`: its performance cores, clamped to 2–4) pays each
engine's `SpeedBudget.threadPenalty`, from #34's sweep: Parakeet 1.45× at 2
threads and 1.14× at 3; streaming transducers the same at 3, 1.37× at 2
(the host's #20 figure: the Pixel's 2-thread Nemotron turn was throttled);
Whisper nothing, as distil is flat from 2 to 8 threads. Canary and
Qwen3-ASR weren't measured and are assumed to behave like Whisper
(autoregressive decoders).

## Probe anchor: measured on the Pixel

**Superseded (#31, 2026-10-02):** `PROBE_REFERENCE_MS` is now 157 ms, the
app's own probe on the Pixel 8a. The figures below came from the debug
benchmark service, which runs outside Android's top-app cpuset (no boost,
no Cortex-X3 core) and reads about 240 ms even idle. See
`2026-10-pixel8a-ort.md`.

The phone's speed factor comes from a short CPU probe that needs no engine
(`speech/SpeedProbe.kt`): `PROBE_REFERENCE_MS` ÷ the phone's probe time.
#34 measured the probe on the Pixel 8a: **291 ms** (`threads-a`) and **264 ms**
(`threads-b`). `PROBE_REFERENCE_MS` = 264, the lower, since other work can
only slow a probe down. Both were on battery with the screen on, and with the
probe's earlier median of 3 runs (it now takes the fastest of 5), so a Pixel
8a may read a little faster than 1.0×.

No thread correction: distil-small.en, the budget's unit, decodes no faster
on more threads, so the factor is the plain CPU ratio, and the thread count
moves engine costs instead (above).

Before #34 the anchor came through the emulator (143 ms, from its probe time,
197 ms, and its distil RTF against the Pixel's projected one). The measured
264 ms makes the emulator about 1.3× a Pixel 8a on the probe, not 0.65×,
though it decodes distil about as fast as the Pixel (RTF 0.307 at 3 threads
against 0.27–0.31): its single-threaded probe runs on a desktop core. So on
an emulator the probe overstates the speed by about a third; the phone is
what counts.

## Emulator run (2026-09-27)

An x86_64 AVD on the dev machine (Android 15), three engines at 2 and 3 threads,
two runs (the second re-ran distil and tiny at 3 threads with the cold-start
session added; the table takes the latest turn). A dry run of the harness
more than a measurement: an emulator on a desktop isn't a phone.

| Engine | Threads | Load s | RTF 5 s | RTF 10 s | RTF 20 s | RTF LS | × distil | First partial s (5/10/20) | End of speech → Final s (5/10/20) | Peak RSS MB | WER LS % |
|---|---|---|---|---|---|---|---|---|---|---|---|
| whisper-tiny-en | 3 | 0.8 | 0.086 | 0.085 | 0.086 | 0.086 | 0.28 | 0.5/0.5/0.5 | 1.5/1.8/2.5 | 591 | 7.9 |
| whisper-tiny-en | 2 | 0.5 | 0.100 | 0.099 | 0.099 | 0.099 | 0.26 | 0.4/0.5/0.4 | 1.7/2.0/2.7 | 570 | 7.9 |
| distil-small-en | 3 | 2.2 | 0.371 | 0.310 | 0.288 | 0.307 | 1.00 | 1.4/1.4/1.3 | 3.1/5.3/6.7 | 997 | 4.0 |
| distil-small-en | 2 | 0.9 | 0.470 | 0.377 | 0.348 | 0.375 | 1.00 | 1.7/1.7/1.6 | 3.6/5.3/9.5 | 973 | 4.0 |
| nemotron-en-streaming-560ms | 3 | 11.0 | 0.591 | 0.606 | 0.632 | 0.618 | 2.01 | 2.7/1.0/0.6 | 7.6/1.1/0.9 | 921 | 2.9 |
| nemotron-en-streaming-560ms | 2 | 2.6 | 0.738 | 0.689 | 0.699 | 0.702 | 1.87 | 1.2/1.1/0.8 | 1.1/1.2/0.9 | 925 | 2.9 |

2 → 3 threads: distil 1.22×, Nemotron EN 1.14×, tiny 1.15×. Nemotron EN at
3 threads was the first turn of the first run, before the harness had a
separate cold-start session, so its 7.6 s end of speech → Final on the 5 s
clips carries the app's cold start (the 10 s and 20 s clips: about 1 s).
