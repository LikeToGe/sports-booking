package com.sportsbooking.admin.order;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderQueryService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private final JdbcTemplate jdbcTemplate;

    public OrderQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> list(Long storeId, int page, int pageSize, String orderStatus, String orderNo,
                                    String paymentStatus, String dateFrom, String dateTo, String keyword) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
        List<Object> parameters = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE o.is_deleted = 0");
        if (storeId != null) {
            where.append(" AND o.store_id = ?");
            parameters.add(storeId);
        }
        if (StringUtils.hasText(orderStatus)) {
            where.append(" AND o.order_status = ?");
            parameters.add(orderStatus);
        }
        if (StringUtils.hasText(orderNo)) {
            where.append(" AND o.order_no LIKE ?");
            parameters.add("%" + orderNo.trim() + "%");
        }
        if (StringUtils.hasText(paymentStatus)) {
            where.append(" AND EXISTS (SELECT 1 FROM payment_order fp WHERE fp.booking_order_id = o.id AND fp.payment_status = ? AND fp.is_deleted = 0)");
            parameters.add(paymentStatus);
        }
        if (StringUtils.hasText(dateFrom)) {
            where.append(" AND o.inventory_date >= ?");
            parameters.add(parseDate(dateFrom));
        }
        if (StringUtils.hasText(dateTo)) {
            where.append(" AND o.inventory_date <= ?");
            parameters.add(parseDate(dateTo));
        }
        if (StringUtils.hasText(keyword)) {
            where.append(" AND EXISTS (SELECT 1 FROM store fs WHERE fs.id = o.store_id AND fs.name LIKE ?)");
            parameters.add("%" + keyword.trim() + "%");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM booking_order o" + where, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(orderSelect() + where + " ORDER BY o.gmt_created_time DESC, o.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> orderData(order(resultSet), false), parameters.toArray());
        return page(page, pageSize, total == null ? 0 : total, records);
    }

    public Map<String, Object> get(Long storeId, long orderId) {
        Order order = requiredOrder(storeId, orderId);
        Map<String, Object> data = orderData(order, false);
        List<Map<String, Object>> events = new ArrayList<Map<String, Object>>();
        addEvent(events, "BOOKING_CREATED", order.createdTime);
        addEvent(events, "PAYMENT_SUCCEEDED", order.paidAt);
        addEvent(events, "VERIFIED", order.verifiedAt);
        addEvent(events, "BOOKING_CANCELLED", order.cancelledAt);
        addEvent(events, "COMPLETED", order.completedAt);
        data.put("events", events);
        data.put("cancellation", cancellation(order.id));
        return data;
    }

    private void addEvent(List<Map<String, Object>> events, String type, LocalDateTime time) {
        if (time == null) return;
        Map<String, Object> event = new LinkedHashMap<String, Object>();
        event.put("type", type);
        event.put("time", DATETIME_FORMATTER.format(time));
        events.add(event);
    }

    private Map<String, Object> cancellation(long orderId) {
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT cancellation_type, reason, cancelled_by_type, gmt_created_time FROM booking_cancellation WHERE booking_order_id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("type", resultSet.getString("cancellation_type"));
                item.put("reason", resultSet.getString("reason"));
                item.put("cancelledByType", resultSet.getString("cancelled_by_type"));
                item.put("time", DATETIME_FORMATTER.format(resultSet.getObject("gmt_created_time", LocalDateTime.class)));
                return item;
            }, orderId);
        return records.isEmpty() ? null : records.get(0);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "预约日期参数不正确");
        }
    }

    public Map<String, Object> revealPhone(long actorId, Long storeId, long orderId, String reason) {
        if (!StringUtils.hasText(reason) || reason.trim().length() > 256) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "查看完整手机号必须填写不超过256字的原因");
        }
        Order order = requiredOrder(storeId, orderId);
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, "
                + "create_operator_id, modify_operator_id) VALUES (?, 'BOOKING_CONTACT_PHONE_REVEALED', 'BOOKING_ORDER', ?, ?, ?, 'SUCCEEDED', ?, ?, ?)",
            actorId, order.id, order.organizationId, order.storeId, reason.trim(), actorId, actorId);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("orderId", String.valueOf(order.id));
        data.put("contactPhone", order.contactPhone);
        return data;
    }

    private Order requiredOrder(Long storeId, long orderId) {
        List<Object> params = new ArrayList<Object>();
        params.add(orderId);
        String where = " WHERE o.id = ? AND o.is_deleted = 0";
        if (storeId != null) {
            where += " AND o.store_id = ?";
            params.add(storeId);
        }
        List<Order> orders = jdbcTemplate.query(orderSelect() + where, (resultSet, rowNum) -> order(resultSet), params.toArray());
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在或不属于当前场馆");
        }
        return orders.get(0);
    }

    private String orderSelect() {
        return "SELECT o.id, o.order_no, o.organization_id, o.store_id, o.inventory_date, o.start_time, o.end_time, o.price_fen, o.contact_name, o.contact_phone, "
            + "o.order_status, o.paid_at, o.cancelled_at, o.completed_at, o.gmt_created_time, s.name AS store_name, u.name AS sellable_unit_name, "
            + "p.id AS payment_id, p.payment_status, r.id AS refund_id, r.refund_status, v.id AS verification_id, v.verified_at, e.employee_name AS verified_employee_name "
            + "FROM booking_order o JOIN store s ON s.id = o.store_id JOIN sellable_unit_version suv ON suv.id = o.sellable_unit_version_id "
            + "JOIN sellable_unit u ON u.id = suv.sellable_unit_id LEFT JOIN payment_order p ON p.booking_order_id = o.id AND p.is_deleted = 0 "
            + "LEFT JOIN refund_order r ON r.id = (SELECT rr.id FROM refund_order rr WHERE rr.booking_order_id = o.id AND rr.is_deleted = 0 ORDER BY rr.id DESC LIMIT 1) "
            + "LEFT JOIN booking_verification v ON v.booking_order_id = o.id AND v.is_deleted = 0 LEFT JOIN venue_employee e ON e.id = v.employee_id";
    }

    private Order order(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        long paymentId = resultSet.getLong("payment_id");
        Long nullablePaymentId = resultSet.wasNull() ? null : paymentId;
        long refundId = resultSet.getLong("refund_id");
        Long nullableRefundId = resultSet.wasNull() ? null : refundId;
        long verificationId = resultSet.getLong("verification_id");
        Long nullableVerificationId = resultSet.wasNull() ? null : verificationId;
        return new Order(resultSet.getLong("id"), resultSet.getString("order_no"), resultSet.getLong("organization_id"), resultSet.getLong("store_id"),
            resultSet.getString("store_name"), resultSet.getString("sellable_unit_name"), resultSet.getObject("inventory_date", LocalDate.class),
            resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"),
            resultSet.getString("contact_name"), resultSet.getString("contact_phone"), resultSet.getString("order_status"), nullablePaymentId,
            resultSet.getString("payment_status"), nullableRefundId, resultSet.getString("refund_status"), nullableVerificationId,
            resultSet.getObject("verified_at", LocalDateTime.class), resultSet.getString("verified_employee_name"),
            resultSet.getObject("paid_at", LocalDateTime.class), resultSet.getObject("cancelled_at", LocalDateTime.class),
            resultSet.getObject("completed_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private Map<String, Object> orderData(Order order, boolean fullPhone) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(order.id));
        data.put("orderNo", order.orderNo);
        data.put("storeId", String.valueOf(order.storeId));
        data.put("storeName", order.storeName);
        data.put("sellableUnitName", order.sellableUnitName);
        data.put("date", order.date.toString());
        data.put("startTime", TIME_FORMATTER.format(order.startTime));
        data.put("endTime", TIME_FORMATTER.format(order.endTime));
        data.put("priceFen", order.priceFen);
        data.put("orderStatus", order.orderStatus);
        data.put("contactName", maskName(order.contactName));
        data.put("contactPhone", fullPhone ? order.contactPhone : maskPhone(order.contactPhone));
        data.put("paymentId", order.paymentId == null ? null : String.valueOf(order.paymentId));
        data.put("paymentStatus", order.paymentStatus);
        data.put("refundId", order.refundId == null ? null : String.valueOf(order.refundId));
        data.put("refundStatus", order.refundStatus);
        data.put("verificationStatus", order.verificationId == null ? "UNVERIFIED" : "VERIFIED");
        data.put("verifiedAt", order.verifiedAt == null ? null : DATETIME_FORMATTER.format(order.verifiedAt));
        data.put("verifiedEmployeeName", order.verifiedEmployeeName);
        data.put("payTime", order.paidAt == null ? null : DATETIME_FORMATTER.format(order.paidAt));
        data.put("cancelTime", order.cancelledAt == null ? null : DATETIME_FORMATTER.format(order.cancelledAt));
        data.put("completedTime", order.completedAt == null ? null : DATETIME_FORMATTER.format(order.completedAt));
        data.put("createTime", DATETIME_FORMATTER.format(order.createdTime));
        return data;
    }

    private String maskName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() == 1 ? "*" : value.substring(0, 1) + "*";
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private Map<String, Object> page(int page, int pageSize, long total, List<Map<String, Object>> records) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total));
        data.put("records", records);
        return data;
    }

    private static final class Order {
        private final long id;
        private final String orderNo;
        private final long organizationId;
        private final long storeId;
        private final String storeName;
        private final String sellableUnitName;
        private final LocalDate date;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final String contactName;
        private final String contactPhone;
        private final String orderStatus;
        private final Long paymentId;
        private final String paymentStatus;
        private final Long refundId;
        private final String refundStatus;
        private final Long verificationId;
        private final LocalDateTime verifiedAt;
        private final String verifiedEmployeeName;
        private final LocalDateTime paidAt;
        private final LocalDateTime cancelledAt;
        private final LocalDateTime completedAt;
        private final LocalDateTime createdTime;
        private Order(long id, String orderNo, long organizationId, long storeId, String storeName, String sellableUnitName, LocalDate date,
                      LocalTime startTime, LocalTime endTime, int priceFen, String contactName, String contactPhone, String orderStatus,
                      Long paymentId, String paymentStatus, Long refundId, String refundStatus, Long verificationId, LocalDateTime verifiedAt,
                      String verifiedEmployeeName, LocalDateTime paidAt, LocalDateTime cancelledAt, LocalDateTime completedAt, LocalDateTime createdTime) {
            this.id = id;
            this.orderNo = orderNo;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.storeName = storeName;
            this.sellableUnitName = sellableUnitName;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
            this.orderStatus = orderStatus;
            this.paymentId = paymentId;
            this.paymentStatus = paymentStatus;
            this.refundId = refundId;
            this.refundStatus = refundStatus;
            this.verificationId = verificationId;
            this.verifiedAt = verifiedAt;
            this.verifiedEmployeeName = verifiedEmployeeName;
            this.paidAt = paidAt;
            this.cancelledAt = cancelledAt;
            this.completedAt = completedAt;
            this.createdTime = createdTime;
        }
    }
}
