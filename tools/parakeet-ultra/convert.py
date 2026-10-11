"""Convert Moondream's Parakeet Ultra to the int8 sherpa-onnx files the app loads (#25).

    python convert.py --work DIR

Parakeet Ultra (https://huggingface.co/moondream/parakeet-ultra, CC-BY-4.0) is
a post-trained parakeet-tdt-0.6b-v3: same architecture, same tokenizer. It is
published only as a Hugging Face transformers `ParakeetForTDT` checkpoint
(`model.safetensors`, fp16 matrices), and its documented runtime is Moondream's
closed Photon. This rebuilds it with open tools only:

1. Load NVIDIA's parakeet-tdt-0.6b-v3 `.nemo` in NeMo (Apache-2.0). It gives
   the model graph, the preprocessor settings and the tokenizer, which Ultra
   shares.
2. Replace every learned weight with Ultra's. The key names are the inverse of
   transformers' `convert_nemo_to_hf.py` mapping (copied below), a pure 1:1
   rename. The mapping is checked strictly: every weight the model's forward
   pass uses must come from Ultra, and every Ultra tensor must land somewhere,
   except the `vad_head.*` Photon uses to cut long audio, which the NeMo graph
   has no place for (the app uses Silero instead).
3. Export encoder, decoder and joiner to ONNX with NeMo's own `.export()`, add
   the metadata sherpa-onnx reads, and quantize to int8 with ONNX Runtime the
   way k2-fsa's export of v3 does (sherpa-onnx
   `scripts/nemo/parakeet-tdt-0.6b-v3/export_onnx.py`): the encoder to QUInt8,
   decoder and joiner to QInt8.

Inputs are pinned by revision and checked by sha256 (`fetch()`), so a rerun
with the versions in `setup.sh` rebuilds the same model. The ONNX bytes can
still differ between tool versions; `SHA256SUMS` in the output directory
records what this run produced.
"""

import argparse
import hashlib
import json
import re
import shutil
import urllib.request
from pathlib import Path

ULTRA_REPO = "moondream/parakeet-ultra"
ULTRA_REVISION = "73175eb7aeb0d82f1e2a6b53b3aabc10a90bcd0b"
ULTRA_SHA256 = "c9608f36d0ab956c14bfcc525479b0746b3b42a56f6949ec85c14eb7466717dc"
V3_REPO = "nvidia/parakeet-tdt-0.6b-v3"
V3_REVISION = "541d1f99c6b0c3cd0b11a95167540bb8edefd82b"
V3_NEMO_SHA256 = "3cbdc85877e668ca7b82d0d56770eb1fac76691f55d6b97545e8d61ca588d10d"

# transformers src/transformers/models/parakeet/convert_nemo_to_hf.py (Apache-2.0):
# NEMO_TO_HF_WEIGHT_MAPPING followed by NEMO_TDT_WEIGHT_MAPPING, applied in order.
NEMO_TO_HF = {
    r"encoder\.pre_encode\.conv\.": r"encoder.subsampling.layers.",
    r"encoder\.pre_encode\.out\.": r"encoder.subsampling.linear.",
    r"encoder\.pos_enc\.": r"encoder.encode_positions.",
    r"encoder\.layers\.(\d+)\.conv\.batch_norm\.": r"encoder.layers.\1.conv.norm.",
    r"decoder\.decoder_layers\.0\.(weight|bias)": r"ctc_head.\1",
    r"linear_([kv])": r"\1_proj",
    r"linear_out": r"o_proj",
    r"linear_q": r"q_proj",
    r"pos_bias_([uv])": r"bias_\1",
    r"linear_pos": r"relative_k_proj",
    r"decoder\.prediction\.embed\.": r"decoder.embedding.",
    r"decoder\.prediction\.dec_rnn\.lstm\.": r"decoder.lstm.",
    r"joint\.enc\.": r"encoder_projector.",
    r"joint\.pred\.": r"decoder.decoder_projector.",
    r"joint\.joint_net\.2\.": r"joint.head.",
}

# In Ultra's checkpoint but not part of the transducer: Photon's voice-activity head.
ULTRA_UNUSED = re.compile(r"^vad_head\.")


def nemo_to_hf(key: str) -> str:
    for pattern, replacement in NEMO_TO_HF.items():
        key = re.sub(pattern, replacement, key)
    return key


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(repo: str, revision: str, name: str, expected: str, dest_dir: Path) -> Path:
    dest = dest_dir / name
    if not (dest.is_file() and sha256(dest) == expected):
        url = f"https://huggingface.co/{repo}/resolve/{revision}/{name}"
        print(f"fetch {url}", flush=True)
        part = dest.with_name(name + ".part")
        with urllib.request.urlopen(url) as r, part.open("wb") as w:
            shutil.copyfileobj(r, w, 1 << 20)
        got = sha256(part)
        if got != expected:
            part.unlink()
            raise SystemExit(f"sha256 mismatch for {name}: {got}")
        part.rename(dest)
    return dest


