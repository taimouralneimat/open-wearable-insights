package com.openwearableinsights.api.ingestion.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.connections.domain.ParsedActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * Persists connector-parsed activity/workout sessions (see ADR-0006) to the
 * {@code activities} table, attributing each batch to its own
 * {@code provenance} row — separate from {@link MeasurementRepository}'s,
 * since a single import can independently produce zero, one, or many of
 * each.
 *
 * <p>Note: the Postgres driver is a runtimeOnly dependency, so this uses
 * plain JDBC with an explicit {@code ::jsonb} cast rather than
 * {@code org.postgresql.util.PGobject}, matching
 * {@code ReadinessScoreHistoryRepository}'s convention.
 */
@Repository
public class ActivityRepository {

    private static final Logger log = LoggerFactory.getLogger(ActivityRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ActivityRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a provenance row for this import batch and inserts every
     * parsed activity attributed to it. Runs as plain JDBC calls inside
     * whatever transaction the caller has open (ImportService wraps each
     * file's persist in its own REQUIRES_NEW transaction).
     *
     * @return the number of activity rows inserted
     */
    public int persist(Long accountId, Long importBatchId, String connectorId, List<ParsedActivity> activities) {
        if (activities.isEmpty()) {
            return 0;
        }

        Long provenanceId = jdbcTemplate.queryForObject(
                "INSERT INTO provenance (source, import_batch_id) VALUES (?, ?) RETURNING id",
                Long.class, connectorId, importBatchId
        );

        int[] results = jdbcTemplate.batchUpdate(
                "INSERT INTO activities (account_id, provenance_id, sport, start_time, end_time, " +
                "duration_seconds, distance_meters, avg_speed_mps, max_speed_mps, avg_heart_rate, " +
                "max_heart_rate, calories, hr_zone_seconds) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)",
                new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        ParsedActivity a = activities.get(i);
                        ps.setLong(1, accountId);
                        ps.setLong(2, provenanceId);
                        ps.setString(3, a.sport());
                        ps.setTimestamp(4, Timestamp.from(a.startTime()));
                        ps.setTimestamp(5, Timestamp.from(a.endTime()));
                        ps.setDouble(6, a.durationSeconds());
                        setNullableDouble(ps, 7, a.distanceMeters());
                        setNullableDouble(ps, 8, a.avgSpeedMps());
                        setNullableDouble(ps, 9, a.maxSpeedMps());
                        setNullableInt(ps, 10, a.avgHeartRate());
                        setNullableInt(ps, 11, a.maxHeartRate());
                        setNullableInt(ps, 12, a.calories());
                        try {
                            ps.setString(13, a.hrZoneSeconds().isEmpty()
                                    ? null : objectMapper.writeValueAsString(a.hrZoneSeconds()));
                        } catch (Exception e) {
                            ps.setString(13, null);
                        }
                    }

                    @Override
                    public int getBatchSize() {
                        return activities.size();
                    }
                }
        );

        int inserted = results.length;
        log.info("Persisted {} activities for import batch {} (connector={})", inserted, importBatchId, connectorId);
        return inserted;
    }

    private void setNullableDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DOUBLE);
        } else {
            ps.setDouble(index, value);
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}
