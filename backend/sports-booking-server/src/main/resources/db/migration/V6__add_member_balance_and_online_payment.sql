CREATE TABLE s_recharge_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    paid_amount_fen INT NOT NULL,
    gift_amount_fen INT NOT NULL DEFAULT 0,
    gift_valid_days INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_recharge_plan_venue_status (venue_id, status, id),
    CONSTRAINT ck_recharge_plan_paid CHECK (paid_amount_fen > 0),
    CONSTRAINT ck_recharge_plan_gift CHECK (gift_amount_fen >= 0),
    CONSTRAINT ck_recharge_plan_validity CHECK (
        (gift_amount_fen = 0 AND gift_valid_days IS NULL)
        OR (gift_amount_fen > 0 AND gift_valid_days > 0)
    ),
    CONSTRAINT fk_recharge_plan_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id)
);

CREATE TABLE s_member_recharge (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    recharge_no VARCHAR(40) NOT NULL,
    venue_customer_id BIGINT NOT NULL,
    plan_id BIGINT NULL,
    plan_name VARCHAR(80) NOT NULL,
    paid_amount_fen INT NOT NULL,
    gift_amount_fen INT NOT NULL DEFAULT 0,
    gift_valid_days INT NULL,
    channel VARCHAR(20) NOT NULL,
    operator_admin_id BIGINT NULL,
    client_idempotency_key VARCHAR(80) NULL,
    provider_no VARCHAR(64) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reverse_reason VARCHAR(255) NULL,
    offline_funds_handled TINYINT(1) NOT NULL DEFAULT 0,
    offline_funds_handled_at DATETIME(3) NULL,
    succeeded_at DATETIME(3) NULL,
    reversed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_member_recharge_no (recharge_no),
    UNIQUE KEY uk_member_recharge_client (venue_customer_id, client_idempotency_key),
    UNIQUE KEY uk_member_recharge_provider (provider_no),
    INDEX idx_member_recharge_customer_status (venue_customer_id, status, id),
    CONSTRAINT ck_member_recharge_paid CHECK (paid_amount_fen > 0),
    CONSTRAINT ck_member_recharge_gift CHECK (gift_amount_fen >= 0),
    CONSTRAINT ck_member_recharge_validity CHECK (
        (gift_amount_fen = 0 AND gift_valid_days IS NULL)
        OR (gift_amount_fen > 0 AND gift_valid_days > 0)
    ),
    CONSTRAINT fk_member_recharge_customer FOREIGN KEY (venue_customer_id) REFERENCES s_venue_customer (id),
    CONSTRAINT fk_member_recharge_plan FOREIGN KEY (plan_id) REFERENCES s_recharge_plan (id),
    CONSTRAINT fk_member_recharge_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id)
);

CREATE TABLE s_member_balance_lot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_customer_id BIGINT NOT NULL,
    source_recharge_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    original_amount_fen INT NOT NULL,
    available_amount_fen INT NOT NULL,
    frozen_amount_fen INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_balance_lot_recharge_type (source_recharge_id, type),
    INDEX idx_balance_lot_spend (venue_customer_id, status, type, expires_at, id),
    CONSTRAINT ck_balance_lot_original CHECK (original_amount_fen > 0),
    CONSTRAINT ck_balance_lot_amounts CHECK (
        available_amount_fen >= 0 AND frozen_amount_fen >= 0
        AND available_amount_fen + frozen_amount_fen <= original_amount_fen
    ),
    CONSTRAINT ck_balance_lot_expiry CHECK (
        (type = 'PAID' AND expires_at IS NULL) OR (type = 'GIFT' AND expires_at IS NOT NULL)
    ),
    CONSTRAINT fk_balance_lot_customer FOREIGN KEY (venue_customer_id) REFERENCES s_venue_customer (id),
    CONSTRAINT fk_balance_lot_recharge FOREIGN KEY (source_recharge_id) REFERENCES s_member_recharge (id)
);

CREATE TABLE s_booking_balance_hold (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    lot_id BIGINT NOT NULL,
    amount_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FROZEN',
    frozen_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    released_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_booking_balance_hold (booking_id, lot_id),
    INDEX idx_booking_balance_hold_lot (lot_id, status, id),
    CONSTRAINT ck_booking_balance_hold_amount CHECK (amount_fen > 0),
    CONSTRAINT fk_booking_balance_hold_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_booking_balance_hold_lot FOREIGN KEY (lot_id) REFERENCES s_member_balance_lot (id)
);

CREATE TABLE s_member_balance_flow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_customer_id BIGINT NOT NULL,
    lot_id BIGINT NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount_fen INT NOT NULL,
    business_type VARCHAR(30) NOT NULL,
    booking_id BIGINT NULL,
    recharge_id BIGINT NULL,
    operator_admin_id BIGINT NULL,
    idempotency_key VARCHAR(120) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_member_balance_flow_idempotency (idempotency_key),
    INDEX idx_member_balance_flow_customer (venue_customer_id, id),
    INDEX idx_member_balance_flow_booking (booking_id, id),
    CONSTRAINT ck_member_balance_flow_amount CHECK (amount_fen > 0),
    CONSTRAINT fk_member_balance_flow_customer FOREIGN KEY (venue_customer_id) REFERENCES s_venue_customer (id),
    CONSTRAINT fk_member_balance_flow_lot FOREIGN KEY (lot_id) REFERENCES s_member_balance_lot (id),
    CONSTRAINT fk_member_balance_flow_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_member_balance_flow_recharge FOREIGN KEY (recharge_id) REFERENCES s_member_recharge (id),
    CONSTRAINT fk_member_balance_flow_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id)
);

