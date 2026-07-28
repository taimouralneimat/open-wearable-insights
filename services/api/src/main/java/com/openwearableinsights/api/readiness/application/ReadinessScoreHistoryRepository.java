package com.openwearableinsights.api.readiness.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openwearableinsights.api.readiness.domain.FactorContribution;
import com.openwearableinsights.api.readiness.domain.ReadinessScore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists each day's computed readiness score, keyed by (account, date),
 * so /diff can compare today against a real prior day instead of the
 * synthetic baseline-derived stand-in it used before.
 *
 * Note: the Postgres driver is a runtimeOnly dependency, so this uses plain
 * JDBC string binding with an explicit ::jsonb cast in the SQL text rather
 * than org.postgresql.util.PGobject, which isn't on the compile classpath.
 */
@Repository
public class ReadinessScoreHistoryRepository {

    private static final Logger log = LoggerFactory.getLogger(ReadinessScoreHistoryRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ReadinessScoreHistoryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Persist (or overwrite) today's score for the given account. Safe to
     * call on every /latest or /diff request — one row per account per day.
     */
    public void upsertToday(Long accountId, LocalDate date, ReadinessScore score) {
        try {
            String factorsJson = objectMapper.writeValueAsString(score.factors());
            jdbcTemplate.update(
                    "INSERT INTO readiness_score_history " +
                    "(account_id, score_date, score, algorithm_version, provisional, " +
                    " baseline_period, confidence, data_quality, factors, " +
                    " missing_data_treatment, explanation, limitations, computed_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
                    "ON CONFLICT (account_id, score_date) DO UPDATE SET " +
                    "  score = EXCLUDED.score, " +
                    "  algorithm_version = EXCLUDED.algorithm_version, " +
                    "  provisional = EXCLUDED.provisional, " +
                    "  baseline_period = EXCLUDED.baseline_period, " +
                    "  confidence = EXCLUDED.confidence, " +
                    "  data_quality = EXCLUDED.data_quality, " +
                    "  factors = EXCLUDED.factors, " +
                    "  missing_data_treatment = EXCLUDED.missing_data_treatment, " +
                    "  explanation = EXCLUDED.explanation, " +
                    "  limitations = EXCLUDED.limitations, " +
                    "  computed_at = EXCLUDED.computed_at",
                    accountId, date, score.score(), score.algorithmVersion(), score.provisional(),
                    score.baselinePeriod(), score.confidence(), score.dataQuality(), factorsJson,
                    score.missingDataTreatment(), score.explanation(), score.limitations(),
                    Timestamp.from(score.computedAt())
            );
        } catch (Exception e) {
            log.warn("Failed to persist readiness score history for account {} on {}: {}",
                    accountId, date, e.getMessage(), e);
        }
    }

    /**
     * Find the persisted score for a specific account and date, if any.
     */
    public Optional<ReadinessScore> findByDate(Long accountId, LocalDate date) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT score, algorithm_version, provisional, baseline_period, confidence, " +
                    "       data_quality, factors, missing_data_treatment, explanation, " +
                    "       limitations, computed_at " +
                    "FROM readiness_score_history WHERE account_id = ? AND score_date = ?",
                    accountId, date
            );
            if (rows.isEmpty()) {
                return Optional.empty();
            }
            Map<String, Object> row = rows.get(0);
            List<FactorContribution> factors = objectMapper.readValue(
                    row.get("factors").toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FactorContribution.class)
            );
            return Optional.of(new ReadinessScore(
                    (Integer) row.get("score"),
                    (String) row.get("algorithm_version"),
                    (Boolean) row.get("provisional"),
                    (String) row.get("baseline_period"),
                    (String) row.get("confidence"),
                    (String) row.get("data_quality"),
                    factors,
                    (String) row.get("missing_data_treatment"),
                    (String) row.get("explanation"),
                    (String) row.get("limitations"),
                    ((Timestamp) row.get("computed_at")).toInstant()
            ));
        } catch (Exception e) {
            log.warn("Failed to fetch readiness score history for account {} on {}: {}",
                    accountId, date, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
