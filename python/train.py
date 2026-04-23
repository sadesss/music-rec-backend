from __future__ import annotations

import json
import random
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
from scipy import sparse
from torch import nn
from torch.utils.data import DataLoader, Dataset

from models.newsasrec import NewSASRec


DATA_DIR = Path("data/processed")
ARTIFACTS_DIR = Path("artifacts")
CHECKPOINTS_DIR = ARTIFACTS_DIR / "checkpoints"
NEWSASREC_DIR = ARTIFACTS_DIR / "newsasrec_model"

TRAIN_PATH = DATA_DIR / "train.parquet"
VAL_PATH = DATA_DIR / "val.parquet"
TEST_PATH = DATA_DIR / "test.parquet"

USER2IDX_PATH = ARTIFACTS_DIR / "user2idx.json"
ITEM2IDX_PATH = ARTIFACTS_DIR / "item2idx.json"

TOP_POP_K = 10000
EASE_L2 = 500.0
EASE_MAX_ITEMS = 12000
SKIP_EXISTING = True

# ---- NewSASRec hyperparameters ----
NEWSASREC_EPOCHS = 20
NEWSASREC_MAX_LEN = 50
NEWSASREC_HIDDEN_DIM = 64
NEWSASREC_N_HEADS = 2
NEWSASREC_N_BLOCKS = 2
NEWSASREC_DROPOUT = 0.2
NEWSASREC_LR = 3e-4
NEWSASREC_BATCH_SIZE = 512
NEWSASREC_NUM_NEGATIVES = 50
NEWSASREC_WEIGHT_DECAY = 1e-6
NEWSASREC_SEED = 42


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def ensure_dirs() -> None:
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    CHECKPOINTS_DIR.mkdir(parents=True, exist_ok=True)
    NEWSASREC_DIR.mkdir(parents=True, exist_ok=True)


def save_json(data: dict[str, Any], path: Path) -> None:
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_json(path: Path) -> dict[str, Any]:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def file_exists(path: Path) -> bool:
    return path.exists() and path.is_file()


def load_data() -> tuple[pd.DataFrame, pd.DataFrame, pd.DataFrame, dict[str, int], dict[str, int]]:
    print("[1/7] Loading data...")

    train = pd.read_parquet(TRAIN_PATH)
    val = pd.read_parquet(VAL_PATH)
    test = pd.read_parquet(TEST_PATH)

    user2idx = load_json(USER2IDX_PATH)
    item2idx = load_json(ITEM2IDX_PATH)

    print(f"Train rows: {len(train):,}")
    print(f"Val rows:   {len(val):,}")
    print(f"Test rows:  {len(test):,}")
    print(f"Users:      {len(user2idx):,}")
    print(f"Items:      {len(item2idx):,}")

    return train, val, test, user2idx, item2idx


def build_interaction_matrix(
        train: pd.DataFrame,
        user2idx: dict[str, int],
        item2idx: dict[str, int],
) -> sparse.csr_matrix:
    print("[2/7] Building full interaction matrix...")

    rows: list[int] = []
    cols: list[int] = []

    for row in train.itertuples(index=False):
        rows.append(user2idx[str(row.user_id)])
        cols.append(item2idx[str(row.track_id)])

    data = np.ones(len(rows), dtype=np.float32)
    x = sparse.csr_matrix(
        (data, (rows, cols)),
        shape=(len(user2idx), len(item2idx)),
        dtype=np.float32,
    )

    print(f"Interaction matrix shape: {x.shape}")
    print(f"NNZ: {x.nnz:,}")
    return x


def compute_popularity(x: sparse.csr_matrix) -> tuple[np.ndarray, np.ndarray]:
    print("[3/7] Computing popularity...")

    top_pop_path = CHECKPOINTS_DIR / "top_pop.npy"
    ease_items_path = CHECKPOINTS_DIR / "ease_items_global.npy"

    if SKIP_EXISTING and file_exists(top_pop_path) and file_exists(ease_items_path):
        print("Popularity artifacts found, loading from checkpoints...")
        top_pop = np.load(top_pop_path)
        ease_items_global = np.load(ease_items_path)
        return top_pop, ease_items_global

    item_pop = np.asarray(x.sum(axis=0)).ravel()
    top_pop = np.argsort(-item_pop)[:TOP_POP_K]
    ease_items_global = np.argsort(-item_pop)[: min(EASE_MAX_ITEMS, x.shape[1])]

    np.save(top_pop_path, top_pop)
    np.save(ease_items_path, ease_items_global)

    np.save(ARTIFACTS_DIR / "top_pop.npy", top_pop)
    np.save(ARTIFACTS_DIR / "ease_items_global.npy", ease_items_global)

    print(f"Top popular saved: {len(top_pop):,}")
    print(f"EASE items selected: {len(ease_items_global):,}")
    return top_pop, ease_items_global


