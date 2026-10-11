# Smart Turn defaults: languages, phone-like silence, short requests, October 2026 (#64)

Before v1: are Smart Turn's shipped defaults right for how the app uses it?
Smart Turn ends one-shot requests (the speak-now popup and other apps'
SpeechRecognizer) by default in every UI language. #43 tuned it on English
meeting speech (AMI) with synthetic −70 dBFS silence after each clip
(`2026-09-smart-turn-ami.md`).

Harness: `tools/asr-bench/asrbench/turn_defaults.py` (README: "Smart Turn
defaults across languages and silence"). It runs the same gate as #43
(`turn.turn_session`).

## Verdict

**Normal was not tuned right, and it changes.** Normal used to ask the
model after a 0.5 s pause. In every language measured, the model calls
many 0.5–1 s pauses in the middle of a sentence "finished". So Normal cut
off 19 % of finished single sentences (FLEURS and VoxPopuli pooled; 3–41 %
by language), against 3 % for Pause 1.2 s. In a one-shot request a cut-off
ends the request with half the sentence.

| Patience | Before (propose / threshold / ceiling) | After |
|---|---|---|
| Quick | 0.5 s / 0.7 / 1.5 s | unchanged |
| **Normal** (the default) | 0.5 s / 0.7 / 2 s | **1.2 s / 0.7 / 2.5 s** (Relaxed's old numbers) |
| Relaxed | 1.2 s / 0.7 / 2.5 s | **1.5 s / 0.7 / 3 s** |

- **New Normal**: the model is only asked at pauses that Pause 1.2 s would
  end at, and it holds the turn when the sentence doesn't sound finished.
  - It cuts off about as rarely as Pause 1.2 s in every language: 0–3 %,
    Japanese 13 % (Pause 1.2 s also 13–14 %). On AMI it cuts less than
    Pause 1.2 s: 2 % against 8 %.
  - A finished sentence ends about 1.3 s after the speech: 0.7 s later than
    the old Normal (0.6 s), and 0.05 s later than Pause 1.2 s.
  - When the model is unsure, the turn waits for the 2.5 s ceiling. That
    happens on 0–6 % of clean sentences and on about 35–40 % of AMI turns,
    where speakers often trail off.
- **New Relaxed** is one step more patient, for people who pause long:
  1 % cut off (AMI 1 %), and a finished sentence ends after about 1.6 s.
- **Quick** keeps its numbers. It is the fast choice (about 0.6 s) and
  cuts off about one long sentence in five. It used to differ from the old
  Normal only in the ceiling, which clean speech rarely reaches, so the two
  measured almost the same.
- No UI text changes. The Settings descriptions now fit their numbers:
  Normal "waits through most pauses to think" (it didn't at 0.5 s), and
  Relaxed "waits longer before it stops".

Answers to the issue's four questions:

1. **Languages.** No language is an exception. The old Normal cut off 3 %
   (Arabic) to 41 % (Japanese) of single sentences in all 16 sets. The
   new Normal stays at Pause 1.2 s's level in every one. Finished
   sentences end promptly in every language: on FLEURS and VoxPopuli only
   0–7 % of final ends come from the ceiling (VoxPopuli-de: 12–14 % with
   Quick and the old Normal, 5 % with the new Normal).
2. **Phone-like silence.** Quieter pauses (noise suppression, then
   SoftAgc) push the model a little towards "finished".
   - The old Normal: +1 to +5 points of cut-offs.
   - The new Normal: within ±1 point on FLEURS and VoxPopuli, +2 to +4 on
     AMI.
   - Pause 1.2 s also moves on AMI (+4–5), because Silero splits quiet
     pauses differently.
   - This doesn't change the verdict.
3. **Short, request-shaped input** (speech ≤ 5 s). The old Normal cut off
   2–4 % of short read or parliament sentences and 8–11 % of short AMI
   turns, and ended the turn at 9–18 % of their mid-sentence pauses. The
   new Normal cut off 0–2 %, at +0.7 s. In a one-shot request a cut-off
   costs the request, so the 0.7 s is worth it. A shorter ceiling doesn't
   help: on clean speech the ceiling rarely decides.
4. **Parity.** Yes, with one fix on the harness side. The harness counted
   the silence and ended the model's window at the hop's end. The app
   (`SmartTurnGate`) counts in whole VAD windows. So the harness scored up
   to 32 ms more audio, and could end a turn at the ceiling one hop early.
   `turn_session` now uses the VAD position, as the app does. Probabilities
   and features match (see Parity below).

## Data

Every set was fetched without an account. Each clip is one sentence,
so every pause inside it is a mid-sentence pause.

| Set | What | Clips used |
|---|---|---|
| `ami-ihm`, `ami-long` | AMI headset test segments (CC-BY-4.0): spontaneous English meeting speech with manual word timings (#43) | 200 + 200 |
| `fleurs-<lang>` | FLEURS test (CC-BY-4.0): read single sentences, many speakers. The 9 UI languages (en, de, es, fr, it, pt-BR, zh, ja, hi) plus ko and ar; `fleurs-it`/`-pt` are new | 39–166 per language: the single-sentence clips of the 50–200 in each set |
| `vox-<lang>` (new) | VoxPopuli test (`facebook/voxpopuli`, CC0, pinned revision): European Parliament speeches, spontaneous or half-read, with real hesitation pauses. Gold transcripts that are one whole capitalised sentence ending in `.` `?` `!` | 100 each: de, es, fr, it, en |

Clips are filtered by `prepare_data.one_sentence`: no sentence end before
the last word, so an abbreviation such as "Dr." drops a clip. That is why
FLEURS de/es/fr keep 39 of their 50 clips.

Spontaneous speech outside Europe (zh, ja, ko, hi, ar) has only the FLEURS
read speech. No openly licensed set that needs no account and has natural
pauses was found in time. FLEURS readers do pause mid-sentence (0.5–1 s),
and those are the pauses that matter here.

## Method

- **Gate.** Same harness and mic emulation as #43:
  - one gain per clip (the AGC stand-in), 100 ms hops, the app's 100 Hz
    high-pass and Silero (threshold 0.5, min speech 0.25 s);
  - 0.6 s pre-roll, and the model scores the turn's last 8 s, trailing
    silence included;
  - re-checks every 0.5 s, virtual time, model cost at 2 ORT threads.
- **Modes.**
  - Pause 1.2 s, Quick, the old Normal, the new Normal (= old Relaxed) and
    the new Relaxed;
  - an in-between candidate, propose 1.0 s / 0.7 / 2.5 s, and propose
    0.8 s / 0.7 / 2 s (full tables only).
  - The new Relaxed ran as a second pass with `--modes`, on the room and
    zero silence models only.
- **Silence models** (`--silence`). Each clip runs once per model:
  - `room`: as recorded, with the harness's −70 dBFS floor before and after
    the clip (what #43 measured).
  - `n70`, `n90`, `zero`: `quieten` replaces everything more than 0.1 s
    from Silero's speech (every pause, and the lead and tail) with white
    noise at −70 or −90 dBFS, or with exact zeros, using 10 ms ramps.
    This stands in for noise suppression followed by SoftAgc, which
    barely lifts a pause: its gain rises only 3 dB/s after speech.
- **Ground truth.**
  - AMI keeps #43's scoring from manual word timings: a turn ended before
    the last word starts is a cut-off.
  - FLEURS and VoxPopuli have no word timings (`segment_reference`). The
    speech is Silero's 0.2 s segments, and a turn ended before the last
    segment's onset (plus Silero's 0.35 s onset lag) cut the sentence off.
    A breath or click that Silero calls speech after the sentence counts
    as a cut for every mode alike. That is the baseline under Pause 1.2 s's
    0–6 %; the comparison between modes holds.
- **Latency.** Silero's end of speech to the final turn end, model checks
  included. Mean, not median: Smart Turn's ends are bimodal (a model end
  at about 0.6 s, or the ceiling), so the median jumps between them.

## Results

### Per language (silence as recorded)

Clips cut off / mean end-of-speech → turn end (s). "Old Normal" is
0.5 s / 0.7 / 2 s; "New Normal" is 1.2 s / 0.7 / 2.5 s; "New Relaxed" is
1.5 s / 0.7 / 3 s.

| Set | Clips | Pause 1.2 s | Quick | Old Normal | p1.0 / 0.7 / 2.5 | **New Normal** | New Relaxed |
|---|---|---|---|---|---|---|---|
| ami-ihm | 200 | 5 % / 1.30 | 14 % / 0.98 | 12 % / 1.14 | 2 % / 1.71 | **1 % / 1.84** | 0 % / 2.18 |
| ami-long | 200 | 12 % / 1.28 | 34 % / 1.04 | 30 % / 1.23 | 6 % / 1.80 | **4 % / 1.98** | 2 % / 2.29 |
| fleurs-en | 163 | 1 % / 1.29 | 10 % / 0.66 | 10 % / 0.69 | 1 % / 1.23 | **1 % / 1.41** | 1 % / 1.71 |
| fleurs-de | 39 | 3 % / 1.24 | 23 % / 0.57 | 23 % / 0.57 | 3 % / 1.15 | **3 % / 1.31** | 3 % / 1.60 |
| fleurs-es | 39 | 0 % / 1.24 | 26 % / 0.56 | 26 % / 0.56 | 3 % / 1.07 | **0 % / 1.26** | 0 % / 1.55 |
| fleurs-fr | 39 | 0 % / 1.24 | 13 % / 0.57 | 13 % / 0.57 | 0 % / 1.06 | **0 % / 1.26** | 0 % / 1.54 |
| fleurs-it | 70 | 0 % / 1.22 | 30 % / 0.54 | 30 % / 0.54 | 0 % / 1.07 | **0 % / 1.25** | 0 % / 1.53 |
| fleurs-pt | 82 | 1 % / 1.25 | 34 % / 0.57 | 34 % / 0.57 | 1 % / 1.08 | **1 % / 1.28** | 1 % / 1.56 |
| fleurs-zh | 81 | 2 % / 1.25 | 30 % / 0.56 | 30 % / 0.56 | 4 % / 1.07 | **2 % / 1.26** | 2 % / 1.54 |
| fleurs-ja | 166 | 14 % / 1.24 | 41 % / 0.57 | 41 % / 0.58 | 24 % / 1.08 | **13 % / 1.27** | 2 % / 1.56 |
| fleurs-hi | 84 | 1 % / 1.26 | 12 % / 0.64 | 12 % / 0.66 | 1 % / 1.15 | **0 % / 1.32** | 0 % / 1.63 |
| fleurs-ko | 79 | 0 % / 1.25 | 6 % / 0.57 | 6 % / 0.57 | 0 % / 1.07 | **0 % / 1.28** | 0 % / 1.56 |
| fleurs-ar | 91 | 0 % / 1.24 | 3 % / 0.59 | 3 % / 0.59 | 0 % / 1.09 | **0 % / 1.31** | 0 % / 1.57 |
| vox-en | 100 | 4 % / 1.24 | 16 % / 0.62 | 16 % / 0.64 | 3 % / 1.17 | **3 % / 1.35** | 1 % / 1.60 |
| vox-de | 100 | 2 % / 1.25 | 11 % / 0.74 | 11 % / 0.80 | 2 % / 1.25 | **2 % / 1.38** | 0 % / 1.71 |
| vox-es | 100 | 2 % / 1.26 | 20 % / 0.59 | 20 % / 0.60 | 2 % / 1.10 | **2 % / 1.29** | 1 % / 1.57 |
| vox-fr | 100 | 1 % / 1.25 | 14 % / 0.62 | 14 % / 0.64 | 3 % / 1.13 | **1 % / 1.32** | 1 % / 1.58 |
| vox-it | 100 | 3 % / 1.25 | 18 % / 0.60 | 18 % / 0.61 | 3 % / 1.13 | **3 % / 1.32** | 1 % / 1.61 |

How often the final end came from the ceiling rather than the model:

- **FLEURS and VoxPopuli:** 0–7 % for every Smart Turn mode. The exception
  is VoxPopuli-de: 12–14 % for Quick and the old Normal, 5 % for the new
  Normal.
- **AMI:** 34–47 %, because AMI segments often end without a finished
  thought (#43 Caveats).

So the model does recognise a finished sentence in every language. What
goes wrong is that it also "recognises" one at a 0.5–1 s pause in the
middle.

The p1.0 / 0.7 / 2.5 candidate ends about 0.2 s sooner than the new
Normal. It cuts about as rarely, except in Japanese (24 % against 13 %)
and on long AMI turns (6 % against 4 %), so 1.2 s stays.

### Silence models

Pooled. "Mid pauses cut" is the share of mid-sentence pauses where the
mode ended the turn.

FLEURS + VoxPopuli (1,433 clips):

| Mode | room | n70 | n90 | zero | mean s (room / zero) |
|---|---|---|---|---|---|
| Pause 1.2 s | 3 % (2 %) | 2 % (2 %) | 3 % (2 %) | 3 % (2 %) | 1.25 / 1.25 |
| Quick | 19 % (17 %) | 22 % (19 %) | 23 % (20 %) | 20 % (18 %) | 0.61 / 0.61 |
| Old Normal | 19 % (17 %) | 22 % (19 %) | 23 % (20 %) | 20 % (18 %) | 0.62 / 0.62 |
| **New Normal** | **3 % (2 %)** | **2 % (1 %)** | **3 % (2 %)** | **2 % (2 %)** | 1.31 / 1.31 |
| New Relaxed | 1 % (1 %) | – | – | 1 % (1 %) | 1.60 / 1.59 |

Clips cut off (mid pauses cut).

AMI ihm + long (400 clips):

| Mode | room | n70 | n90 | zero | mean s (room / zero) |
|---|---|---|---|---|---|
| Pause 1.2 s | 8 % (5 %) | 12 % (6 %) | 12 % (6 %) | 13 % (7 %) | 1.29 / 1.26 |
| Quick | 24 % (17 %) | 28 % (20 %) | 28 % (20 %) | 27 % (20 %) | 1.01 / 0.95 |
| Old Normal | 21 % (14 %) | 26 % (17 %) | 25 % (17 %) | 25 % (17 %) | 1.19 / 1.10 |
| **New Normal** | **2 % (1 %)** | **4 % (2 %)** | **5 % (3 %)** | **6 % (3 %)** | 1.91 / 1.80 |
| New Relaxed | 1 % (0 %) | – | – | 3 % (2 %) | 2.23 / 2.09 |

- **The level of the quiet barely matters.** −70 dBFS noise, −90 dBFS
  noise and digital zeros give about the same numbers. What matters is
  that the pause is quieter than the speaker's room tone.
- Quiet pauses add cut-offs mostly where the model is already asked at
  short pauses (the old Normal: +3 to +5 points).
- Per language, the swing between room and zero can be large in either
  direction (FLEURS-ja, the old Normal: 41 % → 21 %; zh:
  30 % → 37 %). The pooled numbers are the signal.

### Short requests (speech ≤ 5 s)

`--rescore --max-speech 5`: single sentences of at most 5 s from first
onset to end of speech, the shape of a search or a short message.

| Mode | FLEURS + Vox (212 clips): cut off (mid pauses cut) / mean s, room | zero | AMI (133 clips), room | zero |
|---|---|---|---|---|
| Pause 1.2 s | 0 % (0 %) / 1.25 | 0 % (2 %) / 1.26 | 2 % (1 %) / 1.32 | 5 % (4 %) / 1.26 |
| Quick | 2 % (9 %) / 0.60 | 4 % (16 %) / 0.61 | 8 % (11 %) / 1.00 | 11 % (18 %) / 0.95 |
| Old Normal | 2 % (9 %) / 0.62 | 4 % (16 %) / 0.63 | 8 % (10 %) / 1.18 | 11 % (18 %) / 1.09 |
| **New Normal** | **0 % (0 %) / 1.30** | **0 % (2 %) / 1.31** | **0 % (0 %) / 1.88** | **2 % (3 %) / 1.75** |
| New Relaxed | 0 % (0 %) / 1.59 | 0 % (2 %) / 1.59 | 1 % (0 %) / 2.23 | 1 % (0 %) / 2.05 |

Short requests have few pauses, so fewer of them are cut off at all. When
the speaker does pause, though, the old Normal ended the request at 9–18 %
of those pauses. The new Normal ended it at 0–3 %. The price is 0.7 s on
every request (1.3 s instead of 0.6 s after a finished sentence). That is
about what Pause 1.2 s, the Dictate default, already costs.

## Parity (question 4)

- **Gate rule.** `SmartTurnGate` and `turn.TurnGate` agree:
  - the first check comes at the propose pause (sherpa closes a segment
    exactly `min_silence` before the VAD position, `voice-activity-
    detector.cc`);
  - p must be strictly above the threshold;
  - re-checks come after every further 0.5 s of silence, and the ceiling
    counts from the segment's end;
  - the window is the turn from its first onset's pre-roll (0.6 s), cut
    to its last 8 s, trailing silence included.

  One difference was fixed in the harness (commit `5ee6f66`). The harness
  measured silence, and ended the model's window, at the hop's end. The
  app measures at the VAD position, in whole 512-sample windows. On the
  same AMI clips the aggregates match #43's (old Normal on the first 110
  `ami-ihm` clips: 10 cut-offs both, mean 1.28 s vs 1.24 s), but single
  verdicts moved (next point).
- **The model is touchy about the window's end.** The 32 ms shift changed
  8 of 60 clips' outcomes, in both directions. On one pause, p went from
  0.82 to 0.38 with the window ending 32 ms earlier. Only aggregate rates
  mean anything, on host or phone.
- **Features and probabilities.**
  - The Kotlin `WhisperFeaturesTest` (5) and `OnnxTurnDetectorTest` (3)
    pass against the CLI-generated goldens (`tools/smart-turn/
    gen_fixtures.py`).
  - On the same four fixture signals, the harness's `SmartTurnModel` on
    the benchmark host gives within 0.04 of the goldens (0.016 / 0.027 / 0.040 /
    −0.005).
  - That is the known per-CPU int8 spread (AVX-512 VNNI vs AVX2), inside
    the Kotlin test's 0.05 tolerance. The phone's ARM kernels will differ
    by a similar amount.

## How to check it by hand (release check P9)

On the phone, use the speak-now popup or an app's mic button with Smart
Turn, patience Normal.

1. Say a short request with a deliberate half-second pause mid-sentence
   ("Set a timer for … ten minutes"). **Pass**: it waits through the
   pause. The old Normal often ended the request there.
2. Say a finished sentence and stop. **Pass**: it ends about 1.3 s later.
   That is a beat slower than before, and about Pause 1.2 s's pace.
3. Trail off without finishing ("I was going to say …"). **Pass**: it
   ends at about 2.5 s.
4. Quick should feel snappy (about 0.6 s) but cut a mid-sentence pause now
   and then. Relaxed should wait about 1.6 s.

## Caveats

- **No phone runs.** The silence models stand in for the Pixel's noise
  suppression and SoftAgc; the real floor wasn't measured. The result
  doesn't depend much on the floor's level (−70, −90 dBFS or zeros all
  agree), only on the pause being quiet.
- **Ground truth without word timings** (FLEURS, VoxPopuli) uses Silero's
  segments. A trailing breath or click counts as speech after the
  sentence, for every mode alike. FLEURS-ja's room-tone set has the most
  of these: Pause 1.2 s "cuts" 14 % there but 4 % with quieted pauses.
- **Read speech.** FLEURS is read aloud and VoxPopuli is half-read.
  People making a request pause differently, but the mid-sentence pause
  that breaks the old Normal (0.5–1 s) is common in both. AMI covers
  spontaneous English.
- **Small sets.** FLEURS de, es and fr have 39 single-sentence clips each,
  so a single clip is 2.6 points.
- **The model's verdicts are fragile** at the scale of a VAD window (see
  Parity) and differ by about 0.04 in probability between CPUs. A
  threshold-0.7 decision near the line can go either way on the phone.
- The new Relaxed (1.5 s / 3 s) ran only on the room and zero silence
  models.

## Reproduce

On the benchmark host, from a harness copy one level under `$WORK`
(`$WORK/harness-64`):

```sh
../venv/bin/python -m asrbench.prepare_data --work .. fleurs-it fleurs-pt vox-de vox-es vox-fr vox-it vox-en
OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=1 nice -n 19 ../venv/bin/python -m asrbench.turn_defaults --work .. \
  ami-ihm ami-long fleurs-en fleurs-de fleurs-es fleurs-fr fleurs-it fleurs-pt fleurs-zh fleurs-ja \
  fleurs-hi fleurs-ko fleurs-ar vox-en vox-de vox-es vox-fr vox-it
OMP_NUM_THREADS=2 OPENBLAS_NUM_THREADS=1 nice -n 19 ../venv/bin/python -m asrbench.turn_defaults --work .. \
  --results results-64x --modes smart-p1.5-t0.7-c3 --silence room,zero <same sets>
../venv/bin/python -m asrbench.turn_defaults --work .. --rescore [--max-speech 5] <sets>
```

About 25 minutes in three parallel processes at nice 19 (2 ORT threads
each). VoxPopuli's five test parquets are 4.7 GB. Per-clip turns are in
`$WORK/results-64/turn-defaults/<set>@<silence>.jsonl` and
`$WORK/results-64x/` (the new Relaxed). The pooled tables above come from
`$WORK/scratch-64/pool.py`, which merges the two.
