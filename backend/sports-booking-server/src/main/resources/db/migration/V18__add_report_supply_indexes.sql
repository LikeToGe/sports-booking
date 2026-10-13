-- P6 批量读取场馆留存供给的真实时间区间。
ALTER TABLE s_session_slot
    ADD INDEX idx_session_report_supply (venue_id, start_at, end_at, court_id, sport_type_id, status);
