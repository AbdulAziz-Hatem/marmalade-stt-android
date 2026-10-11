# Model Ethics Review: NVIDIA Nemotron 3.5 ASR Streaming 0.6B (int8, k2-fsa exports, 320 ms, 560 ms and 1120 ms)

**Note 2026-10-02 (#37, Max's decision "A"): the 560 ms export left the app too.** Nemotron 3.5 stays only as the
1120 ms export, and only for Hindi, Arabic and Vietnamese, where it matches or beats Whisper Large v3 Turbo
(`docs/benchmarks/2026-10-nemotron-decision.md`); Whisper turbo takes Japanese, Chinese, Korean, Turkish and
Norwegian. Nothing in this review's verdict changed. The 560 ms rows below are history.

**Note 2026-10-02 (#45): the 320 ms export left the app.** The app kept 560 ms and 1120 ms.

**Addendum 2026-09-28: the 1120 ms export.** The app now offers Nemotron 3.5 as one user engine over the 560 ms and
1120 ms exports, picked for the phone's speed (Max, 2026-09-28). The 1120 ms export is the same checkpoint through the
same conversion script by the same publisher (rows below): only the chunk size differs, and its decoder, joiner and
`tokens.txt` are byte-identical to the other two exports'. Nothing in this review's findings changes.

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the two sherpa-onnx int8 exports the Engines screen can download. They sit behind the **Developer engines**
setting so that Max can benchmark them. They are used only to transcribe the user's own dictation on-device. No
generation, no cloning, no speaker identification.

## Verdict first

**PASS against Max's bar, grey. Provenance is PARTIAL, and NVIDIA's track record carries a serious org-level demerit
that Max should rule on.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped *speech* data in this model.** The public sets it
  names are CC0, CC-BY or CC-BY-SA, or European Parliament recordings. Its largest named source, Granary, is Creative-Commons YouTube and audiobook audio.
  The rest is NVIDIA's own proprietary Riva set. NVIDIA says it collected that set itself and obtained consent. Its contents are
  **UNVERIFIED**.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: OpenMDW-1.1**, a permissive, model-specific license published under the Linux Foundation. No use restrictions,
  no restrictions on outputs, and no GPL or LGPL anywhere. Its only condition is the notice duty when *distributing*, plus a patent/copyright-suit
  termination clause. **We do not distribute**, because the app downloads from Hugging Face at the user's request. We still credit and bundle the text (see
  License).

**Caveats. Read these before repeating "clean NVIDIA provenance" anywhere:**

1. **The training labels are partly machine-made.** "Synthetic labels were generated from an ensemble of ASR models
   (NVIDIA Canary, Parakeet Multilingual 1.1B RNNT, Parakeet CTC 1.1B, OpenAI Whisper, and FunASR), with punctuation
   and capitalization (PnC) generated from Qwen3-32B" (card, "Training Datasets"). Granary's own transcripts are
   also Whisper-large-v3 output. So **Whisper's opaque 680k-hour web lineage reaches this model through its training
   targets**, as it does distil-small.en's decoder, but no Whisper *weights* are in it. FunASR's and Qwen's training
   data is undisclosed too. This is a provenance demerit, not piracy. See Provenance, layer 3.
2. **The maintainer's notes rate NVIDIA as an "exemplar" (`training-ethics.md`), and that rating needs revisiting.** Court filings
   in *Nazemian v. NVIDIA* (N.D. Cal. 4:24-cv-01454) allege NVIDIA sought and paid for access to Anna's Archive and
   used LibGen, Z-Library and Books3 pirated books for its NeMo/Megatron **LLMs**. The judge let the direct and
   contributory infringement claims proceed (2026-05). Separately, leaked documents show NVIDIA mass-scraping YouTube
   (with IP-rotating VMs to avoid blocks) for its Cosmos video model, and a class action followed. **Neither concerns
   this ASR model's data**, but the piracy allegation is the same category as Meta's Books3, which is Max's redline. **Max's call:**
   whether that org conduct reaches this model. The adoption here is developer-only and benchmark-only, so the stakes are low.
3. **License history is a little messy but resolved.** Before release the repo was under NVIDIA's gated *Software and
   Model Evaluation License*. On release day (2026-06-04) the metadata tag said `nvidia-open-model-license` while the
   prose said OpenMDW-1.1. The tag was fixed to `openmdw-1.1` on 2026-06-05. Both k2-fsa exports were made **after** the
   fix (2026-06-12, re-exported 2026-07-08/09).

Blocking issues: **none.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point (320 ms) | https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11 at commit `424ce58898995b713f84341f2e1492f9207a26aa` (2026-07-09) | HF API: https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-320ms-int8-2026-06-11/tree/424ce58898995b713f84341f2e1492f9207a26aa |
| Files (320 ms) | `encoder.int8.onnx` 657,601,518 B, sha256 `f79c3fcc149f268b54b7d5754bdc2ba5c47c16b1fc70d15728a56f6efbf60ca5`; `decoder.int8.onnx` 14,978,075 B, `19f9c98fc6d0a2c33a65a43b36fdb2e914c26c0aa9764be3aebc502a1e982fb0`; `joiner.int8.onnx` 9,504,438 B, `4101c7c679a0bc30483794b27a059e34e79232aa2068d78d51231a22c8b0d7ce`; `tokens.txt` 131,440 B, `729cc103155bafa785f9cd45746cd41cabe97eab7182fc04d594129587958f8a` | LFS oids from the tree API above. tokens.txt is not LFS, so I downloaded it and ran `sha256sum` |
| Distribution point (560 ms) | https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11 at commit `ab43d895f5985b1bbab8b6eac8607fcdc05343f3` (2026-07-09) | https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11/tree/ab43d895f5985b1bbab8b6eac8607fcdc05343f3 |
| Files (560 ms) | `encoder.int8.onnx` 657,601,403 B, sha256 `012e9321373af99021415e0b0eb3ec827b4be3153be6f30d9b448fe65e896e68`. Decoder, joiner and `tokens.txt` are byte-identical to the 320 ms ones (same hashes) | same |
| Distribution point (1120 ms) | https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-int8-2026-06-11 at commit `cba1c96ca5ef0e8393b50584ae153a79145dc492` (2026-07-09, authored by HF user `csukuangfj`) | https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-1120ms-int8-2026-06-11/commits/main |
| Files (1120 ms) | `encoder.int8.onnx` 657,601,521 B, sha256 `2fff2166acaa535bd969fb223c1f0783d71029f143cb298bc54c2afe85abf772`. Decoder, joiner and `tokens.txt` are byte-identical to the 320 ms and 560 ms ones (same hashes; `tokens.txt` downloaded and hashed, 2026-09-27) | LFS oids from `/api/models/…?blobs=true` at that commit |
| Encoder metadata (all three) | `model_type=EncDecRNNTBPEModelWithPrompt`, `model_author=NeMo`, `url=https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b`, `comment=Only the transducer branch is exported`, `onnx.infer=onnxruntime.quant`, `cache_last_channel_dim2=56`, `chunk_size_ms=320` / `560` / `1120`, `auto_prompt_id=101`; the 1120 ms file also shows `window_size=121`, `chunk_shift=112` | read from each file's ModelProto `metadata_props` (HTTP range request on the file tail) |
| Upstream checkpoint | https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b (head `ea30d66d`, 2026-09-10; released 2026-06-04; ungated) | https://huggingface.co/api/models/nvidia/nemotron-3.5-asr-streaming-0.6b |
| Conversion | sherpa-onnx `scripts/nemo/nemotron-3.5-asr-streaming-0.6b/export_onnx.py` (header "Copyright 2026 Julian Pscheid", contributed in k2-fsa/sherpa-onnx PR #3671). It loads `nvidia/nemotron-3.5-asr-streaming-0.6b` with `ASRModel.from_pretrained`, sets the attention context to `[56, chunk]`, exports encoder, decoder and joiner, writes `tokens.txt` from the model's SentencePiece tokenizer, then applies `quantize_dynamic` (int8). The 320 ms variant and the context-size fix (70 → 56) came in PR #3732 | https://github.com/k2-fsa/sherpa-onnx/pull/3671, https://github.com/k2-fsa/sherpa-onnx/pull/3732, vendored copy in `third_party/sherpa-onnx/` (v1.13.8) |

**Who csukuangfj2 is.** It is a second Hugging Face account of Fangjun Kuang (`csukuangfj`), the k2-fsa / sherpa-onnx maintainer who
also made our distil-small.en and Silero VAD conversions. The evidence:
- Both accounts have the display name "fangjun" and belong to the `k2-fsa` org
  (https://huggingface.co/api/users/csukuangfj2/overview, created 2026-01-15; https://huggingface.co/api/users/csukuangfj/overview).
- Every commit after the "initial commit" in both repos is authored by HF user `csukuangfj`
  (https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-nemotron-3.5-asr-streaming-0.6b-560ms-int8-2026-06-11/commits/main).
- sherpa-onnx's own CI publishes there. `.github/workflows/export-nemotron-3.5-asr-streaming-0.6b.yaml` pushes to
  `huggingface.co/csukuangfj2/$m` with git identity `csukuangfj@gmail.com`
  (https://github.com/k2-fsa/sherpa-onnx/blob/master/.github/workflows/export-nemotron-3.5-asr-streaming-0.6b.yaml).
  Many other k2-fsa workflows (APKs, wheels) also push to csukuangfj2.

**What the k2-fsa export is.** It is a mechanical format conversion (NeMo → ONNX) plus int8 dynamic weight quantization, with the
chunk size fixed at export time. There is no retraining. Each repo's README says only "This model is from
https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b" plus the chunk size. Neither repo has a license tag or LICENSE file (tags: `onnx`,
`region:us`), so they add no terms. OpenMDW-1.1 governs. The repos also include test WAVs, which we do not download.

## License (weights / code)

- **OpenMDW-1.1.** Card metadata: `license: other`, `license_name: openmdw-1.1`, `license_link: https://openmdw.ai/license/1-1/`.
  Card prose ("License/Terms of Use"): "Governing Terms: Use of the model is governed by the OpenMDW-1.1 license". The same line is in
  `safety.md` and the explainability table. Tag and prose agree. No "research only" or non-commercial rider; the card says "This model is
  ready for commercial use."
  https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b/blob/ea30d66debe3740a08b573244286791d423d6b3e/README.md
- **License history** (card revisions, https://huggingface.co/api/models/nvidia/nemotron-3.5-asr-streaming-0.6b/commits/main):
  - At upload (`9c77e2ff`, 2026-05-15) through `984e9260` (2026-06-04): `nvidia-software-and-model-evaluation-license` (gated, pre-release).
  - `3d849238` (2026-06-04): tag `nvidia-open-model-license`, prose already OpenMDW-1.1.
  - `41defcb1` / `ab89ab0f` / `24b151a8` ("Update License", 2026-06-05): tag becomes `OpenMDW-1.1`, then `openmwd-1.1` (typo), then `openmdw-1.1`.
  - Unchanged since.
  - While access was restricted, Fangjun Kuang asked NVIDIA's permission before exporting (discussion #1, 2026-05-20), and exported only
    after public release (https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b/discussions/1).
- **The license text.** NVIDIA's repo ships **no copy** of the license (file list in the API response above). The bundled text
  `app/src/main/assets/licenses/OpenMDW-1.1.txt` is therefore the steward's canonical file, copied byte-for-byte:
  `1.1/LICENSE.OpenMDW-1.1` of https://github.com/OpenMDW/openmdw at commit `b26b32b34ad2edcc29a7707abb68dcfb25a538c1`
  (the file's only revision is `9067ba54`, 2026-05-19). I checked it word-for-word against https://openmdw.ai/license/1-1/.
  The web page uses curly quotes and the file uses straight quotes; otherwise they are identical. sha256 `2ab44b68…76a74df`.
- **Not an SPDX ID yet.** Per the OpenMDW FAQ §12, use `LicenseRef-OpenMDW-1.1` in SPDX contexts
  (https://github.com/OpenMDW/openmdw/blob/b26b32b34ad2edcc29a7707abb68dcfb25a538c1/1.1/FAQ-1.1.md). OpenMDW-1.0 is on the SPDX list.
- **What OpenMDW-1.1 requires, and when:**
  - **Grant:** "permission is hereby granted, free of charge, to deal in the Model Materials without restriction, including under
    all copyright, patent, database, and trade secret rights".
  - **Outputs:** "This agreement does not impose any restrictions or obligations with respect to any use, modification, or sharing of
    any outputs". Transcripts are unencumbered.
  - **Redistribution** ("If you distribute any portion of the Model Materials"): retain "(1) a copy of this agreement, and (2) all
    copyright notices and other notices of origin included in the Model Materials". FAQ §4 confirms this applies to verbatim and
    modified copies alike.
  - **Download by the user (our case):** the app fetches the files straight from k2-fsa's Hugging Face repo to the device when the user asks. **We distribute
    nothing**, so the notice duty does not bind us. The *user* is the licensee: "By exercising rights granted to you under this
    agreement, you accept and agree to its terms." That is why we still show the text in-app, so the user can read what they accept.
  - **Termination:** rights end for anyone who sues claiming the Model Materials infringe a patent or copyright.
  - **Warranty and rights-clearing disclaimer:** "YOU ARE SOLELY RESPONSIBLE FOR (1) CLEARING RIGHTS OF OTHER PERSONS". NVIDIA
    warrants nothing about third-party rights in the training data.
  - **If we ever mirror or bundle these files** (for example, an F-Droid build or a self-hosted mirror), the redistribution duty applies. Ship
    `OpenMDW-1.1.txt` with them and keep every notice of origin. NVIDIA's card carries **no copyright line** (none in README, `bias.md`, `explainability.md`,
    `privacy.md`, `safety.md` or the JSON configs). The only notice of origin inside the files is the encoder metadata's
    `url=https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b`. **Never strip ONNX metadata** when repackaging.
- **k2-fsa's compliance gap (theirs, not ours).** Their repos redistribute the Model Materials without a copy of OpenMDW-1.1.
  The encoder `url` metadata and the README link are the only notices. It is minor, and upstream is the place to fix it.
- **Code:** sherpa-onnx (Apache-2.0) runs the model and is already in `NOTICE.md`. The export script does not ship.
- **GPL/LGPL: none** in the weights, the tokens file or the export path.

## Provenance (training data)

The card ("Training Datasets") says: "a dynamic blend of public and proprietary internal datasets", 40 language-locales, "10,000 to 1
Million Hours". The card metadata lists `nvidia/Granary`, `multilingual_librispeech`, `fleurs`, `mozilla-foundation/common_voice_8_0`,
`voxpopuli` and `europarl`. **No hour counts per source are given.** That is scale without breakdown, a demerit.

### Layer 1: named public speech corpora. **DISCLOSED, mostly clean**

| Source | License | Notes |
|---|---|---|
| NVIDIA Granary (~1M h pseudo-labelled, 25 EU languages) | CC-BY-4.0 (manifests) | https://huggingface.co/datasets/nvidia/Granary; paper https://arxiv.org/abs/2505.13404. It consolidates **YODAS2** via `espnet/yodas-granary` (CC-BY-3.0; "We made sure that our dataset only consisted of videos with CC licenses during our downloading", with a takedown address: https://huggingface.co/datasets/espnet/yodas2), **YouTube-Commons** (CC-BY-4.0; "All the videos where shared on YouTube with a CC-BY license", every channel credited: https://huggingface.co/datasets/PleIAs/YouTube-Commons), **VoxPopuli** and **LibriLight** via MOSEL (CC-BY-4.0: https://huggingface.co/datasets/FBK-MT/mosel) |
| Multilingual LibriSpeech | CC-BY-4.0 | LibriVox public-domain audiobooks (https://huggingface.co/datasets/facebook/multilingual_librispeech) |
| Mozilla Common Voice | CC0 | crowd-sourced, consented readers |
| FLEURS | CC-BY-4.0 | read speech recorded for Google (https://huggingface.co/datasets/google/fleurs) |
| VoxPopuli / Europarl-ASR | CC0 / public | European Parliament (https://huggingface.co/datasets/facebook/voxpopuli) |

The YouTube-derived parts are **public, not paywalled**. They are only as honest as each uploader's Creative Commons label, and downloading from YouTube
sits uneasily with YouTube's terms. That is grey, not piracy (the same finding as `hf-asr-trending-2026-09.md` in the maintainer's notes).

### Layer 2: NVIDIA Riva multilingual ASR training set. **OPAQUE contents, consent claimed**

Listed only as "(Proprietary)". NVIDIA's `privacy.md` answers "Personal data used to create this model? Yes - Voice",
"Was consent obtained for any personal data used? Yes", and "collected directly by NVIDIA? Yes", but also "not possible with externally-sourced
data" for deletion requests. So part of the corpus is externally sourced
(https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b/blob/ea30d66debe3740a08b573244286791d423d6b3e/privacy.md).
What the Riva set contains, and whether any of it is scraped, is **UNVERIFIED**. No community discussion asks about it: the 33 threads cover languages,
export, fine-tuning and benchmarks (https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b/discussions).

### Layer 3: the labels. **Machine-generated in part, from models with undisclosed data**

"Labeling Method: Human; Synthetic". The synthetic labels come from an ensemble of Canary, Parakeet Multilingual 1.1B RNNT, Parakeet CTC 1.1B,
**OpenAI Whisper** (the card links whisper-large-v3) and **FunASR**, with punctuation and capitalization from **Qwen3-32B** (card, "Training
Datasets"). Granary's transcripts are separately Whisper-large-v3 output, with Qwen-2.5-7B punctuation and a hallucination filter
(Granary card, "Stage 1").

What that means:
- **Knowledge, not weights.** Nemotron learns to reproduce what those teachers heard, so their data lineage flows in indirectly.
  Whisper's is "680,000 hours … collected from the internet", sources unnamed, and OpenAI never answered the direct question
  (see `retired/distil-small-en.md`, layer 1). FunASR (Alibaba) does not say which FunASR model was used (**UNVERIFIED**), and FunASR's
  training data is largely undisclosed "industrial" data (the maintainer's note `sensevoice.md`: "Training data (400K+ hours)
  provenance unclear"). Qwen3's data is undisclosed too. Canary and Parakeet are NVIDIA's own Granary/NeMo lineage.
- **Licensing of the teachers' outputs:** Whisper is MIT and Qwen3-32B is Apache-2.0, and neither restricts outputs. FunASR's `MODEL_LICENSE`
  v1.1 requires attribution when sharing "FunASR open-source model weights and their derivatives, including finetuned models"
  (https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE). Pseudo-labels are outputs, not weights, so Nemotron is not a
  derivative in the ordinary sense. It is a grey note on NVIDIA's side and not our obligation. OpenMDW anyway puts
  rights-clearing on the user, and nothing we do (on-device inference) touches it.
- **Quality side effect:** Whisper-style hallucinations could leak into labels. Granary filters them, and the card measures "Silence Robustness".
  Our benchmark should still include silence and noise clips.

**Whether the model was initialised from earlier NVIDIA checkpoints is not stated. UNVERIFIED.**

### Layer 4: k2-fsa conversion. **N/A (no data)**

**Overall provenance grade: PARTIAL.** The public corpora are named, and their licenses are CC0, CC-BY or public. The proprietary Riva set is opaque, and consent is
asserted, not shown. No hour breakdown is given. The labels partly inherit Whisper's and FunASR's undisclosed data.

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled speech in any layer. The YouTube sources are CC-labelled public uploads.
The Riva set's contents are **UNVERIFIED**.

## Posture: **Structurally N/A for impersonation. No stated surveillance caution.**

- ASR maps audio to text, so bar #2 cannot apply. The card's widget examples are LibriSpeech samples. It has no celebrity or character demos.
- The relevant ASR misuse is surveillance: transcribing people without their consent. Unlike OpenAI's Whisper card, NVIDIA's card **does not caution
  against it**. Its "Intended Users" are "developers and data scientists building interactive call centers, virtual assistants, and language
  learning assistants", and the "Ethical Considerations" section is NVIDIA's generic "Trustworthy AI is a shared responsibility" boilerplate.
  That is weaker than Whisper's verbal discouragement, but not a showcase of misuse.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or sent (a hard rule of this project).

## Org track record

- **NVIDIA: open-release behaviour positive, corporate data conduct negative.**
  - Positive. It published the Granary dataset and pipeline, and the `privacy.md`, `bias.md` and `safety.md` subcards. It moved this model from a gated evaluation license
    to a permissive one. It is responsive on the HF discussions.
  - **Pirated books (alleged, in litigation).** In *Nazemian v. NVIDIA* (N.D. Cal. 4:24-cv-01454), an amended complaint alleges
    "NVIDIA contacted Anna's Archive … about acquiring its millions of pirated materials" and that management gave "the green light"
    after being warned. It also alleges downloads from LibGen, Sci-Hub, Z-Library and Books3, for NeMo/Megatron LLMs
    (https://torrentfreak.com/nvidia-contacted-annas-archive-to-secure-access-to-millions-of-pirated-books/, 2026-01-19).
    NVIDIA denies using pirated books in training
    (https://www.tomshardware.com/tech-industry/artificial-intelligence/nvidia-says-annas-archive-contact-does-not-show-pirated-books-were-used-to-train-its-ai).
    Judge Tigar denied the motion to dismiss the direct and contributory infringement claims in May 2026
    (https://www.loeb.com/en/insights/publications/2026/05/nazemian-v-nvidia-corporation;
    docket https://www.courtlistener.com/docket/68325563/nazemian-v-nvidia-corporation/). These are **allegations, not findings**, and
    they concern LLM text data, not this model.
  - **YouTube and Netflix scraping for Cosmos.** Leaked Slack messages and documents show employees using yt-dlp on IP-rotating
    cloud VMs to download "a human lifetime" of video per day, with managers citing top-level clearance when staff raised concerns
    (https://www.404media.co/nvidia-ai-scraping-foundational-model-cosmos-project/, 2024-08). YouTube creators filed a class
    action in November 2025 (https://news.bloomberglaw.com/ip-law/nvidia-faces-class-action-over-scraping-youtube-to-train-ai).
    That was public but taken against the platform's terms. It is Cosmos, not ASR.
  - Under Max's framework this is an **org-level demerit**, and the pirated-books allegation is the Books3 category Max treats as a
    redline for Meta. The maintainer's notes' NVIDIA "exemplar" rating (`training-ethics.md`, `nvidia-research.md`)
    does not mention either matter and should be revisited. **Max's call** whether it affects a developer-only ASR benchmark engine.
    Using the weights on-device does not pay NVIDIA.
- **k2-fsa / Next-gen Kaldi (Fangjun Kuang):** converter and distributor only. **Positive:** he asked NVIDIA's permission before
  exporting a restricted model. Same maintainer as our other conversions. The one gap is the missing license copy in the repos (see License).
- **Julian Pscheid** (export-script and runtime contributor, PR #3671) works on a meeting app that ships Nemotron on-device
  (https://github.com/k2-fsa/sherpa-onnx/issues/3664). No concerns.

## Feasibility (secondary)

- **Download size:** each variant is 682 MB (encoder 658 MB int8 plus decoder, joiner and tokens). The decoder, joiner and tokens are shared between the
  variants, so installing both costs only one extra encoder. The wiki table's "475 MB" does not match the pinned files. It may be the compressed release archive
  (UNVERIFIED).
- **Runtime:** a true streaming transducer, supported in sherpa-onnx since PR #3671 (v1.13.x). Language is a per-stream string, `auto` or
  empty selects auto-detection (prompt id 101), and sherpa strips the emitted `<xx-XX>` language tags. **32 locales** are usable out of the box.
  Card FLEURS WER at 1.12 s with the language given: en 7.91, es 4.11, de 8.31. Our 320 ms and 560 ms variants trade some of that
  for latency (see the card's WER-vs-chunk figure).
- Known upstream issue: Greek final sigma maps to `<unk>` (discussion #15). Greek is only "adaptation-ready" anyway.

## Working demo link

https://huggingface.co/spaces/victor/nemotron-3-5-asr-streaming. RUNNING (Gradio). HF's API lists
`nvidia/nemotron-3.5-asr-streaming-0.6b` as its model (https://huggingface.co/api/spaces/victor/nemotron-3-5-asr-streaming).
It is a community Space, not NVIDIA's. The authoritative "demo" for our use is the Developer-engines build itself: dictate into it.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **OpenMDW-1.1, pass** | Permissive, no output or use restrictions. Tag and prose agree since 2026-06-05. The k2-fsa repos add no terms. Notice duty applies only to redistribution, which we do not do |
| Provenance | **PARTIAL** | Public corpora named and CC0, CC-BY or public. Proprietary Riva set opaque, consent asserted. No hour breakdown. Labels partly from Whisper, FunASR and Qwen (undisclosed data) |
| Posture | **N/A for impersonation. No surveillance caution** | ASR outputs text only. NVIDIA's card has generic boilerplate, weaker than Whisper's explicit caution |
| Org track record | **NVIDIA grey-to-negative, k2-fsa positive** | Open release conduct good. *Nazemian* pirated-books allegations (claims survived dismissal) and Cosmos YouTube scraping |
| **Verdict vs Max's bar** | **PASS (grey provenance; org-level piracy allegation for Max to rule on)** | Bar #1: no evidence of piracy in this model's speech data. Riva set UNVERIFIED. Bar #2: N/A |

## NOTICE entry

**Attribution** (NVIDIA publishes no copyright line; this is the credit, not a quoted notice):

```
Nemotron 3.5 ASR Streaming 0.6B
by NVIDIA, https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b
Licensed under the OpenMDW License Agreement, version 1.1 (OpenMDW-1.1)
```

**License:** OpenMDW-1.1. It is a standalone body with no embedded copyright line, so it is bundled once as
`app/src/main/assets/licenses/OpenMDW-1.1.txt` and shared by the catalog's OpenMDW-1.1 `License` entry, with the attribution carried by the component.
The provenance statement goes in `NOTICE.md` under "Downloadable speech models".
