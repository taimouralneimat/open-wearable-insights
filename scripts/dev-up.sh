#!/usr/bin/env bash
# Open Wearable Insights — start local development stack
# Starts PostgreSQL + TimescaleDB on loopback. Ollama is optional (--llm).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/../infrastructure/docker"

if [ "${1:-}" = "--llm" ]; then
  echo "Starting PostgreSQL + TimescaleDB + Ollama (loopback only)..."
  docker compose --profile llm -f "$COMPOSE_DIR/docker-compose.yml" up -d
else
  echo "Starting PostgreSQL + TimescaleDB (loopback only)..."
  echo "  (Ollama is optional; use --llm to include it)"
  docker compose -f "$COMPOSE_DIR/docker-compose.yml" up -d
fi

echo ""
echo "Waiting for Postgres to be healthy..."
for i in $(seq 1 30); do
  if docker exec open-wearable-insights-postgres pg_isready -U owi -d owi >/dev/null 2>&1; then
    echo "✓ Postgres is ready on 127.0.0.1:5432"
    break
  fi
  sleep 1
done

if [ "${1:-}" = "--llm" ]; then
  echo "Ollama is on 127.0.0.1:11434 (pull a model with: ollama pull qwen3:8b)"
fi

echo ""
echo "Next:"
echo "  cd services/api && ./gradlew bootRun"
echo "  cd apps/flutter && flutter run -d chrome"