def train_ease(x: sparse.csr_matrix, ease_items_global: np.ndarray) -> None:
    print("[4/7] Training EASE...")

    ease_b_ckpt = CHECKPOINTS_DIR / "ease_B.npy"
    ease_b_final = ARTIFACTS_DIR / "ease_B.npy"
    ease_meta_path = ARTIFACTS_DIR / "ease_meta.json"

    if SKIP_EXISTING and file_exists(ease_b_ckpt):
        print("EASE checkpoint found, reusing...")
        if not ease_b_final.exists():
            np.save(ease_b_final, np.load(ease_b_ckpt))
        if not ease_meta_path.exists():
            save_json(
                {
                    "ease_l2": EASE_L2,
                    "ease_items_count": int(len(ease_items_global)),
                    "dtype": "float32",
                },
                ease_meta_path,
            )
        return

    x_ease = x[:, ease_items_global]
    print(f"Reduced EASE matrix shape: {x_ease.shape}")

    g = (x_ease.T @ x_ease).toarray().astype(np.float32)
    diag_idx = np.arange(g.shape[0])
    g[diag_idx, diag_idx] += np.float32(EASE_L2)

    p = np.linalg.inv(g).astype(np.float32)
    b = -p / np.diag(p)
    np.fill_diagonal(b, 0.0)

    np.save(ease_b_ckpt, b)
    np.save(ease_b_final, b)

    save_json(
        {
            "ease_l2": EASE_L2,
            "ease_items_count": int(len(ease_items_global)),
            "dtype": "float32",
        },
        ease_meta_path,
    )

    print("EASE trained and saved")


class NextItemDataset(Dataset):
    """
    Builds next-item training examples from user histories.

    Example:
        history = [3, 8, 2, 7]
        samples:
            input [3]       -> target 8
            input [3, 8]    -> target 2
            input [3, 8, 2] -> target 7

    We store model item ids shifted by +1:
        original item idx in [0..n-1]
        model item idx in [1..n]
        0 is padding
    """

    def __init__(
            self,
            train: pd.DataFrame,
            user2idx: dict[str, int],
            item2idx: dict[str, int],
            max_len: int,
    ) -> None:
        super().__init__()
        self.max_len = max_len
        self.samples: list[tuple[list[int], int]] = []

        work = train[["user_id", "track_id", "datetime"]].copy()
        work["user_idx"] = work["user_id"].astype(str).map(user2idx)
        work["item_idx"] = work["track_id"].astype(str).map(item2idx)
        work = work.sort_values(["user_idx", "datetime"])

        grouped = work.groupby("user_idx")["item_idx"].apply(list)

        for _, item_history in grouped.items():
            if len(item_history) < 2:
                continue

            shifted = [int(item_id) + 1 for item_id in item_history]

            for pos in range(1, len(shifted)):
                input_seq = shifted[max(0, pos - max_len):pos]
                target = shifted[pos]
                self.samples.append((input_seq, target))

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> tuple[list[int], int]:
        return self.samples[idx]


def collate_next_item_batch(
        batch: list[tuple[list[int], int]],
        max_len: int,
) -> tuple[torch.Tensor, torch.Tensor]:
    seqs: list[list[int]] = []
    targets: list[int] = []

    for seq, target in batch:
        padded = seq[-max_len:]
        pad_len = max_len - len(padded)
        padded = [0] * pad_len + padded

        seqs.append(padded)
        targets.append(target)

    seq_tensor = torch.tensor(seqs, dtype=torch.long)
    target_tensor = torch.tensor(targets, dtype=torch.long)
    return seq_tensor, target_tensor


def sample_negatives(
        batch_size: int,
        num_negatives: int,
        num_items: int,
        positive_targets: torch.Tensor,
        device: torch.device,
) -> torch.Tensor:
    """
    Samples negatives in [1..num_items], excluding positive target when possible.
    """
    negatives = torch.randint(
        low=1,
        high=num_items + 1,
        size=(batch_size, num_negatives),
        device=device,
    )

    positive_targets = positive_targets.unsqueeze(1)

    # Re-sample positions where sampled negative == positive target
    for _ in range(5):
        collision_mask = negatives.eq(positive_targets)
        if not collision_mask.any():
            break
        negatives[collision_mask] = torch.randint(
            low=1,
            high=num_items + 1,
            size=(collision_mask.sum().item(),),
            device=device,
        )

    return negatives


