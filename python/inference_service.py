from __future__ import annotations

import json
from pathlib import Path
from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from scipy import sparse


ARTIFACTS_DIR = Path("artifacts")
DATA_DIR = Path("data/processed")

TRAIN_PATH = DATA_DIR / "train.parquet"

USER2IDX_PATH = ARTIFACTS_DIR / "user2idx.json"
ITEM2IDX_PATH = ARTIFACTS_DIR / "item2idx.json"
REVERSE_MAPPINGS_PATH = ARTIFACTS_DIR / "reverse_mappings.json"

TOP_POP_PATH = ARTIFACTS_DIR / "top_pop.npy"
EASE_ITEMS_PATH = ARTIFACTS_DIR / "ease_items_global.npy"
EASE_B_PATH = ARTIFACTS_DIR / "ease_B.npy"


def load_json(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return json.load(f)


def build_seen_items(
        train: pd.DataFrame,
        user2idx: Dict[str, int],
        item2idx: Dict[str, int],
) -> Dict[int, set]:
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
        self.seen: Dict[int, set] = {}
        self.global_to_local: Dict[int, int] = {}

    def load(self) -> None:
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

    def recommend_popularity(self, user_idx: int, top_k: int, filter_seen: bool) -> List[str]:
        assert self.top_pop is not None

        seen_items = self.seen.get(user_idx, set()) if filter_seen else set()
        recs = [int(item) for item in self.top_pop if item not in seen_items]
        recs = recs[:top_k]
        return [self.idx2item[i] for i in recs]

    def recommend_ease(self, user_idx: int, top_k: int, filter_seen: bool) -> List[str]:
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

        result = []
        for global_idx in top_global:
            result.append(self.idx2item[int(global_idx)])
        return result

    def recommend(self, user_id: str, top_k: int, filter_seen: bool) -> RecommendResponse:
        user_idx = self.user2idx.get(str(user_id))

        if user_idx is None:
            # cold start user -> popularity fallback
            recs = [self.idx2item[int(i)] for i in self.top_pop[:top_k]]
            return RecommendResponse(
                user_id=str(user_id),
                recommendations=recs,
                model="popularity_fallback",
            )

        recs = self.recommend_ease(user_idx, top_k, filter_seen)

        if not recs:
            recs = self.recommend_popularity(user_idx, top_k, filter_seen)
            model_name = "popularity_fallback"
        else:
            model_name = "ease"

        return RecommendResponse(
            user_id=str(user_id),
            recommendations=recs,
            model=model_name,
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