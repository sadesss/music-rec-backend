#!/usr/bin/env python3
import argparse
import json
import os
from datetime import datetime, timezone

import numpy as np
import librosa
from mutagen.mp3 import MP3


def feature_num(key: str, value: float | int | None, confidence: float = 1.0):
    if value is None:
        return None
    return {
        "key": key,
        "valueNumber": float(value),
        "valueString": None,
        "confidence": float(confidence),
    }


def feature_str(key: str, value: str | None, confidence: float = 1.0):
    if value is None or value == "":
        return None
    return {
        "key": key,
        "valueNumber": None,
        "valueString": str(value),
        "confidence": float(confidence),
    }


def safe_mean(arr):
    if arr is None or len(arr) == 0:
        return None
    return float(np.mean(arr))


def estimate_key(y, sr):
    """
    Очень простая эвристика по chroma.
    Не академически точное определение тональности, но для demo/backend подходит.
    """
    try:
        chroma = librosa.feature.chroma_stft(y=y, sr=sr)
        chroma_mean = np.mean(chroma, axis=1)
        note_names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
        idx = int(np.argmax(chroma_mean))
        return note_names[idx]
    except Exception:
        return None


def estimate_mood_hint(tempo_bpm, rms_norm, centroid_norm):
    """
    Легкая эвристика, чтобы был человекочитаемый результат.
    """
    if tempo_bpm is None or rms_norm is None or centroid_norm is None:
        return None

    if tempo_bpm >= 125 and rms_norm >= 0.12:
        return "energetic"
    if tempo_bpm < 90 and centroid_norm < 0.18:
        return "calm"
    if centroid_norm > 0.28 and tempo_bpm >= 100:
        return "bright"
    return "balanced"


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

    # Метаданные контейнера
    audio_meta = MP3(mp3_path)
    duration_seconds = getattr(audio_meta.info, "length", None)
    bitrate_kbps = getattr(audio_meta.info, "bitrate", None)
    sample_rate_meta = getattr(audio_meta.info, "sample_rate", None)
    channels = getattr(audio_meta.info, "channels", None)

    if bitrate_kbps is not None:
        bitrate_kbps = bitrate_kbps / 1000.0

    # Загрузка аудио для анализа
    # mono=True для устойчивости и простоты вычислений признаков
    y, sr = librosa.load(mp3_path, sr=None, mono=True)

    if y is None or len(y) == 0:
        raise SystemExit(f"Failed to decode audio: {mp3_path}")

    # Базовые признаки
    rms = librosa.feature.rms(y=y)[0]
    zcr = librosa.feature.zero_crossing_rate(y)[0]
    centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
    bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=sr)[0]
    rolloff = librosa.feature.spectral_rolloff(y=y, sr=sr)[0]

    # Темп
    tempo_bpm, _ = librosa.beat.beat_track(y=y, sr=sr)
    if isinstance(tempo_bpm, np.ndarray):
        tempo_bpm = float(tempo_bpm.squeeze())

    # Доп. агрегаты
    peak = float(np.max(np.abs(y))) if len(y) else None
    rms_mean = safe_mean(rms)
    dynamic_range = None
    if peak is not None and rms_mean not in (None, 0):
        dynamic_range = float(20.0 * np.log10(max(peak, 1e-12) / max(rms_mean, 1e-12)))

    # Нормированные варианты для простых эвристик
    rms_norm = rms_mean
    centroid_norm = None
    centroid_mean = safe_mean(centroid)
    if centroid_mean is not None and sr:
        centroid_norm = centroid_mean / (sr / 2.0)

    musical_key = estimate_key(y, sr)
    mood_hint = estimate_mood_hint(
        tempo_bpm=tempo_bpm,
        rms_norm=rms_norm,
        centroid_norm=centroid_norm
    )

    features = [
        feature_num("duration_seconds_actual", duration_seconds, 1.0),
        feature_num("sample_rate_hz", sample_rate_meta or sr, 1.0),
        feature_num("channels", channels, 1.0),
        feature_num("bitrate_kbps", bitrate_kbps, 1.0),

        feature_num("tempo_bpm", tempo_bpm, 0.75),
        feature_num("rms_energy", rms_mean, 0.95),
        feature_num("zero_crossing_rate", safe_mean(zcr), 0.95),
        feature_num("spectral_centroid_hz", centroid_mean, 0.9),
        feature_num("spectral_bandwidth_hz", safe_mean(bandwidth), 0.9),
        feature_num("spectral_rolloff_hz", safe_mean(rolloff), 0.9),
        feature_num("dynamic_range_db", dynamic_range, 0.85),

        feature_str("estimated_key", musical_key, 0.55),
        feature_str("mood_hint", mood_hint, 0.35),
        feature_str("analyzed_at", datetime.now(timezone.utc).isoformat(), 1.0),
    ]

    features = [f for f in features if f is not None]

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(features, f, ensure_ascii=False, indent=2)

    print(f"[analyze_track] wrote {len(features)} features to {out_path}")


if __name__ == "__main__":
    main()