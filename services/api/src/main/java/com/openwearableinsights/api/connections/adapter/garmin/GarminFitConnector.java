package com.openwearableinsights.api.connections.adapter.garmin;

import com.garmin.fit.Decode;
import com.garmin.fit.HrvMesg;
import com.garmin.fit.HrvMesgListener;
import com.garmin.fit.MesgBroadcaster;
import com.garmin.fit.MonitoringMesgListener;
import com.garmin.fit.RecordMesgListener;
import com.garmin.fit.SessionMesgListener;
import com.garmin.fit.SleepLevel;
import com.garmin.fit.SleepLevelMesgListener;
import com.garmin.fit.StressLevelMesgListener;
import com.openwearableinsights.api.connections.domain.ParsedActivity;
import com.openwearableinsights.api.connections.domain.ParsedMeasurement;
import com.openwearableinsights.api.connections.domain.WearableConnector;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parses Garmin .fit files (ADR-0006) into canonical measurements using the
 * official Garmin FIT Java SDK ({@code com.garmin:fit}, Maven Central — see
 * NOTICE for license attribution).
 *
 * <p>Extracts:
 * <ul>
 *   <li>hr — instantaneous heart rate from record messages</li>
 *   <li>hrv — rMSSD computed from the HRV message's RR-interval array</li>
 *   <li>steps — from monitoring messages</li>
 *   <li>stress — from stress-level messages</li>
 *   <li>sleep_stage — from sleep-level messages, mapped to the same
 *       deep/rem/light/awake index encoding as
 *       packages/test-data/synthetic_generator.py</li>
 * </ul>
 *
 * <p><b>Known limitations, documented rather than silently guessed:</b>
 * <ul>
 *   <li>Resting heart rate (rhr) is NOT extracted — FIT exposes per-record
 *       instantaneous HR, not a "resting" classification. That needs
 *       additional derivation logic (e.g. minimum HR during a detected
 *       rest/sleep window), tracked as a follow-up.</li>
 *   <li>The HRV message has no timestamp field of its own in this SDK
 *       version — its reading is timestamped using the most recently seen
 *       timestamp from another message in the same file (a standard FIT
 *       parsing approximation), not its own precise time.</li>
 * </ul>
 */
@Component
public class GarminFitConnector implements WearableConnector {

