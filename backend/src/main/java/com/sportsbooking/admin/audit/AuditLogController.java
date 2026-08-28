package com.sportsbooking.admin.audit;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.api.ApiResponse;
import com.sportsbooking.admin.auth.SessionAuthenticationFilter;
import com.sportsbooking.admin.auth.SessionPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditLogController {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;

    public AuditLogController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/admin/audit-logs")
    public ApiResponse<Map<String, Object>> listPlatform(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String action,
                                                         @RequestParam(required = false) String result, @RequestParam(required = false) String targetType,
                                                         @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        SessionPrincipal principal = required(request, "PLATFORM_ADMIN", "无平台审计记录权限");
        return ApiResponse.success(list(null, page, pageSize, action, result, targetType, dateFrom, dateTo));
    }

    @GetMapping("/venue/audit-logs")
    public ApiResponse<Map<String, Object>> listVenue(HttpServletRequest request, @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String action,
                                                      @RequestParam(required = false) String result, @RequestParam(required = false) String targetType,
                                                      @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        SessionPrincipal principal = required(request, "VENUE_ADMIN", "无场馆审计记录权限");
        return ApiResponse.success(list(principal.getStoreId(), page, pageSize, action, result, targetType, dateFrom, dateTo));
    }

    private Map<String, Object> list(Long storeId, int page, int pageSize, String action, String result, String targetType,
                                     String dateFrom, String dateTo) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
        java.util.ArrayList<Object> params = new java.util.ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE l.is_deleted = 0");
        if (storeId != null) {
            where.append(" AND l.store_id = ?");
            params.add(storeId);
        }
        if (action != null && !action.isBlank()) {
            where.append(" AND l.action LIKE ?");
            params.add("%" + action.trim() + "%");
        }
        if (result != null && !result.isBlank()) { where.append(" AND l.result = ?"); params.add(result); }
        if (targetType != null && !targetType.isBlank()) { where.append(" AND l.target_type = ?"); params.add(targetType); }
        if (dateFrom != null && !dateFrom.isBlank()) { where.append(" AND l.gmt_created_time >= ?"); params.add(parseDate(dateFrom).atStartOfDay()); }
        if (dateTo != null && !dateTo.isBlank()) { where.append(" AND l.gmt_created_time < ?"); params.add(parseDate(dateTo).plusDays(1).atStartOfDay()); }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM audit_log l" + where, Long.class, params.toArray());
        params.add(pageSize);
        params.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT l.id, l.action, l.target_type, l.target_id, l.result, l.reason, l.request_id, "
                + "l.gmt_created_time, a.display_name AS actor_name FROM audit_log l LEFT JOIN admin_account a ON a.id = l.actor_account_id" + where
                + " ORDER BY l.gmt_created_time DESC, l.id DESC LIMIT ? OFFSET ?", (resultSet, rowNum) -> {
                    Map<String, Object> data = new LinkedHashMap<String, Object>();
                    data.put("id", String.valueOf(resultSet.getLong("id")));
                    data.put("action", resultSet.getString("action"));
                    data.put("targetType", resultSet.getString("target_type"));
                    long targetId = resultSet.getLong("target_id");
                    data.put("targetId", resultSet.wasNull() ? null : String.valueOf(targetId));
                    data.put("actorName", resultSet.getString("actor_name"));
                    data.put("result", resultSet.getString("result"));
                    data.put("reason", resultSet.getString("reason"));
                    data.put("requestId", resultSet.getString("request_id"));
                    data.put("createTime", DATETIME_FORMATTER.format(resultSet.getObject("gmt_created_time", LocalDateTime.class)));
                    return data;
                }, params.toArray());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total == null ? 0 : total));
        data.put("records", records);
        return data;
    }

    private java.time.LocalDate parseDate(String value) {
        try { return java.time.LocalDate.parse(value); }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_REQUEST, 0, "审计日期参数不正确"); }
    }

    private SessionPrincipal required(HttpServletRequest request, String role, String deniedMessage) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!role.equals(principal.getRole()) || ("VENUE_ADMIN".equals(role) && principal.getStoreId() == null)) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, deniedMessage);
        }
        return principal;
    }
}
