package com.openwearableinsights.api.ingestion;

import com.openwearableinsights.api.ingestion.application.GarminExpressLocator;
import com.openwearableinsights.api.ingestion.domain.GarminExpressDevice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GarminExpressLocator} — real filesystem I/O against
 * temp directories shaped like Garmin Express's real local sync folder
 * (verified against the actual layout on a real machine with a real
 * Forerunner 965 registered: RegisteredDevices/{id}/PendingSyncUploads/
 * Garmin/{Monitor,Sleep,Metrics,HRVStatus,Activity}/*.fit).
 */
class GarminExpressLocatorTest {

    @Test
    void discover_noExpressFolderAtAll_returnsEmptyListNotError(@TempDir Path tmp) {
        GarminExpressLocator locator = new GarminExpressLocator(
                tmp.resolve("imports").toString(),
                tmp.resolve("does-not-exist").toString()
        );

        assertThat(locator.discover()).isEmpty();
    }

    @Test
    void discover_realDeviceLayout_reportsCorrectCategoryCounts(@TempDir Path tmp) throws IOException {
        Path expressDir = tmp.resolve("express");
        Path deviceDir = expressDir.resolve("RegisteredDevices").resolve("1234567890");
        writeFakeFiles(deviceDir, "Monitor", "M1.FIT", "M2.FIT");
        writeFakeFiles(deviceDir, "Sleep", "S1.fit");
        writeFakeFiles(deviceDir, "HRVStatus", "H1.fit", "H2.fit", "H3.fit");
        // Empty category directories (e.g. no activities yet) should not appear.
        Files.createDirectories(deviceDir.resolve("PendingSyncUploads").resolve("Garmin").resolve("Activity"));

        GarminExpressLocator locator = new GarminExpressLocator(
                tmp.resolve("imports").toString(), expressDir.toString()
        );

        List<GarminExpressDevice> devices = locator.discover();

        assertThat(devices).hasSize(1);
        GarminExpressDevice device = devices.get(0);
        assertThat(device.deviceId()).isEqualTo("1234567890");
        assertThat(device.totalFiles()).isEqualTo(6);
        assertThat(device.categories()).extracting(GarminExpressDevice.GarminExpressCategory::name)
                .containsExactlyInAnyOrder("Monitor", "Sleep", "HRVStatus");
        assertThat(device.categories()).noneMatch(c -> c.name().equals("Activity"));
    }

    @Test
    void stageForImport_copiesFiles_withoutModifyingOrDeletingSource(@TempDir Path tmp) throws IOException {
        Path expressDir = tmp.resolve("express");
        Path deviceDir = expressDir.resolve("RegisteredDevices").resolve("dev1");
        writeFakeFiles(deviceDir, "Monitor", "M1.FIT");
        writeFakeFiles(deviceDir, "Sleep", "S1.fit");
        Path importDir = tmp.resolve("imports");

        GarminExpressLocator locator = new GarminExpressLocator(importDir.toString(), expressDir.toString());
        int copied = locator.stageForImport("dev1");

        assertThat(copied).isEqualTo(2);
        assertThat(importDir.resolve("M1.FIT")).exists();
        assertThat(importDir.resolve("S1.fit")).exists();
        // Source files must still exist, untouched — never moved or deleted.
        assertThat(deviceDir.resolve("PendingSyncUploads/Garmin/Monitor/M1.FIT")).exists();
        assertThat(deviceDir.resolve("PendingSyncUploads/Garmin/Sleep/S1.fit")).exists();
    }

    @Test
    void stageForImport_neverOverwritesFileAlreadyInImportDir(@TempDir Path tmp) throws IOException {
        Path expressDir = tmp.resolve("express");
        Path deviceDir = expressDir.resolve("RegisteredDevices").resolve("dev1");
        writeFakeFiles(deviceDir, "Monitor", "M1.FIT");
        Path importDir = tmp.resolve("imports");
        Files.createDirectories(importDir);
        Files.writeString(importDir.resolve("M1.FIT"), "already-imported-content");

        GarminExpressLocator locator = new GarminExpressLocator(importDir.toString(), expressDir.toString());
        int copied = locator.stageForImport("dev1");

        assertThat(copied).isZero();
        assertThat(Files.readString(importDir.resolve("M1.FIT"))).isEqualTo("already-imported-content");
    }

    @Test
    void stageForImport_unknownDeviceId_copiesNothingRatherThanThrowing(@TempDir Path tmp) {
        GarminExpressLocator locator = new GarminExpressLocator(
                tmp.resolve("imports").toString(), tmp.resolve("express").toString()
        );

        assertThat(locator.stageForImport("no-such-device")).isZero();
    }

    private void writeFakeFiles(Path deviceDir, String category, String... filenames) throws IOException {
        Path categoryDir = deviceDir.resolve("PendingSyncUploads").resolve("Garmin").resolve(category);
        Files.createDirectories(categoryDir);
        for (String filename : filenames) {
            Files.writeString(categoryDir.resolve(filename), "fake-fit-bytes");
        }
    }
}
