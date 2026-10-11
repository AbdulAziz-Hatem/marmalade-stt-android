# Model Ethics Review: ReazonSpeech-trained Japanese ASR (Parakeet TDT_CTC 0.6B ja, ReazonSpeech-k2-v2, Kotoba-Whisper v2.0)

Reviewed 2026-10-02 by claude-code (Opus 5.5), per the `model-ethics-review` skill, for #52.
Scope: three Japanese engines that were all trained **only** on the ReazonSpeech corpus (Kotoba-Whisper also inherits
Whisper large-v3). The corpus is reviewed once, as the shared layer, then each model. They would transcribe only the
user's own dictation, on-device. Not legal advice. Nothing here was run on a phone.

## Decision (Max, 2026-10-03)

**Accepted.** Max: "Yes use it and reason being is I don't think you can reconstruct the tv footage at all from the model (so it is maximally transformative, and is legal under their copyright)." Models trained on ReazonSpeech may be used for **recognition**: the weights cannot reproduce the broadcasts, and the collection is lawful under Japan's Art. 30-4. Parakeet TDT_CTC 0.6B ja becomes the Japanese engine candidate (app work tracked separately).

## Verdict first

**Licences: all three pass.** CC-BY-4.0 (Parakeet ja) and Apache-2.0 (k2-v2, Kotoba-Whisper, plus MIT for the Whisper
part). None is NC. There are no riders. The corpus's "Article 30-4 only" condition does not, on the documents, reach
trained weights (see "Does the corpus restriction reach the weights?").

**Ethics: one open question decides all three, and it is Max's call.** All three were trained on ~35,000 h of Japanese
**free-to-air TV**, recorded off the air by a company without the broadcasters' permission. The company relies on a
Japanese statutory exception (Copyright Act Art. 30-4) instead. This is not piracy in the usual sense: nothing was
paywalled, encrypted or taken from a pirate site. But it is professional, copyrighted broadcast content used without a
licence or consent. Max's bar has not yet been applied to that category. The facts are below. The options are in
"The decision for Max".

| Model | Licence | Provenance | Extra demerits | Verdict vs Max's bar |
|---|---|---|---|---|
| Parakeet TDT_CTC 0.6B ja (k2-fsa int8, CTC only) | **CC-BY-4.0, pass** | ReazonSpeech only, plus the model's own re-labels | NVIDIA org demerit (see existing reviews) | **Turns on the corpus decision.** Otherwise clean |
| ReazonSpeech-k2-v2 (int8) | **Apache-2.0, pass** | ReazonSpeech only, by the corpus's own maker | none | **Turns on the corpus decision.** Cleanest of the three |
| Kotoba-Whisper v2.0 (our int8 conversion) | **Apache-2.0 + MIT, pass** | ReazonSpeech + Whisper large-v3 (opaque web data) | ungated re-host of the corpus; org ships a one-shot voice-cloning TTS | **Turns on the corpus decision, then grey** |

## The ReazonSpeech corpus (shared layer)

### What it is and how it was made

