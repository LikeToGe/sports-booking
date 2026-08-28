package com.sportsbooking.admin.staff;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.RedisAvailabilityService;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import java.time.Duration;

@Service
public class VerificationService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;
    private final RedisAvailabilityService redisAvailabilityService;
    private final VerificationAttemptRecorder attemptRecorder;
    private final TransactionTemplate transactionTemplate;
    private final StringRedisTemplate redisTemplate;

    public VerificationService(JdbcTemplate jdbcTemplate, TokenService tokenService, RedisAvailabilityService redisAvailabilityService,
                               VerificationAttemptRecorder attemptRecorder,
                               PlatformTransactionManager transactionManager, StringRedisTemplate redisTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
        this.redisAvailabilityService = redisAvailabilityService;
        this.attemptRecorder = attemptRecorder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Map<String, Object> issueCredentials(long userId, long orderId) {
        BookingOrder order = requiredOrderForUser(userId, orderId, true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!"CONFIRMED".equals(order.orderStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "仅已确认订单可生成核销凭证");
        }
        if (hasVerification(order.id)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "订单已核销，凭证不可再生成");
        }
        LocalDateTime validFrom = businessTime(order.date, order.startTime).minusMinutes(30);
        LocalDateTime validTo = businessTime(order.date, order.endTime);
        if (!now.isBefore(validTo)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "核销窗口已结束");
        }
        jdbcTemplate.update("UPDATE booking_verification_credential SET credential_status = 'REVOKED', active_short_code_hash = NULL, modify_operator_id = 0 "
            + "WHERE booking_order_id = ? AND credential_status = 'ACTIVE' AND is_deleted = 0", order.id);
        String qrToken = tokenService.generate();
        jdbcTemplate.update("INSERT INTO booking_verification_credential (booking_order_id, store_id, credential_type, token_hash, credential_status, valid_from, valid_to, "
                + "create_operator_id, modify_operator_id) VALUES (?, ?, 'QR', ?, 'ACTIVE', ?, ?, 0, 0)", order.id, order.storeId,
            tokenService.hash(qrToken), validFrom, validTo);
        String shortCode = createShortCode(order, validFrom, validTo);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("orderId", String.valueOf(order.id));
        data.put("orderNo", order.orderNo);
        data.put("qrToken", qrToken);
        data.put("shortCode", shortCode);
        data.put("validFrom", DATETIME_FORMATTER.format(validFrom));
        data.put("validTo", DATETIME_FORMATTER.format(validTo));
        data.put("storeName", order.storeName);
        data.put("sellableUnitName", order.sellableUnitName);
        data.put("date", order.date.toString());
        data.put("startTime", order.startTime.toString());
        data.put("endTime", order.endTime.toString());
        return data;
    }

    public Map<String, Object> precheckQr(StaffSessionPrincipal principal, String qrToken) {
        if (!StringUtils.hasText(qrToken) || qrToken.length() > 128) {
            rejected(principal, null, null, "QR", "INVALID_QR_TOKEN", "二维码凭证不可用");
        }
        Credential credential = findQrCredential(tokenService.hash(qrToken), false);
        if (credential == null) {
            rejected(principal, null, null, "QR", "QR_NOT_FOUND", "二维码凭证不可用");
        }
        validatePreview(principal, credential, "QR");
        return previewData(credential);
    }

    public Map<String, Object> precheckShortCode(StaffSessionPrincipal principal, String shortCode) {
        redisAvailabilityService.requireAvailable();
        String key = principal.getStoreId() + ":" + principal.getEmployeeId();
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey("verification:short-code:lock:" + key))) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 0, "短码错误次数过多，请 15 分钟后重试");
            }
            try {
                if (!StringUtils.hasText(shortCode) || !shortCode.matches("\\d{6}")) {
                    rejected(principal, null, null, "SHORT_CODE", "INVALID_SHORT_CODE", "短码不可用");
                }
                Credential credential = findShortCodeCredential(principal.getStoreId(), tokenService.keyedHash("verification-short-code", shortCode), false);
                if (credential == null) {
                    rejected(principal, null, null, "SHORT_CODE", "SHORT_CODE_NOT_FOUND", "短码不可用");
                }
                validatePreview(principal, credential, "SHORT_CODE");
                redisTemplate.delete("verification:short-code:fail:" + key);
                return previewData(credential);
            } catch (ApiException exception) {
                recordShortCodeFailure(key);
                throw exception;
            }
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, 0, "Redis当前不可用，请稍后重试");
        }
    }

    private void recordShortCodeFailure(String key) {
        String failureKey = "verification:short-code:fail:" + key;
        Long failures = redisTemplate.opsForValue().increment(failureKey);
        if (failures != null && failures == 1) redisTemplate.expire(failureKey, Duration.ofMinutes(10));
        if (failures != null && failures >= 5) {
            redisTemplate.opsForValue().set("verification:short-code:lock:" + key, "1", Duration.ofMinutes(15));
            redisTemplate.delete(failureKey);
        }
    }

    public Map<String, Object> confirm(StaffSessionPrincipal principal, long credentialId, String method) {
        try {
            return transactionTemplate.execute(status -> confirmInTransaction(principal, credentialId, method));
        } catch (VerificationRejected exception) {
            attemptRecorder.record(principal.getEmployeeId(), exception.orderId, exception.credentialId, exception.method, "REJECTED", exception.reason);
            throw new ApiException(HttpStatus.CONFLICT, 0, exception.message);
        }
    }

    private Map<String, Object> confirmInTransaction(StaffSessionPrincipal principal, long credentialId, String method) {
        if (!"QR".equals(method) && !"SHORT_CODE".equals(method)) {
            rejectedForConfirmation(null, credentialId, method, "INVALID_METHOD", "核销方式参数不正确");
        }
        Credential credential = findCredentialById(credentialId, true);
        if (credential == null) {
            rejectedForConfirmation(null, null, method, "CREDENTIAL_NOT_FOUND", "核销凭证不可用");
        }
        if (!method.equals(credential.type)) {
            rejectedForConfirmation(credential.orderId, credential.id, method, "METHOD_MISMATCH", "核销方式与凭证不匹配");
        }
        BookingOrder order = requiredOrderById(credential.orderId, true);
        Verification existing = findVerification(order.id, true);
        if (existing != null) {
            recordAttemptWithinTransaction(principal.getEmployeeId(), order.id, credential.id, method, "DUPLICATE", "ALREADY_VERIFIED");
            return duplicateData(existing, order);
        }
        validateForConfirmation(principal, credential, order, method);
        try {
            jdbcTemplate.update("INSERT INTO booking_verification (booking_order_id, employee_id, credential_id, verification_method, verified_at, "
                    + "create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, UTC_TIMESTAMP(), ?, ?)", order.id, principal.getEmployeeId(), credential.id,
                method, principal.getEmployeeId(), principal.getEmployeeId());
        } catch (DuplicateKeyException exception) {
            Verification concurrent = findVerification(order.id, false);
            if (concurrent == null) {
                throw exception;
            }
            recordAttemptWithinTransaction(principal.getEmployeeId(), order.id, credential.id, method, "DUPLICATE", "ALREADY_VERIFIED");
            return duplicateData(concurrent, order);
        }
        jdbcTemplate.update("UPDATE booking_verification_credential SET credential_status = 'REVOKED', active_short_code_hash = NULL, modify_operator_id = ? "
            + "WHERE booking_order_id = ? AND id <> ? AND credential_status = 'ACTIVE' AND is_deleted = 0", principal.getEmployeeId(), order.id, credential.id);
        jdbcTemplate.update("UPDATE booking_verification_credential SET credential_status = 'USED', active_short_code_hash = NULL, used_by_employee_id = ?, used_at = UTC_TIMESTAMP(), "
                + "modify_operator_id = ? WHERE id = ? AND credential_status = 'ACTIVE'", principal.getEmployeeId(), principal.getEmployeeId(), credential.id);
        recordAttemptWithinTransaction(principal.getEmployeeId(), order.id, credential.id, method, "SUCCEEDED", null);
        audit(principal.getEmployeeId(), "BOOKING_VERIFIED", order, "店员首次核销成功");
        return confirmedData(order, principal, method, LocalDateTime.now(ZoneOffset.UTC));
    }

    public Map<String, Object> listMine(StaffSessionPrincipal principal, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM verification_attempt WHERE employee_id = ? AND is_deleted = 0", Long.class,
            principal.getEmployeeId());
        List<Map<String, Object>> records = jdbcTemplate.query("SELECT a.id, a.verification_method, a.attempt_result, a.failure_reason, a.gmt_created_time, o.order_no, "
                + "o.inventory_date, o.start_time, o.end_time, s.name AS store_name, u.name AS sellable_unit_name FROM verification_attempt a "
                + "LEFT JOIN booking_order o ON o.id = a.booking_order_id LEFT JOIN store s ON s.id = o.store_id "
                + "LEFT JOIN sellable_unit_version v ON v.id = o.sellable_unit_version_id LEFT JOIN sellable_unit u ON u.id = v.sellable_unit_id "
                + "WHERE a.employee_id = ? AND a.is_deleted = 0 ORDER BY a.gmt_created_time DESC, a.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> attemptData(resultSet), principal.getEmployeeId(), pageSize, (page - 1L) * pageSize);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total == null ? 0 : total));
        data.put("records", records);
        return data;
    }

    @Scheduled(fixedDelayString = "${app.booking-completion.fixed-delay-ms:60000}")
    @Transactional
    public void completeDueOrders() {
        List<BookingOrder> orders = jdbcTemplate.query("SELECT o.id, o.order_no, o.mini_user_id, o.organization_id, o.store_id, o.inventory_date, o.start_time, "
                + "o.end_time, o.order_status, s.name AS store_name, u.name AS sellable_unit_name FROM booking_order o JOIN store s ON s.id = o.store_id "
                + "JOIN sellable_unit_version v ON v.id = o.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id "
                + "WHERE o.order_status = 'CONFIRMED' AND TIMESTAMP(o.inventory_date, o.end_time) <= CONVERT_TZ(UTC_TIMESTAMP(), '+00:00', '+08:00') "
                + "AND o.is_deleted = 0 ORDER BY o.id ASC LIMIT 100 FOR UPDATE",
            (resultSet, rowNum) -> new BookingOrder(resultSet.getLong("id"), resultSet.getString("order_no"), resultSet.getLong("mini_user_id"),
                resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getObject("inventory_date", LocalDate.class),
                resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getString("order_status"),
                resultSet.getString("store_name"), resultSet.getString("sellable_unit_name"), null, null));
        for (BookingOrder order : orders) {
            int updated = jdbcTemplate.update("UPDATE booking_order SET order_status = 'COMPLETED', completed_at = UTC_TIMESTAMP(), modify_operator_id = 0 "
                + "WHERE id = ? AND order_status = 'CONFIRMED'", order.id);
            if (updated == 1) {
                audit(0, "BOOKING_AUTO_COMPLETED", order, "预约结束后系统自动完成");
            }
        }
    }

    private String createShortCode(BookingOrder order, LocalDateTime validFrom, LocalDateTime validTo) {
        for (int attempt = 0; attempt < 20; attempt++) {
            String shortCode = tokenService.generateSixDigitCode();
            String codeHash = tokenService.keyedHash("verification-short-code", shortCode);
            try {
                jdbcTemplate.update("INSERT INTO booking_verification_credential (booking_order_id, store_id, credential_type, short_code_hash, active_short_code_hash, "
                        + "credential_status, valid_from, valid_to, create_operator_id, modify_operator_id) VALUES (?, ?, 'SHORT_CODE', ?, ?, 'ACTIVE', ?, ?, 0, 0)",
                    order.id, order.storeId, codeHash, codeHash, validFrom, validTo);
                return shortCode;
            } catch (DuplicateKeyException exception) {
                // The active same-store code is retried; the raw code is never persisted or logged.
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, 0, "核销短码生成繁忙，请稍后重试");
    }

    private void validatePreview(StaffSessionPrincipal principal, Credential credential, String method) {
        if (credential.storeId != principal.getStoreId()) {
            rejected(principal, credential.orderId, credential.id, method, "STORE_MISMATCH", "凭证不可用");
        }
        validateCredentialState(principal, credential, method);
    }

    private void validateForConfirmation(StaffSessionPrincipal principal, Credential credential, BookingOrder order, String method) {
        if (credential.storeId != principal.getStoreId() || order.storeId != principal.getStoreId()) {
            rejectedForConfirmation(order.id, credential.id, method, "STORE_MISMATCH", "凭证不可用");
        }
        if (!"CONFIRMED".equals(order.orderStatus)) {
            rejectedForConfirmation(order.id, credential.id, method, "ORDER_NOT_CONFIRMED", "订单当前不能核销");
        }
        validateCredentialStateForConfirmation(credential, method);
    }

    private void validateCredentialState(StaffSessionPrincipal principal, Credential credential, String method) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!"ACTIVE".equals(credential.status)) {
            rejected(principal, credential.orderId, credential.id, method, "CREDENTIAL_INACTIVE", "凭证已失效");
        }
        if (now.isBefore(credential.validFrom) || !now.isBefore(credential.validTo)) {
            rejected(principal, credential.orderId, credential.id, method, "OUTSIDE_WINDOW", "不在核销时间窗口内");
        }
        if (!"CONFIRMED".equals(credential.orderStatus)) {
            rejected(principal, credential.orderId, credential.id, method, "ORDER_NOT_CONFIRMED", "订单当前不能核销");
        }
        if (credential.hasVerification) {
            rejected(principal, credential.orderId, credential.id, method, "ALREADY_VERIFIED", "订单已完成核销");
        }
    }

    private void validateCredentialStateForConfirmation(Credential credential, String method) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!"ACTIVE".equals(credential.status)) {
            rejectedForConfirmation(credential.orderId, credential.id, method, "CREDENTIAL_INACTIVE", "凭证已失效");
        }
        if (now.isBefore(credential.validFrom) || !now.isBefore(credential.validTo)) {
            rejectedForConfirmation(credential.orderId, credential.id, method, "OUTSIDE_WINDOW", "不在核销时间窗口内");
        }
        if (!"CONFIRMED".equals(credential.orderStatus)) {
            rejectedForConfirmation(credential.orderId, credential.id, method, "ORDER_NOT_CONFIRMED", "订单当前不能核销");
        }
        if (credential.hasVerification) {
            rejectedForConfirmation(credential.orderId, credential.id, method, "ALREADY_VERIFIED", "订单已完成核销");
        }
    }

    private void rejected(StaffSessionPrincipal principal, Long orderId, Long credentialId, String method, String reason, String message) {
        attemptRecorder.record(principal.getEmployeeId(), orderId, credentialId, "SHORT_CODE".equals(method) ? "SHORT_CODE" : "QR", "REJECTED", reason);
        throw new ApiException(HttpStatus.CONFLICT, 0, message);
    }

    private void rejectedForConfirmation(Long orderId, Long credentialId, String method, String reason, String message) {
        throw new VerificationRejected(orderId, credentialId, "SHORT_CODE".equals(method) ? "SHORT_CODE" : "QR", reason, message);
    }

    private void recordAttemptWithinTransaction(long employeeId, Long bookingOrderId, Long credentialId, String method, String result, String failureReason) {
        jdbcTemplate.update("INSERT INTO verification_attempt (employee_id, booking_order_id, credential_id, verification_method, attempt_result, failure_reason, "
                + "create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", employeeId, bookingOrderId, credentialId, method, result,
            failureReason, employeeId, employeeId);
    }

    private Credential findQrCredential(String tokenHash, boolean lock) {
        return findCredential("c.token_hash = ?", new Object[] { tokenHash }, lock);
    }

    private Credential findShortCodeCredential(long storeId, String codeHash, boolean lock) {
        return findCredential("c.store_id = ? AND c.active_short_code_hash = ?", new Object[] { storeId, codeHash }, lock);
    }

    private Credential findCredentialById(long credentialId, boolean lock) {
        return findCredential("c.id = ?", new Object[] { credentialId }, lock);
    }

    private Credential findCredential(String where, Object[] parameters, boolean lock) {
        List<Credential> credentials = jdbcTemplate.query("SELECT c.id, c.booking_order_id, c.store_id, c.credential_type, c.credential_status, c.valid_from, c.valid_to, "
                + "o.order_no, o.order_status, o.inventory_date, o.start_time, o.end_time, o.contact_name, o.contact_phone, s.name AS store_name, "
                + "u.name AS sellable_unit_name, EXISTS(SELECT 1 FROM booking_verification bv WHERE bv.booking_order_id = o.id AND bv.is_deleted = 0) AS has_verification "
                + "FROM booking_verification_credential c JOIN booking_order o ON o.id = c.booking_order_id JOIN store s ON s.id = o.store_id "
                + "JOIN sellable_unit_version v ON v.id = o.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id WHERE " + where
                + " AND c.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new Credential(resultSet.getLong("id"), resultSet.getLong("booking_order_id"), resultSet.getLong("store_id"),
                resultSet.getString("credential_type"), resultSet.getString("credential_status"), resultSet.getObject("valid_from", LocalDateTime.class),
                resultSet.getObject("valid_to", LocalDateTime.class), resultSet.getString("order_no"), resultSet.getString("order_status"),
                resultSet.getObject("inventory_date", LocalDate.class), resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class),
                resultSet.getString("contact_name"), resultSet.getString("contact_phone"), resultSet.getString("store_name"), resultSet.getString("sellable_unit_name"),
                resultSet.getBoolean("has_verification")), parameters);
        return credentials.isEmpty() ? null : credentials.get(0);
    }

    private BookingOrder requiredOrderForUser(long userId, long orderId, boolean lock) {
        List<BookingOrder> orders = jdbcTemplate.query(orderSelect() + " WHERE o.id = ? AND o.mini_user_id = ? AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bookingOrder(resultSet), orderId, userId);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在或不属于当前用户");
        }
        return orders.get(0);
    }

    private BookingOrder requiredOrderById(long orderId, boolean lock) {
        List<BookingOrder> orders = jdbcTemplate.query(orderSelect() + " WHERE o.id = ? AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bookingOrder(resultSet), orderId);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在");
        }
        return orders.get(0);
    }

    private String orderSelect() {
        return "SELECT o.id, o.order_no, o.mini_user_id, o.organization_id, o.store_id, o.inventory_date, o.start_time, o.end_time, o.order_status, "
            + "s.name AS store_name, u.name AS sellable_unit_name, o.contact_name, o.contact_phone FROM booking_order o JOIN store s ON s.id = o.store_id "
            + "JOIN sellable_unit_version v ON v.id = o.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id";
    }

    private BookingOrder bookingOrder(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new BookingOrder(resultSet.getLong("id"), resultSet.getString("order_no"), resultSet.getLong("mini_user_id"),
            resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getObject("inventory_date", LocalDate.class),
            resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getString("order_status"),
            resultSet.getString("store_name"), resultSet.getString("sellable_unit_name"), resultSet.getString("contact_name"), resultSet.getString("contact_phone"));
    }

    private boolean hasVerification(long orderId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM booking_verification WHERE booking_order_id = ? AND is_deleted = 0", Integer.class, orderId);
        return count != null && count > 0;
    }

    private Verification findVerification(long orderId, boolean lock) {
        List<Verification> verifications = jdbcTemplate.query("SELECT v.id, v.employee_id, v.verification_method, v.verified_at, e.employee_name "
                + "FROM booking_verification v JOIN venue_employee e ON e.id = v.employee_id WHERE v.booking_order_id = ? AND v.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new Verification(resultSet.getLong("id"), resultSet.getLong("employee_id"), resultSet.getString("verification_method"),
                resultSet.getObject("verified_at", LocalDateTime.class), resultSet.getString("employee_name")), orderId);
        return verifications.isEmpty() ? null : verifications.get(0);
    }

    private Map<String, Object> previewData(Credential credential) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("credentialId", String.valueOf(credential.id));
        data.put("verificationMethod", credential.type);
        data.put("orderNo", credential.orderNo);
        data.put("storeName", credential.storeName);
        data.put("sellableUnitName", credential.sellableUnitName);
        data.put("date", credential.date.toString());
        data.put("startTime", credential.startTime.toString());
        data.put("endTime", credential.endTime.toString());
        data.put("contactName", maskName(credential.contactName));
        data.put("contactPhone", maskPhone(credential.contactPhone));
        data.put("validFrom", DATETIME_FORMATTER.format(credential.validFrom));
        data.put("validTo", DATETIME_FORMATTER.format(credential.validTo));
        return data;
    }

    private Map<String, Object> confirmedData(BookingOrder order, StaffSessionPrincipal principal, String method, LocalDateTime verifiedAt) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("verificationStatus", "VERIFIED");
        data.put("duplicate", false);
        data.put("orderNo", order.orderNo);
        data.put("employeeId", String.valueOf(principal.getEmployeeId()));
        data.put("employeeName", principal.getEmployeeName());
        data.put("verificationMethod", method);
        data.put("verifiedAt", DATETIME_FORMATTER.format(verifiedAt));
        return data;
    }

    private Map<String, Object> duplicateData(Verification verification, BookingOrder order) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("verificationStatus", "VERIFIED");
        data.put("duplicate", true);
        data.put("orderNo", order.orderNo);
        data.put("employeeId", String.valueOf(verification.employeeId));
        data.put("employeeName", verification.employeeName);
        data.put("verificationMethod", verification.method);
        data.put("verifiedAt", DATETIME_FORMATTER.format(verification.verifiedAt));
        return data;
    }

    private Map<String, Object> attemptData(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(resultSet.getLong("id")));
        data.put("orderNo", resultSet.getString("order_no"));
        data.put("storeName", resultSet.getString("store_name"));
        data.put("sellableUnitName", resultSet.getString("sellable_unit_name"));
        LocalDate date = resultSet.getObject("inventory_date", LocalDate.class);
        LocalTime startTime = resultSet.getObject("start_time", LocalTime.class);
        LocalTime endTime = resultSet.getObject("end_time", LocalTime.class);
        data.put("date", date == null ? null : date.toString());
        data.put("startTime", startTime == null ? null : startTime.toString());
        data.put("endTime", endTime == null ? null : endTime.toString());
        data.put("verificationMethod", resultSet.getString("verification_method"));
        data.put("attemptResult", resultSet.getString("attempt_result"));
        data.put("failureReason", resultSet.getString("failure_reason"));
        data.put("createTime", DATETIME_FORMATTER.format(resultSet.getObject("gmt_created_time", LocalDateTime.class)));
        return data;
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return name.length() == 1 ? "*" : name.substring(0, 1) + "*";
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private LocalDateTime businessTime(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time).atZone(BUSINESS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private void audit(long employeeId, String action, BookingOrder order, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, 'BOOKING_ORDER', ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", action, order.id, order.organizationId, order.storeId, reason,
            employeeId, employeeId);
    }

    private static final class Credential {
        private final long id;
        private final long orderId;
        private final long storeId;
        private final String type;
        private final String status;
        private final LocalDateTime validFrom;
        private final LocalDateTime validTo;
        private final String orderNo;
        private final String orderStatus;
        private final LocalDate date;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String contactName;
        private final String contactPhone;
        private final String storeName;
        private final String sellableUnitName;
        private final boolean hasVerification;
        private Credential(long id, long orderId, long storeId, String type, String status, LocalDateTime validFrom, LocalDateTime validTo, String orderNo,
                           String orderStatus, LocalDate date, LocalTime startTime, LocalTime endTime, String contactName, String contactPhone, String storeName,
                           String sellableUnitName, boolean hasVerification) {
            this.id = id;
            this.orderId = orderId;
            this.storeId = storeId;
            this.type = type;
            this.status = status;
            this.validFrom = validFrom;
            this.validTo = validTo;
            this.orderNo = orderNo;
            this.orderStatus = orderStatus;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
            this.storeName = storeName;
            this.sellableUnitName = sellableUnitName;
            this.hasVerification = hasVerification;
        }
    }

    private static final class BookingOrder {
        private final long id;
        private final String orderNo;
        private final long miniUserId;
        private final long organizationId;
        private final long storeId;
        private final LocalDate date;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final String orderStatus;
        private final String storeName;
        private final String sellableUnitName;
        private final String contactName;
        private final String contactPhone;
        private BookingOrder(long id, String orderNo, long miniUserId, long organizationId, long storeId, LocalDate date, LocalTime startTime,
                             LocalTime endTime, String orderStatus, String storeName, String sellableUnitName, String contactName, String contactPhone) {
            this.id = id;
            this.orderNo = orderNo;
            this.miniUserId = miniUserId;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.date = date;
            this.startTime = startTime;
            this.endTime = endTime;
            this.orderStatus = orderStatus;
            this.storeName = storeName;
            this.sellableUnitName = sellableUnitName;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
        }
    }

    private static final class Verification {
        private final long id;
        private final long employeeId;
        private final String method;
        private final LocalDateTime verifiedAt;
        private final String employeeName;
        private Verification(long id, long employeeId, String method, LocalDateTime verifiedAt, String employeeName) {
            this.id = id;
            this.employeeId = employeeId;
            this.method = method;
            this.verifiedAt = verifiedAt;
            this.employeeName = employeeName;
        }
    }

    private static final class VerificationRejected extends RuntimeException {
        private final Long orderId;
        private final Long credentialId;
        private final String method;
        private final String reason;
        private final String message;
        private VerificationRejected(Long orderId, Long credentialId, String method, String reason, String message) {
            this.orderId = orderId;
            this.credentialId = credentialId;
            this.method = method;
            this.reason = reason;
            this.message = message;
        }
    }
}
