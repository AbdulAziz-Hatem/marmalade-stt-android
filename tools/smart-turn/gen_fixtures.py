"""Golden values for the app's Kotlin smart-turn port (#43), from marmalade-stt-cli.

    $MARMALADE_STT_CLI/.venv/bin/python tools/smart-turn/gen_fixtures.py \\
        --cli $MARMALADE_STT_CLI \\
        --model app/src/main/assets/turn/smart-turn-v3.2-cpu.onnx

The reference is the CLI's own code, imported, not copied: `SmartTurnDetector._window`
(the last 8 s, zero-padded at the start) and `whisper_features.log_mel`, a numpy port
of pipecat's `_whisper_features.py`. Needs numpy, plus onnxruntime for `--model`.

The inputs are synthetic and fully determined by (samples, seed): harmonic bursts
with a falling pitch plus xorshift32 noise, the same formula as `TurnFixtures.signal`
in the Kotlin tests, which rebuilds them rather than reading audio from disk. A
fourth case appends a second of exact zeros, the usual shape at a turn's end.

Writes to app/src/test/resources/smart-turn/ (little-endian float32):
- `index.tsv`: name, voiced samples, trailing zero samples, seed, and the model's
  probability for the case (`-` without `--model`).
- `frames.txt`: the frame indices kept in full (a whole 80 x 800 log-mel is 256 KB).
- `<name>.frames.f32`: those frames, [frames][80].
- `<name>.frame-means.f32`: every frame's mean over the 80 mel bins, [800].
- `<name>.mel-means.f32`: every mel bin's mean over the 800 frames, [80].

The probabilities come from an int8 graph whose kernels differ by CPU (see the
Granite notes), so `OnnxTurnDetectorTest` compares them loosely; regenerate on the
machine that runs the test if a tolerance ever bites.
"""

import argparse
import os
import sys
from pathlib import Path

import numpy as np

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / "app/src/test/resources/smart-turn"

SR = 16_000

# (name, voiced samples, trailing zeros, seed): exactly 8 s, a short clip the
# window pads at the start, a long one it cuts from the front, and speech then silence.
CASES = [
    ("full-8s", 128_000, 0, 1),
    ("short-1.5s", 24_000, 0, 2),
    ("long-10s", 160_000, 0, 3),
    ("silence-tail", 112_000, 16_000, 4),
]

# The edges, every tenth frame, and the pad/signal boundary of short-1.5s (frame 650).
FRAMES = sorted({0, 1, 2, 797, 798, 799, *range(0, 800, 10), *range(646, 655)})


def noise(n: int, seed: int) -> np.ndarray:
    """xorshift32 (13, 17, 5) mapped to [-0.5, 0.5); exact in float32, easy to mirror in Kotlin."""
    state = seed & 0xFFFFFFFF
    out = np.empty(n, dtype=np.float64)
    for i in range(n):
        state ^= (state << 13) & 0xFFFFFFFF
        state ^= state >> 17
        state ^= (state << 5) & 0xFFFFFFFF
        out[i] = (state >> 8) / 16_777_216.0 - 0.5
    return out


def signal(n: int, seed: int) -> np.ndarray:
    """Voiced-ish bursts: 4 harmonics of a 140 Hz pitch falling 20 Hz, gated at 5 Hz, plus noise."""
    t = np.arange(n, dtype=np.float64) / SR
    glide = -20.0 / (n / SR)
    phase = 140.0 * t + 0.5 * glide * t * t
    voiced = (0.5 * np.sin(2 * np.pi * phase) + 0.3 * np.sin(2 * np.pi * 2 * phase)
              + 0.2 * np.sin(2 * np.pi * 3 * phase) + 0.1 * np.sin(2 * np.pi * 5 * phase))
    gate = np.maximum(0.0, np.sin(2 * np.pi * 2.5 * t))
    return (0.4 * gate * voiced + 0.02 * noise(n, seed)).astype(np.float32)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    cli_env = os.environ.get("MARMALADE_STT_CLI")
    ap.add_argument("--cli", type=Path, default=Path(cli_env) if cli_env else None, required=not cli_env,
                    help="marmalade-stt-cli checkout (the reference implementation); default $MARMALADE_STT_CLI")
    ap.add_argument("--model", type=Path, help="smart-turn-v3.2-cpu.onnx; omit to skip the probabilities")
    ap.add_argument("--out", type=Path, default=OUT)
    args = ap.parse_args()

    sys.path.insert(0, str(args.cli.expanduser().resolve()))
    from marmalade_stt.turn import SmartTurnDetector
    from marmalade_stt.whisper_features import log_mel

    detector = None
    if args.model:
        import onnxruntime as ort
        session = ort.InferenceSession(str(args.model.expanduser()), providers=["CPUExecutionProvider"])
        detector = SmartTurnDetector(session)

    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "frames.txt").write_text(" ".join(map(str, FRAMES)) + "\n")
    rows = []
    for name, voiced, zeros, seed in CASES:
        audio = np.concatenate([signal(voiced, seed), np.zeros(zeros, dtype=np.float32)])
        mel = log_mel(SmartTurnDetector._window(audio))
        assert mel.shape == (80, 800) and mel.dtype == np.float32
        le = np.dtype("<f4")
        mel.T[FRAMES].astype(le).tofile(args.out / f"{name}.frames.f32")
        mel.astype(np.float64).mean(axis=0).astype(le).tofile(args.out / f"{name}.frame-means.f32")
        mel.astype(np.float64).mean(axis=1).astype(le).tofile(args.out / f"{name}.mel-means.f32")
        prob = "-" if detector is None else f"{detector.complete_prob(audio):.6f}"
        rows.append(f"{name}\t{voiced}\t{zeros}\t{seed}\t{prob}")
        print(name, prob)
    (args.out / "index.tsv").write_text("\n".join(rows) + "\n")


if __name__ == "__main__":
    main()
