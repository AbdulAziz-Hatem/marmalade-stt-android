"""Decode time vs utterance length, for variants of one offline engine (#46, #53).

Cuts real speech to fixed lengths (consecutive clips of a set joined, then cut; RMS to −25 dBFS) and
times sherpa-onnx's two steps per decode: features (`accept_waveform`, the log-mel front-end) and the
model (`decode_stream`). The variants are Whisper tail paddings (`--whisper-tail-paddings 1000,300`):
the encoder runs on the speech plus that many zero frames. Variants take turns piece by piece, so
load from other jobs lands on all of them.

A sherpa-onnx build is a whole venv, so comparing builds (#53's FFT patch) means running this once
per venv, alternating the runs; `--tag` labels the output rows.

    cd $WORK/harness-<n>
    ../venv/bin/python -m asrbench.decode_speed --work .. --engine whisper-turbo --threads 4 \\
        --lengths 2,5,10,20 --whisper-tail-paddings 1000,300,100 --out ../results-46/speed.jsonl

`--variant LABEL[=key:value,...]` (repeatable, #55) replaces the tail variants with named ones: `dir:`
the engine's files from another directory (a re-quantised model), `tail:` Whisper tail paddings,
`arena:off` (OrtTuning -noarena), `threads:`, and `mnt:` a fixed token limit per decode (`mnt:1` = the
encoder and one decoder step: subtract it from the full decode for the decoder's share).

    ../venv-54/bin/python -m asrbench.decode_speed --work .. --engine whisper-turbo --threads 4 \\
        --variant int8 --variant 'enc1=mnt:1' --variant 'q4=dir:../models-55/q4-b32'
"""
import argparse
import json
import statistics
import time
from pathlib import Path

import numpy as np

from . import engines as E
from .backends import SAMPLE_RATE, WHISPER_TAIL_PADDINGS, OfflineTranscriber, no_arena_provider
from .run import load_audio, read_jsonl


def pieces(work: Path, set_name: str, seconds: float, n: int) -> list[np.ndarray]:
    """n pieces of `seconds` each, cut from the set's clips joined in manifest order."""
    need = int(seconds * SAMPLE_RATE)
    out, buf = [], []
    for utt in read_jsonl(work / "data" / set_name / "manifest.jsonl"):
        buf.append(load_audio(utt["audio"]))
        joined = np.concatenate(buf)
        if len(joined) >= need:
            x = joined[:need].astype(np.float32)
            x *= 10 ** (-25 / 20) / max(float(np.sqrt(np.mean(x ** 2))), 1e-6)
            out.append(x)
            buf = []
            if len(out) == n:
                return out
    raise SystemExit(f"{set_name}: not enough audio for {n} pieces of {seconds} s")


def timed(tr: OfflineTranscriber, x: np.ndarray) -> tuple[float, float, str]:
    """(features s, model s, text) for one decode, as OfflineTranscriber.transcribe does it."""
    t0 = time.perf_counter()
    s = tr.recognizer.create_stream()
    for k, v in tr.options_for(x).items():
        s.set_option(k, v)
    s.accept_waveform(SAMPLE_RATE, np.concatenate([x, tr.tail]) if len(tr.tail) else x)
    t1 = time.perf_counter()
    tr.recognizer.decode_stream(s)
    t2 = time.perf_counter()
    return t1 - t0, t2 - t1, s.result.text


