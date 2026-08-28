package com.sportsbooking.admin.payment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import com.sportsbooking.admin.payment.MockPaymentController.MockResultRequest;
import com.sportsbooking.admin.refund.RefundService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MockPaymentService {
    private static final Set<String> OUTCOMES = new HashSet<String>(Arrays.asList("SUCCESS", "FAILED", "PROCESSING", "UNKNOWN"));
    private static final Set<String> HOLD_ORDER_STATUSES = new HashSet<String>(Arrays.asList("PENDING_PAYMENT", "PAYMENT_PROCESSING", "PAYMENT_UNKNOWN"));
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final RefundService refundService;

    public MockPaymentService(JdbcTemplate jdbcTemplate, TokenService tokenService, RefundService refundService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.refundService = refundService;
    }

    @Transactional
    public Map<String, Object> create(long userId, long orderId) {
        BookingOrder order = requiredOrder(userId, orderId, true);
        if (!"PENDING_PAYMENT".equals(order.orderStatus) || !order.holdExpiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "订单当前不能发起支付");
        }
        PaymentOrder existing = findPaymentByBooking(order.id, false);
        if (existing != null) {
            return paymentData(existing);
        }
        String paymentNo = businessNo("PAY");
        jdbcTemplate.update("INSERT INTO payment_order (payment_no, booking_order_id, provider, amount_fen, currency, payment_status, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'MOCK', ?, 'CNY', 'CREATED', 0, 0)", paymentNo, order.id, order.priceFen);
        long paymentId = lastInsertId();
        jdbcTemplate.update("INSERT INTO payment_command_outbox (command_no, payment_order_id, command_type, command_status, payload_json, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, 'CREATE', 'PENDING', JSON_OBJECT('paymentNo', ?, 'amountFen', ?), 0, 0)", businessNo("PCMD"), paymentId, paymentNo, order.priceFen);
        audit("PAYMENT_CREATED", paymentId, order, paymentNo);
        return paymentData(requiredPayment(userId, paymentId, false));
    }

    public Map<String, Object> get(long userId, long paymentId) {
        return paymentData(requiredPayment(userId, paymentId, false));
    }

    @Transactional
    public Map<String, Object> applyMockResult(long userId, long paymentId, MockResultRequest request) {
        if (request == null || !OUTCOMES.contains(request.getOutcome()) || !StringUtils.hasText(request.getProviderEventNo())
            || request.getProviderEventNo().length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "Mock支付结果参数不正确");
        }
        PaymentOrder payment = requiredPayment(userId, paymentId, true);
        try {
            jdbcTemplate.update("INSERT INTO payment_event_inbox (provider, provider_event_no, payment_order_id, event_type, payload_json, process_result, create_operator_id, modify_operator_id) "
                    + "VALUES ('MOCK', ?, ?, ?, JSON_OBJECT('paymentNo', ?, 'amountFen', ?), 'PENDING', 0, 0)", request.getProviderEventNo(), payment.id,
                request.getOutcome(), payment.paymentNo, payment.amountFen);
        } catch (DuplicateKeyException exception) {
            return paymentData(requiredPayment(userId, paymentId, false));
        }
        long eventId = lastInsertId();
        String result = applyResult(payment, request.getOutcome());
        jdbcTemplate.update("UPDATE payment_event_inbox SET process_result = ?, processed_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ?", result, eventId);
        return paymentData(requiredPayment(userId, paymentId, false));
    }

    @Scheduled(fixedDelay = 60000)
    public void closeExpiredPaymentHolds() {
        List<Long> orderIds = jdbcTemplate.query("SELECT id FROM booking_order WHERE order_status = 'PENDING_PAYMENT' AND hold_expires_at <= UTC_TIMESTAMP() "
                + "AND is_deleted = 0 ORDER BY id LIMIT 100", (resultSet, rowNum) -> resultSet.getLong("id"));
        for (Long orderId : orderIds) {
            closeExpiredHold(orderId);
        }
    }

    @Transactional
    public void closeExpiredHold(long orderId) {
        BookingOrder order = requiredOrderById(orderId, true);
        if (!"PENDING_PAYMENT".equals(order.orderStatus) || order.holdExpiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            return;
        }
        PaymentOrder payment = findPaymentByBooking(order.id, true);
        if (payment != null && !("CREATED".equals(payment.paymentStatus) || "CLOSED".equals(payment.paymentStatus))) {
            return;
        }
        if (payment != null && "CREATED".equals(payment.paymentStatus)) {
            jdbcTemplate.update("UPDATE payment_order SET payment_status = 'CLOSED', provider_status = 'CLOSED', provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 "
                    + "WHERE id = ? AND payment_status = 'CREATED'", payment.id);
            jdbcTemplate.update("INSERT IGNORE INTO payment_command_outbox (command_no, payment_order_id, command_type, command_status, payload_json, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, 'CLOSE', 'PENDING', JSON_OBJECT('paymentNo', ?), 0, 0)", businessNo("PCMD"), payment.id, payment.paymentNo);
        }
        cancelUnpaidOrder(order, "PAYMENT_HOLD_EXPIRED", true);
    }

    private String applyResult(PaymentOrder payment, String outcome) {
        BookingOrder order = requiredOrderById(payment.bookingOrderId, true);
        if ("CLOSED".equals(payment.paymentStatus) && "SUCCESS".equals(outcome) && "CANCELLED".equals(order.orderStatus)) {
            jdbcTemplate.update("UPDATE payment_order SET payment_status = 'SUCCEEDED', provider_status = 'SUCCESS', provider_payment_no = COALESCE(provider_payment_no, payment_no), "
                    + "provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ? AND payment_status = 'CLOSED'", payment.id);
            refundService.createFullRefundIntent(order.id, payment.id, order.priceFen, "LATE_PAYMENT", payment.id);
            audit("PAYMENT_LATE_SUCCESS_REFUND_PENDING", payment.id, order, payment.paymentNo);
            return "APPLIED";
        }
        if ("SUCCEEDED".equals(payment.paymentStatus) || "FAILED".equals(payment.paymentStatus) || "CLOSED".equals(payment.paymentStatus)) {
            return "IGNORED";
        }
        if ("SUCCESS".equals(outcome)) {
            if (!HOLD_ORDER_STATUSES.contains(order.orderStatus)) {
                return "REJECTED";
            }
            List<Long> atomIds = lockOrderAtoms(order.id);
            if (atomIds.isEmpty()) {
                throw new IllegalStateException("订单缺少库存原子");
            }
            for (Long atomId : atomIds) {
                int updated = jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = 'BOOKED', hold_expires_at = NULL, modify_operator_id = 0 "
                        + "WHERE id = ? AND occupying_order_id = ? AND inventory_status IN ('PAYMENT_LOCKED', 'PROCESSING', 'UNKNOWN') AND is_deleted = 0", atomId, order.id);
                if (updated != 1) {
                    throw new ApiException(HttpStatus.CONFLICT, 0, "订单库存状态已变化");
                }
            }
            jdbcTemplate.update("UPDATE payment_order SET payment_status = 'SUCCEEDED', provider_status = 'SUCCESS', provider_payment_no = COALESCE(provider_payment_no, payment_no), "
                    + "provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ?", payment.id);
            jdbcTemplate.update("UPDATE booking_order SET order_status = 'CONFIRMED', paid_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ? "
                    + "AND order_status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN')", order.id);
            decrementActiveHold(order.miniUserId);
            audit("PAYMENT_SUCCEEDED", payment.id, order, payment.paymentNo);
            return "APPLIED";
        }
        if ("FAILED".equals(outcome)) {
            if (!HOLD_ORDER_STATUSES.contains(order.orderStatus)) {
                return "REJECTED";
            }
            jdbcTemplate.update("UPDATE payment_order SET payment_status = 'FAILED', provider_status = 'FAILED', provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ?", payment.id);
            cancelUnpaidOrder(order, "PAYMENT_FAILED", false);
            audit("PAYMENT_FAILED", payment.id, order, payment.paymentNo);
            return "APPLIED";
        }
        if ("PROCESSING".equals(outcome) || "UNKNOWN".equals(outcome)) {
            if (!HOLD_ORDER_STATUSES.contains(order.orderStatus)) {
                return "REJECTED";
            }
            String paymentStatus = "PROCESSING".equals(outcome) ? "PROCESSING" : "UNKNOWN";
            String orderStatus = "PROCESSING".equals(outcome) ? "PAYMENT_PROCESSING" : "PAYMENT_UNKNOWN";
            String inventoryStatus = "PROCESSING".equals(outcome) ? "PROCESSING" : "UNKNOWN";
            jdbcTemplate.update("UPDATE payment_order SET payment_status = ?, provider_status = ?, provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ?",
                paymentStatus, outcome, payment.id);
            jdbcTemplate.update("UPDATE booking_order SET order_status = ?, modify_operator_id = 0 WHERE id = ? AND order_status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN')",
                orderStatus, order.id);
            jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = ?, modify_operator_id = 0 WHERE occupying_order_id = ? "
                + "AND inventory_status IN ('PAYMENT_LOCKED', 'PROCESSING', 'UNKNOWN') AND is_deleted = 0", inventoryStatus, order.id);
            audit("PAYMENT_" + outcome, payment.id, order, payment.paymentNo);
            return "APPLIED";
        }
        throw new IllegalStateException("未知Mock支付结果");
    }

    private void cancelUnpaidOrder(BookingOrder order, String reason, boolean naturalExpiry) {
        if (!HOLD_ORDER_STATUSES.contains(order.orderStatus)) {
            return;
        }
        List<Long> atomIds = lockOrderAtoms(order.id);
        String releasedStatus = isScheduleVersionEffective(order.storeId, order.scheduleVersionId, order.inventoryDate) ? "AVAILABLE" : "RECONCILE_PENDING";
        for (Long atomId : atomIds) {
            jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = ?, occupying_order_id = NULL, hold_expires_at = NULL, modify_operator_id = 0 "
                    + "WHERE id = ? AND occupying_order_id = ? AND inventory_status IN ('PAYMENT_LOCKED', 'PROCESSING', 'UNKNOWN') AND is_deleted = 0",
                releasedStatus, atomId, order.id);
        }
        jdbcTemplate.update("UPDATE booking_order SET order_status = 'CANCELLED', cancelled_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ? "
            + "AND order_status IN ('PENDING_PAYMENT', 'PAYMENT_PROCESSING', 'PAYMENT_UNKNOWN')", order.id);
        decrementActiveHold(order.miniUserId);
        if (naturalExpiry) {
            recordNaturalExpiry(order.miniUserId);
        }
        audit("BOOKING_CANCELLED", order.id, order, reason);
    }

    private List<Long> lockOrderAtoms(long orderId) {
        return jdbcTemplate.query("SELECT id FROM inventory_atom WHERE occupying_order_id = ? AND is_deleted = 0 ORDER BY base_resource_id, start_time FOR UPDATE",
            (resultSet, rowNum) -> resultSet.getLong("id"), orderId);
    }

    private void decrementActiveHold(long userId) {
        jdbcTemplate.update("UPDATE user_booking_quota SET active_hold_count = active_hold_count - 1, modify_operator_id = 0 "
            + "WHERE mini_user_id = ? AND active_hold_count > 0 AND is_deleted = 0", userId);
    }

    private void recordNaturalExpiry(long userId) {
        List<NaturalExpiryQuota> quotas = jdbcTemplate.query("SELECT mini_user_id, natural_expiry_count, natural_expiry_window_started_at FROM user_booking_quota "
                + "WHERE mini_user_id = ? AND is_deleted = 0 FOR UPDATE", (resultSet, rowNum) -> new NaturalExpiryQuota(resultSet.getLong("mini_user_id"),
                resultSet.getInt("natural_expiry_count"), resultSet.getObject("natural_expiry_window_started_at", LocalDateTime.class)), userId);
        if (quotas.isEmpty()) {
            return;
        }
        NaturalExpiryQuota quota = quotas.get(0);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int count = quota.windowStartedAt == null || quota.windowStartedAt.plusMinutes(30).isBefore(now) ? 1 : quota.count + 1;
        LocalDateTime windowStart = quota.windowStartedAt == null || quota.windowStartedAt.plusMinutes(30).isBefore(now) ? now : quota.windowStartedAt;
        jdbcTemplate.update("UPDATE user_booking_quota SET natural_expiry_count = ?, natural_expiry_window_started_at = ?, cooldown_until = ?, modify_operator_id = 0 "
                + "WHERE mini_user_id = ?", count, windowStart, count >= 3 ? now.plusMinutes(15) : null, userId);
    }

    private boolean isScheduleVersionEffective(long storeId, long scheduleVersionId, LocalDate date) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM schedule_version WHERE id = ? AND store_id = ? AND version_status = 'PUBLISHED' "
                + "AND effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?) AND is_deleted = 0", Integer.class, scheduleVersionId, storeId, date, date);
        return count != null && count == 1;
    }

    private PaymentOrder requiredPayment(long userId, long paymentId, boolean lock) {
        List<PaymentOrder> payments = jdbcTemplate.query(paymentSelect() + " WHERE p.id = ? AND o.mini_user_id = ? AND p.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> payment(resultSet), paymentId, userId);
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "支付单不存在或不属于当前用户");
        }
        return payments.get(0);
    }

    private PaymentOrder findPaymentByBooking(long bookingOrderId, boolean lock) {
        List<PaymentOrder> payments = jdbcTemplate.query(paymentSelect() + " WHERE p.booking_order_id = ? AND p.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> payment(resultSet), bookingOrderId);
        return payments.isEmpty() ? null : payments.get(0);
    }

    private BookingOrder requiredOrder(long userId, long orderId, boolean lock) {
        List<BookingOrder> orders = jdbcTemplate.query("SELECT id, mini_user_id, organization_id, store_id, schedule_version_id, inventory_date, price_fen, order_status, hold_expires_at "
                + "FROM booking_order WHERE id = ? AND mini_user_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bookingOrder(resultSet), orderId, userId);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在或不属于当前用户");
        }
        return orders.get(0);
    }

    private BookingOrder requiredOrderById(long orderId, boolean lock) {
        List<BookingOrder> orders = jdbcTemplate.query("SELECT id, mini_user_id, organization_id, store_id, schedule_version_id, inventory_date, price_fen, order_status, hold_expires_at "
                + "FROM booking_order WHERE id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bookingOrder(resultSet), orderId);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在");
        }
        return orders.get(0);
    }

    private String paymentSelect() {
        return "SELECT p.id, p.payment_no, p.booking_order_id, p.provider, p.provider_payment_no, p.amount_fen, p.currency, p.payment_status, "
            + "p.provider_status, p.provider_result_at, p.gmt_created_time FROM payment_order p JOIN booking_order o ON o.id = p.booking_order_id";
    }

    private PaymentOrder payment(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new PaymentOrder(resultSet.getLong("id"), resultSet.getString("payment_no"), resultSet.getLong("booking_order_id"), resultSet.getString("provider"),
            resultSet.getString("provider_payment_no"), resultSet.getInt("amount_fen"), resultSet.getString("currency"), resultSet.getString("payment_status"),
            resultSet.getString("provider_status"), resultSet.getObject("provider_result_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private BookingOrder bookingOrder(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new BookingOrder(resultSet.getLong("id"), resultSet.getLong("mini_user_id"), resultSet.getLong("organization_id"), resultSet.getLong("store_id"),
            resultSet.getLong("schedule_version_id"), resultSet.getObject("inventory_date", LocalDate.class), resultSet.getInt("price_fen"),
            resultSet.getString("order_status"), resultSet.getObject("hold_expires_at", LocalDateTime.class));
    }

    private Map<String, Object> paymentData(PaymentOrder payment) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(payment.id));
        data.put("paymentNo", payment.paymentNo);
        data.put("orderId", String.valueOf(payment.bookingOrderId));
        data.put("provider", payment.provider);
        data.put("providerPaymentNo", payment.providerPaymentNo);
        data.put("amountFen", payment.amountFen);
        data.put("currency", payment.currency);
        data.put("paymentStatus", payment.paymentStatus);
        data.put("providerStatus", payment.providerStatus);
        data.put("providerResultAt", payment.providerResultAt == null ? null : DATETIME_FORMATTER.format(payment.providerResultAt));
        List<Long> refundIds = jdbcTemplate.query("SELECT id FROM refund_order WHERE payment_order_id = ? AND is_deleted = 0 ORDER BY id DESC LIMIT 1",
            (resultSet, rowNum) -> resultSet.getLong("id"), payment.id);
        data.put("refundId", refundIds.isEmpty() ? null : String.valueOf(refundIds.get(0)));
        data.put("createTime", DATETIME_FORMATTER.format(payment.createdTime));
        return data;
    }

    private String businessNo(String prefix) {
        return prefix + tokenService.generate().replace("_", "").replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增支付记录ID");
        }
        return id;
    }

    private void audit(String action, long targetId, BookingOrder order, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (NULL, ?, 'PAYMENT_ORDER', ?, ?, ?, 'SUCCEEDED', ?, 0, 0)", action, targetId, order.organizationId, order.storeId, reason);
    }

    private static final class PaymentOrder {
        private final long id;
        private final String paymentNo;
        private final long bookingOrderId;
        private final String provider;
        private final String providerPaymentNo;
        private final int amountFen;
        private final String currency;
        private final String paymentStatus;
        private final String providerStatus;
        private final LocalDateTime providerResultAt;
        private final LocalDateTime createdTime;
        private PaymentOrder(long id, String paymentNo, long bookingOrderId, String provider, String providerPaymentNo, int amountFen, String currency,
                             String paymentStatus, String providerStatus, LocalDateTime providerResultAt, LocalDateTime createdTime) {
            this.id = id;
            this.paymentNo = paymentNo;
            this.bookingOrderId = bookingOrderId;
            this.provider = provider;
            this.providerPaymentNo = providerPaymentNo;
            this.amountFen = amountFen;
            this.currency = currency;
            this.paymentStatus = paymentStatus;
            this.providerStatus = providerStatus;
            this.providerResultAt = providerResultAt;
            this.createdTime = createdTime;
        }
    }

    private static final class BookingOrder {
        private final long id;
        private final long miniUserId;
        private final long organizationId;
        private final long storeId;
        private final long scheduleVersionId;
        private final LocalDate inventoryDate;
        private final int priceFen;
        private final String orderStatus;
        private final LocalDateTime holdExpiresAt;
        private BookingOrder(long id, long miniUserId, long organizationId, long storeId, long scheduleVersionId, LocalDate inventoryDate,
                             int priceFen, String orderStatus, LocalDateTime holdExpiresAt) {
            this.id = id;
            this.miniUserId = miniUserId;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.scheduleVersionId = scheduleVersionId;
            this.inventoryDate = inventoryDate;
            this.priceFen = priceFen;
            this.orderStatus = orderStatus;
            this.holdExpiresAt = holdExpiresAt;
        }
    }

    private static final class NaturalExpiryQuota {
        private final long userId;
        private final int count;
        private final LocalDateTime windowStartedAt;
        private NaturalExpiryQuota(long userId, int count, LocalDateTime windowStartedAt) {
            this.userId = userId;
            this.count = count;
            this.windowStartedAt = windowStartedAt;
        }
    }
}
