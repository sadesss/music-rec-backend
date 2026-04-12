from __future__ import annotations

import json
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
import torch
from scipy import sparse

from rectools import Columns
from rectools.dataset import Dataset
from rectools.models.nn.transformers.sasrec import SASRecModel


DATA_DIR = Path("data/processed")
ARTIFACTS_DIR = Path("artifacts")
CHECKPOINTS_DIR = ARTIFACTS_DIR / "checkpoints"
SASREC_DIR = ARTIFACTS_DIR / "sasrec_model"

TRAIN_PATH = DATA_DIR / "train.parquet"
VAL_PATH = DATA_DIR / "val.parquet"
TEST_PATH = DATA_DIR / "test.parquet"

USER2IDX_PATH = ARTIFACTS_DIR / "user2idx.json"
ITEM2IDX_PATH = ARTIFACTS_DIR / "item2idx.json"

TOP_POP_K = 10000
EASE_L2 = 500.0
EASE_MAX_ITEMS = 12000
SASREC_EPOCHS = 1
SKIP_EXISTING = True


def ensure_dirs() -> None:
    ARTIFACTS_DIR.mkdir(parents=True, exist_ok=True)
    CHECKPOINTS_DIR.mkdir(parents=True, exist_ok=True)
    SASREC_DIR.mkdir(parents=True, exist_ok=True)


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


def _get_sasrec_torch_module(model: SASRecModel):
    if hasattr(model, "torch_model"):
        return model.torch_model
    if hasattr(model, "model"):
        return model.model
    raise AttributeError(
        "Could not find inner torch module in SASRecModel. "
        f"Available attrs: {dir(model)}"
    )


def train_sasrec(train: pd.DataFrame) -> None:
    print("[5/7] Training SASRec...")

    state_dict_path = SASREC_DIR / "model_state_dict.pt"
    config_path = SASREC_DIR / "config.json"

    if SKIP_EXISTING and file_exists(state_dict_path) and file_exists(config_path):
        print("SASRec checkpoint found, skipping training.")
        return

    interactions = pd.DataFrame(
        {
            Columns.User: train["user_id"],
            Columns.Item: train["track_id"],
            Columns.Weight: 1.0,
            Columns.Datetime: train["datetime"],
        }
    )

    dataset = Dataset.construct(interactions)

    model = SASRecModel(
        n_blocks=2,
        n_heads=2,
        n_factors=64,
        dropout_rate=0.2,
        session_max_len=50,
        loss="sampled_softmax",
        n_negatives=50,
        lr=3e-4,
        batch_size=512,
        epochs=SASREC_EPOCHS,
        verbose=1,
    )

    model.fit(dataset)

    torch_module = _get_sasrec_torch_module(model)
    torch.save(torch_module.state_dict(), state_dict_path)

    save_json(
        {
            "n_blocks": 2,
            "n_heads": 2,
            "n_factors": 64,
            "dropout_rate": 0.2,
            "session_max_len": 50,
            "loss": "sampled_softmax",
            "n_negatives": 50,
            "lr": 3e-4,
            "batch_size": 512,
            "epochs": SASREC_EPOCHS,
        },
        config_path,
    )

    print("SASRec trained and saved")


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
        "sasrec_epochs": SASREC_EPOCHS,
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
            "sasrec_model/model_state_dict.pt",
            "sasrec_model/config.json",
        ]
    }
    save_json(manifest, ARTIFACTS_DIR / "artifact_manifest.json")


def main() -> None:
    ensure_dirs()

    train, val, test, user2idx, item2idx = load_data()
    x = build_interaction_matrix(train, user2idx, item2idx)
    _, ease_items_global = compute_popularity(x)
    train_ease(x, ease_items_global)
    train_sasrec(train)
    save_global_meta(train, val, test, user2idx, item2idx, ease_items_global)
    save_manifest()

    print("\nDONE 🚀")
    print("Artifacts saved to:", ARTIFACTS_DIR.resolve())


if __name__ == "__main__":
    main()