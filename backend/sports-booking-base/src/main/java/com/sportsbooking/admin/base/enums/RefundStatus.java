package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 退款状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum RefundStatus {
    PENDING("PENDING", "退款处理中"),
    SUCCESS("SUCCESS", "退款成功");

    private final String key;
    private final String description;

    RefundStatus(String key, String description) {
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
