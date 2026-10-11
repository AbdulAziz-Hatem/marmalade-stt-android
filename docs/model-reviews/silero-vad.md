# Model Ethics Review: Silero VAD (as bundled in marmalade-stt-android)

Reviewed 2026-09-22 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the exact file we bundle, used only for on-device voice activity detection
(speech start/end). No generation, no cloning, no identification.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| File | `silero_vad.onnx`, 643,854 bytes | `app/src/main/assets/vad/silero_vad.onnx` (at review time a download to `/tmp`; same sha256) |
| sha256 | `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6` | local `sha256sum`; also matches the GitHub-computed asset digest in the release API: https://api.github.com/repos/k2-fsa/sherpa-onnx/releases/tags/asr-models |
| Download URL | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx | uploaded 2025-07-11T07:04:20Z (release API above) |
| Producer | pytorch 2.2.2, ONNX IR 7, opset 13 | read from the file with the `onnx` Python package |
| Inputs / outputs | `x [1,512]`, `h [2,1,64]`, `c [2,1,64]` → `prob`, `new_h`, `new_c` | same |
| Embedded metadata | `model_type=silero-vad`, `version=1`, `url=https://github.com/snakers4/silero-vad`, `license=https://github.com/snakers4/silero-vad/blob/master/LICENSE`, `comment="This is silero-vad v4 exported to onnx by k2-fsa. Only the 16kHz branch is kept"` | same |

## Provenance of the k2-fsa file: which Silero version? **v4.0 (16 kHz branch). Established.**

Three independent lines of evidence:

1. **The PR that introduced it.** sherpa-onnx PR #2372, "Support silero-vad v4 exported by k2-fsa", merged
   2025-07-11 (the same day the asset was uploaded) by csukuangfj (Fangjun Kuang). Its table lists
   "silero_vad.onnx exported by us, 629 KB, supports only 16 kHz" next to the original v4.0 file at
   `snakers4/silero-vad/blob/v4.0/files/silero_vad.onnx` (1.72 MB, 16 + 8 kHz).
   https://github.com/k2-fsa/sherpa-onnx/pull/2372
2. **The file's own metadata** says "silero-vad v4 exported to onnx by k2-fsa. Only the 16kHz branch is kept"
   (dumped above). The export recipe is in sherpa-onnx's `scripts/silero_vad/v4/` (README says it starts from
   `https://github.com/snakers4/silero-vad/raw/refs/tags/v4.0/files/silero_vad.jit`, rewrites the forward
   pass for the 16 kHz model, exports with `torch.onnx.export`, and runs onnx-simplifier).
   https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/silero_vad/v4
   The runtime recognises this I/O signature explicitly as `IsExportedByK2Fsa()`:
   https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/csrc/silero-vad-model.cc
3. **Weight comparison (done locally for this review).** I extracted every float32 tensor with ≥64 elements
   from our file (27 tensors, 155,612 floats) and from the upstream `files/silero_vad.onnx` at tags v3.1,
   v4.0 and v5.0 (downloaded from `https://github.com/snakers4/silero-vad/raw/refs/tags/<tag>/files/silero_vad.onnx`).
   - **v4.0: 22 of 27 tensors byte-identical, covering 149,148 of 155,612 floats (95.8%).** The other 5 are
     1x1-conv weights/biases whose values are 81–95% present in v4.0. That is what onnx-simplifier constant
     folding produces.
   - v3.1 and v5.0: only 1 tensor matches (the fixed 66,048-float STFT basis, which is not learned). Nothing else.

So the file is a format conversion (JIT → ONNX, 8 kHz branch dropped, graph simplified) of **Silero VAD v4.0**,
released 2022-10-28: https://github.com/snakers4/silero-vad/releases/tag/v4.0. **k2-fsa did no retraining
or fine-tuning.** Side note: sherpa-onnx also mirrors the original upstream v4 file as `silero_vad_v4.onnx`
(1,807,522 bytes, the same size as upstream v4.0) in the same release. Size match only, hash not compared.

## License (weights / code)

- **Weights and code: MIT, "Copyright (c) 2020-present Silero Team".** The LICENSE file is the same at tag v4.0
  and on master, and it has one commit in its history ("Add License", 2020-11-23). The file has never been relicensed.
  - https://github.com/snakers4/silero-vad/blob/v4.0/LICENSE
  - https://api.github.com/repos/snakers4/silero-vad/commits?path=LICENSE
- The v4.0 README says: "Published under permissive license (MIT) Silero VAD has zero strings attached - no
  telemetry, no keys, no registration, no built-in expiration". https://github.com/snakers4/silero-vad/blob/v4.0/README.md
