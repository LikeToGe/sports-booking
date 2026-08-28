package com.sportsbooking.admin.staff;

import java.time.LocalDateTime;

public class StaffSessionPrincipal {
    private final long sessionId;
    private final long employeeId;
    private final long organizationId;
    private final long storeId;
    private final String employeeName;
    private final LocalDateTime accessExpiresAt;
    private final boolean revoked;

    public StaffSessionPrincipal(long sessionId, long employeeId, long organizationId, long storeId, String employeeName,
                                 LocalDateTime accessExpiresAt, boolean revoked) {
        this.sessionId = sessionId;
        this.employeeId = employeeId;
        this.organizationId = organizationId;
        this.storeId = storeId;
        this.employeeName = employeeName;
        this.accessExpiresAt = accessExpiresAt;
        this.revoked = revoked;
    }

    public long getSessionId() { return sessionId; }
    public long getEmployeeId() { return employeeId; }
    public long getOrganizationId() { return organizationId; }
    public long getStoreId() { return storeId; }
    public String getEmployeeName() { return employeeName; }
    public LocalDateTime getAccessExpiresAt() { return accessExpiresAt; }
    public boolean isRevoked() { return revoked; }
}
