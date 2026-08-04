/**
 * Module: strength
 *
 * <p>Strength training tracking — docs/product/parity-matrix.md row 25.
 * Manual sets/reps/weight logging ({@code strength_workouts} /
 * {@code strength_sets}, see V09 migration) plus a "Strength Activity Time"
 * trend view that combines those manual logs with real Garmin-derived
 * strength sessions already flowing through the {@code activities} table
 * (see {@code activities} module, ADR-0006). Deliberately a separate
 * module/table pair from {@code activities}: rep-level detail doesn't fit
 * that table's session-level cardio-metric shape (see V09 migration
 * comment).
 *
 * <p>Deliberately does NOT parse Garmin FIT's set-level strength messages
 * (e.g. {@code SetMesg} with per-set reps/weight) — a real, separate,
 * larger piece of work, analogous to why the biomarkers module (row 22)
 * deferred PDF import. The Garmin side of the trend uses only the
 * session-level sport/duration data {@code GarminFitConnector} already
 * parses.
 *
 * <p>See docs/architecture/component-view.md and ADR-0005 for module strategy.
 */
package com.openwearableinsights.api.strength;
