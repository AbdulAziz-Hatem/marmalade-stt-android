# A faster Whisper turbo: lower-bit weights, an audio-context fine-tune, runtime knobs, October 2026 (#55)

Whisper Large v3 Turbo stays the app's engine for the languages nothing cheaper covers well, but it costs about
9.8× Parakeet Ultra, shows no live text, and makes the user wait after they stop. #46 showed the cheap knobs don't
work (`2026-10-turbo-speed.md`): stock turbo needs its 10 s of zero padding after the speech, or it loops. This spike
tries what is left: 4-bit and 8-bit weights, published fine-tunes made for short windows or speed, and two runtime
knobs (thread split, memory arena). Host numbers come from the benchmark host's CPU (no GPU, nice 19, at most 8 threads of
ours, Max gaming on the box for part of it); Pixel numbers from one off-charger run on the Pixel 8a over LAN adb (§5).

## Recommendation

| # | Change | Host speed | Accuracy | Memory / download | Ship? |
|---|---|---|---|---|---|
| 1 | **8-bit LM head and embedding** (`int8-h8g8`): the app's int8 decoder with the fp32 logits MatMul and token embedding made 8-bit (§2) | decoder step 0.72×; whole decode 0.88–0.97× | within noise on all 13 sets (one Turkish clip repeats a sentence) | −93 MB own memory; decoder file 361 → 247 MB | **Post-v1, for the download.** Pixel: identical text on all 17 clips, speed and memory within noise (§5). Hosting needs Max's all-clear |
| 2 | **ACFT turbo** (Mahmoud Ashraf's audio-context fine-tune, Apache-2.0) with a 256-frame window, plus #1 (§4) | 0.31–0.33× at 2 s, 0.47–0.48× at 5 s, 0.53–0.58× at 10 s, 0.63–0.65× at 20 s. **Pixel** (one clean turn): end of speech → Final 0.57–0.74× at 2 s, 0.52–0.63× at 5–10 s, 0.59–0.85× at 20 s | within noise on 9 of 13 sets, AMI better (−1.4); **Hindi +1.7, Arabic +1.1, Vietnamese +1.4 WER**, from the fine-tune itself | as #1; Pixel own memory 1409 MB against stock's 1890–1991 (one turn) | **Max's call, post-v1** (below) |
| 3 | 4-bit weights (MatMulNBits RTN) | decoder layers: ≤ 5 % more than #1; encoder: **1.4–2.0× slower** | a new runaway in 1–3 of 6 sets per variant (Turkish +5.6 for the 4-bit decoder, +7.1 for the encoder) | 4-bit encoder file −242 to −302 MB | **No** on host evidence; not run on the Pixel (dropped for v1) |
| 4 | Encoder/decoder thread split | the decoder still gains from more threads (2 → 4: 11–21 %) | – | – | **No** |
| 5 | Memory arena off for turbo | 1.19–1.39× slower | identical | −549 MB own memory | Not by default. As a turbo `LeanMode` for phones it doesn't fit, a recommender follow-up |

**Bottom line.** Two levers work. #1 is small and free: k2-fsa's export leaves the decoder's LM head in fp32, so
every decoded token reads 265 MB of weights; 8-bit makes that 66 MB, saves memory and download, and changes no error
rate. On the Pixel it didn't show the larger gain I expected from bandwidth-bound decoder steps: one off-charger
run puts it within that phone's noise, with identical text (§5). #2 is large: the ACFT model drops the 10 s of zero
padding stock turbo needs (§4), so on the host a short utterance costs a third of the time and a 10 s one about half,
with no loops; on the Pixel the wait after speech up to 10 s is about 0.5–0.75× stock's (one clean turn, §5). It
holds accuracy in turbo's main app languages
(Japanese, Turkish, Norwegian, Korean, Chinese) but costs 1–1.7 WER points in Hindi, Arabic and Vietnamese, because
its fine-tuning data was English-centred. Options for Max:

- **(a) ACFT for everything**: the speed everywhere, a 5–14 % relative WER loss in hi/ar/vi.
- **(b) Both**: ACFT as a "fast" turbo for the languages where it holds, stock turbo kept for hi/ar/vi. Two 0.9 GB
  downloads for a user who needs both, and a recommender change (out of #55's scope).
- **(c) Our own ACFT fine-tune** with Hindi/Arabic/Vietnamese data (FUTO's MIT notebook; Common Voice, CC0): one
  model for all, but a GPU fine-tune of 809M parameters on the benchmark host (its GPU is shared with another long-running job) and an evaluation like §4.
- **(d) Stay** with stock turbo + #1.

My recommendation (updated after the Pixel run; on 2026-10-04 Max decided no further Pixel passes are needed for
v1, so this is for after v1): ACFT is the lever worth pursuing: the Pixel shows the host's gain carries over (§5). Go for (c), with (b) as
the stop-gap only if Max wants the speed before a fine-tune exists. #1 comes along as ACFT's decoder anyway; on its
own it is a download saving (−114 MB), not a phone speed-up. Before either ships, run a proper Pixel pass: on the
charger, the phone idle, at least two interleaved runs (§5). Live partials for turbo stay off until that pass shows
what a 6 s partial costs with ACFT. Two decisions are Max's either way: hosting our converted files on
`marmalade-stt-engines`, and whether the ACFT model is fine under his FUTO rule (it isn't FUTO's, and FUTO's method
repo is MIT; §3).

## 1. Where turbo's time goes

**Host stage split** (`decode_speed.py`, int8 turbo as the app ships it, 4 pieces × 2 rounds, variants in turns;
`mnt:1` stops after one token, so it is the encoder plus one decoder step; ms per decode, median):

| Set | Threads | 2 s | 5 s | 10 s | 20 s |
|---|---|---|---|---|---|
| FLEURS en | 4: full / encoder + 1 step | 531 / 442 | 786 / 600 | 1323 / 889 | 2337 / 1523 |
| FLEURS en | 2 | 847 / 758 | 1231 / 1027 | 2010 / 1462 | 3603 / 2643 |
| FLEURS en | 8 | 373 / 312 | 567 / 406 | 947 / 568 | 1720 / 988 |
| FLEURS ja | 4 | 469 / 450 | 979 / 607 | 1619 / 884 | 2978 / 1524 |
| FLEURS ja | 2 | 774 / 775 | 1493 / 1052 | 2319 / 1494 | 4378 / 2667 |
| FLEURS ja | 8 | 311 / 307 | 716 / 405 | 1191 / 582 | 2169 / 951 |

At 4 threads the decoder is 17–35 % of an English decode and up to half of a Japanese one (more tokens per
second). The encoder runs on the speech plus 1000 zero frames, so even a 2 s utterance costs it 12 s of frames.

**Inside a decoder step** (ORT profile, ORT 1.28.0, 4 threads, cross-attention over 1000 positions, 20 steps): of
20.1 ms of kernel time per step, the LM head is 5.6 ms. k2-fsa's export computes the logits as
`Transpose(MatMul(token_embedding, Transpose(x)))`; `quantize_dynamic` only quantises a MatMul whose second input is a
weight, so this one stays **fp32: 265 MB of weights read per token**. The 32 int8 layer MatMuls take 1.8 ms. The rest
is the export's KV-cache plumbing: 16 `ScatterND` (4.8 ms) copy the 448-position self-attention caches every step,
plus `Transpose` (1.6 ms) and `Split` (1.3 ms) of the cross-attention keys and values. Lower-bit layer weights can
only touch the 1.8 ms; the head is the real target.

## 2. Lower-bit weights

All files are our own conversions of k2-fsa's export (`csukuangfj/sherpa-onnx-whisper-turbo` @ `2ca6ff69`; its
fp32 `turbo-encoder.onnx` + `.weights` and `turbo-decoder.onnx`, sha256 checked against the HF LFS metadata) with
ONNX Runtime 1.28.0's `MatMulNBitsQuantizer` (round-to-nearest, symmetric, `accuracy_level` 4: int8 activations, the
kernels KleidiAI and MLAS have on arm64). Inputs, outputs and the encoder's metadata are unchanged, so sherpa-onnx
runs each as a drop-in. The script is `tools/asr-bench/turbo/quantize.py`; it reproduces the measured files byte for
byte.

| Variant | What changes | Decoder MB | Encoder MB |
|---|---|---|---|
| app (int8) | k2-fsa's `quantize_dynamic`: layer MatMuls int8, LM head and token embedding fp32 | 361 | 675 |
| `int8-h8` | the app's int8 decoder with only the LM head made `MatMulNBits` 8-bit (block 32) | 436 | 675 |
| `int8-h8g8` | as `int8-h8`, and the token-embedding `Gather` 8-bit too (`GatherBlockQuantized`, block 32) | 247 | 675 |
| `int8-h4` | as `int8-h8` with a 4-bit LM head (block 32); speed only | 403 | 675 |
| `q4b32-h8`, `q4b128-h4`, `q4b32-h4` | from the fp32 decoder: layer MatMuls 4-bit (block 32 or 128), LM head 8- or 4-bit | 359–401 | 675 |
| `e4b32`, `e4b64`, `e4b128` | from the fp32 encoder: every MatMul 4-bit, block 32/64/128 | 361 | 373–433 |

(The 4-bit decoders keep the fp32 embedding for the `Gather`; `--gather8` would shrink them the same way.)

**Host speed.** Decoder step (ORT, 4 threads, cross-attention over 1000 positions, 30 steps × 3 rounds in turns):

| Decoder | ms per step | × app |
|---|---|---|
| app | 19.5 | 1.00 |
| `int8-h8` | 14.1 | 0.72 |
| `int8-h4` | 13.8 | 0.71 |
| `q4b32-h8` | 13.8 | 0.71 |
| `q4b32-h4` | 13.4 | 0.69 |
| `q4b128-h4` | 12.8 | 0.66 |

Encoder run (ORT, 2 threads, random log-mel of 1200/2000/3000 frames, 3 rounds in turns): the 4-bit encoders are
**slower** on the host: block 32 2.0× / 1.8× / 1.8× the app's time, block 64 1.9× / 1.7× / 1.6×, block 128 1.5× /
1.4× / 1.4×. The benchmark host's int8 path (`MatMulInteger` on AVX-512 VNNI) is strong; whether ARM's KleidiAI 4-bit kernels
do better was the Pixel's question; not run (§5).

**Accuracy.** App session (`run.py`: VAD, 0.6 s pre-roll, AGC stand-in, 2 threads, the language hint), the first 100
clips of each set (FLEURS es has 50). Δ error rate (CER for Japanese) against the app's files in the same build, with
a 95 % paired bootstrap interval (`paired.py`, 2000 resamples); bold where the interval excludes 0; "runaway" where
the variant loops on a clip the app didn't. Whole-segment silence phrases dropped first (§4), which only matters for Japanese.

| Variant | FLEURS en | FLEURS es (50) | FLEURS nb | FLEURS tr | FLEURS ja (CER) | AMI ihm |
|---|---|---|---|---|---|---|
| app (error rate) | 6.09 | 3.10 | 9.01 | 7.31 | 4.34 | 15.77 |
| `int8-h8` | +0.00 | +0.00 | +0.05 (+0.00 … +0.14) | +0.00 | +0.04 (+0.00 … +0.13) | −0.15 (−0.48 … +0.00) |
| `int8-h8g8` | +0.04 (−0.13 … +0.27) | +0.00 | +0.00 (−0.19 … +0.18) | +0.81 (−0.17 … +2.66), 1 runaway | +0.06 (+0.00 … +0.16) | +0.15 (−0.13 … +0.45) |
| `q4b32-h8` | −0.09 (−0.41 … +0.19) | +0.33 (−0.24 … +1.29) | +0.00 (−0.46 … +0.43) | **+5.63 (+0.17 … +16.31)**, 1 runaway | +0.00 (−0.36 … +0.39) | −0.07 (−0.44 … +0.29) |
| `q4b128-h4` | −0.13 (−0.47 … +0.14) | +0.33 (−0.33 … +1.36) | +0.00 (−0.49 … +0.43) | +0.00 (−0.46 … +0.43) | +1.52 (−0.22 … +4.78), 1 runaway | −0.22 (−0.79 … +0.36) |
| `e4b32` (+ app decoder) | −0.04 (−0.71 … +0.61) | +0.33 (−0.25 … +1.33) | +0.46 (−0.86 … +2.31), 1 runaway | +7.08 (−0.40 … +21.97), 1 runaway | +0.31 (−0.15 … +0.80) | −0.07 (−1.95 … +1.60), 1 runaway |

`int8-h8g8` on the other seven sets, same format:

| | FLEURS hi | FLEURS ar | FLEURS vi | FLEURS ko (CER) | FLEURS zh (CER) | FLEURS de (50) | FLEURS fr (50) |
|---|---|---|---|---|---|---|---|
| app (error rate) | 30.84 | 9.89 | 10.19 | 4.31 | 9.98 | 5.37 | 8.86 |
| `int8-h8g8` | +0.29 (−0.16 … +0.72) | +0.00 (−0.22 … +0.22) | −0.03 (−0.22 … +0.10) | −0.06 (−0.16 … +0.00) | −0.11 (−0.24 … +0.00) | +0.00 | +0.08 (+0.00 … +0.24) |

- **The 8-bit head is as good as lossless.** `int8-h8` changed 0–3 transcripts per set. `int8-h8g8` (the embedding
  8-bit too) changed a few more; its one Turkish runaway repeats the clip's first sentence, the kind of flip int8
  turbo makes on any small numeric change (#53). No set moves outside noise.
- **4-bit costs loops, not a steady loss.** Each 4-bit variant adds a runaway somewhere (Turkish "Müzik Müzik …" for
  `q4b32-h8`, Japanese for `q4b128-h4`, three sets for the 4-bit encoder), which is what moves its error rates. That
  matches #50's 4-bit Qwen3 (+0.5 to +6.7 CER). A calibrated method (GPTQ, which ORT's quantiser has) might reduce it,
  but on the host there is no speed to pay for: not tried.

**Memory** (host, `mem_bench.py`: own = peak RSS − RSS before the load; LibriSpeech clips of 8.5, 10.4, 27.1 and
27.2 s in that order, the arena's worst case; 2 threads):

| Files | Arena | After load MB | Peak at 27 s MB | Decode 8.5 / 10.4 / 27.1 / 27.2 s |
|---|---|---|---|---|
| app | on | 1426 | 2392 | 2.59 / 3.55 / 7.70 / 6.00 s |
| app | off | 1422 | 1843 | 3.39 / 4.72 / 10.73 / 7.16 s |
| `int8-h8g8` | on | 1333 | 2299 | 2.52 / 3.41 / 6.79 / 5.32 s |
| `int8-h8g8` | off | 1347 | 1769 | 3.05 / 4.16 / 9.55 / 6.39 s |

## 3. Published fast variants (research)

Desk research (2026-10-02): cards, licence files, `https://huggingface.co/api/models/<repo>` cardData and the papers.
Ethics grades are sketches in the `model-ethics-review` vocabulary; only the ACFT model got a review
([`../model-reviews/acft-whisper-turbo.md`](../model-reviews/acft-whisper-turbo.md)), because it was converted.

| Candidate | What it is | Weights licence | Ethics sketch | sherpa-onnx export | Published numbers | Verdict |
|---|---|---|---|---|---|---|
| [MahmoudAshraf/acft-whisper-large-v3-turbo](https://huggingface.co/MahmoudAshraf/acft-whisper-large-v3-turbo) | turbo fine-tuned with FUTO's audio-context method ([futo-org/whisper-acft](https://github.com/futo-org/whisper-acft), MIT) to tolerate a short encoder window | Apache-2.0 (+ OpenAI's MIT) | PASS, minor demerit: fine-tuning data undisclosed ([issue #14](https://github.com/futo-org/whisper-acft/issues/14)); self-distillation toward turbo's own outputs | Tensor rename + k2-fsa's exporter unchanged (our `kotoba/convert.py`) | Earnings-22 WER 15.6 at a short window vs turbo's 15.4 at full context (stock turbo at the short window: 62.9); 2.7× turbo's speed on a GPU (TensorRT-LLM). No non-English numbers | **Converted and measured (§4)** |
| Our own ACFT of turbo | the same method with our languages in the data (Common Voice, CC0) | ours | PASS with CC0/CC-BY data | same | [leuconoe/whisper-acft-ko](https://huggingface.co/leuconoe/whisper-acft-ko) (MIT, TFLite only): FLEURS-ko CER 11.1 → 6.5 at a 5 s window, so the method works outside English | Only if the published one falls short on a language; needs a GPU fine-tune |
| [efficient-speech/lite-whisper-large-v3-turbo](https://huggingface.co/efficient-speech/lite-whisper-large-v3-turbo) (LiteASR, [arXiv 2502.20583](https://arxiv.org/abs/2502.20583)) | low-rank encoder (PCA on calibration activations) | Apache-2.0 | GREY: calibrated on ESB clips including TED-LIUM (CC-BY-NC-ND) and SPGISpeech (proprietary) | custom encoder export (`LinearLowRank`); decoder standard | encoder 1.29–1.54× (GPU, M1) at a fixed 30 s window; AMI WER 26.8 → 27.7 / 43.2 / 69.7 for the three sizes; multilingual numbers only for large-v3 | No |
| [rasgaard/whisper-large-v3-turbo-encoder-pruned](https://huggingface.co/rasgaard/whisper-large-v3-turbo-encoder-pruned) ([arXiv 2609.27980](https://arxiv.org/abs/2609.27980)) | 6 of 32 encoder layers removed, distilled back | MIT | PASS (People's Speech, FLEURS) | rename only (26 encoder layers) | encoder 1.22×, end-to-end 1.08× (M4 Pro); FLEURS WER +0.7 (en) to +3.4 (da) | No |
| Token merging ([arXiv 2609.13151](https://arxiv.org/abs/2609.13151)) | training-free merging of adjacent encoder tokens | code Apache-2.0 | N/A (method) | custom encoder export | encoder 1.27× on large-v3, −0.21 points mean over 12 mid/low-resource languages; turbo untested | Maybe later, stacked on ACFT |
| WhisperKit streaming turbo ([arXiv 2507.10860](https://arxiv.org/abs/2507.10860)) | block-diagonal 15 s encoder, mixed-bit palettisation | commercial ("WhisperKit Pro") | licence FAIL | CoreML only | −65 % latency, WER within 1 % (en/ja) | No |
| [aiola/whisper-medusa-multilingual](https://huggingface.co/aiola/whisper-medusa-multilingual) | Medusa heads for multi-token decoding | MIT | PASS (VoxPopuli) | large-v2 base; needs sherpa C++ | decoder-side only, en/es/de/fr | No: turbo's decoder isn't the bottleneck |
| Published int4 turbos (LiteRT, OpenVINO, ExecuTorch, CoreML) | weight quantisation | MIT | as turbo | format-locked; we re-quantise sherpa's graph ourselves (§2) | anecdotal | No as artifacts |
| distil-large-v3/v3.5, CrisperWhisper, Kotoba-Whisper, NB-Whisper distil turbo (beta) | – | – | – | – | – | No: English-only, NC, Japanese-only (#52) or a Norwegian beta |

**FUTO.** FUTO's ACFT method repo is MIT and its own ACFT checkpoints (tiny to small) are Apache-2.0; the "reference
only" rule in this repo is about the FUTO Keyboard and FUTO Voice Input apps (FUTO Source First License).
`2026-10-turbo-speed.md` said "FUTO's are reference-only for us"; that was about whisper.cpp audio-context models in
general and didn't check the licences. The ACFT turbo above is a third party's, Apache-2.0. **Max should confirm**
that it is fine under his rule before it ships.

## 4. The ACFT turbo, measured

Converted with `kotoba/convert.py --model acft-whisper-turbo` (k2-fsa's exporter as `turbo`, its int8
`quantize_dynamic`; `--check`: transformers and openai-whisper agree on 5 of 5 FLEURS-en clips). The tokens file is
the app's. sherpa-onnx runs it unchanged; the window is `--whisper-tail-paddings` (frames of zeros after the speech;
the card's "+128 context" is 128 encoder positions = 256 frames).

**Speed** (host, `decode_speed.py`, 2 threads, 4 pieces × 2 rounds in turns; time × stock turbo's at tail 1000):

| Variant | FLEURS en 2 / 5 / 10 / 20 s | FLEURS ja 2 / 5 / 10 / 20 s |
|---|---|---|
| ACFT, tail 1000 | 1.00 / 1.03 / 0.97 / 0.93 | |
| ACFT, tail 300 | 0.44 / 0.56 / 0.64 / 0.73 | |
| ACFT, tail 128 | 0.33 / 0.46 / 0.58 / 0.66 | |
| ACFT + `h8g8` decoder, tail 256 | 0.33 / 0.47 / 0.53 / 0.63 | 0.31 / 0.48 / 0.58 / 0.65 |
| ACFT + `h8g8` decoder, tail 128 | 0.26 / 0.40 / 0.48 / 0.58 | 0.24 / 0.43 / 0.56 / 0.61 |

Stock turbo took 1.26 / 1.79 / 3.05 / 5.37 s (en) and 1.12 / 2.11 / 3.41 / 6.59 s (ja) at 2 threads. Unlike stock
turbo (`2026-10-turbo-speed.md` §2: 2.2× *slower* at 2 s and tail 300, from loops), ACFT never ran away.

**Accuracy**, same session and clips as §2, Δ against stock turbo (the app's files, tail 1000) with 95 % intervals.
Whole-segment silence phrases are dropped first (`paired.py --drop-phantoms`), see below:

| Set | Stock | ACFT, tail 1000 | ACFT, 300 | ACFT, 128 | **ACFT + `h8g8`, 256** |
|---|---|---|---|---|---|
| FLEURS en | 6.09 | +0.22 | +0.18 | −0.31 | −0.18 (−0.63 … +0.27) |
| FLEURS es (50) | 3.10 | −0.08 | −0.08 | −0.17 | 0.00 |
| FLEURS de (50) | 5.37 | | | | +0.25 (−0.53 … +1.11) |
| FLEURS fr (50) | 8.86 | | | | −0.08 (−1.13 … +0.69) |
| FLEURS nb | 9.01 | +0.41 | +0.18 | +0.23 | +0.37 (−0.27 … +1.00) |
| FLEURS tr | 7.31 | +0.35 | +0.41 | +0.12 | −0.12 (−0.91 … +0.71) |
| FLEURS ja (CER) | 4.34 | +0.21 | +0.10 | +0.41 | +0.29 (−0.04 … +0.68) |
| FLEURS ko (CER) | 4.31 | | | | −0.12 (−0.37 … +0.09) |
| FLEURS zh (CER) | 9.98 | | | | +0.03 (−0.56 … +0.63) |
| AMI ihm | 15.77 | −0.58 | −0.87 | +0.22 | **−1.38 (−3.06 … −0.08)** |
| FLEURS hi | 30.84 | +1.45 (+0.35 … +2.57)¹ | | | **+1.66 (+0.62 … +2.67)** |
| FLEURS ar | 9.89 | +1.55 (+0.45 … +2.77)¹ | | | **+1.05 (+0.11 … +2.07)** |
| FLEURS vi | 10.19 | | | | **+1.41 (+0.75 … +2.09)** |

¹ ACFT + `h8g8` at tail 1000.

- **No runaways** in any ACFT run on these sets (one over-long Hindi output at tail 1000), where stock turbo at tail
  300 looped on 0–12 clips per set (#46).
- **Nine of thirteen sets are within noise, and AMI's short English turns are better.** The fine-tune was made for
  English (Earnings-22).
- **Hindi, Arabic and Vietnamese lose 1.0–1.7 points**, about 5 %, 11 % and 14 % relative. The tail-1000 rows show it
  is the fine-tune, not the short window: a longer window for those languages would not get it back. (`h8g8` alone
  costs nothing there: hi +0.29, ar 0.00, vi +0.06, all within noise.)
- **Silence phrases.** When the harness's 0.5 s lead-in becomes a VAD segment of its own, stock turbo writes "おわり"
  for it (15 of 100 FLEURS-ja clips) and ACFT "ご視聴ありがとうございました" (13–15). Scored raw, that alone puts ACFT at
  +2.4 to +3.4 CER on Japanese. The app's `Phantoms.PHRASES` is English-only, so neither is filtered today (a
  follow-up for stock turbo too; Turkish "Müzik" is another).

## 5. Pixel 8a

**One run, 2026-10-03, off the charger; the rest dropped.** Max decided on 2026-10-04 that the remaining passes (a
second interleaved run, the 4-bit decoder and encoder, ACFT tails, the 2-vs-4-thread split) aren't needed for v1.
So ACFT has one clean turn, stock turbo two turns, and `int8-h8g8` one turn disturbed by the phone being used.

**Setup.** `SpeedBenchmarkService` (`tools/speed-bench/`) with a spike debug APK, never committed: main at
`34f3fc7` plus temporary developer engines with our files (`turbo-x-h8g8`: the app's encoder + `int8-h8g8`;
`turbo-x-acft`: the ACFT encoder + its `h8g8` decoder, 256-frame tail via a debug file) and the app's own
`whisper-turbo`. 4 threads, arena on (the app's default), first decodes (`--ez shuffleClips true --ei repeats 1`, after
a warm-up on silence) and sessions: each `ls*` clip played in real time through a listening session, end of speech →
last Final. Clips: LibriSpeech test-clean 3 × 2 s (new for this run: `ls02-1..3`), 3 × 5, 3 × 10, 3 × 20 and 2 × 27 s,
plus the 3 Kokoro sentences. The phone was off the charger, 55 % falling to 34 %. Max used it between and during turns:
the service pauses while the screen is on and re-runs a disturbed turn once at the end. The h8g8 re-run didn't happen
because the run stopped at the 30 % floor. Results: `2026-10-turbo-faster/s55main-20261003-175207.json`.

**Per turn** (in run order; medians per length; own memory = peak − the RSS before the turn's load, each turn
started from 158–172 MB):

| Turn | Decode s, 2 / 5 / 10 / 20 / 27 s | End of speech → Final s, 2 / 5 / 10 / 20 / 27 s | Own MB | WER, 14 LS clips | Battery temp |
|---|---|---|---|---|---|
| stock turbo (screen was on) | 5.1 / 6.1 / 7.5 / 11.1 / 20.2 | 4.1 / 7.3 / 7.6 / 12.8 / 13.0 | 1890 | 1.9 % | 34 → 36 °C |
| `int8-h8g8` (screen was on) | 5.0 / 6.6 / 10.0 / 14.6 / 19.7 | 5.7 / 6.2 / 9.0 / 13.6 / 13.4 | 1925 | 1.9 % | 36 → 38.5 °C |
| **ACFT + `h8g8`, tail 256** | 3.7 / 3.5 / 5.4 / 9.5 / 21.1 | **2.8 / 3.8 / 4.8 / 10.7 / 12.9** | **1409** | 1.7 % | 38.5 → 38.7 °C |
| stock turbo (re-run, clean) | 5.5 / 7.0 / 11.0 / 16.5 / 22.8 | 5.5 / 7.1 / 9.2 / 16.5 / 19.9 | 1991 | 1.9 % | 38.7 → 39.5 °C |

**Paired by clip** (geometric mean of per-clip time ratios, against each stock turn; `tools/speed-bench/paired.py`
does this for undisturbed turns):

| | Decode ×, 2 / 5 / 10 / 20 / 27 s | End of speech → Final ×, 2 / 5 / 10 / 20 / 27 s |
|---|---|---|
| stock re-run vs stock first turn | 1.06 / 1.09 / 1.37 / 1.29 / 1.14 | 1.28 / 1.22 / 1.22 / 1.45 / 1.53 |
| `int8-h8g8` vs stock first turn | 0.95 / 1.01 / 1.22 / 1.03 / 0.98 | 1.38 / 0.99 / 1.12 / 1.05 / 1.03 |
| `int8-h8g8` vs stock re-run | 0.90 / 0.93 / 0.89 / 0.80 / 0.86 | 1.07 / 0.81 / 0.92 / 0.72 / 0.68 |
| ACFT vs stock first turn | 0.72 / 0.51 / 0.68 / 0.71 / 1.05 | 0.74 / 0.63 / 0.62 / 0.85 / 0.99 |
| ACFT vs stock re-run | 0.68 / 0.47 / 0.49 / 0.55 / 0.92 | 0.57 / 0.52 / 0.51 / 0.59 / 0.65 |

- **Noise first.** The two stock turns gave identical text, but the second, on a warmer phone, was 1.18× slower to
  decode and 1.32× slower to Final over all clips. Anything inside about ±20 % from one turn is noise on this phone
  off the charger.
- **ACFT holds up on the phone.** The wait after speech is 0.5–0.75× stock's up to 10 s, 0.6–0.85× at 20 s, and
  about the same at 27 s, where the speech nearly fills the 30 s window either way. That's the host's picture (§4).
  Up to 10 s it beats even the cooler stock turn by more than the noise. Own memory was 1409 MB against stock's
  1890–1991 (one turn each; the window alone doesn't explain it, since at 27 s both encoders see nearly 30 s). WER
  on the 14 LibriSpeech clips is 1.7 % against 1.9 %. ACFT also cased and punctuated every clip where stock turbo
  wrote some in lower case without punctuation, and of its three word differences two are right ("law", "a moment") and
  one wrong ("in oak").
- **`int8-h8g8` is unsettled.** It gave text identical to stock turbo on all 17 clips. Its speed (0.88–1.04× decode,
  0.84–1.11× Final over all clips, against the two stock turns) and memory (1925 MB) sit inside the noise. The
  bandwidth-bound gain I expected on the phone didn't show beyond that. Its sure win is the download (−114 MB) and
  the host's −93 MB.
- **What a Pixel user waits for with stock turbo today:** 4–5.5 s after a 2 s utterance, about 7 s after 5 s, 7.5–9 s
  after 10 s, 13–16.5 s after 20 s (in the benchmark service, which runs on cores 0–7 without the X3; the app in
  front is somewhat faster).
- **Clip order matters within a turn.** Every turn decodes the clips in the same shuffled order. RTF grows along it,
  up to about 2× from the first clips to the last: stock's `ls05-1` at position 1 is 0.73, `ls05-3` at position 17 is
  1.35. So a length's median mixes positions, but pairs at the same position stay fair. This is why some 2 s medians
  look odd against 5 s ones.
- **The 2 s clips are slow beyond that.** The `ls02` clips (positions 6, 11, 13) decode at RTF 1.7–2.5 for stock and
  1.3–1.7 for ACFT. The 2.0 s Kokoro sentence at position 14 decodes in 1.9 s with ACFT, against 3.0–4.2 s for the
  2.2–2.5 s `ls02` clips. On the host, stock turbo decodes them in 0.76–0.89 s, faster than `ls05-1` (1.16 s), with
  the same text. Unexplained; the first thing to check in a future pass is a stage split (`max_new_tokens` 1) on them.
- **Not run:** the 4-bit decoder and encoder (staged, never timed), ACFT at tails 128 and 1000, the 2-vs-4-thread
  split, arena on/off.

**For the post-v1 pass** (needed before ACFT or `int8-h8g8` ships): the phone on a wall charger and idle, at least
two runs with different seeds, and a longer cool-down (`--ei cooldown 120`). Then compare with `paired.py`. The spike
needs the two developer engines plus the debug-file hook for Whisper's tail padding back (catalog entries for the files
under `$WORK/models-55/pixel/` on the benchmark host). Push only the decoders and copy the stock encoder and tokens
on the phone: wireless ADB moved about 2.4 MB/s.

## 6. Runtime knobs

**Encoder vs decoder threads.** sherpa-onnx gives the encoder and decoder sessions the same `num_threads`; a split
would need a sherpa patch. From the host stage split (§1), the decoder's share (full decode − encoder and one step)
at 10 s:

| Set | Decoder ms at 2 / 4 / 8 threads | Encoder + 1 step ms at 2 / 4 / 8 |
|---|---|---|
| FLEURS en | 548 / 434 / 379 | 1462 / 889 / 568 |
| FLEURS ja | 825 / 735 / 609 | 1494 / 884 / 582 |

The decoder still gains from more threads (2 → 4: 11–21 % faster), just less than the encoder (1.65–1.7×). So on
the host, giving the decoder fewer threads than the encoder only slows it down, and there is nothing a split could
hand back. On a phone the 4 recognizer threads are the 4 big cores either way (#34); the Pixel's 2-vs-4 split was
not run (§5).

**The memory arena** (#47 left it on for turbo, with no lean mode). Host, §2's memory table: without it turbo's own
peak at 27 s drops from 2392 to 1843 MB (−549 MB), and every decode is 1.19–1.39× slower (each of those four decodes
was a new length). The Pixel's first-decode cost for other engines was 1.3–2.1× (#47).

## Method

- **Host:** the benchmark host (Ryzen AI Max+ 395), 2026-10-02/03, CPU only, `nice -n 19`, at most 8 ORT threads of ours
  (three 2-thread WER jobs plus one 2-thread timing job). Max played a Steam game on the box for part of the session;
  timings are therefore only compared within an interleaved run.
- **Build:** the harness's `venv-54` (sherpa-onnx v1.13.8 with patches 0001–0005 and the kissfft kaldi-native-fbank
  patch, as the app; libonnxruntime 1.28.2). Conversions and the ORT replicas: `scratch-50/venv` (onnxruntime 1.28.0,
  the app's version, onnx 1.23.1). The ACFT conversion: `venv-kotoba-55` from `kotoba/requirements.txt`.
- **WER/CER:** `run.py` (the app's offline session: Silero, 0.6 s pre-roll, AGC stand-in, 2 threads, the set's
  language hint, the #54 token budget), `--limit 100` per set (FLEURS es/de/fr have 50), variants via
  `--model-dir/--variant`, ACFT windows via `--whisper-tail-paddings`; `paired.py` (with and without
  `--drop-phantoms`) against the plain run in the same results directory. Greedy int8 decoding flips transcripts on
  tiny numeric changes (#53), so differences are judged by their paired intervals, not by identical text.
- **Timing:** `decode_speed.py --variant …` (real speech cut to 2/5/10/20 s, RMS −25 dBFS, variants in turns);
  `step_bench.py` (decoder steps and encoder runs directly in ORT, random inputs of the shapes sherpa feeds); ORT
  profiles aggregated by `prof_agg.py`. Memory: `mem_bench.py`, one process per variant.
- **Files on the benchmark host** (`$WORK/`), nothing uploaded anywhere:
  - `models-55/turbo-fp32/` held k2-fsa's fp32 turbo export (sha256 `1b960f27…` encoder graph, `746f879e…` weights,
    `b24db5d9…` decoder, as the HF LFS metadata at `2ca6ff69`); deleted after the 4-bit runs (3.2 GB, re-downloadable
    at that revision).
  - `models-55/`: the files staged for the Pixel spike are kept (`dec-int8-h8g8/`, `dec-q4b32-h8/`, `enc-q4b32/`,
    `dec-acft-h8g8/`, `acft-whisper-turbo/` with `SHA256SUMS`), with their `v-*/` symlink directories and
    `pixel/<engine id>/` (the same files under catalog names). The other variants were deleted after measuring; the
    script rebuilds them. `venv-kotoba-55` was deleted too (`kotoba/requirements.txt` recreates it).
  - `scratch-55/`: `quantize_turbo.py` (the first version of `turbo/quantize.py`), `step_bench.py`, `prof_agg.py`,
    `speed_table.py`, `acc_table.py`, `mem_bench.py`, the WER
    queue (`sched.sh`, `wer_job.sh`) and logs. The ACFT `.pt`, its fp32 export, the HF download cache and the ORT
    profiles were deleted after the runs.
  - `results-55/`: hypotheses (`whisper-turbo/<set>[@wtailN]@<variant>.jsonl`), `split.jsonl`, `acft-speed.jsonl`,
    `cand-speed.jsonl`.
- **Reproduce** (from `$WORK`):
  ```sh
  scratch-50/venv/bin/python harness-55/turbo/quantize.py decoder models-55/dec-int8-h8g8/turbo-decoder.int8.onnx \
      --src models/whisper-turbo/turbo-decoder.int8.onnx --head 8 --gather8
  scratch-50/venv/bin/python harness-55/turbo/quantize.py encoder models-55/enc-q4b32/turbo-encoder.int8.onnx \
      --src models-55/turbo-fp32/turbo-encoder.onnx --bits 4 --block 32
  cd harness-55 && ../venv-kotoba-55/bin/python kotoba/convert.py --model acft-whisper-turbo --work ../scratch-55/acft \
      --out ../models-55/acft-whisper-turbo --exporter ../sherpa-onnx/scripts/whisper/export-onnx.py --keep
  ../venv-54/bin/python -m asrbench.run --work .. --results results-55 --engine whisper-turbo --limit 100 \
      --model-dir ../models-55/v-acft-h8g8 --variant acft-h8g8 --whisper-tail-paddings 256 fleurs-hi fleurs-ja
  ../venv-54/bin/python -m asrbench.paired --drop-phantoms --work .. --results results-55 --engine whisper-turbo \
      --variants @wtail256@acft-h8g8 fleurs-hi fleurs-ja
  ```
