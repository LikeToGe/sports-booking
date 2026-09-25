package com.sportsbooking.admin.base.enums;

/**
 * 功能描述: 预约整体收款或线下登记收款状态。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
public enum BookingPaymentStatus {
    UNRECORDED("UNRECORDED", "未记录"),
    UNPAID("UNPAID", "未支付"),
    PAYING("PAYING", "支付中"),
    PAID("PAID", "已支付"),
    REFUNDING("REFUNDING", "退款中"),
    REFUNDED("REFUNDED", "已退款"),
    PAYMENT_FAILED("PAYMENT_FAILED", "支付失败"),
    REFUND_FAILED("REFUND_FAILED", "退款失败");

    private final String key;
    private final String description;

    BookingPaymentStatus(String key, String description) {
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
