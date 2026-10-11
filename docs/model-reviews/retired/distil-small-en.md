# Model Ethics Review: Distil-Whisper distil-small.en (int8, k2-fsa export)

Reviewed 2026-09-23 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the three files the Engines screen downloads, used only to transcribe the user's own dictation
on-device. No generation, no cloning, no speaker identification.

## Verdict first

**PASS against Max's bar, with a grey provenance demerit that the earlier Marmalade verdict ("ethics clear") understated.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-and-scraped training data.** It cannot be ruled out for the
  Whisper part, whose sources OpenAI has never named.
- **Bar #2 (impersonation): N/A.** A speech recognizer outputs text. It cannot clone or imitate a voice.
- **License: MIT**, from both Hugging Face (Distil-Whisper) and OpenAI (Whisper). The k2-fsa repository adds no terms. No GPL or LGPL anywhere.

**Caveats. Read these before repeating the "clean provenance" line anywhere:**

1. **distil-small.en is not a clean break from Whisper's data.** Its encoder is Whisper small.en's encoder,
   copied and frozen: **byte-identical, 53% of all parameters**, verified below. Its decoder starts from Whisper
   small.en decoder layers. Its training targets are Whisper large-v2 transcripts. So most of what we run was
   learned from OpenAI's ~680k hours of unnamed "audio paired with transcripts on the Internet". Marmalade ADR 0012 and `CREDITS.md` say distil-whisper has
   "cleaner training-data ethics than any stock-Whisper option". That claim is **overstated**. The truth is
   Whisper's provenance **plus** a documented distillation set.
2. **The distillation set is documented but not all "permissively licensed"**, although the card says it is.
   The card's own table lists **Fisher and SwitchBoard (LDC, paid licensed corpora)** and **TED-LIUM (CC-BY-NC-ND 3.0)**.
   **GigaSpeech** is web-crawled YouTube, podcast and audiobook audio whose curators say they do not own the copyright
   and grant access for non-commercial research only. Earlier Marmalade notes list only "People's Speech, Common Voice,
   GigaSpeech, LibriSpeech" and so miss the NC and LDC parts. None of this is piracy: LDC data is bought, not scraped,
   and the rest is public. It is licensing grey, and Hugging Face has answered it publicly (see Provenance).
3. **OpenAI's track record is a demerit, not a disqualifier.** Whisper was reportedly built partly to transcribe YouTube for
   GPT-4 training, and OpenAI faces copyright suits over its LLM training data. None of this is about the Whisper
   weights' own training set.

Blocking issues: **none.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/csukuangfj/sherpa-onnx-whisper-distil-small.en at commit `0492324bca9e12a6fca0089bb846f2dd723b50d8` ("upload distil-small.en", 2024-10-02) | HF API: https://huggingface.co/api/models/csukuangfj/sherpa-onnx-whisper-distil-small.en/revision/0492324bca9e12a6fca0089bb846f2dd723b50d8 |
| Files | `distil-small.en-encoder.int8.onnx` 102,961,431 B, sha256 `397a76d2…328beca`; `distil-small.en-decoder.int8.onnx` 195,079,097 B, sha256 `3074092b…e55e065e0`; `distil-small.en-tokens.txt` 835,554 B, sha256 `306cd27f…328dd930` | local `sha256sum` of the Marmalade client's copies (the Marmalade monorepo's `apps/android/app/src/main/assets/stt/`), all three match `SttEngineCatalog` |
| Producer | `onnx.quantize` 0.1.0, ONNX IR 7, metadata `onnx.infer=onnxruntime.quant` | read from both files' ModelProto |
| Encoder metadata | `model_type=whisper-distil-small.en`, `maintainer=k2-fsa`, `n_audio_layer=12`, `n_audio_state=768`, `n_text_layer=4`, `n_vocab=51864`, `is_multilingual=0` | same. 12×768 is Whisper small's encoder shape; 4 text layers is distil-small.en's decoder |
| Upstream checkpoint | https://huggingface.co/distil-whisper/distil-small.en (repo created 2023-12-06; current head `9e4a67ca`) | HF API: https://huggingface.co/api/models/distil-whisper/distil-small.en |
| Conversion | sherpa-onnx `scripts/whisper/export-onnx.py` (header: "Copyright 2023 Xiaomi Corp. (authors: Fangjun Kuang)", "modified from https://github.com/TadaoYamaoka/whisper/blob/main/to_onnx.py"). It downloads `distil-small.en/resolve/main/original-model.bin`, exports encoder and decoder with `torch.onnx.export`, then `quantize_dynamic(..., weight_type=QuantType.QInt8)` | https://github.com/k2-fsa/sherpa-onnx/blob/master/scripts/whisper/export-onnx.py |

