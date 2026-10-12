CREATE TABLE s_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    author_type VARCHAR(20) NOT NULL,
    author_id BIGINT NOT NULL,
    current_revision_id BIGINT NULL,
    public_revision_id BIGINT NULL,
    is_official BOOLEAN NOT NULL DEFAULT FALSE,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    pinned_slot TINYINT NULL,
    comments_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    content_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_post_pinned_slot (pinned_slot),
    INDEX idx_post_public (content_status, created_at, id),
    INDEX idx_post_author (author_type, author_id, created_at),
    CONSTRAINT ck_post_author_type CHECK (author_type IN ('USER', 'VENUE', 'PLATFORM')),
    CONSTRAINT ck_post_pin CHECK ((is_pinned = FALSE AND pinned_slot IS NULL)
        OR (is_pinned = TRUE AND pinned_slot = 1)),
    CONSTRAINT ck_post_status CHECK (content_status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE TABLE s_post_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    sport_type_id BIGINT NOT NULL,
    venue_id BIGINT NULL,
    booking_id BIGINT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_revision_no (post_id, revision_no),
    UNIQUE KEY uk_post_revision_owner (id, post_id),
    INDEX idx_post_revision_feed (sport_type_id, venue_id, created_at),
    CONSTRAINT fk_post_revision_post FOREIGN KEY (post_id) REFERENCES s_post (id),
    CONSTRAINT fk_post_revision_sport FOREIGN KEY (sport_type_id) REFERENCES s_sport_type (id),
    CONSTRAINT fk_post_revision_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id),
    CONSTRAINT fk_post_revision_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT ck_post_revision_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

ALTER TABLE s_post
    ADD CONSTRAINT fk_post_current_revision FOREIGN KEY (current_revision_id, id)
        REFERENCES s_post_revision (id, post_id),
    ADD CONSTRAINT fk_post_public_revision FOREIGN KEY (public_revision_id, id)
        REFERENCES s_post_revision (id, post_id);

CREATE TABLE s_post_media (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    revision_id BIGINT NOT NULL,
    position TINYINT NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    storage_provider VARCHAR(20) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    duration_seconds INT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    cleanup_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_media_position (revision_id, position),
    UNIQUE KEY uk_post_media_object (storage_provider, object_key),
    CONSTRAINT fk_post_media_revision FOREIGN KEY (revision_id) REFERENCES s_post_revision (id),
    CONSTRAINT ck_post_media_position CHECK (position BETWEEN 1 AND 3),
    CONSTRAINT ck_post_media_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_post_media_size CHECK (size_bytes > 0 AND size_bytes <= 31457280),
    CONSTRAINT ck_post_media_duration CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 1 AND 20),
    CONSTRAINT ck_post_media_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_post_media_cleanup CHECK (cleanup_status IN ('ACTIVE', 'PENDING', 'PURGED'))
);

CREATE TABLE s_post_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,
    current_revision_id BIGINT NULL,
    public_revision_id BIGINT NULL,
    content_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    INDEX idx_post_comment_post (post_id, content_status, created_at, id),
    CONSTRAINT fk_post_comment_post FOREIGN KEY (post_id) REFERENCES s_post (id),
    CONSTRAINT fk_post_comment_author FOREIGN KEY (author_user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_post_comment_parent FOREIGN KEY (parent_comment_id) REFERENCES s_post_comment (id),
    CONSTRAINT ck_post_comment_status CHECK (content_status IN ('PENDING', 'PUBLISHED', 'HIDDEN', 'DELETED'))
);

CREATE TABLE s_post_comment_revision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    revision_no INT NOT NULL,
    body VARCHAR(1000) NOT NULL,
    moderation_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_comment_revision_no (comment_id, revision_no),
    UNIQUE KEY uk_post_comment_revision_owner (id, comment_id),
    CONSTRAINT fk_post_comment_revision_comment FOREIGN KEY (comment_id) REFERENCES s_post_comment (id),
    CONSTRAINT ck_post_comment_revision_moderation CHECK (moderation_status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

ALTER TABLE s_post_comment
    ADD CONSTRAINT fk_post_comment_current_revision FOREIGN KEY (current_revision_id, id)
        REFERENCES s_post_comment_revision (id, comment_id),
    ADD CONSTRAINT fk_post_comment_public_revision FOREIGN KEY (public_revision_id, id)
        REFERENCES s_post_comment_revision (id, comment_id);

CREATE TABLE s_post_like (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES s_post (id),
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES s_user_account (id)
);

CREATE TABLE s_post_recruitment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL,
    level VARCHAR(40) NOT NULL,
    max_members TINYINT NOT NULL,
    fee_mode VARCHAR(10) NOT NULL,
    aa_amount_fen INT NOT NULL DEFAULT 0,
    description VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'RECRUITING',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_post_recruitment_post (post_id),
    UNIQUE KEY uk_post_recruitment_booking (booking_id),
    CONSTRAINT fk_post_recruitment_post FOREIGN KEY (post_id) REFERENCES s_post (id),
    CONSTRAINT fk_post_recruitment_booking FOREIGN KEY (booking_id) REFERENCES s_booking (id),
    CONSTRAINT ck_post_recruitment_members CHECK (max_members BETWEEN 1 AND 20),
    CONSTRAINT ck_post_recruitment_fee CHECK ((fee_mode = 'FREE' AND aa_amount_fen = 0)
        OR (fee_mode = 'AA' AND aa_amount_fen >= 0)),
    CONSTRAINT ck_post_recruitment_fee_mode CHECK (fee_mode IN ('FREE', 'AA')),
    CONSTRAINT ck_post_recruitment_status CHECK (status IN ('RECRUITING', 'FULL', 'CLOSED', 'BOOKING_CANCELLED', 'ENDED'))
);

CREATE TABLE s_post_recruitment_member (
    recruitment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'APPLIED',
    applied_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    decided_at DATETIME(3) NULL,
    PRIMARY KEY (recruitment_id, user_id),
    CONSTRAINT fk_post_recruitment_member_recruitment FOREIGN KEY (recruitment_id) REFERENCES s_post_recruitment (id),
    CONSTRAINT fk_post_recruitment_member_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT ck_post_recruitment_member_status CHECK (status IN ('APPLIED', 'APPROVED', 'REJECTED', 'LEFT', 'REMOVED'))
);

CREATE TABLE s_user_block (
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (blocker_user_id, blocked_user_id),
    CONSTRAINT fk_user_block_blocker FOREIGN KEY (blocker_user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_user_block_blocked FOREIGN KEY (blocked_user_id) REFERENCES s_user_account (id),
    CONSTRAINT ck_user_block_self CHECK (blocker_user_id <> blocked_user_id)
);
