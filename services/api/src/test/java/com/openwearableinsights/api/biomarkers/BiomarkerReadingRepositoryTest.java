package com.openwearableinsights.api.biomarkers;

import com.openwearableinsights.api.biomarkers.application.BiomarkerReadingRepository;
import com.openwearableinsights.api.biomarkers.domain.BiomarkerReading;
import com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BiomarkerReadingRepository} using a mocked {@link
 * JdbcTemplate}, matching the Mockito style used elsewhere (see {@code
 * export.ExportServiceTest}, {@code journal.CorrelationServiceTest}).
 *
 * <p>Uses synthetic lab values only — no real health data.
 */
class BiomarkerReadingRepositoryTest {

    private static final Long ACCOUNT_ID = 1L;

    @Test
    void persist_createsOneProvenanceRowThenBatchInsertsEveryRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("INSERT INTO provenance"), eq(Long.class),
                eq(BiomarkerReadingRepository.PROVENANCE_SOURCE), eq(10L))).thenReturn(99L);
        when(jdbc.batchUpdate(contains("INSERT INTO biomarker_readings"), any(BatchPreparedStatementSetter.class)))
                .thenReturn(new int[]{1, 1});

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<ParsedBiomarkerRow> rows = List.of(
                new ParsedBiomarkerRow(LocalDate.of(2026, 7, 1), "LDL Cholesterol", 95.0, "mg/dL", 0.0, 100.0, "Lipid/Cardiovascular"),
                new ParsedBiomarkerRow(LocalDate.of(2026, 7, 1), "HDL Cholesterol", 55.0, "mg/dL", 40.0, null, "Lipid/Cardiovascular")
        );

        int inserted = repository.persist(ACCOUNT_ID, 10L, rows);

        assertThat(inserted).isEqualTo(2);
    }

    @Test
    void persist_emptyRows_isNoOpAndNeverTouchesProvenance() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);

        int inserted = repository.persist(ACCOUNT_ID, 10L, List.of());

        assertThat(inserted).isZero();
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    @Test
    void persist_batchStatementSetter_writesCorrectFieldsIncludingNullableReferenceBounds() throws Exception {
        // Real verification of the actual data mapped into each row of the
        // batch insert — not just that batchUpdate was called — since
        // that's where a column/index mistake would silently corrupt data.
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(contains("INSERT INTO provenance"), eq(Long.class), any(), any())).thenReturn(99L);
        ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
        when(jdbc.batchUpdate(anyString(), setterCaptor.capture())).thenReturn(new int[]{1, 1});

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<ParsedBiomarkerRow> rows = List.of(
                new ParsedBiomarkerRow(LocalDate.of(2026, 7, 1), "LDL Cholesterol", 95.0, "mg/dL", 0.0, 100.0, "Lipid/Cardiovascular"),
                new ParsedBiomarkerRow(LocalDate.of(2026, 7, 2), "HDL Cholesterol", 55.0, "mg/dL", 40.0, null, null)
        );
        repository.persist(ACCOUNT_ID, 10L, rows);

        BatchPreparedStatementSetter setter = setterCaptor.getValue();
        assertThat(setter.getBatchSize()).isEqualTo(2);

        PreparedStatement ps0 = mock(PreparedStatement.class);
        setter.setValues(ps0, 0);
        verify(ps0).setLong(1, ACCOUNT_ID);
        verify(ps0).setLong(2, 99L);
        verify(ps0).setString(3, "LDL Cholesterol");
        verify(ps0).setString(4, "Lipid/Cardiovascular");
        verify(ps0).setDouble(5, 95.0);
        verify(ps0).setString(6, "mg/dL");
        verify(ps0).setDouble(7, 0.0);
        verify(ps0).setDouble(8, 100.0);
        verify(ps0).setDate(9, Date.valueOf(LocalDate.of(2026, 7, 1)));

        // Second row: no reference_high, no category — both must be written
        // as real SQL NULLs, not fabricated/defaulted values.
        PreparedStatement ps1 = mock(PreparedStatement.class);
        setter.setValues(ps1, 1);
        verify(ps1).setString(4, null);
        verify(ps1).setDouble(7, 40.0);
        verify(ps1).setNull(8, Types.DOUBLE);
    }

    @Test
    void findReadings_noFilters_mapsRowsAndComputesInRange() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID))).thenReturn(List.of(
                readingRow(1L, "LDL Cholesterol", "Lipid/Cardiovascular", 95.0, "mg/dL", 0.0, 100.0, LocalDate.of(2026, 7, 1)),
                readingRow(2L, "Glucose", "Metabolic/Glucose", 130.0, "mg/dL", 70.0, 99.0, LocalDate.of(2026, 7, 1)),
                readingRow(3L, "Some Custom Marker", null, 3.2, "units", null, null, LocalDate.of(2026, 7, 1))
        ));

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<BiomarkerReading> readings = repository.findReadings(ACCOUNT_ID, null, null, null);

        assertThat(readings).hasSize(3);
        assertThat(readings.get(0).inRange()).isTrue();   // 95 within [0,100]
        assertThat(readings.get(1).inRange()).isFalse();  // 130 above [70,99]
        assertThat(readings.get(2).inRange()).isNull();   // no reference range supplied at all
        assertThat(readings.get(2).category()).isNull();  // never fabricated
    }

    @Test
    void findReadings_withNameAndDateRangeFilters_buildsExpectedSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        // Filters append bind args in order (accountId, name, from, to) —
        // match that exact arg shape rather than a generic "any args" stub,
        // since Mockito matches varargs position-by-position.
        when(jdbc.queryForList(sqlCaptor.capture(), eq(ACCOUNT_ID), eq("Glucose"), any(), any()))
                .thenReturn(List.of());

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        repository.findReadings(ACCOUNT_ID, "Glucose", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("lower(biomarker_name) = lower(?)");
        assertThat(sql).contains("reading_date >= ?");
        assertThat(sql).contains("reading_date <= ?");
        assertThat(sql).contains("ORDER BY reading_date DESC");
    }

    @Test
    void findReadings_dbError_returnsEmptyListNotException() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID))).thenThrow(new RuntimeException("connection lost"));

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<BiomarkerReading> readings = repository.findReadings(ACCOUNT_ID, null, null, null);

        assertThat(readings).isEmpty();
    }

    @Test
    void findTrend_returnsChronologicalReadingsForExactName() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("LDL Cholesterol"))).thenReturn(List.of(
                readingRow(1L, "LDL Cholesterol", "Lipid/Cardiovascular", 100.0, "mg/dL", 0.0, 100.0, LocalDate.of(2026, 5, 1)),
                readingRow(2L, "LDL Cholesterol", "Lipid/Cardiovascular", 95.0, "mg/dL", 0.0, 100.0, LocalDate.of(2026, 7, 1))
        ));

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<BiomarkerReading> trend = repository.findTrend(ACCOUNT_ID, "LDL Cholesterol");

        assertThat(trend).hasSize(2);
        assertThat(trend.get(0).readingDate()).isEqualTo("2026-05-01");
        assertThat(trend.get(1).readingDate()).isEqualTo("2026-07-01");
    }

    @Test
    void findTrend_noReadingsForName_returnsEmptyNotError() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(ACCOUNT_ID), eq("Nonexistent Marker"))).thenReturn(List.of());

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<BiomarkerReading> trend = repository.findTrend(ACCOUNT_ID, "Nonexistent Marker");

        assertThat(trend).isEmpty();
    }

    @Test
    void findDistinctNames_returnsRealNamesOnly() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(ACCOUNT_ID)))
                .thenReturn(List.of("Glucose", "LDL Cholesterol"));

        BiomarkerReadingRepository repository = new BiomarkerReadingRepository(jdbc);
        List<String> names = repository.findDistinctNames(ACCOUNT_ID);

        assertThat(names).containsExactly("Glucose", "LDL Cholesterol");
    }

    private static Map<String, Object> readingRow(
            Long id, String name, String category, double value, String unit,
            Double referenceLow, Double referenceHigh, LocalDate readingDate
    ) {
        Map<String, Object> row = new java.util.HashMap<>();
        row.put("id", id);
        row.put("biomarker_name", name);
        row.put("category", category);
        row.put("value", value);
        row.put("unit", unit);
        row.put("reference_low", referenceLow);
        row.put("reference_high", referenceHigh);
        row.put("reading_date", Date.valueOf(readingDate));
        return row;
    }
}