**What the k2-fsa export is.** It is a mechanical format conversion (PyTorch → ONNX) plus int8 dynamic weight
quantization, done with the published checkpoint. There is no retraining or fine-tuning. The pinned HF repo has **no README,
no LICENSE file and no license tag** (tags: `onnx`, `region:us`; files listed in the API response above), so it adds no
terms of its own. The upstream MIT terms govern. Marmalade's `CREDITS.md` calls the export "Apache 2.0 (k2-fsa)". **That
is unsupported:** Apache-2.0 is sherpa-onnx's *code* license, and the model repo states no license at all. The export
script does not ship with the model.

**tokens.txt** is Whisper's English (GPT-2) BPE vocabulary, one base64 token and id per line. It comes from the tokenizer files in
the MIT-licensed openai/whisper repo.

## Is the encoder really Whisper's?

**Yes. Byte-identical. Established locally for this review.**

The card says "The encoder … is entirely copied from the teacher to the student and frozen during training" (card,
"## Model Details"). The distil-whisper training README documents `--freeze_encoder` for this case
(https://github.com/huggingface/distil-whisper/blob/main/training/README.md). I checked it against the weights. I read the
safetensors headers and pulled each tensor by HTTP range request from
`distil-whisper/distil-small.en` `model.fp32.safetensors` (revision `9e4a67ca`) and from `openai/whisper-small.en`
`model.safetensors`:

- **Encoder: 187 of 187 tensors byte-identical** to Whisper small.en. That is 88,154,112 of the model's 166,132,224 parameters (**53%**).
- **Decoder:** 4 layers (`model.decoder.layers.0`–`3`). They differ from small.en's corresponding tensors but remain
  strongly correlated (layer-0 `fc1.weight` r = 0.83; `embed_tokens` r = 0.85). That fits initialisation from
  Whisper small.en followed by distillation training.

The k2-fsa files are int8-quantized, so they cannot be byte-compared. They are the ONNX export of the same checkpoint
(the export script downloads `distil-small.en/resolve/main/original-model.bin`, the OpenAI-format copy of those weights). So
**just over half of the model we run is OpenAI's Whisper small.en, unchanged.**

## License (weights / code)

- **Distil-Whisper: MIT, "Copyright 2023 The OpenAI Authors and The HuggingFace Inc. team. All rights reserved."**
  The HF card sets `license: mit` in its metadata, and its prose says: "Distil-Whisper inherits the
  [MIT license](https://github.com/huggingface/distil-whisper/blob/main/LICENSE) from OpenAI's Whisper model."
  The tag and the prose agree. The HF model repo contains no LICENSE file of its own, so the GitHub LICENSE is the text.
  That file has exactly one commit ("add license", 2023-11-02, `a8820e8c`), which predates distil-small.en's release
  (2023-12-06). It has never been changed.
  - https://huggingface.co/distil-whisper/distil-small.en (card, "## License")
  - https://github.com/huggingface/distil-whisper/blob/a8820e8c5e499f41e3a56f4d4cb2799483403f3a/LICENSE
  - https://api.github.com/repos/huggingface/distil-whisper/commits?path=LICENSE
- **OpenAI Whisper: MIT, "Copyright (c) 2022 OpenAI".** README: "Whisper's code and model weights are released under
  the MIT License." LICENSE has one commit ("initial commit", 2022-09-21, `6e3be77e`).
  - https://github.com/openai/whisper/blob/6e3be77e1a105e59086e3e21ff5f609fd6fa89a5/LICENSE
  - https://github.com/openai/whisper/blob/main/README.md ("## License")
  - Tag oddity, harmless: the Transformers conversion `openai/whisper-small.en` on HF is tagged `apache-2.0`
    (https://huggingface.co/api/models/openai/whisper-small.en). OpenAI's own release statement is MIT, and Hugging Face
    itself says Distil-Whisper inherits MIT from Whisper. If anyone raises the tag, this is the explanation.
- **Data licenses vs model license.** Asked directly whether the NC-licensed training data (TED-LIUM) makes the model
  non-commercial, HF maintainers replied within days (issue #7, 2023-11-02 to 11-13). Patrick von Platen: "The licenses
  shown there only apply to the data itself not a model that was trained on the data … we only used the audio, not the
  transcriptions." Sanchit Gandhi then posted HF's internal legal discussion. Its TLDR is "the Distil-Whisper models
  inherit the permissive MIT license from Whisper", and it candidly adds: "permissive licenses on trained model are
  probably fine right now, but we don't want to be in a situation where anyone says 'it's legal because HF told me so'".
  https://github.com/huggingface/distil-whisper/issues/7#issuecomment-1798998524
  Precision note: "only used the audio" is slightly generous. The training WER filter compares Whisper's pseudo-labels
  against each dataset's ground-truth transcripts, so those transcripts were used to select data, though not as targets
  (card, "## WER Filter").
- **GPL/LGPL: none** in the weights, the tokens file or the export path.

## Provenance (training data)

The model has three layers of lineage. Each gets its own grade.

### Layer 1: Whisper small.en (encoder weights, decoder initialisation). **DISCLOSED-SCRAPED, sources unnamed**

- OpenAI says only "680,000 hours of audio and the corresponding transcripts collected from the internet"
  (model card, "## Training Data": https://github.com/openai/whisper/blob/main/model-card.md). The paper (§2.1) says: "We construct
  the dataset from audio that is paired with transcripts on the Internet". It describes heuristics to remove
  machine-generated transcripts and de-duplication against TED-LIUM 3, but names no sites. https://arxiv.org/abs/2212.04356
- **The direct question went unanswered by OpenAI.** Discussion #21, "Curious about the source of the training data?"
  (opened 2022-09-21), has only community replies. https://github.com/openai/whisper/discussions/21
- **Circumstantial evidence of what "the internet" was.** When fed silence, Whisper hallucinates video outros and subtitle
  credits: "Thanks for watching", "❤️ Translated by Amara.org Community", "Untertitel im Auftrag des ZDF für funk, 2017",
  "Copyright WDR 2021", "Sous-titrage ST' 501", "Transcribed by https://otter.ai".
  https://github.com/openai/whisper/discussions/928 and https://github.com/openai/whisper/discussions/21
  This points to subtitled web video (YouTube-style uploads, volunteer subtitle communities, public-broadcaster
  clips). All of that is public web content. Some of it (broadcaster TV subtitles) may be re-uploads its rights holders did not authorise.
  Whether any of it came from paywalled or pirated sources is **UNVERIFIED**. No evidence of that was found. Credits
  from broadcasters and subtitling houses fit free public broadcaster and online clips just as well.
- Under Max's bar this is public-web scraping, which is acceptable. The opacity (no named sources, unanswered question) is a demerit.
  OpenAI at least admits web sourcing, which ranks above pure silence.

### Layer 2: distillation data (decoder training). **DISCLOSED, mixed licenses**

The distil-small.en card, "## Data", says: "trained on 22,000 hours of audio data from 9 open-source, permissively licensed
speech datasets". Its own table contradicts "permissively licensed":

| Dataset | Hours | Card's licence column | Notes |
|---|---|---|---|
| People's Speech | 12,000 | CC-BY-SA-4.0 | Internet Archive audio. The HF dataset lists CC-BY 2.0–4.0 and CC-BY-SA 3.0/4.0 (https://huggingface.co/datasets/MLCommons/peoples_speech) |
| Common Voice 13 | 3,000 | CC0-1.0 | crowd-sourced, consented readers |
| GigaSpeech | 2,500 | "apache-2.0" | **Grey.** The HF gate text says: "SpeechColab does not own the copyright of the audio files. For researchers and educators who wish to use the audio files for non-commercial research and/or educational purposes…" (https://huggingface.co/datasets/speechcolab/gigaspeech). The paper describes crawled podcasts with manual transcripts and YouTube videos with human captions (https://arxiv.org/abs/2106.06909 §3.1). **The audiobook source is not named: UNVERIFIED** whether it was free (LibriVox-style) or commercial. |
| Fisher | 1,960 | LDC | **Paid, licensed** (LDC User Agreement for Non-Members, https://catalog.ldc.upenn.edu/LDC2004S13). Recruited telephone participants who agreed to be recorded. Not scraped, not pirated. Whether HF's LDC license permits releasing a model trained on it under MIT: **UNVERIFIED** |
| LibriSpeech | 960 | CC-BY-4.0 | LibriVox public-domain audiobooks |
| VoxPopuli | 540 | CC0 | European Parliament |
| TED-LIUM | 450 | CC-BY-NC-ND 3.0 | **NC-ND data.** Raised in issue #7 and answered by HF (above) |
| SwitchBoard | 260 | LDC | same as Fisher (https://catalog.ldc.upenn.edu/LDC97S62) |
| AMI | 100 | CC-BY-4.0 | meeting recordings |

About 76% of the hours (16,600 h) are CC0, CC-BY or CC-BY-SA. About 10% (2,220 h) are paid LDC corpora, and about 14% (2,950 h) are under NC or research-only terms. The
training targets are **Whisper large-v2 pseudo-labels** ("The audio data is then pseudo-labelled using the Whisper large-v2
model"), so large-v2, with the same opaque 680k-hour lineage, is the teacher here as well.

### Layer 3: k2-fsa conversion. **N/A (no data)**

Format conversion and quantization only (see Artifact).

**Overall provenance grade: PARTIAL.** The distillation layer is well disclosed, but the core weights sit on
Whisper's DISCLOSED-SCRAPED, unnamed-source corpus.

**Piracy check (Max's bar #1):** no evidence of pirated or paywalled-scraped content in any layer. LDC corpora are paid, but they were
**licensed, not scraped**, which is the opposite of piracy. GigaSpeech's audiobook source and Whisper's specific sources are
**UNVERIFIED**.

## Posture: **Structurally N/A for impersonation. Surveillance dual-use verbally discouraged.**

- ASR maps audio to text. It cannot generate, clone or imitate a voice, so Max's bar #2 cannot apply. The distil-small.en card's
  examples are LibriSpeech samples. No celebrity or character demos exist.
- The relevant misuse risk for ASR is surveillance: transcribing people without their consent. OpenAI's model card says: "we caution against using
  Whisper models to transcribe recordings of individuals taken without their consent", and it names the dual-use
  surveillance concern explicitly (model-card.md, "Evaluated Use" and "Broader Implications"). That is verbal
  discouragement, which is the best available for an open ASR model.
- **Our use fits the benign case:** the user dictates their own speech, entirely on-device. Audio is never stored or sent (a hard rule of this project).

## Org track record

- **Hugging Face (distil-whisper; authors Gandhi, von Platen, Rush):** positive. The paper, the full training code and the
  dataset list are public (https://arxiv.org/abs/2311.00430, https://github.com/huggingface/distil-whisper/tree/main/training).
  When asked about licensing, they answered quickly and candidly and published their internal legal reasoning (issue #7).
  The one demerit is the card's "permissively licensed" wording, which its own table contradicts. No lawsuits, retractions or relicensing of distil-whisper found.
- **OpenAI (Whisper):** grey. The Whisper weights have been MIT since release and were never relicensed or pulled. Separately:
  - The NYT reported (2024-04-06) that OpenAI built Whisper partly to transcribe over a million hours of YouTube for GPT-4
    training, despite YouTube's terms. https://www.techmeme.com/240406/p5
  - OpenAI is a defendant in copyright suits over LLM training data, e.g.
    https://en.wikipedia.org/wiki/The_New_York_Times_v._Microsoft_and_OpenAI
  These concern OpenAI's conduct and its LLMs, not the Whisper training set. Under Max's framework they are an
  org-level demerit. Using the MIT weights on-device does not feed OpenAI.
- **k2-fsa / Next-gen Kaldi (Xiaomi-affiliated; csukuangfj):** converter and distributor only. No concerns. The same
  maintainer supplied our Silero VAD conversion (see `silero-vad.md`).

## Feasibility (secondary)

Already proven in production: the Marmalade Android client has bundled these exact three files (same SHA-256s) as its default
STT since ADR 0012 (2026-07-23), running through sherpa-onnx's Whisper `OfflineRecognizer` with Silero VAD. The download is about
299 MB. It is English-only and CPU int8.

## Working demo link

No working hosted demo of distil-small.en was found. https://huggingface.co/spaces/distil-whisper/whisper-vs-distil-whisper
exists but is SLEEPING and runs distil-large-v3, not small.en. `reach-vb/distil-small.en` is in RUNTIME_ERROR. The k2-fsa
ASR Space does not include distil models. The best available demo is the Marmalade Android client, which runs this exact model.
For an STT model, "listening" means dictating into it, and that client already does.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **MIT, pass** | distil-whisper LICENSE plus Whisper LICENSE, both unchanged since creation. k2-fsa repo adds no terms |
| Provenance | **PARTIAL** | distillation set named (9 corpora, mixed CC / LDC / NC). Core weights are Whisper's unnamed web-scraped 680k h. OpenAI never answered the direct question |
| Posture | **N/A for impersonation. Surveillance risk verbally discouraged** | ASR outputs text only. OpenAI's card cautions against transcribing people without consent |
| Org track record | **HF positive, OpenAI grey, k2-fsa neutral** | HF transparent and responsive. OpenAI's YouTube-transcription reporting and copyright suits |
| **Verdict vs Max's bar** | **PASS (grey provenance demerit)** | Bar #1: no evidence of piracy. LDC data is licensed, the web data is public, specific Whisper and GigaSpeech-audiobook sources UNVERIFIED. Bar #2: N/A |

**Correction to prior art.** `monorepo/apps/android/docs/decisions/0012-distil-small-bundled-stt.md` graded this
model ethics "clear" and stock Whisper "unclear". On the evidence above, both belong in the same band: **pass, grey
provenance**. distil-small.en adds a documented layer on top of Whisper, but it does not replace Whisper's lineage. Its
dataset list there, and in `CREDITS.md`, omits Fisher, SwitchBoard, TED-LIUM, VoxPopuli and AMI. `CREDITS.md` also calls
the k2-fsa export "Apache 2.0", but that repo has no license. Those files belong to the Marmalade repo and have not been edited here.

## NOTICE entry

**Copyright lines** (verbatim from the LICENSE files at the pinned commits above; the texts are bundled as
`app/src/main/assets/licenses/distil-whisper.txt` and `whisper.txt`)

```
Distil-Whisper distil-small.en
Copyright 2023 The OpenAI Authors and The HuggingFace Inc. team. All rights reserved.

OpenAI Whisper
Copyright (c) 2022 OpenAI
```

**License:** MIT for both. Both notices ship, because the weights contain Whisper small.en's weights (the encoder copied) and
MIT requires the notice to accompany copies. The provenance statement is in `NOTICE.md` under "Downloadable speech models".
