# End of dictation: Pause vs Smart Turn on AMI, September 2026 (#43)

How do the app's two ways of ending a dictation compare on spontaneous
speech with pauses inside sentences?

- **Pause**: Silero VAD closes the utterance after a set silence (the
  settings slider, 0.3–1.5 s; default 0.9 s when this was measured, 1.2 s
  after the fix round).
- **Smart Turn**: a 0.2 s pause proposes the end and pipecat's smart-turn
  v3.2 model decides. p > 0.5 ends the turn; otherwise the model is asked
  again every 0.5 s of further silence, and 3 s of silence ends the turn
  regardless. #43 first made this the default; after these results it is
  the default only for one-shot requests (see "Patience (fix round)").

Harness: `tools/asr-bench/asrbench/turn.py` (README: "End of dictation").
Nothing in the app changed.

## Summary

- **With the proposed parameters, Smart Turn cuts people off mid-sentence
  about twice as often as today's Pause 0.9 s.** On 200 long AMI turns
  (8–26 s, one sentence each) it ended 54 % of them before the speaker's
  last word, against 28 % for Pause 0.9 s. It ended the turn at 32 % of
  mid-sentence pauses, against 13 %. That includes a third of the short
  pauses (under 0.5 s) that Pause 0.9 s never cuts.
- **Its typical end is much faster, but its slow end is much slower.**
  Median end-of-speech → turn end 0.29 s (Pause 0.9 s: 0.96 s), but p90
  3.06 s (Pause: 1.00 s). The model misses about 20 % of finished sentences
  (28 of the 142 long turns that end a sentence), and those
  wait for the 3 s ceiling.
- **In multi-sentence dictation it ends the turn at most sentence ends.**
  On 200 turns of two or more sentences it ended at 72 % of the pauses
  between sentences (Pause 0.9 s: 30 %). That is what the model is for (a
  finished sentence is a finished conversational turn), but in dictation it
  is a cut-off.
- **Tuning helps but does not beat a plain pause.** The best setting tried,
  propose 0.5 s / threshold 0.7 / ceiling 2 s, cuts about as often as Pause
  0.9 s (30 % vs 28 % of long turns), with a median of 0.59 s and a p90 of
  2.06 s. Pause 1.2 s cuts less than half as often as either (12 %) at a
  steady 1.25 s.
- **Recommendation:** don't ship Smart Turn as the default with 0.2 s /
  0.5 / 3 s. Details below.
- **Approved design (fix round):** Pause 1.2 s ends Dictate commits and is
  the slider default. Smart Turn is the default only for one-shot requests
  (the speak-now popup, other apps' SpeechRecognizer) and an optional
  auto-stop for the voice keyboard, whose default is Tap to stop. Smart
  Turn has a Patience setting; the sweep picked Quick = propose 0.5 s /
  threshold 0.7 / ceiling 1.5 s, Normal = 0.5 s / 0.7 / 2 s, Relaxed =
  1.2 s / 0.7 / 2.5 s. Relaxed cuts off fewer turns than Pause 1.2 s on all
  three sets (4 % vs 12 % of long turns).

## Setup

- Host: the benchmark host (Ryzen AI Max+ 395), CPU only, `nice 19`. smart-turn ran
  on ONNX Runtime with 4 intra-op threads, Parakeet v3 (for WER) with 2.
- The harness's usual mic emulation: one gain per clip (the platform AGC's
  stand-in), 0.5 s of −70 dBFS noise before the clip and 5 s after it,
  100 ms hops, the app's 100 Hz high-pass, the app's Silero (threshold 0.5,
  min speech 0.25 s), 0.6 s offline pre-roll. Time is virtual: hop i arrives
  at (i + 1) × 100 ms, and each model check adds its measured host time.
