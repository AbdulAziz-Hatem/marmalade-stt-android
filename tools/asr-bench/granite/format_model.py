"""A tiny Granite formatter (#33): a Transformer encoder trained from scratch that
labels each word of Granite's text with case, the mark after it, and whether
it is an expanded contraction (see format_data.py).

Input: Granite's own byte-level BPE ids (its tokenizer.json, which the app
already downloads with the engine), each word encoded as " word". Output:
three label distributions at every token; the labels of a word are read at
its first token. No pretrained weights: everything is learned from the
training text.

    python granite/format_model.py train --data train-*.pt --val val.pt --out DIR [--threads 6]
    python granite/format_model.py export --out DIR     # DIR/model.pt -> DIR/formatter.onnx, formatter.int8.onnx
"""

import argparse
import json
import math
import time
from pathlib import Path

import torch
from torch import nn

VOCAB = 16384
N_CASE, N_PUNCT, N_MERGE = 3, 6, 2
PAD = 0  # Granite's blank; never a text token


class Formatter(nn.Module):
    def __init__(self, dim=256, layers=4, heads=4, ff=1024, max_len=256):
        super().__init__()
        self.config = dict(dim=dim, layers=layers, heads=heads, ff=ff, max_len=max_len)
        self.embed = nn.Embedding(VOCAB, dim)
        self.pos = nn.Embedding(max_len, dim)
        layer = nn.TransformerEncoderLayer(dim, heads, ff, dropout=0.1, activation="gelu",
                                           batch_first=True, norm_first=True)
        self.encoder = nn.TransformerEncoder(layer, layers, enable_nested_tensor=False)
        self.norm = nn.LayerNorm(dim)
        self.head = nn.Linear(dim, N_CASE + N_PUNCT + N_MERGE)

    def forward(self, ids: torch.Tensor, mask: torch.Tensor | None = None) -> torch.Tensor:
        """ids [B, T] -> logits [B, T, 11] (case 0:3, punct 3:9, merge 9:11). mask: True at padding."""
        pos = torch.arange(ids.shape[1], device=ids.device)[None]
        x = self.embed(ids) + self.pos(pos)
        x = self.encoder(x, src_key_padding_mask=mask)
        return self.head(self.norm(x))


def _example(data: dict, i: int):
    t0, t1 = data["tok_off"][i].item(), data["tok_off"][i + 1].item()
    w0, w1 = data["word_off"][i].item(), data["word_off"][i + 1].item()
    return (data["ids"][t0:t1].long(), data["starts"][w0:w1].long(),
            data["case"][w0:w1].long(), data["punct"][w0:w1].long(), data["merge"][w0:w1].long())


def batches(data: dict, batch_tokens: int, shuffle: bool, seed: int):
    n = len(data["tok_off"]) - 1
    lengths = (data["tok_off"][1:] - data["tok_off"][:-1]).tolist()
    order = torch.randperm(n, generator=torch.Generator().manual_seed(seed)).tolist() if shuffle else list(range(n))
    # Bucket by length within windows of 100 batches so padding stays small.
    window = 4096
    for w in range(0, n, window):
        chunk = sorted(order[w: w + window], key=lambda i: lengths[i])
        group, longest = [], 0
        out = []
        for i in chunk:
            longest2 = max(longest, lengths[i])
            if group and longest2 * (len(group) + 1) > batch_tokens:
                out.append(group)
                group, longest2 = [], lengths[i]
            group.append(i)
            longest = longest2
        if group:
            out.append(group)
        if shuffle:
            out = [out[j] for j in torch.randperm(len(out), generator=torch.Generator().manual_seed(seed + w)).tolist()]
        for g in out:
            yield collate([_example(data, i) for i in g])


def collate(examples):
    T = max(len(e[0]) for e in examples)
    B = len(examples)
    ids = torch.full((B, T), PAD, dtype=torch.long)
    mask = torch.ones((B, T), dtype=torch.bool)
    rows, cols, case, punct, merge = [], [], [], [], []
    for b, (e_ids, starts, c, p, m) in enumerate(examples):
        ids[b, : len(e_ids)] = e_ids
        mask[b, : len(e_ids)] = False
        rows += [b] * len(starts)
        cols += starts.tolist()
        case.append(c)
        punct.append(p)
        merge.append(m)
    return ids, mask, torch.tensor(rows), torch.tensor(cols), torch.cat(case), torch.cat(punct), torch.cat(merge)


def losses(model, batch):
    ids, mask, rows, cols, case, punct, merge = batch
    logits = model(ids, mask)[rows, cols]
    ce = nn.functional.cross_entropy
    return ce(logits[:, :3], case), ce(logits[:, 3:9], punct), ce(logits[:, 9:], merge), logits


@torch.no_grad()
def evaluate(model, data, batch_tokens=16384):
    model.eval()
    tot = {"case": 0, "punct": 0, "merge": 0, "words": 0, "loss": 0.0}
    for batch in batches(data, batch_tokens, shuffle=False, seed=0):
        lc, lp, lm, logits = losses(model, batch)
        n = len(batch[4])
        tot["loss"] += (lc + lp + lm).item() * n
        tot["case"] += (logits[:, :3].argmax(-1) == batch[4]).sum().item()
        tot["punct"] += (logits[:, 3:9].argmax(-1) == batch[5]).sum().item()
        tot["merge"] += (logits[:, 9:].argmax(-1) == batch[6]).sum().item()
        tot["words"] += n
    model.train()
    w = tot["words"]
    return {k: (v / w if k != "words" else v) for k, v in tot.items()}


