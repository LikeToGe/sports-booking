ALTER TABLE s_venue ADD COLUMN district VARCHAR(50) NULL AFTER city;
CREATE INDEX idx_venue_district ON s_venue (district);
