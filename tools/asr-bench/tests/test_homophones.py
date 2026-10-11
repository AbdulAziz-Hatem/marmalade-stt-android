"""Kanji-choice scoring on the homophone set (#52; needs the venv: jiwer, numpy)."""

import csv
import unittest
from pathlib import Path

from asrbench.homophones import parse_targets, score, score_row

TSV = Path(__file__).resolve().parent.parent / "homophones-ja.tsv"


def outcomes(ref, hyp, spec):
    return [(x["outcome"], x["wrote"]) for x in score_row({"id": "u", "ref": ref, "hyp": hyp}, parse_targets(spec))]


class Classify(unittest.TestCase):
    def test_correct_wrong_kanji_kana_and_other(self):
        spec = "化学:かがく:科学"
        ref = "大学では化学を専攻して、毎日実験をしています。"
        self.assertEqual(outcomes(ref, "大学では化学を専攻して毎日実験をしています", spec), [("correct", "化学")])
        self.assertEqual(outcomes(ref, "大学では科学を専攻して、毎日実験をしています。", spec), [("homophone", "科学")])
        self.assertEqual(outcomes(ref, "大学ではカガクを専攻して毎日実験をしています", spec), [("kana", "カガク")])
        self.assertEqual(outcomes(ref, "大学では価格を専攻して毎日実験をしています", spec), [("other", "価格")])

    def test_length_change_still_finds_the_competitor(self):
        # 柿 (1 char) heard as 牡蠣 (2): the extra character is an insertion next to the span.
        self.assertEqual(outcomes("庭の柿が赤く実った。", "庭の牡蠣が赤く実った。", "柿:かき:牡蠣"),
                         [("homophone", "牡蠣")])
        self.assertEqual(outcomes("広島で牡蠣を食べました。", "広島で柿を食べました。", "牡蠣:かき:柿"),
                         [("homophone", "柿")])
        self.assertEqual(outcomes("庭の柿が赤く実った。", "庭のカキが赤く実った。", "柿:かき:牡蠣"), [("kana", "カキ")])

    def test_several_targets_in_order(self):
        spec = "橋:はし:箸,端|端:はし:橋,箸|箸:はし:橋,端"
        self.assertEqual(outcomes("橋の端で箸を落とした。", "橋の橋で箸を落とした", spec),
                         [("correct", "橋"), ("homophone", "橋"), ("correct", "箸")])

    def test_empty_hypothesis(self):
        self.assertEqual(outcomes("道の端を歩いてください。", "", "端:はし:橋,箸"), [("other", "")])

    def test_score_percentages(self):
        targets = {"a": "雨:あめ:飴", "b": "飴:あめ:雨"}
        rows = [{"id": "a-v1", "ref": "雨が降る", "hyp": "雨が降る"}, {"id": "b-v1", "ref": "飴をあげた", "hyp": "雨をあげた"}]
        s = score(rows, targets)
        self.assertEqual((s["n"], s["correct"], s["homophone"]), (2, 50.0, 50.0))


class Tsv(unittest.TestCase):
    def test_every_target_is_in_its_sentence_in_order(self):
        with TSV.open(newline="") as f:
            rows = list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))
        self.assertGreaterEqual(len(rows), 70)
        for r in rows:
            # Scoring the reference against itself must find every target.
            got = outcomes(r["text"], r["text"], r["targets"])
            self.assertTrue(all(o == "correct" for o, _ in got), (r["id"], got))
            for t in parse_targets(r["targets"]):
                self.assertNotIn(t["word"], t["competitors"], r["id"])


if __name__ == "__main__":
    unittest.main()
