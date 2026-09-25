package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;

/** 外部支付渠道边界；实现不得写内部资金表。 */
public interface PaymentChannelAdapter {
    PaymentResult startPayment(Payment payment);
    PaymentResult queryPayment(Payment payment);
    PaymentResult refund(Refund refund);
    PaymentResult startRecharge(MemberRecharge recharge);
    PaymentResult queryRecharge(MemberRecharge recharge);
}
