# Granite Speech 5.0 470M TurboCTC spike, September 2026 (#26)

IBM's `granite-speech-5.0-470m-turboctc` (Apache-2.0, English, CTC) measured
on the host harness against the app's engines. sherpa-onnx can't run it, so
the harness gained its own ONNX Runtime pipeline (`tools/asr-bench/asrbench/granite.py`)
and an export script (`tools/asr-bench/granite/export_onnx.py`). Nothing in the
app changed. Per-set statistics are in `2026-09-granite-spike.json` next to
this file. The other engines' rows are the #15 run (`2026-09-host-wer.md`).

## Summary

- **Accuracy: the best engine measured, on every English set.** LibriSpeech-other
  2.6 vs Parakeet v3's 5.0, FLEURS-en 6.7 vs 8.3, AMI 9.5 vs 12.4 (flattered:
  Granite trained on AMI's train split), dictation 10.8 vs 16.9 (normalised).
- **Speed: about twice as fast as Parakeet v3** at int8 on the host CPU, and
  about five times as fast as distil-small.en (2 threads: RTF 0.023 vs 0.047
  vs 0.117). Smaller in memory too.
- **Robust where Parakeet isn't:** no empty results on any set, and none on
  the 16 AMI clips where Parakeet returns nothing after silence (#15).
- **But its raw text is lowercase, unpunctuated and normalised.** There are no
  capitals, no apostrophes, and almost no punctuation in 1080 utterances.
  Possessives come out as "is" ("the tiger is roar", "barcelona is official
  languages": 29 of 36 cases). Contractions are expanded ("i will"). Email
  addresses lose "@" and the dots ("support at example org"). The normalised
  WER hides all of it: Whisper's normaliser also turns `'s` into "is". Raw
  dictation WER is 58 vs Parakeet's 36.

## English WER

WER in %, normalised with Whisper's English normaliser; ± is the 95 %
bootstrap half-width. The app's pipeline (VAD, 0.6 s pre-roll) and the harness
defaults, as in `2026-09-host-wer.md`. No `:textproc` on any row.

| Engine | LS clean | LS other | FLEURS en | AMI | Dictation (norm) | Dictation (raw) |
|---|---|---|---|---|---|---|
| **granite-speech-5-470m-ctc** (int8, our export) | **1.1** ±0.4 | **2.6** ±0.7 | **6.7** ±1.3 | 9.5 ±1.7 | **10.8** ±4.2 | 58.0 |
| granite-speech-5-470m-ctc-fp32 | 1.1 ±0.4 | 2.7 ±0.7 | 6.7 ±1.3 | **9.0** ±1.6 | 11.0 ±4.1 | 58.3 |
| granite-speech-5-470m-ctc-qwertz92 (community int8) | 1.2 ±0.4 | 2.8 ±0.7 | 6.7 ±1.3 | 9.3 ±1.6 | 11.6 ±4.5 | 58.0 |
| parakeet-tdt-0-6b-v3 | 1.6 ±0.4 | 5.0 ±1.0 | 8.3 ±1.6 | 12.4 ±2.4 | 16.9 ±4.8 | 36.2 |
| nemotron-en-streaming-560ms | 2.0 ±0.6 | 6.1 ±1.2 | 8.4 ±1.5 | 10.4 ±1.7 | 14.1 ±5.3 | 62.9 |
| qwen3-asr-0-6b | 2.0 ±0.5 | 6.5 ±1.2 | 7.6 ±1.5 | 10.6 ±1.8 | 14.6 ±5.5 | 42.0 |
| distil-small-en (default) | 2.6 ±0.6 | 7.4 ±1.4 | 9.3 ±1.4 | 14.3 ±1.9 | 24.6 ±7.7 | 35.6 |

- int8 costs nothing measurable: every int8 vs fp32 difference is inside the
  interval. On 40 real clips the int8 and fp32 texts differ on 4, by one word each.
- **AMI is flattered.** Granite's training data includes AMI's train split
  (150 h, plus AMI in 2000 h of synthetic multi-speaker audio). Our test clips
  come from AMI's test split, but the recording setup and meeting style are the ones it
  trained on. FLEURS and LibriSpeech-other are the fair comparison.
- Failure counters: no empty results or runaways on any set. The first word
  was missing on 14 AMI clips vs Parakeet's 18, and the last word on 8 vs 19.
  - With speech from the mic's first sample (`--lead-silence 0`), AMI was
    9.4 and dictation 12.4.
  - The `asrbench.regress` clips (an abrupt, mid-word start after 0.5 s or
    1 s of silence) gave 0 of 16 empty results at both lead-ins. Parakeet's
    budget there is 2 and 3.
- **Noise.** Through the VAD, no text on any of the six noise clips. Fed
  straight to the model, all six gave one word ("i", "thank", "you", "so").
  That's milder than Qwen3's sentences, but it shows the VAD is load-bearing,
  as it is for Whisper and Qwen3.

## What the text looks like

Dictation clips, raw output (reference / Parakeet v3 / Granite):

| | |
|---|---|
| ref | Remind me to pick up Dr. Oyelaran at 4:15 PM. |
| Parakeet | Remind me to pick up Dr. Oilarin at 415 p.m. |
| Granite | remind me to pick up doctor oulerin at 415 p m |
| ref | Björn's birthday party starts at 7 PM sharp. |
| Parakeet | Bjorn's birthday party starts at 7 p.m. sharp. |
| Granite | bjorn is birthday party starts at 7 pm sharp |
| ref | Thanks, Chidinma! I'll send the slides tonight. |
| Parakeet | Thanks, Chittenma. I'll send the slides tonight. |
| Granite | thanks chididenma i will send the slides tonight |
| ref | Pay Aoife €35 for the concert tickets. |
| Parakeet | Pay Eva thirty five euros for the concert tickets. |
| Granite | pay ifa €35 for the concert tickets |

Granite writes numbers, ordinals and currency as digits more often than
Parakeet ("5th avenue", "€35", "90"), and it gets rare names right more often
("nakamura", "priyanka"). Everything else about its text is what an ASR
scorer's normaliser produces: the model was evidently trained on normalised
transcripts.

**What it would take to fix.** Casing and punctuation need a second model.
IBM's own demo (the `ibm-granite/granite-speech-streaming-webgpu` Space) runs
`1-800-BAD-CODE/punct_cap_seg_en` after the CTC model. That punctuator is
Apache-2.0, about 209 MB in fp32, uses a SentencePiece tokenizer, and was
trained on WMT News Crawl. A punctuator can't restore possessives or
contractions, though: "the tiger is roar" stays wrong. `:textproc` rules can't
tell a possessive "is" from a real one either.

## Speed

Decode time ÷ audio time for 60 clips (30 LibriSpeech-other, 30 AMI), whole
clips without the VAD. The engines take turns clip by clip, so machine load
lands on all of them alike. #25 was running on the benchmark host at the same time
(load about 13 on 32 threads), so trust the ratios, not the absolute values.
For Granite, the time includes the numpy front-end, 5–10 % of it.

| Engine | Benchmark host, 2 threads | Benchmark host, 4 threads | Dev machine (i7-8750H), 2 / 4 threads |
|---|---|---|---|
| granite int8 (our export) | **0.023** | **0.015** | 0.072 / 0.044 |
| granite int8 (qwertz92) | 0.023 | 0.015 | – |
| granite fp32 | 0.055 | 0.032 | – |
| parakeet-tdt-0-6b-v3 (sherpa, int8) | 0.047 | 0.033 | – |
| distil-small-en (sherpa, int8) | 0.117 | 0.082 | – |

- **Memory.** The int8 file is 550 MB (Parakeet's encoder is 652 MB). Peak RSS
  of a host process decoding a 28 s clip was 1.4 GB, including Python and
  numpy. The harness runs' peak RSS was 1.4 GB for Granite int8 vs 2.25 GB for
  Parakeet.
- The first export was slower (RTF 0.028 / 0.023 at 2 / 4 threads). Its
  relative-position bias was an ONNX `Einsum`, and ORT's CPU Einsum is slow. The export now uses a
  batched MatMul, as transformers does.
- **Not measured on a phone.** x86 int8 (AVX2/VNNI) and ARM int8 (dotprod)
  kernels differ, so the Pixel ratio may move. Both engines run on the same
  ORT, though, and the gap is 2×.

## The export

- **No official IBM ONNX.** IBM's WebGPU demo Space ships a 4-bit/fp16 graph
  (`onnx-ctc/ctc_conformer_q4f16.onnx`). Its export scripts aren't published
  and the Space has no licence tag. That graph is for WebGPU, not CPU int8.
- **Ours:** `tools/asr-bench/granite/export_onnx.py` exports and verifies the model.
  - Source: `ibm-granite/granite-speech-5.0-470m-turboctc` at
    `286456107c8ba1161f5c22dfe85466402c88333b`, `model.safetensors` sha256
    `8b98a8c3…1aad4` (checked by the script).
  - Model code: transformers 5.17.0's own `GraniteSpeech5ForCTC`, the code
    path the model card uses, loaded in fp32.
  - Export: torch 2.11.0, opset 17, traced. Three rewrites are needed for
    export only. Attention always pads to whole 128-frame blocks, where
    transformers pads only when needed, a branch a trace would freeze. The
    pairwise mean pool is a reshape, because ONNX has no `Unfold`. The
    relative-position bias is a MatMul, not an Einsum.
  - int8: onnxruntime 1.28.0 `quantize_dynamic`, MatMul weights QInt8, per tensor.
  - Graph: input `features` [1, T, 320], output `logits` [1, T', 16384],
    12.5 frames per second.
  - Reproducible: a fresh venv built from the pinned requirements re-exported
    both files byte for byte. int8 `model.int8.onnx` is 549,571,632 B,
    sha256 `36ffe077…c6b8f1`. fp32 `model.onnx` is 1,892,287,031 B, sha256
    `de16df9b…58c382`. The full hashes are in the JSON.
- **Parity.** The check ran on 46 clips: 20 LibriSpeech-other, 20 AMI, and
  6 synthetic lengths from 0.08 s to 29 s around the block and subsampling
  edges.
  - fp32 ONNX vs PyTorch: 100 % frame-level argmax agreement, identical text
    on all 46 clips, largest logit difference 0.0085.
  - The numpy front-end vs transformers' feature extractor: largest
    difference 7e-5.
- **The community export** `qwertz92/granite-speech-5.0-470m-turboctc-onnx`
  (`onnx/model_int8.onnx` at `e6e3b4d6…`, sha256 `8173a50e…60e9`) matches its
  README's hash.
  - It is Apache-2.0, per-channel int8, and publishes its export, quantize and
    validation scripts and its hashes.
  - On our harness its WER is inside the interval of ours and its speed is the
    same. It is a usable download source if we don't host our own.
- Export tooling licences (build time only): torch BSD-3, torchaudio BSD-2,
  transformers Apache-2.0, onnx Apache-2.0, onnxruntime MIT. No GPL or LGPL.
  Pins: `tools/asr-bench/granite/requirements.txt`.

## Server voice loop (dev machine, GTX 1060 Max-Q 6 GB)

- **The CPU is enough.** int8 on the i7-8750H decodes at RTF 0.044 with 4
  threads: 1.2 s for a 28 s utterance, a fraction of that for a spoken turn.
  It is far more accurate than today's faster-whisper base.en (Open ASR
  average 5.04 vs 11.10). The lowercase text matters less when an LLM reads it.
- **GTX 1060, fp32 (estimate, not measured).** The weights take 1.9 GB. A
  30 s utterance's activations are tens of MB: at most 1500 frames × 4096
  wide in the first blocks, 128-frame attention blocks, 24 MB of logits. The
  CUDA context and allocator add about 0.5 GB. That totals about 2.5–3 GB,
  which fits in 6 GB. Pascal's fp16
  is crippled (1/64 rate), so it has to run fp32 there. Whether current
  PyTorch or onnxruntime-gpu wheels still support compute capability 6.1 is
  UNVERIFIED.
- **transcribe.cpp** (MIT; it vendors only ggml and miniz, both MIT) runs the
  `handy-computer/granite-speech-5.0-470m-turboctc-gguf` quantizations
  (Apache-2.0, Q4_K_M up to BF16). ggml's CUDA and Vulkan backends are the
  route on Pascal. Not measured here.

## A Kotlin port, if it happens

**It happened in #32** (a developer engine from the qwertz92 download; see
ARCHITECTURE.md, "Granite (#32)"). The plan as written in #26:

The app's `OfflineTranscriber` interface (`SttFacades.kt`) is the seam.
`StreamingSession`, the VAD, live partials and Finals work unchanged, and at
twice Parakeet's speed live partials are affordable. The pieces:

1. **`GraniteFrontend`:** a 512-point real FFT, the HTK mel filterbank,
   log10 with the per-utterance floor, deltas and stacking. About 150 lines,
   with JVM tests against golden vectors dumped from `asrbench.granite.features`.
2. **CTC greedy and byte-level BPE decoding.** About 70 lines. The vocabulary
   can come straight from IBM's `tokenizer.json` at the pinned revision.
3. **`GraniteOfflineTranscriber`:** the `ai.onnxruntime` Java API. It is
   already a dependency, but the build excludes `libonnxruntime4j_jni.so`
   (83 KB arm64); that exclude would go.
4. **Wiring:** an `EngineFamily.GRANITE_CTC` catalog entry and the `SttEngines`
   wiring, plus NOTICE and in-app licence entries. IBM's repo has no LICENSE
   file, so the Apache-2.0 text is bundled.
5. **A download source (a decision for Max).** Either the community int8
   (pinned, verified above), or our own export hosted somewhere public.
   Hosting is publishing.

That is roughly 400–500 lines of Kotlin with tests. It's mechanical except
for front-end parity, which the golden tests settle. It is smaller than
#13's engine-family work. **Casing and punctuation is a separate, bigger
piece.** It needs a second ONNX model and a SentencePiece tokenizer in
Kotlin, and its own ethics review. It still wouldn't fix possessives.

## Recommendation

- **Server voice loop: go.** It is more accurate and faster than every CPU
  option we have. The LLM downstream doesn't need casing. That is the
  Marmalade server project's call.
- **Phone app: no-go as the dictation default for now, go as a hidden
  developer engine if Max wants to hear it.** The accuracy and speed are the
  best measured. But users see "bjorn is birthday party starts at 7 pm
  sharp", and punctuation and casing can't be bolted on cheaply. IBM's own
  demo needed a 209 MB second model, which still leaves possessives broken.
  If IBM ships a cased, punctuated variant, this becomes the obvious default
  candidate. The harness family and export script are ready to re-measure it.

## Reproduce

```sh
# on the benchmark host; $WORK = the asr-bench scratch dir, harness copied to $WORK/harness-26
hf download ibm-granite/granite-speech-5.0-470m-turboctc --revision 286456107c8ba1161f5c22dfe85466402c88333b \
  --local-dir $WORK/models/granite-speech-5-470m-src
uv venv -p 3.12 $WORK/venv-granite-export && VIRTUAL_ENV=$WORK/venv-granite-export uv pip install \
  --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu \
  -r tools/asr-bench/granite/requirements.txt
cd $WORK/harness-26
../venv-granite-export/bin/python granite/export_onnx.py --src ../models/granite-speech-5-470m-src --work ..
export OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1
../venv/bin/python -m asrbench.run --work .. --results results-26 --engine granite-speech-5-470m-ctc \
  ls-clean ls-other fleurs-en ami-ihm dictation noise
../venv/bin/python granite/rtf_ab.py --work .. --engines parakeet-tdt-0-6b-v3 granite-speech-5-470m-ctc --threads 2 4
```
