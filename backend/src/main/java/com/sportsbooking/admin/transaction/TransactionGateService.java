package com.sportsbooking.admin.transaction;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sportsbooking.admin.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TransactionGateService {
    public static final String ORGANIZATION = "ORGANIZATION";
    public static final String STORE = "STORE";
    private static final String MANUAL = "MANUAL";
    private static final String PLATFORM_STATUS = "PLATFORM_STATUS";
    private static final Set<String> GATE_SCOPE_TYPES = new HashSet<String>(Arrays.asList(ORGANIZATION, STORE));
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public TransactionGateService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void createGates(long operatorId, long organizationId, long storeId) {
        jdbcTemplate.update("INSERT INTO transaction_gate (scope_type, scope_id, gate_status, gate_version, create_operator_id, modify_operator_id) "
                + "VALUES ('ORGANIZATION', ?, 'OPEN', 1, ?, ?)", organizationId, operatorId, operatorId);
        jdbcTemplate.update("INSERT INTO transaction_gate (scope_type, scope_id, gate_status, gate_version, create_operator_id, modify_operator_id) "
                + "VALUES ('STORE', ?, 'OPEN', 1, ?, ?)", storeId, operatorId, operatorId);
    }

    @Transactional
    public void updatePlatformStoreStatus(long operatorId, long storeId, String status, String reason) {
        if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "场馆状态参数不正确");
        }
        StoreScope store = findStore(storeId, false);
        TransactionGate gate = lockGate(STORE, store.storeId);
        if ("PAUSED".equals(status)) {
            validateReason(reason);
            activateBlocker(operatorId, gate, PLATFORM_STATUS, 0, reason.trim());
        } else {
            releaseCause(operatorId, gate, PLATFORM_STATUS, 0);
        }
        jdbcTemplate.update("UPDATE store SET status = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0", status, operatorId, storeId);
        audit(operatorId, "STORE_STATUS_CHANGED", "STORE", storeId, store.organizationId, storeId, status);
    }

    @Transactional
    public void updatePlatformOrganizationStatus(long operatorId, long organizationId, String status, String reason) {
        if (!"ACTIVE".equals(status) && !"PAUSED".equals(status)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "经营主体状态参数不正确");
        }
        StoreScope store = findStoreForOrganization(organizationId, false);
        TransactionGate gate = lockGate(ORGANIZATION, organizationId);
        if ("PAUSED".equals(status)) {
            validateReason(reason);
            activateBlocker(operatorId, gate, PLATFORM_STATUS, 0, reason.trim());
        } else {
            releaseCause(operatorId, gate, PLATFORM_STATUS, 0);
        }
        jdbcTemplate.update("UPDATE organization SET status = ?, modify_operator_id = ? WHERE id = ? AND is_deleted = 0", status, operatorId, organizationId);
        audit(operatorId, "ORGANIZATION_STATUS_CHANGED", "ORGANIZATION", organizationId, organizationId, store.storeId, status);
    }

    @Transactional
    public Map<String, Object> createManualBlocker(long operatorId, String scopeType, long scopeId, String reason) {
        validateScope(scopeType, scopeId);
        validateReason(reason);
        verifyScopeExists(scopeType, scopeId);
        TransactionGate gate = lockGate(scopeType, scopeId);
        long blockerId = activateBlocker(operatorId, gate, MANUAL, operatorId, reason.trim());
        audit(operatorId, "TRANSACTION_BLOCKER_ACTIVATED", "TRANSACTION_BLOCKER", blockerId, organizationId(scopeType, scopeId), storeId(scopeType, scopeId), reason.trim());
        return transactionStatusForScope(scopeType, scopeId);
    }

    @Transactional
    public Map<String, Object> releaseBlocker(long operatorId, String scopeType, long scopeId, long blockerId) {
        validateScope(scopeType, scopeId);
        TransactionBlocker initial = requiredBlocker(scopeType, scopeId, blockerId, false);
        TransactionGate gate = lockGate(scopeType, scopeId);
        TransactionBlocker blocker = requiredBlocker(scopeType, scopeId, initial.id, true);
        if (!"ACTIVE".equals(blocker.blockerStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "交易暂停原因已解除");
        }
        jdbcTemplate.update("UPDATE transaction_blocker SET blocker_status = 'RELEASED', released_at = UTC_TIMESTAMP(), released_operator_id = ?, modify_operator_id = ? "
                + "WHERE id = ? AND blocker_status = 'ACTIVE' AND is_deleted = 0", operatorId, operatorId, blocker.id);
        refreshGate(operatorId, gate);
        audit(operatorId, "TRANSACTION_BLOCKER_RELEASED", "TRANSACTION_BLOCKER", blocker.id, organizationId(scopeType, scopeId), storeId(scopeType, scopeId), blocker.causeType);
        return transactionStatusForScope(scopeType, scopeId);
    }

    public Map<String, Object> transactionStatusForStore(long storeId) {
        StoreScope store = findStore(storeId, false);
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        TransactionGate organizationGate = requiredGate(ORGANIZATION, store.organizationId, false);
        TransactionGate storeGate = requiredGate(STORE, store.storeId, false);
        data.put("organizationGate", gateData(organizationGate));
        data.put("storeGate", gateData(storeGate));
        data.put("allowsNewTransaction", "OPEN".equals(organizationGate.gateStatus) && "OPEN".equals(storeGate.gateStatus)
            && "ACTIVE".equals(store.organizationStatus) && "ACTIVE".equals(store.storeStatus));
        return data;
    }

    public Map<String, Object> transactionStatusForScope(String scopeType, long scopeId) {
        validateScope(scopeType, scopeId);
        if (ORGANIZATION.equals(scopeType)) {
            StoreScope store = findStoreForOrganization(scopeId, false);
            return transactionStatusForStore(store.storeId);
        }
        return transactionStatusForStore(scopeId);
    }

    /**
     * T-06 预约创建在同一事务内调用本方法，使暂停和预约以同一门禁行的锁顺序串行。
     */
    @Transactional
    public void lockAndRequireNewTransactionAllowed(long storeId) {
        StoreScope initial = findStore(storeId, false);
        TransactionGate organizationGate = lockGate(ORGANIZATION, initial.organizationId);
        TransactionGate storeGate = lockGate(STORE, initial.storeId);
        StoreScope current = findStore(storeId, true);
        if (!"OPEN".equals(organizationGate.gateStatus) || !"OPEN".equals(storeGate.gateStatus)
            || !"ACTIVE".equals(current.organizationStatus) || !"ACTIVE".equals(current.storeStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, 0, "经营主体或场馆当前不允许新增预约");
        }
    }

    private long activateBlocker(long operatorId, TransactionGate gate, String causeType, long causeId, String reason) {
        List<TransactionBlocker> blockers = jdbcTemplate.query(
            "SELECT id, transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, released_at, released_operator_id, gmt_created_time "
                + "FROM transaction_blocker WHERE scope_type = ? AND scope_id = ? AND cause_type = ? AND cause_id = ? AND is_deleted = 0 FOR UPDATE",
            (resultSet, rowNum) -> blocker(resultSet), gate.scopeType, gate.scopeId, causeType, causeId);
        long blockerId;
        if (blockers.isEmpty()) {
            jdbcTemplate.update("INSERT INTO transaction_blocker (transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, create_operator_id, modify_operator_id) "
                    + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)", gate.id, gate.scopeType, gate.scopeId, causeType, causeId, reason, operatorId, operatorId);
            blockerId = lastInsertId();
        } else {
            TransactionBlocker existing = blockers.get(0);
            if ("ACTIVE".equals(existing.blockerStatus)) {
                throw new ApiException(HttpStatus.CONFLICT, 0, "该交易暂停原因已生效");
            }
            jdbcTemplate.update("UPDATE transaction_blocker SET blocker_status = 'ACTIVE', reason = ?, released_at = NULL, released_operator_id = NULL, modify_operator_id = ? "
                    + "WHERE id = ? AND blocker_status = 'RELEASED' AND is_deleted = 0", reason, operatorId, existing.id);
            blockerId = existing.id;
        }
        refreshGate(operatorId, gate);
        return blockerId;
    }

    private void releaseCause(long operatorId, TransactionGate gate, String causeType, long causeId) {
        List<Long> ids = jdbcTemplate.query("SELECT id FROM transaction_blocker WHERE scope_type = ? AND scope_id = ? AND cause_type = ? AND cause_id = ? "
                + "AND blocker_status = 'ACTIVE' AND is_deleted = 0 FOR UPDATE", (resultSet, rowNum) -> resultSet.getLong("id"),
            gate.scopeType, gate.scopeId, causeType, causeId);
        for (Long id : ids) {
            jdbcTemplate.update("UPDATE transaction_blocker SET blocker_status = 'RELEASED', released_at = UTC_TIMESTAMP(), released_operator_id = ?, modify_operator_id = ? "
                    + "WHERE id = ? AND blocker_status = 'ACTIVE' AND is_deleted = 0", operatorId, operatorId, id);
        }
        refreshGate(operatorId, gate);
    }

    private void refreshGate(long operatorId, TransactionGate gate) {
        Integer activeCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM transaction_blocker WHERE transaction_gate_id = ? AND blocker_status = 'ACTIVE' AND is_deleted = 0",
            Integer.class, gate.id);
        jdbcTemplate.update("UPDATE transaction_gate SET gate_status = ?, gate_version = gate_version + 1, modify_operator_id = ? WHERE id = ? AND is_deleted = 0",
            activeCount != null && activeCount > 0 ? "BLOCKED" : "OPEN", operatorId, gate.id);
    }

    private Map<String, Object> gateData(TransactionGate gate) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(gate.id));
        data.put("scopeType", gate.scopeType);
        data.put("scopeId", String.valueOf(gate.scopeId));
        data.put("gateStatus", gate.gateStatus);
        data.put("gateVersion", String.valueOf(gate.gateVersion));
        List<Map<String, Object>> blockers = jdbcTemplate.query(
            "SELECT id, transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, released_at, released_operator_id, gmt_created_time "
                + "FROM transaction_blocker WHERE transaction_gate_id = ? AND is_deleted = 0 ORDER BY id DESC",
            (resultSet, rowNum) -> blockerData(blocker(resultSet)), gate.id);
        data.put("blockers", blockers);
        return data;
    }

    private Map<String, Object> blockerData(TransactionBlocker blocker) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", String.valueOf(blocker.id));
        data.put("causeType", blocker.causeType);
        data.put("causeId", String.valueOf(blocker.causeId));
        data.put("blockerStatus", blocker.blockerStatus);
        data.put("reason", blocker.reason);
        data.put("releasedAt", blocker.releasedAt == null ? null : DATETIME_FORMATTER.format(blocker.releasedAt));
        data.put("releasedOperatorId", blocker.releasedOperatorId == null ? null : String.valueOf(blocker.releasedOperatorId));
        data.put("createTime", DATETIME_FORMATTER.format(blocker.createdTime));
        return data;
    }

    private TransactionGate lockGate(String scopeType, long scopeId) {
        return requiredGate(scopeType, scopeId, true);
    }

    private TransactionGate requiredGate(String scopeType, long scopeId, boolean lock) {
        List<TransactionGate> gates = jdbcTemplate.query("SELECT id, scope_type, scope_id, gate_status, gate_version FROM transaction_gate "
                + "WHERE scope_type = ? AND scope_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new TransactionGate(resultSet.getLong("id"), resultSet.getString("scope_type"), resultSet.getLong("scope_id"),
                resultSet.getString("gate_status"), resultSet.getLong("gate_version")), scopeType, scopeId);
        if (gates.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "交易门禁不存在");
        }
        return gates.get(0);
    }

    private TransactionBlocker requiredBlocker(String scopeType, long scopeId, long blockerId, boolean lock) {
        List<TransactionBlocker> blockers = jdbcTemplate.query(
            "SELECT id, transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, released_at, released_operator_id, gmt_created_time "
                + "FROM transaction_blocker WHERE id = ? AND scope_type = ? AND scope_id = ? AND is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> blocker(resultSet), blockerId, scopeType, scopeId);
        if (blockers.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "交易暂停原因不存在或不属于当前范围");
        }
        return blockers.get(0);
    }

    private TransactionBlocker blocker(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        long releasedOperatorId = resultSet.getLong("released_operator_id");
        boolean releasedOperatorIdIsNull = resultSet.wasNull();
        return new TransactionBlocker(resultSet.getLong("id"), resultSet.getLong("transaction_gate_id"), resultSet.getString("scope_type"),
            resultSet.getLong("scope_id"), resultSet.getString("cause_type"), resultSet.getLong("cause_id"), resultSet.getString("blocker_status"),
            resultSet.getString("reason"), resultSet.getObject("released_at", LocalDateTime.class), releasedOperatorIdIsNull ? null : releasedOperatorId,
            resultSet.getObject("gmt_created_time", LocalDateTime.class));
    }

    private StoreScope findStore(long storeId, boolean lock) {
        List<StoreScope> stores = jdbcTemplate.query("SELECT s.id AS store_id, s.organization_id, s.status AS store_status, o.status AS organization_status "
                + "FROM store s JOIN organization o ON o.id = s.organization_id WHERE s.id = ? AND s.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new StoreScope(resultSet.getLong("store_id"), resultSet.getLong("organization_id"), resultSet.getString("store_status"),
                resultSet.getString("organization_status")), storeId);
        if (stores.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "场馆不存在");
        }
        return stores.get(0);
    }

    private StoreScope findStoreForOrganization(long organizationId, boolean lock) {
        List<StoreScope> stores = jdbcTemplate.query("SELECT s.id AS store_id, s.organization_id, s.status AS store_status, o.status AS organization_status "
                + "FROM store s JOIN organization o ON o.id = s.organization_id WHERE s.organization_id = ? AND s.is_deleted = 0 AND o.is_deleted = 0" + (lock ? " FOR UPDATE" : ""),
            (resultSet, rowNum) -> new StoreScope(resultSet.getLong("store_id"), resultSet.getLong("organization_id"), resultSet.getString("store_status"),
                resultSet.getString("organization_status")), organizationId);
        if (stores.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, 0, "经营主体不存在");
        }
        return stores.get(0);
    }

    private void verifyScopeExists(String scopeType, long scopeId) {
        if (ORGANIZATION.equals(scopeType)) {
            findStoreForOrganization(scopeId, false);
        } else {
            findStore(scopeId, false);
        }
    }

    private long organizationId(String scopeType, long scopeId) {
        return ORGANIZATION.equals(scopeType) ? scopeId : findStore(scopeId, false).organizationId;
    }

    private Long storeId(String scopeType, long scopeId) {
        return STORE.equals(scopeType) ? scopeId : findStoreForOrganization(scopeId, false).storeId;
    }

    private void validateScope(String scopeType, long scopeId) {
        if (!GATE_SCOPE_TYPES.contains(scopeType) || scopeId <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "交易门禁范围参数不正确");
        }
    }

    private void validateReason(String reason) {
        if (!StringUtils.hasText(reason) || reason.length() > 255) {
            throw new ApiException(HttpStatus.BAD_REQUEST, 0, "交易暂停说明参数不正确");
        }
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (id == null) {
            throw new IllegalStateException("无法读取新增交易暂停原因ID");
        }
        return id;
    }

    private void audit(long operatorId, String action, String targetType, long targetId, long organizationId, Long storeId, String reason) {
        jdbcTemplate.update("INSERT INTO audit_log (actor_account_id, action, target_type, target_id, organization_id, store_id, result, reason, create_operator_id, modify_operator_id) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'SUCCEEDED', ?, ?, ?)", operatorId, action, targetType, targetId, organizationId, storeId, reason, operatorId, operatorId);
    }

    private static final class TransactionGate {
        private final long id;
        private final String scopeType;
        private final long scopeId;
        private final String gateStatus;
        private final long gateVersion;

        private TransactionGate(long id, String scopeType, long scopeId, String gateStatus, long gateVersion) {
            this.id = id;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.gateStatus = gateStatus;
            this.gateVersion = gateVersion;
        }
    }

    private static final class TransactionBlocker {
        private final long id;
        private final long gateId;
        private final String scopeType;
        private final long scopeId;
        private final String causeType;
        private final long causeId;
        private final String blockerStatus;
        private final String reason;
        private final LocalDateTime releasedAt;
        private final Long releasedOperatorId;
        private final LocalDateTime createdTime;

        private TransactionBlocker(long id, long gateId, String scopeType, long scopeId, String causeType, long causeId, String blockerStatus,
                                   String reason, LocalDateTime releasedAt, Long releasedOperatorId, LocalDateTime createdTime) {
            this.id = id;
            this.gateId = gateId;
            this.scopeType = scopeType;
            this.scopeId = scopeId;
            this.causeType = causeType;
            this.causeId = causeId;
            this.blockerStatus = blockerStatus;
            this.reason = reason;
            this.releasedAt = releasedAt;
            this.releasedOperatorId = releasedOperatorId;
            this.createdTime = createdTime;
        }
    }

    private static final class StoreScope {
        private final long storeId;
        private final long organizationId;
        private final String storeStatus;
        private final String organizationStatus;

        private StoreScope(long storeId, long organizationId, String storeStatus, String organizationStatus) {
            this.storeId = storeId;
            this.organizationId = organizationId;
            this.storeStatus = storeStatus;
            this.organizationStatus = organizationStatus;
        }
    }
}
