# Host WER benchmark, September 2026 (#14, #15, #25)

Word error rate of the catalog engines on public test sets, measured on a
desktop CPU with the files and settings the app uses. Accuracy doesn't
depend on the device, so these numbers hold for phones. Speed does, so the
RTF column only ranks the engines against each other. Harness and
reproduction steps: `tools/asr-bench/README.md`. Every per-set statistic
(S/D/I counts, intervals, failure counters, noise outputs) is in
`2026-09-host-wer.json` next to this file.

**Updated for #15 (2026-09-26):** the app no longer clips the first word.
The main tables show the app after that fix. The #14 numbers (the app
before it) are in "#15: the first-word fix, before and after" and in
`2026-09-host-wer-before-15.json`. The #15 run shared the machine, so the
`rtf` fields in the JSON aren't comparable; the RTF column is #14's.

**Updated for #25 (2026-09-27):** four Parakeet engines from the Open ASR
Leaderboard (TDT_CTC 110M, TDT 0.6B v2, Unified EN 0.6B offline and 560 ms
streaming), Moondream's Parakeet Ultra converted with open tools (not in the
app until it is hosted), and the Parakeet German gap found and fixed: the app now pads
every Parakeet decode with 0.3 s of silence. Parakeet v3's rows show the app
after that fix. Details in "#25: Parakeet leaderboard picks and the German
gap"; per-set statistics in `2026-09-host-wer-25.json`.

## What this means for engine choice

Numbers are for the app after #15 (first word kept); #14's are in the
before/after section.

