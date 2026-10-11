# Model Ethics Review: Qwen3-ASR 0.6B (int8 ONNX, re-hosted by k2-fsa)

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the six files the Engines screen downloads. The model is a **developer-only** engine, hidden behind the
Developer-engines setting so Max can benchmark it. It is used only to transcribe the user's own dictation on-device.
No generation, no cloning, no speaker identification.

## Decision (Max, 2026-10-02)

**Approved as a user-facing engine for Chinese and Korean** ("Yes, zh + ko"), after the grey provenance (≈40 M hours, sources undisclosed) and the unproven distillation allegation against Alibaba were put to him. `SttEngineCatalog.QWEN3_FOR_USERS` is on. It stays a developer engine for its other languages.

## Verdict first

**PASS against Max's bar, but greyer than distil-small.en. Fine as a developer-only benchmark engine. Review it again
before it becomes user-facing.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped training data.** It also cannot be ruled out.
  **The training data is undisclosed.** Qwen names no speech corpus for any training stage. It gives only scale
  ("approximately 40 million hours of pseudo-labeled ASR data"). The Qwen3 text base it is built on is described only
  as "books", web-style content, OCR'd "PDF-like documents" and synthetic data, again with no named sources.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: Apache-2.0**, "Copyright 2026 Alibaba Cloud". The tag, the paper and the GitHub LICENSE agree. There is
  **no NOTICE file** anywhere upstream, so Apache §4(d) adds nothing for us to carry. No GPL or LGPL anywhere.

**Caveats. Read these before calling this model clean:**

1. **Provenance is OPAQUE, one step below distil-small.en's PARTIAL.** distil-small.en at least names its nine
   distillation corpora. Qwen3-ASR names none. Alibaba's org-wide disclosure lists only categories: "publicly available
   internet data", "non-public data provided by partners under agreement", contracted labelling and synthetic data. It
   adds that "Datasets may include copyrighted … content". Nobody has asked Qwen about the data on GitHub or Hugging
   Face, so there is no dodged question, but there is no answer either.
2. **The export is not k2-fsa's own.** The Hugging Face repo belongs to Fangjun Kuang (k2-fsa), but its README says the
   files were **downloaded from a third party's ModelScope upload** (GitHub user Wasser1462, ModelScope user
   zengshuishui). I verified all six files **byte-identical** to that upload. It is a plain PyTorch → ONNX export with
   ONNX Runtime int8 quantization, and it adds no terms. See Artifact.
3. **Org track record has two demerits.** Alibaba's own SEC filing says it has "faced … claims from content owners
   alleging unauthorized use of their materials for AI training" (no specific suit was found). The Qwen audio line
   ships **3-second voice cloning** (Qwen3-TTS) with no consent or misuse language on its card. Neither concerns this
   model's weights. Both count against the org.
4. **No misuse language at all.** Unlike OpenAI's Whisper card, the Qwen3-ASR card and paper say nothing about consent
   or surveillance.

