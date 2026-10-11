"""The pretrained punctuators format_spike.py compares (#33), behind its `fmt(text) -> str` interface.

PunctCapSeg   1-800-BAD-CODE/punctuation_fullstop_truecase_english (formerly punct_cap_seg_en),
              the punctuator IBM's Granite WebGPU demo runs. Decoded by the author's own
              `punctuators` package (Apache-2.0). DIR holds punct_cap_seg_en.onnx (or an int8
              copy from `quantize`), spe_32k_lc_en.model and config.yaml at revision
              b26fd1c40e88678859048898218ea4edcc24c84a.
SherpaPunct   sherpa-onnx-online-punct-en-2024-08-06 (CNN-BiLSTM, Edge-Punct-Casing) through the
              harness's from-source sherpa-onnx module. DIR holds model.int8.onnx and bpe.vocab
              from the release tarball (sha256 9f5e5a72...).

    python granite/format_candidates.py quantize DIR   # DIR/punct_cap_seg_en.onnx -> DIR/int8/
"""

import shutil
import sys
from pathlib import Path


class PunctCapSeg:
    def __init__(self, d: Path, threads: int):
        import onnxruntime as ort
        from punctuators.models.punc_cap_seg_model import PunctCapSegConfigONNX, PunctCapSegModelONNX

        cfg = PunctCapSegConfigONNX(directory=str(d), spe_filename="spe_32k_lc_en.model",
                                    model_filename="punct_cap_seg_en.onnx")
        self.model = PunctCapSegModelONNX(cfg, ort_providers=["CPUExecutionProvider"])
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = threads
        opts.inter_op_num_threads = 1
        onnx = d / "punct_cap_seg_en.onnx"
        self.model._ort_session = ort.InferenceSession(str(onnx), opts, providers=["CPUExecutionProvider"])
        self.size = onnx.stat().st_size + (d / "spe_32k_lc_en.model").stat().st_size

    def __call__(self, text: str) -> str:
        if not text.strip():
            return text
        return self.model.infer([text], apply_sbd=False)[0]


class SherpaPunct:
    def __init__(self, d: Path, threads: int):
        sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
        from asrbench.backends import native

        s = native()
        cfg = s.OnlinePunctuationConfig(model_config=s.OnlinePunctuationModelConfig(
            cnn_bilstm=str(d / "model.int8.onnx"), bpe_vocab=str(d / "bpe.vocab"), num_threads=threads))
        self.punct = s.OnlinePunctuation(cfg)
        self.size = (d / "model.int8.onnx").stat().st_size + (d / "bpe.vocab").stat().st_size

    def __call__(self, text: str) -> str:
        return self.punct.add_punctuation_with_case(text) if text.strip() else text


def quantize(d: Path) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    out = d / "int8"
    out.mkdir(exist_ok=True)
    quantize_dynamic(str(d / "punct_cap_seg_en.onnx"), str(out / "punct_cap_seg_en.onnx"), weight_type=QuantType.QInt8)
    for f in ("spe_32k_lc_en.model", "config.yaml"):
        shutil.copy(d / f, out / f)


if __name__ == "__main__" and sys.argv[1:2] == ["quantize"]:
    quantize(Path(sys.argv[2]))
