# Model Ethics Review: NVIDIA Parakeet TDT-CTC 110M (int8, k2-fsa exports via HF mirrors)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill. This is a **quick review** for issue #25.
NVIDIA's org record, Granary and the NeMo public corpora are already covered in
[`parakeet-tdt-0.6b-v3.md`](parakeet-tdt-0.6b-v3.md), [`canary-180m-flash.md`](canary-180m-flash.md) and
[`retired/nemotron-speech-streaming-en-0.6b.md`](retired/nemotron-speech-streaming-en-0.6b.md). Only what differs is re-checked here.
Scope: one of two int8 exports of the same checkpoint, used only to transcribe the user's own dictation on-device.

**Which export ships:** the transducer (TDT) head, from the punitd mirror. On the #25 host benchmark it beat the CTC
head on every English set at the same speed (`docs/benchmarks/2026-09-host-wer.md`).

## Decision

**2026-09-28, Max: approved as the regular small user engine despite the GREY provenance.** Parakeet TDT_CTC 110M is
in the user lineup next to Parakeet v3 (the default) and Nemotron 3.5, and the setup guide recommends it on phones
short of memory or speed for English.

## Verdict first

**GREY against Max's bar, the same grade as canary-180m-flash, for the same reason.** License is clean. Provenance is the
weakest of the Parakeet family reviewed so far.

- **Bar #1 (no piracy): no evidence of pirated or paywalled-scraped data, but it cannot be ruled out for 75% of the
  hours.** The card: "trained on 36K hours of English speech collected and prepared by NVIDIA NeMo and Suno teams … private
  subset with 27K hours of English speech plus 9k hours from the following public PnC datasets". The 27k hours are not
  named anywhere. Suno is the AI-music company that, in a September 2026 filing, admitted downloading YouTube audio
  with yt-dlp to train its music models (evidence in `canary-180m-flash.md`, Org track record). Nothing links that to the
  speech data it collected with NVIDIA. **UNVERIFIED either way.**
- **Bar #2 (impersonation): N/A.** ASR outputs text.
- **License: CC-BY-4.0.** Card tag and prose agree, unchanged since the first commit. Neither mirror adds terms. No GPL/LGPL.

**Blocking issues:** none for a developer engine. **If this becomes a user-facing or default engine, Max should decide
whether the opaque 27k hours are acceptable**, exactly as `canary-180m-flash.md` asks for its 54k hours. Granary-trained
Parakeet v2 (see `retired/parakeet-tdt-0.6b-v2.md`) does not have this problem.

## Files reviewed

The int8 files exist upstream only as tarballs in the sherpa-onnx GitHub release `asr-models`
(https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models). GitHub's API asset digests (checked 2026-09-27):

| Tarball | Bytes | Uploaded | sha256 |
|---|---|---|---|
| `sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8.tar.bz2` | 104,337,827 | 2025-07-08 | `17f945007b52ccd8b7200ffc7c5652e9e8e961dfdf479cefcabd06cf5703630b` |
| `sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8.tar.bz2` | 108,035,095 | 2026-05-03 | `f628312e9fdf8686374cb01a69425c41732529d540860311f16f37cbc32cfe9b` |

k2-fsa's HuggingFace repos of the same names hold only `.gitattributes` (HF API, created 2025-07-08). The transducer
tarball was added on request in https://github.com/k2-fsa/sherpa-onnx/issues/3570. The app downloads from third-party HF
mirrors whose files the lead session found byte-identical to the extracted tarballs (sha256, 2026-09-27). Sizes and LFS
hashes below re-checked against each mirror's HF tree API at the pinned commit. `tokens.txt` is not in LFS; downloaded
and hashed.

| Export | Mirror @ commit | File | Bytes | sha256 |
|---|---|---|---|---|
| CTC | `VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8` @ `43869f73831659dd8b0ccee617ee42474b7aa974` | `model.int8.onnx` | 131,652,171 | `9177a9146cf32ee0cc8152276ef95116f312018d316be37ccf57f7efea81fc1a` |
| CTC | same | `tokens.txt` | 9,953 | `450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10` |
| TDT | `punitd/sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8` @ `66a4fa70643dc7ce25c9b38b2f87e1b35ddad33d` | `encoder.int8.onnx` | 131,113,202 | `0f35509ddeb9b39002fb077d979a9fe74f06eb0bc4dd5c34f512f82e5111d657` |
| TDT | same | `decoder.int8.onnx` | 3,955,863 | `f7c331c5504c2e593c76ed22b728e3f554af6c4a383dde862e719ced08b1da19` |
| TDT | same | `joiner.int8.onnx` | 1,411,403 | `bf7dff69e9f2cdbe9943d70da358f38b361c115ba0105bae7e908e0d6ec782f6` |
| TDT | same | `tokens.txt` | 9,953 | `450e56bd2f036fe5b6aa821865838cc5aa9d8b0106134ce9a9ba0664abe6cd10` (same file as CTC) |

