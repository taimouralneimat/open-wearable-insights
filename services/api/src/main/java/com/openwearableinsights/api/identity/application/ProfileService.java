package com.openwearableinsights.api.identity.application;

import com.openwearableinsights.api.identity.domain.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Reads and updates the local account's profile — display name and primary
 * goal. Previously the {@code accounts} table carried nothing personal
 * (email, created_at, local_only); every controller hardcoded
 * account_id=1 with no visible identity behind it anywhere in the app.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /**
     * Suggested primary-goal options shown in the UI. Stored as free TEXT
     * (not a DB enum) so this list can change without a migration — mirrors
     * the journal taxonomy's "suggested, not enforced" convention.
     */
    public static final List<String> SUGGESTED_GOALS = List.of(
            "Recovery & readiness",
            "Endurance performance",
            "Strength & training load",
            "Sleep quality",
            "General wellness"
    );

    private final JdbcTemplate jdbcTemplate;

    public ProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Profile fetchProfile(Long accountId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, email, display_name, primary_goal FROM accounts WHERE id = ?",
                    accountId
            );
            if (rows.isEmpty()) {
                throw new IllegalStateException("No account with id " + accountId);
            }
            Map<String, Object> row = rows.get(0);
            return new Profile(
                    ((Number) row.get("id")).longValue(),
                    (String) row.get("email"),
                    (String) row.get("display_name"),
                    (String) row.get("primary_goal")
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to fetch profile for account {}: {}", accountId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch profile", e);
        }
    }

    public Profile updateProfile(Long accountId, String displayName, String primaryGoal) {
        jdbcTemplate.update(
                "UPDATE accounts SET display_name = ?, primary_goal = ? WHERE id = ?",
                blankToNull(displayName), blankToNull(primaryGoal), accountId
        );
        return fetchProfile(accountId);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
