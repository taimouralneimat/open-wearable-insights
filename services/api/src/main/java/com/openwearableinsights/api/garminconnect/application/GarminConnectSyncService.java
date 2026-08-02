package com.openwearableinsights.api.garminconnect.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.openwearableinsights.api.connections.domain.ParsedMeasurement;
import com.openwearableinsights.api.garminconnect.application.GarminConnectDataClient.GarminConnectApiException;
import com.openwearableinsights.api.ingestion.application.ImportBatchRepository;
import com.openwearableinsights.api.ingestion.application.MeasurementRepository;
import com.openwearableinsights.api.ingestion.domain.ImportBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pulls a date range of real historical wellness data from Garmin Connect
 * (sleep, HRV, resting HR, average stress, steps) and persists it through
 * the same {@code measurements} / {@code import_batches} tables every other
 * connector uses — see MeasurementRepository and ADR-0006. One
 * {@code import_batches} row is created per sync run, source
 * {@code "vendor_api"}.
 *
 * <p>Sleep-stage honesty note: Garmin's daily sleep summary only gives
 * aggregate seconds per stage (deep/light/rem/awake) plus the overall sleep
 * window — not the real per-segment sequence (that lives in an undocumented
 * {@code sleepLevels} array whose numeric stage encoding isn't publicly
 * confirmed, so this deliberately does not guess at it). To stay compatible
 * with the existing {@code sleep_stage} storage grain (one row per 15
 * minutes — see GarminFitConnector / SleepInsightService), each stage's real
 * aggregate duration is expanded into 15-minute rows within the real sleep
 * window. The real per-stage *totals* are preserved exactly; only the
 * intra-night *ordering* of stages is a reconstruction, not the real
 * sequence. No downstream feature (sleep debt, baselines, readiness) reads
 * stage order — only per-stage totals and the last reading's timestamp —
 * so this doesn't affect their correctness.
 */
@Service
public class GarminConnectSyncService {

    private static final Logger log = LoggerFactory.getLogger(GarminConnectSyncService.class);
    private static final long MS_PER_15_MIN = 15L * 60 * 1000;
    private static final int MAX_REFRESH_RETRIES = 1;
    private static final int MAX_CONSECUTIVE_RATE_LIMITS = 3;
    // A day costs 3 real HTTP calls to Garmin's API tier (summary/sleep/hrv).
    // This is deliberate throttling, not a performance concern — hammering
    // Garmin's servers with zero delay risks tripping their abuse detection
    // and getting the user's real Garmin account temporarily rate-limited
    // or flagged, which would be a much worse outcome than a slower sync.
    private static final long SYNC_DELAY_BETWEEN_DAYS_MS = 300;

    private final GarminConnectAccountRepository accountRepository;
    private final GarminConnectAuthClient authClient;
    private final GarminConnectDataClient dataClient;
    private final ImportBatchRepository importBatchRepository;
    private final MeasurementRepository measurementRepository;
    private final TransactionTemplate requiresNewTx;

