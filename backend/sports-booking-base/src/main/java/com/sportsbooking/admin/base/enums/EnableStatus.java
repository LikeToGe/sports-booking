package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 账号、场地、员工、运动类型和周场次共用的启停状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum EnableStatus {
    ACTIVE("ACTIVE", "活跃"),
    DISABLED("DISABLED", "停用");

    private final String key;
    private final String description;

    EnableStatus(String key, String description) {
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