- **Parakeet TDT 0.6B v3 is the strongest multilingual offline candidate.**
  It scores LibriSpeech 1.7 / 5.1 and is about 3× faster than today's
  distil-small.en (only Parakeet 110M is faster). It never produced text
  from noise, even without the VAD. Its weaknesses:
  - It is mid-pack on FLEURS-en (8.0 vs turbo's 6.7) and behind Nemotron EN,
    Qwen3 and the English-only Parakeets on AMI (12.4 vs 8.5–10.6).
  - It returns nothing for a few short spontaneous clips (2 of 200 on AMI),
    and for some abrupt onsets after 0.3 s or more of silence (see #15
    below). That's why the offline pre-roll is 0.6 s.
  - Its German was 16.2 until #25: the int8 export drifted into English
    partway through sentences because the app's audio stopped right at the
    last word. With 0.3 s of silence after it, v3 scores German 7.4,
    Spanish 4.5 and French 9.6, behind turbo's 5.8 / 3.1 / 9.4 and at
    Canary's level.
- **Parakeet Ultra beats v3 everywhere at v3's cost** (#25): LibriSpeech
  1.5 / 4.5, FLEURS-en 6.9, AMI 9.1, and German / Spanish / French 6.5 /
  3.8 / 7.9, the best multilingual Parakeet. It never returned empty text
  after silence. It is Moondream's post-trained v3; we convert it ourselves
  (`tools/parakeet-ultra`) and host the files on our own
  `marmalade-stt-engines` release; the app has it as a developer engine
  since #39.
- **Parakeet Unified EN 0.6B and TDT 0.6B v2 are the most accurate English
  engines measured** (#25). Unified: LibriSpeech 1.3 / 3.3, FLEURS 6.5,
  AMI 8.9. v2: 1.6 / 3.4, 6.3, AMI 8.5, the best AMI score of any engine.
  Both decode at v3's cost (RTF about 0.03) with less memory. English only.
- **Parakeet TDT_CTC 110M is the small-model candidate** (#25): 136 MB,
  about a third of v3's decode time, and within noise of v3 on English
  (LibriSpeech 1.9 / 5.2, FLEURS 8.6, AMI 12.0). It beats every Whisper we
  ship, distil-small.en included, on every English set. It writes numbers
  as words, so its raw dictation score leans on `:textproc`'s number
  conversion.
- **Parakeet Unified's 560 ms streaming export is far too slow for a
  phone** (#25): RTF 1.7 on the host, 40 times its offline export. sherpa-onnx
  runs it buffered, re-running the encoder over seconds of left context for
  every 560 ms chunk. It is in the catalog, marked slow, for completeness.
- **distil-small.en (today's default) is middling, not bad.** It is a little
  behind Whisper small.en (LS clean 2.6 vs 2.0, FLEURS 9.3 vs 7.7, AMI level)
  at under half its decode time. It is behind Parakeet and Qwen3 on every
  English set, and still returns nothing for 2 of 40 dictation clips.
- **Qwen3-ASR is in the top group on every English set** (AMI 10.6, FLEURS
  7.6, LibriSpeech 2.0 / 6.5). It costs the most memory (about 4 GB on the
  host) and decodes 5× slower than Parakeet.
  Without the VAD it invents text from noise ("The system is now running."),
  so the VAD is load-bearing for it.
- **Whisper turbo leads Spanish, German and French** (3.1 / 5.8 / 9.4),
  though Canary is within noise in German and French. It is the best
  Whisper overall and the best on FLEURS-en. It is also the slowest engine
  and the second largest in memory. Auto-detect cost it about 2 points in
  German, which is within noise.
- **Canary 180M is good for its size** (207 MB): 4.9 on LibriSpeech-other,
  the best German after turbo, and faster than distil (RTF 0.070 vs 0.101).
  But it sometimes falls into a letter-by-letter loop ("E E E …", "T T T …"),
  about one clip in 200. Its LS-clean (5.3 ±5.6) and FLEURS (12.6 ±7.6)
  numbers are one such loop each. Which clip trips it changes with any
  change to the audio. The app now cuts such loops in its text cleanup
  (#18; see "Decoder loops" below), which takes those two numbers to 2.3
  and 9.2. It isn't sherpa-onnx's first-token workaround (its issue
  #3919): in #18's probe that fired on 12 of 853 decodes, none of which
  looped. Canary also writes numbers as words.
- **Nemotron EN 560 ms is top-tier now that the app keeps the first word**
  (#15: a 1 s pre-roll, padded with silence near mic open). It scores
  LibriSpeech 2.0 / 6.1, FLEURS 8.4 and AMI 10.4, the best AMI score with
  Qwen3's 10.6. That is Parakeet-level accuracy while streaming, at about
  5× Parakeet's decode cost. Nemotron 3.5 gained as much (560 ms: 3.2 / 7.8
  / 10.2 / 13.8) but stays a step behind on English. More points:
  - The 560 ms variant is about as accurate as 320 ms and cheaper to run.
  - All Nemotrons write numbers as words ("twenty minutes"), often skip the
    final full stop, and drop "slash"/"dash" words. That shows as the worst
    raw dictation WER (about 63 % after #15). `:textproc`'s number conversion
    (#16) removes most of that (see "Dictation after text cleanup" below; those
    numbers were re-scored from the pre-#15 outputs).
- **The VAD is what keeps noise out.** Through the app's pipeline no engine
  produced any text on the six noise clips. Fed straight to the model,
  every Whisper and Qwen3 hallucinated: on all six clips, except distil on
  four ("you", "so", "(buzzing)", "[Music]", a run of "( ( ( ("). Canary
  produced "." or letters, while Parakeet and Nemotron stayed silent. A VAD false trigger on
  a Whisper engine would have typed a noise tag like "(buzzing)" into the
  text field; since #16 `:textproc` removes such captions anywhere in an
  utterance.
- **Dictation formatting (synthetic, indicative only):** Whisper small.en
  formats best (raw WER 16.0). Only it and turbo wrote an email address as
  `Maria.Lopez@example.com`; the rest say "at" or "dot". No engine reliably
  writes file names, paths or URLs the way a user types them.

## English WER

WER in %, normalised; ± is the 95 % bootstrap half-width. Dictation is
synthetic. RTF is decode time ÷ audio time with 2 threads on the host, from
the uncontended first run (see Method): **relative only**, a phone is
several times slower. Peak RSS is the whole host process, Python included.

| Engine | LS clean | LS other | FLEURS en | AMI | Dictation (norm) | Dictation (raw) | RTF | Peak RSS MB |
|---|---|---|---|---|---|---|---|---|
| distil-small-en (default) | 2.6 ±0.6 | 7.4 ±1.4 | 9.3 ±1.4 | 14.3 ±1.9 | 24.6 ±7.7 | 35.6 | 0.101 | 1121 |
| whisper-tiny-en | 5.0 ±0.8 | 14.3 ±1.9 | 13.6 ±1.9 | 19.1 ±2.8 | 14.1 ±5.7 | 24.2 | 0.047 | 596 |
| whisper-base-en | 4.1 ±1.1 | 11.4 ±1.9 | 10.0 ±1.6 | 15.8 ±2.1 | 12.7 ±4.8 | 21.2 | 0.085 | 874 |
| whisper-small-en | 2.0 ±0.5 | 7.2 ±1.3 | 7.7 ±1.3 | 14.6 ±2.0 | 10.5 ±4.2 | **16.0** | 0.242 | 1486 |
| whisper-turbo | 1.7 ±0.5 | 5.2 ±1.1 | 6.7 ±1.4 | 14.4 ±2.1 | **9.1** ±3.5 | 19.0 | 0.277 | 3872 |
| parakeet-tdt-0-6b-v3 (#25) | 1.7 ±0.5 | 5.1 ±1.0 | 8.0 ±1.3 | 12.4 ±2.6 | 17.7 ±5.1 | 34.0 | 0.031 | 2259 |
| parakeet-tdt-110m-en (#25) | 1.9 ±0.5 | 5.2 ±1.0 | 8.6 ±1.4 | 12.0 ±1.9 | 17.1 ±6.5 | 58.0 | **≈0.010**† | 608† |
| parakeet-tdt-0-6b-v2 (#25) | 1.6 ±0.5 | 3.4 ±0.9 | **6.3** ±1.3 | **8.5** ±1.5 | 15.2 ±4.6 | 30.1 | ≈0.030† | 1369† |
| parakeet-unified-en-0-6b (#25) | **1.3** ±0.4 | **3.3** ±0.8 | 6.5 ±1.2 | 8.9 ±1.5 | 15.5 ±5.0 | 30.1 | ≈0.032† | 1575† |
| parakeet-ultra (#25) | 1.5 ±0.4 | 4.5 ±1.0 | 6.9 ±1.3 | 9.1 ±1.6 | 16.3 ±5.2 | 28.2 | ≈0.033† | 2407† |
| parakeet-unified-en-streaming-560ms (#25)‡ | 1.6 ±1.0 | 6.3 ±3.0 | 9.1 ±5.1 | 17.8 ±6.2 | 18.5 ±7.3 | 38.0 | ≈1.2† | 1149† |
| canary-180m-flash | 5.3 ±5.6 | 4.9 ±1.1 | 12.6 ±7.6 | 17.0 ±5.2 | 17.7 ±5.5 | 65.3 | 0.070 | 1101 |
| nemotron-3-5-streaming-320ms | 2.7 ±0.7 | 8.3 ±1.5 | 10.4 ±1.6 | 14.7 ±2.4 | 15.7 ±4.9 | 63.2 | 0.207 | 2004 |
| nemotron-3-5-streaming-560ms | 3.2 ±0.7 | 7.8 ±1.4 | 10.2 ±1.5 | 13.8 ±2.2 | 16.0 ±5.3 | 62.9 | 0.132 | 2013 |
| nemotron-en-streaming-560ms | 2.0 ±0.6 | 6.1 ±1.2 | 8.4 ±1.5 | 10.4 ±1.7 | 14.1 ±5.3 | 62.9 | 0.145 | 1015 |
| qwen3-asr-0-6b | 2.0 ±0.5 | 6.5 ±1.2 | 7.6 ±1.5 | 10.6 ±1.8 | 14.6 ±5.5 | 42.0 | 0.169 | 4082 |

Differences under about 1 point on LibriSpeech, and 2–3 points on FLEURS
and AMI, are inside the noise of a 200-utterance sample. Canary's LS-clean
and FLEURS scores each include one runaway loop (see above). Rows marked
#25 are the app with its 0.3 s Parakeet tail padding.

† The #25 run shared the machine with #26's jobs. Its RTFs are scaled by
Parakeet v3's in the same run (0.043 there, 0.031 alone): 110M decodes in
0.33× v3's time, v2 in 0.98×, Unified in 1.02×, and the streaming export
in 40× (RTF 1.7 as measured), Ultra in about 1.07×. Peak RSS is from that
run.
‡ The first 50 utterances of each set only (dictation has 40): at RTF 1.7
the full sets would have taken most of a day. On the same utterances the
offline Unified export scored 0.8 / 3.8 / 4.8 / 10.3 / 15.5, so streaming
costs it 1–8 points (most on AMI, where it returned nothing for 3 of 50) on
top of the cost in time.

## Spanish, German, French

FLEURS, 50 utterances per language, WER % normalised. "Hint" means the
language was set as a user would pick it. "Auto" means no hint, the app's
default for turbo, Nemotron 3.5 and Qwen3. With 50 utterances, differences
under about 3 points are noise. One FLEURS-fr reference is truncated (the
audio says more than the label), which adds the same insertions to every
engine.

| Engine | es | de | fr | es auto | de auto | fr auto | FLEURS en auto |
|---|---|---|---|---|---|---|---|
| whisper-turbo | **3.1** | **5.8** | **9.4** | 3.1 | 7.6 | 9.4 | 6.7 |
| parakeet-tdt-0-6b-v3 (always auto; #25) | 4.5 | 7.4 | 9.6 | – | – | – | – |
| parakeet-ultra (always auto; #25) | 3.8 | 6.5 | 7.9 | – | – | – | – |
| canary-180m-flash (no auto) | 8.0 | 8.4 | 11.7 | – | – | – | – |
| nemotron-3-5-streaming-320ms | 7.5 | 10.6 | 13.3 | 8.2 | 11.3 | 13.4 | 10.6 |
| nemotron-3-5-streaming-560ms | 7.9 | 10.1 | 13.8 | 7.5 | 10.7 | 14.7 | 10.3 |
| qwen3-asr-0-6b | 8.9 | 10.1 | 16.2 | 8.4 | 9.3 | 16.7 | 8.2 |

## #15: the first-word fix, before and after

#14 found two ways the app lost the first word. #15 fixed both
(`StreamingSession` / `OnlineSession`, ported to the harness in lockstep):

1. **Offline engines decoded Silero's bare segment.** Silero starts its
   segment only ~0.3 s before it detects speech. At mic open its state is
   cold, so it detects ~0.1 s later still, and the segment starts inside the
   first word (median 0.13 s, 90th percentile 0.33 s into the clip, measured
   on the dictation set; with a 0.5 s lead-in, 0.02 s). Now the Final decodes
   the audio from a pre-roll before the onset, then the segment.
2. **Nemotron's stream started 0.4 s before the onset**, too little left
   context for the cache-aware encoder. It now starts 1 s before. When the
   mic opened less than 1 s before the onset, silence makes up the rest:
   with the 1 s pre-roll alone, speech from the mic's first sample still
   lost its first word on 27 of 40 dictation clips (Nemotron EN); with the
   padding, on none.

**Choosing the offline pre-roll.** WER, then head_drop in brackets, for
pre-rolls measured back from Silero's onset. "Mic open" is speech from the
mic's first sample (no lead-in); "lead-in" has the usual 0.5 s of mic
noise first. LS clean and FLEURS at 0.6 s come from the full run below.

| Engine | Mic open: dictation | Mic open: AMI | Lead-in: dictation | Lead-in: AMI | Lead-in: LS clean | Lead-in: FLEURS en |
|---|---|---|---|---|---|---|
| distil-small-en, before (segment only) | 37.0 (11) | 16.3 (49) | 27.9 (3) | 13.8 (22) | 3.0 (8) | 9.8 (4) |
| distil-small-en, 0.4 s | 29.3 (7) | 14.8 (32) | 26.0 (2) | 14.2 (18) | 2.7 (3) | 9.1 (2) |
| distil-small-en, 0.6 s | 25.4 (2) | 14.3 (24) | 24.6 (2) | 14.3 (17) | 2.6 (2) | 9.3 (0) |
| distil-small-en, 0.8 s | 24.9 (1) | 14.0 (20) | 22.7 (1) | 13.9 (16) | 2.7 (1) | 9.2 (0) |
| distil-small-en, 1 s | 24.9 (1) | 14.1 (18) | 22.9 (1) | 14.1 (15) | – | – |
| parakeet-tdt-0-6b-v3, before (segment only) | 24.9 (13) | 17.6 (71) | 18.0 (1) | 11.2 (25) | 2.1 (5) | 8.8 (5) |
| parakeet-tdt-0-6b-v3, 0.4 s | 21.0 (4) | 12.2 (32) | 17.7 (0) | 10.1 (19) | 1.8 (4) | 8.2 (3) |
| parakeet-tdt-0-6b-v3, 0.6 s | 17.7 (1) | 11.4 (26) | 16.9 (0) | 12.4 (18) | 1.6 (2) | 8.3 (1) |
| parakeet-tdt-0-6b-v3, 0.8 s | 17.7 (0) | 11.6 (20) | 16.6 (0) | 18.6 (28) | 2.0 (1) | 8.3 (1) |
| parakeet-tdt-0-6b-v3, 1 s | 17.7 (0) | 11.3 (20) | 17.1 (0) | 17.9 (26) | – | – |

0.6 s recovers the first word at mic open as well as 0.8–1 s does. Longer
pre-rolls hurt Parakeet on AMI with a lead-in (18.6 at 0.8 s): with 0.3 s or
more of silence before an abrupt start, it returns nothing. AMI clips are
cut mid-word, so the harness's lead-in creates a jump from silence into
speech that a real mic never produces. Its own audio as pre-roll (the "mic
open" columns) is fine. Probed directly on the 10 clips that came back
empty at 0.8 s: with 0.1 s of silence first, all decoded; with 0.3–0.5 s,
6 went empty again (zeros did it too, so it isn't the synthetic noise).
Natural onsets after real silence (LibriSpeech, FLEURS, dictation) are
unaffected. Worth knowing for Parakeet's promotion (#7).

**Every engine, before (#14) → after (#15),** with the 0.5 s lead-in.
WER, then head_drop in brackets:

| Engine | LS clean | LS other | FLEURS en | AMI | Dictation |
|---|---|---|---|---|---|
| distil-small-en | 3.0 (8) → **2.6** (2) | 8.4 (2) → **7.4** (0) | 9.8 (4) → **9.3** (0) | 13.8 (22) → **14.3** (17) | 27.9 (3) → **24.6** (2) |
| whisper-tiny-en | 5.5 (8) → **5.0** (1) | 14.5 (3) → **14.3** (1) | 14.0 (4) → **13.6** (0) | 19.2 (22) → **19.1** (19) | 15.5 (2) → **14.1** (0) |
| whisper-base-en | 4.0 (6) → **4.1** (2) | 11.9 (3) → **11.4** (0) | 10.4 (3) → **10.0** (0) | 18.5 (21) → **15.8** (22) | 14.1 (3) → **12.7** (0) |
| whisper-small-en | 2.6 (8) → **2.0** (1) | 8.1 (3) → **7.2** (1) | 8.1 (3) → **7.7** (0) | 15.3 (21) → **14.6** (19) | 12.4 (1) → **10.5** (0) |
| whisper-turbo | 2.0 (6) → **1.7** (2) | 5.4 (2) → **5.2** (0) | 6.9 (3) → **6.7** (0) | 14.7 (25) → **14.4** (24) | 12.4 (3) → **9.1** (0) |
| parakeet-tdt-0-6b-v3 | 2.1 (5) → **1.6** (2) | 5.4 (1) → **5.0** (0) | 8.8 (5) → **8.3** (1) | 11.2 (25) → **12.4** (18) | 18.0 (1) → **16.9** (0) |
| canary-180m-flash | 2.3 (9) → **5.3** (3) | 5.9 (8) → **4.9** (3) | 8.1 (5) → **12.6** (0) | 15.8 (22) → **17.0** (17) | 18.5 (2) → **17.7** (0) |
| nemotron-3-5-streaming-320ms | 5.3 (37) → **2.7** (0) | 11.6 (39) → **8.3** (1) | 12.4 (29) → **10.4** (1) | 19.9 (67) → **14.7** (13) | 21.0 (13) → **15.7** (0) |
| nemotron-3-5-streaming-560ms | 4.5 (39) → **3.2** (0) | 10.5 (22) → **7.8** (0) | 11.7 (33) → **10.2** (0) | 18.7 (63) → **13.8** (17) | 22.4 (10) → **16.0** (0) |
| nemotron-en-streaming-560ms | 3.4 (26) → **2.0** (0) | 7.4 (27) → **6.1** (0) | 9.6 (41) → **8.4** (0) | 12.9 (57) → **10.4** (15) | 19.9 (14) → **14.1** (0) |
| qwen3-asr-0-6b | 2.2 (5) → **2.0** (2) | 6.9 (2) → **6.5** (0) | 7.7 (3) → **7.6** (1) | 11.6 (27) → **10.6** (17) | 15.7 (2) → **14.6** (0) |

Every engine gains a little on LibriSpeech and dictation, and Nemotron a
lot. Canary's LS-clean and FLEURS "regressions" are one letter loop each
(see engine choice above), not the pre-roll: which clip loops changes with
any change to the audio. Parakeet's AMI 11.2 → 12.4 is within noise and
partly the leading-silence effect. The remaining AMI head drops are the
clips' own tight cuts (the reference starts mid-word).

**Speech from the mic's first sample** (no lead-in), before → after:

| Engine | Dictation | AMI |
|---|---|---|
| distil-small-en | 37.0 (11) → **25.4** (2) | 16.3 (49) → **14.3** (24) |
| whisper-tiny-en | 20.2 (15) → **14.9** (1) | 22.9 (59) → **19.1** (29) |
| whisper-base-en | 17.7 (13) → **12.4** (1) | 20.1 (58) → **18.0** (28) |
| whisper-small-en | 15.7 (9) → **10.2** (1) | 18.0 (58) → **15.4** (25) |
| whisper-turbo | 14.9 (14) → **11.6** (1) | 16.8 (62) → **14.3** (30) |
| parakeet-tdt-0-6b-v3 | 24.9 (13) → **17.7** (1) | 17.6 (71) → **11.4** (26) |
| canary-180m-flash | 25.1 (18) → **18.2** (1) | 22.3 (63) → **16.2** (29) |
| nemotron-3-5-streaming-320ms | 27.1 (31) → **14.9** (0) | 23.1 (123) → **15.7** (21) |
| nemotron-3-5-streaming-560ms | 27.6 (26) → **14.9** (0) | 22.7 (127) → **13.7** (22) |
| nemotron-en-streaming-560ms | 26.0 (30) → **15.2** (0) | 17.7 (123) → **11.3** (21) |
| qwen3-asr-0-6b | 20.7 (9) → **15.7** (1) | 13.1 (54) → **10.8** (25) |

**Spanish, German, French and auto-detect,** before → after (WER):

| Engine | es | de | fr | es auto | de auto | fr auto | FLEURS en auto |
|---|---|---|---|---|---|---|---|
| whisper-turbo | 4.3 → **3.1** | 8.7 → **5.8** | 10.9 → **9.4** | 4.3 → **3.1** | 10.5 → **7.6** | 10.9 → **9.4** | 6.9 → **6.7** |
| parakeet-tdt-0-6b-v3 | 5.5 → **5.9** | 13.8 → **16.2** | 14.1 → **10.7** | – | – | – | – |
| canary-180m-flash | 8.9 → **8.0** | 9.7 → **8.4** | 12.2 → **11.7** | – | – | – | – |
| nemotron-3-5-streaming-320ms | 9.0 → **7.5** | 12.6 → **10.6** | 17.6 → **13.3** | 9.5 → **8.2** | 13.5 → **11.3** | 18.4 → **13.4** | 12.7 → **10.6** |
| nemotron-3-5-streaming-560ms | 9.3 → **7.9** | 13.1 → **10.1** | 17.8 → **13.8** | 9.9 → **7.5** | 12.9 → **10.7** | 18.0 → **14.7** | 11.6 → **10.3** |
| qwen3-asr-0-6b | 8.2 → **8.9** | 10.7 → **10.1** | 17.5 → **16.2** | 7.7 → **8.4** | 9.4 → **9.3** | 16.3 → **16.7** | 7.7 → **8.2** |

With 50 clips per language, moves under about 3 points (Parakeet's German,
Qwen3) are noise. Nemotron 3.5 gains 1.5–4.5 points everywhere.

### Quiet input: software AGC (#15)

The platform AGC may be missing (`AutomaticGainControl.isAvailable()`
false), and the VOICE_RECOGNITION source is calibrated flat. Dictation at
arm's length then arrives around −40 to −55 dBFS. FLEURS-en is recorded
that quietly (active-speech RMS median −57 dBFS; LibriSpeech −22, AMI −34),
so running it without the AGC stand-in measures that case. These runs came
before the final settings (offline pre-roll 0.8 s, Nemotron without the
silence padding). All three columns of a row share the settings, so they
compare with each other. Cells are WER / head_drop / segments still open
when the clip ended (of 200):

| Engine | Set | AGC stand-in | No AGC | No AGC + SoftAgc |
|---|---|---|---|---|
| nemotron-en-streaming-560ms | fleurs-en | 8.3 / 0 / 1 | 17.8 / 28 / 50 | 8.6 / 2 / 106 |
| nemotron-en-streaming-560ms | ami-ihm | 10.1 / 16 / 9 | 12.3 / 21 / 55 | 10.5 / 19 / 75 |
| distil-small-en | fleurs-en | 9.2 / 0 / 1 | 14.0 / 29 / 50 | 9.1 / 0 / 106 |
| distil-small-en | ami-ihm | 13.9 / 16 / 9 | 15.1 / 20 / 55 | 13.9 / 16 / 75 |
| parakeet-tdt-0-6b-v3 | fleurs-en | 8.3 / 1 / 1 | 16.1 / 32 / 50 | 8.3 / 2 / 106 |
| parakeet-tdt-0-6b-v3 | ami-ihm | 18.6 / 28 / 9 | 13.8 / 27 / 55 | 14.4 / 21 / 75 |
| parakeet-tdt-0-6b-v3, mic floor -85 dBFS | fleurs-en | – | 16.1 / 29 / 19 | 8.0 / 1 / 29 |

- Without any AGC, WER roughly doubles on FLEURS-en. All of it comes from
  clips below −40 dBFS (Nemotron EN: 8.3 → 20.9 on the 129 clips below
  −50 dBFS). Silero finds onsets late (head drops 0 → 28–32) and holds
  segments open.
- `SoftAgc` (what the app now runs when the platform AGC is off) brings WER
  back to the stand-in's level and removes the head drops. It adds no text
  on the noise clips.
- **Its cost is endpointing.** It lifts the mic's noise floor with the
  speech, and more segments stay open to the end of the clip. How many
  depends on the harness's synthetic floor: 50 → 106 with the usual
  −70 dBFS floor (loud next to FLEURS's −57 dBFS speech), 19 → 29 with a
  more realistic −85 dBFS floor. A 20 or 25 dB ceiling instead of 30 dB
  didn't change that. The real floor of a phone without a platform AGC
  decides it, so this needs an on-device check.
- AMI's Parakeet stand-in cell (18.6) carries the leading-silence effect
  above (it ran at a 0.8 s pre-roll).

## Dictation after text cleanup (#16)

The same raw dictation outputs, re-scored offline after the app's text
cleanup (`:textproc`, default settings) with number conversion off and on.
Raw WER, as in the table above: case and punctuation count. Reproduce with
`./gradlew :textproc:test --tests '*DictationWerTest*' --info`; the outputs
are in `textproc/src/test/resources/dictation-hyps.tsv`.

| Engine | Raw (#14) | Cleanup, digits off | Cleanup, digits on |
|---|---|---|---|
| distil-small-en | 38.7 | 38.3 | 36.8 |
| whisper-tiny-en | 27.9 | 27.0 | 26.4 |
| whisper-base-en | 24.2 | 23.0 | 22.7 |
| whisper-small-en | 17.5 | 16.9 | 16.6 |
| whisper-turbo | 23.0 | 23.3 | 22.7 |
| parakeet-tdt-0-6b-v3 | 39.0 | 39.3 | 29.4 |
| canary-180m-flash | 57.1 | 57.1 | 31.6 |
| nemotron-3-5-streaming-320ms | 67.8 | 67.8 | 47.5 |
| nemotron-3-5-streaming-560ms | 67.5 | 67.5 | 47.2 |
| nemotron-en-streaming-560ms | 72.4 | 72.4 | 50.3 |
| qwen3-asr-0-6b | 42.3 | 42.3 | 26.4 |

What is left is mostly not numbers: names, e-mail addresses, paths and
file names said as "dot" and "slash", Nemotron's missing first words and
final full stops, and the references writing small numbers as digits
("3 boxes", "at 8") where the cleanup keeps them as words on purpose.
Parakeet gains more than the Whisper engines because it writes some
numbers as words and glues "$" to the word before ("to$1800").

## Decoder loops (#18)

Engines sometimes repeat one letter, word or short block until their
length limit: Canary about once in 200 utterances ("E E E …", "T T T …",
"S E S E …"), Whisper base and tiny and Nemotron EN more rarely ("Get Get
Get …", "the the the …"). `:textproc` now cuts them (`Phantoms.cutLoops`,
part of the hallucination filter): a block of 1–3 words repeated 6 or more
times. The references never repeat a word more than 4 times, and digits
never count. It sits in `:textproc` rather than the Canary backend
because the other engines loop too and every Final passes through it.

The table re-scores the #15 results ("after") with the rule ported to
Python (a scratch script, not part of the harness, which scores the raw
engine output). It changes 10 of 14,312 hypotheses, all of them loops:

| Engine, set | WER before | with the loop cut |
|---|---|---|
| canary-180m-flash, LS clean | 5.3 | 2.3 |
| canary-180m-flash, FLEURS en | 12.6 | 9.2 |
| canary-180m-flash, AMI | 17.0 | 14.9 |
| canary-180m-flash, AMI, mic open | 16.2 | 14.1 |
| whisper-base-en, LS other | 11.4 | 11.2 |
| whisper-base-en, AMI, mic open | 18.0 | 17.9 |
| whisper-tiny-en, AMI | 19.1 | 19.2 |
| nemotron-en-streaming-560ms, AMI, mic open | 11.3 | 11.1 |

(Whisper tiny's "XL, X, X, X …" becomes "XL": one fewer word than the
loop happened to match.) The FLEURS loop leaves the letters before it
("R L L A D O N D O N …"): only the part that cycles is cut.

**Is it sherpa-onnx?** sherpa-onnx v1.13.8 (2026-09-10) is still the
latest release, and its master has no Canary decoding change since then
besides a language-table fix (#3962). v1.13.8 added the #3919 workaround:
when Canary's first token is end-of-text on audio with signal, it takes
the best other token instead, and logs it. A forced first token could
plausibly start a loop, so #18 decoded LS clean, LS other, FLEURS en and
AMI (800 clips, 853 decodes) and recorded per decode whether that log
line appeared. It appeared 12 times, all with normal text; the one loop
of that run (LS clean, "E E E …") had no forced token. So a bump doesn't
help, and the workaround doesn't cause the loops. Only one of the three
Canary loops the #15 run had on those sets came back, with the same audio
and settings: loops hang on tiny numerical differences (likely ONNX
Runtime's threaded maths, which isn't bit-exact from run to run).

## Threads and end-of-speech latency (#20)

The #11 Pixel 8a pass (distil-small-en, 2 threads) found a first partial
0.9–1.25 s after speech started and, for a 22.4 s utterance, the Final
14.7 s after the speech ended.

**2 vs 4 threads**, LS clean (200 clips), benchmark host, one job at a time:

| Engine | RTF, 2 threads | 4 threads | Speed-up | Hypotheses changed |
|---|---|---|---|---|
| distil-small-en | 0.081 | 0.064 | 1.26× | 0 of 200 |
| parakeet-tdt-0-6b-v3 | 0.032 | 0.021 | 1.49× | 0 of 200 |
| nemotron-en-streaming-560ms | 0.127 | 0.093 | 1.37× | 0 of 200 |

Whisper gains least: its decoder runs token by token and hardly spreads
over threads. The app now gives every recognizer the device's performance
cores, 2 to 4 (`RecognizerThreads`; 4 on the Pixel 8a, which has 1 X3 and
4 A715 besides its 4 A510). The phone's speed-up still needs measuring on
the phone.

**End of speech → Final** comes from `asrbench.latency`: the app's offline
loop with live partials on virtual time, where hops arrive in real time and
each decode takes what a cost model fitted to the #11 `SttTiming` logs says
(distil-small-en, 2 threads on the Pixel 8a: 0.78 s + 0.078 s per second of
audio + 0.007 s per second squared; the fixed part is Whisper's encoder,
which always runs a 30 s window). The model reproduces the phone's 1.15 s
first partial. Silero VAD is real, the default 0.9 s pause included.
LS clean, 14–15 clips per length, seconds, median (max):

| Clip length (median utterance) | Before #20 | After #20 | After, 4 threads (projected ×0.79) |
|---|---|---|---|
| 5 s (4.6 s) | 2.9 (3.9) | 2.4 (3.1) | 2.4 (3.0) |
| 10 s (9.0 s) | 3.5 (4.9) | 3.0 (4.0) | 2.8 (3.6) |
| 22 s (19.0 s) | 7.7 (12.7) | 5.5 (8.3) | 4.7 (7.7) |

Before #20 a partial re-decoding the whole utterance could run while, or
just before, the VAD closed the segment, and the Final waited behind it.
Now closed segments are decoded first, partials wait as long as the last
one took, and a partial decodes at most 6 s (6 s beat 8 and 10 s in the
same simulation). What remains for a long utterance is the pause plus the
Final's own decode of the whole utterance (about 5 s for 19 s at 2
threads). The first partial stays about 1 s at 2 threads (0.97 s
projected at 4): Whisper's fixed encoder cost alone rules out 0.5 s.


## Failure counters

Per set: empty output / runaway (hypothesis > 2× reference + 4 words) /
first word missing / last word missing. AMI segments are cut tight, so
every engine loses some edge words there. Noise: clips (of 6) with any text
through the app's VAD path / fed straight to the model.

| Engine | LS clean | LS other | FLEURS en | AMI | Dictation | Noise via VAD | Noise, no VAD |
|---|---|---|---|---|---|---|---|
| distil-small-en | 0/0/2/2 | 0/0/0/3 | 0/0/0/4 | 0/0/17/11 | 2/0/2/2 | 0 | 4 |
| whisper-tiny-en | 0/0/1/5 | 0/0/1/4 | 0/0/0/3 | 0/0/19/19 | 0/0/0/2 | 0 | 6 |
| whisper-base-en | 0/0/2/7 | 0/0/0/5 | 0/0/0/4 | 0/0/22/18 | 0/0/0/1 | 0 | 6 |
| whisper-small-en | 0/0/1/3 | 0/0/1/3 | 0/0/0/4 | 0/0/19/15 | 0/0/0/1 | 0 | 6 |
| whisper-turbo | 0/0/2/3 | 0/0/0/3 | 0/0/0/4 | 0/0/24/15 | 0/0/0/0 | 0 | 6 |
| parakeet-tdt-0-6b-v3 | 0/0/2/2 | 0/0/0/4 | 0/0/1/4 | 3/0/18/19 | 0/0/0/0 | 0 | 0 |
| canary-180m-flash | 0/1/3/2 | 0/0/3/2 | 0/1/0/4 | 0/1/17/8 | 0/0/0/0 | 0 | 3 |
| nemotron-3-5-streaming-320ms | 0/0/0/4 | 0/0/1/5 | 0/0/1/3 | 1/0/13/24 | 0/0/0/1 | 0 | 0 |
| nemotron-3-5-streaming-560ms | 0/0/0/4 | 0/0/0/4 | 0/0/0/4 | 1/0/17/25 | 0/0/0/2 | 0 | 0 |
| nemotron-en-streaming-560ms | 0/0/0/5 | 0/0/0/6 | 0/0/0/5 | 0/0/15/25 | 0/0/0/0 | 0 | 0 |
| qwen3-asr-0-6b | 0/0/2/2 | 0/0/0/3 | 0/0/1/2 | 0/0/17/9 | 0/0/0/1 | 0 | 6 |
| parakeet-tdt-0-6b-v3 (#25) | 1/0/3/3 | 0/0/0/4 | 0/0/1/3 | 2/0/15/18 | 0/0/0/1 | 0 | 0 |
| parakeet-tdt-110m-en (#25) | 0/0/2/2 | 0/0/0/3 | 0/0/0/4 | 0/0/18/12 | 0/0/0/1 | 0 | 0 |
| parakeet-tdt-0-6b-v2 (#25) | 0/0/2/1 | 0/0/0/3 | 0/0/1/2 | 0/0/11/6 | 0/0/0/0 | 0 | 0 |
| parakeet-unified-en-0-6b (#25) | 0/0/2/1 | 0/0/0/5 | 0/0/1/3 | 0/0/11/12 | 0/0/0/1 | 0 | 0 |
| parakeet-ultra (#25) | 0/0/2/2 | 0/0/0/2 | 0/0/0/5 | 0/0/12/11 | 0/0/0/1 | 0 | 0 |
| parakeet-unified-en-streaming-560ms (#25, 50 per set) | 0/0/0/1 | 1/0/2/3 | 1/0/2/2 | 3/0/6/7 | 1/0/2/2 | 0 | 0 |

What the models say to noise when fed it directly (no VAD), a sample:

| Engine | silence | pink noise −30 dBFS | clicks | mains hum |
|---|---|---|---|---|
| distil-small-en | "you" | – | "[?..." | "you" |
| whisper-tiny-en | "[ Silence ]" | "(wind howling)" | "[Music]" | "(" |
| whisper-base-en | "(buzzing)" | "(engine revving)" | "(tapping)" | "( ( ( ( ( …" |
| whisper-small-en | "you" | "(water rushing)" | "(clicking)" | "[Music]" |
| whisper-turbo | "so" | "so" | "Thank you." | "you" |
| canary-180m-flash | – | – | "." | "T S F" |
| qwen3-asr-0-6b | "The system." | "system" | "Okay." | "The system is now running." |

Parakeet (every export, #25 included) and all three Nemotrons returned
nothing on every clip.

## Dictation formatting, three examples

Synthetic speech, from the #15 run. The reference is what a user would
want typed. (In #14 both Nemotrons lost the leading "Send".)

| | "Send the invoice to maria.lopez@example.com by Friday." | "Schedule the dentist for Tuesday, April 7th at 2 PM." | "Push the branch feature/login-fix to origin." |
|---|---|---|---|
| distil-small-en | …to Maria dot Lopez at example dot com… | …Tuesday April 7th at 2 p.m. | …feature slash login dash fix… |
| whisper-small-en | …to Maria.Lopez@example.com… | …Tuesday, April 7th at 2 p.m. | …feature /login-fix… |
| whisper-turbo | …to Maria.Lopez@example.com… | …Tuesday, April 7 at 2 p.m. | …feature slash login dash fix… (all lower case) |
| parakeet-tdt-0-6b-v3 | …to maria.lopez at example.com… | …Tuesday, April 7th at 2 p.m. | …feature slash login dash fix… |
| canary-180m-flash | …to Maria. Lopez at example. com… | …Tuesday, april seventh at two PM. | …feature slash login dash fix… |
| nemotron-3-5-streaming-560ms | …to Maria Lopez at example.com… | …Tuesday, April seventh at two PM | …feature slash login dash fix… |
| nemotron-en-streaming-560ms | …to Maria dot Lopez at example dot com… | …Tuesday, April seventh at two pm | …feature slash login fix… |
| qwen3-asr-0-6b | …to maria dot lopes at example dot com… | …Tuesday, April 7th at 2 p.m. | …feature slash login dash fix… |

The TTS audio has its own flaws. Every engine heard "$8,249.99" where the
script said "one thousand two hundred…", so Kitten mispronounced it.


## #25: Parakeet leaderboard picks and the German gap

### The German gap

The Open ASR Leaderboard scores Parakeet v3 at 4.16 on FLEURS German; this
benchmark had 16.2. Whisper turbo showed no such gap (3.67 vs 5.8). The bad
clips weren't mistranscribed German: partway through a sentence the model
switched to English and stayed there ("Denken Sie daran, dass…" came out as
"Think that Festival Bis spawned in the Night Music…", and "Die Feuchtigkeit
auf Ihren Händen…" as "The Feuchtigkeit of Earn Hand reaged…"). Parakeet v3
takes no language hint; it decides from the audio as it goes.

Same 50 clips, WER %:

| Decode | int8 (the app's files) | fp32 export |
|---|---|---|
| App pipeline, as before #25 | 16.2 ±7.1 | 5.9 ±1.8 |
| App pipeline, speech from the mic's first sample | 16.3 ±7.6 | – |
| Whole clip, no VAD | 7.0 ±2.0 | – |
| Whole clip, no VAD, no gain (as the leaderboard feeds it) | 7.0 ±2.0 | 5.5 ±1.8 |
| Whole clip, high-passed | 6.7 ±1.7 | – |
| App pipeline, each Final + 0.3 s of zeros (the app now) | **7.4** | – |

So the int8 export is fragile and the pipeline set it off, but not through
the lead-in, the gain or the high-pass. The cause was the end of the audio:
a Final is the pre-roll plus Silero's segment, and the segment ends where the
speech ends. Given no silence after the last word, the int8 model loses its
language. The fp32 export doesn't (5.9 through the same pipeline).
Checking the issue's other hypotheses: there is no language hint to give
Parakeet v3; the normaliser and the 50-clip subset can't explain a
factor of four once the whole clip scores 7.0. What remains of the gap to the
leaderboard's 4.16 is the int8 export (5.5 → 7.0 on whole clips), the
normaliser and the subset.

**Tail padding sweep** (Parakeet v3 int8 unless noted; WER, then empty
results for AMI). Zeros are appended to each Final's audio:

| Set | none | 0.2 s | 0.3 s | 0.5 s | 1 s |
|---|---|---|---|---|---|
| FLEURS de | 16.2 | 7.9 | **7.4** | 7.6 | 7.5 |
| FLEURS es | 5.9 | 5.0 | **4.5** | 4.3 | 4.1 |
| FLEURS fr | 10.7 | 10.2 | **9.6** | 9.7 | 13.5 (1 empty) |
| FLEURS en | 8.3 | 8.0 | **8.0** | 8.2 | 7.8 |
| AMI | 12.4 / 3 | 12.5 / 1 | **12.4 / 2** | 14.6 / 9 | 15.0 / 9 |
| Canary, FLEURS de | 8.4 | 8.1 | 7.4 | 7.4 | 8.6 |
| Canary, AMI | 17.0 | 13.3 | 13.5 | 13.5 | 21.2 |

A second pass (none / 0.15 / 0.2 / 0.25 / 0.3 s) left LibriSpeech clean
(1.6–1.9), other (4.9–5.1) and dictation (16.6–17.7) within noise; one short
LibriSpeech clip came back empty at 0.15, 0.2 and 0.3 s but not at 0.25 s.
From 0.5 s trailing silence empties AMI Finals, the mirror image of the
leading-silence failure (#15). The app now pads every Parakeet decode with
0.3 s (`NEMO_TAIL_PADDING_SAMPLES`), live partials included. Canary isn't
padded: its AMI gain (17.0 → 13.5) is probably its loops (#18), which
`:textproc` already cuts, and needs its own check.

### Parakeet TDT_CTC 110M: which head

k2-fsa exports NVIDIA's `parakeet-tdt_ctc-110m` twice: its TDT (transducer)
head and its CTC head. The TDT head wins every set at the same speed, so the
app ships it:

| Head | LS clean | LS other | FLEURS en | AMI | Dictation (norm) | Size |
|---|---|---|---|---|---|---|
| TDT (shipped) | **1.9** | **5.2** | **8.6** | **12.0** | **17.1** | 136 MB |
| CTC | 2.5 | 6.2 | 9.9 | 12.9 | 19.1 | 132 MB |

k2-fsa publishes these int8 files only as tarballs in the sherpa-onnx
`asr-models` GitHub release (its HuggingFace repos of the same name are
empty). The catalog downloads them from a third-party HuggingFace mirror
whose files are byte-identical to the extracted tarball (sha256 checked,
#25), so the app can install them file by file like every other engine.

### Leading silence (#18 regression check)

`asrbench.regress`, empty results of the 16 clips, lead-in 0.5 s / 1 s:

| Engine | No tail padding | 0.3 s tail padding |
|---|---|---|
| parakeet-tdt-0-6b-v3 | 2 / 3 | 1 / 2 |
| parakeet-tdt-110m-en | 0 / 0 | 0 / 0 |
| parakeet-tdt-0-6b-v2 | 0 / 0 | 0 / 0 |
| parakeet-unified-en-0-6b | 0 / 0 | 0 / 0 |
| parakeet-ultra | 0 / 0 | 0 / 0 |
| canary-180m-flash | 0 / 0 | 0 / 0 (not padded in the app) |

Only v3 has the problem. The streaming Unified export isn't in the check
(it runs the streaming session, with its 1 s pre-roll).

### Noise

Through the VAD, and fed straight to the model, none of the new engines
(Ultra included) produced any text on the six noise clips.

### Parakeet Ultra

Moondream's [Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra)
is a post-trained Parakeet v3 published only as a transformers checkpoint
for Moondream's closed Photon runtime. `tools/parakeet-ultra/convert.py`
loads NVIDIA's v3 `.nemo` in NeMo, copies in all 651 of Ultra's learned
weights (every one differs from v3's) and exports and quantizes the model
the way k2-fsa exported v3. Two runs gave byte-identical files. Ultra's
voice-activity head has no place in the transducer and is left out.

**Checked against Moondream's own numbers.** Moondream reports Ultra against
v3 on the leaderboard's full sets (GPU, fp16/bf16). Here both run through the
app's pipeline on this benchmark's subsets, int8:

| Set | Moondream: v3 → Ultra | Here: v3 → Ultra (int8) | Here: Ultra fp32 |
|---|---|---|---|
| LibriSpeech clean | 1.52 → 1.41 (−7 %) | 1.7 → 1.5 (−12 %) | – |
| LibriSpeech other | 3.13 → 2.98 (−5 %) | 5.1 → 4.5 (−12 %) | 3.9 |
| AMI | 10.86 → 9.77 (−10 %) | 12.4 → 9.1 (−27 %) | 8.6 |
| FLEURS en | 4.25 → 3.55 (−16 %) | 8.0 → 6.9 (−14 %) | – |
| FLEURS de | 4.13 → 3.61 (−13 %) | 7.4 → 6.5 (−12 %) | 5.1 |
| FLEURS es | 3.12 → 2.72 (−13 %) | 4.5 → 3.8 (−16 %) | 3.5 |
| FLEURS fr | 4.81 → 4.32 (−10 %) | 9.6 → 7.9 (−18 %) | 6.9 |

Ultra improves on v3 on every set, by about the margins Moondream reports,
so the conversion carries the post-training over. Part of the AMI gain is
v3's empty Finals, which Ultra doesn't have. The absolute numbers differ
from Moondream's for the same reasons v3's do (subsets, the app's pipeline,
the normaliser). int8 costs Ultra 0.3–1.4 points against its fp32 export,
consistently in one direction but within noise on each 50-clip set.

Ultra's int8 export doesn't drift out of German without the tail padding
(FLEURS de 5.9 without it, 6.5 with; es 3.6 and 3.8): the padding neither
helps nor measurably hurts it. It returned text on every clip of the #18
leading-silence check, with and without the padding.

## Method

- **Host:** AMD Ryzen AI Max+ 395 (16 cores), CPU only, ONNX Runtime
  1.28.2, sherpa-onnx v1.13.8 built from source with TTS off. Each engine
  used 2 threads, like the app. Three engines ran at a time at `nice 10`.
  #14 made two full runs. The first had no lead-in; it only feeds the
  lead-in comparison and the RTF column, because it had the machine to
  itself. The second, with the lead-in, is the "before" of #15. #15's full
  run (the app after the fix) gives the main tables' WER. #14's second run
  and #15's shared the machine with other load, so their timings aren't
  used. #25 ran the four new engines and Parakeet v3 (with the tail padding)
  two or three jobs at a time next to another benchmark's jobs; its RTFs are
  scaled by v3's (see the English table).
- **Engines:** the catalog's int8 files at their pinned revisions, checked
  by sha256; configs as in `SherpaBackends.kt` (greedy search, Whisper tail
  padding 1000, 0.3 s of zeros after each Parakeet decode since #25, Canary
  with punctuation, Qwen3 defaults).
- **Pipeline:** the app's session logic ported to Python. Each clip gets
  one gain as a stand-in for the platform AGC (RMS −25 dBFS, at most +30 dB,
  peak ≤ −1 dBFS). It then goes through the 100 Hz high-pass in 100 ms hops,
  then Silero VAD (threshold 0.5, min speech 0.25 s, min silence 0.9 s,
  max 28 s). Offline engines decode 0.6 s of pre-roll before Silero's onset
  followed by each VAD segment (`StreamingSession`). Nemotron gets one
  OnlineRecognizer stream per utterance, started 1 s before the onset, fed
  hop by hop and flushed at the segment end (`OnlineSession`). (Before #15:
  offline engines decoded the bare segment, and Nemotron's pre-roll was
  0.4 s.)
  After each clip come 1 s of −70 dBFS noise and a stop. The transcript is
  the Finals joined with spaces.
- **Language hint:** engines that take one get the test set's language, as
  for a user who picked it. The "auto" columns send none, which is the app's
  default for Whisper turbo, Nemotron 3.5 and Qwen3. Parakeet always
  detects the language itself. Canary has no auto mode.
- **Data** (all CC-BY-4.0, fetched without an account, seeded subsets):
  - LibriSpeech test-clean and test-other, 200 utterances each (read
    audiobooks).
  - FLEURS en_us test, 200 (read Wikipedia sentences; many speakers and
    mics).
  - AMI headset-mic test, 200 utterances of ≥ 2 s and ≥ 4 words
    (spontaneous meeting speech with many non-native accents).
  - FLEURS es_419, de_de and fr_fr test, 50 each.
  - **Dictation:** 40 sentences packed with names, numbers, dates, times,
    money, file names, emails and URLs, spoken by Kitten TTS from a
    spelled-out script. It is synthetic, so treat it as a formatting check
    and not as accuracy truth.
  - **Noise:** six 10 s clips with no speech: silence, white and pink
    noise, keyboard-like clicks and mains hum.
- **Common Voice is missing.** Its official download now needs a Mozilla
  Data Collective account. The `mozilla-foundation` sets are gone from
  HuggingFace, and the ungated mirrors re-host it against its download
  terms. FLEURS-en and AMI stand in for accent and mic variety.
- **Scoring:** corpus WER. English uses Whisper's English normaliser (case,
  punctuation, number formats, spelling variants, fillers). es, de and fr use
  its basic normaliser (case and punctuation). Raw WER compares words as
  typed, so case and punctuation count. ± is the half-width of a 95 %
  bootstrap interval over utterances. RTF is decode time divided by audio
  time, over every hinted run; it excludes model load.
