"""The Granite pipeline's pure parts: front-end shapes, CTC collapse, byte-level decoding.

Parity with transformers' feature extractor and PyTorch's logits is checked by
granite/export_onnx.py on real clips (it needs torch); these need only numpy.
"""

import tempfile
import unittest
from pathlib import Path

import numpy as np

from asrbench import granite as G


class Features(unittest.TestCase):
    def test_frame_count_and_width(self):
        # 100 mel frames -> 50 stacked; 101 -> padded to 102 -> 51.
        self.assertEqual(G.features(np.zeros(16_000, np.float32)).shape, (50, 320))
        self.assertEqual(G.features(np.zeros(16_160, np.float32)).shape, (51, 320))

    def test_floor_and_scale(self):
        x = np.random.default_rng(0).standard_normal(16_000).astype(np.float32) * 0.1
        x[:4000] = 0  # silence, floored at 8 decades below the loudest bin
        f = G.features(x)
        mels = np.concatenate([f[:, :80], f[:, 160:240]])
        top = mels.max()
        self.assertAlmostEqual(float(mels.min()), top - 2.0, places=5)  # 8 decades / 4

    def test_stacking_order_and_deltas(self):
        x = np.random.default_rng(1).standard_normal(8_000).astype(np.float32)
        f = G.features(x)
        # Each row is [mel, delta] of frame 2i, then of frame 2i+1.
        mel = np.stack([f[:, :80], f[:, 160:240]], axis=1).reshape(-1, 80)
        delta = np.stack([f[:, 80:160], f[:, 240:320]], axis=1).reshape(-1, 80)
        np.testing.assert_allclose(delta[5], (mel[6] - mel[4]) / 2, atol=1e-6)
        np.testing.assert_allclose(delta[0], (mel[1] - mel[0]) / 2, atol=1e-6)
        np.testing.assert_allclose(delta[-1], (mel[-1] - mel[-2]) / 2, atol=1e-6)

    def test_filterbank(self):
        fb = G.mel_filterbank()
        self.assertEqual(fb.shape, (257, 80))
        self.assertTrue((fb >= 0).all())
        self.assertTrue((fb.max(axis=0) > 0).all())  # no empty filter at 512-point resolution


class Decode(unittest.TestCase):
    def test_ctc_greedy_collapses_repeats_and_drops_blank(self):
        ids = [3, 3, 0, 3, 5, 5, 0, 0]
        logits = np.full((len(ids), 8), -1.0)
        logits[np.arange(len(ids)), ids] = 1.0
        self.assertEqual(G.ctc_greedy(logits), [3, 3, 5])

    def test_byte_level_vocab(self):
        pieces = ["<|blank|>", "Ġhello", "Ġw", "orld", "ĠcafÃ", "©", "."]
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "tokens.txt"
            path.write_text("".join(f"{p} {i}\n" for i, p in enumerate(pieces)), encoding="utf-8")
            vocab = G.ByteLevelVocab.load(path)
        self.assertEqual(vocab.decode([1, 2, 3, 4, 5, 6]), "hello world café.")
        self.assertEqual(vocab.decode([0, 1, 0]), "hello")


if __name__ == "__main__":
    unittest.main()
