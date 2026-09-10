package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 具体场次库存状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum SessionSlotStatus {
    OPEN("OPEN", "可预约"),
    HELD("HELD", "支付占位"),
    BOOKED("BOOKED", "已预约"),
    CLOSED("CLOSED", "已关闭");

    private final String key;
    private final String description;

    SessionSlotStatus(String key, String description) {
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
