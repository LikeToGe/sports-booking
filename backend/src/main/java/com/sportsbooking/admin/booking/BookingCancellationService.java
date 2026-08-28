package com.sportsbooking.admin.booking;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.refund.RefundService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BookingCancellationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbcTemplate;
    private final RefundService refundService;

    public BookingCancellationService(JdbcTemplate jdbcTemplate, RefundService refundService) {
        this.jdbcTemplate = jdbcTemplate;
        this.refundService = refundService;
    }

    @Transactional
    public Map<String, Object> cancelByUser(long userId, long orderId) {
        BookingOrder summary = requiredOrderForUser(userId, orderId, false);
        PaymentOrder payment = findPayment(summary.id, true);
        BookingOrder order = requiredOrderById(summary.id, true);
        ensureFuture(order);
        if (hasVerification(order.id)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "订单已核销，用户不能取消");
        }
        if (!("PENDING_PAYMENT".equals(order.orderStatus) || "CONFIRMED".equals(order.orderStatus))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "订单当前不能取消");
        }
        return cancel(order, payment, "USER", "MINI_USER", userId, "用户取消预约", "USER_CANCEL");
    }

    @Transactional
    public Map<String, Object> cancelByVenue(long operatorId, long storeId, long orderId, String reason) {
        if (!StringUtils.hasText(reason) || reason.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "场馆取消原因参数不正确");
        }
        BookingOrder summary = requiredOrderForVenue(storeId, orderId, false);
        PaymentOrder payment = findPayment(summary.id, true);
        BookingOrder order = requiredOrderById(summary.id, true);
        ensureFuture(order);
        if (!("PENDING_PAYMENT".equals(order.orderStatus) || "CONFIRMED".equals(order.orderStatus))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "订单当前不能取消");
        }
        return cancel(order, payment, "VENUE", "VENUE_ADMIN", operatorId, reason.trim(), "VENUE_CANCEL");
    }

    private Map<String, Object> cancel(BookingOrder order, PaymentOrder payment, String cancellationType, String actorType, long actorId,
                                       String reason, String refundSourceType) {
        jdbcTemplate.update("INSERT INTO booking_cancellation (booking_order_id, cancellation_type, reason, cancelled_by_type, cancelled_by_id, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", order.id, cancellationType, reason, actorType, actorId, "VENUE_ADMIN".equals(actorType) ? actorId : 0,
            "VENUE_ADMIN".equals(actorType) ? actorId : 0);
        long cancellationId = lastInsertId();
        if ("PENDING_PAYMENT".equals(order.orderStatus) && payment != null && "CREATED".equals(payment.paymentStatus)) {
            jdbcTemplate.update("UPDATE payment_order SET payment_status = 'CLOSED', provider_status = 'CLOSED', provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 "
                + "WHERE id = ? AND payment_status = 'CREATED'", payment.id);
            jdbcTemplate.update("INSERT IGNORE INTO payment_command_outbox (command_no, payment_order_id, command_type, command_status, payload_json, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, 'CLOSE', 'PENDING', JSON_OBJECT('paymentId', ?), 0, 0)", "PCLOSE" + cancellationId, payment.id, payment.id);
        }
        releaseInventory(order);
        jdbcTemplate.update("UPDATE booking_order SET order_status = 'CANCELLED', cancelled_at = UTC_TIMESTAMP(), modify_operator_id = ? WHERE id = ? "
                + "AND order_status IN ('PENDING_PAYMENT', 'CONFIRMED')", "VENUE_ADMIN".equals(actorType) ? actorId : 0, order.id);
        Long refundId = null;
        if ("PENDING_PAYMENT".equals(order.orderStatus)) {
            decrementActiveHold(order.miniUserId);
        } else if (payment != null && "SUCCEEDED".equals(payment.paymentStatus)) {
            refundId = refundService.createFullRefundIntent(order.id, payment.id, order.priceFen, refundSourceType, cancellationId);
        } else {
            throw new IllegalStateException("已确认订单缺少成功支付单");
        }
        audit(actorType, actorId, "BOOKING_CANCELLED", order, reason);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("orderId", String.valueOf(order.id));
        data.put("orderStatus", "CANCELLED");
        data.put("cancellationId", String.valueOf(cancellationId));
        data.put("refundId", refundId == null ? null : String.valueOf(refundId));
        return data;
    }

    private void releaseInventory(BookingOrder order) {
        List<Long> atomIds = jdbcTemplate.query("SELECT id FROM inventory_atom WHERE occupying_order_id = ? AND is_deleted = 0 "
                + "ORDER BY base_resource_id, start_time FOR UPDATE", (resultSet, rowNum) -> resultSet.getLong("id"), order.id);
        String status = isScheduleVersionEffective(order.storeId, order.scheduleVersionId, order.inventoryDate) ? "AVAILABLE" : "RECONCILE_PENDING";
        for (Long atomId : atomIds) {
            jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = ?, occupying_order_id = NULL, hold_expires_at = NULL, modify_operator_id = 0 "
                    + "WHERE id = ? AND occupying_order_id = ? AND inventory_status IN ('PAYMENT_LOCKED', 'BOOKED') AND is_deleted = 0", status, atomId, order.id);
        }
    }

    private void ensureFuture(BookingOrder order) {
        if (!LocalDateTime.of(order.inventoryDate, order.startTime).isAfter(LocalDateTime.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "预约开始后不能取消");
        }
    }

    private boolean isScheduleVersionEffective(long storeId, long scheduleVersionId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM schedule_version WHERE id = ? AND store_id = ? AND version_status = 'PUBLISHED' "
                + "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) AND is_deleted = 0", Integer.class, scheduleVersionId, storeId, date, date);
        return count != null && count == 1;
    }

    private void decrementActiveHold(long userId) {
        jdbcTemplate.update("UPDATE user_booking_quota SET active_hold_count = active_hold_count - 1, modify_operator_id = 0 "
            + "WHERE mini_user_id = ? AND active_hold_count > 0 AND is_deleted = 0", userId);
    }

    private boolean hasVerification(long orderId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM booking_verification WHERE booking_order_id = ? AND is_deleted = 0", Integer.class, orderId);
        return count != null && count > 0;
    }

    private PaymentOrder findPayment(long orderId, boolean lock) {
        List<PaymentOrder> payments = jdbcTemplate.query("SELECT id, payment_status FROM payment_order WHERE booking_order_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new PaymentOrder(resultSet.getLong("id"), resultSet.getString("payment_status")), orderId);
        return payments.isEmpty() ? null : payments.get(0);
    }

    private BookingOrder requiredOrderForUser(long userId, long orderId, boolean lock) {
        return requiredOrder("id = ? AND mini_user_id = ?", new Object[] { orderId, userId }, lock, "订单不存在或不属于当前用户");
    }

    private BookingOrder requiredOrderForVenue(long storeId, long orderId, boolean lock) {
        return requiredOrder("id = ? AND store_id = ?", new Object[] { orderId, storeId }, lock, "订单不存在或不属于当前场馆");
    }

    private BookingOrder requiredOrderById(long orderId, boolean lock) {
        return requiredOrder("id = ?", new Object[] { orderId }, lock, "订单不存在");
    }

    private BookingOrder requiredOrder(String condition, Object[] parameters, boolean lock, String message) {
        List<BookingOrder> orders = jdbcTemplate.query("SELECT id, mini_user_id, organization_id, store_id, schedule_version_id, inventory_date, start_time, "
                + "price_fen, order_status FROM booking_order WHERE " + condition + " AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new BookingOrder(resultSet.getLong("id"), resultSet.getLong("mini_user_id"), resultSet.getLong("organization_id"),
                resultSet.getLong("store_id"), resultSet.getLong("schedule_version_id"), resultSet.getObject("inventory_date", LocalDate.class),
                resultSet.getObject("start_time", LocalTime.class), resultSet.getInt("price_fen"), resultSet.getString("order_status")), parameters);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, message);
        }
        return orders.get(0);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取取消记录ID");
        }
        return id;
    }

    private void audit(String actorType, long actorId, String action, BookingOrder order, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'BOOKING_ORDER', ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", "VENUE_ADMIN".equals(actorType) ? actorId : null,
            action, order.id, order.organizationId, order.storeId, reason, "VENUE_ADMIN".equals(actorType) ? actorId : 0, "VENUE_ADMIN".equals(actorType) ? actorId : 0);
    }

    private static final class PaymentOrder {
        private final long id;
        private final String paymentStatus;
        private PaymentOrder(long id, String paymentStatus) {
            this.id = id;
            this.paymentStatus = paymentStatus;
        }
    }

    private static final class BookingOrder {
        private final long id;
        private final long miniUserId;
        private final long organizationId;
        private final long storeId;
        private final long scheduleVersionId;
        private final LocalDate inventoryDate;
        private final LocalTime startTime;
        private final int priceFen;
        private final String orderStatus;
        private BookingOrder(long id, long miniUserId, long organizationId, long storeId, long scheduleVersionId, LocalDate inventoryDate,
                             LocalTime startTime, int priceFen, String orderStatus) {
            this.id = id;
            this.miniUserId = miniUserId;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.scheduleVersionId = scheduleVersionId;
            this.inventoryDate = inventoryDate;
            this.startTime = startTime;
            this.priceFen = priceFen;
            this.orderStatus = orderStatus;
        }
    }
}