- No license tag contradicts the card prose. The one oddity is cosmetic: the README's license badge has alt text
  "License: CC BY-NC 4.0" (apparently copied from silero-models), but it renders "MIT" and links to the MIT
  LICENSE (line 1 of https://github.com/snakers4/silero-vad/blob/master/README.md). The LICENSE file governs.
  If anyone raises the badge, this explains it.
- **k2-fsa conversion:** the release asset carries no separate license. Its embedded `license` field points to
  Silero's MIT LICENSE. sherpa-onnx itself is Apache-2.0, and its export script is "Copyright 2025 Xiaomi Corp."
  We ship the model, not the script. A mechanical format conversion adds no new copyrightable authorship to the weights
  (a legal judgement I have not verified). Crediting k2-fsa is a courtesy, not an obligation.
- **No official Hugging Face card exists** (no models under `silero`, `snakers4` or `silero-ai`:
  https://huggingface.co/api/models?author=silero). HF surface 1 is therefore N/A. Community HF copies
  (e.g. `istupakov/silero-vad-onnx`) are not our source.

## Provenance (training data): **OPAQUE for v4** (scale disclosed, sourcing not)

- The v4.0 README says only: "trained on huge corpora that include over **100** languages".
  https://github.com/snakers4/silero-vad/blob/v4.0/README.md
- The Gradient article by Silero's authors (Veysov & Voronin, 2022) gives scale, not sources: "We trained our
  VAD on approximately 13k hours of speech spanning 100 languages". It describes the 2021 rework (v3 era), so
  its applicability to v4 is **UNVERIFIED**. https://thegradient.pub/one-voice-detector-to-rule-them-all/
- The FAQ, under "Papers, datasets, training code": "we have not published any of those for lack of time and
  motivation." https://github.com/snakers4/silero-vad/wiki/FAQ
- **Direct questions went unanswered.** Each of these was closed as "completed" with **zero replies**:
  - #544 "Silero VAD 4.0 training data information" (2024): https://github.com/snakers4/silero-vad/issues/544
  - #98 "VAD Training Data Used" (2021): https://github.com/snakers4/silero-vad/issues/98
  - #780 "Request: Training dataset and model architecture details" (2026): https://github.com/snakers4/silero-vad/issues/780
- **The v5-era partial disclosure does not apply to our v4 file.** In March–April 2024 Silero published
  `datasets/README.md`. It is a set of VAD *annotations* of five open corpora: Bible.is (53k h, "unique" terms),
  globalrecordings.net (CC BY-NC-SA 4.0), VoxLingua107 (CC BY 4.0), Common Voice (CC0) and MLS (CC BY 4.0).
  The dataset was funded by Russia's Innovation Promotion Fund. https://github.com/snakers4/silero-vad/blob/master/datasets/README.md
  It does not say it is the training set. v5's "6,000+ languages" claim lines up with globalrecordings' 6,171 ISO
  codes, which is circumstantial evidence that it is v5's training set. It post-dates v4 by about 18 months, and v4
  claims only 100 languages. So whether any of it went into **v4** is **UNVERIFIED**.
- **Piracy check (Max's bar #1):** I found no evidence that v4 was trained on paid or non-public (paywalled or
  pirated) content. I also cannot rule it out, because v4 sources are undisclosed. Status: **UNVERIFIED, no red flag found.**

## Posture: **Structurally N/A (cannot impersonate)**

- A VAD outputs one speech probability per 32 ms frame. It cannot generate, clone or identify a voice. Max's bar #2
  is about impersonation, and it cannot apply to this artifact.
- Silero's demos for the VAD are a Colab notebook and examples on generic audio. They do not use celebrity or
  character voices. https://colab.research.google.com/github/snakers4/silero-vad/blob/master/silero-vad.ipynb
  (the notebook resolves: https://raw.githubusercontent.com/snakers4/silero-vad/master/silero-vad.ipynb returned 200)

## Org track record (Silero; secondary for a VAD, but recorded)

Silero is a small Russian speech company (author: Alexander Veysov, snakers4). The following are **demerits of the
org, none of which touch the v4 VAD weights directly**:

- **Open STT (their Russian ASR corpus)** is built from radio, YouTube (subtitle-aligned), audiobooks "Internet +
  alignment" and phone calls "Internet + ASR". It is licensed "CC-BY-NC and commercial usage available after agreement
  with dataset authors". That means Silero licenses scraped third-party audio as if they own it.
  https://github.com/snakers4/open_stt/blob/master/README.md
  Whether the audiobooks were commercial or paid titles is **UNVERIFIED**. Whether Open STT fed the VAD is **UNVERIFIED**.
- **Bible.is terms** allow only "personal, non-commercial use" and forbid copying or downloading content otherwise
  (https://live.bible.is/terms). Silero nonetheless published 53k hours of Bible.is annotations. This is a v5-era
  concern, and the audio is free and public, not paywalled. Under Max's bar that is a grey terms issue, not piracy.
  Given Max's missions context, he may care that the source is Faith Comes By Hearing's recordings.
- **TTS side (silero-models):** licensed CC BY-NC-SA 4.0 (https://github.com/snakers4/silero-models/blob/master/LICENSE).
  Asked about TTS training data, the maintainer replied "As for our voices - they are not open"
  (https://github.com/snakers4/silero-models/issues/153). A 2022 blog post describes building a TTS voice model
  of a client's employee, from about 2 hours of recordings, to pen-test a voice-biometric system, and says the synthesis
  fooled it about 90% of the time. The post then plays down the spoofing risk
  (https://silero.ai/silero-tts-v1-vs-biometric-system/). It is framed as a commissioned security test. It is not a
  celebrity-cloning showcase, but the org does do bespoke voice cloning. That is irrelevant to a VAD. Note it before
  ever considering Silero **TTS**.
- **Positive signals:** the VAD has been MIT since 2020 with no relicensing, no telemetry, and active maintenance
  (v6.0 in 2025-08: https://github.com/snakers4/silero-vad/releases/tag/v6.0). No lawsuits, retractions or shutdowns found.
- **Wider sweep:** Silero has published no VAD paper (FAQ above). The HN threads
  (https://news.ycombinator.com/item?id=29734797, https://news.ycombinator.com/item?id=45023499) contain no
  provenance disclosure.
- **k2-fsa** (Next-gen Kaldi, Xiaomi-affiliated) acted only as converter and distributor (PR #2372 above). No concerns found.

## Feasibility (secondary)

Already proven in production. Marmalade's Android client bundles the same file, and the sherpa-onnx runtime supports it
natively. At 644 KB it is small. It supports 16 kHz only, which matches our pipeline.

## Working demo link

https://colab.research.google.com/github/snakers4/silero-vad/blob/master/silero-vad.ipynb (the source notebook
resolves, HTTP 200). For a VAD, "listening" is less useful than the in-app behaviour we already have in Marmalade.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **MIT, pass** | LICENSE at v4.0, never changed |
| Provenance | **OPAQUE** (scale only: 100+ langs, ~13k h) | three training-data issues closed unanswered |
| Posture | **N/A, structurally cannot impersonate** | VAD outputs a speech probability only |
| Org track record | **Grey** | scraped-data corpus relicensed as NC-plus-paid; ToS-grey v5-era data; bespoke cloning work on the TTS side |
| **Verdict vs Max's bar** | **PASS (with a noted opacity demerit)** | Bar #1: no evidence of paid or non-public data (opacity is a demerit, not a disqualifier). Bar #2: N/A |

**Blocking issues: none.** The final adoption decision is Max's. If Max ever wants to lower the opacity demerit, the
options are (a) stay on v4, whose provenance is simply unknown, or (b) move to v5/v6, whose likely data is partly
disclosed but includes Bible.is (ToS-grey) and CC BY-NC-SA globalrecordings audio. Better transparency there comes
with more concrete grey. **I recommend staying on v4.**

## NOTICE entry

**Copyright line(s)**

```
Silero VAD
Copyright (c) 2020-present Silero Team
```

**License:** MIT. Bundle the full MIT text from https://github.com/snakers4/silero-vad/blob/v4.0/LICENSE,
because MIT requires the copyright and permission notice to accompany all copies of the weights.

**Provenance statement (for NOTICE.md)**

> `silero_vad.onnx` (643,854 bytes, SHA-256
> 9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6) is the Silero VAD v4.0 voice activity
> detection model by the Silero Team (https://github.com/snakers4/silero-vad), distributed under the MIT
> License. This copy is the 16 kHz-only ONNX conversion published by the k2-fsa / sherpa-onnx project
> (https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx, introduced in
> https://github.com/k2-fsa/sherpa-onnx/pull/2372). That conversion re-exported the upstream v4.0 TorchScript
> model to ONNX, removed the 8 kHz branch and simplified the graph, without retraining. The model runs entirely
> on-device to detect the start and end of speech. It does not transmit, store or identify audio.