    @Override
    public String getConnectorId() {
        return "garmin";
    }

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".fit");
    }

    @Override
    public List<ParsedMeasurement> parse(Path file) throws IOException {
        List<ParsedMeasurement> measurements = new ArrayList<>();
        AtomicReference<Instant> lastKnownTimestamp = new AtomicReference<>();

        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

        broadcaster.addListener((RecordMesgListener) mesg -> {
            if (mesg.getTimestamp() == null) return;
            Instant time = mesg.getTimestamp().getInstant();
            lastKnownTimestamp.set(time);
            if (mesg.getHeartRate() != null) {
                measurements.add(new ParsedMeasurement(time, "hr", mesg.getHeartRate().doubleValue(), "bpm"));
            }
        });

        broadcaster.addListener((MonitoringMesgListener) mesg -> {
            if (mesg.getTimestamp() == null) return;
            Instant time = mesg.getTimestamp().getInstant();
            lastKnownTimestamp.set(time);
            if (mesg.getSteps() != null) {
                measurements.add(new ParsedMeasurement(time, "steps", mesg.getSteps().doubleValue(), "count"));
            }
        });

        broadcaster.addListener((StressLevelMesgListener) mesg -> {
            if (mesg.getStressLevelTime() == null || mesg.getStressLevelValue() == null) return;
            Instant time = mesg.getStressLevelTime().getInstant();
            lastKnownTimestamp.set(time);
            measurements.add(new ParsedMeasurement(time, "stress", mesg.getStressLevelValue().doubleValue(), "score"));
        });

        broadcaster.addListener((SleepLevelMesgListener) mesg -> {
            if (mesg.getTimestamp() == null) return;
            Instant time = mesg.getTimestamp().getInstant();
            lastKnownTimestamp.set(time);
            Double stageIndex = sleepLevelIndex(mesg.getSleepLevel());
            if (stageIndex != null) {
                measurements.add(new ParsedMeasurement(time, "sleep_stage", stageIndex, "stage"));
            }
        });

        broadcaster.addListener((HrvMesgListener) mesg -> {
            Double rmssd = computeRmssd(mesg);
            Instant time = lastKnownTimestamp.get();
            if (rmssd != null && time != null) {
                measurements.add(new ParsedMeasurement(time, "hrv", rmssd, "ms"));
            }
        });

        try (InputStream in = Files.newInputStream(file)) {
            decode.read(in, broadcaster);
        }

        return measurements;
    }

    @Override
    public List<ParsedActivity> parseActivities(Path file) throws IOException {
        List<ParsedActivity> activities = new ArrayList<>();

        Decode decode = new Decode();
        MesgBroadcaster broadcaster = new MesgBroadcaster(decode);

        broadcaster.addListener((SessionMesgListener) mesg -> {
            if (mesg.getStartTime() == null || mesg.getTotalElapsedTime() == null) {
                return;
            }
            Instant start = mesg.getStartTime().getInstant();
            Instant end = start.plusMillis(Math.round(mesg.getTotalElapsedTime() * 1000));

            List<Double> hrZoneSeconds = new ArrayList<>();
            Float[] zones = mesg.getTimeInHrZone();
            if (zones != null) {
                for (Float zone : zones) {
                    hrZoneSeconds.add(zone == null ? null : zone.doubleValue());
                }
            }

            activities.add(new ParsedActivity(
                    start,
                    end,
                    mesg.getSport() != null ? mesg.getSport().name().toLowerCase() : "generic",
                    mesg.getTotalElapsedTime(),
                    mesg.getTotalDistance() != null ? mesg.getTotalDistance().doubleValue() : null,
                    mesg.getAvgSpeed() != null ? mesg.getAvgSpeed().doubleValue() : null,
                    mesg.getMaxSpeed() != null ? mesg.getMaxSpeed().doubleValue() : null,
                    mesg.getAvgHeartRate() != null ? mesg.getAvgHeartRate().intValue() : null,
                    mesg.getMaxHeartRate() != null ? mesg.getMaxHeartRate().intValue() : null,
                    mesg.getTotalCalories(),
                    hrZoneSeconds
            ));
        });

        try (InputStream in = Files.newInputStream(file)) {
            decode.read(in, broadcaster);
        }

        return activities;
    }

    /**
     * Matches packages/test-data/synthetic_generator.py's SLEEP_STAGES
     * index encoding: ["deep", "rem", "light", "awake"]. Null for
     * UNMEASURABLE/INVALID — not a real reading.
     */
    private Double sleepLevelIndex(SleepLevel level) {
        if (level == null) return null;
        return switch (level) {
            case DEEP -> 0.0;
            case REM -> 1.0;
            case LIGHT -> 2.0;
            case AWAKE -> 3.0;
            default -> null;
        };
    }

    /** rMSSD (root mean square of successive RR-interval differences), in ms. */
    private Double computeRmssd(HrvMesg mesg) {
        int n = mesg.getNumTime();
        if (n < 2) return null;
        double sumSquaredDiffs = 0;
        int pairs = 0;
        Float prev = null;
        for (int i = 0; i < n; i++) {
            Float t = mesg.getTime(i);
            if (t == null) continue;
            if (prev != null) {
                double diffMs = (t - prev) * 1000.0;
                sumSquaredDiffs += diffMs * diffMs;
                pairs++;
            }
            prev = t;
        }
        if (pairs == 0) return null;
        return Math.sqrt(sumSquaredDiffs / pairs);
    }
}