    public GarminConnectSyncService(
            GarminConnectAccountRepository accountRepository,
            GarminConnectAuthClient authClient,
            GarminConnectDataClient dataClient,
            ImportBatchRepository importBatchRepository,
            MeasurementRepository measurementRepository,
            PlatformTransactionManager txManager
    ) {
        this.accountRepository = accountRepository;
        this.authClient = authClient;
        this.dataClient = dataClient;
        this.importBatchRepository = importBatchRepository;
        this.measurementRepository = measurementRepository;
        this.requiresNewTx = new TransactionTemplate(txManager);
        this.requiresNewTx.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW
        );
    }

    public record SyncResult(int daysAttempted, int daysWithData, int measurementsWritten, List<String> errors) {}

    /**
     * Not {@code @Transactional} — this loop makes many real, deliberately
     * throttled HTTP calls to Garmin (see SYNC_DELAY_BETWEEN_DAYS_MS) and can
     * run for minutes on a large date range. Holding a DB transaction open
     * for that whole time would tie up a pooled connection needlessly; only
     * the final persist step needs one (see ImportService for the same
     * per-batch REQUIRES_NEW pattern).
     */
    public SyncResult sync(Long accountId, LocalDate start, LocalDate end) {
        Optional<GarminTokens> stored = accountRepository.findTokens(accountId);
        if (stored.isEmpty()) {
            return new SyncResult(0, 0, 0, List.of("Not connected to Garmin Connect — connect first."));
        }

        AtomicReference<GarminTokens> tokens = new AtomicReference<>(stored.get());
        List<String> errors = new ArrayList<>();
        List<ParsedMeasurement> all = new ArrayList<>();
        int daysWithData = 0;

        String displayName;
        try {
            JsonNode profile = callWithRefresh(accountId, tokens, dataClient::fetchSocialProfile);
            displayName = profile.path("displayName").asText(null);
            if (displayName == null) {
                throw new GarminConnectApiException("/userprofile-service/socialProfile", 200, false);
            }
        } catch (GarminConnectApiException e) {
            String message = "Could not verify Garmin Connect session: " + e.getMessage();
            accountRepository.saveError(accountId, message);
            return new SyncResult(0, 0, 0, List.of(message));
        }

        long totalDays = end.toEpochDay() - start.toEpochDay() + 1;
        int consecutiveRateLimits = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            try {
                List<ParsedMeasurement> dayMeasurements = syncOneDay(accountId, tokens, displayName, date);
                if (!dayMeasurements.isEmpty()) {
                    daysWithData++;
                    all.addAll(dayMeasurements);
                }
                consecutiveRateLimits = 0;
            } catch (GarminConnectApiException e) {
                errors.add(date + ": " + e.getMessage());
                log.warn("Garmin Connect sync failed for {}: {}", date, e.getMessage());
                if (e.statusCode == 429) {
                    consecutiveRateLimits++;
                    if (consecutiveRateLimits >= MAX_CONSECUTIVE_RATE_LIMITS) {
                        errors.add("Stopped early after " + consecutiveRateLimits
                                + " consecutive rate-limited days — Garmin is throttling this account. Try a smaller "
                                + "date range, or wait a while before syncing again.");
                        log.warn("Garmin Connect sync for account {} stopped early at {} after {} consecutive 429s",
                                accountId, date, consecutiveRateLimits);
                        break;
                    }
                } else {
                    consecutiveRateLimits = 0;
                }
            }
            sleepBetweenDays();
        }

        int written = 0;
        if (!all.isEmpty()) {
            written = requiresNewTx.execute(status -> {
                ImportBatch batch = new ImportBatch(
                        accountId, "vendor_api",
                        "garmin-connect-" + start + "-" + end + "-" + Instant.now().toEpochMilli(),
                        "garmin-connect-sync-" + start + "-to-" + end,
                        all.size()
                );
                ImportBatch saved = importBatchRepository.save(batch);
                return measurementRepository.persist(accountId, saved.getId(), "garmin-connect", all);
            });
        }

        accountRepository.saveLastSync(accountId, Instant.now());
        log.info("Garmin Connect sync for account {}: {}/{} days had data, {} measurements written",
                accountId, daysWithData, totalDays, written);
        return new SyncResult((int) totalDays, daysWithData, written, errors);
    }

    private void sleepBetweenDays() {
        try {
            Thread.sleep(SYNC_DELAY_BETWEEN_DAYS_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<ParsedMeasurement> syncOneDay(Long accountId, AtomicReference<GarminTokens> tokens,
                                                String displayName, LocalDate date) throws GarminConnectApiException {
        List<ParsedMeasurement> out = new ArrayList<>();
        Instant anchor = date.atTime(12, 0).toInstant(ZoneOffset.UTC);

        // The daily-summary call below is one HTTP request that already carries far
        // more than steps/RHR/stress — every field pulled out here is real data
        // Garmin already sent us, at zero extra API cost. Field names/units match
        // DailyStats in cyberjunky/python-garminconnect's typed models exactly.
        JsonNode summary = callWithRefresh(accountId, tokens, t -> dataClient.fetchDailySummary(t, displayName, date));
        if (summary != null && !summary.isMissingNode() && !summary.isNull()) {
            addIfPresent(out, summary, "totalSteps", anchor, "steps", "count");
            addIfPresent(out, summary, "restingHeartRate", anchor, "rhr", "bpm");
            addIfPresent(out, summary, "averageStressLevel", anchor, "stress", "score");
            addIfPresent(out, summary, "totalDistanceMeters", anchor, "distance", "meters");
            addIfPresent(out, summary, "totalKilocalories", anchor, "calories", "kcal");
            addIfPresent(out, summary, "activeKilocalories", anchor, "active_calories", "kcal");
            addIfPresent(out, summary, "moderateIntensityMinutes", anchor, "intensity_minutes_moderate", "minutes");
            addIfPresent(out, summary, "vigorousIntensityMinutes", anchor, "intensity_minutes_vigorous", "minutes");
            addIfPresent(out, summary, "floorsAscended", anchor, "floors", "count");
            addIfPresent(out, summary, "bodyBatteryChargedValue", anchor, "body_battery_charged", "score");
            addIfPresent(out, summary, "bodyBatteryDrainedValue", anchor, "body_battery_drained", "score");
            addIfPresent(out, summary, "bodyBatteryHighestValue", anchor, "body_battery_high", "score");
            addIfPresent(out, summary, "bodyBatteryLowestValue", anchor, "body_battery_low", "score");
            addIfPresent(out, summary, "maxStressLevel", anchor, "stress_max", "score");
            addIfPresent(out, summary, "stressDuration", anchor, "stress_duration", "seconds");
        }

        JsonNode sleep = callWithRefresh(accountId, tokens, t -> dataClient.fetchSleepData(t, displayName, date));
        JsonNode dto = sleep != null ? sleep.path("dailySleepDTO") : null;
        if (dto != null && !dto.isMissingNode() && dto.hasNonNull("sleepStartTimestampGMT")) {
            out.addAll(reconstructSleepStages(dto));
        }

        JsonNode hrv = callWithRefresh(accountId, tokens, t -> dataClient.fetchHrvData(t, date));
        JsonNode hrvSummary = hrv != null ? hrv.path("hrvSummary") : null;
        if (hrvSummary != null && hrvSummary.hasNonNull("lastNightAvg")) {
            out.add(new ParsedMeasurement(anchor, "hrv", hrvSummary.get("lastNightAvg").asDouble(), "ms"));
        }

        // Garmin's own proprietary readiness score — kept as a distinct metric
        // (not blended into ours) so it can be shown/cross-referenced alongside
        // this app's own deterministic readiness score, not silently merged with it.
        JsonNode readiness = callWithRefresh(accountId, tokens, t -> dataClient.fetchTrainingReadiness(t, date));
        if (readiness != null && readiness.isArray() && readiness.size() > 0) {
            JsonNode latest = readiness.get(0);
            if (latest.hasNonNull("score")) {
                out.add(new ParsedMeasurement(anchor, "garmin_training_readiness", latest.get("score").asDouble(), "score"));
            }
        }

        return out;
    }

    /** Expands real per-stage aggregate seconds into 15-minute rows across the real sleep window — see class javadoc. */
    List<ParsedMeasurement> reconstructSleepStages(JsonNode dto) {
        List<ParsedMeasurement> stages = new ArrayList<>();
        long cursor = dto.get("sleepStartTimestampGMT").asLong();
        cursor = appendStageBlock(stages, cursor, secondsOf(dto, "deepSleepSeconds"), 0.0);
        cursor = appendStageBlock(stages, cursor, secondsOf(dto, "lightSleepSeconds"), 2.0);
        cursor = appendStageBlock(stages, cursor, secondsOf(dto, "remSleepSeconds"), 1.0);
        appendStageBlock(stages, cursor, secondsOf(dto, "awakeSleepSeconds"), 3.0);
        return stages;
    }

    private long appendStageBlock(List<ParsedMeasurement> out, long cursorMs, long seconds, double stageIndex) {
        int readings = (int) (seconds / (MS_PER_15_MIN / 1000));
        for (int i = 0; i < readings; i++) {
            out.add(new ParsedMeasurement(Instant.ofEpochMilli(cursorMs), "sleep_stage", stageIndex, "stage"));
            cursorMs += MS_PER_15_MIN;
        }
        return cursorMs;
    }

    private long secondsOf(JsonNode dto, String field) {
        return dto.hasNonNull(field) ? dto.get(field).asLong() : 0L;
    }

    private void addIfPresent(List<ParsedMeasurement> out, JsonNode node, String field,
                               Instant time, String metricType, String unit) {
        if (node.hasNonNull(field)) {
            out.add(new ParsedMeasurement(time, metricType, node.get(field).asDouble(), unit));
        }
    }

    @FunctionalInterface
    private interface ApiCall {
        JsonNode call(GarminTokens tokens) throws GarminConnectApiException;
    }

    /**
     * Runs an API call, transparently refreshing the stored token once if
     * Garmin reports it expired, and persisting the refreshed token so the
     * rest of this sync (and future syncs) reuse it.
     */
    private JsonNode callWithRefresh(Long accountId, AtomicReference<GarminTokens> tokens, ApiCall call)
            throws GarminConnectApiException {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.call(tokens.get());
            } catch (GarminConnectApiException e) {
                if (e.tokenExpired && attempt < MAX_REFRESH_RETRIES) {
                    GarminTokens refreshed = authClient.refresh(tokens.get());
                    tokens.set(refreshed);
                    accountRepository.saveTokens(accountId, refreshed);
                    continue;
                }
                throw e;
            }
        }
    }
}
