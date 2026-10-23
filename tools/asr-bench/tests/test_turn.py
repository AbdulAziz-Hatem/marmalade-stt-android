"""The end-of-turn gate (#43): made-up probabilities, no model."""

import unittest

import numpy as np

from asrbench.turn import (MODES, Turn, TurnConfig, TurnGate, decodable, mode_config, reference, score_clip,
                           turn_session)
from test_session import FakeVad

SR = 16_000
SMART = TurnConfig(0.2, smart=True)


class Probs:
    """A detector that answers from a list and remembers when it was asked."""

    def __init__(self, *probs):
        self.probs = list(probs)
        self.asked = []

    def __call__(self, stream, start, end):
        self.asked.append((start, end))
        return self.probs.pop(0) if len(self.probs) > 1 else self.probs[0], 0.05


def run_gate(cfg, probs, until=4.0, resume_at=None):
    """Close a segment at 0, then step every 100 ms. Returns (reason, silence at the end, silences asked at)."""
    gate, asked = TurnGate(cfg), []
    gate.segment_closed(0)
    for k in range(1, int(until * 10) + 1):
        silence = k / 10
        if resume_at is not None and abs(silence - resume_at) < 1e-9:
            gate.speech_resumed()
        reason = gate.step(int(silence * SR), lambda: (asked.append(silence), probs.pop(0))[1])
        if reason:
            return reason, silence, asked
    return None, None, asked


class TurnGateTest(unittest.TestCase):
    def test_pause_ends_at_the_segment_close(self):
        gate = TurnGate(TurnConfig(0.9))
        self.assertIsNone(gate.step(100, lambda: 1.0))  # nothing closed yet
        gate.segment_closed(0)
        self.assertEqual(gate.step(int(0.9 * SR), lambda: self.fail("Pause never asks the model")), "pause")

    def test_proposes_at_0_2_s_and_rechecks_every_0_5_s(self):
        reason, at, asked = run_gate(SMART, [0.2, 0.3, 0.9])
        self.assertEqual(reason, "complete")
        self.assertEqual(asked, [0.2, 0.7, 1.2])
        self.assertAlmostEqual(at, 1.2)

    def test_ceiling_ends_the_turn_whatever_the_model_says(self):
        reason, at, asked = run_gate(SMART, [0.1] * 10)
        self.assertEqual(reason, "ceiling")
        self.assertAlmostEqual(at, 3.0)
        self.assertEqual(asked, [0.2, 0.7, 1.2, 1.7, 2.2, 2.7])

    def test_threshold_is_strict(self):
        self.assertEqual(run_gate(SMART, [0.5] * 10)[0], "ceiling")
        self.assertEqual(run_gate(TurnConfig(0.2, smart=True, threshold=0.3), [0.31])[:2], ("complete", 0.2))

    def test_resumed_speech_stops_the_checks_and_a_new_close_starts_over(self):
        gate, asked = TurnGate(SMART), []
        gate.segment_closed(0)
        self.assertIsNone(gate.step(int(0.2 * SR), lambda: (asked.append(1), 0.1)[1]))
        gate.speech_resumed()
        for k in range(3, 60):
            self.assertIsNone(gate.step(int(k / 10 * SR), lambda: self.fail("asked while speaking")))
        gate.segment_closed(int(6.0 * SR))
        self.assertIsNone(gate.step(int(6.1 * SR), lambda: self.fail("asked before 0.2 s of silence")))
        self.assertEqual(gate.step(int(6.2 * SR), lambda: 0.9), "complete")
        self.assertEqual(len(asked), 1)

    def test_propose_0_3(self):
        _, _, asked = run_gate(TurnConfig(0.3, smart=True), [0.1] * 10)
        self.assertEqual(asked, [0.3, 0.8, 1.3, 1.8, 2.3, 2.8])


EARLY = mode_config("smart-p0.5-e0.95-n1.2-t0.7-c2.5")


