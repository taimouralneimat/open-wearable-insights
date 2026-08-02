package com.openwearableinsights.api.garminconnect.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.connections.domain.ParsedMeasurement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GarminConnectSyncService#reconstructSleepStages} —
 * the honesty-critical piece that turns Garmin's real daily aggregate sleep
 * seconds (deep/light/rem/awake) into the {@code sleep_stage} 15-minute-row
 * storage grain the rest of the app (SleepInsightService, BaselineService)
 * already assumes. See the class javadoc for why intra-night stage order is
 * a reconstruction while per-stage totals are preserved exactly from real
 * Garmin data.
 */
class GarminConnectSyncServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Dependencies are unused by reconstructSleepStages (pure function, no I/O).
    private final GarminConnectSyncService service =
            new GarminConnectSyncService(null, null, null, null, null);

    @Test
    void reconstructsCorrectRowCountPerStage_fromRealAggregateSeconds() throws Exception {
        long start = Instant.parse("2026-07-01T05:00:00Z").toEpochMilli();
        JsonNode dto = MAPPER.readTree("""
                {
                  "sleepStartTimestampGMT": %d,
                  "deepSleepSeconds": 5400,
                  "lightSleepSeconds": 14400,
                  "remSleepSeconds": 5400,
                  "awakeSleepSeconds": 900
                }
                """.formatted(start));

        List<ParsedMeasurement> stages = service.reconstructSleepStages(dto);

        // 5400s/900s = 6 deep, 14400/900 = 16 light, 5400/900 = 6 rem, 900/900 = 1 awake.
        long deep = stages.stream().filter(m -> m.value() == 0.0).count();
        long light = stages.stream().filter(m -> m.value() == 2.0).count();
        long rem = stages.stream().filter(m -> m.value() == 1.0).count();
        long awake = stages.stream().filter(m -> m.value() == 3.0).count();

        assertThat(deep).isEqualTo(6);
        assertThat(light).isEqualTo(16);
        assertThat(rem).isEqualTo(6);
        assertThat(awake).isEqualTo(1);
        assertThat(stages).allMatch(m -> m.metricType().equals("sleep_stage") && m.unit().equals("stage"));
    }

    @Test
    void allRowsFallWithinTheRealSleepWindow_chronologicallyOrdered() throws Exception {
        long start = Instant.parse("2026-07-01T05:00:00Z").toEpochMilli();
        JsonNode dto = MAPPER.readTree("""
                {
                  "sleepStartTimestampGMT": %d,
                  "deepSleepSeconds": 1800,
                  "lightSleepSeconds": 1800,
                  "remSleepSeconds": 0,
                  "awakeSleepSeconds": 0
                }
                """.formatted(start));

        List<ParsedMeasurement> stages = service.reconstructSleepStages(dto);

        assertThat(stages).hasSize(4); // 2 deep + 2 light, 15 min apart
        assertThat(stages.get(0).time()).isEqualTo(Instant.ofEpochMilli(start));
        for (int i = 1; i < stages.size(); i++) {
            assertThat(stages.get(i).time()).isAfter(stages.get(i - 1).time());
        }
        // Last row is exactly 3*15min after start (real window is respected, not overrun).
        assertThat(stages.get(stages.size() - 1).time()).isEqualTo(Instant.ofEpochMilli(start + 3 * 15 * 60 * 1000L));
    }

    @Test
    void missingStageFields_defaultToZeroRowsRatherThanThrowing() throws Exception {
        JsonNode dto = MAPPER.readTree("""
                {"sleepStartTimestampGMT": 1751350800000}
                """);

        List<ParsedMeasurement> stages = service.reconstructSleepStages(dto);

        assertThat(stages).isEmpty();
    }
}