**Embedded metadata** (tail of each ONNX file, HTTP range request): producer `onnx.quantize` 0.1.0,
`model_type=EncDecHybridRNNTCTCBPEModel`, `model_author=NeMo`, `vocab_size=1024`, and the card's "36K hours … NVIDIA NeMo
and Suno teams" sentence as `doc`. The CTC file says "Only the CTC branch is exported" and links NGC
(`catalog.ngc.nvidia.com/…/parakeet-tdt_ctc-110m`). The TDT encoder says "Only the transducer branch is exported" and links
`https://huggingface.co/parakeet-tdt_ctc-110m` (missing `nvidia/`, so a dead link; cosmetic).

**Upstream checkpoint:** `parakeet-tdt_ctc-110m.nemo`, 459,243,520 B, sha256
`1e6b5c3bcaa390b479bd8112512ede515d2ef5b61495568f2c266d6d5e680c3a`, uploaded 2024-09-17 (`4e942108`), unchanged since
(https://huggingface.co/api/models/nvidia/parakeet-tdt_ctc-110m/commits/main). Both exports are format conversion plus
dynamic int8 quantization of one branch, no retraining. Under CC-BY that is a modification and must be indicated.

### The mirrors: availability risk only, no added trust and no added terms

The pinned sha256 values mean only k2-fsa's bytes are accepted, so a mirror owner cannot change what runs. The remaining
risk is availability: a mirror can be deleted or its history rewritten, and the pinned download then breaks.

- **punitd** (created 2026-08-10): no README, no license tag, no card (`tags: onnx, region:us`). Adds no terms.
- **VocaHQ** (created 2026-09-19): `license: cc-by-4.0`, `base_model: nvidia/parakeet-tdt_ctc-110m`, a README crediting
  NVIDIA under CC-BY-4.0, and `quantize.py`. It adds no terms of its own. **But its README claims it made the int8 file
  itself:** "Change made here: dynamic int8 weight quantization of `model.onnx` with ONNX Runtime 1.30.0
  (`QuantType.QUInt8`)", from k2-fsa's FP32 export `csukuangfj/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000` at
  `3af92f15` (2024-09-27), because "the published sherpa-onnx int8 repository for this model contains no model files".
  The file is byte-identical to the one in k2-fsa's 2025-07-08 tarball. Either VocaHQ's quantization reproduced
  k2-fsa's output exactly (dynamic quantization is deterministic, and k2-fsa uses the same `QUInt8` call), or the file was
  taken from the tarball and the README is inaccurate. Not resolved here (no onnxruntime on this host to re-run
  `quantize.py`). **It does not matter for trust or licensing:** the bytes are k2-fsa's either way, and both paths are
  CC-BY modifications of NVIDIA's model. Our NOTICE should credit the k2-fsa release, not VocaHQ's claim.

## License

- **CC-BY-4.0.** Card metadata `license: cc-by-4.0`; prose "## Licence: License to use this model is covered by the
  CC-BY-4.0 … By downloading the public and release version of the model, you accept the terms and conditions of the
  CC-BY-4.0 license." Identical at the first card commit `12719e95` (2024-09-17) and at head `431a349f`.
  https://huggingface.co/nvidia/parakeet-tdt_ctc-110m
- **No copyright notice supplied** (no LICENSE file, no "Copyright" line). Per CC-BY §3(a)(1)(A) we identify the creators
  as named: the card says "jointly developed by NVIDIA NeMo and Suno.ai teams", so credit both.
- **Dataset licenses:** the named 9k public hours (LibriSpeech, Fisher, NSC Part 1, VCTK, VoxPopuli, Europarl-ASR, MLS
  2k-hour subset, Common Voice 7.0) have no NC/SA inheritance problem (see `parakeet-tdt-0.6b-v3.md`, Layer 2). Fisher is a
  paid LDC licence, not scraped.

## Provenance: **PARTIAL, leaning OPAQUE**

- **Lineage:** "finetuning this model for 20,000 steps over `parakeet-tdt-1.1` model" (card). parakeet-tdt-1.1b was
  "trained on 64K hours … collected and prepared by NVIDIA NeMo and Suno teams … private subset with 40K hours"
  (https://huggingface.co/nvidia/parakeet-tdt-1.1b). So the private Suno/NeMo data enters twice: 40k h in the parent and
  27k h in the fine-tune. Neither is described. No Granary: this family predates it (Granary repo created 2025-05-15, per
  `canary-180m-flash.md`).
- **Nobody has asked.** The 5 threads on the card's community tab are technical
  (https://huggingface.co/nvidia/parakeet-tdt_ctc-110m/discussions); parakeet-tdt-1.1b's 7 are too. `gh api search/issues`
  on `NVIDIA-NeMo/Speech` for "suno", "private subset" and "parakeet private data" (2026-09-27) found nothing about data.
  The nearest answer is on canary: NVIDIA's reply to "is the Suno dataset … music lyrics?" was only "No its ASR data".
- **Suno (co-developer and data collector):** negative. The labels' amended complaint (2025-09-19) alleges Suno
  "stream-ripped music from YouTube, using code to get around the 'rolling cipher'"
  (https://www.musicbusinessworldwide.com/3-things-you-might-have-missed-in-the-major-labels-suno-lawsuit/), and Suno's
  2026 answer admits yt-dlp downloads (cited in `canary-180m-flash.md`). Music, not this ASR data, but the same company.
- **Card hygiene:** no privacy, bias or safety sub-cards at all; reference [7] cites Meta's Casual Conversations
  fairness dataset but the body never says it was used.

## Posture and org record

- **Posture: N/A for impersonation** (text output). No surveillance caution on the card. Our use is the benign case: the
  user's own dictation, on-device, never stored.
- **NVIDIA: grey/negative**, unchanged from the earlier reviews (Nazemian / Anna's Archive, Cosmos scraping, *Rogers v.
  NVIDIA* BIPA suit naming "Parakeet speech models"; see `retired/nemotron-speech-streaming-en-0.6b.md`, Org track record).
- **k2-fsa: neutral.** Converter and publisher of the tarballs (their contents beyond the model files not inspected here).
- **Demo:** no NVIDIA-hosted Space. The Developer-engines entry, running these exact files, is the demo.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License | **CC-BY-4.0, pass** | Tag and prose agree since 2024-09-17. Credit NVIDIA and Suno.ai, link license, mark modification |
| Provenance | **PARTIAL, leaning OPAQUE** | 9k h named public/LDC; 27k h (plus the parent's 40k h) private NeMo/Suno data, never described |
| Posture | **N/A** | ASR, text only |
| Org record | **NVIDIA grey/negative, Suno negative, k2-fsa neutral** | As in the canary and Nemotron reviews |
| Mirrors | **Availability risk only** | sha256-pinned to k2-fsa's bytes. VocaHQ README's self-quantization claim conflicts on its face with the byte identity |
| **Verdict vs Max's bar** | **GREY** | Bar #1 not violated on the evidence, but unverifiable for 75% of hours. Bar #2 N/A. Max approved user-facing use on 2026-09-28 (see Decision) |

## NOTICE entry (recommended)

```
Parakeet TDT-CTC 110M
Creators: NVIDIA NeMo and Suno.ai (https://huggingface.co/nvidia/parakeet-tdt_ctc-110m).
No copyright notice is supplied with the model.
License: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/legalcode
Modified: converted from NeMo to ONNX (transducer branch only) and quantized to int8 by the k2-fsa /
sherpa-onnx project, release asr-models, sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8.tar.bz2
(https://github.com/k2-fsa/sherpa-onnx/releases/tag/asr-models). Downloaded from a byte-identical copy at
https://huggingface.co/punitd/sherpa-onnx-nemo-parakeet_tdt_transducer_110m-en-36000-int8.
Provided as-is, without warranties; see Section 5 of the license.
```

The transducer export ships (see the top). CC-BY-4.0 text is already bundled.
