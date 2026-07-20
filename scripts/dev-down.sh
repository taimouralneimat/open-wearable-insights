#!/usr/bin/env bash
# Open Wearable Insights — stop local development stack
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/../infrastructure/docker"

echo "Stopping Open Wearable Insights local stack..."
docker compose --profile llm -f "$COMPOSE_DIR/docker-compose.yml" down

echo "✓ Stack stopped."