def train_newsasrec(
        train: pd.DataFrame,
        user2idx: dict[str, int],
        item2idx: dict[str, int],
) -> None:
    print("[5/7] Training NewSASRec-GR...")

    state_dict_path = NEWSASREC_DIR / "model_state_dict.pt"
    config_path = NEWSASREC_DIR / "config.json"

    if SKIP_EXISTING and file_exists(state_dict_path) and file_exists(config_path):
        print("NewSASRec checkpoint found, skipping training.")
        return

    dataset = NextItemDataset(
        train=train,
        user2idx=user2idx,
        item2idx=item2idx,
        max_len=NEWSASREC_MAX_LEN,
    )

    print(f"Sequential training samples: {len(dataset):,}")

    loader = DataLoader(
        dataset,
        batch_size=NEWSASREC_BATCH_SIZE,
        shuffle=True,
        num_workers=0,
        pin_memory=torch.cuda.is_available(),
        collate_fn=lambda batch: collate_next_item_batch(batch, NEWSASREC_MAX_LEN),
    )

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model = NewSASRec(
        num_items=len(item2idx),
        max_len=NEWSASREC_MAX_LEN,
        hidden_dim=NEWSASREC_HIDDEN_DIM,
        n_heads=NEWSASREC_N_HEADS,
        n_blocks=NEWSASREC_N_BLOCKS,
        dropout_rate=NEWSASREC_DROPOUT,
    ).to(device)

    optimizer = torch.optim.Adam(
        model.parameters(),
        lr=NEWSASREC_LR,
        weight_decay=NEWSASREC_WEIGHT_DECAY,
    )

    bce = nn.BCEWithLogitsLoss()

    model.train()

    for epoch in range(1, NEWSASREC_EPOCHS + 1):
        epoch_loss = 0.0
        epoch_steps = 0

        for input_seqs, targets in loader:
            input_seqs = input_seqs.to(device, non_blocking=True)
            targets = targets.to(device, non_blocking=True)

            batch_size = input_seqs.size(0)

            last_hidden = model.get_last_hidden(input_seqs)  # [B, H]

            pos_emb = model.item_embedding(targets)  # [B, H]
            pos_logits = (last_hidden * pos_emb).sum(dim=1, keepdim=True)  # [B, 1]

            neg_items = sample_negatives(
                batch_size=batch_size,
                num_negatives=NEWSASREC_NUM_NEGATIVES,
                num_items=len(item2idx),
                positive_targets=targets,
                device=device,
            )
            neg_emb = model.item_embedding(neg_items)  # [B, N, H]
            neg_logits = torch.einsum("bh,bnh->bn", last_hidden, neg_emb)  # [B, N]

            logits = torch.cat([pos_logits, neg_logits], dim=1)  # [B, 1+N]
            labels = torch.cat(
                [
                    torch.ones((batch_size, 1), device=device),
                    torch.zeros((batch_size, NEWSASREC_NUM_NEGATIVES), device=device),
                ],
                dim=1,
            )

            loss = bce(logits, labels)

            optimizer.zero_grad()
            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=5.0)
            optimizer.step()

            epoch_loss += float(loss.item())
            epoch_steps += 1

        mean_loss = epoch_loss / max(epoch_steps, 1)
        print(f"Epoch {epoch:02d}/{NEWSASREC_EPOCHS} - loss: {mean_loss:.6f}")

    torch.save(model.state_dict(), state_dict_path)

    save_json(
        {
            "model_name": "NewSASRec-GR",
            "num_items": len(item2idx),
            "max_len": NEWSASREC_MAX_LEN,
            "hidden_dim": NEWSASREC_HIDDEN_DIM,
            "n_heads": NEWSASREC_N_HEADS,
            "n_blocks": NEWSASREC_N_BLOCKS,
            "dropout_rate": NEWSASREC_DROPOUT,
            "epochs": NEWSASREC_EPOCHS,
            "lr": NEWSASREC_LR,
            "batch_size": NEWSASREC_BATCH_SIZE,
            "num_negatives": NEWSASREC_NUM_NEGATIVES,
            "gated_residual": True,
            "padding_idx": 0,
            "item_id_shift": 1,
        },
        config_path,
    )

    print("NewSASRec-GR trained and saved")


def save_global_meta(
        train: pd.DataFrame,
        val: pd.DataFrame,
        test: pd.DataFrame,
        user2idx: dict[str, int],
        item2idx: dict[str, int],
        ease_items_global: np.ndarray,
) -> None:
    print("[6/7] Saving metadata...")

    meta = {
        "train_rows": len(train),
        "val_rows": len(val),
        "test_rows": len(test),
        "n_users": len(user2idx),
        "n_items": len(item2idx),
        "top_pop_k": TOP_POP_K,
        "ease_l2": EASE_L2,
        "ease_items_count": int(len(ease_items_global)),
        "newsasrec_epochs": NEWSASREC_EPOCHS,
        "newsasrec_model": "NewSASRec-GR",
    }

    save_json(meta, ARTIFACTS_DIR / "model_meta.json")


def save_manifest() -> None:
    print("[7/7] Saving artifact manifest...")

    manifest = {
        "files": [
            "user2idx.json",
            "item2idx.json",
            "top_pop.npy",
            "ease_items_global.npy",
            "ease_B.npy",
            "ease_meta.json",
            "model_meta.json",
            "artifact_manifest.json",
            "newsasrec_model/model_state_dict.pt",
            "newsasrec_model/config.json",
        ]
    }
    save_json(manifest, ARTIFACTS_DIR / "artifact_manifest.json")


def main() -> None:
    set_seed(NEWSASREC_SEED)
    ensure_dirs()

    train, val, test, user2idx, item2idx = load_data()
    x = build_interaction_matrix(train, user2idx, item2idx)
    _, ease_items_global = compute_popularity(x)
    train_ease(x, ease_items_global)
    train_newsasrec(train, user2idx, item2idx)
    save_global_meta(train, val, test, user2idx, item2idx, ease_items_global)
    save_manifest()

    print("\nDONE 🚀")
    print("Artifacts saved to:", ARTIFACTS_DIR.resolve())


if __name__ == "__main__":
    main()