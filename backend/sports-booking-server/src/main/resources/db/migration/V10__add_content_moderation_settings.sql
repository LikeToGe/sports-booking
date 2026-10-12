CREATE TABLE s_content_moderation_setting (
    id TINYINT PRIMARY KEY,
    post_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    comment_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    review_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_content_moderation_setting_singleton CHECK (id = 1)
);

INSERT INTO s_content_moderation_setting (id, post_enabled, comment_enabled, review_enabled)
VALUES (1, TRUE, TRUE, TRUE);

ALTER TABLE s_post
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_post_media
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_venue_review_media
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE s_content_agreement_setting (
    id TINYINT PRIMARY KEY,
    rules_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    rules_text VARCHAR(3000) NOT NULL,
    privacy_text VARCHAR(3000) NOT NULL,
    force_reaccept BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT ck_content_agreement_singleton CHECK (id = 1)
);

INSERT INTO s_content_agreement_setting
    (id, rules_version, privacy_version, rules_text, privacy_text, force_reaccept)
VALUES (1, '1', '1',
    '本地演示社区规则：仅发布运动相关内容；尊重他人，不公开联系方式、预约码或他人隐私；违规内容可能被隐藏或删除。',
    '本地演示隐私说明：评价、动态和互动用于本地内容展示与审核；预约联系人和支付数据不在社区页面公开。', TRUE);

CREATE TABLE s_content_agreement_acceptance (
    user_id BIGINT NOT NULL,
    rules_version VARCHAR(40) NOT NULL,
    privacy_version VARCHAR(40) NOT NULL,
    accepted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (user_id, rules_version, privacy_version),
    CONSTRAINT fk_content_acceptance_user FOREIGN KEY (user_id) REFERENCES s_user_account (id)
);

CREATE TABLE s_content_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reporter_user_id BIGINT NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    reason_code VARCHAR(24) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    resolution VARCHAR(255) NULL,
    operator_admin_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    pending_target_key VARCHAR(100) AS (
        CASE WHEN status = 'PENDING' THEN CONCAT(reporter_user_id, ':', target_type, ':', target_id) ELSE NULL END
    ) STORED,
    UNIQUE KEY uk_content_report_pending (pending_target_key),
    INDEX idx_content_report_status (status, created_at),
    INDEX idx_content_report_target (target_type, target_id, created_at),
    CONSTRAINT fk_content_report_user FOREIGN KEY (reporter_user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_content_report_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    CONSTRAINT ck_content_report_target CHECK (target_type IN ('REVIEW', 'REVIEW_REPLY', 'POST', 'COMMENT', 'USER')),
    CONSTRAINT ck_content_report_reason CHECK (reason_code IN ('SPAM', 'ABUSE', 'PRIVACY', 'MISLEADING', 'OTHER')),
    CONSTRAINT ck_content_report_status CHECK (status IN ('PENDING', 'RESOLVED', 'REJECTED'))
);

CREATE TABLE s_content_appeal (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    moderation_log_id BIGINT NOT NULL,
    appellant_user_id BIGINT NOT NULL,
    body VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    decision_reason VARCHAR(255) NULL,
    operator_admin_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    decided_at DATETIME(3) NULL,
    UNIQUE KEY uk_content_appeal_decision (moderation_log_id),
    INDEX idx_content_appeal_status (status, created_at),
    CONSTRAINT fk_content_appeal_log FOREIGN KEY (moderation_log_id) REFERENCES s_content_moderation_log (id),
    CONSTRAINT fk_content_appeal_user FOREIGN KEY (appellant_user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_content_appeal_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    CONSTRAINT ck_content_appeal_status CHECK (status IN ('PENDING', 'UPHELD', 'REVERSED'))
);

CREATE TABLE s_user_content_restriction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    capability VARCHAR(20) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    operator_admin_id BIGINT NOT NULL,
    starts_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    INDEX idx_content_restriction_active (user_id, capability, revoked_at, expires_at),
    CONSTRAINT fk_content_restriction_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT fk_content_restriction_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    CONSTRAINT ck_content_restriction_capability CHECK (capability IN ('POST', 'COMMENT', 'RECRUITMENT')),
    CONSTRAINT ck_content_restriction_period CHECK (expires_at IS NULL OR expires_at > starts_at)
);

