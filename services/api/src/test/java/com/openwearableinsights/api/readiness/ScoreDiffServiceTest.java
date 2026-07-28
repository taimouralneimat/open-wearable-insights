package com.openwearableinsights.api.readiness;

import com.openwearableinsights.api.readiness.application.ScoreDiffService;
import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.FactorContribution.Direction;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import com.openwearableinsights.api.readiness.domain.ScoreDiff;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the ScoreDiffService (Phase 3 EC2).
 *
 * Tests that the diff correctly identifies which factors moved,
 * by how much, and in which direction.
 */
class ScoreDiffServiceTest {

    private final ScoreDiffService service = new ScoreDiffService();

    @Test
    void computeDiff_identifiesImprovedAndWorsenedFactors() {
        ReadinessScore today = new ReadinessScore(
                65, "0.1", false, "28-day rolling", "high", "good",
                List.of(
                        new FactorContribution("HRV deviation", 8.0, "ms", Direction.POSITIVE, 6.0, "app_computed"),
                        new FactorContribution("RHR deviation", -2.0, "bpm", Direction.POSITIVE, 5.0, "app_computed"),
                        new FactorContribution("Sleep duration vs. need", -1.0, "hours", Direction.NEGATIVE, -7.5, "app_computed")
                ),
                "All factors present.", "Readiness is 65/100.", "v0.1", Instant.now()
        );

        ReadinessScore prior = new ReadinessScore(
                55, "0.1", false, "28-day rolling", "high", "good",
                List.of(
                        new FactorContribution("HRV deviation", 2.0, "ms", Direction.POSITIVE, 1.5, "app_computed"),
                        new FactorContribution("RHR deviation", 1.0, "bpm", Direction.NEGATIVE, -2.5, "app_computed"),
                        new FactorContribution("Sleep duration vs. need", -0.5, "hours", Direction.NEGATIVE, -3.75, "app_computed")
                ),
                "All factors present.", "Readiness is 55/100.", "v0.1", Instant.now()
        );

        ScoreDiff diff = service.computeDiff(today, prior, "yesterday");

        assertThat(diff.todayScore()).isEqualTo(65);
        assertThat(diff.priorScore()).isEqualTo(55);
        assertThat(diff.scoreDelta()).isEqualTo(10);
        assertThat(diff.factorDiffs()).hasSize(3);

        // HRV improved (contribution went from 1.5 to 6.0)
        ScoreDiff.FactorDiff hrvDiff = diff.factorDiffs().stream()
                .filter(f -> f.name().contains("HRV")).findFirst().orElseThrow();
        assertThat(hrvDiff.direction()).isEqualTo("improved");
        assertThat(hrvDiff.contributionDelta()).isEqualTo(4.5);

        // RHR improved (contribution went from -2.5 to 5.0)
        ScoreDiff.FactorDiff rhrDiff = diff.factorDiffs().stream()
                .filter(f -> f.name().contains("RHR")).findFirst().orElseThrow();
        assertThat(rhrDiff.direction()).isEqualTo("improved");

        // Sleep worsened (contribution went from -3.75 to -7.5)
        ScoreDiff.FactorDiff sleepDiff = diff.factorDiffs().stream()
                .filter(f -> f.name().contains("Sleep")).findFirst().orElseThrow();
        assertThat(sleepDiff.direction()).isEqualTo("worsened");
        assertThat(sleepDiff.contributionDelta()).isEqualTo(-3.75);

        assertThat(diff.biggestPositive()).contains("RHR");
        assertThat(diff.biggestNegative()).contains("Sleep");
        assertThat(diff.summary()).contains("improved by 10");
    }

    @Test
    void computeDiff_unchangedScore_returnsZeroDelta() {
        ReadinessScore today = new ReadinessScore(
                60, "0.1", false, "28-day rolling", "high", "good",
                List.of(new FactorContribution("HRV deviation", 5.0, "ms", Direction.POSITIVE, 3.75, "app_computed")),
                "All factors present.", "Readiness is 60/100.", "v0.1", Instant.now()
        );

        ReadinessScore prior = new ReadinessScore(
                60, "0.1", false, "28-day rolling", "high", "good",
                List.of(new FactorContribution("HRV deviation", 5.0, "ms", Direction.POSITIVE, 3.75, "app_computed")),
                "All factors present.", "Readiness is 60/100.", "v0.1", Instant.now()
        );

        ScoreDiff diff = service.computeDiff(today, prior, "yesterday");

        assertThat(diff.scoreDelta()).isZero();
        assertThat(diff.factorDiffs().get(0).direction()).isEqualTo("unchanged");
        assertThat(diff.summary()).contains("unchanged");
    }

    @Test
    void computeDiff_newFactorToday_markedAsImproved() {
        ReadinessScore today = new ReadinessScore(
                70, "0.1", false, "28-day rolling", "high", "good",
                List.of(
                        new FactorContribution("HRV deviation", 8.0, "ms", Direction.POSITIVE, 6.0, "app_computed"),
                        new FactorContribution("Stress", 20.0, "score", Direction.POSITIVE, 2.25, "app_computed")
                ),
                "All factors present.", "Readiness is 70/100.", "v0.1", Instant.now()
        );

        ReadinessScore prior = new ReadinessScore(
                60, "0.1", false, "28-day rolling", "high", "good",
                List.of(new FactorContribution("HRV deviation", 5.0, "ms", Direction.POSITIVE, 3.75, "app_computed")),
                "All factors present.", "Readiness is 60/100.", "v0.1", Instant.now()
        );

        ScoreDiff diff = service.computeDiff(today, prior, "yesterday");

        // Stress is new today
        ScoreDiff.FactorDiff stressDiff = diff.factorDiffs().stream()
                .filter(f -> f.name().equals("Stress")).findFirst().orElseThrow();
        assertThat(stressDiff.direction()).isEqualTo("improved");
        assertThat(stressDiff.priorValue()).isZero();
    }

    @Test
    void computeDiff_factorDiffsSortedByImpact() {
        ReadinessScore today = new ReadinessScore(
                70, "0.1", false, "28-day rolling", "high", "good",
                List.of(
                        new FactorContribution("Small change", 1.0, "x", Direction.POSITIVE, 0.5, "app_computed"),
                        new FactorContribution("Big change", 10.0, "x", Direction.POSITIVE, 5.0, "app_computed")
                ),
                "All factors present.", "Readiness is 70/100.", "v0.1", Instant.now()
        );

        ReadinessScore prior = new ReadinessScore(
                60, "0.1", false, "28-day rolling", "high", "good",
                List.of(
                        new FactorContribution("Small change", 0.5, "x", Direction.POSITIVE, 0.25, "app_computed"),
                        new FactorContribution("Big change", 2.0, "x", Direction.POSITIVE, 1.0, "app_computed")
                ),
                "All factors present.", "Readiness is 60/100.", "v0.1", Instant.now()
        );

        ScoreDiff diff = service.computeDiff(today, prior, "yesterday");

        // Biggest mover should be first
        assertThat(diff.factorDiffs().get(0).name()).isEqualTo("Big change");
        assertThat(Math.abs(diff.factorDiffs().get(0).contributionDelta()))
                .isGreaterThan(Math.abs(diff.factorDiffs().get(1).contributionDelta()));
    }
}
