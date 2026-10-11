"""asrbench.phantoms: the per-language phantom tables (#57)."""

import json
import tempfile
import unittest
from pathlib import Path

from asrbench.phantoms import mine, noise_table


def write(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows))


class PhantomsTest(unittest.TestCase):
    def test_noise_table_counts_clips_per_hint_and_agc(self):
        with tempfile.TemporaryDirectory() as d:
            res = Path(d) / "results-57"
            row = lambda id, text, db: {"id": id, "hyp": text, "finals": [text] if text else [],
                                        "rms_db": [db] if text else []}
            write(res / "whisper-turbo/noise-short@direct@hint-ja.jsonl",
                  [row("a", "おわり", -40.0), row("b", "おわり", -25.0), row("c", "", None)])
            write(res / "whisper-turbo/noise-short@direct@noagc@hint-ja.jsonl", [row("a", "おわり", -70.0)])
            write(res / "whisper-turbo/noise-short@direct@auto.jsonl", [row("a", "you", -100.0)])
            write(res / "whisper-turbo/fleurs-ja.jsonl", [row("x", "speech", -20.0)])  # not a noise run
            lines = noise_table(res)
        self.assertIn("| whisper-turbo | ja | おわり | 2/3 | 1/1 | -70 … -25 | 1/3 |", lines)
        self.assertIn("| whisper-turbo | auto | you | 1/1 | 0/0 | -100 … -100 | 1/1 |", lines)
        self.assertEqual(len(lines), 4)

    def test_mine_finds_recurring_edge_finals(self):
        with tempfile.TemporaryDirectory() as d:
            run = Path(d) / "results-57/whisper-turbo"
            write(run / "fleurs-ja.jsonl", [
                {"id": "1", "lang": "ja", "ref": "犬が走る。", "hyp": "おわり 犬が走る。", "segments": 2,
                 "finals": ["おわり", "犬が走る。"], "rms_db": [-55.0, -20.0]},
                {"id": "2", "lang": "ja", "ref": "猫が寝る。", "hyp": "おわり 猫が寝る。", "segments": 2,
                 "finals": ["おわり", "猫が寝る。"], "rms_db": [-45.0, -20.0]},
                # Before #57: no finals, so the hypothesis is split on spaces (CJK only).
                {"id": "3", "lang": "ja", "ref": "雨。", "hyp": "おわり 雨。", "segments": 2},
                {"id": "4", "lang": "ja", "ref": "晴れ。", "hyp": "晴れ。", "segments": 1,
                 "finals": ["晴れ。"], "rms_db": [-20.0]},
            ])
            write(run / "fleurs-de.jsonl", [
                {"id": "5", "lang": "de", "ref": "ja", "hyp": "Musik ja", "segments": 2},  # no finals: skipped
            ])
            lines = mine([run], min_count=2)
        self.assertEqual(lines[2:], ["| whisper-turbo | fleurs-ja | おわり | 3/4 | -55 … -45 | 0 |"])


if __name__ == "__main__":
    unittest.main()
