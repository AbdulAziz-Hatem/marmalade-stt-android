# Speed audit on the Pixel 6a: where the time goes, and what hides it (#91), October 2026

**Status: four changes shipped (2026-10-22/23), on by default; the rest is paused.** See "Shipped" below. Every
other prototype stays behind a debug-only knob (`SpeedKnobs`), and the instrumentation is debug-level logging.
"Next", at the end, is the planned order for when the work resumes.

## Shipped (2026-10-22)

Pixel 6a on the charger, debug build of the shipped code, interleaved A/B per trial (A: the change off, B: on),
medians with ranges, 2026-10-21 18:29–18:49 and 2026-10-22 22:36–22:46 (31–39 °C). Raw data:
`2026-10-speed-audit/shipped-6a.tsv`.

| Change | Engine, surface | Before → after | n per arm |
|---|---|---|---|
| **Speculative Final** at a 0.6 s early silence (`EarlyVad.TARGET_SAMPLES`) | Ultra, Dictate (5/10/20 s clips) | end → text **2166 → 1599 ms** (5 s: 1852 → 1075 and 1992 → 1378; 10 s: 2294 → 1758; 20 s: 2757 → 2017) | 8 |
| | Ultra, popup (Smart Turn Normal) | **2052 → 1624** | 6 |
| | 110M, Dictate | 1470 → 1188 | 8 |
| | 110M, popup | 1668 → 1566 | 6 |
| **Trim at stop** (with the 0.6 s early VAD) | Ultra, keyboard (tap ~0.9 s after the speech) | stop → text **744 → 482** (296–829) | 5 |
| | 110M, keyboard | stop → text 304 → 152 | 5 |
| **First partial at the VAD onset** | Ultra, Dictate | speech → first partial **835 → 544** | 6 (§4) |

- **Every B turn used its speculation** (38 of 38, keyboard included), and none wasted a decode: these LibriSpeech clips have no
  mid-sentence pause of 0.6 s or more. On real speech, the host sim's figure stands: at 0.6 s, 1–5 wasted decodes
  per minute of speech (+11–41 % decode work), against 4–10 (+37–83 %) at 0.4 s (§1).
- **Parity:** with `verifySpeculation` each Final was also decoded the usual way: 10 of 10 identical (Ultra, 5
  Dictate + 5 popup), no "speculation missed". (Three more verify turns didn't speculate: the pass driver's next
  tap stopped them before the early VAD closed. Driver timing, not the session.)
- **Trim at 0.6 vs 0.4 s:** at 0.4 s trim gave Ultra 781 → 133 ms (§3), because the speculation finished before the
  tap; at 0.6 s it finishes about when the tap comes, so the stop often waits for it (482 ms, 296–829). One early
  silence serves both, as #91's head asked; a shorter one for Tap stop alone is a one-line change, at the cost of
  more wasted decodes during keyboard pauses.
- **Pre-emption (#90) and the RecognitionService path:** another app's session runs the same `StreamingSession`
  through `SpeechEngineManager`, and pre-emption cancels it: the session's canceller stops the native decode in
  progress (speculative or not), `transcribe` refuses to start another, and the speculation is the session's local
  state. `SessionArbiterTest`'s `a pre-empted session drops its speculative decode` checks it with a real
  `SherpaSttEngine`: the app's session ends BUSY with no Final, its one decode cancelled.

Sources of ideas: my own list (Phase 1 report, issue comment 8043), Fable's independent audit
(`2026-10-speed-ideas-fable.md`, "Fable #n" below) and the TTS app's speed playbook (issue comment 7994).

## Results in one table

End of speech → text on screen, medians, Pixel 6a on the charger, interleaved A/B on the same clips (5 s LibriSpeech
sentences unless noted). "Ship?" is my recommendation; nothing ships without the head's (and for memory, Max's) call.

