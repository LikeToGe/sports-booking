CREATE TABLE physical_court (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    store_id BIGINT UNSIGNED NOT NULL COMMENT '门店ID',
    sport_category_id BIGINT UNSIGNED NOT NULL COMMENT '具体运动项目分类ID',
    name VARCHAR(128) NOT NULL COMMENT '物理场地名称',
    sort_order INT UNSIGNED NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
    status VARCHAR(16) NOT NULL COMMENT '状态：ACTIVE、DISABLED',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_physical_court_store_name (store_id, name),
    KEY idx_physical_court_store_status_sort (store_id, status, sort_order, id),
    CONSTRAINT fk_physical_court_store FOREIGN KEY (store_id) REFERENCES store (id),
    CONSTRAINT fk_physical_court_sport_category FOREIGN KEY (sport_category_id) REFERENCES sport_category (id),
    CHECK (status IN ('ACTIVE', 'DISABLED')),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物理场地';

CREATE TABLE base_resource (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    physical_court_id BIGINT UNSIGNED NOT NULL COMMENT '物理场地ID',
    code VARCHAR(8) NOT NULL COMMENT '固定基础资源编码：A、B',
    name VARCHAR(128) NOT NULL COMMENT '基础资源名称',
    sort_order INT UNSIGNED NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_base_resource_court_code (physical_court_id, code),
    CONSTRAINT fk_base_resource_physical_court FOREIGN KEY (physical_court_id) REFERENCES physical_court (id),
    CHECK (code IN ('A', 'B')),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物理场地基础资源';

CREATE TABLE sellable_unit (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    physical_court_id BIGINT UNSIGNED NOT NULL COMMENT '物理场地ID',
    rental_type_id BIGINT UNSIGNED NOT NULL COMMENT '出租类型ID',
    name VARCHAR(128) NOT NULL COMMENT '可售单元名称',
    sort_order INT UNSIGNED NOT NULL DEFAULT '0' COMMENT '排序值，越小越靠前',
    status VARCHAR(16) NOT NULL COMMENT '状态：ACTIVE、DISABLED',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sellable_unit_court_name (physical_court_id, name),
    KEY idx_sellable_unit_court_status_sort (physical_court_id, status, sort_order, id),
    CONSTRAINT fk_sellable_unit_physical_court FOREIGN KEY (physical_court_id) REFERENCES physical_court (id),
    CONSTRAINT fk_sellable_unit_rental_type FOREIGN KEY (rental_type_id) REFERENCES rental_type (id),
    CHECK (status IN ('ACTIVE', 'DISABLED')),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可售单元';

CREATE TABLE sellable_unit_version (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    sellable_unit_id BIGINT UNSIGNED NOT NULL COMMENT '可售单元ID',
    version_no INT UNSIGNED NOT NULL COMMENT '版本号，从1递增',
    version_status VARCHAR(16) NOT NULL COMMENT '版本状态：CURRENT、RETIRED',
    current_sellable_unit_id BIGINT UNSIGNED GENERATED ALWAYS AS (
        CASE WHEN version_status = 'CURRENT' AND is_deleted = 0 THEN sellable_unit_id ELSE NULL END
    ) STORED COMMENT '当前版本可售单元ID',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sellable_unit_version_no (sellable_unit_id, version_no),
    UNIQUE KEY uk_sellable_unit_version_current (current_sellable_unit_id),
    KEY idx_sellable_unit_version_status (sellable_unit_id, version_status, id),
    CONSTRAINT fk_sellable_unit_version_unit FOREIGN KEY (sellable_unit_id) REFERENCES sellable_unit (id),
    CHECK (version_status IN ('CURRENT', 'RETIRED')),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可售单元资源组合版本';

CREATE TABLE sellable_unit_version_resource (
    sellable_unit_version_id BIGINT UNSIGNED NOT NULL COMMENT '可售单元版本ID',
    base_resource_id BIGINT UNSIGNED NOT NULL COMMENT '基础资源ID',
    sort_order INT UNSIGNED NOT NULL DEFAULT '0' COMMENT '资源集合排序值',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (sellable_unit_version_id, base_resource_id),
    KEY idx_sellable_unit_version_resource_base (base_resource_id),
    CONSTRAINT fk_sellable_unit_version_resource_version FOREIGN KEY (sellable_unit_version_id) REFERENCES sellable_unit_version (id),
    CONSTRAINT fk_sellable_unit_version_resource_base FOREIGN KEY (base_resource_id) REFERENCES base_resource (id),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可售单元版本基础资源关联';
