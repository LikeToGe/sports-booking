package com.sportsbooking.admin.auth;

import java.time.LocalDateTime;

public class SessionPrincipal {
    private final long sessionId;
    private final long accountId;
    private final String username;
    private final String displayName;
    private final String role;
    private final Long storeId;
    private final Long organizationId;
    private final boolean forcePasswordChange;
    private final String csrfTokenHash;
    private final LocalDateTime expiresAt;
    private final boolean revoked;

    public SessionPrincipal(long sessionId, long accountId, String username, String displayName, String role,
                            Long storeId, Long organizationId, boolean forcePasswordChange,
                            String csrfTokenHash, LocalDateTime expiresAt, boolean revoked) {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.username = username;
        this.displayName = displayName;
        this.role = role;
        this.storeId = storeId;
        this.organizationId = organizationId;
        this.forcePasswordChange = forcePasswordChange;
        this.csrfTokenHash = csrfTokenHash;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }

    public long getSessionId() { return sessionId; }
    public long getAccountId() { return accountId; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getRole() { return role; }
    public Long getStoreId() { return storeId; }
    public Long getOrganizationId() { return organizationId; }
    public boolean isForcePasswordChange() { return forcePasswordChange; }
    public String getCsrfTokenHash() { return csrfTokenHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
}
