#!/usr/bin/env python3
import argparse
import json
import os
from datetime import datetime, timezone

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--training-data", required=True)
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--notes", required=False, default="")
    args = parser.parse_args()

    training_path = args.training_data
    out_dir = args.output_dir

    if not os.path.exists(training_path):
        raise SystemExit(f"Training data not found: {training_path}")

    # --- STUB TRAINING ---
    # Integration point:
    # Replace this with real training (e.g., implicit MF, LightFM, deep model, etc.)
    # For now we just read file size and fabricate metrics.

    size_bytes = os.path.getsize(training_path)
    model_version = datetime.now(timezone.utc).strftime("model_%Y%m%dT%H%M%SZ")

    metrics = {
        "modelVersion": model_version,
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "trainingDataBytes": size_bytes,
        "precisionAt10": 0.10,
        "recallAt10": 0.05,
        "notes": args.notes
    }

    model = {
        "modelVersion": model_version,
        "type": "stub",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "params": {
            "exampleParam": 123
        }
    }

    os.makedirs(out_dir, exist_ok=True)
    metrics_path = os.path.join(out_dir, "metrics.json")
    model_path = os.path.join(out_dir, "model.json")

    with open(metrics_path, "w", encoding="utf-8") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=2)

    with open(model_path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=2)

    print(f"[train_model] wrote metrics to {metrics_path}")
    print(f"[train_model] wrote model to {model_path}")

if __name__ == "__main__":
    main()
