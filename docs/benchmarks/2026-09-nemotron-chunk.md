# Nemotron 3.5 streaming: 1120 ms against 560 ms chunks (2026-09-28)

The app offers Nemotron 3.5 Streaming as one engine and picks the chunk size
for the phone: 560 ms on fast phones, 1120 ms on slower ones
(`NemotronVariant`). sherpa-onnx fixes the chunk size at export, so each is
its own download of the same checkpoint. This run measured the 1120 ms export
against 560 ms on the host, to set its speed cost and check its accuracy.

**Result:** 1120 ms costs **0.67×** the decode time of 560 ms. It is as
accurate or a little better, **once each utterance gets tail padding**.
Without padding it drops utterance endings about twice as often. The app now
feeds padding to every streaming engine (`streamingTailPaddingSamples`: the
chunk plus 0.2 s of zeros before `inputFinished`).

## Method

- `tools/asr-bench` on the benchmark host, the app's session path (`online_session`),
  2 threads, the language hint set as a user would pick it. The sets are
  those of `2026-09-host-wer.md` (English: LibriSpeech clean/other, FLEURS-en,
  AMI; FLEURS es/de/fr, 50 clips each) and `2026-09-cjk-sensevoice.md`
  (FLEURS ja/zh, CER).
- The two engines ran back to back on the same sets, the same night (at most
  3 jobs at once), so their RTFs compare. The absolute RTFs are host numbers.
- Tail padding: once without (the app before this change), once with zeros
  fed before `inputFinished`: 0.7 s for 560 ms and 1.3 s for 1120 ms. The
  app's padding (chunk + 0.2 s) is 0.76 s and 1.32 s.
- Latency: `asrbench.latency --engine <id>` on ls-clean and fleurs-zh, host
  wall time, with the harness now padding as the app does.
- Raw summaries: `2026-09-nemotron-chunk/` (`summary.md` is the unpadded run
  with the padded English WER as the `@tail` variants; latency tables per
  engine and set). The padded run's full tables are in `results-chunk-padded`
  on the benchmark host.

## Why padding

sherpa-onnx v1.13.8 decodes a streaming NeMo model in whole windows. The
1120 ms encoder's metadata gives `window_size=121` frames (1.21 s).
`inputFinished` doesn't pad, so up to a window of speech at the end of an
utterance never reaches the model. The longer the chunk, the more is lost.

Utterances that lost their last word (`tail_drop`):

| Set | 560 ms | 560 ms, padded | 1120 ms | 1120 ms, padded |
|---|---|---|---|---|
| ls-clean (200) | 4 | 3 | 10 | 3 |
| ls-other (200) | 4 | 4 | 14 | 2 |
| fleurs-en (200) | 4 | 4 | 7 | 5 |
| ami-ihm (200) | 25 | 19 | 41 | 19 |
| fleurs-de (50) | 2 | 2 | 7 | 2 |
| fleurs-fr (50) | 1 | 1 | 7 | 2 |
| fleurs-ja (200) | 1 | 1 | 20 | 1 |
| fleurs-zh (100) | 6 | 6 | 26 | 4 |

## Accuracy, with padding (what the app runs now)

| | 560 ms | 1120 ms |
|---|---|---|
| LibriSpeech clean WER | 3.2 | 2.8 |
| LibriSpeech other WER | 7.8 | 7.1 |
| FLEURS-en WER | 10.2 | 9.5 |
| AMI WER | 13.4 | 12.9 |
| **English mean** | **8.65** | **8.1** |
| FLEURS es / de / fr WER | 7.9 / 10.1 / 13.8 | 7.2 / 10.0 / 13.0 |
| FLEURS-ja CER | 14.7 | 14.6 |
| FLEURS-zh CER | 22.7 | 22.2 |

Without padding, 560 ms's English mean is 8.75 (the number in
`2026-09-host-wer.md`) and 1120 ms's 8.8.

The quality marker stays "Good" (3) for both: 8.1 is just over the 8.0
line for "Great".

## Speed and memory

| | 560 ms | 1120 ms | ratio |
|---|---|---|---|
| RTF, English sets, padded | 0.152 | 0.102 | 0.67 |
| RTF, ja/zh, padded | 0.118 | 0.078 | 0.66 |
| RTF, English sets, unpadded | 0.144 | 0.091 | 0.63 |
| Peak RSS MB, English sets | 2088 | 2103 | |

`SpeedBudget.REFERENCE` gives 1120 ms 560 ms's cost × 0.67: **0.80**
against distil-small.en (560 ms: 1.20). With the speed budget's 1.25
tolerance, 560 ms fits phones from 0.96× a Pixel 8a up, and 1120 ms from
0.64×.

## Latency (host, 2 threads, with padding)

End of speech → Final, median by utterance length:

| Set | 560 ms (5 / 10 / 20 s) | 1120 ms (5 / 10 / 20 s) |
|---|---|---|
| ls-clean | 1.1 / 1.1 / 1.1 s | 1.1 / 1.1 / 1.1 s |
| fleurs-zh | 0.4 / 0.8 / 1.0 s | 0.4 / 0.8 / 1.0 s |

Both are set by Silero's 0.9 s end-of-speech silence, not by decoding: the
host decodes the padding in a few tens of milliseconds. No session fell
behind. On a phone the padding costs its decode time at the end of each
utterance: at a Pixel-8a-like RTF of about 0.25, roughly 0.3 s for 1120 ms.
This hasn't been measured on a phone.

## Not measured

- Any phone. The 1120 ms cost is a host ratio applied to 560 ms's
  provisional Pixel cost.
- Korean, and the padded run's latency on a slow phone.
