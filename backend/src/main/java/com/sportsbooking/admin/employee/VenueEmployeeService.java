package com.sportsbooking.admin.employee;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VenueEmployeeService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;

    public VenueEmployeeService(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @Transactional
    public Map<String, Object> create(long operatorId, long organizationId, long storeId, String employeeName) {
        String name = requiredName(employeeName);
        String employeeNo = businessNo("EMP");
        jdbcTemplate.update("INSERT INTO venue_employee (employee_no, organization_id, store_id, employee_name, employee_status, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)", employeeNo, organizationId, storeId, name, operatorId, operatorId);
        long employeeId = lastInsertId();
        audit(operatorId, "EMPLOYEE_CREATED", employeeId, organizationId, storeId, "创建店员");
        return employeeData(requiredEmployee(storeId, employeeId, false));
    }

    public Map<String, Object> list(long storeId, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM venue_employee WHERE store_id = ? AND is_deleted = 0", Long.class, storeId);
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT e.id, e.employee_no, e.organization_id, e.store_id, e.employee_name, e.employee_status, e.disabled_at, "
                + "e.gmt_created_time, b.id AS active_binding_id, v.last_verified_at, v.today_verified_count "
                + "FROM venue_employee e LEFT JOIN employee_wechat_binding b ON b.employee_id = e.id AND b.binding_status = 'ACTIVE' AND b.is_deleted = 0 "
                + "LEFT JOIN (SELECT employee_id, MAX(verified_at) AS last_verified_at, SUM(CASE WHEN DATE(CONVERT_TZ(verified_at, '+00:00', '+08:00')) = CURDATE() THEN 1 ELSE 0 END) AS today_verified_count "
                + "FROM booking_verification WHERE is_deleted = 0 GROUP BY employee_id) v ON v.employee_id = e.id "
                + "WHERE e.store_id = ? AND e.is_deleted = 0 ORDER BY e.gmt_created_time DESC, e.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> employeeData(new Employee(resultSet.getLong("id"), resultSet.getString("employee_no"), resultSet.getLong("organization_id"),
                resultSet.getLong("store_id"), resultSet.getString("employee_name"), resultSet.getString("employee_status"),
                resultSet.getObject("disabled_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class),
                resultSet.getObject("active_binding_id") == null ? null : resultSet.getLong("active_binding_id"),
                resultSet.getObject("last_verified_at", LocalDateTime.class), resultSet.getObject("today_verified_count") == null ? 0 : resultSet.getInt("today_verified_count"))),
            storeId, pageSize, (page - 1L) * pageSize);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total == null ? 0 : total));
        data.put("records", records);
        return data;
    }

    public Map<String, Object> listVerifications(long storeId, int page, int pageSize, String result, Long employeeId, String date) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(storeId);
        StringBuilder where = new StringBuilder(" WHERE e.store_id = ? AND a.is_deleted = 0 AND e.is_deleted = 0");
        if (StringUtils.hasText(result)) { where.append(" AND a.attempt_result = ?"); parameters.add(result); }
        if (employeeId != null) { where.append(" AND a.employee_id = ?"); parameters.add(employeeId); }
        if (StringUtils.hasText(date)) {
            try { parameters.add(LocalDate.parse(date)); } catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, 0, "核销日期参数不正确"); }
            where.append(" AND DATE(CONVERT_TZ(a.gmt_created_time, '+00:00', '+08:00')) = ?");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM verification_attempt a JOIN venue_employee e ON e.id = a.employee_id" + where,
            Long.class, parameters.toArray());
        parameters.add(pageSize); parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT a.id, a.booking_order_id, a.verification_method, a.attempt_result, a.failure_reason, a.gmt_created_time, "
                + "e.id AS employee_id, e.employee_name, o.order_no FROM verification_attempt a JOIN venue_employee e ON e.id = a.employee_id "
                + "LEFT JOIN booking_order o ON o.id = a.booking_order_id" + where + " ORDER BY a.gmt_created_time DESC, a.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", String.valueOf(resultSet.getLong("id")));
                item.put("employeeId", String.valueOf(resultSet.getLong("employee_id")));
                item.put("employeeName", resultSet.getString("employee_name"));
                item.put("orderNo", resultSet.getString("order_no"));
                item.put("verificationMethod", resultSet.getString("verification_method"));
                item.put("result", resultSet.getString("attempt_result"));
                item.put("failureReason", resultSet.getString("failure_reason"));
                item.put("createTime", DATETIME_FORMATTER.format(resultSet.getObject("gmt_created_time", LocalDateTime.class)));
                return item;
            }, parameters.toArray());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page)); data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total == null ? 0 : total)); data.put("records", records);
        return data;
    }

    @Transactional
    public Map<String, Object> updateStatus(long operatorId, long storeId, long employeeId, String status) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "店员状态参数不正确");
        }
        Employee employee = requiredEmployee(storeId, employeeId, true);
        if (status.equals(employee.status)) {
            return employeeData(employee);
        }
        jdbcTemplate.update("UPDATE venue_employee SET employee_status = ?, disabled_at = CASE WHEN ? = 'DISABLED' THEN UTC_TIMESTAMP() ELSE NULL END, "
                + "modify_operator_id = ? WHERE id = ?", status, status, operatorId, employee.id);
        if ("DISABLED".equals(status)) {
            jdbcTemplate.update("UPDATE staff_session SET revoked_at = COALESCE(revoked_at, UTC_TIMESTAMP()), modify_operator_id = ? "
                + "WHERE employee_id = ? AND revoked_at IS NULL AND is_deleted = 0", operatorId, employee.id);
            jdbcTemplate.update("UPDATE employee_binding_code SET code_status = 'REVOKED', modify_operator_id = ? "
                + "WHERE employee_id = ? AND code_status = 'ACTIVE' AND is_deleted = 0", operatorId, employee.id);
        }
        audit(operatorId, "EMPLOYEE_" + status, employee.id, employee.organizationId, employee.storeId, "店员状态变更");
        return employeeData(requiredEmployee(storeId, employeeId, false));
    }

    @Transactional
    public Map<String, Object> issueBindingCode(long operatorId, long storeId, long employeeId) {
        Employee employee = requiredEmployee(storeId, employeeId, true);
        if (!"ACTIVE".equals(employee.status)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "停用店员不能生成绑定码");
        }
        jdbcTemplate.update("UPDATE employee_binding_code SET code_status = 'REVOKED', modify_operator_id = ? "
            + "WHERE employee_id = ? AND code_status = 'ACTIVE' AND is_deleted = 0", operatorId, employee.id);
        String code = "BND" + tokenService.generate();
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10);
        jdbcTemplate.update("INSERT INTO employee_binding_code (employee_id, code_hash, code_status, expires_at, issued_by_account_id, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?)", employee.id, tokenService.keyedHash("employee-binding-code", code), expiresAt, operatorId, operatorId, operatorId);
        audit(operatorId, "EMPLOYEE_BINDING_CODE_ISSUED", employee.id, employee.organizationId, employee.storeId, "一次性绑定码已签发");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("employeeId", String.valueOf(employee.id));
        data.put("employeeName", employee.name);
        data.put("bindingCode", code);
        data.put("expiresAt", DATETIME_FORMATTER.format(expiresAt));
        return data;
    }

    private Employee requiredEmployee(long storeId, long employeeId, boolean lock) {
        List<Employee> employees = jdbcTemplate.query("SELECT e.id, e.employee_no, e.organization_id, e.store_id, e.employee_name, e.employee_status, e.disabled_at, "
                + "e.gmt_created_time, b.id AS active_binding_id, v.last_verified_at, v.today_verified_count FROM venue_employee e "
                + "LEFT JOIN employee_wechat_binding b ON b.employee_id = e.id AND b.binding_status = 'ACTIVE' AND b.is_deleted = 0 "
                + "LEFT JOIN (SELECT employee_id, MAX(verified_at) AS last_verified_at, SUM(CASE WHEN DATE(CONVERT_TZ(verified_at, '+00:00', '+08:00')) = CURDATE() THEN 1 ELSE 0 END) AS today_verified_count "
                + "FROM booking_verification WHERE is_deleted = 0 GROUP BY employee_id) v ON v.employee_id = e.id "
                + "WHERE e.id = ? AND e.store_id = ? AND e.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new Employee(resultSet.getLong("id"), resultSet.getString("employee_no"), resultSet.getLong("organization_id"),
                resultSet.getLong("store_id"), resultSet.getString("employee_name"), resultSet.getString("employee_status"),
                resultSet.getObject("disabled_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class),
                resultSet.getObject("active_binding_id") == null ? null : resultSet.getLong("active_binding_id"),
                resultSet.getObject("last_verified_at", LocalDateTime.class), resultSet.getObject("today_verified_count") == null ? 0 : resultSet.getInt("today_verified_count")), employeeId, storeId);
        if (employees.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "店员不存在或不属于当前场馆");
        }
        return employees.get(0);
    }

    private Map<String, Object> employeeData(Employee employee) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(employee.id));
        data.put("employeeNo", employee.employeeNo);
        data.put("employeeName", employee.name);
        data.put("employeeStatus", employee.status);
        data.put("bindingStatus", employee.activeBindingId == null ? "UNBOUND" : "BOUND");
        data.put("todayVerifiedCount", employee.todayVerifiedCount);
        data.put("lastVerifiedAt", employee.lastVerifiedAt == null ? null : DATETIME_FORMATTER.format(employee.lastVerifiedAt));
        data.put("disabledAt", employee.disabledAt == null ? null : DATETIME_FORMATTER.format(employee.disabledAt));
        data.put("createTime", DATETIME_FORMATTER.format(employee.createdTime));
        return data;
    }

    private String requiredName(String value) {
        if (!StringUtils.hasText(value) || value.trim().length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "店员姓名参数不正确");
        }
        return value.trim();
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增店员ID");
        }
        return id;
    }

    private String businessNo(String prefix) {
        return prefix + tokenService.generate().replace("_", "").replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private void audit(long operatorId, String action, long employeeId, long organizationId, long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'VENUE_EMPLOYEE', ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", operatorId, action, employeeId, organizationId, storeId, reason,
            operatorId, operatorId);
    }

    private static final class Employee {
        private final long id;
        private final String employeeNo;
        private final long organizationId;
        private final long storeId;
        private final String name;
        private final String status;
        private final LocalDateTime disabledAt;
        private final LocalDateTime createdTime;
        private final Long activeBindingId;
        private final LocalDateTime lastVerifiedAt;
        private final int todayVerifiedCount;
        private Employee(long id, String employeeNo, long organizationId, long storeId, String name, String status, LocalDateTime disabledAt,
                         LocalDateTime createdTime, Long activeBindingId, LocalDateTime lastVerifiedAt, int todayVerifiedCount) {
            this.id = id;
            this.employeeNo = employeeNo;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.name = name;
            this.status = status;
            this.disabledAt = disabledAt;
            this.createdTime = createdTime;
            this.activeBindingId = activeBindingId;
            this.lastVerifiedAt = lastVerifiedAt;
            this.todayVerifiedCount = todayVerifiedCount;
        }
    }
}
