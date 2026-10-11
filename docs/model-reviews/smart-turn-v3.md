# Model Ethics Review: Pipecat Smart Turn v3.2 (int8 CPU ONNX)

Reviewed 2026-09-28 by claude-code (Opus 5.5), per the `model-ethics-review` skill.
Scope: the one file the app bundles (#43; a download until #44 bundled it), used on-device to decide
whether the user has finished dictating (the default "Smart Turn" end-of-dictation mode). It outputs one
"complete / incomplete" score for the last 8 s of audio. No generation, no cloning, no identification.

## Verdict first

**PASS against Max's bar. This is the cleanest training-data story of any model we have reviewed**, with one inherited
grey layer (the Whisper Tiny encoder it was fine-tuned from).

- **Bar #1 (no piracy): no evidence of pirated, paywalled-and-scraped or non-public data.** The whole training set is
  published (https://huggingface.co/datasets/pipecat-ai/smart-turn-data-v3.2-train), and every source in it is named.
  About 81–83% of the clips are **synthetic speech** that Pipecat generated through paid or open TTS models (mostly
  Google Chirp 3 HD). The human clips come from **volunteers who clicked an explicit consent screen** that says the
  recordings go into a public open-source dataset, and from three data vendors (Liva AI, Midcentury, MundoAI) who
  contributed them under a guide that requires speaker consent to public release.
- **Bar #2 (impersonation): N/A.** It is a binary classifier. It cannot produce or imitate a voice.
- **License: BSD-2-Clause** on the weights (card tag, no prose rider) and on the training code, "Copyright (c)
  2024–2025, Daily". No GPL or LGPL anywhere in the chain. The weights start from OpenAI's Whisper Tiny (MIT), so
  OpenAI is credited too.

**Caveats (none blocking):**

1. **The datasets carry no license.** `pipecat-ai/smart-turn-data-v3.1-*` and `-v3.2-*` have no license tag, file or
   statement. That does not touch us: we ship none of the data, and the weights are BSD-2. It would matter only if we
   ever fine-tuned on *their* corpus (the wiki already records this: `tech/ai/radar/turn-detection-2026-09.md`).
2. **The encoder inherits Whisper's opaque pre-training.** Smart Turn v3 is `openai/whisper-tiny` fine-tuned with a
   small classifier head, so its encoder weights began as Whisper's, trained on OpenAI's 680k hours of unnamed web audio
   (graded **DISCLOSED-SCRAPED** in [`whisper-family.md`](whisper-family.md)). The same grey layer is already accepted
   for distil-small.en and the Whisper developer engines.
3. **The TTS models' own data is only partly known.** Google does not publish Chirp 3 HD's voice sourcing; Rime says
   its voices come from "everyday people" but does not publish its consent process; Orpheus's 100k-hour pre-training set
   is undisclosed. These are third-hand (the TTS output is the training data, not the TTS training data) and Orpheus is
   under 2% of the clips.

**Blocking issues: none.** The final adoption decision is Max's.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Distribution point | https://huggingface.co/pipecat-ai/smart-turn-v3 at commit `f766f81d3cfdf7737ac64aad813d91bbfd56bf93` ("Smart Turn v3.2", 2026-01-07T18:00:39Z), today's `main` | commit list https://huggingface.co/api/models/pipecat-ai/smart-turn-v3/commits/main |
| File | `smart-turn-v3.2-cpu.onnx`, 8,679,182 bytes | HF tree API at the pinned commit: https://huggingface.co/api/models/pipecat-ai/smart-turn-v3/tree/f766f81d3cfdf7737ac64aad813d91bbfd56bf93 |
| sha256 | `2bb026316b14a660486a75b1733cd3fbab8c2fd0314dc9af7be49f8cca967e4f` | the LFS oid in the tree API above; the `x-linked-etag` of a HEAD request on `https://huggingface.co/pipecat-ai/smart-turn-v3/resolve/f766f81d3cfdf7737ac64aad813d91bbfd56bf93/smart-turn-v3.2-cpu.onnx` (302 to the CDN, then 200; `x-repo-commit: f766f81d…`); and local `sha256sum` of marmalade-stt-cli's copy (`marmalade_stt/models/smart-turn-v3.2-cpu.onnx`) |
| Producer | `onnx.quantize` 0.1.0, ONNX IR 10, opset 18; metadata `onnx.infer=onnxruntime.quant` | read from the file with the `onnx` Python package |
| Inputs / outputs | `input_features [N, 80, 800]` (80-bin Whisper log-mel, 8 s at 16 kHz) → `logits [N, 1]` | same |
| Architecture | Whisper Tiny encoder (positional table cut to 400 frames) + attention pooling + a small MLP head, ~8M parameters, int8 | card "Model architecture"; `train.py` (`base_model_name: "openai/whisper-tiny"`, `SmartTurnV3Model.from_pretrained(...)`, `max_source_positions = 400`): https://github.com/pipecat-ai/smart-turn/blob/main/train.py |

v3.2 is the file added by commit `f766f81d`; earlier commits hold v3.0 and v3.1 only. The pinned revision is the one
our file was taken from, not a later re-upload: the tree API at `f766f81d` and at `main` return the same oid.

## License (weights / code)

- **Weights: BSD-2-Clause.** Card metadata `license: bsd-2-clause`; the card prose adds no terms (no research-only,
  no non-commercial, no acceptable-use rider). https://huggingface.co/pipecat-ai/smart-turn-v3/raw/f766f81d3cfdf7737ac64aad813d91bbfd56bf93/README.md
  The model repo has no LICENSE file of its own, so the text comes from the code repo.
- **Code: BSD-2-Clause, "Copyright (c) 2024–2025, Daily"** (en dash in the original). The LICENSE has one commit
  (`24c72033`, 2025-03-06) and has not changed since: https://github.com/pipecat-ai/smart-turn/blob/main/LICENSE,
  https://api.github.com/repos/pipecat-ai/smart-turn/commits?path=LICENSE. The README: "This is a truly open model (BSD
  2-clause license)". GitHub's license API agrees (`BSD-2-Clause`).
- **Base model: OpenAI Whisper Tiny.** OpenAI's GitHub repo states "Whisper's code and model weights are released
  under the MIT License" ("Copyright (c) 2022 OpenAI"); the Transformers conversion `openai/whisper-tiny` that
  `train.py` loads is tagged `apache-2.0` on HF (https://huggingface.co/api/models/openai/whisper-tiny). Both are
  permissive. As in [`whisper-family.md`](whisper-family.md) we credit OpenAI under the MIT text we already bundle.
  Pipecat's card names "Whisper Tiny encoder" as the backbone but carries no OpenAI notice; our NOTICE supplies it.
- **Datasets: no license** (see caveat 1). HF API `cardData` for all four v3.1/v3.2 sets has no `license` key:
  https://huggingface.co/api/datasets/pipecat-ai/smart-turn-data-v3.2-train. Only the older `human_5_all` subset is
  tagged `bsd-2-clause`.
- **GPL/LGPL: none** in the weights, the code we port, or the training chain.

## Provenance (training data): **DISCLOSED-CLEAN** (named, published, consented or synthetic), plus the inherited Whisper layer

### Layer 1: the Smart Turn fine-tuning set. **DISCLOSED-CLEAN**

The README names the sets: "pipecat-ai/smart-turn-data-v3.2-train", "-test" (https://github.com/pipecat-ai/smart-turn,
"Collecting and contributing data"), and `train.py` loads exactly those. The v3.2 train set has 270,946 clips in 23
languages; each clip carries `dataset` and `synthetic` columns, so the mix is auditable. Counts below are from the
HF dataset viewer's statistics: the **full** v3.2 test set (31,527 clips) and the first 35,915 clips of the train set
(the viewer only indexes part of it). The same 12 sources appear in both, in similar proportions.
https://datasets-server.huggingface.co/statistics?dataset=pipecat-ai/smart-turn-data-v3.2-test&config=default&split=train

| Source (`dataset`) | Test clips | Train sample | What it is | Evidence |
|---|---|---|---|---|
| `chirp3_1`, `chirp3_2`, `chirp3_3_short` | 24,786 | 28,605 | **Synthetic.** Google Cloud Chirp 3 HD TTS reading sentences in 23 languages; incomplete variants made by cutting sentences with Gemini 2.5 Flash and adding fillers | Smart Turn v2 blog, "Dataset": "We eventually settled on Google's Chirp3 model" https://www.daily.co/blog/smart-turn-v2-faster-inference-and-13-new-languages-for-voice-ai/ |
| `rime_2` | 394 | 467 | **Synthetic.** Rime's paid TTS API, model `mist`, a random stock speaker per sentence | Pipecat's generation script, attached by the maintainer to https://github.com/pipecat-ai/smart-turn/issues/17 (`rime_generate.py`: `https://users.rime.ai/v1/rime-tts`, `"modelId": "mist"`, a fixed list of Rime's named voices) |
| `orpheus_endfiller_1`, `orpheus_midfiller_1`, `orpheus_grammar_1` | 484 | 586 | **Synthetic.** Canopy Labs' Orpheus TTS (Apache-2.0, stock voices) | dataset names; https://huggingface.co/api/datasets?author=pipecat-ai lists them as separate sets (2025-04-15). Voices used: **UNVERIFIED** |
| `liva_1`, `midcentury_1`, `mundo_1` | 5,371 | 5,692 | **Human**, contributed by three data vendors (English and Spanish) | v3.1 blog, "New training data": "our partners, Liva AI, Midcentury, and MundoAI, who have provided new human audio samples" https://www.daily.co/blog/improved-accuracy-in-smart-turn-v3-1/ |
| `human_5` | 402 | 469 | **Human**, English, recorded by people (BSD-2 tagged subset) | v2 blog: "`human_5_all` dataset, which contains English samples recorded by actual human speakers"; https://huggingface.co/datasets/pipecat-ai/human_5_all |
| `human_convcollector_1` | 90 | 96 | **Human**, volunteers talking to a Pipecat bot in a web app | https://conversation-collector.vercel.app/ (consent screen quoted below) |
| **Synthetic share** | **81.4%** (25,664 of 31,527) | **82.6%** (29,658 of 35,915) | | `synthetic` column, same statistics endpoint |

Background noise mixed in for v3.2 is 35 named **CC0** clips from Freesound, each credited by URL in the dataset card:
https://huggingface.co/datasets/pipecat-ai/smart-turn-data-v3.2-train

**Consent, checked at the collection points:**

- **Volunteers.** Both of Pipecat's collection apps (https://conversation-collector.vercel.app/ and
  https://turn-training.pipecat.ai/) put a "Consent to Data Collection" card before recording, with an "I Agree & Start
  Recording" button: "By proceeding, you consent to having your voice recorded for the purpose of training an
  end-of-turn detection model (Smart Turn). … These recordings will be used for research and included in an open source
  dataset that will be publicly available". (Read from the apps' shipped JavaScript, 2026-09-28.) That is informed,
  purpose-specific consent, including public release.
- **Contributors and vendors.** The contribution guide requires it: "By contributing, you confirm you own the recordings,
  the speakers consent to public release of their voice, and grant us the rights to redistribute."
  https://github.com/pipecat-ai/smart-turn/blob/main/docs/data_generation_contribution_guide.md. The vendors describe
  their data as recruited or licensed: MundoAI's is "sourced from a global network of contributors"; Midcentury says it
  "license[s] proprietary long-form video directly from creators" (both quoted in the v3.1 blog above). Each vendor's
  own consent paperwork is **UNVERIFIED** (not public), but nothing suggests scraping.
- **Synthetic voices.** No person was recorded for these clips. The voices are the TTS vendors' stock voices; no clip
  imitates a named real person. Pipecat looked for TTS "which allow the data to be used for training purposes"
  (maintainer, issue #17 above). Google's published statement is only that TTS audio may be used "in your applications
  or media in compliance with the Google Cloud Platform Terms of Service" (https://cloud.google.com/text-to-speech/quotas);
  whether Google's and Rime's terms expressly allow training use is **UNVERIFIED**. Either way it is a paid,
  terms-governed API, not piracy.

**Text prompts.** The sentences the TTS read came from `agentlans/high-quality-english-sentences` (C4 and FineWeb
web text, ODC-BY per HF tags) and `agentlans/high-quality-multilingual-sentences` (CC-BY-4.0, derived from
`agentlans/LinguaNova`, whose card names no source). Issue #17 and the v2 blog above name them. This is public web
text used only as scripts to read aloud. The model classifies prosody and cannot reproduce it.

**Piracy check (Max's bar #1):** nothing pirated, paywalled or non-public. Every clip's origin is named, and the
human clips are consented.

### Layer 2: Whisper Tiny pre-training (inherited). **DISCLOSED-SCRAPED**

The encoder was initialised from `openai/whisper-tiny` (`train.py` above) and fine-tuned. Whisper Tiny shares OpenAI's
680k-hour weakly supervised web-audio set, whose sources OpenAI never named. The full grading is in
[`whisper-family.md`](whisper-family.md) (the `.en` siblings there are graded **DISCLOSED-SCRAPED**, PASS with a
grey provenance demerit). Smart Turn fine-tunes every encoder weight, so what remains of Whisper's is a starting
point, not a copy. **UNVERIFIED** how far the weights moved.

### Layer 3: int8 quantisation. **N/A (no data)**

Pipecat quantised its own fp32 model with ONNX Runtime (`onnx.quantize` producer; static calibration on 1,024 of its
own training clips, `train.py` `calibration_dataset_size`).

## Posture: **Structurally N/A (cannot impersonate)**

- The model outputs one score per 8 s window: has the speaker finished? It cannot generate, clone or identify a voice,
  so Max's bar #2 cannot apply.
- Pipecat's demos are its own voice-agent framework and benchmark scripts. No celebrity or character voices; the
  synthetic training voices are stock vendor voices.
- Our use: the user's own dictation, scored on-device, never stored or sent (a hard rule of this project).

## Org track record

- **Daily (daily.co), maker of Pipecat: positive.** A WebRTC company that open-sources its voice-agent framework
  (Pipecat, BSD-2) and this model with weights, training code, datasets and benchmarks. It answered the one
  data-pipeline question on the tracker in detail and published the scripts (issue #17). It asks permission for human
  data and says what happens to it. No lawsuits, retractions, license changes or shutdowns found. The HF community tab
  has two threads, neither about data: https://huggingface.co/pipecat-ai/smart-turn-v3/discussions. The GitHub issues
  have no unanswered provenance question: https://api.github.com/search/issues?q=repo:pipecat-ai/smart-turn+training+data
- **Data vendors (Liva AI, Midcentury, MundoAI): neutral.** Commercial voice-data firms that describe recruited or
  licensed collection. Not investigated further.
- **TTS vendors:** Google (Chirp 3 HD) and Rime are paid APIs; Canopy Labs' Orpheus is open-weights with an
  undisclosed 100k-hour pre-training set (https://github.com/canopyai/Orpheus-TTS, "trained on 100k+ hours of English
  speech data") and a zero-shot cloning feature. Orpheus is the one grey name in the chain. It is under 2% of the clips,
  and nothing it produced imitates a real person.
- **OpenAI (Whisper Tiny base): grey**, as recorded in [`whisper-family.md`](whisper-family.md).

## Feasibility (secondary)

8.7 MB int8 ONNX, runs on ONNX Runtime (already in the APK since #32), one inference per pause over an 8 s window.
Pipecat reports about 12 ms on a modern CPU (v3 blog:
https://www.daily.co/blog/announcing-smart-turn-v3-with-cpu-inference-in-just-12ms/); Pixel 8a latency is not
measured here. 23 languages. marmalade-stt-cli has run the same file since 2026-08-20.

## Working demo link

No first-party hosted demo. The GitHub repo's `record_and_predict.py` runs it on a microphone
(https://github.com/pipecat-ai/smart-turn#run-the-model-locally). Community Spaces using the model are listed on the
card, e.g. https://huggingface.co/spaces/RinggAI/pipecat-smart-turn-simulator (not Pipecat's; not checked). The app's
Smart Turn mode is the demo that matters.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **BSD-2-Clause, pass** | Card tag with no rider; code LICENSE "Copyright (c) 2024–2025, Daily", unchanged since 2025-03-06. Whisper Tiny base: MIT (OpenAI), credited |
| Provenance | **DISCLOSED-CLEAN** for the fine-tuning set; **DISCLOSED-SCRAPED** for the inherited Whisper Tiny layer | Full dataset published with per-clip source and synthetic flags; ~82% synthetic TTS; human clips from volunteers behind an explicit consent screen and from vendors under a consent-to-public-release guide. Datasets themselves unlicensed (irrelevant to shipping the weights) |
| Posture | **N/A, structurally cannot impersonate** | Binary end-of-turn classifier |
| Org track record | **Daily positive; TTS/data vendors neutral; Orpheus and OpenAI grey** | Open weights, code, data and scripts; provenance questions answered |
| **Verdict vs Max's bar** | **PASS** | Bar #1: no pirated or non-public data; consent verified at the collection points. Bar #2: N/A |

**Corrections to prior art** (outside this repo, not edited here):

- The Marmalade monorepo's `docs/research/turn-end-detection.md` says "BSD-2-Clause (weights, data, training code
  open)". The data is *published* but carries **no license**. The maintainer's note `turn-detection-2026-09.md`
  (§ License, 2026-09-17) already has this right.
- The maintainer's note `pipecat.md` describes Smart Turn as "wav2vec2 + linear classifier",
  "~65ms". That is v2. v3 is a Whisper Tiny encoder with a small head, about 12 ms on CPU.
- marmalade-stt-cli's `NOTICE` (and the docstring of its `whisper_features.py`, plus this repo's
  `tools/asr-bench/asrbench/whisper_features.py`) credit pipecat's feature code as "Copyright (c) 2024-2025, Daily".
  Pipecat's LICENSE and the file's own header say **2024–2026** (the file was added in pipecat commit `31aa4ac6`,
  2026-05-21, after pipecat's LICENSE moved to 2024–2026 on 2026-01-07). 2024–2025 is correct for the smart-turn repo
  (the model), not for pipecat (the feature code).

## NOTICE entry

**Copyright lines**

```
Smart Turn v3 (model weights and training code)
Copyright (c) 2024–2025, Daily

OpenAI Whisper (Whisper Tiny, the encoder the model was fine-tuned from)
Copyright (c) 2022 OpenAI
```

**License:** BSD-2-Clause, bundled as its own text with Daily's line (`assets/licenses/smart-turn.txt`, verbatim from
https://github.com/pipecat-ai/smart-turn/blob/main/LICENSE). Whisper's MIT text is already bundled
(`assets/licenses/whisper.txt`). The provenance statement is in `NOTICE.md` under "Smart Turn end-of-dictation".
