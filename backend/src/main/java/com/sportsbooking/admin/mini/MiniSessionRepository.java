package com.sportsbooking.admin.mini;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MiniSessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public MiniSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MiniSessionPrincipal findByAccessTokenHash(String tokenHash) {
        List<MiniSessionPrincipal> sessions = jdbcTemplate.query(
            "SELECT s.id, s.mini_user_id, s.access_expires_at, s.revoked_at FROM mini_session s JOIN mini_user u ON u.id = s.mini_user_id "
                + "WHERE s.access_token_hash = ? AND s.revoked_at IS NULL AND s.access_expires_at > UTC_TIMESTAMP() "
                + "AND s.is_deleted = 0 AND u.status = 'ACTIVE' AND u.is_deleted = 0",
            (resultSet, rowNum) -> new MiniSessionPrincipal(resultSet.getLong("id"), resultSet.getLong("mini_user_id"),
                resultSet.getObject("access_expires_at", java.time.LocalDateTime.class), resultSet.getObject("revoked_at") != null), tokenHash);
        return sessions.isEmpty() ? null : sessions.get(0);
    }
}
