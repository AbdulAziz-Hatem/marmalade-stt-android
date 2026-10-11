# Patches to kaldi-native-fbank v1.22.3

kaldi-native-fbank is sherpa-onnx's feature extraction library (log-mel
filterbanks). The `third_party/kaldi-native-fbank` submodule stays at the
v1.22.3 tag, unmodified. As for sherpa-onnx (`../sherpa-onnx/README.md`),
both builds apply every `*.patch` here, in file-name order, with `patch -p1`
to a copy of its sources:

- the app: `app/src/main/cpp/CMakeLists.txt` (`patched_copy`) copies the
  sources into the CMake build tree at configure time, patches the copy and
  points sherpa-onnx's `FetchContent` at it;
- the host benchmark: `tools/asr-bench/setup.sh` clones the tag, patches a
  copy and passes it to sherpa-onnx's CMake the same way.

kaldi-native-fbank is Apache-2.0. Our patches say so in their header, and
each changed line is marked "Marmalade STT".

| Patch | Upstream | What it fixes | Drop when |
|---|---|---|---|
| `0001-whisper-feature-kissfft.patch` | none: ours (#53). Still on master 2026-10-02 | Whisper's log-mel (Whisper, Qwen3-ASR) ran a 400-point FFT that ends in a naive DFT: 14.4 ms of one core per second of audio, now 0.16 ms with knf's kissfft `Rfft`. Features change only by float rounding; transcripts in `docs/benchmarks/2026-10-turbo-speed.md`. Also builds `Rfft`'s kissfft plan once instead of every frame (Kaldi fbank 2.8× faster, output bit-identical) | upstream replaces the DFT |

When bumping sherpa-onnx, check which kaldi-native-fbank version its
`cmake/kaldi-native-fbank.cmake` pins, re-pin the submodule and `KNF_TAG` in
`setup.sh`, and delete the patches the new version already contains. A patch
that no longer applies fails the build.
