"""Convert Kotoba-Whisper v2.0 (#52), or another transformers Whisper checkpoint in `MODELS`, to the int8
sherpa-onnx Whisper files the harness loads.

    python kotoba/convert.py --work SCRATCH --out $WORK/models/kotoba-whisper-v2-0 \\
        --exporter $WORK/sherpa-onnx/scripts/whisper/export-onnx.py [--check MANIFEST]
    python kotoba/convert.py --model acft-whisper-turbo ...   # #55, see MODELS

`--model acft-whisper-turbo` (#55) is Mahmoud Ashraf's audio-context fine-tune of Whisper large-v3-turbo
(https://huggingface.co/MahmoudAshraf/acft-whisper-large-v3-turbo, Apache-2.0; FUTO's MIT method,
https://github.com/futo-org/whisper-acft): turbo's exact shape, fp16 weights, exported as `turbo`.

Kotoba-Whisper v2.0 (https://huggingface.co/kotoba-tech/kotoba-whisper-v2.0,
Apache-2.0) is a Japanese distillation of Whisper large-v3: distil-large-v3's
shape (32 encoder layers, 2 decoder layers, 128 mel bins) and large-v3's
tokenizer. It is published only as a Hugging Face transformers
`WhisperForConditionalGeneration` checkpoint (bf16 `model.safetensors`), while
sherpa-onnx's Whisper exporter reads openai-whisper checkpoints. So:

1. Download `config.json` and `model.safetensors` at the pinned revision
   (huggingface_hub, cached under $HF_HOME) and check the weights' sha256.
2. Rename every tensor to openai-whisper's name (the inverse of transformers'
   `convert_openai_to_hf.py` mapping, `HF_TO_OPENAI` below), cast to fp32 and
   save `{"dims", "model_state_dict"}` as a `.pt`. `whisper.load_model` loads it
   with strict key checking. `proj_out` is tied to the token embedding and
   isn't stored.
3. Run k2-fsa's `scripts/whisper/export-onnx.py` (sherpa-onnx 11afbd0) on it
   unchanged, as `distil-large-v3`: that name gives 128 mel bins and the
   external-data handling a >2 GB encoder needs. Its int8 encoder and decoder
   (dynamic MatMul quantization) and tokens are renamed to
   `kotoba-whisper-v2-0-*`, the harness engine id, and the encoder's
   `model_type` metadata names Kotoba (sherpa-onnx doesn't read it).

`--check MANIFEST` then decodes the first 5 clips of a FLEURS manifest
greedily with transformers (fp32, the model card's runtime) and with
openai-whisper on the converted `.pt`, and prints both: they should agree,
which checks the renaming. The int8 files are then run through the harness's
sherpa-onnx like any Whisper engine. `--keep` keeps the `.pt` and the fp32
ONNX export (`--work`/export, weights in `*.weights` files beside it) for
int8-vs-fp32 comparisons; by default both are deleted.

Build-time only: the venv (`kotoba/requirements.txt`: torch, openai-whisper,
onnx, onnxruntime, transformers for the check; no GPL or LGPL) never reaches
the APK. Force CPU (`CUDA_VISIBLE_DEVICES=`) and cap threads (`--threads`,
`OMP_NUM_THREADS`) on a shared host. A run with `--check` takes a few minutes
on 4 threads, peaks at ~12.5 GB RSS and ~9 GB of scratch.
"""

import argparse
import hashlib
import importlib.util
import json
import os
import re
import shutil
from pathlib import Path

