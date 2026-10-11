"""End of speech -> Final latency of the offline loop with live partials, on virtual time.

    python -m asrbench.latency --work $WORK --set ls-clean                       # Pixel cost model, no model needed
    python -m asrbench.latency --work $WORK --set ls-clean --engine distil-small-en --threads 2   # host decode times
    python -m asrbench.latency --work $WORK --set fleurs-zh --lang zh --engine nemotron-3-5-streaming-560ms  # #29

Runs `session.offline_session(partials=True)` on clips grouped by length, with
a `SimClock`: hops arrive in real time and each decode takes the time a cost
model says, so the backlog a long partial builds up, and a Final queued behind
a partial, show up as they would on the device. The VAD is the real Silero.

Cost models:
- `pixel-distil-2t` (default): distil-small-en at 2 threads on the Pixel 8a,
  fitted to the #11 device pass (`SttTiming` logs): decodes of about 1, 3.7,
  9.7 and 22.4 s took about 0.86, 1.16, 2.2 and 6.1 s. The fixed ~0.8 s is
  Whisper's encoder, which always runs on a 30 s window.
- `--engine`: the host's wall time for each decode.

`--scale` multiplies either, e.g. to project another thread count from the
host's RTF ratio (#20: distil-small-en 2 -> 4 threads was 1.26x faster), or a
slower phone: `--scale 3` is a device three times slower than the Pixel 8a.

Lag (#30): how far the session fell behind the mic (its LagMonitor), per clip
the worst; "too slow" counts clips where it kept falling behind for 10 s.
`--no-catch-up` runs the app before #30, whose partials never waited for the
session to catch up.

`--streaming` runs `online_session` (Nemotron's loop) instead, with an
illustrative cost model: each decode call costs `--overhead` seconds plus
`--rtf` times the audio it decodes (times `--scale`). No phone measurement of
Nemotron's per-call cost exists yet (the #13 emulator ran Nemotron 3.5 320 ms
at RTF 1.9), so read these for the shape of lag growth, not phone numbers.

`--engine` with a streaming engine (#29) runs `online_session` with the real
model, each decode costing its host wall time. `--lang` is the language hint
`--engine` gets.

Latency is from the end of speech (the last 20 ms frame within 35 dB of the
clip's loudest) to the virtual time the utterance's Final decode finished.
On low-SNR clips that padded speech-end estimate is late; FLEURS is one
(#29). The first-partial column is from the VAD's onset to the first
partial. The first-text column (#29) is from the onset of the utterance that
first shows a word (a letter or digit, not a stray ".") to that partial or
Final: first visible words, comparable across offline and streaming engines.
"""

import argparse
import statistics
from pathlib import Path

import numpy as np

from . import engines as E
from .backends import SAMPLE_RATE, OfflineTranscriber, StreamingRecognizer, silero_vad
from .run import load_audio, read_jsonl
from .session import SimClock, agc, offline_session, online_session

LEAD_SILENCE = 0.5
TAIL_SILENCE = 3.0  # long enough for the VAD to close the segment before stop()


def pixel_distil_2t(audio_s: float, _wall_s: float) -> float:
    return 0.775 + 0.0775 * audio_s + 0.00715 * audio_s ** 2


class _NoModel:
    """Stands in for the transcriber when only the cost model matters."""

    def transcribe(self, _samples) -> str:
        return "x"


class _NoStreamingModel:
    """Stands in for a streaming recognizer when only the cost model matters."""

    class _Stream:
        def input_finished(self) -> None:
            pass

    def create_stream(self):
        return self._Stream()

    def accept(self, _stream, _samples) -> None:
        pass

    def decode(self, _stream) -> None:
        pass

    def text(self, _stream) -> str:
        return "x"


