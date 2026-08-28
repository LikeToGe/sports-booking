package com.sportsbooking.admin.staff;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationAttemptRecorder {
    private final JdbcTemplate jdbcTemplate;

    public VerificationAttemptRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(long employeeId, Long bookingOrderId, Long credentialId, String method, String result, String failureReason) {
        jdbcTemplate.update("INSERT INTO verification_attempt (employee_id, booking_order_id, credential_id, verification_method, attempt_result, failure_reason, "
                + "create_operator_id, modify_operator_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", employeeId, bookingOrderId, credentialId, method, result,
            failureReason, employeeId, employeeId);
    }
}
