"""Download the engines' pinned files into $WORK/models/<engine id>/ and check sha256.

Files are stored flat under their basename, as the app installs them. A file
already present with the right hash is kept, so you can pre-seed the directory
(e.g. rsync from another machine's model cache) and only fetch what's missing.

    python -m asrbench.fetch_models --work $WORK [engine ids...]
"""

import argparse
import hashlib
import shutil
import sys
import urllib.request
from pathlib import Path

from .engines import BY_ID, ENGINES, Engine


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(engine: Engine, models: Path) -> None:
    out = models / engine.id
    out.mkdir(parents=True, exist_ok=True)
    for f in engine.files:
        dest = out / f.name
        if dest.is_file() and dest.stat().st_size == f.size and sha256(dest) == f.sha256:
            continue
        if engine.converted:
            sys.exit(f"{engine.id}/{f.name} is missing or differs: build it with tools/parakeet-ultra/convert.py "
                     f"and copy its output to {out}")
        part = dest.with_name(dest.name + ".part")
        print(f"fetch {engine.id}/{f.name} ({f.size / 1e6:.0f} MB)", flush=True)
        with urllib.request.urlopen(engine.url(f)) as r, part.open("wb") as w:
            shutil.copyfileobj(r, w, 1 << 20)
        got = sha256(part)
        if got != f.sha256:
            part.unlink()
            sys.exit(f"sha256 mismatch for {engine.id}/{f.name}: {got}")
        part.rename(dest)
    print(f"ok {engine.id}", flush=True)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("engines", nargs="*", help="engine ids (default: all)")
    args = ap.parse_args()
    for e in [BY_ID[i] for i in args.engines] or ENGINES:
        fetch(e, args.work / "models")


if __name__ == "__main__":
    main()
