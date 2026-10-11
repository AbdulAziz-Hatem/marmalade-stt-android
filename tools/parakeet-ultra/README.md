# parakeet-ultra: convert Moondream's Parakeet Ultra for sherpa-onnx

[Parakeet Ultra](https://huggingface.co/moondream/parakeet-ultra) (CC-BY-4.0) is
Moondream's post-trained version of NVIDIA's parakeet-tdt-0.6b-v3: the same
architecture and tokenizer with new weights. Moondream publishes it only as a
Hugging Face transformers checkpoint and runs it in its closed Photon runtime.
No sherpa-onnx export exists, so `convert.py` builds one with open tools. How it
works and what it checks are in its docstring. Results:
`docs/benchmarks/2026-09-host-wer.md` ("Parakeet Ultra"). Ethics review:
`docs/model-reviews/parakeet-ultra.md`.

## Run it

```sh
VENV=$HOME/venv-nemo tools/parakeet-ultra/setup.sh     # NeMo 2.7.3, PyTorch 2.11.0 (uv, Python 3.12)
$HOME/venv-nemo/bin/python tools/parakeet-ultra/convert.py --work /path/to/scratch/parakeet-ultra
```

The conversion takes under a minute on a 16-core Ryzen AI Max+ 395 (6 threads),
peaks at about 12 GB of RAM and needs about 10 GB of disk. Two runs gave
byte-identical output (#25). It downloads its two inputs, pinned by revision
and checked by sha256:

| Input | Revision | SHA-256 |
|---|---|---|
| `moondream/parakeet-ultra` `model.safetensors` | `73175eb7aeb0d82f1e2a6b53b3aabc10a90bcd0b` | `c9608f36…66717dc` |
| `nvidia/parakeet-tdt-0.6b-v3` `parakeet-tdt-0.6b-v3.nemo` | `541d1f99c6b0c3cd0b11a95167540bb8edefd82b` | `3cbdc858…588d10d` |

Output, in `<work>/out/`:
- `encoder.int8.onnx`, `decoder.int8.onnx`, `joiner.int8.onnx`, `tokens.txt`:
  what the app's catalog entry `parakeet-ultra` pins and what the harness
  engine of the same name uses.
- `fp32/`: the full-precision export, for comparisons (harness candidate
  `parakeet-ultra-fp32`).
- `conversion-report.json`: how many weights came from Ultra (all 651
  learned ones), which buffers stayed v3's (the mel filterbank and window),
  and which Ultra tensors were left out (the 6 `vad_head` ones).
- `SHA256SUMS`.

`asrbench.fetch_models` downloads the int8 files from the hosted release
(below), like any other engine. The fp32 export isn't hosted: to benchmark it,
copy `fp32/` to `$WORK/models/parakeet-ultra-fp32/`; `fetch_models` checks it
against the pinned hashes.

## Hosting

The int8 files are release `v1` of the public GitHub repo
`maxwhipw/marmalade-stt-engines` (#39), as `parakeet-ultra-encoder.int8.onnx`,
`parakeet-ultra-decoder.int8.onnx`, `parakeet-ultra-joiner.int8.onnx` and
`parakeet-ultra-tokens.txt`. Their hashes match `SHA256SUMS` from the #25 run
and were checked again after upload by downloading them back. That repo's
README says how the files were made; its NOTICE gives the CC-BY-4.0 credit
to NVIDIA and Moondream, the license link and the changes, and
`LICENSES/CC-BY-4.0.txt` has the legal code. A new conversion (other tool
versions, new weights) goes into a new release tag; a published tag's assets
are never replaced.

## Licenses

`convert.py` is the project's (MIT). It copies transformers' NeMo-to-HF key
mapping (Apache-2.0) and follows sherpa-onnx's v3 export script (Apache-2.0).
The environment from `setup.sh` is build-machine tooling: NeMo, PyTorch, ONNX
and ONNX Runtime are Apache-2.0, BSD or MIT. NeMo's ASR extra also installs
**soxr (LGPL-2.1)**, through librosa, and **text-unidecode** (Artistic or
GPLv2+; used under the Artistic License). Max approved running them for this
conversion (2026-09-27). They run on the build machine only. Nothing from the
environment is committed or shipped, and the output is model weights.
