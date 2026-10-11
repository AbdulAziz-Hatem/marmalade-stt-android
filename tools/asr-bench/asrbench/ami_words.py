"""Word timings for AMI clips, from the corpus's manual annotations (#43).

The end-of-turn benchmark (`turn.py`) needs to know where speech is in each
clip: which pauses are inside a sentence and when the last word ends. The AMI
release has hand-checked word timings per meeting and speaker
(`words/<meeting>.<agent>.words.xml`), CC-BY-4.0 like the audio. The zip is
23 MB and is fetched once into `$WORK/raw/ami/`.

A clip id is `AMI_<meeting>_H0<channel>_<speaker>_<begin>_<end>` with times
in centiseconds; `corpusResources/meetings.xml` maps the headset channel to
the annotation's agent letter. Punctuation tokens (`punc="true"`) and
non-word events (laughs, coughs) are left out: the gate is judged on words.
A word followed by a full stop, question mark or exclamation mark is marked
as ending a sentence, so a pause can be told apart as mid-sentence or
between sentences.
"""

import hashlib
import re
import xml.etree.ElementTree as ET
import zipfile
from functools import cache
from pathlib import Path

from .prepare_data import download

ANNOTATIONS_URL = "https://groups.inf.ed.ac.uk/ami/AMICorpusAnnotations/ami_public_manual_1.6.2.zip"
ANNOTATIONS_SHA256 = "b56e5babb2496b8795deeeda7e71178d7fbc9963f94276cf2a3f4b56ebbc9f9d"
# Words whose annotated span reaches this far outside the clip still count (the
# clip bounds are rounded to 10 ms, and the segmenter trims a little).
EDGE_TOLERANCE = 0.05
SENTENCE_ENDS = {".", "?", "!"}
_CLIP_ID = re.compile(r"AMI_(?P<meeting>[A-Z]{2}\d{4}[a-z])_H0(?P<channel>\d)_\w+?_(?P<begin>\d{7})_(?P<end>\d{7})$")


def fetch(work: Path) -> Path:
    path = download(ANNOTATIONS_URL, work / "raw" / "ami" / "ami_public_manual_1.6.2.zip")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if digest != ANNOTATIONS_SHA256:
        raise ValueError(f"{path}: sha256 {digest}, expected {ANNOTATIONS_SHA256}")
    return path


class AmiWords:
    def __init__(self, zip_path: Path):
        self.zip = zipfile.ZipFile(zip_path)
        root = ET.fromstring(self.zip.read("corpusResources/meetings.xml"))
        self.agents = {(m.get("observation"), int(s.get("channel"))): s.get("nxt_agent")
                       for m in root.iter("meeting") for s in m.iter("speaker")}

    @cache
    def _words(self, meeting: str, agent: str) -> list[tuple[float, float, str, bool]]:
        root = ET.fromstring(self.zip.read(f"words/{meeting}.{agent}.words.xml"))
        out = []
        for w in root.iter("w"):
            text = (w.text or "").strip()
            if w.get("punc") == "true":
                if out and text in SENTENCE_ENDS:
                    out[-1] = (*out[-1][:3], True)
                continue
            if w.get("starttime") is None or w.get("endtime") is None:
                continue
            out.append((float(w.get("starttime")), float(w.get("endtime")), text, False))
        return sorted(out)

    def clip_words(self, clip_id: str) -> list[tuple[float, float, str, bool]]:
        """(start, end, word, ends a sentence) in seconds from the clip's first sample, in time order.

        "Ends a sentence": the transcriber put a full stop, question mark or exclamation mark
        after it."""
        m = _CLIP_ID.match(clip_id)
        if not m:
            raise ValueError(f"not an AMI headset clip id: {clip_id}")
        begin, end = int(m["begin"]) / 100, int(m["end"]) / 100
        agent = self.agents[(m["meeting"], int(m["channel"]))]
        return [(max(s - begin, 0.0), min(e, end) - begin, w, stop) for s, e, w, stop in self._words(m["meeting"], agent)
                if s >= begin - EDGE_TOLERANCE and e <= end + EDGE_TOLERANCE]
