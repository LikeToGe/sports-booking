package com.sportsbooking.admin.staff;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StaffSessionRepository {
    private final JdbcTemplate jdbcTemplate;

    public StaffSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public StaffSessionPrincipal findByAccessTokenHash(String tokenHash) {
        List<StaffSessionPrincipal> sessions = jdbcTemplate.query("SELECT s.id, s.employee_id, e.organization_id, e.store_id, e.employee_name, s.access_expires_at, s.revoked_at "
                + "FROM staff_session s JOIN venue_employee e ON e.id = s.employee_id "
                + "JOIN employee_wechat_binding b ON b.id = s.employee_wechat_binding_id "
                + "WHERE s.access_token_hash = ? AND s.revoked_at IS NULL AND s.access_expires_at > UTC_TIMESTAMP() AND s.is_deleted = 0 "
                + "AND e.employee_status = 'ACTIVE' AND e.is_deleted = 0 AND b.binding_status = 'ACTIVE' AND b.is_deleted = 0",
            (resultSet, rowNum) -> new StaffSessionPrincipal(resultSet.getLong("id"), resultSet.getLong("employee_id"),
                resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getString("employee_name"),
                resultSet.getObject("access_expires_at", java.time.LocalDateTime.class), resultSet.getObject("revoked_at") != null), tokenHash);
        return sessions.isEmpty() ? null : sessions.get(0);
    }
}
