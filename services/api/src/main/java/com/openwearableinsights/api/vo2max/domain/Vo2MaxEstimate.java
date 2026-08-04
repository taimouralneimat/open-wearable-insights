package com.openwearableinsights.api.vo2max.domain;

import java.time.Instant;
import java.util.List;

/**
 * A VO2max (maximal oxygen uptake) estimate computed from the account's own
 * real heart-rate data — see {@code vo2max.application.Vo2MaxService} for
 * the cited formula.
 *
 * <p>{@code vo2Max} is {@code null} whenever it can't be honestly computed —
 * never a fabricated placeholder number. This happens in two distinct
 * cases, both explained in {@code limitations}: (1) no real data at all (no
 * qualifying activity with a recorded max heart rate, or no
 * resting-heart-rate baseline yet) — in which case {@code hrMaxBpm}/{@code
 * hrRestBpm} are also {@code null}; or (2) real inputs exist but fall
 * outside a physiologically plausible range together — in which case the
 * raw {@code hrMaxBpm}/{@code hrRestBpm} are still shown (more transparent
 * than hiding suspect-but-real numbers outright) even though no
 * {@code vo2Max} is derived from them.
 *
 * @param vo2Max        estimated VO2max in mL/kg/min, or {@code null} if not
 *                       computable yet
 * @param hrMaxBpm       the highest real recorded heart rate this estimate is
 *                       based on
 * @param hrRestBpm      the personal resting-heart-rate baseline this
 *                       estimate is based on (see
 *                       {@code readiness.application.BaselineService})
 * @param hrMaxSource    plain-language description of where/when
 *                       {@code hrMaxBpm} came from (which real activity data,
 *                       what window)
 * @param hrRestSource   plain-language description of where {@code hrRestBpm}
 *                       came from (the baseline window and sample size)
 * @param algorithmVersion versioned identifier for the formula/logic used
 * @param confidence     "none" (no data), "low", or "medium" — this is a
 *                       single-source, non-personalized-coefficient
 *                       regression estimate with a documented margin of
 *                       error, so it never claims "high" confidence
 *                       regardless of how much data feeds it; see
 *                       {@code Vo2MaxService} class Javadoc
 * @param methodology    citation string naming the published formula this
 *                       estimate uses
 * @param limitations    honest, user-facing caveats — always includes the
 *                       formula's documented margin of error and that this is
 *                       not a lab-measured or medical assessment
 * @param computedAt     when this estimate was computed (this endpoint
 *                       computes on demand rather than from a stored
 *                       snapshot — see {@code Vo2MaxService} class Javadoc)
 */
public record Vo2MaxEstimate(
        Double vo2Max,
        Integer hrMaxBpm,
        Double hrRestBpm,
        String hrMaxSource,
        String hrRestSource,
        String algorithmVersion,
        String confidence,
        String methodology,
        List<String> limitations,
        Instant computedAt
) {}
