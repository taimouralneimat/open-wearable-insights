#!/usr/bin/env bash
# Open Wearable Insights — load synthetic data + synthetic FIT fixture
# Uses ONLY synthetic data. No real health data.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
API_DIR="$SCRIPT_DIR/../services/api"

echo "Loading synthetic wearable data + synthetic FIT fixture..."
echo "(No real health data is used.)"

cd "$API_DIR"
./gradlew bootRun --args='--load-synthetic' || true

echo ""
echo "✓ Synthetic data loaded. Open the dashboard to view a provisional readiness score."