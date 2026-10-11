# Granite formatting spike, September 2026 (#33)

Granite Speech 5.0 470M (#26, #32) writes lowercase text with no punctuation.
It turns a possessive "'s" into " is" ("bjorn is birthday party") and expands
contractions ("i will"). This spike measured post-pass models that restore
case, punctuation and apostrophes, on the host harness, against Parakeet v3's
raw text. Tooling: `tools/asr-bench/granite/format_*.py`.

## Summary

- **Winner: our own tiny formatter.** It is a 7.4M-parameter Transformer
  trained from scratch on permissively licensed text: 7.5 MB int8, under 1 ms
  per utterance.
  - With it, Granite's raw dictation WER is **34.0** (Parakeet v3: 36.2) and
    FLEURS-en **17.1** (Parakeet: 16.2).
  - It restores **24** of the 31 reference words with an apostrophe
    ("Björn's", "I'll"), which no pretrained punctuator can do.
  - It runs as a post-pass inside the Granite engine (`GraniteFormatter.kt`),
    bundled in the APK (Max, 2026-09-27).
- 1-800-BAD-CODE's punctuator, which IBM's own demo uses, comes close on
  punctuation. But it is 210 MB (53 MB after our int8), title-cases news-style
  ("Open the File Quarterly Report"), and can't restore apostrophes.
- sherpa-onnx's English punctuator is small and fast, but weaker on dictation.
  About a quarter of its training data is unnamed.
- A 0.5B instruct LLM rewrite isn't worth it. It is 1 GB, takes 3.4 s per
  utterance, rewrites words, and does no better.

## Results

Granite's hypotheses from the #26 harness run (`results-26`, qwertz92 int8,
the app's VAD and pre-roll) were formatted by each candidate. Raw WER splits
on whitespace, so case and punctuation count: what a user sees. Normalised WER
(Whisper's normaliser) checks that a formatter doesn't damage words.
"Apostrophes" counts reference words with an apostrophe (dictation +
FLEURS-en, 31 in all) that appear in the output. Timing is on the benchmark host with 1
thread, while other jobs were running, so it's relative only. No `:textproc`.

| Candidate | Dictation raw | FLEURS-en raw | Apostrophes | FLEURS norm | Size | ms / utt | Licence / provenance |
|---|---|---|---|---|---|---|---|
| Parakeet v3 (the bar) | 36.2 | 16.2 | 26/31 | 8.3 | – | – | – |
| Granite, raw | 58.0 | 28.5 | 0/31 | 6.7 | – | – | – |
| **+ ours, v2** (Tatoeba + CV + CC BY news) | **34.0** | **17.1** | **24/31** | 6.7 | 7.5 MB | 0.5 | MIT (ours); data CC BY 2.0 FR / CC0 / CC BY 4.0: PASS |
| + ours, v1 (Tatoeba + CV only) | 35.3 | 19.0 | 21/31 | 6.7 | 7.5 MB | 0.7 | same, without the news |
| + 1-800-BAD-CODE `punct_cap_seg_en`, fp32 | 35.9 | 17.1 | 0/31 | 6.9 | 210 MB | 48 | Apache-2.0 (tag only); WMT News Crawl: PASS |
| + the same, our int8 | 36.2 | 17.3 | 0/31 | 6.9 | 53 MB | 44 | same (the int8 file would need hosting) |
| + sherpa-onnx `online-punct-en-2024-08-06` (int8) | 44.8 | 17.9 | 0/31 | 6.7 | 7.6 MB | 4 | Apache-2.0, holder unclear; Wikipedia + TED + unnamed interview transcripts: GREY |
| + Qwen2.5-0.5B-Instruct rewrite | 39.6 | 18.3 | 10/31 | 9.1 | 988 MB | 3,362 | Apache-2.0; undisclosed data |

- **Text-only check** (the references themselves, normalised Granite-style
  and then formatted, so recognition errors don't count): dictation / FLEURS
  raw WER are 27.3 / 14.7 for ours v2, 29.4 / 14.5 for the
  1-800-BAD-CODE int8, and 38.7 / 14.9 for sherpa. The unformatted floor is
  54.9 / 26.9: what normalising alone costs.
- **The dictation set is small and synthetic** (40 sentences, Kitten TTS, #14).
  Its ± is several points, so read it with FLEURS.
- **Qwen2.5-1.5B wasn't measured.** The 0.5B had already shown the shape (slow,
  large, rewrites words). The 1.5B run was stopped to free the CPU for
  training.
- **None of the candidates change Granite's words** (the normalised WER is
  unchanged), apart from the LLM and 1-800-BAD-CODE's acronym periods
  ("p.m."), which the normaliser scores slightly differently.

## The candidates' provenance

The model-ethics bar for a word labeller is how its data was acquired; it
can't reproduce its training text. Researched 2026-09-27. "Unverified" marks
what no artifact settles.

- **1-800-BAD-CODE `punctuation_fullstop_truecase_english`**. This is the
  model IBM's Granite WebGPU demo ships as `punct_cap_seg_en.onnx`; the old
  repo id redirects to this one.
  - Revision `b26fd1c40e88678859048898218ea4edcc24c84a`, file
    `punct_cap_seg_en.onnx`, 209,532,928 B, sha256 `dd922d45…1421b`.
  - Licence: an Apache-2.0 tag. The repo has no LICENSE file.
  - Holder: the account is pseudonymous; its GitHub profile names Shane
    Carroll (unverified as the holder).
  - Model: a 6×512 Transformer, about 52M parameters. The author publishes
    fp32 only; the int8 above is our `quantize_dynamic`.
  - Training data: about 10M lines of WMT News Crawl (2012, 2021). Its
    packaging is CC0, and it is public news pages, scraped and disclosed:
    PASS for a labeller. The card warns it's news-domain.
  - Output: `. , ?`, acronym periods, per-character truecase and sentence
    breaks. No apostrophes.
  - Porting it would mean a SentencePiece unigram tokenizer in Kotlin. IBM's
    `punctuator.js` has no licence, so reimplement it; don't copy it.
- **sherpa-onnx `online-punct-en-2024-08-06`** (CNN-BiLSTM).
  - The weights are from `frankyoujian/Edge-Punct-Casing`, by Cisco
    Shanghai authors (arXiv 2407.13142) on a personal account.
  - Licence: an Apache-2.0 tag. The holder is unclear.
  - Training data (the paper): Wikipedia 58 % (CC BY-SA), TED transcripts
    17 % (CC BY-NC-ND) and "public interview transcripts" 25 %, unnamed. The
    released unigram tokenizer doesn't match the paper's BPE, so even that
    mix is unverified for these weights. GREY.
  - Output: `, . ?` and word Upper/Cap. sherpa's decoder ignores its
    mixed-case label. It is already in our JNI build (`OnlinePunctuation`).
- **sherpa-onnx `punct-ct-transformer-zh-en`** (FunASR).
  - Not measured. It doesn't case English.
  - It is 72 MB int8.
  - Its terms conflict: ModelScope's Apache tag vs FunASR's model licence,
    which is non-OSS, with non-disparagement and auto-revision clauses.
  - Its data is opaque. FAIL.
- **Qwen2.5-0.5B-Instruct** (rev `7ae55760…`). Apache-2.0 with a LICENSE
  file, but its data is opaque; for a desktop upper bound only. Qwen2.5-3B is
  under the Qwen Research License, not Apache.
- **Ours.** Trained from scratch on the three sources below: PASS
  (`docs/model-reviews/granite-formatter.md`). Common Voice's Wikipedia
  sentence files, Common Pile's share-alike news and syndicated copies were
  left out on purpose.

## What the formatter does

It is a token classifier over Granite's own BPE ids (the `tokenizer.json`
that comes with the engine). For each word it predicts three labels:
- case: lower, Capitalised or UPPER
- the mark after the word: none, `.` `,` `?` `!` `:`
- whether the word is an expanded contraction to join back onto the previous
  word: "is" → "'s", "will" → "'ll", "not" → "n't" (with "won't" and
  "can't"), and so on

It can't add, drop or respell a word. So errors Granite made stay, and so do
emails and numbers ("maria lopez example com", "$1800"); `:textproc` handles
some of those.

Examples from dictation and LibriSpeech clean (reference / Granite / formatted by v2):

| | |
|---|---|
| ref | Thanks, Chidinma! I'll send the slides tonight. |
| Granite | thanks chididenma i will send the slides tonight |
| formatted | Thanks Chididenma. I'll send the slides tonight. |
| ref | Tell Krzysztof that the package shipped on June 3rd. |
| Granite | tell christoph that the package shipped on june 3 |
| formatted | Tell Christoph that the package shipped on June 3. |
| Granite | the peculiar circumstances of the colony are within your excellency is knowledge |
| formatted | The peculiar circumstances of the colony are within your excellency's knowledge. |
| Granite | you have not seen any of them no sir |
| formatted | You haven't seen any of them. No, sir. |

It sometimes contracts where the speaker didn't ("the greeting of the
apostle's refreshing"). Raw WER counts that as an error, the same as a missed
contraction.

**Training.** `format_data.py` turns a cased, punctuated sentence into a pair.
Whisper's English normaliser gives the input (Granite's text is that
normaliser's output almost exactly, #26), and aligning it with the original
gives the labels. Sentences the alignment can't match word for word are
dropped: 0.6 % of Tatoeba and Common Voice, 8 % of the news passages. No text or labels came from an LLM.

| Data | Size | Licence |
|---|---|---|
| Tatoeba English (downloaded 2026-09-27) | 2.04M sentences | CC BY 2.0 FR |
| Common Voice `sentence-collector.txt` (not the Wikipedia files) | 61.5k sentences | CC0 |
| Common Pile `news`, CC BY 4.0 documents on the publishers' own sites (v2 only) | 900k passages of 1–3 sentences (822k kept) | CC BY 4.0 |

- Every dictation and FLEURS-en reference was removed from training.
- The model: 4 pre-norm Transformer layers, d 256, 4 heads, FF 1024, learned
  positions up to 256 tokens, and 7.42M parameters (4.19M of them the
  embedding). It starts from random weights.
- Training: AdamW, lr 1e-3, cosine schedule, batches of about 8k tokens.
  PyTorch 2.11 on the benchmark host's CPU (2 or 4 threads, nice 19), no GPU.
  - v1: 2 epochs of 18.4M tokens, 2 h.
  - v2: 1.2 epochs of 45.2M tokens, 2.2 h.
- Export: ONNX opset 17, then onnxruntime `quantize_dynamic` int8. The export
  spells out attention with dynamic reshapes, because the legacy exporter
  freezes the sequence length in PyTorch's fused attention.
- The ethics review is in `docs/model-reviews/granite-formatter.md`.

## In the app

- `GraniteFormatter.kt`:
  - `GraniteBpe` is a Kotlin port of the tokenizer.json BPE (GPT-2 split
    regex, byte stand-ins, merges by rank).
  - An ONNX Runtime session runs the model.
  - `FormatLabels` is the twin of `format_data.apply`.
- `GraniteOfflineTranscriber` formats every decode, partials and Finals alike,
  with the bundled `assets/granite/formatter.int8.onnx`.
- Parity (`GraniteParityTest`, with `dump_golden.py formatter`): BPE ids match
  Python `tokenizers` on 2,300/2,300 words. The formatted text matches the
  Python formatter on 440/440 Granite outputs (dictation, FLEURS, AMI). As
  with Granite itself, the Python side must run on the test machine's CPU.
- On the emulator, dictating with the formatter gave "Can you look into the
  company logo? The meeting moved to March 14 2027." That run caught one
  Android-only bug: ICU regex rejects `UNICODE_CHARACTER_CLASS`.

## Reproduce

```sh
# on the benchmark host; $WORK = the asr-bench scratch dir, harness copied to $WORK/harness-32
uv venv -p 3.12 $WORK/venv-format && VIRTUAL_ENV=$WORK/venv-format uv pip install \
  --index-strategy unsafe-best-match --extra-index-url https://download.pytorch.org/whl/cpu \
  -r tools/asr-bench/granite/format-requirements.txt punctuators==0.0.7
# text: $WORK/format-33/text/{train-*,val,news-*,news-val}.txt (see the model review for sources)
python granite/format_data.py build --tokenizer tokenizer.json --out ../format-33/train-0.pt ../format-33/text/train-0.txt  # per shard
python granite/format_model.py train --data ../format-33/train-?.pt ../format-33/news-?.pt \
  --val ../format-33/val.pt ../format-33/news-val.pt --out ../format-33/tiny-256x4-news --threads 4 --epochs 1.2
python granite/format_model.py export --out ../format-33/tiny-256x4-news
python granite/format_spike.py --work .. --out ../results-33 --threads 1 --candidates none ours:../format-33/tiny-256x4-news \
  pcs:../format-33/pcs pcs:../format-33/pcs/int8 llm:Qwen/Qwen2.5-0.5B-Instruct
../venv/bin/python granite/format_spike.py --work .. --out ../results-33 --threads 1 --candidates sherpa:../format-33/sherpa-punct
```
