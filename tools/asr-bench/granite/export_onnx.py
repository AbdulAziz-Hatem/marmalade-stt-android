"""Export IBM's Granite Speech 5.0 470M TurboCTC to ONNX (fp32 + int8) and check it (#26).

    $WORK/venv-granite-export/bin/python granite/export_onnx.py \\
        --src $WORK/models/granite-speech-5-470m-src --work $WORK

Build-time only; nothing here ships. Input: the Apache-2.0 checkpoint from
huggingface.co/ibm-granite/granite-speech-5.0-470m-turboctc at the pinned
REVISION (download it into --src; the sha256 of model.safetensors is checked).
The model is built with transformers' own GraniteSpeech5ForCTC (the
implementation the model card uses), in fp32, with three rewrites for export
only. Each attention layer always pads to whole 128-frame blocks and masks the
padded keys: transformers pads only when the length isn't a multiple of 128,
a Python branch a trace would freeze, and padding by zero frames is the same
computation, so the graph is exact for every length. The two subsampling
blocks' pairwise mean pool (Tensor.unfold, which ONNX lacks) becomes a
reshape. The relative-position bias is a MatMul, not an Einsum (see below).

Writes to $WORK/models/:
  granite-speech-5-470m-ctc-fp32/model.onnx (one 1.9 GB file) and tokens.txt
  granite-speech-5-470m-ctc/model.int8.onnx and tokens.txt
    (onnxruntime quantize_dynamic: MatMul weights int8, per tensor)

Then checks, on --check-n clips of each of ls-other and ami-ihm:
  - asrbench.granite.features against transformers' feature extractor
  - ONNX fp32 logits against PyTorch's, and the decoded text against
    model.generate() + processor.batch_decode (the model card's path)
  - int8 text against fp32 text
and prints a summary; exits 1 if the fp32 text differs from PyTorch's.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path

import numpy as np
import torch
from torch import nn

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from asrbench import granite as G  # noqa: E402

REPO = "ibm-granite/granite-speech-5.0-470m-turboctc"
REVISION = "286456107c8ba1161f5c22dfe85466402c88333b"
SAFETENSORS_SHA256 = "8b98a8c34fd5fcb081caef719638eded31bb6d197d62053eefc5c1703aaf1ad4"
FP32_ID, INT8_ID = "granite-speech-5-470m-ctc-fp32", "granite-speech-5-470m-ctc"


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def always_pad_attention(self, hidden_states, position_embeddings, attention_mask=None, **kwargs):
    """GraniteSpeech5EncoderAttention.forward for batch 1 without a mask, padding unconditionally."""
    batch_size, seq_length, _ = hidden_states.shape
    c, h, d = self.context_size, self.config.num_attention_heads, self.head_dim
    num_padded = (c - seq_length % c) % c
    hidden_states = nn.functional.pad(hidden_states, (0, 0, 0, num_padded))
    padded_length = hidden_states.shape[1]
    key_valid = torch.arange(padded_length, device=hidden_states.device) < seq_length
    blocks = batch_size * (padded_length // c)
    q = self.q_proj(hidden_states).reshape(blocks, c, h, d).transpose(1, 2)
    k = self.k_proj(hidden_states).reshape(blocks, c, h, d).transpose(1, 2)
    v = self.v_proj(hidden_states).reshape(blocks, c, h, d).transpose(1, 2)
    rel = self.rel_pos_emb(position_embeddings) * self.scaling  # [c, c, d]
    # Shaw bias[g, h, i, j] = q[g, h, i] . rel[i, j], as a batched matmul over i like
    # transformers. An Einsum exports fine, but ORT's CPU Einsum made the whole model 20-35 % slower.
    bias = torch.matmul(q.permute(2, 0, 1, 3).reshape(c, blocks * h, d), rel.transpose(1, 2))
    bias = bias.reshape(c, blocks, h, c).permute(1, 2, 0, 3)
    bias = bias.masked_fill(~key_valid.reshape(blocks, 1, 1, c), torch.finfo(bias.dtype).min)
    weights = torch.softmax(torch.matmul(q, k.transpose(2, 3)) * self.scaling + bias, dim=-1)
    out = torch.matmul(weights, v).transpose(1, 2).reshape(batch_size, padded_length, h * d)
    return self.o_proj(out[:, :seq_length]), None


def reshape_pool_subsampling(self, hidden_states, attention_mask=None, position_embeddings=None, **kwargs):
    """GraniteSpeech5EncoderSubsamplingBlock.forward with the pairwise mean pool as a reshape (ONNX has no Unfold)."""
    residual = hidden_states
    hidden_states = residual + 0.5 * self.feed_forward1(self.norm_feed_forward1(hidden_states))
    attn_output, _ = self.self_attn(hidden_states=self.norm_self_att(hidden_states),
                                    attention_mask=attention_mask, position_embeddings=position_embeddings)
    hidden_states = hidden_states + attn_output
    conv_output = self.conv(self.norm_conv(hidden_states), attention_mask=attention_mask)
    half = hidden_states.shape[1] // 2
    pooled = hidden_states[:, : 2 * half].reshape(hidden_states.shape[0], half, 2, hidden_states.shape[2]).mean(2)
    hidden_states = pooled + conv_output[:, :half]
    hidden_states = hidden_states + 0.5 * self.feed_forward2(self.norm_feed_forward2(hidden_states))
    return self.norm_out(hidden_states)


class Logits(nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, features):
        return self.model(input_features=features).logits


def load_model(src: Path):
    from transformers import GraniteSpeech5ForCTC
    from transformers.models.granite_speech5 import modeling_granite_speech5 as m

    model = GraniteSpeech5ForCTC.from_pretrained(src, dtype=torch.float32, attn_implementation="eager").eval()
    reference = GraniteSpeech5ForCTC.from_pretrained(src, dtype=torch.float32, attn_implementation="eager").eval()
    for layer in model.encoder.layers:
        # Bound per instance so `reference` keeps transformers' own forwards.
        layer.self_attn.forward = always_pad_attention.__get__(layer.self_attn, m.GraniteSpeech5EncoderAttention)
        if isinstance(layer, m.GraniteSpeech5EncoderSubsamplingBlock):
            layer.forward = reshape_pool_subsampling.__get__(layer, m.GraniteSpeech5EncoderSubsamplingBlock)
    return model, reference


def write_tokens(src: Path, out: Path) -> None:
    vocab = json.loads((src / "tokenizer.json").read_text())["model"]["vocab"]
    assert sorted(vocab.values()) == list(range(len(vocab))), "vocab ids aren't dense"
    assert not any(" " in p or "\n" in p for p in vocab), "a piece contains whitespace"
    out.write_text("".join(f"{p} {i}\n" for p, i in sorted(vocab.items(), key=lambda kv: kv[1])), encoding="utf-8")


def export(model, fp32_dir: Path, int8_dir: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    fp32_dir.mkdir(parents=True, exist_ok=True)
    int8_dir.mkdir(parents=True, exist_ok=True)
    fp32 = fp32_dir / "model.onnx"
    dummy = torch.from_numpy(G.features(np.random.default_rng(26).standard_normal(16_000 * 7).astype(np.float32) * 0.1))[None]
    with torch.no_grad():
        torch.onnx.export(
            Logits(model), (dummy,), str(fp32), dynamo=False, opset_version=17,
            input_names=["features"], output_names=["logits"],
            dynamic_axes={"features": {1: "frames"}, "logits": {1: "out_frames"}},
            external_data=True,
        )
    quantize_dynamic(str(fp32), str(int8_dir / "model.int8.onnx"), op_types_to_quantize=["MatMul"],
                     weight_type=QuantType.QInt8)


def check(args, model, reference, fp32_dir: Path, int8_dir: Path) -> int:
    import onnxruntime as ort
    from transformers import AutoProcessor

    from asrbench.run import load_audio

    processor = AutoProcessor.from_pretrained(args.src)
    sess = {k: ort.InferenceSession(str(p), providers=["CPUExecutionProvider"])
            for k, p in (("fp32", fp32_dir / "model.onnx"), ("int8", int8_dir / "model.int8.onnx"))}
    vocab = G.ByteLevelVocab.load(fp32_dir / "tokens.txt")
    clips = []
    for s in ("ls-other", "ami-ihm"):
        manifest = [json.loads(line) for line in (args.work / "data" / s / "manifest.jsonl").read_text().splitlines()]
        clips += [(s, u["id"], load_audio(u["audio"])) for u in manifest[: args.check_n]]
    # Edge lengths: under one attention block, exactly one, odd stacked-frame counts, tiny.
    rng = np.random.default_rng(1)
    base = clips[0][2]
    for n in (1_280, 1_600, 2_560 * 8 + 160, 128 * 4 * 320, 128 * 4 * 320 + 320, 16_000 * 29):
        clips.append(("edge", f"len{n}", np.resize(base, n) + rng.standard_normal(n).astype(np.float32) * 1e-4))

    worst_feat = worst_logit = 0.0
    fp32_vs_torch = int8_vs_fp32 = argmax_frames = argmax_same = 0
    for s, uid, audio in clips:
        ref_feats = processor(audio, sampling_rate=G.SAMPLE_RATE, return_tensors="pt")
        ours = G.features(audio)
        worst_feat = max(worst_feat, float(np.abs(ours - ref_feats["input_features"][0].numpy()).max()))
        with torch.no_grad():
            ref_logits = reference(input_features=ref_feats["input_features"]).logits[0].numpy()
            ids = reference.generate(input_features=ref_feats["input_features"])
        ref_text = processor.batch_decode(ids, skip_special_tokens=True)[0].strip()
        out = {k: v.run(["logits"], {"features": ours[None]})[0][0] for k, v in sess.items()}
        worst_logit = max(worst_logit, float(np.abs(out["fp32"] - ref_logits).max()))
        argmax_frames += len(ref_logits)
        argmax_same += int((out["fp32"].argmax(-1) == ref_logits.argmax(-1)).sum())
        text = {k: vocab.decode(G.ctc_greedy(v)) for k, v in out.items()}
        if text["fp32"] != ref_text:
            fp32_vs_torch += 1
            print(f"fp32 != torch  {s}/{uid}\n  torch: {ref_text}\n  onnx:  {text['fp32']}")
        if text["int8"] != text["fp32"]:
            int8_vs_fp32 += 1
            print(f"int8 != fp32  {s}/{uid}\n  fp32: {text['fp32']}\n  int8: {text['int8']}")
    print(json.dumps({
        "clips": len(clips), "max_feature_abs_diff": worst_feat, "max_fp32_logit_abs_diff": worst_logit,
        "fp32_argmax_agreement": argmax_same / argmax_frames,
        "fp32_text_differs_from_torch": fp32_vs_torch, "int8_text_differs_from_fp32": int8_vs_fp32,
    }, indent=1))
    return 1 if fp32_vs_torch else 0


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src", type=Path, required=True, help=f"snapshot of {REPO} at {REVISION}")
    ap.add_argument("--work", type=Path, required=True)
    ap.add_argument("--check-n", type=int, default=20)
    ap.add_argument("--skip-export", action="store_true")
    ap.add_argument("--threads", type=int, default=6)
    args = ap.parse_args()
    torch.set_num_threads(args.threads)

    got = sha256(args.src / "model.safetensors")
    if got != SAFETENSORS_SHA256:
        raise SystemExit(f"model.safetensors sha256 {got}, expected {SAFETENSORS_SHA256} ({REPO}@{REVISION})")
    fp32_dir, int8_dir = args.work / "models" / FP32_ID, args.work / "models" / INT8_ID
    model, reference = load_model(args.src)
    if not args.skip_export:
        export(model, fp32_dir, int8_dir)
        for d in (fp32_dir, int8_dir):
            write_tokens(args.src, d / "tokens.txt")
        for p in sorted([*fp32_dir.iterdir(), *int8_dir.iterdir()]):
            print(f"{p.relative_to(args.work)}  {p.stat().st_size:_}  {sha256(p)}")
    sys.exit(check(args, model, reference, fp32_dir, int8_dir))


if __name__ == "__main__":
    main()