class EarlyCheckTest(unittest.TestCase):
    """#91: a check at 0.5 s that ends the turn only when the model is very sure, then Normal's rule."""

    def test_a_sure_model_ends_the_turn_at_the_early_check(self):
        self.assertEqual(run_gate(EARLY, [0.96]), ("complete", 0.5, [0.5]))

    def test_an_unsure_early_check_falls_back_to_normal(self):
        # 0.9 would end a plain t0.7 turn; early it doesn't. The 1.2 s check needs only > 0.7.
        self.assertEqual(run_gate(EARLY, [0.9, 0.8]), ("complete", 1.2, [0.5, 1.2]))
        self.assertEqual(run_gate(EARLY, [0.95, 0.7, 0.1, 0.1]), ("ceiling", 2.5, [0.5, 1.2, 1.7, 2.2]))

    def test_resumed_speech_resets_and_the_next_close_starts_with_an_early_check(self):
        gate, asked = TurnGate(EARLY), []

        def prob(p):
            return lambda: (asked.append(p), p)[1]

        gate.segment_closed(0)
        self.assertIsNone(gate.step(int(0.5 * SR), prob(0.9)))  # early check: not sure enough
        self.assertIsNone(gate.step(int(0.8 * SR), prob(1.0)))  # nothing due before 1.2 s
        gate.speech_resumed()
        self.assertIsNone(gate.step(int(1.2 * SR), prob(1.0)))  # speaking: no check
        gate.segment_closed(int(3.0 * SR))
        self.assertIsNone(gate.step(int(3.4 * SR), prob(1.0)))  # 0.4 s of the new pause
        self.assertEqual(gate.step(int(3.5 * SR), prob(0.97)), "complete")
        self.assertEqual(asked, [0.9, 0.97])

    def test_without_an_early_threshold_nothing_changes(self):
        self.assertEqual(run_gate(mode_config("smart-p1.2-t0.7-c2.5"), [0.1] * 10)[2], [1.2, 1.7, 2.2])


def clip(*parts):
    """(seconds, loud) parts: a 440 Hz tone for speech, silence between."""
    out = []
    for seconds, loud in parts:
        n = int(seconds * SR)
        out.append(0.5 * np.sin(2 * np.pi * 440 * np.arange(n) / SR).astype(np.float32) if loud
                   else np.zeros(n, np.float32))
    return np.concatenate(out)


