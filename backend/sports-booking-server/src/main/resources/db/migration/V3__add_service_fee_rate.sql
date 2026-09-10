ALTER TABLE s_venue
    ADD COLUMN service_fee_rate_basis_points INT NOT NULL DEFAULT 0 AFTER cancel_cutoff_minutes,
    ADD CONSTRAINT chk_venue_service_fee_rate
        CHECK (service_fee_rate_basis_points BETWEEN 0 AND 3000);

ALTER TABLE s_booking
    ADD COLUMN service_fee_rate_basis_points INT NOT NULL DEFAULT 0 AFTER amount_fen,
    ADD CONSTRAINT chk_booking_service_fee_rate
        CHECK (service_fee_rate_basis_points BETWEEN 0 AND 3000);
