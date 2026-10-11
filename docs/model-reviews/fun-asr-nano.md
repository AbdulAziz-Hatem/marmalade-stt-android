# Model Ethics Review: Fun-ASR-Nano-2512 (int8 ONNX, k2-fsa re-host)

Reviewed 2026-10-02 by claude-code (Opus 5.5), per the `model-ethics-review` skill, for #52.
Scope: `FunAudioLLM/Fun-ASR-Nano-2512` and the sherpa-onnx int8 package the app would download. It is a candidate
Japanese engine. It would only transcribe the user's own dictation on-device. No generation, no cloning, no speaker
identification.

## License verdict first

**Apache-2.0, not the FunASR Model License. But it rests on a card tag alone, so pin the revision.** This is much
cleaner than SenseVoice (`docs/model-reviews/sensevoice-small.md`), with three soft spots.

- **The card says Apache-2.0 and links nothing else.** At the pinned revision the HF metadata is
  `license: apache-2.0`. The card prose (English and Chinese) never mentions a licence and has no "reference and
  learning purposes only" or "academic purposes only" rider. The repo has **no LICENSE, no MODEL_LICENSE and no NOTICE
  file**. Nothing links the FunASR Model Open Source License Agreement.
- **The release itself was tagged Apache-2.0.** The 2025-12-15 "initial commit" by `pengzhendong` (Zhendong Peng, a
  paper author) was `license: apache-2.0`. The tag then vanished on 2025-12-17 when the README was replaced
  (`9eac5984`), and came back on 2026-05-25 (`1a56e859`, "Add model card YAML metadata", by `langgz` = Zhifu Gao).
- **The GitHub README now says the card governs.** PR #136 (merged 2026-07-14): "Model weights are distributed
  separately and follow the license metadata on their model cards. The official Fun-ASR-Nano … cards currently list
  Apache-2.0". The FunASR toolkit README: "when a model card links to the FunASR Model Open Source License Agreement,
  those terms apply". This card does not link it.

**The soft spots:**

