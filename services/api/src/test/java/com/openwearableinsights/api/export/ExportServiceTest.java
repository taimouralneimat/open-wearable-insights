package com.openwearableinsights.api.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.export.application.ExportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExportService} using a mocked {@link JdbcTemplate},
 * matching the Mockito style used elsewhere (see e.g. {@code
 * ingestion.ImportServiceTest}, {@code journal.JournalServiceTest}).
 *
 * <p>Uses synthetic data only — no real health data.
 */
class ExportServiceTest {

    private static final Long ACCOUNT_ID = 1L;

    @Test
    void buildExport_unknownAccount_returnsEmpty() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID))).thenReturn(List.of());
        ExportService service = new ExportService(jdbc, new ObjectMapper());

        Optional<Map<String, Object>> export = service.buildExport(ACCOUNT_ID);

        assertThat(export).isEmpty();
    }

    @Test
    void buildExport_knownAccount_assemblesEverySectionWithCorrectRecordCounts() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        // Every account-scoped table shares the same mocked call shape
        // (queryForList(String sql, Object... args) with a single accountId
        // arg), so distinguish by SQL content.
        Map<String, Object> accountRow = Map.of("id", 1L, "email", "local@open-wearable-insights.local", "local_only", true);
        when(jdbc.queryForList(contains("FROM accounts"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(accountRow));
        when(jdbc.queryForList(contains("FROM devices"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L)));
        when(jdbc.queryForList(contains("FROM measurements"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("time", Timestamp.from(Instant.now())), Map.of("time", Timestamp.from(Instant.now()))));
        when(jdbc.queryForList(contains("FROM activities"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L)));
        when(jdbc.queryForList(contains("FROM biomarker_readings"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L)));
        when(jdbc.queryForList(contains("FROM journal_entries"), eq(ACCOUNT_ID)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM readiness_score_history"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L), Map.of("id", 3L)));
        when(jdbc.queryForList(contains("FROM derived_metrics"), eq(ACCOUNT_ID)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM llm_outputs"), eq(ACCOUNT_ID)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM import_batches"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L), Map.of("id", 2L)));
        when(jdbc.queryForList(contains("FROM raw_payloads"), eq(ACCOUNT_ID)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM provenance"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L)));
        when(jdbc.queryForList(contains("algorithm_versions")))
                .thenReturn(List.of(Map.of("name", "readiness"), Map.of("name", "trainingload")));

        ExportService service = new ExportService(jdbc, new ObjectMapper());

        Map<String, Object> export = service.buildExport(ACCOUNT_ID).orElseThrow();

        assertThat(export.get("exportVersion")).isEqualTo(ExportService.EXPORT_VERSION);
        assertThat(export.get("accountId")).isEqualTo(ACCOUNT_ID);
        assertThat(export.get("exportedAt")).isInstanceOf(Instant.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> recordCounts = (Map<String, Object>) export.get("recordCounts");
        assertThat(recordCounts.get("account")).isEqualTo(1);
        assertThat(recordCounts.get("devices")).isEqualTo(1);
        assertThat(recordCounts.get("measurements")).isEqualTo(2);
        assertThat(recordCounts.get("activities")).isEqualTo(1);
        assertThat(recordCounts.get("biomarkerReadings")).isEqualTo(2);
        assertThat(recordCounts.get("journalEntries")).isEqualTo(0);
        assertThat(recordCounts.get("readinessScoreHistory")).isEqualTo(3);
        assertThat(recordCounts.get("derivedMetrics")).isEqualTo(0);
        assertThat(recordCounts.get("llmOutputs")).isEqualTo(0);
        assertThat(recordCounts.get("importBatches")).isEqualTo(2);
        assertThat(recordCounts.get("rawPayloads")).isEqualTo(0);
        assertThat(recordCounts.get("provenance")).isEqualTo(1);
        assertThat(recordCounts.get("algorithmVersions")).isEqualTo(2);

        // Every table is a top-level key, even when empty — a "complete"
        // export shouldn't silently omit sections that happen to be empty.
        assertThat(export.keySet()).containsExactlyInAnyOrder(
                "exportVersion", "exportedAt", "accountId", "recordCounts", "account",
                "devices", "measurements", "activities", "biomarkerReadings", "journalEntries",
                "readinessScoreHistory", "derivedMetrics", "llmOutputs",
                "importBatches", "rawPayloads", "provenance", "algorithmVersions"
        );
    }

    @Test
    void buildExport_sanitizesTimestampAndDateColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        stubAccountFound(jdbc);
        when(jdbc.queryForList(contains("FROM readiness_score_history"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of(
                        "score_date", Date.valueOf(today),
                        "computed_at", Timestamp.from(now)
                )));

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        Map<String, Object> export = service.buildExport(ACCOUNT_ID).orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> readiness = (List<Map<String, Object>>) export.get("readinessScoreHistory");
        assertThat(readiness).hasSize(1);
        assertThat(readiness.get(0).get("score_date")).isEqualTo(today);
        assertThat(readiness.get(0).get("computed_at")).isEqualTo(now);
    }

    @Test
    void buildExport_parsesJsonbColumnsIntoRealJsonRatherThanEscapedString() throws Exception {
        // The Postgres driver returns jsonb columns as org.postgresql.util.PGobject,
        // a runtimeOnly dependency not on this module's compile classpath (see
        // ExportService Javadoc). Build one via reflection to exercise the
        // real class-name-based detection path end to end.
        Class<?> pgObjectClass = Class.forName("org.postgresql.util.PGobject");
        Object pgObject = pgObjectClass.getDeclaredConstructor().newInstance();
        pgObjectClass.getMethod("setType", String.class).invoke(pgObject, "jsonb");
        pgObjectClass.getMethod("setValue", String.class).invoke(pgObject, "{\"algorithmVersion\":\"readiness-v3\"}");

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubAccountFound(jdbc);
        when(jdbc.queryForList(contains("FROM derived_metrics"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L, "value", pgObject)));

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        Map<String, Object> export = service.buildExport(ACCOUNT_ID).orElseThrow();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> derivedMetrics = (List<Map<String, Object>>) export.get("derivedMetrics");
        Object value = derivedMetrics.get(0).get("value");
        assertThat(value).isInstanceOf(JsonNode.class);
        assertThat(((JsonNode) value).get("algorithmVersion").asText()).isEqualTo("readiness-v3");
    }

    @Test
    void buildExport_provenanceQuery_includesRowsWithNullImportBatchId() {
        // Single-user system (ADR-0008): a provenance row with no import
        // batch to join through still belongs to the only account that
        // exists, so it must not be silently dropped. Assert the SQL sent
        // to the DB actually allows for that (rather than just inner-joining
        // and losing them).
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubAccountFound(jdbc);

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        service.buildExport(ACCOUNT_ID);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).queryForList(sqlCaptor.capture(), eq(ACCOUNT_ID));

        List<String> provenanceQueries = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("FROM provenance"))
                .toList();
        assertThat(provenanceQueries).hasSize(1);
        assertThat(provenanceQueries.get(0)).contains("IS NULL");
    }

    @Test
    void buildExport_algorithmVersionsQuery_isNotScopedToAnAccount() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        stubAccountFound(jdbc);
        when(jdbc.queryForList(contains("algorithm_versions")))
                .thenReturn(List.of(Map.of("name", "readiness", "version", "v3")));

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        Map<String, Object> export = service.buildExport(ACCOUNT_ID).orElseThrow();

        // Called via the no-arg overload (no accountId bind param) —
        // verifies this is genuinely a global, unscoped query.
        verify(jdbc).queryForList(contains("algorithm_versions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> algorithmVersions = (List<Map<String, Object>>) export.get("algorithmVersions");
        assertThat(algorithmVersions).hasSize(1);
        assertThat(algorithmVersions.get(0).get("name")).isEqualTo("readiness");
    }

    @Test
    void deleteAllData_unknownAccount_returnsEmpty() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ACCOUNT_ID))).thenReturn(0);
        ExportService service = new ExportService(jdbc, new ObjectMapper());

        Optional<Map<String, Integer>> result = service.deleteAllData(ACCOUNT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteAllData_knownAccount_deletesEveryPersonalDataTable_butNotAccountOrAlgorithmVersions() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ACCOUNT_ID))).thenReturn(1);
        when(jdbc.update(anyString(), eq(ACCOUNT_ID))).thenReturn(3);

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        Map<String, Integer> deleted = service.deleteAllData(ACCOUNT_ID).orElseThrow();

        assertThat(deleted.keySet()).containsExactlyInAnyOrder(
                "measurements", "activities", "biomarkerReadings", "rawPayloads", "provenance", "derivedMetrics",
                "journalEntries", "llmOutputs", "readinessScoreHistory", "garminConnectAccount",
                "importBatches", "devices"
        );
        deleted.values().forEach(count -> assertThat(count).isEqualTo(3));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), eq(ACCOUNT_ID));
        List<String> deleteSql = sqlCaptor.getAllValues();

        // The account row itself and the global algorithm_versions catalog
        // must never be targeted by a DELETE — only the personal-data tables.
        assertThat(deleteSql).noneMatch(sql -> sql.contains("DELETE FROM accounts"));
        assertThat(deleteSql).noneMatch(sql -> sql.contains("DELETE FROM algorithm_versions"));
        assertThat(deleteSql).anyMatch(sql -> sql.contains("DELETE FROM measurements"));
        assertThat(deleteSql).anyMatch(sql -> sql.contains("DELETE FROM garmin_connect_account"));
    }

    @Test
    void deleteAllData_provenanceQuery_matchesExportsNullImportBatchIdScopingRule() {
        // Deletion must use the exact same "null import_batch_id still belongs
        // to this account" rule buildExport uses (see that test) — otherwise
        // export and delete would silently disagree about what "all my data"
        // means, e.g. export includes a row deletion leaves behind.
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(ACCOUNT_ID))).thenReturn(1);
        when(jdbc.update(anyString(), eq(ACCOUNT_ID))).thenReturn(0);

        ExportService service = new ExportService(jdbc, new ObjectMapper());
        service.deleteAllData(ACCOUNT_ID);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbc, atLeastOnce()).update(sqlCaptor.capture(), eq(ACCOUNT_ID));
        List<String> provenanceDeletes = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("DELETE FROM provenance"))
                .toList();

        assertThat(provenanceDeletes).hasSize(1);
        assertThat(provenanceDeletes.get(0)).contains("IS NULL");
    }

    /** Stubs every account-scoped table query to return an empty list, and the account row to exist. */
    private void stubAccountFound(JdbcTemplate jdbc) {
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID)))
                .thenReturn(List.of());
        when(jdbc.queryForList(contains("FROM accounts"), eq(ACCOUNT_ID)))
                .thenReturn(List.of(Map.of("id", 1L, "email", "local@open-wearable-insights.local")));
    }
}
