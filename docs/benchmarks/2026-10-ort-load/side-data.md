# Engine load: everything that isn't the ONNX session (#78)

What runs between "a surface asks for an engine" and "the first decode is
possible", other than ORT creating the model's sessions, per engine family.
Code read: sherpa-onnx v1.13.8 (`third_party/sherpa-onnx`, our patches don't
touch any constructor) and the app's `speech/` and `install/` code as of
`f5d3165`.

**Method.** Side data was timed with the real sherpa sources: a small C++
program compiled with sherpa's `symbol-table.cc`, `qwen-asr-tokenizer.cc` and
helpers, `-O2`, one core. Kotlin-side loads were timed on the JVM with the
app's ONNX Runtime (1.28.0) and kotlinx-serialization (1.6.3) jars. Session
creation for the multi-graph engines was timed with ORT 1.28 on the JVM, from
files on disk, 4 intra-op threads, ORT's default optimisation level (as
sherpa does). Host: a laptop with an i7-8750H. The input files are the catalog's (SHA-256 checked for
tokens.txt, vocab.json and tokenizer.json). Each step ran 3–4 times; ranges
are min–max, and rep 0 was cold (first ORT session in the process, JIT not
warm).

**Host to Pixel 8a.** The 8a's measured loads
(`2026-10-pixel8a-mem/first47-*.json`, `2026-10-pixel8a-ja/first60-*.json`)
compared with the same sessions' total on this host give 1.3–1.6× for
Qwen3, 1.25–1.8× for turbo and 1.6–2.3× for Canary. The phone estimates
below use **1.5–2.5×** for native work and 2–3× for Kotlin parsing (ART runs
it cold).

| Engine | Pixel 8a load (s) | Files |
|---|---|---|
| Parakeet Ultra | 3.2–4.7 | encoder 652 MB, decoder 12 MB, joiner 6 MB, tokens 8193 |
| Parakeet 110M | 1.9–2.1 | 131 MB + 4 MB + 1.4 MB, tokens 1025 |
| Parakeet ja (CTC) | 4.0–4.3 | 656 MB, tokens 3073 |
| Canary 180M Flash | 2.0–2.5 | encoder 133 MB, decoder 74 MB, tokens 5248 |
| Nemotron 3.5 1120 ms | 4.3–4.9 | encoder 658 MB, decoder 15 MB, joiner 9.5 MB, tokens 13088 |
| Whisper turbo | 4.9 | encoder 675 MB, decoder 361 MB, tokens 50257 (base64) |
| Qwen3-ASR 0.6B | 6.8–7.1 | conv 44 MB, encoder 182 MB, decoder 756 MB, vocab.json 2.8 MB, merges.txt 1.7 MB |
| Granite (developer) | 2.9–4.2 | 551 MB, tokenizer.json 1.1 MB, bundled formatter 7.5 MB |

## The load path as built

In order, for one `SpeechEngineManager.listen(surface)` with Keep engine
ready off (the default: every session loads the model, and frees it when it
ends):

1. `settings.current()`: DataStore, cached after the first read. Under 1 ms.
2. `engineFor` → `ModelInstaller.installedModel`: reads `.install.json`
   (about 1 KB, regex) and stats each file's size. **No hashing.** About
   1 ms. `SttEngines.create` only builds objects.
3. `turnDetectorFor`: only when the surface's end is Smart Turn (by default
   the Apps surface, not the keyboard). `SmartTurnModel.detectorOrNull` is
   `@Synchronized` and loads once per process. The Application's `warmUp`
   already starts it at process start (`usesSmartTurn` is true by default),
   so a cold-process Apps session waits for that load **before the engine
   load starts**.
