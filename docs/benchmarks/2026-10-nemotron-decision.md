# Does Nemotron 3.5 stay? Whisper turbo vs Nemotron 3.5 vs Qwen3-ASR, October 2026 (#37)

Max keeps Nemotron 3.5 Streaming only if it beats the alternatives on languages Parakeet doesn't cover (his
hands-on verdict, 2026-10-01: worse than Whisper/Parakeet/Canary on English, unusual words and formatting, and
slower). Nemotron 3.5 covers eight such languages: Japanese, Chinese, Korean, Hindi, Arabic, Turkish, Vietnamese
and Norwegian. This run measures all eight on the host harness (`tools/asr-bench`, the app's pipeline and int8
files), against Whisper Large v3 Turbo with two sherpa-onnx fixes and against Qwen3-ASR 0.6B. Per-set statistics
are in `2026-10-nemotron-decision.json` next to this file. SenseVoice-Small's CJK numbers are in
`2026-09-cjk-sensevoice.md` (same harness, same sets); it isn't rerun here.

**Decided: A, 2026-10-02 (Max).** Nemotron 3.5 stays for Hindi, Arabic and Vietnamese only, as its 1120 ms
export; the 560 ms export left the app. Japanese, Chinese, Korean, Turkish, Norwegian and every other language it
used to take go to Whisper turbo (Chinese too; Qwen3-ASR stays a developer engine and is no longer recommended).

## Recommendation

**Keep Nemotron 3.5 for Hindi only; stop offering it for Japanese, Chinese, Korean, Turkish and Norwegian.**
Arabic and Vietnamese are a tie on accuracy, so there it's the cheap option for phones that can't run turbo.

| Language | Best | Nemotron 3.5 (1120 ms) | Whisper turbo (fixed) | Qwen3-ASR | Verdict |
|---|---|---|---|---|---|
| Japanese (CER) | turbo | 14.6 | **8.1** | 16.6 | drop Nemotron |
| Chinese, FLEURS / AISHELL-1 (CER) | Qwen3, turbo | 22.2 / 24.4 | 9.6 / 9.3 | **9.2 / 2.7** | drop Nemotron |
| Korean, FLEURS / Zeroth (CER) | turbo | 8.9 / 5.1, no word spaces | **4.4 / 4.9** | 6.5 / 4.9 | drop Nemotron |
| Hindi (WER) | **Nemotron** | **14.4** | 31.2 hinted, 44.6 auto | 27.3 | keep Nemotron |
| Arabic (WER) | tie | 10.6 | 10.3 | 24.6 | tie |
| Vietnamese (WER) | tie | 15.0 | 14.1 hinted, 11.7 auto (±5.9) | 16.3 | tie |
| Turkish (WER) | turbo | 13.1 | **8.3** | 29.3 | drop Nemotron |
| Norwegian (WER) | turbo | 23.7 | **9.2** | – | drop Nemotron |

