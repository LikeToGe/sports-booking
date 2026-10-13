-- 标签关联与回访均限定在同一场馆，并保存可区分后台账号/员工的审计身份。
ALTER TABLE s_venue_customer
    ADD UNIQUE KEY uk_venue_customer_venue_id (venue_id, id);

CREATE TABLE s_venue_customer_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    created_by_admin_id BIGINT NULL,
    created_by_employee_id BIGINT NULL,
    created_by_name VARCHAR(80) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_venue_customer_tag_name (venue_id, name),
    UNIQUE KEY uk_venue_customer_tag_venue_id (venue_id, id),
    CONSTRAINT fk_customer_tag_venue FOREIGN KEY (venue_id) REFERENCES s_venue(id),
    CONSTRAINT fk_customer_tag_admin FOREIGN KEY (created_by_admin_id) REFERENCES s_admin_account(id),
    CONSTRAINT fk_customer_tag_employee FOREIGN KEY (created_by_employee_id) REFERENCES s_employee(id),
    CONSTRAINT ck_customer_tag_creator CHECK ((created_by_admin_id IS NULL) <> (created_by_employee_id IS NULL))
);

CREATE TABLE s_venue_customer_tag_relation (
    venue_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    created_by_admin_id BIGINT NULL,
    created_by_employee_id BIGINT NULL,
    created_by_name VARCHAR(80) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (customer_id, tag_id),
    UNIQUE KEY uk_customer_tag_relation_scope (venue_id, customer_id, tag_id),
    INDEX idx_customer_tag_relation_venue (venue_id, customer_id),
    CONSTRAINT fk_customer_tag_relation_customer FOREIGN KEY (venue_id, customer_id)
        REFERENCES s_venue_customer(venue_id, id),
    CONSTRAINT fk_customer_tag_relation_tag FOREIGN KEY (venue_id, tag_id)
        REFERENCES s_venue_customer_tag(venue_id, id),
    CONSTRAINT fk_customer_tag_relation_admin FOREIGN KEY (created_by_admin_id) REFERENCES s_admin_account(id),
    CONSTRAINT fk_customer_tag_relation_employee FOREIGN KEY (created_by_employee_id) REFERENCES s_employee(id),
    CONSTRAINT ck_customer_tag_relation_creator CHECK
        ((created_by_admin_id IS NULL) <> (created_by_employee_id IS NULL))
);

CREATE TABLE s_venue_customer_tag_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    customer_id BIGINT NULL,
    tag_id BIGINT NOT NULL,
    tag_name VARCHAR(40) NOT NULL,
    action VARCHAR(32) NOT NULL,
    actor_admin_id BIGINT NULL,
    actor_employee_id BIGINT NULL,
    actor_name VARCHAR(80) NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_customer_tag_audit (venue_id, tag_id, occurred_at),
    INDEX idx_customer_tag_audit_customer (venue_id, customer_id, occurred_at),
    CONSTRAINT fk_customer_tag_audit_venue FOREIGN KEY (venue_id) REFERENCES s_venue(id),
    CONSTRAINT fk_customer_tag_audit_customer FOREIGN KEY (venue_id, customer_id)
        REFERENCES s_venue_customer(venue_id, id),
    CONSTRAINT fk_customer_tag_audit_admin FOREIGN KEY (actor_admin_id) REFERENCES s_admin_account(id),
    CONSTRAINT fk_customer_tag_audit_employee FOREIGN KEY (actor_employee_id) REFERENCES s_employee(id),
    CONSTRAINT ck_customer_tag_audit_actor CHECK ((actor_admin_id IS NULL) <> (actor_employee_id IS NULL))
);

CREATE TABLE s_venue_customer_followup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    purpose VARCHAR(40) NOT NULL,
    source_group VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pending_customer_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status = 'PENDING' THEN customer_id ELSE NULL END) STORED,
    note VARCHAR(255) NULL,
    created_by_admin_id BIGINT NULL,
    created_by_employee_id BIGINT NULL,
    created_by_name VARCHAR(80) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    handled_by_admin_id BIGINT NULL,
    handled_by_employee_id BIGINT NULL,
    handled_by_name VARCHAR(80) NULL,
    handled_at DATETIME(3) NULL,
    UNIQUE KEY uk_customer_followup_pending (venue_id, pending_customer_id, purpose),
    INDEX idx_customer_followup_venue_status (venue_id, status, created_at),
    CONSTRAINT fk_customer_followup_venue FOREIGN KEY (venue_id) REFERENCES s_venue(id),
    CONSTRAINT fk_customer_followup_customer FOREIGN KEY (venue_id, customer_id)
        REFERENCES s_venue_customer(venue_id, id),
    CONSTRAINT fk_customer_followup_creator_admin FOREIGN KEY (created_by_admin_id) REFERENCES s_admin_account(id),
    CONSTRAINT fk_customer_followup_creator_employee FOREIGN KEY (created_by_employee_id) REFERENCES s_employee(id),
    CONSTRAINT fk_customer_followup_handler_admin FOREIGN KEY (handled_by_admin_id) REFERENCES s_admin_account(id),
    CONSTRAINT fk_customer_followup_handler_employee FOREIGN KEY (handled_by_employee_id) REFERENCES s_employee(id),
    CONSTRAINT ck_customer_followup_status CHECK (status IN ('PENDING','COMPLETED','NOT_NEEDED')),
    CONSTRAINT ck_customer_followup_creator CHECK ((created_by_admin_id IS NULL) <> (created_by_employee_id IS NULL)),
    CONSTRAINT ck_customer_followup_handler CHECK
        ((handled_at IS NULL AND handled_by_admin_id IS NULL AND handled_by_employee_id IS NULL AND handled_by_name IS NULL)
          OR (handled_at IS NOT NULL AND handled_by_name IS NOT NULL
              AND ((handled_by_admin_id IS NULL) <> (handled_by_employee_id IS NULL))))
);
