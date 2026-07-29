package com.openwearableinsights.api.activities.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.activities.domain.ActivitySession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read side for the {@code activities} table (discrete workout sessions —
 * see {@link ActivitySession} for how this differs from the day-level step
 * summary in {@link ActivityInsightService}). Write side lives in
 * {@code ingestion.application.ActivityRepository}, matching the same
 * write/read module split as measurements.
 */
@Repository
public class ActivitySessionRepository {

    private static final Logger log = LoggerFactory.getLogger(ActivitySessionRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ActivitySessionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<ActivitySession> findRecent(Long accountId, int limit) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, sport, start_time, end_time, duration_seconds, distance_meters, " +
                    "avg_speed_mps, max_speed_mps, avg_heart_rate, max_heart_rate, calories, hr_zone_seconds " +
                    "FROM activities WHERE account_id = ? ORDER BY start_time DESC LIMIT ?",
                    accountId, limit
            );
            return rows.stream().map(this::toActivitySession).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch recent activities for account {}: {}", accountId, e.getMessage(), e);
            return List.of();
        }
    }

    public Optional<ActivitySession> findById(Long accountId, Long id) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, sport, start_time, end_time, duration_seconds, distance_meters, " +
                    "avg_speed_mps, max_speed_mps, avg_heart_rate, max_heart_rate, calories, hr_zone_seconds " +
                    "FROM activities WHERE account_id = ? AND id = ?",
                    accountId, id
            );
            return rows.isEmpty() ? Optional.empty() : Optional.of(toActivitySession(rows.get(0)));
        } catch (Exception e) {
            log.warn("Failed to fetch activity {} for account {}: {}", id, accountId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private ActivitySession toActivitySession(Map<String, Object> row) {
        Double avgSpeedMps = row.get("avg_speed_mps") != null ? ((Number) row.get("avg_speed_mps")).doubleValue() : null;
        Double avgPaceSecPerKm = (avgSpeedMps != null && avgSpeedMps > 0) ? 1000.0 / avgSpeedMps : null;

        List<Double> hrZoneSeconds = List.of();
        Object hrZoneJson = row.get("hr_zone_seconds");
        if (hrZoneJson != null) {
            try {
                hrZoneSeconds = objectMapper.readValue(
                        hrZoneJson.toString(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class)
                );
            } catch (Exception e) {
                log.warn("Failed to parse hr_zone_seconds for activity {}: {}", row.get("id"), e.getMessage());
            }
        }

        return new ActivitySession(
                ((Number) row.get("id")).longValue(),
                (String) row.get("sport"),
                ((Timestamp) row.get("start_time")).toInstant().toString(),
                ((Timestamp) row.get("end_time")).toInstant().toString(),
                ((Number) row.get("duration_seconds")).doubleValue(),
                row.get("distance_meters") != null ? ((Number) row.get("distance_meters")).doubleValue() : null,
                avgSpeedMps,
                row.get("max_speed_mps") != null ? ((Number) row.get("max_speed_mps")).doubleValue() : null,
                avgPaceSecPerKm,
                row.get("avg_heart_rate") != null ? ((Number) row.get("avg_heart_rate")).intValue() : null,
                row.get("max_heart_rate") != null ? ((Number) row.get("max_heart_rate")).intValue() : null,
                row.get("calories") != null ? ((Number) row.get("calories")).intValue() : null,
                hrZoneSeconds
        );
    }
}
