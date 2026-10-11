#!/usr/bin/env python3
"""Frame raw 1080×2400 phone screenshots for the store listing (#66).

Same treatment as marmalade-tts-android's listing: the status bar and the
gesture strip are cropped off, and the 1080×2250 rest sits on a black
1200×2400 canvas (60 px sides, 75 px top and bottom). Usage:
    python3 tools/release/frame_screenshot.py OUT_DIR RAW.png [RAW.png ...]
    [--top 90] [--bottom 60]
--top/--bottom are the pixels cropped off the raw shot; together they must
remove 150 px so the content stays 1080×2250 (no scaling).
"""
import argparse
from pathlib import Path

from PIL import Image

CANVAS = (1200, 2400)
CONTENT = (1080, 2250)


def frame(raw: Path, out: Path, top: int, bottom: int) -> None:
    im = Image.open(raw).convert("RGB")
    if im.size != (1080, 2400):
        raise SystemExit(f"{raw}: {im.size}, expected 1080×2400")
    body = im.crop((0, top, 1080, 2400 - bottom))
    if body.size != CONTENT:
        raise SystemExit(f"--top + --bottom must be 150 (got {top + bottom})")
    canvas = Image.new("RGB", CANVAS, (0, 0, 0))
    canvas.paste(body, ((CANVAS[0] - CONTENT[0]) // 2, (CANVAS[1] - CONTENT[1]) // 2))
    canvas.save(out, optimize=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("out_dir", type=Path)
    ap.add_argument("raw", type=Path, nargs="+")
    ap.add_argument("--top", type=int, default=90)
    ap.add_argument("--bottom", type=int, default=60)
    a = ap.parse_args()
    a.out_dir.mkdir(parents=True, exist_ok=True)
    for raw in a.raw:
        out = a.out_dir / raw.name
        frame(raw, out, a.top, a.bottom)
        print(out)


if __name__ == "__main__":
    main()
