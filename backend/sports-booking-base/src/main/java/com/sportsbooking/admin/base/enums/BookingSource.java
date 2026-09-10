package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 预约创建来源。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum BookingSource {
    ONLINE("ONLINE", "线上预约"),
    OFFLINE("OFFLINE", "线下预约");

    private final String key;
    private final String description;

    BookingSource(String key, String description) {
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