# engine id -> (repo, revision, model.safetensors sha256, exporter model name, --check language).
# The exporter keys n_mels and external data on the name; any 128-mel "large" name would do.
MODELS = {
    "kotoba-whisper-v2-0": ("kotoba-tech/kotoba-whisper-v2.0", "7eb575277d18909a4af8a24e3ae8cce2e99794ae",
                            "0a96a84e307bbb90128a9fd5a45b4f803be212aadef633e24c088fa1ba30f727",
                            "distil-large-v3", "ja"),
    "acft-whisper-turbo": ("MahmoudAshraf/acft-whisper-large-v3-turbo", "6f95623c4821f6fec36f280ebc7946cc6730854c",
                           "5660919b2af8ad83974c24c7efcd229b757ddfcba0a5786e267c000fb6e51e59", "turbo", "en"),
}
# For checkpoints published without a generation_config.json (--check only).
TURBO_GENERATION_CONFIG = ("openai/whisper-large-v3-turbo", "41f01f3fe87f28c78e2fbf8b568835947dd65ed9")
ENGINE_ID = "kotoba-whisper-v2-0"
REPO, REVISION, WEIGHTS_SHA256, EXPORT_AS, CHECK_LANGUAGE = MODELS[ENGINE_ID]

# Inverse of transformers src/transformers/models/whisper/convert_openai_to_hf.py
# WHISPER_MAPPING (Apache-2.0), applied in order after dropping the "model." prefix.
HF_TO_OPENAI = {
    r"\.layers\.": ".blocks.",
    r"\.self_attn\.k_proj\.": ".attn.key.",
    r"\.self_attn\.q_proj\.": ".attn.query.",
    r"\.self_attn\.v_proj\.": ".attn.value.",
    r"\.self_attn\.out_proj\.": ".attn.out.",
    r"\.self_attn_layer_norm\.": ".attn_ln.",
    r"\.encoder_attn\.k_proj\.": ".cross_attn.key.",
    r"\.encoder_attn\.q_proj\.": ".cross_attn.query.",
    r"\.encoder_attn\.v_proj\.": ".cross_attn.value.",
    r"\.encoder_attn\.out_proj\.": ".cross_attn.out.",
    r"\.encoder_attn_layer_norm\.": ".cross_attn_ln.",
    r"\.fc1\.": ".mlp.0.",
    r"\.fc2\.": ".mlp.2.",
    r"\.final_layer_norm\.": ".mlp_ln.",
    r"^encoder\.layer_norm\.": "encoder.ln_post.",
    r"^decoder\.layer_norm\.": "decoder.ln.",
    r"\.embed_positions\.weight$": ".positional_embedding",
    r"\.embed_tokens\.": ".token_embedding.",
}


def hf_to_openai(key: str) -> str:
    key = key.removeprefix("model.")
    for pattern, replacement in HF_TO_OPENAI.items():
        key = re.sub(pattern, replacement, key)
    return key


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def fetch(name: str) -> Path:
    from huggingface_hub import hf_hub_download

    return Path(hf_hub_download(REPO, name, revision=REVISION))


def convert(config: dict, weights: Path, pt: Path) -> None:
    import torch
    from safetensors.torch import load_file

    dims = {
        "n_mels": config["num_mel_bins"],
        "n_audio_ctx": config["max_source_positions"],
        "n_audio_state": config["d_model"],
        "n_audio_head": config["encoder_attention_heads"],
        "n_audio_layer": config["encoder_layers"],
        "n_vocab": config["vocab_size"],
        "n_text_ctx": config["max_target_positions"],
        "n_text_state": config["d_model"],
        "n_text_head": config["decoder_attention_heads"],
        "n_text_layer": config["decoder_layers"],
    }
    state = {}
    for key, tensor in load_file(str(weights)).items():
        if key == "proj_out.weight":  # tied to decoder.token_embedding
            continue
        name = hf_to_openai(key)
        if name in state:
            raise SystemExit(f"two tensors map to {name}")
        state[name] = tensor.to(torch.float32)
    torch.save({"dims": dims, "model_state_dict": state}, pt)
    print(f"dims {dims}\n{len(state)} tensors -> {pt}", flush=True)


