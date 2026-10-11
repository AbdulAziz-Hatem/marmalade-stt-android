# Patches to sherpa-onnx v1.13.8

The `third_party/sherpa-onnx` submodule stays at the v1.13.8 tag, unmodified.
Both builds apply every `*.patch` here, in file-name order, with `patch -p1`
to a copy of its sources:

- the app: `app/src/main/cpp/CMakeLists.txt` copies the sources into the
  CMake build tree at configure time and patches the copy;
- the host benchmark: `tools/asr-bench/setup.sh` patches a copy of its
  sherpa-onnx clone before building the Python module.

sherpa-onnx is Apache-2.0. Patches from upstream keep their original header;
our own say so in theirs. Either way the header is the notice of what changed.

| Patch | Upstream | What it fixes | Drop when |
|---|---|---|---|
| `0001-byte-level-bpe-itn-3965.patch` | k2-fsa/sherpa-onnx #3965, commit `2edc882f` (merged 2026-09-20) | Whisper (and Cohere Transcribe) cleaned each byte-level BPE token of invalid UTF-8 on its own, which deleted every character split across two tokens ("支式教学" for "支架式教学"). Issue #37 has the CER before and after. | bumping past v1.13.8: the fix is in the first release after 2026-09-20 |
| `0002-whisper-no-per-second-token-cap.patch` | none: ours (#37). Still on master 2026-10-02 | The greedy Whisper decoder stopped after 6 tokens per second of audio, which cuts byte-level BPE scripts short: 98 of 100 Hindi test sentences, 73 of 200 Japanese ones. Only Whisper's own 224-token limit is left. | upstream drops or raises the cap |
| `0003-cancellable-offline-decodes.patch` | none: ours (#49). sherpa-onnx has no way to stop an offline decode as of v1.13.8 | A decode couldn't be interrupted, so a cancelled session held the engine until a runaway Whisper decode had run its 224 steps (the next listen got BUSY). `OfflineStream::Cancel()` sets an atomic flag that Whisper's, Canary's and Qwen3-ASR's decoder loops check before every step; the JNI exposes it (`OfflineStream.cancel` in the vendored Kotlin API). The encoder run is 0004's. | upstream adds a cancel |
| `0004-cancel-terminates-encoder-run.patch` | none: ours (#54), on top of 0003 | A cancel during the encoder still waited for it: 1.8 s on the host for 29.5 s of audio, several seconds on a phone. `OfflineStream` keeps the ONNX Runtime `RunOptions` of the run in progress (`CancellableRun`), and `Cancel()` sets their terminate flag, so the run stops between two graph nodes and throws; the recognizer returns no result for a cancelled stream. Covers Whisper's and Canary's encoders and Qwen3-ASR's conv frontend, encoder and prefill. On the host a cancel during any of them returned within 0–72 ms (`docs/benchmarks/2026-10-whisper-runaway.md`). | upstream adds a cancel |
| `0005-whisper-token-budget.patch` | none: ours (#54), on top of 0002–0004 | Whisper's greedy decoder stopped at n_text_ctx / 2 = 224 tokens: too many for a looping decode (seconds on a phone after Stop), too few for Hindi (17 tokens a second, so 13 s of speech). The stream option `max_new_tokens` (as Qwen3-ASR's) now replaces it, lower or higher; sherpa's existing offset check still stops at the cache's end (443 tokens after the prompt). The app sets it per language (`whisperMaxNewTokens`): FLEURS Hindi 31.0 → 30.8 WER, 11–35 s Hindi 39.3 → 31.5 (`docs/benchmarks/2026-10-whisper-token-budget.md`). | upstream makes the limit configurable |
| `0006-parallel-session-create.patch` | none: ours (#78), on top of 0001–0005 | Whisper's, Canary's and Qwen3-ASR's path constructors created their ONNX Runtime sessions one after another, and Qwen3-ASR built its BPE tokenizer (vocab.json, merges.txt) after all of them. The decoder's session is now created on a second thread (`std::async`) while the calling thread creates the encoder's (and Qwen3's conv frontend's), and Qwen3's tokenizer is built on another thread alongside the model; the `Init*` metadata reads run afterwards in the original order, and `future::get()` rethrows a worker's error. Construction on the host (a 16-core x86 desktop, 4 threads, median of 5): turbo 1031 → 726 ms, Qwen3 1822 → 1461 ms, Canary 583 → 520 ms; identical text on 5 FLEURS clips each. Transient memory: two sessions load at once (host peak RSS turbo 1567 → 1416 MB, Qwen3 1312 → 1490 MB, Canary 323 → 330 MB, glibc). The AssetManager constructors are unchanged. | upstream creates sessions in parallel |

When bumping sherpa-onnx, delete the patches the new tag already contains.
A patch that no longer applies fails the build.
