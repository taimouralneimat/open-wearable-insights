/**
 * Workout generator domain types — goal/equipment/limitation vocabulary and
 * the generated-workout shape. Not currently exposed as a Spring Modulith
 * {@code @NamedInterface}: nothing outside this module depends on these
 * types today (unlike e.g. {@code vo2max.domain}, which {@code healthspan}
 * composes).
 */
package com.openwearableinsights.api.workoutgen.domain;
