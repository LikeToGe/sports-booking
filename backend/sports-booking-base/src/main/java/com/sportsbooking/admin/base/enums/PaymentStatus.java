package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 支付状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum PaymentStatus {
    PENDING("PENDING", "待支付"),
    SUCCESS("SUCCESS", "支付成功"),
    FAILED("FAILED", "支付失败"),
    CLOSED("CLOSED", "已关闭");

    private final String key;
    private final String description;

    PaymentStatus(String key, String description) {
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
