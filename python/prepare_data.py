from __future__ import annotations

import json
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Dict, Any

import polars as pl


@dataclass
class DataPrepConfig:
    raw_interactions_path: str = "~/IdeaProjects/music-rec-backend/python/data/raw/zvuk-interactions.parquet"
    output_dir: str = "~/IdeaProjects/music-rec-backend/python/data/processed"
    artifacts_dir: str = "artifacts"

    sample_n_rows: int = 3_000_000
    min_user_interactions: int = 5
    min_item_interactions: int = 5

    user_col: str = "user_id"
    item_col: str = "track_id"
    time_col: str = "datetime"
    session_col: str = "session_id"
    play_duration_col: str = "play_duration"


def ensure_dirs(*dirs: str) -> None:
    for d in dirs:
        Path(d).mkdir(parents=True, exist_ok=True)


def save_json(data: Dict[str, Any], path: Path) -> None:
    with path.open("w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def load_and_sample(cfg: DataPrepConfig) -> pl.DataFrame:
    print("[1/5] Loading raw interactions...")

    df = (
        pl.scan_parquet(cfg.raw_interactions_path)
        .select(
            [
                cfg.user_col,
                cfg.item_col,
                cfg.time_col,
                cfg.play_duration_col,
                cfg.session_col,
            ]
        )
        .head(cfg.sample_n_rows)
        .collect(streaming=True)
    )

    print(f"Loaded rows: {df.height:,}")
    return df


def filter_interactions(df: pl.DataFrame, cfg: DataPrepConfig) -> pl.DataFrame:
    print("[2/5] Filtering sparse users/items...")

    df = df.sort([cfg.user_col, cfg.time_col])

    df = (
        df.with_columns(
            pl.len().over(cfg.user_col).alias("user_cnt"),
            pl.len().over(cfg.item_col).alias("item_cnt"),
        )
        .filter(
            (pl.col("user_cnt") >= cfg.min_user_interactions)
            & (pl.col("item_cnt") >= cfg.min_item_interactions)
        )
        .drop(["user_cnt", "item_cnt"])
    )

    n_users = df.select(pl.col(cfg.user_col).n_unique()).item()
    n_items = df.select(pl.col(cfg.item_col).n_unique()).item()

    print(f"Filtered rows:  {df.height:,}")
    print(f"Users left:     {n_users:,}")
    print(f"Items left:     {n_items:,}")

    return df


def make_leave_two_out_split(df: pl.DataFrame, cfg: DataPrepConfig) -> tuple[pl.DataFrame, pl.DataFrame, pl.DataFrame]:
    print("[3/5] Building leave-two-out split...")

    df = df.sort([cfg.user_col, cfg.time_col]).with_columns(
        pl.int_range(0, pl.len()).over(cfg.user_col).alias("idx"),
        pl.len().over(cfg.user_col).alias("cnt"),
    )

    # страхуемся: у пользователя должно быть >= 3 события
    df = df.filter(pl.col("cnt") >= 3)

    train = df.filter(pl.col("idx") <= pl.col("cnt") - 3).drop(["idx", "cnt"])
    val = df.filter(pl.col("idx") == pl.col("cnt") - 2).drop(["idx", "cnt"])
    test = df.filter(pl.col("idx") == pl.col("cnt") - 1).drop(["idx", "cnt"])

    print(f"Train rows: {train.height:,}")
    print(f"Val rows:   {val.height:,}")
    print(f"Test rows:  {test.height:,}")

    return train, val, test


def build_mappings(train: pl.DataFrame, cfg: DataPrepConfig) -> dict[str, Any]:
    print("[4/5] Building train-based mappings...")

    users = (
        train.select(cfg.user_col)
        .unique()
        .sort(cfg.user_col)
        .to_series()
        .to_list()
    )
    items = (
        train.select(cfg.item_col)
        .unique()
        .sort(cfg.item_col)
        .to_series()
        .to_list()
    )

    user2idx = {str(u): i for i, u in enumerate(users)}
    item2idx = {str(it): i for i, it in enumerate(items)}

    mappings = {
        "user2idx": user2idx,
        "item2idx": item2idx,
        "idx2user": users,
        "idx2item": items,
        "n_users": len(users),
        "n_items": len(items),
    }

    print(f"Train users: {len(users):,}")
    print(f"Train items: {len(items):,}")

    return mappings


def save_outputs(
        train: pl.DataFrame,
        val: pl.DataFrame,
        test: pl.DataFrame,
        mappings: dict[str, Any],
        cfg: DataPrepConfig,
) -> None:
    print("[5/5] Saving outputs...")

    output_dir = Path(cfg.output_dir)
    artifacts_dir = Path(cfg.artifacts_dir)

    train_path = output_dir / "train.parquet"
    val_path = output_dir / "val.parquet"
    test_path = output_dir / "test.parquet"

    train.write_parquet(train_path)
    val.write_parquet(val_path)
    test.write_parquet(test_path)

    save_json(asdict(cfg), artifacts_dir / "data_config.json")
    save_json(
        {
            "n_users": mappings["n_users"],
            "n_items": mappings["n_items"],
            "train_path": str(train_path),
            "val_path": str(val_path),
            "test_path": str(test_path),
        },
        artifacts_dir / "dataset_meta.json",
        )
    save_json(mappings["user2idx"], artifacts_dir / "user2idx.json")
    save_json(mappings["item2idx"], artifacts_dir / "item2idx.json")
    save_json(
        {
            "idx2user": mappings["idx2user"],
            "idx2item": mappings["idx2item"],
        },
        artifacts_dir / "reverse_mappings.json",
        )

    print(f"Saved: {train_path}")
    print(f"Saved: {val_path}")
    print(f"Saved: {test_path}")
    print(f"Saved mappings to: {artifacts_dir}")


def main() -> None:
    cfg = DataPrepConfig()

    ensure_dirs(cfg.output_dir, cfg.artifacts_dir)

    df = load_and_sample(cfg)
    df = filter_interactions(df, cfg)
    train, val, test = make_leave_two_out_split(df, cfg)
    mappings = build_mappings(train, cfg)
    save_outputs(train, val, test, mappings, cfg)

    print("\nDone.")


if __name__ == "__main__":
    main()