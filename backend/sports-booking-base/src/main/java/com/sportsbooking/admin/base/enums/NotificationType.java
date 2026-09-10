package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 当前需要记录的消息触发类型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum NotificationType {
    BOOKING_CONFIRMED("BOOKING_CONFIRMED", "预约确认"),
    BOOKING_CANCELLED("BOOKING_CANCELLED", "预约取消"),
    SESSION_REMINDER("SESSION_REMINDER", "场次提醒");

    private final String key;
    private final String description;

    NotificationType(String key, String description) {
        this.key = key;
        this.description = description;
    }

    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }
}
