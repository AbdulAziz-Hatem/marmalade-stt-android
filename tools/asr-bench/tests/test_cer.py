"""CJK normalisation, CER and punctuation scoring (needs the venv: jiwer, numpy, opencc)."""

import unittest

from asrbench.cer import cer_stats, chars_with_marks, common_subset, normalise, punct_stats


def row(ref, hyp, lang="zh", id="u"):
    return {"id": id, "ref": ref, "hyp": hyp, "lang": lang}


class Normalise(unittest.TestCase):
    def test_drops_punctuation_spaces_and_folds_width(self):
        self.assertEqual(normalise("今天， 天气 很好！ＡＢＣ１２", "zh"), "今天天气很好abc12")

    def test_traditional_to_simplified_for_chinese_only(self):
        self.assertEqual(normalise("這個漢語", "zh"), "这个汉语")
        self.assertEqual(normalise("漢字", "ja"), "漢字")

    def test_japanese_keeps_long_vowel_mark_and_iteration_mark(self):
        self.assertEqual(normalise("コーヒー、時々。", "ja"), "コーヒー時々")

    def test_korean_scored_without_spaces(self):
        self.assertEqual(normalise("안녕하세요. 반갑습니다", "ko"), "안녕하세요반갑습니다")

    def test_marks_follow_their_character(self):
        chars, marks = chars_with_marks("你好，世界。对吗？", "zh")
        self.assertEqual(chars, "你好世界对吗")
        self.assertEqual(marks, [None, ",", None, ".", None, "?"])


class Cer(unittest.TestCase):
    def test_perfect_and_one_substitution(self):
        self.assertEqual(cer_stats([row("你好世界", "你好，世界。")])["cer"], 0.0)
        s = cer_stats([row("你好世界", "你好视界")])
        self.assertAlmostEqual(s["cer"], 25.0)
        self.assertEqual((s["sub"], s["del"], s["ins"]), (1, 0, 0))

    def test_empty_hypothesis_is_all_deletions(self):
        s = cer_stats([row("你好", "")])
        self.assertEqual((s["cer"], s["empty"], s["head_drop"]), (100.0, 1, 1))

    def test_subset_filter(self):
        rows = [row("你好", "你好", id="a"), row("再见", "在见", id="b")]
        self.assertEqual(cer_stats(rows, {"a"})["cer"], 0.0)
        self.assertIsNone(cer_stats(rows, set()))

    def test_common_subset_drops_digits_on_either_side_in_any_run(self):
        run1 = [row("二零零八年", "2008年", id="a"), row("你好", "你好", id="b"), row("3个", "三个", id="c")]
        run2 = [row("二零零八年", "二零零八年", id="a"), row("你好", "你好", id="b"), row("3个", "3个", id="c")]
        self.assertEqual(common_subset([run1, run2]), {"b"})


class Punct(unittest.TestCase):
    def test_perfect_punctuation(self):
        s = punct_stats([row("你好，世界。", "你好,世界.")])
        self.assertEqual((s["f1"], s["comma_f1"], s["end_f1"]), (100.0, 100.0, 100.0))

    def test_missing_and_wrong_marks(self):
        # Reference: comma after 好, stop at the end. Hypothesis: no comma, question mark at the end.
        s = punct_stats([row("你好，世界。", "你好世界？")])
        self.assertEqual(s["counts"]["all"], [0, 1, 2])
        self.assertEqual(s["comma_f1"], 0.0)
        self.assertEqual(s["end_f1"], 100.0)  # any sentence-end mark counts for end_f1
        self.assertEqual((s["ends_ref"], s["ends_hyp"]), (1, 1))

    def test_marks_survive_a_substituted_character(self):
        s = punct_stats([row("你好，世界。", "你号，世界。")])
        self.assertEqual(s["f1"], 100.0)

    def test_unpunctuated_references_give_none(self):
        self.assertIsNone(punct_stats([row("你好世界", "你好，世界。")]))


if __name__ == "__main__":
    unittest.main()
