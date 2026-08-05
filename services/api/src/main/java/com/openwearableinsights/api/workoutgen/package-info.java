/**
 * Module: workoutgen
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 *
 * <p>Backs docs/product/parity-matrix.md row 26 ("AI workout generation").
 * Generates a structured workout (warmup / main / cooldown blocks with
 * exercises, sets, reps or hold durations, and rest) from a stated goal,
 * available equipment, and physical limitations — deterministically, from a
 * curated, versioned exercise template library this app owns. See {@code
 * workoutgen.application.WorkoutGeneratorService}'s class Javadoc for the
 * full selection algorithm.
 *
 * <p><b>Why a new module, not an extension of {@code coach} or {@code
 * insights}:</b> {@code coach}/{@code insights.application.
 * DeterministicInsightEngine} answer "how am I doing today" from the
 * computed readiness score — text explanations grounded in existing
 * numbers. This module answers a structurally different question — "give me
 * a workout" — with its own request shape (goal/equipment/limitations/
 * duration), its own curated content library (an exercise catalog, not a
 * readiness factor), and its own output shape (a structured, orderable plan,
 * not a headline/summary/actions triple). Mirrors the same reasoning
 * {@code healthspan} (row 24) and {@code monthlyreport} (row 30) already
 * documented for why a new module beat squeezing a distinct concern into an
 * existing one.
 *
 * <p><b>LLM path — deliberately not wired in this pass:</b> the acceptance
 * criteria calls for an "LLM-generated suggestion clearly labeled as such,
 * not a prescription; deterministic template fallback when LLM disabled."
 * This module ships only the deterministic template path. Following the
 * exact precedent {@code coach.adapter.in.CoachController} already
 * established for row 7 (daily coaching) — where the {@code owi.llm.enabled}
 * flag exists and is read, but no Spring AI {@code ChatClient} call is
 * actually wired — this module does not add an unverifiable Ollama call
 * either: there is no local Ollama server in this development environment
 * (confirmed unreachable), so any LLM wiring here could not be
 * end-to-end tested before shipping, only fabricated as "should work."
 * Rather than ship untested LLM code dressed up as working, this module
 * ships a real, fully-tested, deterministic generator — the part of the
 * acceptance criteria that can actually be verified — and defers LLM wiring
 * to when a reachable Ollama instance exists to test against. See {@code
 * workoutgen.domain.WorkoutSource}'s Javadoc.
 */
package com.openwearableinsights.api.workoutgen;
