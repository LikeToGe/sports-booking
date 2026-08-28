package com.sportsbooking.admin.mini;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import com.sportsbooking.admin.mini.MiniBookingController.CreateBookingRequest;
import com.sportsbooking.admin.transaction.TransactionGateService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class MiniBookingService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> ORDER_STATUSES = new HashSet<String>(Arrays.asList(
        "PENDING_PAYMENT", "PAYMENT_PROCESSING", "PAYMENT_UNKNOWN", "CONFIRMED", "CANCELLED", "COMPLETED"));
    private static final Set<String> ACTIVE_HOLD_STATUSES = new HashSet<String>(Arrays.asList("PENDING_PAYMENT", "PAYMENT_PROCESSING", "PAYMENT_UNKNOWN"));

    private final JdbcTemplate jdbcTemplate;
    private final TransactionGateService transactionGateService;
    private final TokenService tokenService;

    public MiniBookingService(JdbcTemplate jdbcTemplate, TransactionGateService transactionGateService, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionGateService = transactionGateService;
        this.tokenService = tokenService;
    }

    @Transactional
    public Map<String, Object> create(long userId, CreateBookingRequest body, String deviceId, String sourceIp) {
        validateCreateRequest(body, deviceId);
        BookingSlot slot = requiredSlot(body.getInventorySlotId(), false);
        transactionGateService.lockAndRequireNewTransactionAllowed(slot.storeId);
        slot = requiredSlot(body.getInventorySlotId(), true);
        validateBookableSlot(slot);
        BookingQuota quota = lockQuota(userId);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (quota.cooldownUntil != null && quota.cooldownUntil.isAfter(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 0, "当前预约冷却中，请稍后再试");
        }
        if (quota.activeHoldCount >= 2) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 0, "同时待支付预约不能超过2笔");
        }
        Integer recentAttempts = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM booking_order WHERE mini_user_id = ? AND gmt_created_time >= ? AND is_deleted = 0",
            Integer.class, userId, now.minusMinutes(10));
        if (recentAttempts != null && recentAttempts >= 5) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 0, "十分钟内预约创建次数已达上限");
        }
        incrementPressureBucket("DEVICE", deviceId, now);
        incrementPressureBucket("SOURCE_IP", sourceIp == null ? "unknown" : sourceIp, now);

        List<InventoryAtom> atoms = listSlotAtoms(slot.id, true);
        if (atoms.isEmpty() || !allAvailable(atoms)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段已不可售");
        }
        LocalDateTime holdExpiresAt = now.plusMinutes(10);
        String orderNo = "BK" + tokenService.generate().replace("_", "").replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("INSERT INTO booking_order (order_no, mini_user_id, organization_id, store_id, inventory_slot_id, schedule_version_id, sellable_unit_version_id, "
                + "inventory_date, start_time, end_time, price_fen, service_rate_basis_points, contact_name, contact_phone, privacy_policy_version, privacy_consented_at, "
                + "privacy_consent_scene, order_status, hold_expires_at, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING_PAYMENT', ?, 0, 0)",
            orderNo, userId, slot.organizationId, slot.storeId, slot.id, slot.scheduleVersionId, slot.sellableUnitVersionId, slot.inventoryDate,
            slot.startTime, slot.endTime, slot.priceFen, slot.serviceRateBasisPoints, body.getContactName().trim(), body.getContactPhone().trim(),
            body.getPrivacyPolicyVersion().trim(), now, body.getPrivacyConsentScene().trim(), holdExpiresAt);
        long orderId = lastInsertId();
        for (InventoryAtom atom : atoms) {
            int updated = jdbcTemplate.update("UPDATE inventory_atom SET inventory_status = 'PAYMENT_LOCKED', occupying_order_id = ?, hold_expires_at = ?, modify_operator_id = 0 "
                    + "WHERE id = ? AND inventory_status = 'AVAILABLE' AND manual_block_id IS NULL AND occupying_order_id IS NULL AND is_deleted = 0",
                orderId, holdExpiresAt, atom.id);
            if (updated != 1) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段已不可售");
            }
        }
        jdbcTemplate.update("UPDATE user_booking_quota SET active_hold_count = active_hold_count + 1, modify_operator_id = 0 WHERE mini_user_id = ? AND active_hold_count < 2", userId);
        audit(userId, "BOOKING_CREATED", orderId, slot.organizationId, slot.storeId, orderNo);
        return orderData(requiredOrder(userId, orderId, false), true);
    }

    public Map<String, Object> listMine(long userId, int page, int pageSize, String status) {
        validatePage(page, pageSize);
        if (StringUtils.hasText(status) && !ORDER_STATUSES.contains(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "订单状态筛选参数不正确");
        }
        List<Object> parameters = new ArrayList<Object>();
        parameters.add(userId);
        String where = " WHERE o.mini_user_id = ? AND o.is_deleted = 0";
        if (StringUtils.hasText(status)) {
            where += " AND o.order_status = ?";
            parameters.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM booking_order o" + where, Long.class, parameters.toArray());
        parameters.add(pageSize);
        parameters.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(orderSelect() + where + " ORDER BY o.gmt_created_time DESC, o.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> orderData(order(resultSet), true), parameters.toArray());
        return pageData(page, pageSize, total == null ? 0 : total, records);
    }

    public Map<String, Object> getMine(long userId, long orderId) {
        BookingOrder order = requiredOrder(userId, orderId, false);
        Map<String, Object> data = orderData(order, true);
        List<Map<String, Object>> payments = jdbcTemplate.query("SELECT id, payment_status FROM payment_order WHERE booking_order_id = ? AND is_deleted = 0",
            (resultSet, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", String.valueOf(resultSet.getLong("id")));
                item.put("status", resultSet.getString("payment_status"));
                return item;
            }, orderId);
        if (!payments.isEmpty()) {
            data.put("paymentId", payments.get(0).get("id"));
            data.put("paymentStatus", payments.get(0).get("status"));
        }
        List<Map<String, Object>> refunds = jdbcTemplate.query("SELECT id, refund_status FROM refund_order WHERE booking_order_id = ? AND is_deleted = 0 ORDER BY id DESC LIMIT 1",
            (resultSet, rowNum) -> {
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", String.valueOf(resultSet.getLong("id")));
                item.put("status", resultSet.getString("refund_status"));
                return item;
            }, orderId);
        if (!refunds.isEmpty()) {
            data.put("refundId", refunds.get(0).get("id"));
            data.put("refundStatus", refunds.get(0).get("status"));
        }
        return data;
    }

    private void validateCreateRequest(CreateBookingRequest body, String deviceId) {
        if (body == null || body.getInventorySlotId() == null || body.getInventorySlotId() <= 0 || !StringUtils.hasText(body.getContactName())
            || body.getContactName().length() > 64 || !StringUtils.hasText(body.getContactPhone()) || body.getContactPhone().length() > 32
            || !StringUtils.hasText(body.getPrivacyPolicyVersion()) || body.getPrivacyPolicyVersion().length() > 64
            || !StringUtils.hasText(body.getPrivacyConsentScene()) || body.getPrivacyConsentScene().length() > 64
            || !StringUtils.hasText(deviceId) || deviceId.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "预约参数不正确");
        }
    }

    private void validateBookableSlot(BookingSlot slot) {
        LocalDateTime start = LocalDateTime.of(slot.inventoryDate, slot.startTime);
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (!slot.currentSchedule || !"ACTIVE".equals(slot.organizationStatus) || !"ACTIVE".equals(slot.storeStatus)
            || !start.isAfter(now.plusMinutes(slot.bookingMinLeadMinutes))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段当前不可预约");
        }
        if (slot.inventoryDate.isAfter(now.toLocalDate().plusDays(slot.bookingMaxDays - 1L))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "所选固定时段超出预约窗口");
        }
    }

    private BookingSlot requiredSlot(long inventorySlotId, boolean lock) {
        List<BookingSlot> slots = jdbcTemplate.query("SELECT i.id, i.store_id, s.organization_id, i.schedule_version_id, i.sellable_unit_version_id, i.inventory_date, "
                + "i.start_time, i.end_time, i.price_fen, s.service_rate_basis_points, s.booking_min_lead_minutes, s.booking_max_days, s.status AS store_status, "
                + "o.status AS organization_status, CASE WHEN EXISTS (SELECT 1 FROM schedule_version v WHERE v.id = i.schedule_version_id "
                + "AND v.version_status = 'PUBLISHED' AND v.effective_from <= i.inventory_date AND (v.effective_to IS NULL OR v.effective_to >= i.inventory_date) "
                + "AND v.is_deleted = 0) THEN 1 ELSE 0 END AS current_schedule "
                + "FROM inventory_slot i JOIN store s ON s.id = i.store_id JOIN organization o ON o.id = s.organization_id "
                + "WHERE i.id = ? AND i.is_deleted = 0 AND s.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new BookingSlot(resultSet.getLong("id"), resultSet.getLong("store_id"), resultSet.getLong("organization_id"),
                resultSet.getLong("schedule_version_id"), resultSet.getLong("sellable_unit_version_id"), resultSet.getObject("inventory_date", LocalDate.class),
                resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class), resultSet.getInt("price_fen"),
                resultSet.getInt("service_rate_basis_points"), resultSet.getInt("booking_min_lead_minutes"), resultSet.getInt("booking_max_days"),
                resultSet.getString("store_status"), resultSet.getString("organization_status"), resultSet.getInt("current_schedule") == 1), inventorySlotId);
        if (slots.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "固定时段不存在");
        }
        return slots.get(0);
    }

    private List<InventoryAtom> listSlotAtoms(long inventorySlotId, boolean lock) {
        return jdbcTemplate.query("SELECT a.id, a.base_resource_id, a.inventory_status, a.manual_block_id, a.occupying_order_id FROM inventory_slot_atom m "
                + "JOIN inventory_atom a ON a.id = m.inventory_atom_id WHERE m.inventory_slot_id = ? AND m.is_deleted = 0 AND a.is_deleted = 0 "
                + "ORDER BY a.base_resource_id, a.start_time" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> {
                long manualBlockId = resultSet.getLong("manual_block_id");
                boolean manualBlockIsNull = resultSet.wasNull();
                long orderId = resultSet.getLong("occupying_order_id");
                boolean orderIdIsNull = resultSet.wasNull();
                return new InventoryAtom(resultSet.getLong("id"), resultSet.getLong("base_resource_id"), resultSet.getString("inventory_status"),
                    manualBlockIsNull ? null : manualBlockId, orderIdIsNull ? null : orderId);
            }, inventorySlotId);
    }

    private boolean allAvailable(List<InventoryAtom> atoms) {
        for (InventoryAtom atom : atoms) {
            if (!"AVAILABLE".equals(atom.inventoryStatus) || atom.manualBlockId != null || atom.occupyingOrderId != null) {
                return false;
            }
        }
        return true;
    }

    private BookingQuota lockQuota(long userId) {
        jdbcTemplate.update("INSERT IGNORE INTO user_booking_quota (mini_user_id, create_operator_id, modify_operator_id) VALUES (?, 0, 0)", userId);
        List<BookingQuota> quotas = jdbcTemplate.query("SELECT mini_user_id, active_hold_count, cooldown_until FROM user_booking_quota "
                + "WHERE mini_user_id = ? AND is_deleted = 0 FOR UPDATE", (resultSet, rowNum) -> new BookingQuota(resultSet.getLong("mini_user_id"),
                resultSet.getInt("active_hold_count"), resultSet.getObject("cooldown_until", LocalDateTime.class)), userId);
        if (quotas.isEmpty()) {
            throw new IllegalStateException("用户预约配额不存在");
        }
        return quotas.get(0);
    }

    private void incrementPressureBucket(String bucketType, String source, LocalDateTime now) {
        LocalDateTime bucketStart = now.withMinute((now.getMinute() / 10) * 10).withSecond(0).withNano(0);
        String keyHash = tokenService.hash(source);
        jdbcTemplate.update("INSERT IGNORE INTO booking_request_bucket (bucket_type, bucket_key_hash, bucket_start_at, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, 0, 0)", bucketType, keyHash, bucketStart);
        List<Integer> counts = jdbcTemplate.query("SELECT request_count FROM booking_request_bucket WHERE bucket_type = ? AND bucket_key_hash = ? "
                + "AND bucket_start_at = ? AND is_deleted = 0 FOR UPDATE", (resultSet, rowNum) -> resultSet.getInt("request_count"), bucketType, keyHash, bucketStart);
        if (counts.isEmpty()) {
            throw new IllegalStateException("预约来源压力桶不存在");
        }
        if (counts.get(0) >= 20) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, 0, "当前设备或来源预约请求过于频繁");
        }
        jdbcTemplate.update("UPDATE booking_request_bucket SET request_count = request_count + 1, modify_operator_id = 0 WHERE bucket_type = ? "
                + "AND bucket_key_hash = ? AND bucket_start_at = ? AND request_count < 20", bucketType, keyHash, bucketStart);
    }

    private BookingOrder requiredOrder(long userId, long orderId, boolean lock) {
        List<BookingOrder> orders = jdbcTemplate.query(orderSelect() + " WHERE o.id = ? AND o.mini_user_id = ? AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> order(resultSet), orderId, userId);
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "订单不存在或不属于当前用户");
        }
        return orders.get(0);
    }

    private String orderSelect() {
        return "SELECT o.id, o.order_no, o.mini_user_id, o.organization_id, o.store_id, o.inventory_slot_id, o.schedule_version_id, o.sellable_unit_version_id, "
            + "o.inventory_date, o.start_time, o.end_time, o.price_fen, o.contact_name, o.contact_phone, o.order_status, o.hold_expires_at, o.paid_at, "
            + "o.cancelled_at, o.completed_at, o.gmt_created_time, s.name AS store_name, u.name AS sellable_unit_name, vr.verified_at FROM booking_order o "
            + "JOIN store s ON s.id = o.store_id JOIN sellable_unit_version v ON v.id = o.sellable_unit_version_id JOIN sellable_unit u ON u.id = v.sellable_unit_id "
            + "LEFT JOIN booking_verification vr ON vr.booking_order_id = o.id AND vr.is_deleted = 0";
    }

    private BookingOrder order(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new BookingOrder(resultSet.getLong("id"), resultSet.getString("order_no"), resultSet.getLong("mini_user_id"), resultSet.getLong("organization_id"),
            resultSet.getLong("store_id"), resultSet.getLong("inventory_slot_id"), resultSet.getLong("schedule_version_id"), resultSet.getLong("sellable_unit_version_id"),
            resultSet.getObject("inventory_date", LocalDate.class), resultSet.getObject("start_time", LocalTime.class), resultSet.getObject("end_time", LocalTime.class),
            resultSet.getInt("price_fen"), resultSet.getString("contact_name"), resultSet.getString("contact_phone"), resultSet.getString("order_status"),
            resultSet.getObject("hold_expires_at", LocalDateTime.class), resultSet.getObject("paid_at", LocalDateTime.class),
            resultSet.getObject("cancelled_at", LocalDateTime.class), resultSet.getObject("completed_at", LocalDateTime.class),
            resultSet.getObject("gmt_created_time", LocalDateTime.class), resultSet.getString("store_name"), resultSet.getString("sellable_unit_name"),
            resultSet.getObject("verified_at", LocalDateTime.class));
    }

    private Map<String, Object> orderData(BookingOrder order, boolean includePhone) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(order.id));
        data.put("orderNo", order.orderNo);
        data.put("storeId", String.valueOf(order.storeId));
        data.put("storeName", order.storeName);
        data.put("inventorySlotId", String.valueOf(order.inventorySlotId));
        data.put("scheduleVersionId", String.valueOf(order.scheduleVersionId));
        data.put("sellableUnitVersionId", String.valueOf(order.sellableUnitVersionId));
        data.put("sellableUnitName", order.sellableUnitName);
        data.put("date", DATE_FORMATTER.format(order.inventoryDate));
        data.put("startTime", TIME_FORMATTER.format(order.startTime));
        data.put("endTime", TIME_FORMATTER.format(order.endTime));
        data.put("priceFen", order.priceFen);
        data.put("contactName", order.contactName);
        data.put("contactPhone", includePhone ? order.contactPhone : maskPhone(order.contactPhone));
        data.put("orderStatus", order.orderStatus);
        data.put("holdExpiresAt", DATETIME_FORMATTER.format(order.holdExpiresAt));
        data.put("payTime", order.paidAt == null ? null : DATETIME_FORMATTER.format(order.paidAt));
        data.put("cancelTime", order.cancelledAt == null ? null : DATETIME_FORMATTER.format(order.cancelledAt));
        data.put("completedTime", order.completedAt == null ? null : DATETIME_FORMATTER.format(order.completedAt));
        data.put("verificationStatus", order.verifiedAt == null ? "UNVERIFIED" : "VERIFIED");
        data.put("verifiedAt", order.verifiedAt == null ? null : DATETIME_FORMATTER.format(order.verifiedAt));
        data.put("createTime", DATETIME_FORMATTER.format(order.createdTime));
        return data;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
    }

    private Map<String, Object> pageData(int page, int pageSize, long total, List<Map<String, Object>> records) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("current", String.valueOf(page));
        data.put("size", String.valueOf(pageSize));
        data.put("total", String.valueOf(total));
        data.put("records", records);
        return data;
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增订单ID");
        }
        return id;
    }

    private void audit(long userId, String action, long orderId, long organizationId, long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (NULL, ?, 'BOOKING_ORDER', ?, ?, ?, 'SUCCEEDED', ?, 0, 0)", action, orderId, organizationId, storeId, reason);
    }

    private static final class BookingSlot {
        private final long id;
        private final long storeId;
        private final long organizationId;
        private final long scheduleVersionId;
        private final long sellableUnitVersionId;
        private final LocalDate inventoryDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final int serviceRateBasisPoints;
        private final int bookingMinLeadMinutes;
        private final int bookingMaxDays;
        private final String storeStatus;
        private final String organizationStatus;
        private final boolean currentSchedule;

        private BookingSlot(long id, long storeId, long organizationId, long scheduleVersionId, long sellableUnitVersionId, LocalDate inventoryDate,
                            LocalTime startTime, LocalTime endTime, int priceFen, int serviceRateBasisPoints, int bookingMinLeadMinutes,
                            int bookingMaxDays, String storeStatus, String organizationStatus, boolean currentSchedule) {
            this.id = id;
            this.storeId = storeId;
            this.organizationId = organizationId;
            this.scheduleVersionId = scheduleVersionId;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.inventoryDate = inventoryDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.serviceRateBasisPoints = serviceRateBasisPoints;
            this.bookingMinLeadMinutes = bookingMinLeadMinutes;
            this.bookingMaxDays = bookingMaxDays;
            this.storeStatus = storeStatus;
            this.organizationStatus = organizationStatus;
            this.currentSchedule = currentSchedule;
        }
    }

    private static final class InventoryAtom {
        private final long id;
        private final long baseResourceId;
        private final String inventoryStatus;
        private final Long manualBlockId;
        private final Long occupyingOrderId;

        private InventoryAtom(long id, long baseResourceId, String inventoryStatus, Long manualBlockId, Long occupyingOrderId) {
            this.id = id;
            this.baseResourceId = baseResourceId;
            this.inventoryStatus = inventoryStatus;
            this.manualBlockId = manualBlockId;
            this.occupyingOrderId = occupyingOrderId;
        }
    }

    private static final class BookingQuota {
        private final long userId;
        private final int activeHoldCount;
        private final LocalDateTime cooldownUntil;
        private BookingQuota(long userId, int activeHoldCount, LocalDateTime cooldownUntil) {
            this.userId = userId;
            this.activeHoldCount = activeHoldCount;
            this.cooldownUntil = cooldownUntil;
        }
    }

    private static final class BookingOrder {
        private final long id;
        private final String orderNo;
        private final long userId;
        private final long organizationId;
        private final long storeId;
        private final long inventorySlotId;
        private final long scheduleVersionId;
        private final long sellableUnitVersionId;
        private final LocalDate inventoryDate;
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final int priceFen;
        private final String contactName;
        private final String contactPhone;
        private final String orderStatus;
        private final LocalDateTime holdExpiresAt;
        private final LocalDateTime paidAt;
        private final LocalDateTime cancelledAt;
        private final LocalDateTime completedAt;
        private final LocalDateTime createdTime;
        private final String storeName;
        private final String sellableUnitName;
        private final LocalDateTime verifiedAt;

        private BookingOrder(long id, String orderNo, long userId, long organizationId, long storeId, long inventorySlotId, long scheduleVersionId,
                             long sellableUnitVersionId, LocalDate inventoryDate, LocalTime startTime, LocalTime endTime, int priceFen,
                             String contactName, String contactPhone, String orderStatus, LocalDateTime holdExpiresAt, LocalDateTime paidAt,
                             LocalDateTime cancelledAt, LocalDateTime completedAt, LocalDateTime createdTime, String storeName, String sellableUnitName,
                             LocalDateTime verifiedAt) {
            this.id = id;
            this.orderNo = orderNo;
            this.userId = userId;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.inventorySlotId = inventorySlotId;
            this.scheduleVersionId = scheduleVersionId;
            this.sellableUnitVersionId = sellableUnitVersionId;
            this.inventoryDate = inventoryDate;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceFen = priceFen;
            this.contactName = contactName;
            this.contactPhone = contactPhone;
            this.orderStatus = orderStatus;
            this.holdExpiresAt = holdExpiresAt;
            this.paidAt = paidAt;
            this.cancelledAt = cancelledAt;
            this.completedAt = completedAt;
            this.createdTime = createdTime;
            this.storeName = storeName;
            this.sellableUnitName = sellableUnitName;
            this.verifiedAt = verifiedAt;
        }
    }
}
