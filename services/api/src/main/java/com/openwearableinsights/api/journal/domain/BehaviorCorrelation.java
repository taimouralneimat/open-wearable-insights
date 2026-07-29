package com.openwearableinsights.api.journal.domain;

import java.util.List;

/**
 * An exploratory correlation between a logged behavior and readiness
 * scores on days that behavior was/wasn't logged.
 *
 * <p>This is a simple group-mean comparison, not a causal model — the
 * project's core principle is explainability over sophistication, and a
 * correlation this small-n shouldn't pretend to more statistical rigor
 * than it has (e.g. no p-values implying significance testing that a
 * handful of data points can't actually support).
 *
 * @param loggedDayCount        distinct days this behavior was logged
 *                              (that also have a readiness score)
 * @param notLoggedDayCount     distinct days with a readiness score but
 *                              no entry for this behavior
 * @param avgReadinessWhenLogged     mean readiness score on logged days
 * @param avgReadinessWhenNotLogged  mean readiness score on non-logged days
 * @param difference            avgReadinessWhenLogged - avgReadinessWhenNotLogged
 * @param confidence            always "low" or "medium" — never "high" for
 *                              a group-mean comparison this small; see
 *                              MIN_SAMPLE_SIZE_PER_GROUP
 * @param limitations           always includes an explicit correlation-not-
 *                              causation caution
 */
public record BehaviorCorrelation(
        String category,
        String behavior,
        int loggedDayCount,
        int notLoggedDayCount,
        double avgReadinessWhenLogged,
        double avgReadinessWhenNotLogged,
        double difference,
        String confidence,
        List<String> limitations
) {}
