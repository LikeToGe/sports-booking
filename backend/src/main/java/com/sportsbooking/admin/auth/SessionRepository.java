package com.sportsbooking.admin.auth;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(long accountId, String tokenHash, String csrfTokenHash, int ttlHours) {
        jdbcTemplate.update("INSERT INTO admin_session (account_id, session_token_hash, csrf_token_hash, expires_at, create_operator_id, modify_operator_id) VALUES (?, ?, ?, DATE_ADD(UTC_TIMESTAMP(), INTERVAL ? HOUR), ?, ?)",
            accountId, tokenHash, csrfTokenHash, ttlHours, accountId, accountId);
    }

    public SessionPrincipal findByToken(String tokenHash) {
        List<SessionPrincipal> results = jdbcTemplate.query(
            "SELECT s.id AS session_id, s.revoked_at, a.id AS account_id, a.username, a.display_name, a.role, a.store_id, "
                + "st.organization_id, a.force_password_change, s.csrf_token_hash, s.expires_at "
                + "FROM admin_session s JOIN admin_account a ON a.id = s.account_id "
                + "LEFT JOIN store st ON st.id = a.store_id AND st.is_deleted = 0 "
                + "WHERE s.session_token_hash = ? AND s.is_deleted = 0 "
                + "AND s.expires_at > UTC_TIMESTAMP() AND a.status = 'ACTIVE' AND a.is_deleted = 0",
            (resultSet, rowNum) -> {
                long storeId = resultSet.getLong("store_id");
                Long nullableStoreId = resultSet.wasNull() ? null : storeId;
                long organizationId = resultSet.getLong("organization_id");
                Long nullableOrganizationId = resultSet.wasNull() ? null : organizationId;
                return new SessionPrincipal(
                    resultSet.getLong("session_id"), resultSet.getLong("account_id"), resultSet.getString("username"),
                    resultSet.getString("display_name"), resultSet.getString("role"),
                    nullableStoreId, nullableOrganizationId,
                    resultSet.getBoolean("force_password_change"), resultSet.getString("csrf_token_hash"),
                    resultSet.getObject("expires_at", LocalDateTime.class), resultSet.getTimestamp("revoked_at") != null);
            }, tokenHash);
        return results.isEmpty() ? null : results.get(0);
    }

    public void revoke(long sessionId, long operatorId, String reason) {
        jdbcTemplate.update("UPDATE admin_session SET revoked_at = UTC_TIMESTAMP(), revoke_reason = ?, modify_operator_id = ? WHERE id = ? AND revoked_at IS NULL AND is_deleted = 0",
            reason, operatorId, sessionId);
    }

    public void revokeOtherSessions(long accountId, long currentSessionId) {
        jdbcTemplate.update("UPDATE admin_session SET revoked_at = UTC_TIMESTAMP(), revoke_reason = 'PASSWORD_CHANGED', modify_operator_id = ? WHERE account_id = ? AND id <> ? AND revoked_at IS NULL AND is_deleted = 0",
            accountId, accountId, currentSessionId);
    }
}
