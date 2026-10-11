# Model Ethics Review: NVIDIA Nemotron Speech Streaming EN 0.6B (int8, k2-fsa export)

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the four files a **developer-only** engine downloads (hidden behind Settings → Developer engines, so Max can
benchmark it). The model is used only to transcribe the user's own dictation on-device. No generation, no cloning,
no speaker identification.

## Verdict first

**PASS for developer-only benchmarking. The provenance is grey and NVIDIA's track record is negative, both clearly worse than
distil-small.en. Re-review before this becomes a user-facing engine.**

- **Bar #1 (no piracy): no evidence that this model's training data was pirated or scraped from behind a paywall.** The named
  sources are Creative-Commons-labelled YouTube, public-domain audiobooks, open corpora and paid LDC licences. The largest
  block, NVIDIA's own "Riva ASR training set (250k hours)", is not itemised, so piracy **cannot be ruled out for that part (UNVERIFIED)**.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: NVIDIA Open Model License (NVIDIA's own licence, not OSI-approved), version dated October 24, 2025.** Commercial use is allowed.
  The licence requires one notice line, bundled with the full agreement text in this change. Its
  guardrail-termination clause has nothing to bite on here (see License). It does not conflict with an MIT app on
  F-Droid or Play, because we do not redistribute the weights. No GPL or LGPL anywhere.

**Caveats. Read these before repeating "NVIDIA ASR = clean provenance" anywhere:**

1. **Granary is not "consented" data.** About 211k of the card's 530k hours (YouTube-Commons 109.5k + YODAS2 102k) are YouTube audio. Those are videos their
   uploaders *labelled* Creative Commons, scraped by third parties and pseudo-labelled by Whisper-large-v3. That is public-web
   data, which Max's bar accepts. But no speaker consented to model training. The model's own `privacy.md` still says "Was
   consent obtained for any personal data used? Yes" and "collected directly by NVIDIA? Yes", and both contradict the card's own
   dataset list. The maintainer's notes (`parakeet.md`, "curated open dataset from public, consented sources") repeat
   the overstatement.
2. **NVIDIA is in active litigation over its training-data practices. One case names its speech models.** *Rogers v. NVIDIA*
   (N.D. Ill., No. 1:26-cv-05478, filed 2026-05-12) is a biometric-privacy (BIPA) class action. It names **Parakeet and Canary**,
   and a July 2026 shareholder complaint ties those models to Granary, the same corpus this model trains on. Separately,
   *Nazemian v. NVIDIA* alleges that NVIDIA sought pirated books from Anna's Archive for its LLMs, and the direct-infringement claims survived
   a motion to dismiss in May 2026. These are allegations, not findings, and none of them is about this checkpoint. Under
   Max's framework they are a heavy org-level demerit.
3. **The licence is revocable and NVIDIA can rewrite it.** §2.1 says NVIDIA "may update this Agreement to comply with legal and regulatory requirements at any time" (#25
   corrected an earlier quote that left out the qualifier), and users must comply
   or stop using the model. That is acceptable for a developer benchmark. It is a real cost for a default engine, especially
   next to Parakeet TDT 0.6B v3 (CC-BY-4.0) and Nemotron 3.5 ASR Streaming (OpenMDW-1.1), which train on the same kind of data.

