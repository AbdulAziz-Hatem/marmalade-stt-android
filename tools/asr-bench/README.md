# asr-bench: host WER benchmark of the app's engines

Measures word error rate for every engine in `SttEngineCatalog.kt` on public
test sets, on a desktop CPU, with the same int8 files and the same sherpa-onnx
configuration the app uses. Accuracy doesn't depend on the device, so these
numbers carry over to phones; speed does, so the RTF here is only relative.
Results from the first run: `docs/benchmarks/2026-09-host-wer.md`.

## What "the same as the app" means here

| App (Kotlin) | Harness (Python) |
|---|---|
| sherpa-onnx v1.13.8 built from source, TTS off, with `third_party/patches/sherpa-onnx` applied, and kaldi-native-fbank v1.22.3 with `third_party/patches/kaldi-native-fbank` (#53) | the same tags and patches, built from source by `setup.sh`, TTS/diarization/binaries off |
| ONNX Runtime 1.28.0 (Android AAR) | ONNX Runtime 1.28.2 (linux-x64, what sherpa v1.13.8's CMake pins) |
| `SttEngineCatalog.kt` files, pinned by revision + sha256 | `asrbench/engines.py` `ENGINES`; `tests/test_engines.py` fails if it drifts from the catalog. `CANDIDATES` are comparison-only engines the app doesn't ship (#25: Parakeet 110M's CTC head, Parakeet v3 fp32, Parakeet Ultra fp32; #29: SenseVoice-Small; `RETIRED`: the engines that left the catalog in #45, with the files and configs the app had). Engines marked `converted` (Parakeet Ultra) are built by `tools/parakeet-ultra/convert.py`, not downloaded: copy its output into `$WORK/models/<id>/` and `fetch_models` only checks the hashes |
| `SherpaBackends.kt` configs: greedy search, 2 threads (`--threads`; the app uses 2–4 by device since #20), Whisper tail padding 1000 and its per-language token budget (#54; `--no-whisper-budget` turns it off, tags `@nobudget`), 0.3 s of zeros after every Parakeet decode (#25), Canary PnC on, Qwen3 defaults | `asrbench/backends.py`, field for field |
| `AudioPreprocessor`: 100 Hz Butterworth high-pass | `session.HighPass` (tested against a port of the Kotlin loop) |
| `SoftAgc` after the high-pass, only without a platform AGC | `session.SoftAgc` with `--soft-agc` |
| Silero VAD: threshold 0.5, min speech 0.25 s, min silence 1.2 s (`SessionOptions.DEFAULT_MIN_SILENCE_SECONDS`; 0.9 s before #43, so results from before #37's review used 0.9), max 28 s, 512-sample windows | `backends.silero_vad` + `session.py`, with the app's bundled `silero_vad.onnx` |
| `StreamingSession` (offline families): Final = decode of 0.6 s pre-roll + each VAD segment; stop flushes the VAD; an utterance still going at `UtteranceCap`'s cap (28.6 s; Whisper in Hindi 21 s, #54) is cut at its quietest window (#48) | `session.offline_session`, `session.max_decode_samples`; `run.py --max-decode S` tries another cap (tags `@capSs`) |
| `OnlineSession` (Nemotron): 100 ms hops into an OnlineRecognizer stream per VAD utterance, started 1 s before the onset (silence-padded near mic open), `inputFinished` at the segment end | `session.online_session` |

Differences, on purpose:
- Audio comes from files. Each clip gets one gain as a stand-in for the
  platform AGC (RMS to −25 dBFS, at most +30 dB, peak ≤ −1 dBFS), 0.5 s of
  −70 dBFS noise before it (a user taps the mic, then speaks) and 1 s after
  it before "stop" (a user pauses, then taps stop), cut into 100 ms hops. No
  NoiseSuppressor. Why the gain: FLEURS is recorded at about −40 dBFS peak,
  and at that level Silero finds onsets late (dropping first words) and
  holds segments open for seconds after speech ends. `--no-agc` measures
  that; `--no-agc --soft-agc` is a phone without a platform AGC, where the
  app runs its own gain control (`SoftAgc.kt`, ported as `session.SoftAgc`)
  before the VAD. Why the lead-in: a user taps the mic, then speaks.
  `--lead-silence 0` measures speech from the mic's first sample, which the
  app clipped before #15.
- Live partials are skipped in WER runs; they never change a Final.
  `offline_session(partials=True)` runs StreamingSession's partial
  schedule (#20) on a `SimClock` for `latency.py` (below).
- Near-silence is judged as the app judges it, on the audio before
  `SoftAgc` (#20), and written per Final as `near_silent`, with the level it
  judged as `rms_db` (#57; a `--direct` text gets the whole clip's level);
  scoring doesn't use it. Each Final's own text is in `finals` (#57); `hyp`
  joins them with spaces.
- Engines that take a language hint get the test set's language (as a user who
  picked it). `--auto` runs measure the app's default of auto-detect.
- The sherpa-onnx Python package's `__init__` imports TTS classes, so the
  harness imports the native `_sherpa_onnx` module directly.

## Data

All fetched without an account, from fixed seeded subsets
(`random.Random(14)`). All CC-BY-4.0 except AISHELL-1 (Apache-2.0) and
VoxPopuli (CC0):

| Set | What | n |
|---|---|---|
| `ls-clean`, `ls-other` | LibriSpeech test-clean / test-other (openslr.org) | 200 each |
| `fleurs-en` | FLEURS en_us test (google/fleurs): many speakers, varied mics | 200 |
| `ami-ihm` | AMI meetings, headset mics, test split, utterances ≥ 2 s and ≥ 4 words (edinburghcstr/ami): spontaneous speech, many non-native accents | 200 |
| `ami-long` | the same, utterances ≥ 8 s (#43): long spontaneous turns with pauses inside, for the end-of-turn benchmark | 200 |
| `ami-multi` | turns of 2+ sentences, 8–20 s, rebuilt by joining a speaker's contiguous AMI segments (#43; every AMI segment is one sentence) | 200 |
| `fleurs-es`, `fleurs-de`, `fleurs-fr` | FLEURS es_419 / de_de / fr_fr test | 50 each |
| `fleurs-ja`, `fleurs-zh`, `fleurs-ko` | FLEURS ja_jp / cmn_hans_cn / ko_kr test (#29): punctuated, numbers as digits | 200 / 100 / 100 |
| `fleurs-it`, `fleurs-pt` | FLEURS it_it / pt_br test (#64: the last two UI languages) | 100 each |
| `vox-de`, `vox-es`, `vox-fr`, `vox-it`, `vox-en` | VoxPopuli test (`facebook/voxpopuli`, pinned revision, #64): European Parliament speeches, gold transcripts that are one whole sentence. Spontaneous and half-read speech with real hesitation pauses | 100 each |
| `aishell1` | AISHELL-1 test, 20 speakers (openslr SLR33; files from the ungated per-file mirror `shenyunhang/AISHELL-1` at a pinned revision, since the original is one 15 GB tarball): read Mandarin, no punctuation, numbers spelled out | 200 |
| `zeroth-ko` | Zeroth-Korean test (openslr SLR40, via `kresnik/zeroth_korean`): read Korean, no punctuation, numbers spelled out | 100 |
| `kokoro-speech-ja` | Kokoro Speech Dataset v1.3 (kaiidams, public domain; #52): one LibriVox reader of 14 Meiji–Shōwa novels, Aozora Bunko text, automatically aligned. Literary read speech | 200 |
| `homophones-ja` | 76 sentences whose target words share a reading with other words (`homophones-ja.tsv`), read by two Kokoro-82M voices (#52). **Synthetic**: a kanji-choice check | 152 |
| `dictation` | 40 dictation sentences (`dictation.tsv`: names, numbers, dates, times, money, file names, emails, URLs), synthesised with Kitten TTS. **Synthetic**: a formatting check, not an accuracy truth | 40 |
| `noise` | six 10 s clips without speech (silence, white/pink noise, clicks, mains hum) | 6 |
| `noise-short` | 1, 2 and 4 s clips without speech (#57): digital silence, the −70 dBFS mic floor, room tone, a breath, a tap, clicks, hum. The length of a VAD false trigger plus the pre-roll, for `--direct` runs | 21 |

**Japanese real speech is FLEURS plus the Kokoro Speech Dataset** (#52).
JSUT and JVS audio is licensed for non-commercial research and personal use
only. ReazonSpeech is limited to analysis under Article 30-4 of Japan's
copyright act, and its HF repo is gated behind a click-through that needs an
account; an ungated mirror of its test split exists, but using it would skip
the licensor's acceptance step, so it isn't used (head's ruling on #52).
FLEURS-ja gets 200 utterances. The Kokoro Speech Dataset is one speaker
reading old novels, so it complements FLEURS rather than replacing a
dictation set. **AISHELL-1** is Apache-2.0 on
openslr.org, but the same page also says "The data is free for academic use";
it is kept in its own column so it can be dropped.

**Common Voice is not included.** Mozilla moved it to the Mozilla Data
Collective, which needs an account, and the `mozilla-foundation` datasets are
gone from HuggingFace. Ungated third-party mirrors exist, but re-hosting goes
against Common Voice's download terms, so they aren't used. `fleurs-en` and
`ami-ihm` cover speaker, accent and mic variety instead.

## Run it

Everything big lives in a scratch directory `$WORK` outside the repo (models
~5.5 GB, downloaded test sets ~6.5 GB). Nothing there is committed.

```sh
export WORK=/path/to/asr-bench-work            # any scratch dir with ~15 GB free
tools/asr-bench/setup.sh                               # venv + patched sherpa-onnx from source (GPL guard included)
cp app/src/main/assets/vad/silero_vad.onnx "$WORK/"
cd tools/asr-bench
$WORK/venv/bin/python -m asrbench.fetch_models --work $WORK     # every catalog engine, sha256-checked
$WORK/venv/bin/python -m asrbench.prepare_data --work $WORK     # LibriSpeech, FLEURS, AMI
$WORK/venv/bin/python -m asrbench.make_synthetic --work $WORK noise noise-short dictation   # needs marmalade-tts kitten
WORK=$WORK ./run_all.sh                                # decode everything, then score
```

`run_all.sh` runs 3 jobs at a time (`PARALLEL`), each one recognizer with 2
ONNX Runtime threads at `nice 10`: about 6–7 busy cores. It is resumable. The
scores land in `$WORK/results/summary.md` and `summary.json`; per-utterance
hypotheses in `$WORK/results/<engine>/<set>.jsonl`.

Single runs: `python -m asrbench.run --work $WORK --engine <id> [--auto|--direct] <sets...>`.
`--direct` skips the VAD and feeds each clip straight to the model (what a VAD
false trigger would hand it). Variants are tagged in the file name next to
the default run: `--preroll 1.0` (seconds kept before Silero's onset; the
app's 0.6 s offline / 1.0 s streaming otherwise) → `@preroll1s`,
`--lead-silence 0` → `@lead0s`, `--no-agc` → `@noagc`, `--soft-agc` →
`@softagc`, `--tail-padding 0.5` (seconds of zeros after each offline decode;
the app's 0.3 s for Parakeet, none otherwise) → `@tail0.5s`. `--limit N`
decodes only the first N utterances of each set, for engines too slow for the
full set (#25's streaming Parakeet Unified). `--hint LANG` gives the engine
that language hint instead of the set's (→ `@hint-LANG`; #57 runs the noise
sets once per language), and `--auto` with `--direct` tags `@direct@auto`. `score.py` adds a comparison table per variant. `--results DIR`
writes to another directory under `$WORK`, and `score.py --results DIR
--baseline OTHER` compares a variant with another run's defaults (the app
before a fix vs after it).

**Regression check after a pre-roll or session change** (#18):
`python -m asrbench.regress --work $WORK [--preroll 0.8]`. It decodes the 16
AMI clips on which Parakeet returned nothing in #15 (silence before an abrupt,
mid-word start) with every offline NeMo engine, after 0.5 s and 1 s of
lead-in, and exits 1 when an engine has more empty results than at the app's
settings (Parakeet v3 1 and 2 since #25's tail padding, the other Parakeets
and Canary 0). A few minutes. At a 0.8 s pre-roll Parakeet v3 had 14 and 9
(#15, before the padding). Run it after changing the pre-roll or the tail
padding too.

**End of speech → Final latency** (#20):
`python -m asrbench.latency --work $WORK [--set ls-clean] [--scale 0.79]`.
Runs the offline loop with live partials on virtual time: hops arrive in
real time and each decode takes what a cost model says (default: distil-small-en
at 2 threads on the Pixel 8a, fitted to the #11 `SttTiming` logs; `--engine
<id>` uses the host's wall time instead; `--scale` multiplies either). Prints
median / p90 / max latency and the first-partial delay for 5, 10 and 22 s
clips. A few seconds with the cost model: only Silero runs.
Since #30 it also prints how far the session fell behind the mic (median
and max over clips of each clip's worst lag) and how many clips the
session's `LagMonitor` called too slow. `--scale 3` is a phone three times
slower than the Pixel 8a; `--no-catch-up` is the app before #30.
`--streaming` runs `online_session` (Nemotron's loop) with an illustrative
per-call cost (`--overhead` s + `--rtf` × audio; no phone measurement of
Nemotron's per-call cost exists yet).

**Partial quality** (#28):
`python -m asrbench.partials --work $WORK --engine <id> [--limit N] [--scale S] [--host-cost] <sets...>`.
Runs the same offline loop with live partials on virtual time, with the real
model decoding, so the partial texts are real and arrive on the Pixel's
schedule (the `pixel-distil-2t` cost model × a per-engine scale from the host
RTFs, `SCALE_VS_DISTIL`; `--host-cost` uses host wall time instead). One run
scores three display policies over the same decodes:
`before` (the #19/#20 app: committed + latest decode, `cleanPartial` without
the tail-repeat cut), `tailcut` (the same text through #28's `cleanPartial`)
and `after` (the #28 app: `PartialStabilizer`, then `cleanPartial`). Per set,
a table per policy: partials shown; partials showing a word or 2–3 word block
repeated 3+ times; flicker (words shown and then taken back, per utterance
and in total); median time from VAD onset to the first word and to the first
word that stays first; and the last partial's WER against the utterance's
Final (what stabilizing must not cost). Partial texts go to
`$WORK/results-28/<engine>/partials-<set>.jsonl` (`--results` to change;
resumable), and `--rescore` prints the tables from those files without
decoding. `--no-agc --soft-agc` feeds the clip at its recorded level
through the app's `SoftAgc`, as on a phone without a platform AGC (the
Pixel 8a); use another `--results` directory for it. The text functions in
`asrbench/partials.py` are ports of
`Phantoms`, `TextPipeline.cleanPartial` and `PartialStabilizer` and must
change with them.

**End of dictation: Pause vs Smart Turn** (#43):
`python -m asrbench.turn --work $WORK [--engine parakeet-tdt-0-6b-v3] ami-ihm ami-long ami-multi`.
Runs every clip through the app's two ways of ending a dictation, on virtual
time: Pause (Silero's minimum silence ends the utterance; 0.2 / 0.5 / 0.9 /
1.5 s) and Smart Turn (a 0.2 s pause proposes the end, pipecat's smart-turn
v3.2 decides, re-checked every 0.5 s, 3 s ceiling; plus threshold, propose
and ceiling variants, `turn.MODES`; `--modes` also takes any
`pause-<s>` or `smart-p<propose>-t<threshold>-c<ceiling>` name, e.g.
`smart-p0.4-t0.6-c1.5`, via `turn.mode_config`; `-e<p>-n<s>` adds an early
check at the propose point that ends the turn only above `e`, the next check
coming at `n` s with the usual threshold, e.g. `smart-p0.5-e0.95-n1.2-t0.7-c2.5`,
#91). `TurnGate` is the rule on its own and
`turn_session` the loop around it; like the app, it cuts a turn still going
at 28.6 s with no segment boundary to end at (#48, mirrored in #54), and
decodes the rest only if it holds speech (`turn.decodable`). Ground truth
is AMI's manual word timings and punctuation (`asrbench/ami_words.py` fetches the 23 MB
annotation zip, CC-BY-4.0, into `$WORK/raw/ami/`): a turn ended before the
clip's last word starts is a false cut-off, charged to the pause it happened
in; latency runs from Silero's end of the last speech to the turn end.
Prints per mode: clips cut off, pauses cut by length and by kind
(mid-sentence or after a sentence end), latency, how the last turn ended
(model or ceiling), model checks and their host time, and with `--engine`
the WER of each mode's turns decoded once each. Per-clip turns go to
`$WORK/results-43/turn/<set>.jsonl`; `--rescore` reprints the tables.
`--bench-threads 1,2,4` times one model check per thread count. The model
(`smart-turn-v3.2-cpu.onnx`, BSD-2-Clause, sha256-checked) is copied into
`$WORK/models/smart-turn-v3.2/` from marmalade-stt-cli; it isn't
downloaded. Its features come from `asrbench/whisper_features.py`, pipecat's
numpy port of Whisper's log-mel (matches `transformers`'
`WhisperFeatureExtractor` to 1e-6). Results:
`docs/benchmarks/2026-09-smart-turn-ami.md`. Like StreamingSession, the
gate's "now" is where the VAD is (whole 512-sample windows), not the hop's
end: the model's window ends there and the silence is counted there (#64).

**Smart Turn defaults across languages and silence** (#64):
`python -m asrbench.turn_defaults --work $WORK <sets>`, then `--rescore`
(`--max-speech 5` keeps short requests only). The same gate on the app's
three Patience levels (`turn_defaults.PATIENCE`), Pause 1.2 s and two
in-between candidates, on AMI, FLEURS (`fleurs-it`/`fleurs-pt` added for
the UI languages) and VoxPopuli (`vox-de/es/fr/it/en`: parliament speeches,
CC0, one-sentence segments; `prepare_data.one_sentence`). Each clip runs
once per silence model (`--silence room,n70,n90,zero`): as recorded, or
with every pause and the lead/tail replaced (`quieten`, a noise-suppression
stand-in) by white noise at -70 / -90 dBFS or by digital zeros. Sets
without word timings are scored on Silero's segments
(`segment_reference`). Results in `$WORK/results-64/turn-defaults/`;
write-up `docs/benchmarks/2026-10-smart-turn-defaults.md`.

Tests: `python -m unittest discover -s tests` from this directory
(`test_engines` and `test_partials` need only the standard library;
`test_session`, `test_turn`, `test_turn_defaults`, `test_granite`, `test_cer`
and `test_homophones` need the venv).

## Granite Speech 5.0 470M TurboCTC (#26)

A harness-only engine family, not in the app's catalog. sherpa-onnx can't run
Granite, so `asrbench/granite.py` is a plain ONNX Runtime pipeline: a numpy
front-end (log-mel + deltas, 2× frame stacking), the ONNX model, then a CTC
greedy decode with byte-level BPE. It plugs into the same offline session as
the sherpa engines (`transcribe(samples) -> str`), so the VAD, the pre-roll
and scoring are unchanged. Engine ids (`granite.ENGINES`):
`granite-speech-5-470m-ctc` (int8), `-fp32`, and `-qwertz92` (a community
int8 export, checked as a download source). Results and the recommendation
are in `docs/benchmarks/2026-09-granite-spike.md`.

The model files come from an export, not `fetch_models`. The export needs its
own venv with torch and transformers (`granite/requirements.txt`, build time
only, no GPL):

```sh
hf download ibm-granite/granite-speech-5.0-470m-turboctc --revision 286456107c8ba1161f5c22dfe85466402c88333b \
  --local-dir $WORK/models/granite-speech-5-470m-src
cd $WORK/harness-<n>
../venv-granite-export/bin/python granite/export_onnx.py --src ../models/granite-speech-5-470m-src --work ..
```

It writes `$WORK/models/granite-speech-5-470m-ctc{,-fp32}/`, then checks the
numpy front-end against transformers', the fp32 ONNX logits and text against
PyTorch (the model card's path), and int8 against fp32. It exits 1 if the
fp32 text differs. The harness venv needs `onnxruntime` (in
`requirements.txt`). Set `OMP_NUM_THREADS=1 OPENBLAS_NUM_THREADS=1`, or
numpy's BLAS spreads the front-end over every core.

`granite/rtf_ab.py` compares decode speed on the same clips, with the engines
taking turns clip by clip, so load from other jobs lands on all of them. It
takes any engine (streaming ones decode each clip in one stream) and, with
`--lang`, gives each engine the set's language (#29).

## SenseVoice-Small and the CJK sets (#29)

SenseVoice-Small (FunAudioLLM) is a harness-only engine
(`engines.CANDIDATES`, id `sense-voice-small`): sherpa-onnx runs it
natively, so it goes through `backends.OfflineTranscriber` like the catalog's
offline engines (VAD, 0.6 s pre-roll, ITN on for punctuation and digits). The
language hint is the set's language; `--auto` passes "auto". Nemotron 3.5,
Whisper turbo and Qwen3 are also scored on ja/zh/ko.

```sh
cd $WORK/harness-<n>
../venv/bin/python -m asrbench.prepare_data --work .. fleurs-ja fleurs-zh fleurs-ko aishell1 zeroth-ko
../venv/bin/python -m asrbench.fetch_models --work .. sense-voice-small
../venv/bin/python -m asrbench.run --work .. --results results-29 --engine sense-voice-small \
  fleurs-ja fleurs-zh aishell1 fleurs-ko zeroth-ko
../venv/bin/python -m asrbench.score --work .. --results results-29
```

`score.py` scores these sets by character error rate (`asrbench/cer.py`):
NFKC, lower case, Traditional to Simplified for Chinese, punctuation, symbols
and spaces removed. Numbers are left alone, so a second table scores only the
utterances where no reference and no engine's output has a digit (the same
utterances for every engine). Punctuation is scored against FLEURS's
punctuated references as F1 over marks after aligned characters (all
classes, commas, sentence ends).

First visible words: `latency.py --engine <id> --lang <l>` also runs
streaming engines (`online_session` with the real model, each decode costing
its host wall time) and prints a first-text column: from the onset of the
utterance that first shows a word (a letter or digit, not a stray ".") to
that partial or Final. Its end-of-speech column is unreliable on FLEURS,
whose low-SNR padded clips make the speech-end estimate late.

## Does Nemotron 3.5 stay? (#37)

`run_37.sh` decodes the CJK sets and FLEURS Hindi, Arabic, Turkish,
Vietnamese and Norwegian (`fleurs-hi/ar/tr/vi/nb`, 100 each: the most-spoken
languages Nemotron 3.5 covers and Parakeet doesn't) with Whisper turbo,
Nemotron 3.5 560 / 1120 ms and Qwen3-ASR, hinted and with `--auto`. Whisper
turbo needs the byte-level BPE patch (`setup.sh` applies it); results from a
venv built before #37 lose CJK characters.

```sh
cd $WORK/harness-<n>
../venv/bin/python -m asrbench.prepare_data --work .. fleurs-hi fleurs-ar fleurs-tr fleurs-vi fleurs-nb
WORK=$WORK RESULTS=results-37 ./run_37.sh
```

- The app offers Qwen3 no language hint for these five languages, so its
  auto runs are what the app does; its hinted runs (the harness adds Qwen3's
  English names for hi/ar/tr/vi) show what adding the hint would give.
  Whisper turbo takes the hint in the app since #37's review, with Norwegian
  as Whisper's `no` (`WHISPER_LANGUAGE_CODES`, as in `SherpaBackends.kt`).
- `score.py` scores these sets by WER after `normalise_world()`. Whisper's
  basic normaliser turns combining marks into spaces, which splits Hindi
  words at every vowel sign. Arabic also drops short vowels and folds alif,
  alif maqsura and taa marbuta forms.

## Japanese engines and the homophone set (#52)

Harness-only Japanese candidates (`engines.JAPANESE`): `parakeet-ja-ctc`
(NVIDIA's Parakeet TDT_CTC 0.6B ja, k2-fsa's CTC export), `reazonspeech-k2-v2`
(icefall offline Zipformer, family `transducer`), `funasr-nano` (Fun-ASR-Nano:
an encoder feeding a Qwen3-0.6B LLM; family `funasr_nano`, prompted with
Chinese language names, "日文"), `qwen3-asr-1-7b` (a community int8 export,
for its memory) and `kotoba-whisper-v2-0` (Kotoba-Whisper v2.0, our own
conversion, below). Results: `docs/benchmarks/2026-10-japanese.md`.

```sh
cd $WORK/harness-<n>
../venv/bin/python -m asrbench.prepare_data --work .. kokoro-speech-ja
../venv/bin/python -m asrbench.fetch_models --work .. parakeet-ja-ctc reazonspeech-k2-v2 funasr-nano qwen3-asr-1-7b
WORK=$WORK RESULTS=results-52 ./run_52.sh
```

**Kotoba-Whisper** is published only as a transformers checkpoint.
`kotoba/convert.py` renames its tensors to openai-whisper's, runs k2-fsa's
`scripts/whisper/export-onnx.py` on it unchanged and writes the int8 files
into `$WORK/models/kotoba-whisper-v2-0/` (its docstring has the command; its
own venv, `kotoba/requirements.txt`, build time only). `--check` compares
transformers and openai-whisper on five FLEURS clips.

**The homophone set** (`homophones-ja.tsv`: id, text, targets as
`word:reading:competitor,…`, `|` between targets) is synthesised by
`homophones/synth.py` with Kokoro-82M in its own venv
(`homophones/requirements.txt`): two voices, jf_alpha and jm_kumo. Kokoro's
package imports its English pipeline, which needs espeak-ng (GPL-3.0); the
script imports `kokoro.model` without it, so espeak-ng is never installed.
It stops if UniDic's reading of a target differs from the TSV's, since the
clip would then test a different word.

```sh
python homophones/synth.py --work $WORK   # in the homophones venv
```

`asrbench/homophones.py` locates each target in the hypothesis (character
alignment) and scores it **correct** (the right kanji), **homophone** (a
listed same-reading word), **kana** (the reading, no kanji) or **other**
(misheard, dropped, or an unlisted form). `score.py` adds that table, and
the list of every target an engine missed, to the CJK section; the set's CER
is scored like any CJK set. Targets come from the TSV, so a corrected
competitor list rescores old results without new audio.

## Whisper turbo speed (#46, #53)

`setup.sh` builds side-by-side venvs for comparing builds: `VENV=venv-x
PATCH_ROOT=<dir with sherpa-onnx/ and kaldi-native-fbank/ patch dirs>`
builds `$WORK/venv-x` from those patches (one build directory per venv,
`$WORK/patched-<venv>`), leaving the shared `venv` alone.

`run.py --whisper-tail-paddings N` (tags `@wtailN`) and `partials.py
--whisper-tail-paddings N` set how many zero feature frames (10 ms each)
sherpa-onnx appends after the speech in Whisper's encoder input; the app's
is 1000 (`SherpaBackends.kt`). The encoder runs on speech + padding, capped
at 30 s, so this is the "shorter encoder window" knob: sherpa's Whisper
export already takes any length. `tail_table.py` lists tail variants (or any
result directories) side by side per set, also re-scored with a repeated
block cut off (`cut_repeats`), and `decode_speed.py` times features and
model per decode against utterance length, with the variants taking turns:

```sh
cd $WORK/harness-<n>
../venv/bin/python -m asrbench.run --work .. --results results-46 --engine whisper-turbo \
  --whisper-tail-paddings 300 fleurs-en fleurs-zh ami-ihm
../venv/bin/python -m asrbench.tail_table --work .. results-46: results-46:@wtail300 fleurs-en fleurs-zh ami-ihm
../venv/bin/python -m asrbench.decode_speed --work .. --engine whisper-turbo --threads 4 \
  --lengths 2,5,10,20 --whisper-tail-paddings 1000,600,300 --out ../results-46/speed.jsonl
```

`engines.DRAFTS` are multilingual Whisper tiny and base, measured as hidden
drafts for turbo's live partials. Results: `docs/benchmarks/2026-10-turbo-speed.md`.

## Re-quantised and fine-tuned turbo variants (#55)

A variant is a directory holding the engine's three files under their
catalog names (symlinks are fine). `run.py --model-dir DIR --variant TAG`
decodes with it and tags the output `@TAG`; `--no-arena` turns ONNX
Runtime's CPU memory arena off as the app's `OrtTuning` `-noarena` does
(tags `@noarena`). `decode_speed.py --variant LABEL=dir:DIR,arena:off,
threads:N,mnt:N,tail:N` (repeatable) times named variants taking turns;
`mnt:1` stops every decode after one token, so it times the encoder plus one
decoder step (the decoder's share is the full decode minus that).
`paired.py` compares tagged runs against a baseline per set: the error-rate
difference with a 95 % paired bootstrap interval, utterances worse/better,
hypotheses changed, and runaways (a repeated block, `cut_repeats`):

```sh
cd $WORK/harness-<n>
../venv-54/bin/python -m asrbench.run --work .. --results results-55 --engine whisper-turbo \
  --model-dir ../models-55/v-int8-h8 --variant int8-h8 --limit 100 fleurs-ja ami-ihm
../venv-54/bin/python -m asrbench.paired --work .. --results results-55 --engine whisper-turbo \
  --variants @int8-h8 fleurs-ja ami-ihm
../venv-54/bin/python -m asrbench.decode_speed --work .. --engine whisper-turbo --threads 4 \
  --variant int8 --variant 'enc=mnt:1' --variant 'h8=dir:../models-55/v-int8-h8'
```

`turbo/quantize.py` re-quantises turbo's files with ONNX Runtime's
`MatMulNBits` quantiser (its own venv, `turbo/requirements.txt`, build time
only): k2-fsa's export leaves the decoder's LM head fp32 (a MatMul on a
transposed weight that `quantize_dynamic` skips); `--head 8 --gather8` makes
it and the token embedding 8-bit, and `--bits 4` re-quantises every MatMul of
the fp32 export. Its docstring has the commands; `tests/test_turbo_quantize.py`
runs in that venv.

`kotoba/convert.py --model acft-whisper-turbo` converts Mahmoud Ashraf's
audio-context fine-tune of turbo (`MODELS` pins it) the same way as Kotoba:
turbo's shape, exported as `turbo`. Its `--check` borrows turbo's
`generation_config.json`, which the fine-tune's repo lacks. Results:
`docs/benchmarks/2026-10-turbo-faster.md`.

## Phantom phrases per language (#57)

`run_57.sh` collects what each app engine that writes free text invents for
noise, per language. Every engine decodes the `noise` and `noise-short`
clips whole (`--direct`), once for each language hint the app offers it
(Qwen3: the developer list), with and without the AGC stand-in. Then each
engine reads the first 100 clips of the FLEURS sets it is offered for, with
that language picked, through the app's VAD session: a lead-in that becomes
a segment of its own shows the engine's phrase for the quiet before speech.
`ACFT=<model dir>` adds #55's ACFT turbo.

```sh
cd $WORK/harness-<n>
WORK=$WORK RESULTS=results-57 PY=$WORK/venv-54/bin/python ACFT=$WORK/models-55/v-acft-h8g8 ./run_57.sh
```

`phantoms.py` prints the tables: per engine, hint and text, on how many
clips it came with and without the AGC, and the levels the app would judge
(`rms_db`; below −50 dBFS is near-silent). `--mine DIR...` lists the texts
that recur as the first or last Final of a clip in speech runs (also older
runs: for ja/zh/ko it splits the joined hypothesis on spaces). The app's
lists are `Phantoms.WHISPER`, `QWEN3` and `CANARY` in `:textproc`;
results: `docs/benchmarks/2026-10-phantoms.md`.

## Scoring

See the docstring of `asrbench/score.py`. In short: corpus WER; Whisper's
English normaliser for English, its basic normaliser for es/de/fr; raw WER
(case and punctuation count) for the dictation set; 95 % bootstrap intervals;
counters for empty output, runaway output, dropped last words, VAD flushes,
and any text on the noise clips.

## Licenses of what the harness pulls in

sherpa-onnx and ONNX Runtime: Apache-2.0 / MIT. Python packages: see
`requirements.txt` (MIT, BSD, Apache-2.0; `opencc-python-reimplemented` is
pure Python, so none of OpenCC's C++ or its bundled marisa-trie) and, for the Granite export venv,
`granite/requirements.txt` (BSD, Apache-2.0, MIT). No GPL or LGPL code is built or
imported: `setup.sh` never fetches espeak-ng and fails if an `espeak`/`piper`
string shows up in the built libraries. Audio is decoded with miniaudio's
public-domain dr_libs, not libsndfile.

#43 adds `asrbench/whisper_features.py`, copied from marmalade-stt-cli, which
ported it from pipecat (BSD-2-Clause, Daily; the license text is in the file's
header), and uses pipecat's smart-turn v3.2 model (BSD-2-Clause) and AMI's
manual annotations (CC-BY-4.0) from `$WORK`, neither committed. Harness only:
none of it is in the APK.

#52 adds three build-time venvs outside the harness's, none of them
reaching the APK: `kotoba/requirements.txt` (torch, openai-whisper, onnx,
onnxruntime, transformers: BSD, MIT, Apache-2.0), and
`homophones/requirements.txt` (Kokoro-82M's code and weights, Apache-2.0;
misaki, Apache-2.0; fugashi with MeCab under its BSD option; unidic-lite,
whose UniDic 2.1.2 is GPL/LGPL/BSD triple-licensed and used under BSD;
pyopenjtalk-plus, MIT, with OpenJTalk under BSD). kokoro and misaki are
installed without their dependencies so that espeak-ng (GPL-3.0) is never
pulled in. The Kokoro Speech Dataset's audio (LibriVox) and text (Aozora
Bunko) are public domain; its clips stay in `$WORK`.

One GPL tool is used at arm's length: the dictation audio comes from
`marmalade-tts kitten`, a separate local program whose Kitten engine
(Apache-2.0 weights) phonemises text with espeak-ng (GPL-3.0). The harness
only runs it as a subprocess and reads its WAV output, which stays in `$WORK`
and is never committed. Using a different TTS means changing
`make_synthetic.dictation`.
