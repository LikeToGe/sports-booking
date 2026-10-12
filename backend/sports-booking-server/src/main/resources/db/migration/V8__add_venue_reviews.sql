CREATE TABLE s_review_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(40) NOT NULL,
    normalized_name VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_tag_name (normalized_name),
    CONSTRAINT ck_review_tag_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE s_review_tag_sport (
    tag_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    PRIMARY KEY (tag_id, sport_type_id),
    CONSTRAINT fk_review_tag_sport_tag FOREIGN KEY (tag_id) REFERENCES s_review_tag (id),
    CONSTRAINT fk_review_tag_sport_type FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id)
);

CREATE TABLE s_venue_review (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    sport_type_id BIGINT NOT NULL,
    current_revision_id BIGINT NULL,
    public_revision_id BIGINT NULL,
    content_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_venue_review_booking (booking_id),
    INDEX idx_venue_review_public (venue_id, content_status, created_at),
    INDEX idx_venue_review_user (user_id, created_at),
    CONSTRAINT fk_venue_review_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT fk_venue_review_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_venue_review_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_venue_review_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT ck_venue_review_status CHECK (content_status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE TABLE s_venue_review_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    overall_score TINYINT NOT NULL,
    court_score TINYINT NOT NULL,
    environment_score TINYINT NOT NULL,
    service_score TINYINT NOT NULL,
    anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_revision_no (review_id, revision_no),
    UNIQUE KEY uk_review_revision_owner (id, review_id),
    CONSTRAINT fk_review_revision_review FOREIGN KEY (review_id) REFERENCES s_venue_review (id),
    CONSTRAINT ck_review_revision_scores CHECK (
        overall_score BETWEEN 1 AND 5 AND court_score BETWEEN 1 AND 5
        AND environment_score BETWEEN 1 AND 5 AND service_score BETWEEN 1 AND 5
    ),
    CONSTRAINT ck_review_revision_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

ALTER TABLE s_venue_review
    ADD CONSTRAINT fk_review_current_revision FOREIGN KEY (current_revision_id, id)
        REFERENCES s_venue_review_revision (id, review_id),
    ADD CONSTRAINT fk_review_public_revision FOREIGN KEY (public_revision_id, id)
        REFERENCES s_venue_review_revision (id, review_id);

CREATE TABLE s_venue_review_tag_rel (
    revision_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    position TINYINT NOT NULL,
    PRIMARY KEY (revision_id, tag_id),
    UNIQUE KEY uk_review_tag_position (revision_id, position),
    CONSTRAINT fk_review_tag_rel_revision FOREIGN KEY (revision_id) REFERENCES s_venue_review_revision (id),
    CONSTRAINT fk_review_tag_rel_tag FOREIGN KEY (tag_id) REFERENCES s_review_tag (id),
    CONSTRAINT ck_review_tag_position CHECK (position BETWEEN 1 AND 3)
);

CREATE TABLE s_venue_review_media (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    position TINYINT NOT NULL,
    storage_provider VARCHAR(20) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cleanup_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_media_position (revision_id, position),
    UNIQUE KEY uk_review_media_object (storage_provider, object_key),
    CONSTRAINT fk_review_media_revision FOREIGN KEY (revision_id) REFERENCES s_venue_review_revision (id),
    CONSTRAINT ck_review_media_position CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT ck_review_media_size CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT ck_review_media_mime CHECK (mime_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT ck_review_media_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_review_media_cleanup CHECK (cleanup_status IN ('ACTIVE', 'PENDING', 'PURGED'))
);

CREATE TABLE s_venue_review_reply (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    review_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    current_revision_id BIGINT NULL,
    public_revision_id BIGINT NULL,
    content_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_reply_review (review_id),
    CONSTRAINT fk_review_reply_review FOREIGN KEY (review_id) REFERENCES s_venue_review (id),
    CONSTRAINT fk_review_reply_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT ck_review_reply_status CHECK (content_status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE TABLE s_venue_review_reply_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reply_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    body VARCHAR(1000) NOT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_review_reply_revision_no (reply_id, revision_no),
    UNIQUE KEY uk_review_reply_revision_owner (id, reply_id),
    CONSTRAINT fk_review_reply_revision_reply FOREIGN KEY (reply_id) REFERENCES s_venue_review_reply (id),
    CONSTRAINT ck_review_reply_revision_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

ALTER TABLE s_venue_review_reply
    ADD CONSTRAINT fk_review_reply_current_revision FOREIGN KEY (current_revision_id, id)
        REFERENCES s_venue_review_reply_revision (id, reply_id),
    ADD CONSTRAINT fk_review_reply_public_revision FOREIGN KEY (public_revision_id, id)
        REFERENCES s_venue_review_reply_revision (id, reply_id);

CREATE TABLE s_content_moderation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_type VARCHAR(20) NOT NULL,
    content_id BIGINT NOT NULL,
    revision_id BIGINT NULL,
    action VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_admin_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_moderation_content (content_type, content_id, created_at),
    CONSTRAINT fk_moderation_operator FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    CONSTRAINT ck_moderation_content_type CHECK (content_type IN ('REVIEW', 'REVIEW_REPLY', 'POST', 'COMMENT')),
    CONSTRAINT ck_moderation_action CHECK (action IN ('APPROVE', 'REJECT', 'HIDE', 'RESTORE', 'DELETE'))
);
