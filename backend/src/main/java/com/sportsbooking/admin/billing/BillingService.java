package com.sportsbooking.admin.billing;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.sportsbooking.admin.api.ApiException;
import com.sportsbooking.admin.auth.TokenService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;

    public BillingService(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @Scheduled(fixedDelayString = "${app.billing-accrual.fixed-delay-ms:60000}")
    @Transactional
    public void accrueEligibleOrders() {
        List<CompletedOrder> orders = jdbcTemplate.query("SELECT o.id, o.organization_id, o.store_id, o.price_fen, o.service_rate_basis_points "
                + "FROM booking_order o WHERE o.order_status = 'COMPLETED' AND o.completed_at <= DATE_SUB(UTC_TIMESTAMP(), INTERVAL 24 HOUR) "
                + "AND o.is_deleted = 0 AND NOT EXISTS (SELECT 1 FROM refund_order r WHERE r.booking_order_id = o.id "
                + "AND r.refund_status = 'REFUND_SUCCEEDED' AND r.is_deleted = 0) ORDER BY o.id ASC LIMIT 100 FOR UPDATE",
            (resultSet, rowNum) -> new CompletedOrder(resultSet.getLong("id"), resultSet.getLong("organization_id"), resultSet.getLong("store_id"),
                resultSet.getInt("price_fen"), resultSet.getInt("service_rate_basis_points")));
        LocalDate period = currentBillingPeriod();
        for (CompletedOrder order : orders) {
            accrue(order, period);
        }
    }

    @Transactional
    public void createRefundAdjustment(long refundId) {
        RefundContext refund = requiredRefund(refundId, true);
        if (!"REFUND_SUCCEEDED".equals(refund.refundStatus)) {
            return;
        }
        LedgerEntry original = findLedgerBySource("ORDER_COMPLETED", refund.bookingOrderId, true);
        if (original == null) {
            return;
        }
        LocalDate period = adjustmentPeriod(original.monthlyBillId);
        try {
            jdbcTemplate.update("INSERT INTO service_fee_ledger (entry_no, organization_id, store_id, booking_order_id, source_type, source_id, entry_type, "
                    + "billing_period, base_amount_fen, service_rate_basis_points, fee_amount_fen, description, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, 'REFUND_ADJUSTMENT', ?, 'ADJUSTMENT', ?, ?, ?, ?, '全额退款服务费调整', 0, 0)",
                businessNo("FEE"), refund.organizationId, refund.storeId, refund.bookingOrderId, refund.id, period, -refund.amountFen,
                original.rateBasisPoints, -original.feeAmountFen);
            audit("SERVICE_FEE_REFUND_ADJUSTED", refund.bookingOrderId, refund.organizationId, refund.storeId, "退款服务费负向调整");
        } catch (DuplicateKeyException exception) {
            // Refund result may be delivered repeatedly; the immutable source key makes this a no-op.
        }
    }

    public Map<String, Object> listLedgers(Long storeId, int page, int pageSize, String billingPeriod) {
        validatePage(page, pageSize);
        LocalDate period = optionalPeriod(billingPeriod);
        List<Object> params = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE l.is_deleted = 0");
        if (storeId != null) {
            where.append(" AND l.store_id = ?");
            params.add(storeId);
        }
        if (period != null) {
            where.append(" AND l.billing_period = ?");
            params.add(period);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM service_fee_ledger l" + where, Long.class, params.toArray());
        params.add(pageSize);
        params.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(ledgerSelect() + where + " ORDER BY l.gmt_created_time DESC, l.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> ledgerData(ledger(resultSet)), params.toArray());
        return page(page, pageSize, total == null ? 0 : total, records);
    }

    public Map<String, Object> listBills(Long storeId, int page, int pageSize, String billingPeriod, String billStatus) {
        validatePage(page, pageSize);
        LocalDate period = optionalPeriod(billingPeriod);
        if (billStatus != null && !billStatus.isBlank() && !isBillStatus(billStatus)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账单状态参数不正确");
        }
        List<Object> params = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE b.is_deleted = 0");
        if (storeId != null) {
            where.append(" AND b.store_id = ?");
            params.add(storeId);
        }
        if (period != null) {
            where.append(" AND b.billing_period = ?");
            params.add(period);
        }
        if (billStatus != null && !billStatus.isBlank()) {
            where.append(" AND b.bill_status = ?");
            params.add(billStatus);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM monthly_bill b" + where, Long.class, params.toArray());
        params.add(pageSize);
        params.add((page - 1L) * pageSize);
        List<Map<String, Object>> records = jdbcTemplate.query(billSelect() + where + " ORDER BY b.billing_period DESC, b.id DESC LIMIT ? OFFSET ?",
            (resultSet, rowNum) -> billData(bill(resultSet)), params.toArray());
        return page(page, pageSize, total == null ? 0 : total, records);
    }

    public Map<String, Object> getBill(Long storeId, long billId) {
        MonthlyBill bill = requiredBill(billId, false);
        if (storeId != null && storeId.longValue() != bill.storeId) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "月账单不存在或不属于当前场馆");
        }
        Map<String, Object> data = billData(bill);
        Long ledgerTotal = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM service_fee_ledger WHERE monthly_bill_id = ? AND is_deleted = 0", Long.class, billId);
        List<Map<String, Object>> ledgers = jdbcTemplate.query(ledgerSelect() + " WHERE l.monthly_bill_id = ? AND l.is_deleted = 0 ORDER BY l.gmt_created_time DESC, l.id DESC LIMIT 500",
            (resultSet, rowNum) -> ledgerData(ledger(resultSet)), billId);
        List<Map<String, Object>> payments = jdbcTemplate.query("SELECT payment_no, payment_method, amount_fen, paid_at, remark, gmt_created_time FROM bill_payment_record WHERE monthly_bill_id = ? AND is_deleted = 0 ORDER BY id DESC",
            (resultSet, rowNum) -> {
                Map<String, Object> payment = new LinkedHashMap<String, Object>();
                payment.put("paymentNo", resultSet.getString("payment_no"));
                payment.put("paymentMethod", resultSet.getString("payment_method"));
                payment.put("amountFen", resultSet.getInt("amount_fen"));
                payment.put("paidAt", DATETIME_FORMATTER.format(resultSet.getObject("paid_at", LocalDateTime.class)));
                payment.put("remark", resultSet.getString("remark"));
                return payment;
            }, billId);
        data.put("ledgerTotal", ledgerTotal == null ? 0 : ledgerTotal);
        data.put("ledgers", ledgers);
        data.put("payments", payments);
        return data;
    }

    @Transactional
    public Map<String, Object> generate(long operatorId, LocalDate billingPeriod) {
        requireBillingPeriod(billingPeriod);
        List<BillGroup> groups = jdbcTemplate.query("SELECT organization_id, store_id FROM service_fee_ledger WHERE billing_period = ? AND monthly_bill_id IS NULL "
                + "AND is_deleted = 0 GROUP BY organization_id, store_id", (resultSet, rowNum) -> new BillGroup(resultSet.getLong("organization_id"),
                resultSet.getLong("store_id")), billingPeriod);
        List<Map<String, Object>> generated = new ArrayList<Map<String, Object>>();
        for (BillGroup group : groups) {
            MonthlyBill bill = findBill(group.storeId, billingPeriod, true);
            if (bill == null) {
                jdbcTemplate.update("INSERT INTO monthly_bill (bill_no, organization_id, store_id, billing_period, bill_status, create_operator_id, modify_operator_id) "
                        + "VALUES (?, ?, ?, ?, 'DRAFT', ?, ?)", businessNo("BIL"), group.organizationId, group.storeId, billingPeriod, operatorId, operatorId);
                bill = requiredBill(lastInsertId(), true);
            }
            if (!"DRAFT".equals(bill.status)) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "已签发账单不能追加本账期分录");
            }
            jdbcTemplate.update("UPDATE service_fee_ledger SET monthly_bill_id = ?, modify_operator_id = ? WHERE organization_id = ? AND store_id = ? "
                + "AND billing_period = ? AND monthly_bill_id IS NULL AND is_deleted = 0", bill.id, operatorId, group.organizationId, group.storeId, billingPeriod);
            refreshDraftBill(bill.id, operatorId);
            generated.add(billData(requiredBill(bill.id, false)));
            audit("MONTHLY_BILL_GENERATED", bill.id, group.organizationId, group.storeId, "账单草稿已归集不可变服务费分录");
        }
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("billingPeriod", billingPeriod.toString());
        data.put("records", generated);
        return data;
    }

    @Transactional
    public Map<String, Object> issue(long operatorId, long billId, LocalDate dueDate) {
        MonthlyBill bill = requiredBill(billId, true);
        if (!"DRAFT".equals(bill.status)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "仅草稿账单可签发");
        }
        if (dueDate == null || !dueDate.isAfter(LocalDate.now(BUSINESS_ZONE))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账单到期日必须晚于当前日期");
        }
        if (bill.payableFeeFen == 0) {
            jdbcTemplate.update("UPDATE monthly_bill SET bill_status = 'PAID', issued_at = UTC_TIMESTAMP(), due_date = ?, paid_at = UTC_TIMESTAMP(), "
                    + "modify_operator_id = ? WHERE id = ? AND bill_status = 'DRAFT'", dueDate, operatorId, bill.id);
        } else {
            jdbcTemplate.update("UPDATE monthly_bill SET bill_status = 'ISSUED', issued_at = UTC_TIMESTAMP(), due_date = ?, modify_operator_id = ? "
                + "WHERE id = ? AND bill_status = 'DRAFT'", dueDate, operatorId, bill.id);
        }
        audit("MONTHLY_BILL_ISSUED", bill.id, bill.organizationId, bill.storeId, "账单已签发");
        return billData(requiredBill(bill.id, false));
    }

    @Transactional
    public Map<String, Object> recordOfflinePayment(long operatorId, long billId, int amountFen, String remark) {
        MonthlyBill bill = requiredBill(billId, true);
        if (!("ISSUED".equals(bill.status) || "OVERDUE".equals(bill.status))) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "当前账单不能登记收款");
        }
        if (amountFen != bill.payableFeeFen || amountFen <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "线下收款金额必须等于账单应付金额");
        }
        if (remark != null && remark.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "收款备注过长");
        }
        try {
            jdbcTemplate.update("INSERT INTO bill_payment_record (payment_no, monthly_bill_id, payment_method, amount_fen, paid_at, remark, recorded_by_account_id, "
                    + "create_operator_id, modify_operator_id) VALUES (?, ?, 'OFFLINE', ?, UTC_TIMESTAMP(), ?, ?, ?, ?)", businessNo("BPM"), bill.id,
                amountFen, remark == null || remark.isBlank() ? null : remark.trim(), operatorId, operatorId, operatorId);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "该账单已登记收款");
        }
        jdbcTemplate.update("UPDATE monthly_bill SET bill_status = 'PAID', paid_at = UTC_TIMESTAMP(), modify_operator_id = ? "
            + "WHERE id = ? AND bill_status IN ('ISSUED', 'OVERDUE')", operatorId, bill.id);
        audit("MONTHLY_BILL_PAID", bill.id, bill.organizationId, bill.storeId, "线下收款已登记");
        return billData(requiredBill(bill.id, false));
    }

    @Scheduled(fixedDelayString = "${app.billing-overdue.fixed-delay-ms:3600000}")
    @Transactional
    public void markOverdueBills() {
        jdbcTemplate.update("UPDATE monthly_bill SET bill_status = 'OVERDUE', modify_operator_id = 0 WHERE bill_status = 'ISSUED' AND due_date < ? "
            + "AND is_deleted = 0", LocalDate.now(BUSINESS_ZONE));
    }

    private void accrue(CompletedOrder order, LocalDate period) {
        int fee = serviceFee(order.priceFen, order.rateBasisPoints);
        try {
            jdbcTemplate.update("INSERT INTO service_fee_ledger (entry_no, organization_id, store_id, booking_order_id, source_type, source_id, entry_type, "
                    + "billing_period, base_amount_fen, service_rate_basis_points, fee_amount_fen, description, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, 'ORDER_COMPLETED', ?, 'ACCRUAL', ?, ?, ?, ?, '订单完成满24小时服务费计提', 0, 0)",
                businessNo("FEE"), order.organizationId, order.storeId, order.id, order.id, period, order.priceFen, order.rateBasisPoints, fee);
            audit("SERVICE_FEE_ACCRUED", order.id, order.organizationId, order.storeId, "订单完成满24小时后计提服务费");
        } catch (DuplicateKeyException exception) {
            // Scheduler retries and concurrent workers both converge on one immutable source entry.
        }
    }

    private void refreshDraftBill(long billId, long operatorId) {
        FeeTotals totals = jdbcTemplate.queryForObject("SELECT COALESCE(SUM(CASE WHEN entry_type = 'ACCRUAL' THEN fee_amount_fen ELSE 0 END), 0) AS accrual_fee, "
                + "COALESCE(SUM(CASE WHEN entry_type = 'ADJUSTMENT' THEN fee_amount_fen ELSE 0 END), 0) AS adjustment_fee FROM service_fee_ledger "
                + "WHERE monthly_bill_id = ? AND is_deleted = 0", (resultSet, rowNum) -> new FeeTotals(resultSet.getInt("accrual_fee"),
                resultSet.getInt("adjustment_fee")), billId);
        int accrual = totals == null ? 0 : totals.accrualFeeFen;
        int adjustment = totals == null ? 0 : totals.adjustmentFeeFen;
        jdbcTemplate.update("UPDATE monthly_bill SET accrual_fee_fen = ?, adjustment_fee_fen = ?, payable_fee_fen = ?, modify_operator_id = ? "
            + "WHERE id = ? AND bill_status = 'DRAFT'", accrual, adjustment, Math.max(0, accrual + adjustment), operatorId, billId);
    }

    private RefundContext requiredRefund(long refundId, boolean lock) {
        List<RefundContext> refunds = jdbcTemplate.query("SELECT r.id, r.booking_order_id, r.amount_fen, r.refund_status, o.organization_id, o.store_id "
                + "FROM refund_order r JOIN booking_order o ON o.id = r.booking_order_id WHERE r.id = ? AND r.is_deleted = 0 AND o.is_deleted = 0"
                + (lock ? " FOR UPDATE" : ""), (resultSet, rowNum) -> new RefundContext(resultSet.getLong("id"), resultSet.getLong("booking_order_id"),
                resultSet.getInt("amount_fen"), resultSet.getString("refund_status"), resultSet.getLong("organization_id"), resultSet.getLong("store_id")), refundId);
        if (refunds.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "退款单不存在");
        }
        return refunds.get(0);
    }

    private LedgerEntry findLedgerBySource(String sourceType, long sourceId, boolean lock) {
        List<LedgerEntry> ledgers = jdbcTemplate.query("SELECT id, booking_order_id, monthly_bill_id, billing_period, service_rate_basis_points, fee_amount_fen "
                + "FROM service_fee_ledger WHERE source_type = ? AND source_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new LedgerEntry(resultSet.getLong("id"), resultSet.getLong("booking_order_id"),
                resultSet.getObject("monthly_bill_id") == null ? null : resultSet.getLong("monthly_bill_id"), resultSet.getObject("billing_period", LocalDate.class),
                resultSet.getInt("service_rate_basis_points"), resultSet.getInt("fee_amount_fen")), sourceType, sourceId);
        return ledgers.isEmpty() ? null : ledgers.get(0);
    }

    private LocalDate adjustmentPeriod(Long originalBillId) {
        if (originalBillId != null) {
            MonthlyBill bill = requiredBill(originalBillId, true);
            if ("PAID".equals(bill.status)) {
                return currentBillingPeriod().plusMonths(1);
            }
        }
        return currentBillingPeriod();
    }

    private MonthlyBill findBill(long storeId, LocalDate billingPeriod, boolean lock) {
        List<MonthlyBill> bills = jdbcTemplate.query(billSelect() + " WHERE b.store_id = ? AND b.billing_period = ? AND b.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bill(resultSet), storeId, billingPeriod);
        return bills.isEmpty() ? null : bills.get(0);
    }

    private MonthlyBill requiredBill(long billId, boolean lock) {
        List<MonthlyBill> bills = jdbcTemplate.query(billSelect() + " WHERE b.id = ? AND b.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> bill(resultSet), billId);
        if (bills.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "月账单不存在");
        }
        return bills.get(0);
    }

    private String ledgerSelect() {
        return "SELECT l.id, l.entry_no, l.organization_id, l.store_id, l.booking_order_id, l.monthly_bill_id, l.source_type, l.source_id, l.entry_type, "
            + "l.billing_period, l.base_amount_fen, l.service_rate_basis_points, l.fee_amount_fen, l.description, l.gmt_created_time, o.order_no, s.name AS store_name "
            + "FROM service_fee_ledger l JOIN booking_order o ON o.id = l.booking_order_id JOIN store s ON s.id = l.store_id";
    }

    private String billSelect() {
        return "SELECT b.id, b.bill_no, b.organization_id, b.store_id, b.billing_period, b.accrual_fee_fen, b.adjustment_fee_fen, b.payable_fee_fen, "
            + "b.bill_status, b.issued_at, b.due_date, b.paid_at, b.gmt_created_time, s.name AS store_name FROM monthly_bill b JOIN store s ON s.id = b.store_id";
    }

    private LedgerEntry ledger(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new LedgerEntry(resultSet.getLong("id"), resultSet.getLong("booking_order_id"),
            resultSet.getObject("monthly_bill_id") == null ? null : resultSet.getLong("monthly_bill_id"), resultSet.getObject("billing_period", LocalDate.class),
            resultSet.getInt("service_rate_basis_points"), resultSet.getInt("fee_amount_fen"), resultSet.getString("entry_no"),
            resultSet.getLong("organization_id"), resultSet.getLong("store_id"), resultSet.getString("source_type"), resultSet.getLong("source_id"),
            resultSet.getString("entry_type"), resultSet.getInt("base_amount_fen"), resultSet.getString("description"),
            resultSet.getObject("gmt_created_time", LocalDateTime.class), resultSet.getString("order_no"), resultSet.getString("store_name"));
    }

    private MonthlyBill bill(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new MonthlyBill(resultSet.getLong("id"), resultSet.getString("bill_no"), resultSet.getLong("organization_id"), resultSet.getLong("store_id"),
            resultSet.getObject("billing_period", LocalDate.class), resultSet.getInt("accrual_fee_fen"), resultSet.getInt("adjustment_fee_fen"),
            resultSet.getInt("payable_fee_fen"), resultSet.getString("bill_status"), resultSet.getObject("issued_at", LocalDateTime.class),
            resultSet.getObject("due_date", LocalDate.class), resultSet.getObject("paid_at", LocalDateTime.class), resultSet.getObject("gmt_created_time", LocalDateTime.class),
            resultSet.getString("store_name"));
    }

    private Map<String, Object> ledgerData(LedgerEntry entry) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(entry.id));
        data.put("entryNo", entry.entryNo);
        data.put("orderId", String.valueOf(entry.bookingOrderId));
        data.put("orderNo", entry.orderNo);
        data.put("storeId", String.valueOf(entry.storeId));
        data.put("storeName", entry.storeName);
        data.put("monthlyBillId", entry.monthlyBillId == null ? null : String.valueOf(entry.monthlyBillId));
        data.put("sourceType", entry.sourceType);
        data.put("sourceId", String.valueOf(entry.sourceId));
        data.put("entryType", entry.entryType);
        data.put("billingPeriod", entry.billingPeriod.toString());
        data.put("baseAmountFen", entry.baseAmountFen);
        data.put("serviceRateBasisPoints", entry.rateBasisPoints);
        data.put("feeAmountFen", entry.feeAmountFen);
        data.put("description", entry.description);
        data.put("createTime", DATETIME_FORMATTER.format(entry.createdTime));
        return data;
    }

    private Map<String, Object> billData(MonthlyBill bill) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(bill.id));
        data.put("billNo", bill.billNo);
        data.put("storeId", String.valueOf(bill.storeId));
        data.put("storeName", bill.storeName);
        data.put("billingPeriod", bill.billingPeriod.toString());
        data.put("accrualFeeFen", bill.accrualFeeFen);
        data.put("adjustmentFeeFen", bill.adjustmentFeeFen);
        data.put("payableFeeFen", bill.payableFeeFen);
        data.put("billStatus", bill.status);
        data.put("issuedAt", bill.issuedAt == null ? null : DATETIME_FORMATTER.format(bill.issuedAt));
        data.put("dueDate", bill.dueDate == null ? null : bill.dueDate.toString());
        data.put("paidAt", bill.paidAt == null ? null : DATETIME_FORMATTER.format(bill.paidAt));
        data.put("createTime", DATETIME_FORMATTER.format(bill.createdTime));
        return data;
    }

    private int serviceFee(int priceFen, int rateBasisPoints) {
        return (int) (((long) priceFen * rateBasisPoints + 5_000L) / 10_000L);
    }

    private LocalDate currentBillingPeriod() {
        return LocalDate.now(BUSINESS_ZONE).withDayOfMonth(1);
    }

    private LocalDate optionalPeriod(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate period = LocalDate.parse(value + "-01");
            requireBillingPeriod(period);
            return period;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账期参数必须为yyyy-MM");
        }
    }

    private void requireBillingPeriod(LocalDate billingPeriod) {
        if (billingPeriod == null || billingPeriod.getDayOfMonth() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "账期必须是月初日期");
        }
    }

    private boolean isBillStatus(String value) {
        return "DRAFT".equals(value) || "ISSUED".equals(value) || "PAID".equals(value) || "OVERDUE".equals(value);
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "分页参数不正确");
        }
    }

    private Map<String, Object> page(int page, int pageSize, long total, List<Map<String, Object>> records) {
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
            throw new IllegalStateException("无法读取新增账单ID");
        }
        return id;
    }

    private String businessNo(String prefix) {
        return prefix + tokenService.generate().replace("_", "").replace("-", "").substring(0, 24).toUpperCase(Locale.ROOT);
    }

    private void audit(String action, long targetId, long organizationId, long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, 'BILLING', ?, ?, ?, 'SUCCEEDED', ?, 0, 0)", action, targetId, organizationId, storeId, reason);
    }

    private static final class CompletedOrder {
        private final long id;
        private final long organizationId;
        private final long storeId;
        private final int priceFen;
        private final int rateBasisPoints;
        private CompletedOrder(long id, long organizationId, long storeId, int priceFen, int rateBasisPoints) {
            this.id = id;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.priceFen = priceFen;
            this.rateBasisPoints = rateBasisPoints;
        }
    }

    private static final class RefundContext {
        private final long id;
        private final long bookingOrderId;
        private final int amountFen;
        private final String refundStatus;
        private final long organizationId;
        private final long storeId;
        private RefundContext(long id, long bookingOrderId, int amountFen, String refundStatus, long organizationId, long storeId) {
            this.id = id;
            this.bookingOrderId = bookingOrderId;
            this.amountFen = amountFen;
            this.refundStatus = refundStatus;
            this.organizationId = organizationId;
            this.storeId = storeId;
        }
    }

    private static final class LedgerEntry {
        private final long id;
        private final long bookingOrderId;
        private final Long monthlyBillId;
        private final LocalDate billingPeriod;
        private final int rateBasisPoints;
        private final int feeAmountFen;
        private final String entryNo;
        private final long organizationId;
        private final long storeId;
        private final String sourceType;
        private final long sourceId;
        private final String entryType;
        private final int baseAmountFen;
        private final String description;
        private final LocalDateTime createdTime;
        private final String orderNo;
        private final String storeName;
        private LedgerEntry(long id, long bookingOrderId, Long monthlyBillId, LocalDate billingPeriod, int rateBasisPoints, int feeAmountFen) {
            this(id, bookingOrderId, monthlyBillId, billingPeriod, rateBasisPoints, feeAmountFen, null, 0, 0, null, 0, null, 0, null, null, null, null);
        }
        private LedgerEntry(long id, long bookingOrderId, Long monthlyBillId, LocalDate billingPeriod, int rateBasisPoints, int feeAmountFen,
                            String entryNo, long organizationId, long storeId, String sourceType, long sourceId, String entryType, int baseAmountFen,
                            String description, LocalDateTime createdTime, String orderNo, String storeName) {
            this.id = id;
            this.bookingOrderId = bookingOrderId;
            this.monthlyBillId = monthlyBillId;
            this.billingPeriod = billingPeriod;
            this.rateBasisPoints = rateBasisPoints;
            this.feeAmountFen = feeAmountFen;
            this.entryNo = entryNo;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.entryType = entryType;
            this.baseAmountFen = baseAmountFen;
            this.description = description;
            this.createdTime = createdTime;
            this.orderNo = orderNo;
            this.storeName = storeName;
        }
    }

    private static final class MonthlyBill {
        private final long id;
        private final String billNo;
        private final long organizationId;
        private final long storeId;
        private final LocalDate billingPeriod;
        private final int accrualFeeFen;
        private final int adjustmentFeeFen;
        private final int payableFeeFen;
        private final String status;
        private final LocalDateTime issuedAt;
        private final LocalDate dueDate;
        private final LocalDateTime paidAt;
        private final LocalDateTime createdTime;
        private final String storeName;
        private MonthlyBill(long id, String billNo, long organizationId, long storeId, LocalDate billingPeriod, int accrualFeeFen, int adjustmentFeeFen,
                            int payableFeeFen, String status, LocalDateTime issuedAt, LocalDate dueDate, LocalDateTime paidAt, LocalDateTime createdTime,
                            String storeName) {
            this.id = id;
            this.billNo = billNo;
            this.organizationId = organizationId;
            this.storeId = storeId;
            this.billingPeriod = billingPeriod;
            this.accrualFeeFen = accrualFeeFen;
            this.adjustmentFeeFen = adjustmentFeeFen;
            this.payableFeeFen = payableFeeFen;
            this.status = status;
            this.issuedAt = issuedAt;
            this.dueDate = dueDate;
            this.paidAt = paidAt;
            this.createdTime = createdTime;
            this.storeName = storeName;
        }
    }

    private static final class BillGroup {
        private final long organizationId;
        private final long storeId;
        private BillGroup(long organizationId, long storeId) {
            this.organizationId = organizationId;
            this.storeId = storeId;
        }
    }

    private static final class FeeTotals {
        private final int accrualFeeFen;
        private final int adjustmentFeeFen;
        private FeeTotals(int accrualFeeFen, int adjustmentFeeFen) {
            this.accrualFeeFen = accrualFeeFen;
            this.adjustmentFeeFen = adjustmentFeeFen;
        }
    }
}
