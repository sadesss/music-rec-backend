from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Tuple

import numpy as np
import pandas as pd
from scipy import sparse


DATA_DIR = Path("data/processed")
ARTIFACTS_DIR = Path("artifacts")

TRAIN_PATH = DATA_DIR / "train.parquet"
VAL_PATH = DATA_DIR / "val.parquet"
TEST_PATH = DATA_DIR / "test.parquet"

USER2IDX_PATH = ARTIFACTS_DIR / "user2idx.json"
ITEM2IDX_PATH = ARTIFACTS_DIR / "item2idx.json"

TOP_POP_PATH = ARTIFACTS_DIR / "top_pop.npy"
EASE_ITEMS_PATH = ARTIFACTS_DIR / "ease_items_global.npy"
EASE_B_PATH = ARTIFACTS_DIR / "ease_B.npy"

RESULTS_PATH = ARTIFACTS_DIR / "eval_results.json"

KS = [10, 20] #50 100


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def save_json(data: dict, path: Path) -> None:
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def build_interaction_matrix(
        df: pd.DataFrame,
        user2idx: Dict[str, int],
        item2idx: Dict[str, int],
) -> sparse.csr_matrix:
    rows: List[int] = []
    cols: List[int] = []

    for row in df.itertuples(index=False):
        u = user2idx.get(str(row.user_id))
        i = item2idx.get(str(row.track_id))
        if u is None or i is None:
            continue
        rows.append(u)
        cols.append(i)

    data = np.ones(len(rows), dtype=np.float32)
    return sparse.csr_matrix(
        (data, (rows, cols)),
        shape=(len(user2idx), len(item2idx)),
        dtype=np.float32,
    )


def build_seen_items(train: pd.DataFrame, user2idx: Dict[str, int], item2idx: Dict[str, int]) -> Dict[int, set]:
    seen: Dict[int, set] = {}
    for row in train.itertuples(index=False):
        u = user2idx.get(str(row.user_id))
        i = item2idx.get(str(row.track_id))
        if u is None or i is None:
            continue
        if u not in seen:
            seen[u] = set()
        seen[u].add(i)
    return seen


def make_eval_pairs(df: pd.DataFrame, user2idx: Dict[str, int], item2idx: Dict[str, int]) -> List[Tuple[int, int]]:
    pairs: List[Tuple[int, int]] = []
    for row in df.itertuples(index=False):
        u = user2idx.get(str(row.user_id))
        i = item2idx.get(str(row.track_id))
        if u is not None and i is not None:
            pairs.append((u, i))
    return pairs


def precision_at_k(hit: int, k: int) -> float:
    return hit / k


def recall_at_k(hit: int) -> float:
    return float(hit)


def hitrate_at_k(hit: int) -> float:
    return float(hit)


def ndcg_at_k(rank_1based: int | None) -> float:
    if rank_1based is None:
        return 0.0
    return 1.0 / np.log2(rank_1based + 1.0)


def evaluate_ranked_lists(
        pairs: List[Tuple[int, int]],
        recs_by_user: Dict[int, np.ndarray],
        ks: List[int],
) -> dict:
    results = {f"Precision@{k}": 0.0 for k in ks}
    results.update({f"Recall@{k}": 0.0 for k in ks})
    results.update({f"HitRate@{k}": 0.0 for k in ks})
    results.update({f"NDCG@{k}": 0.0 for k in ks})

    n = len(pairs)
    if n == 0:
        return results

    for u, true_item in pairs:
        recs = recs_by_user.get(u)
        if recs is None:
            continue

        for k in ks:
            topk = recs[:k]
            hit_positions = np.where(topk == true_item)[0]
            if len(hit_positions) > 0:
                hit = 1
                rank_1based = int(hit_positions[0]) + 1
            else:
                hit = 0
                rank_1based = None

            results[f"Precision@{k}"] += precision_at_k(hit, k)
            results[f"Recall@{k}"] += recall_at_k(hit)
            results[f"HitRate@{k}"] += hitrate_at_k(hit)
            results[f"NDCG@{k}"] += ndcg_at_k(rank_1based)

    for key in results:
        results[key] /= n

    return results


