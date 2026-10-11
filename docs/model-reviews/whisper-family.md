# Model Ethics Review: OpenAI Whisper tiny.en, base.en, small.en and large-v3-turbo (int8, k2-fsa exports)

**Note 2026-10-02 (#45): tiny.en, base.en, small.en and distil-small.en left the app; large-v3-turbo became a user engine** (Max's decision, for the languages no other engine covers). This review's verdict below asks for a re-review of turbo before it becomes user-facing; that re-review hasn't been done yet.

Reviewed 2026-09-25 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the three files per model that the Engines screen downloads for four **developer engines**. They are hidden
behind the Developer-engines setting so Max can benchmark them. They are used only to transcribe the user's own dictation
on-device. No generation, no cloning, no speaker identification.

One review covers all four. They come from the same org, the same paper, the same license and the same k2-fsa export
script. The data story is shared too, except that turbo adds a much larger and less-disclosed layer. Whisper's
original provenance is examined in depth in [`retired/distil-small-en.md`](retired/distil-small-en.md). This file reuses that work, cites
it, and re-verifies each artifact's own facts.

## Decision (Max, 2026-10-02)

**large-v3-turbo approved as a user-facing engine** after the grey provenance (undisclosed source for ~80 % of its training hours; the unverified NYT YouTube report) was explained to him: "Whisper large is ok for now". This settles the re-review checkpoint below for now; revisit if OpenAI's disclosure or the evidence changes.

## Verdict first

| Model | Verdict vs Max's bar |
|---|---|
| tiny.en | **PASS (grey provenance demerit)**, the same band as distil-small.en |
| base.en | **PASS (grey provenance demerit)**, the same band as distil-small.en |
| small.en | **PASS (grey provenance demerit)**, the same band as distil-small.en. distil-small.en already runs 53% of this model's weights unchanged |
| large-v3-turbo | **GREY. Acceptable as a developer-only benchmark engine. Re-review before it becomes a user-facing or default engine.** About 80% of its training hours have a disclosed scale and no disclosed source |

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped training data in any of the four.** It cannot be
  ruled out. OpenAI has never named Whisper's sources.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: MIT, "Copyright (c) 2022 OpenAI"**, for all four, including turbo. The k2-fsa repositories add no terms. No
  GPL or LGPL anywhere. The bundled `app/src/main/assets/licenses/whisper.txt` is the right text.

**Caveats:**

1. **The three `.en` models share Whisper's original lineage exactly:** OpenAI's 680,000 hours of "audio paired with
   transcripts on the Internet", with no named sources. This is DISCLOSED-SCRAPED, and the direct question went unanswered
   (see [`retired/distil-small-en.md`](retired/distil-small-en.md), "Layer 1").
2. **Turbo is worse on disclosure.** It is large-v3 with its decoder cut from 32 layers to 4, then fine-tuned. large-v3 was
   trained on "1 million hours of weakly labeled audio and 4 million hours of pseudolabeled audio collected using
   `large-v2`". OpenAI has never said where the 4 million hours of audio came from. No paper, no card section, no reply
   to the question when it was asked. The skill's pitfall applies: "Scale disclosed, sourcing not — that is NOT
   disclosure." OpenAI's own `model-card.md` still describes only the 680k-hour set. The Transformers card for turbo
   says "Training Data: No information provided."
3. **Circumstantial concern for turbo, UNVERIFIED:** the NYT reported (2024-04) that OpenAI used Whisper to transcribe over
   a million hours of YouTube for GPT-4, against YouTube's terms. Pseudo-labelling audio with large-v2 is the same
   technique. Whether large-v3's 4M pseudo-labelled hours are that audio, or overlap with it, is **UNVERIFIED**. No source
   links them. YouTube is public, not paywalled. So even if they were the same audio, it would be a terms-of-service
   and org-conduct demerit, not piracy under Max's bar.

Blocking issues for developer-only use: **none.** The final adoption decision is Max's.

## Artifact under review

All four are published by k2-fsa maintainer Fangjun Kuang (`csukuangfj`). Each HF repo's `main` head **is** the pinned
revision (checked 2026-09-25), so the Engines screen and the pinned URLs fetch the same bytes.

| Model | Distribution point (repo @ pinned commit) | Evidence |
|---|---|---|
| tiny.en | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny.en @ `d026532c022fa99fd789d6b32446a1df7b6bfc43` ("upload tiny.en", 2024-07-13) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-tiny.en/revision/d026532c022fa99fd789d6b32446a1df7b6bfc43 |
| base.en | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base.en @ `59eea950fc76df2453efb57e6c0fd334548e8ffe` ("upload base.en", 2024-10-02) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-base.en/revision/59eea950fc76df2453efb57e6c0fd334548e8ffe |
| small.en | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small.en @ `d9533f69affd85061aee349af7fea5cb2996dbbe` ("upload small.en", 2024-07-13) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-small.en/revision/d9533f69affd85061aee349af7fea5cb2996dbbe |
| turbo | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-turbo @ `2ca6ff69fc878651b770880507669577ac41c2ff` ("upload turbo", 2024-10-02) | https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-turbo/revision/2ca6ff69fc878651b770880507669577ac41c2ff |

**Files we download** (sizes and SHA-256 from the HF API's LFS metadata, recorded in a
local catalog listing, `catalog-raw.txt`; the tokens files hashed locally from the pinned `resolve/` URLs):

| File | Bytes | SHA-256 |
|---|---|---|
| `tiny.en-encoder.int8.onnx` | 12,937,772 | `0ce578b827c94a961aacb8fa14b02f096504b337e5c94be37c36238cbe3e8bc6` |
| `tiny.en-decoder.int8.onnx` | 89,853,865 | `06c0e6ff6348d427e51839219d1c886c18cfdf411e629e33f5e1679bff9c1527` |
| `tiny.en-tokens.txt` | 835,554 | `306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930` |
| `base.en-encoder.int8.onnx` | 29,120,534 | `ef6b936f4c9b1d90a3b68634b60c4ed8576b26172b33c2535ec0e933c9edb823` |
| `base.en-decoder.int8.onnx` | 130,669,978 | `f7162ad6db2dbef16cfaeaa7f945b9d7dd9c1b8d472f6aca82f2273d185e4d41` |
| `base.en-tokens.txt` | 835,554 | `306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930` |
| `small.en-encoder.int8.onnx` | 112,442,483 | `8bdac288f369aa94ee2194059238c465ed82ea9d47ee8fa4a8c0a891873e462f` |
| `small.en-decoder.int8.onnx` | 262,223,042 | `710ccf890e10f3faa15f51ec346081a2723c9f3adb6e4da81c6573a5a6f877fb` |
| `small.en-tokens.txt` | 835,554 | `306cd27f03c1a714eca7108e03d66b7dc042abe8c258b44c199a7ed9838dd930` |
| `turbo-encoder.int8.onnx` | 674,716,297 | `b02dcdf54f348741e93fe732b67d933c8dcb6735655f710640143081db38878b` |
| `turbo-decoder.int8.onnx` | 361,080,764 | `20accd02388482eb3a46bd615631adfdc85e1eb2c7db9ea3f02a40ffe6b81547` |
| `turbo-tokens.txt` | 816,730 | `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |

The three English tokens files are byte-identical to each other and to `distil-small.en-tokens.txt` (same SHA-256).
That file is Whisper's English GPT-2 BPE vocabulary, 50,256 lines. `turbo-tokens.txt` is Whisper's multilingual vocabulary,
50,257 lines. Both come from the tokenizer files in the MIT-licensed openai/whisper repo.

**ONNX metadata**, read from each encoder's ModelProto for this review. tiny.en and base.en were read from local
copies; small.en and turbo were read over HTTP range requests on the pinned URLs. All four have producer
`onnx.quantize` 0.1.0, ONNX IR 7, `onnx.infer=onnxruntime.quant` and `maintainer=k2-fsa`:

| Model | `model_type` | audio layers × width | text layers | `n_mels` | `n_vocab` | `is_multilingual` |
|---|---|---|---|---|---|---|
| tiny.en | `whisper-tiny.en` | 4 × 384 | 4 | 80 | 51864 | 0 |
| base.en | `whisper-base.en` | 6 × 512 | 6 | 80 | 51864 | 0 |
| small.en | `whisper-small.en` | 12 × 768 | 12 | 80 | 51864 | 0 |
| turbo | `whisper-turbo` | 32 × 1280 | **4** | **128** | 51866 | **1** |

These shapes match OpenAI's published architectures. turbo's 32-layer encoder, 4-layer decoder and 128 mel bins are
exactly what OpenAI announced for large-v3-turbo (see Provenance).

**What the k2-fsa export is.** sherpa-onnx `scripts/whisper/export-onnx.py` (header: "Copyright 2023 Xiaomi Corp. (authors:
Fangjun Kuang)") calls `whisper.load_model(name)` for these four names
(https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/whisper/export-onnx.py). That is openai-whisper's own loader.
It downloads **OpenAI's original checkpoints** from OpenAI's CDN and checks each one's SHA-256 against the URL table in
`whisper/__init__.py`: `tiny.en.pt`, `base.en.pt`, `small.en.pt`, and `large-v3-turbo.pt` (the name `turbo` is an alias
for the same URL) (https://github.com/openai/whisper/blob/main/whisper/__init__.py). The script exports encoder and
decoder with `torch.onnx.export`, writes the metadata above, then runs `quantize_dynamic(..., op_types_to_quantize=["MatMul"],
weight_type=QuantType.QInt8)`. There is no retraining or fine-tuning.

None of the four repos has a README, LICENSE file or license tag at the pinned revision. Their tags are `onnx`,
`region:us`. tiny.en once had a README (commit `da2b5c96`, 2023-12-18), but it is absent at the pin. So the repos add no
terms of their own, and the upstream MIT terms govern. None has community discussions (for example
https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-turbo/discussions returns zero).

**Turbo's fp32 external-data file is not needed.** The repo also holds `turbo-encoder.onnx` (735,920 B) plus
`turbo-encoder.weights` (2,600,325,120 B), because the export script saves large and turbo fp32 graphs with external
data. `quantize_dynamic` is called without `use_external_data_format`, so the int8 files should be self-contained. The
size fits: 674.7 MB is about one byte per encoder parameter. That the app loads turbo from just the two int8 files is
**to be confirmed on device**.

## License (weights / code)

- **OpenAI Whisper: MIT, "Copyright (c) 2022 OpenAI"**, for all four checkpoints.
  - README: "Whisper's code and model weights are released under the MIT License."
    https://github.com/openai/whisper/blob/main/README.md ("## License")
  - LICENSE has exactly one commit ("initial commit", 2022-09-21, `6e3be77e`) and has never changed.
    https://github.com/openai/whisper/blob/6e3be77e1a105e59086e3e21ff5f609fd6fa89a5/LICENSE,
    https://api.github.com/repos/openai/whisper/commits?path=LICENSE
  - **Turbo is covered.** large-v3-turbo was released into the same repo by commit `25e5c364` ("large-v3-turbo model (#2361)",
    2024-09-30), which added it to the `whisper/__init__.py` model table. The unchanged LICENSE and the README's
    "code and model weights … MIT" statement both apply to it. The README also documents `turbo` as a model name in
    the same repo. OpenAI's own Hugging Face release agrees: `openai/whisper-large-v3-turbo` has `license: mit` in its
    card metadata (https://huggingface.co/api/models/openai/whisper-large-v3-turbo; card
    https://huggingface.co/openai/whisper-large-v3-turbo).
  - **So the bundled `whisper.txt` ("Copyright (c) 2022 OpenAI") is the right text for all four.** No second license text is needed.
- **Tag oddity, harmless:** OpenAI's Transformers conversions of tiny.en, base.en, small.en and large-v3 on HF are tagged
  `apache-2.0` (https://huggingface.co/api/models/openai/whisper-tiny.en,
  https://huggingface.co/api/models/openai/whisper-base.en, https://huggingface.co/api/models/openai/whisper-large-v3),
  but turbo's is tagged `mit`. We do not use the Transformers conversions. k2-fsa exports from OpenAI's original `.pt`
  files, whose governing statement is the MIT README. The same oddity is noted in [`retired/distil-small-en.md`](retired/distil-small-en.md).
- **Training-data licenses:** OpenAI names no datasets, so none can be checked for inheritance. This is a provenance gap,
  graded below, not a license finding.
- **GPL/LGPL: none** in the weights, the tokens files or the export path.

## Provenance (training data)

### tiny.en, base.en, small.en: **DISCLOSED-SCRAPED, sources unnamed**

All three belong to the original September 2022 series. Model card, "Release date": "September 2022 (original series)"
(https://github.com/openai/whisper/blob/main/model-card.md). They were trained on the 680,000-hour set: "The models are
trained on 680,000 hours of audio and the corresponding transcripts collected from the internet" (model card, "Training
Data"). The paper says the dataset was constructed "from audio that is paired with transcripts on the Internet", with
heuristics to remove machine-generated transcripts. It names no sites (https://arxiv.org/abs/2212.04356, §2.1).

The full evidence is in [`retired/distil-small-en.md`](retired/distil-small-en.md), "Layer 1", and it applies here unchanged:
- The direct question went unanswered by OpenAI (https://github.com/openai/whisper/discussions/21).
- The hallucinated outros ("Thanks for watching", Amara.org and broadcaster subtitle credits) point to subtitled public
  web video (https://github.com/openai/whisper/discussions/928).
- There is no evidence of paywalled or pirated sources. Their absence is **UNVERIFIED**.

The English-only (`.en`) models were trained on the English part of that set: 438,000 hours of English audio with
English transcripts, per the model card's 65% split. Which subset each `.en` model saw exactly is not stated beyond
"English-only". Under Max's bar, admitted public-web sourcing is acceptable, and the opacity is a demerit.

### large-v3-turbo: **1M h DISCLOSED-SCRAPED by method; 4M h OPAQUE (scale only)**

The lineage has three steps, each from OpenAI's own words:

1. **large-v3 (Nov 2023).** "The `large-v3` model is trained on 1 million hours of weakly labeled audio and 4 million hours
   of pseudolabeled audio collected using `large-v2`. The model was trained for 2.0 epochs over this mixture dataset."
   Release post: https://github.com/openai/whisper/discussions/1762. HF card, same wording:
   https://huggingface.co/openai/whisper-large-v3 ("Training Data").
2. **turbo (Sep/Oct 2024).** "It is an optimized version of Whisper `large-v3` and has only 4 decoder layers … down from
   the 32 in the `large` series … Whisper turbo was fine-tuned for two more epochs over the same amount of multilingual
   transcription data used for training `large-v3`, i.e. excluding translation data." Release post:
   https://github.com/openai/whisper/discussions/2363. HF card: "a finetuned version of a pruned Whisper large-v3 …
   the exact same model, except that the number of decoding layers have reduced from 32 to 4"
   (https://huggingface.co/openai/whisper-large-v3-turbo). Its "Training Data" section says: "No information provided."
3. **k2-fsa conversion.** Format conversion and int8 quantization only (see Artifact).

What is and is not disclosed:
- **"Weakly labeled" (1M h)** is the paper's term for its internet audio-plus-transcript method. So this part is most likely more of the same
  kind of data as the 680k set. That is an inference: OpenAI does not say it is the same pipeline. **UNVERIFIED.**
- **"Pseudolabeled audio collected using large-v2" (4M h, 80% of the hours)** means the *transcripts* were
  machine-generated by large-v2. **Where the audio came from is not stated anywhere.** There is no v3 or turbo paper
  or tech report, and the repo's `model-card.md` was never updated past the 680k-hour description. Questions under the
  release post went unanswered by OpenAI:
  - One asked how the pseudo-labelled data was weighted and whether a paper would follow
    (https://github.com/openai/whisper/discussions/1762#discussioncomment-7491790, 2023-11-06). It has no replies.
  - One asked what "weakly labeled" vs "pseudolabeled" means
    (https://github.com/openai/whisper/discussions/1762#discussioncomment-8910353). Only a community member replied, speculating about YouTube.
  - GitHub issue search for large-v3 or turbo training-data questions returns nothing
    (https://api.github.com/search/issues?q=repo:openai/whisper+large-v3+training+data).
- **"The same amount of" data for turbo**: OpenAI says the same *amount*, not explicitly the same data. This is
  most plausibly the large-v3 transcription mix. **UNVERIFIED.**
- **Circumstantial, UNVERIFIED:** the NYT reported that OpenAI used Whisper to transcribe more than a million hours of
  YouTube video for GPT-4 training, knowing it could come under scrutiny
  (https://www.techmeme.com/240406/p5, https://gizmodo.com/openai-chatgpt-google-youtube-videos-train-gpt4-1851394676).
  Transcribing unlabeled audio with Whisper is exactly what "pseudolabeled audio collected using large-v2" describes.
  No source says the two are the same audio. Even if they are, YouTube is public, not paywalled: it would be a
  terms-of-service breach and an org-conduct demerit, not piracy under bar #1.

**Grade: OPAQUE for 80% of turbo's hours.** This is weaker than the `.en` models. Admitted web sourcing (the 680k set)
outranks silence, and for the 4M hours OpenAI disclosed only a number.

**Piracy check (Max's bar #1), all four:** no evidence of pirated or paywalled-scraped content. The specific sources are
**UNVERIFIED** for all four, and more so for turbo.

## Posture: **Structurally N/A for impersonation. Surveillance dual-use verbally discouraged.**

- ASR maps audio to text. It cannot generate, clone or imitate a voice, so Max's bar #2 cannot apply. The demo
  material is LibriSpeech-style read speech: `test_wavs/` in each k2-fsa repo, and `distil-whisper/librispeech_long` on
  turbo's HF card. No celebrity or character demos exist.
- OpenAI's model card: "we caution against using Whisper models to transcribe recordings of individuals taken without
  their consent". It names the surveillance dual-use risk, and says the models "may have some capabilities to recognize
  specific individuals out of the box" (https://github.com/openai/whisper/blob/main/model-card.md, "Evaluated Use" and "Broader
  Implications"; the same text is on the turbo HF card). That is verbal discouragement, the best available for an open ASR model.
- **Hallucination is a known harm for transcription.** An AP investigation (2024-10) and the peer-reviewed "Careless
  Whisper" study found Whisper inventing text, some of it harmful, mostly over silences and pauses
  (https://fortune.com/2024/10/26/openai-transcription-tool-whisper-hallucination-rate-ai-tools-hospitals-patients-doctors,
  https://arxiv.org/abs/2402.08021). It is not an ethics-of-provenance issue, but it matters for a dictation app. Our VAD
  gating (Silero, only speech segments reach the recognizer) is the main mitigation. It is also a reason to keep these
  models developer-only until benchmarked.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or sent (a hard rule of this project).

## Org track record

- **OpenAI: grey**, exactly as in [`retired/distil-small-en.md`](retired/distil-small-en.md) ("Org track record"):
  - The Whisper weights have been MIT since release. None has been relicensed or pulled, including turbo.
  - OpenAI reportedly transcribed YouTube for GPT-4 against YouTube's terms (above).
  - OpenAI is a defendant in copyright suits over LLM training data
    (https://en.wikipedia.org/wiki/The_New_York_Times_v._Microsoft_and_OpenAI).

  The new point for this family: OpenAI stopped publishing data details after the 2022 paper. Two further model
  generations shipped with a data *size* and no data *description*. Using the MIT weights on-device does not feed OpenAI.
- **k2-fsa / Next-gen Kaldi (Xiaomi-affiliated; csukuangfj):** converter and distributor only. No concerns. The same
  maintainer supplied our Silero VAD and distil-small.en conversions (see `silero-vad.md`, `retired/distil-small-en.md`).

## Feasibility (secondary)

All four run through sherpa-onnx's Whisper `OfflineRecognizer`, the path distil-small.en already uses. Download
totals:

| Model | Download |
|---|---|
| tiny.en | 103.6 MB |
| base.en | 160.6 MB |
| small.en | 375.5 MB |
| turbo | 1,036.6 MB |

turbo is multilingual (`is_multilingual=1`, 128 mel bins) and slow on a phone. OpenAI's README says it "is not
trained for translation tasks". The app uses it for transcription only. The `.en` models are English-only. Phone
performance is for #13's benchmark, not this review.

## Working demo link

- **tiny.en, base.en, small.en:** https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition (RUNNING,
  2026-09-25, https://huggingface.co/api/spaces/k2-fsa/automatic-speech-recognition). Its `model.py` loads **these exact
  int8 files** (`csukuangfj/sherpa-onnx-whisper-{tiny.en,base.en,small.en}`, `*-encoder.int8.onnx`,
  `*-decoder.int8.onnx`, `*-tokens.txt`) through `sherpa_onnx.OfflineRecognizer.from_whisper`. Pick "English" and a
  `whisper-*.en` model, then record or upload.
- **turbo:** no running demo of turbo itself was found. `hf-audio/whisper-large-v3-turbo` is PAUSED
  (https://huggingface.co/api/spaces/hf-audio/whisper-large-v3-turbo). The closest running demo is
  https://huggingface.co/spaces/openai/whisper, which runs **large-v3**, turbo's 32-decoder-layer parent. That is not the same model.
  The best test is the developer engine itself on the Pixel.

## Verdict

| Model | License (weights) | Provenance | Posture | Org | **Verdict vs Max's bar** |
|---|---|---|---|---|---|
| tiny.en @ `d026532c` | **MIT, pass** | **DISCLOSED-SCRAPED** (680k h, unnamed web sources) | N/A for impersonation. Surveillance risk verbally discouraged | OpenAI grey, k2-fsa neutral | **PASS (grey provenance demerit)** |
| base.en @ `59eea950` | **MIT, pass** | **DISCLOSED-SCRAPED** (as above) | as above | as above | **PASS (grey provenance demerit)** |
| small.en @ `d9533f69` | **MIT, pass** | **DISCLOSED-SCRAPED** (as above) | as above | as above | **PASS (grey provenance demerit)** |
| large-v3-turbo @ `2ca6ff69` | **MIT, pass** (repo LICENSE, README, and OpenAI's HF card `license: mit`) | **OPAQUE for 4M of 5M h** (scale only). 1M h weakly labelled, likely the same web method (UNVERIFIED) | as above | as above, plus two model generations with no data description | **GREY. OK as a developer-only benchmark engine. Re-review before any user-facing or default use** |

Bar #1: no evidence of piracy for any of the four, and specific sources UNVERIFIED. Bar #2: N/A.

## NOTICE entry

**Copyright line** (verbatim from openai/whisper `LICENSE` at `6e3be77e`, its only revision; the text is bundled as
`app/src/main/assets/licenses/whisper.txt`):

```
OpenAI Whisper (tiny.en, base.en, small.en, large-v3-turbo)
Copyright (c) 2022 OpenAI
```

**License:** MIT for all four. One notice covers them, because they are the same licensor's checkpoints under the same
LICENSE. The provenance statement is in `NOTICE.md` under "Downloadable speech models".

## Addendum (#55, 2026-10-03): our re-quantised turbo files

`tools/asr-bench/turbo/quantize.py` re-quantises k2-fsa's turbo files above (no new weights, no training). The
candidate is the app's `turbo-decoder.int8.onnx` with its fp32 LM head and token embedding made 8-bit
(`--head 8 --gather8`): 246,975,821 B, sha256 `89342346def24f774e3c46561af63f05cf62f30ff7c90ca22941687e2d67aaf7`,
reproducible byte for byte from the pinned int8 file. The 4-bit variants measured in #55 are re-quantisations of
k2-fsa's fp32 export at the same pinned revision (`turbo-encoder.onnx` + `turbo-encoder.weights`,
`turbo-decoder.onnx`; sha256 `1b960f27…`, `746f879e…`, `b24db5d9…` as in the HF LFS metadata).

- **Provenance, posture, org: unchanged.** The files carry exactly OpenAI's turbo weights, rounded; the grading
  above and Max's 2026-10-02 decision apply as they are.
- **Licence: unchanged, MIT (OpenAI).** Our conversion adds no terms. Tools: ONNX Runtime 1.28.0's quantiser (MIT,
  with Intel Neural Compressor code under Apache-2.0), onnx (Apache-2.0), numpy (BSD-3-Clause). No GPL or LGPL.
- **Hosting:** shipping the file means hosting it as a release asset of `maxwhipw/marmalade-stt-engines` (D5), which
  needs Max's all-clear. The NOTICE entry would not change (same copyright line). Before publishing, scan the file for
  embedded paths (`strings | grep -E '/home|<build host name>'`), as for Parakeet Ultra.

The ACFT fine-tune of turbo (another person's weights, Apache-2.0) has its own review:
[`acft-whisper-turbo.md`](acft-whisper-turbo.md).
