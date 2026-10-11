"""asrbench.paired: the per-utterance paired comparison (#55)."""

import unittest

from asrbench.paired import compare, drop_phantoms


def rows(hyps, lang="en", refs=None):
    refs = refs or ["the cat sat on the mat", "a dog ran in the park", "birds sing in the morning"]
    return [{"id": str(i), "lang": lang, "ref": r, "hyp": h} for i, (r, h) in enumerate(zip(refs, hyps))]


class PairedTest(unittest.TestCase):
    def test_identical_runs(self):
        a = rows(["the cat sat on the mat", "a dog ran in the park", "birds sing in morning"])
        c = compare(a, a)
        self.assertEqual((c["diff"], c["lo"], c["hi"], c["changed"], c["worse"], c["better"]), (0, 0, 0, 0, 0, 0))
        self.assertAlmostEqual(c["base"], 100 / 17)

    def test_one_more_error_and_a_runaway(self):
        a = rows(["the cat sat on the mat", "a dog ran in the park", "birds sing in the morning"])
        b = rows(["the cat sat on a mat", "a dog ran in the park", "birds sing in the morning birds sing in the morning"])
        c = compare(a, b)
        self.assertAlmostEqual(c["diff"], 100 * 6 / 17)  # one substitution, five insertions
        self.assertEqual((c["worse"], c["better"], c["changed"]), (2, 0, 2))
        self.assertEqual(c["runaways"], (0, 1))

    def test_a_long_loop_cut_mid_word_is_a_runaway(self):
        a = rows(["the cat sat on the mat", "a dog ran in the park", "birds sing in the morning"])
        b = rows(["the cat sat on the mat", "a dog ran in the park", "birds sing in the morning" + " music" * 12 + " mu"])
        self.assertEqual(compare(a, b)["runaways"], (0, 1))

    def test_only_shared_utterances_count(self):
        a = rows(["the cat sat on the mat", "a dog ran in the park", "birds sing in the morning"])
        c = compare(a, a[:2])
        self.assertEqual(c["n"], 2)

    def test_cjk_counts_characters(self):
        a = rows(["今日は晴れ"], lang="ja", refs=["今日は晴れです"])
        c = compare(a, a)
        self.assertAlmostEqual(c["base"], 100 * 2 / 7)


    def test_drop_phantoms_only_at_segment_edges(self):
        self.assertEqual(drop_phantoms("ご視聴ありがとうございました 香港島は観光の目玉です"), "香港島は観光の目玉です")
        self.assertEqual(drop_phantoms("おわり お 香港島"), "香港島")
        self.assertEqual(drop_phantoms("Bazı atomlar vardır. MÜZİK"), "Bazı atomlar vardır.")
        self.assertEqual(drop_phantoms("Müzik"), "")
        self.assertEqual(drop_phantoms("おわりのない話"), "おわりのない話")  # inside a segment: kept


if __name__ == "__main__":
    unittest.main()
