package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.PaymentResult;
import com.sportsbooking.admin.base.enums.PaymentStatus;
import com.sportsbooking.admin.base.enums.RechargeStatus;
import com.sportsbooking.admin.base.enums.RechargeChannel;
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
    /** Mock 尚未显式支付的 PENDING 不发生外部扣款，可明确关闭；缺失事实保持未知。 */
    @Override public PaymentResult closePayment(Payment payment) {
        if (payment == null || payment.status == null) return PaymentResult.UNKNOWN;
        return switch (payment.status) {
            case SUCCESS -> PaymentResult.SUCCESS;
            case PENDING, FAILED, CLOSED -> PaymentResult.FAILED;
        };
    }
    @Override public PaymentResult refund(Refund refund) { return PaymentResult.SUCCESS; }
    @Override public PaymentResult startRecharge(MemberRecharge recharge) { return PaymentResult.UNKNOWN; }
    @Override public PaymentResult queryRecharge(MemberRecharge recharge) { return PaymentResult.UNKNOWN; }
    /** 仅关闭本地 Mock 的明确未支付记录，不对线下渠道或缺失状态推断资金结果。 */
    @Override public PaymentResult closeRecharge(MemberRecharge recharge) {
        if (recharge == null || recharge.status == null || recharge.channel != RechargeChannel.MOCK) {
            return PaymentResult.UNKNOWN;
        }
        return switch (recharge.status) {
            case SUCCESS -> PaymentResult.SUCCESS;
            case PENDING, FAILED, CLOSED -> PaymentResult.FAILED;
            case REVERSED -> PaymentResult.UNKNOWN;
        };
    }
}
