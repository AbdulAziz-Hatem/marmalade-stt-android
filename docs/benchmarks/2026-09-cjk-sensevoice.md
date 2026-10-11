# SenseVoice-Small vs Nemotron 3.5 for Japanese, Chinese and Korean, September 2026 (#29)

SenseVoice-Small (FunAudioLLM, Alibaba) measured against Nemotron 3.5 streaming (320 and 560 ms, today's pick for
ja/zh), with Whisper turbo and Qwen3-ASR 0.6B as references. It ran on the host harness (`tools/asr-bench`), with
the app's pipeline and the int8 files the app would download. Nothing in the app changed. Per-set statistics are in
`2026-09-cjk-sensevoice.json` next to this file. The license and ethics review is
`docs/model-reviews/sensevoice-small.md`.

## License first

**SenseVoice-Small's weights are not under a standard open-source license, and commercial use is unresolved.** They
use the FunASR Model Open Source License Agreement v1.1. It grants "use, copy, modify, and share", which covers
downloading the k2-fsa int8 export, and it requires attribution. But it also has these clauses:

- §3 says the model is "provided for reference and learning purposes only". A maintainer told users commercial use
  was fine, then withdrew that as "not final license-owner confirmation" (SenseVoice #334, reopened; PR #357
  unmerged). FunASR #3715 asks about this exact export and is still open.
- §4.2 forfeits the license for "unjustified denigration" of the model.
- §6 lets Alibaba revise the terms, with the new terms taking "effect automatically".

It is fine as a developer or benchmark engine. **A user-facing default needs Max's call on this license.**

The ethics verdict is PASS (grey). The provenance is opaque: Alibaba gives "approximately 300,000 hours" and names
no sources, the same grade as Qwen3-ASR. As a recognition model it can't imitate anyone. Use only the 2024-07-17
export. The `int8-2025-09-09` export is a third-party Cantonese fine-tune trained on CC-BY-NC data.

## Summary

- **Accuracy: SenseVoice-Small is best or tied-best on every set except AISHELL-1**, where Qwen3 leads.
  - Japanese: CER 9.1 vs Nemotron 560 ms's 14.7.
  - FLEURS Chinese: 8.8 vs 22.7.
  - AISHELL-1: 6.6 vs 28.9.
  - Korean: 7.8 / 2.2 vs 9.0 / 5.4 (FLEURS / Zeroth).
  - Qwen3-ASR matches it on Chinese and is best on AISHELL-1 (2.7). It is seven times slower and uses more than
    four times the memory.
- **Speed: SenseVoice is non-autoregressive and by far the cheapest engine measured.** It is about 7× faster than
  Nemotron 560 ms, interleaved on the same clips (RTF 0.021 vs 0.14), and 10× faster than 320 ms. Peak RSS is
  0.9 GB vs 1.9 GB, and the download is 240 MB vs 680 MB. It has no live stream, but a partial re-decode costs so
  little that its first words appear as soon as Nemotron 560 ms's (about 0.25 s after the VAD onset on the host).
- **Nemotron 3.5 has three problems on these languages beyond its CER:**
  1. **Runs of a Thai letter** ("港神อออออ…出道近二十年") replace hard stretches: in 35–43 of 200 AISHELL-1
     clips and 5–9 of 100 FLEURS-zh clips. `:textproc`'s loop cut won't catch them: they are one unspaced "word".
     Without those clips it is still behind (AISHELL-1 9.8, FLEURS-zh 21.4).
  2. **Korean comes out with no word spaces** ("지역별및계절별악천우현상에는…"), on all 200 Korean clips. The
     model emits the spaces. sherpa-onnx's `RemoveSpaceBetweenCjk` deletes them because it counts Hangul as CJK.
     That is in v1.13.8 and still on master, so the app has it too. CER ignores spaces, so the Korean numbers hide
     it.
  3. **Chinese sentences never end with a full stop** (0 of 100), and Japanese rarely do (40 of 200).