class TurnSessionTest(unittest.TestCase):
    x = clip((1.0, True), (0.6, False), (1.0, True))

    def test_pause_splits_where_smart_turn_holds_the_turn(self):
        pause = turn_session(self.x, FakeVad(), TurnConfig(0.2), tail_silence=4.0)
        self.assertEqual([t.reason for t in pause.turns], ["pause", "pause"])
        probs = Probs(0.1, 0.9)  # the mid-clip pause scores low, the real end high
        smart = turn_session(self.x, FakeVad(), SMART, probs, tail_silence=4.0)
        self.assertEqual([t.reason for t in smart.turns], ["complete"])
        turn = smart.turns[0]
        self.assertEqual((turn.start, turn.end), (pause.turns[0].start, pause.turns[1].end))
        # Both checks score the turn from its start, trailing silence included, up to where the
        # VAD is (whole windows, as SmartTurnGate's tail), not the hop's end (#64).
        self.assertTrue(all(start == turn.start for start, _ in probs.asked))
        self.assertGreater(probs.asked[1][1], pause.turns[1].end)
        self.assertTrue(all(end % 512 == 0 for _, end in probs.asked))
        hop_end = (probs.asked[1][1] // 1600 + 1) * 1600
        self.assertLess(hop_end - probs.asked[1][1], 512 + 1)
        self.assertAlmostEqual(turn.at, hop_end / SR + 0.05)  # decided as that hop arrived; the model's time counts

    def test_silence_counts_from_the_vad_position(self):
        # The ceiling (and every recheck) is silence up to where the VAD is: SmartTurnGate counts
        # the windows it is fed, so a 2 s ceiling can land one hop after the hop end reaches 2 s.
        cfg = TurnConfig(0.2, smart=True, ceiling=2.0)
        res = turn_session(self.x, FakeVad(), cfg, Probs(0.0), tail_silence=4.0)
        (turn,) = res.turns
        self.assertEqual(turn.reason, "ceiling")
        silences = [c[0] for c in turn.checks]
        self.assertTrue(all(abs(s * SR / 512 - round(s * SR / 512)) < 1e-6 for s in silences), silences)

        def vad_position(hop_end):  # whole windows fed once that hop has arrived
            return (hop_end - 1) // 512 * 512

        hop_end = round(turn.at * SR)
        self.assertGreaterEqual(vad_position(hop_end) - turn.end, 2 * SR)
        self.assertLess(vad_position(hop_end - 1600) - turn.end, 2 * SR)

    def test_ceiling_and_flush(self):
        smart = turn_session(self.x, FakeVad(), SMART, Probs(0.0), tail_silence=4.0)
        self.assertEqual([t.reason for t in smart.turns], ["ceiling"])
        # 1 in the mid pause, 5 in the final one: at 0.22, 0.83, 1.41, 1.92 and 2.43 s. Silence counts
        # in whole VAD windows (3 or 4 per hop), as SmartTurnGate's, so a recheck can wait a hop more.
        self.assertEqual([c[0] for c in smart.turns[0].checks], [0.224, 0.224, 0.832, 1.408, 1.92, 2.432])
        cut = turn_session(self.x, FakeVad(), SMART, Probs(0.0), tail_silence=1.0)
        self.assertEqual([t.reason for t in cut.turns], ["flush"])

    def test_a_long_turn_ends_at_its_last_segment_boundary(self):
        cfg = TurnConfig(0.2, smart=True, max_turn=2.5)
        res = turn_session(clip((1.0, True), (0.6, False), (1.5, True)), FakeVad(), cfg, Probs(0.1, 0.9),
                           tail_silence=4.0)
        self.assertEqual([t.reason for t in res.turns], ["cap", "complete"])
        self.assertEqual(res.turns[1].start, res.turns[0].end)  # the next turn picks up where it ended


    def test_continuous_speech_is_cut_at_the_cap_and_goes_on_as_the_next_turn(self):
        # 40 s with no pause the VAD could end at, and one quiet gap at 27.8 s (#48, #54).
        x = clip((40.0, True))
        x[int(27.8 * SR):int(27.9 * SR)] = 0.001
        for cfg, probs in ((TurnConfig(0.2), None), (SMART, Probs(0.9))):
            res = turn_session(x, FakeVad(), cfg, probs, lead_silence=0.0, tail_silence=4.0)
            self.assertEqual([t.reason for t in res.turns], ["cut", "pause" if probs is None else "complete"])
            first, rest = res.turns
            self.assertTrue(int(27.8 * SR) <= first.end <= int(27.9 * SR), first.end)
            self.assertLessEqual(first.end - first.start, 28 * SR + 9_600)
            self.assertEqual(rest.start, first.end)
            self.assertTrue(rest.from_cut and decodable(rest))
            self.assertGreaterEqual(rest.end, 40 * SR - 512)

    def test_a_cut_turns_rest_is_decoded_only_if_it_holds_speech(self):
        self.assertTrue(decodable(Turn(0, 600, 1.0, "pause")))  # not from a cut: always
        self.assertTrue(decodable(Turn(0, 600, 1.0, "pause", from_cut=True, near_silent=False)))
        self.assertFalse(decodable(Turn(0, 600, 1.0, "pause", from_cut=True, near_silent=True)))
        self.assertFalse(decodable(Turn(0, 500, 1.0, "pause", from_cut=True, near_silent=False)))  # under a window


class ModeConfigTest(unittest.TestCase):
    def test_named_modes_and_patterns(self):
        self.assertIs(mode_config("smart"), MODES["smart"])
        self.assertEqual(mode_config("pause-1.2"), TurnConfig(1.2))
        self.assertEqual(mode_config("smart-p0.4-t0.6-c1.5"),
                         TurnConfig(0.4, smart=True, threshold=0.6, ceiling=1.5))
        self.assertEqual(mode_config("smart-t0.85"), TurnConfig(0.2, smart=True, threshold=0.85))
        self.assertEqual(mode_config("smart-p0.5-t0.7-c2"), MODES["smart-p0.5-t0.7-c2"])
        self.assertEqual(EARLY, TurnConfig(0.5, smart=True, threshold=0.7, ceiling=2.5,
                                           early_threshold=0.95, normal_at=1.2))

    def test_bad_names(self):
        for name in ("pause", "pause-x", "smart-x0.5", "smart-t0.5-t0.6", "smart-p", "vad-0.5",
                     "smart-p0.5-e0.95", "smart-p0.5-n1.2", "smart-p0.5-e0.95-n0.5"):
            with self.assertRaises(ValueError, msg=name):
                mode_config(name)


class ScoringTest(unittest.TestCase):
    def test_reference_and_cut_offs(self):
        words = [(0.0, 0.4, "so", False), (1.5, 1.9, "then", True), (3.0, 3.5, "done", True)]
        segments = [(int(0.45 * SR), int(0.95 * SR)), (int(1.95 * SR), int(2.5 * SR)), (int(3.45 * SR), int(4.1 * SR))]
        ref = reference(words, segments, lead=0.5)
        self.assertEqual(ref["last"], 3.5)
        self.assertEqual(ref["speech_end"], 4.1)
        self.assertEqual(ref["pauses"], [[0.95, 1.95, "mid"], [2.5, 3.45, "end"]])
        s = score_clip(ref, [{"at": 1.2, "reason": "pause", "checks": []},
                             {"at": 3.3, "reason": "pause", "checks": []},
                             {"at": 5.0, "reason": "pause", "checks": []}])
        self.assertEqual((s["cuts"], s["cut_pauses"], s["unattributed"]), (2, [0, 1], 0))
        self.assertAlmostEqual(s["latency"], 5.0 - 4.1)


if __name__ == "__main__":
    unittest.main()