- **Source: one-seg broadcast recordings.** "「ReazonSpeech」では、ワンセグ放送の録画データから音声コーパスを自動抽出しています" ("ReazonSpeech
  automatically extracts the speech corpus from recordings of one-seg broadcasts"), press release, 2023-01-18,
  https://research.reazon.jp/news/reazonspeech.html. One-seg is the mobile layer of Japan's free-to-air digital
  terrestrial TV. The dataset card says "collected from terrestrial television streams"
  (https://huggingface.co/datasets/reazon-research/reazonspeech, card readable without login).
- **Paper:** Yue Yin, Daijiro Mori (Reazon Holdings), Seiji Fujimoto (ClearCode), "ReazonSpeech: A Free and Massive
  Corpus for Japanese ASR", NLP2023 (言語処理学会第29回年次大会), https://research.reazon.jp/_static/reazonspeech_nlp2023.pdf
  (also https://www.anlp.jp/proceedings/annual_meeting/2023/pdf_dir/A5-3.pdf). No arXiv version was found. §5: "We used
  1seg TV recordings from 2021.05.27 to 2022.12.25 to construct the initial release … 10,390,151 utterances (15,735
  hours …)". The audio is cut to utterances and paired with the **broadcasters' own closed captions**, aligned by an ASR
  bootstrapped from Common Voice (§3.2). v2.0 (2024-02-14) grew it to 35,000 h by the same method
  (https://research.reazon.jp/blog/2024-02-14-ReazonSpeech.html). The recording dates for v2.0 are **UNVERIFIED**.
- **Which channels or broadcasters: UNVERIFIED.** The paper says "Details about the corpus and TV program genre can be found on
  our project page". The current page (https://research.reazon.jp/projects/ReazonSpeech/) has no channel or genre list.
  The press coverage checked doesn't name channels either (https://xtech.nikkei.com/atcl/nxt/column/18/00160/020800341/,
  https://ainow.ai/2023/01/31/272123/). The samples on the project page are news-style (a JMA weather warning, an H-IIA
  launch item) plus one children's-story line. The public toolkit ships a one-seg MPEG-TS caption reader
  (https://research.reazon.jp/projects/ReazonSpeech/api/reazonspeech.espnet.oneseg.html).
- **Mitigations they state** (paper §5): "To comply with the Copyright Act, we shuffled the data at the utterance level
  to prevent the reconstruction of the TV shows for purposes except for ASR researches. We will delete the relevant
  utterances upon request from the authors of the content." Legal advice: "We would like to express our gratitude to
  Taichi Kakinuma for providing legal guidance on the project". Kakinuma is an AI/IP lawyer at STORIA law office
  (https://storialaw.jp/blog/4936). That identification is from the name match and is **UNVERIFIED**.
- **Why they did it:** Director Daijiro Mori said that after the 2019 amendment "everyone cautiously watched each other.
  Since no one was utilizing such a beneficial law, we thought why not take the lead and utilize it ourselves"
  (Reazon interview, 2024-07-18, https://media.reazon.jp/en/articles/ReazonSpeech2_EN/).

### Was any of it paid or non-public?

- **Not paywalled.** One-seg is free-to-air and not scrambled. B-CAS conditional access is not used for the mobile
  service (B-CAS FAQ, https://www.b-cas.co.jp/support/faq/category06/faq036.html; background
  https://ja.wikipedia.org/wiki/限定受信システム). It does carry copy-control flags. Whether Reazon's recorders
  respected them is **UNVERIFIED**, and nothing suggests otherwise. There is no sign of pay-TV (BS/CS premium)
  or streaming sources.
- **One nuance:** NHK is free-to-air, but owning any receiver, including a one-seg phone, creates a legal duty to sign
  NHK's receiving-fee contract (Supreme Court, 2019-03-12, https://www.itmedia.co.jp/mobile/articles/1903/14/news101.html).
  Whether NHK programmes are in the corpus, and whether Reazon held such a contract, are both **UNVERIFIED**. This is a
  licence fee, not a paywall: reception is not gated.
- **Copyrighted, and used without permission.** Reazon says so itself: "放送録画データに含まれる音声及び字幕データの権利は、元のテレビ放送の
  著作権者に帰属しますが、このデータを機械学習モデル構築のために使用することは、商用・非商用の目的を問わず著作権法30条の4によって認められています" ("The rights in the
  audio and caption data in the broadcast recordings belong to the copyright holders of the original TV broadcasts, but
  using this data to build machine-learning models is permitted by Copyright Act Article 30-4, whether commercial or
  non-commercial") (https://research.reazon.jp/news/reazonspeech.html, "ライセンス"). No source checked claims any
  broadcaster licence or consent. The speakers did not consent either: announcers, actors and members of the public.

### Did anyone object?

- **No objection to ReazonSpeech specifically was found**: Japanese and English searches, press, the GitHub tracker
  (searched "copyright", "著作権", "30条", "broadcast", "テレビ", "商用": nothing relevant;
  https://github.com/reazon-research/ReazonSpeech/issues). The dataset's HF Community tab is **disabled** ("Discussions
  are disabled for this repo", https://huggingface.co/api/datasets/reazon-research/reazonspeech/discussions). So there
  is no public venue where a question could have been left unanswered.
- **Broadcasters' general stance is hostile to unlicensed *generative* training.** On 2025-11-26 the Japan Commercial Broadcasters
  Association (民放連) asked AI developers 「会員各社が権利を持つコンテンツを無許諾で学習対象にしない」 ("not to use content our members
  hold rights in as training data without permission"). It argued that training a public generative service has an
  "enjoyment" purpose and needs prior permission (https://www.itmedia.co.jp/aiplus/articles/2511/27/news080.html). The
  statement is about video and generative AI (Sora 2). It does not mention ASR. Whether its members would object to
  ASR training is **UNVERIFIED**.

### What Article 30-4 says, and its limits

- Text (official translation, quoted in the Agency for Cultural Affairs' overview, p. 7,
  https://www.bunka.go.jp/english/policy/copyright/pdf/94055801_01.pdf; Japanese original at
  https://laws.e-gov.go.jp/law/345AC0000000048): "It is permissible to exploit a work, in any way and to the extent
  considered necessary, … in any other case in which it is not a person's purpose to personally enjoy or cause another
  person to enjoy the thoughts or sentiments expressed in that work; **provided, however, that this does not apply if the
  action would unreasonably prejudice the interests of the copyright owner** in light of the nature or purpose of the
  work or the circumstances of its exploitation". Item ii is "data analysis". The provision came in with the 2018
  amendment (Act No. 30 of 2018, in force 2019-01-01): https://www.bunka.go.jp/seisaku/chosakuken/hokaisei/h30_hokaisei/.
- **Broadcasters' and performers' neighbouring rights are covered too.** Art. 102(1) applies Arts. 30-2 to 32 (so 30-4)
  to performances, broadcasts and cablecasts (https://laws.e-gov.go.jp/law/345AC0000000048, Art. 102).
- **Sharing the dataset is within 30-4**, per the Agency's General Understanding (2024-03-15, footnote 40): 「法第 30 条の４に
  おいては、非享受目的の利用であること等の同条の要件を満たす限り、譲渡や公衆送信を含め、いかなる方法でも著作物を利用できる」 ("under Art. 30-4, as long as
  its requirements such as non-enjoyment purpose are met, a work may be exploited in any way, including transfer and
  public transmission") (https://www.bunka.go.jp/seisaku/bunkashingikai/chosakuken/pdf/94037901_01.pdf).
- **The proviso, as the Agency reads it** (overview pp. 6, 10): the test is "whether it will compete in the market with
  the copyrighted work" and "whether it will impede the potential sales channels of the copyrighted work in the future".
  Its examples are copying a database "available on the internet for a fee", or evading "technical measures" such as
  robots.txt or login walls. Nothing like that is documented for ReazonSpeech. A dictation model does not compete with TV
  programmes. **No court has ruled on 30-4 and AI training**, and the guidance "is not legally binding" (overview p. 2).
- **Territoriality:** whether 30-4 covers NVIDIA's training outside Japan is **UNVERIFIED**. We use only the weights.

### Licence on the corpus

- HF dataset `reazon-research/reazonspeech` (head `0df78f99`, gated "auto", metadata `license: other`). Card: "License
  CDLA-Sharing-1.0. TO USE THIS DATASET, YOU MUST AGREE THAT YOU WILL USE THE DATASET SOLELY FOR THE PURPOSE OF JAPANESE
  COPYRIGHT ACT ARTICLE 30-4." (https://huggingface.co/datasets/reazon-research/reazonspeech;
  https://huggingface.co/api/datasets/reazon-research/reazonspeech). The project page agrees: "CDLA-Sharing-1.0
  （ただし利用目的は著作権法３０条の４に定める情報解析に限る）" ("but the purpose of use is limited to information analysis under Art. 30-4").
  The how-to says the same (https://research.reazon.jp/projects/ReazonSpeech/howto.html).
- Oddity: CDLA-Sharing-1.0 §3.3 forbids a *re-publisher* from "limiting permitted Use of such Data to any particular
  platform, technology or field of endeavor". Reazon's purpose rider sits awkwardly with the licence it chose. That is
  Reazon's problem, not ours.

### Does the corpus restriction reach the weights?

On the documents, no. CDLA-Sharing-1.0 (text: https://cdla.dev/sharing-1-0/; SPDX copy
https://github.com/spdx/license-list-data/blob/main/text/CDLA-Sharing-1.0.txt):

> 1.11 "Results" means the outcomes or outputs that You obtain from Your Computational Use of Data. Results shall not
> include more than a de minimis portion of the Data on which the Computational Use is based.
>
> 3.5 This Agreement imposes no obligations or restrictions on Your Use or Publication of Results.

§1.2 defines Computational Use as analysis "to generate information about Data such as patterns, trends, correlations,
inferences, insights and attributes". Trained ASR weights fit that definition, and they hold no audio. The "solely for 30-4"
rider is a click-through promise by whoever downloaded the corpus (NVIDIA, Reazon, the Kotoba authors). It limits their
use of the *dataset*, and training is exactly the analysis it allows. We never receive the dataset. The strongest
evidence: **the licensor itself** ships models trained on the corpus as Apache-2.0, "商用利用可" ("commercial use OK"), and
says model-building is allowed "商用・非商用の目的を問わず" ("whether commercial or non-commercial")
(https://research.reazon.jp/news/reazonspeech.html; https://research.reazon.jp/projects/ReazonSpeech/). The counter-argument
would be that weights trained on broadcasts "unreasonably prejudice" the broadcasters under the 30-4 proviso. No
authority or broadcaster has said that about ASR. **Encumbrance risk: low. Not zero, and no court has ruled.**

**Corpus provenance grade: DISCLOSED-SCRAPED.** The source type, period, method, legal basis and deletion policy are all
public. The channel list is not. It is copyrighted broadcast content, used under a statutory exception, without the
rights-holders' or speakers' consent. Not piracy, not paywalled, not consented.

## The decision for Max

Max's bar #1 rules out "scraped *paid, non-public* services". It accepts "genuinely public data". Bar #3 says that
recognition models are judged on acquisition ethics, not copyright technicalities. ReazonSpeech is a case the bar
does not name:

- **Read literally, it passes.** Free-to-air TV is public and unpaid. Nothing was circumvented. The data was taken openly
  under a law written for exactly this, with a takedown promise. It is arguably *cleaner* than YouTube-scraped corpora
  (Whisper), because the method and legal basis are disclosed.
- **Read by its spirit, it is grey.** This is professional, copyrighted work: news, drama, variety shows. It was taken
  in bulk from identifiable rights-holders who were not asked. Their trade body now says generative training needs
  permission. It is not "Creative Commons or public-domain" data like Granary or Common Voice.

Options:

- **A. Accept ReazonSpeech as public data for recognition models.** All three engines are then eligible. Pick on
  accuracy and size. Record the precedent in the wiki (`tech/ai/training-ethics.md` §1a): "free-to-air broadcast under
  a disclosed statutory exception = acceptable for ASR".
- **B. Accept with conditions.** For example, k2-v2 and Parakeet ja only, with an engine-card line ("trained on Japanese TV
  broadcasts under Copyright Act Art. 30-4"). Kotoba is left out for its extra demerits (below). Or developer-engine only
  until Max decides.
- **C. Decline.** Japanese then relies on the engines already reviewed: Whisper large-v3-turbo (opaque web data) and Nemotron
  3.5 (PARTIAL). Neither is cleaner on provenance than ReazonSpeech.

## Parakeet TDT_CTC 0.6B ja (k2-fsa int8 export)

- **Artifact.** Upstream https://huggingface.co/nvidia/parakeet-tdt_ctc-0.6b-ja @ `44edb27eea9317daf89333e75eb830db4b1cc298`
  (2025-02-18, head, ungated; https://huggingface.co/api/models/nvidia/parakeet-tdt_ctc-0.6b-ja). Export
  https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8 @
  `bef18eb066808c90bd0f5df5be685767b0732de8` (2025-07-09, by `csukuangfj`): `model.int8.onnx` 655,542,604 B, sha256
  `3addd00ef5bd1742078389e540b77394e4a508bdf2f4c9ad1b4a76d93e76598e`; `tokens.txt` 28,557 B,
  `732f64c53909f2620c713f4106b487d92e6f54a6915b3cd3d1dbd32f9f4f392a` (downloaded and hashed). The export is **CTC
  branch only**: sherpa-onnx `scripts/nemo/parakeet-tdt_ctc-0.6b-ja/export-onnx-ctc.py` (header "Copyright 2025 Xiaomi
  Corp. (authors: Fangjun Kuang)"; metadata `"comment": "Only the CTC branch is exported"`), then `quantize_dynamic`
  QUInt8 (https://github.com/k2-fsa/sherpa-onnx/tree/master/scripts/nemo/parakeet-tdt_ctc-0.6b-ja). The repo has no README,
  licence file or tag.
- **Licence: CC-BY-4.0.** The tag and the prose ("License to use this model is covered by the CC-BY-4.0") agree in every
  revision since 2024-05 (checked `3a2788c0`, `403f3a10` and head). **There are no NVIDIA governing terms, no NVIDIA Open
  Model License and no rider.** The card never mentions Article 30-4 or the corpus's purpose restriction. It cites
  the dataset only as "[5] ReazonSpeech v2.0". The k2-fsa export adds no terms.
- **Provenance: DISCLOSED-SCRAPED (corpus as above), plus self-labelling.** Card: "trained on ReazonSpeech v2.0 … more
  than 35k hours", then "finetuned for 100k additional steps on the modified training data (predicted texts for training
  samples with CER>10%)". So some targets are the model's own transcripts, not the captions. That is not a provenance problem.
  The tokenizer was built from the train transcripts. No other data is named.
- **Posture: N/A for impersonation** (text output only). The card has no surveillance caution.
- **Org track record:** NVIDIA is grey. *Nazemian v. NVIDIA* (Books3 / Anna's Archive, LLMs) and the Cosmos
  YouTube-scraping reports are unrelated to this model; details and sources are in
  [`nemotron-3.5-asr-streaming-0.6b.md`](nemotron-3.5-asr-streaming-0.6b.md). The community tab has 4 threads, none on
  data (https://huggingface.co/nvidia/parakeet-tdt_ctc-0.6b-ja/discussions). k2-fsa: neutral, the same maintainer as
  our other exports.
- **Feasibility (secondary):** 655 MB single file. Card CER (TDT / CTC): JSUT basic5000 6.4 / 6.5, CV8 7.1 / 7.2,
  TEDxJP 9.0 / 9.1. We get the CTC numbers. Punctuated output. Offline, not streaming (NVIDIA, discussion #1).
- **Working demo:** no HF Space runs this model (Space search for "parakeet-tdt_ctc", 2026-10-02). k2-fsa's prebuilt
  Android demo APK uses this exact export: https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/vad-asr-simulated-streaming/1.13.8/sherpa-onnx-1.13.8-arm64-v8a-simulated_streaming_asr-ja-parakeet-tdt_ctc_0.6b_ja.apk
  (HTTP 200, 494,940,512 B, 2026-10-02; listed at https://k2-fsa.github.io/sherpa/onnx/android/apk-simulate-streaming-asr.html).
- **Verdict:** licence pass. Ethics: **depends on the corpus decision.** It adds nothing beyond the corpus except NVIDIA's
  unrelated org demerit.

## ReazonSpeech-k2-v2

- **Artifact.** https://huggingface.co/reazon-research/reazonspeech-k2-v2 @ `291488c8151be24d7da4bf7af26e533fad96e407`
  (2024-08-01, head, ungated, by `fujimotos`). int8 files: `encoder-epoch-99-avg-1.int8.onnx` 154,670,139 B,
  `2c7bd08a8a99f9ddd0d9e458456577b1f6279214e51426f114f9eced44c54e1d`; `decoder-epoch-99-avg-1.int8.onnx` 2,959,337 B,
  `8f0bff94d38797b03b762634ed03211a8e303d06cc4603cdd0cf4199d6eb1485`; `joiner-epoch-99-avg-1.int8.onnx` 2,696,970 B,
  `49cc7ea1d3d35a40a27442db5e89996da64bf0e683a903dce76e99e57a12e4de`; fp32 decoder 11,767,836 B,
  `58b18211ae06265466bfa17172dab574df94f76c8bcb61a3640c28ba860e4124`; `tokens.txt` 45,754 B,
  `2c3ac659818a48a0c04010e0593bbc4d7c8a24a054340b01131499c05fd52def` (downloaded and hashed). Tree:
  https://huggingface.co/api/models/reazon-research/reazonspeech-k2-v2/tree/291488c8151be24d7da4bf7af26e533fad96e407
- **Licence: Apache-2.0.** The tag is `apache-2.0` and the prose reads "Apaceh Licence 2.0" (sic, linking choosealicense). **The HF
  repo has no LICENSE or NOTICE file and no copyright line.** Reazon's GitHub repo is Apache-2.0 with "Copyright
  2022-2025 Reazon Holdings, inc." in its README (https://github.com/reazon-research/ReazonSpeech, README.rst "LICENSE").
  Its LICENSE file is the unfilled template ("Copyright [yyyy] [name of copyright owner]"). The launch post says
  "We published the ReazonSpeech-k2-v2 model under the Apache 2.0 license"
  (https://research.reazon.jp/blog/2024-08-01-ReazonSpeech.html). The project page lists the models as Apache-2.0, and the
  press release calls them "商用利用可" ("commercial use OK") (https://research.reazon.jp/news/reazonspeech.html).
  Apache-2.0 grants commercial use. The "OK" claim holds for the weights' licence, subject to the corpus question above.
- **Provenance: DISCLOSED-SCRAPED.** The card says "trained on ReazonSpeech v2.0 corpus". The recipe is public (icefall
  `egs/reazonspeech/ASR`, https://github.com/k2-fsa/icefall/tree/master/egs/reazonspeech/ASR). No other data is named. The
  corpus maker trained it, so the "solely 30-4" promise and the model come from the same party.
- **Posture:** N/A for impersonation. No surveillance caution on the card.
- **Org track record:** Reazon Holdings is a Tokyo holding company (founded 2019-02) in ad-tech, social games, media and
  food-tech (press release, "会社概要"). The Human Interaction Lab is its research arm, which also does ReazonSpeech
  Wav2Vec2/HuBERT and OpenArm robotics. No retractions, lawsuits or licence changes were found. Its engagement on
  GitHub is responsive (maintainer `fujimotos` answers issues). The open HF thread is a DOI request
  (https://huggingface.co/reazon-research/reazonspeech-k2-v2/discussions).
- **Feasibility (secondary):** about 160 MB int8. Reazon reports that an int8 decoder costs accuracy (JSUT 6.63 vs 6.45) and
  recommends the fp32 decoder (+9 MB) (blog Table 2). Japanese only, "Character-based", clips up to "~30 seconds"
  (card). Whether it punctuates is **UNVERIFIED**. Zipformer transducer through sherpa-onnx.
- **Working demo:** https://huggingface.co/spaces/k2-fsa/automatic-speech-recognition (RUNNING per
  https://huggingface.co/api/spaces/k2-fsa/automatic-speech-recognition, 2026-10-02). Its `model.py` loads
  `reazon-research/reazonspeech-k2-v2` with the int8 encoder and joiner and the fp32 decoder. Pick Japanese.
- **Verdict:** licence pass, with a naming nit (no copyright line ships with the weights). Ethics: **depends on the corpus
  decision.** It has no extra demerits, and its maker is the one most accountable for the data.

## Kotoba-Whisper v2.0 (our own int8 sherpa-onnx conversion)

- **Artifact.** https://huggingface.co/kotoba-tech/kotoba-whisper-v2.0 @ `7eb575277d18909a4af8a24e3ae8cce2e99794ae`
  (2024-10-23, head, ungated, all commits by `asahi417`). `model.safetensors` 1,512,875,008 B, sha256
  `0a96a84e307bbb90128a9fd5a45b4f803be212aadef633e24c088fa1ba30f727` (matches `tools/asr-bench/kotoba/convert.py`). Our
  conversion renames the tensors, exports with k2-fsa's Whisper exporter and quantizes to int8 (`convert.py` docstring).
  It would be hosted on `maxwhipw/marmalade-stt-engines`, so **we distribute it**.
- **Licence: Apache-2.0 (card tag) + MIT (Whisper).** **The repo has no LICENSE, NOTICE or copyright line**
  (https://huggingface.co/api/models/kotoba-tech/kotoba-whisper-v2.0). The tag is the only grant, and it is explicit. The
  training-code repo has **no licence at all** (https://github.com/kotoba-tech/kotoba-whisper; `license: null`). That
  doesn't affect the weights. The authors are "Asahi Ushio and Kotoba Technologies" (card). Lineage: the student "consists
  the full encoder of the teacher large-v3 model and the decoder with two layers initialized from" it. The teacher is
  `openai/whisper-large-v3` (card; `create_student_model.py`). OpenAI's GitHub releases Whisper's "code and model
  weights" under MIT, "Copyright (c) 2022 OpenAI". Its HF conversion is tagged `apache-2.0`
  (https://huggingface.co/api/models/openai/whisper-large-v3); see [`whisper-family.md`](whisper-family.md). The encoder is
  copied whole, so treat it as a substantial portion and carry the MIT notice.
- **What redistribution requires** (Apache-2.0 §4; MIT): (a) ship the Apache-2.0 text; (b) "cause any modified files to
  carry prominent notices stating that You changed the files"; (c) retain the source's attribution notices (there are none
  beyond the card's credit); (d) there is no upstream NOTICE file to pass on. Plus the MIT copyright and permission notice for
  OpenAI's part. Apache-2.0 doesn't require a NOTICE when upstream has none, but the release still needs a NOTICE/README
  with the credit and the modification statement (entry below).
- **Provenance: DISCLOSED-SCRAPED (ReazonSpeech "all", 7,203,957 clips after a WER ≤ 10 filter) on top of Whisper
  large-v3's OPAQUE web data.** The targets are Whisper large-v3 pseudo-labels (card, "WER Filter"). So two layers: the TV
  corpus, and Whisper's undisclosed training set (graded OPAQUE in [`whisper-family.md`](whisper-family.md)).
- **Extra demerit 1: the Kotoba side re-hosts the corpus ungated, without licence or the 30-4 condition.** The
  `japanese-asr` HF org (sole public member: Asahi Ushio, https://huggingface.co/api/organizations/japanese-asr/members)
  publishes `whisper_transcriptions.reazonspeech.all` (~2.7 TB of ReazonSpeech audio and captions plus Whisper labels) and 19
  sibling sets. All are `gated: False`, with no licence tag and an empty card body
  (https://huggingface.co/api/datasets/japanese-asr/whisper_transcriptions.reazonspeech.all; a shard returned HTTP 200 to
  an anonymous HEAD request on 2026-10-02). CDLA-Sharing §3.1(a), (c) and §3.3 require a re-publisher to use the same
  agreement and keep the attribution. This re-host does neither, and it removes Reazon's purpose gate. 30-4 permits public
  transmission only for non-enjoyment purposes, and an open re-host lets anyone listen. This is the clearest misstep found in this review.
  It is not in Kotoba-Whisper's weights, but it bears on the org.
- **Extra demerit 2: Kotoba ships one-shot voice cloning, likely trained on the same TV voices.**
  `kotoba-tech/kotoba-speech-v0.1` (Apache-2.0) offers "One-shot voice cloning through speech prompt"
  (https://huggingface.co/kotoba-tech/kotoba-speech-v0.1). Its training README uses ReazonSpeech as the worked example
  and says "our model is not necessary trained on Reazon Speech solely"
  (https://github.com/kotoba-tech/kotoba-speech-release). That implies but does not state that TV voices are in it:
  **UNVERIFIED**. No consent or misuse language was found in either README. The demo Space is gone (404). Under bar #2 this is an
  org-level demerit, not a fail, because no celebrity-clone showcase was found.
- **Org track record:** Kotoba Technologies, founded 2023 by Noriyuki Kojima and Jungo Kasai (SF/Tokyo). It is
  VC-funded: Kindred Ventures, Salesforce Ventures and Sony Innovation Fund; a USD 11.83 M seed-2 in 2025
  (https://www.kotoba.tech/en/news-all/we-have-raised-usd-1183-million-jpy-17-billion-in-a-seed-2-round-to-accelerate-commercialization;
  https://slator.com/ai-interpreting-startup-kotoba-second-seed-round/). Products are real-time interpreting and voice AI.
  No lawsuits or retractions were found. The HF community tab has one thread, not on data.
- **Feasibility (secondary):** 756 M parameters (card), 32 encoder and 2 decoder layers, 128 mel bins. Card CER: CV8 9.2,
  JSUT 8.4, ReazonSpeech test 11.6. The int8 size and phone speed are not measured here. It is the largest of the three.
- **Working demo:** no running v2.0 demo found. `kotoba-speech/kotoba-whisper-demo` (which loads v2.0) is
  RUNTIME_ERROR (https://huggingface.co/api/spaces/kotoba-speech/kotoba-whisper-demo, 2026-10-02). `yichen0403/Kotoba-whisper`
  is SLEEPING and loads **v2.2**, which is v2.0 plus a punctuation/diarization pipeline (https://huggingface.co/spaces/yichen0403/Kotoba-whisper). Our harness
  is the best demo.
- **Verdict:** licence pass, provided the release carries the notices below. Ethics: **depends on the corpus decision, then
  grey**: Whisper's opaque data, the ungated re-host, and the org's cloning TTS.

## NOTICE entries

These are drafts for `NOTICE.md` → "Downloadable speech models" and the in-app licenses screen, used only if Max adopts the
engine. The licence bodies are already bundled: `assets/licenses/CC-BY-4.0.txt`, `Apache-2.0.txt`, `whisper.txt`. Each entry
should end with the provenance line: "Trained on the ReazonSpeech corpus (Japanese TV broadcast audio, Reazon Holdings,
CDLA-Sharing-1.0, used under Japanese Copyright Act Art. 30-4)."

```
### NVIDIA Parakeet TDT_CTC 0.6B ja (int8, k2-fsa export)
Creator: NVIDIA (https://huggingface.co/nvidia/parakeet-tdt_ctc-0.6b-ja, commit 44edb27e). No copyright notice
is supplied with the model. License: Creative Commons Attribution 4.0 International (CC BY 4.0),
https://creativecommons.org/licenses/by/4.0/legalcode
Modified: CTC branch exported to ONNX and quantized to int8 (QUInt8) by the k2-fsa / sherpa-onnx project,
https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet-tdt_ctc-0.6b-ja-35000-int8 (commit bef18eb0),
without retraining. Provided as-is, without warranties; see Section 5 of the license.
```

```
### ReazonSpeech-k2-v2 (int8)
Creator: Reazon Human Interaction Lab, Reazon Holdings, Inc.
(https://huggingface.co/reazon-research/reazonspeech-k2-v2, commit 291488c8). No copyright notice is supplied
with the model. Licensed under the Apache License, Version 2.0. Used unmodified (the publisher's own int8 ONNX files).
```

```
### Kotoba-Whisper v2.0 (int8, our conversion)
Kotoba-Whisper v2.0 by Asahi Ushio and Kotoba Technologies
(https://huggingface.co/kotoba-tech/kotoba-whisper-v2.0, commit 7eb57527), licensed under the Apache License,
Version 2.0. No copyright notice is supplied with the model. It is distilled from OpenAI Whisper large-v3,
Copyright (c) 2022 OpenAI, MIT License (https://github.com/openai/whisper).
Modified for this app: tensors renamed to the openai-whisper layout, exported to ONNX with k2-fsa's
scripts/whisper/export-onnx.py and the weights quantized to int8, without retraining
(tools/asr-bench/kotoba/convert.py). Published as release assets of
https://github.com/maxwhipw/marmalade-stt-engines.
```

The engines repo release for Kotoba must itself carry `LICENSES/Apache-2.0.txt`, `LICENSES/whisper-MIT.txt` (verbatim
from openai/whisper `6e3be77e`), and a `NOTICE` with the third block. That satisfies Apache §4(a)–(b) and the MIT notice
condition. The Parakeet and k2-v2 files are fetched from their publishers' HF repos, so Apache/CC redistribution
duties do not formally fall on us. We credit them anyway, as for the other engines.

**Correction to prior art:** none needed. The maintainer's note `datasets.md` already records the corpus as
"Broadcast audio, not speaker-consented", with "Its *models* are Apache-2.0; the corpus isn't". That is accurate. The facts here
(one-seg, 2021-05 to 2022-12, no broadcaster consent, the Results clause) belong there once Max decides.
