"""Re-quantise Whisper large-v3-turbo's sherpa-onnx files with ONNX Runtime's MatMulNBits quantiser (#55).

k2-fsa's export (csukuangfj/sherpa-onnx-whisper-turbo @ 2ca6ff69, and our ACFT conversion, which uses the same
exporter) computes the decoder's logits as Transpose(MatMul(token_embedding, Transpose(x))). quantize_dynamic only
quantises a MatMul whose second input is a weight, so this LM head stays fp32: 265 MB read per decoded token, about
a quarter of a decoder step. This script rewrites it as MatMul(x, token_embedding^T) and quantises it.

    # the app's int8 decoder with an 8-bit LM head and an 8-bit token embedding ("int8-h8g8")
    python turbo/quantize.py decoder turbo-decoder.int8.onnx --src turbo-decoder.int8.onnx --head 8 --gather8
    # 4-bit layers from the fp32 export (turbo-decoder.onnx), LM head 8-bit
    python turbo/quantize.py decoder out.onnx --src turbo-decoder.onnx --bits 4 --block 32 --head 8
    # 4-bit encoder from the fp32 export (turbo-encoder.onnx + turbo-encoder.weights beside it)
    python turbo/quantize.py encoder out.onnx --src turbo-encoder.onnx --bits 4 --block 32

`--src` is an int8 decoder (only the head and, with --gather8, the embedding change) or an fp32 export (every
MatMul gets --bits). Weights are symmetric, round-to-nearest, accuracy_level 4 (int8 activations: KleidiAI's and
MLAS's arm64 kernels); the head's block is --head-block (32). With --gather8 the embedding Gather becomes an 8-bit
GatherBlockQuantized (block 32, zero point 128), which the quantiser can't make. Inputs, outputs and the encoder's
metadata stay those of the source, so sherpa-onnx runs the result as a drop-in; for an int8 source, one decoder
step on random inputs is compared before and after (the bare rewrite must agree to 1e-3).

Build-time only (onnx, onnxruntime 1.28.0, numpy); nothing here reaches the APK. Results:
docs/benchmarks/2026-10-turbo-faster.md.
"""
import argparse
import copy
import os
from collections import Counter

import numpy as np
import onnx
from onnx import numpy_helper
from onnxruntime.quantization.matmul_nbits_quantizer import DefaultWeightOnlyQuantConfig, MatMulNBitsQuantizer

AXES = (("MatMul", 0), ("Gather", 1))


def initializers(m: onnx.ModelProto) -> dict:
    return {t.name: t for t in m.graph.initializer}


def materialise_head(m: onnx.ModelProto) -> str:
    """Transpose(MatMul(W, Transpose(x))) -> MatMul(x, lm_head_t), lm_head_t = W^T an initializer; returns its name."""
    inits = initializers(m)
    prod = {o: n for n in m.graph.node for o in n.output}
    cons = {}
    for n in m.graph.node:
        for i in n.input:
            cons.setdefault(i, []).append(n)
    resolve = lambda name: prod[name].input[0] if name in prod and prod[name].op_type == "Identity" else name
    perm = lambda n: next(list(x.ints) for x in n.attribute if x.name == "perm")
    for mm in m.graph.node:
        if mm.op_type != "MatMul" or resolve(mm.input[0]) not in inits:
            continue
        w = numpy_helper.to_array(inits[resolve(mm.input[0])])
        if w.ndim != 2 or w.shape[0] < 50000:
            continue
        inner, (outer,) = prod[mm.input[1]], cons[mm.output[0]]
        assert inner.op_type == outer.op_type == "Transpose" and perm(inner) == perm(outer) == [0, 2, 1], (inner, outer)
        m.graph.initializer.append(numpy_helper.from_array(np.ascontiguousarray(w.T), "lm_head_t"))
        new = onnx.helper.make_node("MatMul", [inner.input[0], "lm_head_t"], [outer.output[0]], name="lm_head_matmul")
        i = list(m.graph.node).index(mm)
        for n in (mm, outer):
            m.graph.node.remove(n)
        m.graph.node.insert(i, new)
        outputs = {g.name for g in m.graph.output}
        for n in list(m.graph.node):  # the now-dead Transpose / Identity
            if n.op_type in ("Transpose", "Identity") and not any(o in outputs or any(o in x.input for x in m.graph.node)
                                                                  for o in n.output):
                m.graph.node.remove(n)
        return new.name
    raise SystemExit("no LM-head MatMul found")


def quant(m: onnx.ModelProto, bits: int, block: int, include=None, exclude=None) -> onnx.ModelProto:
    cfg = DefaultWeightOnlyQuantConfig(block_size=block, is_symmetric=True, accuracy_level=4,
                                       op_types_to_quantize=("MatMul",), quant_axes=AXES, bits=bits)
    q = MatMulNBitsQuantizer(m, bits=bits, block_size=block, is_symmetric=True, accuracy_level=4,
                             op_types_to_quantize=("MatMul",), quant_axes=AXES, algo_config=cfg,
                             nodes_to_include=include, nodes_to_exclude=exclude or [])
    q.process()
    return q.model.model


