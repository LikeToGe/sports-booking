package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 消息发送状态；真实订阅消息接入前仅记录模拟发送。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum NotificationStatus {
    MOCK_SENT("MOCK_SENT", "模拟发送成功"),
    PENDING_DELIVERY("PENDING_DELIVERY", "待投递");

    private final String key;
    private final String description;

    NotificationStatus(String key, String description) {
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
