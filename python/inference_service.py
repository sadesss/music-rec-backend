from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
import torch
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from scipy import sparse

from models.newsasrec import NewSASRec


ARTIFACTS_DIR = Path("artifacts")
DATA_DIR = Path("data/processed")

TRAIN_PATH = DATA_DIR / "train.parquet"

USER2IDX_PATH = ARTIFACTS_DIR / "user2idx.json"
ITEM2IDX_PATH = ARTIFACTS_DIR / "item2idx.json"
REVERSE_MAPPINGS_PATH = ARTIFACTS_DIR / "reverse_mappings.json"

TOP_POP_PATH = ARTIFACTS_DIR / "top_pop.npy"
EASE_ITEMS_PATH = ARTIFACTS_DIR / "ease_items_global.npy"
EASE_B_PATH = ARTIFACTS_DIR / "ease_B.npy"

NEWSASREC_DIR = ARTIFACTS_DIR / "newsasrec_model"
NEWSASREC_STATE_PATH = NEWSASREC_DIR / "model_state_dict.pt"
NEWSASREC_CONFIG_PATH = NEWSASREC_DIR / "config.json"

# Сколько кандидатов брать у EASE перед переранжированием
EASE_CANDIDATES = 200


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def build_seen_items(
        train: pd.DataFrame,
        user2idx: Dict[str, int],
        item2idx: Dict[str, int],
) -> Dict[int, set[int]]:
    seen: Dict[int, set[int]] = {}
    for row in train.itertuples(index=False):
        u = user2idx.get(str(row.user_id))
        i = item2idx.get(str(row.track_id))
        if u is None or i is None:
            continue
        if u not in seen:
            seen[u] = set()
        seen[u].add(i)
    return seen


def build_user_histories(
        train: pd.DataFrame,
        user2idx: Dict[str, int],
        item2idx: Dict[str, int],
) -> Dict[int, List[int]]:
    """
    Храним историю пользователя в порядке времени.
    Важно: для NewSASRec item ids должны быть сдвинуты на +1, потому что 0 = padding.
    """
    work = train[["user_id", "track_id", "datetime"]].copy()
    work["user_idx"] = work["user_id"].astype(str).map(user2idx)
    work["item_idx"] = work["track_id"].astype(str).map(item2idx)
    work = work.dropna(subset=["user_idx", "item_idx"])
    work["user_idx"] = work["user_idx"].astype(int)
    work["item_idx"] = work["item_idx"].astype(int)
    work = work.sort_values(["user_idx", "datetime"])

    grouped = work.groupby("user_idx")["item_idx"].apply(list)

    histories: Dict[int, List[int]] = {}
    for user_idx, item_history in grouped.items():
        # +1 because 0 is padding
        histories[int(user_idx)] = [int(item_id) + 1 for item_id in item_history]

    return histories


def build_interaction_matrix(
        train: pd.DataFrame,
        user2idx: Dict[str, int],
        item2idx: Dict[str, int],
) -> sparse.csr_matrix:
    rows = []
    cols = []

    for row in train.itertuples(index=False):
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


class RecommendRequest(BaseModel):
    user_id: str
    top_k: int = 20
    filter_seen: bool = True


class RecommendResponse(BaseModel):
    user_id: str
    recommendations: List[str]
    model: str


class HealthResponse(BaseModel):
    status: str


