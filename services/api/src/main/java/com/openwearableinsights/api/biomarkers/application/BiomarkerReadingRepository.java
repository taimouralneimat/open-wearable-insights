package com.openwearableinsights.api.biomarkers.application;

import com.openwearableinsights.api.biomarkers.domain.BiomarkerReading;
import com.openwearableinsights.api.biomarkers.domain.ParsedBiomarkerRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Persists and reads {@code biomarker_readings} rows.
 *
 * <p>Write side ({@link #persist}) follows the exact pattern {@code
 * ingestion.application.MeasurementRepository} uses: one {@code provenance}
 * row per import batch, then a batch insert attributed to it, so every
 * imported reading stays traceable to the file it came from. Called from
 * {@code ingestion.application.ImportService} when a scanned CSV is
 * recognized as a biomarker CSV.
 *
 * <p>Read side mirrors {@code activities.application.ActivitySessionRepository}'s
 * style: plain {@code queryForList} + row mapping, single-user default
 * account id, swallow-and-log on unexpected DB errors so a read-side glitch
 * degrades to an honest empty list rather than a 500.
 */
@Repository
public class BiomarkerReadingRepository {

    private static final Logger log = LoggerFactory.getLogger(BiomarkerReadingRepository.class);

    /** provenance.source for readings imported via a biomarker CSV. */
    public static final String PROVENANCE_SOURCE = "csv_biomarker_import";

    private static final String SELECT_COLUMNS =
            "id, biomarker_name, category, value, unit, reference_low, reference_high, reading_date";

    private final JdbcTemplate jdbcTemplate;

    public BiomarkerReadingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates one provenance row for this import batch and batch-inserts
     * every parsed row attributed to it.
     *
     * @return the number of rows inserted
     */
    public int persist(Long accountId, Long importBatchId, List<ParsedBiomarkerRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }

        Long provenanceId = jdbcTemplate.queryForObject(
                "INSERT INTO provenance (source, import_batch_id) VALUES (?, ?) RETURNING id",
                Long.class, PROVENANCE_SOURCE, importBatchId
        );

        int[] results = jdbcTemplate.batchUpdate(
                "INSERT INTO biomarker_readings " +
                "(account_id, provenance_id, biomarker_name, category, value, unit, reference_low, reference_high, reading_date) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ParsedBiomarkerRow r = rows.get(i);
                        ps.setLong(1, accountId);
                        ps.setLong(2, provenanceId);
                        ps.setString(3, r.biomarkerName());
                        ps.setString(4, r.category());
                        ps.setDouble(5, r.value());
                        ps.setString(6, r.unit());
                        setNullableDouble(ps, 7, r.referenceLow());
                        setNullableDouble(ps, 8, r.referenceHigh());
                        ps.setDate(9, Date.valueOf(r.readingDate()));
                    }

                    @Override
                    public int getBatchSize() {
                        return rows.size();
                    }
                }
        );

        int inserted = results.length;
        log.info("Persisted {} biomarker readings for import batch {}", inserted, importBatchId);
        return inserted;
    }

    private static void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    /** Most recent readings first, optionally filtered by name and/or date range. */
    public List<BiomarkerReading> findReadings(Long accountId, String biomarkerName, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM biomarker_readings WHERE account_id = ?");
        List<Object> args = new java.util.ArrayList<>();
        args.add(accountId);
        if (biomarkerName != null && !biomarkerName.isBlank()) {
            sql.append(" AND lower(biomarker_name) = lower(?)");
            args.add(biomarkerName);
        }
        if (from != null) {
            sql.append(" AND reading_date >= ?");
            args.add(Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND reading_date <= ?");
            args.add(Date.valueOf(to));
        }
        sql.append(" ORDER BY reading_date DESC, id DESC");

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
            return rows.stream().map(BiomarkerReadingRepository::toBiomarkerReading).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch biomarker readings for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    /** All readings for one biomarker name, chronological (oldest first) — a trend series. */
    public List<BiomarkerReading> findTrend(Long accountId, String biomarkerName) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT " + SELECT_COLUMNS + " FROM biomarker_readings " +
                    "WHERE account_id = ? AND lower(biomarker_name) = lower(?) " +
                    "ORDER BY reading_date ASC, id ASC",
                    accountId, biomarkerName
            );
            return rows.stream().map(BiomarkerReadingRepository::toBiomarkerReading).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch biomarker trend for account {} / {}: {}", accountId, biomarkerName, e.getMessage(), e);
            return List.of();
        }
    }

    /** Distinct biomarker names this account has any readings for, alphabetical. */
    public List<String> findDistinctNames(Long accountId) {
        try {
            return jdbcTemplate.queryForList(
                    "SELECT DISTINCT biomarker_name FROM biomarker_readings WHERE account_id = ? ORDER BY biomarker_name",
                    String.class, accountId
            );
        } catch (Exception e) {
            log.warn("Failed to fetch distinct biomarker names for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    private static BiomarkerReading toBiomarkerReading(Map<String, Object> row) {
        double value = ((Number) row.get("value")).doubleValue();
        Double referenceLow = row.get("reference_low") != null ? ((Number) row.get("reference_low")).doubleValue() : null;
        Double referenceHigh = row.get("reference_high") != null ? ((Number) row.get("reference_high")).doubleValue() : null;
        return new BiomarkerReading(
                ((Number) row.get("id")).longValue(),
                (String) row.get("biomarker_name"),
                (String) row.get("category"),
                value,
                (String) row.get("unit"),
                referenceLow,
                referenceHigh,
                BiomarkerReading.computeInRange(value, referenceLow, referenceHigh),
                ((Date) row.get("reading_date")).toLocalDate().toString()
        );
    }
}