def export(pt: Path, exporter: Path, export_dir: Path) -> None:
    """Run k2-fsa's export-onnx.py on `pt` in `export_dir`, with only its loader and args swapped."""
    import torch
    import whisper
    from whisper.model import disable_sdpa

    spec = importlib.util.spec_from_file_location("sherpa_whisper_export", exporter)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    model = whisper.load_model(str(pt), device="cpu")  # strict load_state_dict
    mod.load_model = lambda name: model
    mod.get_args = lambda: argparse.Namespace(model=EXPORT_AS)
    # The script is written for the TorchScript exporter (dynamic_axes, in-place cache writes);
    # torch >= 2.9 defaults to dynamo.
    onnx_export = torch.onnx.export
    torch.onnx.export = lambda *a, **kw: onnx_export(*a, **kw, dynamo=False)
    cwd = Path.cwd()
    os.chdir(export_dir)
    try:
        with disable_sdpa():
            mod.main()
    finally:
        os.chdir(cwd)
        torch.onnx.export = onnx_export


def finish(export_dir: Path, out: Path) -> list[Path]:
    import onnx
    from onnx.external_data_helper import uses_external_data

    files = []
    for src, role in ((f"{EXPORT_AS}-encoder.int8.onnx", "encoder.int8.onnx"),
                      (f"{EXPORT_AS}-decoder.int8.onnx", "decoder.int8.onnx"),
                      (f"{EXPORT_AS}-tokens.txt", "tokens.txt")):
        dest = out / f"{ENGINE_ID}-{role}"
        if src.endswith(".onnx"):
            raw = onnx.load(str(export_dir / src), load_external_data=False)
            if any(uses_external_data(t) for t in raw.graph.initializer):
                print(f"note: the exporter's {src} keeps its weights in external data; inlining", flush=True)
            m = onnx.load(str(export_dir / src))
            if role.startswith("encoder"):
                meta = {p.key: p.value for p in m.metadata_props}
                # The exporter lists the languages in set order, which changes with Python's hash seed;
                # sort the (token, code) pairs so the file's sha256 is reproducible.
                pairs = sorted(zip(map(int, meta["all_language_tokens"].split(",")),
                                   meta["all_language_codes"].split(",")))
                meta["all_language_tokens"] = ",".join(str(t) for t, _ in pairs)
                meta["all_language_codes"] = ",".join(c for _, c in pairs)
                meta.update({"model_type": f"whisper-{ENGINE_ID}", "maintainer": "marmalade-stt-android",
                             "url": f"https://huggingface.co/{REPO}/tree/{REVISION}",
                             "comment": "Converted by marmalade-stt-android tools/asr-bench/kotoba/convert.py "
                                        "with sherpa-onnx scripts/whisper/export-onnx.py"})
                del m.metadata_props[:]
                for k, v in meta.items():
                    m.metadata_props.add(key=k, value=str(v))
            onnx.save(m, str(dest))  # one self-contained file, like k2-fsa's turbo int8
            if any(uses_external_data(t) for t in onnx.load(str(dest), load_external_data=False).graph.initializer):
                raise SystemExit(f"{dest} references external data")
        else:
            shutil.copy(export_dir / src, dest)
        files.append(dest)
    return files


