#!/bin/sh
# Python environment for convert.py: NeMo 2.7.3 and PyTorch 2.11.0, in $VENV
# (default ./venv-nemo). Needs uv (https://docs.astral.sh/uv/). About 6 GB: the
# NeMo step replaces the CPU-only torch wheel with PyPI's CUDA build (as run for
# #25). The conversion runs on the CPU either way.
#
# Licenses: NeMo, PyTorch, ONNX and ONNX Runtime are Apache-2.0 / BSD / MIT.
# NeMo's ASR extra also pulls in soxr (LGPL-2.1, through librosa) and
# text-unidecode (Artistic or GPLv2+). They run on the build machine only, at
# arm's length; nothing from this environment is committed or shipped, and the
# output is model weights.
set -eu
VENV="${VENV:-venv-nemo}"
uv venv -p 3.12 "$VENV"
uv pip install -p "$VENV/bin/python" --index-url https://download.pytorch.org/whl/cpu \
  --extra-index-url https://pypi.org/simple "torch==2.11.0" "torchaudio==2.11.0"
uv pip install -p "$VENV/bin/python" "nemo_toolkit[asr]==2.7.3" "onnx==1.22.0" "onnxruntime==1.23.2" \
  "safetensors==0.8.0"