ALTER TABLE s_booking
    ADD COLUMN balance_amount_fen INT NOT NULL DEFAULT 0 AFTER amount_fen,
    ADD COLUMN gift_balance_amount_fen INT NOT NULL DEFAULT 0 AFTER balance_amount_fen,
    ADD COLUMN paid_balance_amount_fen INT NOT NULL DEFAULT 0 AFTER gift_balance_amount_fen,
    ADD COLUMN external_amount_fen INT NOT NULL DEFAULT 0 AFTER paid_balance_amount_fen,
    ADD COLUMN client_idempotency_key VARCHAR(80) NULL AFTER payment_expires_at,
    ADD UNIQUE KEY uk_booking_user_client (user_id, client_idempotency_key);

UPDATE s_booking SET external_amount_fen = amount_fen WHERE source = 'ONLINE';

UPDATE s_booking b
JOIN s_payment p ON p.booking_id = b.id
SET b.payment_status = 'PAYING'
WHERE b.source = 'ONLINE' AND b.status = 'PENDING_PAYMENT' AND p.status = 'PENDING';

ALTER TABLE s_booking
    ADD CONSTRAINT ck_booking_payment_composition CHECK (
        (source = 'OFFLINE' AND balance_amount_fen = 0 AND gift_balance_amount_fen = 0
            AND paid_balance_amount_fen = 0 AND external_amount_fen = 0)
        OR
        (source = 'ONLINE' AND balance_amount_fen >= 0 AND gift_balance_amount_fen >= 0
            AND paid_balance_amount_fen >= 0 AND external_amount_fen >= 0
            AND balance_amount_fen = gift_balance_amount_fen + paid_balance_amount_fen
            AND amount_fen = balance_amount_fen + external_amount_fen)
    );

ALTER TABLE s_payment
    ADD COLUMN payment_no VARCHAR(40) NULL AFTER booking_id,
    ADD COLUMN closed_at DATETIME(3) NULL AFTER paid_at,
    ADD COLUMN last_query_at DATETIME(3) NULL AFTER closed_at,
    ADD UNIQUE KEY uk_payment_no (payment_no);

UPDATE s_payment SET payment_no = CONCAT('PAY-', LPAD(id, 24, '0')) WHERE payment_no IS NULL;

UPDATE s_payment SET status = 'SUCCESS' WHERE status = 'REFUNDED';

ALTER TABLE s_payment
    MODIFY COLUMN payment_no VARCHAR(40) NOT NULL,
    ADD CONSTRAINT ck_payment_amount CHECK (amount_fen > 0);

ALTER TABLE s_refund
    CHANGE COLUMN status status VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    ADD COLUMN refund_no VARCHAR(40) NULL AFTER payment_id,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 AFTER status,
    ADD COLUMN next_retry_at DATETIME(3) NULL AFTER retry_count,
    ADD COLUMN last_failure_reason VARCHAR(255) NULL AFTER next_retry_at,
    ADD UNIQUE KEY uk_refund_payment (payment_id),
    ADD UNIQUE KEY uk_refund_no (refund_no);

UPDATE s_refund
SET refund_no = CONCAT('REF-', LPAD(id, 24, '0')),
    status = CASE WHEN status = 'PENDING' THEN 'PROCESSING' ELSE status END
WHERE refund_no IS NULL;

ALTER TABLE s_refund
    MODIFY COLUMN refund_no VARCHAR(40) NOT NULL,
    ADD CONSTRAINT ck_refund_amount CHECK (amount_fen > 0),
    ADD CONSTRAINT ck_refund_retry CHECK (retry_count >= 0);

ALTER TABLE s_notification
    MODIFY COLUMN booking_id BIGINT NULL,
    ADD COLUMN business_type VARCHAR(30) NOT NULL DEFAULT 'BOOKING' AFTER booking_id,
    ADD COLUMN business_id BIGINT NULL AFTER business_type,
    ADD COLUMN title VARCHAR(100) NOT NULL DEFAULT '' AFTER type,
    ADD COLUMN content VARCHAR(500) NOT NULL DEFAULT '' AFTER title,
    ADD COLUMN read_at DATETIME(3) NULL AFTER content,
    ADD COLUMN idempotency_key VARCHAR(120) NULL AFTER read_at,
    ADD UNIQUE KEY uk_notification_idempotency (idempotency_key);

UPDATE s_notification SET business_id = booking_id WHERE business_id IS NULL;

CREATE TABLE s_notification_delivery (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_no VARCHAR(64) NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_notification_delivery (notification_id, channel),
    CONSTRAINT fk_notification_delivery_notification FOREIGN KEY (notification_id) REFERENCES s_notification (id)
);
