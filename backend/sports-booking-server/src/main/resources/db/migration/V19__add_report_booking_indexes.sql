-- P6 批量读取预约快照，场地筛选可选，时间范围紧随场馆前缀。
ALTER TABLE s_booking
    ADD INDEX idx_booking_report_interval (venue_id, start_at, end_at, court_id, sport_type_id, status);
