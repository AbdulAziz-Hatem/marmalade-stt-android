# Model Ethics Review: NVIDIA Parakeet TDT 0.6B v3 (int8, k2-fsa export)

**Note 2026-10-02 (#45): v3 left the app.** This review stands as the base-model provenance of Parakeet Ultra ([`parakeet-ultra.md`](parakeet-ultra.md)), the default engine since then.

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the four files the Engines screen downloads when **Developer engines** is switched on. Max uses the model
only to benchmark the transcription of his own dictation on-device. No generation, no cloning, no speaker identification.

## Verdict first

**PASS against Max's bar, with a grey provenance demerit and a serious org-level demerit.** The wiki calls this model
"clear" and Granary "consented". Both overstate it.

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped training data in this model.** About 97% of the
  pre-training hours are YouTube videos marked Creative Commons, European Parliament recordings and LibriVox audiobooks.
  The fine-tuning set includes one paid, licensed LDC corpus (Fisher). That corpus was bought, not scraped.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: CC-BY-4.0**, from NVIDIA's model card (tag and prose agree, unchanged since v3 was published). The k2-fsa
  repository adds no terms. No GPL or LGPL anywhere. CC-BY requires credit, a license link and a statement that the
  int8 ONNX conversion is a modification. The NOTICE entry below covers all three.

**Caveats. Read these before repeating the "clean provenance" line anywhere:**

1. **Granary is license-filtered public web data, not "consented" data.** About half of it (314,647 of 643,238
   filtered hours) is audio downloaded from YouTube videos whose *uploaders* ticked the Creative Commons box. YouTube
   does not check that flag, and the people speaking in those videos never agreed to anything. The wiki notes
   `tech/ai/stt/parakeet.md` ("public, consented sources") and `nvidia-nemo-asr.md` ("Morally clear across the
   board") need correcting.
2. **Every Granary transcript was written by OpenAI's Whisper large-v3**, then re-punctuated by Qwen-2.5-7B. No Whisper
   *weights* are in Parakeet, but its training targets carry Whisper's opaque 680k-hour lineage (see
   `retired/distil-small-en.md`, Layer 1).
3. **YODAS, the biggest YouTube source, withholds the real video IDs** "to avoid unnecessary issues". Nobody outside
   CMU can audit its CC-only claim or credit the creators.
4. **NVIDIA's org record is the worst of any model we've reviewed.** It faces a pending authors' lawsuit alleging it
   trained NeMo LLMs on Books3 and sought millions of pirated books from Anna's Archive. Leaked 2024 documents show it
   scraped YouTube and Netflix video for its Cosmos model. None of this concerns Parakeet's documented, CC-filtered data,
   but it is the same company and the same NeMo brand.

Blocking issues: **none** for a developer-only benchmark engine. Whether it is fit to become a user-facing default is
the same question plus caveat 4, and that is Max's call.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 at commit `2bda32ec70b097a55adaa07d9a7173915b43cc78` ("first commit", 2025-08-16T09:29) | HF API: https://huggingface.co/api/models/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/revision/2bda32ec70b097a55adaa07d9a7173915b43cc78 |
| Files | `encoder.int8.onnx` 652,184,281 B, sha256 `acfc2b4456377e15d04f0243af540b7fe7c992f8d898d751cf134c3a55fd2247`; `decoder.int8.onnx` 11,845,275 B, `179e50c43d1a9de79c8a24149a2f9bac6eb5981823f2a2ed88d655b24248db4e`; `joiner.int8.onnx` 6,355,277 B, `3164c13fc2821009440d20fcb5fdc78bff28b4db2f8d0f0b329101719c0948b3`; `tokens.txt` 93,939 B, `d58544679ea4bc6ac563d1f545eb7d474bd6cfa467f0a6e2c1dc1c7d37e3c35d` | ONNX sizes and sha256 are the LFS oids in the HF tree API (https://huggingface.co/api/models/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8/tree/2bda32ec70b097a55adaa07d9a7173915b43cc78). `tokens.txt` is not in LFS; I downloaded it and ran `sha256sum`. All four match `SttEngineCatalog` |
| Encoder metadata | `model_type=EncDecRNNTBPEModel`, `model_author=NeMo`, `url=https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3`, `comment=Only the transducer branch is exported`, `vocab_size=8192`, `feat_dim=128`, `subsampling_factor=8`, `pred_rnn_layers=2`, `pred_hidden=640`; producer `onnx.quantize` 0.1.0 | read from the last 4 KB of `encoder.int8.onnx` by HTTP range request (python `onnx` was not installed, so the strings were read raw) |
| tokens.txt | 8,193 lines: NVIDIA's 8,192-piece SentencePiece vocabulary (`<unk>`, `<\|nospeech\|>`, `<pad>`, …) plus `<blk> 8192` | downloaded file; the export script writes `asr_model.joint.vocabulary` then `<blk>` |
| Upstream checkpoint | https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3, file `parakeet-tdt-0.6b-v3.nemo`, 2,509,332,480 B, sha256 `3cbdc85877e668ca7b82d0d56770eb1fac76691f55d6b97545e8d61ca588d10d`. That file was last changed in commit `1b6821cb` ("update model", 2025-08-08) and is identical at today's head `541d1f99`. So the 2025-08-16 export can only have used this checkpoint | HF tree API at `1b6821cb`, `bb0964b9` and `541d1f99`; commit list https://huggingface.co/api/models/nvidia/parakeet-tdt-0.6b-v3/commits/main |
| Conversion | sherpa-onnx `scripts/nemo/parakeet-tdt-0.6b-v3/` (header "Copyright 2025 Xiaomi Corp. (authors: Fangjun Kuang)"). `run.sh` downloads the `.nemo` from `resolve/main`. `export_onnx.py` loads it in NeMo, exports the encoder, prediction network and joiner with NeMo's `.export()`, then applies `quantize_dynamic` (encoder `QUInt8`, decoder and joiner `QInt8`). The CTC head is dropped | https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/nemo/parakeet-tdt-0.6b-v3 (also vendored at `third_party/sherpa-onnx/scripts/nemo/parakeet-tdt-0.6b-v3/`, v1.13.8) |

**What the k2-fsa export is.** It is a format conversion (NeMo → ONNX) plus int8 dynamic weight quantization, with the
auxiliary CTC branch removed. There is no retraining or fine-tuning. Under CC-BY-4.0 that is still an **adaptation**:
quantization changes the weights. The pinned HF repo has **no README, no LICENSE file and no license tag** (tags `onnx`,
`region:us`; `cardData` null), so it adds no terms and does not state the upstream license or the modification itself.
The only upstream pointer is the `url` metadata key inside the encoder. That gap is k2-fsa's. Our NOTICE entry
supplies the missing credit and modification notice.

## License (weights / code)

- **Parakeet TDT 0.6B v3 weights: CC-BY-4.0.** The card metadata says `license: cc-by-4.0`. The prose says "GOVERNING
  TERMS: Use of this model is governed by the [CC-BY-4.0](https://creativecommons.org/licenses/by/4.0/legalcode.en)
  license", "This model is ready for commercial/non-commercial use", and, under Safety, "Use Case Restrictions: Abide by
  CC-BY-4.0 License". The tag and prose agree, and I found no rider. The `cc-by-4.0` tag has been there since the v3
  commit `3f6aa9af` (2025-08-07). The prose terms are identical at `1b6821cb` (the checkpoint's commit) and at today's head.
  - https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3 (card, "License/Terms of Use")
  - https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/raw/1b6821cbe889fcf82347cb95c3f8f0c7515a60e9/README.md
- **No copyright notice is supplied.** The card and repo contain no "Copyright" line and no LICENSE file (sibling
  list in https://huggingface.co/api/models/nvidia/parakeet-tdt-0.6b-v3). CC-BY §3(a)(1)(A) only requires retaining a
  copyright notice "if it is supplied by the Licensor", so the credit is to **NVIDIA** as creator, with no invented line.
- **What CC-BY-4.0 asks of whoever shares it** (§3(a)(1), from the legal code bundled as
  `app/src/main/assets/licenses/CC-BY-4.0.txt`, verbatim from https://creativecommons.org/licenses/by/4.0/legalcode.txt):
  identify the creator; keep any copyright and disclaimer notices supplied; link the licensed material; **indicate
  modifications** and keep earlier modification notices; and name the license with its text or a link. Marmalade
  does not host the files: the app fetches them from k2-fsa's repo. So strictly the sharer is k2-fsa, but we give full
  attribution anyway because the app delivers the weights to the device.
- **Tokens:** NVIDIA's SentencePiece vocabulary, part of the CC-BY model. No separate terms.
- **Dataset licenses vs model license.** Granary's own licenses are CC-BY-3.0 (YODAS-Granary) and CC-BY-4.0 (MOSEL),
  with the raw audio under "respective source licenses" (https://huggingface.co/datasets/nvidia/Granary, "License").
  None is NC or SA, so there is no NC or share-alike inheritance problem like the ones distil-whisper and Emilia have.
  Housekeeping oddity: the Granary card's metadata tag changed from `cc-by-3.0` to `cc-by-4.0` on 2026-06-25
  ("License Update", https://huggingface.co/datasets/nvidia/Granary/discussions/20), while its body still lists
  YODAS-Granary as CC-BY-3.0. Harmless for us.
- **GPL/LGPL: none** in the weights, the tokens file or the export path.

## Provenance (training data)

The card ("Training Dataset") and NVIDIA's tech report (https://arxiv.org/abs/2509.14128, §3–4) describe two stages:
pre-training on "the ASR subset of the Canary-1B-v2 training set (~660,000 hours across the same 25 languages)",
initialised from a multilingual CTC checkpoint pre-trained on Granary, then "5,000 steps on 4 A100 GPUs using
7,500 hours of high-quality NeMo ASR Set 3.0 data". The report adds 36,000 hours of non-speech audio "extracted during
Granary pipeline filtration steps", paired with empty targets to curb hallucination (§3.2).

### Layer 1: Granary (≈97% of pre-training hours). **DISCLOSED-SCRAPED, license-filtered**

Granary is a set of transcripts (manifests) over four public audio sources. The audio is not NVIDIA's. Hours are the
filtered ASR hours from the Granary card ("Cross-Corpus Distribution"):

| Source | Hours | What it is | License basis | Evidence |
|---|---|---|---|---|
| YODAS (via espnet/yodas-granary) | 192,172 | YouTube videos found by keyword crawling and "YouTube's native filtering feature" | "the video content must be accompanied by a Creative Commons license" (paper §2). Card: "We made sure that our dataset only consisted of videos with CC licenses during our downloading", with an e-mail address for removal requests. Dataset CC-BY-3.0 | https://arxiv.org/abs/2406.00899 §2; https://huggingface.co/datasets/espnet/yodas2 ("Contact") |
| VoxPopuli | 206,116 | European Parliament plenary recordings | Dataset CC0, raw data under the EP's legal notice | https://huggingface.co/datasets/facebook/voxpopuli ("Licensing Information"); https://www.europarl.europa.eu/legal-notice/en/ |
| YouTube-Commons | 122,475 | YouTube videos "shared on YouTube under a CC-By license" (PleIAs) | CC-BY-4.0. The dataset records title, link and channel for every video, so creators are credited | https://huggingface.co/datasets/PleIAs/YouTube-Commons ("License and ethics") |
| LibriLight | ~23,500 | LibriVox public-domain audiobooks | public domain audio | https://huggingface.co/datasets/FBK-MT/mosel ("Dataset Description") |
| **Total** | **643,238** | | | The card says "660,000 hours", presumably counting before the final filtering |

**Every Granary label is machine-made.** The pipeline runs "Two-Pass Inference: Whisper-large-v3 with language ID
verification", then "P&C Restoration: Qwen-2.5-7B" (Granary card, "Pipeline & Quality"; paper §3.1,
https://arxiv.org/abs/2505.13404). MOSEL's transcripts are also Whisper large-v3 output (MOSEL card). The paper
chose Whisper for its "open license". So Whisper's training data, which OpenAI never named, shaped what Parakeet
learned to write, though none of Whisper's weights are in Parakeet.

**Is Granary "clean"? Mostly, but not in the way the wiki says.**

- **Named and public: yes.** Every source is named, and none is paywalled or pirated.
- **License-filtered at collection: yes**, for both YouTube sources.
- **Consented: no.** YouTube's CC-BY flag is **self-declared by the uploader**. YouTube tells uploaders they may use it
  only if the video "consists entirely of content licensable by you" (https://support.google.com/youtube/answer/2797468),
  but it does not check. So re-uploads of other people's material can carry the flag. How much of YODAS/YTC is
  mis-flagged is **UNVERIFIED**; nobody has measured it that I found. The people *speaking* in those videos, and
  MEPs in VoxPopuli, were never asked about ASR training. That is normal for public-web data and within Max's bar, but it
  is not consent.
- **Auditable: only half.** YouTube-Commons publishes channels and links. **YODAS does not.** Asked for real video IDs,
  the maintainer replied: "we are not planning to release the real ids in this datasets to avoid unnecessary issues"
  (https://huggingface.co/datasets/espnet/yodas2/discussions/2, 2025-02-28). They are pseudo-IDs
  (https://huggingface.co/datasets/espnet/yodas/discussions/9). That also makes CC-BY attribution to the creators
  impossible for 192k hours. A licensing question on YODAS2 got only a pointer to the paper and the README line
  (https://huggingface.co/datasets/espnet/yodas2/discussions/6, 2026-03-30).
- **NVIDIA's own privacy table overstates it.** It answers "Personal data used to create this model?" with "None" and
  "Is there provenance for all datasets used in training?" with "Yes" (card, "Privacy"). Voices of identifiable
  YouTubers and MEPs are personal data, and YODAS provenance is not public. The same table does concede that removal
  requests are "not possible with externally-sourced data".

### Layer 2: NeMo ASR Set 3.0 (fine-tuning, 7,500 h). **PARTIAL, mixed licenses, one paid corpus**

NVIDIA lists "LibriSpeech (960 hours), Fisher Corpus, National Speech Corpus Part 1, VCTK, Europarl-ASR,
Multilingual LibriSpeech, Mozilla Common Voice (v7.0), AMI" (card). The tech report adds FLEURS and "language-specific
datasets such as Golos for Russian" (§3.1). The set itself is not public: https://huggingface.co/api/datasets/nemo/asr-set-3.0
returns 401, although the card's metadata links it. So which subset formed the 7,500 fine-tuning hours is **UNVERIFIED**.

| Corpus | License basis | Evidence |
|---|---|---|
| LibriSpeech, MLS | CC-BY-4.0, LibriVox public-domain audio | (standard; MLS https://arxiv.org/abs/2012.03411) |
| Common Voice, FLEURS, VCTK, AMI | CC0 / CC-BY-4.0, recruited or volunteer readers | (standard) |
| Europarl-ASR | CC-BY-4.0 for UPV's work; EP audio reused under the EP legal notice | https://github.com/mllpresearch/Europarl-ASR ("LICENCE") |
| National Speech Corpus (Singapore) | Singapore Open Data Licence; recruited speakers | https://www2.imda.gov.sg/NationalSpeechCorpus |
| **Fisher** | **Paid, licensed LDC corpus**; recruited telephone participants who agreed to be recorded | https://catalog.ldc.upenn.edu/LDC2004S13 |
| Golos (Russian) | Custom Sber licence (PDF). Its terms were **not reviewed here** | https://github.com/salute-developers/golos |

**Overall provenance grade: DISCLOSED-SCRAPED (license-filtered), with a PARTIAL fine-tuning layer.** This is
better than distil-small.en (PARTIAL, core weights from Whisper's unnamed corpus): the sources are named, the YouTube
portions were filtered to CC, and there are no NC or ND datasets.

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled-scraped content in any layer. Fisher is paid but
**licensed, not scraped**. YouTube CC-flag misuse by individual uploaders is possible and **UNVERIFIED**.

### Layer 3: k2-fsa conversion. **N/A (no data)**

Format conversion and quantization only (see Artifact).

## Posture: **Structurally N/A for impersonation. Surveillance dual-use not discouraged.**

- ASR maps audio to text. It cannot generate, clone or imitate a voice, so Max's bar #2 cannot apply. It has no
  speaker-identification output either (the card's Output is "Text"; a community request for it is open at
  https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/discussions/4).
- **Weaker than Whisper's card on surveillance.** NVIDIA lists "voice analytics platforms" among its use cases and
  gives only generic "Trustworthy AI is a shared responsibility" language (card, "Use Case", "Ethical Considerations").
  It has no equivalent of OpenAI's "we caution against using Whisper models to transcribe recordings of individuals
  taken without their consent". That is a small demerit.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or
  sent (a hard rule of this project). The engine is hidden behind Developer engines.

## Org track record

- **NVIDIA: grey, with the most serious allegations of any org reviewed so far.** None concern Parakeet or Granary.
  - **Books piracy lawsuit (pending, allegations only).** *Nazemian v. NVIDIA* (N.D. Cal., filed 2024) alleges NVIDIA
    trained NeMo Megatron and other models on Books3, a pirated-books set taken from Bibliotik. The amended complaint
    (January 2026) cites internal correspondence showing NVIDIA contacted **Anna's Archive** for millions of pirated
    books after being told they were illegally obtained, and alleges downloads from LibGen, Sci-Hub and Z-Library.
    NVIDIA calls this fair use and says the contact does not prove the books were used.
    https://torrentfreak.com/nvidia-contacted-annas-archive-to-secure-access-to-millions-of-pirated-books/ ·
    https://www.heise.de/en/news/Nvidia-Court-documents-reveal-correspondence-regarding-pirated-dataset-11148122.html ·
    https://www.tomshardware.com/tech-industry/artificial-intelligence/nvidia-says-annas-archive-contact-does-not-show-pirated-books-were-used-to-train-its-ai
  - **Video scraping (2024).** Leaked Slack messages and e-mails show NVIDIA's Cosmos project downloading "a human
    lifetime visual experience worth of training data per day" from YouTube and Netflix, rotating IPs to avoid
    YouTube's blocks, with employees told it had clearance from the top.
    https://www.404media.co/nvidia-ai-scraping-foundational-model-cosmos-project/
  - **The contrast matters.** The speech team's Granary paper filters YouTube to CC licences, publishes its manifests
    and pipeline (https://github.com/NVIDIA/NeMo-speech-data-processor/tree/main/dataset_configs/multilingual/granary),
    and co-authors with the academic curators (CMU, FBK). That is materially better practice than the Cosmos and LLM
    allegations. Under Max's framework this is an org-level demerit, not a disqualifier for this model. Using the
    CC-BY weights on-device does not pay or feed NVIDIA.
  - No provenance or licensing question has been asked on the model's own community tab (49 threads, all technical:
    https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3/discussions) or found in the NeMo issue tracker
    (https://github.com/NVIDIA-NeMo/Speech/issues?q=granary). So there are no unanswered questions, and no answered ones
    either. The license has not changed since release, and the model has not been pulled.
- **CMU / ESPnet (YODAS):** mixed. Discloses the CC filter and offers removal on request, but withholds video IDs.
- **FBK (MOSEL) and PleIAs (YouTube-Commons):** positive. Open pipelines. YTC credits every channel and explicitly
  discusses the ethics of training on CC content.
- **k2-fsa / Next-gen Kaldi (Xiaomi-affiliated; csukuangfj):** converter and distributor only. The repo has no
  attribution or modification notice (see Artifact), which is a minor CC-BY hygiene gap on their side. Same
  maintainer as our Silero VAD and distil-small.en conversions.

## Feasibility (secondary)

About 670 MB downloaded (the encoder is 652 MB), offline only, 25 European languages with automatic language detection,
punctuation and capitals. It runs through sherpa-onnx's NeMo transducer `OfflineRecognizer`, the same session type as
the Whisper engines. The Open ASR Leaderboard average is 6.32% WER at RTFx 3,333 on an A100 (tech report, Table 5).
No Pixel 8a numbers yet. That is the benchmark this developer engine exists for (Marmalade STT issue #7;
the maintainer's benchmark plan). Nothing here is measured on a phone.

## Working demo link

https://huggingface.co/spaces/nvidia/parakeet-tdt-0.6b-v3. NVIDIA's own Gradio Space, stage **RUNNING** per
https://huggingface.co/api/spaces/nvidia/parakeet-tdt-0.6b-v3 on 2026-09-25. It runs the full-precision NeMo model, not
our int8 export. The Developer engines screen is the int8 demo.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **CC-BY-4.0, pass** | Card tag and prose agree, unchanged since release, no riders. k2-fsa repo adds no terms. Attribution plus modification notice required (NOTICE entry below) |
| Provenance | **DISCLOSED-SCRAPED (license-filtered); fine-tune layer PARTIAL** | Granary sources named and CC-filtered (YouTube CC, EP, LibriVox), but uploader-declared, not consented. YODAS IDs withheld. Labels by Whisper large-v3. NeMo ASR Set 3.0 only partly enumerated; it includes paid LDC Fisher and custom-licensed Golos |
| Posture | **N/A for impersonation. No surveillance caution** | ASR outputs text only. NVIDIA's card lists "voice analytics" and does not caution against non-consensual transcription |
| Org track record | **NVIDIA grey (serious, unrelated allegations); CMU mixed; FBK/PleIAs positive; k2-fsa neutral** | Books3 / Anna's Archive lawsuit (pending) and Cosmos YouTube/Netflix scraping. The speech team's data practice is markedly better |
| **Verdict vs Max's bar** | **PASS (grey provenance demerit, org demerit)** | Bar #1: no evidence of piracy in this model's data; Fisher licensed; YouTube CC-flag accuracy UNVERIFIED. Bar #2: N/A |

**Correction to prior art.** The maintainer's notes `parakeet.md` ("Ethics tier: clear … curated open dataset
from public, consented sources … one of the cleanest-provenance models") and `nvidia-nemo-asr.md` ("Morally clear
across the board", "Cleanest training data provenance of any major ASR family") overstate it. **Pass, grey
provenance** is accurate, and "consented" is wrong. `hf-asr-trending-2026-09.md`'s summary ("Nothing paywalled, but
YouTube-CC is only as honest as the uploaders' labels") is accurate. It omits the Whisper-labelled transcripts and the
LDC Fisher corpus. (`parakeet.md` also calls TDT "native streaming"; this checkpoint is used offline.) Those wiki
files are outside this repo and have not been edited here.

## NOTICE entry

**Attribution** (CC-BY-4.0 §3(a)(1); the license body is bundled once as `app/src/main/assets/licenses/CC-BY-4.0.txt`,
shared with other CC-BY-4.0 models)

```
Parakeet TDT 0.6B v3
Creator: NVIDIA (https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3). No copyright notice is supplied with the model.
License: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/legalcode
Modified: converted from NeMo to ONNX (transducer branch only) and quantized to int8 by the k2-fsa / sherpa-onnx
project, https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8 (commit 2bda32ec).
Provided as-is, without warranties; see Section 5 of the license.
```

The provenance statement is in `NOTICE.md` under "Downloadable speech models".
