package com.sportsbooking.admin.refund;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import com.sportsbooking.admin.billing.BillingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final BillingService billingService;

    public RefundService(JdbcTemplate jdbcTemplate, TokenService tokenService, BillingService billingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.billingService = billingService;
    }

    @Transactional
    public long createFullRefundIntent(long bookingOrderId, long paymentOrderId, int amountFen, String sourceType, long sourceId) {
        try {
            String refundNo = businessNo("RFD");
            jdbcTemplate.update("INSERT INTO refund_order (refund_no, booking_order_id, payment_order_id, refund_source_type, refund_source_id, refund_scope, "
                    + "amount_fen, currency, refund_status, create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, 'FULL', ?, 'CNY', 'REFUND_PENDING', 0, 0)",
                refundNo, bookingOrderId, paymentOrderId, sourceType, sourceId, amountFen);
            long refundId = lastInsertId();
            jdbcTemplate.update("INSERT INTO refund_command_outbox (command_no, refund_order_id, command_status, payload_json, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, 'PENDING', JSON_OBJECT('refundNo', ?, 'amountFen', ?), 0, 0)", businessNo("RCMD"), refundId, refundNo, amountFen);
            return refundId;
        } catch (DuplicateKeyException exception) {
            Long existing = jdbcTemplate.queryForObject("SELECT id FROM refund_order WHERE refund_source_type = ? AND refund_source_id = ? AND refund_scope = 'FULL' "
                + "AND is_deleted = 0", Long.class, sourceType, sourceId);
            if (existing == null) {
                throw exception;
            }
            return existing;
        }
    }

    public Map<String, Object> getForUser(long userId, long refundId) {
        return refundData(requiredRefund(userId, refundId, false));
    }

    @Transactional
    public Map<String, Object> applyMockResult(long userId, long refundId, String outcome) {
        if (!"SUCCESS".equals(outcome) && !"UNKNOWN".equals(outcome) && !"PROCESSING".equals(outcome)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "Mock退款结果参数不正确");
        }
        RefundOrder refund = requiredRefund(userId, refundId, true);
        if ("REFUND_SUCCEEDED".equals(refund.refundStatus)) {
            return refundData(refund);
        }
        String status = "SUCCESS".equals(outcome) ? "REFUND_SUCCEEDED" : "UNKNOWN".equals(outcome) ? "REFUND_UNKNOWN" : "REFUND_PROCESSING";
        jdbcTemplate.update("UPDATE refund_order SET refund_status = ?, provider_status = ?, provider_refund_no = COALESCE(provider_refund_no, refund_no), "
                + "provider_result_at = UTC_TIMESTAMP(), modify_operator_id = 0 WHERE id = ?", status, outcome, refund.id);
        if ("REFUND_SUCCEEDED".equals(status)) {
            billingService.createRefundAdjustment(refund.id);
        }
        return refundData(requiredRefund(userId, refundId, false));
    }

    private RefundOrder requiredRefund(long userId, long refundId, boolean lock) {
        List<RefundOrder> refunds = jdbcTemplate.query("SELECT r.id, r.refund_no, r.booking_order_id, r.payment_order_id, r.refund_source_type, r.refund_source_id, "
                + "r.amount_fen, r.currency, r.refund_status, r.provider_status, r.provider_result_at, r.gmt_created_time FROM refund_order r "
                + "JOIN booking_order o ON o.id = r.booking_order_id WHERE r.id = ? AND o.mini_user_id = ? AND r.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> refund(resultSet), refundId, userId);
        if (refunds.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "退款单不存在或不属于当前用户");
        }
        return refunds.get(0);
    }

    private RefundOrder refund(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new RefundOrder(resultSet.getLong("id"), resultSet.getString("refund_no"), resultSet.getLong("booking_order_id"),
            resultSet.getLong("payment_order_id"), resultSet.getString("refund_source_type"), resultSet.getLong("refund_source_id"),
            resultSet.getInt("amount_fen"), resultSet.getString("currency"), resultSet.getString("refund_status"), resultSet.getString("provider_status"),
            resultSet.getObject("provider_result_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private Map<String, Object> refundData(RefundOrder refund) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(refund.id));
        data.put("refundNo", refund.refundNo);
        data.put("orderId", String.valueOf(refund.bookingOrderId));
        data.put("paymentId", String.valueOf(refund.paymentOrderId));
        data.put("sourceType", refund.sourceType);
        data.put("amountFen", refund.amountFen);
        data.put("currency", refund.currency);
        data.put("refundStatus", refund.refundStatus);
        data.put("providerStatus", refund.providerStatus);
        data.put("providerResultAt", refund.providerResultAt == null ? null : DATETIME_FORMATTER.format(refund.providerResultAt));
        data.put("createTime", DATETIME_FORMATTER.format(refund.createdTime));
        return data;
    }

    private String businessNo(String prefix) {
        return prefix + tokenService.generate().replace("_", "").replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增退款单ID");
        }
        return id;
    }

    private static final class RefundOrder {
        private final long id;
        private final String refundNo;
        private final long bookingOrderId;
        private final long paymentOrderId;
        private final String sourceType;
        private final long sourceId;
        private final int amountFen;
        private final String currency;
        private final String refundStatus;
        private final String providerStatus;
        private final LocalDateTime providerResultAt;
        private final LocalDateTime createdTime;
        private RefundOrder(long id, String refundNo, long bookingOrderId, long paymentOrderId, String sourceType, long sourceId, int amountFen,
                            String currency, String refundStatus, String providerStatus, LocalDateTime providerResultAt, LocalDateTime createdTime) {
            this.id = id;
            this.refundNo = refundNo;
            this.bookingOrderId = bookingOrderId;
            this.paymentOrderId = paymentOrderId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.amountFen = amountFen;
            this.currency = currency;
            this.refundStatus = refundStatus;
            this.providerStatus = providerStatus;
            this.providerResultAt = providerResultAt;
            this.createdTime = createdTime;
        }
    }
}