def get_popularity_recs(
        top_pop: np.ndarray,
        seen: Dict[int, set],
        user_ids: List[int],
        k_max: int,
) -> Dict[int, np.ndarray]:
    recs = {}
    for u in user_ids:
        user_seen = seen.get(u, set())
        filtered = [item for item in top_pop if item not in user_seen]
        recs[u] = np.asarray(filtered[:k_max], dtype=np.int64)
    return recs


def get_ease_recs(
        x_train: sparse.csr_matrix,
        ease_items_global: np.ndarray,
        ease_b: np.ndarray,
        seen: Dict[int, set],
        user_ids: List[int],
        k_max: int,
) -> Dict[int, np.ndarray]:
    recs = {}

    global_to_local = {int(g): idx for idx, g in enumerate(ease_items_global.tolist())}

    for u in user_ids:
        user_vector_local = x_train[u, :][:, ease_items_global]
        scores_local = user_vector_local @ ease_b
        scores_local = np.asarray(scores_local).ravel()

        # filter seen
        user_seen_global = seen.get(u, set())
        for g_item in user_seen_global:
            local_idx = global_to_local.get(g_item)
            if local_idx is not None:
                scores_local[local_idx] = -np.inf

        top_local = np.argsort(-scores_local)[:k_max]
        top_global = ease_items_global[top_local]
        recs[u] = np.asarray(top_global, dtype=np.int64)

    return recs


def main() -> None:
    print("[1/6] Loading data and artifacts...")
    train = pd.read_parquet(TRAIN_PATH)
    val = pd.read_parquet(VAL_PATH)
    test = pd.read_parquet(TEST_PATH)

    user2idx = load_json(USER2IDX_PATH)
    item2idx = load_json(ITEM2IDX_PATH)

    top_pop = np.load(TOP_POP_PATH)
    ease_items_global = np.load(EASE_ITEMS_PATH)
    ease_b = np.load(EASE_B_PATH)

    print("[2/6] Building train interaction matrix...")
    x_train = build_interaction_matrix(train, user2idx, item2idx)

    print("[3/6] Building seen sets and eval pairs...")
    seen = build_seen_items(train, user2idx, item2idx)

    val_pairs = make_eval_pairs(val, user2idx, item2idx)
    test_pairs = make_eval_pairs(test, user2idx, item2idx)

    val_users = sorted({u for u, _ in val_pairs})
    test_users = sorted({u for u, _ in test_pairs})

    k_max = max(KS)

    print("[4/6] Generating popularity recommendations...")
    pop_val_recs = get_popularity_recs(top_pop, seen, val_users, k_max)
    pop_test_recs = get_popularity_recs(top_pop, seen, test_users, k_max)

    print("[5/6] Generating EASE recommendations...")
    ease_val_recs = get_ease_recs(x_train, ease_items_global, ease_b, seen, val_users, k_max)
    ease_test_recs = get_ease_recs(x_train, ease_items_global, ease_b, seen, test_users, k_max)

    print("[6/6] Calculating metrics...")
    results = {
        "val": {
            "Popularity": evaluate_ranked_lists(val_pairs, pop_val_recs, KS),
            "EASE": evaluate_ranked_lists(val_pairs, ease_val_recs, KS),
        },
        "test": {
            "Popularity": evaluate_ranked_lists(test_pairs, pop_test_recs, KS),
            "EASE": evaluate_ranked_lists(test_pairs, ease_test_recs, KS),
        },
        "meta": {
            "val_pairs": len(val_pairs),
            "test_pairs": len(test_pairs),
            "ks": KS,
        },
    }

    save_json(results, RESULTS_PATH)

    print("\n=== VALIDATION ===")
    for model_name, metrics in results["val"].items():
        print(f"\n[{model_name}]")
        for k, v in metrics.items():
            print(f"{k}: {v:.6f}")

    print("\n=== TEST ===")
    for model_name, metrics in results["test"].items():
        print(f"\n[{model_name}]")
        for k, v in metrics.items():
            print(f"{k}: {v:.6f}")

    print(f"\nSaved results to: {RESULTS_PATH.resolve()}")


if __name__ == "__main__":
    main()