# Utterance cap: continuous speech past 28 s (#48, 2026-10-02)

**Question.** sherpa-onnx's Silero `max_speech_duration` (28 s,
`MAX_UTTERANCE_SECONDS`) is not a hard cut. Past it, `voice-activity-detector.cc`
only lowers the minimum silence to 0.1 s and raises the threshold to 0.9, so
continuous speech can make a longer segment (#47 saw a 30.6 s utterance).
What does that cost, and does cutting it in the session help?

**Answer.** Whisper lost speech: sherpa's Whisper reads at most 29.5 s
(`offline-recognizer-whisper-impl.h` truncates the features to 2950 frames
and logs "We process only the first 30 seconds"), so a 32.1 s decode
dropped 2.6 s from the middle of the dictation. Parakeet Ultra read the
whole 32 s, with memory above the 28 s figures `SpeedBudget.REFERENCE` is
built from. The session now cuts such an utterance itself at the quietest
VAD window in the last 1.5 s before 28.6 s (28 s + the 0.6 s pre-roll,
`UtteranceCap.MAX_DECODE_SAMPLES`): no decode is over the cap, turbo's
lost speech comes back, and Ultra is unchanged within noise.

## Setup

Host harness on the benchmark host (`tools/asr-bench`, app-identical offline
session, Silero 1.2 s minimum silence as Pause's default, 4 threads, harness
AGC, 0.5 s lead and 1 s tail of mic floor). Clips: LibriSpeech test-clean
utterances from the speed-bench audio (`stt-bench-audio/`, kept outside the repo; its README has the
IDs), each trimmed to its speech and concatenated with no gap, so the only
pauses are the ones inside sentences (none reaches 1.2 s). "Before" is
`main`'s `session.py`; "after" is this branch's. Scripts and logs:
`$WORK/scratch-48/` on the benchmark host (`cap40.py`, `sweep.py`,
`sweep-*.log`).

## Results

Decode lengths (s) and normalised WER on a 38.8 s clip (ls20-1 + ls20-2):

| Engine | Session | Decodes | WER |
|---|---|---|---|
| Whisper turbo | before | 32.1, 7.2 | 9.1 |
| Whisper turbo | after | 27.6, 4.6, 7.2 | 1.0 |
| Parakeet Ultra | before | 32.1, 7.2 | 1.0 |
| Parakeet Ultra | after | 27.6, 4.6, 7.2 | 1.0 |

Six concatenations (36–52 s, 633 words), word errors:

| Clip | s | turbo before | turbo after | Ultra before | Ultra after |
|---|---|---|---|---|---|
| ls20-1+ls20-2 | 38.8 | 9 | 1 | 1 | 1 |
| ls20-2+ls20-3 | 36.6 | 0 | 0 | 0 | 0 |
| ls20-3+ls28-1 | 43.4 | 0 | 1 | 1 | 1 |
| ls28-1+ls28-2 | 52.1 | 1 | 1 | 3 | 2 |
| ls28-2+ls20-1 | 45.5 | 2 | 3 | 1 | 1 |
| ls10-1+ls10-2+ls20-3 | 36.2 | 2 | 2 | 2 | 2 |
| **Total** | | **14 (2.2 %)** | **8 (1.3 %)** | **8 (1.3 %)** | **7 (1.1 %)** |

Every "after" decode was at most 28.6 s; five of the six clips needed one
forced cut (the last one Silero split on its own). The clip ends were
always transcribed.

**Where to cut.** A cut can land just after a word the engine then drops at
the end of the first part, or split a word when there is no real gap
(a clip with every pause shortened to 30 ms split "account" into "a…" and
"count"). Variants tried on the same six clips: the quietest 100 ms or
200 ms stretch instead of one 32 ms window, and searching the last 3 s or
4 s instead of 1.5 s. Totals: turbo 8–12 errors, Ultra 7–9, the single
window over 1.5 s among the best for both. The differences are a word or two
per clip and go both ways, so the simplest rule stays: the same
`quietestWindow` that cuts long partials.

After a cut, the rest of the segment is decoded unless the VAD saw no
speech window in it or it is near-silent: Whisper makes up text for such an
edge, but a rest can be one short word ("…and that's it"), so length alone
never decides (#48 review; the first version skipped anything under 0.25 s).
None of these clips left a rest that short, so the numbers above hold for
both. The A/B and speed benchmarks' splitter (`vadUtterances`) cuts the same
way (`UtteranceCap`).
