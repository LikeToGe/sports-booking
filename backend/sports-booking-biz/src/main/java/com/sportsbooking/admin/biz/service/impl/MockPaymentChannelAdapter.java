package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.po.MemberRecharge;
import com.sportsbooking.admin.base.po.Payment;
import com.sportsbooking.admin.base.po.Refund;
import com.sportsbooking.admin.biz.service.PaymentChannelAdapter;
import org.springframework.stereotype.Component;

/** 本地 Mock 渠道；发起和查单保持未知，通知入口负责显式成功。 */
@Component
public class MockPaymentChannelAdapter implements PaymentChannelAdapter {
    @Override public PaymentResult startPayment(Payment payment) { return PaymentResult.UNKNOWN; }
    @Override public PaymentResult queryPayment(Payment payment) { return PaymentResult.UNKNOWN; }
    @Override public PaymentResult refund(Refund refund) { return PaymentResult.SUCCESS; }
    @Override public PaymentResult startRecharge(MemberRecharge recharge) { return PaymentResult.UNKNOWN; }
    @Override public PaymentResult queryRecharge(MemberRecharge recharge) { return PaymentResult.UNKNOWN; }
}
