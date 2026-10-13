ALTER TABLE s_post_recruitment
    ADD COLUMN level_tag VARCHAR(20) NULL AFTER level,
    ADD INDEX idx_post_recruitment_discovery (level_tag, status, booking_id),
    ADD CONSTRAINT ck_post_recruitment_level_tag CHECK (level_tag IS NULL OR level_tag IN ('BEGINNER_FRIENDLY', 'CASUAL', 'ADVANCED'));
