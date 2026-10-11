# Pixel 8a recognizer thread count, September 2026 (#34)

How many ONNX Runtime threads should a recognizer get on the Pixel 8a
(Tensor G3: 1 Cortex-X3, 4 Cortex-A715, 4 Cortex-A510)? `RecognizerThreads`
gives it 4: the performance cores (5), capped at 4. Before #34 that value was
unmeasured.

**Answer: 4 threads, for every engine.** Parakeet v3, the main engine, is
fastest at 4. It is 1.14× slower at 3, 1.4× at 2, and 1.7–1.8× slower at 5
or 6, although the phone has 5 performance cores. distil-small.en is flat from
2 to 8 threads. Nemotron 3.5 streaming is about the same at 3 and 4. One
count serves all three engines, so `RecognizerThreads` keeps
`MAX = 4` (now with the measurement in its KDoc and a test).

**Note (#31, 2026-10-02):** the benchmark service runs outside Android's
top-app cpuset, so its threads may only use cores 0–7: the four A715s and
four A510s, not the Cortex-X3. A 5th thread therefore had to share an A510,
which likely explains the cliff at 5. #31 re-swept Parakeet Ultra with
spinning on and off and found the same cliff
(`2026-10-pixel8a-ort.md`). With the app in front, all nine cores are
available, and that wasn't measured.

## Method

- The debug-only `SpeedBenchmarkService` (#23) driven by `tools/speed-bench/`,
  headless over wireless adb. It uses the app's own JNI build and decode path.
- Clips: LibriSpeech test-clean `ls10-1` (10.9 s) and `ls20-1` (19.6 s). After a
  warm-up decode, each is decoded 3 times per engine and thread count
  (`--es clips ls10-1,ls20-1 --ei repeats 3 --ez sessions false`). RTF =
  decode time / clip length. The tables give the median of the 3 runs, with
  the range.
- Engines and thread counts ran in a shuffled order, with a 30 s cool-down
  between them. Thermal status and battery temperature and level were logged
  before and after each one.
- Two runs:
  - `threads-a` (21:10–21:19): distil and Parakeet at 2–6 threads.
  - `threads-b` (21:19–21:45): all three engines at 1, 2, 3, 4, 5, 6 and 8 threads.

  Raw results: `2026-09-pixel8a-threads/*.json`.

## Conditions: read before the numbers

Max chose to run the sweep **on battery** (the phone was at 20 % and
discharging). The debug service gained `--ez unplugged true` and
`--ei stopBattery 10` for this.

- **The screen was on** for most turns. It was on for `threads-a` up to
  distil@2; it then went off (`KEYCODE_SLEEP`), and parakeet@5, distil@3 and
  distil@4 ran with it off. Something woke it about a minute into `threads-b`,
  and it stayed on, with the launcher in front, for every later turn.
- **The phone throttled late in `threads-b`.** Once the battery was at 15 %
  or below and 39.7 °C or above, model load times doubled: Parakeet went from
  3.3–4.0 s to 7.2–7.3 s, and Nemotron from 3.7–4.8 s to 7.5–7.8 s. RTF
  worsened by 1.5–2.5×, though Android's thermal status stayed at 1 (light).
  Those turns are marked *throttled* below. They are not used for the
  conclusions, except where noted.

Most turns had the screen on, so treat the absolute RTFs as a little
pessimistic. The comparisons between thread counts
hold, because each conclusion rests on turns taken under the same conditions.

## Results (median RTF, 10 s / 20 s clip; lower is faster)

### Parakeet TDT 0.6B v3 (the main engine)

| Threads | `threads-a` | `threads-b` | vs 4 |
|---|---|---|---|
| 1 | – | 0.167 / 0.171 | 2.4× |
| 2 | 0.094 / 0.094 | 0.107 / 0.104 | 1.4–1.5× |
| 3 | 0.078 / 0.077 | *throttled* 0.179 / 0.168 | 1.14× |
| **4** | **0.069 / 0.066** | **0.069 / 0.072** | 1.00× |
| 5 | 0.121 / 0.127 (screen off) | *throttled* 0.165 / 0.166 | 1.8× |
| 6 | 0.124 / 0.124 | 0.120 / 0.123 | 1.7–1.8× |
| 8 | – | *throttled* 0.141 / 0.133 | (≥1.9×) |

The cliff at 5 is the clearest result of the sweep. It appears in both runs,
with the screen on and off, and the 3-run ranges don't overlap (5 threads:
0.114–0.129; 4 threads: 0.066–0.073). The phone has exactly 5 performance
cores. The likely cause (not traced): 5 ORT threads (4 pool threads plus the
calling thread) share those cores with the audio pipeline and the system, so
one lands on an A510 and the others wait for it at every sync point.

### distil-small.en (the speed-budget reference)

| Threads | `threads-a` | `threads-b` |
|---|---|---|
| 1 | – | 1.002 / 0.946 (load 2.6 s: possibly throttled) |
| 2 | 0.347 / 0.334 | 0.290 / 0.312 |
| 3 | 0.227 / 0.277 (screen off) | 0.240 / 0.307 |
| 4 | 0.309 / 0.270 (screen off) | *throttled* 0.355 / 0.272 |
| 5 | 0.290 / 0.277 | *throttled* 0.349 / 0.365 |
| 6 | 0.271 / 0.287 | *throttled* 0.356 / 0.354 |
| 8 | – | 0.269 / 0.304 |

From 2 to 8 threads distil sits between 0.23 and 0.35 with no trend. Within
a turn the runs vary as much as between thread counts (e.g. 5 threads,
10 s: 0.171–0.293). The small autoregressive Whisper decoder dominates, and
extra threads don't help it. Only 1 thread is clearly worse (3.5×). 4
threads costs distil nothing measurable.

### Nemotron 3.5 streaming 560 ms (the streaming path)

`threads-b` only; the screen was on for all of these turns.

| Threads | RTF | Load s | Note |
|---|---|---|---|
| 1 | 0.835 / 0.593 | 7.5 | throttled |
| 2 | 0.625 / 0.637 | 7.7 | throttled |
| 3 | 0.266 / 0.437 | 4.6 | clean; the screen came on during it |
| 4 | 0.350 / 0.377 | 7.8 | throttled |
| 5 | 0.404 / 0.512 | 4.8 | |
| 6 | 0.408 / 0.584 | 3.7 | |
| 8 | 0.708 / 0.789 | 7.6 | throttled |

Even throttled, 4 threads matched the clean 3-thread turn (mean 0.36 vs
0.35). An unthrottled 4 is therefore at least as fast as 3. 5 and 6 are
slower (0.46, 0.50) and 8 is the slowest measured. So Nemotron fits the same
rule: 4, not more.

## Recommendation and change

- **Keep 4 recognizer threads on the Pixel 8a, for every engine family.**
  The count comes from `RecognizerThreads.forClusters`: performance cores,
  clamped to 2–4. Parakeet needs 4. Nemotron is best at 3–4. distil doesn't
  care. A per-family count would buy nothing measurable.
- **Don't raise `MAX` for phones with more performance cores.** On the Pixel
  8a, using all 5 performance cores was 1.7–1.8× slower than 4. The cap is
  what gives the right answer here, so it now has a test (`never more than 4,
  even with more performance cores`).

## Not measured

- **Nemotron first-partial latency at 3 and 4 threads** (the issue asks for
  it). The session pass needs about 10–15 minutes on the charger, and the
  battery hit the 10 % floor. A clean, unthrottled Nemotron 4-thread decode
  RTF is also still missing.
- A screen-off, on-charger rerun of the key configs (distil and Parakeet at
  3, 4 and 5 threads). Parakeet's result held with the screen both on and off,
  so it is unlikely to change the answer.
- Whisper tiny.

Rerun, with the phone on the charger and the screen off:

```bash
OUT=docs/benchmarks/2026-09-pixel8a-threads tools/speed-bench/run.sh --es tag threads-c \
    --es engines distil-small-en,parakeet-tdt-0-6b-v3,nemotron-3-5-streaming-560ms --es threads 3,4,5 \
    --es clips ls10-1,ls20-1 --ei repeats 3 --ez sessions false --ei cooldown 45
OUT=docs/benchmarks/2026-09-pixel8a-threads tools/speed-bench/run.sh --es tag threads-nemo-sessions \
    --es engines nemotron-3-5-streaming-560ms --es threads 3,4 --es clips ls05-1,ls10-1,ls20-1
python3 tools/speed-bench/summarize.py docs/benchmarks/2026-09-pixel8a-threads/*.json --sweep
```
