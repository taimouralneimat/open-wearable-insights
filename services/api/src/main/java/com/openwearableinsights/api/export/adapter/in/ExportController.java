package com.openwearableinsights.api.export.adapter.in;

import com.openwearableinsights.api.export.application.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * REST controller for the local data export and deletion — both halves of
 * docs/security/privacy-model.md's "User rights" (GDPR-style access +
 * erasure).
 *
 * <p>Core principle #1: no health data leaves the machine unless the user
 * explicitly triggers it. Export is exactly that trigger — hitting it
 * downloads a real file, nothing is sent anywhere automatically. Deletion is
 * irreversible, so it requires an explicit {@code confirm=DELETE} parameter
 * as a server-side safety check — the Flutter UI additionally requires
 * typing a confirmation phrase before it ever sends this request, but this
 * endpoint doesn't trust the caller to have done that.
 */
@RestController
@RequestMapping("/api/v1/export")
@Tag(name = "Export", description = "Complete local data export")
public class ExportController {

    private static final Long DEFAULT_ACCOUNT_ID = 1L;

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/full")
    @Operation(summary = "Download the complete data export for an account",
            description = "Returns every personal-data table (account, devices, measurements, activities, " +
                    "journal entries, readiness score history, derived metrics, LLM outputs, import batches, " +
                    "raw payloads, provenance) plus the global algorithm-versions catalog, scoped to one " +
                    "account, as a single downloadable JSON file (format version " + ExportService.EXPORT_VERSION + ").")
    public ResponseEntity<Map<String, Object>> exportFull(
            @RequestParam(required = false) Long accountId) {
        Long resolvedAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;

        Map<String, Object> export = exportService.buildExport(resolvedAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account with id " + resolvedAccountId));

        String filename = "open-wearable-insights-export-account-%d-%s.json"
                .formatted(resolvedAccountId, LocalDate.now(ZoneOffset.UTC));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(export);
    }

    @DeleteMapping("/all")
    @Operation(summary = "Permanently delete all personal data for an account",
            description = "Irreversible. Deletes every personal-data table's rows for the account (same table " +
                    "list as /full) except the account row itself and the global algorithm-versions catalog. " +
                    "Requires confirm=DELETE as a server-side safety check, independent of whatever confirmation " +
                    "the caller's own UI already required.")
    public ResponseEntity<Map<String, Integer>> deleteAll(
            @RequestParam(required = false) Long accountId,
            @RequestParam String confirm) {
        if (!"DELETE".equals(confirm)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Refusing to delete: pass confirm=DELETE to acknowledge this is permanent.");
        }
        Long resolvedAccountId = accountId != null ? accountId : DEFAULT_ACCOUNT_ID;

        Map<String, Integer> deletedCounts = exportService.deleteAllData(resolvedAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account with id " + resolvedAccountId));

        return ResponseEntity.ok(deletedCounts);
    }
}
