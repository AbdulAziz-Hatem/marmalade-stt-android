"""Generate the two synthetic sets: dictation (TTS) and noise (no speech).

    python -m asrbench.make_synthetic --work $WORK dictation   # needs marmalade-tts (Kitten)
    python -m asrbench.make_synthetic --work $WORK noise

dictation: the 40 sentences in dictation.tsv. Kitten TTS (marmalade-tts
  kitten, nano 0.8, Apache-2.0) reads the `spoken` column (numbers, symbols
  and addresses spelled out, preprocessing off, so the TTS can't misread
  them); the reference is the `written` column, which is what a user wants
  typed. Voices rotate over two female and two male Kitten voices. Synthetic
  speech: a formatting check, not an accuracy truth.
noise: six 10 s clips with no speech (digital silence, white and pink noise at
  three levels, keyboard-like clicks, mains hum with harmonics), generated
  from a fixed seed. Any text an engine returns for them is a hallucination.
noise-short: 21 clips of 1, 2 and 4 s without speech (#57), the length of a
  VAD false trigger plus the app's 0.6 s pre-roll: the mic floor, room tone,
  a breath, a tap, mains hum, clicks and digital silence. Meant for --direct
  runs with --hint per language, to collect the stock phrases each engine
  writes for noise.
"""

import argparse
import csv
import json
import subprocess
from pathlib import Path

import numpy as np

SR = 16_000
VOICES = ["Bella", "Jasper", "Luna", "Bruno"]
HERE = Path(__file__).resolve().parent.parent


def write_wav(path: Path, x: np.ndarray) -> None:
    import wave
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def dictation(work: Path, tts: str) -> None:
    out = work.resolve() / "data" / "dictation"  # absolute: the TTS daemon has its own cwd
    audio = out / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    rows = []
    with (HERE / "dictation.tsv").open(newline="") as f:
        for i, r in enumerate(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE)):
            wav = audio / f"{r['id']}.wav"
            if not wav.exists():
                subprocess.run([tts, "kitten", "--voice", VOICES[i % len(VOICES)], "--no-preprocessing",
                                "--no-effects", "--quiet", "--out", str(wav), "--text", r["spoken"]], check=True)
            rows.append({"id": r["id"], "audio": str(wav), "text": r["written"], "lang": "en"})
    (out / "manifest.jsonl").write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows))
    print(f"dictation: {len(rows)} utterances", flush=True)


def _pink(rng, n):
    # Voss-McCartney-ish: filter white noise with 1/f spectral shaping.
    spec = np.fft.rfft(rng.standard_normal(n))
    f = np.arange(len(spec))
    f[0] = 1
    x = np.fft.irfft(spec / np.sqrt(f), n)
    return x / np.sqrt(np.mean(x ** 2))


def _at_dbfs(x, dbfs):
    return x / np.sqrt(np.mean(x ** 2)) * 10 ** (dbfs / 20)


def noise(work: Path) -> None:
    out = work / "data" / "noise"
    audio = out / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(14)
    n = 10 * SR
    t = np.arange(n) / SR
    clicks = np.zeros(n)
    for start in rng.integers(0, n - 400, 25):  # 25 short decaying bursts, like typing
        clicks[start:start + 400] += rng.standard_normal(400) * np.exp(-np.arange(400) / 60)
    hum = sum(np.sin(2 * np.pi * 50 * k * t) / k for k in (1, 2, 3, 4, 5))
    clips = {
        "silence": np.zeros(n),
        "white-60dbfs": _at_dbfs(rng.standard_normal(n), -60),
        "pink-45dbfs": _at_dbfs(_pink(rng, n), -45),
        "pink-30dbfs": _at_dbfs(_pink(rng, n), -30),
        "clicks-20dbfs": _at_dbfs(clicks, -20),
        "hum-30dbfs": _at_dbfs(hum, -30),
    }
    rows = []
    for name, x in clips.items():
        wav = audio / f"{name}.wav"
        write_wav(wav, x)
        rows.append({"id": name, "audio": str(wav), "text": "", "lang": "en"})
    (out / "manifest.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
    print(f"noise: {len(rows)} clips", flush=True)


def noise_short(work: Path) -> None:
    out = work / "data" / "noise-short"
    audio = out / "audio"
    audio.mkdir(parents=True, exist_ok=True)
    rng = np.random.default_rng(57)
    rows = []
    for seconds in (1, 2, 4):
        n = seconds * SR
        t = np.arange(n) / SR
        # A breath: band-limited noise (300 Hz–3 kHz) under a slow swell.
        spec = np.fft.rfft(rng.standard_normal(n))
        f = np.fft.rfftfreq(n, 1 / SR)
        spec[(f < 300) | (f > 3000)] = 0
        breath = np.fft.irfft(spec, n) * np.sin(np.pi * np.minimum(t / min(seconds, 1.5), 1)) ** 2
        tap = np.zeros(n)
        tap[n // 3:n // 3 + 800] = rng.standard_normal(800) * np.exp(-np.arange(800) / 120)
        clicks = np.zeros(n)
        for start in rng.integers(0, n - 400, 3 * seconds):
            clicks[start:start + 400] += rng.standard_normal(400) * np.exp(-np.arange(400) / 60)
        hum = sum(np.sin(2 * np.pi * 50 * k * t) / k for k in (1, 2, 3))
        clips = {
            "silence": np.zeros(n),
            "floor-70dbfs": _at_dbfs(rng.standard_normal(n), -70),  # the harness's lead-in noise
            "room-50dbfs": _at_dbfs(_pink(rng, n), -50),
            "breath-40dbfs": _at_dbfs(breath, -40),
            "tap-35dbfs": _at_dbfs(tap, -35),
            "clicks-35dbfs": _at_dbfs(clicks, -35),
            "hum-45dbfs": _at_dbfs(hum, -45),
        }
        for name, x in clips.items():
            wav = audio / f"{name}-{seconds}s.wav"
            write_wav(wav, x)
            rows.append({"id": f"{name}-{seconds}s", "audio": f"../data/noise-short/audio/{wav.name}", "text": "",
                         "lang": "en"})
    (out / "manifest.jsonl").write_text("".join(json.dumps(r) + "\n" for r in rows))
    print(f"noise-short: {len(rows)} clips", flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--tts", default="marmalade-tts")
    ap.add_argument("sets", nargs="+", choices=["dictation", "noise", "noise-short"])
    args = ap.parse_args()
    for s in args.sets:
        if s == "dictation":
            dictation(args.work, args.tts)
        else:
            {"noise": noise, "noise-short": noise_short}[s](args.work)


if __name__ == "__main__":
    main()
