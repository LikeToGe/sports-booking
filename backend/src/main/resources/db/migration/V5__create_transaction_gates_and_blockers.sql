CREATE TABLE transaction_gate (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    scope_type VARCHAR(16) NOT NULL COMMENT '范围：ORGANIZATION、STORE',
    scope_id BIGINT UNSIGNED NOT NULL COMMENT '主体或门店ID',
    gate_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT '门禁状态：OPEN、BLOCKED',
    gate_version BIGINT UNSIGNED NOT NULL DEFAULT '1' COMMENT '门禁版本',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_gate_scope (scope_type, scope_id),
    CHECK (scope_type IN ('ORGANIZATION', 'STORE')),
    CHECK (gate_status IN ('OPEN', 'BLOCKED')),
    CHECK (gate_version > 0),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='新增交易门禁聚合';

CREATE TABLE transaction_blocker (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    transaction_gate_id BIGINT UNSIGNED NOT NULL COMMENT '交易门禁ID',
    scope_type VARCHAR(16) NOT NULL COMMENT '范围：ORGANIZATION、STORE',
    scope_id BIGINT UNSIGNED NOT NULL COMMENT '主体或门店ID',
    cause_type VARCHAR(32) NOT NULL COMMENT '原因：MANUAL、PLATFORM_STATUS、BALANCE_INSUFFICIENT、RISK',
    cause_id BIGINT UNSIGNED NOT NULL COMMENT '原因实体ID',
    blocker_status VARCHAR(16) NOT NULL COMMENT '状态：ACTIVE、RELEASED',
    reason VARCHAR(255) NOT NULL COMMENT '暂停说明',
    released_at DATETIME NULL COMMENT '解除时间，UTC',
    released_operator_id BIGINT UNSIGNED NULL COMMENT '解除用户ID',
    active_cause_key VARCHAR(96) GENERATED ALWAYS AS (
        CASE WHEN blocker_status = 'ACTIVE' AND is_deleted = 0
            THEN CONCAT(scope_type, ':', scope_id, ':', cause_type, ':', cause_id)
            ELSE NULL END
    ) STORED COMMENT '活动原因唯一键',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_transaction_blocker_cause (scope_type, scope_id, cause_type, cause_id),
    UNIQUE KEY uk_transaction_blocker_active_cause (active_cause_key),
    KEY idx_transaction_blocker_gate_status (transaction_gate_id, blocker_status),
    KEY idx_transaction_blocker_scope_status (scope_type, scope_id, blocker_status),
    CONSTRAINT fk_transaction_blocker_gate FOREIGN KEY (transaction_gate_id) REFERENCES transaction_gate (id),
    CHECK (scope_type IN ('ORGANIZATION', 'STORE')),
    CHECK (cause_type IN ('MANUAL', 'PLATFORM_STATUS', 'BALANCE_INSUFFICIENT', 'RISK')),
    CHECK (blocker_status IN ('ACTIVE', 'RELEASED')),
    CHECK ((blocker_status = 'ACTIVE' AND released_at IS NULL AND released_operator_id IS NULL)
        OR (blocker_status = 'RELEASED' AND released_at IS NOT NULL)),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='新增交易暂停原因';

INSERT INTO transaction_gate (scope_type, scope_id, gate_status, gate_version, create_operator_id, modify_operator_id)
SELECT 'ORGANIZATION', id, CASE WHEN status = 'ACTIVE' THEN 'OPEN' ELSE 'BLOCKED' END, 1, 0, 0
FROM organization
WHERE is_deleted = 0;

INSERT INTO transaction_gate (scope_type, scope_id, gate_status, gate_version, create_operator_id, modify_operator_id)
SELECT 'STORE', id, CASE WHEN status = 'ACTIVE' THEN 'OPEN' ELSE 'BLOCKED' END, 1, 0, 0
FROM store
WHERE is_deleted = 0;

INSERT INTO transaction_blocker (transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, create_operator_id, modify_operator_id)
SELECT g.id, 'ORGANIZATION', o.id, 'PLATFORM_STATUS', 0, 'ACTIVE', '主体状态为暂停', 0, 0
FROM organization o JOIN transaction_gate g ON g.scope_type = 'ORGANIZATION' AND g.scope_id = o.id
WHERE o.is_deleted = 0 AND o.status = 'PAUSED';

INSERT INTO transaction_blocker (transaction_gate_id, scope_type, scope_id, cause_type, cause_id, blocker_status, reason, create_operator_id, modify_operator_id)
SELECT g.id, 'STORE', s.id, 'PLATFORM_STATUS', 0, 'ACTIVE', '门店状态为暂停', 0, 0
FROM store s JOIN transaction_gate g ON g.scope_type = 'STORE' AND g.scope_id = s.id
WHERE s.is_deleted = 0 AND s.status = 'PAUSED';
