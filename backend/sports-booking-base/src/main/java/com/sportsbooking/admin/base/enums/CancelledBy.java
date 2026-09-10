package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 取消预约的操作方。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum CancelledBy {
    USER("USER", "用户"),
    VENUE("VENUE", "场馆"),
    SYSTEM("SYSTEM", "系统");

    private final String key;
    private final String description;

    CancelledBy(String key, String description) {
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
