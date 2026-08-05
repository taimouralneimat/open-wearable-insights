package com.openwearableinsights.api.workoutgen.domain;

/**
 * Which engine actually produced a {@link GeneratedWorkout} — always
 * present on the response (see {@code GeneratedWorkout#source} and
 * {@code #disclaimer}), per the row 26 acceptance criteria's "clearly
 * labeled as such" requirement.
 *
 * <p>Only {@link #DETERMINISTIC_TEMPLATE} is actually produced by this
 * codebase today. {@link #LLM} exists as a documented placeholder for a
 * future Ollama-backed path (see the {@code workoutgen} module's
 * package-info "LLM path" section for exactly why that path isn't wired in
 * this pass) — it is never returned by {@code WorkoutGeneratorService} and
 * must not be treated as implemented.
 */
public enum WorkoutSource {
    DETERMINISTIC_TEMPLATE,
    LLM
}