class RecommenderService:
    def __init__(self) -> None:
        self.user2idx: Dict[str, int] = {}
        self.item2idx: Dict[str, int] = {}
        self.idx2item: List[str] = []

        self.top_pop: Optional[np.ndarray] = None
        self.ease_items_global: Optional[np.ndarray] = None
        self.ease_b: Optional[np.ndarray] = None

        self.x_train: Optional[sparse.csr_matrix] = None
        self.seen: Dict[int, set[int]] = {}
        self.user_histories: Dict[int, List[int]] = {}
        self.global_to_local: Dict[int, int] = {}

        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.newsasrec: Optional[NewSASRec] = None
        self.newsasrec_max_len: int = 50

    def load(self) -> None:
        print(f"Loading inference service on device: {self.device}")
        if torch.cuda.is_available():
            print(f"GPU: {torch.cuda.get_device_name(0)}")

        self.user2idx = load_json(USER2IDX_PATH)
        self.item2idx = load_json(ITEM2IDX_PATH)

        reverse = load_json(REVERSE_MAPPINGS_PATH)
        self.idx2item = [str(x) for x in reverse["idx2item"]]

        self.top_pop = np.load(TOP_POP_PATH)
        self.ease_items_global = np.load(EASE_ITEMS_PATH)
        self.ease_b = np.load(EASE_B_PATH)

        self.global_to_local = {
            int(global_idx): local_idx
            for local_idx, global_idx in enumerate(self.ease_items_global.tolist())
        }

        train = pd.read_parquet(TRAIN_PATH)
        self.x_train = build_interaction_matrix(train, self.user2idx, self.item2idx)
        self.seen = build_seen_items(train, self.user2idx, self.item2idx)
        self.user_histories = build_user_histories(train, self.user2idx, self.item2idx)

        self._load_newsasrec()
        print("Inference service loaded successfully")

    def _load_newsasrec(self) -> None:
        if not NEWSASREC_STATE_PATH.exists():
            raise FileNotFoundError(f"NewSASRec state dict not found: {NEWSASREC_STATE_PATH}")
        if not NEWSASREC_CONFIG_PATH.exists():
            raise FileNotFoundError(f"NewSASRec config not found: {NEWSASREC_CONFIG_PATH}")

        config = load_json(NEWSASREC_CONFIG_PATH)
        self.newsasrec_max_len = int(config["max_len"])

        model = NewSASRec(
            num_items=int(config["num_items"]),
            max_len=int(config["max_len"]),
            hidden_dim=int(config["hidden_dim"]),
            n_heads=int(config["n_heads"]),
            n_blocks=int(config["n_blocks"]),
            dropout_rate=float(config["dropout_rate"]),
        )

        state_dict = torch.load(
            NEWSASREC_STATE_PATH,
            map_location=self.device,
        )
        model.load_state_dict(state_dict)
        model.to(self.device)
        model.eval()

        self.newsasrec = model

    def recommend_popularity(self, user_idx: int, top_k: int, filter_seen: bool) -> List[str]:
        assert self.top_pop is not None

        seen_items = self.seen.get(user_idx, set()) if filter_seen else set()
        recs = [int(item) for item in self.top_pop if item not in seen_items]
        recs = recs[:top_k]
        return [self.idx2item[i] for i in recs]

    def recommend_ease_candidates(
            self,
            user_idx: int,
            top_k: int,
            filter_seen: bool,
    ) -> List[int]:
        """
        Возвращает глобальные item indices от EASE.
        """
        assert self.x_train is not None
        assert self.ease_items_global is not None
        assert self.ease_b is not None

        user_vector_local = self.x_train[user_idx, :][:, self.ease_items_global]
        scores_local = user_vector_local @ self.ease_b
        scores_local = np.asarray(scores_local).ravel()

        if filter_seen:
            user_seen_global = self.seen.get(user_idx, set())
            for global_item in user_seen_global:
                local_idx = self.global_to_local.get(global_item)
                if local_idx is not None:
                    scores_local[local_idx] = -np.inf

        top_local = np.argsort(-scores_local)[:top_k]
        top_global = self.ease_items_global[top_local]
        return [int(x) for x in top_global.tolist()]

    def _prepare_user_sequence(self, user_idx: int) -> torch.Tensor:
        history = self.user_histories.get(user_idx, [])
        history = history[-self.newsasrec_max_len :]

        pad_len = self.newsasrec_max_len - len(history)
        padded = [0] * pad_len + history

        seq_tensor = torch.tensor([padded], dtype=torch.long, device=self.device)
        return seq_tensor

    def rerank_with_newsasrec(
            self,
            user_idx: int,
            candidate_global_ids: List[int],
            top_k: int,
            filter_seen: bool,
    ) -> List[str]:
        assert self.newsasrec is not None
        assert self.top_pop is not None

        if not candidate_global_ids:
            return self.recommend_popularity(user_idx, top_k, filter_seen)

        # global item idx [0..n-1] -> model item idx [1..n]
        candidate_model_ids = [global_id + 1 for global_id in candidate_global_ids]

        user_seq = self._prepare_user_sequence(user_idx)
        candidate_tensor = torch.tensor(
            [candidate_model_ids],
            dtype=torch.long,
            device=self.device,
        )

        with torch.no_grad():
            scores = self.newsasrec.score_candidates(user_seq, candidate_tensor)
            scores = scores.squeeze(0).detach().cpu().numpy()

        order = np.argsort(-scores)

        user_seen = self.seen.get(user_idx, set()) if filter_seen else set()
        ranked_global: List[int] = []
        used: set[int] = set()

        for idx in order:
            global_item = candidate_global_ids[int(idx)]
            if global_item in used:
                continue
            if filter_seen and global_item in user_seen:
                continue
            used.add(global_item)
            ranked_global.append(global_item)
            if len(ranked_global) >= top_k:
                break

        # если после EASE+rerank кандидатов не хватило, добиваем popularity
        if len(ranked_global) < top_k:
            for pop_item in self.top_pop.tolist():
                pop_item = int(pop_item)
                if pop_item in used:
                    continue
                if filter_seen and pop_item in user_seen:
                    continue
                used.add(pop_item)
                ranked_global.append(pop_item)
                if len(ranked_global) >= top_k:
                    break

        return [self.idx2item[i] for i in ranked_global[:top_k]]

    def recommend(self, user_id: str, top_k: int, filter_seen: bool) -> RecommendResponse:
        assert self.top_pop is not None

        user_idx = self.user2idx.get(str(user_id))

        if user_idx is None:
            # cold start user -> popularity fallback
            recs = [self.idx2item[int(i)] for i in self.top_pop[:top_k]]
            return RecommendResponse(
                user_id=str(user_id),
                recommendations=recs,
                model="popularity_fallback",
            )

        candidate_global_ids = self.recommend_ease_candidates(
            user_idx=user_idx,
            top_k=max(EASE_CANDIDATES, top_k),
            filter_seen=filter_seen,
        )

        if not candidate_global_ids:
            recs = self.recommend_popularity(user_idx, top_k, filter_seen)
            return RecommendResponse(
                user_id=str(user_id),
                recommendations=recs,
                model="popularity_fallback",
            )

        recs = self.rerank_with_newsasrec(
            user_idx=user_idx,
            candidate_global_ids=candidate_global_ids,
            top_k=top_k,
            filter_seen=filter_seen,
        )

        return RecommendResponse(
            user_id=str(user_id),
            recommendations=recs,
            model="ease+newsasrec_gr",
        )


app = FastAPI(title="Music Rec Inference Service")
service = RecommenderService()


@app.on_event("startup")
def startup_event() -> None:
    service.load()


@app.get("/health", response_model=HealthResponse)
def health() -> HealthResponse:
    return HealthResponse(status="ok")


@app.post("/recommend", response_model=RecommendResponse)
def recommend(request: RecommendRequest) -> RecommendResponse:
    try:
        return service.recommend(
            user_id=request.user_id,
            top_k=request.top_k,
            filter_seen=request.filter_seen,
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))