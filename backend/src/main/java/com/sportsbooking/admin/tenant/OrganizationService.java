package com.sportsbooking.admin.tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import com.sportsbooking.admin.transaction.TransactionGateService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OrganizationService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TransactionGateService transactionGateService;

    public OrganizationService(JdbcTemplate jdbcTemplate, BCryptPasswordEncoder passwordEncoder, TokenService tokenService,
                               TransactionGateService transactionGateService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.transactionGateService = transactionGateService;
    }

    @Transactional
    public Map<String, Object> create(long operatorId, String organizationName, String storeName, String adminDisplayName,
                                      String adminUsername, String initialPassword, Integer serviceRateBasisPoints) {
        validate(organizationName, storeName, adminDisplayName, adminUsername, initialPassword, serviceRateBasisPoints);
        try {
            String organizationNo = businessNo("ORG");
            jdbcTemplate.update("INSERT INTO organization (organization_no, name, status, create_operator_id, modify_operator_id) VALUES (?, ?, 'ACTIVE', ?, ?)",
                organizationNo, organizationName, operatorId, operatorId);
            long organizationId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            String storeNo = businessNo("STR");
            jdbcTemplate.update("INSERT INTO store (organization_id, store_no, name, status, service_rate_basis_points, create_operator_id, modify_operator_id) VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?)",
                organizationId, storeNo, storeName, serviceRateBasisPoints, operatorId, operatorId);
            long storeId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            transactionGateService.createGates(operatorId, organizationId, storeId);

            jdbcTemplate.update("INSERT INTO admin_account (username, password_hash, display_name, role, store_id, status, force_password_change, create_operator_id, modify_operator_id) VALUES (?, ?, ?, 'VENUE_ADMIN', ?, 'ACTIVE', 1, ?, ?)",
                adminUsername, passwordEncoder.encode(initialPassword), adminDisplayName, storeId, operatorId, operatorId);
            long adminAccountId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) VALUES (?, 'ORGANIZATION_CREATED', 'ORGANIZATION', ?, ?, ?, 'SUCCEEDED', '场馆管理员临时密码线下交接', ?, ?)",
                operatorId, organizationId, organizationId, storeId, operatorId, operatorId);

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("organizationId", String.valueOf(organizationId));
            result.put("organizationNo", organizationNo);
            result.put("storeId", String.valueOf(storeId));
            result.put("storeNo", storeNo);
            result.put("adminAccountId", String.valueOf(adminAccountId));
            result.put("adminUsername", adminUsername);
            result.put("forcePasswordChange", true);
            return result;
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "业务编号或管理员登录名已存在");
        }
    }

    @Transactional
    public void updateStoreStatus(long operatorId, long storeId, String status, String reason) {
        transactionGateService.updatePlatformStoreStatus(operatorId, storeId, status, reason);
    }

    public Map<String, Object> list(int page, int pageSize, String status, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > 100 || (StringUtils.hasText(status) && !"ACTIVE".equals(status) && !"PAUSED".equals(status))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "场馆分页或状态参数不正确");
        }
        String where = " WHERE o.is_deleted = 0 AND s.is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND o.status = ?";
        }
        if (StringUtils.hasText(keyword)) {
            where += " AND (o.name LIKE ? OR s.name LIKE ? OR o.organization_no LIKE ? OR s.store_no LIKE ?)";
        }
        List<Object> filters = new java.util.ArrayList<Object>();
        if (StringUtils.hasText(status)) filters.add(status);
        if (StringUtils.hasText(keyword)) {
            String value = "%" + keyword.trim() + "%";
            filters.add(value); filters.add(value); filters.add(value); filters.add(value);
        }
        Object[] countParameters = filters.toArray();
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM organization o JOIN store s ON s.organization_id = o.id" + where,
            Long.class, countParameters);
        List<Object> parameters = new java.util.ArrayList<Object>(filters);
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT o.id AS organization_id, o.organization_no, o.name AS organization_name, o.status AS organization_status, o.gmt_created_time AS organization_created_time, "
                + "s.id AS store_id, s.store_no, s.name AS store_name, s.status AS store_status, s.service_rate_basis_points, "
                + "a.id AS admin_account_id, a.username AS admin_username, a.display_name AS admin_display_name, a.status AS admin_status, a.force_password_change "
                + "FROM organization o JOIN store s ON s.organization_id = o.id "
                + "LEFT JOIN admin_account a ON a.store_id = s.id AND a.role = 'VENUE_ADMIN' AND a.is_deleted = 0" + where
                + " ORDER BY o.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> organizationData(resultSet), parameters.toArray());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total == null ? 0 : total));
        data.put("records", records);
        return data;
    }

    public Map<String, Object> get(long organizationId) {
        List<Map<String, Object>> records = jdbcTemplate.query(
            "SELECT o.id AS organization_id, o.organization_no, o.name AS organization_name, o.status AS organization_status, o.gmt_created_time AS organization_created_time, "
                + "s.id AS store_id, s.store_no, s.name AS store_name, s.status AS store_status, s.service_rate_basis_points, "
                + "a.id AS admin_account_id, a.username AS admin_username, a.display_name AS admin_display_name, a.status AS admin_status, a.force_password_change "
                + "FROM organization o JOIN store s ON s.organization_id = o.id LEFT JOIN admin_account a ON a.store_id = s.id AND a.role = 'VENUE_ADMIN' AND a.is_deleted = 0 "
                + "WHERE o.id = ? AND o.is_deleted = 0 AND s.is_deleted = 0",
            (resultSet, rowNum) -> organizationData(resultSet), organizationId);
        if (records.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆不存在");
        Map<String, Object> data = records.get(0);
        data.put("transactionGate", transactionGateService.transactionStatusForStore(Long.parseLong((String) data.get("storeId"))));
        return data;
    }

    @Transactional
    public void updateOrganizationStatus(long operatorId, long organizationId, String status, String reason) {
        transactionGateService.updatePlatformOrganizationStatus(operatorId, organizationId, status, reason);
    }

    @Transactional
    public void resetVenueAdminPassword(long operatorId, long organizationId, String newPassword) {
        if (!StringUtils.hasText(newPassword)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "重置密码参数不正确");
        }
        List<AdminScope> accounts = jdbcTemplate.query(
            "SELECT a.id AS account_id, s.id AS store_id FROM admin_account a JOIN store s ON s.id = a.store_id "
                + "WHERE s.organization_id = ? AND a.role = 'VENUE_ADMIN' AND a.is_deleted = 0 AND s.is_deleted = 0",
            (resultSet, rowNum) -> new AdminScope(resultSet.getLong("account_id"), resultSet.getLong("store_id")), organizationId);
        if (accounts.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆管理员不存在");
        }
        AdminScope account = accounts.get(0);
        jdbcTemplate.update("UPDATE admin_account SET password_hash = ?, force_password_change = 1, failed_login_count = 0, locked_until = NULL, modify_operator_id = ? "
                + "WHERE id = ? AND is_deleted = 0", passwordEncoder.encode(newPassword), operatorId, account.accountId);
        jdbcTemplate.update("UPDATE admin_session SET revoked_at = UTC_TIMESTAMP(), revoke_reason = 'PLATFORM_PASSWORD_RESET', modify_operator_id = ? "
                + "WHERE account_id = ? AND revoked_at IS NULL AND is_deleted = 0", operatorId, account.accountId);
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, 'VENUE_ADMIN_PASSWORD_RESET', 'ACCOUNT', ?, ?, ?, 'SUCCEEDED', '平台重置场馆管理员密码', ?, ?)",
            operatorId, account.accountId, organizationId, account.storeId, operatorId, operatorId);
    }

    private Map<String, Object> organizationData(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("organizationId", String.valueOf(resultSet.getLong("organization_id")));
        data.put("organizationNo", resultSet.getString("organization_no"));
        data.put("organizationName", resultSet.getString("organization_name"));
        data.put("organizationStatus", resultSet.getString("organization_status"));
        data.put("storeId", String.valueOf(resultSet.getLong("store_id")));
        data.put("storeNo", resultSet.getString("store_no"));
        data.put("storeName", resultSet.getString("store_name"));
        data.put("storeStatus", resultSet.getString("store_status"));
        data.put("serviceRateBasisPoints", resultSet.getInt("service_rate_basis_points"));
        long accountId = resultSet.getLong("admin_account_id");
        data.put("adminAccountId", resultSet.wasNull() ? null : String.valueOf(accountId));
        data.put("adminUsername", resultSet.getString("admin_username"));
        data.put("adminDisplayName", resultSet.getString("admin_display_name"));
        data.put("adminStatus", resultSet.getString("admin_status"));
        data.put("forcePasswordChange", resultSet.getObject("force_password_change"));
        LocalDateTime createdTime = resultSet.getObject("organization_created_time", LocalDateTime.class);
        data.put("createTime", DATETIME_FORMATTER.format(createdTime));
        return data;
    }

    private void validate(String organizationName, String storeName, String adminDisplayName, String adminUsername,
                          String initialPassword, Integer serviceRateBasisPoints) {
        if (!StringUtils.hasText(organizationName) || !StringUtils.hasText(storeName) || !StringUtils.hasText(adminDisplayName)
            || !StringUtils.hasText(adminUsername) || !StringUtils.hasText(initialPassword) || serviceRateBasisPoints == null
            || serviceRateBasisPoints < 0 || serviceRateBasisPoints > 10000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "创建场馆参数不正确");
        }
    }

    private String businessNo(String prefix) {
        return prefix + tokenService.generate().replace("_", "").replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT);
    }

    private static final class AdminScope {
        private final long accountId;
        private final long storeId;
        private AdminScope(long accountId, long storeId) {
            this.accountId = accountId;
            this.storeId = storeId;
        }
    }
}
