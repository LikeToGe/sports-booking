package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;

/** 外部支付渠道边界；实现不得写内部资金表。 */
public interface PaymentChannelAdapter {
    PaymentResult startPayment(Payment payment);
    PaymentResult queryPayment(Payment payment);
    /**
     * SUCCESS 表示已支付；FAILED 仅限渠道确认未支付并关闭；UNKNOWN 不得解除内部占位。
     * 实现必须调用安全闭单操作，不能由查单未知推断未支付，闭单后不得再次发起扣款。
     */
    PaymentResult closePayment(Payment payment);
    PaymentResult refund(Refund refund);
    PaymentResult startRecharge(MemberRecharge recharge);
    PaymentResult queryRecharge(MemberRecharge recharge);
    /**
     * SUCCESS 表示已支付待内部入账；FAILED 仅限渠道保证已关闭且不会再扣款；UNKNOWN 拒绝关闭。
     * 真实渠道必须验证安全闭单结果，不能仅依赖内部 PENDING 或查单未知解除资金阻塞。
     */
    PaymentResult closeRecharge(MemberRecharge recharge);
}
