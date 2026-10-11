"""turbo/quantize.py: the LM-head rewrite and the 8-bit embedding on a small graph shaped like k2-fsa's (#55).

Needs onnx (not in the harness venv; the conversion venv has it), so it skips without."""

import sys
import unittest
from pathlib import Path

import numpy as np

try:
    import onnx
    import onnxruntime as ort
    from onnx import TensorProto, helper, numpy_helper
except ImportError:
    onnx = None

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "turbo"))

VOCAB, DIM = 50_016, 64


def k2_like_decoder():
    """emb = Gather(W, tokens); logits = Transpose(MatMul(Identity(W), Transpose(x))) + emb's sum, as the export."""
    w = np.random.default_rng(0).standard_normal((VOCAB, DIM)).astype(np.float32)
    nodes = [
        helper.make_node("Gather", ["W", "tokens"], ["emb"], name="emb_gather"),
        helper.make_node("Identity", ["W"], ["W_id"]),
        helper.make_node("Transpose", ["x"], ["xt"], perm=[0, 2, 1]),
        helper.make_node("MatMul", ["W_id", "xt"], ["wx"]),
        helper.make_node("Transpose", ["wx"], ["logits"], perm=[0, 2, 1]),
    ]
    g = helper.make_graph(
        nodes, "dec",
        [helper.make_tensor_value_info("x", TensorProto.FLOAT, [1, None, DIM]),
         helper.make_tensor_value_info("tokens", TensorProto.INT64, [1, None])],
        [helper.make_tensor_value_info("logits", TensorProto.FLOAT, None),
         helper.make_tensor_value_info("emb", TensorProto.FLOAT, None)],
        [numpy_helper.from_array(w, "W")])
    return helper.make_model(g, opset_imports=[helper.make_opsetid("", 13)], ir_version=8)


def run(m, x, tokens):
    s = ort.InferenceSession(m.SerializeToString(), providers=["CPUExecutionProvider"])
    return s.run(None, {"x": x, "tokens": tokens})


@unittest.skipIf(onnx is None, "needs onnx")
class TurboQuantizeTest(unittest.TestCase):
    def setUp(self):
        import quantize
        self.q = quantize
        rng = np.random.default_rng(1)
        self.x = rng.standard_normal((1, 3, DIM)).astype(np.float32)
        self.tokens = np.array([[5, 50_000, 17]], np.int64)
        self.m = k2_like_decoder()
        self.ref = run(self.m, self.x, self.tokens)

    def test_head_rewrite_is_exact_and_removes_the_transposes(self):
        name = self.q.materialise_head(self.m)
        ops = [n.op_type for n in self.m.graph.node]
        self.assertEqual(sorted(ops), ["Gather", "MatMul"])
        self.assertEqual(name, "lm_head_matmul")
        got = run(self.m, self.x, self.tokens)
        np.testing.assert_allclose(got[0], self.ref[0], atol=1e-4)
        np.testing.assert_array_equal(got[1], self.ref[1])

    def test_8bit_head_and_embedding_stay_close(self):
        head = self.q.materialise_head(self.m)
        m = self.q.quant(self.m, 8, 32, include=[head])
        self.q.gather8(m)
        ops = sorted(n.op_type for n in m.graph.node)
        self.assertEqual(ops, ["GatherBlockQuantized", "MatMulNBits"])
        self.assertNotIn("W", self.q.initializers(m))  # the fp32 embedding is gone
        logits, emb = run(m, self.x, self.tokens)
        self.assertLess(np.abs(logits - self.ref[0]).max(), 0.05 * np.abs(self.ref[0]).max())
        self.assertLess(np.abs(emb - self.ref[1]).max(), 0.01 * np.abs(self.ref[1]).max())


if __name__ == "__main__":
    unittest.main()