4. `SherpaSttEngine.listen` → `load()` under `loadMutex` on
   `Dispatchers.Default`:
   - `modelFilePaths`: stats (microseconds).
   - `OrtTuning.sherpaProvider()`: **no file I/O for `DEFAULT`** (no config
     lines, plain `"cpu"`). Only a lean-mode phone (`arena = false`) gets
     `cpu:<cache>/ort/ort-cpu-noarena.cfg`: one ~20-byte read per load, a
     write only the first time. Negligible.
   - JNI `newFromFile` (Kotlin passes no AssetManager) → `config.Validate()`
     (file-exists checks) → `OfflineRecognizerImpl::Create` /
     `OnlineRecognizerImpl::Create` → the family's Impl, below.
5. `vadFor(minSilence)`: after the model, in the same mutex. A new Silero VAD
   every session with Keep engine ready off: `releaseNow` frees the VAD
   with the model.
6. `newSession.create` → `run()` → `micSession`: `AudioRecord` + platform
   NoiseSuppressor/AGC + `startRecording`, **only now**, then
   `SttEvent.Ready`. Nothing is recorded while the model loads.

All of 4–6 run on one thread, one after another.

## How sherpa creates sessions (all families)

- **By path, no buffer copy in sherpa.** Every model class we use calls
  `Ort::Session(env, path, opts)` when there's no AssetManager. sherpa's
  `ReadFile` copy into a `std::vector` happens only for AssetManager loads,
  and of ours only the bundled Silero VAD (0.6 MB) loads that way. ORT reads
  the file itself: for the ONNX format it parses the protobuf, so the weights
  are in memory once more before the initializers are placed. That is part
  of session creation, not side data.
