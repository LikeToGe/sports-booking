-- 旧案件无法可靠还原提交时所见版本，保留 NULL，不以当前版本伪造历史证据。
ALTER TABLE s_content_report ADD COLUMN evidence_revision_id BIGINT NULL;
