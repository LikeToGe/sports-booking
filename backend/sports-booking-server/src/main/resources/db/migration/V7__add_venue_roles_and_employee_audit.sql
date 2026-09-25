CREATE TABLE s_venue_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    venue_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_venue_role_name (venue_id, name),
    CONSTRAINT fk_venue_role_venue FOREIGN KEY (venue_id) REFERENCES s_venue (id)
);

CREATE TABLE s_venue_role_permission (
    role_id BIGINT NOT NULL,
    permission_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_venue_role_permission_role FOREIGN KEY (role_id) REFERENCES s_venue_role (id) ON DELETE CASCADE
);

CREATE TABLE s_employee_role (
    employee_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (employee_id, role_id),
    CONSTRAINT fk_employee_role_employee FOREIGN KEY (employee_id) REFERENCES s_employee (id) ON DELETE CASCADE,
    CONSTRAINT fk_employee_role_role FOREIGN KEY (role_id) REFERENCES s_venue_role (id) ON DELETE CASCADE
);

ALTER TABLE s_employee
    DROP FOREIGN KEY fk_employee_user,
    DROP INDEX uk_employee_user;

ALTER TABLE s_employee
    ADD UNIQUE KEY uk_employee_venue_user (venue_id, user_id),
    ADD CONSTRAINT fk_employee_user FOREIGN KEY (user_id) REFERENCES s_user_account (id);

ALTER TABLE s_booking_payment_status_log
    DROP FOREIGN KEY fk_booking_payment_status_log_admin,
    MODIFY COLUMN operator_admin_id BIGINT NULL,
    ADD COLUMN operator_employee_id BIGINT NULL AFTER operator_admin_id;

ALTER TABLE s_booking_payment_status_log
    ADD CONSTRAINT fk_booking_payment_status_log_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    ADD CONSTRAINT fk_booking_payment_status_log_employee FOREIGN KEY (operator_employee_id) REFERENCES s_employee (id),
    ADD CONSTRAINT ck_booking_payment_status_log_operator CHECK (
        (operator_admin_id IS NOT NULL) + (operator_employee_id IS NOT NULL) = 1
    );

ALTER TABLE s_booking_reschedule_log
    DROP FOREIGN KEY fk_booking_reschedule_log_admin,
    MODIFY COLUMN operator_admin_id BIGINT NULL,
    ADD COLUMN operator_employee_id BIGINT NULL AFTER operator_admin_id;

ALTER TABLE s_booking_reschedule_log
    ADD CONSTRAINT fk_booking_reschedule_log_admin FOREIGN KEY (operator_admin_id) REFERENCES s_admin_account (id),
    ADD CONSTRAINT fk_booking_reschedule_log_employee FOREIGN KEY (operator_employee_id) REFERENCES s_employee (id),
    ADD CONSTRAINT ck_booking_reschedule_log_operator CHECK (
        (operator_admin_id IS NOT NULL) + (operator_employee_id IS NOT NULL) = 1
    );

ALTER TABLE s_member_recharge
    ADD COLUMN operator_employee_id BIGINT NULL AFTER operator_admin_id,
    ADD CONSTRAINT fk_member_recharge_employee FOREIGN KEY (operator_employee_id) REFERENCES s_employee (id);

ALTER TABLE s_member_balance_flow
    ADD COLUMN operator_employee_id BIGINT NULL AFTER operator_admin_id,
    ADD CONSTRAINT fk_member_balance_flow_employee FOREIGN KEY (operator_employee_id) REFERENCES s_employee (id);

INSERT INTO s_venue_role (venue_id, name, status)
SELECT DISTINCT venue_id, '核销员工', 'ACTIVE'
FROM s_employee;

INSERT INTO s_venue_role_permission (role_id, permission_code)
SELECT id, 'BOOKING_VERIFY'
FROM s_venue_role
WHERE name = '核销员工';

INSERT INTO s_employee_role (employee_id, role_id)
SELECT e.id, r.id
FROM s_employee e
JOIN s_venue_role r ON r.venue_id = e.venue_id AND r.name = '核销员工';
