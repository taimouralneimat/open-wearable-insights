package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.connections.application.ConnectorRegistry;
import com.openwearableinsights.api.ingestion.domain.ValidationResult;
import com.openwearableinsights.api.ingestion.domain.ScannedFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Dry-run validator for import files.
 *
 * <p>Computes content hashes (SHA-256), parses supported formats (JSON, CSV),
 * counts valid records, detects duplicates, and reports unsupported records.
 * No data is persisted — this is a preview/validation step only.
 *
 * <p>Duplicate detection checks both:
 * <ul>
 *   <li>Within-scan duplicates (two files in the same folder with same content)</li>
 *   <li>DB-backed duplicates (file with same content hash already imported
 *       with status 'imported') — so the preview and the real import always agree</li>
 * </ul>
 */
@Service
public class DryRunValidator {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("json", "csv", "fit");

    private final ImportDirectoryScanner scanner;
    private final ImportBatchRepository batchRepository;
    private final ConnectorRegistry connectorRegistry;

    /**
     * Spring-injected constructor — checks DB for duplicates.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public DryRunValidator(ImportDirectoryScanner scanner, ImportBatchRepository batchRepository, ConnectorRegistry connectorRegistry) {
        this.scanner = scanner;
        this.batchRepository = batchRepository;
        this.connectorRegistry = connectorRegistry;
    }

    /**
     * Test constructor — no DB duplicate checking (for unit tests).
     */
    public DryRunValidator(ImportDirectoryScanner scanner, ConnectorRegistry connectorRegistry) {
        this.scanner = scanner;
        this.batchRepository = null;
        this.connectorRegistry = connectorRegistry;
    }

    /**
     * Validate all files in the import directory (dry run).
     */
    public DryRunSummary validateAll() {
        var scanResult = scanner.scan();
        if (!scanResult.exists()) {
            return new DryRunSummary(
                    List.of(),
                    scanResult.directory(),
                    false,
                    scanResult.errorMessage(),
                    0, 0, 0, 0
            );
        }

        List<ValidationResult> results = new ArrayList<>();
        Set<String> seenHashes = new java.util.HashSet<>();

        for (ScannedFile file : scanResult.files()) {
            ValidationResult result = validateFile(file, scanResult.directory(), seenHashes);
            results.add(result);
            if (result.contentHash() != null) {
                seenHashes.add(result.contentHash());
            }
        }

        int supported = (int) results.stream().filter(ValidationResult::supported).count();
        int duplicates = (int) results.stream().filter(ValidationResult::duplicate).count();
        int totalRecords = results.stream().mapToInt(ValidationResult::recordCount).sum();
        int unsupportedFiles = results.size() - supported;

        return new DryRunSummary(
                results,
                scanResult.directory(),
                true,
                null,
                supported,
                duplicates,
                totalRecords,
                unsupportedFiles
        );
    }

