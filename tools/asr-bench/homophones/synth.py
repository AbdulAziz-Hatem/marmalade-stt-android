"""Synthesise the Japanese homophone set (#52) with Kokoro-82M and write its manifest.

    python homophones/synth.py --work $WORK      # in the venv of homophones/requirements.txt

Each sentence of `homophones-ja.tsv` is read by two Kokoro voices (jf_alpha,
jm_kumo) into $WORK/data/homophones-ja/audio/<id>-<voice>.wav (24 kHz; run.py
resamples). `asrbench.homophones` scores the targets, read from the TSV.
Synthetic speech: it measures kanji choice from
context, not robustness to real voices.

The text goes through Kokoro's own Japanese G2P (misaki's JAG2P, which reads
with MeCab + UniDic). Before synthesis every target's reading is checked
against the TSV: if UniDic reads 端 as はじ where the sentence wants はし, the
clip would test the wrong word, so the script stops.

Kokoro's package __init__ imports its English pipeline, which needs espeak-ng
(GPL-3.0). The Japanese path doesn't, so `kokoro.model` is imported without
the package __init__ and espeak-ng is never installed (requirements.txt).
"""

import argparse
import csv
import importlib.util
import json
import sys
import types
import wave
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent.parent
REPO, REVISION = "hexgrad/Kokoro-82M", "f3ff3571791e39611d31c381e3a41a3af07b4987"  # Apache-2.0
VOICES = ("jf_alpha", "jm_kumo")
SR = 24_000


def kmodel():
    """kokoro.model.KModel, imported without kokoro/__init__ (which pulls in espeak-ng)."""
    pkg = types.ModuleType("kokoro")
    pkg.__path__ = list(importlib.util.find_spec("kokoro").submodule_search_locations)
    sys.modules["kokoro"] = pkg
    from kokoro.model import KModel
    return KModel


def parse_targets(spec: str) -> list[dict]:
    """asrbench.homophones.parse_targets, copied: that module needs jiwer, which this venv doesn't have."""
    out = []
    for t in spec.split("|"):
        word, reading, comp = t.split(":")
        out.append({"word": word, "reading": reading, "competitors": comp.split(",") if comp else []})
    return out


def target_spans(text: str, targets: list[dict]) -> list[tuple[int, int]]:
    spans, pos = [], 0
    for t in targets:
        i = text.index(t["word"], pos)
        spans.append((i, i + len(t["word"])))
        pos = i + len(t["word"])
    return spans


def check_readings(tagger, text: str, targets: list[dict]) -> list[str]:
    """Problems where UniDic's reading of a target differs from the TSV's (empty list = all fine)."""
    import jaconv
    words, pos = [], 0
    for w in tagger(text):
        start = text.index(w.surface, pos)
        pos = start + len(w.surface)
        words.append((start, pos, jaconv.kata2hira(w.feature.kana or w.surface)))
    problems = []
    for t, (a, b) in zip(targets, target_spans(text, targets)):
        kana = "".join(k for s, e, k in words if s < b and e > a)
        if t["reading"] not in kana:
            problems.append(f"{t['word']}: UniDic reads {kana!r}, the TSV wants {t['reading']!r}")
    return problems


def write_wav(path: Path, x: np.ndarray) -> None:
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    args = ap.parse_args()
    import torch
    from huggingface_hub import hf_hub_download
    from loguru import logger
    from misaki import ja

    logger.remove()  # KModel logs every phoneme string at DEBUG
    logger.add(sys.stderr, level="WARNING")
    torch.set_num_threads(4)
    with (HERE / "homophones-ja.tsv").open(newline="") as f:
        rows = list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))
    g2p = ja.JAG2P()
    bad = {r["id"]: p for r in rows if (p := check_readings(g2p.cutlet.tagger, r["text"], parse_targets(r["targets"])))}
    if bad:
        sys.exit("reading mismatches:\n" + "\n".join(f"  {k}: {v}" for k, v in bad.items()))

    model = kmodel()(repo_id=REPO, config=hf_hub_download(REPO, "config.json", revision=REVISION),
                     model=hf_hub_download(REPO, "kokoro-v1_0.pth", revision=REVISION)).eval()
    packs = {v: torch.load(hf_hub_download(REPO, f"voices/{v}.pt", revision=REVISION), weights_only=True)
             for v in VOICES}
    out = args.work.resolve() / "data" / "homophones-ja"
    (out / "audio").mkdir(parents=True, exist_ok=True)
    manifest = []
    for r in rows:
        ps, _ = g2p(r["text"])
        for v in VOICES:
            wav = out / "audio" / f"{r['id']}-{v}.wav"
            if not wav.exists():
                with torch.no_grad():
                    audio = model(ps, packs[v][len(ps) - 1], 1.0)
                write_wav(wav, audio.numpy())
            manifest.append({"id": f"{r['id']}-{v}", "audio": str(wav), "text": r["text"], "lang": "ja",
                             "voice": v, "phonemes": ps})
    (out / "manifest.jsonl").write_text("".join(json.dumps(m, ensure_ascii=False) + "\n" for m in manifest))
    print(f"homophones-ja: {len(manifest)} clips ({len(rows)} sentences x {len(VOICES)} voices)", flush=True)


if __name__ == "__main__":
    main()