| # | Idea (source) | Before → after | Accuracy | Cost | Ship? |
|---|---|---|---|---|---|
| 1 | **Speculative Final**: decode when an early VAD (0.4 s) sees the silence, use it when the real VAD (1.2 s) closes the same range (Fable #1, mine a) | Ultra Dictate **1780 → 1277 ms**, Ultra popup **1950 → 1504**, 110M Dictate 1442 → 1311, 110M popup 1592 → 1504, turbo Dictate 6322 → 5780 | Identical by construction; re-decoded and compared on 12 Ultra turns: 12/12 same | A second Silero (+300 ms CPU per 5 s session, ~2 % of a core). Mid-sentence pauses ≥ 0.4 s waste a decode (host sim: 4–10 per minute of speech, +40–83 % decode work); at a 0.6 s early silence 1–5 per minute (+16–41 %) | **Yes, at a ~0.6 s early silence**, after a 0.6 s A/B on the phone (§1) |
| 2 | No partials once the silence starts (Fable #2a, bug a) | Comes with #1. In-flight partial at the Final: Ultra Dictate worst case 404 → 108 ms | None | Saves 1–2 partial decodes per utterance end | With #1 |
| 3 | **Trim at stop**: Tap stop sends the speculative text (speech without the silence up to the tap) (mine) | Ultra keyboard stop → text **781 → 133 ms** (end → text 1694 → 1114) | Host paired WER: never worse; better on AMI (Ultra −0.63) and ls-other (110M −0.34) | None beyond #1 | **Yes**, with #1 |
| 4 | First partial at the VAD onset, not 200 ms later (Fable #4a, bug b) | Ultra speech → first partial **835 → 544 ms** | None | +1 partial per utterance (+240 ms CPU, Ultra) | **Yes** |
| 5 | Smart Turn early end at p > 0.95/0.98 at 0.5 s (Fable #7) | Median 1.27 → 0.58 s on read speech (host) | **Cut-offs 3–10× Normal's** in every set and silence | +0.5–2.5 model checks per clip | **No** (§5) |
| 6 | Smart Turn check cost: features vs model, threads (mine d, Fable #7 last paragraph) | A check is 212 ms warm (Phase 1), 325–420 ms in a fresh process; log-mel 107–168 ms of it; 4 threads instead of 2: model −15 % | None | – | Incremental log-mel: **yes, next** (§6) |
| 7 | Threads and pinning in the top-app cpuset (Fable #5, mine e) | 4 threads unpinned 564 ms; pinned 576, 3 threads 594, 5 threads 630 (Final decode) | None | – | **No change** |
| 8 | Timed warm hold after a session (mine b, Fable #10) | First partial **2885 → 745 ms** (keyboard, Keep engine ready on) | None | Memory: §8 | Max decides (§8) |

## Setup

- Pixel 6a: Tensor G1, 8 cores (0–3 A55, 4–5 A76, 6–7 X1). 4 recognizer threads. All three surfaces run in the
  `/top-app` cpuset (all 8 cores). USB, on the charger, screen held on.
- A debug build of `child/91-speed-audit`, installed as `app.marmalade.stt.speedaudit` (`-PdebugIdSuffix`).
- **A clip-fed mic** (`app/src/debug/.../ClipMic.kt`): every listen's mic plays a WAV in real time, then digital zeros,
  so trials are silent and identical. The real mic still opens and closes. Clips are LibriSpeech test-clean
  sentences trimmed to their speech, with a 300 ms lead.
- Driver: `tools/speed-audit/pass.py` (keyboard via `SpeechTestActivity` + our IME and a tap on its mic,
  then back to FUTO; the speak-now popup via `RecognizeSpeechActivity`; Dictate via its mic button).
  `--ab "A|B"` alternates knob sets per trial, so heat drift falls on both arms equally.
  Summariser: `tools/speed-audit/summarize.py` (pairs the debug log stamps per trial).
- Heat: the phone on the charger with the screen on sat at 36–40 °C (thermal status 1) through Phase 2. It would
  not cool below 33 °C in 10 minutes. That's why every Phase 2 comparison is interleaved, not before/after.
- Default settings: Keep engine ready off; keyboard = Tap to stop; popup = Smart Turn Normal; Dictate = Pause 1.2 s.

## Phase 1: the timing breakdown

10 turns per engine × surface, 2026-10-20 20:47–21:59. Raw data: `2026-10-speed-audit/phase1-6a.tsv`.

**Medians, ms.** "Speech→partial" runs from the clip's first speech to the first partial on screen. "End→text" runs
from the clip's last speech to the surface having applied the Final. For Tap stop, "stop→text" runs from the stop tap.

| Engine | Surface | Mic open | Model load | Speech→VAD onset | Speech→1st partial | Partials (n × decode) | Silence wait | Smart Turn check | Final decode (audio) | Cleanup+commit | End→text (stop→text) |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Ultra | keyboard | 90 | **2643** (every open) | 2321 | **2885** | 4 × 462 | – (tap ~0.87 s after the end) | – | 524 (5.9 s) | 9 | 1513 (**640**) |
| Ultra | keyboard, Keep on | 113 | 0 | 357 | **745** | 7 × 368 | – | – | 571 (5.9 s) | 9 | 1671 (824) |
| Ultra | popup | 32 | **2777** | 2418 | **3016** | 4 × 484 | **1232** | **212** (146–287) | 434 (5.2 s) | 6 | **1906** |
| Ultra | Dictate (preloaded) | 96 | 0 | 367 | 828 | 8 × 314 | **1232** | – | 468 (5.2 s) | 4 | **1848** |
| Ultra | Dictate, 10 s | 112 | 0 | 360 | 761 | 14 × 436 | 1200 | – | 915 (10.5 s) | 28 | 2385 |
| Ultra | Dictate, 20 s | 80 | 0 | 764 | 1230 | 23 × 419 | 1232 | – | 1732 (19 s) | 28 | 2539 |
| 110M | keyboard | 90 | 826 | 548 | 850 | 13 × 130 | – | – | 223 (5.9 s) | 8 | 1222 (**308**) |
| 110M | popup | 34 | 755 | 444 | 751 | 14 × 138 | 1232 | 183 | 149 (5.2 s) | 6 | 1637 |
| 110M | Dictate | 84 | 0 | 363 | 745 | 14 × 130 | 1232 | – | 160 (5.2 s) | 11 | 1417 |
| turbo | keyboard | 150 | 2063 | 1752 | – (no partials) | – | – | – | **5732** (6.0 s) | 34 | 6744 (**5848**) |
| turbo | popup | 33 | 2140 | 1819 | – | – | 1232 | 226 | **5115** (5.2 s) | 66 | **6605** |
| turbo | Dictate | 82 | 0 | 366 | – | – | 1232 | – | **5020** (5.2 s) | 16 | **6232** |

Other stages:
- **VAD (Silero):** 140–330 ms of CPU per 5 s session (2–3 % of a core); the worst 32 ms window took 6–12 ms.
- **Cold process** (force-stop, then the keyboard): the same as a warm process. The model load is the whole cost.
- **Real mic open** (no clip): 83–126 ms in the keyboard, 32 ms in the popup (debug build; #78's release figures
  were 35–63 ms). Not a lever.
- **Cleanup + commit, and delivery:** 4–66 ms.

**Where the milliseconds go (Ultra, a 5 s sentence):**
1. **Waiting, not computing**, is most of end→text in the auto modes. Dictate: 1.23 s of VAD silence + 0.47 s
   decode. Popup: 1.23 s + 0.21 s Smart Turn + 0.43 s. The decode doesn't have to wait for the silence (§1).
2. **Model load on every keyboard and popup open:** 2.6–2.8 s for Ultra, 2.1 s turbo, 0.8 s 110M. With the engine
   warm the first partial comes 0.75 s after speech starts instead of 2.9 s: the biggest gap in time to first text.
3. **Keyboard stop→text, 0.64 s**, is all compute: the decode includes the ~0.9 s of silence before the tap.
4. **The Smart Turn check costs 150–290 ms**, not "tens of ms", and it sits on the end path before the decode.
5. **First partial:** the VAD onset comes ~0.36 s after speech starts, then the 200 ms partial gate, then the decode.
6. **Partials cost a lot of CPU:** Ultra decodes 1.7–2.7 s per 5 s sentence, and partials go on through the trailing
   silence.
7. **Turbo is real time or worse on the 6a** (RTF ≈ 1.0). Only a model change (#55's ACFT turbo) moves this much.

### Correction to the Phase 1 report: a partial in flight does delay the Final, sometimes

Phase 1's report said partials "don't delay the Final (the VAD drains first)". That is true of the order within one
hop, not of a partial already running: the session has one consumer thread, so a hop that closes the segment waits
behind a partial decode in progress. Per turn, the Final's start lagged the mic by (median, worst):

| Engine, surface | Turns with a partial in flight at the close | Extra wait |
|---|---|---|
| Ultra Dictate, 5 s | 4 of 10 | +142, +311, +334, +410 ms |
| Ultra Dictate, 10 s | 2 of 3 | +209, +245 ms |
| Ultra Dictate, 20 s | 0 of 3 | – |
| 110M Dictate | 0 of 10 (partials ~130 ms) | – |

So Fable's bug (a) is real for Ultra on the 6a: 0 or one partial, about 0.12 s on average. §1 removes most of it.

## Fable's four "plain latency bugs"

| Bug | Verdict on the 6a |
|---|---|
| (a) Partials keep decoding through the silence, and the Final can wait behind one | **Yes.** Partials run until the segment closes (`isSpeechStarted` stays true); stopping them at the early silence saves 250–550 ms of partial CPU per Ultra sentence. The Final waited behind one in 4 of 10 Ultra Dictate turns, +140 to +410 ms; never for 110M. Fixed by #1 (worst 404 → 108 ms) |
| (b) The first partial waits 200 ms after the onset for no reason | **Yes**, by the code: `lastPartialAt` is set at the onset. Measured gain in §4 |
| (c) Nemotron's fixed 1.32 s tail padding | **Not measured**: Nemotron isn't one of the three engines in this audit; it needs the host's `tail_drop` + WER first (Next) |
| (d) One-shot results and the keyboard's switch-back wait for the flow's end | **No, negligible.** Final sent → the listen ends: 15–104 ms in the popup, 2–13 ms in the keyboard |

## Where my list and Fable's disagree

- **The Final waiting behind a partial** (Fable #2): Fable estimated 0.5 s on the 6a for Ultra. Measured: in 4 of
  10 turns, +0.14 to +0.41 s; median 0, mean 0.12 s.
- **Cancellable NeMo decodes** (Fable #2b): not needed for the end-of-speech case once #1 stops partials at the early
  silence (worst backlog 108 ms). It still matters for a speculative decode wasted on a mid-sentence pause (§1), so it
  stays on the list, lower.
- **The Smart Turn check** (Fable #7's footnote guessed 50–100 ms for the log-mel): the whole check is 212 ms warm and
  the log-mel alone 107–168 ms in a fresh process. Fable put it last; I rank it right after #1 for the popup.
- **Model load** (Fable #10): Fable's "draft engine during the load" and my timed warm hold aim at the same 2.1 s gap.
  The hold is simpler and measured here (§8); the draft engine is untried.
- **Smart Turn early end** (Fable #7): Fable's idea; the host sweep rejects it (§5).

## Phase 2

### 1. Speculative Final (Fable #1, my (a))

`StreamingSession` gets a second Silero (`earlyVad`) whose minimum silence is the main one's minus whole VAD windows,
about 0.4 s (`EarlyVad.minSilenceSeconds`). Silero ends a segment where its silence began, counted in whole windows,
so both detectors end the utterance at the same sample unless speech came back in between. When the early VAD closes
a segment while the main one is still in it, the session decodes exactly the audio the main VAD's Final will decode
(Pause: pre-roll + segment; Smart Turn: the turn from its start) and keeps `(from, to, text)`. When the main VAD's
Final covers the same `(from, to)`, that text is the Final; anything else is decoded as usual. No partials are
decoded after the early VAD's close until it hears speech again. Knob: `speculativeFinal`. Tests:
`SpeculativeFinalTest` (6).

Interleaved A/B, 10 turns per arm, 2026-10-20 22:12–22:40 (36–40 °C):

| Engine, surface | End → text, A → B | Final decode, A → B | Speculation done after the speech's end | Partial CPU per session, A → B | VAD CPU, A → B |
|---|---|---|---|---|---|
| Ultra, Dictate | **1780 → 1277** | 467 → 0 | 1030 (820–1340) | 2638 → 2388 | 338 → 631 |
| Ultra, popup | **1950 → 1504** | 480 → 0 | 994 | 1790 → 1242 | 168 → 246 |
| 110M, Dictate | 1442 → 1311 | 171 → 0 | 594 | 1936 → 1618 | 330 → 697 |
| 110M, popup | 1592 → 1504 | 140 → 0 | 594 | 1972 → 1668 | 239 → 411 |
| turbo, Dictate (n = 4) | 6322 → 5780 | 5140 → 0 | 5780 | – | 636 → 1124 |

- **Hits:** every B turn in every engine and surface (56 of 56). On the host (1,040 clips of six sets, Silero only,
  `early_vad_sim.py`) the early VAD ended a segment where the main one did at 1,057 of 1,082 utterance ends
  (98 %); the rest fall back to the usual decode.
- **Parity:** with `verifySpeculation` the session also decodes the Final the usual way and compares: 12 of 12 same
  (6 Dictate + 6 popup, Ultra). Decodes are deterministic (#53), so this checks the range arithmetic.
- **What's left in the popup** is the 1.2 s silence plus the Smart Turn check (229 ms), which now runs while nothing
  else is decoding. In Dictate it's the silence plus ~45 ms.
- **CPU:** the second VAD adds ~300 ms per 5 s session; the partials it saves in the silence take off about the same.
- **Misses on mid-sentence pauses** are the real cost. A pause of 0.4–1.2 s inside an utterance closes the early VAD
  but not the main one, and that decode is thrown away (NeMo decodes can't be cancelled, so it also delays the next
  partial by up to one decode). The clips here have no such pauses. On the host (Silero only, the app's mic emulation):

  | Set | Speech | Misses per minute of speech at an early silence of 0.40 / 0.62 / 0.82 s | Wasted decode audio as a share of the Finals' audio |
  |---|---|---|---|
  | ls-clean | 24.9 min | 4.6 / 1.6 / 0.4 | 57 % / 22 % / 6 % |
  | ls-other | 19.4 min | 5.3 / 1.6 / 0.5 | 41 % / 11 % / 2 % |
  | ami-ihm | 16.4 min | 7.2 / 3.7 / 1.9 | 37 % / 18 % / 10 % |
  | ami-long | 34.0 min | 9.7 / 5.0 / 2.1 | 83 % / 41 % / 17 % |
  | fleurs-en | 33.9 min | 3.7 / 1.0 / 0.3 | 49 % / 12 % / 3 % |
  | dictation (TTS) | 2.8 min | 0 / 0 / 0 | 0 |

  Hits stay at 197–221 per set at every early silence. The trade: the speculation starts later. With Ultra's 5 s
  decode at ~0.47 s, a 0.62 s early silence still finishes by ~1.2 s, so it hides nearly all of a 5 s sentence's
  decode for a third of the waste. **Recommendation: ship with `TARGET_SAMPLES` ≈ 0.6 s**, after one 0.6 s phone A/B
  and an energy check on long dictation with pauses.

### 3. Trim at stop (mine)

With Tap stop (the keyboard's default), today's Final decodes the speech plus all the silence up to the tap. With
`trimAtStop` (needs #1), a tap during the early VAD's silence, with nothing but near-silence since, sends the
speculative text: the speech as a Pause Final would have it.

Ultra, keyboard, interleaved A/B, 6 turns per arm, the tap ~0.93 s after the speech's end (22:54–22:58):

| | Stop → text | End of speech → text | Final decode |
|---|---|---|---|
| A (today) | **781** (678–1231) | 1694 | 621 ms for 5.9 s of audio |
| B (`speculativeFinal` + `trimAtStop`) | **133** (126–165) | 1114 | 0 (speculation done 868 ms after the speech's end, before the tap) |

The remaining 133 ms is the stop itself: the mic's last hop, the VAD flush and the commit. (Batch 3 ran the same A/B
with `verifySpeculation`, which re-decodes the untrimmed audio before sending, so its stop → text isn't a measure of
B; its texts are: trimmed vs untrimmed differed in 0 of 6 Ultra turns and 4 of 6 110M turns.)

**Accuracy (host, paired):** trimmed is the Final a Pause close decodes, untrimmed is the Final a stop 1.0 s into the
silence decodes (the harness default, `--tail-silence 1.0` vs `2.0`; `results-91` vs `results-91-tail2`). Word error
rate, 200 utterances per set, difference with its 95 % paired-bootstrap interval:

| Engine | ls-clean | ls-other | ami-ihm | fleurs-en |
|---|---|---|---|---|
| Ultra | 1.43 → 1.36 (−0.07, −0.20…0.00) | 4.61 → 4.57 (−0.03, −0.20…+0.12) | 9.09 → **8.45** (−0.63, −1.11…−0.18) | 6.88 → 6.86 (−0.02, −0.13…+0.07) |
| 110M | 1.82 → 1.82 (0.00) | 5.47 → **5.13** (−0.34, −0.65…−0.09) | 12.26 → 12.26 (0.00, −0.53…+0.52) | 8.56 → 8.54 (−0.02) |

Trimming is never worse and is better on AMI (Ultra) and ls-other (110M): the silence before the tap only gives the
model room to hallucinate. **Recommendation: ship with #1.** The harness's silence is −70 dBFS noise; the phone's
was digital zeros.

### 4. First partial at the onset (Fable #4a, bug b)

Knob `partialAtOnset`: the first partial's gate opens at the VAD onset instead of 200 ms after it. Ultra, Dictate,
interleaved A/B, 6 turns per arm (22:48–22:51):

| | Speech → first partial | Partials per sentence | Partial CPU per session | End → text |
|---|---|---|---|---|
| A (today) | **835** (773–998) | 7 | 2787 ms | 1859 |
| B (at the onset) | **544** (537–770) | 8 | 3026 ms | 1862 |

The first partial comes 0.29 s sooner, more than the 200 ms gate: the earlier partial also decodes less audio. It
costs one more partial decode per utterance (+240 ms CPU for Ultra) and changes nothing at the end. With #1 the
partials in the silence go away, which more than pays for it. **Recommendation: ship** (a one-line change in
`StreamingSession`; the knob already does it).

### 5. Smart Turn early end at very high confidence (Fable #7): rejected on the host

`asrbench.turn_defaults` (#64's sets and silence models), a new two-stage mode `smart-p0.5-e<p>-n1.2-t0.7-c2.5`: Silero
proposes at 0.5 s, the model ends the turn there only above `e`, else Normal's rule from 1.2 s on. Clips cut off,
room silence (n70/n90/zero agree):

| Set | Normal | e0.95 | e0.98 | Quick | Median latency Normal → e0.95 |
|---|---|---|---|---|---|
| ami-ihm | 1.0 % | 6.5 % | 4.5 % | 14.5 % | 1.30 → 1.28 s |
| ami-long | 4.0 % | 20.5 % | 12.5 % | 34.5 % | 2.31 → 1.85 s |
| fleurs-en | 1.2 % | 7.4 % | 4.3 % | 9.8 % | 1.27 → 0.58 s |
| fleurs-ja | 12.7 % | 33.7 % | 30.7 % | 41.0 % | 1.26 → 0.56 s |
| vox-it | 3.0 % | 13.0 % | 6.0 % | 18.0 % | 1.26 → 0.56 s |

Checks per clip rise from 1.1–2.2 to 1.5–4.8. At e0.99 almost nothing ends early (under 0.1 s saved). Even a check
that never ends early (`e1.0`) cuts more than Normal on AMI and Japanese: proposing at 0.5 s makes Silero split
pauses of 0.5–1.2 s, and a short sound after the split doesn't restart the silence count. Normal's numbers reproduce
#64's. Results on the benchmark host: `results-91-turn/`. **Verdict: no.** #1 already makes Smart Turn's wait free
of decode time; the remaining wait is the silence rule itself.

### 6. The Smart Turn check's own cost (mine d)

`OnnxTurnDetector` now logs features vs model per check (debug level). Ultra popup, a fresh process per trial,
6 turns per arm:

| Smart Turn threads | Whole check | Log-mel features | Model |
|---|---|---|---|
| 2 (default) | 325–420 ms | 107–168 ms | 217–250 ms |
| 4 | 296–386 ms | 106–153 ms | 188–232 ms |

Warm (Phase 1, process already running): 146–287 ms, median 212. The features are `WhisperFeatures.logMel` over the
last 8 s, recomputed from scratch at every check in Kotlin (debug build: not R8-optimised). Computing the mel frames
incrementally per hop (Fable's footnote) would take them off the end path almost entirely. More threads buy ~15 %
of the model step: not worth the contention with the recognizer.

### 7. Threads and pinning in the top-app cpuset (Fable #5, mine e)

Fable's point: every thread and pinning result so far (#31, #34) came from the benchmark service's cpuset (no X1/
prime core). Phase 1 showed the keyboard, popup and Dictate all run in `/top-app` (cores 0–7) on the 6a. Knobs
`threads` and `tuning` (an `OrtTuning` id), the model reloaded every trial (`pass.py --release`), Ultra, Dictate,
6 turns per arm, 22:51–22:57 (40.6–40.8 °C):

| Arm | Final decode (5.2 s of audio) | End → text |
|---|---|---|
| 4 threads (today's count) | 564 (483–617) | 1886 |
| 4 threads pinned to the fast cores 4–7 (`cpu-pin`) | 576 (547–628) | 1914 |
| 3 threads | 594 (549–659) | 1946 |
| 5 threads | 630 (501–663) | 2034 |

No arm beats today's 4 unpinned threads; the differences are inside the spread. The Final here follows a freshly
loaded model (two partials), so absolute numbers are ~20 % above the warm ones in Phase 1. **Verdict: keep 4
threads, unpinned**, on the 6a. The 8a in front (its X3 is top-app only) is still unmeasured.

### 8. A timed warm hold (mine b, Fable #10)

With Keep engine ready off (the default), the keyboard and the popup load the model on every open: Ultra 2.6–2.8 s,
turbo 2.1 s, 110M 0.8 s (Phase 1). With the engine already loaded, the keyboard's first partial comes 0.75 s after
speech starts instead of 2.9 s. A timed hold would keep the model for a while after a session instead of freeing it at
once. That is a memory and battery trade-off, so Max picks the numbers; these are the facts for it.

**Measured on the 6a (6 GB RAM)**, the app's process idle in the background for 5 minutes, 23:00–23:22:

| Engine held | Process PSS, held | Released | CPU while held idle, 5 min |
|---|---|---|---|
| Ultra | **858 MB** | 174–186 MB | 80 ms (7 + 1 ticks): nothing runs |
| 110M | 315 MB | – | 90 ms |
| turbo | **1,178 MB** | – | 90 ms |

- **Battery:** a held model is memory, not work. No thread runs while it's held (≈ 0.03 % of a core), so the battery
  cost is nil; the cost is RAM other apps can't have.
- **Low-memory killer:** with Ultra held and FUTO as the current keyboard (so our process is cached, oom adj 700 →
  920 as apps open), I opened Settings, Clock, Calculator, Camera and Organic Maps on top. The process survived;
  MemAvailable fell from 1.96 GB to 0.73 GB with the camera open. With Marmalade as the current keyboard the process
  is bound (adj 200) and survives far longer. A heavier app (a game, a long video call) would kill a cached process
  first, and then the next open loads from scratch, as today. A light test, not a stress test.
- **What "held" buys:** the model load, every open. On the keyboard that's 2.1 s of the 2.9 s to first text for Ultra.

**Options for Max** (all keep today's behaviour as the fallback: if Android kills the process, the next open loads):

1. **5 minutes after the last use, on phones with ≥ 6 GB** (recommended). Covers the "dictate a few messages in a row"
   pattern, which is where the load hurts most; Ultra's 860 MB goes back to the system within 5 minutes.
2. **1 minute, any phone.** Covers a quick follow-up correction only; smallest memory footprint.
3. **15 minutes, phones with ≥ 8 GB** (the 8a class). Closer to "always ready" for heavy users; turbo would hold
   1.2 GB that long.

Keep engine ready (forever) stays as the opt-in it is today. Not shipped: Max decides.

## Not tried, and why

| Idea | Source | Why not (yet) |
|---|---|---|
| Progressive Final (decode long utterances in pieces) | Fable #3 | Needs host paired WER first (piece boundaries lose context). Only long utterances gain: Ultra 20 s end→text is 2.5 s, of which 1.7 s decode. Next |
| Smaller mic hops (32/50 ms) | Fable #4b | Saves ≤ 50 ms per stage on average; lower than everything above |
| Nemotron pad-to-window | Fable #6, bug c | Not one of this audit's engines; host `tail_drop` + WER first. Next |
| Turbo token streaming | Fable #8 | A sherpa patch; perceived speed only. #1 already starts turbo's decode ~0.7 s sooner |
| int8 kernel paths, QDQ | Fable #9, TTS playbook | TTS found static QDQ no faster and XNNPACK slower; simpleperf first. Low |
| Draft engine during the load | Fable #10 | 400 MB resident for a draft; the warm hold (§8) attacks the same gap more simply |
| Process start before `startListening` | Fable #10 | Measured: a cold process costs the same as a warm one; the load is the whole cost |
| Spinning off, XNNPACK, arena off, prepacking off | TTS playbook | Already measured and rejected for STT (`2026-10-pixel8a-ort.md`, `2026-10-ort-load/`) |
| Cancel the in-flight ONNX run | TTS playbook, Fable #2b | See "Where my list and Fable's disagree"; for misses of #1 only |
| Thermal gating at ≤ 33 °C | TTS playbook | The 6a doesn't cool on the charger with the screen on; interleaved A/B instead |
| The 6a's foreground cpuset (0–5) | TTS playbook | Not the case for STT: the keyboard, popup and Dictate all ran in `/top-app` (0–7) |
| RecognitionService surface | – | Waits for #90, which rewrites that path |

## Shipped: the warm hold (2026-10-23)

Max's pick from §8: five minutes after the last use, on phones of 6 GB or more (`WarmHold`: `totalMem` ≥ 5 GiB;
the 6a reports 5.47 GiB). Freed early by `onTrimMemory` at a pressure level (not UI_HIDDEN or BACKGROUND) and by
picking another engine. Pixel 6a, Parakeet Ultra, keyboard (Keep engine ready off), 2026-10-23 00:07–00:18, debug
build. Cold: the model released just before the open; warm: the next open, 5 s after the previous one ended.

| Keyboard open | Open → ready | Open → first partial | Speech → first partial | n |
|---|---|---|---|---|
| Cold (today on smaller phones) | 2956 ms (2846–3009) | 3272 ms (3143–3412) | 2772 ms | 6 |
| Warm (inside the hold) | **103 ms** (100–211) | **1220 ms** (1164–1292) | **734 ms** | 5 |

Process PSS through the hold's life (the clip's turn ended at 00:10:48):

| When | PSS |
|---|---|
| 30 s after the last session | 953 MB |
| After `am send-trim-memory … BACKGROUND`, then `UI_HIDDEN` | 933, 931 MB (kept, as designed) |
| 4 min 50 s after | 930 MB |
| 5 min 20 s after (hold over) | **137 MB** |
| Held again, then `am send-trim-memory … COMPLETE` | 891 → **130 MB** |
| Held again, then 110M picked (no explicit release) | 892 → **137 MB** |

The process sat at oom adj 700 throughout (FUTO the current keyboard). Idle CPU while held was nil in §8. The hold's
timer counts awake time only: with the phone asleep nothing runs to free the model, and the low-memory killer may end
the cached process instead.

## Next (planned order)

Shipped: the speculative Final (0.6 s), trim at stop and the first partial at the onset (2026-10-22); the warm hold
(2026-10-23).

1. **Incremental Smart Turn log-mel** (§6), then re-measure the popup's end→text. Not cheap: Whisper's log-mel clamps
   against the whole 8 s window's maximum and its edge frames depend on where the window starts, so cached frames
   need the clamp redone per check and a parity test against `WhisperFeatures.logMel` (Smart Turn flips on a 32 ms
   shift, #64).
2. An energy check of the speculative Final on long dictation with real pauses (the phone clips had none).
3. Whether Tap stop alone should use a shorter early silence (Max to decide; "Shipped" above).
4. Progressive Final for long utterances (host WER first); Nemotron pad-to-window (host first).
5. The service surface's own timing (#90 has merged).

## Reproduce

```
./gradlew :app:assembleFdroidDebug -PdebugIdSuffix=.speedaudit
P=app.marmalade.stt.speedaudit; S=$P/app.marmalade.stt.debug.SpeedBenchmarkService
adb shell am start-foreground-service -n $S --es cmd use --es engine parakeet-ultra --ez keep false
adb push ls05-1.wav /data/local/tmp/   # pass.py arms the clip per trial
PKG=$P python3 tools/speed-audit/pass.py out parakeet-ultra dictate 20 --ab "none|speculativeFinal=true" --pos "<taps>"
FIXED_MIC=1 python3 tools/speed-audit/summarize.py out
```

Raw data in `2026-10-speed-audit/`: `phase1-6a.tsv`, `phase2-spec-ab-6a.tsv` (§1, §3 with verify),
`phase2-followups-6a.tsv` (§3, §4, §6, §7), `phase2-hold-6a.txt` (§8), `early-vad-sim.tsv` (§1), thermal logs.
Host results on the benchmark host: `results-91`, `results-91-tail2` (§3), `results-91-turn` (§5).

Knobs (`files/debug-mic/knobs`, set by the service's `use --es knobs a=b,…`): `speculativeFinal`, `trimAtStop`,
`partialAtOnset`, `verifySpeculation`, `threads`, `tuning` (an `OrtTuning` id), `smartTurnThreads`.
