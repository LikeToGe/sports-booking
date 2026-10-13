ALTER TABLE s_notification_delivery
    ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 AFTER failure_reason,
    ADD COLUMN last_attempt_at DATETIME(3) NULL AFTER attempt_count;

ALTER TABLE s_notification
    ADD COLUMN delivery_claimed_at DATETIME(3) NULL AFTER status,
    ADD COLUMN delivery_next_attempt_at DATETIME(3) NULL AFTER delivery_claimed_at;

CREATE TABLE s_notification_delivery_attempt (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_notification_attempt (notification_id, id),
    CONSTRAINT fk_notification_attempt_notification FOREIGN KEY (notification_id) REFERENCES s_notification(id)
);

CREATE INDEX idx_booking_arrival_due ON s_booking (source, status, start_at, id);