def check(pt: Path, manifest: Path, n: int = 5) -> None:
    """Greedy text from transformers (fp32) and from openai-whisper on the converted .pt."""
    import miniaudio
    import numpy as np
    import torch
    import whisper
    from transformers import WhisperForConditionalGeneration, WhisperProcessor

    rows = [json.loads(line) for line in manifest.read_text().splitlines()[:n]]
    base = manifest.parents[2] / "harness"  # audio paths are relative to a harness dir under $WORK
    from huggingface_hub.errors import EntryNotFoundError
    from transformers import GenerationConfig

    hf_dir = fetch("config.json").parent
    missing = []
    for name in ("generation_config.json", "preprocessor_config.json", "tokenizer.json", "tokenizer_config.json",
                 "vocab.json", "merges.txt", "added_tokens.json", "special_tokens_map.json", "normalizer.json"):
        try:
            fetch(name)
        except EntryNotFoundError:  # the ACFT repo has no tokenizer.json or generation_config.json
            missing.append(name)
    proc = WhisperProcessor.from_pretrained(hf_dir)
    hf = WhisperForConditionalGeneration.from_pretrained(hf_dir, dtype=torch.float32).eval()
    if "generation_config.json" in missing:
        # generate(language=...) needs Whisper's language/task token tables: the base model's, same tokenizer.
        hf.generation_config = GenerationConfig.from_pretrained(TURBO_GENERATION_CONFIG[0], revision=TURBO_GENERATION_CONFIG[1])
    ow = whisper.load_model(str(pt), device="cpu")
    opts = whisper.DecodingOptions(language=CHECK_LANGUAGE, task="transcribe", temperature=0.0,
                                   without_timestamps=True, fp16=False)
    same = 0
    for r in rows:
        audio = np.asarray(miniaudio.decode_file(str((base / r["audio"]).resolve()), nchannels=1,
                                                 sample_rate=16000,
                                                 output_format=miniaudio.SampleFormat.FLOAT32).samples, np.float32)
        feats = proc(audio, sampling_rate=16000, return_tensors="pt").input_features
        with torch.no_grad():
            ids = hf.generate(feats, language=CHECK_LANGUAGE, task="transcribe", return_timestamps=False)
        a = proc.batch_decode(ids, skip_special_tokens=True)[0].strip()
        mel = whisper.log_mel_spectrogram(whisper.pad_or_trim(torch.from_numpy(audio)), n_mels=ow.dims.n_mels)
        b = whisper.decode(ow, mel, opts).text.strip()
        same += a == b
        print(f"{r['id']}\n  ref     {r['text']}\n  hf      {a}\n  openai  {b}\n  {'same' if a == b else 'DIFFERS'}",
              flush=True)
    print(f"check: {same}/{len(rows)} identical")


def main() -> None:
    global ENGINE_ID, REPO, REVISION, WEIGHTS_SHA256, EXPORT_AS, CHECK_LANGUAGE
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", choices=list(MODELS), default=ENGINE_ID)
    ap.add_argument("--work", type=Path, required=True, help="scratch dir (.pt and fp32 ONNX, ~10 GB)")
    ap.add_argument("--out", type=Path, required=True, help="harness model dir, e.g. $WORK/models/" + ENGINE_ID)
    ap.add_argument("--exporter", type=Path, required=True, help="sherpa-onnx scripts/whisper/export-onnx.py")
    ap.add_argument("--check", type=Path, help="FLEURS manifest.jsonl: compare transformers and openai-whisper")
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--keep", action="store_true", help="keep the .pt and the fp32 export")
    args = ap.parse_args()
    ENGINE_ID = args.model
    REPO, REVISION, WEIGHTS_SHA256, EXPORT_AS, CHECK_LANGUAGE = MODELS[ENGINE_ID]

    import torch

    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    torch.set_grad_enabled(False)
    work, out = args.work.resolve(), args.out.resolve()
    export_dir = work / "export"
    shutil.rmtree(export_dir, ignore_errors=True)
    for d in (work, out, export_dir):
        d.mkdir(parents=True, exist_ok=True)

    config = json.loads(fetch("config.json").read_text())
    weights = fetch("model.safetensors")
    if (got := sha256(weights)) != WEIGHTS_SHA256:
        raise SystemExit(f"sha256 mismatch for model.safetensors: {got}")
    pt = work / f"{ENGINE_ID}.pt"
    convert(config, weights, pt)
    export(pt, args.exporter.resolve(), export_dir)
    files = finish(export_dir, out)
    sums = [f"{sha256(p)}  {p.name}  {p.stat().st_size}" for p in files]
    (out / "SHA256SUMS").write_text("\n".join(sums) + "\n")
    print("\n".join(sums), flush=True)
    if args.check:
        check(pt, args.check.resolve())
    if not args.keep:
        shutil.rmtree(export_dir)
        pt.unlink()


if __name__ == "__main__":
    main()
