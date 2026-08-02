package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.ingestion.domain.GarminExpressDevice;
import com.openwearableinsights.api.ingestion.domain.GarminExpressDevice.GarminExpressCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds real wellness data already staged locally by the Garmin Express
 * desktop app, before it uploads to Garmin Connect's cloud — replacing the
 * one-off manual file copy this was originally done with. Never modifies or
 * deletes anything inside Express's own folders; only reads, counts, and
 * copies (never moves) into this app's own import directory.
 *
 * <p>Garmin Connect's web export only offers per-activity workout files —
 * daily wellness data (sleep, steps, stress, HRV) isn't downloadable there
 * at all. Garmin Express, when a device syncs, stages the device's raw FIT
 * files locally before uploading them, which is what this reads.
 *
 * <p>macOS only for now (the only platform with a known, documented path
 * convention here) — reports "not found" honestly on other platforms
 * rather than guessing at an unverified path.
 */
@Service
public class GarminExpressLocator {

    private static final Logger log = LoggerFactory.getLogger(GarminExpressLocator.class);

    private static final List<String> CATEGORIES = List.of("Monitor", "Sleep", "Metrics", "HRVStatus", "Activity");

    private final Path importDir;
    private final Path expressBaseDir;

    public GarminExpressLocator(
            @Value("${owi.import-dir:~/.open-wearable-insights/imports}") String importDir,
            @Value("${owi.garmin-express-dir:~/Library/Application Support/Garmin/Express}") String expressBaseDir
    ) {
        this.importDir = resolvePath(importDir);
        this.expressBaseDir = resolvePath(expressBaseDir);
    }

    /**
     * Read-only discovery — lists registered devices and how many real
     * files are available per category. Makes no changes anywhere.
     */
    public List<GarminExpressDevice> discover() {
        List<GarminExpressDevice> devices = new ArrayList<>();
        Path registeredDevicesDir = expressBaseDir.resolve("RegisteredDevices");
        if (!Files.isDirectory(registeredDevicesDir)) {
            return devices;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(registeredDevicesDir)) {
            for (Path deviceDir : stream) {
                if (!Files.isDirectory(deviceDir)) continue;
                GarminExpressDevice device = discoverDevice(deviceDir);
                if (device.totalFiles() > 0) {
                    devices.add(device);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to list Garmin Express registered devices: {}", e.getMessage(), e);
        }
        return devices;
    }

    /**
     * Copies every discovered file for the given device into this app's
     * import directory (skips files that already exist there by name —
     * never overwrites). Source files in Express's folder are untouched.
     *
     * @return number of files actually copied (excludes ones already present)
     */
    public int stageForImport(String deviceId) {
        Path deviceDir = expressBaseDir.resolve("RegisteredDevices").resolve(deviceId);
        int copied = 0;
        try {
            Files.createDirectories(importDir);
            for (String category : CATEGORIES) {
                Path categoryDir = pendingSyncDir(deviceDir).resolve(category);
                if (!Files.isDirectory(categoryDir)) continue;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(categoryDir)) {
                    for (Path file : stream) {
                        if (Files.isDirectory(file)) continue;
                        Path dest = importDir.resolve(file.getFileName());
                        if (Files.exists(dest)) continue; // never overwrite
                        Files.copy(file, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        copied++;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Failed to stage Garmin Express files for device {}: {}", deviceId, e.getMessage(), e);
        }
        return copied;
    }

    private GarminExpressDevice discoverDevice(Path deviceDir) {
        String deviceId = deviceDir.getFileName().toString();
        List<GarminExpressCategory> categories = new ArrayList<>();
        int total = 0;
        for (String category : CATEGORIES) {
            Path categoryDir = pendingSyncDir(deviceDir).resolve(category);
            int count = countFiles(categoryDir);
            if (count > 0) {
                categories.add(new GarminExpressCategory(category, count));
                total += count;
            }
        }
        return new GarminExpressDevice(deviceId, categories, total);
    }

    private Path pendingSyncDir(Path deviceDir) {
        return deviceDir.resolve("PendingSyncUploads").resolve("Garmin");
    }

    private int countFiles(Path dir) {
        if (!Files.isDirectory(dir)) return 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            int count = 0;
            for (Path p : stream) {
                if (!Files.isDirectory(p)) count++;
            }
            return count;
        } catch (IOException e) {
            return 0;
        }
    }

    private static Path resolvePath(String raw) {
        String expanded = raw.startsWith("~")
                ? raw.replaceFirst("^~", System.getProperty("user.home"))
                : raw;
        return Paths.get(expanded);
    }
}
