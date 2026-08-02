package com.openwearableinsights.api.garminconnect.application;

import com.openwearableinsights.api.garminconnect.domain.GarminConnectAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persists Garmin Connect connection state — status and OAuth tokens only,
 * never the raw password. One row per account (upserted), matching this
 * app's single-local-account model.
 */
@Repository
public class GarminConnectAccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public GarminConnectAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<GarminConnectAccount> find(Long accountId) {
        List<GarminConnectAccount> rows = jdbcTemplate.query(
                "SELECT account_id, status, email, last_error, connected_at, last_sync_at " +
                "FROM garmin_connect_account WHERE account_id = ?",
                (rs, rowNum) -> new GarminConnectAccount(
                        rs.getLong("account_id"),
                        rs.getString("status"),
                        rs.getString("email"),
                        rs.getString("last_error"),
                        toInstant(rs.getTimestamp("connected_at")),
                        toInstant(rs.getTimestamp("last_sync_at"))
                ),
                accountId
        );
        return rows.stream().findFirst();
    }

    /** Returns the account's stored tokens, if any (used before every sync/refresh). */
    public Optional<GarminTokens> findTokens(Long accountId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT di_token, di_refresh_token, di_client_id FROM garmin_connect_account " +
                "WHERE account_id = ? AND di_token IS NOT NULL",
                accountId
        );
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        return Optional.of(new GarminTokens(
                (String) row.get("di_token"), (String) row.get("di_refresh_token"), (String) row.get("di_client_id")
        ));
    }

    public void saveConnected(Long accountId, String email, GarminTokens tokens) {
        upsert(accountId, "connected", email, tokens, null, Instant.now(), null);
    }

    public void saveMfaRequired(Long accountId, String email) {
        upsert(accountId, "mfa_required", email, null, null, null, null);
    }

    public void saveTokens(Long accountId, GarminTokens tokens) {
        jdbcTemplate.update(
                "UPDATE garmin_connect_account SET di_token = ?, di_refresh_token = ?, di_client_id = ?, updated_at = now() " +
                "WHERE account_id = ?",
                tokens.diToken(), tokens.diRefreshToken(), tokens.diClientId(), accountId
        );
    }

    public void saveLastSync(Long accountId, Instant when) {
        jdbcTemplate.update(
                "UPDATE garmin_connect_account SET last_sync_at = ?, updated_at = now() WHERE account_id = ?",
                Timestamp.from(when), accountId
        );
    }

    public void saveError(Long accountId, String message) {
        upsert(accountId, "error", null, null, message, null, null);
    }

    public void disconnect(Long accountId) {
        jdbcTemplate.update(
                "UPDATE garmin_connect_account SET status = 'disconnected', di_token = NULL, " +
                "di_refresh_token = NULL, di_client_id = NULL, last_error = NULL, connected_at = NULL, updated_at = now() " +
                "WHERE account_id = ?",
                accountId
        );
    }

    private void upsert(Long accountId, String status, String email, GarminTokens tokens,
                         String lastError, Instant connectedAt, Instant lastSyncAt) {
        int updated = jdbcTemplate.update(
                "UPDATE garmin_connect_account SET status = ?, " +
                "email = COALESCE(?, email), " +
                "di_token = COALESCE(?, di_token), " +
                "di_refresh_token = COALESCE(?, di_refresh_token), " +
                "di_client_id = COALESCE(?, di_client_id), " +
                "last_error = ?, " +
                "connected_at = COALESCE(?, connected_at), " +
                "updated_at = now() " +
                "WHERE account_id = ?",
                status, email,
                tokens != null ? tokens.diToken() : null,
                tokens != null ? tokens.diRefreshToken() : null,
                tokens != null ? tokens.diClientId() : null,
                lastError,
                connectedAt != null ? Timestamp.from(connectedAt) : null,
                accountId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO garmin_connect_account " +
                    "(account_id, status, email, di_token, di_refresh_token, di_client_id, last_error, connected_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    accountId, status, email,
                    tokens != null ? tokens.diToken() : null,
                    tokens != null ? tokens.diRefreshToken() : null,
                    tokens != null ? tokens.diClientId() : null,
                    lastError,
                    connectedAt != null ? Timestamp.from(connectedAt) : null
            );
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
