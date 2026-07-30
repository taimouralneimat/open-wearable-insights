package com.openwearableinsights.api.export.adapter.in;

import com.openwearableinsights.api.export.application.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * REST controller for the local data export (docs/security/privacy-model.md
 * "User rights" — complete local data export half; deletion is separate,
 * out of scope here).
 *
 * <p>Core principle #1: no health data leaves the machine unless the user
 * explicitly triggers it. This endpoint is exactly that trigger — hitting it
 * downloads a real file, nothing is sent anywhere automatically.
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
}
