# Model Ethics Review: NVIDIA canary-180m-flash (int8, k2-fsa export)

**Note 2026-10-02 (#45): Max made Canary a user engine.** The verdict below left that decision to him.

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the three files a **developer-only** engine downloads (hidden behind the Developer engines setting, for Max to
benchmark), used only to transcribe the user's own dictation on-device. Canary can also translate between en/es/de/fr;
we only run transcription (source language = target language). No generation, no cloning, no speaker identification.

## Verdict first

**GREY against Max's bar. Acceptable for the developer-only benchmark engine. Weaker provenance than distil-small.en, and
Marmalade's earlier "morally clear, trained on Granary" note is wrong for this model.**

- **Bar #1 (no piracy): no evidence that this model's training data was pirated or scraped from paywalled services.**
  It cannot be ruled out: **about 64% of the training hours (54k of 85k) come from undisclosed sources**, and the
  organisations behind them carry piracy allegations elsewhere (see Org track record).
- **Bar #2 (impersonation): N/A.** A speech recognizer or translator outputs text. It cannot clone or imitate a voice.
- **License: CC-BY-4.0.** Attribution is required, and so is a note that k2-fsa modified the weights. No GPL or LGPL anywhere.

**Caveats. Read these before calling this model "clean" anywhere:**

1. **Granary is not in this model.** The model card lists the training data as "31K hrs of public data, 20K hrs collected
   by [Suno](https://suno.ai/), and 34K hrs of in-house data". The weights were uploaded on 2025-03-12. NVIDIA's Granary
   dataset repo was created on 2025-05-15, two months later, and the card never mentions it. The maintainer's note
   `nvidia-nemo-asr.md` says the CC-BY NeMo models are "trained on NVIDIA's open Granary dataset" and
   "**Morally clear across the board**". That is wrong for canary-180m-flash. (Granary belongs to the later
   parakeet-tdt-0.6b-v3 / canary-1b-v2 generation.)
2. **The undisclosed 54k hours were questioned publicly and never explained.** Asked whether "the Suno dataset" is
   music lyrics and whether it is public, an NVIDIA engineer replied only "No its ASR data". The availability question
   was left unanswered. On the older canary-1b (same 85k-hour mix), a user pointed out that the Suno and in-house data
   are not public, and NVIDIA did not reply.
3. **Suno, the data partner, has since admitted to YouTube downloading for its music models.** In 2025–2026 court
   filings, Suno admitted obtaining YouTube audio with yt-dlp to train its music generator. The record labels call
   that "stream ripping" under DMCA §1201. **Nothing links those filings to the speech data Suno collected for NVIDIA.**
   But it is the same company, and it has never said where its NVIDIA speech data came from.

Blocking issues for the developer-only use: **none.** Before this model is offered to ordinary users, or chosen as a
default, Max should decide whether the opaque 54k hours are acceptable. The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8 at commit `9077164e0d3dd1d5353743e89ceaa1d3a770838c` ("first commit", 2025-07-07) | HF API: https://huggingface.co/api/models/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/revision/9077164e0d3dd1d5353743e89ceaa1d3a770838c |
| Files | `encoder.int8.onnx` 132,678,643 B, sha256 `7a75b4e2a5857a6dcc0819503bbe3fad66943db4a3ccf21d3f27c633667d303f`; `decoder.int8.onnx` 74,437,848 B, sha256 `e41a2ab9c0c2fe81a1e8ade5a45fb02a74bc4db7d1f91b89a54a25e2cf79cba2`; `tokens.txt` 53,555 B, sha256 `2dae6fc7815f9640645e0c765522b278ee0cef49b482d91f6913e334628d3e77` | The two ONNX hashes are the Git LFS oids in the HF tree API at that revision (https://huggingface.co/api/models/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8/tree/9077164e0d3dd1d5353743e89ceaa1d3a770838c). They match the locally recorded catalog listing (`catalog-raw.txt`). `tokens.txt` is not in LFS; hash from local `sha256sum` of the file downloaded at that revision |
| Encoder metadata | `model_type=EncDecMultiTaskModel`, `model_author=NeMo`, `url=https://huggingface.co/nvidia/canary-180m-flash`, `vocab_size=5248`, `feat_dim=128`, `subsampling_factor=8`, `normalize_type=per_feature` | read from the tail of the encoder's ModelProto (HTTP range request) |
| Decoder metadata | `onnx.infer=onnxruntime.quant`; parameters named `decoder._decoder.layers.0`–`3` (4 layers) | same method. 4 decoder layers matches the card ("17 encoder layers and 4 decoder layers") |
| `tokens.txt` | 5,248 lines. Canary's concatenated SentencePiece vocabulary plus special tokens (`<|nospeech|>`, `<|startoftranscript|>`, `<|pnc|>`, language tags …), one token and id per line | downloaded at the pinned revision |
| Upstream checkpoint | https://huggingface.co/nvidia/canary-180m-flash, `canary-180m-flash.nemo` (736,665,600 B, sha256 `7b97de18…12675`), uploaded once (commit `611632e7`, 2025-03-12), never changed. Repo head `b12ab418` (2025-03-18); every later commit touched only the README | HF API: https://huggingface.co/api/models/nvidia/canary-180m-flash/commits/main and https://huggingface.co/api/models/nvidia/canary-180m-flash/tree/main |
| Conversion | sherpa-onnx `scripts/nemo/canary/export_onnx_180m_flash.py` (header: "Copyright 2025 Xiaomi Corp. (authors: Fangjun Kuang)"). It loads `EncDecMultiTaskModel.from_pretrained("nvidia/canary-180m-flash")`, writes `tokens.txt` from the model's tokenizer, exports encoder and decoder to ONNX, then runs `quantize_dynamic(..., weight_type=QuantType.QUInt8)` and adds the metadata above | https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/nemo/canary/export_onnx_180m_flash.py; history: k2-fsa/sherpa-onnx PRs #2272 (2025-06-02), #2348, #2351, #2352 (2025-07-06/07) |

**What the k2-fsa export is.** It is a mechanical format conversion (NeMo/PyTorch → ONNX) plus dynamic 8-bit weight
quantization (unsigned `QUInt8`, although the files are named "int8"), done with the published checkpoint. There is no
retraining or fine-tuning. Under CC-BY-4.0 it is still **Adapted Material**: quantization changes the weights, so the
modification must be indicated (see NOTICE entry). The pinned HF repo has **no README, no LICENSE file and no license
tag** (tags: `onnx`, `region:us`). It adds no terms of its own, and NVIDIA's CC-BY-4.0 governs. sherpa-onnx's own
Apache-2.0 covers the export *code*, which does not ship with the model.

## License (weights / code)

- **Weights: CC-BY-4.0.** The card metadata says `license: cc-by-4.0`. The prose says "This model is released under the
  permissive CC-BY-4.0 license and is available for commercial use", and in "## License/Terms of Use": "canary-180m-flash
  is released under the CC-BY-4.0 license". Tag and prose agree, and have agreed since the first card commit (`a6fd0a24`,
  2025-03-12). There are no research-only or acceptable-use riders.
  - https://huggingface.co/nvidia/canary-180m-flash
  - https://huggingface.co/nvidia/canary-180m-flash/blob/a6fd0a24c7/README.md
  - Legal code: https://creativecommons.org/licenses/by/4.0/legalcode (the card links a choosealicense.com summary instead, which is harmless)
- **No copyright notice is supplied.** The card, the repo and the start of the `.nemo` archive carry no "Copyright …"
  line. The creator is NVIDIA (the card is published by the `nvidia` org, and every author of the model's papers is
  at NVIDIA). CC-BY-4.0 §3(a)(1)(A) requires keeping a copyright notice only "if supplied", so we credit the creator and
  **do not invent a © line**.
- **CC-BY-4.0 obligations** (§3(a)), for anyone Sharing the model or an adaptation of it:
  (A) keep the creator identification, any copyright notice, the license notice, the warranty-disclaimer notice and a link
  to the licensed material; (B) say whether you modified it; (C) say it is under CC BY 4.0 and include the license text
  or a link to it. §3(a)(2) allows doing this "in any reasonable manner". Marmalade STT does not redistribute the files:
  the app downloads them straight from k2-fsa's Hugging Face repo. We attribute anyway, because the app fetches and runs
  them, and the NOTICE entry below meets (A)–(C) in full.
- **Precedent: the same data mix was once NC.** The original canary-1b (2024-02, same "31k public + 20k Suno + 34k in-house"
  mix) is `cc-by-nc-4.0`. Asked to relax it, NVIDIA said "This is the best we could do for this model". Asked "Is it the
  training data that restricts usage?", it gave no answer. The Flash models (2025-03) are CC-BY-4.0. The license change
  is not evidence about the data either way, but the question was left open.
  - https://huggingface.co/api/models/nvidia/canary-1b (license `cc-by-nc-4.0`)
  - https://huggingface.co/nvidia/canary-1b/discussions/11
- **GPL/LGPL: none** in the weights, `tokens.txt` or the export path.

## Provenance (training data)

The card (section "# Training and Evaluation Datasets") says: "The canary-180m-flash model is trained on a total of 85K
hrs of speech data. It consists of 31K hrs of public data, 20K hrs collected by [Suno](https://suno.ai/), and 34K hrs of
in-house data. The datasets below include conversations, videos from the web, and audiobook recordings." It gives
"Data Collection Method: Human" and "Labeling Method: Hybrid: Human, Automated". The Flash paper
(https://arxiv.org/abs/2503.05931, section "Experimental setup") says the training data is the same as in Canary-1B's paper
(Puvvada et al., "Less is More", Interspeech 2024, https://www.isca-archive.org/interspeech_2024/puvvada24_interspeech.pdf, §3).
That paper also lists 300 h of non-speech audio from AudioSet for robustness. The speech-translation targets are
**synthetic**: machine translations of the ASR transcripts made with NVIDIA's own NMT models. That does not matter here,
because we only transcribe.

`https://suno.ai` redirects to `https://suno.com/` (checked 2026-09-25), the AI music company. Suno also co-developed
NVIDIA's Parakeet ASR models ("jointly developed by NVIDIA NeMo and Suno.ai teams"; "private subset with 40K hours of
English speech", https://huggingface.co/nvidia/parakeet-rnnt-1.1b).

### Layer 1: public portion, about 31k h. **DISCLOSED, mixed licenses**

| Language | Named corpora (card and Puvvada §3) | License notes |
|---|---|---|
| English (25.5k h) | LibriSpeech 960 h; Fisher; Switchboard-1; WSJ-0/WSJ-1; National Speech Corpus (parts 1, 6); VCTK; VoxPopuli EN; Europarl-ASR EN; MLS EN (2k h); Common Voice 7.0; People's Speech (12k h); Common Voice 11.0 (1.5k h) | Fisher, Switchboard and WSJ are **paid LDC corpora**: licensed, not scraped (e.g. https://catalog.ldc.upenn.edu/LDC2004S13, https://catalog.ldc.upenn.edu/LDC97S62). People's Speech: CC-BY 2.0–4.0 and CC-BY-SA 3.0/4.0 (https://huggingface.co/datasets/MLCommons/peoples_speech). Europarl-ASR: CC BY 4.0, © 2021 UPV (https://github.com/mllpresearch/Europarl-ASR). MLS: CC-BY-4.0 (https://huggingface.co/datasets/facebook/multilingual_librispeech). Common Voice: CC0, consenting volunteers. VoxPopuli: CC0 (https://huggingface.co/datasets/facebook/voxpopuli). National Speech Corpus: Singapore IMDA corpus; its licence was not re-checked for this review (UNVERIFIED) |
| German (2.5k h) | Common Voice 12.0 (800 h), MLS DE (1.5k h), VoxPopuli DE (200 h) | CC0 / CC-BY-4.0 / CC0 |
| Spanish (1.4k h) | Common Voice 12.0 (395 h), MLS ES (780 h), VoxPopuli ES (108 h), Fisher Spanish (141 h) | as above; Fisher Spanish is LDC |
| French (1.8k h) | Common Voice 12.0 (708 h), MLS FR (926 h), VoxPopuli FR (165 h) | as above |

This layer is well named. There are no NC corpora in it, unlike distil-small.en's TED-LIUM and GigaSpeech. The paid LDC
corpora are the only licensing grey: whether NVIDIA's LDC licence permits releasing a model under CC-BY is **UNVERIFIED**.
None of it is piracy. One side note: the Puvvada paper says the German, Spanish and French data "were collected from
public domain". That is loose wording (MLS is CC-BY, Fisher is LDC), not a misstatement of sources.

### Layer 2: "20K hrs collected by Suno". **OPAQUE**

- There is no dataset name, source, consent statement or licence. The only description is the card's shared sentence
  about "conversations, videos from the web, and audiobook recordings", which is not broken down by source.
- **Direct question, half-answered.** https://huggingface.co/nvidia/canary-1b-flash/discussions/7 (2025-03-29): "Is the
  Suno dataset for transcribing music lyrics and is it publicly available?" NVIDIA's Nithin Rao Koluguri replied
  (2025-03-31): "No its ASR data". Nothing about the source or availability followed; the thread is closed.
- https://huggingface.co/nvidia/canary-1b/discussions/11 (2024-05-14), community: "It turns out that some training data
  is not freely available … So it is actually not possible to replicate the training". NVIDIA did not reply.
- Whether any of it came from YouTube or other "videos from the web", and whether that involved paywalled content, is
  **UNVERIFIED**. The Suno court admissions (Org track record) are about Suno's *music* training data, not this.

### Layer 3: "34K hrs of in-house data". **OPAQUE**

- There is no description beyond the shared sentence above. The Puvvada paper's Table 1 gives only per-language totals
  ("Public + In-house [K hours]": EN 25.5 + 37.9, DE 2.5 + 3.6, ES 1.4 + 5.2, FR 1.8 + 3.3; 50k in-house in total, with
  Suno's share not separated). Note that the card's split (20k + 34k = 54k) and the paper's 50k disagree slightly, which
  is another small sign of loose bookkeeping.
- A search of NVIDIA's NeMo issue tracker (now `NVIDIA-NeMo/Speech`) found no question or answer about the Suno or
  in-house data (`gh api search/issues` for "suno", "canary in-house data" and "canary training data" on 2026-09-25; the
  only "suno" hit is the Parakeet blog post, https://github.com/NVIDIA-NeMo/Speech/pull/8269).

### Layer 4: k2-fsa conversion. **N/A (no data)**

Format conversion and quantization only (see Artifact).

**Overall provenance grade: PARTIAL, leaning OPAQUE.** 36% of the hours are named public or licensed corpora. 64% are
unnamed, and NVIDIA sidestepped the direct question. This is worse than distil-small.en: Whisper's data is also unnamed,
but OpenAI at least says it was scraped from the web ("DISCLOSED-SCRAPED"). NVIDIA and Suno say nothing about their source.

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled-scraped content in *this* model. The LDC corpora
are licensed, not scraped. The Suno and in-house sources are **UNVERIFIED**.

## Posture: **Structurally N/A for impersonation. Surveillance risk not addressed.**

- ASR and speech translation map audio to text. The model cannot generate, clone or imitate a voice, so Max's bar #2
  cannot apply. The card and the k2-fsa repo use corpus and test clips (LibriSpeech, `test_wavs/en.wav`, `de.wav`).
  No celebrity or character demos exist.
- The misuse risk that matters for ASR is surveillance: transcribing people without their consent. **The card says nothing
  about it.** Its "Ethical Considerations" section is NVIDIA's generic boilerplate ("Trustworthy AI is a shared
  responsibility … developers should work with their internal model team …"). That is weaker than OpenAI's Whisper card,
  which cautions explicitly against transcribing people without consent. A demerit, but a mild one.
- The card does report a fairness evaluation by gender and age on Meta's Casual Conversations set (WER 16.9% male vs
  14.0% female; 14.95–15.65% across age bands). That is a positive sign of care.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or sent (a hard rule of this project).

## Org track record

- **NVIDIA (model creator): grey, with serious allegations elsewhere.**
  - Positive for this model family: two papers, public training code and configs
    (https://github.com/NVIDIA/NeMo/blob/main/examples/asr/speech_multitask/speech_to_text_aed.py), a named public-data
    list, a fairness evaluation, a license that became *more* open over time (canary-1b NC → Flash CC-BY), and a later
    move to a fully open speech dataset (Granary, CC-BY-4.0, https://huggingface.co/datasets/nvidia/Granary).
  - Negative, org level: leaked documents reported by 404 Media (2024-08) describe NVIDIA downloading YouTube and Netflix
    video at scale, using IP rotation to avoid YouTube's blocks, to train its Cosmos video models
    (https://www.404media.co/nvidia-ai-scraping-foundational-model-cosmos-project/). Netflix is a paid service, so this would
    fall under Max's bar #1 if it touched a model we used. It concerns video models, not ASR. An authors' class action
    (Nazemian v. NVIDIA, N.D. Cal.) alleges NVIDIA trained LLMs on Books3 and other shadow libraries. A January 2026 amended
    complaint quotes internal emails about seeking bulk "high-speed access" to Anna's Archive
    (https://torrentfreak.com/nvidia-contacted-annas-archive-to-secure-access-to-millions-of-pirated-books/). NVIDIA denies
    wrongdoing and moved to dismiss (https://torrentfreak.com/nvidia-contact-with-annas-archive-doesnt-prove-copyright-infringement/).
    The outcome is **UNVERIFIED** as of this review. **None of this concerns the speech corpora**, but it is a real
    org-level demerit under Max's framework, more serious than OpenAI's.
- **Suno (data partner): negative.** UMG and Sony are suing Suno for copyright infringement over its music models (D. Mass.,
  filed 2024). The court allowed them to add a DMCA §1201 "stream ripping" claim in August 2025
  (https://www.musicbusinessworldwide.com/umg-and-sony-accuse-suno-of-pirating-youtube-via-stream-ripping-to-train-its-ai/).
  In a September 2026 filing Suno admitted "that audio data was obtained from YouTube for use as training data using YT-DLP"
  (https://www.musicbusinessworldwide.com/suno-admits-it-obtained-youtube-audio-to-train-its-ai-but-challenges-umg-and-sonys-standing-to-bring-stream-ripping-claim/).
  This is about music, not the ASR data it collected for NVIDIA. But it shows how this company has sourced audio, and it
  has never described the 20k hours.
- **k2-fsa / Next-gen Kaldi (Xiaomi-affiliated; csukuangfj):** converter and distributor only. No concerns. The same
  maintainer supplied our Silero VAD conversion and the distil-small.en export (see `silero-vad.md`, `retired/distil-small-en.md`).

## Feasibility (secondary)

- Download: about 207 MB (132.7 + 74.4 MB + 54 kB), CPU int8. That is about 30% smaller than distil-small.en's 299 MB.
- Our pinned sherpa-onnx v1.13.8 (`docs/ARCHITECTURE.md` D3) includes the Canary runtime and its Kotlin API
  (`third_party/sherpa-onnx/CHANGELOG.md`: #2352 C++ runtime, #2359 Java/Kotlin API, #3920 empty-transcript fix).
- Card accuracy (Open ASR Leaderboard sets, no PnC): the eight-set mean is **7.12% WER** (LS clean 1.87, LS other 3.83,
  AMI 14.86, Earnings22 13.33). MLS WER is 4.81 de / 3.17 es / 4.75 fr. These numbers are self-reported and for the
  fp32 model; the int8 cost is unmeasured. It outputs punctuation and capitals.
- The maintainer's note `hf-asr-trending-2026-09.md` ("LFM (Liquid AI) and MiniCPM check") records that
  Liquid AI's LFM2.5-Audio uses canary-180m-flash as its audio encoder, and flagged this sherpa build as a possible tiny engine.

## Working demo link

No working hosted demo of canary-180m-flash itself was found (checked 2026-09-25):
- https://huggingface.co/spaces/Huggingfacest/canary-180m-flash is in RUNTIME_ERROR.
- The k2-fsa ASR Space (https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition) does not include Canary models.
- **Closest working demo:** https://huggingface.co/spaces/nvidia/canary-1b-flash (RUNNING). It is the 1B sibling, trained on
  the same 85k-hour mix, so it shows the family's behaviour but not this model's accuracy.

For an STT model, "trying it" means dictating into it, and the Developer engines setting exists for exactly that.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **CC-BY-4.0, pass** | Card tag and prose agree since the first commit. No copyright notice supplied. Attribution plus a modification notice required. k2-fsa repo adds no terms |
| Provenance | **PARTIAL, leaning OPAQUE** | 31k h named public/LDC corpora, none NC. 54k h (Suno 20k, in-house 34k) unnamed. The direct question was deflected ("No its ASR data"). Not Granary |
| Posture | **N/A for impersonation. Surveillance not addressed** | ASR/translation outputs text only. The card's ethics section is generic boilerplate. It does include a fairness evaluation |
| Org track record | **NVIDIA grey (serious allegations elsewhere), Suno negative, k2-fsa neutral** | Cosmos YouTube/Netflix scraping reports. Nazemian v. NVIDIA (Books3, Anna's Archive). Suno admits yt-dlp YouTube downloads for music training |
| **Verdict vs Max's bar** | **GREY: OK for the developer-only engine; Max decides before any user-facing use** | Bar #1: no evidence of piracy in this model, but most of the data is undisclosed and comes from orgs with piracy allegations. Bar #2: N/A |

**Correction to prior art.** The maintainer's note `nvidia-nemo-asr.md` (created 2026-04-01) grades the CC-BY
NeMo family, canary-180m-flash included, "morally clear" because of "transparent training data (Granary + NeMo ASR Set 3.0)"
and "Cleanest training data provenance of any major ASR family". For canary-180m-flash that is wrong: its card names Suno
and in-house data, not Granary, and Granary postdates it. The note has not been edited here (out of scope for this review).

## NOTICE entry

**Attribution** (CC-BY-4.0 §3(a)(1); no copyright notice is supplied upstream, so none is invented; the license body is
bundled as `app/src/main/assets/licenses/CC-BY-4.0.txt`)

```
canary-180m-flash by NVIDIA (https://huggingface.co/nvidia/canary-180m-flash),
licensed under CC BY 4.0 (https://creativecommons.org/licenses/by/4.0/).
Modified: converted to ONNX and weight-quantized to 8 bits by the k2-fsa /
sherpa-onnx project (https://huggingface.co/csukuangfj/sherpa-onnx-nemo-canary-180m-flash-en-es-de-fr-int8).
Provided as-is, without warranties (CC BY 4.0, Section 5).
```

**License:** CC-BY-4.0 (standalone body, shared). The provenance statement is in `NOTICE.md` under "Downloadable speech models".