- **Whisper turbo's numbers here are a sherpa-onnx bug, not the model.** sherpa-onnx v1.13.8 cleans each
  byte-level BPE token of invalid UTF-8 separately, which deletes every character split across two tokens
  ("支式教学" for "支架式教学"). It is fixed on master (k2-fsa/sherpa-onnx #3965, merged 2026-09-20) but not
  released yet. The app's Whisper turbo loses the same characters today, in any script with multi-byte characters.
- **Recommendation:** see the end.

## CER

Character error rate in %, after NFKC, lower case, Traditional → Simplified for Chinese, and with punctuation,
symbols and spaces removed. ± is the 95 % bootstrap half-width. The language hint is set as a user who picked the
language would set it. Pipeline: the app's (VAD, 0.6 s offline pre-roll, 1 s streaming pre-roll), as in
`2026-09-host-wer.md`. No `:textproc`.

| Engine | FLEURS ja (200) | FLEURS zh (100) | AISHELL-1 (200) | FLEURS ko (100) | Zeroth ko (100) | RTF (shared machine) | Peak RSS MB |
|---|---|---|---|---|---|---|---|
| **sense-voice-small** | **9.1** ±1.2 | **8.8** ±2.0 | 6.6 ±1.5 | 7.8 ±2.2 | **2.2** ±0.6 | **0.019** | **892** |
| nemotron-3-5-streaming-560ms | 14.7 ±1.5 | 22.7 ±3.3 | 28.9 ±7.1 | 9.0 ±2.5 | 5.4 ±1.1 | 0.140 | 1917 |
| nemotron-3-5-streaming-320ms | 15.5 ±1.5 | 22.4 ±3.0 | 34.1 ±7.8 | 9.1 ±2.4 | 5.3 ±1.2 | 0.227 | 1953 |
| qwen3-asr-0-6b | 16.6 ±4.5 | 9.2 ±2.4 | **2.7** ±0.8 | **6.5** ±2.1 | 4.9 ±1.1 | 0.183 | 3945 |
| whisper-turbo (sherpa bug, see above) | 19.0 ±1.4 | 32.7 ±2.2 | 35.1 ±2.2 | 19.4 ±2.6 | 19.6 ±1.7 | 0.297 | 3689 |

- Qwen3's Japanese score includes one runaway loop ("このようにして、" repeated to its token limit); `:textproc`
  would cut that.
- Nemotron's AISHELL-1 intervals are wide because of the Thai-letter runs.
- **Auto-detect** (no hint; the app's default for the multilingual engines) changes little: SenseVoice 8.7 / 8.8 /
  7.8 on FLEURS ja / zh / ko, Nemotron 560 ms 15.5 / 23.4 / 8.9. On Japanese, SenseVoice's auto mode prefixed "아."
  (Korean) to 7 of 200 clips: a filler it produces for the pre-roll's noise.
- **Numbers.** The sets disagree on how to write them: FLEURS uses digits, AISHELL-1 and Zeroth spell them out. The
  engines disagree too. SenseVoice (ITN on), Qwen3 and Whisper write digits; Nemotron mostly writes words. On only
  the utterances where no reference and no engine's output has a digit (the same utterances for every engine), the
  order holds:

| Engine | FLEURS ja (n=124) | FLEURS zh (n=72) | AISHELL-1 (n=162) | FLEURS ko (n=80) | Zeroth ko (n=69) |
|---|---|---|---|---|---|
| sense-voice-small | **7.8** | 7.3 | 3.0 | 6.7 | **2.2** |
| nemotron-3-5-streaming-560ms | 12.0 | 18.7 | 30.3 | 7.2 | 4.8 |
| nemotron-3-5-streaming-320ms | 13.1 | 18.8 | 35.6 | 7.1 | 4.4 |
| qwen3-asr-0-6b | 16.5 | **6.0** | **2.4** | **6.1** | 3.8 |
| whisper-turbo (sherpa bug) | 20.2 | 32.0 | 33.4 | 19.0 | 16.7 |

Japanese CER also counts spelling variants that a reader accepts, such as 名のとおり / 名の通り and 応える / 答える.
Every engine pays for these, so the ranking holds, but no engine could reach 0.

## Punctuation and text

Scored against FLEURS's punctuated references. F1 % over marks after aligned characters, class-aware (comma,
full stop, question, exclamation); then comma F1; then sentence-end F1. After the semicolon: utterances ending in a
sentence mark, out of all.

| Engine | FLEURS ja | FLEURS zh | FLEURS ko |
|---|---|---|---|
| sense-voice-small | **78** / **72** / 85; 200 of 200 | 81 / 79 / 84; 100 of 100 | 79 / 22 / 97; 100 of 100 |
| nemotron-3-5-streaming-560ms | 48 / 52 / 42; 40 of 200 | 45 / 65 / 5; **0** of 100 | 77 / 6 / 97; 95 of 100 |
| nemotron-3-5-streaming-320ms | 49 / 51 / 46; 48 of 200 | 46 / 66 / 3; 0 of 100 | 77 / 6 / 97; 95 of 100 |
| qwen3-asr-0-6b | 76 / 66 / **86**; 199 of 200 | **82** / 79 / **85**; 100 of 100 | **88** / **65** / 99; 100 of 100 |
| whisper-turbo | 56 / 56 / 56; 70 of 200 | 32 / 39 / 21; 17 of 100 | 79 / 38 / 95; 92 of 100 |

- **SenseVoice and Qwen3 punctuate like the references.** Marks per 100 characters: 5.3 and 5.6 vs 5.6 in
  Japanese, 7.1 and 7.7 vs 6.8 in Chinese. In Korean, SenseVoice rarely writes commas (comma F1 22; Qwen3 65).
- **Nemotron** under-punctuates Japanese (2.0 marks per 100 characters). In Chinese it writes half-width ", " with a
  space and no final "。".
- **Spaces.** Korean word spacing, in spaces per 100 characters (the references have 28–33): SenseVoice 28 / 34,
  Qwen3 28 / 31, Whisper 27 / 31, Nemotron **0.4 / 0.0** (the sherpa-onnx bug above). In Japanese, SenseVoice puts
  a stray space into some sentences (3.3 per 100 characters, vs 0.1 in the references), e.g. "領土で あり".

One sentence each (FLEURS; AISHELL-1 has no punctuation):

| | FLEURS zh: 支架式教学不是一种学习方法，而是一种辅助刚学习使用新电脑程序或启动新项目的人的手段。 | FLEURS ko: 지역별 및 계절별 악천후 현상에는 폭풍설, 눈보라, 진눈깨비 및 황사 등이 포함됩니다. |
|---|---|---|
| sense-voice-small | 支架式教学不是一种学习方法，而是一种辅辅助刚学习使用新电脑程序或启动新项目的人的手段。 | 지별 및 계절별 악천의 현상에는 폭풍설 눈보라 진눈깨비 및 황사 등이 포함됩니다. |
| nemotron-3-5-streaming-560ms | 支架式教学不是一种学习方法, 而是一种辅助刚学习使用新电脑程序或启动新项目的人的手段 | 지역별및계절별악천우현상에는폭풍설, 눈보라, 진능개비및황사등이포함됩니다. |
| qwen3-asr-0-6b | 支架式教学不是一种学习方法，而是一种辅助刚学习、使用新电脑程序或启动新项目的人的手段。 | 지역별 및 계절별 악천후 현상에는 폭풍설, 눈보라, 진눈기비 및 황사 등이 포함됩니다. |
| whisper-turbo | 支式教学不是一种学方法,而是一种助刚学,使用新电程或动新目的人的手段。 | 지역별  계 악천후 현상에는 폭설, ,   사 등이 포니다. |

## Speed

Decode time over audio time, 2 threads, with the engines taking turns clip by clip (`granite/rtf_ab.py --lang`,
no VAD) so that the other load on the machine fell on all of them equally. Trust the ratios, not the absolute
values; a phone is several times slower.

| Engine | FLEURS zh + AISHELL-1 (50 clips, 406 s) | FLEURS ja (30 clips, 391 s) | vs SenseVoice |
|---|---|---|---|
| sense-voice-small | **0.021** | **0.021** | 1× |
| nemotron-3-5-streaming-560ms | 0.143 | 0.141 | 6.8× |
| nemotron-3-5-streaming-320ms | 0.222 | 0.216 | 10.5× |
| qwen3-asr-0-6b | 0.145 | – | 7.0× |

SenseVoice is a CTC-style encoder with no decoder loop, so its cost grows only with the audio length. The model is
about 234M parameters (per ASLP-lab) and 239 MB at int8. Nemotron pays for its cache-aware streaming encoder on every chunk, and the
320 ms variant decodes more chunks for the same audio. It is slower than 560 ms and no more accurate, as it was on
English (#14). There is no phone measurement of SenseVoice yet; the Pixel 8a thread sweep (#34) is the place to
add one.

## Latency to first words

Time from the VAD's onset to the first partial or Final that shows a word, on the harness's virtual clock
(`latency.py --engine`, each decode costing its host wall time). SenseVoice runs the app's offline loop, with a
partial re-decode every 0.2 s at the earliest (`StreamingSession`). Nemotron runs the streaming loop and shows the
live result after every decode (`OnlineSession`). 10 clips per length and language, median seconds, host:

| Engine | FLEURS ja, 10 s | FLEURS ja, 20 s | FLEURS zh, 10 s | FLEURS zh, 20 s |
|---|---|---|---|---|
| sense-voice-small | 0.29 | 0.24 | 0.24 | 0.24 |
| nemotron-3-5-streaming-560ms | 0.28 | 0.28 | 0.28 | 0.28 |
| nemotron-3-5-streaming-320ms | 0.48 | 0.47 | 0.48 | 0.43 |

- On the host, both SenseVoice and Nemotron 560 ms show words about a quarter-second after the onset.
- SenseVoice's figure is the partial interval plus one decode of about 1 s of audio. On a phone that decode is
  several times longer, but at host RTF 0.02 it stays a small fraction of a second. Nemotron's figure is bound by
  its chunk, and its per-chunk cost also grows on a phone. The #13 emulator ran Nemotron 320 ms at RTF 1.9.
- The first-text counter ignores a lone "." and a first VAD segment on breath or noise. SenseVoice emits a stray
  "." on pre-roll noise, and counting those would have credited it with 0.2 s it didn't earn.
- The harness's "end of speech → Final" column isn't reported here. Its speech-end estimate (the last frame within
  35 dB of the loudest) lands late on FLEURS's low-SNR, padded clips, which gave negative lags on Japanese. Both
  engines close an utterance on the same Silero pause (0.9 s), so a Final's wait is the pause plus one decode:
  milliseconds for SenseVoice, and the flushed last chunk for Nemotron.

## Noise

Through the app's VAD, SenseVoice produced no text on the six noise clips. Fed straight to the model (what a VAD
false trigger would hand it, with the English hint the noise set carries), it said "The." or "I." on all six.
Neither is in `:textproc`'s phantom list (`Phantoms.kt`), so after a VAD false trigger they would be typed. It is
the same risk as Whisper's "you", and the VAD is what prevents it. Adding the pair would matter only if SenseVoice
reaches the app. Parakeet and Nemotron stay silent on noise (#14).

## Recommendation for the ja/zh (and ko) default

**On the measurements, SenseVoice-Small should be the default for Japanese, Chinese and Korean**, replacing
Nemotron 3.5 streaming.
- It is more accurate on every set: roughly a third lower CER than Nemotron on Japanese and less than half on
  Chinese.
- It writes correct Korean spacing and punctuates like the references.
- It is about 7× cheaper to run, with half the memory and a third of the download.
- It shows first words as fast as Nemotron 560 ms.

It is an offline engine, so it depends on the VAD and on partial re-decodes, the same as Parakeet. That keeps the
engine model simple: Parakeet for its languages, SenseVoice for ja/zh/ko/yue, both offline.

**The license decides it, not the numbers.** The FunASR v1.1 agreement allows use and redistribution with
attribution, but Alibaba hasn't confirmed commercial use (§3), and it adds a non-disparagement forfeiture and
self-updating terms. The options, all Max's call:

1. **Adopt now** as a user engine with the FunASR attribution and a licence call-out at install. Accept that §3
   and §6 leave commercial use at Alibaba's discretion.
2. **Adopt as a developer engine now, promote later** if FunASR #3715 or SenseVoice #334 gets an owner-level
   "commercial use is permitted".
3. **Keep Nemotron 3.5 for ja/zh.** Nemotron 560 ms is usable for Japanese (14.7). For Chinese it is weak (22.7,
   with Thai-letter runs). Chinese would then want Qwen3-ASR (Apache-2.0, 9.2 / 2.7) on phones that can hold its
   ~4 GB, but Max found Qwen3 too slow in hands-on use. Korean then needs the sherpa-onnx spacing fix below.

Recommendation: **option 2**. It costs nothing to wait, and a user-facing default shouldn't rest on a clause the
licensor itself calls unsettled. Whatever Max picks, prefer the 560 ms variant to 320 ms for Nemotron.

**Follow-ups** (outside this issue's scope; none are app changes made here):
- **sherpa-onnx drops Korean word spaces** (`RemoveSpaceBetweenCjk` treats Hangul as CJK, in v1.13.8 and on
  master). The app's Nemotron 3.5 Korean output has no spaces. Fix options: a patch in our source build, or an
  upstream issue/PR. Upstream is public, so that needs Max.
- **sherpa-onnx v1.13.8 deletes characters split across byte-level BPE tokens** in Whisper, which hits the app's
  Whisper turbo for CJK and other multi-byte scripts. The fix (k2-fsa/sherpa-onnx #3965) is merged but
  unreleased: backport the patch or bump past it, then re-run the turbo row.
- **`:textproc` loop cut for unspaced scripts:** Nemotron's "อออออ…" runs are one word, so `Phantoms.cutLoops`
  misses them. A rule for a long run of one character from a script foreign to the text would catch them.
- If SenseVoice is adopted: discard its emotion and event tags (the review's posture note), and consider adding
  its noise outputs "The." / "I." to the phantom list.
- AISHELL-1's "free for academic use" sentence: Max to rule whether it may stay in the harness.

## Method

- **Host:** the benchmark host (Ryzen AI Max+ 395), CPU only, sherpa-onnx v1.13.8 built from source with TTS off, 2 threads
  per engine, 3 jobs at a time at `nice 19`. The machine was shared with another benchmark (#25) and a game, so
  the RTF column ranks engines only roughly; the Speed section's interleaved runs give the ratios.
- **Engines:** the app catalog's int8 files for Nemotron 3.5, Whisper turbo and Qwen3. SenseVoice-Small is
  `csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` @ `2365baea`, `model.int8.onnx` + `tokens.txt`,
  sha256-pinned in `tools/asr-bench/asrbench/engines.py` (`CANDIDATES`). It runs with ITN on, which gives
  punctuation and digits. The language hint is the set's language (sherpa-onnx `language`); auto passes "auto".
  Nemotron gets `ja-JP` / `zh-CN` / `ko` as the app sends them (`SherpaBackends.kt`).
- **Data** (fetched without an account, seeded subsets):
  - FLEURS ja_jp, cmn_hans_cn and ko_kr test (CC-BY-4.0): 200 / 100 / 100 clips. Read Wikipedia sentences,
    punctuated, numbers as digits.
  - AISHELL-1 test (Apache-2.0, openslr SLR33): 200 clips from 20 speakers. The files came from the ungated
    per-file mirror `shenyunhang/AISHELL-1` @ `2724409d`, because the original is one 15 GB tarball. No
    punctuation; numbers spelled out. The openslr page also says "The data is free for academic use"; that is
    why AISHELL-1 has its own column.
  - Zeroth-Korean test (CC-BY-4.0, openslr SLR40, via `kresnik/zeroth_korean` @ `1fe93789`): 100 clips. No
    punctuation; numbers spelled out.
  - **No second Japanese set.** JSUT and JVS audio is non-commercial research and personal use only.
    ReazonSpeech is restricted to analysis under Article 30-4 of Japan's copyright act, and its HF repo is gated.
    Common Voice needs an account.
- **Scoring:** `tools/asr-bench/asrbench/cer.py` (docstring). The Traditional → Simplified step uses
  `opencc-python-reimplemented` (Apache-2.0, pure Python).
- **Reproduce:** `tools/asr-bench/README.md`, section "SenseVoice-Small and the CJK sets", and `run_cjk.sh`.