def speech_end(x: np.ndarray) -> float:
    frame = SAMPLE_RATE // 50
    n = len(x) // frame
    rms = np.sqrt(np.mean(np.square(x[:n * frame].reshape(n, frame), dtype=np.float64), axis=1)) + 1e-12
    db = 20 * np.log10(rms)
    loud = np.nonzero(db > db.max() - 35)[0]
    return (loud[-1] + 1) * frame / SAMPLE_RATE


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--set", default="ls-clean")
    ap.add_argument("--buckets", default="5,10,22", help="utterance lengths in seconds (each +-20 %%)")
    ap.add_argument("--per-bucket", type=int, default=12)
    ap.add_argument("--engine", choices=list(E.BY_ID), help="decode for real and use host wall time")
    ap.add_argument("--lang", default="en", help="the language hint --engine gets")
    ap.add_argument("--threads", type=int, default=E.NUM_THREADS)
    ap.add_argument("--scale", type=float, default=1.0, help="multiplies every decode's cost")
    ap.add_argument("--vad", type=Path)
    ap.add_argument("--no-catch-up", action="store_true", help="partials don't wait while behind (the app before #30)")
    ap.add_argument("--streaming", action="store_true", help="the streaming loop (online_session), cost model below")
    ap.add_argument("--rtf", type=float, default=0.8, help="--streaming: decode seconds per second of audio")
    ap.add_argument("--overhead", type=float, default=0.02, help="--streaming: seconds per decode call")
    ap.add_argument("--tail-padding", type=float,
                    help="--engine, streaming: seconds of zeros fed before each input_finished "
                         "(default: the app's, the chunk + 0.2 s)")
    args = ap.parse_args()

    if args.streaming:
        transcriber = _NoStreamingModel()
        cost = lambda audio_s, _wall_s: (args.overhead + args.rtf * audio_s) * args.scale  # noqa: E731
        label = f"streaming loop, {args.overhead:g} s + RTF {args.rtf:g} per decode x{args.scale:g}"
    elif args.engine:
        engine = E.BY_ID[args.engine]
        if engine.streaming:
            transcriber = StreamingRecognizer(engine, args.work / "models" / engine.id, args.lang,
                                              num_threads=args.threads,
                                              tail_padding=None if args.tail_padding is None
                                              else int(args.tail_padding * SAMPLE_RATE))
        else:
            transcriber = OfflineTranscriber(engine, args.work / "models" / engine.id, args.lang,
                                             num_threads=args.threads)
        args.streaming = engine.streaming
        cost = lambda _audio_s, wall_s: wall_s * args.scale  # noqa: E731
        label = f"{engine.id}, {args.threads} threads, host wall time x{args.scale:g}"
        if args.tail_padding:
            label += f", {args.tail_padding:g} s tail padding"
    else:
        transcriber = _NoModel()
        cost = lambda audio_s, wall_s: pixel_distil_2t(audio_s, wall_s) * args.scale  # noqa: E731
        label = f"pixel-distil-2t cost model x{args.scale:g}"
    vad = silero_vad(args.vad or args.work / "silero_vad.onnx")

    manifest = read_jsonl(args.work / "data" / args.set / "manifest.jsonl")
    clips = []
    for utt in manifest:
        x = agc(load_audio(utt["audio"]))
        clips.append((utt["id"], x))

    if args.no_catch_up:
        label += ", no catch-up (before #30)"
    print(f"# {args.set}, {label}\n")
    print("| length | n | utterance s (median) | end of speech -> Final s: median | p90 | max "
          "| first partial s (median) | partials (median) | max lag s: median | max | too slow | first text s (median) |")
    print("|---|---|---|---|---|---|---|---|---|---|---|---|")
    for target in (float(b) for b in args.buckets.split(",")):
        picked = [(i, x) for i, x in clips if 0.8 * target <= len(x) / SAMPLE_RATE <= 1.2 * target][:args.per_bucket]
        lags, lengths, firsts, counts, behind, too_slow, texts = [], [], [], [], [], 0, []
        for _, x in picked:
            clock = SimClock(cost)
            if args.streaming:
                res = online_session(x, transcriber, vad, TAIL_SILENCE, LEAD_SILENCE, clock=clock,
                                     catch_up=not args.no_catch_up)
            else:
                res = offline_session(x, transcriber, vad, TAIL_SILENCE, LEAD_SILENCE, partials=True, clock=clock,
                                      catch_up=not args.no_catch_up)
            end = LEAD_SILENCE + speech_end(x)
            finals = [(t, n) for kind, t, n in res.events if kind == "final"]
            onsets = [t for kind, t, _ in res.events if kind == "onset"]
            partials = [t for kind, t, _ in res.events if kind == "partial"]
            if not finals:
                continue
            t, n = finals[-1]
            lags.append(t - end)
            # The streaming Final decodes only what is left, so take the clip's length there.
            lengths.append(len(x) / SAMPLE_RATE if args.streaming else n / SAMPLE_RATE)
            counts.append(len(partials))
            behind.append(res.max_lag_s)
            too_slow += res.too_slow
            if onsets and partials:
                firsts.append(partials[0] - onsets[0])
            shown = [t for kind, t, _ in res.events if kind == "text"]
            if shown:
                # From the onset of the utterance that showed it: a first VAD segment on
                # breath or noise that never shows words doesn't count against the engine.
                texts.append(shown[0] - max(t for t in onsets if t <= shown[0]))
        if not lags:
            print(f"| {target:g} s | 0 | | | | | | | | | | |")
            continue
        lags.sort()
        p90 = lags[min(len(lags) - 1, int(0.9 * len(lags)))]
        print(f"| {target:g} s | {len(lags)} | {statistics.median(lengths):.1f} | {statistics.median(lags):.1f} "
              f"| {p90:.1f} | {max(lags):.1f} | {statistics.median(firsts) if firsts else float('nan'):.2f} "
              f"| {statistics.median(counts):g} | {statistics.median(behind):.1f} | {max(behind):.1f} | {too_slow} "
              f"| {statistics.median(texts) if texts else float('nan'):.2f} |")


if __name__ == "__main__":
    main()