def parse_variant(spec: str) -> tuple[str, dict[str, str]]:
    """`LABEL[=key:value,...]` -> (label, {key: value})."""
    label, _, rest = spec.partition("=")
    opts = dict(kv.split(":", 1) for kv in rest.split(",") if kv)
    unknown = set(opts) - {"dir", "tail", "arena", "threads", "mnt"}
    if unknown or opts.get("arena", "on") not in ("on", "off"):
        raise SystemExit(f"bad --variant {spec!r}")
    return label, opts


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--engine", required=True, choices=list(E.BY_ID))
    ap.add_argument("--set", default="fleurs-en", help="where the speech comes from; its language is the hint")
    ap.add_argument("--threads", type=int, default=E.NUM_THREADS)
    ap.add_argument("--lengths", default="2,5,10,20", help="seconds, comma-separated")
    ap.add_argument("--pieces", type=int, default=5, help="pieces per length")
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--whisper-tail-paddings", default=str(WHISPER_TAIL_PADDINGS),
                    help="comma-separated variants (Whisper only)")
    ap.add_argument("--variant", action="append", default=[],
                    help="LABEL[=dir:PATH,tail:N,arena:off,threads:N,mnt:N]; replaces the tail variants")
    ap.add_argument("--tag", default="", help="label for this build/venv in the output")
    ap.add_argument("--out", type=Path, help="append one JSON row per decode")
    args = ap.parse_args()

    engine = E.BY_ID[args.engine]
    lang = read_jsonl(args.work / "data" / args.set / "manifest.jsonl")[0]["lang"]
    tails = [int(t) for t in args.whisper_tail_paddings.split(",")]
    if engine.family != E.WHISPER and tails != [WHISPER_TAIL_PADDINGS]:
        raise SystemExit("--whisper-tail-paddings is for Whisper engines")
    model_dir = args.work / "models" / engine.id
    if args.variant:
        trs = {}
        for label, o in map(parse_variant, args.variant):
            trs[label] = OfflineTranscriber(
                engine, Path(o["dir"]) if "dir" in o else model_dir, lang,
                num_threads=int(o.get("threads", args.threads)),
                whisper_tail_paddings=int(o.get("tail", WHISPER_TAIL_PADDINGS)),
                provider=no_arena_provider(args.work) if o.get("arena") == "off" else "cpu",
                max_new_tokens=int(o["mnt"]) if "mnt" in o else None)
        tails = list(trs)
    else:
        trs = {t: OfflineTranscriber(engine, model_dir, lang, num_threads=args.threads, whisper_tail_paddings=t)
               for t in tails}
    lengths = [float(s) for s in args.lengths.split(",")]
    audio = {L: pieces(args.work, args.set, L, args.pieces) for L in lengths}

    for tr in trs.values():  # warm-up: first-run allocations
        timed(tr, audio[lengths[0]][0])
    times: dict[tuple[int, float], list[tuple[float, float]]] = {}
    out = args.out.open("a") if args.out else None
    for rep in range(args.reps):
        for L in lengths:
            for i, x in enumerate(audio[L]):
                order = tails[rep % len(tails):] + tails[:rep % len(tails)]
                for t in order:
                    feat, model, text = timed(trs[t], x)
                    times.setdefault((t, L), []).append((feat, model))
                    if out:
                        out.write(json.dumps({"tag": args.tag, "engine": engine.id, "threads": args.threads,
                                              "variant" if args.variant else "tail": t, "seconds": L, "piece": i, "rep": rep,
                                              "features_s": feat, "model_s": model, "text": text},
                                             ensure_ascii=False) + "\n")
                        out.flush()

    base = tails[0]
    print(f"{engine.id}, {args.threads} threads{', ' + args.tag if args.tag else ''}: median ms per decode "
          f"(features + model), {args.pieces} pieces x {args.reps} reps")
    print(f"| {'variant' if args.variant else 'tail'} | " + " | ".join(f"{L:g} s" for L in lengths) + " |")
    print("|---|" + "---|" * len(lengths))
    for t in tails:
        cells = []
        for L in lengths:
            f = statistics.median(a for a, _ in times[(t, L)]) * 1000
            m = statistics.median(b for _, b in times[(t, L)]) * 1000
            cell = f"{f:.0f} + {m:.0f}"
            if t != base:
                b = statistics.median(x + y for x, y in times[(base, L)])
                cell += f" ({statistics.median(x + y for x, y in times[(t, L)]) / b:.2f}x)"
            cells.append(cell)
        print(f"| {t} | " + " | ".join(cells) + " |")


if __name__ == "__main__":
    main()