- **Where it's a tie, Nemotron is much cheaper.** On the host it decodes about 3× faster than turbo (RTF 0.09 vs
  0.30), uses half the memory (1.9 vs 3.7 GB), and shows text while you speak. Turbo has no live text yet (#46).
  On a phone whose speed budget rules turbo out, Nemotron is the only engine for Arabic and Vietnamese. Whether
  that is worth keeping it for is Max's call. The phone budget isn't measured here.
- **Hindi is a clear win:** a third of turbo's error rate even with turbo hinted. On auto-detect, which was all
  the app gave turbo for Hindi when this ran, it's worse (44.6); it takes the hint since #37's review.
- **If Nemotron stays, prefer the 1120 ms export.** It is as accurate as 560 ms or better on every set (Hindi
  14.4 vs 15.3), and about 40 % cheaper to run. It shows text in bigger steps.
- **Chinese:** Qwen3 is the most accurate (AISHELL-1 2.7), but Max found it too slow on the phone; turbo is the
  practical pick. SenseVoice-Small (#29: ja 9.1, zh 8.8, AISHELL-1 6.6, ko 7.8 / 2.2) remains the cheapest
  option, pending its licence.

What follows from the recommendation (head / Max to decide; nothing here changed the catalog):
- Point the recommender at Whisper turbo, not Nemotron, for ja/zh/ko/tr/nb (Qwen3 for zh on phones that fit it).
- Give Whisper turbo a language hint for hi/ar/tr/vi/nb (Whisper's code `no` for Norwegian). Its catalog hint list
  stopped at ja/ko/zh. Hinted vs auto: Hindi 31.2 vs 44.6, the rest within noise. **Done in #37's review.**
- The Korean-spacing patch (#37 item 1) is needed only if Nemotron keeps Korean. Turbo and Qwen3 write Korean
  spaces; neither goes through sherpa's `RemoveSpaceBetweenCjk`. The Thai-run cut (item 3) is needed only if
  Nemotron keeps Chinese.

## Two sherpa-onnx fixes for Whisper

Both are build patches in `third_party/patches/sherpa-onnx/`, applied by the app's CMake and by the harness's
`setup.sh`. The turbo numbers above have both; the app will too.

1. **Byte-level BPE character loss** (upstream #3965, merged, unreleased). v1.13.8 cleaned each token of invalid
   UTF-8 separately, deleting every character split across two tokens ("支式教学" for "支架式教学").
2. **A cap of 6 tokens per second of audio** (ours; still on upstream master). sherpa's greedy Whisper decoder
   stops there. Byte-level BPE needs more for many scripts, so turbo cut sentences short: 98 of 100 Hindi clips,
   73 of 200 Japanese, 30 of 100 Arabic. The patch leaves only Whisper's own limit of 224 tokens.

Whisper turbo, same clips, hinted (CER for CJK, WER otherwise):

| Set | v1.13.8 (#29) | + #3965 | + no cap (final) |
|---|---|---|---|
| FLEURS ja | 19.0 | 11.0 | **8.1** |
| FLEURS zh | 32.7 | 11.8 | **9.6** |
| AISHELL-1 | 35.1 | 10.0 | **9.3** |
| FLEURS ko | 19.4 | 4.6 | **4.4** |
| Zeroth ko | 19.6 | 5.6 | **4.9** |
| FLEURS hi | – | 69.4 | **31.2** |
| FLEURS ar | – | 14.4 | **10.3** |
| FLEURS tr / vi / nb | – | 7.7 / 13.5 / 9.5 | 8.3 / 14.1 / 9.2 (within noise) |

English with no cap, against the `results-v3` run: FLEURS-en and LibriSpeech clean unchanged (6.7, 1.7), no
hypothesis different on FLEURS-en. On AMI, 4 of 200 changed: two gained their final full stop and one its last two
words ("…then we have, well, new buttons."), which the cap had cut off, and one existing "xlsx.xlsx.…" loop ran
on to the 224-token limit. LibriSpeech's one change is also a restored full stop. The noise clips gave the same output.
`:textproc`'s loop cut doesn't catch that loop (it has no spaces, like Nemotron's Thai runs); a runaway now costs
up to 224 decoder steps, so a phone may spend a few seconds on one.

FLEURS zh, one sentence:

| | 支架式教学不是一种学习方法，而是一种辅助刚学习使用新电脑程序或启动新项目的人的手段。 |
|---|---|
| turbo, v1.13.8 | 支式教学不是一种学方法,而是一种助刚学,使用新电程或动新目的人的手段。 |
| turbo, fixed | 自驾驶教学不是一种学习方法,而是一种辅助刚学习,使用新电脑程序或启动新项目的人的手段。 |

FLEURS hi: "टेलीविजन रिपोर्टों में प्लांट से निकलने वाला सफेद धुआँ दिखाया गया है।" With the cap turbo stopped at
"टेलेविजन रिपोर्टों में"; without it, "…सफेद दूआ दिखाया गया है।". Nemotron 1120 ms: "…सफेद धुआं दिखाया गया है".

## Japanese, Chinese, Korean

CER in %, normalised as in #29 (`cer.py`); ± is the 95 % bootstrap half-width; language hint set as a user who
picked the language. Nemotron 560 ms and Qwen3 match #29's rows within noise (the engines didn't change; 1120 ms is new).

| Engine | FLEURS ja (200) | FLEURS zh (100) | AISHELL-1 (200) | FLEURS ko (100) | Zeroth ko (100) | RTF | Peak RSS MB |
|---|---|---|---|---|---|---|---|
| whisper-turbo (fixed) | **8.1** ±2.6 | 9.6 ±2.1 | 9.3 ±1.8 | **4.4** ±2.0 | 4.9 ±1.3 | 0.296 | 3711 |
| nemotron-3-5-streaming-560ms | 14.7 ±1.5 | 22.7 ±3.3 | 29.2 ±7.0 | 9.0 ±2.5 | 5.4 ±1.1 | 0.142 | 1931 |
| nemotron-3-5-streaming-1120ms | 14.6 ±1.5 | 22.2 ±3.1 | 24.4 ±5.7 | 8.9 ±2.5 | 5.1 ±1.0 | **0.088** | 1965 |
| qwen3-asr-0-6b | 16.6 ±4.5 | **9.2** ±2.4 | **2.7** ±0.8 | 6.5 ±2.1 | 4.9 ±1.1 | 0.167 | 3946 |

On only the utterances with no digits anywhere (numbers are written differently by each engine and set), the
order holds: turbo 8.8 / 7.8 / 6.2 / 4.2 / 2.5, Nemotron 1120 ms 12.1 / 18.6 / 24.9 / 7.0 / 4.6, Qwen3
16.5 / 6.0 / 2.4 / 6.1 / 3.8.

Nemotron's text problems from #29 are all still there:
- **Korean has no word spaces** (sherpa's `RemoveSpaceBetweenCjk` counts Hangul as CJK). CER ignores spaces.
- **Runs of the Thai letter "อ"** on hard Chinese stretches: 33 (560 ms) and 26 (1120 ms) of 200 AISHELL-1
  clips, 5 and 4 of 100 FLEURS-zh clips.
- **Chinese sentences rarely end with a full stop** (6–8 of 100). Turbo is also weak here (24 of 100); Qwen3
  punctuates like the references.

Punctuation F1 (all marks / commas / sentence ends) on FLEURS ja, zh, ko: turbo 62/58/69, 45/53/34, 82/46/96;
Nemotron 1120 ms 47/52/38, 50/68/15, 79/6/99; Qwen3 76/66/86, 82/79/85, 88/65/99.

## Hindi, Arabic, Turkish, Vietnamese, Norwegian

FLEURS hi_in / ar_eg / tr_tr / vi_vn / nb_no test, 100 utterances each, WER in %. Scored with
`score.normalise_world()`: Whisper's basic normaliser turns combining marks into spaces, splitting Hindi words at
every vowel sign. Arabic also drops short vowels and folds alif, alif maqsura and taa marbuta forms. Hint = as a
user would pick the language; auto = no hint. When this ran, the app gave Whisper turbo and Qwen3 no hint for
these languages (auto) and Nemotron the hint; turbo takes the hint since #37's review.

| Engine | hi | ar | tr | vi | nb | hi auto | ar auto | tr auto | vi auto | nb auto | RTF |
|---|---|---|---|---|---|---|---|---|---|---|---|
| whisper-turbo (fixed) | 31.2 ±2.7 | **10.3** ±2.0 | **8.3** ±2.2 | 14.1 ±5.9 | **9.2** ±1.7 | 44.6 | 10.3 | 8.3 | 11.7 | 9.2 | 0.316 |
| nemotron-3-5-streaming-560ms | 15.3 ±2.2 | 10.7 ±2.1 | 12.9 ±2.6 | 16.3 ±2.8 | 23.7 ±3.0 | 15.4 | 10.9 | 12.9 | 16.3 | 25.7 | 0.152 |
| nemotron-3-5-streaming-1120ms | **14.4** ±2.1 | 10.6 ±2.0 | 13.1 ±2.5 | 15.0 ±2.6 | 23.7 ±3.2 | 14.7 | 10.7 | 13.2 | 14.9 | 24.2 | **0.097** |
| qwen3-asr-0-6b | 27.3 ±3.4 | 24.6 ±2.8 | 29.3 ±3.0 | 16.3 ±2.9 | – | 27.6 | 24.6 | 30.3 | 16.1 | – | 0.257 |

- Qwen3 has no Norwegian. Its Hindi lost the end of 26 of 100 clips.
- Turbo's Vietnamese interval is wide because of one runaway clip.
- **Nemotron's Norwegian prompt key matters.** The app sends `nb` (prompt 103). Its other key, `no` (prompt 27),
  gives garbage (99 % WER), so the app's mapping is right.

## Method

- **Host:** the benchmark host (Ryzen AI Max+ 395), CPU only, 4 jobs × 2 threads at `nice 19`, alongside another
  GPU job. RTF is relative only; a phone is several times slower. #29's interleaved runs give cleaner ratios
  (Nemotron 560 ms ≈ Qwen3 on Chinese; 320 ms slower).
- **Runtime:** sherpa-onnx v1.13.8 built from source, TTS off, with both patches. The turbo-only patch build was
  checked against the shared build: identical text on all 100 Hindi clips.
- **Engines:** the app catalog's int8 files. Nemotron gets the app's keys (`ja-JP`, `zh-CN`, `vi-VN`, else the
  code); Whisper gets the code (`no` for Norwegian); Qwen3 gets English language names (the harness adds Hindi,
  Arabic, Turkish and Vietnamese, which the app lacks).
- **Data:** FLEURS (CC-BY-4.0), AISHELL-1 and Zeroth-Korean as in #29; the five new FLEURS sets are seeded
  subsets of 100 (`prepare_data.py`). FLEURS is read Wikipedia text; dictation is harder for every engine.
- **Reproduce:** `tools/asr-bench/README.md`, section "Does Nemotron 3.5 stay? (#37)", and `run_37.sh`.
  Results on the benchmark host: `results-37` (turbo with #3965 only), `results-37cap` (turbo with both patches, plus
  English), `results-37-final` (the tables here).
