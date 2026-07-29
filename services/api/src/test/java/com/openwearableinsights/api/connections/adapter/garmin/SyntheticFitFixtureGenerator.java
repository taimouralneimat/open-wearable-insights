package com.openwearableinsights.api.connections.adapter.garmin;

import com.garmin.fit.ActivityType;
import com.garmin.fit.DateTime;
import com.garmin.fit.File;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.HrvMesg;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.MonitoringMesg;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SleepLevel;
import com.garmin.fit.SleepLevelMesg;
import com.garmin.fit.StressLevelMesg;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Generates {@code packages/test-data/synthetic-activity.fit} — a real,
 * parseable FIT binary built with the official Garmin FIT SDK encoder, using
 * only synthetic (fabricated, not-real-device) values.
 *
 * <p>Per ADR-0006, no real health data is ever used in fixtures. This
 * replaces the earlier placeholder-bytes stand-in now that FIT parsing
 * (GarminFitConnector) is actually implemented and needs a real file to
 * parse in tests.
 *
 * <p>Not a JUnit test — a manually-run generator. Re-run via {@link #main}
 * if the fixture ever needs regenerating (e.g. to add more message types).
 * Uses a fixed reference time rather than "now" so the checked-in binary is
 * stable across regenerations.
 */
public final class SyntheticFitFixtureGenerator {

    private static final Instant BASE_TIME = Instant.parse("2026-01-15T06:00:00Z");

    private SyntheticFitFixtureGenerator() {
    }

    public static void main(String[] args) {
        Path output = args.length > 0
                ? Path.of(args[0])
                : Path.of("../../packages/test-data/synthetic-activity.fit");
        generate(output);
        System.out.println("Wrote synthetic FIT fixture -> " + output.toAbsolutePath());
    }

    public static void generate(Path output) {
        FileEncoder encoder = new FileEncoder(output.toFile());

        FileIdMesg fileId = new FileIdMesg();
        fileId.setType(File.ACTIVITY);
        fileId.setManufacturer(Manufacturer.GARMIN);
        fileId.setSerialNumber(1234567890L);
        fileId.setTimeCreated(new DateTime(BASE_TIME));
        encoder.write(fileId);

        // 5 heart-rate records, one per minute, synthetic bpm values.
        int[] heartRates = {58, 61, 64, 62, 59};
        for (int i = 0; i < heartRates.length; i++) {
            RecordMesg record = new RecordMesg();
            record.setTimestamp(new DateTime(BASE_TIME.plus(i, ChronoUnit.MINUTES)));
            record.setHeartRate((short) heartRates[i]);
            encoder.write(record);
        }

        // 3 monitoring (steps) records, cumulative synthetic step counts.
        // activityType must be WALKING so the SDK's cycles<->steps
        // conversion factor is 1:1 and getSteps() round-trips on read —
        // without it, setSteps() silently stores into the "cycles" field
        // and getSteps() returns null (caught by parsing the generated
        // fixture and finding steps missing entirely).
        long[] steps = {120, 340, 610};
        for (int i = 0; i < steps.length; i++) {
            MonitoringMesg monitoring = new MonitoringMesg();
            monitoring.setTimestamp(new DateTime(BASE_TIME.plus(i * 10L, ChronoUnit.MINUTES)));
            monitoring.setActivityType(ActivityType.WALKING);
            monitoring.setSteps(steps[i]);
            encoder.write(monitoring);
        }

        // 2 stress-level records, synthetic 0-100 scores.
        int[] stress = {22, 35};
        for (int i = 0; i < stress.length; i++) {
            StressLevelMesg stressMesg = new StressLevelMesg();
            stressMesg.setStressLevelTime(new DateTime(BASE_TIME.plus(i * 15L, ChronoUnit.MINUTES)));
            stressMesg.setStressLevelValue((short) stress[i]);
            encoder.write(stressMesg);
        }

        // Sleep stages cycling through the same deep/rem/light/awake order
        // as packages/test-data/synthetic_generator.py's SLEEP_STAGES.
        SleepLevel[] sleepStages = {SleepLevel.DEEP, SleepLevel.REM, SleepLevel.LIGHT, SleepLevel.AWAKE};
        for (int i = 0; i < sleepStages.length; i++) {
            SleepLevelMesg sleepMesg = new SleepLevelMesg();
            sleepMesg.setTimestamp(new DateTime(BASE_TIME.plus(i * 30L, ChronoUnit.MINUTES)));
            sleepMesg.setSleepLevel(sleepStages[i]);
            encoder.write(sleepMesg);
        }

        // One HRV record: synthetic RR-interval array in seconds (~60-70 bpm).
        HrvMesg hrv = new HrvMesg();
        float[] rrIntervals = {0.82f, 0.85f, 0.81f, 0.88f, 0.83f};
        for (int i = 0; i < rrIntervals.length; i++) {
            hrv.setTime(i, rrIntervals[i]);
        }
        encoder.write(hrv);

        encoder.close();
    }
}
