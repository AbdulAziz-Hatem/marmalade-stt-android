"""Golden vectors for the app's Kotlin Granite port (#32), from `asrbench.granite`.

    python granite/dump_golden.py frontend ../../app/src/test/resources/granite
    python granite/dump_golden.py parity --work $WORK --out $WORK/granite-parity
    python granite/dump_golden.py expect --dir $WORK/granite-parity

`frontend` writes deterministic synthetic signals and their features: for each
case `<name>.samples.f32` (16 kHz mono) and `<name>.features.f32` ([T, 320],
row-major), little-endian float32, plus `index.tsv` (name, samples, frames).
`GraniteFrontendTest` compares the Kotlin front-end with them. Small enough to
commit; no audio from any dataset.

`parity` writes a few real clips (levelled by `session.agc`, whole clips, no
VAD) as `clips/<id>.f32` and the harness's features for them as
`clips/<id>.features.f32`. `expect` then decodes those clips with the harness's
`GraniteTranscriber` into `expected.tsv` (id, text). Run `expect` on the machine
that runs the Kotlin test: the int8 graph's kernels differ by CPU (AVX2 vs
AVX-512 VNNI), and in #32 two of twelve clips differed by a near-tied word
between the benchmark host (AVX-512 VNNI) and the dev machine (AVX2), in Python alone. `GraniteParityTest` runs the
Kotlin transcriber on the same clips when GRANITE_PARITY_DIR points at the
directory, with the catalog's `model_int8.onnx` and `tokenizer.json` and the
harness's `tokens.txt` copied in. Not committed.

`formatter` (#33) writes the Python formatter's output for each line of
`formatter-inputs.txt` (Granite text) to `formatter-expected.tsv`, and the BPE
ids of every word to `bpe.tsv`, with `formatter.int8.onnx` in the directory;
also CPU-dependent, so run it where the Kotlin test runs.
Run from $WORK/harness-<n> (the manifests' audio paths are relative).
"""

import argparse
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from asrbench import granite as G  # noqa: E402

SR = G.SAMPLE_RATE


def synthetic(n: int, seed: int) -> np.ndarray:
    """Speech-like test signal: gated harmonic bursts with vibrato, noise, and a silent stretch."""
    rng = np.random.default_rng(seed)
    t = np.arange(n) / SR
    f0 = 140.0 + 30.0 * np.sin(2 * np.pi * 3.0 * t)
    phase = 2 * np.pi * np.cumsum(f0) / SR
    x = sum(np.sin(k * phase) / k for k in range(1, 8))
    gate = (np.sin(2 * np.pi * 2.5 * t) > -0.3).astype(np.float64)
    x = 0.3 * x * gate + 0.01 * rng.standard_normal(n)
    # A stretch of exact zeros exercises the log floor.
    x[n // 3: n // 3 + n // 8] = 0.0
    return x.astype(np.float32)


# (name, samples): the minimum the transcriber accepts, a whole second, an odd
# mel-frame count (the stacking pads one frame), and an odd count that ends
# exactly on a hop (the signal itself is zero-padded first).
CASES = [("min", G.MIN_SAMPLES), ("one-second", SR), ("odd-frames", 16_000 + 160 * 7 + 37), ("pad-tail", 160 * 101)]


def dump_frontend(out: Path) -> None:
    out.mkdir(parents=True, exist_ok=True)
    rows = []
    for i, (name, n) in enumerate(CASES):
        x = synthetic(n, seed=32 + i)
        f = G.features(x)
        x.astype("<f4").tofile(out / f"{name}.samples.f32")
        f.astype("<f4").tofile(out / f"{name}.features.f32")
        rows.append(f"{name}\t{n}\t{f.shape[0]}\n")
    (out / "index.tsv").write_text("".join(rows))


def dump_parity(work: Path, out: Path, sets: list[str], n: int) -> None:
    from asrbench.run import load_audio, read_jsonl
    from asrbench.session import agc

    (out / "clips").mkdir(parents=True, exist_ok=True)
    for s in sets:
        for u in read_jsonl(work / "data" / s / "manifest.jsonl")[:n]:
            x = agc(load_audio(u["audio"])).astype(np.float32)
            x.astype("<f4").tofile(out / "clips" / f"{u['id']}.f32")
            G.features(x).astype("<f4").tofile(out / "clips" / f"{u['id']}.features.f32")


def dump_expected(d: Path) -> None:
    """The harness's text for each clip in d/clips, with d's model_int8.onnx and tokens.txt."""
    rec = G.GraniteTranscriber(G.GraniteEngine("parity", "model_int8.onnx"), d, num_threads=2)
    rows = [f"{c.name[:-4]}\t{rec.transcribe(np.fromfile(c, '<f4'))}\n"
            for c in sorted((d / "clips").glob("*.f32")) if not c.name.endswith(".features.f32")]
    (d / "expected.tsv").write_text("".join(rows), encoding="utf-8")


def dump_formatter(d: Path) -> None:
    """Python's formatter output for each line of d/formatter-inputs.txt (Granite text), and
    the Granite BPE ids of every word in it, with d/formatter.int8.onnx and d/tokenizer.json."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from format_spike import Ours

    fmt = Ours(d, threads=2)
    lines = [line for line in (d / "formatter-inputs.txt").read_text(encoding="utf-8").splitlines() if line.strip()]
    (d / "formatter-expected.tsv").write_text("".join(f"{t}\t{fmt(t)}\n" for t in lines), encoding="utf-8")
    words = sorted({w for t in lines for w in t.split()})
    (d / "bpe.tsv").write_text("".join(
        f"{w}\t{' '.join(map(str, fmt.tok.encode(' ' + w, add_special_tokens=False).ids))}\n" for w in words),
        encoding="utf-8")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    f = sub.add_parser("frontend")
    f.add_argument("out", type=Path)
    p = sub.add_parser("parity")
    p.add_argument("--work", type=Path, required=True)
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--sets", nargs="+", default=["ls-other", "ami-ihm", "dictation"])
    p.add_argument("--n", type=int, default=4, help="clips per set")
    e = sub.add_parser("expect")
    e.add_argument("--dir", type=Path, required=True)
    fm = sub.add_parser("formatter")
    fm.add_argument("--dir", type=Path, required=True)
    args = ap.parse_args()
    if args.cmd == "frontend":
        dump_frontend(args.out)
    elif args.cmd == "formatter":
        dump_formatter(args.dir)
    elif args.cmd == "parity":
        dump_parity(args.work, args.out, args.sets, args.n)
    else:
        dump_expected(args.dir)


if __name__ == "__main__":
    main()