CREATE TABLE s_user_action_event (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    target_type VARCHAR(20) NULL,
    target_id BIGINT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_user_action_type_time (event_type, occurred_at),
    INDEX idx_user_action_user_time (user_id, occurred_at),
    CONSTRAINT fk_user_action_user FOREIGN KEY (user_id) REFERENCES s_user_account (id),
    CONSTRAINT ck_user_action_event CHECK (event_type IN (
        'VENUE_TO_BOOKING', 'POST_TO_VENUE', 'POST_TO_BOOKING', 'SHARE_OPEN',
        'RECRUITMENT_APPLY', 'RECRUITMENT_FILLED', 'REPORT_SUBMIT', 'REPORT_RESOLVE'))
);

CREATE TABLE s_user_identity_tombstone (
    identity_hash CHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_user_tombstone_user (user_id),
    CONSTRAINT fk_user_tombstone_user FOREIGN KEY (user_id) REFERENCES s_user_account (id)
);

ALTER TABLE s_user_account
    ADD COLUMN deleted_at DATETIME(3) NULL,
    ADD COLUMN nickname VARCHAR(40) NOT NULL DEFAULT '球友',
    ADD COLUMN avatar_url VARCHAR(255) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_content_moderation_log
    DROP CHECK ck_moderation_content_type,
    DROP CHECK ck_moderation_action,
    ADD CONSTRAINT ck_moderation_content_type CHECK (content_type IN ('REVIEW','REVIEW_REPLY','POST','COMMENT','USER')),
    ADD CONSTRAINT ck_moderation_action CHECK (action IN ('APPROVE','REJECT','HIDE','RESTORE','DELETE','RESTRICT'));

ALTER TABLE s_venue_review
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_venue_review_reply
    ADD COLUMN deleted_at DATETIME(3) NULL,
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_post_comment
    ADD COLUMN purge_after DATETIME(3) NULL,
    ADD COLUMN legal_hold BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE s_post_recruitment_member ADD COLUMN reason VARCHAR(255) NULL;

CREATE TABLE s_ugc_upload (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    media_type VARCHAR(10) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    mime_type VARCHAR(80) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    duration_seconds INT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'STAGED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_ugc_upload_object (object_key),
    INDEX idx_ugc_upload_owner (owner_user_id, status, created_at),
    INDEX idx_ugc_upload_expiry (status, expires_at),
    CONSTRAINT fk_ugc_upload_user FOREIGN KEY (owner_user_id) REFERENCES s_user_account (id),
    CONSTRAINT ck_ugc_upload_type CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT ck_ugc_upload_status CHECK (status IN ('STAGED', 'ATTACHED', 'PURGED'))
);

CREATE TABLE s_content_detection_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    content_type VARCHAR(10) NOT NULL,
    revision_id BIGINT NOT NULL,
    status VARCHAR(12) NOT NULL DEFAULT 'QUEUED',
    auto_publish BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    completed_at DATETIME(3) NULL,
    UNIQUE KEY uk_content_detection_revision (content_type, revision_id),
    INDEX idx_content_detection_pending (status, created_at),
    CONSTRAINT ck_content_detection_type CHECK (content_type IN ('POST', 'REVIEW')),
    CONSTRAINT ck_content_detection_status CHECK (status IN ('QUEUED', 'PASSED', 'REVIEW', 'RISK', 'TIMED_OUT', 'FAILED'))
);

ALTER TABLE s_post_media
    DROP INDEX uk_post_media_object,
    ADD INDEX idx_post_media_object (storage_provider, object_key);

ALTER TABLE s_venue_review_media
    DROP INDEX uk_review_media_object,
    ADD INDEX idx_review_media_object (storage_provider, object_key);
