package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 支付渠道；当前只开放本地模拟渠道。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum PaymentProvider {
    MOCK("MOCK", "模拟支付");

    private final String key;
    private final String description;

    PaymentProvider(String key, String description) {
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
