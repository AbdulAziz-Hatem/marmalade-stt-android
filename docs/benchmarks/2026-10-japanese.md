# Is there a better Japanese engine than Whisper turbo? October 2026 (#52)

Max (2026-10-02): "Would be interested to see if there is a better Japanese STT. Japanese is hard because so many of
their words are homonyms and a model would need semantic contextual awareness to be accurate."

The app's Japanese options were measured against five candidates on the host harness (`tools/asr-bench`), with the
app's pipeline (VAD, pre-roll, AGC stand-in) and int8 files. There were three sets: FLEURS-ja (read Wikipedia
sentences), the Kokoro Speech Dataset (one reader of old novels), and a new **homophone set** that scores kanji
choice directly. Per-set statistics are in `2026-10-japanese.json` next to this file. The licence and ethics reviews
are in `docs/model-reviews/`. Nothing in the app changed.

## Recommendation

**Decided (2026-10-03):** Max accepted ReazonSpeech; Parakeet-ja is the Japanese engine since #60 ("Adoption
(#60)" below).

**Parakeet TDT_CTC 0.6B ja (NVIDIA) is the better Japanese engine, if Max accepts its training data.** On real
speech it is the most accurate engine measured, and it costs about what Parakeet Ultra costs, a tenth of turbo. It
picks the wrong kanji far less often than turbo, but writes the reading in kana when unsure. It is weak on
punctuation. Its training data is ReazonSpeech: 35,000 hours of Japanese free-to-air TV, recorded without the
broadcasters' permission under Japan's Article 30-4. That is Max's call (below), and it decides the recommendation.

- **If Max accepts ReazonSpeech:** adopt Parakeet-ja as the Japanese engine. It needs app work: the app has no NeMo
  CTC engine family yet, and its punctuation needs help (see "What adoption would take").
- **If not:** stay with Whisper turbo. **Fun-ASR-Nano** (Alibaba, Apache-2.0) is the alternative worth a second
  look. It ties turbo on accuracy, punctuates best of all, and costs 3.4× Ultra on the host, which on today's ratios puts it over a Pixel 8a's speed
  budget, like Qwen3 0.6B. Its training data is undisclosed and its licence rests on a card tag.
- **No phone-sized engine clearly beats turbo at kanji choice.** On the homophone set every candidate's interval
  overlaps turbo's. Qwen3-ASR 1.7B chooses best (86 % vs 78 %), but it needs 4.3 GB on the host and is the slowest
  engine measured. It doesn't fit a phone.
- **Drop the other candidates.** Kotoba-Whisper v2.0 loops under a full int8 export; with an fp32 decoder it
  works but is still behind turbo (8.0 vs 7.2) at turbo's cost, and chooses kanji no better.
  ReazonSpeech k2-v2 is tiny and fast, but less accurate and has no punctuation.

| Engine | FLEURS-ja CER | Kokoro Speech CER | Homophone set: right kanji / wrong kanji / kana | Punct. F1 (FLEURS) | Host cost × Ultra | Host RSS MB | Download MB | Licence | Ethics |
|---|---|---|---|---|---|---|---|---|---|
| **parakeet-ja-ctc** | **5.4** ±0.9 | **18.1** ±2.0 | 81 / **5** / 10 % | 26 | **0.92** | 1108 | 656 | CC-BY-4.0 | ReazonSpeech: Max's call |
| whisper-turbo (today) | 7.2 ±2.6 | 19.0 ±2.3 | 78 / 17 / 3 % | 62 | 7.3 | 1943 | 1037 | MIT | pass (grey) |
| funasr-nano | 9.2 ±1.3 (6.1 without digits) | 19.2 ±2.2 | 80 / 13 / 0 % | **84** | 3.4 | 1914 | 1010 | Apache-2.0 (card tag) | pass (grey) |
| qwen3-asr-1-7b | 8.9 ±1.2 | 22.1 ±2.2 | **86** / 10 / 3 % | 83 | 7.9 | 4319 | 2404 | Apache-2.0 | pass (grey) |
| sense-voice-small | 8.9 ±1.2 | 20.4 ±2.1 | 71 / 17 / 0 % | 80 | 0.47 | 466 | 240 | FunASR v1.1 (unresolved) | pass (grey) |
| qwen3-asr-0-6b (today) | 13.4 ±1.5 | 23.8 ±2.1 | 74 / 17 / 3 % | 80 | 4.0 | 2026 | 986 | Apache-2.0 | pass (grey) |
| reazonspeech-k2-v2 | 12.5 ±2.1 | 25.6 ±2.7 | 73 / 6 / 12 % | 0 | 0.32 | 482 | 160 | Apache-2.0 | ReazonSpeech: Max's call |
| kotoba-whisper-v2-0 (int8, ours) | 20.4 ±13.9 (8.0 with an fp32 decoder) | 38.8 ±5.2 | 78 / 19 / 0 % | 68 | 6.4 | 1892 | 984 | Apache-2.0 + MIT | ReazonSpeech: Max's call, then grey |
| nemotron-3-5-streaming-1120ms (today) | 14.5 ±1.6 | 24.5 ±2.2 | 46 / 43 / 0 % | 47 | 2.1 | 1054 | 682 | OpenMDW-1.1 | see its review |

- CER in %, ± the 95 % bootstrap half-width. Lower is better. Bold = best in the column.
- "Host cost × Ultra" is decode time relative to Parakeet Ultra on the same Japanese clips, interleaved. Phone costs
  scale differently by architecture, so treat these as rough; Parakeet-ja is Ultra's encoder with a CTC head, so its
  phone cost should be close to Ultra's.
- Host RSS is the decoding process's peak, a relative measure. The Pixel figures for today's engines are in
  `2026-10-pixel8a-mem.md` (turbo 2270 MB, Qwen3 0.6B 3130 MB, Ultra 1710 MB with ORT's arena).

## The homophone set

FLEURS and the Kokoro Speech Dataset score every character alike, so a wrong kanji is one error among dozens.
`tools/asr-bench/homophones-ja.tsv` has 76 short sentences built around words that share a reading with other words,
where only the context says which kanji is right: 橋/箸/端, 化学/科学, 機会/機械, 意外/以外, 公園/講演/公演,
暑い/熱い/厚い, 保証/保障/補償, 医師/意志 and so on (78 targets). Two Kokoro-82M voices (one female, one male) read
each sentence, so 156 targets in all. Each target is located in the transcript and scored:

- **right kanji**: the target as written.
- **wrong kanji**: a same-reading word with a different meaning (橋 for 箸).
- **kana**: the reading in hiragana or katakana (はし). The word was heard; no kanji was chosen.
- **other**: misheard or dropped.

| Engine | Right kanji (95 % interval) | Wrong kanji | Kana | Other | Set CER |
|---|---|---|---|---|---|
| qwen3-asr-1-7b | **85.9** (78.7–93.4) | 9.6 | 3.2 | 1.3 | 4.9 |
| parakeet-ja-ctc | 81.4 (72.6–88.8) | **5.1** | 10.3 | 3.2 | 6.5 |
| funasr-nano | 80.1 (71.9–88.2) | 13.5 | 0.0 | 6.4 | **3.7** |
| whisper-turbo | 78.2 (69.5–86.2) | 17.3 | 2.6 | 1.9 | 4.7 |
| kotoba-whisper-v2-0 (int8) | 78.2 (69.4–86.5) | 19.2 | 0.0 | 2.6 | 7.3 |
| qwen3-asr-0-6b | 73.7 (65.2–82.7) | 16.7 | 2.6 | 7.1 | 5.9 |
| reazonspeech-k2-v2 | 73.1 (62.5–82.5) | 6.4 | 11.5 | 9.0 | 12.9 |
| sense-voice-small | 70.5 (60.9–78.9) | 16.7 | 0.0 | 12.8 | 4.7 |
| nemotron-3-5-streaming-1120ms | 45.5 (34.9–55.9) | 42.9 | 0.0 | 11.5 | 10.0 |

The interval is a bootstrap over sentences (both voices of a sentence together).

- **Most engines are close; Nemotron is not.** It writes the wrong kanji for 43 % of targets: 公園 for 講演 and 公演,
  機会 for 機械, 地震 for 自信, 化学 for 科学. #37 already recommended moving Japanese off Nemotron.
- **Against turbo, target by target:** Qwen3 1.7B gets 22 targets turbo misses and misses 10 turbo gets. Parakeet-ja
  18 and 13, Fun-ASR-Nano 17 and 14, Kotoba 16 and 16. SenseVoice (6 and 18) and Qwen3 0.6B (15 and 22) are behind.
  Only Qwen3 1.7B's lead is near the edge of chance.
- **Parakeet-ja and ReazonSpeech k2 hedge in kana.** When unsure they write はし, くも, かみ or なおった. That is the
  safer failure for a reader: the word is right, just not in kanji. Turbo and Fun-ASR-Nano always commit to a kanji,
  and are wrong more often (17 % and 13 %). The kana outputs cost CER, which is why Parakeet-ja's set CER (6.5) is
  higher than turbo's (4.7) while it has a third of turbo's wrong kanji.
- **Two targets beat every engine in at least one voice:** 箸 in 「橋の端で箸を落とした」 (a deliberate
  tongue-twister) and 飴 in 「子供に飴をあげたら、とても喜んだ」 (every engine wrote 雨 for the female voice; turbo and
  Fun-ASR-Nano got the male voice's). Several others are hard even for people without the context a
  dictation gives: 化学 vs 科学 in 「大学では化学を専攻して」 (only Qwen3 1.7B got it, for both voices), and 速い vs 早い, which many
  writers mix up.
- **Caveats.** The audio is synthetic: two Kokoro voices with clean, even prosody. Real speech carries pitch accent
  that sometimes separates homophones (はし for bridge and chopsticks differ in accent), and real dictation is less
  clean. The set is small (78 targets); differences under about ten points are within noise. The TTS's own reading
  of every target was checked against the intended one before synthesis (`homophones/synth.py`).

## The Kokoro Speech Dataset

The second real-speech set: 200 clips from 14 Meiji- to Shōwa-era novels (Sōseki, Ōgai, Katai and others), read by one
LibriVox volunteer, with the Aozora Bunko text as the reference (Kokoro Speech Dataset v1.3, public domain, aligned
automatically). Every engine scores 18–26 here. Most of that is old spelling that a modern engine writes differently:
云えぬ / 言えぬ, うれしかった / 嬉しかった, われ / 我, 後影 / 後ろ影. The order matches FLEURS: Parakeet-ja 18.1,
turbo 19.0, Fun-ASR-Nano 19.2, SenseVoice 20.4. The spread is too small to separate the top engines.

The ReazonSpeech test split, the usual second Japanese set, was not used. Its corpus is gated behind a click-through,
and an ungated mirror would skip the licensor's acceptance step (head's ruling on #52). JSUT and JVS are
non-commercial, and Common Voice needs an account. A third-party benchmark (efwkjn/whisper-ja-760M `BENCH.md`, no
licence, UNVERIFIED) has the same order on Common Voice 8 and JSUT: Parakeet-ja 7.5 / 6.6, turbo 9.5 / 7.3,
Kotoba-Whisper 9.3 / 8.4.

## Speed and memory

Decode time on the same 20 FLEURS-ja clips (248 s of audio), the engines taking turns clip by clip
(`granite/rtf_ab.py --lang`, 2 threads, no VAD), so the machine's other load fell on all of them alike:

| Engine | Host RTF | × Ultra (host) | Phone cost × Ultra (`SpeedBudget.REFERENCE`, #47) | Host peak RSS MB | Pixel MB with ORT's arena (#47) |
|---|---|---|---|---|---|
| parakeet-ultra (reference) | 0.043 | 1.00 | 1.00 | – | 1710 |
| reazonspeech-k2-v2 | 0.014 | 0.32 | – | 482 | – |
| sense-voice-small | 0.020 | 0.47 | – | 466 | – |
| **parakeet-ja-ctc** | 0.039 | **0.92** | ≈1 (same encoder as Ultra; estimate) | 1108 | ≈1700 (estimate) |
| nemotron-3-5-streaming-1120ms | 0.088 | 2.06 | 2.86 | 1054 | 960 |
| funasr-nano | 0.144 | 3.37 | ≈5 (estimate) | 1914 | ≈3000 (estimate) |
| qwen3-asr-0-6b | 0.169 | 3.96 | 5.96 | 2026 | 3130 |
| kotoba-whisper-v2-0 | 0.271 | 6.36 | – | 1892 | – |
| whisper-turbo | 0.311 | 7.31 | 9.79 | 1943 | 2270 |
| qwen3-asr-1-7b | 0.338 | 7.93 | – | 4319 | – |

- **Parakeet-ja costs what Ultra costs.** It is Ultra's FastConformer encoder with a CTC head instead of a TDT decoder,
  so on a phone it should land near Ultra (cost 1.0, about 1.7 GB with ORT's arena, about 1.2 GB lean). That is
  roughly a tenth of turbo's cost and three-quarters of its memory. Phone numbers still need a run.
- **Fun-ASR-Nano** is an encoder feeding a Qwen3-0.6B LLM, like Qwen3-ASR 0.6B, and costs a little less than it on
  the host. Today's phone/host ratios (1.3–1.5 for the LLM and Whisper engines) put it near 5× Ultra on a phone,
  over a Pixel 8a's limit (4.46), and near Qwen3 0.6B's 3 GB.
- **Qwen3-ASR 1.7B** is as slow as turbo on the host and needs 4.3 GB there. It doesn't fit a phone.
- Host RSS is each run's peak (FLEURS, Kokoro Speech and the homophone set), a relative measure only.

## Kotoba-Whisper v2.0: our conversion, and why int8 breaks it

Kotoba-Whisper v2.0 is Whisper large-v3 distilled on ReazonSpeech: turbo's encoder size with 2 decoder layers instead
of 4. Nobody publishes a sherpa-onnx export, so `tools/asr-bench/kotoba/convert.py` makes one. It renames the
transformers checkpoint's tensors to openai-whisper's and runs k2-fsa's own Whisper exporter on it, unchanged. The
renaming is exact: transformers and openai-whisper on the converted checkpoint give identical text on 5 of 5 FLEURS
clips. Nothing was published.

k2-fsa's recipe quantizes every MatMul to int8, as for turbo. For Kotoba that breaks the decoder:

| Kotoba variant | FLEURS-ja CER | Homophone set CER | Right / wrong kanji | Host RTF | Host RSS MB | Files MB |
|---|---|---|---|---|---|---|
| int8 encoder + int8 decoder (the k2-fsa recipe) | 20.4 ±13.9 | 7.3 | 78 / 19 % | 0.31 | 1892 | 983 |
| int8 encoder + fp32 decoder | 8.0 ±1.1 | 7.0 | 79 / 20 % | 0.30 | 1988 | 1120 |
| fp32 + fp32 | 7.7 ±1.1 | 6.1 | 77 / 22 % | 0.48 | 3993 | 3027 |

- **The int8 decoder loops and stops early** on some clips: 13 of 200 FLEURS clips are above 50 % CER, one ran to
  about 2,000 characters of "KingKing…". The median clip is fine (6.5 % vs 5.8 % fp32).
- **With an fp32 decoder it works, but it doesn't beat turbo**: 8.0 against turbo's 7.2, at turbo's cost (6.4× Ultra on
  the host), with no better kanji choice. 18 homophone targets flip between int8 and fp32, in both directions.
- The variants other than full int8 come from a scratch run on the same clips through the same session code
  (`$WORK/results-52k`).

## Text problems to fix before any adoption

- **Parakeet-ja** punctuates little: F1 26 on FLEURS, 1.1 marks per 100 characters against 5.6 in the references. It
  ends 53 of 200 FLEURS sentences with a mark. It emits `<unk>` in 5 of 552 clips and Latin fragments in a few
  ("サファari", "prideは"). It drops the first character more often than the others (11 of 200 FLEURS clips).
- **Fun-ASR-Nano** prefixes a literal "/sil" to 18 of 200 FLEURS clips and writes numbers
  as kanji (二週間, sometimes 一九百二十年代). CER counts the numbers against it: 9.2 overall, 6.1 on the clips without
  digits.
- **Whisper turbo** ends only 100 of 200 FLEURS sentences with a mark (F1 62).
- **ReazonSpeech k2-v2** has no punctuation in its vocabulary at all.

## Licences and ethics

Full reviews: `docs/model-reviews/reazonspeech-japanese.md` (Parakeet-ja, ReazonSpeech k2-v2, Kotoba-Whisper and the
corpus), `fun-asr-nano.md`, `qwen3-asr-1.7b.md`. Whisper turbo, Qwen3 0.6B and SenseVoice keep their earlier reviews.

**The ReazonSpeech decision is Max's.** Three of the five candidates, including the best one, were trained only on
it. The facts:

- **What it is:** about 35,000 hours of Japanese **free-to-air TV**, recorded off the air (one-seg) by Reazon
  Holdings from 2021, cut into utterances and paired with the broadcasters' own captions.
- **Not pirated or paywalled.** One-seg is free, unscrambled broadcast. No pay-TV or pirate source is indicated.
- **But nobody asked.** Reazon says the rights stay with the broadcasters and relies on **Article 30-4** of Japan's
  Copyright Act, which permits "information analysis" such as machine learning without permission, unless it
  "unreasonably prejudices" the rights holder. No broadcaster, announcer or member of the public consented. Reazon
  shuffled the data so programmes can't be rebuilt, promised deletion on request, and took legal advice.
- **No objection to ReazonSpeech was found.** Japan's commercial broadcasters' association demanded permission for
  *generative* AI training in November 2025; that statement doesn't mention speech recognition.
- **The weights are not bound by the corpus's 30-4 condition.** The corpus licence (CDLA-Sharing-1.0) says trained
  models ("Results") carry no obligations, and Reazon itself ships Apache-2.0 models marked "commercial use OK".
- **Max's options:** (A) treat free-to-air broadcast used under 30-4 as public data, which passes the letter of the bar
  for recognition models; (B) accept it case by case (for example Parakeet-ja and k2-v2, not Kotoba); (C) decline, and
  stay with turbo (or Fun-ASR-Nano).

| Model | Licence | Ethics vs Max's bar |
|---|---|---|
| Parakeet TDT_CTC 0.6B ja | CC-BY-4.0, pass (attribution) | turns on the ReazonSpeech decision; otherwise NVIDIA's usual grade |
| ReazonSpeech k2-v2 | Apache-2.0, pass | turns on the ReazonSpeech decision; cleanest of the three |
| Kotoba-Whisper v2.0 | Apache-2.0 + MIT, pass | ReazonSpeech decision, then grey (Whisper's opaque data; the org's voice-cloning TTS) |
| Fun-ASR-Nano | Apache-2.0 by card tag only; pin revision `272c57b8` | pass (grey): Japanese training data undisclosed |
| Qwen3-ASR 1.7B | Apache-2.0, pass | pass (grey), as 0.6B; the only sherpa export is a community upload |

## What adoption would take

Done in #60 for Parakeet-ja: see "Adoption (#60)" below.

Not done here; the sibling child on `child/37-cjk` owns the catalog and recommender.

- **Parakeet-ja:** a NeMo CTC engine family in `SherpaBackends.kt` (sherpa-onnx supports it; the harness already
  runs it), a catalog entry pinned to k2-fsa's export (`csukuangfj/sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8`
  @ `bef18eb0`), its NOTICE and in-app licence entries (draft in the review), a phone measurement of cost and memory,
  and `:textproc` rules for `<unk>`. Its punctuation needs a fix: the Granite formatter (#33) is English, so either a
  Japanese punctuation model or accepting turbo-level punctuation as a known gap.
- **Fun-ASR-Nano** instead: a FunASR-Nano family (Qwen3 tokenizer files, Chinese language names in the prompt), a
  "/sil" filter, and a phone measurement; it is an LLM decoder, so check runaway loops.

## Adoption (#60)

Max accepted ReazonSpeech-trained recognition models on 2026-10-03, so Parakeet TDT_CTC 0.6B ja became the
Japanese engine (`parakeet-ja` in the catalog, family `NEMO_CTC`). What changed, and what the user now gets:

**Punctuation: a sentence-end rule, no commas.** Each Final is one VAD segment, ended by a pause (1.2 s by
default), so it is mostly a sentence. `:textproc` now ends a Japanese Final that ends in a letter or digit with
「。」 (`TextPipeline.endSentence`). Punctuation F1 against the references (comma F1 / sentence-end F1), through the
app's session (`tools/asr-bench/asrbench/ja_punct.py`, results `$WORK/results-60`, `2026-10-japanese-punct.json`):

| | FLEURS-ja | Kokoro Speech | Homophone set |
|---|---|---|---|
| Parakeet-ja, raw (#52) | 26 (18 / 35) | 15 (0 / 27) | 21 (0 / 29) |
| **Parakeet-ja + 「。」 rule (shipped)** | **56** (18 / 86) | **39** (0 / 55) | **81** (0 / 100) |
| Parakeet-ja + rule + 「、」 at pauses ≥ 0.48 s | 55 (29 / 86) | 35 (6 / 55) | 75 (7 / 100) |
| Whisper turbo, raw (what Japanese users had) | 62 (56 / 69) | 18 (15 / 21) | 44 (21 / 52) |
| Whisper turbo + 「。」 rule (shipped too) | 74 (56 / 90) | 43 (15 / 57) | 84 (21 / 100) |

- **The rule closes most of the gap.** Parakeet-ja ends every sentence now. Against what Japanese users had
  (turbo, 62 / 18 / 44) it is behind on FLEURS and ahead on the other two sets. The rule runs for every
  Japanese engine, so turbo gains too.
- **Commas stay Parakeet-ja's weak spot** (comma F1 18 against turbo's 56): long sentences come out without 「、」.
  A comma at each pause didn't help. The CTC token start times come in 80 ms steps and a read comma pause is
  0.2–0.3 s, so no threshold separates them: F1 fell at every one tried (0.24–0.64 s; 0.48 s is the best,
  shown), also with the gap corrected for token length.
- **No question mark.** The vocabulary has no 「？」, and a 「？」-after-か rule changed nothing on these sets (it cost
  Kokoro Speech a point). 「明日来ますか。」 is standard formal Japanese.
- **No punctuation model.** A small one with a clean licence that knows Japanese wasn't found; the multilingual
  punctuation restorers are XLM-RoBERTa-sized. A comma model of our own (like the Granite formatter, #33) is the
  way if commas matter.
- CER doesn't change: it ignores punctuation.

**Other text fixes in `:textproc`** (`textproc/README.md`):

- **「うん」 from noise.** Parakeet-ja wrote a lone 「うん」 ("yeah") as an extra Final for noise before speech on
  28 of 200 FLEURS-ja clips, from audio loud enough to pass the near-silence check (segments 0.46–0.78 s at
  −29.5 to −35.6 dBFS after AGC, like real short speech). Every one was the clip's first Final, and real speech
  followed. So the app (`FinishedUtterances`, not `:textproc`) holds a session's first Final when it is only
  「うん」, drops it when more speech follows, and sends it when the session ends without more: a 「うん」 reply
  survives. Turbo never did this.
- **`<unk>`** (5 of 552 clips): removed. The character it stood for is lost (「骨<unk>う品」 → 「骨う品」).
- **Latin in capitals.** The vocabulary has only lower-case Latin letters, so acronyms came out as "fbi", "usoc",
  "f1". Runs of up to 4 letters become capitals; longer ones ("safari") stay. Fragments inside katakana words
  ("サファari") are recognition errors and stay as they are. Parakeet-ja only: Whisper and Qwen3 write case.
- **Spacing.** No joining space between Japanese (or Chinese) utterances any more (「です。 明日」 was what every
  engine gave), no space inside a Final between two Japanese characters, and no sentence-start capital on a
  Latin word in Japanese or Chinese. A switch to Latin keeps its space (「東京 and」).
- **Only on Japanese text.** The Japanese rules run on Finals with a kana or kanji in them, so English heard by
  Ultra or turbo on Auto on a Japanese phone stays plain English.

**Routing.** `EngineRecommender.preference` puts Parakeet-ja after Parakeet 110M (cost order). Japanese scores
come from this doc now (`EngineQuality`: Parakeet-ja 5.4, turbo 7.2, both FLEURS-ja). Turbo is within the
accuracy margin but costs about ten times more, so Parakeet-ja wins on every phone it fits; turbo is the pick
only where Parakeet-ja fell behind real time and turbo fits the speed budget. `RULES_VERSION` went to 2, so a
Japanese phone recommended turbo gets Parakeet-ja at its next start, and keeps running an installed turbo
until the user switches.

**Speed and memory on a Pixel 8a** (2026-10-03 16:54–17:04, `docs/benchmarks/2026-10-pixel8a-ja/`). The debug
`SpeedBenchmarkService` over wireless adb on the LAN, as #47's first-decode runs: 4 threads, sessions off,
`--ez shuffleClips true --ei repeats 1`, seeds 1 and 2, each engine with ORT's arena on and off. Clips: LibriSpeech
`ls05/10/20/28-1,2` and two FLEURS-ja clips (12.7 and 15.6 s). Off the charger (61→59 %), screen off, thermal
status 0 throughout, battery 34.6–36.1 °C.

| Engine | Cost × Ultra (arena, first decodes) | Own MB, arena on | Own MB, arena off | Arena-off cost factor |
|---|---|---|---|---|
| parakeet-ultra | 1.00 | 1239 / 1274 | 849 / 944 | 1.32 |
| parakeet-ja | 0.98 | 882 / 826 | 771 / 798 | 1.14 |

- **Parakeet-ja costs what Ultra costs, and needs less memory**: no TDT decoder and joiner, and a third of the
  vocabulary. On the two Japanese clips alone it was 0.8× Ultra's decode time.
- **It works on the phone.** Its raw text for both FLEURS-ja clips was right (「香港島はその名のとおり香港の領土であり
  観光の目玉として多くの観光客が訪れる場所です。」); Ultra wrote romaji for one and nothing for the other.
- **`SpeedBudget.REFERENCE`:** cost 0.98, lean factor 1.14, memory 1240 MB with the arena and 1030 without. The
  memory is Parakeet-ja's own peak scaled by Ultra's REFERENCE over Ultra's own peak in the same runs (these shuffled
  orders grew the arena less than #47's ascending one), plus the 180 MB baseline. So a 4 GB phone runs Parakeet-ja
  with the arena and a 3 GB phone without it.
- The host estimate before the run (benchmark host, peaks at 28.6 s: Parakeet-ja 1201 / 1115 MB against Ultra's 1771 /
  1304, decode time 0.89×) was 0.92, 1220 and 1040 MB: close.
- `python3 tools/speed-bench/summarize.py docs/benchmarks/2026-10-pixel8a-ja/first60-*.json --kotlin` prints the
  raw rows (0.98, 1070, lean 980 at 1.14; Ultra 1.00, 1460, lean 1130 at 1.32).

## Method

- **Host:** the benchmark host (Ryzen AI Max+ 395), CPU only, `nice 19`, 2 threads per engine, 2–3 jobs at a time, shared with
  other work (and at times a game). Per-run RTFs are noisy; the cost column comes from the interleaved run.
- **Runtime:** sherpa-onnx v1.13.8 from source with the repo's patches (`third_party/patches/sherpa-onnx`), as the app.
  Pipeline as in `2026-10-nemotron-decision.md` (VAD minimum silence 1.2 s since #43), language hint `ja`.
- **Engines** (`tools/asr-bench/asrbench/engines.py`, all sha256-pinned): the app catalog's int8 files for turbo,
  Nemotron 3.5 1120 ms and Qwen3 0.6B; SenseVoice-Small as in #29; the candidates in `engines.JAPANESE`:
  - `parakeet-ja-ctc`: k2-fsa's int8 export of the CTC head (the TDT head isn't exported).
  - `reazonspeech-k2-v2`: Reazon's own sherpa-layout int8 files.
  - `funasr-nano`: k2-fsa's int8 export, prompted "语音转写成日文" (transcribe into Japanese), ITN on.
  - `qwen3-asr-1-7b`: a community int8 export (`thieunv-asilla`), the only one in sherpa's layout.
  - `kotoba-whisper-v2-0`: our own conversion (`tools/asr-bench/kotoba/convert.py`), not published.
- **Sets:** FLEURS ja_jp test, 200 clips (as #29). Kokoro Speech Dataset v1.3, a seeded 200 of 44,788 clips, cut from
  LibriVox's MP3s. The homophone set: 76 sentences × 2 Kokoro-82M voices, synthesised in a GPL-free venv (Kokoro's
  English path, which needs espeak-ng, is never imported).
- **Scoring:** CER as in #29 (`cer.py`: NFKC, punctuation and spaces removed, numbers as written); homophone targets
  by `asrbench/homophones.py`.
- **Reproduce:** `tools/asr-bench/README.md`, section "Japanese engines and the homophone set (#52)", and `run_52.sh`.
  Results on the benchmark host: `$WORK/results-52`.
- **Not done:** no phone runs; no real-voice homophone recordings (Max could record the 76 sentences; the scorer takes
  any audio); no Common Voice or ReazonSpeech test numbers of our own.
