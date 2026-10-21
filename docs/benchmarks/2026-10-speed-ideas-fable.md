# Speed ideas: an independent audit, October 2026 (#91, Fable 5.1)

Read-only. From the code on `main` (2026-10-20), the benchmark history under
`docs/benchmarks/`, and marmalade-tts-android's speed work. Nothing here was
run on a phone; every number is quoted from an existing doc or derived from
one, and each idea names the measurement that would prove or kill it. Written
before reading `child/91-speed-audit` or the other comments on #91.

## Where the user-felt time goes today

The two waits Max feels are **request → first text** and **end of speech →
final text**. From the Pixel 8a lineup run (`2026-10-pixel8a-ort.md`, benchmark
service, 4 threads, Pause with the 1.2 s slider) and the Pixel 6a surface pass
(`2026-10-ort-load/README.md`):

| Engine (8a) | Decode RTF | First partial after onset | End of speech → Final, 5 / 10 / 20 s |
|---|---|---|---|
| Parakeet 110M | 0.03 | 0.5 s | 1.5 / 1.8 / 1.5 s |
| Parakeet Ultra | 0.08 | 0.6–0.7 s | 1.9 / 2.2 / 2.5 s |
| Nemotron 3.5 1120 ms | 0.2 | 1.7 / 0.6 s | 1.4 / 1.6 / 1.1 s |
| Whisper turbo | 0.77 | none | 6.4 / 9.0 / 11.9 s |

Decomposing end of speech → Final for the offline engines, from the code:

1. **The VAD's minimum silence: 1.2 s, fixed.** sherpa's Silero closes a
   segment only once `min_silence` has elapsed, and trims the segment's end
   back to where the silence began (`voice-activity-detector.cc`: `end =
   Tail − MinSilenceDurationSamples`). So the audio the Final decodes is
   complete 1.2 s before the decode is allowed to start.
2. **A partial in flight.** `isSpeechStarted` stays true until the segment
   closes, so partials keep re-decoding the utterance every ≥ 200 ms
   *through the silence*. Each is a full decode of up to 6 s (Ultra ≈ 0.5 s
   on the 8a, about double on the 6a). The Final can't start until the one
   in flight returns: NeMo decodes aren't cancellable (patches 0003/0004
   cover Whisper, Canary and Qwen3 only). Expected cost: 0 to one partial,
   average about a quarter second on the 8a.
3. **Hop granularity.** The VAD sees audio in 100 ms hops, so the segment
   closes up to 100 ms late (average 50 ms).
4. **The Final decode itself.** Features + encoder + greedy decode of the
   whole utterance from scratch: RTF × length, 0.4 s for 5 s of Ultra on the
   8a, 2.2 s at the 28 s cap.
5. **Cleanup and delivery.** `:textproc` is pure Kotlin (milliseconds); the
   IME commit is one `getTextBeforeCursor(1000)` IPC plus a batch edit.
   Unmeasured, but there is no sign of anything large here.

So for 110M about 80 % of the wait is the silence rule, for Ultra 50–65 %,
and for turbo and Qwen3 it is almost all decode. **Waiting and computing
happen in sequence today, and could overlap almost entirely.** That is the
first idea, and the biggest.

For request → first text, #78 already moved the mic open to 20–60 ms; what
remains is the model load (Ultra 2.7–3.7 s in the keyboard on the 6a, then
the first partial at 3.3–4.3 s) and the first partial's own 0.5–0.7 s.

## Ranked ideas

### 1. Speculative Final: decode when the silence starts, deliver when the VAD confirms

**What changes.** When Silero's per-window probability drops below threshold
(the moment sherpa records as `temp_end_`), the session decodes exactly what
the Final would decode if no speech resumes: the pre-roll, the segment up to
the last speech window, and the family's tail padding. If the VAD then closes
the segment (1.2 s later) with no new onset, that text *is* the Final and is
sent at once. If speech resumes first, the candidate is dropped. The silence
start is known from `Vad.compute()` (already in the vendored Kotlin API: the
per-window probability; Silero costs about 0.2 ms per window, so running it a
second time per window is free), or from a tiny JNI patch exposing
`temp_end_`. The candidate decode replaces the partials of step 2 above:
after silence begins, no more partials are decoded.