def load_ultra_into(model, ultra_path: Path) -> dict:
    """Copy Ultra's weights into the NeMo v3 `model` in place. Returns a report."""
    from safetensors.torch import load_file

    ultra = load_file(str(ultra_path))
    state = model.state_dict()
    # Parameters are what the forward pass learns; buffers are fixed (mel filterbank,
    # window, positional table) or running statistics (batch norm), which Ultra has too.
    learned = {n for n, _ in model.named_parameters()}
    replaced, kept, differs, used = [], [], [], set()
    for key, tensor in state.items():
        hf = nemo_to_hf(key)
        if hf in ultra:
            src = ultra[hf]
            if tuple(src.shape) != tuple(tensor.shape):
                raise SystemExit(f"shape mismatch {key} <- {hf}: {tuple(tensor.shape)} vs {tuple(src.shape)}")
            new = src.to(tensor.dtype)
            if not new.equal(tensor):
                differs.append(key)
            state[key] = new
            replaced.append(key)
            used.add(hf)
        elif key in learned:
            raise SystemExit(f"no Ultra tensor for learned weight {key} (looked for {hf})")
        else:
            kept.append(key)
    unused = sorted(k for k in ultra if k not in used)
    unexpected = [k for k in unused if not ULTRA_UNUSED.match(k)]
    if unexpected:
        raise SystemExit(f"Ultra tensors with no place in the NeMo model: {unexpected}")
    model.load_state_dict(state, strict=True)
    return {"replaced": len(replaced), "learned_replaced": sum(1 for k in replaced if k in learned),
            "learned_total": len(learned), "differ_from_v3": len(differs),
            "kept_buffers": kept, "ultra_unused": unused}


def add_meta_data(src: Path, dest: Path, meta: dict, external: bool) -> None:
    """Save `src` as `dest` with sherpa-onnx's metadata; `external` puts the weights in <stem>.weights."""
    import onnx

    model = onnx.load(str(src))
    while len(model.metadata_props):
        model.metadata_props.pop()
    for key, value in meta.items():
        p = model.metadata_props.add()
        p.key, p.value = key, str(value)
    if external:
        onnx.save(model, str(dest), save_as_external_data=True, all_tensors_to_one_file=True,
                  location=dest.stem + ".weights")
    else:
        onnx.save(model, str(dest))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--work", type=Path, required=True, help="scratch directory (inputs, ~8 GB of outputs)")
    args = ap.parse_args()
    work = args.work.resolve()
    # out/: the int8 files the app loads. out/fp32/: the full-precision export, for
    # comparisons. export/: NeMo's raw export (the >2 GB encoder as one file per tensor).
    inputs, out, fp32, export = work / "inputs", work / "out", work / "out" / "fp32", work / "export"
    shutil.rmtree(export, ignore_errors=True)
    for d in (inputs, out, fp32, export):
        d.mkdir(parents=True, exist_ok=True)

    ultra_path = fetch(ULTRA_REPO, ULTRA_REVISION, "model.safetensors", ULTRA_SHA256, inputs)
    v3_path = fetch(V3_REPO, V3_REVISION, "parakeet-tdt-0.6b-v3.nemo", V3_NEMO_SHA256, inputs)

    import nemo.collections.asr as nemo_asr
    import torch
    from onnxruntime.quantization import QuantType, quantize_dynamic

    torch.set_grad_enabled(False)
    model = nemo_asr.models.ASRModel.restore_from(restore_path=str(v3_path), map_location="cpu")
    model.eval()
    report = load_ultra_into(model, ultra_path)
    print(json.dumps(report, indent=1), flush=True)
    (out / "conversion-report.json").write_text(json.dumps(report, indent=1))

    with (out / "tokens.txt").open("w", encoding="utf-8") as f:
        for i, s in enumerate(model.joint.vocabulary):
            f.write(f"{s} {i}\n")
        f.write(f"<blk> {i + 1}\n")

    model.encoder.export(str(export / "encoder.onnx"))
    model.decoder.export(str(export / "decoder.onnx"))
    model.joint.export(str(export / "joiner.onnx"))

    normalize = model.cfg.preprocessor.normalize
    meta = {
        "vocab_size": model.decoder.vocab_size,
        "normalize_type": "" if normalize == "NA" else normalize,
        "pred_rnn_layers": model.decoder.pred_rnn_layers,
        "pred_hidden": model.decoder.pred_hidden,
        "subsampling_factor": 8,
        "model_type": "EncDecRNNTBPEModel",
        "version": "2",
        "model_author": "Moondream (post-trained from NVIDIA NeMo parakeet-tdt-0.6b-v3)",
        # sherpa-onnx treats a NeMo transducer as TDT only when "tdt" is in this URL
        # (offline-transducer-nemo-model.cc), so it names the base model; `source` names Ultra.
        "url": f"https://huggingface.co/{V3_REPO}",
        "source": f"https://huggingface.co/{ULTRA_REPO}/tree/{ULTRA_REVISION}",
        "comment": "Only the transducer branch is exported. Converted by marmalade-stt-android "
                   "tools/parakeet-ultra/convert.py",
        "feat_dim": 128,
    }
    for m in ("encoder", "decoder", "joiner"):
        quantize_dynamic(model_input=str(export / f"{m}.onnx"), model_output=str(out / f"{m}.int8.onnx"),
                         weight_type=QuantType.QUInt8 if m == "encoder" else QuantType.QInt8)
    add_meta_data(out / "encoder.int8.onnx", out / "encoder.int8.onnx", meta, external=False)
    add_meta_data(export / "encoder.onnx", fp32 / "encoder.onnx", meta, external=True)
    for name in ("decoder.onnx", "joiner.onnx"):
        shutil.copy(export / name, fp32 / name)
    shutil.copy(out / "tokens.txt", fp32 / "tokens.txt")
    shutil.rmtree(export)

    files = sorted(p for p in out.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
    sums = [f"{sha256(p)}  {p.relative_to(out)}  {p.stat().st_size}" for p in files]
    (out / "SHA256SUMS").write_text("\n".join(sums) + "\n")
    print("\n".join(sums))


if __name__ == "__main__":
    main()
