# ORT-format (`.ort`) engine files on the host, October 2026 (#78)

Do pre-optimised ONNX Runtime `.ort` files load faster than our `.onnx`
files, and do they give the same transcripts? marmalade-tts-android's Kokoro
went from 2.8 s to 1.5 s on a Pixel by loading `.ort` files. This page has the
host (x86) half: conversion, metadata, load time, peak memory and transcript
parity. The phone half is still to come.

**Answers:**

- **Transcripts: identical.** 2114 of 2200 paired hypotheses are byte-identical
  (text and every Final). The other 86 are Qwen3-ASR's, and they come from the
  x86 host, not from the `.ort` files: on x86, loading the `.onnx` adds ORT's
  amd64-only NCHWc conv layout to Qwen3's fp32 conv frontend (a 1.4e-6
  difference). The arm-targeted `.ort` leaves NCHWc out on purpose. With
  NchwcTransformer disabled, the `.onnx` frontend's output is bit-identical to
  the `.ort`'s. With the frontend left as `.onnx` and the encoder and decoder
  as `.ort`, 200 of 200 Qwen3 hypotheses are identical. ARM has no NCHWc, so on
  the phone `.onnx` and `.ort` should run the same graph.
- **Conversion: clean.** All 18 graphs converted with ORT 1.28.0
  (`--optimization_style Fixed --target_platform arm`), with no warnings, in
  0.2–3.2 s each. The converter is **not byte-deterministic**: a second run
  gave different bytes for 4 of 6 files (once even a different size), so the
  files and sums in `files.tsv` are the frozen set.
- **Metadata: kept.** Every file's custom metadata map (NeMo `vocab_size`,
  `normalize_type`, `feat_dim`, Whisper's 29 keys, Nemotron's cache dims and
  prompt dictionary, Canary's, Parakeet ja's), producer, domain, version, and
  input/output names, types and shapes are equal. Only the graph name is
  dropped (`main_graph`/`torch_jit` → ``). In sherpa-onnx, only the QNN backend
  reads graph names, and the app doesn't use QNN.
- **Host load time: modestly faster for most engines, unchanged for Whisper.**
  Per engine, the files loaded one after another: 0.02–0.36 s faster (2–30 %),
  mostly on the big encoders. Whisper turbo gains nothing (1.19 → 1.17 s).
- **Graph optimisation is not where the load time goes.** `.onnx` with
  optimisation off loads about as fast as with ALL (Ultra encoder: 1.26 vs
  1.21 s). Kokoro's gain came from heavy fp32 graph rewriting. Our
  dynamic-int8 graphs are cheap to optimise, so the host gain comes from
  faster deserialisation and session set-up, not from skipped
  optimisation. Whether the phone gains more is still open.
- **Peak memory during load is much higher.** A ~650 MB `.ort` peaks 0.7–0.95
  GB above its `.onnx` (Ultra encoder 1052 → 1760 MB, turbo encoder 912 →
  1854 MB, Qwen3 decoder 1208 → 1970 MB). ORT reads the whole `.ort` into a
  buffer and copies the initializers out of it. The peak is transient: memory
  held after load is about the same (Ultra encoder 1052 → 1108 MB).
- **Size: the same, except Granite.** Most files are within 0.1 % of their
  `.onnx`. Canary's decoder is +6.7 % (+5 MB). Granite's is **+23 % (+126
  MB)**: its export dequantises some int8 weights with Cast/Mul chains, and
  the Fixed optimiser constant-folds them into fp32 tensors (e.g. one
  1024×16384 fp32 copy, 67 MB). The `.onnx` path builds the same tensors in
  memory at load (its peak is 726 MB at opt off vs 1316 MB at ALL), so only
  the download grows.
