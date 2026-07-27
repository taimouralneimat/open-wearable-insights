# Phase 1 fixes and lessons for continued development

This records what was broken in the Phase 0/1 output before it could run,
and the process discipline to apply going forward. Read this before
starting Phase 2 work.

## What was broken and had to be fixed

Backend (services/api):

1. `gradle/wrapper/gradle-wrapper.properties` was never generated/committed
   — only the jar existed, so `./gradlew` couldn't bootstrap at all.
2. `gradle/libs.versions.toml` pointed the `io.spring.dependency-management`
   plugin at the Spring Boot version (3.5.6) instead of its own release
   line (1.1.x). That plugin has no 3.5.6 release.
3. `build.gradle.kts` declared Spring Boot but never applied the `java`
   plugin, so `implementation`/`testImplementation`/`java{}` were all
   unresolved.
4. The CycloneDX SBOM task called `setIncludeBomJson`/`setIncludeBomXml`,
   methods that don't exist on that plugin version.
5. `spring-ai-ollama-spring-boot-starter` was the milestone-era artifact
   name. Spring AI 1.0 GA renamed it to `spring-ai-starter-model-ollama`.
6. `ReadinessCalculator`'s factor weights/clamp ranges had a hard ceiling
   of ~74 — even with maximally positive inputs, the score could never
   reach the 75 threshold the code's own "Good readiness" headline
   required. A real design bug, not just a test mismatch.
7. `DeterministicInsightEngine`'s limitations text said "does not diagnose
   or treat" — violates the project's own health-safety-boundary test
   (banned word "diagnos").
8. Spring Modulith verification failed: `insights`/`coach` reached into
   `readiness`'s internal `domain`/`application` packages without them
   being exposed as named interfaces.
9. `SecurityConfig` used `setAllowedOrigins(["http://localhost:*"])` — that
   method only matches literal origin strings; it does NOT support
   wildcards. Needed `setAllowedOriginPatterns()` instead. This silently
   broke every CORS request from the Flutter web dev server (which binds
   to a random port each run), surfacing as an opaque browser network
   error with no useful stack trace.

Frontend (apps/flutter):

10. `lib/app/app.dart` imported `features/readiness/dashboard_page.dart`
    with a path relative to `lib/app/` instead of `lib/` — the import
    target never existed at that path.
11. The project had `lib/` and `pubspec.yaml` but no platform runner
    scaffolding at all (no `web/`, `android/`, `ios/`) — `flutter run`/
    `build` had nothing to target.
12. `widget_test.dart` was untouched `flutter create` counter-app
    boilerplate referencing a nonexistent `MyApp` class.
13. A `Row` used `crossAxisAlignment: CrossAxisAlignment.baseline` without
    the required `textBaseline` — Flutter asserts at build time, but only
    once that widget actually renders with real data (so it didn't
    surface until #9 was fixed and the dashboard could load).

## The pattern

Every one of these traces to the same root cause: code was generated but
never actually compiled, run, or exercised end-to-end before being
committed. None of these are subtle logic errors — a single real build/run
catches every one of them immediately.

## Process rules for all future phases

- After any backend change: run `./gradlew build`. If it doesn't compile,
  the task isn't done.
- After any backend change touching business logic: run `./gradlew test`
  and read the actual failure output, not just confirm tests exist.
- Before adding a new dependency/plugin coordinate, verify the exact
  artifact ID and version exist for the Spring Boot/Spring AI version in
  use — don't rely on memory for fast-moving ecosystems (Spring AI in
  particular renamed artifacts between milestones and GA).
- After any Flutter change: run `flutter analyze` AND `flutter build web`.
  Analyze alone misses runtime assertion errors (like the textBaseline
  one) — it only catches static analysis issues.
- Before declaring a UI screen done, load it in a browser with real (or
  realistic) data flowing through it, not just a compile check. Several of
  these bugs (CORS, textBaseline) only manifest when a widget is actually
  exercised with live data.
- When touching a CORS/security config, test it with an actual
  cross-origin request (curl with an `Origin` header, or the real browser
  client) — don't assume a method supports wildcard patterns just because
  the string looks like one.
- If a calculator/scoring function has documented thresholds (e.g. "≥75 =
  good"), sanity-check that the function's actual output range can reach
  those thresholds given its own clamp/weight constants — do the
  arithmetic, don't just trust that the weights "look reasonable."
- Don't leave placeholder/boilerplate files (default `widget_test.dart`,
  default `README`) uncustomized if they reference symbols that don't
  exist in the actual project — either wire them to the real code or
  remove them.
- **Never write file contents via a shell heredoc** (`cmd << 'EOF' ... EOF`,
  especially through a tool-driven terminal). This caused multiple
  multi-hour deadlocks during Phase 2: the file's own content contained
  quotes that could desync the heredoc terminator, leaving the shell
  waiting forever for a closing marker that never arrived byte-for-byte,
  while the calling process waited forever for output that would never
  come — neither side times out, so it hangs indefinitely with no error.
  Diagnosed by finding an orphaned child shell process still holding the
  target directory as its cwd, doing zero CPU work, with no subprocess
  (e.g. no `python3`) actually running under it — proof the shell never
  got past reading the heredoc body. Use the dedicated file-write tool
  for file contents instead. If a one-off shell edit is unavoidable, use
  a single-line command (`sed`, or `python3 -c '...'` with a carefully
  escaped one-liner) — never a multi-line heredoc.
