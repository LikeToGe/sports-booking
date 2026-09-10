package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 场馆上架状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum VenueStatus {
    DRAFT("DRAFT", "草稿"),
    ACTIVE("ACTIVE", "营业中"),
    SUSPENDED("SUSPENDED", "已暂停");

    private final String key;
    private final String description;

    VenueStatus(String key, String description) {
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
