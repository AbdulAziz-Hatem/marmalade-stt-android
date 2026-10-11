# Qwen3-ASR 0.6B: where the time goes and what would make it faster, October 2026 (#50)

Qwen3-ASR is the most accurate engine for Chinese (#37), but it is too slow and too big for a Pixel 8a. Its cost is
5.96 Parakeet Ultra units (`SpeedBudget.REFERENCE`) against the Pixel 8a's budget of 3.57 × 1.25 = 4.46. With ONNX
Runtime's arena its peak memory is 3130 MB, over the 40 % RAM rule on an 8 GB phone (about 2900–3100 MB allowed,
depending on what the phone reports), so the Pixel runs it lean: 1790 MB at 1.63× the cost, about 9.7 units. This host spike measures where the time
goes and tries the cheap speed-ups. Everything ran on the benchmark host's CPU with the app's int8 files and sherpa
configuration. The host can't measure phone speed, so every Pixel number below is an estimate to check.

## Recommendation

| # | Change | What changes in the app | Host effect | Pixel 8a cost, Ultra units (now 9.7 lean) | Ship? |
|---|---|---|---|---|---|
| 1 | **LM head on the last position only** in the prefill | A new decoder file (graph surgery on the pinned int8 file, same weights: one `Slice` before the LM head). It would be hosted on our `marmalade-stt-engines` release with a model-review addendum. Plus a one-line sherpa patch: the prefill reads logits row `time_dim − 1`, not `context_len − 1` (v1.13.8 returns "" otherwise) | Peak memory at 27 s: −585 MB (2664 → 2079). Prefill −20 to −23 %, total about −3 to −5 %. Tokens identical on 100 of 100 clips | **≈ 5.7, with the arena.** Fits the RAM rule if the Pixel's peak drops as much as the host's (3130 → ≈ 2550 MB) | **Yes**, after the Pixel memory check |
| 2 | **Fix the log-mel FFT** | A patch to kaldi-native-fbank v1.22.3 (sherpa's FetchContent dependency). `WhisperFeatureComputer` uses whisper.cpp's recursive FFT, which for N = 400 ends in a naive DFT with a `sin`/`cos` per term. Use knf's own kissfft `Rfft` | Features cost 15.4 ms per audio second on one core: 8–15 % of Qwen3's host time. Whisper turbo uses the same code | About −5 to −12 % (not measured on a phone) → **≈ 5.1–5.4** with #1 | **Yes**: cheap, helps every Whisper-feature engine |
| 3 | **Scale `max_new_tokens` with the segment length, raise `max_total_len`** | Kotlin only: `stream.setOption("max_new_tokens", ceil(10 × seconds + 30))` per decode (sherpa already reads it per stream), and `OfflineQwen3AsrModelConfig.maxTotalLen = 768` | No speed change on normal speech. A runaway on a 2 s segment stops after 50 steps instead of 128. Long fast utterances are no longer cut | 0. KV cache +59 MB | **Yes**, for correctness. **Shipped in #50 in a wider form:** `max(128, ceil(16 × seconds) + 30)` and 896 positions (+88 MB), so Qwen3 as a developer engine covers Hindi too and no decode gets less than before (end of §4) |
| 4 | 4-bit decoder (MatMulNBits, RTN) | A new converted file | 0.79–0.92× the time, but +0.5 to +6.7 CER points | – | **No**, unless a calibrated 4-bit export keeps accuracy *and* the Pixel shows a large step speed-up |
| 5 | Other thread counts, or a separate decoder thread count | – | 8 threads: 0.85–0.91×; decode steps barely scale | – | **No**: keep 4 |

**Bottom line:** #1 to #3 take Qwen3 on a Pixel 8a from about 9.7 units to about 5.1–5.7, mostly by letting it
keep the arena. That is still over the Pixel 8a's 4.46: it stays "slow" there. 60–85 % of its time is
token-by-token decoding, and none of the safe changes make those steps faster. Ways to do that, none measured:
- A calibrated 4-bit export (GPTQ/AWQ), only if the Pixel shows that 4-bit steps are much faster on ARM than on
  the host (check 2 below).
- A re-export with fused attention. Every step copies each layer's KV cache through Slice → Transpose → Tile
  (the GQA head repeat), so step time grows with context: 24 ms at a 122-token context, 37 ms at 382, on the host
  at 4 threads.

## 1. Baseline: the app's configuration at 4 threads

The Pixel's thread count, one job, one recognizer at a time. CER in % (`cer.py`, as in #29/#37); ± is the 95 %
bootstrap half-width. Tokens per second = tokens generated ÷ seconds of decoded audio (VAD segment + 0.6 s
pre-roll).

| Set | Clips | CER | CER without digits (n) | RTF | RTF without features | Tokens/s (mean) | Tokens/s (max per decode) | Load |
|---|---|---|---|---|---|---|---|---|
| FLEURS zh | 100 | 9.3 ±2.3 | 6.5 (73) | 0.087 | 0.077 | 3.3 | 4.4 | 4.6 |
| AISHELL-1 | 200 | 2.6 ±0.8 | 2.6 (199) | 0.088 | 0.075 | 2.4 | 4.5 | 5.6 |
| FLEURS ko | 100 | 6.3 ±2.0 | 5.8 (80) | 0.117 | 0.106 | 4.7 | 6.8 | 9.5 |
| Zeroth ko | 100 | 5.6 ±1.2 | 4.0 (71) | 0.155 | 0.142 | 5.3 | 7.3 | 8.7 |

- These match #37 (9.2 / 2.7 / 6.5 / 4.9) except Zeroth. There, 63 of 100 hypotheses differ from #37's run,
  mostly digits against spelled-out numbers ("88%" against "팔십팔 퍼센트"). Thread count isn't the cause: 2, 4
  and 8 threads give identical text (80 clips, section 3). The shared venv's sherpa build was rebuilt at 07:15,
  after #37's run (04:48). That is the likely cause, but I didn't pin it down. All variants here use the same build.
- No decode hit the 128-token cap, and sherpa's repetition-loop cut didn't fire (510 decodes; runs 3 and 5
  repeated this configuration with byte-identical text).

## 2. Where the time goes

**Sherpa, the same clips decoded with `max_new_tokens` 1 and with the default** (run 1, interleaved, 4 threads).
The difference ÷ the tokens generated gives the cost of one decode step. The `max_new_tokens` 1 run gives
conv front-end + encoder + prefill (and the first token's argmax).

| Set | Decodes | Mean segment s | Features, s per clip | Encoder + prefill, s per decode | Steps per decode | ms per step | Share of model time: encoder + prefill / steps |
|---|---|---|---|---|---|---|---|
| FLEURS zh | 103 | 7.8 | 0.122 | 0.232 | 25.5 | 25.2 | 26 % / 74 % |
| AISHELL-1 | 207 | 4.3 | 0.068 | 0.150 | 10.1 | 22.5 | 40 % / 60 % |
| FLEURS ko | 100 | 9.3 | 0.146 | 0.280 | 43.4 | 25.7 | 20 % / 80 % |
| Zeroth ko | 100 | 7.4 | 0.116 | 0.243 | 39.6 | 26.8 | 19 % / 81 % |

Encoder + prefill ≈ 0.05 s + 0.024 s per second of segment. "Features" is sherpa's 128-bin log-mel
(`acceptWaveform`): 15.4 ms per audio second at any length, on one core (`feat_bench.py`). That is 8–15 % of the
whole decode, which is why fix #2 is worth doing.

**Each stage alone** (`stage_timing.py`: plain ONNX Runtime running the three graphs as sherpa does, on random
features of the right shape; ms, median of 3 after a warm-up). At 8 s it agrees with sherpa's
`max_new_tokens` 1 run within 4 % (223 vs 232 ms).

| Audio s | Context tokens | Conv front-end | Encoder | Prefill | Prefill, LM head on last position | One step |
|---|---|---|---|---|---|---|
| 3 | 57 | 21 | 29 | 85 | 70 | 27 |
| 8 | 122 | 49 | 32 | 142 | 111 | 24 |
| 15 | 213 | 89 | 51 | 245 | 193 | 27 |
| 28 | 382 | 191 | 91 | 509 | 393 | 37 |

- **Prefill is about two thirds of the non-step time.** It runs the LM head (1024 × 151 936) on every prompt
  position, though only the last is used. That is ~20 % of the prefill. At a 28 s utterance it also allocates four
  [382 × 151 936] buffers (int32, then float casts), about 230 MB each: hence fix #1's memory saving.
- The conv front-end is fp32 (not quantized) and costs more than the int8 encoder.
- **Steps don't depend on the KV cache's allocated length** (128 / 256 / 512 all within noise), so shrinking
  sherpa's fixed 512-position cache wouldn't help. They do grow with the context actually used (24 → 37 ms):
  that is the per-step cache copying noted above.

## 3. Threads

Same 80 clips (20 per set), all six variants interleaved (run 2, load 11–12, so absolute times are higher than in
section 2):

| Threads | Total time vs 4 threads | Encoder + prefill, ms per audio s | ms per step |
|---|---|---|---|
| 2 | 1.29–1.45× | 50 | 43–50 |
| 4 | 1.00 | 30 | 30–37 |
| 8 | 0.85–0.91× | 20 | 26–35 |

Stage replica at 28 s, 2 / 4 / 8 threads: conv 349 / 191 / 148 ms, encoder 160 / 91 / 82, prefill
921 / 509 / 397, step 45 / 37 / 36. The compute-bound stages scale; the steps, which are bound by memory traffic,
hardly gain past 4 threads. **A separate decoder thread count wouldn't help.** Sherpa's decoder session runs both
the prefill (which scales) and the steps (which don't), and the conv front-end and encoder together are only
about a third of the non-step time. The transcripts were identical at 2, 4 and 8 threads.

## 4. A `max_new_tokens` bound scaled to the audio

**What shipped (#50 review):** `qwen3MaxNewTokens` = `max(128, ceil(16 × seconds) + 30)` and `QWEN3_MAX_TOTAL_LEN` = 896
(`SherpaBackends.kt`). While Qwen3 is a developer engine it transcribes all 30 languages, so the rate covers the
densest measured (Hindi, 15.3 a second at most), and nothing gets less than sherpa's 128. At 28.6 s that is 488
tokens: 390 + 488 = 878 positions. The 384 positions over sherpa's 512 cost 88 MB, which `SpeedBudget.REFERENCE`
adds to Qwen3's measured peaks. Short segments no longer stop runaways sooner (that is #49's).

Tokens per second of decoded audio, per decode, with the cap raised to 512 where it was hit:

| Set | Mean | Max | Decodes at the app's 128 cap |
|---|---|---|---|
| FLEURS zh / AISHELL-1 | 3.3 / 2.5 | 4.4 / 4.5 | 0 / 0 |
| FLEURS ko / Zeroth ko | 4.8 / 5.4 | 6.8 / 7.3 | 0 / 0 |
| FLEURS ja (40) | 3.8 | 5.3 | 0 |
| LibriSpeech clean (40) | 3.6 | 5.4 | 0 |
| FLEURS hi (40) | 11.3 | 15.3 | **10 of 40** (CER 14.5 → 9.1 without the cap) |

Hindi needs about 11 tokens per second, so 128 tokens run out after about 11 s. This is very likely #37's "Qwen3
lost the end of 26 of 100 Hindi clips". (Hindi is scored by CER here, not #37's WER.) **The KV cache is a second cap.** At 28.6 s the prompt and audio take 390 of
sherpa's 512 positions, leaving 122 tokens. On whole 25–27 s utterances (consecutive clips joined, `long_test.py`):

| Set (6 utterances) | Tokens needed | App (128, 512) | `max_new_tokens` 400, 512 | 400, `max_total_len` 1024 |
|---|---|---|---|---|
| Zeroth ko | 96–118 | CER 7.0 | 7.0 | 7.0 |
| FLEURS zh | 45–61 | CER 1.7 | 1.7 | 1.7 |
| FLEURS hi | 201–294 | CER 49.7, all cut | 38.9, cut by the cache | **9.0** |

Korean needed 96–118 tokens here, just under the 122 that remain, so fast Korean at the 28 s cap would be cut.
`ceil(10 × seconds + 30)` is at least 1.9× the tokens of every one of the 598 decodes outside Hindi (at least 35
tokens to spare). Hindi, which the app doesn't offer Qwen3 for, needs `15 × seconds + 30` and `max_total_len` 896.
At 28.6 s, 10 × seconds + 30 is 316 tokens, which fits `max_total_len` 768 (390 + 316). The extra KV memory is 59 MB (fp32,
28 layers × 2 × 256 positions × 8 heads × 128 × 4 B). Short segments get a tighter cap: 50 at 2 s, 80 at 5 s,
128 from 9.8 s. A runaway on a short segment therefore stops sooner: at 25 ms a step, 78 fewer steps on a 2 s
segment is about 2 s less on the host (and more on a phone). Sherpa already cuts loops of 3 or fewer distinct
tokens after 64 tokens. Runaway handling itself is #49.

## 5. A 4-bit decoder

Source: the fp32 export from the same lineage, ModelScope `zengshuishui/Qwen3-ASR-onnx` `model_0.6B/decoder.onnx`
(+ `.data`, 3.0 GB, Apache-2.0). Its `decoder.int8.onnx` has our pinned sha256 (`4f6885be…`). Quantized with ORT
1.28.0's `MatMulNBitsQuantizer`: symmetric, `accuracy_level` 4, embedding via `GatherBlockQuantized`. Inputs and
outputs are unchanged, and sherpa runs it as a drop-in. The quantizer bumps the opset to 21, which breaks opset-17
`ReduceMean`, so the script sets it back to 17. All runs used the same clips, interleaved, at 4 threads:

| Decoder | File MB | FLEURS zh | AISHELL-1 | FLEURS ko | Zeroth ko | Time × int8 | Host own peak MB at 27 s |
|---|---|---|---|---|---|---|---|
| int8 (the app's) | 756 | 9.3 | 2.6 | 6.3 | 5.6 | 1.00 | 2561 |
| 4-bit, block 32 | 471 | 9.9 (+0.6) | 3.1 (+0.5) | 7.0 (+0.7) | 7.9 (**+2.3**) | 0.85–0.92 | 2378 |
| 4-bit, block 128 | 400 | 10.6 (+1.3) | 5.3 (+2.6) | 9.9 (+3.6) | 12.3 (+6.7) | 0.79–0.83 | 2161 |
| 4-bit b32 layers, 8-bit LM head, fp32 embedding | 1059 | 10.1 (+0.8) | 3.2 (+0.6) | 6.8 (+0.5) | 6.8 (+1.2) | 0.89–0.95 | – |

Differences are paired bootstraps over the same utterances. Block 32's Zeroth interval is [+1.1, +3.5]; its other
intervals touch zero, but all four point the same way. Block 128 also produced one repetition loop and one decode
at the cap. The 8-bit LM head doesn't recover accuracy, so the loss comes from round-to-nearest 4-bit on the
transformer layers. (8-bit `GatherBlockQuantized` isn't supported, hence the fp32 embedding in that test file.)
On the host the steps get faster (stage replica, 8 s: 24 → 18 ms for block 32, 17 ms for block 128) but block
32's prefill gets slower (142 → 189 ms), so the total gain is small.

**Does it transfer to the phone?** The decode steps are bound by memory traffic: about 599 MB of weights per step
in int8, 373 MB in block 32, 317 MB in block 128, plus the cache copying. A phone has much less bandwidth than
the benchmark host, so the steps could gain more there than here. The app's ORT 1.28.0 Android AAR has the ARM64 kernels:
`libonnxruntime.so` (arm64-v8a) contains KleidiAI's
`kai_matmul_clamp_f32_qai8dxp…_qsi4c32p…_neon_dotprod` (one-row steps) and `…_neon_i8mm` (prefill), plus MLAS's
`sqnbitgemm_neon` int8 path. The Tensor G3 has dotprod and i8mm. These kernels need symmetric weights in blocks
that are a multiple of 32, which ours are. Whether ORT picks them for this graph, and how fast they run, is
unmeasured. The host's int8 path (`MatMulInteger` on AVX-512 VNNI) is strong, so a small host gain says little
either way.

## 6. Memory and the arena

#47's lean figures stand (Qwen3 without the arena: 1790 MB, 1.63× slower). Peak own memory on the host, arena on,
4 threads, lengths in ascending order (8.5, 10.4, 27.1, 27.2 s: the arena's worst case):

| Decoder | After load | Peak at 27 s |
|---|---|---|
| int8 (sherpa) | 1186 | 2561 |
| 4-bit block 32 (sherpa) | 981 | 2378 |
| 4-bit block 128 (sherpa) | 753 | 2161 |
| int8, ORT replica | 1173 | 2664 |
| int8 + LM head on last position, replica | 1166 | **2079** |
| 4-bit block 128 + last position, replica | 761 | 1713 |

The Pixel's own peak for Qwen3 with the arena was 2683–2802 MB (#47), close to the host's 2561–2664. If fix #1
saves on the phone what it saves here, `REFERENCE` drops from 3130 to about 2550 MB. That fits an 8 GB phone
(about 2900–3100 MB allowed) with the arena, so the Pixel 8a would stop running Qwen3 lean: 5.96 × ≈0.96
instead of 5.96 × 1.63. It still wouldn't fit a 6 GB phone (2253 MB). A smaller decoder alone (4-bit) saves less
(185–400 MB) and costs accuracy. Fix #3 as proposed adds 59 MB; as shipped (896 positions), 88 MB.

## One Pixel session: what to check

**Not run yet (2026-10-02):** the phone was reachable only through a slow network relay (about 0.05 MB/s), too
slow to stage 1.2 GB of decoders. The kit is ready on the dev machine in a scratch directory outside the repo (`stt-50-pixel-spike/`: a test
APK with both candidate decoders as temporary developer engines, the one-line sherpa patch, and a README); the
staged model directories are on the benchmark host in `$WORK/models-50/pixel/`. Run it when the phone is on the LAN.

On a debug build carrying fix #1's sherpa patch, with the candidate decoder files side-loaded. Run the speed
bench (`tools/speed-bench/run.sh`, first decodes, 4 threads, `ls05/10/20` + `ls28-1/2`), Qwen3 only:
1. **int8 vs int8 + last position, arena on:** own memory at ls28 (expect about −0.5 GB; own + 180 MB must come
   in under 0.4 × the Pixel's `totalMem`, so own must be about 2700 MB or less) and RTF. This decides whether `REFERENCE` gets new memory figures and the
   Pixel 8a keeps the arena.
2. **int8 vs 4-bit block 32:** RTF on the same clips. If the steps aren't at least about 1.5× faster on the
   phone, drop 4-bit for good. If they are, a calibrated 4-bit export is worth a look.
3. **Features:** time `createStream` + `acceptWaveform` alone on a 27 s clip (expect 15–50 ms per audio second
   before fix #2).

## Method

- **Host:** the benchmark host (Ryzen AI Max+ 395, 16 cores / 32 threads), 2026-10-02, CPU only, `nice -n 19`, at most 8
  ORT threads of mine at once. Another benchmark's asr-bench jobs (#52) ran throughout; the 1-minute load average
  is in every result row (4.4–12.4). Absolute RTFs are therefore noisy, so speeds are compared only within an
  interleaved run. Each run decodes its variants clip by clip in rotating order, one recognizer at a time.
- **Runtime:** sherpa-onnx v1.13.8 built from source with the app's patches (the shared `$WORK/venv`, bundling
  libonnxruntime 1.28.2). Scratch venv `scratch-50/venv`: onnxruntime 1.28.0 (the app's version) and onnx 1.23.1,
  for conversions and the replica. Session as `asrbench.run`: VAD, AGC stand-in, 0.6 s pre-roll, language hint
  (Chinese / Korean). Qwen3 config as the app's: greedy, `max_new_tokens` 128, `max_total_len` 512,
  temperature 1e-6.
- **Stage split:** two methods. (a) Sherpa with `max_new_tokens` 1 against the default, same clips and segments;
  decodes at the cap or cut as loops are excluded; the `max_new_tokens` 1 run includes the first token. (b) The
  ORT replica on random features (cost depends on shapes only), which agrees with (a).
- **Last-position logits check:** `replica_check.py` runs greedy decoding in the replica on 100 real clips (25 per
  set, whole clips) with both decoders. It uses a numpy 128-bin centred log-mel, so it reproduces sherpa's exact
  text on 54 of 100. Both decoders get the same features. Result: identical tokens on 100 of 100; the smallest
  first-token margin was 0.32 logits.
- **Memory:** `mem_bench.py`, one process per variant; own = VmHWM − RSS before loading (#47's definition).
- **Files on the benchmark host** (`$WORK/`):
  - `harness-50/`: this branch's `tools/asr-bench`.
  - `scratch-50/`:
    - `run_50.sh` (runs r1–r5 and the stages; `chain.sh` ran them in sequence), `run_mem.sh`
    - `qwen3_bench.py` (interleaved sherpa runs with per-decode tokens), `analyze.py`, `paired.py`, `bound.py`
    - `stage_timing.py` + `stages_table.py` (replica), `mem_bench.py`, `feat_bench.py`, `long_test.py`,
      `replica_check.py`
    - `quantize_q4.py`, `quantize_mixed.py`, `last_logits.py`
    - logs `*.log` / `*.err`; `stages.jsonl`
  - `results-50/`:
    - `r1`: baseline and `max_new_tokens` 1
    - `r2`: threads
    - `r3`: 4-bit
    - `r4`: Hindi, Japanese and English caps
    - `r5`: mixed precision
  - `models-50/`:
    - `qwen3-fp32-src`: the ModelScope fp32 decoder, sha256 `cec03c39…` / `.data` `2d86c064…`
    - `qwen3-q4-b32`, `qwen3-q4-b128`, `qwen3-mix-b32`
    - `qwen3-int8-last`, `qwen3-q4-b128-last`
  - Nothing was uploaded anywhere.
- **Reproduce:**
  ```sh
  cd $WORK/scratch-50 && ./run_50.sh r1   # likewise r2..r5, stages
  ./run_mem.sh
  cd ../harness-50 && ../venv/bin/python ../scratch-50/analyze.py ../results-50/r1 base4 mnt1
  ```
- **Not done:** phone measurements (the host can't give them); a calibrated (GPTQ/AWQ) 4-bit export; a
  fused-attention re-export; a kaldi-native-fbank patch (#2's saving is estimated from the cost of the current
  FFT). The Wasser1462 export script (no licence) was not used.