Blocking issues: **none for developer-only use.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25 at commit `68818b2313fe77bd06f6a7c5068ff3ef59d02b8a` ("add models", 2026-04-07; the repo head as of this review) | HF API: https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/revision/68818b2313fe77bd06f6a7c5068ff3ef59d02b8a and https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/commits/main |
| Files | `conv_frontend.onnx` 44,148,281 B, sha256 `d22dc442…8ef9e`; `encoder.int8.onnx` 182,491,662 B, `60748d3e…a9d9`; `decoder.int8.onnx` 755,914,231 B, `4f6885be…5771`; `tokenizer/merges.txt` 1,671,853 B, `8831e4f1…04d5`; `tokenizer/tokenizer_config.json` 12,487 B, `4942d005…c26c4c`; `tokenizer/vocab.json` 2,776,833 B, `ca10d7e9…0910`. Total 987,015,347 B | HF tree API with LFS oids: https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/tree/68818b2313fe77bd06f6a7c5068ff3ef59d02b8a?recursive=true ; local `sha256sum` of the downloaded `conv_frontend.onnx`, `encoder.int8.onnx` and the three tokenizer files |
| Producer | `conv_frontend.onnx`: `pytorch` 2.10.0, IR 8, opset 17 (fp32). `encoder.int8.onnx`: `onnx.quantize` 0.1.0, IR 9, metadata `onnx.infer=onnxruntime.quant` | read from both files' ModelProto |
| Upstream checkpoint | https://huggingface.co/Qwen/Qwen3-ASR-0.6B at `5eb144179a02acc5e5ba31e748d22b0cf3e303b0` (created 2026-01-28). 938,008,576 BF16 parameters. The "0.6B" is the Qwen3-0.6B LLM decoder. The paper says the model "is built with Qwen3-0.6B, a projector and an AuT encoder with 180M parameters" | HF API: https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B ; paper §2.1: https://arxiv.org/abs/2601.21337 |
| Who re-hosts it | HF user `csukuangfj2`, full name "fangjun", member of the `k2-fsa` org, created 2026-01-15. The two "add models" commits are authored by `csukuangfj` (Fangjun Kuang's main account, also "fangjun" and in `k2-fsa`). So it is the same person | https://huggingface.co/api/users/csukuangfj2/overview , https://huggingface.co/api/users/csukuangfj/overview , commits API above |
| Who exported it | The repo README: "Models in this directory are downloaded from https://modelscope.cn/models/zengshuishui/Qwen3-ASR-onnx/files … Export script can be found at https://github.com/Wasser1462/Qwen3-ASR-onnx … The author is https://github.com/Wasser1462". Wasser1462 also contributes to sherpa-onnx (hotwords support for Qwen3-ASR, #3434) | https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/blob/68818b2313fe77bd06f6a7c5068ff3ef59d02b8a/README.md ; https://github.com/k2-fsa/sherpa-onnx/commit/36a29236f9 |
| Byte-identity with the source upload | The ModelScope file list for `model_0.6B/` and `tokenizer/` gives the **same size and SHA-256 for all six files**. The ModelScope repo is tagged `License: apache-2.0`, `BaseModel: Qwen/Qwen3-ASR-0.6B`, `BaseModelRelation: quantized` | https://modelscope.cn/api/v1/models/zengshuishui/Qwen3-ASR-onnx/repo/files?Recursive=true and https://modelscope.cn/api/v1/models/zengshuishui/Qwen3-ASR-onnx |
| Conversion | `export_qwen3_asr_onnx.py` loads the checkpoint with Transformers, exports three graphs (conv frontend, encoder, decoder) with `torch.onnx.export`, then runs ONNX Runtime `quantize_dynamic(..., weight_type=QuantType.QInt8)`. Its README: "A large portion of the code is from https://github.com/QwenLM/Qwen3-ASR" | https://github.com/Wasser1462/Qwen3-ASR-onnx/blob/main/export_qwen3_asr_onnx.py , https://github.com/Wasser1462/Qwen3-ASR-onnx/blob/main/README.md |

**What the export is.** A mechanical format conversion plus int8 dynamic weight quantization of the published
checkpoint. There is no retraining. The csukuangfj2 repo has **no license file and no license tag** (tags: `onnx`,
`region:us`). The ModelScope source says Apache-2.0. Neither adds terms of its own, so Qwen's Apache-2.0 governs. The
export script's GitHub repo has **no license** (the API reports `license: null`), but the script does not ship with the
model, so that does not reach us. sherpa-onnx itself points its examples at this exact package: its release archive
`sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25.tar.bz2` (878,702,423 B, the files plus test clips) and its
`run-qwen3-asr.sh` examples (https://github.com/k2-fsa/sherpa-onnx/blob/master/rust-api-examples/run-qwen3-asr.sh).

**The tokenizer files are Qwen's, unchanged.** `merges.txt`, `vocab.json` and `tokenizer_config.json` are
byte-identical (same SHA-256) to the files in `Qwen/Qwen3-ASR-0.6B` at `5eb14417`. `merges.txt` and `vocab.json` are
also byte-identical to the Qwen3-0.6B LLM's (`Qwen/Qwen3-0.6B` at `c1899de2`, Apache-2.0, LICENSE "Copyright 2024
Alibaba Cloud": https://huggingface.co/Qwen/Qwen3-0.6B/blob/main/LICENSE). So the tokenizer is the Qwen3 LLM's
byte-level BPE, under Apache-2.0 from both sources.

## License (weights / code)

- **Qwen3-ASR: Apache-2.0, "Copyright 2026 Alibaba Cloud".**
  - HF card metadata: `license: apache-2.0` (a real license tag, unlike Qwen3-Omni's `license: other`). The HF model
    repo contains **no LICENSE and no NOTICE file** (file list in the HF API response above). The card prose does not
    restate the license and adds no riders.
  - The tech report abstract says: "we release these models under the Apache 2.0 license."
    https://arxiv.org/abs/2601.21337
  - GitHub `QwenLM/Qwen3-ASR` LICENSE is the Apache-2.0 body with the appendix filled in as "Copyright 2026 Alibaba
    Cloud". The file has one commit ("Initial commit", 2026-01-29, `9567667698`) and has never changed.
    https://github.com/QwenLM/Qwen3-ASR/blob/9567667698f195fa807b1581de5c03184e63d2b0/LICENSE and
    https://api.github.com/repos/QwenLM/Qwen3-ASR/commits?path=LICENSE
  - The tag, the paper and the LICENSE agree. That settles the license. No prose rider, no acceptable-use policy, no
    gating.
- **NOTICE file: none.** Apache §4(d) only requires passing through a NOTICE file "if the Work includes" one. I checked
  the HF repo, the ModelScope mirror (https://modelscope.cn/api/v1/models/Qwen/Qwen3-ASR-0.6B/repo/files?Recursive=true)
  and the GitHub tree (`LICENSE` is the only license or notice file: https://api.github.com/repos/QwenLM/Qwen3-ASR/git/trees/main?recursive=1).
  None has one. We owe the license text (§4(a), already bundled as the shared `Apache-2.0.txt`) and a statement that
  the files were modified by conversion and quantization (§4(b); our NOTICE entry says so).
- **Qwen3-0.6B (LLM base and tokenizer): Apache-2.0, "Copyright 2024 Alibaba Cloud".** Same license, same holder.
- **GPL/LGPL: none** in the weights, the tokenizer or the export path.

## Provenance (training data)

The model has four layers. The paper describes the training stages, but not what data went into them.

### Layer 1: AuT audio encoder pretraining. **OPAQUE (scale only)**

- Qwen3-ASR paper §2.2: "We leverage approximately 40 million hours of pseudo-labeled ASR data, where the majority is in
  Chinese and English." The paper says this stage is "identical to" Qwen3-Omni's. https://arxiv.org/abs/2601.21337
- The Qwen3-Omni report describes the same encoder as "trained from scratch on 20 million hours of supervised audio".
  https://arxiv.org/abs/2509.17765 (§2.2). **The two reports disagree on scale (40M vs 20M hours).** Neither names a
  source, a corpus or the model that made the pseudo-labels.

### Layer 2: Qwen3-0.6B LLM (decoder initialisation) and Omni pretraining. **OPAQUE (categories only)**

- The Qwen3 report: "approximately 36 trillion tokens … covering up to 119 languages and dialects", including "books,
  multilingual texts, and synthetic data", plus text OCR'd by Qwen2.5-VL from "a large volume of PDF-like documents".
  No source is named. https://arxiv.org/abs/2505.09388 (§3.1)
- Qwen3-ASR's Omni-pretraining stage: "trained with 3 trillion tokens" of "multi-task audio, vision, and text data".
  No source is named. https://arxiv.org/abs/2601.21337 (§2.2)
- **Whether the "books" and "PDF-like documents" came from licensed, public or pirated sources is UNVERIFIED.** No
  report, filing or press item found says Qwen used shadow libraries. None rules it out either.

### Layer 3: ASR fine-tuning and RL. **OPAQUE**

- SFT uses "a substantially smaller set of multilingual data that is disjoint from the pretraining corpus", plus
  "non-speech data, streaming-enhancement data and context biasing data". RL uses "about 50k utterances". No sources
  named. https://arxiv.org/abs/2601.21337 (§2.2)

### Layer 4: ONNX export. **N/A (no data)**

### Org-level disclosure

Alibaba Cloud's "Qwen and Wan Training data summary" (last updated 2026-09-02) says: "Training data includes four
categories: Publicly available internet data -- web content accessible to the general public. Third-party partner data
-- non-public data provided by partners under agreement. Labeled and contracted data -- data from data-labeling
services and paid contractors. Synthetic data … Datasets may include copyrighted, trademarked, or patented content, as
well as public domain content." It does not mention robots.txt, paywalls or audio.
https://www.alibabacloud.com/help/en/model-studio/qwen-and-wan-training-data-disclosure

Read at face value, that is public-web scraping plus licensed partner data, which Max's bar accepts. But it is written
for the whole Qwen and Wan family, not for this model.

### Community questions

Nobody has asked. The GitHub issue searches for "training data", "dataset", "data source", "license" and "copyright"
return only usage and fine-tuning threads
(e.g. https://api.github.com/search/issues?q=repo:QwenLM/Qwen3-ASR+training+data). None of the 14 HF discussions is
about provenance (https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B/discussions). So opacity here is silence, not
a dodged question.

### Circumstantial evidence

The demo clips from Qwen's launch blog, which the k2-fsa repo also carries as `test_wavs/`, include rap lyrics, a
Chinese pop song, what reads as Formula 1 team radio, and a scripted comedy skit (per their transcripts; the recordings'
origins are not stated)
(https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/blob/68818b2313fe77bd06f6a7c5068ff3ef59d02b8a/test_wavs/transcript.txt ;
the test_wavs README says "All audio files are from https://qwen.ai/blog?id=qwen3asr"). The card advertises "Singing
Voice, Songs with BGM" support. That suggests the training data includes music and broadcast-style audio. Whether
it was licensed is **UNVERIFIED**. These clips are demo inputs to a transcriber, not mimicry, and **the app does not
download `test_wavs/`**.

**Overall provenance grade: OPAQUE.** Scale and categories are disclosed, but the skill's rule applies: "Scale
disclosed, sourcing not … is NOT disclosure." Alibaba's generic web-sourcing admission keeps it from being pure silence.

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled-scraped content found. Alibaba's summary names
"non-public" data, but "provided by partners under agreement", which is licensed rather than scraped. The book and PDF
sources behind the Qwen3 base are **UNVERIFIED**.

## Posture: **Structurally N/A for impersonation. Surveillance dual-use not addressed at all.**

- ASR maps audio to text. It cannot generate, clone or imitate a voice, so Max's bar #2 cannot apply. The paper says
  the model was trained "to be an ASR-only model that does not follow natural-language instructions in the prompt, in
  order to mitigate instruction injection" (§2.2). That matters for an LLM-style decoder: it transcribes rather than
  answers.
- **No consent or misuse language anywhere.** The card and the paper have no statement on transcribing people without
  consent, and no prohibited uses. That is weaker than Whisper, whose card cautions against it.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device, and audio is never stored
  or sent (a hard rule of this project). Developer-only exposure narrows it further.

## Org track record

- **Alibaba Cloud / Qwen team: grey.**
  - Alibaba's FY2026 Form 20-F: "The training data used for our large language models and AI systems may also include
    content that is subject to copyright … We have faced, and may face additional claims from content owners alleging
    unauthorized use of their materials for AI training." No specific named suit against Alibaba over training data was
    found in a web search, so which claims these are is **UNVERIFIED**.
    https://www.sec.gov/Archives/edgar/data/0001577552/000119312526231755/baba-20260331.htm
  - **Cloning as a product.** Qwen3-TTS Base: "capable of 3-second rapid voice clone from user audio input", with a
    `generate_voice_clone` API and no consent or misuse language on the card.
    https://huggingface.co/Qwen/Qwen3-TTS-12Hz-1.7B-Base . The earlier Qwen3-Omni sweep reached the same conclusion
    (the maintainer's note `qwen3-omni.md`: "the org trajectory is cloning-as-product"). This review did not check
    whose voice the TTS demos clone. It does not touch this model, which cannot clone, but it is an org-level demerit
    under Max's framework.
  - Positive: weights under a plain OSI license with the license file in the code repo; a detailed tech report on
    architecture and training stages; an open fine-tuning recipe. No retractions or relicensing of Qwen3-ASR found.
- **Wasser1462 / zengshuishui (exporter):** individual contributor, also a sherpa-onnx contributor. No concerns. The
  ModelScope README says the export is "intended for reference only".
- **k2-fsa / Fangjun Kuang (re-host):** distributor only. No concerns. The same maintainer supplied our Silero VAD and
  distil-small.en conversions.

## Feasibility (secondary)

- About 0.94B parameters and a **987 MB download** (the 878 MB figure in the maintainer's scan
  `hf-asr-trending-2026-09.md` is the compressed release archive). 30 languages plus 22 Chinese dialects,
  automatic language ID, punctuation. Offline only in sherpa-onnx; Qwen's streaming mode needs the vLLM backend.
- An LLM-style autoregressive decoder, so it is slower and can hallucinate. The silence-hallucination fix
  (https://github.com/k2-fsa/sherpa-onnx/pull/3907, merged 2026-08-31) is in sherpa-onnx 1.13.7 and so in our pinned
  1.13.8 (`third_party/sherpa-onnx/CHANGELOG.md`).
- Speed, RAM and cold load on the Pixel 8a are **unmeasured**. That is what the developer-only engine is for.

## Working demo link

https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition (RUNNING when checked). Its `model.py` loads **this
exact repo** (`csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25`) through `sherpa_onnx.OfflineRecognizer.from_qwen3_asr`.
Pick the Qwen3-ASR entry and upload a clip.

Not equivalent: `Qwen/Qwen3-ASR` runs the 1.7B model, and `Qwen/Qwen3-ASR-Demo` calls Alibaba's hosted
`qwen3-asr-flash` API, which sends audio to Alibaba Cloud.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **Apache-2.0, pass** | HF tag, tech report and GitHub LICENSE ("Copyright 2026 Alibaba Cloud") agree. No NOTICE file upstream. Export adds no terms |
| Provenance | **OPAQUE** | Scale only (40M h pseudo-labeled audio, 36T-token text base, 3T multimodal tokens). No named corpus at any stage. Org-level summary admits public web plus licensed partner data. Nobody has asked |
| Posture | **N/A for impersonation. Surveillance not addressed** | ASR outputs text only and is trained not to follow prompt instructions. No consent or misuse language on any surface |
| Org track record | **Alibaba/Qwen grey. Exporter and re-host neutral** | 20-F admits AI-training copyright claims. Qwen3-TTS ships 3-second cloning without consent language |
| **Verdict vs Max's bar** | **PASS (grey: opaque provenance and org demerits)** | Bar #1: no evidence of piracy; the book, PDF and music sources are UNVERIFIED. Bar #2: N/A. Acceptable for a developer-only benchmark engine. Review again before any user-facing release |

## NOTICE entry

**Copyright lines** (verbatim from the LICENSE appendices at the pinned commits above; the body is the shared
`app/src/main/assets/licenses/Apache-2.0.txt`)

```
Qwen3-ASR (Qwen/Qwen3-ASR-0.6B)
Copyright 2026 Alibaba Cloud

Qwen3 (Qwen/Qwen3-0.6B; decoder initialisation and tokenizer)
Copyright 2024 Alibaba Cloud
```

**License:** Apache-2.0 for both. There is no upstream NOTICE file to pass through. The NOTICE entry states that the
files are a third-party ONNX conversion with int8 quantization (Apache §4(b)). The provenance statement is in
`NOTICE.md` under "Downloadable speech models".

## Re-check for user-facing use (#50, 2026-10-02)

Re-checked 2026-10-02 by claude-code (Opus 5.5), per the `model-ethics-review` skill. The question: does the verdict
above still hold if Qwen3-ASR becomes a **user** engine for Chinese and Korean? This section covers only what changed
since 2026-09-25. Nothing above was edited.

### What I checked

| Surface | Result | Evidence |
|---|---|---|
| HF `Qwen/Qwen3-ASR-0.6B` | **No change.** Head is still `5eb14417` (2026-01-30). There are no commits after the four README edits of 2026-01-29/30. The license tag is still `apache-2.0`. There are still 14 discussions, none about data, licence or consent | https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B , https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B/commits/main , https://huggingface.co/api/models/Qwen/Qwen3-ASR-0.6B/discussions |
| GitHub `QwenLM/Qwen3-ASR` | **No change that matters.** There are two commits since January, both on 2026-06-26 and both a README note about native Transformers support. LICENSE still has its one "Initial commit". Issue searches for "training data", "dataset", "copyright", "license", "consent", "privacy", "训练数据" and "版权" turn up no provenance question. The nearest is #134 (2026-03-23). It asked for the Chinese **evaluation** set, not training data, and a bot closed it as inactive with no answer | https://api.github.com/repos/QwenLM/Qwen3-ASR/commits , https://api.github.com/repos/QwenLM/Qwen3-ASR/commits?path=LICENSE , https://github.com/QwenLM/Qwen3-ASR/issues/134 |
| Pinned export `csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25` | **No change.** Head is still `68818b23`. Still no licence file or tag. No discussions | https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/commits/main |
| ModelScope `zengshuishui/Qwen3-ASR-onnx` | **No change to our files.** All six files still have the same size and SHA-256. Still tagged `apache-2.0`. The README was last edited 2026-06-04, which is before the first review. That edit added the "intended for reference only" line quoted above | https://modelscope.cn/api/v1/models/zengshuishui/Qwen3-ASR-onnx/repo/files?Recursive=true |
| GitHub `Wasser1462/Qwen3-ASR-onnx` | **One new item.** Issue #11, "License clarification" (2026-09-30, no reply yet), asks the author to add a LICENSE file to the export **code** repo. This is the gap the first review already noted. The script does not ship with the model, and the weights carry Qwen's Apache-2.0 through the ModelScope tag. So it does not reach us. Last commit is still 2026-04-08 | https://github.com/Wasser1462/Qwen3-ASR-onnx/issues/11 |
| Alibaba training-data summary | **Re-dated, but the Qwen wording is the same.** "Last Updated" moved from 2026-09-02 to 2026-09-28. The page now adds a HappyHorse section. The four-category text and the line "Datasets may include copyrighted … content" are unchanged. Still no audio sources and nothing about ASR | https://www.alibabacloud.com/help/en/model-studio/qwen-and-wan-training-data-disclosure |

### Wider sweep

- **No news since late September** about Qwen or Alibaba speech or audio training data. I found no lawsuit, takedown or
  dataset report naming Qwen3-ASR.
- **A sibling report, not this model.** The Qwen-Audio-3.0-ASR tech report (arXiv 2609.07549, 2026-09-07) is newer and
  slightly more open. It names the ASR systems used to make pseudo-labels (Paraformer-V2, Whisper, SenseVoice). It
  describes the audio only as "large-scale unlabeled speech collected from diverse real-world scenarios". It names no
  corpus. It does not cover Qwen3-ASR, so it tells us nothing firm about our model's data.
  https://arxiv.org/abs/2609.07549
- **An org demerit the first review missed.** On 2026-06-24 Bloomberg reported that Anthropic told US lawmakers that
  operators linked to the Qwen lab used about 25,000 fraudulent accounts for about 29 million Claude exchanges
  (April to June 2026), to distil Claude. Alibaba did not comment. Anthropic has not sued. A securities class action
  filed 2026-08-04 in the Southern District of New York repeats the allegation.
  https://www.bloomberg.com/news/articles/2026-06-24/anthropic-accuses-alibaba-of-illicitly-accessing-its-ai-models ,
  https://thenextweb.com/news/anthropic-accuses-alibaba-distillation-claude-qwen ,
  https://www.dandodiary.com/2026/08/articles/artificial-intelligence/securities-suit-against-alibaba-combines-two-key-litigation-trends/
  - In spirit this is the kind of conduct bar #1 targets: taking from a paid service against its terms. It is still
    **an allegation, not a finding**.
  - **It cannot touch these weights.** The alleged activity began in April 2026. Qwen3-ASR came out in January 2026
    and the Qwen3-0.6B base in April 2025. The targeted skills were coding and agentic reasoning, not speech.
  - **Conflict of interest.** This review was written by Claude, an Anthropic model, and the accuser is Anthropic.
    Weigh this point with that in mind.

### Specific to Chinese and Korean dictation

- **The language hint can rewrite mixed-language speech.** The app tells the model the language in the prompt
  (`"language" to "Chinese"` or `"Korean"`, `SherpaBackends.kt` `QWEN3_LANGUAGE_NAMES`). In QwenLM issue #16, a user
  forced Chinese, and the English words in the clip came out **translated into Chinese**. With auto-detect, the
  Chinese came out as similar-sounding English. That report is for the 1.7B model through Qwen's own toolkit; the 0.6B
  in sherpa-onnx is untested here. A collaborator suggested the language parameter, and the issue was closed as
  inactive with no fix. https://github.com/QwenLM/Qwen3-ASR/issues/16
  - This is a **faithfulness** issue, not a provenance one. Code-switching is common for Chinese and Korean speakers.
    An LLM decoder can write fluent text the user did not say. It is worth a bench check on mixed clips before
    release, and maybe a line in the engine description.
- **Repetition loops are reported** (#129, 1.7B on vLLM: "I think" repeated about 2,000 times). Our config caps
  output at `maxNewTokens = 128`, which bounds the damage per segment. https://github.com/QwenLM/Qwen3-ASR/issues/129
- **Misuse posture is unchanged by going user-facing.** The user dictates their own speech on-device, and audio is
  never stored or sent. The card still has no consent or misuse language. That is a demerit for Qwen, not a risk our
  use adds.
- **Not checked:** whether Chinese output is Simplified or Traditional for Taiwan or Hong Kong speakers.

### Verdict

**Nothing changed in the model, its licence, the export or the files we download. The PASS (grey) verdict stands for
user-facing use for Chinese and Korean. No blocker.** Provenance is still OPAQUE. The org record is a shade greyer
because of the Claude-distillation allegation, which postdates these weights and is unproven. The mixed-language
rewriting above should be benchmarked, but it is a quality risk, not an ethics one. The final adoption decision is
Max's.
