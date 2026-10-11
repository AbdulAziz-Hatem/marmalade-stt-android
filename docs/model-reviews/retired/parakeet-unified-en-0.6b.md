# Model Ethics Review: NVIDIA Parakeet Unified EN 0.6B (int8, k2-fsa offline + 560 ms streaming exports)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill. This is a **quick review** for issue #25.
It reuses [`nemotron-speech-streaming-en-0.6b.md`](nemotron-speech-streaming-en-0.6b.md) (the NVIDIA Open Model License
precedent and NVIDIA's litigation record) and [`parakeet-tdt-0.6b-v3.md`](../parakeet-tdt-0.6b-v3.md) (Granary). Only what
differs is re-checked here. Scope: two developer engines built from the same checkpoint, used only to transcribe the
user's own dictation on-device.

## Verdict first

**PASS against Max's bar for developer-only use (grey provenance demerit, org demerit).** Provenance is a little better
than Nemotron Speech Streaming EN; the licence is the same one, same version, and that review's conclusions carry over.

- **Bar #1 (no piracy): no evidence of pirated or paywalled-scraped data.** The card names Granary's English portion
  plus public and LDC corpora. Unlike Nemotron EN, it lists **no un-itemised "Riva ASR training set"**, and the paper says
  the model was trained "on 280,000 hours of English data from the Granary dataset".
- **Bar #2 (impersonation): N/A.** ASR outputs text.
- **License: NVIDIA Open Model License Agreement, "Last Modified: October 24, 2025"**, the version already bundled as
  `app/src/main/assets/licenses/NVIDIA-Open-Model-License.txt`. Commercial use allowed. One notice line. No GPL/LGPL.

**Caveats:** Granary's YouTube audio is uploader-labelled CC, not speaker-consented, and its labels are Whisper large-v3
output (v3 review). The privacy sub-card claims consent and direct collection that its own dataset list contradicts
(below). NVIDIA's org record is negative (Nemotron review). The licence is revocable (below).

**Blocking issues:** none for developer engines. Re-review before user-facing use, as for Nemotron EN.

## Files reviewed

Both repos are Fangjun Kuang's `csukuangfj2` account (k2-fsa; identity established in the Nemotron review). Sizes and
sha256 are LFS oids from the HF tree API at the pinned commits; `tokens.txt` downloaded and hashed.

| Export | File | Bytes | sha256 |
|---|---|---|---|
| Offline | `encoder.int8.onnx` | 654,040,552 | `6716910b7a0833997fec7a410494c995d70124001a0e9b66d6370d6aced577e0` |
| Offline | `decoder.int8.onnx` | 7,257,753 | `a5e223392c90e75f8144cdb5eb95af7625db389e39edef2bd1a9c872b3298fe6` |
| Both | `joiner.int8.onnx` | 1,735,860 | `869f43f7d24595c55581ad3bf249a935fb8a71389fbdaa7504b9f46f93140f8a` |
| Both | `tokens.txt` | 8,952 | `dc0b4584ab2e4ddbf888425c076c61b736e7356a015250db7d307e6f1a8188ff` |
| 560 ms | `encoder.int8.onnx` | 654,046,389 | `e566c3f014598a41724f2df028779a2d4cf7943cbefa324964f6a72e8ee255fb` |
| 560 ms | `decoder.int8.onnx` | 7,257,777 | `34fea72425d2506600772ba191a6d3f99c0710abdb68d9a3dc89fa8cb2aa473a` |

- **Offline:** https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming at
  `8c3a10fb13408c7a7054f6898958bf1c64a8d6c7` (2026-04-27). Encoder metadata: `model_type=EncDecRNNTBPEModel`,
  `url=https://huggingface.co/nvidia/parakeet-unified-en-0.6b`, "This model contains only the non-streaming part".
- **560 ms buffered streaming:** https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms
  at `7551fd26fc810cc1e4e043e608db4d13b59be31e` (2026-05-12, from https://github.com/k2-fsa/sherpa-onnx/pull/3602).
  Encoder metadata: `streaming_model_type=nemo_parakeet_unified_streaming`, `buffered_streaming`, "Buffered streaming
  export, latency=560ms". The script's 560 ms preset is left 70 / chunk 2 / right 5 encoder frames
  (`scripts/nemo/parakeet-unified-en-0.6b/export_onnx_streaming.py`), i.e. NVIDIA's 5.6 s default left context and one of
  the latencies the card documents as a runtime choice ("from 2080ms to 160ms with step of 80ms"). Both exports use
  `quantize_dynamic` (encoder `QUInt8`, others `QInt8`); no retraining.
- **Repos:** no README, no LICENSE, no license tag (`onnx`, `region:us`). Each holds `bias.md`, `explainability.md`,
  `privacy.md` and `safety.md`, **byte-identical to NVIDIA's own sub-cards** at head (sha256 compared). So k2-fsa passes on
  NVIDIA's documentation but still not the licence text or §3.1 notice line (same gap as the Nemotron export).
- **Upstream checkpoint:** `parakeet-unified-en-0.6b.nemo`, 2,474,055,680 B, sha256
  `ec23ed9150c8fde49072c3e2d61678ab903dbcef389d658db833420cbc1da35b`. Identical at `5b96b6bc` (2026-04-07) and head
  `fe53cd88`, so both exports used it.

## License

- **Weights: NVIDIA Open Model License.** Metadata `license: other`, `license_name: nvidia-open-model-license`,
  `license_link` to https://www.nvidia.com/en-us/agreements/enterprise-software/nvidia-open-model-license/. Prose:
  "Governing Terms: Use of the model is governed by the NVIDIA Open Model License Agreement"; "ready for
  commercial/non-commercial use"; `explainability.md` and `safety.md` say the same. Tag and prose agree.
  - **Card-history wrinkle, harmless:** the very first model commit `1a86871e` (2026-04-07) carried `license: cc-by-4.0`
    in metadata while the prose already said the NVIDIA licence. The next commit (`1dac27d6`, same day) fixed the tag.
    No `.nemo` file existed at either commit, so the weights were never published under a CC-BY tag.
- **Same version as Nemotron EN.** The live licence page (fetched 2026-09-27) still reads "Last Modified: October 24,
  2025" / "Version Release Date: October 24, 2025". A word-level diff against our bundled copy differs only in section
  numbers and quote-mark tokenisation. So the bundled text needs no update.
- **Required notice (§3.1), verbatim:** "include the following attribution notice within a “Notice” text file with such
  copies: “Licensed by NVIDIA Corporation under the NVIDIA Open Model License”". As in the Nemotron review, this strictly
  binds distributors (we download from k2-fsa, we don't redistribute), but we include it with the full text anyway.
- **Guardrail clause (§2.1): nothing to bite on.** `safety.md` lists no guardrail (only "Use of this model is governed by
  the NVIDIA Open Model License Agreement"). No safety filter, watermark, encryption or DRM exists in the checkpoint.
  Int8 quantization, exporting one inference mode per file and choosing a documented latency remove no protective
  mechanism. The Nemotron review's reasoning carries over unchanged (not legal advice).
- **Other terms, unchanged from Nemotron EN:** §2.3 Trustworthy AI terms (no illegal surveillance or non-consensual
  biometric processing), §5 trademarks only to describe origin, §8 user indemnifies NVIDIA, §10 Delaware law, §11 export
  control. §3.2 ("Built on NVIDIA Cosmos") does not apply. The card does not declare a "Special-Purpose Model".
- **One correction to the Nemotron review:** it paraphrases §2.1 as NVIDIA "may update this Agreement … at any time". The
  full sentence is narrower: "NVIDIA may update this Agreement **to comply with legal and regulatory requirements** at any
  time and You agree to either comply with any updated license or cease Your copying, use, and distribution". Still a
  one-sided change right, but limited in stated purpose.
- **F-Droid:** same open question as Nemotron EN (possible `NonFreeAdd` if developer engines ship in the fdroid flavor).

## Provenance: **DISCLOSED-SCRAPED (license-filtered), with PARTIAL gaps**

- **Card:** "The majority of the training data comes from the English portion of the Granary dataset": YouTube-Commons
  109.5k h, YODAS2 102k h, Mosel 14k h, LibriLight 49.5k h. "In addition": LibriSpeech 960 h, Fisher, Switchboard-1,
  WSJ-0/1, National Speech Corpus Parts 1 and 6, VCTK, VoxPopuli EN, Europarl-ASR EN, MLS EN, Common Voice 4.0/7.0/11.0,
  People's Speech, AMI. "Labeling Method: Hybrid (Human, Synthetic)". The same named list as Nemotron EN, **minus** its
  "NVIDIA Riva ASR training set (250k hours)", plus NSC Part 6.
- **The totals don't add up.** The card says "Audio Training Data Size: 250k hours", but the four Granary parts alone sum
  to 275k; the paper says 280k hours of Granary English (https://arxiv.org/abs/2604.19079, experimental setup, Andrusenko
  et al.) and does not mention the "In addition" corpora. Sloppy, but no hidden block is claimed anywhere.
- **Piracy check:** nothing paywalled or pirated among the named sources. Fisher, Switchboard and WSJ are paid LDC
  licences, not scraped. YouTube CC-flag accuracy **UNVERIFIED** (as v3).
- **Privacy sub-card contradicts the data list** (identical in NVIDIA's repo and both k2-fsa repos): "Personal data used?
  Yes - Voice", "Was consent obtained … Yes", "collected directly by NVIDIA? Yes", "mechanism … to honor … deletion? Yes",
  yet also "not possible with externally-sourced data". Granary is third-party YouTube/LibriVox audio with no speaker
  consent. Same overstatement as the Nemotron EN sub-card.
- **Bias sub-card is better than its siblings:** "Used a custom dataset to evaluate model performance across genders, age
  groups, and linguistic backgrounds" (Nemotron EN and Parakeet v2 answer "None").
- **Community:** 6 discussions, none on data or licence (https://huggingface.co/nvidia/parakeet-unified-en-0.6b/discussions).

## Posture and org record

- **Posture: N/A for impersonation.** Surveillance is prohibited by the incorporated Trustworthy AI terms (binding, as
  for Nemotron EN). Intended users per `explainability.md`: "call centers, virtual assistants, and language learning
  assistants". Our use is the benign case: own dictation, on-device, never stored.
- **NVIDIA: negative** (allegations: Nazemian / Anna's Archive, Cosmos scraping, *Rogers v. NVIDIA* BIPA suit naming
  "Parakeet speech models"; *Berliner v. Huang* on Granary consent). See the Nemotron review. **k2-fsa: neutral.**

## Demo

No NVIDIA Space for this model (HF API lists none). The Developer-engines entries, running these exact files, are the demo.

## Verdict

| Axis | Grade | Basis |
|---|---|---|
| License | **NVIDIA Open Model License (2025-10-24), pass for our use** | Same version as Nemotron EN; bundled text current. One notice line. Guardrail clause has nothing to act on. Revocable |
| Provenance | **DISCLOSED-SCRAPED (license-filtered), PARTIAL gaps** | Granary English + named public/LDC corpora; no Riva block. Hour totals inconsistent. Privacy sub-card overstates consent |
| Posture | **N/A; surveillance prohibited by incorporated terms** | ASR, text only |
| Org record | **NVIDIA negative, k2-fsa neutral** | As in the Nemotron review |
| **Verdict vs Max's bar** | **PASS for developer-only use (grey provenance, org demerit)** | Bar #1: no evidence of piracy. Bar #2: N/A. Re-review before user-facing use |

## NOTICE entry (recommended)

```
NVIDIA Parakeet Unified EN 0.6B
Licensed by NVIDIA Corporation under the NVIDIA Open Model License
```

One entry covers both engines. The first line names the model; the second is the §3.1 line verbatim. Suggested
provenance note: "int8 ONNX conversions (offline, and 560 ms buffered streaming) by the k2-fsa / sherpa-onnx project,
https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-non-streaming (commit 8c3a10fb) and
https://huggingface.co/csukuangfj2/sherpa-onnx-nemo-parakeet-unified-en-0.6b-int8-streaming-560ms (commit 7551fd26)".
The licence gives no separate copyright line (§2.4 "NVIDIA owns the Model"). The full agreement text is already bundled.
