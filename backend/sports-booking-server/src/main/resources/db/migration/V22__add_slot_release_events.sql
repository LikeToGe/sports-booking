CREATE TABLE s_slot_release_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_slot_id BIGINT NOT NULL,
    event_key VARCHAR(120) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    UNIQUE KEY uk_slot_release_event_key (event_key),
    INDEX idx_slot_release_unprocessed (processed_at, id),
    CONSTRAINT fk_slot_release_event_slot FOREIGN KEY (session_slot_id) REFERENCES s_session_slot (id)
);
