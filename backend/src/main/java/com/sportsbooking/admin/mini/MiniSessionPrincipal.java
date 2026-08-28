package com.sportsbooking.admin.mini;

import java.time.LocalDateTime;

public class MiniSessionPrincipal {
    private final long sessionId;
    private final long userId;
    private final LocalDateTime accessExpiresAt;
    private final boolean revoked;

    public MiniSessionPrincipal(long sessionId, long userId, LocalDateTime accessExpiresAt, boolean revoked) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.accessExpiresAt = accessExpiresAt;
        this.revoked = revoked;
    }

    public long getSessionId() { return sessionId; }
    public long getUserId() { return userId; }
    public LocalDateTime getAccessExpiresAt() { return accessExpiresAt; }
    public boolean isRevoked() { return revoked; }
}
