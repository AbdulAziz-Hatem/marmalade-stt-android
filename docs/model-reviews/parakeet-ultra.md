# Model Ethics Review: Moondream Parakeet Ultra (our own int8 conversion)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill. This is a **quick review** for issue #25.
Parakeet Ultra is a post-trained NVIDIA parakeet-tdt-0.6b-v3. Everything about the NVIDIA base (Granary, NeMo ASR Set
3.0, NVIDIA's org record) is in [`parakeet-tdt-0.6b-v3.md`](parakeet-tdt-0.6b-v3.md) and is not repeated here. Scope:
int8 ONNX files **we build ourselves** with `tools/parakeet-ultra/convert.py`, used only to transcribe the user's own
dictation on-device. The app would not download Moondream's files, and would not use Moondream's Photon runtime.

## Verdict first

**PASS against Max's bar, inheriting v3's grey provenance and NVIDIA org demerit, plus a minor demerit for undisclosed
post-training data.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-scraped data.** Everything v3 was trained on carries over.
  Moondream's post-training data is **not disclosed** anywhere: not on the card, and not in the release post. Under bar
  #3 that is a minor demerit for a recognition model, not a disqualifier. The only data the card mentions is MUSAN noise
  ("the half of the corpus not used for training"). MUSAN is CC BY 4.0 (https://www.openslr.org/17/).
- **Bar #2 (impersonation): N/A.** ASR outputs text. The extra `vad_head` outputs a speech/pause signal, not audio.
- **License: CC-BY-4.0**, consistent with v3. The attribution chain is NVIDIA → Moondream → us. No GPL/LGPL in the weights
  or the conversion path (NeMo and transformers' mapping are Apache-2.0).

**Blocking issues:** none. Max approved hosting the converted files publicly (2026-09-28). They are release `v1` of
https://github.com/maxwhipw/marmalade-stt-engines (#39), whose README and NOTICE meet the CC-BY requirements set out
below.

## Artifact under review

https://huggingface.co/moondream/parakeet-ultra at commit `73175eb7aeb0d82f1e2a6b53b3aabc10a90bcd0b` (2026-09-23; the
repo's only commit, "parakeet-ultra: weights, config, tokenizer and model card"). The size and sha256 come from the HF
tree API. We use only `model.safetensors`.

| File | Bytes | sha256 |
|---|---|---|
| `model.safetensors` | 1,255,353,386 | `c9608f36d0ab956c14bfcc525479b0746b3b42a56f6949ec85c14eb7466717dc` |
| `config.json`, `tokenizer.json`, `README.md`, `.eval_results/…yaml` | 1,153 / 1,159,960 / 6,433 / 2,541 | not used |

- **Format:** a transformers `ParakeetForTDT` checkpoint (`config.json`: `model_type: parakeet_tdt`, `vocab_size: 8193`,
  24 encoder layers, 1024 hidden). It contains 729 tensors, including six `vad_head.*` tensors (proj 1024→128, ctx 128×128×5,
  out 128→1).
- **Precision: fp16, not "full precision".** I read the safetensors header (HTTP range request). It holds 356 F16 tensors
  (1,253,809,154 B, every weight matrix, the VAD head included), 349 F32 tensors (1,464,344 B, biases and norms) and 24
  I64 batch-norm counters. So the card's "same 0.6B parameters in full precision", the release post's "keeps the
  full-precision weights" and `config.json`'s `"dtype": "float32"` are all loose. Read in context, "full precision" means
  "not ternary like Redux". This is a documentation accuracy issue, not an ethics one. `convert.py` casts each tensor to
  v3's dtype before export, so the ONNX files carry Ultra's fp16 values exactly.
- **Our conversion** (`tools/parakeet-ultra/convert.py`):
  - NeMo loads NVIDIA's `parakeet-tdt-0.6b-v3.nemo` (revision `541d1f99c6b0c3cd0b11a95167540bb8edefd82b`, sha256
    `3cbdc85877e668ca7b82d0d56770eb1fac76691f55d6b97545e8d61ca588d10d`; the checkpoint the v3 review verified).
  - Every learned weight is replaced by Ultra's under a 1:1 name mapping. Fixed buffers (mel filterbank, window,
    positional table) and the tokenizer stay NVIDIA's, which the card says Ultra shares ("same tokenizer").
  - `vad_head.*` is dropped.
  - The transducer is exported with NeMo's `.export()` and int8-quantized the way k2-fsa quantizes v3.
  - Under CC-BY this is a further **adaptation**, layered on Moondream's adaptation of NVIDIA's model.

## License

- **Card:** metadata `license: cc-by-4.0`. The prose says "License is CC-BY-4.0, same as the original" and "Based on
  parakeet-tdt-0.6b-v3 by NVIDIA", with a link. The release post says "Weights are available under the CC-BY-4.0
  license, same as NVIDIA's original model" (https://moondream.ai/blog/introducing-parakeet-redux-and-ultra, 2026-09-22).
  The Redux sibling is also tagged `cc-by-4.0`.
- **Consistent with v3.** CC-BY-4.0 has no share-alike clause. An adapter may license its own contribution on any terms
  that don't restrict use of the original (§3(b)), so choosing CC-BY for Ultra is the clean case. Moondream credits
  NVIDIA and links v3, but it supplies no copyright notice of its own, and neither did NVIDIA. It does not describe what
  it changed beyond "post-trained". That meets CC-BY's "indicate modifications" at a minimum level.
- **Photon is not ours to worry about, but note it:** the `moondream` PyPI package (author "M87 Labs") and its engine
  dependency `kestrel` 0.8.2 ship with **no license field or file**, which means all rights reserved. The client takes
  an optional `api_key` and mentions a "final telemetry flush". We don't use Photon. Our pipeline is open tools only.
- **If we host the converted files publicly** (CC-BY §3(a)), we must:
  - credit **NVIDIA** (parakeet-tdt-0.6b-v3) and **Moondream / M87 Labs** (Parakeet Ultra), with links to both;
  - name CC BY 4.0 and link its text;
  - say that the files are modified and how: post-trained by Moondream; converted by us to ONNX (transducer only, VAD head
    removed) and quantized to int8;
  - keep the disclaimer;
  - add no terms or DRM that restrict recipients (§2(a)(5)(B)).

  Putting the NOTICE text below in the hosted repo's README, plus a `cc-by-4.0` tag, satisfies all of that. Our own
  code (convert.py) stays MIT.

## Provenance: **inherits v3 (DISCLOSED-SCRAPED, license-filtered) + an OPAQUE post-training layer**

- **Base:** v3, graded in `parakeet-tdt-0.6b-v3.md`.
- **Post-training: undisclosed.** The card and release post give benchmarks, speed and the VAD head, but no dataset,
  hours or teacher model. The one training hint is MUSAN, held-out half reserved for evaluation. Whether the
  post-training data includes the FLEURS, TED-LIUM or Open ASR Leaderboard distributions it is benchmarked on is
  **UNVERIFIED**; that is a benchmark-honesty question, not a piracy one.
- **Nobody has asked yet.** The HF community tab has 2 threads ("Awesome!", "Demo for this model on Spaces"). The model is
  5 days old.

## Posture and org record

- **Posture: N/A for impersonation.** The card makes no surveillance claims either way. Our use is the benign case: own
  dictation, on-device, never stored.
- **Moondream (M87 Labs, Inc.): neutral, with an opacity habit.**
  - It is known for its small open vision-language models.
  - Its vision training data is also undisclosed. A direct GitHub question asking which datasets were used has sat
    unanswered since 2025-02-10 (https://github.com/m87-labs/moondream/issues/245, 0 comments).
  - Its licensing has tightened: moondream2 was Apache-2.0; Moondream 3 Preview is BSL 1.1 with a no-competing-hosted-API
    grant (https://huggingface.co/moondream/moondream3-preview/blob/main/LICENSE.md); 3.1 uses a custom "Moondream Model
    License 1.0". Ultra and Redux match v3's CC-BY, though CC-BY did not force that choice.
  - A quick web search found no lawsuits, retractions or scraping reports about Moondream (sweep not exhaustive).
- **NVIDIA (base): grey/negative**, as in the v3 and Nemotron reviews.
- **Demo:** no first-party hosted demo (a third-party Space is offered in discussion #1, not checked). Moondream's
  numbers are self-reported, from Photon on NVIDIA GPUs. Our converted files in Developer engines are the real demo.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License | **CC-BY-4.0, pass** | Tag, prose and blog agree; consistent with v3. Credit NVIDIA and Moondream, link, mark modifications |
| Provenance | **v3 grade + OPAQUE post-training layer** | Post-training data undisclosed (MUSAN noise the only named source) |
| Posture | **N/A** | ASR, text only; VAD head outputs a pause signal |
| Org record | **Moondream neutral (opacity habit), NVIDIA grey/negative** | Unanswered dataset question on its vision model; licence tightening on its own models; no scandals found |
| **Verdict vs Max's bar** | **PASS (grey provenance, minor post-training demerit, NVIDIA org demerit)** | Bar #1: no evidence of piracy. Bar #2: N/A |

## NOTICE entry (recommended)

```
Parakeet Ultra
Created by Moondream (M87 Labs, Inc.), https://huggingface.co/moondream/parakeet-ultra (commit 73175eb7),
a post-trained version of NVIDIA's parakeet-tdt-0.6b-v3, https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3.
No copyright notice is supplied with either model.
License: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/legalcode
Modified: Moondream post-trained NVIDIA's model. Marmalade converted Moondream's weights to ONNX (transducer only;
the voice-activity head removed) using NVIDIA's v3 model graph and tokenizer, and quantized them to int8.
Provided as-is, without warranties; see Section 5 of the license.
```

The files are hosted (#39): the app's NOTICE.md and licenses screen add the release URL, and the hosting repo's
`NOTICE` carries the same attribution with `LICENSES/CC-BY-4.0.txt`. The CC-BY-4.0 text is already bundled.
