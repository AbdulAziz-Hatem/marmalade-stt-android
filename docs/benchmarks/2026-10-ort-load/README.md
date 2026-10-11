# Faster engine loading, October 2026 (#78)

Max's top friction point for 1.0.0 was the wait between tapping the mic and
being able to talk and see text. This measures where that time goes on the
Pixel 6a, per surface and engine, and evaluates each option in #78.

Files here:
- `host.md`: `.ort` conversion of all 8 engines (ORT 1.28.0, Fixed, arm),
  metadata and transcript parity on the asr-bench sets, host load times.
- `files.tsv`: the frozen `.ort` files' names, sizes and sha256 (the files
  are on the benchmark host, `$WORK/ort-78/ort/<engine>/`).
- `side-data.md`: what else loads before the first decode, per family.
- `pixel6a/surfaces-before.tsv`, `pixel6a/surfaces-after.tsv`: the surface
  pass (one row per trial; ms from the request).
- `pixel6a/ort78a.json`, `pixel6a/ort78b.json`: the `.onnx` vs `.ort`
  A/B (SpeedBenchmarkService `--es formats onnx,ort`).

## Answers

- **Words were lost because the mic wasn't open during the load.**
  `SherpaSttEngine.listen` loaded the model before the session opened the
  `AudioRecord`, so every surface was deaf for the whole load (2.6 s for
  Parakeet Ultra in the keyboard, 4.4–7.4 s through the RecognitionService).
  Fixed: the mic opens at once and its audio waits in memory (`EarlyMic`).
  The mic is now open 20–60 ms after the request on every surface and
  engine, and the whole sentence comes out.
- **The RecognitionService loads 1.7–2.8× slower** than the keyboard or the
  popup (4.3–7.4 s for Ultra against 2.6 s): our process isn't top-app when
  another app binds the service. Buffering makes that wait lossless; the
  first text still comes later there.
- **Hold while a surface is visible.** With Keep engine ready off, every
  session freed the model, so a second mic tap in an open keyboard panel
  reloaded it. Now the panel, the speak-now sheet and the Dictate screen
  hold the engine while shown, and Dictate preloads it when it opens:
  second tap and Dictate's first tap are ready in 32–82 ms.
- **Parallel session creation (sherpa patch 0006)** helps the multi-graph
  engines: Qwen3-ASR's load went 4.9 → 4.1 s and Whisper turbo's 3.3 → 2.4 s
  (keyboard, medians). Same text on the host.
- **`.ort` files help some engines, not the default one** (table below):
  110M −31 to −37 %, Nemotron −24 to −50 %, Qwen3 −20 %, but Parakeet Ultra
  −1 to −9 % and turbo −4 to −7 %. Transcripts identical, decode speed
  unchanged. Loading a large `.ort` peaks 0.36–0.6 GB higher (ORT reads the
  whole file into a buffer first). Shipped for 110M, Nemotron and Qwen3-ASR: see
  "The .ort route".
- **Graph optimisation isn't the cost** (host: level 0/1/99 load the same),
  so session options don't help, and **prepacking off is out**: it saves
  load time but decodes 1.2× (Ultra) to 2.4× (Qwen3) slower.