**Stage.** End-of-speech wait + final decode (they overlap), and the partial
in flight.

**Expected gain.** Decodes that take less than the silence wait become free:
on the 8a Ultra's end of speech → Final drops from 1.9–2.5 s to about 1.3 s
for utterances up to roughly 12 s (decode ≤ 1 s hidden inside 1.2 s), 110M to
about 1.25 s at any length, turbo and Qwen3 save about 1.2 s of their 6–12 s,
and the 6a gains proportionally more because its decodes are longer. Smart
Turn gets the same: the candidate is ready when the model says complete.
Battery goes **down**: today's 2–5 wasted partial decodes per utterance end
are replaced by one decode that is used.

**Risk.** Accuracy: none by construction. The candidate's audio must be
byte-identical to what the Final would get (same pre-roll start, same end:
the last speech window, same padding); decodes are deterministic (#53: a
second run changed 0 of 200 transcripts), so the text is identical. The
trap is a candidate decoded on slightly different audio than the Final
would use (the VAD's segment start is `Tail − 2 windows − minSpeech` at
detection; take the Final's audio range from the same arithmetic). Memory:
none. Short syllable gaps start candidates that get discarded; gate the
candidate on 150–300 ms of silence to keep the decode count near today's
partial count.

**Tried before?** No. #20 ordered Finals ahead of partials and capped the
partial window; it did not use the wait. The issue's "speculative final
decode" is this idea; the measurement history says it is the largest lever
for the default engine.

**Measurement.** `SttTiming` "listen" line `final=` and the "final decode"
line, 10+ turns per engine on the 6a, before and after; count of candidates
discarded; a parity check that the candidate text equals a re-decode of the
closed segment on every turn (log a hash of both, not the text). Host:
`asrbench.latency` with the candidate added to the sim.

### 2. Stop partials once silence has begun, and make NeMo decodes cancellable (latency bug)

**What changes.** (a) No partial is started after the last speech window
(same silence detection as #1). (b) sherpa patch: the NeMo transducer's
encoder run takes the cancellable `RunOptions` of patch 0004 and the greedy
loop checks the stream's cancel flag, so `OfflineTranscriber.cancel` works
for Parakeet; the session cancels a partial in flight when a segment closes.

**Stage.** End-of-speech wait → final decode handoff; battery.

**Expected gain.** Removes the 0 to one-partial delay before every Final
(average about 0.25 s on the 8a, 0.5 s on the 6a, for Ultra), and the CPU
of 2–5 partials per utterance end that produce text nobody sees (the audio
no longer changes). If #1 lands, (a) comes with it; (b) still matters for a
segment that closes while a partial begun during speech is running.

**Risk.** None to accuracy; a cancelled partial is simply not shown.

**Tried before?** No. The ARCHITECTURE note "a Final never waits behind a
partial" holds for the *ordering*, not for a partial already running.

**Measurement.** Per-turn: time from the VAD closing the segment (log it)
to the Final decode's start; today this should show a 0–500 ms spread, after
the change near 0. CPU: the number of `transcribe` calls per utterance.

### 3. Progressive Final: decode long utterances in pieces as they are spoken

**What changes.** Today a long utterance (up to 28 s) is re-decoded from
scratch at the end (2.2 s on the 8a at the cap, about 4 s on the 6a) while the
partial path already commits text for audio before `partialFrom` at a quiet
VAD window. Make those committed pieces Final-grade: decode each piece with
the Final's own audio treatment (pre-roll in front of the first, the family's
tail padding after each, the cut at a quiet window), keep their text, and let
the Final decode only the last piece. The 28 s cap (#48) already does this
once per utterance and left Ultra's WER unchanged and turbo's better
(`2026-10-utterance-cap.md`).

**Stage.** Final decode (long utterances only).

**Expected gain.** Final cost becomes the last piece's: about 0.5 s on the
8a whatever the length, instead of 0.08 × length. With #1 the whole wait
collapses to the silence rule for every length. Partials also get cheaper
once the utterance is longer than one piece (no 6 s re-decode).

**Risk.** Accuracy: a piece boundary loses right context at the cut. #48's
single cut at a quiet window measured as harmless for Ultra on 36–52 s
clips; several cuts every 6–8 s on normal speech is a different test. Not
for Whisper: each piece would pay its own 10 s of zero padding.

**Tried before?** Not as a Final strategy. The partial cut exists; #48 cut
at the cap only. The issue's "incremental encoder caching" is the same goal;
this is the version that works with a non-causal encoder (Parakeet's full
attention makes true encoder caching inexact).

**Measurement.** Paired WER/CER (`asrbench.paired`) on LibriSpeech clean and
other, AMI, FLEURS en/de/es/fr/ja, pieces vs whole, 6 s and 8 s pieces;
`asrbench.latency` end of speech → Final vs length.

### 4. First partial sooner: fire it at onset, and shrink the mic hop

**What changes.** (a) `lastPartialAt` is set to the onset time, so the first
partial waits a full `PARTIAL_INTERVAL_MS` (200 ms) after Silero already
spent about 0.3 s + 0.25 s `minSpeech` recognising the onset; start it on the
next hop instead. (b) Mic hops of 100 ms gate the VAD, the partial cadence,
and the segment close; 32 ms hops (one Silero window) or 50 ms cut the
average 50 ms of hop latency from every stage.

**Stage.** Partials (first partial), and a little off end-of-speech.

**Expected gain.** First partial 0.5–0.7 s → about 0.3–0.45 s after onset
on the 8a; every Final and every partial about 50–70 ms sooner.

**Risk.** None to accuracy. CPU: more coroutine wake-ups (negligible);
`AudioRecord` reads of 512 samples are routine. `EarlyMic`'s 60 s cap is in
hops, so scale `MAX_QUEUED_HOPS`.

**Tried before?** No. The issue's "smaller chunks for the first partial".

**Measurement.** The "first partial … after onset" log line over 20
utterances per engine; `listen` line `final=`.

### 5. Threads and placement with the app in front, and a Pixel 6a sweep

**What changes.** Nothing yet: a measurement gap. Every thread and pinning
result (#34, #31) came from the benchmark service, which Android keeps on
cores 0–7: no Cortex-X3 and no top-app boost. The 5-thread cliff was
measured with the fifth thread forced onto an A510. In front, 5 threads (4
A715 + X3) might beat 4, and pinning gave Ultra 8 % even there. The 6a
(Tensor G1: 2 X1, 2 A76, 4 A55) has never been swept: `fastCores` gives it
the X1s and A76s as one group of 4, and an A76 is well under an X1, so ORT's
sync points may wait on the slow pair; 2 threads on the X1s or 3 may win.

**Stage.** Every decode (load too).

**Expected gain.** Bounded by the 3→4 thread step (1.14–1.26×): perhaps
10–20 % of decode time on the 8a if 5 threads win in front; unknown on the 6a,
possibly more because its clusters are more uneven.

**Risk.** Heat: 5 threads is more total CPU. No accuracy risk (transcripts
are thread-count independent).

**Tried before?** Threads yes, but only in the service cpuset; the ORT doc
says so under "Not measured". Pinning was rejected for Nemotron (−28 %) but
helped Ultra; a per-family choice was never considered.

**Measurement.** A developer override for threads and pinning (there is one
for tuning already: `OrtTuning.PRESETS`), then the "final decode N ms for M
ms of audio" line from real dictation on the 6a and 8a with the keyboard in
front, 10 utterances per setting, charger, screen on both times.

### 6. Nemotron: pad only to the window boundary (streaming engines already heard the pause)

**What changes.** `streamingTailPaddingSamples` feeds chunk + 0.2 s (1.32 s)
of zeros before `inputFinished`, measured on the host with about 1 s of
faint noise before stop. In the app the stream has already consumed the
real 1.2 s pause before the segment closes. sherpa decodes whole 121-frame
windows, so what the ending needs is the *current window completed*, not a
fixed 1.32 s: pad to the next window boundary (0 to 1.21 s, average 0.6 s).

**Stage.** Final decode for the streaming family.

**Expected gain.** About one window decode less per utterance end: 1.2 s of
audio at RTF 0.2 is about 0.25 s on the 8a, 0.4–0.5 s on the 6a. Nemotron's
Final is the one place #1 can't help (a stream can't be forked and rolled
back), so this is its lever.

**Risk.** Dropped endings if the arithmetic is off by a window; the host sets
with `tail_drop` counts exist to check it.

**Tried before?** Padding was measured at 0.7 and 1.3 s only
(`2026-09-nemotron-chunk.md`).

**Measurement.** Host `online_session` with the app's 1.2 s silence actually
fed, `tail_drop` and WER on ls-clean/other, AMI, FLEURS de/fr, padding =
to-boundary vs 1.32 s; Final latency from `SttTiming` on the 6a.

### 7. Smart Turn: an early end only at very high confidence

**What changes.** Normal asks the model first at 1.2 s because at 0.5 s it
called 11–41 % of mid-sentence pauses finished at threshold 0.7 (#64). Ask
at 0.5 s too, but end only above a much higher threshold (0.95–0.98); the
1.2 s/0.7 rule stays as the second gate.

**Stage.** End-of-speech wait (Smart Turn surfaces: the popup and other
apps' recognizer).

**Expected gain.** For the sentences the model is sure about, the turn ends
at about 0.6 s instead of 1.3 s (the Quick vs Normal difference). The share
of sentences that reach 0.95 at a 0.5 s pause is the unknown.

**Risk.** Cut-offs in one-shot requests cost the whole request. #43 found
"a 0.2 s pause was scored finished as often as a long one", which says the
model's probability isn't calibrated to pause length; the high threshold
may not separate them either. The harness decides cheaply.

**Tried before?** Threshold 0.6 vs 0.7 only (#43); never a high threshold
at a short propose.

**Measurement.** `asrbench.turn_defaults` sweep: propose 0.5 s, thresholds
0.9 / 0.95 / 0.98, fall back to the Normal rule; keep it only if cut-offs
stay at Pause 1.2 s's level (0–4 %, ja 13 %) in every language set. Also
cheap and separate: the 8 s window's log-mel in `WhisperFeatures` is
recomputed per check (host 19 ms, phone likely 50–100 ms on the consumer
thread); compute frames incrementally per hop.

### 8. The slow engines: show turbo's tokens as they decode; the known levers

**What changes.** Whisper turbo has no partials and its Final takes 6–12 s;
Qwen3 similar. A sherpa patch that reports each decoded token to the
session (a callback or a polled result on the stream) lets the composing
region fill word by word during the Final, which was candidate 4 of #46 and
never tried. Combined with #1 the decode starts 1.2 s earlier too. The
measured levers stay as documented: ACFT turbo (0.5–0.75× the wait up to
10 s on the 8a, `2026-10-turbo-faster.md`), the 8-bit LM head (a download
saving), Qwen3's last-position LM head (−585 MB, keeps the arena:
`2026-10-qwen3-speed.md`).

**Stage.** Final decode (perceived), end-of-speech wait.

**Expected gain.** Perceived only for the token stream: the first word
appears about one encoder pass after the silence (turbo: 0.4–0.9 s on the
host, several seconds on a phone) instead of after the whole decode.

**Risk.** A looping decode shows its loop live; the token stream must go
through `cleanPartial`. No accuracy change.

**Measurement.** Time to first shown token vs time to Final on the 6a; a
small user check that live tokens read better than a spinner.

### 9. Arm int8 kernels: which path runs, and a QDQ trial for one encoder

**What changes.** Our graphs are dynamic int8 (`MatMulInteger`), served by
ORT's MLAS. Tensor G3's A715/X3 have i8mm (SMMLA); G1's X1/A76 have only
dotprod. Whether MLAS picks its SMMLA kernel on the G3, and whether the
int8 path is the bottleneck at all, is unmeasured. The TTS app's hardware
doc notes the 20–30 % int8 gains reported for Arm come from XNNPACK with
KleidiAI, which needs QDQ (static) quantisation, not dynamic.

**Stage.** Every decode.

**Expected gain.** Unknown; the realistic range on the encoder is 0–25 %.
The GPU and NPU are closed doors on these phones (TTS doc: Tensor NPU SDK
is G5 only, NNAPI deprecated and slower, Vulkan a loss on small graphs).

**Risk.** Static quantisation changes transcripts (calibration), XNNPACK on
QDQ crashed in ORT 1.26 for TTS, and it is our own conversion to host.
Large effort for an uncertain gain: last of the real ideas.

**Tried before?** XNNPACK with the dynamic graphs: takes no node (#31).
QDQ: never for STT.

**Measurement.** `simpleperf record` on a decode in the app (symbols of
`libonnxruntime.so`): share of time in `MlasGemm*` and which variant. Then
a QDQ export of 110M's encoder only, A/B RTF and paired WER on the 8a.

### 10. Cold start on the keyboard: what is left, and a draft engine during the load

**What changes.** The keyboard's first text after a cold process is the
load (Ultra 2.7–3.7 s on the 6a) plus the first partial. #78 took the mic
open and the holds; `.ort` and prepacking-off were measured and rejected for
Ultra. Two things remain unmeasured: the process start before
`startListening` (`ListenTiming` logs `process=` age but the surface pass
counted from the request), and ORT's file read for the 652 MB encoder
(mmap vs read; the TTS app notes ORT mmaps by path on arm64). The only
large remaining lever is a model already resident: Keep engine ready (720
MB idle, off by default). A cheaper variant: when 110M is installed, keep
*it* resident (≈ 400 MB) and let it decode partials during Ultra's load, so
text appears at about 1.5 s instead of 3.5 s; Ultra's Final replaces it.

**Stage.** Load, first partial.

**Expected gain.** Process start: unknown, maybe 0.3–0.8 s on a cold IME
process (worth knowing). The draft engine: about 2 s off the first shown
text on cold starts, English only, at 400 MB resident.

**Risk.** Memory on 4 GB phones; the draft's text jumps when the Final
replaces it (110M and Ultra agree closely in English, unlike the Whisper
drafts #46 rejected for turbo's languages).

**Measurement.** `listen` lines with `process=` under 2 s vs over, mic and
partial deltas; `strace -e openat,mmap,read` of the load on the 6a to see
how ORT reads the file.

## Not worth re-trying (measured, with the doc)

- Session options, graph optimisation level, prepacking off, XNNPACK,
  spinning off, memory arena off by default: `2026-10-pixel8a-ort.md`,
  `2026-10-ort-load/README.md`, `2026-10-pixel8a-mem.md`.
- Per-shape warm-up ladders: with the arena on, first decodes of new
  lengths run at the warm RTF already (0.072–0.079 vs 0.068–0.072, #47).
- Whisper: a shorter encoder tail (loops), draft partials from small
  Whispers, 4-bit weights, an encoder/decoder thread split:
  `2026-10-turbo-speed.md`, `2026-10-turbo-faster.md`.
- Overlapping `:textproc` with the decode: the cleanup is milliseconds of
  pure Kotlin; nothing to overlap.
- Lowering the pause slider: 0.5 s cuts off one sentence in five (#64).
  The right move is to make the wait *free* (#1), not shorter.

## Things in the code that look like plain latency bugs

1. **Partials keep decoding through the silence** (#2 above): identical
   audio decoded 2–5 times per utterance end, and the Final waits behind
   the last one. `StreamingSession.consume`, the partial condition
   `livePartials && isSpeechStarted`.
2. **The first partial waits 200 ms after onset for no reason**
   (`lastPartialAt = nowMs()` at onset; #4 above).
3. **Nemotron's fixed 1.32 s tail padding is decoded on top of the 1.2 s of
   real pause the stream already received** (#6 above).
4. Smaller, worth a timestamp each: the one-shot surfaces return `results`
   only after the engine's flow ends (mic close + queue drain after the
   Final), and the keyboard's auto modes switch back the same way. Likely
   tens of milliseconds; one `SttTiming` mark at the Final and one at the
   flow's end would say.

## Measurement plan, in order

1. Instrument first (log-only): segment-closed timestamp, candidate/Final
   decode start and end, flow end, process age. One `listen` line already
   carries most stages; add the three marks.
2. Baseline on the 6a: 10 turns × {110M, Ultra, turbo} × {keyboard, popup,
   Dictate}, charger, with the three marks. This is the #91 Phase 1 table.
3. Prototype #1 + #2 behind a developer flag; same table. Expected: Ultra
   and 110M end of speech → Final at about 1.25–1.35 s for utterances up to
   ~10 s, no text changes (hash parity on every turn).
4. #4 (trivial), then #5 (measurement only), then #6 and #3 on the host
   first (paired WER), the phone second.
5. #7, #8, #9, #10 as time allows; each has its own kill criterion above.
