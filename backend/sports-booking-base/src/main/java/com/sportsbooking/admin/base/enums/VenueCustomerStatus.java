package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 场馆级顾客与储值会员状态。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
public enum VenueCustomerStatus {
    CUSTOMER("CUSTOMER", "普通顾客"),
    MEMBER("MEMBER", "会员");

    private final String key;
    private final String description;

    VenueCustomerStatus(String key, String description) {
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