Blocking issues for developer-only use: **none.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/csukuangfj2/sherpa-onnx-nemotron-speech-streaming-en-0.6b-560ms-int8-2026-04-25 at commit `52056fdc070914a48dcd68b31b44d6a6f5b85902` (2026-04-25) | HF API: https://huggingface.co/api/models/csukuangfj2/sherpa-onnx-nemotron-speech-streaming-en-0.6b-560ms-int8-2026-04-25/revision/52056fdc070914a48dcd68b31b44d6a6f5b85902?blobs=true |
| Files | `encoder.int8.onnx` 652,916,849 B, sha256 `7d932213491ad355c6e5576705dc3494731a52af87d7a1b954559340147909d8`; `decoder.int8.onnx` 7,257,753 B, sha256 `0be9702c2f427a2b6bb241d298e0d3836a558de1f5b9fd3018f1cce6e2b3fa98`; `joiner.int8.onnx` 1,735,862 B, sha256 `a35eac38a22ebceb04d230ed7afe0d68f446ba6914a036b97f14fece95967e23`; `tokens.txt` 8,952 B, sha256 `dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff` | ONNX sizes and LFS sha256 from the API call above. The tokens.txt hash is from a local `sha256sum` of the file downloaded at the pinned revision. The repo also holds `test_wavs/` and a README, which we don't fetch |
| Encoder metadata | `vocab_size=1024`, `chunk_size_ms=560`, `model_type=EncDecHybridRNNTCTCBPEModel`, `model_author=NeMo`, `url=https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b`, `comment=Only the transducer branch is exported`, producer `onnx.quantize` / `onnxruntime.quant` | read from the tail of `encoder.int8.onnx` (HTTP range request at the pinned revision) |
| Upstream checkpoint | https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b, **March 2026 checkpoint** (commit `2ea504fb` "update the model checkpoint", 2026-03-13; the January checkpoint is kept on branch `nemotron-speech-streaming-jan2026`) | commit list: https://huggingface.co/api/models/nvidia/nemotron-speech-streaming-en-0.6b/commits/main ; refs: https://huggingface.co/api/models/nvidia/nemotron-speech-streaming-en-0.6b/refs |
| Why the March checkpoint | sherpa-onnx issue #3408 (2026-03-25) asked for "the updated version … updated on March 12, 2026". It was closed by PR #3555 ("Update nemotron-speech-streaming-en-0.6b", merged 2026-04-27), which produced the `-2026-04-25` exports. The script loads `from_pretrained("nvidia/nemotron-speech-streaming-en-0.6b")` (main). Not byte-verified against the `.nemo` | https://github.com/k2-fsa/sherpa-onnx/issues/3408 , https://github.com/k2-fsa/sherpa-onnx/pull/3555 |
| Conversion | sherpa-onnx `scripts/nemo/nemotron-speech-streaming-en-0.6b/export_onnx.py` (header: "Copyright 2026 Xiaomi Corp. (authors: Fangjun Kuang)"). NeMo's own `.export()` for encoder, decoder and joiner with `cache_support`, `att_context_size=[70, 6]` for 560 ms, then `quantize_dynamic(..., weight_type=QuantType.QUInt8)`. Run by the `export-nemotron-speech-streaming-en-0.6b.yaml` workflow | `third_party/sherpa-onnx/scripts/nemo/nemotron-speech-streaming-en-0.6b/export_onnx.py` and `.github/workflows/export-nemotron-speech-streaming-en-0.6b.yaml` (v1.13.8 submodule); https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/nemo/nemotron-speech-streaming-en-0.6b/export_onnx.py |

