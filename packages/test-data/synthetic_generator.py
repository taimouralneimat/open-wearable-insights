#!/usr/bin/env python3
"""
Open Wearable Insights — Synthetic Data Generator

Generates SYNTHETIC wearable health data for development and testing.
NO REAL HEALTH DATA is used. All values are randomly generated within
physiologically plausible ranges.

Usage:
    python3 synthetic_generator.py [--days 30] [--output ./]

Outputs:
    synthetic-measurements.json  — synthetic time-series measurements
    synthetic-activity.fit       — synthetic FIT fixture (placeholder bytes)
"""

import argparse
import json
import random
from datetime import datetime, timedelta, timezone

METRIC_TYPES = [
    ("hr", "bpm", 50, 180),
    ("hrv", "ms", 20, 120),
    ("rhr", "bpm", 45, 70),
    ("steps", "count", 0, 20000),
    ("calories", "kcal", 1500, 3500),
    ("stress", "score", 0, 100),
    ("spo2", "percent", 90, 100),
    ("respiration", "brpm", 10, 25),
]

SLEEP_STAGES = ["deep", "rem", "light", "awake"]


def generate_measurements(days: int) -> list[dict]:
    """Generate synthetic measurements for the given number of days."""
    measurements = []
    now = datetime.now(timezone.utc)
    for day in range(days):
        day_start = now - timedelta(days=day)
        for metric, unit, low, high in METRIC_TYPES:
            # 4 readings per day per metric
            for hour in range(0, 24, 6):
                t = day_start + timedelta(hours=hour)
                measurements.append({
                    "time": t.isoformat(),
                    "source_tz": "UTC",
                    "metric_type": metric,
                    "value": round(random.uniform(low, high), 1),
                    "unit": unit,
                    "source": "synthetic",
                })
        # Sleep session
        for stage in SLEEP_STAGES:
            t = day_start + timedelta(hours=2)
            measurements.append({
                "time": t.isoformat(),
                "source_tz": "UTC",
                "metric_type": "sleep_stage",
                "value": float(SLEEP_STAGES.index(stage)),
                "unit": "stage",
                "source": "synthetic",
            })
    return measurements


def generate_synthetic_fit(path: str) -> None:
    """
    Generate a placeholder synthetic FIT file.

    NOTE: This is a minimal placeholder. A proper synthetic FIT file requires
    the Garmin FIT SDK encoder. For Phase 1, this placeholder is sufficient
    to test the import pipeline's file-handling and idempotency; the actual
    FIT parsing will use the Garmin FIT SDK in the backend.
    """
    # Minimal placeholder: a text marker so the file is non-empty and
    # clearly synthetic. The backend ingestion module will handle real
    # FIT parsing via the Garmin FIT SDK.
    with open(path, "wb") as f:
        f.write(b"SYNTHETIC-FIT-PLACEHOLDER-OPEN-WEARABLE-INSIGHTS")


def main() -> None:
    parser = argparse.ArgumentParser(description="Synthetic data generator")
    parser.add_argument("--days", type=int, default=30, help="Number of days")
    parser.add_argument("--output", default="./", help="Output directory")
    args = parser.parse_args()

    random.seed(42)  # Deterministic for golden tests

    measurements = generate_measurements(args.days)
    json_path = f"{args.output}synthetic-measurements.json"
    with open(json_path, "w") as f:
        json.dump(measurements, f, indent=2)
    print(f"✓ Generated {len(measurements)} synthetic measurements → {json_path}")

    fit_path = f"{args.output}synthetic-activity.fit"
    generate_synthetic_fit(fit_path)
    print(f"✓ Generated synthetic FIT fixture → {fit_path}")


if __name__ == "__main__":
    main()