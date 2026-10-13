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
    SESSION_REMINDER("SESSION_REMINDER", "场次提醒"),
    BALANCE_EXPIRY_REMINDER("BALANCE_EXPIRY_REMINDER", "赠送余额到期提醒"),
    CONTENT_ACTIVITY("CONTENT_ACTIVITY", "内容互动"),
    ARRIVAL_REMINDER("ARRIVAL_REMINDER", "到场提醒"),
    OPENING_ALERT("OPENING_ALERT", "开售提醒"),
    SLOT_RELEASED_ALERT("SLOT_RELEASED_ALERT", "空位提醒");

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
