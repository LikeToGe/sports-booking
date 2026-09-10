CREATE TABLE s_venue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    cover_path VARCHAR(255) NULL,
    city VARCHAR(50) NOT NULL DEFAULT '郑州市',
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    phone VARCHAR(30) NOT NULL,
    facilities VARCHAR(500) NOT NULL DEFAULT '',
    entry_notice VARCHAR(1000) NOT NULL DEFAULT '',
    booking_days_ahead INT NOT NULL DEFAULT 7,
    min_booking_minutes INT NOT NULL DEFAULT 30,
    cancel_cutoff_minutes INT NOT NULL DEFAULT 120,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_venue_status (status)
);

CREATE TABLE s_admin_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NULL,
    role VARCHAR(20) NOT NULL,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_admin_username (username),
    CONSTRAINT fk_admin_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id)
);

CREATE TABLE s_user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    mock_openid VARCHAR(100) NOT NULL,
    contact_name VARCHAR(80) NULL,
    phone VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_mock_openid (mock_openid)
);

CREATE TABLE s_sport_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_venue_id BIGINT NULL,
    name VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(50) NOT NULL,
    scope_venue_id BIGINT AS (COALESCE(owner_venue_id, 0)) STORED,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_sport_scope_name (scope_venue_id, normalized_name),
    CONSTRAINT fk_sport_owner_venue FOREIGN KEY (owner_venue_id) REFERENCES s_venue (id)
);

CREATE TABLE s_court (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_court_venue_name (venue_id, name),
    CONSTRAINT fk_court_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id)
);

CREATE TABLE s_court_sport (
    court_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    PRIMARY KEY (court_id, sport_type_id),
    CONSTRAINT fk_court_sport_court FOREIGN KEY (court_id) REFERENCES s_court (id),
    CONSTRAINT fk_court_sport_type FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id)
);

CREATE TABLE s_weekly_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    court_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    price_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_weekly_slot_court_day (court_id, day_of_week, start_time),
    CONSTRAINT fk_weekly_slot_court FOREIGN KEY (court_id) REFERENCES s_court (id),
    CONSTRAINT fk_weekly_slot_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT chk_weekly_slot_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_weekly_slot_time CHECK (start_time < end_time),
    CONSTRAINT chk_weekly_slot_price CHECK (price_fen >= 0)
);

CREATE TABLE s_session_slot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    court_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    weekly_slot_id BIGINT NULL,
    slot_date DATE NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    price_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    hold_booking_id BIGINT NULL,
    hold_expires_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_session_court_time (court_id, start_at, end_at),
    INDEX idx_session_venue_date_status (venue_id, slot_date, status),
    CONSTRAINT fk_session_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_session_court FOREIGN KEY (court_id) REFERENCES s_court (id),
    CONSTRAINT fk_session_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT fk_session_weekly FOREIGN KEY (weekly_slot_id) REFERENCES s_weekly_slot (id)
);

CREATE TABLE s_booking (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_no VARCHAR(32) NOT NULL,
    user_id BIGINT NULL,
    venue_id BIGINT NOT NULL,
    court_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    session_slot_id BIGINT NOT NULL,
    source VARCHAR(20) NOT NULL,
    contact_name VARCHAR(80) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    venue_name VARCHAR(100) NOT NULL,
    court_name VARCHAR(80) NOT NULL,
    sport_name VARCHAR(50) NOT NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    amount_fen INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    payment_expires_at DATETIME NULL,
    cancelled_by VARCHAR(20) NULL,
    cancel_reason VARCHAR(255) NULL,
    cancelled_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_booking_no (booking_no),
    INDEX idx_booking_user_status (user_id, status, created_at),
    INDEX idx_booking_venue_time (venue_id, start_at, status),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_booking_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_booking_court FOREIGN KEY (court_id) REFERENCES s_court (id),
    CONSTRAINT fk_booking_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT fk_booking_session FOREIGN KEY (session_slot_id) REFERENCES s_session_slot (id)
);

ALTER TABLE s_session_slot
    ADD CONSTRAINT fk_session_hold_booking FOREIGN KEY (hold_booking_id) REFERENCES s_booking (id);

CREATE TABLE s_payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    provider VARCHAR(20) NOT NULL DEFAULT 'MOCK',
    provider_no VARCHAR(64) NULL,
    amount_fen INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_payment_booking (booking_id),
    UNIQUE KEY uk_payment_provider_no (provider_no),
    CONSTRAINT fk_payment_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id)
);

CREATE TABLE s_refund (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount_fen INT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    refunded_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_refund_booking (booking_id),
    CONSTRAINT fk_refund_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id) REFERENCES s_payment (id)
);

CREATE TABLE s_employee (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    binding_code_hash VARCHAR(64) NULL,
    binding_expires_at DATETIME NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_employee_user (user_id),
    CONSTRAINT fk_employee_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES s_user_account (id)
);

CREATE TABLE s_verification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    employee_id BIGINT NOT NULL,
    method VARCHAR(20) NOT NULL,
    verified_at DATETIME NOT NULL,
    UNIQUE KEY uk_verification_booking (booking_id),
    INDEX idx_verification_employee_time (employee_id, verified_at),
    CONSTRAINT fk_verification_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_verification_employee FOREIGN KEY (employee_id) REFERENCES s_employee (id)
);

CREATE TABLE s_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    type VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'MOCK_SENT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_notification_user_time (user_id, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_notification_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id)
);

INSERT INTO s_sport_type (owner_venue_id, name, normalized_name)
VALUES (NULL, '篮球', '篮球'),
       (NULL, '羽毛球', '羽毛球'),
       (NULL, '网球', '网球'),
       (NULL, '足球', '足球'),
       (NULL, '乒乓球', '乒乓球');
