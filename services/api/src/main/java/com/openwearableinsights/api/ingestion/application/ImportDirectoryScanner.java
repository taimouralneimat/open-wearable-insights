package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.ingestion.domain.ScannedFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Scans the local import directory (outside the repo) for importable files.
 *
 * <p>The import directory is configured via {@code owi.import-dir} (env:
 * {@code OWI_IMPORT_DIR}). It defaults to {@code ~/.open-wearable-insights/imports}.
 * Real health data lives outside the repo; only checksums/metadata are stored
 * in PostgreSQL.
 *
 * <p>Supported formats (Phase 2): JSON, CSV, FIT. Other files are reported as
 * unsupported so the UI can show them explicitly (no silent drops).
 */
@Service
public class ImportDirectoryScanner {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("json", "csv", "fit");

    private final Path importDir;

    public ImportDirectoryScanner(@Value("${owi.import-dir:~/.open-wearable-insights/imports}") String importDir) {
        this.importDir = resolveImportDir(importDir);
    }

    /**
     * Scan the import directory and return all files with detected format.
     */
    public ImportScanResult scan() {
        List<ScannedFile> files = new ArrayList<>();

        if (!Files.exists(importDir)) {
            return new ImportScanResult(files, importDir.toString(), false,
                    "Import directory does not exist: " + importDir);
        }

        if (!Files.isDirectory(importDir)) {
            return new ImportScanResult(files, importDir.toString(), false,
                    "Import path is not a directory: " + importDir);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(importDir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) continue;
                files.add(toScannedFile(entry));
            }
        } catch (IOException e) {
            return new ImportScanResult(files, importDir.toString(), false,
                    "Failed to read import directory: " + e.getMessage());
        }

        // Sort by name for stable output
        files.sort((a, b) -> a.filename().compareToIgnoreCase(b.filename()));

        return new ImportScanResult(files, importDir.toString(), true, null);
    }

    private ScannedFile toScannedFile(Path entry) throws IOException {
        String filename = entry.getFileName().toString();
        String format = detectFormat(filename);
        boolean supported = SUPPORTED_FORMATS.contains(format);
        long size = Files.size(entry);
        Instant modified = Files.getLastModifiedTime(entry).toInstant();
        return new ScannedFile(filename, size, modified, format, supported);
    }

    private String detectFormat(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) return "unknown";
        String ext = filename.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "json" -> "json";
            case "csv" -> "csv";
            case "fit" -> "fit";
            case "tcx" -> "tcx";
            case "gpx" -> "gpx";
            default -> "unknown";
        };
    }

    /**
     * Resolve the import directory path, expanding {@code ~} to the user home.
     */
    private static Path resolveImportDir(String raw) {
        String expanded = raw;
        if (raw.startsWith("~")) {
            String home = System.getProperty("user.home");
            expanded = raw.replaceFirst("^~", home);
        }
        return Paths.get(expanded);
    }

    /**
     * Result of scanning the import directory.
     *
     * @param files        discovered files
     * @param directory    resolved directory path
     * @param exists       whether the directory exists and is readable
     * @param errorMessage null if no error; otherwise a human-readable message
     */
    public record ImportScanResult(
            List<ScannedFile> files,
            String directory,
            boolean exists,
            String errorMessage
    ) {}
}
