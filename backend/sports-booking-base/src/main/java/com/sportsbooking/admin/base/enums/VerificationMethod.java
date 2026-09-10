package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 核销凭证类型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public enum VerificationMethod {
    QR("QR", "二维码"),
    SHORT_CODE("SHORT_CODE", "短码");

    private final String key;
    private final String description;

    VerificationMethod(String key, String description) {
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
