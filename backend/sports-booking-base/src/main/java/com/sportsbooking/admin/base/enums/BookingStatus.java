package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 预约生命周期状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum BookingStatus {
    PENDING_PAYMENT("PENDING_PAYMENT", "待支付"),
    CONFIRMED("CONFIRMED", "待核销"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消"),
    NO_SHOW("NO_SHOW", "未到场");

    private final String key;
    private final String description;

    BookingStatus(String key, String description) {
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