- Smart Turn is simulated as the #43 design says: Silero with a 0.2 s
  minimum silence; when a segment closes, the model scores the last 8 s of
  the turn so far (from the first onset's pre-roll to now, earlier segments
  and the trailing silence included). p above the threshold ends the turn.
  If speech resumes the turn goes on; if silence goes on it re-checks every
  0.5 s; at 3 s of silence (counted from Silero's end of speech) the turn
  ends. A turn reaching 28 s ends at its last segment boundary (not reached
  in these sets). Each turn is decoded once.
- The model path is checked against marmalade-stt-cli's detector: the same
  probabilities on fox.wav (for example 0.5295 for the mid-word cut plus
  0.2 s of zeros, in both). `whisper_features.py` matches `transformers`'
  `WhisperFeatureExtractor` to within 1.3e-6.

### Data

AMI meeting corpus, headset mics, test split (CC-BY-4.0). Every AMI segment
is one sentence: of the 400 single-segment clips, 305 end in a full stop,
question mark or exclamation mark in the manual transcript, and only one
has a sentence end inside. So the pauses inside a clip are pauses inside a
sentence.

| Set | Clips | Length | Pauses inside |
|---|---|---|---|
| `ami-ihm` (the harness's AMI set) | 200 segments, ≥ 2 s | median 4.3 s | 180, all mid-sentence |
| `ami-long` (new) | 200 segments, ≥ 8 s | median 9.5 s | 535, all mid-sentence |
| `ami-multi` (new) | 200 turns of 2+ sentences, 8–20 s | | 768: 535 mid-sentence, 233 between sentences |

`ami-multi` rebuilds real multi-sentence turns: about 40 % of a speaker's
consecutive segments are contiguous in the corpus (the next starts where
the last ended), so joining a run of them gives that speaker's actual
audio across the sentence ends.

### Definitions

Ground truth is AMI's manual annotation (release 1.6.2): word start times
and the transcribers' punctuation. The annotated word end times can't be
used: the annotation stretches each word over the pause after it.

- **Pause**: a gap between two consecutive Silero segments at 0.2 s minimum
  silence (the reference pass; every pause the Smart Turn gate sees),
  between the clip's first and last word. Its length is Silero's gap.
  **Mid-sentence** unless the word before it ends in `.`, `?` or `!` in the
  transcript.
- **False cut-off**: the mode ends the turn before the start of the clip's
  last annotated word. **Clips cut off** counts clips with at least one;
  **pauses cut** charges each cut-off to the pause it happened in (the
  latest pause that began before it, allowing 0.35 s after the pause for
  Silero to confirm the next onset), per pause.
- **Latency**: from the end of the last speech (the end of the reference
  pass's Silero segment that holds the last word) to the first turn end
  after the last word started. It includes the model's check time.
- **Ended by ceiling**: the share of those final turn ends that came from
  the 3 s (or 2 s) ceiling rather than the model.
- **WER**: Parakeet v3 decodes each turn once; all of a clip's turns are
  joined and scored (Whisper's English normaliser, 95 % bootstrap ±). It
  prices the accuracy lost to splitting, not the words a real cut-off
  would lose.

## Results

Smart Turn modes: `smart` is the proposed default (propose 0.2 s, recheck
0.5 s, threshold 0.5, ceiling 3 s); suffixes change one or more of them (`p`
propose, `t` threshold, `c` ceiling). Recheck stayed at 0.5 s.

### Long single-sentence turns (`ami-long`)

| Mode | Clips cut off | Mid-sentence pauses cut | Latency median s | p90 s | Ended by ceiling | WER % |
|---|---|---|---|---|---|---|
| Pause 0.5 s | 78 % | 51 % | 0.54 | 0.58 | – | 13.7 ± 2.1 |
| **Pause 0.9 s (slider default then)** | **28 %** | **13 %** | **0.96** | **1.00** | – | **8.9 ± 1.5** |
| Pause 1.2 s | 12 % | 5 % | 1.25 | 1.28 | – | 7.5 ± 1.1 |
| Pause 1.5 s | 4 % | 2 % | 1.53 | 1.58 | – | 7.8 ± 1.3 |
| **smart (proposed default)** | **54 %** | **32 %** | **0.29** | **3.06** | **30 %** | **11.8 ± 2.1** |
| smart t0.3 | 62 % | 39 % | 0.29 | 3.05 | 20 % | 12.5 ± 2.1 |
| smart t0.7 | 45 % | 26 % | 0.74 | 3.06 | 36 % | 11.1 ± 2.0 |
| smart p0.3 | 46 % | 26 % | 0.39 | 3.06 | 28 % | 10.6 ± 1.8 |
| smart p0.5 | 36 % | 16 % | 0.58 | 3.05 | 30 % | 10.0 ± 1.7 |
| smart p0.3 t0.7 | 41 % | 22 % | 0.62 | 3.06 | 33 % | 10.0 ± 1.6 |
| smart p0.5 t0.7 | 30 % | 13 % | 0.59 | 3.06 | 36 % | 9.5 ± 1.6 |
| smart c2 | 54 % | 32 % | 0.29 | 2.06 | 35 % | 11.8 ± 2.1 |
| smart p0.5 t0.7 c2 | 30 % | 14 % | 0.59 | 2.06 | 40 % | 9.6 ± 1.7 |

Pauses cut by length, same set (288 pauses under 0.5 s, 188 of 0.5–1 s,
56 of 1–2 s, 3 of 2 s or more):

| Mode | < 0.5 s | 0.5–1 s | 1–2 s | ≥ 2 s |
|---|---|---|---|---|
| Pause 0.9 s | 0 % | 14 % | 73 % | 2 of 3 |
| Pause 1.2 s | 0 % | 0 % | 41 % | 2 of 3 |
| smart | 30 % | 32 % | 34 % | 3 of 3 |
| smart p0.5 t0.7 c2 | 3 % | 26 % | 27 % | 2 of 3 |

The model's verdict barely depends on how long the pause is: it calls
about a third of mid-sentence pauses finished whether they last 0.3 s or
1.5 s. A one-off look at the saved probabilities agrees: at the first
check (0.2 s of silence) p was above 0.5 at about 42 % of the mid-sentence
pauses it saw and at about 70 % of finished sentences (`ami-ihm` and
`ami-long`).

### Turns of two or more sentences (`ami-multi`)

| Mode | Clips cut off | Mid-sentence pauses cut | Sentence-end pauses cut | Latency median s | p90 s | WER % |
|---|---|---|---|---|---|---|
| Pause 0.9 s | 48 % | 12 % | 30 % | 0.96 | 1.00 | 12.2 ± 2.0 |
| Pause 1.2 s | 25 % | 5 % | 17 % | 1.24 | 1.29 | 12.0 ± 2.0 |
| Pause 1.5 s | 16 % | 3 % | 9 % | 1.53 | 1.58 | 11.0 ± 1.8 |
| smart | 76 % | 35 % | 72 % | 0.30 | 3.07 | 15.2 ± 2.2 |
| smart p0.5 t0.7 c2 | 55 % | 13 % | 50 % | 1.08 | 2.07 | 12.7 ± 2.1 |

### Short turns (`ami-ihm`, the harness's usual AMI set)

| Mode | Clips cut off | Mid-sentence pauses cut | Latency median s | p90 s | WER % |
|---|---|---|---|---|---|
| Pause 0.9 s | 14 % | 16 % | 0.96 | 1.00 | 12.0 ± 2.5 |
| Pause 1.2 s | 5 % | 5 % | 1.25 | 1.29 | 10.9 ± 2.0 |
| smart | 29 % | 36 % | 0.29 | 3.04 | 16.2 ± 3.0 |
| smart p0.5 t0.7 c2 | 13 % | 14 % | 0.59 | 2.06 | 13.2 ± 2.6 |

Pause 0.9 s's 12.0 here matches the host WER run's 12.4 for Parakeet v3 on
this set (`2026-09-host-wer.md`).

### Model cost

One check (8 s window) on the benchmark host, median of 50: the numpy features take
1.7 ms, the model 24 / 13 / 8 / 6 ms at 1 / 2 / 4 / 6 threads (26 / 15 / 10
/ 8 ms in total). Smart Turn made about 4–9 checks per clip. The phone is
unmeasured and the harness has no Pixel cost model for this network. If the
Pixel 8a is 2–3 times slower than the benchmark host at the same thread count, as it
was for distil-small.en (#11, #20), a check takes about 30–80 ms there. That
adds under 0.1 s to a turn end, so it doesn't change the comparison.

The full per-mode tables (every pause-length bucket, every set) print with
`python -m asrbench.turn --work $WORK --rescore ami-ihm ami-long ami-multi`;
per-clip turns are in `$WORK/results-43/turn/` on the benchmark host.

## Recommendation

**The proposed parameters (propose 0.2 s, recheck 0.5 s, threshold 0.5,
ceiling 3 s) are not right for dictation.** They come from pipecat's voice
agents, where ending a turn early costs little (the user speaks again and
the agent adjusts). In dictation an early end cuts a sentence in two. On
this data they double the cut-offs of today's default and make one
finished sentence in five wait 3 s.

What I'd change, in order of effect:

1. **Propose at 0.5 s, not 0.2 s.** Half of all mid-sentence pauses are
   shorter than 0.5 s, and the model calls a third of them finished. With
   Silero at 0.5 s they never reach the model. This alone takes long turns
   from 54 % to 36 % cut off.
2. **Threshold 0.7.** A further drop, to 30 %.
3. **Ceiling 2 s.** Pauses of 2 s or more are rare (3 of 535 long-turn
   pauses) and cut anyway, so this costs nothing measurable and takes the
   slow end from 3.06 s to 2.06 s.
4. Recheck at 0.5 s: not varied here; no reason to change it.

With 0.5 s / 0.7 / 2 s, Smart Turn cuts about as often as Pause 0.9 s, ends
a typical turn faster (0.6 s vs 0.96 s), and ends the slowest tenth slower
(2.1 s vs 1.0 s). It still ends at half of the pauses between sentences.

**Even tuned it doesn't beat a plain pause on AMI.** For the default, I
recommend Pause, at 1.2 s rather than 0.9 s: less than half the cut-offs of
either (12 % vs 28–30 % of long turns, 17 % vs 30–50 % of sentence ends in
multi-sentence turns) for about 0.3 s more wait, and a WER among the best. Smart
Turn fits short one-sentence input (a search, a chat reply), where ending
at the first finished sentence is what the user wants; offer it as an
option with the tuned parameters. If Smart Turn stays the default, use the
tuned parameters.

**Approved (fix round):** Max chose a default per context, in line with
the above:

- **Dictate** commits a sentence at a pause: Pause, and the slider's
  default moves from 0.9 s to 1.2 s.
- **One-shot requests** (the speak-now popup and other apps'
  SpeechRecognizer): Smart Turn is the default. A request is usually one
  sentence, so ending at the first finished sentence is what the user wants.
- **Voice keyboard:** Tap to stop is the default; Smart Turn is an optional
  auto-stop.
- **Smart Turn gets a Patience setting** (Quick / Normal / Relaxed). Normal
  is the tuned setting above; the sweep below picked the other two.

## Patience (fix round)

Each Patience level is a (propose, threshold, ceiling) triple; the recheck
stays at 0.5 s. Normal was fixed in advance at 0.5 s / 0.7 / 2 s. Quick
should end sooner and may cut off somewhat more. Relaxed should cut off
less, aiming at Pause 1.2 s's 12 % of long turns or better; Max dislikes
false cut-offs, so both lean patient.

Same harness, sets, definitions and host as above. 20 candidates in three
rounds; the Pause and Normal rows were rerun alongside them and match the
tables above. Two columns are new: **sentence-final** latency is the
median over clips whose last word ends a sentence (`.`, `?`, `!`), and
**mean** is over all clips. For Smart Turn the two medians differ because a
clip that doesn't end a sentence usually waits for the ceiling.

### Chosen

| Patience | Propose | Threshold | Ceiling |
|---|---|---|---|
| Quick | 0.5 s | 0.7 | 1.5 s |
| Normal | 0.5 s | 0.7 | 2 s |
| Relaxed | 1.2 s | 0.7 | 2.5 s |

The threshold is the same for all three; only when the model is first
asked (propose) and how long an unsure turn waits (ceiling) change.

- **Quick: Normal with a 1.5 s ceiling.** No Quick candidate got down to
  Pause 0.9 s's cut-offs (28 % of long turns); Normal itself is at 30 %.
  Of the two ways to be quicker, the ceiling is the cheaper one:
  - About 35–50 % of final turn ends come from the ceiling, because the
    model doesn't call the sentence finished. A 1.5 s ceiling takes the
    mean end-of-speech → turn end on long turns from 1.20 to 1.01 s
    (Pause 0.9 s: 0.99 s) and the p90 from 2.06 to 1.56 s. Clips cut off
    rise from 30 to 36 % (long), 55 to 60 % (multi-sentence) and 13 to
    16 % (short). A finished sentence still ends at a median 0.59 s.
  - Proposing earlier saves 0.1–0.2 s on a finished sentence but lets
    pauses under 0.5 s reach the model, which calls some of them finished:
    at the same 1.5 s ceiling, long turns cut off go from 36 % (propose
    0.5 s) to 38–44 % (0.4 s) and 45–47 % (0.3 s), and sentence ends cut
    in multi-sentence turns from 53 % to 55–70 %.
  - Threshold 0.6 instead of 0.7 saves about 0.04 s of mean and adds up
    to three points of cut-offs.
  - If Quick should also end finished sentences sooner, the next step is
    propose 0.4 s / 0.7 / 1.5 s: median 0.49 s, 40 % / 64 % / 16 % of
    clips cut off.
- **Relaxed: propose at 1.2 s, ceiling 2.5 s.** Proposing at 1.2 s means
  Smart Turn only looks at pauses that Pause 1.2 s would end the turn at,
  and holds the turn when the sentence doesn't sound finished. Against
  Pause 1.2 s: clips cut off 4 % vs 12 % (long), 15 % vs 25 %
  (multi-sentence; sentence ends cut 11 % vs 17 %), 1 % vs 5 % (short). A
  finished sentence ends after a median 1.30 s (Pause 1.2 s: 1.25 s); the
  price is the wait when the model is unsure: p90 2.57–2.58 s and mean
  1.85–1.95 s, against 1.28–1.29 s and 1.28–1.30 s. Its WER is among the
  best (7.4 / 12.3 / 10.8 % on long / multi-sentence / short; Pause 1.2 s:
  7.5 / 12.0 / 10.9 %), within the confidence intervals.
  - Propose 0.7–0.9 s left 7–15 % of long turns and 22–36 % of
    multi-sentence turns cut off: the model still called some 0.5–1 s
    mid-sentence pauses finished.
  - Propose 1.0 s: about the same cut-offs on long and short turns (4 %,
    2 % vs 1 %), more on multi-sentence turns (19–20 % vs 15 %); finished
    sentences on the single-sentence sets end 0.2 s sooner (1.10 s vs
    1.30 s).
  - Ceiling 3 s instead of 2.5 s: three clips fewer cut off in
    multi-sentence turns (14 % vs 15 %), none on the other sets, and the
    p90 goes from 2.57 to 3.07 s.
  - Threshold: 0.6 (at 2.5 s) cuts off one or two more clips on the
    multi-sentence and short sets. 0.8 (tried at 3 s, against 0.7 at 3 s)
    cuts off within one clip of 0.7 and holds unfinished-sounding ends
    longer (median over all long clips 2.73 s vs 2.23 s).
  - Against Pause 1.5 s, Relaxed cuts off about as often (4 / 15 / 1 % vs
    4 / 16 / 2 %), ends finished sentences sooner (1.30 s vs 1.53 s) and
    unsure ones later (p90 2.57 s vs 1.58 s; mean on long turns 1.95 s vs
    1.57 s).

### Sweep: long single-sentence turns (`ami-long`)

Mode names: `p` propose (s), `t` threshold, `c` ceiling (s).

| Mode | Clips cut off | Mid-sentence pauses cut | Latency median s (all / sentence-final) | p90 s | mean s | Ended by ceiling | WER % |
|---|---|---|---|---|---|---|---|
| Pause 0.9 s | 28 % | 13 % | 0.96 / 0.96 | 1.00 | 0.99 | – | 8.9 ± 1.5 |
| Pause 1.2 s | 12 % | 5 % | 1.25 / 1.25 | 1.28 | 1.28 | – | 7.5 ± 1.1 |
| Pause 1.5 s | 4 % | 2 % | 1.53 / 1.53 | 1.58 | 1.57 | – | 7.8 ± 1.3 |
| smart p0.5 t0.7 c2 (**Normal**) | 30 % | 14 % | 0.59 / 0.58 | 2.06 | 1.20 | 40 % | 9.6 ± 1.7 |
| smart p0.5 t0.7 c1.5 (**Quick**) | 36 % | 17 % | 0.59 / 0.58 | 1.56 | 1.01 | 46 % | 10.7 ± 1.9 |
| smart p0.5 t0.6 c1.5 | 37 % | 18 % | 0.59 / 0.57 | 1.56 | 0.97 | 40 % | 10.7 ± 1.8 |
| smart p0.4 t0.8 c1.5 | 38 % | 21 % | 0.98 / 0.49 | 1.56 | 1.00 | 45 % | 11.2 ± 2.0 |
| smart p0.4 t0.7 c1.5 | 40 % | 22 % | 0.89 / 0.49 | 1.56 | 0.96 | 40 % | 11.1 ± 1.9 |
| smart p0.4 t0.6 c1.5 | 44 % | 23 % | 0.50 / 0.49 | 1.56 | 0.93 | 37 % | 11.4 ± 2.0 |
| smart p0.3 t0.7 c1.5 | 45 % | 26 % | 0.40 / 0.39 | 1.56 | 0.88 | 39 % | 11.0 ± 1.8 |
| smart p0.3 t0.6 c1.5 | 47 % | 27 % | 0.39 / 0.38 | 1.56 | 0.81 | 35 % | 11.4 ± 1.9 |
| smart p0.7 t0.8 c2.5 | 15 % | 6 % | 1.74 / 1.18 | 2.56 | 1.67 | 45 % | 8.1 ± 1.2 |
| smart p0.7 t0.85 c3 | 13 % | 6 % | 2.21 / 1.25 | 3.06 | 1.95 | 45 % | 8.1 ± 1.3 |
| smart p0.8 t0.8 c3 | 9 % | 4 % | 2.30 / 1.34 | 3.06 | 2.02 | 44 % | 8.0 ± 1.2 |
| smart p0.8 t0.9 c3 | 7 % | 3 % | 2.98 / 1.85 | 3.06 | 2.18 | 54 % | 7.9 ± 1.2 |
| smart p0.9 t0.8 c3 | 8 % | 3 % | 1.96 / 1.01 | 3.08 | 2.03 | 43 % | 7.8 ± 1.2 |
| smart p1.0 t0.7 c2.5 | 4 % | 2 % | 1.55 / 1.10 | 2.57 | 1.79 | 42 % | 7.8 ± 1.2 |
| smart p1.0 t0.8 c2.5 | 4 % | 1 % | 1.62 / 1.11 | 2.58 | 1.86 | 46 % | 7.7 ± 1.2 |
| smart p1.0 t0.7 c3 | 4 % | 2 % | 1.55 / 1.10 | 3.07 | 1.99 | 39 % | 7.8 ± 1.2 |
| smart p1.0 t0.8 c3 | 4 % | 1 % | 1.62 / 1.11 | 3.08 | 2.08 | 44 % | 7.7 ± 1.2 |
| smart p1.2 t0.6 c2.5 | 4 % | 1 % | 1.73 / 1.29 | 2.57 | 1.89 | 41 % | 7.4 ± 1.1 |
| smart p1.2 t0.7 c2.5 (**Relaxed**) | 4 % | 1 % | 2.23 / 1.30 | 2.57 | 1.95 | 45 % | 7.4 ± 1.1 |
| smart p1.2 t0.7 c3 | 4 % | 1 % | 2.23 / 1.30 | 3.07 | 2.17 | 42 % | 7.4 ± 1.1 |
| smart p1.2 t0.8 c3 | 4 % | 1 % | 2.73 / 1.73 | 3.07 | 2.25 | 46 % | 7.4 ± 1.1 |

### Sweep: turns of two or more sentences (`ami-multi`)

| Mode | Clips cut off | Mid-sentence pauses cut | Sentence-end pauses cut | Latency median s (all / sentence-final) | p90 s | mean s | Ended by ceiling | WER % |
|---|---|---|---|---|---|---|---|---|
| Pause 0.9 s | 48 % | 12 % | 30 % | 0.96 / 0.96 | 1.00 | 0.99 | – | 12.2 ± 2.0 |
| Pause 1.2 s | 25 % | 5 % | 17 % | 1.24 / 1.25 | 1.29 | 1.30 | – | 12.0 ± 2.0 |
| Pause 1.5 s | 16 % | 3 % | 9 % | 1.53 / 1.54 | 1.58 | 1.62 | – | 11.0 ± 1.8 |
| smart p0.5 t0.7 c2 (**Normal**) | 55 % | 13 % | 50 % | 1.08 / 0.59 | 2.07 | 1.34 | 48 % | 12.7 ± 2.1 |
| smart p0.5 t0.7 c1.5 (**Quick**) | 60 % | 15 % | 53 % | 1.07 / 0.59 | 1.57 | 1.10 | 49 % | 12.8 ± 1.9 |
| smart p0.5 t0.6 c1.5 | 63 % | 17 % | 56 % | 0.60 / 0.58 | 1.57 | 1.05 | 45 % | 12.9 ± 2.0 |
| smart p0.4 t0.8 c1.5 | 62 % | 17 % | 55 % | 1.43 / 0.50 | 1.57 | 1.09 | 48 % | 13.7 ± 2.0 |
| smart p0.4 t0.7 c1.5 | 64 % | 19 % | 55 % | 0.98 / 0.49 | 1.57 | 1.03 | 44 % | 13.7 ± 2.0 |
| smart p0.4 t0.6 c1.5 | 69 % | 21 % | 60 % | 0.94 / 0.49 | 1.57 | 1.00 | 41 % | 13.6 ± 2.0 |
| smart p0.3 t0.7 c1.5 | 73 % | 26 % | 67 % | 0.88 / 0.41 | 1.57 | 0.96 | 43 % | 13.5 ± 2.0 |
| smart p0.3 t0.6 c1.5 | 74 % | 28 % | 70 % | 0.86 / 0.40 | 1.57 | 0.93 | 40 % | 13.7 ± 2.1 |
| smart p0.7 t0.8 c2.5 | 36 % | 7 % | 28 % | 2.21 / 0.79 | 2.57 | 1.75 | 50 % | 11.7 ± 1.9 |
| smart p0.7 t0.85 c3 | 32 % | 6 % | 26 % | 2.71 / 1.26 | 3.07 | 2.03 | 49 % | 11.7 ± 1.9 |
| smart p0.8 t0.8 c3 | 29 % | 4 % | 23 % | 1.86 / 1.30 | 3.06 | 2.01 | 46 % | 12.6 ± 2.2 |
| smart p0.8 t0.9 c3 | 24 % | 4 % | 18 % | 2.86 / 1.43 | 3.07 | 2.14 | 51 % | 12.6 ± 2.3 |
| smart p0.9 t0.8 c3 | 22 % | 3 % | 17 % | 2.48 / 1.22 | 3.10 | 2.11 | 47 % | 12.5 ± 2.1 |
| smart p1.0 t0.7 c2.5 | 20 % | 3 % | 15 % | 2.05 / 1.52 | 2.59 | 1.89 | 51 % | 12.7 ± 2.5 |
| smart p1.0 t0.8 c2.5 | 19 % | 3 % | 14 % | 2.50 / 1.57 | 2.59 | 1.93 | 53 % | 12.8 ± 2.5 |
| smart p1.0 t0.7 c3 | 19 % | 3 % | 15 % | 2.08 / 1.52 | 3.09 | 2.12 | 47 % | 12.7 ± 2.5 |
| smart p1.0 t0.8 c3 | 18 % | 2 % | 14 % | 2.55 / 1.57 | 3.09 | 2.17 | 50 % | 12.9 ± 2.5 |
| smart p1.2 t0.6 c2.5 | 16 % | 2 % | 12 % | 1.50 / 1.28 | 2.57 | 1.88 | 45 % | 12.3 ± 2.1 |
| smart p1.2 t0.7 c2.5 (**Relaxed**) | 15 % | 2 % | 11 % | 1.73 / 1.29 | 2.58 | 1.92 | 48 % | 12.3 ± 2.1 |
| smart p1.2 t0.7 c3 | 14 % | 1 % | 11 % | 1.76 / 1.29 | 3.08 | 2.14 | 45 % | 12.3 ± 2.0 |
| smart p1.2 t0.8 c3 | 14 % | 1 % | 11 % | 2.76 / 1.30 | 3.08 | 2.25 | 50 % | 12.3 ± 2.0 |

### Sweep: short turns (`ami-ihm`, selected rows)

| Mode | Clips cut off | Mid-sentence pauses cut | Latency median s (all / sentence-final) | p90 s | mean s | Ended by ceiling | WER % |
|---|---|---|---|---|---|---|---|
| Pause 0.9 s | 14 % | 16 % | 0.96 / 0.96 | 1.00 | 1.00 | – | 12.0 ± 2.5 |
| Pause 1.2 s | 5 % | 5 % | 1.25 / 1.25 | 1.29 | 1.30 | – | 10.9 ± 2.0 |
| Pause 1.5 s | 2 % | 2 % | 1.53 / 1.53 | 1.58 | 1.59 | – | 11.4 ± 2.3 |
| smart p0.5 t0.7 c2 (**Normal**) | 13 % | 14 % | 0.59 / 0.59 | 2.06 | 1.15 | 36 % | 13.2 ± 2.6 |
| smart p0.5 t0.7 c1.5 (**Quick**) | 16 % | 18 % | 0.59 / 0.59 | 1.57 | 0.97 | 38 % | 14.3 ± 2.8 |
| smart p0.4 t0.7 c1.5 | 16 % | 20 % | 0.49 / 0.49 | 1.57 | 0.89 | 33 % | 14.5 ± 3.0 |
| smart p1.0 t0.7 c2.5 | 2 % | 2 % | 1.11 / 1.10 | 2.57 | 1.70 | 34 % | 12.0 ± 2.4 |
| smart p1.2 t0.7 c2.5 (**Relaxed**) | 1 % | 1 % | 1.30 / 1.30 | 2.58 | 1.85 | 36 % | 10.8 ± 2.2 |

Every candidate on every set, with the pause-length buckets, prints with
`--rescore` on `$WORK/results-43/patience-all` (see Reproduce).

## Caveats

- **AMI is meeting speech on headset mics, not phone dictation.** It is
  more disfluent ("um", restarts), has crosstalk from other speakers, and a
  segment's end isn't always a finished thought: 24 % of the single-segment
  clips don't end in `.`, `?` or `!`, and on those Smart Turn waits longer,
  as it should. People dictating may pause differently, and may finish
  sentences more cleanly.
- **Turn boundaries are the corpus's.** `ami-multi` joins contiguous
  segments of one speaker; what the speaker said next (after a gap or
  someone else's turn) is not part of the turn.
- **Latency is relative to Silero's end of speech**, not a hand-marked one
  (the annotations can't give it). The comparison between modes holds; the
  absolute numbers leave out Silero's own hangover.
- **Pause lengths are Silero's**, so a pause the 0.2 s gate closed on can
  measure a little under 0.2 s. Pauses Silero doesn't split at 0.2 s aren't
  counted; they can't cause a cut-off in any mode here.
- **The silence matters to the model.** Pauses inside a clip are AMI's room
  tone; the silence after a clip is the harness's synthetic −70 dBFS noise.
  On fox.wav, marmalade-stt-cli measured a mid-word cut at p = 0.17 after
  0.2 s of room tone but 0.53 after 0.2 s of digital zeros. A phone whose
  noise suppression makes pauses quieter than AMI's may push the model
  towards "finished" and cut off more.
- English only. The model is multilingual; other languages are unmeasured.
- The app's Kotlin gate (`SmartTurnGate`, with `Patience` holding the
  three triples) is meant to match `turn.TurnGate`; its tests should keep
  following `tests/test_turn.py`.
- The WER column keeps every turn's text. In the app a cut-off may lose the
  rest of the sentence unless the user taps again.

## Reproduce

On the benchmark host, from a harness copy one level under `$WORK`:

```sh
cp <repo>/app/src/main/assets/turn/smart-turn-v3.2-cpu.onnx $WORK/models/smart-turn-v3.2/   # the file the app bundles
../venv/bin/python -m asrbench.prepare_data --work .. ami-long ami-multi
OMP_NUM_THREADS=6 OPENBLAS_NUM_THREADS=1 nice -n 19 ../venv/bin/python -m asrbench.turn --work .. \
  --engine parakeet-tdt-0-6b-v3 ami-ihm ami-long ami-multi
../venv/bin/python -m asrbench.turn --work .. --bench-threads 1,2,4,6
```

About 35 minutes for the three sets. The AMI annotation zip (23 MB) is
fetched on the first run.

The Patience sweep: `--modes` takes any `pause-<s>` or
`smart-p<propose>-t<threshold>-c<ceiling>` name (`turn.mode_config`), and
`--results` keeps it apart from the main run:

```sh
OMP_NUM_THREADS=6 OPENBLAS_NUM_THREADS=1 nice -n 19 ../venv/bin/python -m asrbench.turn --work .. \
  --results results-43/patience-all --engine parakeet-tdt-0-6b-v3 \
  --modes pause-0.9,pause-1.2,pause-1.5,smart-p0.5-t0.7-c2,smart-p0.5-t0.7-c1.5,smart-p1.2-t0.7-c2.5 \
  ami-long ami-multi ami-ihm
```

The saved sweep ran in three rounds (`results-43/patience`, `-r2`, `-r3`),
merged per clip into `results-43/patience-all`; the Silero segments were
identical across rounds.