- **No warm-up inference** in any constructor we use. The Init functions
  read input/output names, model metadata (`vocab_size`,
  `subsampling_factor`, Nemotron's `prompt_dictionary` JSON, Canary's and
  Whisper's language tables) and one output shape (the NeMo joiner). All
  under a millisecond.
- **No metadata double-load for the offline families.** `Create` returns
  early for `qwen3_asr.conv_frontend`, `nemo_ctc.model`, `whisper.encoder`,
  `canary.encoder` and `model_type = "nemo_transducer"`, before the fallback
  that opens the encoder just to read `model_type`. `OfflineCtcModel::Create`
  also short-circuits for `nemo_ctc`. **The online path does double-load**:
  see Nemotron.
- **Every session is created one after another** on the calling thread, and
  the symbol table or tokenizer is parsed on the same thread, before (NeMo,
  Whisper, Canary: `symbol_table_` is declared before `model_`) or after
  (Qwen3: `tokenizer_` after `model_`) the sessions.
- No ITN FSTs, FARs, hotwords, LM, lexicon or BPE vocab are configured, so
  none load. The base classes' rule-FST code is skipped.
- Each model class makes its own `Ort::Env`. ORT's environment is a process
  singleton, so that's cheap.

### Session options sherpa sets (`csrc/session.cc`, `GetSessionOptionsImpl`)

| Option | Value |
|---|---|
| Intra-op threads | `num_threads` (the app's `RecognizerThreads.count`), a pool per session |
| Inter-op threads | `num_threads` too, but ORT creates the inter-op pool only in parallel execution mode, so it has no effect here |
| Execution mode | ORT's default, sequential (only DirectML sets it) |
| Graph optimisation | ORT's default (`ORT_ENABLE_ALL`) unless the config file sets `GraphOptimizationLevel` |
| Memory pattern, CPU arena | on unless the config file turns them off |
| XNNPACK | only for provider `xnnpack`, never for `cpu` |
| `session.use_ort_model_bytes_directly`, prepacking, global thread pools | not set (ORT defaults: prepacking on, per-session pools) |

**Keys the provider config file accepts** (`ParseConfigFile` +
`GetSessionOptionsImpl`; `#` comments, `key=value` only: the comment says
the legacy `key:value` still works, but the v1.13.8 code skips any line
without `=`):

- `GraphOptimizationLevel=<0|1|2|99>` (ORT's enum)
- `LogSeverityLevel=<0..4>`
- `ProfilingFilePrefix=<path>`: turns on ORT's profiler. Its JSON records
  `model_loading_uri` and `session_initialization` per session, so this
  gives a **per-session load breakdown on the phone without a patch**
  (it also profiles every run, so use it only in a benchmark).
- `EnableMemPattern=0`, `EnableCpuMemArena=0`
- `SessionConfig.<key>=<value>`: any ORT session config entry,
  forwarded to `AddConfigEntry`. Reachable this way with no patch:
  `session.load_model_format=ORT`, `session.disable_prepacking`,
  `session.intra_op.allow_spinning`, `session.intra_op_thread_affinities`,
  `optimization.disable_specified_optimizers`, `mlas.*`, and
  `session.use_ort_model_bytes_directly` /
  `session.use_ort_model_bytes_for_initializers`. Those last two apply to
  sessions created from a byte array, and sherpa loads by path; check ORT
  1.28's path load before relying on them.
- `DEBUG=1` logs the parsed config.
- Anything else is ignored for `cpu` (it goes only to OpenVINO/SpacemiT).

Not reachable without a patch: `SetOptimizedModelFilePath` (saving the
optimised graph), execution mode, global thread pools /
`DisablePerSessionThreads`, shared or prepacked-weight containers,
`AddExternalInitializers`, loading from an mmap'd buffer.

## Per family

"First partial" means the first text the user sees: a live partial for the
re-decoding families, the first Final for Whisper turbo and Qwen3
(`livePartials` off), and the model's running text for Nemotron. A
yes/no for "Needed for first partial" means "needed before the first
decode". Common steps (1–3, 5, 6 above) are in the last table.

### NEMO_TRANSDUCER (Parakeet Ultra, Parakeet 110M)

| Step | Eager/lazy | Needed for first partial | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `SymbolTable(tokens.txt)` | eager, before the sessions | yes (text) | Ultra 3.1–3.4 ms, 110M 0.4–0.5 ms | ≤ 8 ms |
| Encoder session + names, metadata | eager | yes | (the load) | most of 3.2–4.7 s |
| Decoder session | eager, after the encoder | yes | Ultra 9–21 ms | 15–50 ms |
| Joiner session + output shape check | eager, after the decoder | yes | Ultra 6–14 ms | 10–35 ms |
| `PostInit` (feature config, blank/vocab checks) | eager | yes | < 0.1 ms | – |

Parallel decoder/joiner creation could save ≤ 1 % of the load. **Not worth
a patch.**

### NEMO_CTC (Parakeet ja)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `SymbolTable` (3073) | eager | yes | 1.1–1.3 ms | ≤ 3 ms |
| One session (656 MB) | eager | yes | (the load) | ~4 s |

Nothing to gain outside the session.

### CANARY (Canary 180M Flash)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `SymbolTable` (5248) | eager | yes | 1.9–2.1 ms | ≤ 5 ms |
| Encoder session (133 MB) | eager | yes | 0.94–1.26 s | |
| Decoder session (74 MB), after the encoder | eager | yes | 0.12–0.26 s | 0.2–0.6 s |
| Both at once (measured) | – | – | 1.52 → 1.27 s, 1.06 → 1.03 s (3–17 %) | −0.05–0.4 s |

### STREAMING_TRANSDUCER (Nemotron 3.5 1120 ms)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| **Probe:** `OnlineRecognizerImpl::Create` opens `decoder.int8.onnx` with 1 thread only to call `IsNeMoParakeetUnifiedStreaming` and count its outputs, then frees it | eager, before everything | no (a type check) | 10–31 ms | 20–80 ms |
| Encoder session + metadata (`prompt_dictionary` JSON etc.) | eager | yes | (the load) | most of 4.3–4.9 s |
| Decoder session (15 MB, opened a second time) | eager | yes | 10–31 ms | 20–80 ms |
| Joiner session (9.5 MB) | eager | yes | 16–19 ms | 25–50 ms |
| `SymbolTable` (13088) | eager, after the model | yes | 4.9–5.4 ms | ≤ 15 ms |

`OnlineRecognizerImpl::Create` ignores `model_type` for transducers, so
setting it in Kotlin doesn't skip the probe. A two-line patch could: skip the
probe when `model_type == "nemo_transducer"`, and set `modelType` in
`SherpaStreamingRecognizer.load`.

### WHISPER (Whisper Large v3 Turbo)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `SymbolTable` (50257 base64 lines, 816 KB) | eager, before the sessions | only to turn the first Final's ids into text | 23–28 ms | 35–70 ms |
| `ApplyBase64Decode` (rebuilds both maps) | eager | same | 9–12 ms | 15–30 ms |
| Encoder session (675 MB) | eager | yes | 1.60–2.64 s | |
| Decoder session (361 MB), after the encoder | eager | yes | 1.16–1.30 s | |
| Both at once (measured) | – | – | 3.95 → 3.22 s, 2.75 → 1.66 s (18–40 %) | −0.9–1.9 s |

Turbo shows nothing until an utterance ends, so the symbol table could be
built during the first utterance (lazily or on another thread). That saves
about 50–100 ms, which only matters once the sessions are faster.

### QWEN3_ASR (Qwen3-ASR 0.6B)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| Conv frontend session (44 MB) | eager | yes | 0.11–0.17 s | |
| Encoder session (182 MB) | eager | yes | 0.58–0.67 s | |
| Decoder session (756 MB) | eager | yes | 3.39–4.03 s | |
| All three at once (measured) | – | – | 4.87 → 4.29 s, 4.08 → 3.49 s (12–15 %) | −0.8–1.0 s |
| **`QwenAsrTokenizer`**, after all three sessions | eager | yes: `InitPromptTemplateIds` in the constructor; every decode encodes "language X" with the BPE merges | **225–240 ms** total | **0.35–0.6 s** |
| of which `vocab.json` (151,643 entries, nlohmann DOM parse) | | | 155–264 ms | |
| of which `merges.txt` (151,291 lines) | | | 59–81 ms | |
| of which file reads, `tokenizer_config.json` added tokens | | | ~2 ms | |
| Prompt encodes (`InitPromptTemplateIds`, per decode) | | | 0.035 ms | – |

The tokenizer is the largest piece of side data in the app: 5–9 % of
Qwen3's load now, and a bigger share once the sessions get faster (#78's
.ort work). Nothing needs it before the first Final, which comes at least
an utterance after Ready. So it can run on another thread while the sessions
load: a small patch in `OfflineRecognizerQwen3ASRImpl`'s constructor
(`std::async`, joined in the body). A SAX parse of `vocab.json` would
roughly halve its cost, but overlapping it hides all of it.

### GRANITE_CTC (Granite Speech 5.0, developer engine; ORT from Kotlin)

| Step | Eager/lazy | Needed | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `tokenizer.json` `readText` (1.1 MB) | eager, before the session | yes (vocab) | 2–13 ms | |
| kotlinx `parseToJsonElement` (JSON tree) | eager | yes | **118 ms cold**, 10–25 ms warm | 0.2–0.35 s (ART, cold) |
| `GraniteVocab` (array of 16,384) + `GraniteBpe` ids map (a second map from the same vocab) | eager | vocab yes, BPE only for the formatter | 15–22 ms | 30–60 ms |
| `GraniteBpe` merges (16,127 pairs) | eager | formatter only | 4–10 ms | 10–30 ms |
| Main session (551 MB) | eager | yes | (the load) | most of 2.9–4.2 s |
| Formatter asset read (7.5 MB, deflated in the APK) | eager, after the session | yes (every decode is formatted) | 3–19 ms | 10–50 ms |
| Formatter session | eager, after the main session | yes | 28–63 ms | 50–150 ms |

About 0.3–0.6 s of side data, all on the loading thread. The formatter
session and the tokenizer parse could both run while the main session loads.
It's a developer engine, so this ranks low.

### Common to every family (Kotlin)

| Step | When | Needed for first partial | Host | Pixel 8a (est.) |
|---|---|---|---|---|
| `SpeechEngineManager` construction: `initialChoice()` → `runBlocking { settings.current() }` + `getMemoryInfo` | once per process, **on the main thread** (Hilt injects it in `Application.onCreate`) | no | not measured | 10–50 ms (DataStore's first read + one binder call) |
| `VoiceInputService.onStartInputView` → `isReady()`, `startListening` → `engineOrNull()` | every panel show, main thread | no | ~1 ms each (`.install.json` + stats) | 1–5 ms |
| `installedModel` per listen (off main) | every session | no | ~1 ms | ~2 ms |
| Smart Turn: asset inflate (8.7 MB) + ORT session from bytes | once per process; a cold-process Apps session waits for it before the engine load | only for Smart Turn's end check, which comes after speech | read 3–25 ms, session 51–79 ms | 0.1–0.25 s |
| Silero VAD: asset inflate (0.6 MB) + sherpa `ReadFile` copy + session | **every session** with Keep engine ready off, and whenever `minSilence` changes; after the model, same thread | yes (segments the mic audio) | read 0.3–10 ms, session 6–14 ms | 15–40 ms |
| Mic: `AudioRecord` + NoiseSuppressor + AGC + `startRecording` | every session, **after** model and VAD | yes | not measured | ~30–150 ms (Android audio HAL; untimed) |
| `OrtTuning.sherpaProvider` | every load | no | 0 (DEFAULT) / < 1 ms (lean) | – |

`ModelInstaller.installedModel` never hashes: `deepCheck` runs only from
an explicit deep verify.

### A rebuild that isn't needed: language changes for Qwen3 and Nemotron

`SpeechEngineManager.Choice` includes the language, so a different language
(a RecognitionService/`RecognizeSpeechActivity` request whose
`EXTRA_LANGUAGE` differs from the settings, then the next session without
it) drops the engine and reloads it: 4.3–7 s. But Qwen3 reads the language
**per stream** (`stream.setOption("language", …)`, read in
`offline-recognizer-qwen3-asr-impl.cc`), and so does Nemotron
(`SherpaStreamingRecognizer.createStream`,
`GetLanguagePromptId(GetOption("language"))`). Neither needs a new model
for a new language. Parakeet has no language options (always ""), so it
never rebuilds. Whisper and Canary genuinely take the language at load
(`OfflineWhisperModelConfig.language`, Canary's `srcLang`/`tgtLang`), so
they still need the reload unless a patch makes it per stream.

## Ranked changes

Ranked by expected gain on a typical first dictation, then by risk. Phone
gains are estimates: the host numbers scaled by 1.5–2.5×.

1. **Create the sessions of the multi-graph engines in parallel** (patch:
   `OfflineWhisperModel::Impl`, `OfflineQwen3ASRModel::Impl` and
   `OfflineCanaryModel::Impl` path constructors; `std::async` per session,
   then the existing `Init*` on the results). Measured on the host: turbo
   −18–40 % (≈ −0.9–1.9 s on the 8a), Qwen3 −12–15 % (≈ −0.8–1.0 s),
   Canary −3–17 %. **Risk: medium.** Peak memory during the load rises,
   because two protobuf copies exist at once (turbo: ~+0.7 GB transient).
   Gate it off for lean-mode phones (`SpeedBudget.runsLean`): a
   config-file key would need the patch to read it. Transducers and
   Parakeet-ja: no gain (≤ 1 %), leave them.
2. **Overlap Qwen3's tokenizer with its sessions** (patch:
   `OfflineRecognizerQwen3ASRImpl` constructor, a tokenizer future started
   before `model_`). −0.35–0.6 s on the 8a. Small patch, **low risk**. It
   could ship with change 1 (one future more) or alone if change 1 is
   deferred.
3. **Stop rebuilding Qwen3/Nemotron for a language change** (Kotlin:
   `SpeechEngineManager.Choice`/`engineFor`; take the language per session
   in `SherpaOfflineTranscriber` (Qwen3 already passes it per stream in
   `offlineStreamOptions`) and in `SherpaStreamingRecognizer.createStream`).
   Saves a whole reload (4.3–7 s) whenever a request's language differs from
   the loaded engine's. **Low–medium risk:** the cleanup language and
   `UtteranceCap.maxDecodeSamples` also depend on the language, so they must
   follow the session's language, not the engine's.
4. **Keep the Silero VAD across model releases, and open the mic while the
   model loads** (Kotlin: `SherpaSttEngine.releaseNow` keeps `vad` unless
   dropped, about 2 MB; `listen` builds the VAD and the `MicCapture`
   concurrently with `load()`). With Keep engine ready off (the default)
   that's ≈ 50–200 ms off every session. **Risk: the VAD part is low.** The
   mic part changes when the privacy indicator appears and the error paths.
   Taken further, it is the biggest perceived win available: record and
   buffer the user's speech during the 2–7 s load, so they can talk at once.
   That changes the UX and needs Max's call (an ARCHITECTURE decision).
5. **Load Smart Turn concurrently with the engine, not before it** (Kotlin:
   `SpeechEngineManager.listen` calls `turnDetectorFor` before `e.listen`;
   resolve it in parallel, and in `warmUp` start both at once).
   −0.1–0.25 s, only for the first Apps (Smart Turn) session in a cold
   process and for Keep-engine-ready warm-ups. **Low risk.**
6. **Skip Nemotron's decoder probe** (patch: `OnlineRecognizerImpl::Create`,
   skip it when `model_type == "nemo_transducer"`; Kotlin: set `modelType` in
   `SherpaStreamingRecognizer.load`). −20–80 ms. Tiny, **low risk**.
7. **Whisper's symbol table on another thread** (patch, in the same place as
   change 1: `OfflineRecognizerWhisperImpl`). −50–100 ms. Tiny, low risk.
   Worth it only alongside 1.
8. **Granite (developer engine):** load the formatter session in parallel
   with the main session, and build `GraniteBpe`'s ids map from
   `GraniteVocab`'s array instead of walking the JSON again
   (`GraniteOfflineTranscriber.load`, `GraniteBpe.fromTokenizerJson`).
   −0.1–0.3 s. Low risk, low priority.
9. **Main thread:** `SpeechEngineManager.initialChoice`'s `runBlocking`
   DataStore read in `Application.onCreate` (cold process start, est. 10–50
   ms; it delays the IME's first panel after the process was killed). Making
   the synchronous callers (`isReady`, `engineOrNull`) tolerate a "not read
   yet" state is more work than the gain. Leave it unless a cold-start
   trace shows it.

**Not worth doing:** `noCompress("onnx")` for the bundled assets (saves
3–40 ms of inflate, costs +2.3 MB of APK), the `OrtTuning` config file (no
I/O for DEFAULT), parallel decoder/joiner for transducers, faster
`SymbolTable` parsing outside Whisper (everything else is under 6 ms on the
host), and `installedModel` (no hashing).

## Top findings

1. **Side data is small for the default engines.** For Parakeet Ultra,
   110M, ja and Nemotron, everything outside the encoder's session is about
   0.1–0.3 s of a 2–5 s load: tokens, the VAD, the decoder/joiner and the
   serial mic open. Faster loads for them have to come from session creation
   (.ort, optimisation level), which is #78's main track.
2. **Multi-graph engines build their sessions one after another, and
   Qwen3's BPE tokenizer comes after all of them.** On the host, parallel
   creation cut turbo by 18–40 %, Qwen3 by 12–15 % and Canary by 3–17 %.
   Qwen3's tokenizer (0.23 s host, ~0.35–0.6 s phone) can be hidden
   entirely. The cost is a higher transient memory peak.
3. **The Kotlin pipeline is strictly serial:** model, then VAD (rebuilt
   every session by default), then the mic. Qwen3 and Nemotron are reloaded
   for a language change they handle per stream. And the provider config
   file already reaches every ORT `SessionConfig` key plus
   `ProfilingFilePrefix`, which gives a per-session load breakdown on the
   phone without a patch.
