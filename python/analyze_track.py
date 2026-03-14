#!/usr/bin/env python3
import argparse
import json
import os
import random
from datetime import datetime, timezone

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-mp3", required=True)
    parser.add_argument("--output-json", required=True)
    parser.add_argument("--track-id", required=True)
    args = parser.parse_args()

    mp3_path = args.input_mp3
    out_path = args.output_json

    if not os.path.exists(mp3_path):
        raise SystemExit(f"Input mp3 not found: {mp3_path}")

    # --- STUB FEATURES ---
    # Integration point:
    # Replace this with real audio analysis (e.g., librosa) to compute tempo, energy, mood, etc.
    tempo_bpm = random.choice([90, 100, 110, 120, 130, 140])
    mood = random.choice(["calm", "happy", "energetic", "sad"])
    genre_guess = random.choice(["rock", "pop", "hiphop", "electronic", "jazz"])
    energy = round(random.random(), 3)

    features = [
        {"key": "tempo_bpm", "valueNumber": float(tempo_bpm), "valueString": None, "confidence": 0.6},
        {"key": "mood", "valueString": mood, "valueNumber": None, "confidence": 0.5},
        {"key": "genre_guess", "valueString": genre_guess, "valueNumber": None, "confidence": 0.4},
        {"key": "energy", "valueNumber": float(energy), "valueString": None, "confidence": 0.5},
        {"key": "analyzed_at", "valueString": datetime.now(timezone.utc).isoformat(), "valueNumber": None, "confidence": 1.0}
    ]

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(features, f, ensure_ascii=False, indent=2)

    print(f"[analyze_track] wrote {len(features)} features to {out_path}")

if __name__ == "__main__":
    main()
