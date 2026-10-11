# Whisper turbo runaways: cost, step budgets, cancelling (#49, 2026-10-02)

**Question.** Patch 0002 (#37) removed sherpa-onnx's 6-tokens-per-second
cap, so a looping Whisper decode now runs to Whisper's own 224 steps. A
decode couldn't be interrupted: Cancel emitted nothing, but the engine stayed
claimed until the decode returned, and a new listen got BUSY. How long is
that, and should it be a cancellable decode or a looser step budget?

**Answer.** Far too long to wait out: about 8–10 s on the host at 4
threads, so roughly 30–40 s on the Pixel 8a. A step budget can't fix that
for every language (Hindi needs 17 tokens a second), so decodes are now
cancellable: sherpa patch 0003 checks a per-stream flag before every
decoder step, and a cancelled session sets it. On the host a cancelled
runaway returned within one decoder step (15–31 ms).

## Runaway cost (host)

The benchmark host, CPU only, `nice -n 19`, patched sherpa (0001 + 0002) as the app
runs it (greedy, `tail_paddings` 1000, language set). Load average 5–11 from
other jobs, so absolute times are a little noisy. Medians of 3 runs:

| Input | Audio decoded | Steps | 4 threads | 2 threads |
|---|---|---|---|---|
| FLEURS ja `3321843553379073107` through the app's session; loops on 昆虫学者は… | 6.9 s | 224 | 8.1 s | 11.5 s |
| AMI "xlsx" clip repeated 8× | 27.0 s | 224 | 10.3 s | 14.1 s |
| the ja clip repeated 4× (sherpa keeps 29.5 s) | 29.5 s | 224 | 10.0 s | 14.5 s |

Split with ONNX Runtime driving the same int8 files: the encoder takes
0.8 s (5 s of audio) to 1.7 s (27 s; sherpa feeds the audio plus 1000
padding frames, capped at 3000, so it grows up to ~20 s of audio), and a
decoder step 34–39 ms, growing about 1.4× from the first steps to the last.
Audio length barely changes a step's cost.

**Pixel 8a estimate.** On the same 11 LibriSpeech clips, the Pixel's turbo
decodes (`2026-10-pixel8a-ort/`, `2026-10-pixel8a-mem/`, 4 threads) took a
median 3.8× the host's (3.2–5.6×). A 224-step runaway is then about 31 s on
a 7 s segment and 38 s at 28 s. A linear fit to the Pixel data alone
(1.74 s + 0.257 s per audio second + 129 ms per token) agrees. These
come from the benchmark service, which runs without the Cortex-X3 core or
the foreground boost (probe 240 ms vs 157 in the app), so in the app it may
be nearer 20–26 s. Not measured on the phone: it was reachable only through
a slow network relay.

## A step budget instead?

Decoder tokens per second of audio, Whisper turbo's own Finals through the
app's session (FLEURS; Hindi and most Japanese counted by re-encoding the
text with the model's byte-level BPE, which matched exact counts within 1–2
tokens):

| Set | Finals | Max | p99 | p95 | Median |
|---|---|---|---|---|---|
| FLEURS hi | 102 | 17.2 | 16.8 | 16.1 | 12.4 |
| FLEURS ja (1 runaway left out) | 232 | 8.1 | 7.4 | 6.9 | 5.5 |
| FLEURS en | 202 | 4.8 | 4.7 | 4.3 | 3.3 |

Finals a budget of ceil(k·s) + c would cut short: k = 6 (the old cap) cuts
all Hindi and, with c = 0, 71 Japanese ones; k = 15 still cuts 12 Hindi
(c = 0) or 5 (c = 10); k ≥ 20 cuts none. So one k for every language must
be about 20, which reaches 224 steps at 10.7 s of audio: it only shortens
runaways on short segments (the 6.9 s ja runaway would stop at 148 steps
instead of 224). Per-language budgets would do more (ja k = 10, c = 10 cuts
no Final here and would stop that runaway at 79 steps; en k = 6, c = 10 cuts
none), but a budget never frees the engine on Cancel, which is the bug.