    private ValidationResult validateFile(ScannedFile file, String directory, Set<String> seenHashes) {
        Path filePath = Path.of(directory, file.filename());
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> unsupportedRecords = new ArrayList<>();

        if (!file.supported()) {
            return new ValidationResult(
                    file.filename(),
                    file.detectedFormat(),
                    false,
                    null,
                    0,
                    false,
                    List.of("Unsupported format: " + file.detectedFormat()),
                    List.of(),
                    List.of()
            );
        }

        // Compute content hash
        String contentHash;
        try {
            contentHash = computeSha256(filePath);
        } catch (IOException e) {
            return new ValidationResult(
                    file.filename(),
                    file.detectedFormat(),
                    true,
                    null,
                    0,
                    false,
                    List.of("Failed to read file: " + e.getMessage()),
                    List.of(),
                    List.of()
            );
        }

        // Check for duplicate within this scan
        boolean duplicate = seenHashes.contains(contentHash);

        // Check for duplicate in DB (previously imported with status 'imported')
        if (!duplicate && batchRepository != null) {
            duplicate = batchRepository.existsByContentHashAndStatus(contentHash, "imported");
        }

        // Parse and count records
        int recordCount = 0;
        try {
            recordCount = switch (file.detectedFormat()) {
                case "json" -> countJsonRecords(filePath, unsupportedRecords, warnings);
                case "csv" -> countCsvRecords(filePath, unsupportedRecords, warnings);
                case "fit" -> {
                    var connector = connectorRegistry.findFor(file.filename());
                    if (connector.isEmpty()) {
                        warnings.add("No connector registered for this FIT file.");
                        yield 0;
                    }
                    yield connector.get().parse(filePath).size() + connector.get().parseActivities(filePath).size();
                }
                default -> 0;
            };
        } catch (IOException | RuntimeException e) {
            // RuntimeException also covers connector-thrown parse failures
            // (e.g. com.garmin.fit.FitRuntimeException for malformed FIT
            // files) — a bad vendor file must not crash the whole scan.
            errors.add("Failed to parse file: " + e.getMessage());
        }

        if (duplicate) {
            if (seenHashes.contains(contentHash)) {
                warnings.add("Duplicate: a file with the same content hash was already seen in this scan.");
            } else {
                warnings.add("Duplicate: a file with the same content hash was already imported (found in database).");
            }
        }

        return new ValidationResult(
                file.filename(),
                file.detectedFormat(),
                true,
                contentHash,
                recordCount,
                duplicate,
                errors,
                warnings,
                unsupportedRecords
        );
    }

    private String computeSha256(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(filePath);
            byte[] hash = digest.digest(bytes);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private int countJsonRecords(Path filePath, List<String> unsupportedRecords, List<String> warnings) throws IOException {
        String content = Files.readString(filePath);
        content = content.trim();

        if (content.startsWith("[")) {
            int count = countObjectsInArray(content);
            if (count == 0 && content.length() > 2) {
                warnings.add("JSON array appears non-empty but no valid objects detected.");
            }
            return count;
        } else if (content.startsWith("{")) {
            int measurementsIdx = content.indexOf("\"measurements\"");
            if (measurementsIdx >= 0) {
                int bracketStart = content.indexOf('[', measurementsIdx);
                int bracketEnd = content.lastIndexOf(']');
                if (bracketStart >= 0 && bracketEnd > bracketStart) {
                    String arrayContent = content.substring(bracketStart, bracketEnd + 1);
                    return countObjectsInArray(arrayContent);
                }
            }
            return 1;
        }

        unsupportedRecords.add("JSON content does not start with '[' or '{' — unrecognized structure.");
        return 0;
    }

    private int countObjectsInArray(String jsonArray) {
        int count = 0;
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (c == '"' && (i == 0 || jsonArray.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString) {
                if (c == '{') {
                    depth++;
                    if (depth == 1) count++;
                } else if (c == '}') {
                    depth--;
                }
            }
        }
        return count;
    }

    private int countCsvRecords(Path filePath, List<String> unsupportedRecords, List<String> warnings) throws IOException {
        List<String> lines = Files.readAllLines(filePath);
        if (lines.isEmpty()) return 0;

        int count = 0;
        boolean headerSkipped = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            if (!headerSkipped) {
                String lower = trimmed.toLowerCase();
                if (lower.contains("time") || lower.contains("metric") || lower.contains("date") || lower.contains("source")) {
                    headerSkipped = true;
                    continue;
                }
            }
            int commas = countChar(trimmed, ',');
            if (commas < 1) {
                unsupportedRecords.add("CSV line has fewer than 2 fields: " + trimmed.substring(0, Math.min(50, trimmed.length())));
                continue;
            }
            count++;
        }
        return count;
    }

    private int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    public record DryRunSummary(
            List<ValidationResult> results,
            String directory,
            boolean exists,
            String errorMessage,
            int supportedFiles,
            int duplicates,
            int totalRecords,
            int unsupportedFiles
    ) {}
}
