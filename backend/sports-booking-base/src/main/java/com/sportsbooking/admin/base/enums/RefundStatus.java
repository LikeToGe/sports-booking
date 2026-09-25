package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 退款状态。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum RefundStatus {
    PROCESSING("PROCESSING", "退款处理中"),
    SUCCESS("SUCCESS", "退款成功"),
    FAILED("FAILED", "退款失败"),
    CLOSED("CLOSED", "已关闭");

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
