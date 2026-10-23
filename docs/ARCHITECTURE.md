# Marmalade STT — architecture (stage 1)

Offline, private speech-to-text for Android. It works as the voice-input engine
for FUTO Keyboard, for any keyboard whose mic key uses Android's voice-IME
switch or speech input (the README's "Works with" table lists which, #61), and
for apps that call Android speech recognition. Sibling of
Marmalade TTS (`marmalade-tts-android`, the UI and scaffolding source). The engine is
ported from the Marmalade Android client (the Marmalade monorepo's `apps/android`).
Background research: `docs/RESEARCH-2026-09-22.md`.

## Decisions (settled; change them only deliberately, with the reasons recorded here)

| # | Decision | Why |
|---|---|---|
| D1 | applicationId / package root `app.marmalade.stt` | Matches `app.marmalade.tts`. Can change until first public release. |
| D2 | License **MIT**; the APK must stay free of GPL/LGPL | TTS APK is GPL only because of espeak-ng; STT needs no espeak. |
| D3 | **sherpa-onnx built from source**, linked against `com.microsoft.onnxruntime:onnxruntime-android` from Maven. **Never use the prebuilt sherpa-onnx AAR.** As built (#2): sherpa-onnx v1.13.8 and its 8 FetchContent dependencies are pinned shallow submodules in `third_party/`. `app/src/main/cpp/CMakeLists.txt` builds fully offline (`FETCHCONTENT_FULLY_DISCONNECTED`) with TTS, diarization, the C API and the binaries all off. ORT 1.28.0 headers and .so files come from the Maven AAR (Gradle `extractOnnxRuntime`). `check<Variant>NativeLicenses` fails the build on any espeak/piper string in a packaged .so. Kotlin API wrappers are vendored in `com.k2fsa.sherpa.onnx`, unmodified except `OfflineStream.cancel` (patch 0003). Since #37, unreleased upstream fixes are patch files in `third_party/patches/sherpa-onnx/` (upstream #3965, Whisper's byte-level BPE character loss; our own removal of the Whisper decoder's 6-tokens-per-second cap; #49, our cancellable offline decodes; and #54, a cancel that also stops the encoder run, and a per-stream Whisper token budget; and #78, Whisper's, Canary's and Qwen3-ASR's ONNX Runtime sessions and Qwen3's tokenizer created on two threads at once), applied by CMake to a copy of the sources in the build tree; the host benchmark's `setup.sh` applies the same files. Since #53, kaldi-native-fbank gets the same treatment (`third_party/patches/kaldi-native-fbank/`: a real FFT for Whisper's log-mel), and sherpa's `FetchContent` builds the patched copy. | The prebuilt AAR statically links espeak-ng (GPL-3.0); verified 2026-09-22 with `strings` on `libsherpa-onnx-jni.so` from 1.12.32. Building from source is also what F-Droid wants. |
| D4 | Multi-engine from day one: an `SttEngine` interface plus a catalog, as in the TTS app. Stage 1 ships one engine: **Whisper distil-small.en int8** (the proven Marmalade engine). As built in #13: five engine families, the stage-2 candidates as hidden developer engines, and an A/B benchmark (see "Engine families"). #27 made **Parakeet TDT 0.6B v3** the default, with a per-phone recommendation (see "Recommendation"). Since #45 (Max, 2026-10-02) the user engines are **Parakeet Ultra** (the default), Parakeet TDT_CTC 110M (the light one), Parakeet TDT_CTC 0.6B ja (Japanese, since #60: Max accepted ReazonSpeech-trained models, `docs/benchmarks/2026-10-japanese.md`), Canary 180M Flash, Nemotron 3.5 Streaming (since #37, Max's decision "A" on `docs/benchmarks/2026-10-nemotron-decision.md`: only its 1120 ms export, and only for Hindi, Arabic and Vietnamese; see "Nemotron 3.5 chunk size") Whisper Large v3 Turbo (for the languages the others don't cover) and, since Max approved its provenance on 2026-10-02, Qwen3-ASR 0.6B (Chinese and Korean in #50; all 30 of its languages since #88, Max 2026-10-17, still recommended for Chinese and Korean only); Granite is a developer engine. Every other engine left the app (see "Engines that left the app"). | Max wants multiple engines. Parakeet TDT 0.6b v3 and others come in stage 2. |
| D5 | Models are **downloaded, not bundled**: per-file downloads pinned by revision + sha256. Every engine's files are hosted as release assets on the public `maxwhipw/marmalade-stt-engines` repo: our own conversions (e.g. Parakeet Ultra; Max, 2026-09-28) and, since #75, the third-party exports re-hosted unchanged (no downloads from Hugging Face; Max, 2026-10-08). **Bundled exceptions:** Silero VAD (~0.6 MB, MIT), the Granite formatter (~7.5 MB, #33, Max-approved 2026-09-28), and Smart Turn v3.2 (~8.7 MB, BSD-2; Max-approved 2026-09-28, #44). | Marmalade's bundled model made a ~400 MB APK. VAD is tiny and required for every engine. |
| D6 | Three Android entry points: **voice IME** (`imeSubtypeMode="voice"`, returns via `switchToPreviousInputMethod()`), **RecognizerIntent activity** (`ACTION_RECOGNIZE_SPEECH`), **RecognitionService**. | Voice IME is what FUTO Keyboard's external voice input uses (0.1.30: Voice Input → "Disable built-in voice input"; verified on the emulator in #11), and what nearly every other keyboard's mic key opens (#61: AOSP, HeliBoard, OpenBoard, FlorisBoard, AnySoftKeyboard, Unexpected, Fossify and Thumb-Key verified on the emulator; `docs/RESEARCH-keyboards-2026-10.md`). AnySoftKeyboard falls back to `ACTION_RECOGNIZE_SPEECH` when no voice IME is on (verified, #61). The other two cover everything else. |
| D7 | Text post-processing is **rules only** in stage 1: a pure-Kotlin `:textproc` module. | Private, instant, testable. LLM rewrite = stage 2, opt-in. |
| D8 | Single `:app` module + pure-JVM `:textproc`. No KMP. Hilt + DataStore + Compose M3. Room only if a feature needs it. Marmalade theme from the TTS app, with its Settings → Appearance (#35). | Mirrors the TTS app. Marmalade's KMP split exists for desktop reasons that don't apply here. |
| D9 | `fdroid` / `play` flavors, versionCode scheme and reproducible-build flags copied from the TTS app. | F-Droid is a target. |
| D10 | INTERNET is used only to download models. No analytics, no crash reporting, audio is never stored. `allowBackup=false`. | Privacy is the product. |

## Stage-1 pipeline (ported from Marmalade, `WhisperStreamingEngine.kt`)

As built in #4 (`app/.../speech/StreamingSession.kt`):

1. **Mic:** `AudioRecord(VOICE_RECOGNITION)` at 16 kHz mono, with platform
   NoiseSuppressor/AGC when available; 100 ms reads. Each hop also emits a
   `Level` event (dBFS) for meters and `onRmsChanged`. The mic opens the
   moment the engine claims the session, before anything slow (#78): the
   model load (2–3 s for Parakeet Ultra on a Pixel 6a with Keep engine
   ready off, the default), the VAD, a warm-up decode in progress and Smart
   Turn's first load all come after it. `SttEvent.Listening` says so, and
   the surfaces show "listening" with a moving meter. Until the model is
   ready, `EarlyMic` keeps the hops in memory (at most 60 s; the oldest
   dropped past that, counted in the log) and sends their `Level` from the
   raw hop; the session then reads the kept hops first, without repeating
   their levels, and goes on with live ones, so words spoken during the
   load are transcribed. Before #78 the mic opened only after the load and
   those first words were lost (Max, FUTO Keyboard's mic key).
2. **Filtering:** 100 Hz Butterworth high-pass filter. When the platform
   AGC isn't running (`MicCapture.hasPlatformAgc` false), `SoftAgc` follows:
   a hop-RMS envelope sets 0 to +30 dB of gain towards −20 dBFS (instant
   attack, 3 dB/s release, peaks ≤ −1 dBFS, full gain at mic open). The
   flat VOICE_RECOGNITION path delivers arm's-length dictation around −40 to
   −55 dBFS, where Silero finds onsets late and holds segments open; #15
   measured WER roughly halving on such input with it (FLEURS-en, Parakeet
   16.1 → 8.0). Level, the VAD and the models see the gained audio, as they
   would with a platform AGC. Near-silence does not (step 7): the session
   keeps each hop from before the gain too (`MicHop.preGain`).
3. **VAD:** Silero, threshold 0.5, minimum speech 0.25 s. The minimum-silence
   slider runs 0.3–1.5 s, default 1.2 s (0.9 s before #43). Utterances are capped at **28 s**
   (Marmalade used 60 s). sherpa-onnx's Whisper decodes at most 29.5 s and
   drops the rest (`offline-recognizer-whisper-impl.h` truncates the
   features). Silero's `max_speech_duration` is not a hard cut: past it,
   Silero only ends the segment at the next 0.1 s pause at threshold 0.9,
   so continuous speech made a 30.6 s segment (#47). The offline session
   therefore cuts it itself (#48, `UtteranceCap`, shared with the
   benchmarks' splitter `vadUtterances`): an utterance still going at the
   cap (28 s + the 0.6 s pre-roll, `MAX_DECODE_SAMPLES`, less one hop's
   headroom) is decoded up to its quietest VAD window in the last 1.5 s and
   handled as a finished utterance: a Final in Pause, kept in Tap stop; End
   word holds it and matches the end word only at the utterance's real end
   (`FinishedUtterances.cut`), so a cut never ends the dictation. The rest
   goes on as the next one. When Silero closes the segment that was cut,
   only its part after the cut is decoded, unless the VAD saw no speech
   window there or it is near-silent (Whisper invents text for such an
   edge); a short word ("…that's it") is still decoded. A later segment,
   even one starting a few windows before the cut, is a new utterance with
   the usual pre-roll. On 36–52 s clips
   this took turbo from 14 to 8 word errors (it had lost the end of a 32 s
   decode) and left Ultra unchanged (`docs/benchmarks/2026-10-utterance-cap.md`).
   Whisper in Hindi is cut at 21 s instead (`UtteranceCap.maxDecodeSamples`,
   #54): there its token budget (step 6) would pass the 443 tokens Whisper's
   decoder holds. A Smart Turn's 28 s shrinks with it.
4. **Pre-roll:** audio from before the VAD's onset is kept: 0.6 s for the
   offline families, 1.0 s for streaming (`PREROLL_SAMPLES` in each session;
   #15 measured both). While nobody is speaking, the buffer is trimmed to
   that pre-roll. Silero's own segment starts only ~0.3 s before it detects
   speech, and at mic open (cold model state) it detects late enough that its
   segment starts up to ~0.3 s into the first word. So a Final never decodes
   the bare segment.
   Don't lengthen the offline pre-roll without the harness's regression
   check (`python -m asrbench.regress`, #18): at 0.8 s, Parakeet returned
   nothing on 14 of 200 AMI clips (see Parakeet under "Engine families").
5. **Partials:** during speech, re-decode the audio since onset: the first
   at the onset itself (#91), then at most every 200 ms and never sooner
   than the last partial's decode took.
   A decode can't be interrupted, so partials must never stand in front of
   a Final (#20: on the Pixel 8a a 22 s utterance's Final came 14.7 s after
   the speech ended). Each hop drains the VAD's closed segments first, and a
   partial decodes at most 6 s: once the utterance is longer, the audio up
   to the quietest VAD window 2.5–0.5 s before its end is transcribed once
   and that text kept in front of later partials. Whisper's
   trailing punctuation is stripped from partials (it punctuates every
   chunk as a finished sentence, so the full stop would flicker). Finals
   keep it. With utterances kept for a later Final (Tap stop, End word)
   only the utterance in progress is stripped.
   Each partial of the utterance in progress then goes through a
   `PartialStabilizer` (#28, LocalAgreement-2 as in whisper_streaming):
   words that two partials in a row agree on become stable and are never
   taken back, and only the latest partial's tail after them can change.
   A partial's last word is never made stable (its audio is usually cut
   mid-word), and a word or 2–3 word block repeated 3+ times at the end
   is cut to one copy first (`Phantoms.cutTailRepeats`), so a small
   Whisper's "test test test" can't become stable. A new partial's tail
   starts where the stable words line up with it by word edit distance.
   The Final replaces the partial, so none of this changes the finished
   text; on screen, a word made stable too early stays until the Final.
   The UI cleans partials of decoder loops and noise captions
   (`TextPipeline.cleanPartial`, #19; not the tail-repeat cut, which would
   take back stable words); the rest of `:textproc` runs on Finals only,
   and Finals keep the 6-repeat loop rule.
6. **Final:** at each VAD segment end, decode the pre-roll followed by that
   segment (`withPreroll`; the segment keeps its clean, trimmed end), then
   reset the buffer. The pre-roll never reaches back past the previous
   utterance. The reset keeps each decode inside Whisper's 30 s window
   (28 s cap + 0.6 s pre-roll); the Marmalade note claiming audio
   accumulates across segments was wrong. On stop, the VAD is flushed so the
   utterance in progress becomes a segment and is decoded the same way.
   Cancelling instead (the collector goes away: Cancel, a dismissed
   keyboard) starts no further decode and stops the one in progress at its
   next decoder step (`OfflineTranscriber.cancel`, sherpa patch 0003, #49:
   Whisper, Canary and Qwen3-ASR), or inside its encoder run (patch 0004,
   #54: ONNX Runtime's terminate flag; 0–72 ms on the host instead of up to
   1.8 s), so the engine is free for the next listen instead of answering BUSY
   while a runaway Whisper decode runs its 224 steps (8–10 s on the host,
   roughly 30–40 s on the Pixel 8a; `docs/benchmarks/2026-10-whisper-runaway.md`).
   Whisper's 224-token limit is replaced per language by a token budget,
   ceil(k·s) + c for s seconds of audio (`whisperMaxNewTokens`, sherpa patch
   0005, #54): English 6/s + 10 and Japanese 10/s + 10, so a runaway after
   Stop ends after a few dozen steps; Hindi 20/s + 10, past 224, so Hindi
   over 13 s no longer loses its end (FLEURS Hindi 31.0 → 30.8 WER, 11–35 s
   Hindi 39.3 → 31.5; `docs/benchmarks/2026-10-whisper-token-budget.md`).
   Other languages keep 224.
   Tap stop and End word join the segments, and Smart Turn decodes a whole
   turn at once (see "End of speech" below). The NeMo families (Parakeet) get 0.3 s
   of zeros after every decode (`NEMO_TAIL_PADDING_SAMPLES`, #25), as
   Whisper does through its own `tailPaddings`: without silence after the
   last word, Parakeet v3's int8 export drifted into English partway through
   German and Spanish sentences (see "Parakeet and the end of the audio").
   **The early VAD (#91):** a second Silero whose minimum silence is the
   main one's less whole windows, about 0.6 s (`EarlyVad`), so both end an
   utterance at the same sample. When it closes the utterance first, the
   session decodes at once exactly the audio the Final would decode there,
   and decodes no more partials until speech resumes. A stop in that
   silence (Tap stop) sends that text: the speech without the silence up
   to the tap. Tap stop sessions run it (`SpeedKnobs.earlyVadFor`). A
   cancelled or pre-empted session (#90) drops it with everything else.
7. **Near-silence:** each Final carries `nearSilent`, true when the RMS of
   the utterance's VAD segment (without the quiet pre-roll), measured
   **before** `SoftAgc`, is below `NearSilence.THRESHOLD_DB` (−50 dBFS,
   calibrated on raw Pixel 8a levels). Judged after the gain, quiet noise
   reads as speech and the phantom filter is off on phones without a
   platform AGC (#20). It also carries `silent`, below
   `NearSilence.SILENT_THRESHOLD_DB` (−58 dBFS: digital silence or the mic
   floor, under the quietest speech measured): only then does `:textproc`
   drop the ordinary phantoms a user may really say ("Thank you.",
   "Gracias.", #57). A Final that joins segments (Tap stop, End word, a
   Smart Turn) is near-silent or silent only if every joined segment was
   (`Quiet.and`).
8. **Lag (#30):** the mic queue is unbounded, so an engine slower than
   real time falls further and further behind the speaker. Each session's
   `LagMonitor` counts 100 ms hops captured against hops dealt with (VAD'd,
   and for `OnlineSession` decoded). Behind by more than 1.5 s it is
   *catching up* (until under 0.5 s): the offline session skips partials,
   and the streaming one only feeds the stream until the queue is drained,
   then decodes the backlog in one go and shows one partial. The UI hears
   of it (`SttEvent.CatchingUp`) only once it has lasted 1 s: any decode
   longer than 1.5 s (a long Final) leaves the session that far behind for
   the few milliseconds the VAD needs to drain the backlog. Still catching up 10 s later and at least 1 s
   further behind (10 % slower than real time), it reports
   `SttEvent.TooSlow` once per session. The Dictate screen shows "Catching
   up…" and a snackbar; `SpeechEngineManager.tooSlow` is the hook for the
   speed budget (#27). Offline, #20's partial gate already drains a backlog
   before the next partial (the `asrbench.latency` sim is the same with and
   without catch-up), so there catch-up is a guarantee rather than a
   change. For a streaming engine at RTF 1.2 (sim, `--streaming`) it cut
   end of speech → Final on ~19 s utterances from 10.2 to 6.6 s (median).
   `SttTiming` logs the lag (numbers only). The audio kept while the model
   loaded (#78) arrives in a burst but counts as captured from the
   session's start (`MicCapture.backlogHops`), so the session starts that
   far behind and catches up, and only a lag that grows from there is
   `TooSlow`; a long load alone never is.
9. **Text pipeline:** send the final text through `:textproc`
   (`TextPipeline.process(text, before, nearSilent, silent)`) before committing or
   returning it.

Audio lives only in memory: the utterance in progress, and what the mic
captured while the model loaded until the session has read it.

**End of speech (#43).** `SessionOptions.end` decides when text is
finalised. Each listening surface has its own (`ListenSurface`,
`SpeechPrefs.endFor`, `SpeechPrefs.sessionOptions(surface)`):

| Surface | End of speech | Choices | A Final ends listening? |
|---|---|---|---|
| Voice keyboard (`KEYBOARD`) | `keyboardEnd`, default **Tap to stop** | Tap to stop, Smart Turn, Pause, End word | In the auto modes (all but Tap to stop) the first committed Final acts as Done, unless locked |
| Dictate screen (`DICTATE`) | always **Pause** | none | Never: only the mic tap stops |
| Speak-now sheet + RecognitionService (`APPS`) | `appsEnd`, default **Smart Turn** | Smart Turn, Pause, End word | Yes: the request ends at the first end (#41), unless the sheet is locked |

The engine-level mode only decides when Finals come; whether a Final ends
the session is the surface's decision (`VoiceIme.actsAsDone`,
`OneShotSession`, `Dictation`), which is what keeps the lock simple. The
Dictate screen runs Pause, not Smart Turn, from the AMI data
(`docs/benchmarks/2026-09-smart-turn-ami.md`): Pause 1.2 s cut 12 % of long
sentences off mid-way, tuned Smart Turn 30 %, and there a cut only commits
text early, it never stops listening. A stored `appsEnd` outside its choices
reads as Smart Turn, so a one-shot request never runs Tap stop (no utterance
would end until the caller stopped, holding the mic). Patient mode (before
#43) isn't read: whoever had it on gets Tap to stop on the keyboard, the
default. The pause slider's default is **1.2 s**
(`SessionOptions.DEFAULT_MIN_SILENCE_SECONDS`; it was 0.9 s): only a moved
slider is stored, so everyone else follows the default and a stored value
is kept.

`FinishedUtterances` holds the per-mode text rules and `SmartTurnGate` the
Smart Turn schedule; both sessions (`StreamingSession`, `OnlineSession`)
use them.
- **Pause:** a Final per VAD segment; the VAD's minimum silence is the
  slider's.
- **Smart Turn:** the hybrid gate ported from marmalade-stt-cli
  (`streaming.py`), with its numbers from the user's **Patience**
  (`speech/SmartTurnGate.kt`, `enum class Patience(proposeSeconds,
  threshold, ceilingSeconds)`, Settings, default Normal). The AMI
  benchmark (#43) replaced the CLI's 0.2 s / 0.5 / 3 s (54 % of long
  sentences cut off) and its patience sweep picked Quick = 0.5 s / 0.7 /
  1.5 s and 1.2 s / 0.7 / 2.5 s as the patient end. #64
  (`docs/benchmarks/2026-10-smart-turn-defaults.md`) checked the defaults on
  FLEURS in the UI languages plus ko and ar, VoxPopuli in de/es/fr/it/en,
  and phone-like quiet pauses: asking the model after a 0.5 s pause cut off
  11-41 % of single sentences in every language, so **Normal = 1.2 s / 0.7
  / 2.5 s** (only pauses Pause 1.2 s would end, and only if the sentence
  sounds finished: Pause 1.2 s's cut-off rate or lower, finished sentences
  end at about 1.3 s), **Quick = 0.5 s / 0.7 / 1.5 s** (ends a finished
  sentence at about 0.6 s, cuts off more), **Relaxed = 1.5 s / 0.7 / 3 s**
  (for long thinking pauses). The VAD's minimum silence is the patience's propose pause, and
  a closed segment only *proposes* the end of the turn; it isn't decoded.
  The turn's audio stays in the session buffer from its onset minus the
  pre-roll. pipecat's smart-turn v3.2 (`speech/turn/OnnxTurnDetector`)
  scores the last 8 s of the turn up to now, trailing silence included (the
  model is trained on windows that end in silence, and each re-check then
  sees a longer pause). Probability above the threshold ends the turn;
  otherwise it is checked again after every further 0.5 s of silence, and
  the ceiling's silence ends it regardless. Speech first: the turn goes on
  (a new `SpeechStarted`, partials continue). An ended turn is decoded
  once, pre-roll to the end of its last segment, and sent as one Final, as
  Pause sends one utterance. A turn that grows to 28 s ends at its last
  segment boundary and the speech in progress starts the next turn
  (Whisper's window); one with no closed segment yet is cut at the cap as
  in step 3. No partials are decoded while a turn only waits in
  silence. `OnlineSession` keeps one stream open for the whole turn (fed
  the pause too) and flushes it when the turn ends; there a turn that has
  lasted 28 s ends at its next segment end. The check runs on the
  session's consumer thread (logged under `SttTiming`). If the model fails
  to load, `turnDetector` is null and the session is exactly Pause, with
  the slider's silence; a detector that fails mid-session ends turns at the
  slider's silence.
- **End word:** utterances are kept (as Tap stop) until one ends with an
  end word standing alone (`EndWords.match`, on each VAD segment's own
  text): the segment is just the word or phrase plus punctuation (a
  separate utterance after a pause, the path for engines that write no
  punctuation: Granite before its formatter, Nemotron), or the word comes
  right after sentence-final punctuation (. ! ?) and only punctuation
  follows ("…the bridge. Over."). Case-insensitive, whole words, phrases
  allowed. Then one Final (`endWord = true`) carries the kept text plus the
  text before the word, what is kept is cleared, and listening goes on.
  The word alone gives an empty end-word Final. Partials never trigger it:
  "over" at the end of a partial may be "over there" still being spoken.
  The VAD's minimum silence is the slider's.
- **Tap stop** (voice keyboard only): utterances are kept, shown as a
  Partial, and joined into one Final at stop.

Per surface, in detail. The **voice keyboard** commits every Final. In Tap
to stop the mic stays on until the user taps it and there is no 30 s idle
pause. In the auto modes the first Final that put text in the editor
(`ImeTextWriter.commitFinal` returns it; a filtered hallucination doesn't
count), or any end-word Final, takes the Done path (stop, then switch back
once the session has ended), and the idle pause runs
(`VoiceIme.actsAsDone`, `VoiceIme.idlePauses`). The Final a mic tap
flushes never acts as Done. **One-shot** requests end at the first Final
with text after `:textproc`, or at an end-word Final even when empty (the
request then ends with no match).

**Press and hold to lock (#43).** On the keyboard panel and the speak-now
sheet, in an auto mode, pressing and holding the mic locks the session: its
auto end is off. Keyboard (`VoiceIme.locking`, `VoiceImeState.locked`):
Finals still commit but none acts as Done, and the idle pause is off; a
long press while idle starts listening locked (`VoiceIme.started(locked =
true)`; `VoiceIme.configured` drops the lock in Tap to stop, where a long
press is just a tap). Sheet (`SpeechSheet.locking` + `OneShotSession.lock`):
Finals only join the text, the no-speech timeout is off, and the request
doesn't end on its own. A tap on the mic unlocks and stops: on the keyboard
like the mic tap (the Final lands, the panel stays), on the sheet like ✓
Done (the joined text is returned; a tap on the sheet's mic is Done
unlocked too). The RecognitionService has no UI, so no lock; the Dictate
screen never stops on its own, so none there either. Locked, the mic shows a
lock icon, the status line says "Locked: tap to stop" with a lock icon (a
polite live region, so TalkBack announces it), and the mic carries
`stateDescription` "Locked" and the long-press action label "Lock
listening" (Compose `combinedClickable`).

The Smart Turn model is bundled (a D5 exception, #44): the asset
`turn/smart-turn-v3.2-cpu.onnx`, pinned in `speech/turn/SmartTurnAsset`
(revision, sha256, size; `SmartTurnAssetTest` checks the committed file,
and the detector's goldens run on it). `SmartTurnModel` reads its bytes
once and keeps the detector while the app runs, so Smart Turn works on a
fresh install with no network. Before #44 it was a download under
`files/models/smart-turn-v3/`; `MarmaladeSttApplication` deletes that copy
at startup with every other id that left the catalog
(`ModelInstaller.removeUnknownModels`, see "Engines that left the app").

**Code map.**
- `speech/SttEngine.kt` is the contract every listening surface uses
  (`SttEvent`, `SessionOptions`). `SherpaSttEngine` implements it for every
  engine: one sherpa-onnx model plus the bundled Silero VAD, run by the
  session type its family needs (see "Engine families" below).
  `SttEngines.create` builds it from an installed catalog entry. A listen
  opens the mic first (`SttEvent.Listening`, `EarlyMic`, #78), then loads;
  the session's `SttEvent.Ready` means the model is loaded and the session
  runs. `stop()` closes the mic at once, even during the load, and the
  session still transcribes everything captured until then
  (`ListeningSession.stop` would drop the hops it hasn't read, so the
  engine doesn't call it).
- `SpeechEngineManager` hands out the active engine and applies the user's
  `SpeechSettings`. The active engine is the one picked on the Engines tab
  (`SpeechPrefs.activeEngine`), with its language hint; a new pick or
  language builds a new engine and frees the old one. It carries a stop that
  arrives before the engine claimed the session (repeated at `Listening`),
  and frees the model after a session when Keep engine ready is off and no
  surface holds it: `hold(preload)` returns a reference-counted
  `EngineHold` (`speech/EngineHolds.kt`, #78) that a surface keeps open
  while it is on screen. On phones of 6 GB or more (`totalMem` ≥ 5 GiB),
  "frees" means after a five-minute warm hold with no session or hold in
  between (#91, Max 2026-10-22, `WarmHold`): only the model stays (no mic,
  service or wake lock), so reopening the keyboard or popup within it skips
  the load. `onTrimMemory` at a pressure level (RUNNING_LOW, RUNNING_CRITICAL,
  MODERATE and up; not UI_HIDDEN or BACKGROUND, which come every time a
  surface goes away) frees it at once, and so does picking another engine,
  language or tuning. `listen(surface)`
  builds the surface's `SessionOptions`; when that surface's end is Smart
  Turn it hands over `loadTurnDetector`, which the engine runs once the mic
  is open (`SmartTurnModel`'s first load reads 8.7 MB; null if it failed to
  load). Each listen logs its stages from the user's tap (`ListenTiming`,
  `SttTiming`: `mic`, `ready`, first `level`, `speech`, `partial`, `final`).
  #91 added debug-level stage marks (each Final's decode and its wait, per
  session VAD and partial cost, `final sent`, per-surface `delivered`, Smart
  Turn features vs model). Debug builds can also feed every listen's mic from
  a clip (`debug/ClipMic.kt` through `MicOverride`) and A/B the prototype
  switches in `SpeedKnobs` (all off by default); see
  `docs/benchmarks/2026-10-speed-audit.md`.
- `install/SttEngineCatalog.kt` lists the engines. Each has a family (which
  runtime builds it) and files, each with a role, name, URL pinned to a
  revision, sha256 and size. When an engine's files change format (#78:
  `.onnx` to pre-optimised `.ort`, under a new tag), `replacingFiles` keeps
  the old list as its `previousFiles`.
- `ModelInstaller` downloads per file, with resume, into
  `files/models/<id>.partial/`, then renames that to `files/models/<id>/`.
  `.install.json` records each file's hash, so a catalog change shows as
  Corrupt, except that a record naming exactly the engine's `previousFiles`
  (#78) is Installed: `installedModel` hands back those files as the
  descriptor's (so `path(role)` and `modelFilePaths` load them) with
  `previousFormat = true`, a deep check hashes them, `install` leaves them
  alone and `uninstall` removes them. Nothing asks the user to move to the
  current files yet. An install first deletes staging files that aren't the
  current set's (a partial of the previous files). Partial downloads survive
  process death. It installs catalog
  engines only; `removeUnknownModels` deletes the files of an id that left the
  catalog. Every request goes to the catalog URL, so a GitHub release
  asset's short-lived signed redirect is fetched fresh for each resume. A
  dropped connection is retried within `install` after 2, 5, 10 and 20 s,
  resuming the `.part`; bytes gained since the last drop start the count
  again, and a body that ends early counts as a drop, not a damaged file.
  Only what a retry can fix is retried: a connection error, a wrong size,
  HTTP 408/429/5xx; 401/403 and other 4xx fail at once (`HttpStatusException`).
  Before the `.part` is touched, the size the server states (a 200's
  Content-Length, a 206's Content-Range total) must equal the catalog's,
  so a captive portal's login page can't truncate or pollute a partial; a
  416 (the server's file is shorter than the partial) deletes the `.part`
  and fails as CHECKSUM (#74, `ModelDownloadHttpTest` against a local
  server with single-use redirects).
- `ModelDownloads` runs installs in an app-wide scope and, on every start,
  starts `ModelDownloadService`, a `dataSync` foreground service that stays
  up while `ModelDownloads.running` isn't empty and shows the progress
  (#74). Without it Android 15+ cuts the app's network a few seconds after
  the screen turns off or the user leaves the app ("Software caused
  connection abort"; reproduced on the API 35 emulator): Max's Ultra
  download on 5G failed that way, and so did each Retry. The app doesn't
  ask for the notification permission, so on Android 13+ the notification
  stays hidden, but the service still runs. Android 15's 6-hour `dataSync`
  limit calls `onTimeout`, which pauses the downloads; so does a refused
  `startForeground` (`goForegroundOrPause`), since the network would go
  anyway.

**Settings and setup (#10).**
- `data/TextProcSettings.kt` is the cleanup-settings contract every listening
  surface builds its `TextPipeline` from (rebuilt when `config` emits).
  `DataStoreTextProcSettings` stores the switches as preference keys (unset =
  the `:textproc` default) and the dictionary and snippets as ordered JSON
  arrays (`TextProcJson`, kotlinx-serialization's JsonElement API). Its
  `config` also carries the cleanup language (#41,
  `SpeechPrefs.cleanupLanguage`): the active engine's language hint, else
  its only language (English for the `.en` engines), else Settings →
  Recognition language or the phone's. `:textproc` runs its English-only
  rules (fillers, spoken commands and punctuation, numbers as digits) only
  for English; Settings greys those switches out, marked "English only",
  while `config.isEnglish` is false (#42). Japanese has rules of its own
  (#60, `textproc/README.md`), for Finals with a kana or kanji in them only,
  so English heard on a Japanese phone stays English: 「。」 ends a Final
  without a mark, and no space goes between two Japanese characters. An
  engine that writes Latin in lower case only (`lowerCaseLatin`, Parakeet ja)
  gets short runs in capitals. The config also carries the active engine's
  recogniser family (`EngineFamily.recognizer`, #57), so only that engine's
  phantom phrases are checked against its text. What an engine writes for the
  noise before speech (`SttEngineDescriptor.isPreSpeechPhantom`: Parakeet ja's
  「うん」, #60; Whisper's distinctive phantoms and 「おわり」 and Qwen3's
  「システム」, #57) is held by `FinishedUtterances` when it is a session's
  first text and dropped when more speech follows. Dictionary and snippet triggers find word edges by script
  (#62, `PhraseMatcher`/`Words.Edge`): Chinese and Japanese ones match
  inside a sentence, a katakana one not inside a katakana run, a Korean one
  before particles (`Words.KOREAN_PARTICLES`) but not inside a longer word,
  and a script change is an edge ("gh" in 「ghのページ」; full-width ０-９ and
  Ａ-ｚ count as Latin). Settings cleanup (#42): the
  phantom filter is not a setting (always `NEAR_SILENT`; a pre-#42
  `cleanup_phantom_filter` value is ignored), and spoken punctuation
  ("Punctuation words") applies only while spoken commands ("Voice
  commands") is on: `toConfig` reports it off then, keeping the stored
  choice.
- `ui/screen/CleanupLists.kt` keeps one entry per left side or trigger,
  because `:textproc` uses the first of two and a later duplicate never fires.
- `ui/onboarding/` is the setup guide. `OnboardingFlow` (pure) holds the step
  logic: Welcome, Microphone, Model, Keyboard (turn on our voice IME), Mic
  key, Speech service (only where Android declares
  `android.software.voice_recognizers`; Android Go phones have no picker),
  Done. The Mic key step (#61) gives advice per enabled keyboard
  (`Keyboards`, pure: package → `KeyboardKind`, the keyboard in use first,
  voice-only input methods left out), warns about other enabled voice
  keyboards (a voice subtype and no keyboard subtype, never the keyboard in
  use; most mic keys open the first enabled voice IME, and
  `InputMethodManager.getShortcutInputMethodsAndSubtypes` puts system ones
  first, so an enabled Google voice typing wins), holds FUTO's card, and ends
  with a "Try it here" text field. That field is also the workaround for
  keyboards that declare no `<queries>` for input methods (OpenBoard,
  Thumb-Key, Fossify): Android hides our IME from them until they have typed
  into our app, and forgets that at every restart (verified on the emulator,
  #61). Our own manifest queries `android.view.InputMethod`, or the enabled
  list would leave other apps' keyboards out (also verified). The Speech
  service step reads `Settings.Secure` `voice_recognition_service`
  (hidden from the SDK, readable) and opens the Voice input picker's screen
  (`SpeechService`: Android 14+ `Settings$LanguageSettingsActivity`, checked on
  the emulator; 13 `Settings$LanguageAndInputSettingsActivity`; 9–12
  `ACTION_VOICE_INPUT_SETTINGS`; Settings' home as the fallback). The public
  `ACTION_VOICE_INPUT_SETTINGS` opens the Assist screen, which lost the
  picker in Android 13, and `android.settings.LANGUAGE_SETTINGS` matches two
  screens on 14+ (a chooser), so neither is used there. Android resets the
  setting when our app is force-stopped; the step says so.
- `ui/components/JarMascot` draws the animated jar on every frame while it
  moves, so it must not move forever on a screen that sits idle (#63: the
  setup guide and Dictate held the app at 50–75 % CPU on the emulator and
  starved its mic). An IDLE jar breathes twice, then draws only at each
  blink's start and end (6 frames per 10 s, about 1 % CPU). The Microphone
  step shows LISTENING for 4 s after the grant, then IDLE. The other states
  (LISTENING, THINKING) keep moving, so use them only while something is
  really happening. Moving jars draw at about 30 fps, whatever the display
  rate (`JarFrames`), so a listening jar takes less CPU from the decoder.
  With "Remove animations" on (`MotionDurationScale` 0), every state is drawn
  once, still.
  `FutoVoiceInput` is FUTO Keyboard's VoiceInputSwitch protocol. FUTO
  Keyboard 0.1.30, the release in September 2026 and still the latest on
  2026-10-03, doesn't have that activity yet, so the card shows the manual
  route (#11). The speech model step
  offers the engine recommended for the phone (`RecommendedEngine`, see
  "Recommendation" below) and selects it when its download finishes. It is
  the one step that can't be skipped (#74, Max: the Mic key step's "Try it
  here" box can't dictate without an engine): Next stays greyed out until
  that engine or any other is installed, and there is no "Skip setup". A
  saved step past it resumes on it while no engine is installed. The guide
  sets `SOFT_INPUT_ADJUST_RESIZE` while it is shown (`ResizeForKeyboard`):
  Android picked "pan" for MainActivity, and a tall keyboard (FUTO's on the
  Pixel 6a, #73) slid the top bar under the status bar. Leaving the guide
  sets "pan" again, always, and Settings opens the guide single-top, so a
  double tap can't stack two guides. The voice
  IME is found by component name `<package>/app.marmalade.stt.ime.VoiceInputService`.
  Its only subtype keeps `overridesImplicitlyEnabledSubtype` (`VoiceSubtypeTest`):
  without it Android enables an en_US voice subtype only on phones with
  English among their languages, and the mic keys would skip us elsewhere.
  `MainActivity` chooses the start destination once from `OnboardingSettings`.
- Settings order (#44, #42): Appearance, Speech, End of speech, Text
  cleanup, Support, About, Developer (last, out of everyone's way; no
  hidden opt-in). Within End of speech, Smart Turn patience shows only
  while a surface uses Smart Turn (`SpeechPrefs.usesSmartTurn`), the end
  words only while one uses an end word (`usesEndWord`), and the pause
  length always (the Dictate screen uses it). "Keep engine ready"
  (`keepModelLoaded`) is off by default since #42: the first session loads
  the engine, and it is freed once no listening surface is on screen,
  unless the user turned it on. While a surface is up it holds the engine
  (`SpeechEngineManager.hold`, #78), so its next session doesn't reload:
  the voice keyboard while its panel is shown (no blocker), the speak-now
  sheet from its first listen until it closes (Retry), and the Dictate
  screen while it is started, which also preloads (load plus the warm-up
  decode below) because its user is about to dictate. Closing the last hold
  frees the model, after the session if one runs; with Keep engine ready
  on, nothing frees it. The RecognitionService holds nothing (apps may stay
  bound to it for long), so each of its sessions loads and frees. A session
  that ends with no hold open frees the model as before. Holds are opened
  and closed on the main thread; the freeing runs on the manager's
  background scope and logs `Last hold closed: freeing <engine>`, a
  preload `A surface's hold preloads <engine>`. Either way the mic opens
  before the load and keeps what is said meanwhile (#78), so a load costs a
  later first word on screen, not the word. With
  it on, `SpeechEngineManager.warmUp` calls `SttEngine.warmUp` (#31): the
  load, then one 0.5 s silence decode outside the load lock. A session
  waiting for the load skips the decode; one that starts during it waits
  for that decode only, so the two never share the recognizer. Whisper
  skips it (its 30 s window makes it as costly as a real decode, no gain
  measured). A release during the load or the decode frees the model when
  it ends. An engine the manager forgets (another engine, language or
  tuning picked, files about to change) is dropped for good
  (`SttEngine.drop`): a load under way frees its model, a later warm-up
  loads nothing, and a session handed the engine just before still runs,
  then frees it. On the
  Pixel 8a it brought the first partial of Parakeet's first dictation down
  to the steady value (0.1–0.2 s). Support (`ui/screen/SupportSection.kt`,
  marmalade-tts-android's, both flavors) links out through `util/openUrl`:
  Support development (GitHub Sponsors), More Marmalade (Max's GitHub) and
  Report a bug (`BugReportUrl`). About keeps identity, privacy and licenses.
- Settings → Appearance (#35, marmalade-tts-android's): a colour preset
  (`ThemePreset`: Marmalade by default, Midnight, Forest, Berry, and
  Wallpaper, Material You, from Android 12) and a mode (`ThemeMode`: System,
  Light, Dark), stored by `data/AppearanceSettings` under the TTS app's
  keys. `MainActivity`, the voice keyboard and the speech sheet collect it
  and restyle live; `MainActivity` waits for it with the start destination,
  and keys the system bars' icons off the resolved light/dark. Marmalade,
  Midnight, Forest, Berry and Wallpaper are the shared design library's
  presets, drawn exactly as Marmalade TTS draws them (#87, below); High
  contrast (#68) is this app's own. The brand colours no Material role
  carries in both modes (Toast pills, the in-use outline, warm-ink links,
  the mic) are `MaterialTheme.accent` (`AccentColors`), per preset.
  `ThemePresetTest` checks that the shared presets equal the library's,
  that High contrast changes only its listed roles and meets AA, and that
  the accent colours read. Emulator screenshots of every preset in light
  and dark from before the library (Appearance, the Engines tab, the
  details dialog, the setup guide's card, the voice keyboard) are in
  `docs/screenshots/35/` (`index.html`).
- **Design library (#87).** The theme is the shared Marmalade design
  library, `marmalade-ui-android`, a git submodule at
  `third_party/marmalade-ui-android` built from source as the Gradle module
  `:marmalade-ui` (package `app.marmalade.ui`). It is Marmalade TTS's
  theme layer lifted as TTS has it (1.3.0 plus TTS #89's dark-mode AA
  fixes): the colour schemes and presets, the depth ramp
  (`MaterialTheme.depth`: page → card → raised, sheet, scrim; the ink ramp
  `dim` / `faint` / `ghost`; hairlines `line` / `line2`; soft CSS-style
  shadows, `Modifier.softShadow`), `MarmaladeMotion` (easing (0.2, 0.8,
  0.2, 1) at 150 / 200 / 320 ms, `riseIn`, `rememberReducedMotion`),
  shapes, typography, Manrope and Momo Trust Display, and the components
  `MarmaladeCard`, `MarmaladeSheet`, `MarmaladeMenu`, `chevronAngle`,
  `WorkingDots`, `LiveDot` and the stepped slider track. Rules: the
  library has no app code; this app's theme (`ui/theme/`) is a thin layer
  that calls the library's `MarmaladeTheme(colorScheme, darkTheme,
  wordmarkColor, depth)` hook with its own High contrast scheme (whose
  depth keeps the full-strength quiet text), its `AccentColors`, the focus
  ripple and its wordmark family (Momo from the library, Fredoka as the
  fallback). Where the app draws a style from a shared token, its contrast
  must equal TTS's: `TextContrastReportTest` checks that and writes the
  per-style table (`app/build/reports/text-contrast.json`), each row
  marked `shared`, `library` (a library change since TTS's own theme,
  260fc1d, that TTS gets when it moves its pin: the AA error reds) or `stt`.
  The app's own differences are small and listed there: links and text
  buttons in light mode are the preset's dark ink (#68), and so are light
  Midnight, Forest and Berry's section titles (their primary is below AA
  on cards); pills keep this app's preset colours; the dark Marmalade mic,
  progress bars and busy rings stay toast (#68 P2-B; Max, #87 round 3:
  `accent.progress`); meters keep their own 3:1 unlit outline. Max's round
  3 kept three things from before the library: the dark Dictate
  transcript sits on the page colour inside a hairline, the engine cards
  are main's outlined cards in light mode, and in dark mode the engine in
  use has only its outline, no wash (an engine card's outline is the pill
  colour, toast in Marmalade; an Other engines row's is `accent.ink`).
  Since round 3 the library's presets recolour the secondary and tertiary
  roles too (the working dots, a default progress track and the slider
  track carry no orange outside Marmalade; a dark preset's
  secondaryContainer is its dark tone, so a default track never matches
  its bar), the tab pill comes from the library's
  `marmaladeNavigationBarItemColors` (toast on the Marmalade palette,
  primary in a dark preset, 3:1 on the bar), and its error red is AA in both modes (Red 700 light, Red 400
  dark), so High contrast no longer overrides it. Move the pin in its own
  commit, naming the library commit and what changes on screen; the
  library's own `CHANGELOG.md` says what each commit brings.
- Polish (#84, TTS's #83 with Max's picks): the bottom bar has a hairline
  and the soft bar shadow, and on the Marmalade palette a toast tab pill;
  the selected tab's label is bold (#89, the library's
  `MarmaladeNavigationBarLabel`, still capped at 1.5× font scale); tabs crossfade, detail pages rise in, back only fades. Cards are the
  library's `MarmaladeCard` in both modes, except the light engine cards
  and the dark transcript (above). Settings, licences and the
  cleanup lists sit on the page with ink hairlines and faint row icons.
  Setup steps rise in, download bars glide, Check files and the setup
  guide's checks show `WorkingDots`; the mics keep their spinning ring
  (Max skipped the halo). The voice keyboard has a hairline along its top;
  the speak-now popup is the sheet step over the library's scrim with a
  hairline edge. The Pause length slider uses the library's stepped track.

**System entry points (#5, D6).**
- `ime/VoiceInputService` is the voice keyboard: an `InputMethodService`
  that hosts Compose itself (its own lifecycle, saved-state and ViewModel
  owners). `VoiceIme` (pure) is the panel's state machine. `ImeTextWriter`
  puts partials in the composing region and applies each Final as a
  `TextEdit` (`deleteSurroundingText` + `commitText`) against the 1000
  characters before the cursor. The keyboard listens only while shown, and
  `IdleTimeout` pauses it gracefully after 30 s without speech
  (`speech/ListenTimeouts`, next to the 6 s one-shot timeout) in the auto
  modes only, unlocked (#43, see "End of speech"); there the first Final
  also acts as Done. Done and Cancel call `switchToPreviousInputMethod()`, and so does hiding
  the panel any other way (Back, Home), unless the keyboard itself opened
  an activity. `MicPermissionActivity` asks for the mic, which an input
  method can't do itself.
- `recognizer/OneShotSession` is the shared "one request, one result"
  logic. The first utterance that leaves text after `:textproc` stops the
  engine. Nobody speaking for 6 s (`ListenTimeouts.NO_SPEECH_TIMEOUT_MS`)
  stops it too, and so does an end word (#43); the sheet's lock turns all
  three off. Tap stop never applies to one-shot requests (#41): both
  surfaces call `SpeechEngineManager.listen(ListenSurface.APPS)`, whose
  choices leave it out. With it no utterance would end until the caller
  stopped, and the no-speech timeout (cancelled once speech starts) could
  never fire again, so the mic stayed open. We chose
  this over a maximum listen time because callers of both APIs expect the
  answer at the first pause (as Google's recognizer gives it), and a cap
  would still hold the mic long after the user finished.
  `RecognizeSpeechActivity` (ACTION_RECOGNIZE_SPEECH, state machine
  `SpeechSheet`) and `MarmaladeRecognitionService` (SpeechRecognizer; codes
  in `RecognizerCodes`) build on it. The 6 s timeout, like the keyboard's
  idle pause, counts from `Ready` (the model loaded), not from the mic
  opening, so a slow load is never taken for silence (#78).
  `readyForSpeech` goes out once per request at `Listening`
  (`ReadyForSpeech`): the mic is open and what the user says is kept, even
  while the model still loads. All three surfaces leave "Loading model…"
  at `Listening`. A missing model is
  `ERROR_LANGUAGE_UNAVAILABLE` on Android 12+. A caller's `EXTRA_LANGUAGE`
  applies to its request only (#43, `SpeechPrefs.forRequestLanguage`): it
  counts as the chosen recognition language, so it is the engine's hint
  where the engine takes one (`SpeechEngineManager.listen(language =)`; an
  engine loads its hint, so a different one rebuilds it for that request)
  and the cleanup language (`LivePipeline.get(language)`). Since #90 it
  also picks the engine (`routeLanguage`, "Speech APIs" below). The stored
  settings don't change.
  The speak-now sheet shapes its request like the service
  (`RecognitionRequest`, `routeLanguage`; segmented sessions and
  `EXTRA_AUDIO_SOURCE` stay service-only); a language no installed engine
  transcribes is shown on the sheet without listening and returns
  `RESULT_CLIENT_ERROR` (#90). `LanguageDetailsReceiver` answers
  `ACTION_GET_LANGUAGE_DETAILS` sent to our package with the installed
  languages and the user's language. The gear Android shows next to the
  keyboard and the recognition service opens the Settings tab (the
  `SettingsActivity` alias of `MainActivity`).
- `speech/LivePipeline` keeps the `TextPipeline` for the current
  `TextProcSettings`. The three surfaces call `get()` for each Final.
- Debug builds add `debug/SpeechTestActivity`: a caller for all three entry
  points with a text field.


**Speech APIs (#90).** Every public Android speech-input API a third-party
recognizer can serve, and what we do with it. Requests are parsed once
(`recognizer/RecognitionRequest`); the service maps a session onto the
callbacks in `ServiceSession` and runs requests one at a time in
`ServiceRequests` (both framework-free, unit-tested); the debug
build logs every call and its extras (tag `RecognitionService`, never in
release). `tools/recognizer-probe/` is a separate-package test client that
drives all of it the way other apps do; `check.sh` there runs the
scenarios on a device and checks the outcomes.

*Root cause of Duolingo's "could not recognize the input" (GitHub #1):*
Android checks `RECORD_AUDIO` along the attribution chain, the proxy (us)
included, and our app is in the background whenever another app asks, so
every request from another app failed at once with
`ERROR_INSUFFICIENT_PERMISSIONS` (458–1103 ms on the API 35 emulator,
three runs, by component and as the default service). All earlier tests
drove the service from our own debug `SpeechTestActivity`, in our own
foreground process, which hid it. Android 12+ wants the service to create
an attribution context for the caller in `onStartListening` and open the
mic with it (`AudioRecord.Builder().setContext`); the framework then skips
its own check and blames the caller's foreground mic access. Android 9–11
have no attribution sources; there the framework binds the service from
the caller directly (not verified on those versions).

| API | Status | What we do / why not |
|---|---|---|
| `startListening` from another app | **added (#90)** | the mic is opened for the caller (above) |
| `readyForSpeech`, `beginningOfSpeech`, `rmsChanged`, `endOfSpeech` | supported | ready at `Listening` (#78); beginning once per request; rms on Google's -2..10 scale |
| `RESULTS_RECOGNITION` | supported | one hypothesis (two with formatting) |
| `CONFIDENCE_SCORES` | **added** | -1 per hypothesis, Android's "unavailable": sherpa-onnx's offline results carry no probabilities, and an app reading the array unguarded mustn't crash on a missing one. Also `EXTRA_CONFIDENCE_SCORES` on the sheet |
| `EXTRA_MAX_RESULTS` | **added** | a cap; we never have more than two hypotheses |
| `EXTRA_PARTIAL_RESULTS` | supported | `cleanPartial`ed partials, blank ones too (the last turned out to be noise) |
| `EXTRA_PREFER_OFFLINE` | n/a | always offline |
| `EXTRA_LANGUAGE` | **added: picks the engine** | the active engine if it transcribes the language, else the best installed one for it (`routeLanguage`: the recommender's near-best and preference order; the stored choice stays), else `ERROR_LANGUAGE_UNAVAILABLE` (an engine could be downloaded) or `ERROR_LANGUAGE_NOT_SUPPORTED` (none could); `ERROR_CLIENT` before Android 12. The sheet shows the same as a message. A language-learning app asks for the language being learned; running the active engine regardless would give it the wrong language's words (not seen in Duolingo's own calls: it couldn't be installed for #90) |
| `EXTRA_LANGUAGE_PREFERENCE`, `EXTRA_SUPPORTED_LANGUAGES`, `EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE` | **added** | the `ACTION_GET_LANGUAGE_DETAILS` answer below |
| `EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` | **added** | clamped to 0.3–5 s when the request is parsed. A plain request: Pause with that silence instead of the user's end of speech; a segmented one: the session ends after that pause (`OneShotEnd.Pause`, counted from the last Final) |
| `EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS` | **added** | at most 60 s. Both lengths are read as Long or Int only (typed on Android 13+, so a caller can't have a class of its choosing deserialised in our process); anything else, or a value that can't be read, counts as absent. No end of the session's own (first end, end word, timeout) before it; new speech cancels a held end |
| The user's own use while another app listens | **pre-empts (Max, 2026-10-21)** | starting the voice keyboard's mic, Dictate or the speak-now sheet while another app's RecognitionService request holds the engine (mic or stream) ends that request at once (`SessionArbiter`): it is cancelled, its mic or stream closes, and the app gets `ERROR_RECOGNIZER_BUSY`; what it had heard is dropped, segments already sent stay sent. BUSY rather than `ERROR_CLIENT`: Android documents BUSY as "RecognitionService busy" and apps already handle it for a second client (retry later), while CLIENT means the app misused the API. Google's recognizer has no such hand-over to copy: on the emulator, when our Dictate took the mic during its session it heard silence and ended at its own timeout (`ERROR_NO_MATCH`). A release deferred behind the pre-empted session (a surface's hold closed meanwhile) is undone (`SttEngine.keep`), so the model isn't unloaded and reloaded under the user. The user's session then starts without a busy error, and on the same engine without reloading its model. Never the reverse: another app's request gets BUSY while the user's own session starts or runs, before it picks an engine (routed to another engine or hint it would otherwise build a second engine beside the user's: two mics, two models) |
| How long a request may run | **bounded** | no caller holds the mic or the engine (the keyboard and Dictate would get busy) for ever: a request from the mic is stopped after 10 min (`RecognitionRequest.MAX_MIC_LENGTH_MS`; what was heard is still transcribed), whatever its extras asked; a stream by its audio (next row) |
| `EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS` | not honoured | Smart Turn and Pause decide when speech is complete; Android documents the extra as possibly ignored |
| `EXTRA_SEGMENTED_SESSION` (33+) | **added** | `onSegmentResults` per utterance, then `onEndOfSegmentedSession` (also with nothing heard); all three kinds. A spoken edit ("delete that") can't take back a segment already sent |
| `EXTRA_AUDIO_SOURCE` + `_CHANNEL_COUNT`/`_ENCODING`/`_SAMPLING_RATE` (33+) | **added** | `speech/StreamCapture`: 8/16/24/32-bit and float PCM, 1–8 channels, 8–192 kHz, resampled to 16 kHz in memory, never written. Runs to the end of the stream; read as fast as the engine consumes it (not live: no drops, no too-slow evidence). The stream also ends when we have waited 10 s for audio without 1 s of it arriving (a stalled writer, a pipe never closed, one trickling bytes to keep it open; only time waiting on the caller counts, not decoding), after 1 h of audio (`/dev/zero`), or 1 h after its first read whatever it does (decoding included: a 1 h file on an engine slower than real time is cut short, with what was decoded by then). So a live streamer that only writes while someone speaks is ended by 10 s of not writing: it should send its silence too. A read returning no bytes (a non-blocking descriptor) waits 20 ms before the next. What came before is transcribed. The descriptor unparcelled from the intent is ours: taken before anything copies the extras (a copy keeps the incoming parcel's duplicate open until GC; `RecognitionRequest.from` reads with the intent's accessors), and closed when the request ends, however it ends (`ServiceRequests`). Needs no mic permission. Other formats: `ERROR_AUDIO` |
| `EXTRA_ENABLE_FORMATTING` (33+) | **added** | we always format (`:textproc`); with the extra the lists are [formatted, raw]. Partials repeat the cleaned text as raw (the engine's own partial isn't kept) |
| `EXTRA_HIDE_PARTIAL_TRAILING_PUNCTUATION` (33+) | **added** | trailing `.,;:!?。、，！？…` cut from partials |
| `EXTRA_MASK_OFFENSIVE_WORDS` (33+, default true) | not supported | we never change what was said; no word lists |
| `EXTRA_BIASING_STRINGS` (33+) | deferred | needs sherpa-onnx hotwords (modified beam search, transducers only); none of our engines run that way |
| `onCheckRecognitionSupport` (33+) | **corrected** | installed = every installed engine's languages that may run (the active engine's first, the spoken language leading, the phone's full tag after its language); supported = what a download would add |
| `onTriggerModelDownload` + `ModelDownloadListener` (33/34+) | **added (answers only)** | `onSuccess` when an installed engine covers the language, else the language error. We don't download on another app's say-so: hundreds of MB, and downloads are the user's choice (D10). Pending languages aren't reported |
| `EXTRA_ENABLE_LANGUAGE_DETECTION` / `onLanguageDetection`, `EXTRA_ENABLE_LANGUAGE_SWITCH` (34+) | deferred | sherpa-onnx's detected language (Whisper's `lang`) isn't surfaced by our sessions, and Parakeet has none |
| `EXTRA_REQUEST_WORD_TIMING`, `EXTRA_REQUEST_WORD_CONFIDENCE`, `RECOGNITION_PARTS`, `RESULTS_ALTERNATIVES` | not supported | no per-word data reaches the session |
| `onBufferReceived` | not supported | optional; we don't send audio to apps |
| `ERROR_RECOGNIZER_BUSY` on a quick restart | **fixed** | a start right after `cancel` waits for the cancelled session to let go of the engine (a model load can't be interrupted); reproduced with cancel + start 1.5 s in. Our own busy check is gone: Android already turns a second session away, so a session of ours still running at a start is one it is done with (a dead client's cancel still on its way: seen once, under load), and it is cancelled and waited for. A request cancelled while it waits still ends after the session it waited for, so a third start waits for the real holder too |
| `ERROR_CLIENT` (start twice), `ERROR_RECOGNIZER_BUSY` (second client), `ERROR_SERVER_DISCONNECTED` (`destroy` then a new recognizer at once), `ERROR_TOO_MANY_REQUESTS` | framework | Android's own session bookkeeping sends these (one session per service); Google's recognizer gets the same on the emulator |
| `ERROR_CANNOT_CHECK_SUPPORT`, `ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS` | n/a | we implement both checks |
| The client dying mid-session | handled | Android 12+ calls our `onCancel`; a callback that fails sends an error first (which frees the framework's session) and aborts the mic |
| `ACTION_RECOGNIZE_SPEECH` | supported, **aligned** | the sheet; same request parsing and routing; `EXTRA_PROMPT` shown; `EXTRA_RESULTS_PENDINGINTENT` honoured; segmented sessions and audio sources stay service-only |
| `ACTION_GET_LANGUAGE_DETAILS` | **added** | `LanguageDetailsReceiver`, for broadcasts addressed to our package (unreadable settings answer `RESULT_CANCELED`, never a crash). `getVoiceDetailsIntent()` never finds it: it reads `DETAILS_META_DATA` from the `ACTION_WEB_SEARCH` activity |
| A routed request and Keep engine ready | handled | a request that `EXTRA_LANGUAGE` routed to another engine (or another language hint) loads it in the active engine's place; where one is kept loaded (Keep engine ready, or a surface's hold) the active engine is loaded again a minute after the last such request (`EngineHolds.routedSessionEnded`): the keyboard finds its engine ready, and a language app asking again soon finds its own still warm. The stored choice never changes. Accepted costs: a routed request arriving while the restore is loading the active engine loads its own beside it for a moment (two models at once, briefly); and an app alternating languages can make us load about two models a minute, only engines the user installed |
| `SpeechRecognizer.isRecognitionAvailable` | supported | true with us as the service (the probe checks it) |
| `recognition_service` `settingsActivity` | **fixed** | the gear opens the Settings tab (`SettingsActivity` alias), not Dictate; the voice keyboard's too |
| `ACTION_VOICE_SEARCH_HANDS_FREE`, `ACTION_WEB_SEARCH` | n/a | search apps' actions; we are not one, and registering would hijack web search |
| `createOnDeviceSpeechRecognizer` | n/a | binds the OEM's trusted on-device service (`config_defaultOnDeviceSpeechRecognitionService`), never a third-party one. Apps that use it don't reach us whatever the user picks |

**Engine families (D4 as built, #13).** The catalog's `EngineFamily` picks
the sherpa-onnx config and the session type:

| Family | Engines | sherpa-onnx | Session |
|---|---|---|---|
| `WHISPER` | Whisper Large v3 Turbo | `OfflineRecognizer`, whisper | `StreamingSession` |
| `NEMO_TRANSDUCER` | Parakeet Ultra (default, #45), Parakeet TDT_CTC 110M (TDT head) | `OfflineRecognizer`, `nemo_transducer`, 0.3 s tail padding | `StreamingSession` |
| `NEMO_CTC` | Parakeet TDT_CTC 0.6B ja (CTC head, #60) | `OfflineRecognizer`, nemo (CTC), 0.3 s tail padding | `StreamingSession` |
| `CANARY` | canary-180m-flash | `OfflineRecognizer`, canary (src = tgt language) | `StreamingSession` |
| `QWEN3_ASR` | Qwen3-ASR 0.6B | `OfflineRecognizer`, qwen3Asr (tokenizer files installed flat) | `StreamingSession` |
| `STREAMING_TRANSDUCER` | Nemotron 3.5 Streaming 1120 ms (hi/ar/vi) | `OnlineRecognizer`, transducer, endpointing off, chunk + 0.2 s tail padding | `OnlineSession` |
| `GRANITE_CTC` | Granite Speech 5.0 470M TurboCTC (#32) | none: ONNX Runtime's Java API (`GraniteOfflineTranscriber`) | `StreamingSession` |

- **Offline families** share the stage-1 pipeline above. `livePartials`
  turns the 200 ms re-decode off for the slow models (Qwen3-ASR, Whisper
  turbo): they show text when an utterance ends. **Parakeet re-decodes like
  Whisper.** Its encoder is non-autoregressive and the TDT decoder skips
  blank frames, so a whole-utterance decode should cost no more than a
  distil-small.en one (on the #13 emulator run it was faster: RTF 0.48 vs
  0.64; the Pixel run decides), and live partials keep dictation feeling the same as with the
  default. The Final still comes from the VAD segment. Finals-only would
  save CPU but leave the user staring at nothing for whole sentences.
- **Parakeet and leading silence (for #7).** (Parakeet v3 left the app in
  #45; Ultra, its post-trained successor, never returned empty text after
  silence on the host.) Parakeet TDT returns empty
  text when 0.3 s or more of silence (zeros or mic noise) comes right before
  speech that starts abruptly, mid-word (#15). Natural onsets after real
  silence decode fine (LibriSpeech, FLEURS, dictation). A longer pre-roll
  puts more silence in front of every Final: at 0.8 s it emptied 14 of 200
  AMI clips with a lead-in, at the app's 0.6 s 2. `tools/asr-bench`'s
  `asrbench.regress` decodes the affected clips with every offline NeMo
  engine and fails when one returns more empties than at the app's settings.
  Only v3 has the problem: Parakeet 110M, v2 and Unified returned text on
  every clip (#25). The phone pass for #7 should include starting to speak
  mid-word right after tapping the mic.
- **Parakeet and the end of the audio (#25).** Silero's segment ends where
  the speech ends, so a Final has no silence after its last word. Parakeet
  v3's int8 export then sometimes switches to English for the rest of a
  German sentence ("Denken Sie daran, dass…" → "Think that Festival…"):
  FLEURS-de WER was 16.2 through the app's pipeline, 7.0 for the whole clip
  and 5.9 with the fp32 export. Zeros after the audio fix it: 7.9 at 0.2 s,
  7.4 at 0.3 s. More is worse: from 0.5 s, AMI's empty results went 3 → 9
  (trailing silence can empty a Final too), and at 1 s French got worse. At
  0.3 s Spanish went 5.9 → 4.5, French 10.7 → 9.6, English unchanged or
  slightly better, and the leading-silence empties dropped (2 → 1, 3 → 2).
  `SherpaOfflineTranscriber` pads every NeMo decode, live partials included.
- **Granite (#32)** is the one engine sherpa-onnx can't run. `GraniteFrontend`
  is a Kotlin port of the harness's numpy front-end (512-point FFT, HTK mel,
  log floor, deltas, 2× stacking; held to Python golden vectors within 1e-5),
  the ONNX graph runs on ONNX Runtime's Java API (the same
  `libonnxruntime.so` sherpa-onnx links; `libonnxruntime4j_jni.so` ships
  since #32), and greedy CTC plus the byte-level BPE vocabulary from IBM's
  `tokenizer.json` make the text. It plugs into `OfflineTranscriber`, so
  sessions, partials and Finals are unchanged. Its text is lowercase with no
  punctuation, so every decode (partials and Finals) goes through the
  **Granite formatter (#33)**: our own 7.4M-parameter Transformer, bundled
  as `assets/granite/formatter.int8.onnx` (7.5 MB). `GraniteBpe` encodes the
  words with Granite's own `tokenizer.json`, the model labels each word
  (case, the mark after it, whether it's an expanded contraction to join
  back: "is" → "'s"), and `FormatLabels` applies the labels. It never adds,
  drops or respells a word. `FormatLabels` is the twin of
  `tools/asr-bench/granite/format_data.py`'s `apply`, which also makes the
  training pairs; change both together. Training and the candidates it
  beat: `docs/benchmarks/2026-09-granite-formatting.md`. `GraniteParityTest`
  compares it with the harness on real clips
  (needs the model; `tools/asr-bench/granite/dump_golden.py`). The int8
  graph's kernels differ by CPU, so near-tied words can differ between an
  AVX2 and an AVX-512 host, and presumably an ARM phone: the Python reference
  must run on the same machine as the test.
- **`OnlineSession`** (true streaming) shares the mic, high-pass, Level and
  error handling with `StreamingSession` (`micSession`). At each VAD onset
  it opens a new `OnlineStream`, feeds it the 1 s pre-roll and then every
  hop, and emits the model's running text as the Partial (not stripped: the
  model punctuates as it goes). The cache-aware encoder needs that much left
  context: with 0.4 s it dropped the first word of 15–20 % of utterances
  (#14/#15). Near mic open, or right after the previous utterance, less
  than 1 s was recorded before the onset, and silence makes up the rest.
  Without it, speech from the mic's first sample lost its first word two
  times in three. At the VAD segment end it feeds the chunk plus 0.2 s of
  zeros (`streamingTailPaddingSamples`: sherpa-onnx decodes only whole
  windows and `inputFinished` pads nothing, so up to a chunk of speech at the
  end was lost; 1120 ms dropped endings twice as often as 560 ms without it,
  2026-09-28), calls `inputFinished()` and decodes once more, which flushes
  the model's right context, then emits the Final and frees the stream. **Endpointing is
  Silero's, not the model's endpoint rules**, so the silence slider,
  SpeechStarted (which the idle and no-speech timeouts use), nearSilent and
  the end of dictation modes behave the same for every engine, and the 0.6B encoder runs
  only while someone speaks.
- **Language:** engines with `languageOptions` get a per-engine language
  setting (`SpeechPrefs.languageFor`). Whisper turbo, Nemotron 3.5 and
  Qwen3-ASR default to auto-detect; Canary has no auto and defaults to
  English; the English-only and self-detecting models (the Parakeets) take
  no hint. Nemotron and Qwen3-ASR read it as a per-stream option, Whisper and
  Canary from the model config.
- Every recognizer gets the same thread count on a device, so the
  benchmark compares models, not thread counts: the performance cores
  (every CPU cluster but the slowest), 2 to 4 (`RecognizerThreads`, #20;
  the Pixel 8a gets 4). On the host, 4 threads decoded 1.26× (distil) to
  1.5× (Parakeet) faster than 2. On the Pixel 8a, 4 was the measured best
  for Parakeet, and 5 or more (all its performance cores, or more) was
  1.7–1.8× slower. distil was flat from 2 to 8, Nemotron streaming the same
  at 3 and 4, so one count fits every family
  (`docs/benchmarks/2026-09-pixel8a-threads.md`, #34). Clusters come from
  each core's sysfs `cpu_capacity`, so unknown phones need no table (#31).
  When the capacities are missing or all equal (kernels without
  `capacity-dmips-mhz` report 1024 for every core), the cpufreq policies'
  top frequencies decide instead. `fastCores` lists every cluster but the
  slowest.
- **ONNX Runtime tuning (#31, `speech/OrtTuning.kt`).** The TTS app's
  options, as a developer choice (Settings → Developer → Runtime tuning) and
  a benchmark dimension; users get `OrtTuning.DEFAULT`: the CPU EP, spinning
  on, no pinning. The Pixel 8a decided
  (`docs/benchmarks/2026-10-pixel8a-ort.md`):
  - XNNPACK takes no node of any of our int8 graphs, so it's out of
    Settings (the benchmark keeps it).
  - Spinning off is 3–4× slower unless pinned, and pinned it only ties the
    default.
  - Pinning alone: Ultra +8 %, Nemotron −28 %.
  - **Memory arena (#47):** on (ORT's default), and off only on a phone an
    engine fits only without it (`SpeedBudget.runsLean`, applied by
    `OrtTuning.forPhone` when `SpeechEngineManager` builds the engine). The
    arena keeps the largest buffers a session ever needed and grows in steps,
    so a long utterance raises an offline engine's memory for good: on the
    Pixel at a 27 s utterance, Ultra's own memory is 1128–1522 MB with it
    (depending on the order of the lengths) and 895–993 MB without. But
    without it every decode of a new length allocates afresh, and
    dictation's utterances are almost always new lengths: first decodes are
    1.3× slower for Ultra and up to 1.6–2.1× for 110M, Canary and Qwen3.
    Transcripts are identical. Each engine's `LeanMode` in
    `SpeedBudget.REFERENCE` holds its memory and cost factor without the
    arena; the recommender (`SpeedBudget.forPhone`) and the cost tiers
    (`SpeechPrefs.engineCosts`, with the RAM stored at each recommendation)
    count an engine as the phone runs it. This, not a shorter cap or split
    decodes, is how a 4 GB phone gets Ultra
    (`docs/benchmarks/2026-10-pixel8a-mem.md`).

  Mechanics: sherpa-onnx v1.13.8 reads the provider string
  `<ep>:<file>` and forwards the file's `SessionConfig.<key>=<value>` lines
  to ORT, and `EnableCpuMemArena=0` disables the arena, so spinning and the
  arena need no patch. sherpa exits the process if the file is missing, so
  it is checked before each load and, if stale, written to a temporary file
  and renamed into place (concurrent loads never read half a file). A
  failed write falls back to the plain provider, logged as an error and
  counted (`OrtTuning.configFallbacks`). Granite's ORT Java session sets the
  arena with `setCPUArenaAllocator`.
  ORT compiles `session.intra_op_thread_affinities` out on Android, so
  pinning is our own `libmarmalade-cpu.so` (`cpp/cpu_affinity.c`,
  `CpuAffinity`): threads inherit their creator's affinity, so the
  recognizer is built on a pinned thread and each decode runs pinned.
  Android intersects it with the process's cpuset: the debug benchmark
  service gets cores 0–7 only (no Cortex-X3), the app in front all nine.
- The Kotlin API for `OnlineRecognizer` and `OnlineStream` is vendored,
  unmodified, next to the offline one; R8 keeps the whole package.

**Developer engines (#13).** Every engine outside the user lineup is a
developer engine (`SttEngineDescriptor.developer`): not offered on the
Engines tab, and never the active engine, unless Settings → Developer engines
is on (`SttEngineCatalog.runnable`). Turning the setting off falls back to
the recommendation (or the default) without forgetting the pick. Until #89
the engine recommended for the phone was an exception, and so was a
developer engine selected while it was the recommendation (a "kept"
selection, #50); both existed for Qwen3-ASR, and #89 removed them once it
was a user engine and the recommender picked only user engines. Since #45 they were Granite Speech
5.0 470M and Qwen3-ASR 0.6B; Qwen3-ASR became a user engine for Chinese
and Korean in #50 and for all 30 of its languages in #88, so Granite is
the only one left. Each has an ethics
review in `docs/model-reviews/` and a NOTICE / licenses-screen entry.
Every engine's files are release assets of the public
`maxwhipw/marmalade-stt-engines` repo (D5), one release per engine, pinned by
tag and sha256 and named `<engine id>-<file>`; a published tag's assets are
never replaced, so new or changed files get a new tag. Parakeet Ultra (#25) is
converted on the host by `tools/parakeet-ultra` and published as its release
`v1` (#39). The other engines (`v2` to `v8`, #75) are third-party int8 exports
re-hosted byte for byte from the pinned Hugging Face commits NOTICE.md names;
nothing downloads from Hugging Face (Max, 2026-10-08). Parakeet 110M, Nemotron
3.5 and Qwen3-ASR download ONNX Runtime 1.28.0 `.ort` conversions of those
files instead (`v9` to `v11`, #78, Max, 2026-10-10; Fixed style, target arm;
`docs/benchmarks/2026-10-ort-load/`). Their earlier `.onnx` sets stay valid
installs (`previousFiles`). Re-check the `.ort` files when the app's ONNX
Runtime version changes (Fixed-style files hold that version's fused CPU
ops); a re-conversion gets a new tag and new sums. That repo's README,
NOTICE and `LICENSES/` carry the attribution each asset's license needs.

**A/B benchmark (#13).** `benchmark/` records one take (up to 30 s,
high-passed like a live session, and through `SoftAgc` when the mic has no
platform AGC, #18) into a `FloatArray` that lives only in the
ViewModel: never written, never logged. `BenchmarkRunner` runs it through
each installed, visible engine strictly one at a time (load, transcribe,
release) after freeing the active engine. `SttEngines.loadForTake` splits the
take with Silero exactly as a live session would, so every engine sees the
same utterances, each with its family's pre-roll in front
(`vadUtterances`). Offline engines decode each utterance; streaming engines
get each utterance in 100 ms chunks as fast as they take them (not paced to
real time). It reports load and decode time, RTF, sampled peak RSS and, with a
reference text, WER, and copies a Markdown table. Each row of that table
says whether the soft AGC ran ("Soft AGC" on/off), so rows from phones with
and without a platform AGC aren't compared blind.

Each engine's transcript is cleaned the way dictation cleans its Finals
(#45): `TakeCleanup` commits the take's utterances one by one
(`TakeTranscriber.transcribeUtterances`) through `Dictation.commitFinal`,
the Dictate screen's own step, with the user's `TextProcSettings.config`.
The cleanup language is the one that engine would have as the active engine
(`SpeechPrefs.cleanupLanguageFor`), so an English-only engine gets the
English rules even while a multilingual engine on another language is
selected. Every Final counts as not near-silent (the take keeps no
per-utterance level from before the gain), so phantom phrases stay; sound
tags and loops still go, and the engine's pre-speech phantom is dropped
before speech as in a session (`SttEngineDescriptor.isPreSpeechPhantom`). The runner cleans after releasing the engine, so
cleanup time is in none of the measurements. The screen shows the cleaned
text with the raw text under it ("Raw: …"); WER is scored on the cleaned
text, and the Markdown table has a Transcript (cleaned) and a Raw column.

The source was the maintainer's working copy of the Marmalade monorepo, which
was 221 commits ahead of github.com/maxwhipw/marmalade at the time and
included newer STT fixes.

**Speed budget (#24, #27).** On each phone, engines slower than
distil-small.en was (Max's rule, set when it was the default) are flagged,
and faster phones afford more (`install/SpeedBudget.kt`):
- `SpeedBudget.REFERENCE` gives every engine its decode cost relative to
  **Parakeet Ultra** at 4 threads (#45; distil-small.en until it left, and
  every cost was divided by Ultra's 0.28 then) and its peak memory. **Provisional** until the
  Pixel 8a run (#23): host ratios (benchmark host, #14's 2-thread RTF, moved to 4
  threads with #20's measured speed-ups) and host peak RSS. Replace both from
  a Pixel run with `tools/speed-bench/summarize.py --kotlin`. #34's Pixel
  thread sweep agrees where it overlaps (Parakeet v3 0.22–0.25, Nemotron 3.5
  about 1.25). #31's Pixel spot check confirmed Nemotron 3.5 560/1120 ms,
  Whisper turbo and distil (`REFERENCE_BUDGET`) within 7 % of these, in
  Ultra units; Parakeet 110M is now the Pixel's 0.378 (the estimate was
  0.321). #47's Pixel runs measured every engine on first decodes, against
  Ultra with the arena (the unit of every cost): Canary is now 1.92 and
  Granite 0.76 (host estimates 2.46 and 0.429); Qwen3 (5.15), turbo, the
  Nemotrons and 110M were within 15 % and stay. Memory is the app's since the #31 review, not the host's (whose
  Python process read up to twice as high): the Pixel's peak for its own
  model plus a 180 MB process baseline. Offline engines are measured at
  the 28 s utterance cap (two 27 s LibriSpeech utterances, #47), every
  offline engine on the Pixel; Nemotron and Whisper don't grow with the
  utterance (`docs/benchmarks/2026-10-pixel8a-mem.md`).
- `SpeedCalibrator` measures the phone with `SpeedProbe`, which needs no
  engine: the bundled Silero VAD over `assets/calibration/speech.wav` (3.6 s,
  synthetic), 8 passes a run, the fastest of 5 runs after a warm-up (other
  work can only slow a run down). It stores
  the probe time and the recognizer thread count, and everything is
  computed from them, so a new reference applies without measuring again:
  - The speed factor is the Pixel 8a's probe time ÷ this phone's
    (`SpeedBudget.speedFactor`; `PROBE_REFERENCE_MS` = 157, the app's own
    probe on the Pixel 8a, #31). The debug benchmark service reads about
    240 ms on the same phone: outside the top-app cpuset it gets no boost
    and no X3 core. #34's 264 ms was such a service probe.
  - The thread count moves each engine's cost instead
    (`SpeedBudget.threadPenalty`, `costsAt`, `SpeechPrefs.engineCosts`), per
    family, from #34's Pixel sweep: Parakeet costs 1.45× at 2 threads and
    1.14× at 3; streaming transducers 1.37× at 2 (host, #20); Whisper, and
    Canary and Qwen3-ASR by assumption, nothing. The factor itself gets no
    thread correction, as when distil-small.en (flat from 2 to 8 threads)
    was the unit.

  The probe runs the first time the setup guide or the Engines
  tab needs it; Settings → Developer → "Re-measure phone speed" (shown
  only while Developer engines is on, #42) runs it again.
- The budget is that factor, clamped to 0.25–4 (1.0 until measured), times
  `REFERENCE_BUDGET` = 3.57, distil-small.en's cost in Ultra units: the
  rule's ceiling on the reference phone. It is a host ratio too (#34's Pixel
  sweep implies about 3.5–4.4); revisit it with `REFERENCE`. The app can't
  run distil any more, so `summarize.py --kotlin` suggests a value only
  from a pre-#45 result file of the same phone. A dump of every kept engine's tier
  and over-budget flag at 12 factors and 2–4 threads was identical before and
  after the re-base. An engine is over budget when its cost exceeds the budget by
  more than 1.25× (the "about the same" band).
- Per-phone evidence (#30): an engine that falls behind real time for 10 s
  in a session (`SpeechEngineManager.tooSlow`) is stored
  (`SpeechPrefs.tooSlowEngineIds`, via `SpeedCalibrator.watch`, started by
  the Application so voice-keyboard sessions count) and is over budget and
  slow on this phone whatever its reference cost; the recommendation passes
  it over. Re-measuring the phone's speed forgets it.
- The card's speed meter is the cost as a share of the budget, five
  segments like the quality meter (#80, Max 2026-10-10): ≤ 0.2 fastest (5),
  ≤ 0.35 very fast (4), ≤ 0.7 fast (3), ≤ 1.25 moderate (2), beyond that
  slow (1, "May be slow on this phone", the same line as over budget). On a
  Pixel 8a 110M is fastest and Parakeet Ultra very fast.
- User engines over budget get no card: they move under "Other engines"
  (see "Engines tab"), except the default, the one in use and the
  recommended engine. What runs (`SttEngineCatalog.effective`) never changes
  because of the budget. This
  replaced the catalog's static `slow` flag; `livePartials` stays a separate,
  explicit choice.

**Engines tab (#27; design picked 2026-09-28).** `engineGroups`
(`EngineCardState`) splits the catalog in two:
- Full cards (lab variant A1, `docs/design/engine-cards-lab.html`) for the
  engines offered on this phone (`SttEngineCatalog.offered`) within its
  speed budget, plus the default, the one in use and the recommended one,
  which comes first. A card has the name, badges, description, language
  note, size, a SPEED / QUALITY / LANGUAGES column (`EngineSpecColumn`) and
  a bottom row of actions: Remove and Use (or "In use") once installed.
- Compact rows (lab A3) behind an **Other engines** button, collapsed by
  default and remembered (`SpeechPrefs.otherEnginesExpanded`): offered
  engines over the budget, installed engines without a card (so their files
  can be removed), and with Developer engines on every other engine,
  developer engines last. Tapping an installed row selects it.
- "Show more" on a card, or a row's chevron, opens `EngineInfoDialog`, a
  centered modal spec sheet (lab B1): tiles, then status, Check files,
  Remove, the developer language override, speed and quality facts,
  languages, size, license and source.
The card before the lab: `docs/design/engine-card-current.md`.

**Quality markers (#27, per language since #50).** `install/EngineRatings.kt`
keeps one table of measured error rates per language and engine
(`EngineQuality.SCORES`, each value the mean over that language's sets, the
source doc cited in its KDoc). The cards and the recommender both read it.
Markers are 1–5 stars, shown as a five-segment meter in the speed meter's
traffic-light colours (#67: 5 green, 4–3 lime, 2 amber, 1 red; "Tested in
English" under it on the card when measured in another language), with the
words Best / Great / Good / Fair / Basic for TalkBack, in the dialog's tiles
and after the meter on the compact rows (which have no labels):
- English: the mean normalised WER over LibriSpeech clean, LibriSpeech other,
  FLEURS-en and AMI (`docs/benchmarks/2026-09-host-wer.md`). ≤ 7.0 → 5,
  ≤ 8.0 → 4, ≤ 9.5 → 3, ≤ 11 → 2, else 1. Today: Parakeet Ultra, 110M,
  Granite, Qwen3, turbo 5; Canary (loop-cut, #18) 3.
- Spanish, German, French (same doc) and Hindi, Arabic, Turkish,
  Vietnamese, Norwegian (`docs/benchmarks/2026-10-nemotron-decision.md`):
  FLEURS WER with the language set. ≤ 5 → 5, ≤ 8 → 4, ≤ 11 → 3, ≤ 15 → 2,
  else 1.
- Japanese (FLEURS), Chinese (FLEURS + AISHELL-1), Korean (FLEURS +
  Zeroth-Korean): CER, a point stricter: ≤ 4 → 5, ≤ 7 → 4, ≤ 10 → 3,
  ≤ 15 → 2, else 1. Turbo: ja 3, zh 3, ko 4; Qwen3: ja 2, zh 4, ko 4;
  Parakeet-ja: ja 4. Japanese is #52's FLEURS-ja run (`2026-10-japanese.md`;
  turbo 7.2 there against #37's 8.1 on the same clips, with #43's pipeline).
- Near-best floor (#80, Max 2026-10-10, `EngineQuality.stars`): an engine
  whose error is about as good as the best measured in that language
  (`nearBest`, the recommender's margin) gets at least 3 stars, so the
  best engines of a hard language don't read Fair. It raises Nemotron 3.5's
  Hindi (14.4, the best) and Vietnamese (15.0) to 3, turbo's Vietnamese
  (14.1) to 3, and Qwen3-ASR's Vietnamese (16.3) from 1 to 3;
  Nemotron's Arabic was 3 already.
- The user's language is the one they dictate in (#76,
  `ratingLanguage` in `EngineCardState.kt`): Settings → Recognition
  language, or on Auto the UI locale (`SpeechPrefs.spokenLanguage`). The
  cards, rows and info dialog get it from `prefs`; the setup guide's card
  uses the recommendation's language, which is the same rule.
- A card shows the marker for the user's language when the engine is
  offered for it (`EngineLanguages.of`) and was measured in it, else the
  English one when the engine is offered for English, else the one for its
  main language (the first of `EngineLanguages.of` that was measured:
  Parakeet-ja Japanese, Nemotron 3.5 Hindi), each labelled
  "Tested in …"; "Not rated" only for an engine measured in none of its
  languages. Not "Not rated" for those: they were measured, and the card's
  language note already says they don't transcribe the user's language.
  Nemotron 3.5's FLEURS es/de/fr/ja scores stay in the table but never show:
  it is offered for Hindi, Arabic and Vietnamese only.

**Recommendation (#27, by accuracy since #50).** The setup guide recommends
one engine for the phone (`install/EngineRecommender.kt`, run by
`SpeedCalibrator.recommend()`):
- Candidates: `EngineRecommender.preference` filtered to user engines
  offered for the spoken language (`EngineLanguages`): Parakeet Ultra (25
  European languages), Parakeet TDT_CTC 110M (English), Parakeet TDT_CTC
  0.6B ja (Japanese, #60), Canary 180M Flash
  (Spanish, German, French; never English, where 110M is lighter, faster
  and better; #45 fix round), Nemotron 3.5 1120 ms (Hindi, Arabic,
  Vietnamese, #37), Qwen3-ASR (Chinese and Korean only, though offered for 30; below), Whisper Large v3 Turbo (Whisper's ~100). The order is cheapest
  first, except Ultra, the main engine, ahead of 110M. A language none
  covers falls back to the English candidates.
- Of the candidates that fit the phone (peak memory within 40 % of
  `ActivityManager.MemoryInfo.totalMem`, cost within the speed budget), the
  first whose measured error is within a margin of the best fitting one's
  wins (#50, `EngineQuality.nearBest`): best + 2 points or best × 1.2,
  whichever is looser. Two points is about a 100-clip FLEURS set's noise.
  So a costlier engine wins only where it is clearly more accurate:
  turbo (~10× Ultra's cost, no live text) only for Turkish, Norwegian
  and the unmeasured languages only it covers. Japanese gets Parakeet-ja
  (CER 5.4; turbo's 7.2 is inside the margin but costlier), with turbo
  only where Parakeet-ja fell behind and turbo fits the budget. In a language with no
  measurement, the first that fits, as before.
- Each engine counts as the phone runs it (#47, `SpeedBudget.forPhone`):
  without ORT's arena, leaner and slower, where it fits only that way.
  With the app's own peaks at a 28 s utterance: Parakeet Ultra needs a 6 GB
  phone with the arena (1.71 GB) and a 4 GB one without (1.18 GB, 1.34×
  the cost); 3 GB fits Parakeet 110M, Canary and Nemotron, and Parakeet-ja
  without the arena (1.03 GB; 1.24 GB with it, Pixel 8a, #60);
  2 GB 110M and Canary without the arena.
- If nothing fits the speed budget: the cheapest of those that fit in
  memory and are within the margin of the best of them ("may be slow"), or
  the one needing the least memory. An engine that fell behind on this
  phone is ranked by its estimated cost too (#47): an unproven engine
  replaces it only at the same or a lower estimate, since one that costs
  more would most likely fall behind as well.
- What changed with #50: nothing, on today's lineup, for any phone class
  (#37 had already left Nemotron only where it is near the best). It
  decides edge cases (a Spanish phone where Ultra fell behind gets turbo,
  3.1, not Canary, 8.0) and keeps turbo from taking a language where a
  cheaper engine is close. Ranking the near-best by cost alone instead of
  the preference order would move English to Parakeet 110M (6.9 against
  Ultra's 5.5, inside the 2 points), which Max's "Ultra is the main engine"
  rules out; hence the order.
- **Qwen3-ASR: offered for 30 languages, recommended for two (#50,
  #88).** A user engine since Max approved its grey provenance on
  2026-10-02 (`docs/model-reviews/qwen3-asr-0.6b.md`), first for Chinese and
  Korean behind a switch (#50), and since #88 (Max, 2026-10-17: "If someone
  wants to use it for English no reason not to stop them") for all 30 of its
  languages (`EngineLanguages.QWEN3`, Qwen's own `SUPPORTED_LANGUAGES`), with
  a language hint for each. Its cards rate it by the same rules as every
  engine (measured: en 5, es 3, de 3, fr 1, ja 2, zh 4, ko 4, hi 1, ar 1,
  tr 1, vi 3; its other 19 languages show its English marker, "Tested in
  English"). The recommendation is unchanged:
  `EngineRecommender.QWEN3_RECOMMENDED` keeps it a candidate for Chinese and
  Korean only. Without that it would lead turbo as the first that fits in
  every unmeasured language of its list (Thai, Indonesian, …), and take
  Vietnamese where Nemotron 3.5 fell behind (16.3 is inside turbo's 14.1
  margin). It is recommended for both on every phone that fits it in
  memory: Chinese because it is the most accurate (CER 5.95 against turbo's
  9.45), Korean because it is within the margin (5.7 against 4.65) at about
  0.6× turbo's cost. On a Pixel 8a it runs lean (9.71 against turbo's 9.79,
  both "may be slow"). A test pins every other language's recommendation,
  on every phone class, to the one without Qwen3 in the catalog.
- The result is stored with the rules version (`SpeechPrefs.recommendedEngineId`,
  `recommendationRules`): always a user engine (the recommender skips
  developer engines), and it is
  what runs while nothing is selected (`SttEngineCatalog.effective`), so
  leaving the setup guide mid-download doesn't fall back to an uninstalled
  default.
- **Rules version (#50).** `EngineRecommender.RULES_VERSION` ("2+qwen3": "2"
  since #60's Parakeet-ja for Japanese, "+qwen3" for Qwen3-ASR's Chinese and
  Korean; #88 recommends nothing new, so it stays) is stored with every recommendation. At startup a
  stored recommendation made under another version (or none, before #50) is
  redone (`SpeechPrefs.recommendationOutdated`, in `cleanUpRemovedEngines`),
  with `keepRunningEngine` pinning an installed engine that is running. So
  a Japanese phone recommended turbo before #60 gets Parakeet-ja as its
  recommendation but keeps running the turbo it downloaded. Change the
  version whenever a rule or table change moves a recommendation.
- **Kept selection (#50 review, removed in #89).** A developer engine
  recommended by an older version (Qwen3-ASR on a Thai phone, before #37)
  was marked kept when selected (`speech_engine_kept`), so it ran on after
  the recommendation moved. Qwen3-ASR is a user engine since #50/#88 and
  runs anyway, so #89 dropped the mark: `forgetUnknownEngines` deletes the
  stored key, and `SpeechPrefs.engineToPin` pins only when nothing is
  selected.
- User engines (#45, 2026-10-02): Parakeet Ultra (the default), Parakeet
  TDT_CTC 110M (the light one), Canary 180M Flash, Nemotron 3.5 Streaming
  (1120 ms; hi/ar/vi since #37) and Whisper Large v3 Turbo.

**Engines that left the app (#45).** Max cut the lineup on 2026-10-02
after testing on the phone: distil-small.en, Whisper tiny.en, base.en and
small.en, Parakeet TDT 0.6B v3 (Ultra replaces it), v2, Unified EN 0.6B and
its 560 ms streaming export, Nemotron Speech Streaming EN 560 ms and the
Nemotron 3.5 320 ms export; #37 removed the Nemotron 3.5 560 ms export. Their reviews are in
`docs/model-reviews/retired/` (v3's stays in place as Ultra's base). At
startup `MarmaladeSttApplication` runs `cleanUpRemovedEngines`, which:
- deletes every `files/models/<id>` and `<id>.partial` whose id isn't in
  the catalog (`ModelInstaller.removeUnknownModels`; catalog engines are
  never swept);
- forgets stored ids that aren't in the catalog: the selection, the
  recommendation, too-slow entries and per-engine languages
  (`SpeechSettings.forgetUnknownEngines`), and drops the dead
  `nemotron_chunk_ms` setting (Settings → Developer's chunk size, #37);
- when the selection or the recommendation was one of them, or the
  recommendation was made under older rules (#50, "Rules version" above),
  recommends again (`SpeedCalibrator.recommendAgain`), so what runs is chosen for this
  phone rather than the default, which may not be downloaded or fit. Its
  `keepRunningEngine` pins an installed engine that is running instead of
  switching to an undownloaded recommendation. Until then
  `SttEngineCatalog.effective` already skips unknown ids.
`RemovedEnginesUpgradeTest` runs that function against the real catalog,
DataStore, `models/` directory and `SpeedCalibrator` (only the RAM is faked,
through `PhoneHardware`): an upgrader on distil or v3 loses the files and
ends on the recommendation; one on Nemotron 3.5's 560 ms export ends on
Whisper turbo for Japanese, or on the 1120 ms export for Hindi.
A future cut needs nothing more than removing the catalog entry.

**Nemotron 3.5 chunk size.** sherpa-onnx fixes a streaming model's chunk
size when it exports it, so each size is its own ~680 MB download of the
same checkpoint. From 2026-09-28 the app offered two exports behind one
user engine (560 ms, text sooner; 1120 ms, less work per second of audio),
picked for the phone's speed, with a Developer override. #37 (Max's
decision "A", 2026-10-02) kept only the 1120 ms export: it is as accurate
as 560 ms or better on every set and about 40 % cheaper, and Nemotron now
serves only Hindi, Arabic and Vietnamese (`EngineLanguages.NEMOTRON_35`),
where Whisper turbo doesn't beat it. The 560 ms export, the per-phone pick
and the override are gone; an upgrader's 560 ms files and stored id are
cleaned up by `cleanUpRemovedEngines` (above). The 320 ms export left in
#45 (no more accurate than 560 ms, and slower).

**Recognition language (#27).** Settings → Recognition language is the
language the user speaks: Auto (the default) follows the system language,
or one of `EngineLanguages.userChoices()`, every language a user engine
transcribes (about 100 since #45 made Whisper turbo a user engine). Stored as
`SpeechPrefs.recognitionLanguage` ("" for Auto):
- `SpeechPrefs.spokenLanguage()` (the choice, else the system language)
  picks the recommended engine. Changing it recommends again
  (`SpeedCalibrator.recommendAgain`, no re-measuring) but never switches
  engines; instead a card whose engine doesn't transcribe the language says
  so, and the card of the engine in use names the recommended engine when
  that one does (`languageNote`). No note when no listed engine has the
  language.
- It is the language hint (`SpeechPrefs.languageFor`): a chosen language
  goes to every engine that takes it (Nemotron 3.5 hi/ar/vi, as keys of its
  prompt dictionary; turbo the #13 dozen plus hi/ar/tr/vi/nb since #37,
  Qwen3 its 30 languages since #88, by the English names its prompt
  takes, `QWEN3_LANGUAGE_NAMES`; Canary
  en/es/de/fr), and an engine that doesn't gets its default, auto-detect
  where it has it. On Auto, engines that detect the language do, and only
  Canary (no auto-detect) gets the system language; when that isn't one of
  its four, its card asks for a recognition language
  (`LanguageNote.choices`, #45), unless the developer override
  (`SpeechPrefs.languageOverride`) gives it one of its own.
- The #13 per-engine language pick stays as a developer override: the info
  dialog's "Language for this engine" row shows, and applies, only with
  Developer engines on, where it beats the recognition language. Its
  picker lists Auto-detect first, then the languages by name in the UI
  language (`sortedLanguageChoices`, #89).

**Speed benchmark (#23).** Debug builds carry `debug/SpeedBenchmarkService`,
started only from the adb shell (DUMP permission) and driven by
`tools/speed-bench/`. It installs model files pushed over adb through
`ModelInstaller` (every sha256 checked, no download) and runs each installed
engine alone, in a shuffled order with a cool-down: load time, RTF per clip
length, the calibration clip, and real-time sessions (`benchmark/SpeedBench.kt`:
`PacedClipMic` plays a WAV through a real listening session, no platform AGC,
so SoftAgc runs as on the Pixel) for first partial and end of speech to
Final. It holds a wake lock, pauses while the screen is on, stops below 20 %
battery or at thermal status severe, and re-measures a turn that ran hot.
Engines load with an explicit thread count and ONNX Runtime tuning
(`SttEngines.create` / `loadForTake` take `numThreads` and `tuning`), so it
can compare thread counts and `OrtTuning`s (#31: `--es tunings`, with each
turn's thread affinity and, since #47, whether ORT's arena was on).
`tools/speed-bench/cpuset-watch.sh` (#47) samples which cpuset the app's
threads get while someone dictates. Sessions also time the cold start after
`SttEngine.warmUp` (`coldWarmed`). Each run probes 5 times once the phone is
idle.

## Accessibility (#68)

The audit, per screen and check, is `docs/accessibility-2026-10.md`. The
conventions it left:
- **Accent text goes through `MaterialTheme.accent`, never
  `colorScheme.primary`.** `accent.text` (section headers, text buttons,
  links, the slider's value) is the scheme's primary in Marmalade (orange
  500 in light mode, the brand Max kept at 2.6:1 on cream; orange 400 in
  dark, AA), and an AA ink in High contrast and the other presets' light
  mode; `accent.ink` (Copy / Clear, the engine screens' marks and buttons)
  is the dark ink in light mode and primary in dark. Quiet buttons are `MarmaladeTextButton` (default
  `accent.text`) / `MarmaladeOutlinedButton` (`ui/components/Buttons.kt`).
  `ButtonContrastSourceTest` fails on a bare `TextButton(` or text in
  `primary`; `ThemePresetTest` checks every text role (error red too) on
  every surface in every preset.
- **High contrast preset** (`ThemePreset.HIGH_CONTRAST`): Marmalade with
  orange 700 as light `primary` (white on it 5.2:1: buttons, switches,
  sliders, the mic) and ink for `accent.text`; dark mode is Marmalade's.
  The setup guide's welcome page has a switch for it
  (`AppearanceSettings.setHighContrast`: one DataStore edit remembers the
  earlier preset on disk and restores it when turned off; a pick in
  Settings → Appearance forgets it). TalkBack says the switch's own On/Off.
- **Async changes:** a polite live region on a node that stays put (the
  Dictate status); `rememberAnnouncer()` when the text appears in a new
  node, which live regions miss (a download ready, paused or failed:
  `downloadOutcomes`, said once by EnginesScreen and the setup guide, so
  `EngineStatus` has no live region; the setup guide's microphone result,
  the sheet's problem). The setup
  guide sets a pane title per page ("Step 2 of 6: …").
- **One node per control**: a button whose look changes keeps one node
  (the setup guide's advance button), or TalkBack focus falls off. A
  button TalkBack names differently from its label ("Remove, Parakeet
  110M") uses `spokenAs` + `ButtonLabel` (`EngineParts.kt`), because a
  merged button reads its description and then its text.
- **Headings** on screen titles (`TopBarTitle`), dialog titles, card titles
  and engine names (M3 1.3 sets none).
- **Large text:** screens scroll; the engine card's spec column and the
  details dialog's tiles stack at font scale ≥ 1.5; tab labels grow to
  1.5× and then ellipsize.
  Top bar titles go through `TopBarTitle`, which fills the title slot
  (an Arabic title measured at its own width lost a letter at 200 %);
  `DetailTopBar` balances its back button with an empty actions slot.
- **Click labels** (`onClickLabel`) are spoken inside TalkBack's own
  "Double-tap to …" hint, so translations follow that template
  (`docs/i18n/TRANSLATING.md`, "Click labels").
- **Focus:** the theme's `LocalRippleConfiguration` raises Material's focus
  layer to 32 %, so keyboard and Switch Access focus shows on every
  component.
- **Motion:** JarMascot and the mic level ring (`rememberMicMeter`) read
  `MotionDurationScale` and hold still when it is 0; so does the library's
  `WorkingDots` (`rememberReducedMotion`, #84). Finite animations follow
  the system scale on their own.
- **Animated status lines** (#84): the Dictate, voice keyboard and speech
  sheet status lines rise in when they change. The live region and the
  words TalkBack reads (`clearAndSetSemantics { text }`) sit on an outer
  node that stays put; the animated lines inside are hidden from it, so the
  line on its way out is never read.
- The three mics share `micColors` (`ui/components/MicColors.kt`): the
  preset's `accent.mic` / `accent.onMic`, so orange with white in light
  mode and toast with toast ink in Marmalade's dark mode.
- **Contrast claim:** "designed to meet WCAG 2.1 AA; an AA-contrast High
  contrast theme (light and dark) and the dark theme are available; the
  setup guide offers High contrast on its first screen". The default light
  mode keeps the brand's orange (Max's call), so High contrast (both modes)
  must stay AA: `ThemePresetTest` gates its text, filled controls and
  edges. Dark mode is the shared library's, held to AA by its
  `ThemeContrastTest` (TTS #89) for quiet text, icons, edges, primary text,
  button labels and, since #87 round 3, error text in both modes; this
  app's `ThemePresetTest` checks the same in dark mode with its own accent
  colours, the selected tab's pill (3:1 on the dark bar) and that no
  orange is left in Midnight, Forest and Berry.
  Meter segments: lit colours and unlit outlines 3:1 (`MeterContrastTest`).
- Compose semantics tests under Robolectric live in `app/src/testDebug`
  (debug only: `createComposeRule` needs `ui-test-manifest`).

## UI languages (#51, #69)

All UI text is Android string resources: `res/values/strings*.xml` in
English and the same files under `values-de`, `-es`, `-fr`, `-hi`, `-it`,
`-ja`, `-pt-rBR` and `-zh-rCN` (#51), and `-ar`, `-cs`, `-in` (Indonesian,
tag `id`), `-ko`, `-nl`, `-pl`, `-ru`, `-sv`, `-tr`, `-uk`, `-vi` and
`-zh-rTW` (#69). Engine summaries are resources too
(`SttEngineDescriptor.description` is a `@StringRes`); engine names, the
license catalog and the benchmark's Markdown report stay English. The
locale list lives in three places that `TranslationsTest` keeps equal:
`androidResources.localeFilters`, `res/xml/locales_config.xml` (Android 13's
per-app language) and the test's own `LOCALES`, which also holds each
language's CLDR plural categories. The test fails on a missing or extra key,
a placeholder mismatch, wrong plural categories, an unescaped `'` or `"`, a
dropped brand or engine name, or a translated English-only spoken word
(the text cleanup's commands, fillers, number examples and end word only
work on English speech). Each locale's `ui_locale` string is its own tag, so `rememberUiLocale()`
knows which language the UI resolved to: language names, their case and
their order (a `Collator`) follow it, not `Locale.getDefault()`, which on a
phone language the app doesn't ship (Thai, Greek) is the phone's while
the UI is English. The recognition language (Auto) still follows the
phone's or the per-app language. The privacy policy stays English
(`PRIVACY.md` is the single source); `about_privacy_english_note`, empty in
English, puts a translated summary above it. Rules for translators and the
per-language glossaries: `docs/i18n/`. Side-by-side review page:
`docs/design/i18n-lab.html`, built by `tools/i18n/build_lab.py`.

Arabic is right to left (#69). Compose mirrors layouts by itself
(`supportsRtl`, start/end padding, AutoMirrored icons), but lays every Text
out in the UI's direction unless its style says otherwise, which turned an
English "Hello." into ".Hello". So text that isn't the app's own words
(transcripts, every text field, snippet rows, another app's prompt, the
licence entries) takes `TextDirection.Content` via `contentDirection()` in
`ui/Bidi.kt`, user text inside a translated sentence is wrapped in a bidi
isolate (`isolate()`), and the English privacy policy is laid out left to
right under its translated note. The keyboard panel applies the app's
per-app locales itself (`ime/InAppLanguage.kt`): Android gives an input
method's window the system configuration, so it otherwise stayed in the
phone's language and direction. Audit with before/after shots:
`docs/design/rtl-audit.html`.

## Stage 2 (tracked as issues, not scheduled)

- Choose the stage-2 engines from the developer-engine benchmarks on the
  Pixel 8a (#13 added the candidates and the A/B benchmark screen).
  Accuracy is measured off-device (#14): `tools/asr-bench` runs every
  catalog engine on public test sets with the app's files, configs, VAD and
  session logic; results in `docs/benchmarks/2026-09-host-wer.md`
  (Japanese, Chinese and Korean by CER, with SenseVoice-Small, in
  `2026-09-cjk-sensevoice.md`, #29). The phone pass then only has to judge
  latency and feel.
- Evaluate newer small models for engines and cleanup: LFM 2.5 (the 2.6B is
  "weirdly good" per Max) and MiniCPM. Every model gets a `model-ethics-review`
  first.
- Opt-in on-device LLM cleanup, Wispr-style.
- Model hosting on our own release assets (a public action; needs Max).