Hindi also hits the 224-step limit on its own: one 16.6 s Final stopped at
exactly 224 tokens (its reference needs 267). At 12–17 tokens a second, a
Hindi utterance longer than about 13–18 s is cut by Whisper's limit, budget
or not.

#54 adopted the English and Japanese budgets, and gave Hindi one above 224
(patch 0005): `2026-10-whisper-token-budget.md`.

## Cancelling

Patch 0003 (`third_party/patches/sherpa-onnx/`) adds
`OfflineStream::Cancel()`, checked before every decoder step by Whisper's
greedy search, Canary's loop and Qwen3-ASR's generation loop.
`StreamingSession` calls it (through `OfflineTranscriber.cancel`) when its
collector goes away, and starts no decode after that. A host program linked
against the patched build decoded the 29.5 s ja clip and cancelled it from a
second thread (4 threads):

| Cancel at | Decode returned | Tokens |
|---|---|---|
| never | 11.5 s | 224 |
| 2.5 s | 2.5 s (15 ms after the cancel) | 20 |
| 4.0 s | 4.0 s (31 ms after) | 56 |
| 0.3 s, during the encoder | 2.1 s (1.8 s after) | 0 |

The encoder run can't be stopped this way: a cancel during it waits for the
encoder (1.8 s here, so several seconds on the Pixel for a long utterance).
Stopping that needs ONNX Runtime's `RunOptions::SetTerminate` passed into
each model's encoder call.

**Encoder (#54, patch 0004).** `OfflineStream` now keeps the `RunOptions` of
the run in progress and `Cancel()` sets their terminate flag, so ONNX Runtime
stops the run between two graph nodes. It covers Whisper's and Canary's
encoders and Qwen3-ASR's conv frontend, encoder and prefill. The same
program and clip (the 29.5 s ja clip; patches 0001–0004, 4 threads,
`nice -n 19`, an otherwise idle benchmark host), cancelling at a fixed time after
the decode started:

| Model | Uncancelled decode | Cancel at (ms) | Returned after the cancel (ms) |
|---|---|---|---|
| Whisper turbo int8 | 7.7 s, 224 tokens (the runaway) | 0, 50, 150, 200, 400, 800, 1200, 1500 (all in the encoder: 0 tokens) | 2, 7, 1, 1, 1, 7, 0, 8 |
| Canary 180M Flash int8 | 0.34 s, 8 tokens | 0, 50, 150, 200 (in the encoder) | 3, 2, 0, 0 |
| Qwen3-ASR 0.6B int8 | 2.9 s, 77 tokens | 0, 50, 150, 200, 400 (conv frontend / encoder / prefill: 0 tokens); 800, 1200, 1500 (decoder steps) | 2, 72, 0, 0, 1; 5, 2, 24 |

Cancels during Whisper's decoder steps still stop at the next step (2.5 s:
5 ms after, 26 tokens; 4.0 s: 5 ms, 72 tokens). Every cancel returned within
72 ms, against 1.8 s before. The longest wait
is one graph node (Qwen3's conv frontend at 50 ms). On the Pixel a node takes
roughly 4× the host's, so a few hundred ms at worst; not measured there.

Scripts and raw outputs, on the benchmark host: `$WORK/scratch-49/`
(measurements: `runaway.py`, `steps.py`, `density.py`, `*-summary*`) and
`scratch-49b/` (`cancel_test.cc`, `mkwav.py` for the clip; the patched C++
build it linked against was deleted afterwards). #54's encoder test:
`scratch-54/` (`cancel_test.cc` for all three models, `build.sh`, `cancel*.log`).

Not re-measured: turbo's `SpeedBudget` cost with the cap gone (#49 item 3)
needs the Pixel. Normal decodes end at their end-of-text token well before
any cap, so only output that the 6/s cap used to truncate (Hindi, Japanese)
costs more steps now.
