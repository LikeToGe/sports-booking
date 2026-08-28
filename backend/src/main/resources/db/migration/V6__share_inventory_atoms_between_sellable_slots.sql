CREATE TABLE inventory_slot_atom (
    inventory_slot_id BIGINT UNSIGNED NOT NULL COMMENT '库存时段ID',
    inventory_atom_id BIGINT UNSIGNED NOT NULL COMMENT '库存原子ID',
    create_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '创建用户ID',
    modify_operator_id BIGINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '修改用户ID',
    gmt_created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    gmt_modified_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT UNSIGNED NOT NULL DEFAULT '0' COMMENT '是否删除：0未删除 1已删除',
    PRIMARY KEY (inventory_slot_id, inventory_atom_id),
    KEY idx_inventory_slot_atom_atom (inventory_atom_id),
    CONSTRAINT fk_inventory_slot_atom_slot FOREIGN KEY (inventory_slot_id) REFERENCES inventory_slot (id),
    CONSTRAINT fk_inventory_slot_atom_atom FOREIGN KEY (inventory_atom_id) REFERENCES inventory_atom (id),
    CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可售库存时段与基础资源原子关联';

INSERT INTO inventory_slot_atom (inventory_slot_id, inventory_atom_id, create_operator_id, modify_operator_id)
SELECT inventory_slot_id, id, create_operator_id, modify_operator_id
FROM inventory_atom;

ALTER TABLE inventory_atom
    DROP FOREIGN KEY fk_inventory_atom_inventory_slot,
    DROP INDEX idx_inventory_atom_slot,
    DROP COLUMN inventory_slot_id;
