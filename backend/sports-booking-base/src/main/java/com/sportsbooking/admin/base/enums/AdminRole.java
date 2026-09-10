package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 后台账号角色。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum AdminRole {
    PLATFORM_ADMIN("PLATFORM_ADMIN", "平台管理员"),
    VENUE_ADMIN("VENUE_ADMIN", "场馆管理员");

    private final String key;
    private final String description;

    AdminRole(String key, String description) {
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
