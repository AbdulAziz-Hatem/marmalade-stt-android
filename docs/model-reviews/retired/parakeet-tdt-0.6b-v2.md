# Model Ethics Review: NVIDIA Parakeet TDT 0.6B v2 (int8, k2-fsa export)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill. This is a **quick review** for issue #25.
It reuses [`parakeet-tdt-0.6b-v3.md`](../parakeet-tdt-0.6b-v3.md) (Granary, NeMo ASR Set 3.0, NVIDIA's org record) and
[`nemotron-speech-streaming-en-0.6b.md`](nemotron-speech-streaming-en-0.6b.md) (later litigation). Only what differs
from v3 is re-checked here. Scope: the four files of the English int8 export, used only to transcribe the user's own
dictation on-device.

## Verdict first

**PASS against Max's bar, with the same grey provenance demerit and org demerit as v3.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-scraped data.** All training data is named: Granary's English
  portion (Creative-Commons-labelled YouTube, LibriLight) and ~10k hours of NeMo ASR Set 3.0 public and LDC corpora. No
  private or "in-house" block, unlike parakeet-tdt_ctc-110m and canary-180m-flash.
- **Bar #2 (impersonation): N/A.** ASR outputs text.
- **License: CC-BY-4.0.** Card tag and prose agree, unchanged since the weights were published (2025-05-01). The k2-fsa
  repo is tagged `cc-by-4.0` and adds no terms. No GPL/LGPL.

**Caveats** (all shared with v3, details there): Granary's YouTube audio is uploader-labelled CC, not speaker-consented;
YODAS withholds video IDs; every Granary transcript was written by Whisper large-v3; the card's privacy table says
"Personal data used to create this model? None", which is wrong for voice recordings. *Rogers v. NVIDIA* (BIPA) names
"Parakeet speech models", and NVIDIA says the Nemotron EN streaming encoder was initialised from this v2 checkpoint
(`nemotron-speech-streaming-en-0.6b.md`).

**Blocking issues:** none.

## Files reviewed

https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 at commit
`1ab9323565ddb038682214b292f588070a538ce2` (2025-08-16, "first commit", author `csukuangfj`). ONNX sizes and sha256 are
the LFS oids from the HF tree API at that commit; `tokens.txt` was downloaded and hashed.

| File | Bytes | sha256 |
|---|---|---|
| `encoder.int8.onnx` | 652,184,296 | `a32b12d17bbbc309d0686fbbcc2987b5e9b8333a7da83fa6b089f0a2acd651ab` |
| `decoder.int8.onnx` | 7,257,753 | `b6bb64963457237b900e496ee9994b59294526439fbcc1fecf705b31a15c6b4e` |
| `joiner.int8.onnx` | 1,739,080 | `7946164367946e7f9f29a122407c3252b680dbae9a51343eb2488d057c3c43d2` |
| `tokens.txt` | 9,384 | `ec182b70dd42113aff6c5372c75cac58c952443eb22322f57bbd7f53977d497d` |

- **Repo:** README is just `license: cc-by-4.0` front matter (26 B); tags `onnx`, `license:cc-by-4.0`. Also holds
  `test_wavs/`, which we don't fetch.
- **Encoder metadata** (tail, HTTP range request): `model_type=EncDecRNNTBPEModel`, `model_author=NeMo`,
  `url=https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2`, `comment=Only the transducer branch is exported`,
  `vocab_size=1024`.
- **Upstream checkpoint:** `parakeet-tdt-0.6b-v2.nemo`, 2,472,222,720 B, sha256
  `d99e39955c9d3d0350d8fb7c75e40c64a2b2eaeb003883d7c941fd2e8747b28c`. Identical at the weights commit `9101a745`
  (2025-05-01) and every later commit through head `ae9ad070`, so the export can only have used this checkpoint
  (https://huggingface.co/api/models/nvidia/parakeet-tdt-0.6b-v2/commits/main).
- **Conversion:** sherpa-onnx `scripts/nemo/parakeet-tdt-0.6b-v2/export_onnx.py`, the same NeMo `.export()` +
  `quantize_dynamic` path as v3 (https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/nemo/parakeet-tdt-0.6b-v2).
  No retraining. Under CC-BY a modification, which must be indicated.

## License

- **CC-BY-4.0.** Metadata `license: cc-by-4.0`. Prose: "GOVERNING TERMS: Use of this model is governed by the CC-BY-4.0
  license", "This model is ready for commercial/non-commercial use", and Safety "Use Case Restrictions: Abide by CC-BY-4.0
  License". Same wording at `9101a745` and at head. https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2
- **No copyright notice supplied.** Credit NVIDIA as creator, link the license, mark the modification (as for v3).
- **Dataset licenses:** Granary CC-BY, component corpora CC0/CC-BY/public domain, Fisher a paid LDC licence. No NC/SA
  inheritance problem (`parakeet-tdt-0.6b-v3.md`, License).

## Provenance: **DISCLOSED-SCRAPED (license-filtered); fine-tune layer PARTIAL**

What differs from v3 (card, "Training Dataset"):

- **English only, ~120k hours** (v3: ~660k hours, 25 languages). "10,000 hours from human-transcribed NeMo ASR Set 3.0"
  (LibriSpeech 960 h, Fisher, National Speech Corpus Part 1, VCTK, VoxPopuli EN, Europarl-ASR EN, MLS EN 2k-hour subset,
  Common Voice 7.0, AMI) plus "110,000 hours of pseudo-labeled data from YTC (YouTube-Commons), YODAS, Librilight".
- **Encoder initialised from a wav2vec-style SSL checkpoint "pretrained … on the LibriLight dataset"** (public-domain
  LibriVox audio). Stage-2 fine-tune: ~500 hours of NeMo ASR Set 3.0 (v3 used 7,500).
- **Pseudo-labelled sources are only YTC, YODAS and LibriLight** (VoxPopuli appears only as a human-transcribed ASR Set
  corpus), and the card does not mention v3's non-speech augmentation set. The pseudo-labels come from the Granary
  pipeline (Whisper large-v3, then Qwen-2.5-7B punctuation; https://huggingface.co/datasets/nvidia/Granary).
- The card files NeMo ASR Set 3.0 under "the Granary dataset". The public Granary release does not include it, and
  `nvidia/nemo-asr-set-3.0` is not publicly accessible (401, per the v3 review). Which 500 h were used: **UNVERIFIED**.
- **Piracy check:** nothing paywalled or pirated among named sources. YouTube CC-flag accuracy **UNVERIFIED** (as v3).
- **Community:** 70 discussions, none on training data or licence (titles scanned 2026-09-27,
  https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2/discussions).

## Posture and org record

- **Posture: N/A for impersonation.** No surveillance caution on the card (same generic "Trustworthy AI" text as v3).
  "Measures taken to mitigate against unwanted bias: None." Our use is the benign case.
- **NVIDIA: grey/negative** (Nazemian / Anna's Archive, Cosmos scraping, *Rogers* BIPA suit naming Parakeet; *Berliner v.
  Huang* names Granary and v3). See `nemotron-speech-streaming-en-0.6b.md`. **k2-fsa: neutral**; this repo at least carries
  the CC-BY tag, though still no attribution or modification notice.

## Feasibility and demo (secondary)

~661 MB download, English, punctuation and casing, offline transducer. Demo: NVIDIA's Space
https://huggingface.co/spaces/nvidia/parakeet-tdt-0.6b-v2 (RUNNING per the HF API, 2026-09-27; full-precision model).

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License | **CC-BY-4.0, pass** | Tag and prose agree since release. Attribution plus modification notice |
| Provenance | **DISCLOSED-SCRAPED (license-filtered); fine-tune layer PARTIAL** | Granary English (YTC, YODAS, LibriLight) + named NeMo ASR Set 3.0 corpora. No private block |
| Posture | **N/A** | ASR, text only |
| Org record | **NVIDIA grey/negative, k2-fsa neutral** | As in the v3 and Nemotron reviews |
| **Verdict vs Max's bar** | **PASS (grey provenance demerit, org demerit)** | Bar #1: no evidence of piracy. Bar #2: N/A |

## NOTICE entry (recommended)

```
Parakeet TDT 0.6B v2
Creator: NVIDIA (https://huggingface.co/nvidia/parakeet-tdt-0.6b-v2). No copyright notice is supplied with the model.
License: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/legalcode
Modified: converted from NeMo to ONNX (transducer branch only) and quantized to int8 by the k2-fsa / sherpa-onnx
project, https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8 (commit 1ab93235).
Provided as-is, without warranties; see Section 5 of the license.
```

The CC-BY-4.0 text is already bundled (`app/src/main/assets/licenses/CC-BY-4.0.txt`).