- **XNNPACK**: sherpa enables it only for provider `xnnpack`; we use `cpu`
  (and #31 found it takes no node of our int8 graphs).
- **Side data** is small next to the encoder for every default engine
  (0.1–0.3 s); Qwen3's tokenizer (0.35–0.6 s) now loads alongside its
  sessions (0006). Smart Turn's first load now runs after the mic opens.
- **Keep engine ready stays off by default.** Ultra loaded and idle costs
  ~720 MB (PSS 902 MB against 183 MB) on a 5.7 GB phone, and with buffering
  a cold start only delays the text, it no longer loses words.
- **Loading earlier** than the panel or sheet opening isn't possible: those
  open on the mic tap itself, and our IME gets no earlier signal. Dictate
  now preloads on screen open. The RecognitionService could start loading
  when an app binds it; not done (apps may stay bound for long, and
  `onCheckRecognitionSupport` binds too).

## Method

Pixel 6a (Android 14, 5.7 GB, on the charger, media volume 13, Keep engine
ready off), 2026-10-10 17:24–18:39 under the shared bench lock. Release
builds, arm64: **before** = main f5d3165 plus the timing log only (1dca0a7);
**after** = 595d2e4 (mic-first buffering, holds, patch 0006). Each trial
starts the surface and plays LibriSpeech `ls10-1` (11 s, one sentence)
through the phone's speaker 0.3 s later, from the debug app's
`TestAudioPlayerService`. Timing: the `SttTiming` "listen" line
(`ListenTiming`), ms from the request (`requestedAt`: the IME's
`startListening`, the sheet's start, `onStartListening`, Dictate's tap).
Surfaces: keyboard = `ime set` to our voice IME over a focused EditText (the
step FUTO's mic key triggers; FUTO's own delay isn't included), popup =
`RECOGNIZE_SPEECH` to our activity, service = `SpeechRecognizer` default
service (set to ours), Dictate = the app's mic. Texts read from the screen
or `onResults`.

The `.ort` A/B ran in the debug `SpeedBenchmarkService` (outside top-app,
like the RecognitionService, so absolute loads are about 1.7× the
keyboard's): `.ort` files pushed next to the installed `.onnx` files, two
runs (`ort78a`: 3 clips, `cpu` and `cpu-noprepack`; `ort78b`: 2 clips,
`cpu`), seeds 78 and 7878, cool-down 15 s, screen off, none disturbed.

## Before → after, Parakeet Ultra (ms from the request)

| Surface | Mic open | Ready (model) | First partial | Words lost |
|---|---|---|---|---|
| Keyboard | 2620 → 41 | 2620 → 2750–3690 | 3710 → 3280–4290 | 7 words (2.6 s) → none |
| Keyboard, 2nd tap in the panel | 2620 → 40 | 2620 → 41 | 3710 → 1861 | → none |
| Popup | 2700 → 27 | 2700 → 2790 | 3540 → 3370 | (not readable) |
| Popup, cold process | 3350 → – | | 4340 → – | |
| RecognitionService | 4360–7440 → 26–39 | 4360–7440 → 4380–7410 | 5500–8600 → 5300–10550 | ~4.5 s → none |
| Dictate | 2720 → 31–39 | 2720 → 32–82 (preloaded) | 3660 → 1500–1800 | 6–7 words → none |

## Before → after, other engines (keyboard; load ms, first partial ms)

| Engine | Load before | Load after | Partial before | Partial after | Final (Tap stop at 13.3 s) |
|---|---|---|---|---|---|
| Parakeet 110M | 1234–1241 | 1278–1460 | 2143–2252 | 1723–1805 | 13.4–13.6 s → 13.5–13.6 s |
| Nemotron 3.5 | 2720–3233 | 2729–3265 | 3967–5334 | 3391–3977 | same |
| Whisper turbo | 3259–3845 | 2386–3322 | (no live partials) | | 18.0–18.7 s → 20.8–21.9 s |
| Qwen3-ASR | 4907–5439 | 4100–4109 | 14371–14913 | 16366–16479 | 16.6–16.8 s → 16.5 s |

Turbo's and Qwen3's later text after: they now decode the whole sentence
(13 s) instead of what was left after the lost start (10 s).

## `.onnx` → `.ort` on the 6a (SpeedBenchmarkService, `cpu`)

| Engine | Load, run a | Load, run b | Own peak memory | Decode | Text |
|---|---|---|---|---|---|
| Parakeet Ultra | 5627 → 5589 | 4742 → 4307 | +357/+386 MB | same | 3/3, 2/2 identical |
| Parakeet 110M | 2306 → 1603 | 2130 → 1349 | ±0 | same | identical |
| Nemotron 3.5 | 7007 → 3507 | 4740 → 3600 | +568/+593 MB | same | identical |
| Whisper turbo | 4174 → 3881 | 3956 → 3813 | +105/+177 MB | same | identical |
| Qwen3-ASR | 6975 → 5619 | 6764 → 5444 | ±0 | same | identical |

Prepacking off (`cpu-noprepack`, run a): loads 10–45 % faster, but decodes
Ultra 1.0–1.6×, Nemotron 1.3–1.9×, Qwen3 2.2–2.4× slower. Rejected.

## The `.ort` route (published)

- Max approved the switch for 110M, Nemotron and Qwen3-ASR on 2026-10-10
  (Nemotron's +0.6 GB load peak accepted). Ultra and turbo stay on `.onnx`.
- Files: releases `v9` (110M), `v10` (Nemotron) and `v11` (Qwen3-ASR) of
  https://github.com/maxwhipw/marmalade-stt-engines, exactly the frozen files
  in `files.tsv`, plus each engine's unchanged tokenizer files. Every public
  asset was re-downloaded and its SHA-256 checked after publishing.
- App: `SttEngineDescriptor.previousFiles` + `replacingFiles` (5320623): an
  engine installed from its `.onnx` files stays Installed and runs from
  them, with no download; new installs get the `.ort` files.
- Re-check on every ONNX Runtime bump: Fixed-style files hold fused CPU ops
  of the converting version. A re-conversion needs a new tag and new sums.

### Pixel 6a check of the switch (release build, 2026-10-10)

Release build of `child/78-ort-catalog`, installed over a release build
whose catalog still had the `.onnx` files. Load = the app's "Loaded … in"
line, cold, from the speak-now popup (top-app). Text = Dictate, the clip
played through the phone's speaker (ls10-1 English, a FLEURS Hindi and a
FLEURS Chinese clip).

| Engine | Kept `.onnx` install after the update | Load `.onnx` (ms) | Fresh `.ort` download | Load `.ort` (ms) | Text |
|---|---|---|---|---|---|
| Parakeet 110M | Installed, dictates | 1214, 1244, 1245 | 137 MB, installs | 736, 762 | identical, the full sentence |
| Nemotron 3.5 | Installed, dictates | 3185 | 682 MB, installs | 1882, 2206 | identical, matches the reference |
| Qwen3-ASR | Installed, dictates | 3937 | 988 MB, installs | 2895, 3080 | correct (`.onnx` run: two slips through the speaker) |

Engine cards show the `.ort` set's size for a kept `.onnx` install
(110M: 137 MB instead of 136 MB), because the size comes from the catalog.