- **No embedded paths.** `strings` finds no `/home`, `/tmp`, hostname, user
  name or e-mail in any `.ort`. The only provenance text is Ultra's existing
  metadata comment ("Converted by marmalade-stt-android
  tools/parakeet-ultra/convert.py"), which is in the `.onnx` too.

## Method

- **Host:** a 16-core x86 desktop (32 threads, AVX-512 VNNI), busy with other
  work the whole time, so every job ran at `nice 19` on at most 4 threads. Load timings
  were pinned to cores 4–7. Absolute times are noisy, but each median has a
  tight spread (e.g. Nemotron encoder `.ort`: 0.76–0.81 s over 5 loads).
- **Sources:** each engine's catalog files on the host (`$WORK/models/…`),
  with sha256 checked against `SttEngineCatalog.kt`. All 18 matched, so
  nothing was downloaded.
- **Conversion:** `$WORK/ort-78/convert.sh` runs
  `python -m onnxruntime.tools.convert_onnx_models_to_ort <tmp> --optimization_style Fixed --target_platform arm`
  in a venv with `onnxruntime==1.28.0`, the app's runtime. Each file is
  symlinked into a fixed temp dir first. `--target_platform arm` only
  excludes NchwcTransformer. Everything else is ORT_ENABLE_ALL.
- **Metadata:** `meta_parity.py` compares
  `get_modelmeta()` (custom map, producer, graph name, domain, version,
  description) and the inputs and outputs of `.onnx` vs `.ort` in ORT 1.28.0.
- **Load time:** `load_bench.py` uses sherpa-onnx's session options
  (`GetSessionOptionsImpl`: intra = inter = threads, default ALL
  optimisation) at 4 threads. Each load runs in a fresh process; the file is
  in the page cache ("cold-ish"). Five loads per file and configuration, with
  configurations interleaved. Peak = `ru_maxrss` minus RSS before the load.
  Held = RSS after the load minus RSS before it.
- **Transcript parity:** the asr-bench harness (`$WORK/harness-78`, this
  branch's `tools/asr-bench` plus a 6-line scratch fallback: a model dir with
  `<name>.ort` instead of `<name>.onnx` loads the `.ort`) runs with the
  shared venv's from-source sherpa-onnx 1.13.8, which links ONNX Runtime
  1.28.2 and loads the 1.28.0 `.ort` files fine. The `.ort` model dirs hold
  no `.onnx`, so a run can't silently fall back. App defaults, 2 threads,
  each baseline and `.ort` run back to back. `asrbench.paired` gives the
  table, plus a byte-for-byte check of `hyp` and `finals`.

## Transcript parity

| Engine | Set | n | Identical | Error rate `.onnx` | Error rate `.ort` |
|---|---|---|---|---|---|
| parakeet-ultra | ls-clean | 200 | 200 | 1.43 | 1.43 |
| parakeet-ultra | fleurs-en | 200 | 200 | 6.88 | 6.88 |
| parakeet-ultra | fleurs-de | 50 | 50 | 6.21 | 6.21 |
| parakeet-ultra | fleurs-es | 50 | 50 | 3.60 | 3.60 |
| parakeet-tdt-110m-en | ls-clean | 200 | 200 | 1.82 | 1.82 |
| whisper-turbo | fleurs-hi | 100 | 100 | 31.66 | 31.66 |
| whisper-turbo | fleurs-ar | 100 | 100 | 9.94 | 9.94 |
| whisper-turbo | fleurs-vi | 100 | 100 | 10.53 | 10.53 |
| nemotron-3-5-streaming-1120ms | fleurs-hi | 100 | 100 | 14.32 | 14.32 |
| nemotron-3-5-streaming-1120ms | fleurs-ar | 100 | 100 | 10.55 | 10.55 |
| nemotron-3-5-streaming-1120ms | fleurs-vi | 100 | 100 | 14.90 | 14.90 |
| qwen3-asr-0-6b | fleurs-zh (CER) | 100 | 64 | 9.27 | 9.82 (+0.55, 95 % CI −0.16 … +1.34) |
| qwen3-asr-0-6b | fleurs-ko (CER) | 100 | 50 | 6.32 | 6.50 (+0.18, 95 % CI −0.34 … +0.66) |
| qwen3-asr-0-6b, frontend `.onnx` + encoder/decoder `.ort` | fleurs-zh / fleurs-ko | 200 | 200 | 9.27 / 6.32 | 9.27 / 6.32 |
| parakeet-ja | fleurs-ja (CER) | 200 | 200 | 5.40 | 5.40 |
| canary-180m-flash | fleurs-en | 200 | 200 | 12.49 | 12.49 |
| canary-180m-flash | fleurs-de | 50 | 50 | 8.73 | 8.73 |
| canary-180m-flash | fleurs-es | 50 | 50 | 7.45 | 7.45 |
| granite-speech-5-470m | ls-clean | 200 | 200 | 1.14 | 1.14 |

Qwen3 tensor check (`tensor_parity.py`, the same random 800-frame input):
conv frontend `.onnx` (x86 default) vs `.ort`: max abs diff 1.43e-6 (output
scale 6.5); `.onnx` with NchwcTransformer disabled vs `.ort`: bit-identical.
Encoder: bit-identical in every pairing. Re-runs of the same session are
deterministic (|A−A| = 0). This is the int8 sensitivity #53/#46 found with
front-end changes: a tiny change in the features flips a third to a half of
Qwen3's transcripts, with no significant change in CER.

## Load time and memory (4 threads, median of 5, seconds)

| File | .onnx ALL | .onnx BASIC | .onnx off | .ort | .ort − .onnx ALL | peak .onnx → .ort (MB) | held .onnx → .ort (MB) |
|---|---|---|---|---|---|---|---|
| parakeet-ultra/decoder.int8 | 0.024 | 0.027 | 0.031 | 0.036 | +0.012 (+49 %) | 25 → 42 | 26 → 26 |
| parakeet-ultra/encoder.int8 | 1.213 | 1.316 | 1.262 | 1.063 | -0.150 (-12 %) | 1052 → 1760 | 1052 → 1108 |
| parakeet-ultra/joiner.int8 | 0.031 | 0.028 | 0.025 | 0.034 | +0.003 (+9 %) | 21 → 28 | 16 → 22 |
| parakeet-tdt-110m-en/decoder.int8 | 0.023 | 0.020 | 0.013 | 0.019 | -0.004 (-20 %) | 16 → 19 | 16 → 15 |
| parakeet-tdt-110m-en/encoder.int8 | 0.499 | 0.555 | 0.518 | 0.379 | -0.119 (-24 %) | 216 → 372 | 216 → 241 |
| parakeet-tdt-110m-en/joiner.int8 | 0.016 | 0.009 | 0.011 | 0.016 | -0.000 (-1 %) | 10 → 13 | 11 → 12 |
| whisper-turbo/turbo-decoder.int8 | 0.468 | 0.453 | 0.446 | 0.462 | -0.006 (-1 %) | 642 → 1026 | 440 → 440 |
| whisper-turbo/turbo-encoder.int8 | 0.720 | 0.745 | 0.760 | 0.708 | -0.012 (-2 %) | 912 → 1854 | 912 → 1180 |
| nemotron-3-5-streaming-1120ms/decoder.int8 | 0.029 | 0.037 | 0.036 | 0.040 | +0.011 (+36 %) | 31 → 50 | 29 → 29 |
| nemotron-3-5-streaming-1120ms/encoder.int8 | 1.149 | 1.106 | 0.972 | 0.782 | -0.368 (-32 %) | 1059 → 1756 | 1059 → 1099 |
| nemotron-3-5-streaming-1120ms/joiner.int8 | 0.033 | 0.037 | 0.031 | 0.033 | -0.001 (-2 %) | 27 → 37 | 19 → 28 |
| qwen3-asr-0-6b/conv_frontend | 0.088 | 0.070 | 0.071 | 0.092 | +0.004 (+5 %) | 102 → 131 | 67 → 87 |
| qwen3-asr-0-6b/decoder.int8 | 1.526 | 1.646 | 1.518 | 1.236 | -0.290 (-19 %) | 1208 → 1970 | 1053 → 1059 |
| qwen3-asr-0-6b/encoder.int8 | 0.244 | 0.271 | 0.241 | 0.232 | -0.012 (-5 %) | 242 → 472 | 243 → 290 |
| parakeet-ja/model.int8 | 1.175 | 1.294 | 1.235 | 1.010 | -0.165 (-14 %) | 904 → 1772 | 905 → 1116 |
| canary-180m-flash/decoder.int8 | 0.121 | 0.116 | 0.108 | 0.113 | -0.008 (-7 %) | 144 → 212 | 145 → 133 |
| canary-180m-flash/encoder.int8 | 0.507 | 0.610 | 0.559 | 0.350 | -0.157 (-31 %) | 210 → 373 | 211 → 240 |
| granite-speech-5-470m/model_int8 | 0.830 | 0.895 | 0.738 | 0.755 | -0.075 (-9 %) | 1316 → 1643 | 1233 → 967 |

| Engine (all its files, loaded one after another) | .onnx (s) | .ort (s) | saved |
|---|---|---|---|
| parakeet-ultra | 1.27 | 1.13 | 0.14 s (11 %) |
| parakeet-tdt-110m-en | 0.54 | 0.41 | 0.12 s (23 %) |
| whisper-turbo | 1.19 | 1.17 | 0.02 s (2 %) |
| nemotron-3-5-streaming-1120ms | 1.21 | 0.85 | 0.36 s (30 %) |
| qwen3-asr-0-6b | 1.86 | 1.56 | 0.30 s (16 %) |
| parakeet-ja | 1.17 | 1.01 | 0.17 s (14 %) |
| canary-180m-flash | 0.63 | 0.46 | 0.17 s (26 %) |
| granite-speech-5-470m | 0.83 | 0.76 | 0.07 s (9 %) |

`.ort` with optimisation off loads in the same time as `.ort` with ALL (in
`load-4t.jsonl`, not shown), so ORT doesn't re-optimise a Fixed `.ort`.

**Where the time goes** (`load_breakdown.py`, ORT profiler, median of 3):

| File | .onnx parse (`model_loading_uri`) | .onnx `session_initialization` | .ort total | .ort `session_initialization` | prepacking off: .onnx time / held MB (vs on) |
|---|---|---|---|---|---|
| parakeet-ultra/encoder | 0.24 | 0.91 | 1.04 | 0.66 | 0.92 s / 686 MB (1.15 s / 1071 MB) |
| whisper-turbo/encoder | 0.22 | 0.46 | 0.75 | 0.34 | 0.49 s / 700 MB (0.68 s / 899 MB) |
| whisper-turbo/decoder | 0.22 | 0.21 | 0.47 | 0.17 | 0.40 s / 379 MB (0.44 s / 438 MB) |
| qwen3-asr-0-6b/decoder | 0.36 | 1.08 | 1.29 | 0.77 | 1.22 s / 794 MB (1.44 s / 1056 MB) |

Reading the `.ort` costs about as much as parsing the `.onnx`. The `.ort`
saves time in session set-up instead.

A side lead, not part of #78's question: the session option
`session.disable_prepacking=1` cut load time by 0.04–0.23 s and held memory
by 13–36 % (Ultra encoder 1071 → 686 MB) on both formats. ORT keeps packed
copies of the int8 weights next to the originals. Its decode-speed cost was
not measured.

## What this means for shipping `.ort`

Nothing found blocks correctness. Before shipping, check on the phone:

1. **Phone load time.** The host gain is 0.1–0.4 s per engine and nothing
   for turbo. The phone may gain more, since its slower cores make session
   set-up dearer, but only a device run (Pixel 6a, the app's own load path)
   can say whether it's worth new release assets.
2. **Peak memory at load.** About +0.7–0.95 GB transient per 650 MB file. On
   a low-RAM phone this could push the app over Android's low-memory killer
   while an engine loads. ORT's `session.use_ort_model_bytes_directly` and
   `session.use_ort_model_bytes_for_initializers` avoid the copy, but they
   need sherpa to create the session from a buffer it keeps alive. That
   wasn't tested here: in Python the bytes path segfaulted, because the
   binding doesn't keep the buffer alive.
3. **ORT upgrades.** Fixed-style files carry fused CPU-EP contrib nodes
   (`DynamicQuantizeMatMul`, `MatMulIntegerToFloat`, `QuickGelu`, …).
   Convert again and re-run this parity check whenever the app's ONNX Runtime
   changes. XNNPACK doesn't matter: #31 found it takes no node of these int8
   graphs anyway.
4. **Downloads.** The `.ort` files would be new assets under new tags (a
   published tag's assets never change), with the frozen sums from
   `files.tsv`. Granite's is 126 MB larger. The rest are about the same size.

## Files

- Frozen `.ort` set: `$WORK/ort-78/ort/<engine id>/` on the host
  (4.8 GB). Names, sizes, sha256, source file and sum, conversion time:
  `files.tsv` here. `ort-rerun/` holds the determinism re-run (don't ship it).
- Scripts and raw data in `$WORK/ort-78/`: `convert.sh`,
  `meta_parity.py` → `meta-parity.json`, `load_bench.py` → `load-4t.jsonl`,
  `load_breakdown.py` → `load-breakdown.jsonl`, `tensor_parity.py`,
  `parity.sh` + `parity-jobs.txt`, `tables.py`. Hypotheses:
  `$WORK/results-78/<engine>/<set>{,@ort78,@ort78mixed}.jsonl`.
