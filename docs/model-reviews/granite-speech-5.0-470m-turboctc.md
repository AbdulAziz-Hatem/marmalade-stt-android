# Model Ethics Review: IBM Granite Speech 5.0 470M TurboCTC

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill, for the #26 spike.
Scope: `ibm-granite/granite-speech-5.0-470m-turboctc` at revision `286456107c8ba1161f5c22dfe85466402c88333b`, as a
possible on-device engine. The measurements are in `docs/benchmarks/2026-09-granite-spike.md`.

**Update (#32, 2026-09-27):** the app now offers it as a developer engine (also in the fdroid flavor), downloaded
from `qwertz92/granite-speech-5.0-470m-turboctc-onnx` at `e6e3b4d6f590ac51a05f25028b53591d4b260786`
(`onnx/model_int8.onnx`, sha256 `8173a50e…60e9`, and `tokenizer.json`, sha256 `3ee80b02…61e6`, byte-identical to
IBM's at the pinned revision). qwertz92 exported from IBM revision `18ca3c1d…`, whose `model.safetensors` has the
same sha256 as the revision reviewed here. The verdict is unchanged. NOTICE.md and the in-app licenses screen carry
the entry, with the Apache-2.0 text bundled.

## Verdict first

**PASS against Max's bar, with one minor grey note on about 1 % of the audio.** It is a recognition model, so bar #3
applies: judge how the training data was acquired.

- **Bar #1 (no piracy): passes.**
  - Every training set is named with hours, on the card, in the tech report (Table 4) and in the blog.
  - The report says "only publicly available data".
  - IBM put the two restricted corpora (GigaSpeech, SPGISpeech) into a separate `-nc` model rather than this one.
- **Bar #2 (impersonation): N/A.** A CTC recognizer outputs text. It can't synthesise or clone a voice.
- **License: Apache-2.0.** The card tag and prose agree, and there are no riders. The only intended-use line is
  "enterprise applications".
  - The repo has **no LICENSE file** at the pinned revision (`/raw/<rev>/LICENSE` is a 404). A NOTICE entry
    must bundle the standard Apache-2.0 text.
- **Never the `-nc` sibling.** It is CC-BY-NC-SA-4.0, "research and non-commercial use-only".

## License and artifact

| Field | Value | Evidence |
|---|---|---|
| Weights | Apache-2.0; `model.safetensors` 946,180,704 B (bf16), sha256 `8b98a8c34fd5fcb081caef719638eded31bb6d197d62053eefc5c1703aaf1ad4` | https://huggingface.co/ibm-granite/granite-speech-5.0-470m-turboctc/blob/286456107c8ba1161f5c22dfe85466402c88333b/README.md; hash computed on download |
| Signature | `model.sig`, a sigstore bundle, is in the repo. Not verified here | same repo |
| Tokenizer | `tokenizer.json`: byte-level BPE, 16,384 pieces, blank at id 0 | same repo |
| Tech report | arXiv 2609.20104 calls it "a permissive license" | https://arxiv.org/html/2609.20104v1 |
| Code used to export | transformers 5.17.0 `GraniteSpeech5ForCTC` (Apache-2.0), build time only | `tools/asr-bench/granite/export_onnx.py` |

Copyright holder for NOTICE: IBM (the card's author line is "IBM Granite Speech Team"). The ONNX conversion and int8
quantization are a modification and should say so.

## Provenance: DISCLOSED-CLEAN, one minor grey item

The card lists this training data (in hours):

| Set | Hours | Licence | Evidence |
|---|---|---|---|
| MLS (English) | 44,600 | CC-BY-4.0 (LibriVox public-domain audiobooks) | https://huggingface.co/datasets/facebook/multilingual_librispeech |
| YODAS | 8,900 | CC-BY-3.0; the authors say it includes "only videos with CC licenses", with a removal contact | https://huggingface.co/datasets/espnet/yodas |
| Common Voice 17 | 2,500 | CC0, donated with consent. Access terms forbid identifying speakers | https://huggingface.co/api/datasets/esb/datasets |
| LibriSpeech | 960 | CC-BY-4.0 | https://huggingface.co/datasets/openslr/librispeech_asr |
| VoxPopuli | 500 | CC0 data (its *models* are NC; only the data was used) | https://github.com/facebookresearch/voxpopuli |
| AMI | 150 | CC-BY-4.0 | https://huggingface.co/datasets/edinburghcstr/ami |
| Earnings-22 | 100 | transcripts CC-BY-SA-4.0; **audio unlicensed** (see below) | https://raw.githubusercontent.com/revdotcom/speech-datasets/main/earnings22/LICENSE.md |

The card also lists synthetic data:
- 2,000 h of multi-speaker concatenations of the sets above.
- 500 h of Earnings-22 concatenations.
- 240 h of number, URL and address sentences. gpt-oss (Apache-2.0) wrote them and StyleTTS2 (MIT code) voiced them.
  StyleTTS2's weight rider asks users to disclose synthetic speech to listeners. That is about publishing voices;
  nothing synthetic is released here.

Later training stages:
- Distillation from granite-4.1-8b-base (Apache-2.0).
- MUSAN noise and OpenSLR SLR28 room impulses, for augmentation only.
- Source: https://arxiv.org/html/2609.20104v1 §3.2–3.3.

**Grey: Earnings-22 audio.**
- Rev's paper says most calls came from seekingalpha.com and cites fair use (https://arxiv.org/pdf/2203.15591,
  footnotes 2–3).
- Seeking Alpha puts past call recordings behind a paid tier today. Whether Rev used a paid account in 2022 is
  UNVERIFIED.
- Mitigating points:
  - The companies broadcast these calls publicly.
  - Rev, not IBM, collected them.
  - It is a widely redistributed benchmark.
  - It is about 1 % of the roughly 62k hours.
- This is a note, not piracy.

**Why the `-nc` sibling is NC.**
- It adds GigaSpeech, whose terms allow "non-commercial research and educational purposes" only:
  https://huggingface.co/datasets/speechcolab/gigaspeech
- It adds SPGISpeech, whose terms allow "academic research purposes and internal use only":
  https://huggingface.co/datasets/kensho/spgispeech
- IBM fenced those two corpora off the Apache models. The Apache Granite Speech line has done this before: the
  granite-speech-4.1-2b card lists neither. Honouring upstream terms like this counts in its favour.

**Community.**
- Nobody has asked about provenance.
- The HF discussions hold only IBM's own PRs: https://huggingface.co/ibm-granite/granite-speech-5.0-470m-turboctc/discussions
- The GitHub repo has no data issues: https://github.com/ibm-granite/granite-speech-models/issues

## Posture: structurally safe

Audio in, text out. IBM's own demo is on-device dictation from your own mic ("Audio never leaves the device"):
https://huggingface.co/spaces/ibm-granite/granite-speech-streaming-webgpu

## Org track record: good for Granite, one old blemish elsewhere

- No training-data lawsuit against Granite was found. IBM ranks high on Stanford's transparency index and
  indemnifies Granite on watsonx (https://crfm.stanford.edu/fmti/May-2024/company-reports/IBM_Granite.html).
- The blemish is IBM's 2019 "Diversity in Faces" Flickr dataset. It led to *Vance v. IBM* under Illinois's
  biometric privacy law (https://www.insideprivacy.com/data-privacy/what-the-diversity-in-faces-litigation-means-for-biometric-technologies/).
  It is outside Granite and old, but worth knowing.

## Feasibility (secondary)

The model is 470M parameters, encoder only, with no decoder loop. The int8 file is 550 MB. On the host it decodes
twice as fast as Parakeet v3 and is more accurate on every English set. Its text is lowercase and unpunctuated, and
turns possessives into "is". See the benchmark doc.

## Download sources checked

| Source | Licence | Notes |
|---|---|---|
| Our export (`tools/asr-bench/granite/export_onnx.py`) | Apache-2.0 (derivative of IBM's weights) | Reproducible from the pinned checkpoint; parity with PyTorch verified; not hosted anywhere |
| `qwertz92/granite-speech-5.0-470m-turboctc-onnx` @ `e6e3b4d6f590ac51a05f25028b53591d4b260786` | Apache-2.0 | Publishes its scripts, hashes and parity reports. `onnx/model_int8.onnx` sha256 `8173a50ea67f864801971a40622a2e5c7d62fd230912bde94b745f92c74d60e9` matches its README. Same WER (within noise) and speed as ours on our harness |
| `diarizeapp/granite-speech-5.0-470m-turboctc-onnx` | Apache-2.0 | No conversion script, no source pin. Not used |
| `litert-community/granite-speech-5.0-470m-turboctc` | Apache-2.0, with a LICENSE file | `.tflite`, a different runtime from the app's ORT |
| `handy-computer/granite-speech-5.0-470m-turboctc-gguf` | Apache-2.0 | For transcribe.cpp (MIT; vendors only ggml and miniz, both MIT) |
| IBM's WebGPU Space `onnx-ctc/ctc_conformer_q4f16.onnx` | no licence tag on the Space | Scripts not published |
