"""#64: silence models, the timing-free reference, the one-sentence filter."""

import unittest

import numpy as np

from asrbench.prepare_data import one_sentence
from asrbench.turn import score_clip
from asrbench.turn_defaults import PATIENCE, quieten, segment_reference

SR = 16_000


class QuietenTest(unittest.TestCase):
    def test_speech_is_kept_and_pauses_become_the_floor(self):
        x = np.full(3 * SR, 0.5, np.float32)
        y = quieten(x, [(0, SR), (2 * SR, 3 * SR)], None)
        self.assertTrue(np.all(y[:SR] == 0.5))  # speech, untouched
        self.assertTrue(np.all(y[SR + 1_700:2 * SR - 1_700] == 0.0))  # the pause, beyond the margin and ramp
        self.assertTrue(np.all(y[SR:SR + 1_500] == 0.5))  # the margin after the speech
        n90 = quieten(x, [(0, SR)], -90.0)[2 * SR:]
        self.assertAlmostEqual(20 * np.log10(np.sqrt(np.mean(n90.astype(np.float64) ** 2))), -90.0, delta=0.5)


class SegmentReferenceTest(unittest.TestCase):
    def test_pauses_are_mid_sentence_and_the_last_onset_marks_a_cut(self):
        segments = [(int(0.6 * SR), int(2.0 * SR)), (int(2.5 * SR), int(4.0 * SR))]
        ref = segment_reference(segments, lead=0.5)
        self.assertEqual(ref["pauses"], [[2.0, 2.5, "mid"]])
        self.assertEqual((ref["last"], ref["speech_end"]), (2.85, 4.0))  # onset + Silero's onset lag
        s = score_clip(ref, [{"at": 2.4, "reason": "complete", "checks": []},
                             {"at": 4.6, "reason": "complete", "checks": []}])
        self.assertEqual((s["cuts"], s["cut_pauses"]), (1, [0]))
        self.assertAlmostEqual(s["latency"], 0.6)

    def test_nothing_after_the_lead(self):
        self.assertIsNone(segment_reference([(0, int(0.4 * SR))], lead=0.5))


class OneSentenceTest(unittest.TestCase):
    def test_sentence_ends_inside(self):
        self.assertTrue(one_sentence("Questo è fondamentale, se pensiamo a paesi come la Malesia."))
        self.assertTrue(one_sentence("It costs 3.5 euros?"))
        self.assertTrue(one_sentence('"He left."'))
        self.assertFalse(one_sentence("He left. She stayed."))
        self.assertFalse(one_sentence("行きました。帰りました。"))
        self.assertTrue(one_sentence("行きました。"))
        self.assertFalse(one_sentence("वह गया। वह रुकी।"))


class PatienceTest(unittest.TestCase):
    def test_matches_the_app(self):
        # SmartTurnGate.kt's Patience: (propose, threshold, ceiling).
        self.assertEqual(PATIENCE, {"quick": "smart-p0.5-t0.7-c1.5", "normal": "smart-p1.2-t0.7-c2.5",
                                    "relaxed": "smart-p1.5-t0.7-c3"})


if __name__ == "__main__":
    unittest.main()