def gather8(m: onnx.ModelProto, block: int = 32) -> None:
    """Gather(token_embedding fp32, tokens) -> GatherBlockQuantized (uint8, zero point 128, fp32 scale per block
    along the hidden axis)."""
    inits = initializers(m)
    g = next(n for n in m.graph.node if n.op_type == "Gather" and n.input[0] in inits
             and numpy_helper.to_array(inits[n.input[0]]).shape[0] > 50000)
    name = g.input[0]
    w = numpy_helper.to_array(inits[name])
    vocab, dim = w.shape
    blk = w.reshape(vocab, dim // block, block)
    scale = (np.abs(blk).max(-1) / 127).astype(np.float32)
    q = (np.clip(np.round(blk / np.where(scale == 0, 1, scale)[..., None]), -127, 127) + 128).astype(np.uint8)
    m.graph.initializer.extend([numpy_helper.from_array(q.reshape(vocab, dim), name + "_q8"),
                                numpy_helper.from_array(scale, name + "_scales"),
                                numpy_helper.from_array(np.full(scale.shape, 128, np.uint8), name + "_zp")])
    new = onnx.helper.make_node("GatherBlockQuantized", [name + "_q8", g.input[1], name + "_scales", name + "_zp"],
                                list(g.output), name=g.name + "_q8", domain="com.microsoft",
                                bits=8, block_size=block, gather_axis=0, quantize_axis=1)
    i = list(m.graph.node).index(g)
    m.graph.node.remove(g)
    m.graph.node.insert(i, new)
    if not any(name in n.input for n in m.graph.node):
        m.graph.initializer.remove(inits[name])
    if not any(o.domain == "com.microsoft" for o in m.opset_import):
        m.opset_import.append(onnx.helper.make_opsetid("com.microsoft", 1))


def check(m0: onnx.ModelProto, m1: onnx.ModelProto, tol: float) -> None:
    """One decoder step (4-token prompt, zero caches, random cross-attention over 300 positions): logits compared."""
    import onnxruntime as ort

    rng = np.random.default_rng(0)
    feeds = {"tokens": np.array([[50258, 50259, 50360, 50364]], np.int64),
             "in_n_layer_self_k_cache": np.zeros((4, 1, 448, 1280), np.float32),
             "in_n_layer_self_v_cache": np.zeros((4, 1, 448, 1280), np.float32),
             "n_layer_cross_k": rng.standard_normal((4, 1, 300, 1280), np.float32),
             "n_layer_cross_v": rng.standard_normal((4, 1, 300, 1280), np.float32),
             "offset": np.array([0], np.int64)}
    so = ort.SessionOptions()
    so.intra_op_num_threads = 4
    run = lambda m: ort.InferenceSession(m.SerializeToString(), so, providers=["CPUExecutionProvider"]).run(None, feeds)[0]
    l0, l1 = run(m0), run(m1)
    diff = float(np.abs(l0 - l1).max())
    print(f"check: logits max |diff| {diff:.4g} (largest {float(np.abs(l0).max()):.3g}), "
          f"argmax agrees on {float((l0.argmax(-1) == l1.argmax(-1)).mean()):.0%}")
    assert diff <= tol, diff


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("part", choices=["encoder", "decoder"])
    ap.add_argument("out")
    ap.add_argument("--src", required=True, help="an int8 decoder, or the fp32 export (encoder: .onnx + .weights)")
    ap.add_argument("--bits", type=int, default=4, help="fp32 source: bits for every MatMul but the head")
    ap.add_argument("--block", type=int, default=32)
    ap.add_argument("--head", default=None, help="decoder LM head bits: 4, 8 or fp32 (default: --bits)")
    ap.add_argument("--head-block", type=int, default=32)
    ap.add_argument("--gather8", action="store_true", help="decoder: the token-embedding Gather 8-bit too")
    a = ap.parse_args()

    m = onnx.load(a.src)  # pulls an fp32 encoder's external .weights
    io = lambda x: ([(i.name, i.type.tensor_type.elem_type) for i in x.graph.input],
                    [(o.name, o.type.tensor_type.elem_type) for o in x.graph.output])
    io0, opsets = io(m), {o.domain: o.version for o in m.opset_import}
    meta = [(p.key, p.value) for p in m.metadata_props]
    int8_src = any(n.op_type == "MatMulInteger" for n in m.graph.node)

    if a.part == "decoder":
        before = copy.deepcopy(m) if int8_src else None
        head = materialise_head(m)
        if before is not None:
            check(before, m, 1e-3)  # the rewrite alone: exact up to summation order
        if not int8_src:
            m = quant(m, a.bits, a.block, exclude=[head])
        if (a.head or str(a.bits)) != "fp32":
            m = quant(m, int(a.head or a.bits), a.head_block, include=[head])
        if a.gather8:
            gather8(m)
        if before is not None:
            check(before, m, float("inf"))
    else:
        assert not int8_src, "the encoder needs the fp32 export"
        m = quant(m, a.bits, a.block)

    for o in m.opset_import:  # the quantiser may bump the default opset; it only adds com.microsoft ops
        if o.domain in opsets:
            o.version = opsets[o.domain]
    assert io(m) == io0, "inputs/outputs changed"
    assert [(p.key, p.value) for p in m.metadata_props] == meta, "metadata changed"
    print(Counter((n.domain or "ai.onnx", n.op_type) for n in m.graph.node
                  if n.op_type.startswith(("MatMul", "Gather"))))
    onnx.save_model(m, a.out)  # < 2 GB: one self-contained file, as the app's
    print("wrote", a.out, os.path.getsize(a.out))


if __name__ == "__main__":
    main()
