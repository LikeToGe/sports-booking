package com.sportsbooking.admin.base.po;

import java.time.LocalDateTime;

/** 一次内容限权处置，撤销只影响本条限制。 */
public class ContentRestriction {
    public Long id;
    public Long userId;
    public String capability;
    public String reason;
    public Long operatorAdminId;
    public LocalDateTime expiresAt;
}