1. **One contradicting maintainer reply.** On 2026-05-20, Fun-ASR #99 asked exactly our question. Zhifu Gao
   (`LauraGPT`) answered that the weights "follow the FunASR Model License which permits commercial use with
   attribution". That is the boilerplate he posted on SenseVoice #286/#287 the same day. His later SenseVoice answer
   (#334) was withdrawn as "not final license-owner confirmation". Five days after #99 he put `apache-2.0` on the
   card himself, and in July wrote the "follow the card" README text. The later and more specific acts point to
   Apache. In FunASR #3458 (2026-08-03) the same maintainer set out the rule for Apache-tagged FunASR models: the
   fixed-revision card governs, and `MODEL_LICENSE` is not additionally applied when the card does not reference it.
   He also wrote (translated from Chinese) that, until the publisher formally confirms, a short card tag should not
   be stretched into rights "the publisher has not yet explicitly granted".
2. **The tag is mutable and already disappeared once.** It was missing for five months. HF discussion #7 ("do we have
   any licensing information of the model?", 2026-01-13) is still open and unanswered. **Pin revision
   `272c57b8`** in the NOTICE and the engine manifest. A later card edit cannot change the terms of a revision we
   already pinned.
3. **No copyright holder is named anywhere.** The Fun-ASR GitHub LICENSE is the Apache body with the appendix left as
   the template `Copyright [yyyy] [name of copyright owner]`. The card, ModelScope and the paper name no holder. We
   cannot carry a copyright line that does not exist, so the NOTICE gives attribution (model name, team, source,
   revision) without inventing one.

The rest:

- **The LLM decoder and tokenizer are Qwen3-0.6B: Apache-2.0, "Copyright 2024 Alibaba Cloud".** The tokenizer files in
  both the upstream repo and the export are byte-identical to `Qwen/Qwen3-0.6B`.
- **ModelScope `FunAudioLLM/Fun-ASR-Nano-2512` has no licence field and no front matter.** It adds no other terms.
- **The export adds no terms.** The k2-fsa repo has no licence file or tag. Its source upload on ModelScope is tagged
  `apache-2.0`, `base_model: FunAudioLLM/Fun-ASR-Nano-2512`, `base_model_relation: quantized`.
- **The export script's repo (`Wasser1462/FunASR-nano-onnx`) has no licence** (GitHub API `license: null`). The script
  does not ship with the model and holds no rights in the converted weights. This is the same position the Qwen3-ASR
  0.6B review took for the same exporter.
- **GPL/LGPL: none** in the weights, the tokenizer or the export path.

**Classification:** Apache-2.0 by card tag at a pinned revision, with no licence file, no named holder and one stale
contradicting maintainer reply. **Fine for the benchmark. Fine for a user-facing release on the pinned tag if Max
accepts tag-only evidence.** For a stronger record, ask Alibaba for an immutable tag with a real `LICENSE` file. They
did that for paraformer-zh-streaming in FunASR #3454 (`apache-2.0-20260804`). That would be a public post, so Max
signs off on the text first.

## Ethics verdict

**PASS against Max's bar, grey. Same grade as Qwen3-ASR and SenseVoice.** It is a recognition model, so bar #3
applies: judge how the data was acquired.

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped data. Not ruled out either.** The tech report
  gives scale per stage and data categories, and names no source.
- **Japanese training data is wholly undisclosed.** The word "Japanese" does not appear in the tech report (v4,
  2025-12-19). The report calls the main Fun-ASR model "a Chinese-English model". Only the card says the Nano supports
  "Chinese, English, and Japanese".
- **Bar #2 (impersonation): N/A.** ASR outputs text. It cannot clone or imitate a voice.
- **Synthetic speech from a cloning model is in the SFT data** ("TTS generated data using CosyVoice3"). That is a
  minor note: whose voices prompted the synthesis is UNVERIFIED, and the output here is text.
- **Org demerits** carry over from the SenseVoice review: Alibaba's 20-F admits AI-training copyright claims, and the
  same team's CosyVoice headlines zero-shot cloning. Neither touches these weights.

Blocking issues: **none for benchmark use.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Upstream checkpoint | `FunAudioLLM/Fun-ASR-Nano-2512` at `272c57b82523ada6fd87095e955f8e29100979ab` (2026-06-20, head). `model.pt` 1,971,149,431 B, sha256 `55ae0d2fee369f0f11cce0795f6927934ad17cf11b278a7e56a51272074160bb`, last changed in `6d5631d3` ("combine models", 2025-12-15) | https://huggingface.co/api/models/FunAudioLLM/Fun-ASR-Nano-2512/tree/272c57b82523ada6fd87095e955f8e29100979ab?recursive=true ; https://huggingface.co/api/models/FunAudioLLM/Fun-ASR-Nano-2512/commits/main |
| ModelScope copy | `model.pt` is **different**: 2,127,426,538 B, sha256 `81fec861…a499`. Why is UNVERIFIED. The export names the HF repo (below) | https://modelscope.cn/api/v1/models/FunAudioLLM/Fun-ASR-Nano-2512/repo/files?Recursive=true |
| Architecture | `config.yaml`: `audio_encoder: SenseVoiceEncoderSmall` (50 blocks, 512-dim), a 2-layer Transformer adaptor, `llm: Qwen3-0.6b` with `init_param_path: Qwen3-0.6B`, plus a CTC decoder. Paper: "an audio encoder with 0.2B parameters and an LLM-based decoder with 0.6B parameters". Whether the encoder started from SenseVoiceSmall's weights is UNVERIFIED; the paper says it was initialised from Qwen3 layers. Either way the rights holder is Alibaba, who can release its own derivative under Apache | https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512/blob/272c57b82523ada6fd87095e955f8e29100979ab/config.yaml ; https://arxiv.org/abs/2509.12508 §2, §4.1 |
| Distribution point | https://huggingface.co/csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30 at `6f16bd378457e13f36ccf3910df9017f96c346fb` ("add models", 2026-01-13, head) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30/commits/main |
| Files the app needs | `embedding.int8.onnx` 155,584,380 B, `95e61cd0c9c3b9543339a4cf973c95c116815e745ccc1e0285cbd81f76d18644`; `encoder_adaptor.int8.onnx` 237,792,748 B, `f36dea2e30fbc33b5db1d7a7265cc976c5e5586c77b042d5adb1ad27c72db422`; `llm.int8.onnx` 600,356,593 B, `dfbf9aa3be41bccc257587f151e15c63fbe1b549f2b517f5ccd5bdce3bf4322a`; `Qwen3-0.6B/tokenizer.json` 11,422,654 B, `aeb13307a71acd8fe81861d94ad54ab689df773318809eed3cbe794b4492dae4`; `Qwen3-0.6B/merges.txt` 1,671,853 B, `8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5`; `Qwen3-0.6B/vocab.json` 2,776,833 B, `ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910`. Total 1,009,605,061 B | LFS oids from https://huggingface.co/api/models/csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30/tree/6f16bd378457e13f36ccf3910df9017f96c346fb?recursive=true ; the two non-LFS hashes computed locally. The ONNX hashes are not re-computed locally (weights not downloaded) |
| ONNX metadata | `encoder_adaptor.int8.onnx` and `embedding.int8.onnx`: `model_author=FunAudioLLM`, `comment=FunAudioLLM/Fun-ASR-Nano-2512`, url `https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512`, `onnx.infer=onnxruntime.quant`. `llm.int8.onnx`: `model_type=qwen3_causallm_unified_prefill_decode_kv_delta`, `max_total_len=512`, `quantization_type=int8` | read from the last 16 KB of each file (HTTP range request) |
| Who exported it | The repo README: "Models in this directory are downloaded from https://www.modelscope.cn/models/zengshuishui/FunASR-nano-onnx/files … Export script can be found at https://github.com/Wasser1462/FunASR-nano-onnx". The sherpa docs: "converted from Fun-ASR-Nano-2512 using scripts from https://github.com/Wasser1462/FunASR-nano-onnx" | https://huggingface.co/csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30/blob/6f16bd378457e13f36ccf3910df9017f96c346fb/README.md ; https://k2-fsa.github.io/sherpa/onnx/funasr-nano/pretrained.html |
| Byte-identity with the source upload | **Not identical today.** The ModelScope int8 files have different sizes and hashes (e.g. `embedding.int8.onnx` 155,583,106 B). That repo was re-exported on 2026-06-04, after k2-fsa copied it in January. The tokenizer files match | https://modelscope.cn/api/v1/models/zengshuishui/FunASR-nano-onnx/repo/files?Recursive=true |
| Who re-hosts it | Fangjun Kuang (`csukuangfj`, k2-fsa). Same maintainer as our Silero VAD, distil-small.en and Qwen3-ASR packages | commits API above |
| sherpa-onnx support | `OfflineRecognizer` FunASR-nano support landed 2025-12-30 (#2936) and is in our pinned `11afbd00` | https://github.com/k2-fsa/sherpa-onnx/blob/11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf/sherpa-onnx/csrc/offline-funasr-nano-model.cc |

**What the export is.** A mechanical ONNX conversion of the published checkpoint with ONNX Runtime int8 quantization.
No retraining. The app does not download `test_wavs/`.

## License (weights / code)

- **Card tag history** (each from `https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512/raw/<rev>/README.md`):
  `ef76ad97` (2025-12-15, initial) `license: apache-2.0`; `a1833f8e` `license: Apache License 2.0`; `6d5631d3`
  `apache-2.0`; `9eac5984` (2025-12-17) to `abb99a38` (2026-05-23) **no front matter**; `1a56e859` (2026-05-25) to
  `272c57b8` `license: apache-2.0`. An HF-staff PR proposing the same tag (discussion #6, 2025-12-22) was never merged.
- **Fun-ASR #99** (2026-04-24, CrispASR): "Are the model weights also released under Apache-2.0, or do they have a
  separate license?" A third party pointed at FunASR's `MODEL_LICENSE`. The maintainer's reply is quoted above.
  Closed "completed". https://github.com/QwenAudio/Fun-ASR/issues/99
- **Fun-ASR PR #136** (merged 2026-07-14, `9ff2e09a`): the README License section quoted above, also in `README_zh.md`
  ("官方 Fun-ASR-Nano 与 Fun-ASR-MLT-Nano 模型卡目前均标注 Apache-2.0"). https://github.com/QwenAudio/Fun-ASR/pull/136
- **FunASR #3458**, the maintainer's rule for Apache-tagged cards:
  https://github.com/modelscope/FunASR/issues/3458#issuecomment-5165189699
- **Code: Apache-2.0**, `QwenAudio/Fun-ASR` (formerly `FunAudioLLM/Fun-ASR`; GitHub redirects). The LICENSE has one
  commit (`b9f117d7`, 2025-12-15) and an unfilled copyright appendix.
  https://github.com/QwenAudio/Fun-ASR/blob/0339018ba74a7defa3b6b6a96718d17b816be77b/LICENSE . Not shipped.
- **Qwen3-0.6B:** https://huggingface.co/Qwen/Qwen3-0.6B/blob/c1899de289a04d12100db370d81485cdf75e47ca/LICENSE
  ("Copyright 2024 Alibaba Cloud"). `tokenizer.json` matches its LFS oid `aeb13307…dae4`.
- **The paper is CC BY 4.0** (arXiv page). It says nothing about the weights' licence.

## Provenance (training data)

**Tech report** (https://arxiv.org/abs/2509.12508, v4; HTML https://arxiv.org/html/2509.12508):

- §3.1 pre-training: "approximately tens of millions hours of audio data, including both unlabeled audio and labeled
  audio-text data. The unlabeled audio data span a broad range of real-world scenarios in domains such as artificial
  intelligence, biotechnology, e-commerce, education, entertainment, finance, and mobility." Labels are pseudo-labels
  "by multiple ASR systems (such as Paraformer-V2, Whisper, and SenseVoice)". "The primary languages in the labeled
  dataset are Chinese and English."
- §3.2 SFT: "approximately millions of hours … human-transcribed data, pseudo-labeled data, environmental noise data,
  TTS generated data using CosyVoice3, simulated streaming data, noise augmented data, and hotword customized data."
- §4.2 stages: 200k h (adaptor), "about 10M hours of low-cost ASR training data" (encoder), 20k h (LoRA), "about 3M
  hours" of high-quality data. §4.3: "over 50K hours" of contextual SFT with Qwen3-32B-synthesised context. §5.2: "110K
  hours of low-noise speech and 10K hours of noise samples from our in-house corpus". §4.4.3: RL "hard cases" from "a
  large amount of unlabeled speech".
- §5.3: the multilingual model's dataset "comprises approximately 500k hours". That describes Fun-ASR-ML, not the
  Nano-2512 Japanese data.
- §6.1: "all training data is collected before June 30th 2025". The test set was "newly uploaded videos from YouTube and
  Bilibili posted after June 30th". That suggests, but does not prove, that public video is a training source too.
- §7 Limitations covers only accuracy, context length and far-field audio. No ethics or data-rights section.

**Japanese:** no figure, no source, no mention in the paper. The card and GitHub README only say "English and Japanese
cover multiple regional accents". UNVERIFIED in every respect.

**Community questions.** Fun-ASR #50 (2025-12-31) asked about the "language distribution … within the ~100k-hour
training data". It was closed for inactivity on 2026-05-24 without an answer
(https://github.com/QwenAudio/Fun-ASR/issues/50). It asked about proportions, not sources. GitHub searches for
"dataset", "data", "japanese" and "licen" found nothing else on provenance. None of the 8 HF discussions is about data
(https://huggingface.co/api/models/FunAudioLLM/Fun-ASR-Nano-2512/discussions).

**Org-level disclosure.** Alibaba Cloud's training-data summary is scoped to Qwen and Wan, not to Fun-ASR (see the
Qwen3-ASR 0.6B review). It admits public web data and licensed partner data.

**Overall provenance grade: OPAQUE.** Scale, stages and categories are disclosed in more detail than SenseVoice, but
no corpus is named and the Japanese data is not mentioned at all. Per the skill: "Scale disclosed, sourcing not … is
NOT disclosure."

**Piracy check (bar #1):** no evidence of pirated or paywalled-scraped content. **UNVERIFIED** either way.

## Posture: **Structurally N/A for impersonation.**

- Output is text. The sherpa export carries only the LLM path (no emotion or event tags, unlike SenseVoice).
- No consent or misuse language on the card, the GitHub README or the paper.
- Our use is the benign case: the user's own dictation, on-device, never stored.

## Org track record

- **Alibaba Group, Tongyi Lab (HF `FunAudioLLM`, GitHub now `QwenAudio`): grey.** Unchanged from
  `docs/model-reviews/sensevoice-small.md` (2026-09-27), not re-checked here: the FY2026 20-F admits "claims from
  content owners alleging unauthorized use of their materials for AI training"; CosyVoice (same team) headlines
  zero-shot voice cloning with an "academic purposes only" disclaimer over an Apache tag.
- **Licence churn, again.** The Fun-ASR card lost and regained its licence tag, and the maintainer's #99 answer
  contradicts his own card edit. The replies in this org carry `funasr-ops:` markers and look agent-assisted, which may
  explain the inconsistency.
- In its favour: a detailed tech report, Apache-2.0 code, and an org that publishes immutable licence tags when asked
  (#3454). The weights have not changed since 2025-12-15.
- **Wasser1462 / zengshuishui (exporter):** individual sherpa-onnx contributor. No concerns beyond the unlicensed
  script repo. **k2-fsa / Fangjun Kuang (re-host):** no concerns.

## Feasibility (secondary)

- About 0.8B parameters, **1.01 GB** int8 download, about the same as Qwen3-ASR 0.6B (987 MB).
- Offline only in sherpa-onnx. An autoregressive LLM decoder, so slower and hallucination-prone. The export's KV cache
  is sized `max_total_len=512` (audio plus text tokens). What that means for long utterances is unmeasured.
- The card claims zh, en and ja. The paper: "primarily optimized for Chinese and English … support for other languages
  remains limited". Japanese accuracy is what #52's benchmark has to settle.

## Working demo link

- https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition (RUNNING when checked). Its `model.py` loads **this
  exact repo** (`csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30`). Pick "31 languages (FunASR Nano)", then that
  repo. The repo's `test_wavs/` include `ja.wav` and `ja_en_codeswitch.wav`.
- Official: https://huggingface.co/spaces/FunAudioLLM/Fun-ASR-Nano (RUNNING). It runs the PyTorch checkpoint on HF
  hardware, so uploads leave the device. Fine for test clips.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **Apache-2.0 by card tag, pass (pin the revision)** | Tag at release and at `272c57b8`; README says weights follow the card. No LICENSE file, no named holder, tag missing Dec 2025 to May 2026, one stale maintainer reply naming the FunASR licence |
| License (export) | **No added terms** | k2-fsa repo has no licence; ModelScope source tagged Apache-2.0; script repo unlicensed but not shipped |
| Provenance | **OPAQUE** | "Tens of millions" of hours, per-stage scale and categories. No named corpus. Japanese not mentioned in the paper. #50 closed unanswered |
| Posture | **N/A for impersonation** | Text only. No consent or misuse language anywhere |
| Org track record | **Alibaba grey** | 20-F copyright claims, CosyVoice cloning, licence-answer churn |
| **Verdict vs Max's bar** | **PASS (grey)** | Bar #1: no evidence of piracy, sources UNVERIFIED. Bar #2: N/A. Bar #3: undisclosed but plausibly public, a minor demerit |

## NOTICE entry

**Copyright lines.** Fun-ASR-Nano: **none published** (the card, ModelScope and paper name no holder, and the GitHub
LICENSE appendix is the unfilled template). Qwen3: verbatim from the Qwen3-0.6B LICENSE appendix at `c1899de2`. The
body is the shared `app/src/main/assets/licenses/Apache-2.0.txt`. No upstream NOTICE file exists to pass through.

**Draft entry for `NOTICE.md` → "Downloadable speech models", and for the in-app licenses screen:**

```
### Fun-ASR-Nano-2512 (int8, k2-fsa re-host)

> The Fun-ASR-Nano engine is Fun-ASR-Nano-2512 by Tongyi Lab (FunAudioLLM),
> Alibaba Group: https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512 at revision
> 272c57b82523ada6fd87095e955f8e29100979ab, licensed under the Apache License 2.0
> per that revision's model card. Its language-model decoder and tokenizer are
> Qwen3-0.6B (https://huggingface.co/Qwen/Qwen3-0.6B), Copyright 2024 Alibaba Cloud,
> Apache License 2.0.
>
> This copy is a third-party ONNX conversion with int8 dynamic quantization
> (export scripts by Wasser1462), re-hosted by the k2-fsa / sherpa-onnx project at
> https://huggingface.co/csukuangfj/sherpa-onnx-funasr-nano-int8-2025-12-30,
> commit 6f16bd378457e13f36ccf3910df9017f96c346fb. No retraining. Files:
> embedding.int8.onnx, encoder_adaptor.int8.onnx, llm.int8.onnx and the
> Qwen3-0.6B tokenizer files (tokenizer.json, merges.txt, vocab.json).
```

**License text:** the shared Apache-2.0 body. If Alibaba ever re-links the FunASR Model License to this model, this
entry already meets its §2.2 attribution clause (source, author and model name). The review is in
`docs/model-reviews/fun-asr-nano.md`.
