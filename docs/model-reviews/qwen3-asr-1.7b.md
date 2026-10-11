# Model Ethics Review: Qwen3-ASR 1.7B (int8 ONNX, community export)

Reviewed 2026-10-02 by claude-code (Opus 5.5), per the `model-ethics-review` skill, for #52.
Scope: a delta on `docs/model-reviews/qwen3-asr-0.6b.md`. Same org, same tech report (https://arxiv.org/abs/2601.21337),
same training pipeline. Only what differs is re-checked here. It is a candidate Japanese engine and would only
transcribe the user's own dictation on-device.

## Verdict first

**License: Apache-2.0, pass. Ethics: PASS (grey), the same grade as 0.6B. The export is the open question, not the
model.**

- **Weights:** `Qwen/Qwen3-ASR-1.7B` at `7278e1e70fe206f11671096ffdd38061171dd6e5` is tagged `license: apache-2.0`.
  The card prose adds no rider. The repo has no LICENSE or NOTICE file, as with 0.6B. The GitHub LICENSE
  ("Copyright 2026 Alibaba Cloud", one commit `95676676`, never changed) and the paper ("we release these models under
  the Apache 2.0 license") cover both sizes.
- **Decoder base: Qwen3-1.7B, Apache-2.0, "Copyright 2024 Alibaba Cloud"**, not Qwen3-0.6B. Paper §2.1: "Qwen3-ASR-1.7B
  is built with Qwen3-1.7B, a projector, and an AuT encoder with 300M parameters".
- **Provenance: OPAQUE, unchanged.** Same report, same stages ("approximately 40 million hours of pseudo-labeled ASR
  data"), no corpus named for either size. Re-searched QwenLM/Qwen3-ASR issues (licence, training data, dataset,
  commercial) and all 27 HF discussions on the 1.7B repo: nothing on provenance.
- **Posture: N/A for impersonation.** Org record unchanged (20-F copyright claims, Qwen3-TTS 3-second cloning).
- **The only sherpa-layout 1.7B packages are community uploads.** k2-fsa has not re-hosted a 1.7B build, and
  sherpa-onnx has **no export script of its own** for Qwen3-ASR (or Fun-ASR-Nano) at master `040afe36`. Its 0.6B
  package also came from the same third-party exporter. See Export.

## Artifact under review

| Field | Value | Evidence |
|---|---|---|
| Upstream checkpoint | `Qwen/Qwen3-ASR-1.7B` at `7278e1e70fe206f11671096ffdd38061171dd6e5` (2026-01-30, head). 2,349,217,408 BF16 parameters. `model-00001-of-00002.safetensors` 4,220,320,824 B, `a4cd1f1a…08d6`; `model-00002-of-00002.safetensors` 478,200,688 B, `6e0b9d9e…ccfc`. ModelScope `Qwen/Qwen3-ASR-1.7B` has the same sizes and hashes | https://huggingface.co/api/models/Qwen/Qwen3-ASR-1.7B/tree/7278e1e70fe206f11671096ffdd38061171dd6e5?recursive=true ; https://modelscope.cn/api/v1/models/Qwen/Qwen3-ASR-1.7B/repo/files?Recursive=true |
| Candidate package | https://huggingface.co/thieunv-asilla/sherpa-onnx-qwen3-asr-1.7B-int8 at `69eb686fd94a4a865bb5340a3d6ac0d7f1fec0d5` (2026-07-23, head, 0 downloads). Card tag `apache-2.0`, "inherited from Qwen/Qwen3-ASR-1.7B". Exported "with Wasser1462/Qwen3-ASR-onnx (`export_qwen3_asr_onnx.py --device cpu --max-total-len 512`, INT8 dynamic quantization)" | https://huggingface.co/thieunv-asilla/sherpa-onnx-qwen3-asr-1.7B-int8/blob/69eb686fd94a4a865bb5340a3d6ac0d7f1fec0d5/README.md |
| Its files | `conv_frontend.onnx` 48,080,441 B, `3cb27a9fe94d95c938e476f2012b21aba2ec0bfceef33b0e58acd208946bafdd`; `encoder.int8.onnx` 314,222,162 B, `a5deedae034ece715de8ed204378d8c77f889af3a60c2566581135e84cced7cd`; `decoder.int8.onnx` 2,037,458,645 B, `c43c853fa6e97d08365cb8a5502b360b595cd43c00dc60e4d8ca7cc18cad460b`; `tokenizer/` merges.txt, vocab.json, tokenizer_config.json. Total 2,404,222,421 B | LFS oids from https://huggingface.co/api/models/thieunv-asilla/sherpa-onnx-qwen3-asr-1.7B-int8/tree/69eb686fd94a4a865bb5340a3d6ac0d7f1fec0d5?recursive=true |
| Uploader | HF user `thieunv-asilla` ("Thiệu Nguyễn"), no org, three models (an RTMPose upload plus 0.6B and 1.7B Qwen3-ASR exports). No track record to judge | https://huggingface.co/api/users/thieunv-asilla/overview |
| The exporter's own upload | ModelScope `zengshuishui/Qwen3-ASR-onnx` (tagged `apache-2.0`) has a `model_1.7B/` folder, the same source k2-fsa used for 0.6B. `decoder.int8.onnx` is **byte-identical** to thieunv's (same size and sha256). `conv_frontend.onnx` (`fa894a4b…e2fb`) and `encoder.int8.onnx` (`436fbd91…1469`) have the **same sizes but different hashes**: thieunv re-ran the export for those two. Why is UNVERIFIED | https://modelscope.cn/api/v1/models/zengshuishui/Qwen3-ASR-onnx/repo/files?Recursive=true |
| Tokenizer | `merges.txt`, `vocab.json`, `tokenizer_config.json` are byte-identical across `Qwen/Qwen3-ASR-1.7B`, `Qwen/Qwen3-ASR-0.6B` and thieunv's package (sha256 `8831e4f1…`, `ca10d7e9…`, `4942d005…`, computed locally) | the three repos above |

## Export

- **`Wasser1462/Qwen3-ASR-onnx` has no licence.** The GitHub API reports `license: null`, the tree at `0829a577` has
  no licence file, and the three source files checked carry no licence header. Its README says "A large portion of
  the code is from https://github.com/QwenLM/Qwen3-ASR" (Apache-2.0). Issue #11 (2026-09-30) asks the author to add a
  LICENSE; no reply yet. https://github.com/Wasser1462/Qwen3-ASR-onnx/issues/11
- **What that means.** No licence means all rights reserved **in the script**. We may read it. We may not vendor,
  modify or redistribute it, and running a local copy is grey. It does **not** reach the converted files: a
  mechanical PyTorch → ONNX conversion plus int8 quantization creates no new rights for the script's author, so
  Qwen's Apache-2.0 governs the outputs. That is the position the 0.6B review already took for the package we ship
  from the same exporter.
- **So for the benchmark:** measuring with thieunv's package is fine.
- **For shipping, the licence is not the blocker; trust is.** thieunv's package is an unvetted individual upload, and
  two of its three graphs do not match the exporter's own build. In order of preference:
  1. **The exporter's own `model_1.7B/` files from ModelScope.** Same provenance chain as the 0.6B package we already
     ship (k2-fsa copied 0.6B from that same upload, and its 0.6B files there still match). The repo has no tags, only
     `master`, so pin by file sha256.
  2. **Our own export, written from Qwen's Apache-2.0 code** (`QwenLM/Qwen3-ASR`), not from Wasser1462's script,
     and published on `maxwhipw/marmalade-stt-engines`. This is the clean path if issue #11 stays unanswered and Max
     wants reproducible files. Write our own script rather than run or copy Wasser1462's while it is unlicensed.
  3. Ask k2-fsa to re-host a 1.7B build. It would still be Wasser1462's export.
- **This is looser than the brief's "measure but not ship" framing.** The files themselves are shippable under
  Apache-2.0. The reasons to prefer option 1 or 2 are integrity and reproducibility, not licence. Max's call.
- Not checked: `andrewleech/qwen3-asr-1.7b-onnx` and `baicai1145/Qwen3-ASR-1.7B-ONNX` (HF search hits, not sherpa
  layout as far as their names show).

## Feasibility (secondary)

- About 2.35B parameters and a **2.40 GB** int8 download, 2.4 times the 0.6B package (987 MB). The decoder alone is
  2.04 GB. RAM and speed on the Pixel 8a are unmeasured; that is what #52's benchmark is for.
- The export fixes `--max-total-len 512` (audio plus text tokens). What that means for long utterances is unmeasured.
- The card says the 1.7B "achieves state-of-the-art performance among open-source ASR models". Our Japanese numbers
  will decide whether the extra size over 0.6B is worth it.

## Working demo link

- https://huggingface.co/spaces/Qwen/Qwen3-ASR (RUNNING when checked). Its `app.py` loads `Qwen/Qwen3-ASR-1.7B` with
  the forced aligner. It runs on HF hardware, so uploads leave the device. Fine for test clips.
- The k2-fsa sherpa space offers only the 0.6B Qwen3-ASR package
  (https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition, `model.py`). There is no on-device-equivalent
  1.7B demo.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License (weights) | **Apache-2.0, pass** | Card tag, paper and GitHub LICENSE agree. No NOTICE file upstream. Qwen3-1.7B base also Apache-2.0 |
| License (export) | **Files: no added terms. Script: unlicensed** | Outputs carry Qwen's Apache-2.0. The script cannot be vendored or modified; issue #11 open |
| Provenance | **OPAQUE** | Same report and pipeline as 0.6B. No named corpus |
| Posture | **N/A for impersonation** | Text only. No consent or misuse language |
| Org track record | **Alibaba/Qwen grey. Uploader unknown** | As in the 0.6B review. thieunv-asilla has no track record |
| **Verdict vs Max's bar** | **PASS (grey)** | Bar #1: no evidence of piracy, sources UNVERIFIED. Bar #2: N/A. Ship from option 1 or 2 above, not from thieunv's upload |

## NOTICE entry

**Copyright lines** (verbatim from the LICENSE appendices; `QwenLM/Qwen3-ASR` at `95676676`, `Qwen/Qwen3-1.7B` at
`70d244cc`). The body is the shared `app/src/main/assets/licenses/Apache-2.0.txt`. No upstream NOTICE file exists.

```
Qwen3-ASR (Qwen/Qwen3-ASR-1.7B)
Copyright 2026 Alibaba Cloud

Qwen3 (Qwen/Qwen3-1.7B; decoder initialisation and tokenizer)
Copyright 2024 Alibaba Cloud
```

**Draft entry for `NOTICE.md` → "Downloadable speech models"** (fill in the source line for whichever option ships):

```
### Qwen3-ASR 1.7B (int8)

> Qwen3-ASR-1.7B by the Qwen team, Alibaba Cloud:
> https://huggingface.co/Qwen/Qwen3-ASR-1.7B at revision
> 7278e1e70fe206f11671096ffdd38061171dd6e5. Copyright 2026 Alibaba Cloud.
> Built on Qwen3-1.7B, Copyright 2024 Alibaba Cloud. Both Apache License 2.0.
>
> This copy is an ONNX conversion with int8 dynamic quantization, without
> retraining, from <source repo, revision and file hashes>.
```

The modification statement satisfies Apache §4(b). The review is in `docs/model-reviews/qwen3-asr-1.7b.md`.
