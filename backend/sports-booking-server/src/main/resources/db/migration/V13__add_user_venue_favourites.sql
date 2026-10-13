CREATE TABLE s_user_venue_favourite (
    user_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, venue_id),
    CONSTRAINT fk_favourite_user FOREIGN KEY (user_id) REFERENCES s_user_account(id),
    CONSTRAINT fk_favourite_venue FOREIGN KEY (venue_id) REFERENCES s_venue(id)
);