def load_data(paths: list[Path]) -> dict:
    """format_data.py shards, concatenated."""
    parts = [torch.load(p) for p in paths]
    out = {k: torch.cat([p[k] for p in parts]) for k in ("ids", "starts", "case", "punct", "merge")}
    for key, base in (("tok_off", "ids"), ("word_off", "starts")):
        offs, total = [torch.zeros(1, dtype=torch.int64)], 0
        for p in parts:
            offs.append(p[key][1:] + total)
            total += len(p[base])
        out[key] = torch.cat(offs)
    return out


def train(args) -> None:
    torch.manual_seed(33)
    torch.set_num_threads(args.threads)
    data = load_data(args.data)
    val = load_data(args.val)
    model = Formatter(dim=args.dim, layers=args.layers, heads=args.heads, ff=args.ff)
    n_params = sum(p.numel() for p in model.parameters())
    print(f"{n_params / 1e6:.2f} M parameters; {len(data['tok_off']) - 1} examples", flush=True)
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.01)
    n_tokens = len(data["ids"])
    steps = int(args.epochs * n_tokens / (args.batch_tokens * 0.8))
    sched = torch.optim.lr_scheduler.LambdaLR(
        opt, lambda s: min(1.0, s / 1000) * max(0.05, 0.5 * (1 + math.cos(math.pi * min(1.0, s / steps)))))
    args.out.mkdir(parents=True, exist_ok=True)
    step, t0, best = 0, time.time(), None
    for epoch in range(math.ceil(args.epochs)):
        for batch in batches(data, args.batch_tokens, shuffle=True, seed=epoch):
            lc, lp, lm, _ = losses(model, batch)
            loss = lc + lp + lm
            opt.zero_grad()
            loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            opt.step()
            sched.step()
            step += 1
            if step % 200 == 0:
                print(f"step {step}/{steps} epoch {epoch} loss {loss.item():.4f} "
                      f"({lc.item():.3f} {lp.item():.3f} {lm.item():.3f}) {time.time() - t0:.0f}s", flush=True)
            if step % args.eval_every == 0 or step >= steps:
                v = evaluate(model, val)
                print(f"val step {step}: {json.dumps({k: round(x, 4) for k, x in v.items()})}", flush=True)
                if best is None or v["loss"] < best:
                    best = v["loss"]
                    torch.save({"config": model.config, "state": model.state_dict(), "step": step, "val": v},
                               args.out / "model.pt")
            if step >= steps:
                return


class _ExportLayer(nn.Module):
    """A trained nn.TransformerEncoderLayer (norm_first, gelu) spelled out: the legacy ONNX
    exporter freezes the sequence length inside PyTorch's fused attention; reshapes with -1
    keep it dynamic."""

    def __init__(self, layer: nn.TransformerEncoderLayer, heads: int):
        super().__init__()
        self.layer, self.heads = layer, heads

    def forward(self, x):
        L = self.layer
        B, D = x.shape[0], x.shape[2]
        h = L.norm1(x)
        qkv = nn.functional.linear(h, L.self_attn.in_proj_weight, L.self_attn.in_proj_bias)
        q, k, v = qkv.reshape(B, -1, 3, self.heads, D // self.heads).permute(2, 0, 3, 1, 4)
        att = torch.softmax(q @ k.transpose(-1, -2) / math.sqrt(D // self.heads), dim=-1)
        a = (att @ v).transpose(1, 2).reshape(B, -1, D)
        x = x + L.self_attn.out_proj(a)
        return x + L.linear2(nn.functional.gelu(L.linear1(L.norm2(x))))


class _Export(nn.Module):
    def __init__(self, model: Formatter):
        super().__init__()
        self.model = model
        self.layers = nn.ModuleList(_ExportLayer(l, model.config["heads"]) for l in model.encoder.layers)

    def forward(self, ids):
        m = self.model
        pos = torch.arange(ids.shape[1])[None]
        x = m.embed(ids) + m.pos(pos)
        for layer in self.layers:
            x = layer(x)
        return m.head(m.norm(x))


def export(args) -> None:
    from onnxruntime.quantization import QuantType, quantize_dynamic

    ckpt = torch.load(args.out / "model.pt")
    model = Formatter(**ckpt["config"])
    model.load_state_dict(ckpt["state"])
    model.eval()
    ids = torch.randint(1, VOCAB, (1, 37))
    with torch.no_grad():
        ref, got = model(ids), _Export(model)(ids)
    assert torch.allclose(ref, got, atol=1e-4), (ref - got).abs().max()
    fp32 = args.out / "formatter.onnx"
    torch.onnx.export(_Export(model), (ids,), str(fp32), input_names=["ids"], output_names=["logits"],
                      dynamic_axes={"ids": {1: "tokens"}, "logits": {1: "tokens"}}, opset_version=17, dynamo=False)
    quantize_dynamic(str(fp32), str(args.out / "formatter.int8.onnx"), weight_type=QuantType.QInt8)
    print(f"{fp32} and formatter.int8.onnx written")


def main() -> None:
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    t = sub.add_parser("train")
    t.add_argument("--data", type=Path, nargs="+", required=True)
    t.add_argument("--val", type=Path, nargs="+", required=True)
    t.add_argument("--out", type=Path, required=True)
    t.add_argument("--threads", type=int, default=6)
    t.add_argument("--dim", type=int, default=256)
    t.add_argument("--layers", type=int, default=4)
    t.add_argument("--heads", type=int, default=4)
    t.add_argument("--ff", type=int, default=1024)
    t.add_argument("--lr", type=float, default=1e-3)
    t.add_argument("--epochs", type=float, default=2.0)
    t.add_argument("--batch-tokens", type=int, default=8192)
    t.add_argument("--eval-every", type=int, default=2000)
    e = sub.add_parser("export")
    e.add_argument("--out", type=Path, required=True)
    args = ap.parse_args()
    (train if args.cmd == "train" else export)(args)


if __name__ == "__main__":
    main()
