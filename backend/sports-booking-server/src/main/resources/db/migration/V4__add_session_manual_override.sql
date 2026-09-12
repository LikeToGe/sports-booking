ALTER TABLE s_session_slot
    ADD COLUMN manual_override TINYINT NOT NULL DEFAULT 0 AFTER weekly_slot_id,
    ADD COLUMN template_slot_date DATE NULL AFTER manual_override,
    ADD INDEX idx_session_template_override (weekly_slot_id, template_slot_date, manual_override);

UPDATE s_session_slot
SET template_slot_date = slot_date
WHERE weekly_slot_id IS NOT NULL;
