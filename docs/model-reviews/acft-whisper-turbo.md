# Model Ethics Review: Mahmoud Ashraf's ACFT Whisper large-v3-turbo (our own int8 conversion)

Reviewed 2026-10-02 by claude-code (Opus 5.5), per the `model-ethics-review` skill. This is a **quick review** for
issue #55. The model is OpenAI's Whisper large-v3-turbo, fine-tuned by one person for "dynamic audio context". Turbo
itself is reviewed in [`whisper-family.md`](whisper-family.md) (grey provenance; Max accepted it as a user engine on
2026-10-02), and none of that is repeated here. Scope: int8 ONNX files **we build ourselves** with
`tools/asr-bench/kotoba/convert.py --model acft-whisper-turbo`, used only to transcribe the user's own dictation
on-device. Measurements: [`../benchmarks/2026-10-turbo-faster.md`](../benchmarks/2026-10-turbo-faster.md).

## Verdict first

**PASS against Max's bar, inheriting turbo's grey provenance, plus a minor demerit for an undisclosed fine-tuning
set.**

- **Bar #1 (no piracy): no evidence of pirated or paywalled-scraped data.** Turbo's own data story carries over. The
  fine-tuning data is **not disclosed**: not on the card, and the author didn't share his scripts when asked
  (https://github.com/futo-org/whisper-acft/issues/14). The method limits what that data can add. ACFT trains the
  model to reproduce a frozen copy of *itself* (turbo on the full 30 s window) when it is given a shorter window, with
  an L2 loss on the decoder's hidden states (https://github.com/futo-org/whisper-acft, "Finetuning method"). The data
  decides which inputs that behaviour is kept on, not what the model knows. Under bar #3 (recognition model) this is
  a minor demerit.
- **Bar #2 (impersonation): N/A.** ASR outputs text.
- **Licence: Apache-2.0** for the fine-tune (card metadata and prose), on top of OpenAI's **MIT** for turbo. No GPL or
  LGPL in the weights or the conversion path (openai-whisper MIT, k2-fsa's exporter Apache-2.0, transformers' mapping
  Apache-2.0).

**Blocking issues:** none for a decision. Shipping it would need (a) Max's all-clear to host our converted files on
`maxwhipw/marmalade-stt-engines`, (b) an Apache-2.0 entry for the fine-tune in NOTICE and the in-app licences screen
next to the existing Whisper MIT notice, and (c) Max's confirmation that the FUTO rule (below) doesn't cover it.

## Artifact under review

https://huggingface.co/MahmoudAshraf/acft-whisper-large-v3-turbo at commit `6f95623c4821f6fec36f280ebc7946cc6730854c`
(last modified 2025-01-20, per https://huggingface.co/api/models/MahmoudAshraf/acft-whisper-large-v3-turbo). We use
only `model.safetensors` (fp16, 1,617,821,344 B, sha256 `5660919b2af8ad83974c24c7efcd229b757ddfcba0a5786e267c000fb6e51e59`,
checked by `convert.py` on download) and `config.json` (32 encoder layers, 4 decoder layers, 128 mel bins, vocabulary
51866: exactly turbo's shape). The repo has no LICENSE file, no `generation_config.json` and no `tokenizer.json`; the
tokenizer files it has are turbo's.

Our conversion (benchmark host, 2026-10-02): tensors renamed to openai-whisper's, cast to fp32, exported by k2-fsa's
`scripts/whisper/export-onnx.py` as `turbo` and int8-quantised by it. `--check`: transformers (fp32) and openai-whisper
on the converted checkpoint gave identical text on 5 of 5 FLEURS-en clips. The tokens file is byte-identical to the
app's `turbo-tokens.txt` (sha256 `b34b360d…`).

| File | Bytes | sha256 |
|---|---|---|
| `acft-whisper-turbo-encoder.int8.onnx` | 674,622,623 | `ca06b295e7072a3234be78393a7df9a940bb535123df4c2f34ec7a9a2e42b74f` |
| `acft-whisper-turbo-decoder.int8.onnx` | 361,070,805 | `52e7836f8bed9c25a11425231efe9773595dcaf1b135de1278fe21e6e2a553d0` |
| decoder with an 8-bit LM head and embedding (`turbo/quantize.py --head 8 --gather8`) | 246,965,860 | `b5aeff20df2613cfa8764a06bba86fa2e0ba0ba72a984e51722c2f1c543769a9` |

## Licence

- **Fine-tune: Apache-2.0.** Card metadata `license: apache-2.0`, `base_model: openai/whisper-large-v3-turbo`; card prose
  "License: Apache-2.0" (https://huggingface.co/MahmoudAshraf/acft-whisper-large-v3-turbo). There is no LICENSE file in
  the repo, so the card is the only grant. It is unambiguous, and tag and prose agree.
- **Base: MIT, "Copyright (c) 2022 OpenAI"** (see `whisper-family.md`). The bundled `whisper.txt` keeps applying.
- **Method: MIT, "Copyright (c) 2024 FUTO Organization"** (https://github.com/futo-org/whisper-acft/blob/main/LICENSE.md;
  GitHub reports `mit`). FUTO's own ACFT checkpoints (tiny/base/small) are Apache-2.0
  (https://huggingface.co/api/models/futo-org/acft-whisper-base). We use neither FUTO's code nor its checkpoints; the
  method is only described here.
- **The FUTO rule in this repo** ("reference only, never copy") is about the FUTO Keyboard and FUTO Voice Input apps,
  which are under the FUTO Source First License. This model isn't FUTO's, and the ACFT method repo is MIT. The earlier
  turbo-speed doc's "FUTO's are reference-only for us" (`2026-10-turbo-speed.md`, bottom line) assumed otherwise.
  **Max should confirm** that a third party's Apache-2.0 model trained with FUTO's MIT method is fine under his rule.

## Provenance (fine-tuning layer): OPAQUE, minor

- No dataset is named on the card. Its only data mention is the Earnings-22 evaluation.
- Asked "Would you mind sharing your exact scripts?" (2025-08-05), the author replied that he uses the models with
  TensorRT-LLM and pointed to the benchmarks; no data or scripts
  (https://github.com/futo-org/whisper-acft/issues/14). UNVERIFIED whether he used FUTO's notebook default (FLEURS
  en_us train, CC-BY-4.0) or other data.
- No community discussions on the HF repo (`/discussions` count 0).
- Because the loss pulls the model toward turbo's own full-context behaviour, the fine-tune adds little knowledge of
  its own. In our measurements its text stays close to turbo's: the same sentences with small differences, and the
  same kind of silence phrases on the same noise segments.

## Posture

N/A for ASR. No demo; the card shows WER and speed only.

## Author track record

Mahmoud Ashraf (GitHub MahmoudAshraf97), "Speech ML/AI Engineer at @TarteelAI" (https://api.github.com/users/MahmoudAshraf97),
the second-largest contributor to SYSTRAN's faster-whisper (MIT;
https://api.github.com/repos/SYSTRAN/faster-whisper/contributors). Also published ACFT large-v2 and large-v3 the same
way. Nothing adverse found. A single maintainer: no one would update the model, but our pinned conversion doesn't
depend on him.

## Feasibility (secondary)

Same graph and sherpa-onnx interface as turbo; only the app's `tailPaddings` for this engine would change. One user
reported near-baseline WER after converting it to CTranslate2 (issue #14, 2025-10-29; language and data UNVERIFIED).
Our measurements are in the benchmark doc.

## Verdict vs Max's bar

**PASS (minor demerit: undisclosed fine-tuning data; inherits turbo's grey).** The adoption decision, hosting, and the
FUTO-rule confirmation are Max's.

## FUTO lineage (head, 2026-10-03)

Checked against this repo's rule ("FUTO Keyboard / FUTO Voice Input: reference only, never copy"):
- The model (`MahmoudAshraf/acft-whisper-large-v3-turbo` @ `6f95623c`) is Apache-2.0 per its card metadata and prose. There's no LICENSE file, and no riders. Its card text was copied from FUTO's ACFT cards, a wording mix-up and not a licence term.
- The method repo `futo-org/whisper-acft` is **MIT** (LICENSE.md from the initial commit `8f64873`, 2024-03-12, never changed). It was never under the FUTO Source First License.
- The Source First License covers "the software the licensor makes available". It claims no rights over models trained with FUTO code.
- FUTO Keyboard ships sibling tiny/base/small ACFT models (its app code is Source First 1.1-kb), not this turbo. FUTO also publishes those models on HF under Apache-2.0.
- **Conclusion:** the rule targets Source First *code*; this third-party Apache-2.0 model made with an MIT method is outside it. **Max, 2026-10-03: OK to use** (pending the Pixel speed test, which decides whether it ships).
