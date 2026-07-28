#!/usr/bin/env bash
# Open Wearable Insights — stop backend + Flutter dev processes started by dev-start.sh
#
# Postgres/Ollama are left running (they're meant to be persistent local
# infra) — use ./scripts/dev-down.sh separately to stop those too.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PID_DIR="$ROOT_DIR/.dev-pids"

stop_pid_file() {
  local name="$1" file="$PID_DIR/$2"
  if [ -f "$file" ]; then
    local pid
    pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null
      echo "Stopped $name (pid $pid)"
    fi
    rm -f "$file"
  fi
}

stop_pid_file "backend" "backend.pid"
stop_pid_file "flutter" "flutter.pid"

# The backend actually runs as a child Java process under the gradlew
# wrapper, and Flutter spawns its own Chrome-controlling process — killing
# only the stored PID can leave these orphaned holding port 8080 / a stale
# Chrome session. Clean those up explicitly too.
pkill -f "OpenWearableInsightsApplication" 2>/dev/null && echo "Stopped orphaned backend process"
pkill -f "flutter_tools.snapshot run -d chrome" 2>/dev/null && echo "Stopped orphaned Flutter process"

echo "Done. (Postgres/Ollama still running — use ./scripts/dev-down.sh to stop those too.)"
