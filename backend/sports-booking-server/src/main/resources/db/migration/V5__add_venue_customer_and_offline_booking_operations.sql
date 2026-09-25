CREATE TABLE s_venue_customer (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',
    member_code VARCHAR(16) NOT NULL,
    activated_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_venue_customer_user (venue_id, user_id),
    UNIQUE KEY uk_venue_customer_code (venue_id, member_code),
    INDEX idx_venue_customer_status (venue_id, status, created_at),
    CONSTRAINT fk_venue_customer_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_venue_customer_user FOREIGN KEY (user_id) REFERENCES s_user_account (id)
);

ALTER TABLE s_user_account
    ADD INDEX idx_user_phone (phone);

ALTER TABLE s_booking
    ADD COLUMN venue_customer_id BIGINT NULL AFTER user_id,
    ADD COLUMN payment_status VARCHAR(30) NULL AFTER status,
    ADD INDEX idx_booking_venue_payment_status (venue_id, payment_status, start_at);

INSERT INTO s_venue_customer (venue_id, user_id, status, member_code, created_at, updated_at)
SELECT DISTINCT b.venue_id,
       b.user_id,
       'CUSTOMER',
       UPPER(SUBSTRING(SHA2(CONCAT('sports-booking-member:', b.venue_id, ':', b.user_id), 256), 1, 12)),
       UTC_TIMESTAMP(3),
       UTC_TIMESTAMP(3)
FROM s_booking b
WHERE b.source = 'ONLINE' AND b.user_id IS NOT NULL;

UPDATE s_booking b
JOIN s_venue_customer vc ON vc.venue_id = b.venue_id AND vc.user_id = b.user_id
SET b.venue_customer_id = vc.id
WHERE b.source = 'ONLINE';

UPDATE s_booking b
LEFT JOIN s_payment p ON p.booking_id = b.id
LEFT JOIN s_refund r ON r.booking_id = b.id
SET b.payment_status = CASE
    WHEN b.source = 'OFFLINE' THEN 'UNRECORDED'
    WHEN r.status = 'SUCCESS' OR p.status = 'REFUNDED' THEN 'REFUNDED'
    WHEN p.status = 'SUCCESS' THEN 'PAID'
    ELSE 'UNPAID'
END;

ALTER TABLE s_booking
    MODIFY COLUMN payment_status VARCHAR(30) NOT NULL,
    ADD CONSTRAINT fk_booking_venue_customer
        FOREIGN KEY (venue_customer_id) REFERENCES s_venue_customer (id);

CREATE TABLE s_booking_payment_status_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    before_status VARCHAR(30) NOT NULL,
    after_status VARCHAR(30) NOT NULL,
    operator_admin_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_booking_payment_status_log (booking_id, created_at),
    CONSTRAINT fk_booking_payment_status_log_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_booking_payment_status_log_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id)
);

CREATE TABLE s_booking_reschedule_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    original_session_slot_id BIGINT NOT NULL,
    new_session_slot_id BIGINT NOT NULL,
    operator_admin_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_booking_reschedule_log (booking_id, created_at),
    CONSTRAINT fk_booking_reschedule_log_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_booking_reschedule_log_original_slot
        FOREIGN KEY (original_session_slot_id) REFERENCES s_session_slot (id),
    CONSTRAINT fk_booking_reschedule_log_new_slot
        FOREIGN KEY (new_session_slot_id) REFERENCES s_session_slot (id),
    CONSTRAINT fk_booking_reschedule_log_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id)
);
