# Model Ethics Review: SenseVoice-Small (int8 ONNX, k2-fsa export)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill, for #29.
Scope: `FunAudioLLM/SenseVoiceSmall` (ModelScope `iic/SenseVoiceSmall`) and the two sherpa-onnx int8 exports the app
could download. It is a candidate on-device engine for the benchmark harness, not yet in the app. It would only
transcribe the user's own dictation on-device. The model also emits language, emotion and audio-event tags; see
Posture.

## License verdict first

**Not non-commercial on its face, but not clean either. Treat it as unresolved for a commercial release.** The
weights are under a custom, non-OSI licence, the **FunASR Model Open Source License Agreement v1.1**. Its grant is
permissive with an attribution rider. It adds two restrictive riders and contains one sentence that the licensor has
not settled:

1. **Commercial use is not confirmed.** §2.1 grants "use, copy, modify, and share" with no commercial carve-out.
   But §3 says the model "is provided for reference and learning purposes only". A maintainer (Zhifu Gao,
   `LauraGPT`, Alibaba) said three times in 2026 that commercial use is permitted
   (SenseVoice #286, #287, #334). He then **reopened #334**: that answer "should be treated as context, not as a
   substitute for" license-owner confirmation. On 2026-09-22 he stamped it "not final license-owner confirmation" and
   opened PR #357, which removes the "commercial use is permitted" wording from the README (unmerged). FunASR #3715
   asks exactly our question (this export, this revision, these hashes). It is still open, with the same "remain
   open for the relevant license owner" status. **So nobody at Alibaba currently stands behind "commercial use is
   OK".**
2. **Non-disparagement with automatic forfeiture (§4.2).** "Individual or organizational users shall not engage in
   unjustified denigration, malicious smearing, or baseless insults against [FunASR Software] … it will be
   considered an automatic forfeiture of all licenses under this agreement." Honest benchmark numbers are not
   "unjustified". But Alibaba decides what counts as a breach.
3. **Unilateral amendment (§6).** "The revised agreement will be published in the official repository … and will
   take effect automatically. Continuing to use, copy, modify, and share [FunASR Software] indicates your acceptance
   of the revised agreement." v1.0 became v1.1 this way in 2024-11, adding the §4 non-disparagement clause. A future
   revision could add restrictions to weights we already ship.

The rest:

- **Redistribution and modification are allowed**, including via a third-party mirror, ONNX conversion and int8
  quantization (§2.1 "use, copy, modify, and share"). §1 defines the licensed material as "model weights and their
  derivatives, including finetuned models", so the terms follow the export.
- **Attribution is required (§2.2).** "You must attribute the source and author information and retain relevant model
  names."
- **Governing law is a blank template placeholder:** "This agreement is governed by the laws of [Country/Region]."
  §3 is an as-is disclaimer. There is no patent grant.
- **Metadata disagrees across hubs.** The HF card says `license: other` and links MODEL_LICENSE on the moving `main`
  branch. The ModelScope card says `license: Apache License 2.0`. The SenseVoice README and maintainers say the
  FunASR agreement governs. Take the stricter reading: FunASR v1.1, pinned at `58830eca`.
- **Code (SenseVoice and FunASR repos): MIT**, "Copyright (c) 2025 FunASR". It is not shipped. The app uses
  sherpa-onnx's own runtime.
- **The k2-fsa export adds no terms.** Its `LICENSE` is one line pointing at FunASR's README licence section.
- **The newer `…-int8-2025-09-09` export is different weights with a worse licence picture.** It is ASLP-lab's
  Cantonese fine-tune (NWPU). It is tagged Apache-2.0, but the fine-tune is still FunASR-licensed under §1, and it was
  trained on WenetSpeech-Yue, which is tagged **CC-BY-NC-4.0**. **Do not use it.** See Download sources checked.

**Classification:** custom licence, permissive with an attribution rider, plus restrictive riders (§4.2
non-disparagement forfeiture, §6 automatic amendment), plus an open commercial-use ambiguity (§3). **Fine for a
developer-only benchmark engine. For a user-facing commercial release, Max has to make the call**, ideally after
FunASR #3715 or SenseVoice #334 gets a license-owner answer.

## Ethics verdict

**PASS against Max's bar, grey. Same grade as Qwen3-ASR.** It is a recognition model, so bar #3 applies: judge how
the data was acquired.

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped data. It cannot be ruled out, because the
  training data is undisclosed.** The paper gives only scale: SenseVoice-Small was "trained on … approximately
  300,000 hours" in 5 languages. The card's "over 400,000 hours" includes Large's extra 100k. No corpus is named.
- **A direct question went unanswered.** SenseVoice #186, "官方的训练数据集有没有可能开放？" ("Could the official
  training dataset be opened?"), sat for 15 months with no reply. The maintainer then closed it as `not_planned`
  without comment.
- **Circumstantial evidence points at public web data plus bought or collected corpora.** The repo's example
  training manifest has GigaSpeech-style segment IDs (YouTube, podcast and audiobook), YouTube-video-ID keys from a
  labelling "production" pipeline, an NBA broadcast commentary line and a Korean smartphone-recorded speaker corpus.
  None of it looks paywalled. GigaSpeech's own access terms are non-commercial. Bar #3 says not to judge copyright
  technicalities, so that is a note, not a fail.
- **Bar #2 (impersonation): N/A.** A CTC recognizer outputs text and tags. It cannot synthesise a voice.
- **Emotion recognition: a minor posture note, not a blocker.** See Posture. Recommendation: drop the emotion and
  event tags in the engine adapter. Never show or store them.
- **Org demerits.** The same team (FunAudioLLM, now GitHub `QwenAudio`) ships CosyVoice, which is built around
  zero-shot and cross-lingual voice cloning, trained on web-collected audio, and carries an "academic purposes only"
  disclaimer that contradicts its Apache tag. Alibaba's 20-F admits AI-training copyright claims. Neither touches
  this model's weights.

Blocking issues: **none for developer-only use.** The licence, not the ethics, is what gates a commercial release.
The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Upstream checkpoint | `FunAudioLLM/SenseVoiceSmall` at `3847d57b6bdf2dd8875cb1508d2af43d80a16bf7` (2026-06-20; the repo head at review time). `model.pt` 936,291,369 B, sha256 `833ca2dcfdf8ec91bd4f31cfac36d6124e0c459074d5e909aec9cabe6204a3ea`, last changed in `2b05887414d0` (2024-07-04). Every commit after 2024-07-31 edits only the card | https://huggingface.co/api/models/FunAudioLLM/SenseVoiceSmall/tree/3847d57b6bdf2dd8875cb1508d2af43d80a16bf7?recursive=true ; https://huggingface.co/api/models/FunAudioLLM/SenseVoiceSmall/commits/main |
| Same weights on ModelScope | `iic/SenseVoiceSmall` `model.pt`, same size and **same sha256** (`833ca2dc…a3ea`) | https://modelscope.cn/api/v1/models/iic/SenseVoiceSmall/repo/files?Recursive=true |
| Distribution point (recommended) | https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 at `2365baeacb507f821a0c8120fcee3d484dba7a07` ("add models", 2024-07-18; the repo head) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/commits/main |
| Files the app needs | `model.int8.onnx` 239,233,841 B, sha256 `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51`; `tokens.txt` 315,894 B, sha256 `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc`. Total 239,549,735 B | LFS oid from the tree API (https://huggingface.co/api/models/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/tree/2365baeacb507f821a0c8120fcee3d484dba7a07?recursive=true). **Both hashes re-computed locally** on the downloaded files. `tokens.txt` is not in LFS, so its hash is local only |
| Producer | `onnx.quantize` 0.1.0, IR 7, opset 13. Metadata: `model_author=iic`, `comment=iic/SenseVoiceSmall`, `url=https://huggingface.co/FunAudioLLM/SenseVoiceSmall`, `maintainer=k2-fsa`, `vocab_size=25055` | read from the file's ModelProto |
| Conversion | The repo's `export-onnx.py` ("Copyright 2024 Xiaomi Corp. (authors: Fangjun Kuang)") calls `SenseVoiceSmall.from_pretrained(model="iic/SenseVoiceSmall")`. It exports the CTC graph with `torch.onnx.export`, writes `tokens.txt` from the BPE model, then runs `quantize_dynamic(op_types_to_quantize=["MatMul"], weight_type=QuantType.QUInt8)`. No retraining | https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/blob/2365baeacb507f821a0c8120fcee3d484dba7a07/export-onnx.py |
| Upstream as stated by the export | README: "This directory contains models converted from https://github.com/FunAudioLLM/SenseVoice". The sherpa docs say "converted from https://www.modelscope.cn/models/iic/SenseVoiceSmall using the script export-onnx.py" | https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/blob/2365baeacb507f821a0c8120fcee3d484dba7a07/README.md ; https://k2-fsa.github.io/sherpa/onnx/sense-voice/pretrained.html |
| Who re-hosts it | Fangjun Kuang (`csukuangfj`, k2-fsa / sherpa-onnx maintainer, Xiaomi). The same person supplied our Silero VAD, distil-small.en and Qwen3-ASR packages | commits API above |

## License (weights / code)

- **Weights: FunASR Model Open Source License Agreement, Version 1.1, "Copyright (C) [2023-2028] [Alibaba Group]"**
  (the brackets are in the original).
  - HF card metadata: `license: other`, `license_name: model-license`,
    `license_link: https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE`. The link floats on `main`. The HF
    repo has no licence file. The card prose does not mention the licence.
    https://huggingface.co/FunAudioLLM/SenseVoiceSmall/blob/3847d57b6bdf2dd8875cb1508d2af43d80a16bf7/README.md
  - `MODEL_LICENSE` history in modelscope/FunASR: v1.0 (`75ddde7a`, 2023-11-23) was current when SenseVoice was
    released (2024-07). v1.1 arrived 2024-11-20 (`6a19d111`, then `58830eca`, byte-identical to today's `main`).
    **§6 makes v1.1 the binding text now.** Pin it as
    https://github.com/modelscope/FunASR/blob/58830eca4012644aac0c3218c3ccc7d98f003fda/MODEL_LICENSE (history:
    https://api.github.com/repos/modelscope/FunASR/commits?path=MODEL_LICENSE). The SenseVoice README cites the same
    commit as "the immutable v1.1 model license".
  - **v1.0 → v1.1 changes.** v1.1 adds §4 "Community Conduct Guidelines", with the non-disparagement and forfeiture
    clause. It rewords §2.2 from "should indicate the code and model source and author information" to "must
    attribute the source and author information". The v1.0 text:
    https://github.com/modelscope/FunASR/blob/75ddde7acd49cb6940de339cf7de24297f0826c2/MODEL_LICENSE
  - Decisive quotes from v1.1:
    - §1: "[FunASR Software] refers to FunASR open-source model weights and their derivatives, including finetuned
      models"
    - §2.1: "You are free to use, copy, modify, and share [FunASR Software] under the terms of this agreement."
    - §2.2: "When using, copying, modifying, and sharing [FunASR Software], you must attribute the source and author
      information and retain relevant model names in [FunASR Software]."
    - §3: "[FunASR Software] is provided for reference and learning purposes only, and Alibaba Group assumes no
      responsibility for any direct or indirect losses resulting from your use or modification of [FunASR Software]."
    - §4.2: quoted in the License verdict above.
    - §5: "If you violate any terms of this agreement, your license will automatically terminate, and you must cease
      using, copying, modifying, and sharing [FunASR Software]."
    - §6: quoted in the License verdict above.
    - §7: "This agreement is governed by the laws of [Country/Region]."
  - A Chinese text follows in the same file and matches the English. §3 in Chinese: "[FunASR 软件]仅作为参考和学习使用".
- **ModelScope disagrees.** `iic/SenseVoiceSmall` metadata and card front matter say `license: Apache License 2.0`,
  and the repo has no licence file. https://modelscope.cn/api/v1/models/iic/SenseVoiceSmall . FunASR's own README says
  "when a model card links to the FunASR Model Open Source License Agreement, those terms apply"
  (https://github.com/modelscope/FunASR/blob/main/README.md#license). The HF card links it. The SenseVoice README and
  every maintainer reply say the FunASR agreement governs SenseVoiceSmall. **We follow the HF card.**
- **The commercial-use record, in order:**
  - 2026-05-20, SenseVoice #286 (paid desktop app) and #287 (commercial VRChat tool). Maintainer LauraGPT ("zhifu
    gao", company "alibaba", a FunASR author per the paper's author list): "**Commercial use is permitted**, subject
    to the FunASR Model License — you must attribute the source and retain the model name."
    https://github.com/QwenAudio/SenseVoice/issues/286 , https://github.com/QwenAudio/SenseVoice/issues/287
  - 2026-07-26, SenseVoice #334. The same maintainer: "Section 3 is the responsibility/risk disclaimer; it is not an
    additional non-commercial-use restriction … Commercial use of the official SenseVoiceSmall weights is permitted
    … an About/Licenses page can identify 'SenseVoiceSmall by FunASR/FunAudioLLM' and link the model card and model
    license." https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5083546605
  - 2026-08-29: **reopened.** "My earlier answer should be treated as context, not as a substitute for that
    confirmation." https://github.com/QwenAudio/SenseVoice/issues/334#issuecomment-5463240506 . On 2026-09-22 the
    earlier reply was stamped "This is an earlier response, not final license-owner confirmation."
  - 2026-09-22: PR #357 replaces the README's "Commercial use … is permitted" with "Questions about commercial use …
    remain open". Open, unmerged. https://github.com/QwenAudio/SenseVoice/pull/357 . The README at head
    (`ea152195`) still carries the old wording.
  - FunASR #3715 (2026-09-16) asks about this exact export, revision and set of hashes: v1.1 applicability,
    commercial use, redistribution, conversion and quantization, and whether amendments are retroactive. Only reply:
    "does **not** establish which license version applies … grant redistribution or commercial-use rights … Those
    questions remain open for the relevant license owner." https://github.com/modelscope/FunASR/issues/3715
  - Context: the maintainer's recent replies and PRs look agent-assisted. PR #357 says "Prepared with Codex
    assistance", and replies carry `funasr-ops:` markers. That may explain the answer-then-retract pattern. It does
    not change the fact that the confirmation is outstanding.
  - A related precedent: FunASR #3454 (paraformer-zh-streaming) was resolved on 2026-08-03 by publishing a new
    immutable HF tag, `apache-2.0-20260804`, with an actual `LICENSE` file and byte-identical weights.
    https://github.com/modelscope/FunASR/issues/3454 . SenseVoiceSmall has had no such release. Asking for one on
    #3715 is the cleanest route to a commercial go.
- **Code:** SenseVoice (`QwenAudio/SenseVoice`, formerly `FunAudioLLM/SenseVoice`; GitHub redirects) and FunASR are
  both MIT, "Copyright (c) 2025 FunASR".
  https://github.com/QwenAudio/SenseVoice/blob/ea15219509625e5d4c5143c37c86970135886b5d/LICENSE . The README says "Model
  weights are distributed separately and follow the terms on each model card." Not shipped by us.
- **The k2-fsa export:** `LICENSE` (71 B) says "Ref to https://github.com/modelscope/FunASR?tab=readme-ov-file#license".
  In 2024-07 that section read "The use of pretraining model is subject to [model license](./MODEL_LICENSE)"
  (https://github.com/modelscope/FunASR/blob/bd352983c6392c32206d50ca0f5c55cb6e02e4c6/README.md#license). The repo
  has no licence tag (tags: `onnx`, `region:us`). **No added terms.** `export-onnx.py` carries a Xiaomi copyright and
  no licence. It is build tooling and is not downloaded by the app.
- **GPL/LGPL: none** in the weights, the tokens file or the export path.

## Provenance (training data)

### SenseVoice-Small pretraining. **OPAQUE (scale only)**

- FunAudioLLM tech report §3.1: "The SenseVoice-Small model was trained on an extensive audio data corpus of
  approximately 300,000 hours, covering 5 languages including Chinese, Cantonese, English, Japanese, and Korean. To
  further enhance the multilingual ability of SenseVoice-Large, an additional 100,000 hours of diverse multilingual
  data were integrated." Figure 6 gives hours per language. **No corpus, source or collection method is named.**
  https://arxiv.org/abs/2407.04051 (v3, 2024-07-11; HTML: https://arxiv.org/html/2407.04051)
- The card says "Trained with over 400,000 hours of data, supporting more than 50 languages". That is the Large
  figure. The GitHub README now separates the two: "The broader SenseVoice work reports training on more than
  400,000 hours … The released SenseVoiceSmall checkpoint … supports Mandarin, Cantonese, English, Japanese, and
  Korean." https://github.com/QwenAudio/SenseVoice/blob/ea15219509625e5d4c5143c37c86970135886b5d/README.md
- **The rich-transcription labels are pseudo-labels.** "We leveraged open-source models for audio event detection
  (AED) [PANNs, `qiuqiangkong/audioset_tagging_cnn`] and speech emotion recognition (SER) [`iic/emotion2vec_plus_large`]
  to generate pseudo labels … the AED data amounted to 150 million entries, while the SER data comprised 30 million
  entries." (§3.1). The training data of those two teacher models was not checked.
- The paper has no ethics or data-rights section. Its "Limitations" section (§6) covers only accuracy and streaming.

### Circumstantial evidence: the example training manifest

`data/train_example.jsonl` has been in the repo since launch (`4264e5dc52`, 2024-07-04). It is **10 rows copied
from the team's internal training lists**, with internal paths such as
`/cpfs01/shared/Group-speech/…/industrial_data/english_all/audio/…`.
https://github.com/QwenAudio/SenseVoice/blob/ea15219509625e5d4c5143c37c86970135886b5d/data/train_example.jsonl

- `YOU0000008470_S0000238`, `POD0000007250_S0000518` and `AUD0000001556_S0007580` follow **GigaSpeech's segment-ID
  format** exactly (source code YOU/POD/AUD, 10-digit audio ID, `_S` plus 7-digit segment;
  https://github.com/SpeechColab/GigaSpeech#readme). GigaSpeech was crawled from public YouTube, podcasts and
  audiobooks. Its access terms say "Researcher shall use the Database only for non-commercial research and
  educational purposes" and "SpeechColab does not own the copyright of the audio files"
  (https://huggingface.co/datasets/speechcolab/gigaspeech). **UNVERIFIED** that GigaSpeech is in the released
  checkpoint's training set. The ID match is strong but not proof.
- `19208207_HJwKrcFJ8_o_segment720` and `18874657_MSnA4nfDC7Q_segment680` sit under
  `lcd_data/english/production/<date>/wer_0_5/taskid_…`. The middle part has the shape of an 11-character YouTube
  video ID, and the path suggests a labelling-vendor pipeline. One transcript is NBA broadcast commentary ("fourth
  foul up top now to austin three for leonard …").
- Other rows: a Chinese court-judgment reading ("案件受理费减半收取…"), `Speaker0244_iPhone_s0_227` (a Korean
  smartphone-recorded speaker corpus), and `SPEAKER0272_SESSION1` / `data2sim_speed_…` rows in the style of
  collected read-speech corpora, including speed augmentation.

That is consistent with public web audio (YouTube, podcasts), a public research corpus and bought or collected
corpora. None of it suggests paywalled or pirated sources. Under bar #3, public-web and collected data is
acceptable. The NC terms on GigaSpeech are a copyright technicality the bar says not to judge. They are still worth
knowing if Alibaba is ever asked for a warranty.

### Org-level disclosure

Alibaba Cloud's "Qwen and Wan Training data summary" (last updated 2026-09-02) lists "Publicly available internet
data", "Third-party partner data -- non-public data provided by partners under agreement", "Labeled and contracted
data" and "Synthetic data", and adds "Datasets may include copyrighted … content". **It is scoped to Qwen and Wan,
not to SenseVoice.** https://www.alibabacloud.com/help/en/model-studio/qwen-and-wan-training-data-disclosure

### Community questions

- **SenseVoice #186 "数据集开放吗？"** (2025-02-15): "官方的训练数据集有没有可能开放？" (Could the official training
  dataset be opened?). No reply. The maintainer closed it 2026-05-19 as `not_planned`.
  https://github.com/QwenAudio/SenseVoice/issues/186 . A direct question closed without an answer is the skill's
  "itself evidence" case. It asked about release, though, not sources.
- Other searches found only fine-tuning threads: "training data", "dataset", "训练数据", "数据集", "data source",
  "copyright", "gigaspeech", "wenetspeech" (e.g. https://api.github.com/search/issues?q=repo:QwenAudio/SenseVoice+training+data).
- FunASR #3458 and #3715 ask the publisher to state "any obligations … arising from the training data". Both are
  open, and the maintainer says that part "需要 `iic` / 权重发布方确认" (needs the publisher to confirm).
  https://github.com/modelscope/FunASR/issues/3458
- None of the 17 HF discussions is about provenance.
  https://huggingface.co/api/models/FunAudioLLM/SenseVoiceSmall/discussions

**Overall provenance grade: OPAQUE.** Scale is disclosed, sources are not, and a direct question was closed
unanswered. The manifest evidence leans towards public and collected sources.

**Piracy check (bar #1):** no evidence of pirated or paywalled-scraped content found. **UNVERIFIED** either way.

## Posture: **Structurally N/A for impersonation. Emotion recognition is a minor note.**

- **Output is text plus tags.** Each result starts with `<|lang|><|EMOTION|><|Event|><|withitn|>`. The emotion tags
  are HAPPY, SAD, ANGRY, NEUTRAL, FEARFUL, DISGUSTED, SURPRISED and EMO_UNKNOWN (GitHub README "Data prepare"). The
  event tags are BGM, Speech, Applause, Laughter, Cry, Sneeze, Breath and Cough (same section). sherpa-onnx returns them as separate
  `lang`, `emotion` and `event` fields. The model cannot generate or clone a voice.
- **Emotion recognition on the user's own voice, on-device, never stored, does not raise a real concern.** No third
  party sees the inference. Two cautions:
  - Emotion inference is a surveillance-adjacent capability. The org markets it with no consent or misuse language
    on any surface we checked (card, README, paper, demo page).
  - The EU AI Act Art. 5(1)(f) prohibits "the placing on the market, the putting into service for this specific
    purpose, or the use of AI systems to infer emotions of a natural person in the areas of workplace and education
    institutions" (https://artificialintelligenceact.eu/article/5/). A dictation app is not placed on the market for
    that purpose. But a user-visible emotion readout invites that use.
  - **Recommendation:** the engine adapter should keep only the text, discard the emotion and event fields, and never
    log them. This is also the simplest UX. The language tag is harmless and useful.
- **No consent or misuse language anywhere,** as with Qwen3-ASR. Whisper's card at least cautions against
  non-consensual transcription.

## Org track record

- **Alibaba Group (Tongyi Lab speech team; HF `FunAudioLLM`, ModelScope `iic`, GitHub now `QwenAudio`): grey.**
  - The GitHub org is renamed. `github.com/FunAudioLLM` returns 404, the API redirects `FunAudioLLM/SenseVoice` to
    `QwenAudio/SenseVoice`, and the org describes itself as "Open-source speech and audio language models from the
    QwenAudio Team". https://github.com/QwenAudio
  - **Alibaba's FY2026 Form 20-F** (re-verified this review): "The training data used for our large language models
    and AI systems may also include content that is subject to copyright … We have faced, and may face additional
    claims from content owners alleging unauthorized use of their materials for AI training."
    https://www.sec.gov/Archives/edgar/data/0001577552/000119312526231755/baba-20260331.htm . No specific suit found
    (see the Qwen3-ASR review).
  - **CosyVoice (same team, same tech report): cloning as a headline feature.** The report and demo page: CosyVoice
    "excels in multi-lingual voice generation, zero-shot voice generation, cross-lingual voice cloning". The report
    adds "CosyVoice also exhibited greater speaker similarity than the original utterances, showcasing its effective
    voice-cloning proficiency" (https://arxiv.org/html/2407.04051 §4.5; https://fun-audio-llm.github.io/). The
    current README lists "zero-shot voice cloning" as a core feature (https://github.com/QwenAudio/CosyVoice).
  - **CosyVoice 3's data:** "we collect in-the-wild multilingual audio data mainly from Internet audiobooks, videos,
    and podcasts" (https://arxiv.org/html/2505.17589 §3). Whether the "Internet audiobooks" were free or commercial is
    **UNVERIFIED**.
  - **CosyVoice tag vs prose (the pitfall this skill lists):** `FunAudioLLM/Fun-CosyVoice3-0.5B-2512` is tagged
    `apache-2.0`, but its card's Disclaimer says "The content provided above is for academic purposes only … Some
    examples are sourced from the internet. If any content infringes on your rights, please contact us to request
    its removal." https://huggingface.co/FunAudioLLM/Fun-CosyVoice3-0.5B-2512 . The same disclaimer is on the demo
    page. **Whose voices the zero-shot demo prompts clone is UNVERIFIED.** No real-person or celebrity clone was
    identified in this pass.
  - Qwen3-TTS ships "3-second rapid voice clone" with no consent language. That is per the Qwen3-ASR review
    (`docs/model-reviews/qwen3-asr-0.6b.md`) and was not re-checked here.
  - **Licensing churn**, detailed above: three "commercial use is permitted" answers, then a walk-back, with no
    license-owner confirmation. The team does engage with licence questions, and it cut an immutable Apache-2.0
    revision for paraformer-zh-streaming when asked (FunASR #3454).
  - In its favour: a detailed tech report on architecture and scale, open fine-tuning code, and a public
    SER-benchmark contract. SenseVoiceSmall has had no retraction or weight change since 2024-07-04.
- **k2-fsa / Fangjun Kuang (exporter and re-host): no concerns.** A plain conversion from the official ModelScope
  checkpoint, with a published script and embedded provenance metadata.
- **ASLP-lab, NWPU (the 2025-09-09 fine-tune): a data-licence concern, not a conduct concern.** See below.

## Feasibility (secondary)

- About 234M parameters (per ASLP's leaderboard: https://huggingface.co/ASLP-lab/WSYue-ASR). Encoder-only,
  non-autoregressive CTC. No decoder loop, so no hallucination loops. The **240 MB** int8 download is about a
  quarter of Qwen3-ASR's.
- Five languages (zh, yue, en, ja, ko) with automatic language ID. Punctuation and ITN come via `use_itn=1` ("This
  model supports punctuations if you set use_itn to 1", sherpa docs). **Offline only**: "SenseVoice is not designed
  for streaming transcription" (paper §6).
- English: the paper says SenseVoice-S beats Whisper "in most test sets except Librispeech". Our English WER, speed,
  RAM and cold load on the Pixel 8a are **unmeasured**. That is what #29's benchmark is for.

## Working demo link

- https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition (RUNNING when checked). Its `model.py` loads
  **this exact repo** (`csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17`, `model.int8.onnx`,
  `use_itn=True`). Pick the Chinese+English+Cantonese+Japanese+Korean language group, then that repo.
- Official: https://huggingface.co/spaces/FunAudioLLM/SenseVoice (RUNNING). It runs the PyTorch checkpoint on HF
  hardware, so uploads leave the device. Fine for Max listening with test clips.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **Custom, not NC on its face, commercial use UNCONFIRMED** | FunASR Model License v1.1 (pinned `58830eca`). Permissive grant with an attribution rider. §4.2 non-disparagement forfeiture, §6 automatic amendment, §3 "reference and learning purposes only". The maintainer's "commercial OK" answers were withdrawn pending license-owner confirmation (SenseVoice #334, FunASR #3715 open). ModelScope's Apache tag conflicts |
| License (export) | **No added terms** | The k2-fsa `LICENSE` points at FunASR's licence section. A plain conversion from `iic/SenseVoiceSmall` |
| Provenance | **OPAQUE** | Scale only (~300k h for Small). No named corpus. #186 closed unanswered. The example manifest suggests GigaSpeech-style public web data, vendor-labelled YouTube audio and collected corpora. No sign of paywalled data |
| Posture | **N/A for impersonation. Emotion tags: minor note** | Text plus tags only. Drop the emotion and event fields in the adapter. No consent or misuse language anywhere |
| Org track record | **Alibaba grey** | 20-F admits AI-training copyright claims. The same team's CosyVoice headlines zero-shot cloning on web-collected audio, with an "academic purposes only" disclaimer over an Apache tag. Licence-answer churn |
| **Verdict vs Max's bar** | **PASS (grey)** | Bar #1: no evidence of piracy, sources UNVERIFIED. Bar #2: N/A. Bar #3: undisclosed but plausibly public, a minor demerit. **Fine as a developer or benchmark engine. The licence, not the ethics, gates a commercial release** |

## Download sources checked

| Repo | Revision | Files (bytes, sha256) | Converted from | Use? |
|---|---|---|---|---|
| `csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` | `2365baeacb507f821a0c8120fcee3d484dba7a07` (2024-07-18, head) | `model.int8.onnx` 239,233,841, `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51`; `tokens.txt` 315,894, `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc`. Also present, not needed: `model.onnx` (fp32) 937,617,178, `977016bd9c79f9eb343430b5cc305e07ab64d5212dff41b0dcfa1694bee9a8cb`; `test_wavs/` (5 files); `export-onnx.py`; `LICENSE`; `README.md` | Official `iic/SenseVoiceSmall` (ModelScope), the same `model.pt` as HF `FunAudioLLM/SenseVoiceSmall` @ `3847d57b` (sha256 `833ca2dc…a3ea`). ONNX metadata `comment=iic/SenseVoiceSmall` | **Yes. Recommended.** Both hashes verified locally |
| `csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09` | `355f4d4884d8afd08aef04b9007a8556d7b463b2` (2025-09-09, head) | `model.int8.onnx` 237,115,547, `12ca1a2ae7ecf3e0019ef2822307ee0b5cadc9196569e379b4c4026f8205276d` (verified locally); `tokens.txt` 315,894, the **same sha256** as the 2024 export; `test_wavs/` (23 files) | **Different weights:** ASLP-lab's Cantonese fine-tune. README: "converted from https://huggingface.co/ASLP-lab/WSYue-ASR/tree/main/sensevoice_small_yue". ONNX metadata `model_author=ASLP-lab`, `comment=ASLP-lab/WSYue-ASR`. The sherpa docs: "fine-tuned on [the 2024-07-17 model] with 21.8k hours of Cantonese data" and "This model does not support punctuations" | **No.** Loses punctuation. The licence is muddled (below) |
| `csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2025-09-09` | `c7c66d1179dc6600570f122e4d70c4e6c3dfa3c8` | `model.onnx` (fp32) 929,211,046, `97a50aa118af9032ae976da66549bcc51a5b157f50be28ee466e363f549749a8` | The same ASLP-lab fine-tune, fp32 | No |
| `ASLP-lab/WSYue-ASR` (source of the 2025 exports) | `4a74a91430485d7456cb786f1d0ff0aba1185ded` (head) | `sensevoice_small_yue/model.pt` 2,809,073,798, `38940ad5ae8a580efef0069f6a9c38ac67656dd8f046af9bd8d162cea3448706` | Fine-tuned from SenseVoiceSmall. Tagged `apache-2.0`, card titled "WenetSpeech-Yue". Its training corpus `ASLP-lab/WenetSpeech-Yue` is tagged **`cc-by-nc-4.0`**, with audio fetched from original video links. The card's example segment is a 2002 RTHK radio drama (https://huggingface.co/datasets/ASLP-lab/WenetSpeech-Yue ; https://arxiv.org/abs/2509.03959). The Apache tag cannot lift FunASR §1 ("derivatives, including finetuned models"), and the NC dataset licence raises the skill's inheritance question | No |
| Other re-uploads (`chris-cao/…-int8-2024-07-17`, `GTR007/…-int8-2025-09-09`, `LunarOilRig/…`, `TinyQ/…`, `HatiSkoll28/…` and others) | not pinned | not checked | Third-party mirrors of the above | No. Use the maintainer's repo |
| `FunAudioLLM/SenseVoiceSmall-GGUF` | not checked | not checked | Official llama.cpp format | No. Different runtime from the app's ORT |

Also listed but out of scope: `csukuangfj/sherpa-onnx-sense-voice-funasr-nano-*-2025-12-17` (Fun-ASR-Nano, a
different LLM-based model) and the `sherpa-mlx-*` and `sherpa-ncnn-*` variants.

## NOTICE entry

**Copyright line.** Verbatim from `MODEL_LICENSE` v1.1 at
https://github.com/modelscope/FunASR/blob/58830eca4012644aac0c3218c3ccc7d98f003fda/MODEL_LICENSE (the square brackets
are in the original):

```
Copyright (C) [2023-2028] [Alibaba Group]. All rights reserved.
```

**What §2.2 requires (verbatim):** "When using, copying, modifying, and sharing [FunASR Software], you must attribute
the source and author information and retain relevant model names in [FunASR Software]." The maintainer's suggested
form, later withdrawn as "not final" but a reasonable reading of §2.2, was "SenseVoiceSmall by FunASR/FunAudioLLM" with
links to the model card and the model licence (SenseVoice #334).

**Draft entry for `NOTICE.md` → "Downloadable speech models", and for the in-app licenses screen:**

```
### SenseVoiceSmall (int8, k2-fsa export)

> The SenseVoice engine is SenseVoiceSmall by FunASR / FunAudioLLM (Tongyi Speech
> Team, Alibaba Group): https://huggingface.co/FunAudioLLM/SenseVoiceSmall
> (also https://www.modelscope.cn/models/iic/SenseVoiceSmall), distributed under
> the FunASR Model Open Source License Agreement, Version 1.1
> (https://github.com/modelscope/FunASR/blob/58830eca4012644aac0c3218c3ccc7d98f003fda/MODEL_LICENSE).
> Copyright (C) [2023-2028] [Alibaba Group]. All rights reserved.
>
> This copy is the ONNX conversion with int8 (QUInt8) dynamic quantization of the
> MatMul weights, published by the k2-fsa / sherpa-onnx project at
> https://huggingface.co/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17,
> commit `2365baeacb507f821a0c8120fcee3d484dba7a07`, converted from
> iic/SenseVoiceSmall without retraining:
>
> | File | Bytes | SHA-256 |
> |---|---|---|
> | `model.int8.onnx` | 239,233,841 | `c71f0ce00bec95b07744e116345e33d8cbbe08cef896382cf907bf4b51a2cd51` |
> | `tokens.txt` | 315,894 | `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc` |
>
> The model runs entirely on-device and returns only the transcript; its emotion
> and audio-event tags are discarded.
```

**License text:** bundle the full v1.1 text (English and Chinese) from the pinned commit above as
`app/src/main/assets/licenses/FunASR-Model-License-1.1.txt`. It is not an SPDX licence, so
`fetch_license_text.py` cannot supply it. Copy it from the pinned file. The review is in
`docs/model-reviews/sensevoice-small.md`.
