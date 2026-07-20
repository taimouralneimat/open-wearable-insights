# Runbook — Local Development

## Prerequisites

- **Java 21 LTS** (JDK). Set `JAVA_HOME` if `javac` is not on PATH.
- **Docker** + Docker Compose.
- **Flutter** (stable channel) for client work. Install via
  `brew install --cask flutter` if absent.
- **Ollama** (optional) for local AI. Install via `brew install ollama`.
  The app works fully without it.

## First-time setup

```bash
# 1. Clone
git clone https://github.com/taimour-dev/open-wearable-insights.git
cd open-wearable-insights

# 2. Start local infrastructure (Postgres + TimescaleDB; Ollama if installed)
./scripts/dev-up.sh

# 3. Run the backend
cd services/api
./gradlew bootRun
# API on http://127.0.0.1:8080

# 4. In a new terminal, run the Flutter web client
cd apps/flutter
flutter pub get
flutter run -d chrome
```

## Loading synthetic data (Phase 1)

```bash
./scripts/load-synthetic.sh
```

This loads synthetic wearable data and a synthetic FIT fixture. No real health
data is used.

## Stopping

```bash
./scripts/dev-down.sh
```

## Common Gradle tasks

```bash
cd services/api
./gradlew build          # build + test
./gradlew test           # run tests
./gradlew bootRun        # run the API
./gradlew cyclonedxBom   # generate SBOM
```

## Common Flutter tasks

```bash
cd apps/flutter
flutter pub get
flutter analyze
flutter test
flutter run -d chrome
```

## LLM-disabled mode

Stop Ollama (or never start it). The app continues to function via the
deterministic fallback. LLM-only features are marked unavailable.

## No-network mode

Disconnect from the network. All local features continue to work.

## Troubleshooting

- **Port 8080 in use**: another process is using it. Stop it or change the
  port in `application.yml`.
- **Postgres not starting**: ensure Docker is running and no other Postgres
  is bound to `127.0.0.1:5432`.
- **`javac` not found**: set `JAVA_HOME` to your JDK 21 install.
- **Flutter not found**: run `brew install --cask flutter`.

## Health-safety reminder

This is a wellness/fitness analytics product, not a medical device. Do not use
it for diagnosis or treatment decisions.