**Who csukuangfj2 is: Fangjun Kuang, the same person as `csukuangfj`. Established.**
- Both HF accounts show the full name "fangjun" and belong to the `k2-fsa` org (https://huggingface.co/api/users/csukuangfj/overview, https://huggingface.co/api/users/csukuangfj2/overview).
  `csukuangfj2` was created 2026-01-15 and hosts 101 repos, among them `sherpa-onnx-wheels`, `-libs` and `-apk`.
- The pinned repo's two content commits are authored by `csukuangfj`.
- sherpa-onnx's own export workflow pushes to `https://csukuangfj2:$HF_TOKEN@huggingface.co/csukuangfj2/$m` with git email `csukuangfj@gmail.com`
  (`.github/workflows/export-nemotron-speech-streaming-en-0.6b.yaml`). This is the same maintainer who supplied our Silero VAD and distil-small.en conversions.

**What the k2-fsa export is.** It is a format conversion (NeMo → ONNX) of the transducer branch (the CTC head is dropped),
fixed to the 560 ms chunk size that NVIDIA documents as a runtime option, plus 8-bit dynamic weight quantization. There is no retraining
or fine-tuning. Under the licence this is a "Derivative Model" (§1.1, "modifications to the Model"). The pinned repo has
**no LICENSE file and no license tag** (tags: `onnx`, `region:us`). Its README says only "This model is from
https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b". k2-fsa adds no terms of its own, so NVIDIA's licence
governs. k2-fsa does not pass on the agreement or the notice line that §3.1 requires when redistributing. That gap is on the distributor, not on
us, and our NOTICE and licenses screen close it for our users.

**tokens.txt** holds the model's 1,024 SentencePiece pieces (`<unk> 0` … `¥ 1023`) plus `<blk> 1024`, written from
`asr_model.joint.vocabulary` by the export script. It is part of the Model ("configuration files", §1.3), under the same licence.

**Lineage.** NVIDIA's NeMo maintainer says: "The encoder was initialized from the offline nvidia/parakeet-tdt-0.6b-v2.nemo
checkpoint. The decoder and joint modules were initialized from scratch."
(https://github.com/NVIDIA-NeMo/Speech/issues/15455, KunalDhawan, 2026-03-20). Parakeet TDT 0.6B v2 is CC-BY-4.0
(https://huggingface.co/api/models/nvidia/parakeet-tdt-0.6b-v2). NVIDIA owns both, so it may relicense its own derivative. This adds no
third-party holder.

## License (weights / code)

- **Weights: NVIDIA Open Model License Agreement, "Last Modified: October 24, 2025" / "Version Release Date: October 24, 2025".**
  The model card sets `license: other`, `license_name: nvidia-open-model-license`,
  `license_link: https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/`. The prose says: "Governing
  Terms: Use of the model is governed by the NVIDIA Open Model License Agreement" and "This model is ready for
  commercial/non-commercial use." The tag and the prose agree. The first commit (`100fc004`, 2025-12-17), the first full card (`150339c4`, 2025-12-18), head `ebe59e5a` and the January branch all carry
  the same licence metadata (spot-checked, not every revision).
  - https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b (card, "## License/Terms of Use")
  - https://huggingface.co/api/models/nvidia/nemotron-speech-streaming-en-0.6b
  - **Bundled text:** `app/src/main/assets/licenses/NVIDIA-Open-Model-License.txt`, taken verbatim from the web page the card links
    (fetched 2026-09-25; section numbers restored from the page's CSS list counters). Cross-checked word for word against NVIDIA's
    PDF of the same version,
    https://www.nvidia.com/content/dam/en-zz/Solutions/license-agreements/enterprise-software/nvidia-open-model-license-agreements-24-10-2025.pdf
    (sha256 `4d2fb590aa9b30c47f2058bff17291df7fa2aa0c1bd775a20703da9bb267cfab`). The only differences: the PDF ends definitions 1.4 and 1.5 with a
    full stop, and it omits the web page's "IMPORTANT NOTICE" and "Last Modified" header lines.
  - No SPDX identifier exists for this licence (SPDX list 3.29.0, 2026-09-16).
- **Required notice (§3.1), verbatim:**
  "If you distribute the Model, You must give any other recipients of the Model a copy of this Agreement and include
  the following attribution notice within a “Notice” text file with such copies: “Licensed by NVIDIA Corporation under
  the NVIDIA Open Model License”;"
  - Strictly, this binds only whoever *distributes*. We don't: the app fetches the files from k2-fsa's HF repo when the user asks for them. We add the
    line and the full text anyway, because it costs nothing and covers a broader reading of "make available". If we ever mirror or bundle the weights,
    it becomes mandatory.
- **Guardrail-termination clause (§2.1), verbatim:**
  "If You bypass, disable, reduce the efficacy of, or circumvent any technical limitation, safety guardrail or
  associated safety guardrail hyperparameter, encryption, security, digital rights management, or authentication
  mechanism (collectively “Guardrail”) contained in the Model without a substantially similar Guardrail appropriate for
  your use case, your rights under this Agreement will automatically terminate."
  - **In plain words:** if anyone strips out a protective mechanism *built into the model*, such as a safety filter, a
    refusal behaviour, a watermark, encryption or a licence key, that person loses the licence at once and automatically.
  - **Nothing in our use triggers it.** The clause bites only on a Guardrail "contained in the Model", and this model has none:
    - The weights map audio to English text. There is no safety classifier, refusal head, watermark, encryption, DRM or
      authentication in the `.nemo`, the card or the ONNX graphs.
    - NVIDIA's `safety.md` lists no guardrail. Its only "Use Case Restrictions" entry is "Abide by NVIDIA Open Model License"
      (https://huggingface.co/nvidia/nemotron-speech-streaming-en-0.6b/blob/main/safety.md).
    - The k2-fsa steps remove nothing protective. The 560 ms chunk is one of the four sizes NVIDIA documents as a runtime choice ("No re-training is required to
      adjust for different use-case requirements"). Int8 quantization and dropping the auxiliary CTC head change
      accuracy or format, not any protective mechanism. The app only runs the files as published.
  - The clause is drafted broadly ("any technical limitation"). A hostile reading could try to call quantization "reducing the efficacy" of
    something. In context the listed items are all protective or access-control mechanisms, so that reading looks
    untenable. **This is not legal advice, and no court reading of the clause was found (UNVERIFIED).** If it were ever triggered, it would terminate
    the rights of the party who did the bypassing (k2-fsa or an end user), not our MIT code.
- **Other terms that matter for us:**
  - §2.1: revocable if you sue NVIDIA over IP in the model, and NVIDIA "may update this Agreement to comply with legal and regulatory requirements at any time".
  - §2.3: use "must be consistent with NVIDIA's Trustworthy AI terms". Those terms (last modified June 27, 2024) prohibit "illegal
    surveillance" and "illegal collection or processing of biometric information without the consent of the subject",
    https://www.nvidia.com/en-us/agreements/trustworthy-ai/terms/. Transcribing the user's own dictation on-device is compliant.
  - §5: NVIDIA trademarks may be used only "in describing the origin of the Model". So we name the engine accurately and use no NVIDIA logos.
  - §8: the *user* indemnifies NVIDIA.
  - §10: Delaware law, Santa Clara courts.
  - §11: the user must comply with export and sanctions law.
- **Conflicts with shipping an MIT app on Play or F-Droid: none in the licence.** It has no copyleft and puts no terms on software that
  merely runs the model. Our code stays MIT, and the licence's obligations fall on whoever uses or distributes the weights.
  **F-Droid policy is a separate question for Max.** The downloads already make the app `NonFreeNet`. This licence is not
  OSI- or FSF-free (it is revocable, can be rewritten unilaterally and has field-of-use terms), so if the Developer-engines setting ships in the
  `fdroid` flavor, a reviewer could reasonably ask for the `NonFreeAdd` anti-feature too ("promote other Non-Free applications or
  plugins", https://f-droid.org/en/docs/Anti-Features/). Leaving developer engines out of the fdroid flavor would avoid the question.
- **GPL/LGPL: none** in the weights, the tokens file or the export path.

## Provenance (training data)

### Layer 1: NVIDIA training set. **PARTIAL: sources named, the largest block not itemised**

The model card, "### Training Datasets", says: "The majority of the training data comes from NVIDIA Riva ASR training set (250k hours) and the
English portion of the Granary dataset". It lists YouTube-Commons (109.5k h), YODAS2 (102k h), Mosel (14k h) and LibriLight
(49.5k h), "In addition" LibriSpeech, Fisher, Switchboard-1, WSJ-0/1, National Speech Corpus, VCTK, VoxPopuli, Europarl-ASR,
MLS, Common Voice v4/v7/v11, People's Speech and AMI. It gives the totals as "Audio Training Data Size: 530k hours", "Data Collection Method: Human - All audios are
human recorded" and "Labeling Method: Hybrid (Human, Synthetic)". The card's intro gives a different figure ("Trained on the ASRSet … approximately 250,000
hours"), so the two totals disagree.

| Source | Hours | Terms | Notes |
|---|---|---|---|
| YouTube-Commons (YTC) | 109.5k | CC-BY-4.0 dataset of videos "shared on YouTube under a CC-By license" | https://huggingface.co/datasets/PleIAs/YouTube-Commons . The licence is only as honest as each uploader's label. PleIAs "recommend that this set be preferably used for open research" |
| YODAS2 | 102k | CC-BY-3.0 | CMU/ESPnet YouTube crawl: "We made sure that our dataset only consisted of videos with CC licenses during our downloading", with a deletion-request contact (https://huggingface.co/datasets/espnet/yodas ; paper https://arxiv.org/abs/2406.00899) |
| Mosel | 14k | CC-BY-4.0 | pseudo-labels for VoxPopuli, YTC and LibriLight (https://huggingface.co/datasets/FBK-MT/mosel) |
| LibriLight | 49.5k | public-domain LibriVox audio | |
| Riva ASR training set | ~250k | **not disclosed** | NVIDIA-internal. Earlier NeMo ASR Sets were "nearly 12000 hours of speech from various public datasets", and NVIDIA declined to publish file lists (https://github.com/NVIDIA-NeMo/Speech/issues/3506). Contents beyond the "In addition" list: **UNVERIFIED** |
| Fisher, Switchboard-1, WSJ-0/1 | — | **LDC, paid licences** | licensed, not scraped |
| National Speech Corpus (IMDA, Singapore) | — | Singapore Open Data Licence | https://www2.imda.gov.sg/NationalSpeechCorpus |
| LibriSpeech, VCTK, VoxPopuli, Europarl-ASR, MLS, Common Voice, People's Speech, AMI | — | CC0 / CC-BY / CC-BY-SA | open corpora |

**Labels.** Granary's ASR labels come from "Whisper-large-v3 with language ID verification", with "Qwen-2.5-7B for
punctuation/capitalization" (https://huggingface.co/datasets/nvidia/Granary). The HF launch blog confirms the Granary
P&C labels were used for this model (https://huggingface.co/blog/nvidia/nemotron-speech-asr-scaling-voice-agents, author reply).
So OpenAI Whisper's opaque 680k-hour lineage enters here as a **teacher**, not as weights (compare distil-small.en, which
contains Whisper weights).

**No one asked the direct question.** Of 21 HF discussions, none raises training data or licence
(https://huggingface.co/api/models/nvidia/nemotron-speech-streaming-en-0.6b/discussions). The one NeMo GitHub thread asking
for ASRSet contents got "most probably we won't be releasing scripts to construct ASR set soon" (issue #3506 above).

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled-scraped content among the named sources. YouTube-CC is public,
LDC is licensed, and the rest are open. Whether the Riva 250k-hour set is clean is **UNVERIFIED**. Granary is public-web scraping without speaker consent. Max's
bar accepts that, but the card's privacy claims (Caveat 1) overstate it.

### Layer 2: k2-fsa conversion. **N/A (no data)**

Format conversion and quantization only (see Artifact).

**Overall provenance grade: PARTIAL.** The public half is well named, the NVIDIA-internal half is not, and the card makes
contradictory privacy claims.

## Posture: **Structurally N/A for impersonation. Surveillance verbally prohibited.**

- ASR maps audio to text, so Max's bar #2 cannot apply. The card's widget samples are LibriSpeech clips. The Transformers
  streaming example transcribes `obama.mp3` from HF's `dummy-audio-samples` test set. That is input audio, not a voice showcase.
- The misuse risk for ASR is surveillance. NVIDIA's Trustworthy AI terms, incorporated by §2.3, prohibit "illegal surveillance" and
  non-consensual biometric processing. That is verbal discouragement in a binding document, slightly stronger than OpenAI's card-only caution.
- `bias.md` answers "Measures taken to mitigate against unwanted bias: None". This is honest but a demerit.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or sent (a hard rule of this project).

## Org track record

- **NVIDIA: negative (allegations, not findings).**
  - **Pirated books (LLMs).** *Nazemian v. NVIDIA*, N.D. Cal. 4:24-cv-01454
    (https://www.courtlistener.com/docket/68325563/nazemian-v-nvidia-corporation/). The amended complaint alleges "NVIDIA contacted
    Anna's Archive … about acquiring its millions of pirated materials" (TorrentFreak, 2026-01-19,
    https://torrentfreak.com/nvidia-contacted-annas-archive-to-secure-access-to-millions-of-pirated-books/). Judge Tigar let
    the direct-infringement claims proceed on 2026-05-04
    (https://www.loeb.com/en/insights/publications/2026/05/nazemian-v-nvidia-corporation).
  - **YouTube and Netflix video scraping (Cosmos).** Leaked documents describe a "video data factory" that used yt-dlp and rotating IPs, and a
    manager's "We have an umbrella approval for all of the data" (404 Media, August 2024,
    https://www.404media.co/nvidia-ai-scraping-foundational-model-cosmos-project/). A YouTuber class action followed
    (https://news.bloomberglaw.com/ip-law/nvidia-faces-class-action-over-scraping-youtube-to-train-ai).
  - **Voiceprints (speech models, closest to this one).** *Rogers v. NVIDIA Corp.*, No. 1:26-cv-05478 (N.D. Ill.), is one of nine BIPA suits
    filed in May 2026 by Illinois journalists, narrators and voice actors
    (https://www.cbsnews.com/chicago/news/illinois-class-action-lawsuits-voices-stolen-train-ai-bipa/,
    https://www.courthousenews.com/illinois-suits-accuse-microsoft-nvidia-of-stealing-voiceprints-for-ai-products/). It names
    Magpie TTS, Fugatto, and "the … Parakeet speech models". The shareholder derivative complaint *Berliner v. Huang*
    (N.D. Ill. 1:26-cv-09153, filed 2026-07-31, ¶109) says Granary's "one-million-hours of speech audio processing were placed on
    platforms without the consent of speakers" and that it trained Canary-1b-v2 and Parakeet-tdt-0.6b-v3
    (https://www.courthousenews.com/wp-content/uploads/2026/08/nvidia-v-huang.pdf). This model is not named. It shares Granary's
    English portion and Parakeet v2's encoder.
  - **Positives.** The weights have not been pulled or relicensed. Granary's manifests and pipeline are public. The model-card
    sub-pages (bias/privacy/safety) exist, even where the answers are thin or contradictory.
  - **Card hygiene.** The demo Space the card links (`spaces/nvidia/nemotron-speech-streaming-en-0.6b`) returns HTTP 401 (gone or private).
- **k2-fsa / Next-gen Kaldi (Xiaomi-affiliated; Fangjun Kuang): converter and distributor, neutral.** The one lapse is not passing on
  NVIDIA's agreement and notice line (Artifact).

**Does the BIPA theory reach us?** The suits target NVIDIA for extracting and distributing voiceprints. Our app does no
speaker identification and keeps no biometric data. Whether an on-device *user* of such weights has any exposure is an open legal
question (**UNVERIFIED**). It is one more reason to keep this engine developer-only until *Rogers* resolves.

## Feasibility (secondary)

- **Size.** About 662 MB download (661,919,416 B for the four files). The maintainer's scan (`hf-asr-trending-2026-09.md`) says "463 MB", which is wrong for this
  export.
- **Runtime.** Native cache-aware streaming (partial results every 560 ms) via sherpa-onnx `OnlineRecognizer` (transducer),
  which is already in our v1.13.8 build. Punctuation and casing are built in. English only.
- **Accuracy.** Card WER at 0.56 s is 7.07 average on the Open ASR sets (2.46 LS-clean / 5.07 LS-other), self-reported.
- **Unmeasured.** RTF and RAM on the Pixel 8a (the maintainer's benchmark plan).

## Working demo link

- **Hosted, closest to our files:** the k2-fsa ASR Space (RUNNING 2026-09-25) offers
  `csukuangfj/sherpa-onnx-nemotron-speech-streaming-en-0.6b-int8-2026-01-14`, the **January** checkpoint's sherpa export, not
  ours. https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition
- **Hosted, March checkpoint:** NVIDIA's NIM at https://build.nvidia.com/nvidia/nemotron-asr-streaming (HTTP 200; needs a free
  API key, and **audio goes to NVIDIA**, so use a test clip, not private speech). Third-party Gradio Space
  https://huggingface.co/spaces/gnumanth/nemotron-speech-streaming (RUNNING; which checkpoint it loads is UNVERIFIED).
- The real demo is the Developer-engines entry itself, which runs these exact files.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **NVIDIA Open Model License (2025-10-24). Pass for our use** | Commercial use allowed. One notice line. Guardrail clause has nothing to act on. Revocable and unilaterally changeable. No conflict with an MIT app; possible F-Droid `NonFreeAdd` question |
| Provenance | **PARTIAL** | Granary (CC-labelled YouTube, LibriLight, Mosel) plus LDC and open corpora named. The 250k-hour Riva set not itemised. Whisper-large-v3 and Qwen as label teachers. Card's consent claims contradict its own dataset list |
| Posture | **N/A for impersonation. Surveillance prohibited by incorporated terms** | ASR outputs text only. Trustworthy AI terms bind |
| Org track record | **NVIDIA negative, k2-fsa neutral** | Pirated-books claims surviving dismissal. YouTube/Netflix scraping reporting. BIPA suits naming sibling ASR models trained on the same corpus |
| **Verdict vs Max's bar** | **PASS for developer-only use (grey provenance, org demerit)** | Bar #1: no evidence of piracy in the named sources, Riva set UNVERIFIED. Bar #2: N/A. Re-review before any user-facing use (check the *Rogers* outcome and the current licence version) |

## NOTICE entry

**Required notice line** (verbatim from §3.1 of the agreement; the text is bundled as
`app/src/main/assets/licenses/NVIDIA-Open-Model-License.txt`):

```
NVIDIA Nemotron Speech Streaming EN 0.6B
Licensed by NVIDIA Corporation under the NVIDIA Open Model License
```

**License:** NVIDIA Open Model License Agreement, version of October 24, 2025
(https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/). NVIDIA publishes no separate
copyright line for the model. §2.4 says "NVIDIA owns the Model". The body embeds no licensor line, so it is a standalone text, like
Apache-2.0. The provenance statement belongs in `NOTICE.md` under "Downloadable speech models".
