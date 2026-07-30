package com.openwearableinsights.api.export.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Assembles a single, complete, per-account JSON export of every table that
 * holds personal data (see docs/security/privacy-model.md, "User rights" —
 * this covers the "complete local data export" half; deletion is a separate,
 * out-of-scope feature).
 *
 * <p>Format is versioned as {@code EXPORT_VERSION}, matching this project's
 * convention of versioning algorithm/output formats (e.g. readiness's
 * {@code algorithm_version}). Bump it whenever the export's shape changes in
 * a way a consumer would need to know about.
 *
 * <p><strong>Table scoping.</strong> Most tables have a direct
 * {@code account_id} column and are queried with a plain {@code WHERE
 * account_id = ?}. Two tables don't:
 * <ul>
 *   <li>{@code raw_payloads} scopes via {@code batch_id -> import_batches.account_id}.</li>
 *   <li>{@code provenance} scopes via {@code import_batch_id -> import_batches.account_id}.
 *       Some provenance rows have a null {@code import_batch_id} (verified
 *       against the real dev DB: one row, source {@code synthetic}, created
 *       ahead of any import batch). Those rows are still included — this is
 *       a single-user local system (ADR-0008; exactly one account exists),
 *       so a provenance row with no import batch to join through is
 *       unambiguously this account's data, not some other account's. If the
 *       system ever grows multi-account support, this join-based inclusion
 *       rule would need to be revisited (e.g. adding a direct
 *       {@code account_id} column to {@code provenance}) since a null
 *       {@code import_batch_id} would then be genuinely unattributable.
 * </ul>
 *
 * <p><strong>algorithm_versions</strong> is a global, non-personal reference
 * catalog (not scoped to any account). It's included anyway, unscoped, so
 * the export is self-describing — {@code algorithm_version} strings that
 * appear elsewhere in the export (readiness history, derived metrics,
 * provenance) can be cross-referenced without a second request.
 *
 * <p><strong>Size.</strong> This queries every row into memory and builds
 * the full JSON tree before returning — no streaming. Given this is a
 * local, single-user, hobby-scale system (per ADR-0008) rather than a
 * multi-tenant service, that's an acceptable tradeoff for the simplicity it
 * buys; a genuinely large {@code measurements} history (years of
 * high-frequency data) would eventually want a streaming
 * {@code JsonGenerator}-based writer instead.
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    /** Bump when the export's shape changes in a consumer-visible way. */
    public static final String EXPORT_VERSION = "export-v1";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ExportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the full export for one account, or {@link Optional#empty()} if
     * no account with that id exists.
     */
    public Optional<Map<String, Object>> buildExport(Long accountId) {
        List<Map<String, Object>> accountRows = query(
                "SELECT * FROM accounts WHERE id = ?", accountId);
        if (accountRows.isEmpty()) {
            return Optional.empty();
        }

        List<Map<String, Object>> devices = query(
                "SELECT * FROM devices WHERE account_id = ? ORDER BY id", accountId);
        List<Map<String, Object>> measurements = query(
                "SELECT * FROM measurements WHERE account_id = ? ORDER BY time", accountId);
        List<Map<String, Object>> activities = query(
                "SELECT * FROM activities WHERE account_id = ? ORDER BY start_time", accountId);
        List<Map<String, Object>> journalEntries = query(
                "SELECT * FROM journal_entries WHERE account_id = ? ORDER BY time", accountId);
        List<Map<String, Object>> readinessScoreHistory = query(
                "SELECT * FROM readiness_score_history WHERE account_id = ? ORDER BY score_date", accountId);
        List<Map<String, Object>> derivedMetrics = query(
                "SELECT * FROM derived_metrics WHERE account_id = ? ORDER BY computed_at", accountId);
        List<Map<String, Object>> llmOutputs = query(
                "SELECT * FROM llm_outputs WHERE account_id = ? ORDER BY created_at", accountId);
        List<Map<String, Object>> importBatches = query(
                "SELECT * FROM import_batches WHERE account_id = ? ORDER BY imported_at", accountId);
        List<Map<String, Object>> rawPayloads = query(
                "SELECT rp.* FROM raw_payloads rp " +
                "JOIN import_batches ib ON rp.batch_id = ib.id " +
                "WHERE ib.account_id = ? ORDER BY rp.received_at", accountId);
        // See class Javadoc: rows with a null import_batch_id are included
        // too, since in this single-user system they can only belong to the
        // one account that exists.
        List<Map<String, Object>> provenance = query(
                "SELECT p.* FROM provenance p " +
                "LEFT JOIN import_batches ib ON p.import_batch_id = ib.id " +
                "WHERE p.import_batch_id IS NULL OR ib.account_id = ? " +
                "ORDER BY p.id", accountId);
        // Global reference catalog — not account-scoped, included for a
        // self-describing export (see class Javadoc).
        List<Map<String, Object>> algorithmVersions = queryGlobal(
                "SELECT * FROM algorithm_versions ORDER BY name, version");

        Map<String, Object> account = accountRows.get(0);

        Map<String, Integer> recordCounts = new LinkedHashMap<>();
        recordCounts.put("account", 1);
        recordCounts.put("devices", devices.size());
        recordCounts.put("measurements", measurements.size());
        recordCounts.put("activities", activities.size());
        recordCounts.put("journalEntries", journalEntries.size());
        recordCounts.put("readinessScoreHistory", readinessScoreHistory.size());
        recordCounts.put("derivedMetrics", derivedMetrics.size());
        recordCounts.put("llmOutputs", llmOutputs.size());
        recordCounts.put("importBatches", importBatches.size());
        recordCounts.put("rawPayloads", rawPayloads.size());
        recordCounts.put("provenance", provenance.size());
        recordCounts.put("algorithmVersions", algorithmVersions.size());

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportVersion", EXPORT_VERSION);
        export.put("exportedAt", Instant.now());
        export.put("accountId", accountId);
        export.put("recordCounts", recordCounts);
        export.put("account", account);
        export.put("devices", devices);
        export.put("measurements", measurements);
        export.put("activities", activities);
        export.put("journalEntries", journalEntries);
        export.put("readinessScoreHistory", readinessScoreHistory);
        export.put("derivedMetrics", derivedMetrics);
        export.put("llmOutputs", llmOutputs);
        export.put("importBatches", importBatches);
        export.put("rawPayloads", rawPayloads);
        export.put("provenance", provenance);
        export.put("algorithmVersions", algorithmVersions);

        log.info("Built export for account {}: {}", accountId, recordCounts);
        return Optional.of(export);
    }

    private List<Map<String, Object>> query(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        return rows.stream().map(this::sanitizeRow).toList();
    }

    /**
     * Same as {@link #query(String, Object...)} but for queries with no bind
     * parameters (currently just the unscoped {@code algorithm_versions}
     * catalog). Kept as a separate method — rather than calling {@link
     * #query} with zero varargs — so it goes through {@code JdbcTemplate}'s
     * distinct no-arg {@code queryForList(String)} overload, which keeps
     * this call unambiguous to mock in tests.
     */
    private List<Map<String, Object>> queryGlobal(String sql) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return rows.stream().map(this::sanitizeRow).toList();
    }

    /**
     * Converts JDBC-native row values into JSON-friendly ones: {@code
     * java.sql.Timestamp} -> {@link Instant} (so Jackson emits ISO-8601, not
     * epoch millis), {@code java.sql.Date} -> {@link java.time.LocalDate},
     * and Postgres {@code jsonb} columns (which come back as a driver
     * {@code PGobject} whose {@code toString()} is the raw JSON text) ->
     * parsed {@link JsonNode} so they nest as real JSON rather than an
     * escaped string.
     *
     * <p>The Postgres driver is a runtimeOnly dependency (see
     * {@code MeasurementRepository}, {@code ReadinessScoreHistoryRepository}),
     * so {@code PGobject} isn't on the compile classpath; it's detected by
     * class name instead.
     */
    private Map<String, Object> sanitizeRow(Map<String, Object> row) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        row.forEach((column, value) -> sanitized.put(column, sanitizeValue(column, value)));
        return sanitized;
    }

    private Object sanitizeValue(String column, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if ("org.postgresql.util.PGobject".equals(value.getClass().getName())) {
            try {
                return objectMapper.readTree(value.toString());
            } catch (Exception e) {
                log.warn("Failed to parse jsonb column {} as JSON, falling back to raw string: {}", column, e.getMessage());
                return value.toString();
            }
        }
        return value;
    }
}
