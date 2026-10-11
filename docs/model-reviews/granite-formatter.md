# Model Ethics Review: the Granite formatter (our own, #33)

Reviewed 2026-09-27 by claude-code (Opus 5.5), per the `model-ethics-review` skill. Scope: the tiny formatter
trained in #33 (`tools/asr-bench/granite/format_model.py`), which restores case, punctuation and apostrophes in
Granite Speech's lowercase output. We trained it ourselves, from scratch, so this review covers the training
text. The measurements are in `docs/benchmarks/2026-09-granite-formatting.md`.

## Verdict first

**PASS.** Every training sentence comes from a source whose licence allows this use, and none is share-alike,
non-commercial or scraped without permission. No pretrained weights are involved.

- **Bar #1 (no piracy): passes.** Three sources, each published under an explicit open licence by the people
  who wrote it (table below).
- **Bar #2 (impersonation): N/A.** A word labeller: it outputs a case, a mark and a join flag for each word
  already in Granite's text. It generates no text and no audio.
- **Bar #3 (recognition-type models: judge acquisition): passes.** Every source was downloaded from its
  official export, under its own terms.
- **Licence of the weights: ours.** They would ship under the app's MIT licence, with the attribution below.
  Whether trained weights are an adaptation of CC BY text is legally unsettled; crediting the sources in
  NOTICE and the licenses screen is the cautious practice, and CC BY allows credit by URL.

## Training data

| Source | What | Licence | Evidence |
|---|---|---|---|
| Tatoeba English sentences | 2,037,522 short sentences, conversational (`eng_sentences.tsv` downloaded 2026-09-27, sha256 `029328ff36f4d2b4384da8bf40525775de708d268d6872479cdd581fc458033e`) | CC BY 2.0 FR (a 41.5k subset is also CC0) | https://tatoeba.org/en/downloads, https://tatoeba.org/en/terms_of_use ("It is forbidden to contribute copyrighted content") |
| Common Voice English, `sentence-collector.txt` | 61,513 community-submitted sentences (sha256 `31ac8e200449ebd7aee4a8c6bd16d4dafd076cf11f7c3efe1035f0283712d136`) | CC0 1.0 (the directory's `LICENSE`) | https://github.com/common-voice/common-voice/tree/main/server/data/en |
| Common Pile `news`, CC BY sources only | 900,000 passages of 1–3 sentences from 12 news sites that publish under CC BY 4.0 (dataset revision `13e76bdc8d49ed14d710fac7e3b61186cf74c8d3`) | CC BY 4.0, per document | https://huggingface.co/datasets/common-pile/news; each document's `license` field |

- **Excluded on purpose:**
  - Common Voice's `wiki.en*.txt`: Wikipedia extractions, CC0 as published, but from CC BY-SA text.
  - Common Pile news documents marked Share-Alike (five sources: educeleb, libertytvradio, oxpeckers,
    propastop, thepublicrecord).
  - 1,650 news documents whose URL is on another publisher's site (syndicated, e.g. milwaukeenns items on
    247wallst.com or jsonline.com), whose own licence the CC BY tag may not cover. Common Pile's paper names
    this licence-laundering risk: https://huggingface.co/papers/2506.05209
  - Anything Whisper transcribed (Common Pile `youtube`): training on it would copy Whisper's punctuation.
- **The news sources kept** (all CC BY 4.0, on their own domains): Global Voices, Factly, Milwaukee
  Neighborhood News Service, New Canadian Media, Alt News, 360info, Balkan Diskurs, Freedom of the Press
  Foundation, Mekong Eye, ZimFact, Minority Africa, Solutions Journalism.
- **Held out of training:** every reference sentence of the harness's dictation and FLEURS-en sets, so the
  scores in the benchmark doc aren't on memorised text.

## Posture

Text in, the same words out with case, marks and joined contractions. On-device, after the recogniser. It sees
only what the user dictated, and keeps nothing.

## Attribution it would need (NOTICE and the in-app licenses screen)

> The Granite formatter is Marmalade STT's own model, trained from scratch on:
> sentences from Tatoeba (https://tatoeba.org) by the Tatoeba contributors, licensed CC BY 2.0 FR
> (https://creativecommons.org/licenses/by/2.0/fr/); sentences from Mozilla Common Voice
> (https://github.com/common-voice/common-voice), CC0 1.0; and news text published under CC BY 4.0
> (https://creativecommons.org/licenses/by/4.0/) by Global Voices, Factly, Milwaukee Neighborhood News
> Service, New Canadian Media, Alt News, 360info, Balkan Diskurs, Freedom of the Press Foundation, Mekong Eye,
> ZimFact, Minority Africa and Solutions Journalism, as collected in the Common Pile `news` dataset
> (https://huggingface.co/datasets/common-pile/news). The model can't reproduce text from these sources: it
> only labels the case and punctuation of words it is given.
