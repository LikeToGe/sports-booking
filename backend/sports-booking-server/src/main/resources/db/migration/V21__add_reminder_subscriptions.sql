CREATE TABLE s_reminder_subscription (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    target_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_status VARCHAR(20) AS (CASE WHEN status = 'ACTIVE' THEN 'ACTIVE' ELSE NULL END) STORED,
    had_open_at_registration TINYINT NOT NULL DEFAULT 0,
    opening_notified_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_reminder_subscription (user_id, venue_id, sport_type_id, target_date, start_time, end_time, type, active_status),
    INDEX idx_reminder_subscription_target (venue_id, sport_type_id, target_date, status),
    INDEX idx_reminder_opening_due (status, type, opening_notified_at, target_date),
    CONSTRAINT fk_reminder_subscription_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_reminder_subscription_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_reminder_subscription_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT chk_reminder_subscription_time CHECK (start_time < end_time),
    CONSTRAINT chk_reminder_subscription_type CHECK (type IN ('OPENING', 'RELEASED')),
    CONSTRAINT chk_reminder_subscription_status CHECK (status IN ('ACTIVE', 'CANCELLED', 'EXPIRED'))
);

ALTER TABLE s_notification
    ADD COLUMN subscription_id BIGINT NULL AFTER business_id,
    ADD CONSTRAINT fk_notification_subscription FOREIGN KEY (subscription_id) REFERENCES s_reminder_subscription(id);
