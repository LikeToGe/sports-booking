package com.sportsbooking.admin.dashboard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;

    public DashboardController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/admin/dashboard/summary")
    public ApiResponse<Map<String, Object>> platform(HttpServletRequest request) {
        required(request, "PLATFORM_ADMIN");
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("venueCount", count("SELECT COUNT(1) FROM store WHERE is_deleted = 0"));
        data.put("activeVenueCount", count("SELECT COUNT(1) FROM store WHERE status = 'ACTIVE' AND is_deleted = 0"));
        data.put("orderCount", count("SELECT COUNT(1) FROM booking_order WHERE is_deleted = 0"));
        data.put("pendingOrderCount", count("SELECT COUNT(1) FROM booking_order WHERE order_status IN ('PENDING_PAYMENT','PAYMENT_PROCESSING','PAYMENT_UNKNOWN','CONFIRMED') AND is_deleted = 0"));
        data.put("paymentExceptionCount", count("SELECT COUNT(1) FROM booking_order WHERE order_status = 'PAYMENT_UNKNOWN' AND is_deleted = 0"));
        data.put("billCount", count("SELECT COUNT(1) FROM monthly_bill WHERE is_deleted = 0"));
        data.put("unpaidBillCount", count("SELECT COUNT(1) FROM monthly_bill WHERE bill_status IN ('ISSUED','OVERDUE') AND is_deleted = 0"));
        data.put("generatedAt", DATETIME_FORMATTER.format(LocalDateTime.now(BUSINESS_ZONE)));
        return ApiResponse.success(data);
    }

    @GetMapping("/venue/dashboard/summary")
    public ApiResponse<Map<String, Object>> venue(HttpServletRequest request) {
        SessionPrincipal principal = required(request, "VENUE_ADMIN");
        long storeId = principal.getStoreId();
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("todayOrderCount", count("SELECT COUNT(1) FROM booking_order WHERE store_id = ? AND inventory_date = ? AND is_deleted = 0", storeId, today));
        data.put("pendingVerificationCount", count("SELECT COUNT(1) FROM booking_order o WHERE o.store_id = ? AND o.inventory_date = ? AND o.order_status = 'CONFIRMED' AND o.is_deleted = 0 AND NOT EXISTS (SELECT 1 FROM booking_verification v WHERE v.booking_order_id = o.id AND v.is_deleted = 0)", storeId, today));
        data.put("verifiedCount", count("SELECT COUNT(1) FROM booking_verification WHERE store_id = ? AND DATE(CONVERT_TZ(verified_at, '+00:00', '+08:00')) = ? AND is_deleted = 0", storeId, today));
        data.put("completedCount", count("SELECT COUNT(1) FROM booking_order WHERE store_id = ? AND inventory_date = ? AND order_status = 'COMPLETED' AND is_deleted = 0", storeId, today));
        long totalSlots = count("SELECT COUNT(1) FROM inventory_slot WHERE store_id = ? AND inventory_date = ? AND is_deleted = 0", storeId, today);
        long availableSlots = count("SELECT COUNT(1) FROM inventory_slot i WHERE i.store_id = ? AND i.inventory_date = ? AND i.is_deleted = 0 AND NOT EXISTS (SELECT 1 FROM inventory_slot_atom m JOIN inventory_atom a ON a.id = m.inventory_atom_id WHERE m.inventory_slot_id = i.id AND m.is_deleted = 0 AND a.is_deleted = 0 AND a.inventory_status <> 'AVAILABLE')", storeId, today);
        data.put("inventorySlotCount", totalSlots);
        data.put("availableSlotCount", availableSlots);
        data.put("utilizationRate", totalSlots == 0 ? 0 : Math.round((totalSlots - availableSlots) * 1000.0 / totalSlots) / 10.0);
        List<Map<String, Object>> trend = jdbcTemplate.query("SELECT inventory_date, COUNT(1) AS order_count FROM booking_order WHERE store_id = ? AND inventory_date BETWEEN DATE_SUB(?, INTERVAL 6 DAY) AND ? AND is_deleted = 0 GROUP BY inventory_date ORDER BY inventory_date",
            (resultSet, rowNum) -> {
                Map<String, Object> point = new LinkedHashMap<String, Object>();
                point.put("date", resultSet.getObject("inventory_date", LocalDate.class).toString());
                point.put("orderCount", resultSet.getLong("order_count"));
                return point;
            }, storeId, today, today);
        data.put("sevenDayTrend", trend);
        data.put("generatedAt", DATETIME_FORMATTER.format(LocalDateTime.now(BUSINESS_ZONE)));
        return ApiResponse.success(data);
    }

    private long count(String sql, Object... parameters) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, parameters);
        return value == null ? 0 : value;
    }

    private SessionPrincipal required(HttpServletRequest request, String role) {
        Object value = request.getAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof SessionPrincipal) || ((SessionPrincipal) value).isRevoked()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, 0, "登录状态已失效");
        }
        SessionPrincipal principal = (SessionPrincipal) value;
        if (!role.equals(principal.getRole()) || ("VENUE_ADMIN".equals(role) && principal.getStoreId() == null)) {
            throw new ApiException(HttpStatus.FORBIDDEN, 0, "无经营看板权限");
        }
        return principal;
    }
}
