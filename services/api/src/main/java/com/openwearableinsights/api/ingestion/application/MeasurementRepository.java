package com.openwearableinsights.api.ingestion.application;

import com.openwearableinsights.api.connections.domain.ParsedMeasurement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

/**
 * Persists connector-parsed measurements (see ADR-0006) to the
 * {@code measurements} hypertable, attributing each batch to a
 * {@code provenance} row so imported data stays traceable to its source
 * file.
 *
 * <p>Note: the Postgres driver is a runtimeOnly dependency, so this uses
 * plain JDBC rather than {@code org.postgresql.util.PGobject}, matching
 * {@code ReadinessScoreHistoryRepository}'s convention.
 */
@Repository
public class MeasurementRepository {

    private static final Logger log = LoggerFactory.getLogger(MeasurementRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public MeasurementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a provenance row for this import batch and inserts every
     * parsed measurement attributed to it. Runs as plain JDBC calls inside
     * whatever transaction the caller has open (ImportService wraps each
     * file's persist in its own REQUIRES_NEW transaction).
     *
     * @return the number of measurement rows inserted
     */
    public int persist(Long accountId, Long importBatchId, String connectorId, List<ParsedMeasurement> measurements) {
        if (measurements.isEmpty()) {
            return 0;
        }

        Long provenanceId = jdbcTemplate.queryForObject(
                "INSERT INTO provenance (source, import_batch_id) VALUES (?, ?) RETURNING id",
                Long.class, connectorId, importBatchId
        );

        int[] results = jdbcTemplate.batchUpdate(
                "INSERT INTO measurements (time, account_id, metric_type, value, unit, source, provenance_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(java.sql.PreparedStatement ps, int i) throws java.sql.SQLException {
                        ParsedMeasurement m = measurements.get(i);
                        ps.setTimestamp(1, Timestamp.from(m.time()));
                        ps.setLong(2, accountId);
                        ps.setString(3, m.metricType());
                        ps.setDouble(4, m.value());
                        ps.setString(5, m.unit());
                        ps.setString(6, "vendor");
                        ps.setLong(7, provenanceId);
                    }

                    @Override
                    public int getBatchSize() {
                        return measurements.size();
                    }
                }
        );

        int inserted = results.length;
        log.info("Persisted {} measurements for import batch {} (connector={})", inserted, importBatchId, connectorId);
        return inserted;
    }
}
