# Whisper turbo live speed: the log-mel FFT, a shorter encoder input, draft partials, October 2026 (#53, #46)

Max found Whisper Large v3 Turbo usable but slow to show anything live, and it shows no partials (#46). #50 had
found that the log-mel front-end shared by Whisper and Qwen3-ASR ran a naive DFT (#53). This host spike fixes
the FFT and measures the cheap ways to make turbo faster live. Everything ran on the benchmark host's CPU (Ryzen AI Max+
395, no GPU) with the app's int8 files and sherpa-onnx configuration (`tools/asr-bench`, nice 10, at most 8
threads, another benchmark's jobs sharing the machine). The phone was only reachable through a slow network relay, so
**nothing here was measured on the Pixel 8a**; Pixel figures are estimates.

## Recommendation

| # | Change | Host effect | Accuracy | Ship? |
|---|---|---|---|---|
| 1 | **#53: kissfft for Whisper's log-mel** (`third_party/patches/kaldi-native-fbank/0001`) | Features 14.5 → 0.16 ms per audio second (one core). Decodes at 4 threads: turbo −9 to −12 %, Qwen3 −14 to −16 % | Features 10× closer to an exact DFT than before. Transcripts change on 14–39 % of turbo's clips and 37–85 % of Qwen3's (int8 + greedy decoding amplify float rounding); error rates unchanged: every per-set difference inside its 95 % interval | **Yes** (on this branch). Pixel effect not measured; probably smaller, about −3 to −7 % (§1) |
| 2 | Shorter encoder input (whisper.cpp's `audio_ctx`): fewer zero frames after the speech | Tail 600 frames: 0.73–0.87× the time; 300: 0.56–0.78× on 5–20 s, **2.2× slower** on 2 s | Tail 600: AMI 14.8 → 22.6 WER, ja 7.5 → 10.1 CER. 300: AMI 80.5, ja 29.3 (decoder loops) | **No.** Keep `tailPaddings = 1000` |
| 3 | Draft partials from multilingual Whisper base (hidden), turbo for the Final | Base costs about 1/3 of turbo | Base's text is 10–56 % (hi 130 %) away from turbo's Final on turbo's languages; tiny 22–243 % | **No**: the live text would jump at every Final. Not added to the app |
| 4 | Lower-bit (4-bit) weights | not measured | – | Not tried: the last resort, needs our own conversion and hosting; #50's 4-bit Qwen3 decoder was 0.79–0.92× the time at +0.5 to +6.7 CER |

**Bottom line.** The FFT patch is a small free win. The premise of #46 doesn't hold: sherpa-onnx does
not pad every decode to 30 s. Its Whisper export already takes any length, and sherpa feeds the encoder the
speech plus `tailPaddings` zero frames (the app's 1000 = 10 s). Turbo needs those 10 s of padding to end its
transcript: with less, it repeats sentences until its 224-token limit, and even with the repeats cut off it is
less accurate (§2). So a 5 s utterance costs the encoder 15 s of frames, and nothing cheap changes that without
fine-tuning the model. Live partials for turbo stay off: on the Pixel 8a a partial of the app's 6 s window would
take about as long as a 5 s Final (≈ 4 s, from #31's RTF 0.77), and the only cheap drafts are too inaccurate.
Turbo's live speed would need a fine-tuned variable-window model (whisper.cpp's users rely on fine-tuned
"audio_ctx" models) or a different engine for those languages. (Correction, #55: FUTO's audio-context method is MIT
and its checkpoints Apache-2.0; only FUTO's apps are reference-only. A third party's audio-context turbo was measured
in `2026-10-turbo-faster.md`.)

## 1. The log-mel FFT (#53)

`WhisperFeatureComputer` in kaldi-native-fbank v1.22.3 (sherpa-onnx's feature library) used whisper.cpp's
recursive radix-2 FFT. The 25 ms frame has 400 samples, so the recursion halves 400 → 200 → 100 → 50 → 25 and
then runs a naive DFT with a `sin` and a `cos` per term. The patch uses knf's own `Rfft` (kissfft, mixed radix)
and also builds `Rfft`'s kissfft plan once instead of on every frame (that part also speeds up the Kaldi fbank
of Parakeet, Canary and Nemotron, bit-identically). Both builds apply it: the app's CMake and the harness's
`setup.sh` (`third_party/patches/kaldi-native-fbank/README.md`).

**Features**, 392 s of speech (8 clips each of FLEURS en/zh/ja/de and AMI), one core, `-O3`:

| Front-end | Before, ms per audio s | After | Output |
|---|---|---|---|
| Whisper log-mel, 80 bins | 14.3 | 0.13 | max \|diff\| 2.8e-4 after Whisper's normalisation (range 2.0) |
| Whisper log-mel, 128 bins (turbo, Qwen3) | 14.5 | 0.16 | max \|diff\| 5.2e-4 |
| Kaldi fbank, 80 / 128 bins | 0.73 | 0.26 / 0.28 | bit-identical |

Against an exact double-precision DFT (same frames), the old features were off by up to 5.3e-4 (mean 6.8e-7),
the new ones by up to 3.0e-5 (mean 1.1e-7): the patch makes them more accurate, not just faster.

**Decode time**, `decode_speed.py`, 4 threads, real speech cut to length, 3 pieces, two alternating rounds per
build (ms per decode, features + model, median):

| Engine | Build | 2 s | 5 s | 10 s | 20 s |
|---|---|---|---|---|---|
| whisper-turbo | before | 30 + 504–514 | 74 + 757–768 | 146–147 + 1293–1337 | 292 + 2207–2234 |
| whisper-turbo | after | 1 + 517–519 | 2 + 769–775 | 4–5 + 1344–1353 | 8–10 + 2248–2260 |
| qwen3-asr-0-6b | before | 30 + 179–184 | 74 + 383–386 | 147 + 826–840 | 293–296 + 1755–1826 |
| qwen3-asr-0-6b | after | 1 + 175 | 2 + 373–374 | 4 + 825–832 | 8 + 1674–1692 |

The model time is unchanged; the features were 9–12 % of turbo's decode and 14–16 % of Qwen3's (#50 estimated
8–15 % for Qwen3). On the Pixel the model is about 5× slower than here (turbo 5 s: RTF 0.77 there, 0.16
here) while a scalar loop like the old DFT is perhaps 1.5–2× slower, so the saving there is probably about
3–4 % for turbo and 5–7 % for Qwen3. Not measured; `SpeedBudget.REFERENCE` is unchanged.

**Transcripts.** The same clips decoded by the build without and with the patch (both turbo and Qwen3 are
deterministic: a second run of the old build changed 0 of 200 transcripts). Ultra's 200 FLEURS clips
(en/de/es/fr, 50 each) are identical. Turbo changes on 14–39 % of clips per set and Qwen3 on 37–85 %, mostly a
word or a punctuation mark: int8 dynamic quantisation and greedy decoding amplify a 1e-4 feature change. Error rates,
paired bootstrap over the same utterances (difference after − before, 95 % interval):

| Engine | Set | n | Before | After | Difference | Utterances worse / better |
|---|---|---|---|---|---|---|
| turbo | FLEURS en | 200 | 6.70 | 6.68 | −0.02 (−0.32 … +0.24) | 8 / 6 |
| turbo | FLEURS es | 50 | 3.10 | 3.10 | 0.00 | 0 / 0 |
| turbo | FLEURS de | 50 | 5.71 | 5.37 | −0.34 (−1.47 … +0.55) | 4 / 4 |
| turbo | FLEURS fr | 50 | 8.78 | 8.86 | +0.08 (−0.47 … +0.72) | 2 / 2 |
| turbo | FLEURS zh (CER) | 100 | 9.74 | 9.98 | +0.24 (−0.16 … +0.63) | 16 / 7 |
| turbo | FLEURS ja (CER) | 200 | 7.25 | 7.48 | +0.24 (−0.06 … +0.59) | 22 / 17 |
| turbo | AMI ihm | 200 | 13.95 | 14.83 | +0.88 (−0.58 … +2.88) | 13 / 11 |
| Qwen3 | FLEURS en | 200 | 7.33 | 7.38 | +0.04 (−0.34 … +0.42) | 25 / 20 |
| Qwen3 | FLEURS zh (CER) | 100 | 9.27 | 9.01 | −0.26 (−1.13 … +0.58) | 8 / 12 |
| Qwen3 | FLEURS ja (CER) | 200 | 13.38 | 13.65 | +0.27 (−0.82 … +1.43) | 70 / 81 |
| Qwen3 | FLEURS de, first 50 | 50 | 9.24 | 9.15 | −0.08 (−1.50 … +1.32) | 12 / 15 |

Every interval includes 0. Turbo leans slightly worse (65 utterances worse, 47 better over its seven sets; a
sign test gives p = 0.11), Qwen3 doesn't (115 worse, 128 better; p = 0.44). Since the new features are closer to the
exact transform the models were trained on, a real loss is unlikely. AMI's +0.88 is
almost all one clip: 22 of its 25 extra errors are a short turn ("It's an Excel … X. S. L.") that the new
build loops on ("xlsx.xlsx.xlsx…"). Noise clips: no text either way.

## 2. A shorter encoder input (#46, candidate 1)

**What the encoder actually gets.** sherpa-onnx's Whisper export (`scripts/whisper/export-onnx.py`) has a
dynamic frame axis and slices the positional embedding (`positional_embedding[: x.shape[1]]`). Its recognizer
(`offline-recognizer-whisper-impl.h`) feeds `min(frames + tail_paddings, 3000)` frames: the speech plus
`tail_paddings` frames of 0 (after normalisation). The app sets `tailPaddings = 1000`, sherpa's own default.
So the window is already "the utterance, rounded up", plus 10 s. No re-export is needed to try shorter inputs;
`run.py --whisper-tail-paddings N` does.

**Accuracy.** App session, full sets, the FFT patch in all rows. Error rate, then the same with a repeated
block cut off (`tail_table.cut_repeats`: what stopping the decoder at a repeat would give), then the number of
runaway outputs:

| Tail (frames) | FLEURS en | es | de | fr | zh (CER) | ja (CER) | AMI ihm | noise |
|---|---|---|---|---|---|---|---|---|
| 1000 (the app) | 6.7 / 6.7; 0 | 3.1 / 3.1; 0 | 5.4 / 5.4; 0 | 8.9 / 8.9; 1 | 10.0 / 10.0; 0 | 7.5 / 6.2; 1 | 14.8 / 15.0; 1 | none |
| 600 | 6.9 / 6.9; 0 | 3.4 / 3.4; 0 | 7.8 / 5.9; 0 | 9.7 / 9.7; 1 | 9.9 / 9.9; 0 | 10.1 / 6.2; 3 | 22.6 / 22.4; 1 | none |
| 300 | 14.1 / 10.1; 2 | 15.2 / 5.9; 2 | 7.9 / 6.0; 0 | 12.8 / 9.7; 2 | 22.3 / 17.3; 3 | 29.3 / 11.2; 12 | 80.5 / 39.9; 11 | none |
| 100 (5 sets) | 25.9 / 12.7; 6 | 54.6 / 22.2; 8 | 14.5 / 5.0; 1 | 85.3 / 38.4; 9 | 48.7 / 32.4; 8 | – | – | none |

With less padding turbo doesn't emit its end-of-text token and repeats the sentence ("Okay, so this is our
second meeting. Okay, so this is our second meeting. …"), worst on AMI's short turns. Cutting the repeats
doesn't rescue it: AMI is still 39.9 at 300, and 22.4 at 600, where the extra errors aren't repeats at all.
The loss tracks how little padding follows the speech, not how short the whole input is: FLEURS-en clips
(about 10 s, so 1300 frames at tail 300) lose as much as shorter ones. A floor on the total length therefore
wouldn't help.

**Padding with silence instead of 0** (an experimental sherpa patch: the log-mel of silence, which is what
Whisper saw after the audio in training, instead of sherpa's 0) was worse, not better. At tail 300, first 100
FLEURS-en clips: WER 17.9 against 14.0 (6.0 and 6.2 with repeats cut), 2 runaways against 1; first 43 AMI
clips: 148.2 against 67.6, 6 runaways against 2. Not kept.

**Speed vs utterance length**, `decode_speed.py`, 4 threads, 5 pieces × 3 rounds, tails taking turns (model ms
per decode, median; features add 1–9 ms):

| Tail | 2 s | 5 s | 10 s | 20 s |
|---|---|---|---|---|
| 1000 | 517 | 772 | 1359 | 2436 |
| 600 | 375 (0.73×) | 568 (0.74×) | 1097 (0.81×) | 2126 (0.87×) |
| 300 | 1143 (2.21×) | 433 (0.56×) | 925 (0.68×) | 1898 (0.78×) |
| 100 | 4829 (9.32×) | 559 (0.72×) | 820 (0.60×) | 2040 (0.84×) |

The time falls much less than the frame count (tail 300 at 5 s: 0.53× the frames, 0.56× the time; at 20 s
0.77× the frames, 0.78× the time), and at short lengths the loops run the decoder to its 224-token limit, which
costs more than the encoder saved (2 s at tail 100: median output 1023 characters). Even a lossless shorter
tail would have bought at most 1.2–1.4× on the usual 3–10 s dictation utterance.

## 3. Draft partials from a small model (#46, candidate 2)

Turbo is the app's engine for languages the Parakeets and Canary don't cover (#45), so Parakeet Ultra can't
draft for it; only a multilingual Whisper shares those languages. Multilingual Whisper tiny and base
(`engines.DRAFTS`, k2-fsa's int8 exports, same tokens as turbo) were decoded through the app's session on
FLEURS. "vs turbo" scores the draft's text with turbo's Final as the reference: how far the live text would
jump when the Final replaces it. Turbo's own rows for hi/ar/ko/vi/tr come from #37's run (`results-37-final`,
same files and patches, the 0.9 s minimum silence of the time).

| Set | turbo | tiny | base | tiny vs turbo | base vs turbo |
|---|---|---|---|---|---|
| FLEURS zh (CER) | 10.0 | 39.8 | 23.2 | 35.6 | 17.7 |
| FLEURS ja (CER) | 7.5 | 61.4 | 26.4 | 61.0 | 26.1 |
| FLEURS ko (CER) | 4.4 | 24.2 | 12.6 | 22.1 | 9.8 |
| FLEURS hi | 31.2 | 249.6 | 133.4 | 243.3 | 130.4 |
| FLEURS ar | 10.3 | 67.7 | 48.4 | 66.8 | 47.2 |
| FLEURS vi | 14.1 | 125.0 | 56.2 | 123.8 | 55.8 |
| FLEURS tr | 8.3 | 56.9 | 33.5 | 56.1 | 31.4 |
| FLEURS en (not a turbo language) | 6.7 | 36.0 | 13.1 | 32.5 | 9.2 |

Host RTF at 2 threads: tiny 0.05–0.06, base 0.06–0.10, turbo 0.29–0.32. Base would cost about a third of
turbo per partial, and its text would change by a quarter to a half of its characters or words at the Final in
most of turbo's languages. Only Korean (9.8) comes close to usable. Not worth a hidden 160 MB model; nothing
was added to the app, so no approval for one was needed.

## 4. Not done

- **Pixel measurements.** The phone was relay-only all session. To check: turbo and Qwen3 decode time with the
  FFT patch (`tools/speed-bench`, as in #31/#47), and a turbo A/B on a few dictations.
- **Lower-bit weights** (candidate 3): not tried, as the issue ranks them last and the encoder is
  compute-bound. #50's 4-bit Qwen3 decoder was 0.79–0.92× the time on the host at a real accuracy cost.
- **Showing the Final's tokens as they decode** (candidate 4): skipped, as the issue suggests when candidate 1
  doesn't land.

## Method

- Builds: `setup.sh` with `VENV=venv-53-base` (sherpa patches 0001–0002, kaldi-native-fbank unpatched: the
  app today; it reproduced the shared venv's #52 Japanese run exactly, 0 of 200 differ) and `VENV=venv-53`
  (plus the kaldi-native-fbank patch). The silence-padding experiment was a third venv with an extra sherpa patch
  (not committed).
- Feature check: a scratch C++ driver linking kaldi-native-fbank's sources (unpatched, patched, and a
  double-precision DFT variant) on 40 clips of 16 kHz float audio, `OnlineWhisperFbank` and `OnlineFbank`.
- WER/CER: `run.py` (app session: VAD, 0.6 s pre-roll, AGC stand-in, 2 threads) into `results-46-base`
  (before), `results-46` (after; tail variants tagged `@wtailN`; tiny/base), `results-53-base`/`results-53`
  (Ultra and the first 50 clips), `results-53q-base`/`results-53q` (Qwen3 full sets). `tail_table.py` and a
  paired bootstrap (2000 resamples, seed 14) over per-utterance errors.
- Timing: `decode_speed.py` at 4 threads with this child's other jobs paused; tails alternate per piece, builds
  alternate per run (two rounds). The `decode_s` sums in the result files are not used: runs were paused
  mid-decode while the timings ran.
