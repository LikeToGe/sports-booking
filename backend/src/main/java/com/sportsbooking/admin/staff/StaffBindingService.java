package com.sportsbooking.admin.staff;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.RedisAvailabilityService;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StaffBindingService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern MOCK_CODE_PATTERN = Pattern.compile("mock:[A-Za-z0-9_-]{3,64}");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final RedisAvailabilityService redisAvailabilityService;

    public StaffBindingService(JdbcTemplate jdbcTemplate, TokenService tokenService, RedisAvailabilityService redisAvailabilityService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.redisAvailabilityService = redisAvailabilityService;
    }

    @Transactional
    public Map<String, Object> bind(String bindingCode, String authorizationCode) {
        redisAvailabilityService.requireAvailable();
        if (!StringUtils.hasText(bindingCode) || bindingCode.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "绑定码参数不正确");
        }
        String subjectHash = exchangeMockWechatCode(authorizationCode);
        BindingCode code = requiredBindingCode(tokenService.keyedHash("employee-binding-code", bindingCode), true);
        if (!"ACTIVE".equals(code.status)) {
            return replayUsedCode(code, subjectHash);
        }
        if (!code.expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            jdbcTemplate.update("UPDATE employee_binding_code SET code_status = 'EXPIRED', modify_operator_id = issued_by_account_id WHERE id = ?", code.id);
            throw new ApiException(HttpStatus.CONFLICT, 0, "绑定码已过期");
        }
        Employee employee = requiredEmployee(code.employeeId, true);
        if (!"ACTIVE".equals(employee.status)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "店员已停用");
        }
        ExistingBinding existingSubject = findActiveSubjectBinding(subjectHash, true);
        if (existingSubject != null && existingSubject.employeeId != employee.id) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该微信身份已绑定其他活动店员");
        }
        jdbcTemplate.update("UPDATE staff_session SET revoked_at = COALESCE(revoked_at, UTC_TIMESTAMP()), modify_operator_id = 0 "
            + "WHERE employee_id = ? AND revoked_at IS NULL AND is_deleted = 0", employee.id);
        jdbcTemplate.update("UPDATE employee_wechat_binding SET binding_status = 'REVOKED', active_employee_key = NULL, active_subject_key = NULL, "
                + "revoked_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE employee_id = ? AND binding_status = 'ACTIVE' AND is_deleted = 0", employee.id);
        long bindingId;
        if (existingSubject != null) {
            bindingId = existingSubject.id;
            jdbcTemplate.update("UPDATE employee_wechat_binding SET binding_status = 'ACTIVE', active_employee_key = ?, active_subject_key = ?, "
                    + "bound_at = UTC_TIMESTAMP(), revoked_at = NULL, modify_operator_id = 0 WHERE id = ?", employee.id, subjectHash, bindingId);
        } else {
            try {
                jdbcTemplate.update("INSERT INTO employee_wechat_binding (employee_id, provider, provider_subject_hash, binding_status, active_employee_key, active_subject_key, "
                        + "bound_at, create_operator_id, modify_operator_id) VALUES (?, 'MOCK_WECHAT', ?, 'ACTIVE', ?, ?, UTC_TIMESTAMP(), 0, 0)",
                    employee.id, subjectHash, employee.id, subjectHash);
            } catch (DuplicateKeyException exception) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "该微信身份已绑定其他活动店员");
            }
            bindingId = lastInsertId();
        }
        jdbcTemplate.update("UPDATE employee_binding_code SET code_status = 'USED', used_at = UTC_TIMESTAMP(), modify_operator_id = issued_by_account_id "
            + "WHERE id = ? AND code_status = 'ACTIVE'", code.id);
        audit("STAFF_WECHAT_BOUND", employee, "店员微信身份已绑定");
        return issueSession(employee, bindingId);
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken) {
        redisAvailabilityService.requireAvailable();
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "刷新令牌参数不正确");
        }
        List<Session> sessions = jdbcTemplate.query("SELECT s.id, s.employee_id, s.employee_wechat_binding_id, e.organization_id, e.store_id, e.employee_name, e.employee_status, "
                + "s.refresh_expires_at, s.revoked_at, b.binding_status FROM staff_session s JOIN venue_employee e ON e.id = s.employee_id "
                + "JOIN employee_wechat_binding b ON b.id = s.employee_wechat_binding_id WHERE s.refresh_token_hash = ? AND s.is_deleted = 0 FOR UPDATE",
            (resultSet, rowNum) -> new Session(resultSet.getLong("id"), resultSet.getLong("employee_id"), resultSet.getLong("employee_wechat_binding_id"),
                resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getString("employee_name"), resultSet.getString("employee_status"),
                resultSet.getObject("refresh_expires_at", LocalDateTime.class), resultSet.getObject("revoked_at") != null, resultSet.getString("binding_status")), tokenService.hash(refreshToken));
        if (sessions.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "店员刷新令牌已失效");
        }
        Session session = sessions.get(0);
        if (session.revoked || !session.refreshExpiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC)) || !"ACTIVE".equals(session.employeeStatus)
            || !"ACTIVE".equals(session.bindingStatus)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "店员刷新令牌已失效");
        }
        jdbcTemplate.update("UPDATE staff_session SET revoked_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ? AND revoked_at IS NULL", session.id);
        return issueSession(new Employee(session.employeeId, session.organizationId, session.storeId, session.employeeName, session.employeeStatus), session.bindingId);
    }

    @Transactional
    public void logout(long sessionId) {
        jdbcTemplate.update("UPDATE staff_session SET revoked_at = COALESCE(revoked_at, UTC_TIMESTAMP()), modify_operator_id = 0 WHERE id = ? AND is_deleted = 0", sessionId);
    }

    public Map<String, Object> profile(StaffSessionPrincipal principal) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("employeeId", String.valueOf(principal.getEmployeeId()));
        data.put("employeeName", principal.getEmployeeName());
        data.put("organizationId", String.valueOf(principal.getOrganizationId()));
        data.put("storeId", String.valueOf(principal.getStoreId()));
        data.put("accessExpiresAt", DATETIME_FORMATTER.format(principal.getAccessExpiresAt()));
        return data;
    }

    private Map<String, Object> replayUsedCode(BindingCode code, String subjectHash) {
        if (!"USED".equals(code.status)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "绑定码不可用");
        }
        ExistingBinding binding = findActiveEmployeeBinding(code.employeeId);
        if (binding == null || !subjectHash.equals(binding.subjectHash)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "绑定码已使用");
        }
        Employee employee = requiredEmployee(code.employeeId, false);
        if (!"ACTIVE".equals(employee.status)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "店员已停用");
        }
        return issueSession(employee, binding.id);
    }

    private Map<String, Object> issueSession(Employee employee, long bindingId) {
        String accessToken = tokenService.generate();
        String refreshToken = tokenService.generate();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime accessExpiresAt = now.plusHours(2);
        LocalDateTime refreshExpiresAt = now.plusDays(30);
        jdbcTemplate.update("INSERT INTO staff_session (employee_id, employee_wechat_binding_id, access_token_hash, refresh_token_hash, access_expires_at, refresh_expires_at, "
                + "create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, 0, 0)", employee.id, bindingId, tokenService.hash(accessToken),
            tokenService.hash(refreshToken), accessExpiresAt, refreshExpiresAt);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("employeeId", String.valueOf(employee.id));
        data.put("employeeName", employee.name);
        data.put("organizationId", String.valueOf(employee.organizationId));
        data.put("storeId", String.valueOf(employee.storeId));
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("accessExpiresAt", DATETIME_FORMATTER.format(accessExpiresAt));
        return data;
    }

    private String exchangeMockWechatCode(String code) {
        if (!StringUtils.hasText(code) || !MOCK_CODE_PATTERN.matcher(code).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "Mock微信授权码参数不正确");
        }
        return tokenService.hash(code.substring("mock:".length()));
    }

    private BindingCode requiredBindingCode(String hash, boolean lock) {
        List<BindingCode> codes = jdbcTemplate.query("SELECT id, employee_id, code_status, expires_at FROM employee_binding_code WHERE code_hash = ? AND is_deleted = 0"
                + (lock ? " FOR UPDATE" : ""), (resultSet, rowNum) -> new BindingCode(resultSet.getLong("id"), resultSet.getLong("employee_id"),
                resultSet.getString("code_status"), resultSet.getObject("expires_at", LocalDateTime.class)), hash);
        if (codes.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "绑定码不存在");
        }
        return codes.get(0);
    }

    private Employee requiredEmployee(long employeeId, boolean lock) {
        List<Employee> employees = jdbcTemplate.query("SELECT id, organization_id, store_id, employee_name, employee_status FROM venue_employee WHERE id = ? "
                + "AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""), (resultSet, rowNum) -> new Employee(resultSet.getLong("id"),
                resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getString("employee_name"), resultSet.getString("employee_status")), employeeId);
        if (employees.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "店员不存在");
        }
        return employees.get(0);
    }

    private ExistingBinding findActiveSubjectBinding(String subjectHash, boolean lock) {
        List<ExistingBinding> bindings = jdbcTemplate.query("SELECT id, employee_id, provider_subject_hash FROM employee_wechat_binding WHERE active_subject_key = ? "
                + "AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""), (resultSet, rowNum) -> new ExistingBinding(resultSet.getLong("id"),
                resultSet.getLong("employee_id"), resultSet.getString("provider_subject_hash")), subjectHash);
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    private ExistingBinding findActiveEmployeeBinding(long employeeId) {
        List<ExistingBinding> bindings = jdbcTemplate.query("SELECT id, employee_id, provider_subject_hash FROM employee_wechat_binding WHERE active_employee_key = ? "
                + "AND is_deleted = 0", (resultSet, rowNum) -> new ExistingBinding(resultSet.getLong("id"), resultSet.getLong("employee_id"),
                resultSet.getString("provider_subject_hash")), employeeId);
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    private void audit(String action, Employee employee, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, 'VENUE_EMPLOYEE', ?, ?, ?, 'SUCCEEDED', ?, 0, 0)", action, employee.id, employee.organizationId, employee.storeId, reason);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取微信绑定ID");
        }
        return id;
    }

    private static final class BindingCode {
        private final long id;
        private final long employeeId;
        private final String status;
        private final LocalDateTime expiresAt;
        private BindingCode(long id, long employeeId, String status, LocalDateTime expiresAt) {
            this.id = id;
            this.employeeId = employeeId;
            this.status = status;
            this.expiresAt = expiresAt;
        }
    }

    private static final class Employee {
        private final long id;
        private final long organizationId;
        private final long storeId;
        private final String name;
        private final String status;
        private Employee(long id, long organizationId, long storeId, String name, String status) {
            this.id = id;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.name = name;
            this.status = status;
        }
    }

    private static final class ExistingBinding {
        private final long id;
        private final long employeeId;
        private final String subjectHash;
        private ExistingBinding(long id, long employeeId, String subjectHash) {
            this.id = id;
            this.employeeId = employeeId;
            this.subjectHash = subjectHash;
        }
    }

    private static final class Session {
        private final long id;
        private final long employeeId;
        private final long bindingId;
        private final long organizationId;
        private final long storeId;
        private final String employeeName;
        private final String employeeStatus;
        private final LocalDateTime refreshExpiresAt;
        private final boolean revoked;
        private final String bindingStatus;
        private Session(long id, long employeeId, long bindingId, long organizationId, long storeId, String employeeName, String employeeStatus,
                        LocalDateTime refreshExpiresAt, boolean revoked, String bindingStatus) {
            this.id = id;
            this.employeeId = employeeId;
            this.bindingId = bindingId;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.employeeName = employeeName;
            this.employeeStatus = employeeStatus;
            this.refreshExpiresAt = refreshExpiresAt;
            this.revoked = revoked;
            this.bindingStatus = bindingStatus;
        }
    }
}
