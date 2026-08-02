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
import org.springframework.transaction.annotation.Transactional;

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

    private final GarminConnectAccountRepository accountRepository;
    private final GarminConnectAuthClient authClient;
    private final GarminConnectDataClient dataClient;
    private final ImportBatchRepository importBatchRepository;
    private final MeasurementRepository measurementRepository;

    public GarminConnectSyncService(
            GarminConnectAccountRepository accountRepository,
            GarminConnectAuthClient authClient,
            GarminConnectDataClient dataClient,
            ImportBatchRepository importBatchRepository,
            MeasurementRepository measurementRepository
    ) {
        this.accountRepository = accountRepository;
        this.authClient = authClient;
        this.dataClient = dataClient;
        this.importBatchRepository = importBatchRepository;
        this.measurementRepository = measurementRepository;
    }

    public record SyncResult(int daysAttempted, int daysWithData, int measurementsWritten, List<String> errors) {}

    @Transactional
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
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            try {
                List<ParsedMeasurement> dayMeasurements = syncOneDay(accountId, tokens, displayName, date);
                if (!dayMeasurements.isEmpty()) {
                    daysWithData++;
                    all.addAll(dayMeasurements);
                }
            } catch (GarminConnectApiException e) {
                errors.add(date + ": " + e.getMessage());
                log.warn("Garmin Connect sync failed for {}: {}", date, e.getMessage());
            }
        }

        int written = 0;
        if (!all.isEmpty()) {
            ImportBatch batch = new ImportBatch(
                    accountId, "vendor_api",
                    "garmin-connect-" + start + "-" + end + "-" + Instant.now().toEpochMilli(),
                    "garmin-connect-sync-" + start + "-to-" + end,
                    all.size()
            );
            ImportBatch saved = importBatchRepository.save(batch);
            written = measurementRepository.persist(accountId, saved.getId(), "garmin-connect", all);
        }

        accountRepository.saveLastSync(accountId, Instant.now());
        log.info("Garmin Connect sync for account {}: {}/{} days had data, {} measurements written",
                accountId, daysWithData, totalDays, written);
        return new SyncResult((int) totalDays, daysWithData, written, errors);
    }

    private List<ParsedMeasurement> syncOneDay(Long accountId, AtomicReference<GarminTokens> tokens,
                                                String displayName, LocalDate date) throws GarminConnectApiException {
        List<ParsedMeasurement> out = new ArrayList<>();
        Instant anchor = date.atTime(12, 0).toInstant(ZoneOffset.UTC);

        JsonNode summary = callWithRefresh(accountId, tokens, t -> dataClient.fetchDailySummary(t, displayName, date));
        if (summary != null && !summary.isMissingNode() && !summary.isNull()) {
            addIfPresent(out, summary, "totalSteps", anchor, "steps", "count");
            addIfPresent(out, summary, "restingHeartRate", anchor, "rhr", "bpm");
            addIfPresent(out, summary, "averageStressLevel", anchor, "stress", "score");
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
