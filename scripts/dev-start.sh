#!/usr/bin/env bash
# Open Wearable Insights — start backend + Flutter web in the background
#
# Runs both as background processes with logs under .dev-logs/ and PIDs
# under .dev-pids/. Chrome opens automatically for the Flutter app.
#
# Note: hot-reload keypresses (r/R) need a live terminal attached to
# `flutter run`, so this script is best for "get it running to click
# around," not active coding sessions — for that, run the two commands
# directly in separate terminals instead.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_DIR="$ROOT_DIR/.dev-logs"
PID_DIR="$ROOT_DIR/.dev-pids"
mkdir -p "$LOG_DIR" "$PID_DIR"

echo "Ensuring Postgres is up..."
"$SCRIPT_DIR/dev-up.sh"
echo ""

if [ -f "$PID_DIR/backend.pid" ] && kill -0 "$(cat "$PID_DIR/backend.pid")" 2>/dev/null; then
  echo "Backend already running (pid $(cat "$PID_DIR/backend.pid"))"
else
  echo "Starting backend..."
  # Note: the stored PID is gradlew's own process, not the JVM it hands off
  # to via Gradle's daemon — that's fine, dev-stop.sh's pkill -f fallback
  # targets the actual application process regardless of this PID.
  (
    cd "$ROOT_DIR/services/api"
    nohup ./gradlew bootRun --console=plain > "$LOG_DIR/backend.log" 2>&1 &
    echo $! > "$PID_DIR/backend.pid"
  )
fi

echo "Waiting for backend health..."
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q 200; then
    echo "✓ Backend is up on http://127.0.0.1:8080"
    break
  fi
  sleep 1
done

if [ -f "$PID_DIR/flutter.pid" ] && kill -0 "$(cat "$PID_DIR/flutter.pid")" 2>/dev/null; then
  echo "Flutter already running (pid $(cat "$PID_DIR/flutter.pid"))"
else
  echo "Starting Flutter web (Chrome will open automatically)..."
  (
    cd "$ROOT_DIR/apps/flutter"
    nohup flutter run -d chrome > "$LOG_DIR/flutter.log" 2>&1 &
    echo $! > "$PID_DIR/flutter.pid"
  )
fi

echo ""
echo "Backend log:  $LOG_DIR/backend.log"
echo "Flutter log:  $LOG_DIR/flutter.log"
echo "Stop everything with: ./scripts/dev-stop.sh"
