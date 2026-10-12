package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;

/** 后台动作事件投影。 */
public class UserActionEventBO {
    public Long id;
    public Long userId;
    public String eventType;
    public String targetType;
    public Long targetId;
    public LocalDateTime occurredAt;
}
